package rs.ac.bg.etf.kdp.server;

import rs.ac.bg.etf.kdp.common.domain.JobSpecification;
import rs.ac.bg.etf.kdp.common.domain.Task;
import rs.ac.bg.etf.kdp.common.domain.TaskStatus;
import rs.ac.bg.etf.kdp.common.protocol.FileTransferMetadata;
import rs.ac.bg.etf.kdp.common.protocol.FileTransferProtocolCodec;
import rs.ac.bg.etf.kdp.common.protocol.FileTransferSender;
import rs.ac.bg.etf.kdp.common.protocol.MessageType;
import rs.ac.bg.etf.kdp.common.protocol.ProtocolError;
import rs.ac.bg.etf.kdp.common.protocol.ProtocolException;
import rs.ac.bg.etf.kdp.common.protocol.ProtocolFrame;
import rs.ac.bg.etf.kdp.common.protocol.TaskAssignmentMessage;
import rs.ac.bg.etf.kdp.common.protocol.TaskExecutionMessage;
import rs.ac.bg.etf.kdp.common.protocol.TaskExecutionProtocolCodec;
import rs.ac.bg.etf.kdp.common.protocol.TaskFailureMessage;
import rs.ac.bg.etf.kdp.common.protocol.TaskProtocolCodec;
import rs.ac.bg.etf.kdp.common.protocol.TransferFileKind;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Assigns ready tasks to registered workers with available capacity. */
public final class TaskScheduler {

    private final WorkerRegistry workerRegistry;
    private final Clock clock;
    private final TaskProtocolCodec taskCodec = new TaskProtocolCodec();
    private final TaskExecutionProtocolCodec executionCodec = new TaskExecutionProtocolCodec();
    private final FileTransferProtocolCodec fileCodec = new FileTransferProtocolCodec();
    private final FileTransferSender fileSender = new FileTransferSender();
    private final ArrayDeque<Task> readyTasks = new ArrayDeque<>();
    private final Map<UUID, Task> tasks = new LinkedHashMap<>();
    private final Map<UUID, Task> pendingAcceptances = new HashMap<>();
    private final Set<UUID> acceptedTaskIds = new HashSet<>();
    private final Set<UUID> executionRequestedTaskIds = new HashSet<>();
    private final Map<UUID, Task> pendingFileTransfers = new HashMap<>();
    private final Set<UUID> acknowledgedFileTransfers = new HashSet<>();

    public TaskScheduler(WorkerRegistry workerRegistry) {
        this(workerRegistry, Clock.systemUTC());
    }

