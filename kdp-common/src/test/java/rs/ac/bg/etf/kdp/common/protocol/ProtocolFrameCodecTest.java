package rs.ac.bg.etf.kdp.common.protocol;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProtocolFrameCodecTest {

    private final ProtocolFrameCodec codec = new ProtocolFrameCodec();

    @Test
    void writesAndReadsCompleteFrame() throws Exception {
        UUID requestId = UUID.randomUUID();
        byte[] payload = {10, 20, 30};
        ProtocolFrame original = new ProtocolFrame(MessageType.HEARTBEAT, requestId, payload);
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        codec.write(output, original);
        ProtocolFrame decoded = codec.read(new ByteArrayInputStream(output.toByteArray()));

        assertEquals(MessageType.HEARTBEAT, decoded.getType());
        assertEquals(requestId, decoded.getRequestId());
        assertArrayEquals(payload, decoded.getPayload());
        assertEquals(ProtocolConstants.HEADER_SIZE + payload.length, output.size());
    }

    @Test
    void readsConsecutiveFramesWithoutLosingBoundaries() throws Exception {
        ProtocolFrame first = ProtocolFrame.request(MessageType.HEARTBEAT, new byte[]{1});
        ProtocolFrame second = ProtocolFrame.request(MessageType.GET_BATCH_STATUS, new byte[]{2, 3});
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        codec.write(output, first);
        codec.write(output, second);

        ByteArrayInputStream input = new ByteArrayInputStream(output.toByteArray());
        ProtocolFrame decodedFirst = codec.read(input);
        ProtocolFrame decodedSecond = codec.read(input);

        assertEquals(first.getRequestId(), decodedFirst.getRequestId());
        assertEquals(second.getRequestId(), decodedSecond.getRequestId());
        assertArrayEquals(new byte[]{2, 3}, decodedSecond.getPayload());
    }

    @Test
    void protectsPayloadFromExternalModification() throws Exception {
        byte[] source = {1, 2, 3};
        ProtocolFrame frame = ProtocolFrame.request(MessageType.HEARTBEAT, source);
        source[0] = 99;
        byte[] returnedPayload = frame.getPayload();
        returnedPayload[1] = 99;

        assertArrayEquals(new byte[]{1, 2, 3}, frame.getPayload());
        assertNotSame(returnedPayload, frame.getPayload());
    }

    @Test
    void rejectsTelnetAndHttpTrafficAsInvalidMagic() {
        assertProtocolError(ProtocolError.INVALID_MAGIC, "nonsense".getBytes());
        assertProtocolError(ProtocolError.INVALID_MAGIC, "HTTP/1.1 200 OK\r\n".getBytes());
    }

    @Test
    void rejectsUnsupportedVersion() throws Exception {
        byte[] bytes = rawFrame(
                ProtocolConstants.MAGIC,
                ProtocolConstants.VERSION + 1,
                MessageType.HELLO.getCode(),
                0,
                new byte[0]);

        assertProtocolError(ProtocolError.UNSUPPORTED_VERSION, bytes);
    }

    @Test
    void rejectsUnknownMessageType() throws Exception {
        byte[] bytes = rawFrame(
                ProtocolConstants.MAGIC,
                ProtocolConstants.VERSION,
                65_000,
                0,
                new byte[0]);

        assertProtocolError(ProtocolError.UNKNOWN_MESSAGE_TYPE, bytes);
    }

    @Test
    void rejectsNegativeAndOversizedPayloadBeforeAllocation() throws Exception {
        byte[] negative = rawFrame(
                ProtocolConstants.MAGIC,
                ProtocolConstants.VERSION,
                MessageType.HEARTBEAT.getCode(),
                -1,
                new byte[0]);
        byte[] oversized = rawFrame(
                ProtocolConstants.MAGIC,
                ProtocolConstants.VERSION,
                MessageType.HEARTBEAT.getCode(),
                ProtocolConstants.MAX_CONTROL_PAYLOAD_SIZE + 1,
                new byte[0]);

        assertProtocolError(ProtocolError.INVALID_PAYLOAD_LENGTH, negative);
        assertProtocolError(ProtocolError.INVALID_PAYLOAD_LENGTH, oversized);
    }

    @Test
    void rejectsTruncatedPayload() throws Exception {
        byte[] bytes = rawFrame(
                ProtocolConstants.MAGIC,
                ProtocolConstants.VERSION,
                MessageType.HEARTBEAT.getCode(),
                10,
                new byte[]{1, 2});

        assertProtocolError(ProtocolError.TRUNCATED_FRAME, bytes);
    }

    @Test
    void limitsFileChunksToSixtyFourKibibytes() throws Exception {
        ProtocolFrame maximumChunk = ProtocolFrame.request(
                MessageType.FILE_CHUNK,
                new byte[ProtocolConstants.FILE_CHUNK_SIZE]);
        assertEquals(ProtocolConstants.FILE_CHUNK_SIZE, maximumChunk.getPayloadSize());

        ProtocolException exception = assertThrows(ProtocolException.class, () ->
                ProtocolFrame.request(
                        MessageType.FILE_CHUNK,
                        new byte[ProtocolConstants.FILE_CHUNK_SIZE + 1]));
        assertEquals(ProtocolError.INVALID_PAYLOAD_LENGTH, exception.getError());
    }

    private void assertProtocolError(ProtocolError expectedError, byte[] bytes) {
        ProtocolException exception = assertThrows(
                ProtocolException.class,
                () -> codec.read(new ByteArrayInputStream(bytes)));
        assertEquals(expectedError, exception.getError());
    }

    private static byte[] rawFrame(
            int magic,
            int version,
            int messageType,
            int declaredPayloadSize,
            byte[] actualPayload) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        DataOutputStream data = new DataOutputStream(output);
        UUID requestId = UUID.randomUUID();
        data.writeInt(magic);
        data.writeShort(version);
        data.writeShort(messageType);
        data.writeLong(requestId.getMostSignificantBits());
        data.writeLong(requestId.getLeastSignificantBits());
        data.writeInt(declaredPayloadSize);
        data.write(actualPayload);
        return output.toByteArray();
    }
}
