package rs.ac.bg.etf.kdp.common.protocol;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Encodes the manifest that precedes streamed batch result files. */
public final class BatchResultProtocolCodec {

    private static final int MAX_RESULT_FILES = 2_048;
    private static final int MAX_FILE_NAME_BYTES = 4_096;

    public byte[] encode(BatchResultMessage message) {
        Objects.requireNonNull(message, "batch result must not be null");
        if (message.getFiles().size() > MAX_RESULT_FILES) {
            throw new IllegalArgumentException("Too many files in batch result");
        }
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream output = new DataOutputStream(bytes);
            writeUuid(output, message.getBatchId());
            output.writeInt(message.getFiles().size());
            for (BatchResultFile file : message.getFiles()) {
                writeUuid(output, file.getJobId());
                writeUuid(output, file.getTaskId());
                output.writeByte(file.getKind().getCode());
                writeString(output, file.getFileName());
                output.writeLong(file.getSize());
            }
            output.flush();
            return bytes.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Could not create batch result payload", exception);
        }
    }

    public BatchResultMessage decode(byte[] payload) throws ProtocolException {
        Objects.requireNonNull(payload, "batch result payload must not be null");
        try {
            ByteArrayInputStream bytes = new ByteArrayInputStream(payload);
            DataInputStream input = new DataInputStream(bytes);
            UUID batchId = readUuid(input);
            int count = input.readInt();
            if (count < 0 || count > MAX_RESULT_FILES) {
                throw new IllegalArgumentException("Invalid result file count: " + count);
            }

            List<BatchResultFile> files = new ArrayList<>(count);
            for (int index = 0; index < count; index++) {
                files.add(new BatchResultFile(
                        readUuid(input),
                        readUuid(input),
                        TransferFileKind.fromCode(input.readUnsignedByte()),
                        readString(input),
                        input.readLong()));
            }
            if (bytes.available() != 0) {
                throw new IllegalArgumentException("Batch result payload has trailing data");
            }
            return new BatchResultMessage(batchId, files);
        } catch (EOFException exception) {
            throw invalidPayload("Batch result payload is truncated", exception);
        } catch (IOException | IllegalArgumentException exception) {
            throw invalidPayload("Invalid batch result payload", exception);
        }
    }

    private static void writeString(DataOutputStream output, String value) throws IOException {
        byte[] encoded = value.getBytes(StandardCharsets.UTF_8);
        if (encoded.length > MAX_FILE_NAME_BYTES) {
            throw new IllegalArgumentException("Result file name is too long");
        }
        output.writeInt(encoded.length);
        output.write(encoded);
    }

    private static String readString(DataInputStream input) throws IOException {
        int length = input.readInt();
        if (length < 0 || length > MAX_FILE_NAME_BYTES) {
            throw new IllegalArgumentException("Invalid result file name length: " + length);
        }
        byte[] encoded = input.readNBytes(length);
        if (encoded.length != length) {
            throw new EOFException("Result file name is truncated");
        }
        return new String(encoded, StandardCharsets.UTF_8);
    }

    private static void writeUuid(DataOutputStream output, UUID id) throws IOException {
        output.writeLong(id.getMostSignificantBits());
        output.writeLong(id.getLeastSignificantBits());
    }

    private static UUID readUuid(DataInputStream input) throws IOException {
        return new UUID(input.readLong(), input.readLong());
    }

    private static ProtocolException invalidPayload(String message, Throwable cause) {
        return new ProtocolException(ProtocolError.INVALID_MESSAGE_PAYLOAD, message, cause);
    }
}
