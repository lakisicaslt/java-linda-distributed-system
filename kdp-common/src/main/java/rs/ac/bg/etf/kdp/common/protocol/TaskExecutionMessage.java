package rs.ac.bg.etf.kdp.common.protocol;

import rs.ac.bg.etf.kdp.common.domain.JobSpecification;

import java.util.Objects;
import java.util.UUID;

/** Contains the command data needed to execute an assigned task. */
public final class TaskExecutionMessage {

    private final UUID batchId;
    private final UUID taskId;
    private final JobSpecification specification;

    public TaskExecutionMessage(
            UUID batchId,
            UUID taskId,
            JobSpecification specification) {
        this.batchId = Objects.requireNonNull(batchId, "batch id must not be null");
        this.taskId = Objects.requireNonNull(taskId, "task id must not be null");
        this.specification = Objects.requireNonNull(
                specification, "job specification must not be null");
    }

    public UUID getBatchId() {
        return batchId;
    }

    public UUID getTaskId() {
        return taskId;
    }

    public JobSpecification getSpecification() {
        return specification;
    }
}
