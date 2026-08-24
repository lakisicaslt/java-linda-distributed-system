package rs.ac.bg.etf.kdp.common.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JobBatchTest {

    private static final Instant CREATED_AT = Instant.parse("2026-08-24T10:00:00Z");

    @Test
    void newBatchIsInProgressAndFindsItsJobs() {
        UUID batchId = UUID.randomUUID();
        Job firstJob = newJob(batchId);
        Job secondJob = newJob(batchId);

        JobBatch batch = new JobBatch(
                batchId, "Public test batch", CREATED_AT, List.of(firstJob, secondJob));

        assertEquals(JobBatchStatus.IN_PROGRESS, batch.getStatus());
        assertEquals(2, batch.getJobs().size());
        assertTrue(batch.findJob(firstJob.getId()).isPresent());
        assertTrue(batch.findJob(UUID.randomUUID()).isEmpty());
    }

    @Test
    void batchIsDoneOnlyWhenEveryJobIsDone() {
        UUID batchId = UUID.randomUUID();
        Job firstJob = newJob(batchId);
        Job secondJob = newJob(batchId);
        JobBatch batch = new JobBatch(
                batchId, "Successful batch", CREATED_AT, List.of(firstJob, secondJob));

        complete(firstJob, 1);
        assertEquals(JobBatchStatus.IN_PROGRESS, batch.getStatus());

        complete(secondJob, 10);
        assertEquals(JobBatchStatus.DONE, batch.getStatus());
        assertTrue(batch.getStatus().isTerminal());
    }

    @Test
    void failedJobRequiresAttentionAfterOtherJobsFinish() {
        UUID batchId = UUID.randomUUID();
        Job failedJob = newJob(batchId);
        Job successfulJob = newJob(batchId);
        JobBatch batch = new JobBatch(
                batchId, "Retry batch", CREATED_AT, List.of(failedJob, successfulJob));

        fail(failedJob, 1);
        assertEquals(JobBatchStatus.IN_PROGRESS, batch.getStatus());

        complete(successfulJob, 10);
        assertEquals(JobBatchStatus.REQUIRES_ATTENTION, batch.getStatus());

        failedJob.retry(CREATED_AT.plusSeconds(20));
        assertEquals(JobBatchStatus.IN_PROGRESS, batch.getStatus());
    }

    @Test
    void batchIsAbortedWhenNoJobIsActiveAndAtLeastOneWasAborted() {
        UUID batchId = UUID.randomUUID();
        Job completedJob = newJob(batchId);
        Job abortedJob = newJob(batchId);
        JobBatch batch = new JobBatch(
                batchId, "Aborted batch", CREATED_AT, List.of(completedJob, abortedJob));

        complete(completedJob, 1);
        abortedJob.abort(CREATED_AT.plusSeconds(10));

        assertEquals(JobBatchStatus.ABORTED, batch.getStatus());
        assertTrue(batch.getStatus().isTerminal());
    }

    @Test
    void rejectsEmptyBatchAndJobFromAnotherBatch() {
        UUID batchId = UUID.randomUUID();

        assertThrows(IllegalArgumentException.class,
                () -> new JobBatch(batchId, "Empty", CREATED_AT, List.of()));
        assertThrows(IllegalArgumentException.class, () -> new JobBatch(
                batchId,
                "Mismatched",
                CREATED_AT,
                List.of(newJob(UUID.randomUUID()))));
    }

    @Test
    void rejectsDuplicateJobsAndProtectsJobList() {
        UUID batchId = UUID.randomUUID();
        Job job = newJob(batchId);

        assertThrows(IllegalArgumentException.class, () -> new JobBatch(
                batchId, "Duplicates", CREATED_AT, List.of(job, job)));

        List<Job> mutableJobs = new ArrayList<>(List.of(job));
        JobBatch batch = new JobBatch(batchId, "Protected list", CREATED_AT, mutableJobs);
        mutableJobs.add(newJob(batchId));

        assertEquals(1, batch.getJobs().size());
        assertThrows(UnsupportedOperationException.class,
                () -> batch.getJobs().add(newJob(batchId)));
    }

    private static Job newJob(UUID batchId) {
        JobSpecification specification = new JobSpecification(
                "Test job",
                "test-job.jar",
                List.of(),
                List.of(),
                List.of(),
                List.of());
        return new Job(UUID.randomUUID(), batchId, specification, CREATED_AT);
    }

    private static void complete(Job job, long offsetSeconds) {
        Task rootTask = job.getRootTask();
        rootTask.assignTo(UUID.randomUUID(), CREATED_AT.plusSeconds(offsetSeconds));
        job.schedule(CREATED_AT.plusSeconds(offsetSeconds));
        rootTask.markRunning(CREATED_AT.plusSeconds(offsetSeconds + 1));
        job.markRunning(CREATED_AT.plusSeconds(offsetSeconds + 1));
        rootTask.markDone(CREATED_AT.plusSeconds(offsetSeconds + 2));
        job.markDone(CREATED_AT.plusSeconds(offsetSeconds + 2));
    }

    private static void fail(Job job, long offsetSeconds) {
        Task rootTask = job.getRootTask();
        rootTask.assignTo(UUID.randomUUID(), CREATED_AT.plusSeconds(offsetSeconds));
        job.schedule(CREATED_AT.plusSeconds(offsetSeconds));
        rootTask.markRunning(CREATED_AT.plusSeconds(offsetSeconds + 1));
        job.markRunning(CREATED_AT.plusSeconds(offsetSeconds + 1));
        rootTask.markFailed(CREATED_AT.plusSeconds(offsetSeconds + 2), "Worker lost");
        job.markFailed(CREATED_AT.plusSeconds(offsetSeconds + 2), "Worker lost");
    }
}
