package rs.ac.bg.etf.kdp.worker;

import rs.ac.bg.etf.kdp.common.protocol.ConnectionRole;
import rs.ac.bg.etf.kdp.common.protocol.FileTransferMetadata;
import rs.ac.bg.etf.kdp.common.protocol.FileTransferProtocolCodec;
import rs.ac.bg.etf.kdp.common.protocol.FileTransferSender;
import rs.ac.bg.etf.kdp.common.protocol.MessageType;
import rs.ac.bg.etf.kdp.common.protocol.ProtocolError;
import rs.ac.bg.etf.kdp.common.protocol.ProtocolException;
import rs.ac.bg.etf.kdp.common.protocol.ProtocolFrame;
import rs.ac.bg.etf.kdp.common.protocol.ProtocolFrameCodec;
import rs.ac.bg.etf.kdp.common.protocol.ProtocolHandshake;
import rs.ac.bg.etf.kdp.common.protocol.ProtocolSockets;
import rs.ac.bg.etf.kdp.common.protocol.ReceivedFile;
import rs.ac.bg.etf.kdp.common.protocol.TaskAssignmentMessage;
import rs.ac.bg.etf.kdp.common.protocol.TaskExecutionMessage;
import rs.ac.bg.etf.kdp.common.protocol.TaskExecutionProtocolCodec;
import rs.ac.bg.etf.kdp.common.protocol.TaskFailureMessage;
import rs.ac.bg.etf.kdp.common.protocol.TaskProtocolCodec;
import rs.ac.bg.etf.kdp.common.protocol.TransferFileKind;
import rs.ac.bg.etf.kdp.common.protocol.WorkerProtocolCodec;
import rs.ac.bg.etf.kdp.common.protocol.WorkerRegistrationMessage;
import rs.ac.bg.etf.kdp.runner.JavaTaskResult;
import rs.ac.bg.etf.kdp.runner.JavaTaskRunner;
import rs.ac.bg.etf.kdp.runner.LindaConnectionInfo;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Network client used by a worker process, with or without a GUI. */
public final class WorkerClient implements AutoCloseable {

    private static final Logger LOGGER = Logger.getLogger(WorkerClient.class.getName());

    private final WorkerConfig config;
    private final WorkerTaskHandler taskHandler;
    private final ProtocolFrameCodec frameCodec = new ProtocolFrameCodec();
    private final WorkerProtocolCodec workerCodec = new WorkerProtocolCodec();
    private final TaskProtocolCodec taskCodec = new TaskProtocolCodec();
    private final TaskExecutionProtocolCodec executionCodec = new TaskExecutionProtocolCodec();
    private final FileTransferProtocolCodec fileCodec = new FileTransferProtocolCodec();
    private final FileTransferSender fileSender = new FileTransferSender();
    private final ProtocolHandshake handshake = new ProtocolHandshake();
    private final Object writeLock = new Object();
    private final Object resultTransferLock = new Object();
    private final AtomicBoolean running = new AtomicBoolean();
    private final CountDownLatch stopped = new CountDownLatch(1);
    private final Set<UUID> assignedTaskIds = ConcurrentHashMap.newKeySet();
    private final Set<UUID> executingTaskIds = ConcurrentHashMap.newKeySet();
    private final Map<UUID, CompletableFuture<Void>> pendingResultTransfers =
            new ConcurrentHashMap<>();
    private final WorkerFileStore fileStore;
    private final JavaTaskRunner taskRunner = new JavaTaskRunner();

    private volatile Socket socket;
    private volatile InputStream input;
    private volatile OutputStream output;
    private volatile ScheduledExecutorService heartbeatExecutor;
    private volatile ExecutorService taskExecutor;

    public WorkerClient(WorkerConfig config) {
        this(config, assignment -> { });
    }

    public WorkerClient(WorkerConfig config, WorkerTaskHandler taskHandler) {
        this.config = Objects.requireNonNull(config, "worker config must not be null");
        this.taskHandler = Objects.requireNonNull(taskHandler, "task handler must not be null");
        this.fileStore = new WorkerFileStore(config.getWorkDirectory(), assignedTaskIds);
    }

    public synchronized void start() throws IOException {
        if (running.get()) {
            throw new IllegalStateException("worker client is already running");
        }

        Socket connectedSocket = ProtocolSockets.connect(
                config.getServerHost(),
                config.getServerPort(),
                config.getConnectTimeoutMillis(),
                config.getHandshakeTimeoutMillis());
        try {
            socket = connectedSocket;
            input = connectedSocket.getInputStream();
            output = connectedSocket.getOutputStream();
            performHandshake();
            registerWorker();
            connectedSocket.setSoTimeout(0);
            running.set(true);
            startTaskExecutor();
            startHeartbeat();
            startReader();
        } catch (IOException | RuntimeException exception) {
            closeSocket(connectedSocket);
            socket = null;
            input = null;
            output = null;
            throw exception;
        }
    }

