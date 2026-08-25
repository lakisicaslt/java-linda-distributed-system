package rs.ac.bg.etf.kdp.common.protocol;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Writes one incoming file incrementally and removes incomplete data on close. */
public final class FileTransferReceiver implements AutoCloseable {

    private final Path destinationDirectory;
    private final FileTransferProtocolCodec fileCodec = new FileTransferProtocolCodec();

    private UUID transferId;
    private FileTransferMetadata metadata;
    private Path temporaryPath;
    private OutputStream output;
    private long receivedBytes;

    public FileTransferReceiver(Path destinationDirectory) {
        this.destinationDirectory = Objects.requireNonNull(
                destinationDirectory, "destination directory must not be null")
                .toAbsolutePath()
                .normalize();
    }

    public synchronized Optional<ReceivedFile> accept(ProtocolFrame frame) throws IOException {
        Objects.requireNonNull(frame, "protocol frame must not be null");
        return switch (frame.getType()) {
            case FILE_BEGIN -> {
                begin(frame);
                yield Optional.empty();
            }
            case FILE_CHUNK -> {
                writeChunk(frame);
                yield Optional.empty();
            }
            case FILE_END -> Optional.of(finish(frame));
            default -> throw invalidPayload("Expected a file transfer frame");
        };
    }

    public synchronized boolean isReceiving() {
        return transferId != null;
    }

    public synchronized long getReceivedBytes() {
        return receivedBytes;
    }

    private void begin(ProtocolFrame frame) throws IOException {
        if (transferId != null) {
            throw invalidPayload("Another file transfer is already active");
        }
        FileTransferMetadata decodedMetadata = fileCodec.decodeBegin(frame.getPayload());
        Files.createDirectories(destinationDirectory);
        Path partFile = destinationDirectory.resolve(frame.getRequestId() + ".part");
        OutputStream createdOutput = Files.newOutputStream(partFile);

        transferId = frame.getRequestId();
        metadata = decodedMetadata;
        temporaryPath = partFile;
        output = createdOutput;
        receivedBytes = 0;
    }

    private void writeChunk(ProtocolFrame frame) throws IOException {
        requireActiveTransfer(frame);
        byte[] chunk = frame.getPayload();
        if (chunk.length == 0) {
            throw invalidPayload("FILE_CHUNK payload must not be empty");
        }
        if (receivedBytes + chunk.length > metadata.getSize()) {
            throw invalidPayload("Received more bytes than declared in FILE_BEGIN");
        }
        output.write(chunk);
        receivedBytes += chunk.length;
    }

    private ReceivedFile finish(ProtocolFrame frame) throws IOException {
        requireActiveTransfer(frame);
        fileCodec.requireEmptyPayload(frame);
        if (receivedBytes != metadata.getSize()) {
            throw invalidPayload(
                    "Received " + receivedBytes + " of " + metadata.getSize() + " bytes");
        }

        output.close();
        output = null;
        Path destination = destinationDirectory.resolve(metadata.getFileName()).normalize();
        if (!destination.getParent().equals(destinationDirectory)) {
            throw invalidPayload("File name escapes the destination directory");
        }
        moveCompletedFile(temporaryPath, destination);

        ProtocolFrame acknowledgement = frame.reply(MessageType.FILE_ACKNOWLEDGED, new byte[0]);
        ReceivedFile receivedFile = new ReceivedFile(metadata, destination, acknowledgement);
        clearState();
        return receivedFile;
    }

    private void requireActiveTransfer(ProtocolFrame frame) throws ProtocolException {
        if (transferId == null) {
            throw invalidPayload("FILE_BEGIN must be received first");
        }
        if (!transferId.equals(frame.getRequestId())) {
            throw new ProtocolException(
                    ProtocolError.CORRELATION_MISMATCH,
                    "File frame belongs to a different transfer");
        }
    }

    private static void moveCompletedFile(Path source, Path destination) throws IOException {
        try {
            Files.move(
                    source,
                    destination,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    @Override
    public synchronized void close() {
        if (output != null) {
            try {
                output.close();
            } catch (IOException ignored) {
                // Cleanup continues with deletion of the incomplete file.
            }
        }
        if (temporaryPath != null) {
            try {
                Files.deleteIfExists(temporaryPath);
            } catch (IOException ignored) {
                // There is no useful recovery action during receiver cleanup.
            }
        }
        clearState();
    }

    private void clearState() {
        transferId = null;
        metadata = null;
        temporaryPath = null;
        output = null;
        receivedBytes = 0;
    }

    private static ProtocolException invalidPayload(String message) {
        return new ProtocolException(ProtocolError.INVALID_MESSAGE_PAYLOAD, message);
    }
}
