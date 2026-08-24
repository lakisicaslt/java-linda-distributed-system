package rs.ac.bg.etf.kdp.common.protocol;

public final class ProtocolLimits {

    private ProtocolLimits() {
    }

    public static int maximumPayloadSize(MessageType type) {
        return type == MessageType.FILE_CHUNK
                ? ProtocolConstants.FILE_CHUNK_SIZE
                : ProtocolConstants.MAX_CONTROL_PAYLOAD_SIZE;
    }

    public static void validatePayloadSize(MessageType type, int payloadSize)
            throws ProtocolException {
        if (payloadSize < 0 || payloadSize > maximumPayloadSize(type)) {
            throw new ProtocolException(
                    ProtocolError.INVALID_PAYLOAD_LENGTH,
                    "Invalid payload size " + payloadSize + " for " + type);
        }
    }
}
