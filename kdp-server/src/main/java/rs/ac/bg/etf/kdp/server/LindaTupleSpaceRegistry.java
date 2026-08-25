package rs.ac.bg.etf.kdp.server;

import rs.ac.bg.etf.kdp.LocalLinda;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Keeps one shared tuple space for every submitted batch. */
final class LindaTupleSpaceRegistry {

    private final Map<UUID, LocalLinda> spaces = new HashMap<>();

    synchronized LocalLinda getOrCreate(UUID batchId) {
        Objects.requireNonNull(batchId, "batch id must not be null");
        return spaces.computeIfAbsent(batchId, ignored -> new LocalLinda());
    }
}