    public boolean isRunning() {
        return running.get();
    }

    public void awaitTermination() throws InterruptedException {
        stopped.await();
    }

    public Set<UUID> getAssignedTaskIds() {
        return Set.copyOf(assignedTaskIds);
    }

    public Path getTaskDirectory(UUID taskId) {
        return fileStore.taskDirectory(taskId);
    }

    public void reportTaskStarted(UUID taskId) throws IOException {
        requireAssignedTask(taskId);
        send(ProtocolFrame.request(
                MessageType.TASK_STARTED,
                taskCodec.encodeTaskReference(taskId)));
    }

    public void reportTaskFinished(UUID taskId) throws IOException {
        requireAssignedTask(taskId);
        assignedTaskIds.remove(taskId);
        send(ProtocolFrame.request(
                MessageType.TASK_FINISHED,
                taskCodec.encodeTaskReference(taskId)));
    }

    public void reportTaskFailed(UUID taskId, String reason) throws IOException {
        requireAssignedTask(taskId);
        byte[] payload = taskCodec.encodeFailure(new TaskFailureMessage(taskId, reason));
        assignedTaskIds.remove(taskId);
        send(ProtocolFrame.request(
                MessageType.TASK_FAILED,
                payload));
    }

    private void performHandshake() throws IOException {
        ProtocolFrame hello = handshake.createHello(ConnectionRole.WORKER);
        send(hello);
        ProtocolFrame response = frameCodec.read(input);
        handshake.verifyAccepted(response, hello.getRequestId());
    }

    private void registerWorker() throws IOException {
        WorkerRegistrationMessage registration = new WorkerRegistrationMessage(
                config.getWorkerId(),
                localHostname(),
                operatingSystemDescription(),
                System.getProperty("java.version", "unknown"),
                config.getCapacity());
        ProtocolFrame request = ProtocolFrame.request(
                MessageType.REGISTER_WORKER,
                workerCodec.encodeRegistration(registration));
        send(request);

        ProtocolFrame response = frameCodec.read(input);
        if (!request.getRequestId().equals(response.getRequestId())) {
            throw new ProtocolException(
                    ProtocolError.CORRELATION_MISMATCH,
                    "Worker registration response has a different request id");
        }
        if (response.getType() != MessageType.WORKER_REGISTERED
                || response.getPayloadSize() != 0) {
            throw new ProtocolException(
                    ProtocolError.INVALID_MESSAGE_PAYLOAD,
                    "Expected an empty WORKER_REGISTERED response");
        }
    }

