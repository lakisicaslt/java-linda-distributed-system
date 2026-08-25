package rs.ac.bg.etf.kdp.common.protocol;

import rs.ac.bg.etf.kdp.common.domain.JobSpecification;

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

/** Encodes the command and file names needed to start a task on a worker. */
public final class TaskExecutionProtocolCodec {

    private static final int MAX_TEXT_BYTES = 4_096;
    private static final int MAX_ARGUMENT_COUNT = 256;

    public byte[] encode(TaskExecutionMessage message) {
        Objects.requireNonNull(message, "task execution message must not be null");
        JobSpecification specification = message.getSpecification();
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream output = new DataOutputStream(bytes);
            writeUuid(output, message.getTaskId());
            writeString(output, specification.getJarFileName());
            writeStrings(output, specification.getJvmArguments());
            writeStrings(output, specification.getProgramArguments());
            writeStrings(output, specification.getInputFileNames());
            writeStrings(output, specification.getOutputFileNames());
            output.flush();
            return bytes.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Could not create task execution payload", exception);
        }
    }

    public TaskExecutionMessage decode(byte[] payload) throws ProtocolException {
        Objects.requireNonNull(payload, "task execution payload must not be null");
        try {
            ByteArrayInputStream bytes = new ByteArrayInputStream(payload);
            DataInputStream input = new DataInputStream(bytes);
            UUID taskId = readUuid(input);
            JobSpecification specification = new JobSpecification(
                    readString(input),
                    readStrings(input),
                    readStrings(input),
                    readStrings(input),
                    readStrings(input));
            if (bytes.available() != 0) {
                throw new IllegalArgumentException("Task execution payload has trailing data");
            }
            return new TaskExecutionMessage(taskId, specification);
        } catch (EOFException exception) {
            throw invalidPayload("Task execution payload is truncated", exception);
        } catch (IOException | IllegalArgumentException exception) {
            throw invalidPayload("Invalid task execution payload", exception);
        }
    }

    private static void writeStrings(DataOutputStream output, List<String> values)
            throws IOException {
        if (values.size() > MAX_ARGUMENT_COUNT) {
            throw new IllegalArgumentException("Too many values in task execution payload");
        }
        output.writeInt(values.size());
        for (String value : values) {
            writeString(output, value);
        }
    }

    private static List<String> readStrings(DataInputStream input) throws IOException {
        int count = input.readInt();
        if (count < 0 || count > MAX_ARGUMENT_COUNT) {
            throw new IllegalArgumentException("Invalid list size: " + count);
        }
        List<String> values = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            values.add(readString(input));
        }
        return List.copyOf(values);
    }

    private static void writeString(DataOutputStream output, String value) throws IOException {
        byte[] encoded = value.getBytes(StandardCharsets.UTF_8);
        if (encoded.length > MAX_TEXT_BYTES) {
            throw new IllegalArgumentException("Task execution value is too long");
        }
        output.writeInt(encoded.length);
        output.write(encoded);
    }

    private static String readString(DataInputStream input) throws IOException {
        int length = input.readInt();
        if (length < 0 || length > MAX_TEXT_BYTES) {
            throw new IllegalArgumentException("Invalid task execution value length: " + length);
        }
        byte[] encoded = input.readNBytes(length);
        if (encoded.length != length) {
            throw new EOFException("Task execution value is truncated");
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
