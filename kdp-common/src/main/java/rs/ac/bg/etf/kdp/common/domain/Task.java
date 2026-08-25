package rs.ac.bg.etf.kdp.common.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * One schedulable unit of work belonging to a job.
 */
public final class Task {

    private final UUID id;
    private final UUID jobId;
    private final UUID parentTaskId;
    private final TaskType type;
    private final String name;
    private final Instant createdAt;

    private TaskStatus status;
    private UUID assignedWorkerId;
    private int attemptNumber;
    private Instant lastStatusChangedAt;
    private Instant scheduledAt;
    private Instant startedAt;
    private Instant finishedAt;
    private String failureReason;

    private Task(
            UUID id,
            UUID jobId,
            UUID parentTaskId,
            TaskType type,
            String name,
            Instant createdAt) {
        this.id = Objects.requireNonNull(id, "task id must not be null");
        this.jobId = Objects.requireNonNull(jobId, "job id must not be null");
        this.parentTaskId = parentTaskId;
        this.type = Objects.requireNonNull(type, "task type must not be null");
        this.name = requireNonBlank(name, "task name");
        this.createdAt = Objects.requireNonNull(createdAt, "creation time must not be null");
        this.status = TaskStatus.READY;
        this.lastStatusChangedAt = createdAt;
    }

    public static Task root(UUID id, UUID jobId, String name, Instant createdAt) {
        return new Task(id, jobId, null, TaskType.ROOT, name, createdAt);
    }

    public static Task eval(
            UUID id,
            UUID jobId,
            UUID parentTaskId,
            String name,
            Instant createdAt) {
        Objects.requireNonNull(parentTaskId, "parent task id must not be null for an eval task");
        return new Task(id, jobId, parentTaskId, TaskType.EVAL, name, createdAt);
    }

    public void assignTo(UUID workerId, Instant occurredAt) {
        Objects.requireNonNull(workerId, "worker id must not be null");
        transitionTo(TaskStatus.SCHEDULED, occurredAt);
        assignedWorkerId = workerId;
        scheduledAt = occurredAt;
        attemptNumber++;
    }

    public void markRunning(Instant occurredAt) {
        transitionTo(TaskStatus.RUNNING, occurredAt);
        startedAt = occurredAt;
    }

    public void markDone(Instant occurredAt) {
        transitionTo(TaskStatus.DONE, occurredAt);
        finishedAt = occurredAt;
        failureReason = null;
    }

    public void markFailed(Instant occurredAt, String reason) {
        String validatedReason = requireNonBlank(reason, "failure reason");
        transitionTo(TaskStatus.FAILED, occurredAt);
        finishedAt = occurredAt;
        failureReason = validatedReason;
    }

    public void retry(Instant occurredAt) {
        transitionTo(TaskStatus.READY, occurredAt);
        clearAttemptDetails();
    }

    public void abort(Instant occurredAt) {
        transitionTo(TaskStatus.ABORTED, occurredAt);
        finishedAt = occurredAt;
        failureReason = null;
    }

    public UUID getId() {
        return id;
    }

    public UUID getJobId() {
        return jobId;
    }

    public Optional<UUID> getParentTaskId() {
        return Optional.ofNullable(parentTaskId);
    }

    public TaskType getType() {
        return type;
    }

    public String getName() {
        return name;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public TaskStatus getStatus() {
        return status;
    }

    public Optional<UUID> getAssignedWorkerId() {
        return Optional.ofNullable(assignedWorkerId);
    }

    public int getAttemptNumber() {
        return attemptNumber;
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

    private void transitionTo(TaskStatus nextStatus, Instant occurredAt) {
        Instant validatedTime = Objects.requireNonNull(
                occurredAt, "status change time must not be null");
        if (validatedTime.isBefore(lastStatusChangedAt)) {
            throw new IllegalArgumentException("status change time must not move backwards");
        }
        TaskStatusTransitions.requireValidTransition(status, nextStatus);
        status = nextStatus;
        lastStatusChangedAt = validatedTime;
    }

    private void clearAttemptDetails() {
        assignedWorkerId = null;
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
