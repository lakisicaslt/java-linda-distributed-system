package rs.ac.bg.etf.kdp.common.protocol;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Identifiers assigned by the server to an accepted batch and its jobs. */
public final class BatchAcceptedMessage {

    private final UUID batchId;
    private final List<UUID> jobIds;

    public BatchAcceptedMessage(UUID batchId, List<UUID> jobIds) {
        this.batchId = Objects.requireNonNull(batchId, "batch id must not be null");
        Objects.requireNonNull(jobIds, "job ids must not be null");
        if (jobIds.isEmpty()) {
            throw new IllegalArgumentException("accepted batch must contain at least one job id");
        }
        if (jobIds.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("job ids must not contain null");
        }
        if (new HashSet<>(jobIds).size() != jobIds.size()) {
            throw new IllegalArgumentException("job ids must be unique");
        }
        this.jobIds = List.copyOf(jobIds);
    }

    public UUID getBatchId() {
        return batchId;
    }

    public List<UUID> getJobIds() {
        return jobIds;
    }
}
