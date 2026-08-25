package rs.ac.bg.etf.kdp.common.protocol;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;

public final class FileTransferProtocolCodec {

    private static final int MAX_FILE_NAME_LENGTH = 4_096;

    public byte[] encodeBegin(FileTransferMetadata metadata) {
        Objects.requireNonNull(metadata, "file metadata must not be null");
        byte[] fileName = metadata.getFileName().getBytes(StandardCharsets.UTF_8);
        if (fileName.length > MAX_FILE_NAME_LENGTH) {
            throw new IllegalArgumentException("file name is too long");
        }

        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream output = new DataOutputStream(bytes);
            writeUuid(output, metadata.getOwnerId());
            output.writeByte(metadata.getKind().getCode());
            output.writeLong(metadata.getSize());
            output.writeInt(fileName.length);
            output.write(fileName);
            output.flush();
            return bytes.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Could not create FILE_BEGIN payload", exception);
        }
    }

    public FileTransferMetadata decodeBegin(byte[] payload) throws ProtocolException {
        Objects.requireNonNull(payload, "FILE_BEGIN payload must not be null");
        try {
            ByteArrayInputStream bytes = new ByteArrayInputStream(payload);
            DataInputStream input = new DataInputStream(bytes);
            UUID ownerId = readUuid(input);
            TransferFileKind kind = TransferFileKind.fromCode(input.readUnsignedByte());
            long size = input.readLong();
            int fileNameLength = input.readInt();
            if (fileNameLength < 0 || fileNameLength > MAX_FILE_NAME_LENGTH) {
                throw new IllegalArgumentException("invalid file name length: " + fileNameLength);
            }
            byte[] encodedFileName = input.readNBytes(fileNameLength);
            if (encodedFileName.length != fileNameLength) {
                throw new EOFException("file name is truncated");
            }
            if (bytes.available() != 0) {
                throw new IllegalArgumentException("FILE_BEGIN payload has trailing data");
            }
            return new FileTransferMetadata(
                    ownerId,
                    kind,
                    new String(encodedFileName, StandardCharsets.UTF_8),
                    size);
        } catch (EOFException exception) {
            throw invalidPayload("FILE_BEGIN payload is truncated", exception);
        } catch (IOException | IllegalArgumentException exception) {
            throw invalidPayload("Invalid FILE_BEGIN payload", exception);
        }
    }

    public void requireEmptyPayload(ProtocolFrame frame) throws ProtocolException {
        Objects.requireNonNull(frame, "protocol frame must not be null");
        if (frame.getPayloadSize() != 0) {
            throw invalidPayload(frame.getType() + " payload must be empty", null);
        }
    }

    public void verifyAcknowledgement(ProtocolFrame frame, UUID transferId)
            throws ProtocolException {
        Objects.requireNonNull(frame, "acknowledgement frame must not be null");
        Objects.requireNonNull(transferId, "transfer id must not be null");
        if (frame.getType() != MessageType.FILE_ACKNOWLEDGED) {
            throw invalidPayload("Expected FILE_ACKNOWLEDGED", null);
        }
        requireEmptyPayload(frame);
        if (!frame.getRequestId().equals(transferId)) {
            throw new ProtocolException(
                    ProtocolError.CORRELATION_MISMATCH,
                    "File acknowledgement belongs to a different transfer");
        }
    }

    private static void writeUuid(DataOutputStream output, UUID id) throws IOException {
        output.writeLong(id.getMostSignificantBits());
        output.writeLong(id.getLeastSignificantBits());
    }

    private static UUID readUuid(DataInputStream input) throws IOException {
        return new UUID(input.readLong(), input.readLong());
    }

    private static ProtocolException invalidPayload(String message, Throwable cause) {
        if (cause == null) {
            return new ProtocolException(ProtocolError.INVALID_MESSAGE_PAYLOAD, message);
        }
        return new ProtocolException(ProtocolError.INVALID_MESSAGE_PAYLOAD, message, cause);
    }
}
