package rs.ac.bg.etf.kdp.common.protocol;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WorkerProtocolCodecTest {

    private final WorkerProtocolCodec codec = new WorkerProtocolCodec();

    @Test
    void roundTripsWorkerRegistration() throws Exception {
        UUID workerId = UUID.randomUUID();
        WorkerRegistrationMessage original = new WorkerRegistrationMessage(
                workerId, "računar-1", "Windows 11", "21.0.2", 4);

        WorkerRegistrationMessage decoded = codec.decodeRegistration(
                codec.encodeRegistration(original));

        assertEquals(workerId, decoded.getWorkerId());
        assertEquals("računar-1", decoded.getHostname());
        assertEquals("Windows 11", decoded.getOperatingSystem());
        assertEquals("21.0.2", decoded.getJavaVersion());
        assertEquals(4, decoded.getCapacity());
    }

    @Test
    void roundTripsHeartbeat() throws Exception {
        UUID workerId = UUID.randomUUID();

        assertEquals(workerId, codec.decodeHeartbeat(codec.encodeHeartbeat(workerId)));
    }

    @Test
    void rejectsTruncatedAndExtendedHeartbeat() {
        assertInvalidPayload(() -> codec.decodeHeartbeat(new byte[15]));
        assertInvalidPayload(() -> codec.decodeHeartbeat(new byte[17]));
    }

    @Test
    void rejectsTruncatedRegistration() {
        WorkerRegistrationMessage registration = new WorkerRegistrationMessage(
                UUID.randomUUID(), "host", "os", "java", 1);
        byte[] valid = codec.encodeRegistration(registration);

        assertInvalidPayload(() -> codec.decodeRegistration(
                Arrays.copyOf(valid, valid.length - 1)));
    }

    @Test
    void rejectsInvalidTextLengthAndTrailingData() {
        byte[] invalidLength = ByteBuffer.allocate(20)
                .putLong(1L)
                .putLong(2L)
                .putInt(-1)
                .array();
        WorkerRegistrationMessage registration = new WorkerRegistrationMessage(
                UUID.randomUUID(), "host", "os", "java", 1);
        byte[] valid = codec.encodeRegistration(registration);
        byte[] trailingData = Arrays.copyOf(valid, valid.length + 1);

        assertInvalidPayload(() -> codec.decodeRegistration(invalidLength));
        assertInvalidPayload(() -> codec.decodeRegistration(trailingData));
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
