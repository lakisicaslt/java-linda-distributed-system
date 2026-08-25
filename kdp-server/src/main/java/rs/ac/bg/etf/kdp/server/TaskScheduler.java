package rs.ac.bg.etf.kdp.server;

import rs.ac.bg.etf.kdp.common.domain.Job;
import rs.ac.bg.etf.kdp.common.domain.JobSpecification;
import rs.ac.bg.etf.kdp.common.domain.JobStatus;
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
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
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
    private final Map<UUID, JobSpecification> executionSpecifications = new HashMap<>();
    private final Map<UUID, Job> jobsByTaskId = new HashMap<>();
    private final Map<UUID, PreparedExecution> preparedExecutions = new HashMap<>();
    private final Set<UUID> preparedTransfersStarted = new HashSet<>();
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

    public synchronized void submit(
            Job job,
            Path archive,
            List<Path> inputFiles) throws IOException {
        Objects.requireNonNull(job, "job must not be null");
        if (job.getStatus() != JobStatus.READY) {
            throw new IllegalArgumentException("only a ready job can be submitted");
        }

        Task rootTask = job.getRootTask();
        PreparedExecution prepared = new PreparedExecution(
                job.getSpecification(), archive, inputFiles);
        if (tasks.containsKey(rootTask.getId())
                || jobsByTaskId.containsKey(rootTask.getId())
                || preparedExecutions.containsKey(rootTask.getId())) {
            throw new IllegalArgumentException("job is already known to the scheduler");
        }
        jobsByTaskId.put(rootTask.getId(), job);
        preparedExecutions.put(rootTask.getId(), prepared);
        try {
            submit(rootTask);
        } catch (IOException | RuntimeException exception) {
            if (!tasks.containsKey(rootTask.getId())) {
                jobsByTaskId.remove(rootTask.getId());
                preparedExecutions.remove(rootTask.getId());
            }
            throw exception;
        }
    }

    public synchronized void workerAvailable() throws IOException {
        dispatchAvailableTasks();
    }

    public synchronized UUID accept(ServerConnection connection, ProtocolFrame frame)
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
        return taskId;
    }

    public synchronized void startPreparedExecution(UUID taskId) throws IOException {
        Objects.requireNonNull(taskId, "task id must not be null");
        PreparedExecution prepared = preparedExecutions.get(taskId);
        if (prepared == null) {
            return;
        }
        if (!acceptedTaskIds.contains(taskId)) {
            throw new IllegalStateException("worker must accept the task before file transfer");
        }
        if (!preparedTransfersStarted.add(taskId)) {
            throw new IllegalStateException("prepared task files are already being transferred");
        }

        try {
            sendFile(
                    taskId,
                    prepared.archive,
                    TransferFileKind.JOB_ARCHIVE,
                    prepared.specification.getJarFileName());
            for (String inputFileName : prepared.specification.getInputFileNames()) {
                sendFile(
                        taskId,
                        prepared.inputFiles.get(inputFileName),
                        TransferFileKind.INPUT,
                        inputFileName);
            }
        } catch (IOException | RuntimeException exception) {
            preparedTransfersStarted.remove(taskId);
            throw exception;
        }
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
                executionSpecifications.remove(task.getId());
                preparedTransfersStarted.remove(task.getId());
                workerRegistry.releaseTask(task);
                Instant occurredAt = clock.instant();
                task.markFailed(occurredAt, "Worker disconnected");
                markJobFailed(task, occurredAt, "Worker disconnected");
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
        Instant occurredAt = clock.instant();
        Job job = jobsByTaskId.get(taskId);
        if (job != null) {
            job.retry(occurredAt);
        }
        task.retry(occurredAt);
        preparedTransfersStarted.remove(taskId);
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
        Instant occurredAt = clock.instant();
        task.markRunning(occurredAt);
        markJobRunning(task, occurredAt);
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
        executionSpecifications.put(taskId, specification);
        try {
            connection.send(request);
        } catch (IOException exception) {
            executionRequestedTaskIds.remove(taskId);
            executionSpecifications.remove(taskId);
            workerRegistry.releaseTask(task);
            acceptedTaskIds.remove(taskId);
            Instant occurredAt = clock.instant();
            task.markFailed(occurredAt, "Could not request task execution");
            markJobFailed(task, occurredAt, "Could not request task execution");
            throw exception;
        }
    }

    public synchronized void finishTask(
            ServerConnection connection,
            ProtocolFrame frame) throws IOException {
        UUID taskId = taskCodec.decodeTaskReference(frame.getPayload());
        Task task = assignedTask(connection, taskId);
        requireAccepted(taskId);
        Instant occurredAt = clock.instant();
        task.markDone(occurredAt);
        markJobDone(task, occurredAt);
        completeAssignment(task);
        dispatchAvailableTasks();
    }

    public synchronized void failTask(
            ServerConnection connection,
            ProtocolFrame frame) throws IOException {
        TaskFailureMessage failure = taskCodec.decodeFailure(frame.getPayload());
        Task task = assignedTask(connection, failure.getTaskId());
        requireAccepted(task.getId());
        Instant occurredAt = clock.instant();
        task.markFailed(occurredAt, failure.getReason());
        markJobFailed(task, occurredAt, failure.getReason());
        completeAssignment(task);
        dispatchAvailableTasks();
    }

    public synchronized void validateResultTransfer(
            ServerConnection connection,
            FileTransferMetadata metadata) throws ProtocolException {
        Objects.requireNonNull(metadata, "file metadata must not be null");
        Task task = assignedTask(connection, metadata.getOwnerId());
        requireAccepted(task.getId());
        if (task.getStatus() != TaskStatus.RUNNING
                || !executionRequestedTaskIds.contains(task.getId())) {
            throw new ProtocolException(
                    ProtocolError.INVALID_MESSAGE_PAYLOAD,
                    "Worker can send results only for a running task");
        }
        if (metadata.getKind() == TransferFileKind.OUTPUT) {
            JobSpecification specification = executionSpecifications.get(task.getId());
            if (specification == null
                    || !specification.getOutputFileNames().contains(metadata.getFileName())) {
                throw new ProtocolException(
                        ProtocolError.INVALID_MESSAGE_PAYLOAD,
                        "Worker sent an undeclared output file");
            }
        } else if (metadata.getKind() != TransferFileKind.STANDARD_OUTPUT
                && metadata.getKind() != TransferFileKind.STANDARD_ERROR) {
            throw new ProtocolException(
                    ProtocolError.INVALID_MESSAGE_PAYLOAD,
                    "Worker can send only task output files and captured streams");
        }
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
            ProtocolFrame frame) throws IOException {
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
        boolean taskHasPendingTransfers = pendingFileTransfers.values().stream()
                .anyMatch(pendingTask -> pendingTask.getId().equals(task.getId()));
        if (!taskHasPendingTransfers
                && preparedTransfersStarted.contains(task.getId())
                && !executionRequestedTaskIds.contains(task.getId())) {
            PreparedExecution prepared = preparedExecutions.get(task.getId());
            requestExecution(task.getId(), prepared.specification);
        }
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
            markJobScheduled(task, task.getScheduledAt());
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
            Instant occurredAt = clock.instant();
            task.markFailed(occurredAt, "Could not send task to worker");
            markJobFailed(task, occurredAt, "Could not send task to worker");
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
        executionSpecifications.remove(task.getId());
        preparedTransfersStarted.remove(task.getId());
        pendingFileTransfers.entrySet().removeIf(
                entry -> entry.getValue().getId().equals(task.getId()));
    }

    private void markJobScheduled(Task task, Instant occurredAt) {
        Job job = jobsByTaskId.get(task.getId());
        if (job != null && job.getStatus() == JobStatus.READY) {
            job.schedule(occurredAt);
        }
    }

    private void markJobRunning(Task task, Instant occurredAt) {
        Job job = jobsByTaskId.get(task.getId());
        if (job != null && job.getStatus() == JobStatus.SCHEDULED) {
            job.markRunning(occurredAt);
        }
    }

    private void markJobDone(Task task, Instant occurredAt) {
        Job job = jobsByTaskId.get(task.getId());
        if (job != null && job.getStatus() == JobStatus.RUNNING) {
            job.markDone(occurredAt);
        }
    }

    private void markJobFailed(Task task, Instant occurredAt, String reason) {
        Job job = jobsByTaskId.get(task.getId());
        if (job != null
                && (job.getStatus() == JobStatus.SCHEDULED
                || job.getStatus() == JobStatus.RUNNING)) {
            job.markFailed(occurredAt, reason);
        }
    }

    private static final class PreparedExecution {

        private final JobSpecification specification;
        private final Path archive;
        private final Map<String, Path> inputFiles;

        private PreparedExecution(
                JobSpecification specification,
                Path archive,
                List<Path> inputFiles) throws IOException {
            this.specification = Objects.requireNonNull(
                    specification, "job specification must not be null");
            this.archive = requireRegularFile(archive, "job archive");
            if (!this.archive.getFileName().toString().equals(specification.getJarFileName())) {
                throw new IllegalArgumentException(
                        "archive name must match the job specification");
            }

            Objects.requireNonNull(inputFiles, "input files must not be null");
            Map<String, Path> filesByName = new LinkedHashMap<>();
            for (Path inputFile : inputFiles) {
                Path validated = requireRegularFile(inputFile, "input file");
                String name = validated.getFileName().toString();
                if (filesByName.putIfAbsent(name, validated) != null) {
                    throw new IllegalArgumentException("input file names must be unique");
                }
            }
            if (!filesByName.keySet().equals(
                    new HashSet<>(specification.getInputFileNames()))) {
                throw new IllegalArgumentException(
                        "prepared inputs must match the job specification");
            }
            this.inputFiles = Map.copyOf(filesByName);
        }

        private static Path requireRegularFile(Path path, String fieldName) throws IOException {
            Objects.requireNonNull(path, fieldName + " must not be null");
            Path normalized = path.toAbsolutePath().normalize();
            if (!Files.isRegularFile(normalized)) {
                throw new IOException(fieldName + " does not exist: " + normalized);
            }
            return normalized;
        }
    }
}
