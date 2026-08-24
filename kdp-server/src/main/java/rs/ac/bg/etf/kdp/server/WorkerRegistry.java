package rs.ac.bg.etf.kdp.server;

import rs.ac.bg.etf.kdp.common.domain.Worker;
import rs.ac.bg.etf.kdp.common.domain.WorkerStatus;
import rs.ac.bg.etf.kdp.common.protocol.WorkerRegistrationMessage;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Keeps the server-side state of worker connections. */
public final class WorkerRegistry {

    private final Clock clock;
    private final Map<UUID, Worker> workers = new HashMap<>();
    private final Map<ServerConnection, UUID> sessions = new IdentityHashMap<>();

    public WorkerRegistry() {
        this(Clock.systemUTC());
    }

    WorkerRegistry(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    public synchronized Worker register(
            ServerConnection connection,
            WorkerRegistrationMessage registration) {
        Objects.requireNonNull(connection, "connection must not be null");
        Objects.requireNonNull(registration, "registration must not be null");
        if (sessions.containsKey(connection)) {
            throw new IllegalStateException("this connection has already registered a worker");
        }

        Worker previous = workers.get(registration.getWorkerId());
        if (previous != null && previous.getStatus() != WorkerStatus.OFFLINE) {
            throw new IllegalStateException("a worker with this id is already connected");
        }

        Instant now = clock.instant();
        Worker worker = new Worker(
                registration.getWorkerId(),
                registration.getHostname(),
                registration.getOperatingSystem(),
                registration.getJavaVersion(),
                registration.getCapacity(),
                now);
        workers.put(worker.getId(), worker);
        sessions.put(connection, worker.getId());
        return worker;
    }

    public synchronized void recordHeartbeat(ServerConnection connection, UUID workerId) {
        UUID registeredId = sessions.get(connection);
        if (registeredId == null) {
            throw new IllegalStateException("worker must register before sending heartbeats");
        }
        if (!registeredId.equals(workerId)) {
            throw new IllegalStateException("heartbeat worker id does not match the session");
        }
        workers.get(workerId).recordHeartbeat(clock.instant());
    }

    public synchronized void disconnect(ServerConnection connection) {
        UUID workerId = sessions.remove(connection);
        if (workerId == null) {
            return;
        }
        Worker worker = workers.get(workerId);
        if (worker != null && worker.getStatus() != WorkerStatus.OFFLINE) {
            worker.markOffline(clock.instant());
        }
    }

    public synchronized Optional<Worker> find(UUID workerId) {
        Objects.requireNonNull(workerId, "worker id must not be null");
        return Optional.ofNullable(workers.get(workerId));
    }

    public synchronized List<Worker> getWorkers() {
        return List.copyOf(new ArrayList<>(workers.values()));
    }
}