    private void startHeartbeat() {
        heartbeatExecutor = Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(task, "kdp-worker-heartbeat");
            thread.setDaemon(true);
            return thread;
        });
        heartbeatExecutor.scheduleAtFixedRate(
                this::sendHeartbeat,
                config.getHeartbeatIntervalMillis(),
                config.getHeartbeatIntervalMillis(),
                TimeUnit.MILLISECONDS);
    }

    private void sendHeartbeat() {
        if (!running.get()) {
            return;
        }
        try {
            send(ProtocolFrame.request(
                    MessageType.HEARTBEAT,
                    workerCodec.encodeHeartbeat(config.getWorkerId())));
        } catch (IOException exception) {
            LOGGER.log(Level.FINE, "Could not send worker heartbeat", exception);
            close();
        }
    }

    private void startReader() {
        Thread reader = new Thread(this::readLoop, "kdp-worker-server-reader");
        reader.setDaemon(true);
        reader.start();
    }

    private void readLoop() {
        try {
            while (running.get()) {
                ProtocolFrame frame = frameCodec.read(input);
                if (frame.getType() == MessageType.ERROR) {
                    LOGGER.warning("Server reported an error; worker connection will close");
                    break;
                } else if (frame.getType() == MessageType.ASSIGN_TASK) {
                    acceptAssignment(frame);
                } else if (frame.getType() == MessageType.EXECUTE_TASK) {
                    executeTask(frame);
                } else if (frame.getType() == MessageType.FILE_ACKNOWLEDGED) {
                    acknowledgeResultFile(frame);
                } else if (frame.getType() == MessageType.FILE_BEGIN
                        || frame.getType() == MessageType.FILE_CHUNK
                        || frame.getType() == MessageType.FILE_END) {
                    receiveFile(frame);
                } else {
                    throw new ProtocolException(
                            ProtocolError.INVALID_MESSAGE_PAYLOAD,
                            "Unexpected server message: " + frame.getType());
                }
            }
        } catch (IOException exception) {
            if (running.get()) {
                LOGGER.log(Level.FINE, "Worker connection closed", exception);
            }
        } finally {
            close();
        }
    }

    private void receiveFile(ProtocolFrame frame) throws IOException {
        Optional<ReceivedFile> receivedFile = fileStore.accept(frame);
        if (receivedFile.isPresent()) {
            send(receivedFile.get().getAcknowledgement());
        }
    }

    private void acceptAssignment(ProtocolFrame frame) throws IOException {
        TaskAssignmentMessage assignment = taskCodec.decodeAssignment(frame.getPayload());
        boolean newAssignment = assignedTaskIds.add(assignment.getTaskId());
        if (newAssignment && assignedTaskIds.size() > config.getCapacity()) {
            assignedTaskIds.remove(assignment.getTaskId());
            throw new ProtocolException(
                    ProtocolError.INVALID_MESSAGE_PAYLOAD,
                    "Server assigned more tasks than this worker can run");
        }

        if (newAssignment) {
            try {
                taskHandler.onTaskAssigned(assignment);
            } catch (RuntimeException exception) {
                assignedTaskIds.remove(assignment.getTaskId());
                throw new ProtocolException(
                        ProtocolError.INVALID_MESSAGE_PAYLOAD,
                        "Worker could not accept the assigned task",
                        exception);
            }
        }

        send(frame.reply(
                MessageType.TASK_ACCEPTED,
                taskCodec.encodeTaskReference(assignment.getTaskId())));
    }

    private void executeTask(ProtocolFrame frame) throws ProtocolException {
        TaskExecutionMessage execution = executionCodec.decode(frame.getPayload());
        UUID taskId = execution.getTaskId();
        if (!assignedTaskIds.contains(taskId)) {
            throw new ProtocolException(
                    ProtocolError.INVALID_MESSAGE_PAYLOAD,
                    "Server requested execution of a task that is not assigned to this worker");
        }
        if (!executingTaskIds.add(taskId)) {
            throw new ProtocolException(
                    ProtocolError.INVALID_MESSAGE_PAYLOAD,
                    "Task execution has already started");
        }

        try {
            taskExecutor.submit(() -> runTask(execution));
        } catch (RejectedExecutionException exception) {
            executingTaskIds.remove(taskId);
            throw new ProtocolException(
                    ProtocolError.INVALID_MESSAGE_PAYLOAD,
                    "Worker is shutting down and cannot start the task",
                    exception);
        }
    }

    private void runTask(TaskExecutionMessage execution) {
        UUID taskId = execution.getTaskId();
        try {
            reportTaskStarted(taskId);
            LindaConnectionInfo lindaConnection = new LindaConnectionInfo(
                    config.getServerHost(),
                    config.getServerPort(),
                    execution.getBatchId(),
                    taskId);
            JavaTaskResult result;
            if (execution.isEvalTask()) {
                result = taskRunner.runEval(
                        execution.getSpecification(),
                        fileStore.taskDirectory(taskId),
                        config.getLindaLibrary(),
                        lindaConnection,
                        execution.getEvalName(),
                        execution.getSerializedRunnable());
            } else {
                result = taskRunner.run(
                        execution.getSpecification(),
                        fileStore.taskDirectory(taskId),
                        config.getLindaLibrary(),
                        lindaConnection);
            }
            sendTaskResults(taskId, result);
            if (result.isSuccessful()) {
                reportTaskFinished(taskId);
            } else {
                reportTaskFailed(taskId, executionFailureReason(result));
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            reportExecutionFailure(taskId, "Task execution was interrupted", exception);
        } catch (IOException | RuntimeException exception) {
            String reason = exception.getMessage();
            if (reason == null || reason.isBlank()) {
                reason = exception.getClass().getSimpleName();
            }
            reportExecutionFailure(taskId, reason, exception);
        } finally {
            executingTaskIds.remove(taskId);
        }
    }

    private void sendTaskResults(UUID taskId, JavaTaskResult result)
            throws IOException, InterruptedException {
        sendResultFile(
                taskId,
                result.getStandardOutput(),
                TransferFileKind.STANDARD_OUTPUT,
                "stdout.txt");
        sendResultFile(
                taskId,
                result.getStandardError(),
                TransferFileKind.STANDARD_ERROR,
                "stderr.txt");
        for (Path outputFile : result.getOutputFiles()) {
            if (Files.isRegularFile(outputFile)) {
                sendResultFile(
                        taskId,
                        outputFile,
                        TransferFileKind.OUTPUT,
                        outputFile.getFileName().toString());
            }
        }
    }

    private static String executionFailureReason(JavaTaskResult result) {
        if (result.getExitCode() != 0) {
            return "Java process exited with code " + result.getExitCode();
        }
        Path missingOutput = result.getMissingOutputFiles().get(0);
        return "Declared output file was not created: " + missingOutput.getFileName();
    }

    private void sendResultFile(
            UUID taskId,
            Path source,
            TransferFileKind kind,
            String remoteFileName) throws IOException, InterruptedException {
        synchronized (resultTransferLock) {
            sendResultFileAndWaitForAcknowledgement(
                    taskId, source, kind, remoteFileName);
        }
    }

    private void sendResultFileAndWaitForAcknowledgement(
            UUID taskId,
            Path source,
            TransferFileKind kind,
            String remoteFileName) throws IOException, InterruptedException {
        UUID transferId = UUID.randomUUID();
        CompletableFuture<Void> acknowledgement = new CompletableFuture<>();
        pendingResultTransfers.put(transferId, acknowledgement);
        FileTransferMetadata metadata = new FileTransferMetadata(
                taskId, kind, remoteFileName, Files.size(source));
        try {
            fileSender.send(transferId, source, metadata, this::send);
        } catch (IOException | RuntimeException exception) {
            pendingResultTransfers.remove(transferId);
            throw exception;
        }

        try {
            acknowledgement.get();
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof IOException ioException) {
                throw ioException;
            }
            throw new IOException("Result transfer acknowledgement failed", cause);
        } finally {
            pendingResultTransfers.remove(transferId);
        }
    }

    private void acknowledgeResultFile(ProtocolFrame frame) throws ProtocolException {
        CompletableFuture<Void> acknowledgement = pendingResultTransfers.get(
                frame.getRequestId());
        if (acknowledgement == null) {
            throw new ProtocolException(
                    ProtocolError.CORRELATION_MISMATCH,
                    "File acknowledgement does not match a result transfer");
        }
        fileCodec.verifyAcknowledgement(frame, frame.getRequestId());
        pendingResultTransfers.remove(frame.getRequestId(), acknowledgement);
        acknowledgement.complete(null);
    }

    private void reportExecutionFailure(UUID taskId, String reason, Exception cause) {
        if (!running.get() || !assignedTaskIds.contains(taskId)) {
            return;
        }
        try {
            reportTaskFailed(taskId, reason);
        } catch (IOException | RuntimeException reportingException) {
            LOGGER.log(Level.FINE, "Could not report task execution failure", reportingException);
            close();
        }
        LOGGER.log(Level.FINE, "Task execution failed", cause);
    }

    private void send(ProtocolFrame frame) throws IOException {
        synchronized (writeLock) {
            frameCodec.write(output, frame);
        }
    }

    private void startTaskExecutor() {
        taskExecutor = Executors.newFixedThreadPool(config.getCapacity(), task -> {
            Thread thread = new Thread(task, "kdp-worker-task");
            thread.setDaemon(true);
            return thread;
        });
    }

    private void requireAssignedTask(UUID taskId) {
        Objects.requireNonNull(taskId, "task id must not be null");
        if (!assignedTaskIds.contains(taskId)) {
            throw new IllegalArgumentException("task is not assigned to this worker");
        }
    }

    @Override
    public void close() {
        if (!running.getAndSet(false) && socket == null) {
            return;
        }
        ScheduledExecutorService executor = heartbeatExecutor;
        if (executor != null) {
            executor.shutdownNow();
        }
        ExecutorService executionExecutor = taskExecutor;
        if (executionExecutor != null) {
            executionExecutor.shutdownNow();
        }
        IOException closedException = new IOException("Worker connection closed");
        pendingResultTransfers.values().forEach(
                acknowledgement -> acknowledgement.completeExceptionally(closedException));
        pendingResultTransfers.clear();
        Socket connectedSocket = socket;
        socket = null;
        closeSocket(connectedSocket);
        fileStore.close();
        stopped.countDown();
    }

    private static String localHostname() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException exception) {
            return "unknown-host";
        }
    }

    private static String operatingSystemDescription() {
        String name = System.getProperty("os.name", "unknown");
        String version = System.getProperty("os.version", "");
        return version.isBlank() ? name : name + " " + version;
    }

    private static void closeSocket(Socket socket) {
        if (socket == null) {
            return;
        }
        try {
            socket.close();
        } catch (IOException ignored) {
            // The connection failure that caused cleanup is more useful.
        }
    }
}
