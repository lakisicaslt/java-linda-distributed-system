package rs.ac.bg.etf.kdp.worker;

import rs.ac.bg.etf.kdp.common.protocol.FileTransferMetadata;
import rs.ac.bg.etf.kdp.common.protocol.FileTransferProtocolCodec;
import rs.ac.bg.etf.kdp.common.protocol.FileTransferReceiver;
import rs.ac.bg.etf.kdp.common.protocol.MessageType;
import rs.ac.bg.etf.kdp.common.protocol.ProtocolError;
import rs.ac.bg.etf.kdp.common.protocol.ProtocolException;
import rs.ac.bg.etf.kdp.common.protocol.ProtocolFrame;
import rs.ac.bg.etf.kdp.common.protocol.ReceivedFile;
import rs.ac.bg.etf.kdp.common.protocol.TransferFileKind;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Stores files received for tasks currently assigned to this worker. */
final class WorkerFileStore implements AutoCloseable {

    private final Path workDirectory;
    private final Set<UUID> assignedTaskIds;
    private final FileTransferProtocolCodec fileCodec = new FileTransferProtocolCodec();

    private FileTransferReceiver activeReceiver;

    WorkerFileStore(Path workDirectory, Set<UUID> assignedTaskIds) {
        this.workDirectory = Objects.requireNonNull(
                workDirectory, "work directory must not be null");
        this.assignedTaskIds = Objects.requireNonNull(
                assignedTaskIds, "assigned task ids must not be null");
    }

    synchronized Optional<ReceivedFile> accept(ProtocolFrame frame) throws IOException {
        Objects.requireNonNull(frame, "protocol frame must not be null");
        if (frame.getType() == MessageType.FILE_BEGIN) {
            begin(frame);
        } else if (activeReceiver == null) {
            throw new ProtocolException(
                    ProtocolError.INVALID_MESSAGE_PAYLOAD,
                    "FILE_BEGIN must be received before file data");
        }

        Optional<ReceivedFile> result = activeReceiver.accept(frame);
        if (result.isPresent()) {
            activeReceiver.close();
            activeReceiver = null;
        }
        return result;
    }

    Path taskDirectory(UUID taskId) {
        Objects.requireNonNull(taskId, "task id must not be null");
        return workDirectory.resolve(taskId.toString());
    }

    private void begin(ProtocolFrame frame) throws ProtocolException {
        if (activeReceiver != null) {
            throw new ProtocolException(
                    ProtocolError.INVALID_MESSAGE_PAYLOAD,
                    "Only one file can be received at a time");
        }
        FileTransferMetadata metadata = fileCodec.decodeBegin(frame.getPayload());
        if (!assignedTaskIds.contains(metadata.getOwnerId())) {
            throw new ProtocolException(
                    ProtocolError.INVALID_MESSAGE_PAYLOAD,
                    "File belongs to a task that is not assigned to this worker");
        }
        if (metadata.getKind() != TransferFileKind.JOB_ARCHIVE
                && metadata.getKind() != TransferFileKind.INPUT) {
            throw new ProtocolException(
                    ProtocolError.INVALID_MESSAGE_PAYLOAD,
                    "Worker can receive only a job archive or an input file from the server");
        }
        activeReceiver = new FileTransferReceiver(taskDirectory(metadata.getOwnerId()));
    }

    @Override
    public synchronized void close() {
        if (activeReceiver != null) {
            activeReceiver.close();
            activeReceiver = null;
        }
    }
}
