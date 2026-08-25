package rs.ac.bg.etf.kdp.client;

import rs.ac.bg.etf.kdp.common.protocol.BatchAcceptedMessage;
import rs.ac.bg.etf.kdp.common.protocol.BatchStatusMessage;
import rs.ac.bg.etf.kdp.common.protocol.BatchStatusProtocolCodec;
import rs.ac.bg.etf.kdp.common.protocol.BatchSubmissionMessage;
import rs.ac.bg.etf.kdp.common.protocol.BatchSubmissionProtocolCodec;
import rs.ac.bg.etf.kdp.common.protocol.ConnectionRole;
import rs.ac.bg.etf.kdp.common.protocol.FileTransferMetadata;
import rs.ac.bg.etf.kdp.common.protocol.FileTransferProtocolCodec;
import rs.ac.bg.etf.kdp.common.protocol.FileTransferSender;
import rs.ac.bg.etf.kdp.common.protocol.MessageType;
import rs.ac.bg.etf.kdp.common.protocol.ProtocolError;
import rs.ac.bg.etf.kdp.common.protocol.ProtocolException;
import rs.ac.bg.etf.kdp.common.protocol.ProtocolFrame;
import rs.ac.bg.etf.kdp.common.protocol.ProtocolFrameCodec;
import rs.ac.bg.etf.kdp.common.protocol.ProtocolHandshake;
import rs.ac.bg.etf.kdp.common.protocol.ProtocolSockets;
import rs.ac.bg.etf.kdp.common.protocol.TransferFileKind;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Synchronous Java NET client for submitting jobs and their files. */
public final class JobClient {

    private final ClientConfig config;
    private final ProtocolFrameCodec frameCodec = new ProtocolFrameCodec();
    private final ProtocolHandshake handshake = new ProtocolHandshake();
    private final BatchSubmissionProtocolCodec submissionCodec =
            new BatchSubmissionProtocolCodec();
    private final BatchStatusProtocolCodec statusCodec = new BatchStatusProtocolCodec();
    private final FileTransferSender fileSender = new FileTransferSender();
    private final FileTransferProtocolCodec fileCodec = new FileTransferProtocolCodec();

    public JobClient(ClientConfig config) {
        this.config = Objects.requireNonNull(config, "client config must not be null");
    }

    public BatchAcceptedMessage submit(List<JobSubmission> jobs) throws IOException {
        Objects.requireNonNull(jobs, "job submissions must not be null");
        if (jobs.isEmpty()) {
            throw new IllegalArgumentException("at least one job must be submitted");
        }
        if (jobs.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("job submissions must not contain null");
        }

        try (Socket socket = ProtocolSockets.connect(
                config.getServerHost(),
                config.getServerPort(),
                config.getConnectTimeoutMillis(),
                config.getHandshakeTimeoutMillis())) {
            InputStream input = socket.getInputStream();
            OutputStream output = socket.getOutputStream();
            performHandshake(input, output);
            socket.setSoTimeout(0);

            BatchAcceptedMessage accepted = submitDescriptions(jobs, input, output);
            for (int index = 0; index < jobs.size(); index++) {
                sendJobFiles(accepted.getJobIds().get(index), jobs.get(index), input, output);
            }
            return accepted;
        }
    }

    public BatchStatusMessage getBatchStatus(UUID batchId) throws IOException {
        Objects.requireNonNull(batchId, "batch id must not be null");
        try (Socket socket = ProtocolSockets.connect(
                config.getServerHost(),
                config.getServerPort(),
                config.getConnectTimeoutMillis(),
                config.getHandshakeTimeoutMillis())) {
            InputStream input = socket.getInputStream();
            OutputStream output = socket.getOutputStream();
            performHandshake(input, output);
            socket.setSoTimeout(0);

            ProtocolFrame request = ProtocolFrame.request(
                    MessageType.GET_BATCH_STATUS,
                    statusCodec.encodeBatchReference(batchId));
            frameCodec.write(output, request);
            ProtocolFrame response = frameCodec.read(input);
            requireResponse(response, MessageType.BATCH_STATUS, request.getRequestId());
            BatchStatusMessage status = statusCodec.decodeStatus(response.getPayload());
            if (!status.getBatchId().equals(batchId)) {
                throw new ProtocolException(
                        ProtocolError.CORRELATION_MISMATCH,
                        "Server returned status for a different batch");
            }
            return status;
        }
    }

    private void performHandshake(InputStream input, OutputStream output) throws IOException {
        ProtocolFrame hello = handshake.createHello(ConnectionRole.CLIENT);
        frameCodec.write(output, hello);
        ProtocolFrame response = frameCodec.read(input);
        handshake.verifyAccepted(response, hello.getRequestId());
    }

    private BatchAcceptedMessage submitDescriptions(
            List<JobSubmission> jobs,
            InputStream input,
            OutputStream output) throws IOException {
        BatchSubmissionMessage submission = new BatchSubmissionMessage(jobs.stream()
                .map(JobSubmission::getSpecification)
                .toList());
        ProtocolFrame request = ProtocolFrame.request(
                MessageType.SUBMIT_BATCH,
                submissionCodec.encodeSubmission(submission));
        frameCodec.write(output, request);

        ProtocolFrame response = frameCodec.read(input);
        requireResponse(response, MessageType.BATCH_ACCEPTED, request.getRequestId());
        BatchAcceptedMessage accepted = submissionCodec.decodeAccepted(response.getPayload());
        if (accepted.getJobIds().size() != jobs.size()) {
            throw new ProtocolException(
                    ProtocolError.INVALID_MESSAGE_PAYLOAD,
                    "Server returned a different number of job ids");
        }
        return accepted;
    }

    private void sendJobFiles(
            UUID jobId,
            JobSubmission job,
            InputStream input,
            OutputStream output) throws IOException {
        sendFile(
                jobId,
                TransferFileKind.JOB_ARCHIVE,
                job.getJarFile(),
                job.getSpecification().getJarFileName(),
                input,
                output);
        for (String inputFileName : job.getSpecification().getInputFileNames()) {
            sendFile(
                    jobId,
                    TransferFileKind.INPUT,
                    job.getInputFile(inputFileName),
                    inputFileName,
                    input,
                    output);
        }
    }

    private void sendFile(
            UUID jobId,
            TransferFileKind kind,
            Path source,
            String remoteFileName,
            InputStream input,
            OutputStream output) throws IOException {
        FileTransferMetadata metadata = new FileTransferMetadata(
                jobId, kind, remoteFileName, Files.size(source));
        UUID transferId = fileSender.send(source, metadata, frame ->
                frameCodec.write(output, frame));
        ProtocolFrame acknowledgement = frameCodec.read(input);
        fileCodec.verifyAcknowledgement(acknowledgement, transferId);
    }

    private static void requireResponse(
            ProtocolFrame response,
            MessageType expectedType,
            UUID expectedRequestId) throws ProtocolException {
        if (response.getType() != expectedType) {
            throw new ProtocolException(
                    ProtocolError.INVALID_MESSAGE_PAYLOAD,
                    "Expected " + expectedType + " but received " + response.getType());
        }
        if (!response.getRequestId().equals(expectedRequestId)) {
            throw new ProtocolException(
                    ProtocolError.CORRELATION_MISMATCH,
                    "Server response does not match the submission request");
        }
    }
}
