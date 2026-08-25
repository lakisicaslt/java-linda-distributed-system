package rs.ac.bg.etf.kdp.server;

import rs.ac.bg.etf.kdp.common.domain.Worker;
import rs.ac.bg.etf.kdp.common.domain.WorkerStatus;
import rs.ac.bg.etf.kdp.common.domain.Task;
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

    public synchronized Optional<ServerConnection> tryAssign(Task task) {
        Objects.requireNonNull(task, "task must not be null");
        ServerConnection selectedConnection = null;
        Worker selectedWorker = null;

        for (Map.Entry<ServerConnection, UUID> session : sessions.entrySet()) {
            Worker candidate = workers.get(session.getValue());
            if (candidate == null || !candidate.hasCapacity()) {
                continue;
            }
            if (selectedWorker == null
                    || candidate.getAvailableSlots() > selectedWorker.getAvailableSlots()
                    || (candidate.getAvailableSlots() == selectedWorker.getAvailableSlots()
                    && candidate.getId().compareTo(selectedWorker.getId()) < 0)) {
                selectedConnection = session.getKey();
                selectedWorker = candidate;
            }
        }

        if (selectedWorker == null) {
            return Optional.empty();
        }

        selectedWorker.reserveTask(task.getId());
        try {
            task.assignTo(selectedWorker.getId(), clock.instant());
        } catch (RuntimeException exception) {
            selectedWorker.releaseTask(task.getId());
            throw exception;
        }
        return Optional.of(selectedConnection);
    }

    public synchronized void releaseTask(Task task) {
        Objects.requireNonNull(task, "task must not be null");
        UUID workerId = task.getAssignedWorkerId()
                .orElseThrow(() -> new IllegalStateException("task has no assigned worker"));
        Worker worker = workers.get(workerId);
        if (worker != null && worker.getActiveTaskIds().contains(task.getId())) {
            worker.releaseTask(task.getId());
        }
    }

    public synchronized Optional<UUID> findWorkerId(ServerConnection connection) {
        Objects.requireNonNull(connection, "connection must not be null");
        return Optional.ofNullable(sessions.get(connection));
    }

    public synchronized Optional<ServerConnection> findConnection(UUID workerId) {
        Objects.requireNonNull(workerId, "worker id must not be null");
        return sessions.entrySet().stream()
                .filter(entry -> entry.getValue().equals(workerId))
                .map(Map.Entry::getKey)
                .findFirst();
    }

    public synchronized List<ServerConnection> findUnresponsiveConnections(Instant cutoff) {
        Objects.requireNonNull(cutoff, "heartbeat cutoff must not be null");
        return sessions.entrySet().stream()
                .filter(entry -> {
                    Worker worker = workers.get(entry.getValue());
                    return worker != null
                            && worker.getStatus() == WorkerStatus.ONLINE
                            && !worker.getLastHeartbeatAt().isAfter(cutoff);
                })
                .map(Map.Entry::getKey)
                .toList();
    }

    public synchronized Optional<Worker> find(UUID workerId) {
        Objects.requireNonNull(workerId, "worker id must not be null");
        return Optional.ofNullable(workers.get(workerId));
    }

    public synchronized List<Worker> getWorkers() {
        return List.copyOf(new ArrayList<>(workers.values()));
    }
}