    TaskScheduler(WorkerRegistry workerRegistry, Clock clock) {
        this.workerRegistry = Objects.requireNonNull(
                workerRegistry, "worker registry must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    public synchronized void submit(Task task) throws IOException {
        Objects.requireNonNull(task, "task must not be null");
        if (task.getStatus() != TaskStatus.READY) {
            throw new IllegalArgumentException("only a ready task can be submitted");
        }
        if (tasks.putIfAbsent(task.getId(), task) != null) {
            throw new IllegalArgumentException("task is already known to the scheduler");
        }
        readyTasks.addLast(task);
        dispatchAvailableTasks();
    }

    public synchronized void workerAvailable() throws IOException {
        dispatchAvailableTasks();
    }

    public synchronized void accept(ServerConnection connection, ProtocolFrame frame)
            throws ProtocolException {
        UUID taskId = taskCodec.decodeTaskReference(frame.getPayload());
        Task task = pendingAcceptances.get(frame.getRequestId());
        if (task == null) {
            throw new ProtocolException(
                    ProtocolError.CORRELATION_MISMATCH,
                    "Task acceptance does not match a pending assignment");
        }
        if (!task.getId().equals(taskId)) {
            throw new ProtocolException(
                    ProtocolError.CORRELATION_MISMATCH,
                    "Task acceptance contains a different task id");
        }

        UUID workerId = workerRegistry.findWorkerId(connection).orElseThrow(() ->
                new ProtocolException(
                        ProtocolError.INVALID_MESSAGE_PAYLOAD,
                        "Unregistered worker cannot accept a task"));
        if (!task.getAssignedWorkerId().filter(workerId::equals).isPresent()) {
            throw new ProtocolException(
                    ProtocolError.CORRELATION_MISMATCH,
                    "Task was assigned to a different worker");
        }

        pendingAcceptances.remove(frame.getRequestId());
        acceptedTaskIds.add(taskId);
    }

    public synchronized void workerDisconnected(ServerConnection connection) {
        Optional<UUID> disconnectedWorkerId = workerRegistry.findWorkerId(connection);
        if (disconnectedWorkerId.isEmpty()) {
            return;
        }

        for (Task task : tasks.values()) {
            boolean belongsToWorker = task.getAssignedWorkerId()
                    .filter(disconnectedWorkerId.get()::equals)
                    .isPresent();
            if (belongsToWorker
                    && (task.getStatus() == TaskStatus.SCHEDULED
                    || task.getStatus() == TaskStatus.RUNNING)) {
                removePendingAcceptance(task.getId());
                acceptedTaskIds.remove(task.getId());
                executionRequestedTaskIds.remove(task.getId());
                workerRegistry.releaseTask(task);
                task.markFailed(clock.instant(), "Worker disconnected");
            }
        }
        pendingFileTransfers.entrySet().removeIf(entry -> entry.getValue()
                .getAssignedWorkerId()
                .filter(disconnectedWorkerId.get()::equals)
                .isPresent());
    }

    public synchronized void retryTask(UUID taskId) throws IOException {
        Objects.requireNonNull(taskId, "task id must not be null");
        Task task = tasks.get(taskId);
        if (task == null) {
            throw new IllegalArgumentException("task is not known to the scheduler");
        }
        if (task.getStatus() != TaskStatus.FAILED) {
            throw new IllegalStateException("only a failed task can be retried");
        }
        task.retry(clock.instant());
        readyTasks.addLast(task);
        dispatchAvailableTasks();
    }

    public synchronized void startTask(
            ServerConnection connection,
            ProtocolFrame frame) throws ProtocolException {
        UUID taskId = taskCodec.decodeTaskReference(frame.getPayload());
        Task task = assignedTask(connection, taskId);
        requireAccepted(taskId);
        if (!executionRequestedTaskIds.contains(taskId)) {
            throw new ProtocolException(
                    ProtocolError.INVALID_MESSAGE_PAYLOAD,
                    "Server did not request execution of this task");
        }
        task.markRunning(clock.instant());
    }

    public synchronized void requestExecution(
            UUID taskId,
            JobSpecification specification) throws IOException {
        Objects.requireNonNull(taskId, "task id must not be null");
        Objects.requireNonNull(specification, "job specification must not be null");
        Task task = tasks.get(taskId);
        if (task == null) {
            throw new IllegalArgumentException("task is not known to the scheduler");
        }
        if (task.getStatus() != TaskStatus.SCHEDULED || !acceptedTaskIds.contains(taskId)) {
            throw new IllegalStateException("worker must accept the task before execution");
        }
        if (executionRequestedTaskIds.contains(taskId)) {
            throw new IllegalStateException("task execution has already been requested");
        }
        boolean transferInProgress = pendingFileTransfers.values().stream()
                .anyMatch(pendingTask -> pendingTask.getId().equals(taskId));
        if (transferInProgress) {
            throw new IllegalStateException(
                    "all task files must be acknowledged before execution");
        }

        UUID workerId = task.getAssignedWorkerId().orElseThrow();
        ServerConnection connection = workerRegistry.findConnection(workerId)
                .orElseThrow(() -> new IOException("assigned worker is no longer connected"));
        ProtocolFrame request = ProtocolFrame.request(
                MessageType.EXECUTE_TASK,
                executionCodec.encode(new TaskExecutionMessage(taskId, specification)));
        executionRequestedTaskIds.add(taskId);
        try {
            connection.send(request);
        } catch (IOException exception) {
            executionRequestedTaskIds.remove(taskId);
            workerRegistry.releaseTask(task);
            acceptedTaskIds.remove(taskId);
            task.markFailed(clock.instant(), "Could not request task execution");
            throw exception;
        }
    }

    public synchronized void finishTask(
            ServerConnection connection,
            ProtocolFrame frame) throws IOException {
        UUID taskId = taskCodec.decodeTaskReference(frame.getPayload());
        Task task = assignedTask(connection, taskId);
        requireAccepted(taskId);
        task.markDone(clock.instant());
        completeAssignment(task);
        dispatchAvailableTasks();
    }

    public synchronized void failTask(
            ServerConnection connection,
            ProtocolFrame frame) throws IOException {
        TaskFailureMessage failure = taskCodec.decodeFailure(frame.getPayload());
        Task task = assignedTask(connection, failure.getTaskId());
        requireAccepted(task.getId());
        task.markFailed(clock.instant(), failure.getReason());
        completeAssignment(task);
        dispatchAvailableTasks();
    }

    public synchronized UUID sendFile(
            UUID taskId,
            Path source,
            TransferFileKind kind,
            String remoteFileName) throws IOException {
        Objects.requireNonNull(taskId, "task id must not be null");
        Objects.requireNonNull(source, "source path must not be null");
        Objects.requireNonNull(kind, "file kind must not be null");
        if (kind != TransferFileKind.JOB_ARCHIVE && kind != TransferFileKind.INPUT) {
            throw new IllegalArgumentException(
                    "server can send only a job archive or an input file to a worker");
        }
        Task task = tasks.get(taskId);
        if (task == null) {
            throw new IllegalArgumentException("task is not known to the scheduler");
        }
        if (!acceptedTaskIds.contains(taskId)) {
            throw new IllegalStateException("worker must accept the task before file transfer");
        }
        UUID workerId = task.getAssignedWorkerId().orElseThrow();
        ServerConnection connection = workerRegistry.findConnection(workerId)
                .orElseThrow(() -> new IOException("assigned worker is no longer connected"));
        FileTransferMetadata metadata = new FileTransferMetadata(
                taskId, kind, remoteFileName, Files.size(source));
        UUID transferId = UUID.randomUUID();
        pendingFileTransfers.put(transferId, task);
        try {
            fileSender.send(transferId, source, metadata, connection::send);
        } catch (IOException | RuntimeException exception) {
            pendingFileTransfers.remove(transferId);
            throw exception;
        }
        return transferId;
    }

    public synchronized void acknowledgeFile(
            ServerConnection connection,
            ProtocolFrame frame) throws ProtocolException {
        fileCodec.verifyAcknowledgement(frame, frame.getRequestId());
        Task task = pendingFileTransfers.get(frame.getRequestId());
        if (task == null) {
            throw new ProtocolException(
                    ProtocolError.CORRELATION_MISMATCH,
                    "File acknowledgement does not match a pending transfer");
        }
        UUID workerId = workerRegistry.findWorkerId(connection).orElseThrow(() ->
                new ProtocolException(
                        ProtocolError.INVALID_MESSAGE_PAYLOAD,
                        "Unregistered worker cannot acknowledge a file"));
        if (!task.getAssignedWorkerId().filter(workerId::equals).isPresent()) {
            throw new ProtocolException(
                    ProtocolError.CORRELATION_MISMATCH,
                    "File was sent to a different worker");
        }
        pendingFileTransfers.remove(frame.getRequestId());
        acknowledgedFileTransfers.add(frame.getRequestId());
    }

    public synchronized int getReadyTaskCount() {
        return readyTasks.size();
    }

    public synchronized int getPendingAcceptanceCount() {
        return pendingAcceptances.size();
    }

    public synchronized boolean isAccepted(UUID taskId) {
        Objects.requireNonNull(taskId, "task id must not be null");
        return acceptedTaskIds.contains(taskId);
    }

    public synchronized boolean isFileAcknowledged(UUID transferId) {
        Objects.requireNonNull(transferId, "transfer id must not be null");
        return acknowledgedFileTransfers.contains(transferId);
    }

    private void dispatchAvailableTasks() throws IOException {
        while (!readyTasks.isEmpty()) {
            Task task = readyTasks.peekFirst();
            Optional<ServerConnection> target = workerRegistry.tryAssign(task);
            if (target.isEmpty()) {
                return;
            }
            readyTasks.removeFirst();
            sendAssignment(target.get(), task);
        }
    }

    private void sendAssignment(ServerConnection connection, Task task) throws IOException {
        TaskAssignmentMessage assignment = new TaskAssignmentMessage(
                task.getId(),
                task.getJobId(),
                task.getType(),
                task.getAttemptNumber());
        ProtocolFrame request = ProtocolFrame.request(
                MessageType.ASSIGN_TASK,
                taskCodec.encodeAssignment(assignment));
        pendingAcceptances.put(request.getRequestId(), task);
        try {
            connection.send(request);
        } catch (IOException exception) {
            pendingAcceptances.remove(request.getRequestId());
            workerRegistry.releaseTask(task);
            task.markFailed(clock.instant(), "Could not send task to worker");
            throw exception;
        }
    }

    private void removePendingAcceptance(UUID taskId) {
        pendingAcceptances.entrySet().removeIf(entry -> entry.getValue().getId().equals(taskId));
    }

    private Task assignedTask(ServerConnection connection, UUID taskId)
            throws ProtocolException {
        Task task = tasks.get(taskId);
        if (task == null) {
            throw new ProtocolException(
                    ProtocolError.INVALID_MESSAGE_PAYLOAD,
                    "Worker reported an unknown task");
        }
        UUID workerId = workerRegistry.findWorkerId(connection).orElseThrow(() ->
                new ProtocolException(
                        ProtocolError.INVALID_MESSAGE_PAYLOAD,
                        "Unregistered worker cannot report task status"));
        if (!task.getAssignedWorkerId().filter(workerId::equals).isPresent()) {
            throw new ProtocolException(
                    ProtocolError.CORRELATION_MISMATCH,
                    "Task is assigned to a different worker");
        }
        return task;
    }

    private void requireAccepted(UUID taskId) throws ProtocolException {
        if (!acceptedTaskIds.contains(taskId)) {
            throw new ProtocolException(
                    ProtocolError.INVALID_MESSAGE_PAYLOAD,
                    "Worker must accept the task before reporting its status");
        }
    }

    private void completeAssignment(Task task) {
        workerRegistry.releaseTask(task);
        acceptedTaskIds.remove(task.getId());
        executionRequestedTaskIds.remove(task.getId());
        pendingFileTransfers.entrySet().removeIf(
                entry -> entry.getValue().getId().equals(task.getId()));
    }
}
