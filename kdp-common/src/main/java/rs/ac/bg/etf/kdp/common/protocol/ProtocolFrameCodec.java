package rs.ac.bg.etf.kdp.common.protocol;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Objects;
import java.util.UUID;

public final class ProtocolFrameCodec {

    public void write(OutputStream output, ProtocolFrame frame) throws IOException {
        Objects.requireNonNull(output, "output stream must not be null");
        Objects.requireNonNull(frame, "protocol frame must not be null");

        DataOutputStream data = new DataOutputStream(output);
        UUID requestId = frame.getRequestId();
        byte[] payload = frame.getPayload();

        data.writeInt(ProtocolConstants.MAGIC);
        data.writeShort(ProtocolConstants.VERSION);
        data.writeShort(frame.getType().getCode());
        data.writeLong(requestId.getMostSignificantBits());
        data.writeLong(requestId.getLeastSignificantBits());
        data.writeInt(payload.length);
        data.write(payload);
        data.flush();
    }

    public ProtocolFrame read(InputStream input) throws IOException {
        Objects.requireNonNull(input, "input stream must not be null");
        DataInputStream data = new DataInputStream(input);

        try {
            int magic = data.readInt();
            if (magic != ProtocolConstants.MAGIC) {
                throw new ProtocolException(
                        ProtocolError.INVALID_MAGIC, "Peer does not use the KDP protocol");
            }

            int version = data.readUnsignedShort();
            if (version != ProtocolConstants.VERSION) {
                throw new ProtocolException(
                        ProtocolError.UNSUPPORTED_VERSION,
                        "Unsupported protocol version: " + version);
            }

            MessageType type = MessageType.fromCode(data.readUnsignedShort());
            UUID requestId = new UUID(data.readLong(), data.readLong());
            int payloadSize = data.readInt();
            ProtocolLimits.validatePayloadSize(type, payloadSize);

            byte[] payload = data.readNBytes(payloadSize);
            if (payload.length != payloadSize) {
                throw new ProtocolException(
                        ProtocolError.TRUNCATED_FRAME,
                        "Connection closed before the complete payload was received");
            }
            return new ProtocolFrame(type, requestId, payload);
        } catch (EOFException exception) {
            throw new ProtocolException(
                    ProtocolError.TRUNCATED_FRAME,
                    "Connection closed in the middle of a protocol frame",
                    exception);
        }
    }
}
