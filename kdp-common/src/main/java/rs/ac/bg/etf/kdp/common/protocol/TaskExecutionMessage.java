package rs.ac.bg.etf.kdp.common.protocol;

import rs.ac.bg.etf.kdp.common.domain.JobSpecification;

import java.util.Objects;
import java.util.UUID;

/** Contains the command data needed to execute an assigned task. */
public final class TaskExecutionMessage {

    private final UUID batchId;
    private final UUID taskId;
    private final JobSpecification specification;
    private final String evalName;
    private final byte[] serializedRunnable;

    public TaskExecutionMessage(
            UUID batchId,
            UUID taskId,
            JobSpecification specification) {
        this(batchId, taskId, specification, null, null);
    }

    public TaskExecutionMessage(
            UUID batchId,
            UUID taskId,
            JobSpecification specification,
            String evalName,
            byte[] serializedRunnable) {
        this.batchId = Objects.requireNonNull(batchId, "batch id must not be null");
        this.taskId = Objects.requireNonNull(taskId, "task id must not be null");
        this.specification = Objects.requireNonNull(
                specification, "job specification must not be null");
        if ((evalName == null) != (serializedRunnable == null)) {
            throw new IllegalArgumentException(
                    "eval name and serialized runnable must either both exist or both be absent");
        }
        if (evalName != null && evalName.isBlank()) {
            throw new IllegalArgumentException("eval name must not be blank");
        }
        if (serializedRunnable != null && serializedRunnable.length == 0) {
            throw new IllegalArgumentException("serialized runnable must not be empty");
        }
        this.evalName = evalName == null ? null : evalName.trim();
        this.serializedRunnable = serializedRunnable == null
                ? null
                : serializedRunnable.clone();
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

    public boolean isEvalTask() {
        return serializedRunnable != null;
    }

    public String getEvalName() {
        return evalName;
    }

    public byte[] getSerializedRunnable() {
        return serializedRunnable == null ? null : serializedRunnable.clone();
    }
}
