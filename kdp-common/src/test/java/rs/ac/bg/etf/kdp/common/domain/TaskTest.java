package rs.ac.bg.etf.kdp.common.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TaskTest {

    private static final Instant CREATED_AT = Instant.parse("2026-08-24T12:00:00Z");

    @Test
    void createsRootAndEvalTasksWithCorrectParentRelationship() {
        UUID jobId = UUID.randomUUID();
        Task root = Task.root(UUID.randomUUID(), jobId, "Main program", CREATED_AT);
        Task eval = Task.eval(
                UUID.randomUUID(), jobId, root.getId(), "Partial calculation", CREATED_AT);

        assertEquals(TaskType.ROOT, root.getType());
        assertFalse(root.getParentTaskId().isPresent());
        assertEquals(TaskType.EVAL, eval.getType());
        assertEquals(root.getId(), eval.getParentTaskId().orElseThrow());
        assertThrows(NullPointerException.class,
                () -> Task.eval(UUID.randomUUID(), jobId, null, "Invalid", CREATED_AT));
    }

    @Test
    void recordsSuccessfulAssignmentAndExecution() {
        Task task = newRootTask();
        UUID workerId = UUID.randomUUID();

        task.assignTo(workerId, CREATED_AT.plusSeconds(1));
        task.markRunning(CREATED_AT.plusSeconds(2));
        task.markDone(CREATED_AT.plusSeconds(3));

        assertEquals(TaskStatus.DONE, task.getStatus());
        assertEquals(workerId, task.getAssignedWorkerId().orElseThrow());
        assertEquals(1, task.getAttemptNumber());
        assertEquals(CREATED_AT.plusSeconds(1), task.getScheduledAt());
        assertEquals(CREATED_AT.plusSeconds(2), task.getStartedAt());
        assertEquals(CREATED_AT.plusSeconds(3), task.getFinishedAt());
    }

    @Test
    void failedTaskCanBeRetriedOnAnotherWorker() {
        Task task = newRootTask();
        UUID firstWorker = UUID.randomUUID();
        UUID secondWorker = UUID.randomUUID();

        task.assignTo(firstWorker, CREATED_AT.plusSeconds(1));
        task.markRunning(CREATED_AT.plusSeconds(2));
        task.markFailed(CREATED_AT.plusSeconds(3), "Worker disconnected");
        task.retry(CREATED_AT.plusSeconds(4));

        assertEquals(TaskStatus.READY, task.getStatus());
        assertTrue(task.getAssignedWorkerId().isEmpty());
        assertEquals(1, task.getAttemptNumber());

        task.assignTo(secondWorker, CREATED_AT.plusSeconds(5));

        assertEquals(TaskStatus.SCHEDULED, task.getStatus());
        assertEquals(secondWorker, task.getAssignedWorkerId().orElseThrow());
        assertEquals(2, task.getAttemptNumber());
    }

    @Test
    void cannotRunBeforeWorkerAssignment() {
        Task task = newRootTask();

        assertThrows(IllegalStateException.class,
                () -> task.markRunning(CREATED_AT.plusSeconds(1)));
        assertEquals(TaskStatus.READY, task.getStatus());
    }

    @Test
    void completedTaskCannotBeAssignedAgain() {
        Task task = newRootTask();
        task.assignTo(UUID.randomUUID(), CREATED_AT.plusSeconds(1));
        task.markRunning(CREATED_AT.plusSeconds(2));
        task.markDone(CREATED_AT.plusSeconds(3));

        assertThrows(IllegalStateException.class,
                () -> task.assignTo(UUID.randomUUID(), CREATED_AT.plusSeconds(4)));
    }

    private static Task newRootTask() {
        return Task.root(
                UUID.randomUUID(), UUID.randomUUID(), "Main program", CREATED_AT);
    }
}
