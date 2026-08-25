package rs.ac.bg.etf.kdp.common.protocol;

import rs.ac.bg.etf.kdp.common.domain.JobSpecification;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Encodes job submission requests and the identifiers assigned by the server. */
public final class BatchSubmissionProtocolCodec {

    private static final int MAX_JOBS_PER_BATCH = 256;
    private final JobSpecificationProtocolCodec specificationCodec =
            new JobSpecificationProtocolCodec();

    public byte[] encodeSubmission(BatchSubmissionMessage message) {
        Objects.requireNonNull(message, "batch submission must not be null");
        if (message.getSpecifications().size() > MAX_JOBS_PER_BATCH) {
            throw new IllegalArgumentException("Too many jobs in one submission");
        }
        return createPayload(output -> {
            output.writeInt(message.getSpecifications().size());
            for (JobSpecification specification : message.getSpecifications()) {
                specificationCodec.write(output, specification);
            }
        });
    }

    public BatchSubmissionMessage decodeSubmission(byte[] payload) throws ProtocolException {
        Objects.requireNonNull(payload, "batch submission payload must not be null");
        try {
            ByteArrayInputStream bytes = new ByteArrayInputStream(payload);
            DataInputStream input = new DataInputStream(bytes);
            int count = readCount(input);
            List<JobSpecification> specifications = new ArrayList<>(count);
            for (int index = 0; index < count; index++) {
                specifications.add(specificationCodec.read(input));
            }
            requireFullyRead(bytes);
            return new BatchSubmissionMessage(specifications);
        } catch (EOFException exception) {
            throw invalidPayload("Batch submission payload is truncated", exception);
        } catch (IOException | IllegalArgumentException exception) {
            throw invalidPayload("Invalid batch submission payload", exception);
        }
    }

    public byte[] encodeAccepted(BatchAcceptedMessage message) {
        Objects.requireNonNull(message, "accepted batch must not be null");
        if (message.getJobIds().size() > MAX_JOBS_PER_BATCH) {
            throw new IllegalArgumentException("Too many job ids in accepted batch");
        }
        return createPayload(output -> {
            writeUuid(output, message.getBatchId());
            output.writeInt(message.getJobIds().size());
            for (UUID jobId : message.getJobIds()) {
                writeUuid(output, jobId);
            }
        });
    }

    public BatchAcceptedMessage decodeAccepted(byte[] payload) throws ProtocolException {
        Objects.requireNonNull(payload, "accepted batch payload must not be null");
        try {
            ByteArrayInputStream bytes = new ByteArrayInputStream(payload);
            DataInputStream input = new DataInputStream(bytes);
            UUID batchId = readUuid(input);
            int count = readCount(input);
            List<UUID> jobIds = new ArrayList<>(count);
            for (int index = 0; index < count; index++) {
                jobIds.add(readUuid(input));
            }
            requireFullyRead(bytes);
            return new BatchAcceptedMessage(batchId, jobIds);
        } catch (EOFException exception) {
            throw invalidPayload("Accepted batch payload is truncated", exception);
        } catch (IOException | IllegalArgumentException exception) {
            throw invalidPayload("Invalid accepted batch payload", exception);
        }
    }

    private static int readCount(DataInputStream input) throws IOException {
        int count = input.readInt();
        if (count <= 0 || count > MAX_JOBS_PER_BATCH) {
            throw new IllegalArgumentException("Invalid number of jobs: " + count);
        }
        return count;
    }

    private static byte[] createPayload(PayloadWriter writer) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream output = new DataOutputStream(bytes);
            writer.write(output);
            output.flush();
            return bytes.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Could not create batch payload", exception);
        }
    }

    private static void requireFullyRead(ByteArrayInputStream input) {
        if (input.available() != 0) {
            throw new IllegalArgumentException("Batch payload has trailing data");
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
