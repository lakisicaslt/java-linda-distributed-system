package rs.ac.bg.etf.kdp.common.protocol;

import java.nio.file.Path;
import java.util.Objects;

public final class ReceivedFile {

    private final FileTransferMetadata metadata;
    private final Path path;
    private final ProtocolFrame acknowledgement;

    public ReceivedFile(
            FileTransferMetadata metadata,
            Path path,
            ProtocolFrame acknowledgement) {
        this.metadata = Objects.requireNonNull(metadata, "file metadata must not be null");
        this.path = Objects.requireNonNull(path, "received file path must not be null");
        this.acknowledgement = Objects.requireNonNull(
                acknowledgement, "acknowledgement must not be null");
    }

    public FileTransferMetadata getMetadata() {
        return metadata;
    }

    public Path getPath() {
        return path;
    }

    public ProtocolFrame getAcknowledgement() {
        return acknowledgement;
    }
}
