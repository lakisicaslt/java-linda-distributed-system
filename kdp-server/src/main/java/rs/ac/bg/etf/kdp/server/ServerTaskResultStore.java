package rs.ac.bg.etf.kdp.server;

import rs.ac.bg.etf.kdp.common.domain.Job;
import rs.ac.bg.etf.kdp.common.domain.JobStatus;
import rs.ac.bg.etf.kdp.common.protocol.BatchResultFile;
import rs.ac.bg.etf.kdp.common.protocol.FileTransferMetadata;
import rs.ac.bg.etf.kdp.common.protocol.FileTransferProtocolCodec;
import rs.ac.bg.etf.kdp.common.protocol.FileTransferReceiver;
import rs.ac.bg.etf.kdp.common.protocol.MessageType;
import rs.ac.bg.etf.kdp.common.protocol.ProtocolError;
import rs.ac.bg.etf.kdp.common.protocol.ProtocolException;
import rs.ac.bg.etf.kdp.common.protocol.ProtocolFrame;
import rs.ac.bg.etf.kdp.common.protocol.ReceivedFile;
import rs.ac.bg.etf.kdp.common.protocol.TransferFileKind;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Stores output files and captured streams received from workers. */
public final class ServerTaskResultStore {

    private final Path resultDirectory;
    private final FileTransferProtocolCodec fileCodec = new FileTransferProtocolCodec();
    private final Map<ServerConnection, FileTransferReceiver> receivers =
            new IdentityHashMap<>();

    public ServerTaskResultStore(Path resultDirectory) {
        this.resultDirectory = Objects.requireNonNull(
                resultDirectory, "result directory must not be null")
                .toAbsolutePath()
                .normalize();
    }

    public synchronized Optional<ReceivedFile> accept(
            ServerConnection connection,
            ProtocolFrame frame) throws IOException {
        Objects.requireNonNull(connection, "connection must not be null");
        Objects.requireNonNull(frame, "protocol frame must not be null");
        FileTransferReceiver receiver = receivers.get(connection);
        if (frame.getType() == MessageType.FILE_BEGIN) {
            if (receiver != null) {
                throw invalidPayload("Worker already has an active result transfer");
            }
            FileTransferMetadata metadata = fileCodec.decodeBegin(frame.getPayload());
            receiver = new FileTransferReceiver(destinationDirectory(metadata));
            receivers.put(connection, receiver);
        } else if (receiver == null) {
            throw invalidPayload("FILE_BEGIN must be received before result data");
        }

        try {
            Optional<ReceivedFile> result = receiver.accept(frame);
            if (result.isPresent()) {
                receiver.close();
                receivers.remove(connection);
            }
            return result;
        } catch (IOException | RuntimeException exception) {
            receiver.close();
            receivers.remove(connection);
            throw exception;
        }
    }

    public synchronized void disconnect(ServerConnection connection) {
        FileTransferReceiver receiver = receivers.remove(connection);
        if (receiver != null) {
            receiver.close();
        }
    }

    public Path taskDirectory(UUID taskId) {
        Objects.requireNonNull(taskId, "task id must not be null");
        return resultDirectory.resolve(taskId.toString());
    }

    public List<StoredResultFile> findResults(Job job) throws IOException {
        Objects.requireNonNull(job, "job must not be null");
        UUID taskId = job.getRootTask().getId();
        Path taskDirectory = taskDirectory(taskId);
        List<StoredResultFile> results = new ArrayList<>();

        for (String outputFileName : job.getSpecification().getOutputFileNames()) {
            Path output = taskDirectory.resolve("outputs").resolve(outputFileName);
            if (Files.isRegularFile(output)) {
                results.add(storedResult(job, taskId, TransferFileKind.OUTPUT, output));
            } else if (job.getStatus() == JobStatus.DONE) {
                throw new IOException("Completed job is missing output file: " + outputFileName);
            }
        }

        addIfPresent(
                results,
                job,
                taskId,
                TransferFileKind.STANDARD_OUTPUT,
                taskDirectory.resolve("streams").resolve("stdout.txt"),
                job.getStatus() == JobStatus.DONE);
        addIfPresent(
                results,
                job,
                taskId,
                TransferFileKind.STANDARD_ERROR,
                taskDirectory.resolve("streams").resolve("stderr.txt"),
                job.getStatus() == JobStatus.DONE);
        return List.copyOf(results);
    }

    private static void addIfPresent(
            List<StoredResultFile> results,
            Job job,
            UUID taskId,
            TransferFileKind kind,
            Path path,
            boolean required) throws IOException {
        if (Files.isRegularFile(path)) {
            results.add(storedResult(job, taskId, kind, path));
        } else if (required) {
            throw new IOException("Completed job is missing result stream: " + path.getFileName());
        }
    }

    private static StoredResultFile storedResult(
            Job job,
            UUID taskId,
            TransferFileKind kind,
            Path path) throws IOException {
        BatchResultFile description = new BatchResultFile(
                job.getId(), taskId, kind, path.getFileName().toString(), Files.size(path));
        return new StoredResultFile(description, path);
    }

    private Path destinationDirectory(FileTransferMetadata metadata)
            throws ProtocolException {
        Path taskDirectory = taskDirectory(metadata.getOwnerId());
        return switch (metadata.getKind()) {
            case OUTPUT -> taskDirectory.resolve("outputs");
            case STANDARD_OUTPUT, STANDARD_ERROR -> taskDirectory.resolve("streams");
            default -> throw invalidPayload(
                    "Server can receive only task results from a worker");
        };
    }

    private static ProtocolException invalidPayload(String message) {
        return new ProtocolException(ProtocolError.INVALID_MESSAGE_PAYLOAD, message);
    }

    public record StoredResultFile(BatchResultFile description, Path path) {

        public StoredResultFile {
            Objects.requireNonNull(description, "result description must not be null");
            Objects.requireNonNull(path, "result path must not be null");
        }
    }
}
