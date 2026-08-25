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
    void groupsJobsAndFindsThemById() {
        UUID batchId = UUID.randomUUID();
        Job firstJob = newJob(batchId);
        Job secondJob = newJob(batchId);

        JobBatch batch = new JobBatch(batchId, CREATED_AT, List.of(firstJob, secondJob));

        assertEquals(2, batch.getJobs().size());
        assertTrue(batch.findJob(firstJob.getId()).isPresent());
        assertTrue(batch.findJob(UUID.randomUUID()).isEmpty());
    }

    @Test
    void rejectsEmptyBatchAndJobFromAnotherBatch() {
        UUID batchId = UUID.randomUUID();

        assertThrows(IllegalArgumentException.class,
                () -> new JobBatch(batchId, CREATED_AT, List.of()));
        assertThrows(IllegalArgumentException.class, () -> new JobBatch(
                batchId,
                CREATED_AT,
                List.of(newJob(UUID.randomUUID()))));
    }

    @Test
    void rejectsDuplicateJobsAndProtectsJobList() {
        UUID batchId = UUID.randomUUID();
        Job job = newJob(batchId);

        assertThrows(IllegalArgumentException.class, () -> new JobBatch(
                batchId, CREATED_AT, List.of(job, job)));

        List<Job> mutableJobs = new ArrayList<>(List.of(job));
        JobBatch batch = new JobBatch(batchId, CREATED_AT, mutableJobs);
        mutableJobs.add(newJob(batchId));

        assertEquals(1, batch.getJobs().size());
        assertThrows(UnsupportedOperationException.class,
                () -> batch.getJobs().add(newJob(batchId)));
    }

    private static Job newJob(UUID batchId) {
        JobSpecification specification = new JobSpecification(
                "test-job.jar",
                List.of(),
                List.of(),
                List.of(),
                List.of());
        return new Job(UUID.randomUUID(), batchId, specification, CREATED_AT);
    }
}
