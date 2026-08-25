package rs.ac.bg.etf.kdp.common.protocol;

import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.UUID;

/** Encodes the job id used when a client aborts or retries a failed job. */
public final class JobDecisionProtocolCodec {

    private static final int UUID_BYTES = 16;

    public byte[] encodeJobReference(UUID jobId) {
        Objects.requireNonNull(jobId, "job id must not be null");
        return ByteBuffer.allocate(UUID_BYTES)
                .putLong(jobId.getMostSignificantBits())
                .putLong(jobId.getLeastSignificantBits())
                .array();
    }

    public UUID decodeJobReference(byte[] payload) throws ProtocolException {
        Objects.requireNonNull(payload, "job reference payload must not be null");
        if (payload.length != UUID_BYTES) {
            throw new ProtocolException(
                    ProtocolError.INVALID_MESSAGE_PAYLOAD,
                    "Job reference must contain exactly one job id");
        }
        ByteBuffer buffer = ByteBuffer.wrap(payload);
        return new UUID(buffer.getLong(), buffer.getLong());
    }
}
