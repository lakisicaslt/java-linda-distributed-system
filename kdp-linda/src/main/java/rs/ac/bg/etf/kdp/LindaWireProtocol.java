package rs.ac.bg.etf.kdp;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** Minimal standalone implementation of the KDP wire protocol used by the Linda JAR. */
final class LindaWireProtocol {

    private static final int MAGIC = 0x4B445031;
    private static final int VERSION = 1;
    private static final int HELLO = 1;
    private static final int HELLO_ACCEPTED = 2;
    private static final int ERROR = 3;
    private static final int LINDA_RESULT = 406;
    private static final int LINDA_ROLE = 3;
    private static final int MAX_PAYLOAD_SIZE = 1024 * 1024;
    private static final int MAX_TUPLE_FIELDS = 4_096;
    private static final int MAX_FIELD_BYTES = 256 * 1024;
    private static final int CONNECT_TIMEOUT_MILLIS = 5_000;
    private static final int HANDSHAKE_TIMEOUT_MILLIS = 5_000;

    private LindaWireProtocol() {
    }

    static Result execute(
            String host,
            int port,
            UUID batchId,
            UUID taskId,
            Operation operation,
            String[] tuple) throws IOException {
        return send(host, port, operation.code, encodeRequest(batchId, taskId, tuple));
    }

    static void eval(
            String host,
            int port,
            UUID batchId,
            UUID parentTaskId,
            String name,
            byte[] serializedRunnable) throws IOException {
        Result result = send(
                host,
                port,
                Operation.EVAL.code,
                encodeEvalRequest(batchId, parentTaskId, name, serializedRunnable));
        if (!result.matched() || result.tuple() == null || result.tuple().length != 0) {
            throw new IOException("Server returned an invalid Linda eval acknowledgement");
        }
    }

