package rs.ac.bg.etf.kdp.server;

import rs.ac.bg.etf.kdp.common.protocol.ConnectionRole;
import rs.ac.bg.etf.kdp.common.protocol.FileTransferMetadata;
import rs.ac.bg.etf.kdp.common.protocol.FileTransferProtocolCodec;
import rs.ac.bg.etf.kdp.common.protocol.MessageType;
import rs.ac.bg.etf.kdp.common.protocol.ProtocolError;
import rs.ac.bg.etf.kdp.common.protocol.ProtocolException;
import rs.ac.bg.etf.kdp.common.protocol.ProtocolFrame;
import rs.ac.bg.etf.kdp.common.protocol.ReceivedFile;
import rs.ac.bg.etf.kdp.common.protocol.WorkerProtocolCodec;
import rs.ac.bg.etf.kdp.common.protocol.WorkerRegistrationMessage;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Handles registration and liveness messages from one worker connection. */
public final class WorkerSessionHandler implements ServerMessageHandler {

    private final WorkerRegistry registry;
    private final TaskScheduler scheduler;
    private final ServerTaskResultStore resultStore;
    private final WorkerProtocolCodec workerCodec = new WorkerProtocolCodec();
    private final FileTransferProtocolCodec fileCodec = new FileTransferProtocolCodec();

    public WorkerSessionHandler(WorkerRegistry registry) {
        this(
                registry,
                new TaskScheduler(registry),
                new ServerTaskResultStore(Path.of("server-data", "results")));
    }

    public WorkerSessionHandler(WorkerRegistry registry, TaskScheduler scheduler) {
        this(
                registry,
                scheduler,
                new ServerTaskResultStore(Path.of("server-data", "results")));
    }

    public WorkerSessionHandler(
            WorkerRegistry registry,
            TaskScheduler scheduler,
            ServerTaskResultStore resultStore) {
        this.registry = Objects.requireNonNull(registry, "worker registry must not be null");
        this.scheduler = Objects.requireNonNull(scheduler, "task scheduler must not be null");
        this.resultStore = Objects.requireNonNull(
                resultStore, "result store must not be null");
    }

    @Override
    public void onConnected(ServerConnection connection) throws ProtocolException {
        if (connection.getRole() != ConnectionRole.WORKER) {
            throw new ProtocolException(
                    ProtocolError.ROLE_VIOLATION,
                    "Worker session handler only accepts worker connections");
        }
    }

    @Override
    public void onMessage(ServerConnection connection, ProtocolFrame frame) throws IOException {
        try {
            if (frame.getType() == MessageType.REGISTER_WORKER) {
                register(connection, frame);
            } else if (frame.getType() == MessageType.HEARTBEAT) {
                heartbeat(connection, frame);
            } else if (frame.getType() == MessageType.TASK_ACCEPTED) {
                scheduler.accept(connection, frame);
            } else if (frame.getType() == MessageType.TASK_STARTED) {
                scheduler.startTask(connection, frame);
            } else if (frame.getType() == MessageType.TASK_FINISHED) {
                scheduler.finishTask(connection, frame);
            } else if (frame.getType() == MessageType.TASK_FAILED) {
                scheduler.failTask(connection, frame);
            } else if (frame.getType() == MessageType.FILE_BEGIN
                    || frame.getType() == MessageType.FILE_CHUNK
                    || frame.getType() == MessageType.FILE_END) {
                receiveResultFile(connection, frame);
            } else if (frame.getType() == MessageType.FILE_ACKNOWLEDGED) {
                scheduler.acknowledgeFile(connection, frame);
            } else {
                throw new ProtocolException(
                        ProtocolError.INVALID_MESSAGE_PAYLOAD,
                        "Unexpected worker message: " + frame.getType());
            }
        } catch (IllegalStateException exception) {
            throw new ProtocolException(
                    ProtocolError.INVALID_MESSAGE_PAYLOAD, exception.getMessage(), exception);
        }
    }

    @Override
    public void onDisconnected(ServerConnection connection) {
        resultStore.disconnect(connection);
        scheduler.workerDisconnected(connection);
        registry.disconnect(connection);
    }

    private void register(ServerConnection connection, ProtocolFrame frame) throws IOException {
        WorkerRegistrationMessage registration = workerCodec.decodeRegistration(frame.getPayload());
        registry.register(connection, registration);
        connection.send(frame.reply(MessageType.WORKER_REGISTERED, new byte[0]));
        scheduler.workerAvailable();
    }

    private void heartbeat(ServerConnection connection, ProtocolFrame frame)
            throws ProtocolException {
        UUID workerId = workerCodec.decodeHeartbeat(frame.getPayload());
        registry.recordHeartbeat(connection, workerId);
    }

    private void receiveResultFile(ServerConnection connection, ProtocolFrame frame)
            throws IOException {
        if (frame.getType() == MessageType.FILE_BEGIN) {
            FileTransferMetadata metadata = fileCodec.decodeBegin(frame.getPayload());
            scheduler.validateResultTransfer(connection, metadata);
        }
        Optional<ReceivedFile> receivedFile = resultStore.accept(connection, frame);
        if (receivedFile.isPresent()) {
            connection.send(receivedFile.get().getAcknowledgement());
        }
    }
}
