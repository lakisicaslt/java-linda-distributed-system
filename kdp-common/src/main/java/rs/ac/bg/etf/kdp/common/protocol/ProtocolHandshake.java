package rs.ac.bg.etf.kdp.common.protocol;

import java.util.Objects;
import java.util.UUID;

public final class ProtocolHandshake {

    public ProtocolFrame createHello(ConnectionRole role) throws ProtocolException {
        Objects.requireNonNull(role, "connection role must not be null");
        return ProtocolFrame.request(MessageType.HELLO, new byte[]{(byte) role.getCode()});
    }

    public ConnectionRole readHello(ProtocolFrame frame) throws ProtocolException {
        Objects.requireNonNull(frame, "hello frame must not be null");
        byte[] payload = frame.getPayload();
        if (frame.getType() != MessageType.HELLO || payload.length != 1) {
            throw new ProtocolException(
                    ProtocolError.INVALID_HANDSHAKE, "Expected a HELLO frame with one role byte");
        }
        return ConnectionRole.fromCode(Byte.toUnsignedInt(payload[0]));
    }

    public ProtocolFrame accept(ProtocolFrame helloFrame) throws ProtocolException {
        readHello(helloFrame);
        return helloFrame.reply(MessageType.HELLO_ACCEPTED, new byte[0]);
    }

    public void verifyAccepted(ProtocolFrame frame, UUID helloRequestId)
            throws ProtocolException {
        Objects.requireNonNull(frame, "handshake response must not be null");
        Objects.requireNonNull(helloRequestId, "hello request id must not be null");
        if (frame.getType() != MessageType.HELLO_ACCEPTED || frame.getPayloadSize() != 0) {
            throw new ProtocolException(
                    ProtocolError.INVALID_HANDSHAKE, "Expected an empty HELLO_ACCEPTED frame");
        }
        if (!frame.getRequestId().equals(helloRequestId)) {
            throw new ProtocolException(
                    ProtocolError.CORRELATION_MISMATCH,
                    "Handshake response has a different request id");
        }
    }
}
