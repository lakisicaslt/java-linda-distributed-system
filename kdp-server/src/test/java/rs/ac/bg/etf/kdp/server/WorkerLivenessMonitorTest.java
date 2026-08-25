package rs.ac.bg.etf.kdp.server;

import org.junit.jupiter.api.Test;
import rs.ac.bg.etf.kdp.common.protocol.ConnectionRole;
import rs.ac.bg.etf.kdp.common.protocol.ProtocolFrameCodec;
import rs.ac.bg.etf.kdp.common.protocol.WorkerRegistrationMessage;

import java.io.ByteArrayOutputStream;
import java.net.Socket;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkerLivenessMonitorTest {

    @Test
    void closesWorkerWhoseHeartbeatExpired() {
        Instant registeredAt = Instant.parse("2026-08-25T12:00:00Z");
        WorkerRegistry registry = new WorkerRegistry(
                Clock.fixed(registeredAt, ZoneOffset.UTC));
        ServerConnection connection = connection();
        registry.register(connection, registration());
        WorkerLivenessMonitor monitor = new WorkerLivenessMonitor(
                registry,
                1_000,
                6_000,
                Clock.fixed(registeredAt.plusSeconds(7), ZoneOffset.UTC));

        monitor.checkWorkers();

        assertFalse(connection.isOpen());
    }

    @Test
    void leavesRecentlySeenWorkerConnected() {
        Instant registeredAt = Instant.parse("2026-08-25T12:00:00Z");
        WorkerRegistry registry = new WorkerRegistry(
                Clock.fixed(registeredAt, ZoneOffset.UTC));
        ServerConnection connection = connection();
        registry.register(connection, registration());
        WorkerLivenessMonitor monitor = new WorkerLivenessMonitor(
                registry,
                1_000,
                6_000,
                Clock.fixed(registeredAt.plusSeconds(5), ZoneOffset.UTC));

        monitor.checkWorkers();

        assertTrue(connection.isOpen());
    }

    private static WorkerRegistrationMessage registration() {
        return new WorkerRegistrationMessage(
                UUID.randomUUID(), "worker", "Linux", "17", 1);
    }

    private static ServerConnection connection() {
        return new ServerConnection(
                new Socket(),
                ConnectionRole.WORKER,
                new ProtocolFrameCodec(),
                new ByteArrayOutputStream());
    }
}
