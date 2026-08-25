package rs.ac.bg.etf.kdp.common.protocol;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Status snapshot for all jobs submitted as one batch. */
public final class BatchStatusMessage {

    private final UUID batchId;
    private final List<JobStatusSnapshot> jobs;

    public BatchStatusMessage(UUID batchId, List<JobStatusSnapshot> jobs) {
        this.batchId = Objects.requireNonNull(batchId, "batch id must not be null");
        Objects.requireNonNull(jobs, "job statuses must not be null");
        if (jobs.isEmpty()) {
            throw new IllegalArgumentException("batch status must contain at least one job");
        }
        if (jobs.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("job statuses must not contain null");
        }
        long uniqueIds = jobs.stream()
                .map(JobStatusSnapshot::getJobId)
                .collect(HashSet::new, HashSet::add, HashSet::addAll)
                .size();
        if (uniqueIds != jobs.size()) {
            throw new IllegalArgumentException("batch status must not contain duplicate job ids");
        }
        this.jobs = List.copyOf(jobs);
    }

    public UUID getBatchId() {
        return batchId;
    }

    public List<JobStatusSnapshot> getJobs() {
        return jobs;
    }

    public Optional<JobStatusSnapshot> findJob(UUID jobId) {
        Objects.requireNonNull(jobId, "job id must not be null");
        return jobs.stream().filter(job -> job.getJobId().equals(jobId)).findFirst();
    }
}
