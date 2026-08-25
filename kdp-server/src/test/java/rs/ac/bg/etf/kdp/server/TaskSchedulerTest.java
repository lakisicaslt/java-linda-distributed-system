package rs.ac.bg.etf.kdp.server;

import org.junit.jupiter.api.Test;
import rs.ac.bg.etf.kdp.common.domain.Task;
import rs.ac.bg.etf.kdp.common.domain.TaskStatus;
import rs.ac.bg.etf.kdp.common.protocol.ConnectionRole;
import rs.ac.bg.etf.kdp.common.protocol.MessageType;
import rs.ac.bg.etf.kdp.common.protocol.ProtocolError;
import rs.ac.bg.etf.kdp.common.protocol.ProtocolException;
import rs.ac.bg.etf.kdp.common.protocol.ProtocolFrame;
import rs.ac.bg.etf.kdp.common.protocol.ProtocolFrameCodec;
import rs.ac.bg.etf.kdp.common.protocol.TaskAssignmentMessage;
import rs.ac.bg.etf.kdp.common.protocol.TaskProtocolCodec;
import rs.ac.bg.etf.kdp.common.protocol.WorkerRegistrationMessage;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TaskSchedulerTest {

    private static final Instant NOW = Instant.parse("2026-08-25T12:00:00Z");
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private final ProtocolFrameCodec frameCodec = new ProtocolFrameCodec();
    private final TaskProtocolCodec taskCodec = new TaskProtocolCodec();

    @Test
    void keepsTaskReadyUntilAWorkerBecomesAvailable() throws Exception {
        WorkerRegistry registry = new WorkerRegistry(clock);
        TaskScheduler scheduler = new TaskScheduler(registry, clock);
        Task task = newTask();

        scheduler.submit(task);

        assertEquals(TaskStatus.READY, task.getStatus());
        assertEquals(1, scheduler.getReadyTaskCount());
        assertEquals(0, scheduler.getPendingAcceptanceCount());
    }

    @Test
    void assignsTaskAndRecordsCorrelatedAcceptance() throws Exception {
        WorkerRegistry registry = new WorkerRegistry(clock);
        TaskScheduler scheduler = new TaskScheduler(registry, clock);
        ConnectionOutput target = registeredWorker(registry, 2);
        Task task = newTask();

        scheduler.submit(task);

        ProtocolFrame request = target.readFrame();
        TaskAssignmentMessage assignment = taskCodec.decodeAssignment(request.getPayload());
        assertEquals(MessageType.ASSIGN_TASK, request.getType());
        assertEquals(task.getId(), assignment.getTaskId());
        assertEquals(1, assignment.getAttemptNumber());
        assertEquals(TaskStatus.SCHEDULED, task.getStatus());
        assertEquals(1, scheduler.getPendingAcceptanceCount());

        ProtocolFrame accepted = request.reply(
                MessageType.TASK_ACCEPTED,
                taskCodec.encodeTaskReference(task.getId()));
        scheduler.accept(target.connection, accepted);

        assertEquals(0, scheduler.getPendingAcceptanceCount());
        assertTrue(scheduler.isAccepted(task.getId()));
        assertTrue(registry.find(task.getAssignedWorkerId().orElseThrow())
                .orElseThrow().getActiveTaskIds().contains(task.getId()));
    }

    @Test
    void rejectsAcceptanceWithWrongRequestOrTaskId() throws Exception {
        WorkerRegistry registry = new WorkerRegistry(clock);
        TaskScheduler scheduler = new TaskScheduler(registry, clock);
        ConnectionOutput target = registeredWorker(registry, 1);
        Task task = newTask();
        scheduler.submit(task);
        ProtocolFrame request = target.readFrame();

        ProtocolFrame wrongRequest = ProtocolFrame.request(
                MessageType.TASK_ACCEPTED,
                taskCodec.encodeTaskReference(task.getId()));
        ProtocolFrame wrongTask = request.reply(
                MessageType.TASK_ACCEPTED,
                taskCodec.encodeTaskReference(UUID.randomUUID()));

        assertProtocolError(
                ProtocolError.CORRELATION_MISMATCH,
                () -> scheduler.accept(target.connection, wrongRequest));
        assertProtocolError(
                ProtocolError.CORRELATION_MISMATCH,
                () -> scheduler.accept(target.connection, wrongTask));
        assertEquals(1, scheduler.getPendingAcceptanceCount());
    }

    @Test
    void failedWorkerRequiresExplicitRetryDecision() throws Exception {
        WorkerRegistry registry = new WorkerRegistry(clock);
        TaskScheduler scheduler = new TaskScheduler(registry, clock);
        ConnectionOutput target = registeredWorker(registry, 1);
        Task task = newTask();
        scheduler.submit(task);

        scheduler.workerDisconnected(target.connection);
        registry.disconnect(target.connection);

        assertEquals(TaskStatus.FAILED, task.getStatus());
        assertEquals("Worker disconnected", task.getFailureReason());
        assertEquals(0, scheduler.getReadyTaskCount());
        assertEquals(0, scheduler.getPendingAcceptanceCount());

        scheduler.retryTask(task.getId());

        assertEquals(TaskStatus.READY, task.getStatus());
        assertFalse(task.getAssignedWorkerId().isPresent());
        assertEquals(1, scheduler.getReadyTaskCount());
    }

    @Test
    void failedAssignmentIsNotAutomaticallyQueuedAgain() {
        WorkerRegistry registry = new WorkerRegistry(clock);
        TaskScheduler scheduler = new TaskScheduler(registry, clock);
        ServerConnection connection = new ServerConnection(
                new Socket(), ConnectionRole.WORKER, frameCodec, failingOutput());
        registry.register(connection, new WorkerRegistrationMessage(
                UUID.randomUUID(), "worker-host", "Windows", "21", 1));
        Task task = newTask();

        assertThrows(IOException.class, () -> scheduler.submit(task));

        assertEquals(TaskStatus.FAILED, task.getStatus());
        assertEquals("Could not send task to worker", task.getFailureReason());
        assertEquals(0, scheduler.getReadyTaskCount());
        assertEquals(0, scheduler.getPendingAcceptanceCount());
    }

    @Test
    void doesNotAssignMoreTasksThanWorkerCapacity() throws Exception {
        WorkerRegistry registry = new WorkerRegistry(clock);
        TaskScheduler scheduler = new TaskScheduler(registry, clock);
        registeredWorker(registry, 1);
        Task first = newTask();
        Task second = newTask();

        scheduler.submit(first);
        scheduler.submit(second);

        assertEquals(TaskStatus.SCHEDULED, first.getStatus());
        assertEquals(TaskStatus.READY, second.getStatus());
        assertEquals(1, scheduler.getReadyTaskCount());
        assertEquals(1, scheduler.getPendingAcceptanceCount());
    }

    private ConnectionOutput registeredWorker(WorkerRegistry registry, int capacity) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ServerConnection connection = new ServerConnection(
                new Socket(), ConnectionRole.WORKER, frameCodec, output);
        registry.register(connection, new WorkerRegistrationMessage(
                UUID.randomUUID(), "worker-host", "Windows", "21", capacity));
        return new ConnectionOutput(connection, output);
    }

    private static Task newTask() {
        return Task.root(UUID.randomUUID(), UUID.randomUUID(), "Main task", NOW);
    }

    private static OutputStream failingOutput() {
        return new OutputStream() {
            @Override
            public void write(int value) throws IOException {
                throw new IOException("connection lost");
            }
        };
    }

    private static void assertProtocolError(
            ProtocolError expected,
            ThrowingOperation operation) {
        ProtocolException exception = assertThrows(ProtocolException.class, operation::run);
        assertEquals(expected, exception.getError());
    }

    private final class ConnectionOutput {

        private final ServerConnection connection;
        private final ByteArrayOutputStream output;

        private ConnectionOutput(ServerConnection connection, ByteArrayOutputStream output) {
            this.connection = connection;
            this.output = output;
        }

        private ProtocolFrame readFrame() throws Exception {
            return frameCodec.read(new ByteArrayInputStream(output.toByteArray()));
        }
    }

    @FunctionalInterface
    private interface ThrowingOperation {
        void run() throws ProtocolException;
    }
}
