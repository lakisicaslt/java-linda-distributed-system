package rs.ac.bg.etf.kdp.common.protocol;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;

/** Reads a file incrementally and emits one sequence of file protocol frames. */
public final class FileTransferSender {

    private final FileTransferProtocolCodec fileCodec = new FileTransferProtocolCodec();

    public UUID send(
            Path source,
            FileTransferMetadata metadata,
            ProtocolFrameSink sink) throws IOException {
        UUID transferId = UUID.randomUUID();
        send(transferId, source, metadata, sink);
        return transferId;
    }

    public void send(
            UUID transferId,
            Path source,
            FileTransferMetadata metadata,
            ProtocolFrameSink sink) throws IOException {
        Objects.requireNonNull(transferId, "transfer id must not be null");
        Objects.requireNonNull(source, "source path must not be null");
        Objects.requireNonNull(metadata, "file metadata must not be null");
        Objects.requireNonNull(sink, "frame sink must not be null");
        if (!Files.isRegularFile(source)) {
            throw new IllegalArgumentException("source must be a regular file");
        }
        if (Files.size(source) != metadata.getSize()) {
            throw new IllegalArgumentException("declared file size does not match source file");
        }

        sink.send(new ProtocolFrame(
                MessageType.FILE_BEGIN, transferId, fileCodec.encodeBegin(metadata)));

        long sentBytes = 0;
        byte[] buffer = new byte[ProtocolConstants.FILE_CHUNK_SIZE];
        try (InputStream input = Files.newInputStream(source)) {
            int count;
            while ((count = input.read(buffer)) != -1) {
                sentBytes += count;
                if (sentBytes > metadata.getSize()) {
                    throw new IOException("source file grew during transfer");
                }
                byte[] chunk = count == buffer.length
                        ? buffer.clone()
                        : Arrays.copyOf(buffer, count);
                sink.send(new ProtocolFrame(MessageType.FILE_CHUNK, transferId, chunk));
            }
        }
        if (sentBytes != metadata.getSize()) {
            throw new IOException("source file changed during transfer");
        }

        sink.send(new ProtocolFrame(MessageType.FILE_END, transferId, new byte[0]));
    }
}
