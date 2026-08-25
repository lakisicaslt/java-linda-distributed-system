package rs.ac.bg.etf.kdp.common.protocol;

import org.junit.jupiter.api.Test;
import rs.ac.bg.etf.kdp.common.domain.TaskType;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TaskProtocolCodecTest {

    private final TaskProtocolCodec codec = new TaskProtocolCodec();

    @Test
    void roundTripsTaskAssignment() throws Exception {
        UUID taskId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        TaskAssignmentMessage assignment = new TaskAssignmentMessage(
                taskId, jobId, TaskType.EVAL, 3);

        TaskAssignmentMessage decoded = codec.decodeAssignment(
                codec.encodeAssignment(assignment));

        assertEquals(taskId, decoded.getTaskId());
        assertEquals(jobId, decoded.getJobId());
        assertEquals(TaskType.EVAL, decoded.getTaskType());
        assertEquals(3, decoded.getAttemptNumber());
    }

    @Test
    void roundTripsTaskReference() throws Exception {
        UUID taskId = UUID.randomUUID();

        assertEquals(taskId, codec.decodeTaskReference(codec.encodeTaskReference(taskId)));
    }

    @Test
    void rejectsMalformedPayloads() {
        assertInvalidPayload(() -> codec.decodeAssignment(new byte[36]));
        assertInvalidPayload(() -> codec.decodeTaskReference(new byte[15]));

        byte[] unknownType = new byte[37];
        unknownType[32] = 99;
        unknownType[36] = 1;
        assertInvalidPayload(() -> codec.decodeAssignment(unknownType));
    }

    @Test
    void rejectsNonPositiveAttemptNumber() {
        assertThrows(IllegalArgumentException.class, () -> new TaskAssignmentMessage(
                UUID.randomUUID(), UUID.randomUUID(), TaskType.ROOT, 0));
    }

    private static void assertInvalidPayload(ThrowingOperation operation) {
        ProtocolException exception = assertThrows(ProtocolException.class, operation::run);
        assertEquals(ProtocolError.INVALID_MESSAGE_PAYLOAD, exception.getError());
    }

    @FunctionalInterface
    private interface ThrowingOperation {
        void run() throws ProtocolException;
    }
}
