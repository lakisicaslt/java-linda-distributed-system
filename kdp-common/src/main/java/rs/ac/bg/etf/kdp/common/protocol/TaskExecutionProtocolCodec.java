package rs.ac.bg.etf.kdp.common.protocol;

import rs.ac.bg.etf.kdp.common.domain.JobSpecification;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;

/** Encodes the command and file names needed to start a task on a worker. */
public final class TaskExecutionProtocolCodec {

    private static final int MAX_EVAL_NAME_BYTES = 4_096;

    private final JobSpecificationProtocolCodec specificationCodec =
            new JobSpecificationProtocolCodec();

    public byte[] encode(TaskExecutionMessage message) {
        Objects.requireNonNull(message, "task execution message must not be null");
        JobSpecification specification = message.getSpecification();
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream output = new DataOutputStream(bytes);
            writeUuid(output, message.getBatchId());
            writeUuid(output, message.getTaskId());
            specificationCodec.write(output, specification);
            byte[] runnable = message.getSerializedRunnable();
            output.writeBoolean(runnable != null);
            if (runnable != null) {
                writeString(output, message.getEvalName());
                output.writeInt(runnable.length);
                output.write(runnable);
            }
            output.flush();
            byte[] payload = bytes.toByteArray();
            if (payload.length > ProtocolConstants.MAX_CONTROL_PAYLOAD_SIZE) {
                throw new IllegalArgumentException("Task execution payload is too large");
            }
            return payload;
        } catch (IOException exception) {
            throw new IllegalStateException("Could not create task execution payload", exception);
        }
    }

    public TaskExecutionMessage decode(byte[] payload) throws ProtocolException {
        Objects.requireNonNull(payload, "task execution payload must not be null");
        try {
            ByteArrayInputStream bytes = new ByteArrayInputStream(payload);
            DataInputStream input = new DataInputStream(bytes);
            UUID batchId = readUuid(input);
            UUID taskId = readUuid(input);
            JobSpecification specification = specificationCodec.read(input);
            byte[] runnable = null;
            String evalName = null;
            if (input.readBoolean()) {
                evalName = readString(input);
                int length = input.readInt();
                if (length < 1 || length > ProtocolConstants.MAX_CONTROL_PAYLOAD_SIZE) {
                    throw new IllegalArgumentException(
                            "Invalid serialized runnable length: " + length);
                }
                runnable = input.readNBytes(length);
                if (runnable.length != length) {
                    throw new EOFException("Serialized runnable is truncated");
                }
            }
            if (bytes.available() != 0) {
                throw new IllegalArgumentException("Task execution payload has trailing data");
            }
            return new TaskExecutionMessage(
                    batchId, taskId, specification, evalName, runnable);
        } catch (EOFException exception) {
            throw invalidPayload("Task execution payload is truncated", exception);
        } catch (IOException | IllegalArgumentException exception) {
            throw invalidPayload("Invalid task execution payload", exception);
        }
    }

    private static void writeUuid(DataOutputStream output, UUID id) throws IOException {
        output.writeLong(id.getMostSignificantBits());
        output.writeLong(id.getLeastSignificantBits());
    }

    private static UUID readUuid(DataInputStream input) throws IOException {
        return new UUID(input.readLong(), input.readLong());
    }

    private static void writeString(DataOutputStream output, String value) throws IOException {
        byte[] encoded = value.getBytes(StandardCharsets.UTF_8);
        if (encoded.length > MAX_EVAL_NAME_BYTES) {
            throw new IllegalArgumentException("Eval name is too long");
        }
        output.writeInt(encoded.length);
        output.write(encoded);
    }

    private static String readString(DataInputStream input) throws IOException {
        int length = input.readInt();
        if (length < 1 || length > MAX_EVAL_NAME_BYTES) {
            throw new IllegalArgumentException("Invalid eval name length: " + length);
        }
        byte[] encoded = input.readNBytes(length);
        if (encoded.length != length) {
            throw new EOFException("Eval name is truncated");
        }
        return new String(encoded, StandardCharsets.UTF_8);
    }

    private static ProtocolException invalidPayload(String message, Throwable cause) {
        return new ProtocolException(ProtocolError.INVALID_MESSAGE_PAYLOAD, message, cause);
    }
}
