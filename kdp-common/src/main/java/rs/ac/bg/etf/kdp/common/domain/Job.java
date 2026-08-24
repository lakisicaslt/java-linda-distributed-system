package rs.ac.bg.etf.kdp.common.domain;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Server-side state of one submitted job.
 */
public final class Job {

    private final UUID id;
    private final UUID batchId;
    private final JobSpecification specification;
    private final Instant createdAt;
    private final UUID rootTaskId;
    private final Map<UUID, Task> tasks = new LinkedHashMap<>();

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
        this(id, batchId, UUID.randomUUID(), specification, createdAt);
    }

    public Job(
            UUID id,
            UUID batchId,
            UUID rootTaskId,
            JobSpecification specification,
            Instant createdAt) {
        this.id = Objects.requireNonNull(id, "job id must not be null");
        this.batchId = Objects.requireNonNull(batchId, "batch id must not be null");
        this.rootTaskId = Objects.requireNonNull(rootTaskId, "root task id must not be null");
        this.specification = Objects.requireNonNull(
                specification, "job specification must not be null");
        this.createdAt = Objects.requireNonNull(createdAt, "creation time must not be null");
        this.status = JobStatus.READY;
        this.lastStatusChangedAt = createdAt;
        Task rootTask = Task.root(rootTaskId, id, specification.getName(), createdAt);
        tasks.put(rootTaskId, rootTask);
    }

    public void addEvalTask(Task task) {
        Objects.requireNonNull(task, "task must not be null");
        if (status.isTerminal()) {
            throw new IllegalStateException("a terminal job cannot accept new eval tasks");
        }
        if (task.getType() != TaskType.EVAL) {
            throw new IllegalArgumentException("only eval tasks can be added to an existing job");
        }
        if (!task.getJobId().equals(id)) {
            throw new IllegalArgumentException("task must belong to this job");
        }
        UUID parentTaskId = task.getParentTaskId().orElseThrow();
        if (!tasks.containsKey(parentTaskId)) {
            throw new IllegalArgumentException("eval task parent must already belong to this job");
        }
        if (tasks.putIfAbsent(task.getId(), task) != null) {
            throw new IllegalArgumentException("job already contains this task id");
        }
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
        if (tasks.values().stream().anyMatch(task -> task.getStatus() != TaskStatus.DONE)) {
            throw new IllegalStateException("job cannot finish before all of its tasks are done");
        }
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
        tasks.values().stream()
                .filter(task -> task.getStatus() == TaskStatus.FAILED)
                .forEach(task -> task.retry(occurredAt));
        clearExecutionDetails();
    }

    public void abort(Instant occurredAt) {
        transitionTo(JobStatus.ABORTED, occurredAt);
        tasks.values().stream()
                .filter(task -> !task.getStatus().isTerminal())
                .forEach(task -> task.abort(occurredAt));
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

    public Task getRootTask() {
        return tasks.get(rootTaskId);
    }

    public List<Task> getTasks() {
        return List.copyOf(tasks.values());
    }

    public Optional<Task> findTask(UUID taskId) {
        Objects.requireNonNull(taskId, "task id must not be null");
        return Optional.ofNullable(tasks.get(taskId));
    }

    public List<Task> getFailedTasks() {
        return tasks.values().stream()
                .filter(task -> task.getStatus() == TaskStatus.FAILED)
                .toList();
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
