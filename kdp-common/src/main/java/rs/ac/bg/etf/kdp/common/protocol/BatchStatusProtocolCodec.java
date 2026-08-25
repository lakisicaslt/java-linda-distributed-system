package rs.ac.bg.etf.kdp.common.protocol;

import rs.ac.bg.etf.kdp.common.domain.JobStatus;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Encodes batch status requests and server snapshots. */
public final class BatchStatusProtocolCodec {

    private static final int MAX_JOBS_PER_BATCH = 256;
    private static final int MAX_FAILURE_REASON_BYTES = 4_096;

    public byte[] encodeBatchReference(UUID batchId) {
        Objects.requireNonNull(batchId, "batch id must not be null");
        return createPayload(output -> writeUuid(output, batchId));
    }

    public UUID decodeBatchReference(byte[] payload) throws ProtocolException {
        Objects.requireNonNull(payload, "batch reference payload must not be null");
        try {
            ByteArrayInputStream bytes = new ByteArrayInputStream(payload);
            UUID batchId = readUuid(new DataInputStream(bytes));
            requireFullyRead(bytes);
            return batchId;
        } catch (EOFException exception) {
            throw invalidPayload("Batch reference payload is truncated", exception);
        } catch (IOException | IllegalArgumentException exception) {
            throw invalidPayload("Invalid batch reference payload", exception);
        }
    }

    public byte[] encodeStatus(BatchStatusMessage message) {
        Objects.requireNonNull(message, "batch status must not be null");
        if (message.getJobs().size() > MAX_JOBS_PER_BATCH) {
            throw new IllegalArgumentException("Too many jobs in batch status");
        }
        return createPayload(output -> {
            writeUuid(output, message.getBatchId());
            output.writeInt(message.getJobs().size());
            for (JobStatusSnapshot job : message.getJobs()) {
                writeUuid(output, job.getJobId());
                output.writeByte(statusCode(job.getStatus()));
                output.writeBoolean(job.getFailureReason().isPresent());
                if (job.getFailureReason().isPresent()) {
                    writeString(output, job.getFailureReason().orElseThrow());
                }
            }
        });
    }

    public BatchStatusMessage decodeStatus(byte[] payload) throws ProtocolException {
        Objects.requireNonNull(payload, "batch status payload must not be null");
        try {
            ByteArrayInputStream bytes = new ByteArrayInputStream(payload);
            DataInputStream input = new DataInputStream(bytes);
            UUID batchId = readUuid(input);
            int count = input.readInt();
            if (count <= 0 || count > MAX_JOBS_PER_BATCH) {
                throw new IllegalArgumentException("Invalid number of job statuses: " + count);
            }

            List<JobStatusSnapshot> jobs = new ArrayList<>(count);
            for (int index = 0; index < count; index++) {
                UUID jobId = readUuid(input);
                JobStatus status = statusFromCode(input.readUnsignedByte());
                String failureReason = input.readBoolean() ? readString(input) : null;
                jobs.add(new JobStatusSnapshot(jobId, status, failureReason));
            }
            requireFullyRead(bytes);
            return new BatchStatusMessage(batchId, jobs);
        } catch (EOFException exception) {
            throw invalidPayload("Batch status payload is truncated", exception);
        } catch (IOException | IllegalArgumentException exception) {
            throw invalidPayload("Invalid batch status payload", exception);
        }
    }

    private static int statusCode(JobStatus status) {
        return switch (status) {
            case READY -> 1;
            case SCHEDULED -> 2;
            case RUNNING -> 3;
            case DONE -> 4;
            case FAILED -> 5;
            case ABORTED -> 6;
        };
    }

    private static JobStatus statusFromCode(int code) {
        return switch (code) {
            case 1 -> JobStatus.READY;
            case 2 -> JobStatus.SCHEDULED;
            case 3 -> JobStatus.RUNNING;
            case 4 -> JobStatus.DONE;
            case 5 -> JobStatus.FAILED;
            case 6 -> JobStatus.ABORTED;
            default -> throw new IllegalArgumentException("Unknown job status code: " + code);
        };
    }

    private static void writeString(DataOutputStream output, String value) throws IOException {
        byte[] encoded = value.getBytes(StandardCharsets.UTF_8);
        if (encoded.length > MAX_FAILURE_REASON_BYTES) {
            throw new IllegalArgumentException("Failure reason is too long");
        }
        output.writeInt(encoded.length);
        output.write(encoded);
    }

    private static String readString(DataInputStream input) throws IOException {
        int length = input.readInt();
        if (length < 0 || length > MAX_FAILURE_REASON_BYTES) {
            throw new IllegalArgumentException("Invalid failure reason length: " + length);
        }
        byte[] encoded = input.readNBytes(length);
        if (encoded.length != length) {
            throw new EOFException("Failure reason is truncated");
        }
        return new String(encoded, StandardCharsets.UTF_8);
    }

    private static byte[] createPayload(PayloadWriter writer) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream output = new DataOutputStream(bytes);
            writer.write(output);
            output.flush();
            return bytes.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Could not create batch status payload", exception);
        }
    }

    private static void requireFullyRead(ByteArrayInputStream input) {
        if (input.available() != 0) {
            throw new IllegalArgumentException("Batch status payload has trailing data");
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
