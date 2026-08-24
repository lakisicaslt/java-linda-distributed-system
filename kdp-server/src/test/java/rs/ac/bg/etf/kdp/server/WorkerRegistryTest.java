package rs.ac.bg.etf.kdp.server;

import org.junit.jupiter.api.Test;
import rs.ac.bg.etf.kdp.common.domain.Worker;
import rs.ac.bg.etf.kdp.common.domain.WorkerStatus;
import rs.ac.bg.etf.kdp.common.protocol.ConnectionRole;
import rs.ac.bg.etf.kdp.common.protocol.ProtocolFrameCodec;
import rs.ac.bg.etf.kdp.common.protocol.WorkerRegistrationMessage;

import java.io.ByteArrayOutputStream;
import java.net.Socket;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WorkerRegistryTest {

    @Test
    void registersHeartbeatAndDisconnectsWorker() {
        Instant now = Instant.parse("2026-08-24T12:00:00Z");
        WorkerRegistry registry = new WorkerRegistry(Clock.fixed(now, ZoneOffset.UTC));
        ServerConnection connection = connection();
        UUID workerId = UUID.randomUUID();

        Worker worker = registry.register(connection, registration(workerId));
        registry.recordHeartbeat(connection, workerId);
        registry.disconnect(connection);

        assertEquals(WorkerStatus.OFFLINE, worker.getStatus());
        assertEquals(now, worker.getLastHeartbeatAt());
        assertEquals(1, registry.getWorkers().size());
    }

    @Test
    void rejectsHeartbeatBeforeRegistrationAndWrongWorkerId() {
        WorkerRegistry registry = new WorkerRegistry();
        ServerConnection connection = connection();
        registry.register(connection, registration(UUID.randomUUID()));

        assertThrows(IllegalStateException.class,
                () -> registry.recordHeartbeat(connection(), UUID.randomUUID()));
        assertThrows(IllegalStateException.class,
                () -> registry.recordHeartbeat(connection, UUID.randomUUID()));
    }

    @Test
    void rejectsDuplicateOnlineWorkerButAllowsReconnectAfterDisconnect() {
        WorkerRegistry registry = new WorkerRegistry();
        UUID workerId = UUID.randomUUID();
        ServerConnection first = connection();
        registry.register(first, registration(workerId));

        assertThrows(IllegalStateException.class,
                () -> registry.register(connection(), registration(workerId)));

        registry.disconnect(first);
        Worker reconnected = registry.register(connection(), registration(workerId));
        assertEquals(WorkerStatus.ONLINE, reconnected.getStatus());
    }

    private static WorkerRegistrationMessage registration(UUID workerId) {
        return new WorkerRegistrationMessage(workerId, "host", "Windows", "21", 2);
    }

    private static ServerConnection connection() {
        return new ServerConnection(
                new Socket(),
                ConnectionRole.WORKER,
                new ProtocolFrameCodec(),
                new ByteArrayOutputStream());
    }
}
