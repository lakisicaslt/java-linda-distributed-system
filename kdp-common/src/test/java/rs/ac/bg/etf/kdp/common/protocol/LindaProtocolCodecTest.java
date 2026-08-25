package rs.ac.bg.etf.kdp.common.protocol;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LindaProtocolCodecTest {

    private final LindaProtocolCodec codec = new LindaProtocolCodec();

    @Test
    void roundTripsRequestWithWildcardAndUnicodeFields() throws Exception {
        LindaRequestMessage original = new LindaRequestMessage(
                UUID.randomUUID(), UUID.randomUUID(), new String[]{"rezultat", null, "čćž"});

        LindaRequestMessage decoded = codec.decodeRequest(codec.encodeRequest(original));

        assertEquals(original.getBatchId(), decoded.getBatchId());
        assertEquals(original.getTaskId(), decoded.getTaskId());
        assertArrayEquals(original.getTuple(), decoded.getTuple());
    }

    @Test
    void roundTripsMatchedAndUnmatchedResults() throws Exception {
        LindaResultMessage matched = codec.decodeResult(codec.encodeResult(
                new LindaResultMessage(true, new String[]{"value", "42"})));
        LindaResultMessage unmatched = codec.decodeResult(codec.encodeResult(
                new LindaResultMessage(false, null)));

        assertTrue(matched.isMatched());
        assertArrayEquals(new String[]{"value", "42"}, matched.getTuple());
        assertFalse(unmatched.isMatched());
    }

    @Test
    void rejectsTruncatedAndTrailingPayloads() {
        byte[] valid = codec.encodeResult(new LindaResultMessage(false, null));
        byte[] trailing = {valid[0], 1};

        assertThrows(ProtocolException.class, () -> codec.decodeRequest(new byte[10]));
        assertThrows(ProtocolException.class, () -> codec.decodeResult(trailing));
    }
}
