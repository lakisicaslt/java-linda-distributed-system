package rs.ac.bg.etf.kdp.common.protocol;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BatchResultProtocolCodecTest {

    private final BatchResultProtocolCodec codec = new BatchResultProtocolCodec();

    @Test
    void roundTripsResultManifest() throws Exception {
        BatchResultMessage original = new BatchResultMessage(
                UUID.randomUUID(),
                List.of(
                        new BatchResultFile(
                                UUID.randomUUID(),
                                UUID.randomUUID(),
                                TransferFileKind.OUTPUT,
                                "result.txt",
                                70_000),
                        new BatchResultFile(
                                UUID.randomUUID(),
                                UUID.randomUUID(),
                                TransferFileKind.STANDARD_ERROR,
                                "stderr.txt",
                                12)));

        BatchResultMessage decoded = codec.decode(codec.encode(original));

        assertEquals(original.getBatchId(), decoded.getBatchId());
        assertEquals(2, decoded.getFiles().size());
        assertEquals("result.txt", decoded.getFiles().get(0).getFileName());
        assertEquals(TransferFileKind.STANDARD_ERROR,
                decoded.getFiles().get(1).getKind());
        assertEquals(12, decoded.getFiles().get(1).getSize());
    }

    @Test
    void acceptsEmptyManifestAndRejectsTruncatedPayload() throws Exception {
        BatchResultMessage empty = new BatchResultMessage(UUID.randomUUID(), List.of());

        assertEquals(0, codec.decode(codec.encode(empty)).getFiles().size());
        assertThrows(ProtocolException.class, () -> codec.decode(new byte[8]));
    }
}
