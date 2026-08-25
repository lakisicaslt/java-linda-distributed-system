package rs.ac.bg.etf.kdp.server;

import rs.ac.bg.etf.kdp.common.domain.Job;
import rs.ac.bg.etf.kdp.common.domain.JobBatch;
import rs.ac.bg.etf.kdp.common.domain.JobStatus;
import rs.ac.bg.etf.kdp.common.protocol.BatchAcceptedMessage;
import rs.ac.bg.etf.kdp.common.protocol.BatchStatusMessage;
import rs.ac.bg.etf.kdp.common.protocol.BatchStatusProtocolCodec;
import rs.ac.bg.etf.kdp.common.protocol.BatchSubmissionMessage;
import rs.ac.bg.etf.kdp.common.protocol.BatchSubmissionProtocolCodec;
import rs.ac.bg.etf.kdp.common.protocol.ConnectionRole;
import rs.ac.bg.etf.kdp.common.protocol.MessageType;
import rs.ac.bg.etf.kdp.common.protocol.ProtocolError;
import rs.ac.bg.etf.kdp.common.protocol.ProtocolException;
import rs.ac.bg.etf.kdp.common.protocol.ProtocolFrame;
import rs.ac.bg.etf.kdp.common.protocol.ReceivedFile;
import rs.ac.bg.etf.kdp.common.protocol.JobStatusSnapshot;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Receives job descriptions and files from client connections. */
public final class ClientSessionHandler implements ServerMessageHandler {

    private final ServerJobStore jobStore;
    private final TaskScheduler scheduler;
    private final BatchSubmissionProtocolCodec submissionCodec =
            new BatchSubmissionProtocolCodec();
    private final BatchStatusProtocolCodec statusCodec = new BatchStatusProtocolCodec();
    private final Map<ServerConnection, Set<UUID>> submittedJobIds =
            new IdentityHashMap<>();

    public ClientSessionHandler(ServerJobStore jobStore, TaskScheduler scheduler) {
        this.jobStore = Objects.requireNonNull(jobStore, "job store must not be null");
        this.scheduler = Objects.requireNonNull(scheduler, "task scheduler must not be null");
    }

    @Override
    public synchronized void onConnected(ServerConnection connection) throws ProtocolException {
        if (connection.getRole() != ConnectionRole.CLIENT) {
            throw new ProtocolException(
                    ProtocolError.ROLE_VIOLATION,
                    "Client session handler only accepts client connections");
        }
        submittedJobIds.put(connection, new HashSet<>());
    }

    @Override
    public void onMessage(ServerConnection connection, ProtocolFrame frame) throws IOException {
        try {
            if (frame.getType() == MessageType.SUBMIT_BATCH) {
                submitBatch(connection, frame);
            } else if (frame.getType() == MessageType.GET_BATCH_STATUS) {
                sendBatchStatus(connection, frame);
            } else if (frame.getType() == MessageType.FILE_BEGIN
                    || frame.getType() == MessageType.FILE_CHUNK
                    || frame.getType() == MessageType.FILE_END) {
                receiveJobFile(connection, frame);
            } else {
                throw new ProtocolException(
                        ProtocolError.INVALID_MESSAGE_PAYLOAD,
                        "Unexpected client message: " + frame.getType());
            }
        } catch (IllegalArgumentException | IllegalStateException exception) {
            throw new ProtocolException(
                    ProtocolError.INVALID_MESSAGE_PAYLOAD, exception.getMessage(), exception);
        }
    }

    @Override
    public synchronized void onDisconnected(ServerConnection connection) {
        jobStore.disconnect(connection);
        submittedJobIds.remove(connection);
    }

    private void submitBatch(ServerConnection connection, ProtocolFrame frame)
            throws IOException {
        BatchSubmissionMessage submission = submissionCodec.decodeSubmission(frame.getPayload());
        JobBatch batch = jobStore.createBatch(submission.getSpecifications());
        BatchAcceptedMessage accepted = new BatchAcceptedMessage(
                batch.getId(), batch.getJobs().stream().map(job -> job.getId()).toList());

        synchronized (this) {
            Set<UUID> ids = requireSession(connection);
            ids.addAll(accepted.getJobIds());
        }
        connection.send(frame.reply(
                MessageType.BATCH_ACCEPTED, submissionCodec.encodeAccepted(accepted)));
    }

    private void receiveJobFile(ServerConnection connection, ProtocolFrame frame)
            throws IOException {
        Set<UUID> allowedIds;
        synchronized (this) {
            allowedIds = Set.copyOf(requireSession(connection));
        }
        Optional<ReceivedFile> received = jobStore.accept(connection, frame, allowedIds);
        if (received.isPresent()) {
            ReceivedFile file = received.get();
            connection.send(file.getAcknowledgement());
            scheduleCompleteJob(file.getMetadata().getOwnerId());
        }
    }

    private void sendBatchStatus(ServerConnection connection, ProtocolFrame frame)
            throws IOException {
        UUID batchId = statusCodec.decodeBatchReference(frame.getPayload());
        JobBatch batch = jobStore.findBatch(batchId).orElseThrow(() ->
                new ProtocolException(
                        ProtocolError.INVALID_MESSAGE_PAYLOAD,
                        "Requested batch does not exist"));

        List<JobStatusSnapshot> jobs;
        synchronized (scheduler) {
            jobs = batch.getJobs().stream()
                    .map(job -> new JobStatusSnapshot(
                            job.getId(),
                            job.getStatus(),
                            job.getStatus() == JobStatus.FAILED
                                    ? job.getFailureReason()
                                    : null))
                    .toList();
        }
        BatchStatusMessage status = new BatchStatusMessage(batchId, jobs);
        connection.send(frame.reply(
                MessageType.BATCH_STATUS, statusCodec.encodeStatus(status)));
    }

    private void scheduleCompleteJob(UUID jobId) throws IOException {
        Optional<Job> completeJob = jobStore.claimForScheduling(jobId);
        if (completeJob.isEmpty()) {
            return;
        }
        Job job = completeJob.get();
        List<Path> inputs = job.getSpecification().getInputFileNames().stream()
                .map(fileName -> jobStore.inputPath(jobId, fileName))
                .toList();
        scheduler.submit(job, jobStore.archivePath(jobId), inputs);
    }

    private Set<UUID> requireSession(ServerConnection connection) throws ProtocolException {
        Set<UUID> ids = submittedJobIds.get(connection);
        if (ids == null) {
            throw new ProtocolException(
                    ProtocolError.INVALID_MESSAGE_PAYLOAD,
                    "Client connection is not registered in the session handler");
        }
        return ids;
    }
}
