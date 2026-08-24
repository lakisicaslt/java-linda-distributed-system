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
    }

    @Test
    void recordsSuccessfulExecutionTimeline() {
        Job job = newJob();
        Instant scheduledAt = CREATED_AT.plusSeconds(5);
        Instant startedAt = CREATED_AT.plusSeconds(10);
        Instant finishedAt = CREATED_AT.plusSeconds(30);

        job.schedule(scheduledAt);
        job.markRunning(startedAt);
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
        job.schedule(CREATED_AT.plusSeconds(1));
        job.markRunning(CREATED_AT.plusSeconds(2));
        job.markFailed(CREATED_AT.plusSeconds(3), "Worker connection lost");

        assertEquals(JobStatus.FAILED, job.getStatus());
        assertEquals("Worker connection lost", job.getFailureReason());

        job.retry(CREATED_AT.plusSeconds(4));

        assertEquals(JobStatus.READY, job.getStatus());
        assertNull(job.getScheduledAt());
        assertNull(job.getStartedAt());
        assertNull(job.getFinishedAt());
        assertNull(job.getFailureReason());
    }

    @Test
    void rejectsBlankFailureReason() {
        Job job = newJob();
        job.schedule(CREATED_AT.plusSeconds(1));
        job.markRunning(CREATED_AT.plusSeconds(2));

        assertThrows(IllegalArgumentException.class,
                () -> job.markFailed(CREATED_AT.plusSeconds(3), "   "));
        assertEquals(JobStatus.RUNNING, job.getStatus());
    }

    @Test
    void rejectsTimeThatMovesBackwards() {
        Job job = newJob();
        job.schedule(CREATED_AT.plusSeconds(10));

        assertThrows(IllegalArgumentException.class,
                () -> job.markRunning(CREATED_AT.plusSeconds(5)));
        assertEquals(JobStatus.SCHEDULED, job.getStatus());
    }

    @Test
    void completedJobCannotBeRetried() {
        Job job = newJob();
        job.schedule(CREATED_AT.plusSeconds(1));
        job.markRunning(CREATED_AT.plusSeconds(2));
        job.markDone(CREATED_AT.plusSeconds(3));

        assertThrows(IllegalStateException.class,
                () -> job.retry(CREATED_AT.plusSeconds(4)));
        assertEquals(JobStatus.DONE, job.getStatus());
    }

    private static Job newJob() {
        return new Job(UUID.randomUUID(), UUID.randomUUID(), validSpecification(), CREATED_AT);
    }

    private static JobSpecification validSpecification() {
        return new JobSpecification(
                "Test job",
                "test-job.jar",
                List.of(),
                List.of(),
                List.of(),
                List.of());
    }
}
