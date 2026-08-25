package rs.ac.bg.etf.kdp.common.protocol;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JobDecisionProtocolCodecTest {

    private final JobDecisionProtocolCodec codec = new JobDecisionProtocolCodec();

    @Test
    void roundTripsJobReference() throws Exception {
        UUID jobId = UUID.randomUUID();

        assertEquals(jobId, codec.decodeJobReference(codec.encodeJobReference(jobId)));
    }

    @Test
    void rejectsPayloadThatDoesNotContainOneUuid() {
        assertThrows(ProtocolException.class, () -> codec.decodeJobReference(new byte[15]));
        assertThrows(ProtocolException.class, () -> codec.decodeJobReference(new byte[17]));
    }
}
