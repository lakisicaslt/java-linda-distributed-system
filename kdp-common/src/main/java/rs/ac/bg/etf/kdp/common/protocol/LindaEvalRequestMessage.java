package rs.ac.bg.etf.kdp.common.protocol;

import java.util.Objects;
import java.util.UUID;

/** Request to create a distributed task from Linda.eval. */
public final class LindaEvalRequestMessage {

    private final UUID batchId;
    private final UUID parentTaskId;
    private final String name;
    private final byte[] serializedRunnable;

    public LindaEvalRequestMessage(
            UUID batchId,
            UUID parentTaskId,
            String name,
            byte[] serializedRunnable) {
        this.batchId = Objects.requireNonNull(batchId, "batch id must not be null");
        this.parentTaskId = Objects.requireNonNull(
                parentTaskId, "parent task id must not be null");
        Objects.requireNonNull(name, "eval name must not be null");
        if (name.isBlank()) {
            throw new IllegalArgumentException("eval name must not be blank");
        }
        this.name = name.trim();
        Objects.requireNonNull(serializedRunnable, "serialized runnable must not be null");
        if (serializedRunnable.length == 0) {
            throw new IllegalArgumentException("serialized runnable must not be empty");
        }
        this.serializedRunnable = serializedRunnable.clone();
    }

    public UUID getBatchId() {
        return batchId;
    }

    public UUID getParentTaskId() {
        return parentTaskId;
    }

    public String getName() {
        return name;
    }

    public byte[] getSerializedRunnable() {
        return serializedRunnable.clone();
    }
}
