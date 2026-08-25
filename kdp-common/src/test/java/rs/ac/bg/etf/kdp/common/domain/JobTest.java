package rs.ac.bg.etf.kdp.common.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JobTest {

    private static final Instant CREATED_AT = Instant.parse("2026-08-16T10:00:00Z");

    @Test
    void newJobStartsReady() {
        UUID jobId = UUID.randomUUID();
        UUID batchId = UUID.randomUUID();
        JobSpecification specification = validSpecification();

        Job job = new Job(jobId, batchId, specification, CREATED_AT);

        assertEquals(jobId, job.getId());
        assertEquals(batchId, job.getBatchId());
        assertSame(specification, job.getSpecification());
        assertEquals(JobStatus.READY, job.getStatus());
        assertEquals(CREATED_AT, job.getCreatedAt());
        assertEquals(CREATED_AT, job.getLastStatusChangedAt());
        assertNull(job.getScheduledAt());
        assertNull(job.getStartedAt());
        assertNull(job.getFinishedAt());
        assertNull(job.getFailureReason());
        assertEquals(1, job.getTasks().size());
        assertEquals(TaskType.ROOT, job.getRootTask().getType());
        assertEquals(jobId, job.getRootTask().getJobId());
    }

    @Test
    void recordsSuccessfulExecutionTimeline() {
        Job job = newJob();
        Instant scheduledAt = CREATED_AT.plusSeconds(5);
        Instant startedAt = CREATED_AT.plusSeconds(10);
        Instant finishedAt = CREATED_AT.plusSeconds(30);

        job.getRootTask().assignTo(UUID.randomUUID(), scheduledAt);
        job.schedule(scheduledAt);
        job.getRootTask().markRunning(startedAt);
        job.markRunning(startedAt);
        job.getRootTask().markDone(finishedAt);
        job.markDone(finishedAt);

        assertEquals(JobStatus.DONE, job.getStatus());
        assertEquals(scheduledAt, job.getScheduledAt());
        assertEquals(startedAt, job.getStartedAt());
        assertEquals(finishedAt, job.getFinishedAt());
        assertEquals(finishedAt, job.getLastStatusChangedAt());
        assertNull(job.getFailureReason());
    }

    @Test
    void rejectsSkippedExecutionStep() {
        Job job = newJob();

        assertThrows(IllegalStateException.class,
                () -> job.markRunning(CREATED_AT.plusSeconds(1)));
        assertEquals(JobStatus.READY, job.getStatus());
    }

    @Test
    void failedJobCanBeRetriedAndClearsPreviousExecutionDetails() {
        Job job = newJob();
        Task rootTask = job.getRootTask();
        rootTask.assignTo(UUID.randomUUID(), CREATED_AT.plusSeconds(1));
        job.schedule(CREATED_AT.plusSeconds(1));
        rootTask.markRunning(CREATED_AT.plusSeconds(2));
        job.markRunning(CREATED_AT.plusSeconds(2));
        rootTask.markFailed(CREATED_AT.plusSeconds(3), "Worker connection lost");
        job.markFailed(CREATED_AT.plusSeconds(3), "Worker connection lost");

        assertEquals(JobStatus.FAILED, job.getStatus());
        assertEquals("Worker connection lost", job.getFailureReason());

        job.retry(CREATED_AT.plusSeconds(4));

        assertEquals(JobStatus.READY, job.getStatus());
        assertNull(job.getScheduledAt());
        assertNull(job.getStartedAt());
        assertNull(job.getFinishedAt());
        assertNull(job.getFailureReason());
        assertEquals(TaskStatus.READY, rootTask.getStatus());
    }

    @Test
    void retryKeepsJobRunningWhileAnotherTaskIsStillActive() {
        Job job = newJob();
        Task root = job.getRootTask();
        Instant scheduledAt = CREATED_AT.plusSeconds(1);
        Instant startedAt = CREATED_AT.plusSeconds(2);
        root.assignTo(UUID.randomUUID(), scheduledAt);
        job.schedule(scheduledAt);
        root.markRunning(startedAt);
        job.markRunning(startedAt);
        Task eval = Task.eval(
                UUID.randomUUID(), job.getId(), root.getId(), "Eval", startedAt);
        job.addEvalTask(eval);
        eval.assignTo(UUID.randomUUID(), CREATED_AT.plusSeconds(3));
        eval.markRunning(CREATED_AT.plusSeconds(4));
        eval.markFailed(CREATED_AT.plusSeconds(5), "Worker disconnected");
        job.markFailed(CREATED_AT.plusSeconds(5), "Worker disconnected");

        job.retry(CREATED_AT.plusSeconds(6));

        assertEquals(JobStatus.RUNNING, job.getStatus());
        assertEquals(TaskStatus.RUNNING, root.getStatus());
        assertEquals(TaskStatus.READY, eval.getStatus());
        assertEquals(scheduledAt, job.getScheduledAt());
        assertEquals(startedAt, job.getStartedAt());
        assertNull(job.getFinishedAt());
        assertNull(job.getFailureReason());
    }

    @Test
    void rejectsBlankFailureReason() {
        Job job = newJob();
        job.getRootTask().assignTo(UUID.randomUUID(), CREATED_AT.plusSeconds(1));
        job.schedule(CREATED_AT.plusSeconds(1));
        job.getRootTask().markRunning(CREATED_AT.plusSeconds(2));
        job.markRunning(CREATED_AT.plusSeconds(2));

        assertThrows(IllegalArgumentException.class,
                () -> job.markFailed(CREATED_AT.plusSeconds(3), "   "));
        assertEquals(JobStatus.RUNNING, job.getStatus());
    }

    @Test
    void rejectsTimeThatMovesBackwards() {
        Job job = newJob();
        job.getRootTask().assignTo(UUID.randomUUID(), CREATED_AT.plusSeconds(10));
        job.schedule(CREATED_AT.plusSeconds(10));

        assertThrows(IllegalArgumentException.class,
                () -> job.markRunning(CREATED_AT.plusSeconds(5)));
        assertEquals(JobStatus.SCHEDULED, job.getStatus());
    }

    @Test
    void completedJobCannotBeRetried() {
        Job job = newJob();
        job.getRootTask().assignTo(UUID.randomUUID(), CREATED_AT.plusSeconds(1));
        job.schedule(CREATED_AT.plusSeconds(1));
        job.getRootTask().markRunning(CREATED_AT.plusSeconds(2));
        job.markRunning(CREATED_AT.plusSeconds(2));
        job.getRootTask().markDone(CREATED_AT.plusSeconds(3));
        job.markDone(CREATED_AT.plusSeconds(3));

        assertThrows(IllegalStateException.class,
                () -> job.retry(CREATED_AT.plusSeconds(4)));
        assertEquals(JobStatus.DONE, job.getStatus());
    }

    @Test
    void acceptsEvalTaskOnlyWhenItsParentBelongsToJob() {
        Job job = newJob();
        Task root = job.getRootTask();
        root.assignTo(UUID.randomUUID(), CREATED_AT.plusSeconds(1));
        job.schedule(CREATED_AT.plusSeconds(1));
        root.markRunning(CREATED_AT.plusSeconds(2));
        job.markRunning(CREATED_AT.plusSeconds(2));
        Task evalTask = Task.eval(
                UUID.randomUUID(),
                job.getId(),
                root.getId(),
                "Partial calculation",
                CREATED_AT.plusSeconds(2));

        job.addEvalTask(evalTask);

        assertEquals(2, job.getTasks().size());
        assertSame(evalTask, job.findTask(evalTask.getId()).orElseThrow());

        Task unknownParent = Task.eval(
                UUID.randomUUID(),
                job.getId(),
                UUID.randomUUID(),
                "Invalid eval",
                CREATED_AT.plusSeconds(2));
        assertThrows(IllegalArgumentException.class, () -> job.addEvalTask(unknownParent));
    }

    @Test
    void cannotFinishUntilRootAndAllEvalTasksAreDone() {
        Job job = newJob();
        Task root = job.getRootTask();
        root.assignTo(UUID.randomUUID(), CREATED_AT.plusSeconds(1));
        job.schedule(CREATED_AT.plusSeconds(1));
        root.markRunning(CREATED_AT.plusSeconds(2));
        job.markRunning(CREATED_AT.plusSeconds(2));
        Task eval = Task.eval(
                UUID.randomUUID(), job.getId(), root.getId(), "Eval", CREATED_AT.plusSeconds(2));
        job.addEvalTask(eval);
        root.markDone(CREATED_AT.plusSeconds(3));

        assertThrows(IllegalStateException.class,
                () -> job.markDone(CREATED_AT.plusSeconds(4)));

        eval.assignTo(UUID.randomUUID(), CREATED_AT.plusSeconds(4));
        eval.markRunning(CREATED_AT.plusSeconds(5));
        eval.markDone(CREATED_AT.plusSeconds(6));
        job.markDone(CREATED_AT.plusSeconds(6));

        assertEquals(JobStatus.DONE, job.getStatus());
    }

    @Test
    void abortCascadesToUnfinishedTasks() {
        Job job = newJob();
        Task root = job.getRootTask();
        root.assignTo(UUID.randomUUID(), CREATED_AT.plusSeconds(1));
        job.schedule(CREATED_AT.plusSeconds(1));
        root.markRunning(CREATED_AT.plusSeconds(2));
        job.markRunning(CREATED_AT.plusSeconds(2));
        Task eval = Task.eval(
                UUID.randomUUID(),
                job.getId(),
                root.getId(),
                "Eval",
                CREATED_AT.plusSeconds(2));
        job.addEvalTask(eval);

        job.abort(CREATED_AT.plusSeconds(3));

        assertEquals(JobStatus.ABORTED, job.getStatus());
        assertEquals(TaskStatus.ABORTED, job.getRootTask().getStatus());
        assertEquals(TaskStatus.ABORTED, eval.getStatus());
    }

    @Test
    void failedJobCanBeAbortedAfterWorkerFailure() {
        Job job = newJob();
        Task root = job.getRootTask();
        root.assignTo(UUID.randomUUID(), CREATED_AT.plusSeconds(1));
        job.schedule(CREATED_AT.plusSeconds(1));
        root.markRunning(CREATED_AT.plusSeconds(2));
        job.markRunning(CREATED_AT.plusSeconds(2));
        root.markFailed(CREATED_AT.plusSeconds(3), "Worker disconnected");
        job.markFailed(CREATED_AT.plusSeconds(3), "Worker disconnected");

        job.abort(CREATED_AT.plusSeconds(4));

        assertEquals(JobStatus.ABORTED, job.getStatus());
        assertEquals(TaskStatus.ABORTED, root.getStatus());
        assertEquals(CREATED_AT.plusSeconds(4), job.getFinishedAt());
        assertNull(job.getFailureReason());
        assertNull(root.getFailureReason());
    }

    private static Job newJob() {
        return new Job(UUID.randomUUID(), UUID.randomUUID(), validSpecification(), CREATED_AT);
    }

    private static JobSpecification validSpecification() {
        return new JobSpecification(
                "test-job.jar",
                List.of(),
                List.of(),
                List.of(),
                List.of());
    }
}
