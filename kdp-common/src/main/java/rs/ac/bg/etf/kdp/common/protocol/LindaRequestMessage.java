package rs.ac.bg.etf.kdp.common.protocol;

import java.util.Objects;
import java.util.UUID;

/** Context and tuple template sent with one Linda operation. */
public final class LindaRequestMessage {

    private final UUID batchId;
    private final UUID taskId;
    private final String[] tuple;

    public LindaRequestMessage(UUID batchId, UUID taskId, String[] tuple) {
        this.batchId = Objects.requireNonNull(batchId, "batch id must not be null");
        this.taskId = Objects.requireNonNull(taskId, "task id must not be null");
        this.tuple = Objects.requireNonNull(tuple, "Linda tuple must not be null").clone();
    }

    public UUID getBatchId() {
        return batchId;
    }

    public UUID getTaskId() {
        return taskId;
    }

    public String[] getTuple() {
        return tuple.clone();
    }
}
