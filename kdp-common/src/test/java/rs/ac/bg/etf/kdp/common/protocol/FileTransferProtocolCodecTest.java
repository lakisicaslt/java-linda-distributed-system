package rs.ac.bg.etf.kdp.common.protocol;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FileTransferProtocolCodecTest {

    private final FileTransferProtocolCodec codec = new FileTransferProtocolCodec();

    @Test
    void roundTripsFileMetadata() throws Exception {
        UUID ownerId = UUID.randomUUID();
        FileTransferMetadata metadata = new FileTransferMetadata(
                ownerId, TransferFileKind.INPUT, "ulaz-č.txt", 75_000_000L);

        FileTransferMetadata decoded = codec.decodeBegin(codec.encodeBegin(metadata));

        assertEquals(ownerId, decoded.getOwnerId());
        assertEquals(TransferFileKind.INPUT, decoded.getKind());
        assertEquals("ulaz-č.txt", decoded.getFileName());
        assertEquals(75_000_000L, decoded.getSize());
    }

    @Test
    void rejectsTruncatedAndExtendedMetadata() {
        FileTransferMetadata metadata = new FileTransferMetadata(
                UUID.randomUUID(), TransferFileKind.JOB_ARCHIVE, "job.jar", 100);
        byte[] valid = codec.encodeBegin(metadata);

        assertInvalidPayload(() -> codec.decodeBegin(Arrays.copyOf(valid, valid.length - 1)));
        assertInvalidPayload(() -> codec.decodeBegin(Arrays.copyOf(valid, valid.length + 1)));
    }

    @Test
    void rejectsUnsafeFileNamesAndNegativeSize() {
        UUID ownerId = UUID.randomUUID();

        assertThrows(IllegalArgumentException.class, () -> new FileTransferMetadata(
                ownerId, TransferFileKind.INPUT, "../secret.txt", 1));
        assertThrows(IllegalArgumentException.class, () -> new FileTransferMetadata(
                ownerId, TransferFileKind.INPUT, "folder\\secret.txt", 1));
        assertThrows(IllegalArgumentException.class, () -> new FileTransferMetadata(
                ownerId, TransferFileKind.INPUT, "input.txt", -1));
    }

    @Test
    void verifiesCorrelatedEmptyAcknowledgement() throws Exception {
        UUID transferId = UUID.randomUUID();
        ProtocolFrame valid = new ProtocolFrame(
                MessageType.FILE_ACKNOWLEDGED, transferId, new byte[0]);
        codec.verifyAcknowledgement(valid, transferId);

        ProtocolException mismatch = assertThrows(ProtocolException.class, () ->
                codec.verifyAcknowledgement(valid, UUID.randomUUID()));
        assertEquals(ProtocolError.CORRELATION_MISMATCH, mismatch.getError());
        assertInvalidPayload(() -> codec.verifyAcknowledgement(
                new ProtocolFrame(MessageType.FILE_ACKNOWLEDGED, transferId, new byte[]{1}),
                transferId));
    }

    private static void assertInvalidPayload(ThrowingOperation operation) {
        ProtocolException exception = assertThrows(ProtocolException.class, operation::run);
        assertEquals(ProtocolError.INVALID_MESSAGE_PAYLOAD, exception.getError());
    }

    @FunctionalInterface
    private interface ThrowingOperation {
        void run() throws Exception;
    }
}
