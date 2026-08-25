package rs.ac.bg.etf.kdp.common.protocol;

import rs.ac.bg.etf.kdp.common.domain.JobSpecification;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.util.Objects;
import java.util.UUID;

/** Encodes the command and file names needed to start a task on a worker. */
public final class TaskExecutionProtocolCodec {

    private final JobSpecificationProtocolCodec specificationCodec =
            new JobSpecificationProtocolCodec();

    public byte[] encode(TaskExecutionMessage message) {
        Objects.requireNonNull(message, "task execution message must not be null");
        JobSpecification specification = message.getSpecification();
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream output = new DataOutputStream(bytes);
            writeUuid(output, message.getTaskId());
            specificationCodec.write(output, specification);
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
            JobSpecification specification = specificationCodec.read(input);
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
