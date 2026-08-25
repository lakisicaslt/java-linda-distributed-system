package rs.ac.bg.etf.kdp.common.domain;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * A client-visible group of jobs submitted and queried as one unit.
 */
public final class JobBatch {

    private final UUID id;
    private final Instant createdAt;
    private final List<Job> jobs;

    public JobBatch(UUID id, Instant createdAt, List<Job> jobs) {
        this.id = Objects.requireNonNull(id, "batch id must not be null");
        this.createdAt = Objects.requireNonNull(createdAt, "creation time must not be null");
        this.jobs = copyAndValidateJobs(id, jobs);
    }

    public UUID getId() {
        return id;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public List<Job> getJobs() {
        return jobs;
    }

    public Optional<Job> findJob(UUID jobId) {
        Objects.requireNonNull(jobId, "job id must not be null");
        return jobs.stream()
                .filter(job -> job.getId().equals(jobId))
                .findFirst();
    }

    private static List<Job> copyAndValidateJobs(UUID batchId, List<Job> jobs) {
        Objects.requireNonNull(jobs, "jobs must not be null");
        if (jobs.isEmpty()) {
            throw new IllegalArgumentException("batch must contain at least one job");
        }
        if (jobs.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("jobs must not contain null");
        }
        if (jobs.stream().anyMatch(job -> !job.getBatchId().equals(batchId))) {
            throw new IllegalArgumentException("every job must belong to the batch");
        }

        long uniqueJobIds = jobs.stream()
                .map(Job::getId)
                .collect(HashSet::new, HashSet::add, HashSet::addAll)
                .size();
        if (uniqueJobIds != jobs.size()) {
            throw new IllegalArgumentException("batch must not contain duplicate job ids");
        }

        return List.copyOf(jobs);
    }
}