    private static Result send(String host, int port, int operation, byte[] requestPayload)
            throws IOException {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), CONNECT_TIMEOUT_MILLIS);
            socket.setTcpNoDelay(true);
            socket.setKeepAlive(true);
            socket.setSoTimeout(HANDSHAKE_TIMEOUT_MILLIS);

            DataInputStream input = new DataInputStream(socket.getInputStream());
            DataOutputStream output = new DataOutputStream(socket.getOutputStream());
            UUID helloId = UUID.randomUUID();
            writeFrame(output, HELLO, helloId, new byte[]{LINDA_ROLE});
            Frame helloResponse = readFrame(input);
            requireResponse(helloResponse, HELLO_ACCEPTED, helloId);

            socket.setSoTimeout(0);
            UUID requestId = UUID.randomUUID();
            writeFrame(output, operation, requestId, requestPayload);
            Frame response = readFrame(input);
            if (response.type == ERROR) {
                throw new IOException(new String(response.payload, StandardCharsets.UTF_8));
            }
            requireResponse(response, LINDA_RESULT, requestId);
            return decodeResult(response.payload);
        }
    }

    private static byte[] encodeEvalRequest(
            UUID batchId,
            UUID parentTaskId,
            String name,
            byte[] serializedRunnable) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream output = new DataOutputStream(bytes);
        writeUuid(output, batchId);
        writeUuid(output, parentTaskId);
        writeString(output, name, 4_096, "eval name");
        output.writeInt(serializedRunnable.length);
        output.write(serializedRunnable);
        output.flush();
        byte[] payload = bytes.toByteArray();
        if (payload.length > MAX_PAYLOAD_SIZE) {
            throw new IllegalArgumentException("Linda eval request is too large");
        }
        return payload;
    }

    private static byte[] encodeRequest(UUID batchId, UUID taskId, String[] tuple)
            throws IOException {
        if (tuple.length > MAX_TUPLE_FIELDS) {
            throw new IllegalArgumentException("Linda tuple contains too many fields");
        }
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream output = new DataOutputStream(bytes);
        writeUuid(output, batchId);
        writeUuid(output, taskId);
        writeTuple(output, tuple);
        output.flush();
        byte[] payload = bytes.toByteArray();
        if (payload.length > MAX_PAYLOAD_SIZE) {
            throw new IllegalArgumentException("Linda request is too large");
        }
        return payload;
    }

    private static Result decodeResult(byte[] payload) throws IOException {
        ByteArrayInputStream bytes = new ByteArrayInputStream(payload);
        DataInputStream input = new DataInputStream(bytes);
        boolean matched = input.readBoolean();
        String[] tuple = matched ? readTuple(input) : null;
        if (bytes.available() != 0) {
            throw new IOException("Linda result contains trailing data");
        }
        return new Result(matched, tuple);
    }

    private static void writeTuple(DataOutputStream output, String[] tuple) throws IOException {
        output.writeInt(tuple.length);
        for (String field : tuple) {
            output.writeBoolean(field != null);
            if (field != null) {
                writeString(output, field, MAX_FIELD_BYTES, "Linda tuple field");
            }
        }
    }

    private static void writeString(
            DataOutputStream output,
            String value,
            int maximumLength,
            String fieldName) throws IOException {
        byte[] encoded = value.getBytes(StandardCharsets.UTF_8);
        if (encoded.length > maximumLength) {
            throw new IllegalArgumentException(fieldName + " is too long");
        }
        output.writeInt(encoded.length);
        output.write(encoded);
    }

    private static String[] readTuple(DataInputStream input) throws IOException {
        int count = input.readInt();
        if (count < 0 || count > MAX_TUPLE_FIELDS) {
            throw new IOException("Invalid Linda tuple field count: " + count);
        }
        String[] tuple = new String[count];
        for (int field = 0; field < count; field++) {
            if (input.readBoolean()) {
                int length = input.readInt();
                if (length < 0 || length > MAX_FIELD_BYTES) {
                    throw new IOException("Invalid Linda tuple field length: " + length);
                }
                byte[] encoded = input.readNBytes(length);
                if (encoded.length != length) {
                    throw new EOFException("Linda tuple field is truncated");
                }
                tuple[field] = new String(encoded, StandardCharsets.UTF_8);
            }
        }
        return tuple;
    }

    private static void writeFrame(
            DataOutputStream output,
            int type,
            UUID requestId,
            byte[] payload) throws IOException {
        output.writeInt(MAGIC);
        output.writeShort(VERSION);
        output.writeShort(type);
        writeUuid(output, requestId);
        output.writeInt(payload.length);
        output.write(payload);
        output.flush();
    }

    private static Frame readFrame(DataInputStream input) throws IOException {
        int magic = input.readInt();
        if (magic != MAGIC) {
            throw new IOException("Peer does not use the KDP protocol");
        }
        int version = input.readUnsignedShort();
        if (version != VERSION) {
            throw new IOException("Unsupported KDP protocol version: " + version);
        }
        int type = input.readUnsignedShort();
        UUID requestId = readUuid(input);
        int payloadSize = input.readInt();
        if (payloadSize < 0 || payloadSize > MAX_PAYLOAD_SIZE) {
            throw new IOException("Invalid KDP payload size: " + payloadSize);
        }
        byte[] payload = input.readNBytes(payloadSize);
        if (payload.length != payloadSize) {
            throw new EOFException("KDP frame is truncated");
        }
        return new Frame(type, requestId, payload);
    }

    private static void requireResponse(Frame response, int type, UUID requestId)
            throws IOException {
        if (response.type != type) {
            throw new IOException("Unexpected KDP response type: " + response.type);
        }
        if (!response.requestId.equals(requestId)) {
            throw new IOException("KDP response has a different request id");
        }
    }

    private static void writeUuid(DataOutputStream output, UUID id) throws IOException {
        output.writeLong(id.getMostSignificantBits());
        output.writeLong(id.getLeastSignificantBits());
    }

    private static UUID readUuid(DataInputStream input) throws IOException {
        return new UUID(input.readLong(), input.readLong());
    }

    enum Operation {
        OUT(400),
        IN(401),
        INP(402),
        RD(403),
        RDP(404),
        EVAL(405);

        private final int code;

        Operation(int code) {
            this.code = code;
        }
    }

    record Result(boolean matched, String[] tuple) {
    }

    private record Frame(int type, UUID requestId, byte[] payload) {
    }
}
