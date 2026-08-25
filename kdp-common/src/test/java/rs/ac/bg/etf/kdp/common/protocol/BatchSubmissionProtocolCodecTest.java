package rs.ac.bg.etf.kdp.common.protocol;

import org.junit.jupiter.api.Test;
import rs.ac.bg.etf.kdp.common.domain.JobSpecification;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BatchSubmissionProtocolCodecTest {

    private final BatchSubmissionProtocolCodec codec = new BatchSubmissionProtocolCodec();

    @Test
    void roundTripsJobSpecifications() throws Exception {
        JobSpecification first = new JobSpecification(
                "first.jar",
                List.of("-Xmx512m"),
                List.of("one", "two"),
                List.of("input.txt"),
                List.of("result.txt"));
        JobSpecification second = new JobSpecification(
                "second.jar", List.of(), List.of(), List.of(), List.of());

        BatchSubmissionMessage decoded = codec.decodeSubmission(codec.encodeSubmission(
                new BatchSubmissionMessage(List.of(first, second))));

        assertEquals(2, decoded.getSpecifications().size());
        assertEquals("first.jar", decoded.getSpecifications().get(0).getJarFileName());
        assertEquals(List.of("-Xmx512m"),
                decoded.getSpecifications().get(0).getJvmArguments());
        assertEquals("second.jar", decoded.getSpecifications().get(1).getJarFileName());
    }

    @Test
    void roundTripsAssignedIdentifiers() throws Exception {
        BatchAcceptedMessage accepted = new BatchAcceptedMessage(
                UUID.randomUUID(), List.of(UUID.randomUUID(), UUID.randomUUID()));

        BatchAcceptedMessage decoded = codec.decodeAccepted(codec.encodeAccepted(accepted));

        assertEquals(accepted.getBatchId(), decoded.getBatchId());
        assertEquals(accepted.getJobIds(), decoded.getJobIds());
    }

    @Test
    void rejectsEmptyAndTruncatedPayloads() {
        assertThrows(ProtocolException.class, () -> codec.decodeSubmission(new byte[0]));
        assertThrows(ProtocolException.class, () -> codec.decodeAccepted(new byte[10]));
    }
}
