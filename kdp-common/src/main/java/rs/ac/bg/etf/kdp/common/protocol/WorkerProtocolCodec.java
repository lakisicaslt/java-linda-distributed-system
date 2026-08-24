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

/** Encodes the payloads of worker-specific protocol messages. */
public final class WorkerProtocolCodec {

    private static final int MAX_TEXT_LENGTH = 4_096;

    public byte[] encodeRegistration(WorkerRegistrationMessage registration) {
        Objects.requireNonNull(registration, "registration must not be null");
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream output = new DataOutputStream(bytes);
            writeUuid(output, registration.getWorkerId());
            writeString(output, registration.getHostname());
            writeString(output, registration.getOperatingSystem());
            writeString(output, registration.getJavaVersion());
            output.writeInt(registration.getCapacity());
            output.flush();
            return bytes.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Could not create registration payload", exception);
        }
    }

    public WorkerRegistrationMessage decodeRegistration(byte[] payload)
            throws ProtocolException {
        Objects.requireNonNull(payload, "registration payload must not be null");
        try {
            ByteArrayInputStream bytes = new ByteArrayInputStream(payload);
            DataInputStream input = new DataInputStream(bytes);
            WorkerRegistrationMessage registration = new WorkerRegistrationMessage(
                    readUuid(input),
                    readString(input),
                    readString(input),
                    readString(input),
                    input.readInt());
            requireFullyRead(bytes);
            return registration;
        } catch (EOFException exception) {
            throw invalidPayload("Registration payload is truncated", exception);
        } catch (IOException | IllegalArgumentException exception) {
            throw invalidPayload("Invalid worker registration payload", exception);
        }
    }

    public byte[] encodeHeartbeat(UUID workerId) {
        Objects.requireNonNull(workerId, "worker id must not be null");
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream(16);
            DataOutputStream output = new DataOutputStream(bytes);
            writeUuid(output, workerId);
            output.flush();
            return bytes.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Could not create heartbeat payload", exception);
        }
    }

    public UUID decodeHeartbeat(byte[] payload) throws ProtocolException {
        Objects.requireNonNull(payload, "heartbeat payload must not be null");
        if (payload.length != 16) {
            throw invalidPayload("Heartbeat payload must contain exactly one worker id", null);
        }
        try {
            return readUuid(new DataInputStream(new ByteArrayInputStream(payload)));
        } catch (IOException exception) {
            throw invalidPayload("Invalid heartbeat payload", exception);
        }
    }

    private static void writeUuid(DataOutputStream output, UUID id) throws IOException {
        output.writeLong(id.getMostSignificantBits());
        output.writeLong(id.getLeastSignificantBits());
    }

    private static UUID readUuid(DataInputStream input) throws IOException {
        return new UUID(input.readLong(), input.readLong());
    }

    private static void writeString(DataOutputStream output, String value) throws IOException {
        byte[] encoded = value.getBytes(StandardCharsets.UTF_8);
        if (encoded.length > MAX_TEXT_LENGTH) {
            throw new IllegalArgumentException("Worker description is too long");
        }
        output.writeInt(encoded.length);
        output.write(encoded);
    }

    private static String readString(DataInputStream input) throws IOException {
        int length = input.readInt();
        if (length < 0 || length > MAX_TEXT_LENGTH) {
            throw new IllegalArgumentException("Invalid worker description length: " + length);
        }
        byte[] encoded = input.readNBytes(length);
        if (encoded.length != length) {
            throw new EOFException("Worker description is truncated");
        }
        return new String(encoded, StandardCharsets.UTF_8);
    }

    private static void requireFullyRead(ByteArrayInputStream input) {
        if (input.available() != 0) {
            throw new IllegalArgumentException("Registration payload has trailing data");
        }
    }

    private static ProtocolException invalidPayload(String message, Throwable cause) {
        if (cause == null) {
            return new ProtocolException(ProtocolError.INVALID_MESSAGE_PAYLOAD, message);
        }
        return new ProtocolException(ProtocolError.INVALID_MESSAGE_PAYLOAD, message, cause);
    }
}
