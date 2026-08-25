package rs.ac.bg.etf.kdp.common.protocol;

import rs.ac.bg.etf.kdp.common.domain.TaskType;

import java.util.Objects;
import java.util.UUID;

/** Identifies one task offered by the server to a worker. */
public final class TaskAssignmentMessage {

    private final UUID taskId;
    private final UUID jobId;
    private final TaskType taskType;
    private final int attemptNumber;

    public TaskAssignmentMessage(
            UUID taskId,
            UUID jobId,
            TaskType taskType,
            int attemptNumber) {
        this.taskId = Objects.requireNonNull(taskId, "task id must not be null");
        this.jobId = Objects.requireNonNull(jobId, "job id must not be null");
        this.taskType = Objects.requireNonNull(taskType, "task type must not be null");
        if (attemptNumber <= 0) {
            throw new IllegalArgumentException("attempt number must be greater than zero");
        }
        this.attemptNumber = attemptNumber;
    }

    public UUID getTaskId() {
        return taskId;
    }

    public UUID getJobId() {
        return jobId;
    }

    public TaskType getTaskType() {
        return taskType;
    }

    public int getAttemptNumber() {
        return attemptNumber;
    }
}
