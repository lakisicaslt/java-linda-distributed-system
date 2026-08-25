package rs.ac.bg.etf.kdp.server;

import rs.ac.bg.etf.kdp.common.protocol.ConnectionRole;
import rs.ac.bg.etf.kdp.common.protocol.MessageType;
import rs.ac.bg.etf.kdp.common.protocol.ProtocolError;
import rs.ac.bg.etf.kdp.common.protocol.ProtocolException;
import rs.ac.bg.etf.kdp.common.protocol.ProtocolFrame;
import rs.ac.bg.etf.kdp.common.protocol.WorkerProtocolCodec;
import rs.ac.bg.etf.kdp.common.protocol.WorkerRegistrationMessage;

import java.io.IOException;
import java.util.Objects;
import java.util.UUID;

/** Handles registration and liveness messages from one worker connection. */
public final class WorkerSessionHandler implements ServerMessageHandler {

    private final WorkerRegistry registry;
    private final TaskScheduler scheduler;
    private final WorkerProtocolCodec workerCodec = new WorkerProtocolCodec();

    public WorkerSessionHandler(WorkerRegistry registry) {
        this(registry, new TaskScheduler(registry));
    }

    public WorkerSessionHandler(WorkerRegistry registry, TaskScheduler scheduler) {
        this.registry = Objects.requireNonNull(registry, "worker registry must not be null");
        this.scheduler = Objects.requireNonNull(scheduler, "task scheduler must not be null");
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
}
