package rs.ac.bg.etf.kdp.common.protocol;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;

/** Encodes Linda requests and results without Java object serialization. */
public final class LindaProtocolCodec {

    private static final int MAX_TUPLE_FIELDS = 4_096;
    private static final int MAX_FIELD_BYTES = 256 * 1024;

    public byte[] encodeRequest(LindaRequestMessage message) {
        Objects.requireNonNull(message, "Linda request must not be null");
        return createPayload(output -> {
            writeUuid(output, message.getBatchId());
            writeUuid(output, message.getTaskId());
            writeTuple(output, message.getTuple());
        });
    }

    public LindaRequestMessage decodeRequest(byte[] payload) throws ProtocolException {
        Objects.requireNonNull(payload, "Linda request payload must not be null");
        try {
            ByteArrayInputStream bytes = new ByteArrayInputStream(payload);
            DataInputStream input = new DataInputStream(bytes);
            LindaRequestMessage request = new LindaRequestMessage(
                    readUuid(input), readUuid(input), readTuple(input));
            requireFullyRead(bytes);
            return request;
        } catch (EOFException exception) {
            throw invalidPayload("Linda request payload is truncated", exception);
        } catch (IOException | IllegalArgumentException exception) {
            throw invalidPayload("Invalid Linda request payload", exception);
        }
    }

    public byte[] encodeResult(LindaResultMessage message) {
        Objects.requireNonNull(message, "Linda result must not be null");
        return createPayload(output -> {
            output.writeBoolean(message.isMatched());
            if (message.isMatched()) {
                writeTuple(output, message.getTuple());
            }
        });
    }

    public LindaResultMessage decodeResult(byte[] payload) throws ProtocolException {
        Objects.requireNonNull(payload, "Linda result payload must not be null");
        try {
            ByteArrayInputStream bytes = new ByteArrayInputStream(payload);
            DataInputStream input = new DataInputStream(bytes);
            boolean matched = input.readBoolean();
            LindaResultMessage result = new LindaResultMessage(
                    matched, matched ? readTuple(input) : null);
            requireFullyRead(bytes);
            return result;
        } catch (EOFException exception) {
            throw invalidPayload("Linda result payload is truncated", exception);
        } catch (IOException | IllegalArgumentException exception) {
            throw invalidPayload("Invalid Linda result payload", exception);
        }
    }

    private static void writeTuple(DataOutputStream output, String[] tuple) throws IOException {
        if (tuple.length > MAX_TUPLE_FIELDS) {
            throw new IllegalArgumentException("Linda tuple contains too many fields");
        }
        output.writeInt(tuple.length);
        for (String field : tuple) {
            output.writeBoolean(field != null);
            if (field != null) {
                writeString(output, field);
            }
        }
    }

    private static String[] readTuple(DataInputStream input) throws IOException {
        int count = input.readInt();
        if (count < 0 || count > MAX_TUPLE_FIELDS) {
            throw new IllegalArgumentException("Invalid Linda tuple field count: " + count);
        }
        String[] tuple = new String[count];
        for (int field = 0; field < count; field++) {
            if (input.readBoolean()) {
                tuple[field] = readString(input);
            }
        }
        return tuple;
    }

    private static void writeString(DataOutputStream output, String value) throws IOException {
        byte[] encoded = value.getBytes(StandardCharsets.UTF_8);
        if (encoded.length > MAX_FIELD_BYTES) {
            throw new IllegalArgumentException("Linda tuple field is too long");
        }
        output.writeInt(encoded.length);
        output.write(encoded);
    }

    private static String readString(DataInputStream input) throws IOException {
        int length = input.readInt();
        if (length < 0 || length > MAX_FIELD_BYTES) {
            throw new IllegalArgumentException("Invalid Linda tuple field length: " + length);
        }
        byte[] encoded = input.readNBytes(length);
        if (encoded.length != length) {
            throw new EOFException("Linda tuple field is truncated");
        }
        return new String(encoded, StandardCharsets.UTF_8);
    }

    private static byte[] createPayload(PayloadWriter writer) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream output = new DataOutputStream(bytes);
            writer.write(output);
            output.flush();
            byte[] payload = bytes.toByteArray();
            if (payload.length > ProtocolConstants.MAX_CONTROL_PAYLOAD_SIZE) {
                throw new IllegalArgumentException("Linda payload is too large");
            }
            return payload;
        } catch (IOException exception) {
            throw new IllegalStateException("Could not create Linda payload", exception);
        }
    }

    private static void requireFullyRead(ByteArrayInputStream input) {
        if (input.available() != 0) {
            throw new IllegalArgumentException("Linda payload contains trailing data");
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

    @FunctionalInterface
    private interface PayloadWriter {
        void write(DataOutputStream output) throws IOException;
    }
}
