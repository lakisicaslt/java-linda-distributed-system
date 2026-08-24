package rs.ac.bg.etf.kdp.common.protocol;

import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;

public final class ProtocolFrame {

    private final MessageType type;
    private final UUID requestId;
    private final byte[] payload;

    public ProtocolFrame(MessageType type, UUID requestId, byte[] payload)
            throws ProtocolException {
        this.type = Objects.requireNonNull(type, "message type must not be null");
        this.requestId = Objects.requireNonNull(requestId, "request id must not be null");
        Objects.requireNonNull(payload, "payload must not be null");
        ProtocolLimits.validatePayloadSize(type, payload.length);
        this.payload = Arrays.copyOf(payload, payload.length);
    }

    public static ProtocolFrame request(MessageType type, byte[] payload)
            throws ProtocolException {
        return new ProtocolFrame(type, UUID.randomUUID(), payload);
    }

    public ProtocolFrame reply(MessageType responseType, byte[] responsePayload)
            throws ProtocolException {
        return new ProtocolFrame(responseType, requestId, responsePayload);
    }

    public MessageType getType() {
        return type;
    }

    public UUID getRequestId() {
        return requestId;
    }

    public byte[] getPayload() {
        return Arrays.copyOf(payload, payload.length);
    }

    public int getPayloadSize() {
        return payload.length;
    }
}
