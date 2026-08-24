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
    private final String name;
    private final Instant createdAt;
    private final List<Job> jobs;

    public JobBatch(UUID id, String name, Instant createdAt, List<Job> jobs) {
        this.id = Objects.requireNonNull(id, "batch id must not be null");
        this.name = requireNonBlank(name, "batch name");
        this.createdAt = Objects.requireNonNull(createdAt, "creation time must not be null");
        this.jobs = copyAndValidateJobs(id, jobs);
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
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

    public JobBatchStatus getStatus() {
        boolean hasActiveJob = jobs.stream()
                .map(Job::getStatus)
                .anyMatch(JobBatch::isActive);
        if (hasActiveJob) {
            return JobBatchStatus.IN_PROGRESS;
        }

        boolean hasFailedJob = jobs.stream()
                .anyMatch(job -> job.getStatus() == JobStatus.FAILED);
        if (hasFailedJob) {
            return JobBatchStatus.REQUIRES_ATTENTION;
        }

        boolean allJobsDone = jobs.stream()
                .allMatch(job -> job.getStatus() == JobStatus.DONE);
        return allJobsDone ? JobBatchStatus.DONE : JobBatchStatus.ABORTED;
    }

    private static boolean isActive(JobStatus status) {
        return status == JobStatus.READY
                || status == JobStatus.SCHEDULED
                || status == JobStatus.RUNNING;
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

    private static String requireNonBlank(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName + " must not be null");
        String trimmedValue = value.trim();
        if (trimmedValue.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return trimmedValue;
    }
}
