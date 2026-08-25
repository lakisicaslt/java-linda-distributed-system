package rs.ac.bg.etf.kdp.common.protocol;

import java.util.Objects;
import java.util.UUID;

public final class FileTransferMetadata {

    private final UUID ownerId;
    private final TransferFileKind kind;
    private final String fileName;
    private final long size;

    public FileTransferMetadata(
            UUID ownerId,
            TransferFileKind kind,
            String fileName,
            long size) {
        this.ownerId = Objects.requireNonNull(ownerId, "owner id must not be null");
        this.kind = Objects.requireNonNull(kind, "file kind must not be null");
        this.fileName = validateFileName(fileName);
        if (size < 0) {
            throw new IllegalArgumentException("file size must not be negative");
        }
        this.size = size;
    }

    public UUID getOwnerId() {
        return ownerId;
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

    private static String validateFileName(String fileName) {
        Objects.requireNonNull(fileName, "file name must not be null");
        String trimmed = fileName.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("file name must not be blank");
        }
        if (trimmed.equals(".")
                || trimmed.equals("..")
                || trimmed.contains("/")
                || trimmed.contains("\\")) {
            throw new IllegalArgumentException("file name must be a simple name");
        }
        return trimmed;
    }
}
