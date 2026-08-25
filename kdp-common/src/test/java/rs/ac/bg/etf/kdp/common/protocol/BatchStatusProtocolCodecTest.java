package rs.ac.bg.etf.kdp.common.protocol;

import org.junit.jupiter.api.Test;
import rs.ac.bg.etf.kdp.common.domain.JobStatus;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BatchStatusProtocolCodecTest {

    private final BatchStatusProtocolCodec codec = new BatchStatusProtocolCodec();

    @Test
    void roundTripsBatchReference() throws Exception {
        UUID batchId = UUID.randomUUID();

        assertEquals(batchId, codec.decodeBatchReference(codec.encodeBatchReference(batchId)));
    }

    @Test
    void roundTripsStatusAndFailureReason() throws Exception {
        BatchStatusMessage original = new BatchStatusMessage(
                UUID.randomUUID(),
                List.of(
                        new JobStatusSnapshot(UUID.randomUUID(), JobStatus.RUNNING, null),
                        new JobStatusSnapshot(
                                UUID.randomUUID(), JobStatus.FAILED, "Worker disconnected")));

        BatchStatusMessage decoded = codec.decodeStatus(codec.encodeStatus(original));

        assertEquals(original.getBatchId(), decoded.getBatchId());
        assertEquals(JobStatus.RUNNING, decoded.getJobs().get(0).getStatus());
        assertEquals(JobStatus.FAILED, decoded.getJobs().get(1).getStatus());
        assertEquals("Worker disconnected",
                decoded.getJobs().get(1).getFailureReason().orElseThrow());
    }

    @Test
    void rejectsTruncatedPayloads() {
        assertThrows(ProtocolException.class, () -> codec.decodeBatchReference(new byte[5]));
        assertThrows(ProtocolException.class, () -> codec.decodeStatus(new byte[10]));
    }
}
