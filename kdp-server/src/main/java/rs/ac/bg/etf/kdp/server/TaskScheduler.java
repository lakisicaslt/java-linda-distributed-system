package rs.ac.bg.etf.kdp.server;

import rs.ac.bg.etf.kdp.common.domain.Task;
import rs.ac.bg.etf.kdp.common.domain.TaskStatus;
import rs.ac.bg.etf.kdp.common.protocol.MessageType;
import rs.ac.bg.etf.kdp.common.protocol.ProtocolError;
import rs.ac.bg.etf.kdp.common.protocol.ProtocolException;
import rs.ac.bg.etf.kdp.common.protocol.ProtocolFrame;
import rs.ac.bg.etf.kdp.common.protocol.TaskAssignmentMessage;
import rs.ac.bg.etf.kdp.common.protocol.TaskProtocolCodec;

import java.io.IOException;
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
    private final ArrayDeque<Task> readyTasks = new ArrayDeque<>();
    private final Map<UUID, Task> tasks = new LinkedHashMap<>();
    private final Map<UUID, Task> pendingAcceptances = new HashMap<>();
    private final Set<UUID> acceptedTaskIds = new HashSet<>();

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
            if (belongsToWorker && task.getStatus() == TaskStatus.SCHEDULED) {
                removePendingAcceptance(task.getId());
                acceptedTaskIds.remove(task.getId());
                workerRegistry.releaseTask(task);
                task.returnToReady(clock.instant());
                readyTasks.addLast(task);
            }
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
            task.returnToReady(clock.instant());
            readyTasks.addFirst(task);
            throw exception;
        }
    }

    private void removePendingAcceptance(UUID taskId) {
        pendingAcceptances.entrySet().removeIf(entry -> entry.getValue().getId().equals(taskId));
    }
}
