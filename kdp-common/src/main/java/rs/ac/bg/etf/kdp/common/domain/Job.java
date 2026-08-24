package rs.ac.bg.etf.kdp.common.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Server-side state of one submitted job.
 */
public final class Job {

    private final UUID id;
    private final UUID batchId;
    private final JobSpecification specification;
    private final Instant createdAt;

    private JobStatus status;
    private Instant lastStatusChangedAt;
    private Instant scheduledAt;
    private Instant startedAt;
    private Instant finishedAt;
    private String failureReason;

    public Job(
            UUID id,
            UUID batchId,
            JobSpecification specification,
            Instant createdAt) {
        this.id = Objects.requireNonNull(id, "job id must not be null");
        this.batchId = Objects.requireNonNull(batchId, "batch id must not be null");
        this.specification = Objects.requireNonNull(
                specification, "job specification must not be null");
        this.createdAt = Objects.requireNonNull(createdAt, "creation time must not be null");
        this.status = JobStatus.READY;
        this.lastStatusChangedAt = createdAt;
    }

    public void schedule(Instant occurredAt) {
        transitionTo(JobStatus.SCHEDULED, occurredAt);
        scheduledAt = occurredAt;
    }

    public void returnToReady(Instant occurredAt) {
        transitionTo(JobStatus.READY, occurredAt);
        clearExecutionDetails();
    }

    public void markRunning(Instant occurredAt) {
        transitionTo(JobStatus.RUNNING, occurredAt);
        startedAt = occurredAt;
    }

    public void markDone(Instant occurredAt) {
        transitionTo(JobStatus.DONE, occurredAt);
        finishedAt = occurredAt;
        failureReason = null;
    }

    public void markFailed(Instant occurredAt, String reason) {
        String validatedReason = requireNonBlank(reason, "failure reason");
        transitionTo(JobStatus.FAILED, occurredAt);
        finishedAt = occurredAt;
        failureReason = validatedReason;
    }

    public void retry(Instant occurredAt) {
        transitionTo(JobStatus.READY, occurredAt);
        clearExecutionDetails();
    }

    public void abort(Instant occurredAt) {
        transitionTo(JobStatus.ABORTED, occurredAt);
        finishedAt = occurredAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getBatchId() {
        return batchId;
    }

    public JobSpecification getSpecification() {
        return specification;
    }

    public JobStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getLastStatusChangedAt() {
        return lastStatusChangedAt;
    }

    public Instant getScheduledAt() {
        return scheduledAt;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    public String getFailureReason() {
        return failureReason;
    }

    private void transitionTo(JobStatus nextStatus, Instant occurredAt) {
        Instant validatedTime = Objects.requireNonNull(
                occurredAt, "status change time must not be null");
        if (validatedTime.isBefore(lastStatusChangedAt)) {
            throw new IllegalArgumentException("status change time must not move backwards");
        }

        JobStatusTransitions.requireValidTransition(status, nextStatus);
        status = nextStatus;
        lastStatusChangedAt = validatedTime;
    }

    private void clearExecutionDetails() {
        scheduledAt = null;
        startedAt = null;
        finishedAt = null;
        failureReason = null;
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
