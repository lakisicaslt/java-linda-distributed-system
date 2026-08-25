package rs.ac.bg.etf.kdp.client;

import rs.ac.bg.etf.kdp.common.domain.JobStatus;
import rs.ac.bg.etf.kdp.common.protocol.BatchStatusMessage;
import rs.ac.bg.etf.kdp.common.protocol.BatchStatusProtocolCodec;
import rs.ac.bg.etf.kdp.common.protocol.ConnectionRole;
import rs.ac.bg.etf.kdp.common.protocol.JobStatusSnapshot;
import rs.ac.bg.etf.kdp.common.protocol.MessageType;
import rs.ac.bg.etf.kdp.common.protocol.ProtocolError;
import rs.ac.bg.etf.kdp.common.protocol.ProtocolException;
import rs.ac.bg.etf.kdp.common.protocol.ProtocolFrame;
import rs.ac.bg.etf.kdp.common.protocol.ProtocolFrameCodec;
import rs.ac.bg.etf.kdp.common.protocol.ProtocolHandshake;
import rs.ac.bg.etf.kdp.common.protocol.ProtocolSockets;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/** Long-lived client connection that receives worker-failure notifications. */
public final class JobFailureMonitor implements AutoCloseable {

    private final ClientConfig config;
    private final UUID batchId;
    private final ProtocolFrameCodec frameCodec = new ProtocolFrameCodec();
    private final ProtocolHandshake handshake = new ProtocolHandshake();
    private final BatchStatusProtocolCodec statusCodec = new BatchStatusProtocolCodec();
    private final AtomicBoolean open = new AtomicBoolean();
    private final AtomicBoolean waiting = new AtomicBoolean();

    private volatile Socket socket;
    private volatile InputStream input;

    JobFailureMonitor(ClientConfig config, UUID batchId) {
        this.config = Objects.requireNonNull(config, "client config must not be null");
        this.batchId = Objects.requireNonNull(batchId, "batch id must not be null");
    }

    void start() throws IOException {
        Socket connectedSocket = ProtocolSockets.connect(
                config.getServerHost(),
                config.getServerPort(),
                config.getConnectTimeoutMillis(),
                config.getHandshakeTimeoutMillis());
        try {
            InputStream connectedInput = connectedSocket.getInputStream();
            OutputStream connectedOutput = connectedSocket.getOutputStream();
            ProtocolFrame hello = handshake.createHello(ConnectionRole.CLIENT);
            frameCodec.write(connectedOutput, hello);
            handshake.verifyAccepted(frameCodec.read(connectedInput), hello.getRequestId());
            connectedSocket.setSoTimeout(0);

            ProtocolFrame request = ProtocolFrame.request(
                    MessageType.MONITOR_BATCH,
                    statusCodec.encodeBatchReference(batchId));
            frameCodec.write(connectedOutput, request);
            ProtocolFrame response = frameCodec.read(connectedInput);
            requireResponse(response, MessageType.BATCH_MONITORED, request.getRequestId());
            UUID monitoredBatchId = statusCodec.decodeBatchReference(response.getPayload());
            if (!batchId.equals(monitoredBatchId)) {
                throw new ProtocolException(
                        ProtocolError.CORRELATION_MISMATCH,
                        "Server started monitoring a different batch");
            }

            socket = connectedSocket;
            input = connectedInput;
            open.set(true);
        } catch (IOException | RuntimeException exception) {
            closeSocket(connectedSocket);
            throw exception;
        }
    }

    public JobStatusSnapshot waitForFailure() throws IOException {
        if (!open.get()) {
            throw new IllegalStateException("batch monitor is not open");
        }
        if (!waiting.compareAndSet(false, true)) {
            throw new IllegalStateException("another thread is already waiting for a failure");
        }
        try {
            ProtocolFrame notification = frameCodec.read(input);
            if (notification.getType() != MessageType.JOB_FAILED_NOTIFICATION) {
                throw new ProtocolException(
                        ProtocolError.INVALID_MESSAGE_PAYLOAD,
                        "Expected JOB_FAILED_NOTIFICATION but received "
                                + notification.getType());
            }
            BatchStatusMessage status = statusCodec.decodeStatus(notification.getPayload());
            if (!batchId.equals(status.getBatchId()) || status.getJobs().size() != 1) {
                throw new ProtocolException(
                        ProtocolError.CORRELATION_MISMATCH,
                        "Failure notification does not belong to the monitored batch");
            }
            JobStatusSnapshot failedJob = status.getJobs().get(0);
            if (failedJob.getStatus() != JobStatus.FAILED
                    || failedJob.getFailureReason().isEmpty()) {
                throw new ProtocolException(
                        ProtocolError.INVALID_MESSAGE_PAYLOAD,
                        "Failure notification must describe one failed job");
            }
            return failedJob;
        } catch (IOException | RuntimeException exception) {
            close();
            throw exception;
        } finally {
            waiting.set(false);
        }
    }

    public UUID getBatchId() {
        return batchId;
    }

    public boolean isOpen() {
        return open.get();
    }

    @Override
    public void close() {
        if (!open.getAndSet(false) && socket == null) {
            return;
        }
        Socket connectedSocket = socket;
        socket = null;
        input = null;
        closeSocket(connectedSocket);
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
        if (!expectedRequestId.equals(response.getRequestId())) {
            throw new ProtocolException(
                    ProtocolError.CORRELATION_MISMATCH,
                    "Server response does not match the monitor request");
        }
    }

    private static void closeSocket(Socket socket) {
        if (socket == null) {
            return;
        }
        try {
            socket.close();
        } catch (IOException ignored) {
            // The operation that caused cleanup is more useful.
        }
    }
}
