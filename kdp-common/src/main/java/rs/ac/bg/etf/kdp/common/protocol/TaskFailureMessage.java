package rs.ac.bg.etf.kdp.common.protocol;

import java.util.Objects;
import java.util.UUID;

/** Describes a task that the worker could not execute. */
public final class TaskFailureMessage {

    private final UUID taskId;
    private final String reason;

    public TaskFailureMessage(UUID taskId, String reason) {
        this.taskId = Objects.requireNonNull(taskId, "task id must not be null");
        Objects.requireNonNull(reason, "failure reason must not be null");
        if (reason.isBlank()) {
            throw new IllegalArgumentException("failure reason must not be blank");
        }
        this.reason = reason.trim();
    }

    public UUID getTaskId() {
        return taskId;
    }

    public String getReason() {
        return reason;
    }
}
