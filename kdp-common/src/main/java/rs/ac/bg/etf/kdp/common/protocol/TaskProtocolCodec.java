package rs.ac.bg.etf.kdp.common.protocol;

import rs.ac.bg.etf.kdp.common.domain.TaskType;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.util.Objects;
import java.util.UUID;

/** Encodes task assignment and task acknowledgement payloads. */
public final class TaskProtocolCodec {

    private static final int ASSIGNMENT_PAYLOAD_SIZE = 2 * 16 + 1 + Integer.BYTES;
    private static final int TASK_REFERENCE_PAYLOAD_SIZE = 16;

    public byte[] encodeAssignment(TaskAssignmentMessage assignment) {
        Objects.requireNonNull(assignment, "task assignment must not be null");
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream(ASSIGNMENT_PAYLOAD_SIZE);
            DataOutputStream output = new DataOutputStream(bytes);
            writeUuid(output, assignment.getTaskId());
            writeUuid(output, assignment.getJobId());
            output.writeByte(taskTypeCode(assignment.getTaskType()));
            output.writeInt(assignment.getAttemptNumber());
            output.flush();
            return bytes.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Could not create task assignment payload", exception);
        }
    }

    public TaskAssignmentMessage decodeAssignment(byte[] payload) throws ProtocolException {
        requireLength(payload, ASSIGNMENT_PAYLOAD_SIZE, "task assignment");
        try {
            DataInputStream input = new DataInputStream(new ByteArrayInputStream(payload));
            UUID taskId = readUuid(input);
            UUID jobId = readUuid(input);
            int taskTypeCode = input.readUnsignedByte();
            return new TaskAssignmentMessage(
                    taskId, jobId, taskTypeFromCode(taskTypeCode), input.readInt());
        } catch (IOException | IllegalArgumentException exception) {
            throw invalidPayload("Invalid task assignment payload", exception);
        }
    }

    public byte[] encodeTaskReference(UUID taskId) {
        Objects.requireNonNull(taskId, "task id must not be null");
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream(TASK_REFERENCE_PAYLOAD_SIZE);
            DataOutputStream output = new DataOutputStream(bytes);
            writeUuid(output, taskId);
            output.flush();
            return bytes.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Could not create task reference payload", exception);
        }
    }

    public UUID decodeTaskReference(byte[] payload) throws ProtocolException {
        requireLength(payload, TASK_REFERENCE_PAYLOAD_SIZE, "task reference");
        try {
            return readUuid(new DataInputStream(new ByteArrayInputStream(payload)));
        } catch (EOFException exception) {
            throw invalidPayload("Task reference payload is truncated", exception);
        } catch (IOException exception) {
            throw invalidPayload("Invalid task reference payload", exception);
        }
    }

    private static void requireLength(byte[] payload, int expectedLength, String payloadName)
            throws ProtocolException {
        Objects.requireNonNull(payload, payloadName + " payload must not be null");
        if (payload.length != expectedLength) {
            throw invalidPayload(
                    "Invalid " + payloadName + " payload length: " + payload.length, null);
        }
    }

    private static void writeUuid(DataOutputStream output, UUID id) throws IOException {
        output.writeLong(id.getMostSignificantBits());
        output.writeLong(id.getLeastSignificantBits());
    }

    private static UUID readUuid(DataInputStream input) throws IOException {
        return new UUID(input.readLong(), input.readLong());
    }

    private static int taskTypeCode(TaskType taskType) {
        return switch (taskType) {
            case ROOT -> 1;
            case EVAL -> 2;
        };
    }

    private static TaskType taskTypeFromCode(int code) {
        return switch (code) {
            case 1 -> TaskType.ROOT;
            case 2 -> TaskType.EVAL;
            default -> throw new IllegalArgumentException("unknown task type: " + code);
        };
    }

    private static ProtocolException invalidPayload(String message, Throwable cause) {
        if (cause == null) {
            return new ProtocolException(ProtocolError.INVALID_MESSAGE_PAYLOAD, message);
        }
        return new ProtocolException(ProtocolError.INVALID_MESSAGE_PAYLOAD, message, cause);
    }
}
