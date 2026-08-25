package rs.ac.bg.etf.kdp.server;

import rs.ac.bg.etf.kdp.common.domain.Job;
import rs.ac.bg.etf.kdp.common.domain.JobBatch;
import rs.ac.bg.etf.kdp.common.domain.JobSpecification;
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
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Stores submitted job descriptions, archives and input files on the server. */
public final class ServerJobStore {

    private final Path jobDirectory;
    private final Clock clock;
    private final FileTransferProtocolCodec fileCodec = new FileTransferProtocolCodec();
    private final Map<UUID, JobBatch> batches = new LinkedHashMap<>();
    private final Map<UUID, Job> jobs = new HashMap<>();
    private final Map<UUID, Set<FileKey>> receivedFiles = new HashMap<>();
    private final Map<ServerConnection, FileTransferReceiver> receivers =
            new IdentityHashMap<>();

    public ServerJobStore(Path jobDirectory) {
        this(jobDirectory, Clock.systemUTC());
    }

    ServerJobStore(Path jobDirectory, Clock clock) {
        this.jobDirectory = Objects.requireNonNull(
                jobDirectory, "job directory must not be null")
                .toAbsolutePath()
                .normalize();
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    public synchronized JobBatch createBatch(List<JobSpecification> specifications)
            throws IOException {
        Objects.requireNonNull(specifications, "job specifications must not be null");
        if (specifications.isEmpty()) {
            throw new IllegalArgumentException("batch must contain at least one job");
        }

        UUID batchId = UUID.randomUUID();
        Instant createdAt = clock.instant();
        List<Job> createdJobs = new ArrayList<>(specifications.size());
        for (JobSpecification specification : specifications) {
            Job job = new Job(UUID.randomUUID(), batchId, specification, createdAt);
            Files.createDirectories(archiveDirectory(job.getId()));
            Files.createDirectories(inputDirectory(job.getId()));
            createdJobs.add(job);
        }

        JobBatch batch = new JobBatch(batchId, createdAt, createdJobs);
        batches.put(batchId, batch);
        for (Job job : createdJobs) {
            jobs.put(job.getId(), job);
            receivedFiles.put(job.getId(), new HashSet<>());
        }
        return batch;
    }

    public synchronized Optional<ReceivedFile> accept(
            ServerConnection connection,
            ProtocolFrame frame,
            Set<UUID> allowedJobIds) throws IOException {
        Objects.requireNonNull(connection, "connection must not be null");
        Objects.requireNonNull(frame, "protocol frame must not be null");
        Objects.requireNonNull(allowedJobIds, "allowed job ids must not be null");

        FileTransferReceiver receiver = receivers.get(connection);
        if (frame.getType() == MessageType.FILE_BEGIN) {
            if (receiver != null) {
                throw invalidPayload("Client already has an active file transfer");
            }
            FileTransferMetadata metadata = fileCodec.decodeBegin(frame.getPayload());
            validateIncomingFile(metadata, allowedJobIds);
            receiver = new FileTransferReceiver(destinationDirectory(metadata));
            receivers.put(connection, receiver);
        } else if (receiver == null) {
            throw invalidPayload("FILE_BEGIN must be received before file data");
        }

        try {
            Optional<ReceivedFile> result = receiver.accept(frame);
            if (result.isPresent()) {
                ReceivedFile file = result.get();
                receivedFiles.get(file.getMetadata().getOwnerId()).add(
                        new FileKey(file.getMetadata().getKind(), file.getMetadata().getFileName()));
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

    public synchronized Optional<JobBatch> findBatch(UUID batchId) {
        Objects.requireNonNull(batchId, "batch id must not be null");
        return Optional.ofNullable(batches.get(batchId));
    }

    public synchronized Optional<Job> findJob(UUID jobId) {
        Objects.requireNonNull(jobId, "job id must not be null");
        return Optional.ofNullable(jobs.get(jobId));
    }

    public synchronized boolean isComplete(UUID jobId) {
        Job job = requireJob(jobId);
        Set<FileKey> files = receivedFiles.get(jobId);
        JobSpecification specification = job.getSpecification();
        if (!files.contains(new FileKey(
                TransferFileKind.JOB_ARCHIVE, specification.getJarFileName()))) {
            return false;
        }
        return specification.getInputFileNames().stream()
                .allMatch(name -> files.contains(new FileKey(TransferFileKind.INPUT, name)));
    }

    public synchronized List<Job> getCompleteJobs() {
        return jobs.values().stream()
                .filter(job -> isComplete(job.getId()))
                .toList();
    }

    public Path jobDirectory(UUID jobId) {
        Objects.requireNonNull(jobId, "job id must not be null");
        return jobDirectory.resolve(jobId.toString());
    }

    public Path archivePath(UUID jobId) {
        Job job;
        synchronized (this) {
            job = requireJob(jobId);
        }
        return archiveDirectory(jobId).resolve(job.getSpecification().getJarFileName());
    }

    public Path inputPath(UUID jobId, String fileName) {
        Objects.requireNonNull(fileName, "input file name must not be null");
        return inputDirectory(jobId).resolve(fileName);
    }

    private void validateIncomingFile(
            FileTransferMetadata metadata,
            Set<UUID> allowedJobIds) throws ProtocolException {
        if (!allowedJobIds.contains(metadata.getOwnerId())) {
            throw invalidPayload("Client cannot upload a file for this job");
        }
        Job job = jobs.get(metadata.getOwnerId());
        if (job == null) {
            throw invalidPayload("Client sent a file for an unknown job");
        }

        JobSpecification specification = job.getSpecification();
        boolean declaredArchive = metadata.getKind() == TransferFileKind.JOB_ARCHIVE
                && metadata.getFileName().equals(specification.getJarFileName());
        boolean declaredInput = metadata.getKind() == TransferFileKind.INPUT
                && specification.getInputFileNames().contains(metadata.getFileName());
        if (!declaredArchive && !declaredInput) {
            throw invalidPayload("Client sent a file that is not declared by the job");
        }

        FileKey key = new FileKey(metadata.getKind(), metadata.getFileName());
        if (receivedFiles.get(metadata.getOwnerId()).contains(key)) {
            throw invalidPayload("Client sent the same job file more than once");
        }
    }

    private Path destinationDirectory(FileTransferMetadata metadata) {
        return metadata.getKind() == TransferFileKind.JOB_ARCHIVE
                ? archiveDirectory(metadata.getOwnerId())
                : inputDirectory(metadata.getOwnerId());
    }

    private Path archiveDirectory(UUID jobId) {
        return jobDirectory(jobId).resolve("archive");
    }

    private Path inputDirectory(UUID jobId) {
        return jobDirectory(jobId).resolve("inputs");
    }

    private Job requireJob(UUID jobId) {
        Job job = jobs.get(Objects.requireNonNull(jobId, "job id must not be null"));
        if (job == null) {
            throw new IllegalArgumentException("job is not stored on this server");
        }
        return job;
    }

    private static ProtocolException invalidPayload(String message) {
        return new ProtocolException(ProtocolError.INVALID_MESSAGE_PAYLOAD, message);
    }

    private record FileKey(TransferFileKind kind, String name) {
    }
}
