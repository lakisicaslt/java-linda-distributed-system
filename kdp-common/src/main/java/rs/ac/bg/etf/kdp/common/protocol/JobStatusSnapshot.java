package rs.ac.bg.etf.kdp.common.protocol;

import rs.ac.bg.etf.kdp.common.domain.JobStatus;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Client-visible status of one job at the time of a status request. */
public final class JobStatusSnapshot {

    private final UUID jobId;
    private final JobStatus status;
    private final String failureReason;

    public JobStatusSnapshot(UUID jobId, JobStatus status, String failureReason) {
        this.jobId = Objects.requireNonNull(jobId, "job id must not be null");
        this.status = Objects.requireNonNull(status, "job status must not be null");
        if (status == JobStatus.FAILED) {
            Objects.requireNonNull(failureReason, "failed job must have a reason");
            String trimmedReason = failureReason.trim();
            if (trimmedReason.isEmpty()) {
                throw new IllegalArgumentException("failure reason must not be blank");
            }
            this.failureReason = trimmedReason;
        } else {
            if (failureReason != null) {
                throw new IllegalArgumentException(
                        "only a failed job can contain a failure reason");
            }
            this.failureReason = null;
        }
    }

    public UUID getJobId() {
        return jobId;
    }

    public JobStatus getStatus() {
        return status;
    }

    public Optional<String> getFailureReason() {
        return Optional.ofNullable(failureReason);
    }
}
