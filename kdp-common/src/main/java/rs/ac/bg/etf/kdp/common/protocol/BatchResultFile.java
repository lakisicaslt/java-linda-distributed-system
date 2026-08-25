package rs.ac.bg.etf.kdp.common.protocol;

import java.util.Objects;
import java.util.UUID;

/** Description of one file available in a batch result. */
public final class BatchResultFile {

    private final UUID jobId;
    private final UUID taskId;
    private final TransferFileKind kind;
    private final String fileName;
    private final long size;

    public BatchResultFile(
            UUID jobId,
            UUID taskId,
            TransferFileKind kind,
            String fileName,
            long size) {
        this.jobId = Objects.requireNonNull(jobId, "job id must not be null");
        this.taskId = Objects.requireNonNull(taskId, "task id must not be null");
        this.kind = requireResultKind(kind);
        this.fileName = validateFileName(fileName);
        if (size < 0) {
            throw new IllegalArgumentException("result file size must not be negative");
        }
        this.size = size;
    }

    public UUID getJobId() {
        return jobId;
    }

    public UUID getTaskId() {
        return taskId;
    }

    public TransferFileKind getKind() {
        return kind;
    }

    public String getFileName() {
        return fileName;
    }

    public long getSize() {
        return size;
    }

    private static TransferFileKind requireResultKind(TransferFileKind kind) {
        Objects.requireNonNull(kind, "result file kind must not be null");
        if (kind != TransferFileKind.OUTPUT
                && kind != TransferFileKind.STANDARD_OUTPUT
                && kind != TransferFileKind.STANDARD_ERROR) {
            throw new IllegalArgumentException("batch result can contain only result files");
        }
        return kind;
    }

    private static String validateFileName(String fileName) {
        Objects.requireNonNull(fileName, "result file name must not be null");
        String trimmed = fileName.trim();
        if (trimmed.isEmpty()
                || trimmed.equals(".")
                || trimmed.equals("..")
                || trimmed.contains("/")
                || trimmed.contains("\\")) {
            throw new IllegalArgumentException("result file name must be a simple name");
        }
        return trimmed;
    }
}
