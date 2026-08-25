package rs.ac.bg.etf.kdp.common.protocol;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Manifest sent before the server streams files belonging to a batch result. */
public final class BatchResultMessage {

    private final UUID batchId;
    private final List<BatchResultFile> files;

    public BatchResultMessage(UUID batchId, List<BatchResultFile> files) {
        this.batchId = Objects.requireNonNull(batchId, "batch id must not be null");
        Objects.requireNonNull(files, "batch result files must not be null");
        if (files.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("batch result files must not contain null");
        }
        long uniqueFiles = files.stream()
                .map(file -> new ResultFileKey(
                        file.getJobId(), file.getKind(), file.getFileName()))
                .collect(HashSet::new, HashSet::add, HashSet::addAll)
                .size();
        if (uniqueFiles != files.size()) {
            throw new IllegalArgumentException("batch result must not contain duplicate files");
        }
        this.files = List.copyOf(files);
    }

    public UUID getBatchId() {
        return batchId;
    }

    public List<BatchResultFile> getFiles() {
        return files;
    }

    private record ResultFileKey(
            UUID jobId,
            TransferFileKind kind,
            String fileName) {
    }
}
