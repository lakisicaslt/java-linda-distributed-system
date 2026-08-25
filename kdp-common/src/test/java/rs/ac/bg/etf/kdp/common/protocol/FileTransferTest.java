package rs.ac.bg.etf.kdp.common.protocol;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileTransferTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void streamsFileInBoundedChunksAndAcknowledgesCompletion() throws Exception {
        byte[] content = new byte[ProtocolConstants.FILE_CHUNK_SIZE * 2 + 123];
        for (int index = 0; index < content.length; index++) {
            content[index] = (byte) (index % 251);
        }
        Path source = temporaryDirectory.resolve("source.bin");
        Path destination = temporaryDirectory.resolve("received");
        Files.write(source, content);
        FileTransferMetadata metadata = new FileTransferMetadata(
                UUID.randomUUID(), TransferFileKind.INPUT, "input.bin", content.length);
        List<Integer> chunkSizes = new ArrayList<>();
        List<ReceivedFile> completed = new ArrayList<>();

        try (FileTransferReceiver receiver = new FileTransferReceiver(destination)) {
            UUID transferId = new FileTransferSender().send(source, metadata, frame -> {
                if (frame.getType() == MessageType.FILE_CHUNK) {
                    chunkSizes.add(frame.getPayloadSize());
                }
                Optional<ReceivedFile> result = receiver.accept(frame);
                result.ifPresent(completed::add);
            });

            assertEquals(List.of(65_536, 65_536, 123), chunkSizes);
            assertEquals(1, completed.size());
            ReceivedFile received = completed.get(0);
            assertEquals(destination.resolve("input.bin").toAbsolutePath(), received.getPath());
            assertArrayEquals(content, Files.readAllBytes(received.getPath()));
            new FileTransferProtocolCodec().verifyAcknowledgement(
                    received.getAcknowledgement(), transferId);
            assertFalse(receiver.isReceiving());
        }
    }

    @Test
    void deletesPartialFileWhenTransferIsInterrupted() throws Exception {
        Path destination = temporaryDirectory.resolve("interrupted");
        FileTransferReceiver receiver = new FileTransferReceiver(destination);
        FileTransferProtocolCodec codec = new FileTransferProtocolCodec();
        UUID transferId = UUID.randomUUID();
        FileTransferMetadata metadata = new FileTransferMetadata(
                UUID.randomUUID(), TransferFileKind.JOB_ARCHIVE, "job.jar", 100);
        receiver.accept(new ProtocolFrame(
                MessageType.FILE_BEGIN, transferId, codec.encodeBegin(metadata)));
        receiver.accept(new ProtocolFrame(
                MessageType.FILE_CHUNK, transferId, new byte[25]));

        receiver.close();

        assertTrue(Files.exists(destination));
        try (var files = Files.list(destination)) {
            assertEquals(0, files.count());
        }
    }

    @Test
    void rejectsTooManyBytesAndWrongTransferId() throws Exception {
        Path destination = temporaryDirectory.resolve("invalid");
        FileTransferProtocolCodec codec = new FileTransferProtocolCodec();
        UUID transferId = UUID.randomUUID();
        FileTransferMetadata metadata = new FileTransferMetadata(
                UUID.randomUUID(), TransferFileKind.OUTPUT, "result.txt", 5);

        try (FileTransferReceiver receiver = new FileTransferReceiver(destination)) {
            receiver.accept(new ProtocolFrame(
                    MessageType.FILE_BEGIN, transferId, codec.encodeBegin(metadata)));
            ProtocolException wrongId = assertThrows(ProtocolException.class, () ->
                    receiver.accept(new ProtocolFrame(
                            MessageType.FILE_CHUNK, UUID.randomUUID(), new byte[]{1})));
            assertEquals(ProtocolError.CORRELATION_MISMATCH, wrongId.getError());

            ProtocolException tooLarge = assertThrows(ProtocolException.class, () ->
                    receiver.accept(new ProtocolFrame(
                            MessageType.FILE_CHUNK, transferId, new byte[6])));
            assertEquals(ProtocolError.INVALID_MESSAGE_PAYLOAD, tooLarge.getError());
        }
    }

    @Test
    void transfersTwoFilesLargerThanSixtyMegabytes() throws Exception {
        long largeFileSize = 61L * 1024 * 1024;
        Path sourceDirectory = Files.createDirectory(temporaryDirectory.resolve("large-source"));
        Path destinationDirectory = temporaryDirectory.resolve("large-destination");

        for (int index = 1; index <= 2; index++) {
            String fileName = "large-" + index + ".bin";
            Path source = sourceDirectory.resolve(fileName);
            try (RandomAccessFile file = new RandomAccessFile(source.toFile(), "rw")) {
                file.setLength(largeFileSize);
            }
            FileTransferMetadata metadata = new FileTransferMetadata(
                    UUID.randomUUID(), TransferFileKind.INPUT, fileName, largeFileSize);

            try (FileTransferReceiver receiver = new FileTransferReceiver(destinationDirectory)) {
                new FileTransferSender().send(source, metadata, receiver::accept);
            }

            assertEquals(largeFileSize, Files.size(destinationDirectory.resolve(fileName)));
        }
    }
}
