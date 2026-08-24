package rs.ac.bg.etf.kdp.common.protocol;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProtocolHandshakeTest {

    private final ProtocolHandshake handshake = new ProtocolHandshake();

    @Test
    void exchangesRoleAndCorrelatedAcceptance() throws Exception {
        ProtocolFrame hello = handshake.createHello(ConnectionRole.WORKER);

        assertEquals(ConnectionRole.WORKER, handshake.readHello(hello));

        ProtocolFrame accepted = handshake.accept(hello);
        handshake.verifyAccepted(accepted, hello.getRequestId());
        assertEquals(MessageType.HELLO_ACCEPTED, accepted.getType());
        assertEquals(hello.getRequestId(), accepted.getRequestId());
    }

    @Test
    void rejectsUnknownRoleAndMalformedHello() throws Exception {
        ProtocolFrame unknownRole = ProtocolFrame.request(MessageType.HELLO, new byte[]{99});
        ProtocolFrame wrongPayload = ProtocolFrame.request(MessageType.HELLO, new byte[]{1, 2});
        ProtocolFrame wrongType = ProtocolFrame.request(MessageType.HEARTBEAT, new byte[]{1});

        assertError(ProtocolError.INVALID_HANDSHAKE, () -> handshake.readHello(unknownRole));
        assertError(ProtocolError.INVALID_HANDSHAKE, () -> handshake.readHello(wrongPayload));
        assertError(ProtocolError.INVALID_HANDSHAKE, () -> handshake.readHello(wrongType));
    }

    @Test
    void rejectsResponseWithDifferentRequestId() throws Exception {
        ProtocolFrame response = new ProtocolFrame(
                MessageType.HELLO_ACCEPTED, UUID.randomUUID(), new byte[0]);

        assertError(
                ProtocolError.CORRELATION_MISMATCH,
                () -> handshake.verifyAccepted(response, UUID.randomUUID()));
    }

    private static void assertError(ProtocolError error, ThrowingOperation operation) {
        ProtocolException exception = assertThrows(ProtocolException.class, operation::run);
        assertEquals(error, exception.getError());
    }

    @FunctionalInterface
    private interface ThrowingOperation {
        void run() throws ProtocolException;
    }
}
