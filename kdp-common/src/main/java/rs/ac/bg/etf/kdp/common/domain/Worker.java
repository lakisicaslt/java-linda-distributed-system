package rs.ac.bg.etf.kdp.common.domain;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Server-side representation of one registered worker process.
 */
public final class Worker {

    private final UUID id;
    private final String hostname;
    private final String operatingSystem;
    private final String javaVersion;
    private final int capacity;
    private final Instant registeredAt;
    private final Set<UUID> activeTaskIds = new LinkedHashSet<>();

    private WorkerStatus status;
    private Instant lastHeartbeatAt;
    private Instant lastStatusChangedAt;

    public Worker(
            UUID id,
            String hostname,
            String operatingSystem,
            String javaVersion,
            int capacity,
            Instant registeredAt) {
        this.id = Objects.requireNonNull(id, "worker id must not be null");
        this.hostname = requireNonBlank(hostname, "hostname");
        this.operatingSystem = requireNonBlank(operatingSystem, "operating system");
        this.javaVersion = requireNonBlank(javaVersion, "Java version");
        if (capacity <= 0) {
            throw new IllegalArgumentException("worker capacity must be greater than zero");
        }
        this.capacity = capacity;
        this.registeredAt = Objects.requireNonNull(
                registeredAt, "registration time must not be null");
        this.status = WorkerStatus.ONLINE;
        this.lastHeartbeatAt = registeredAt;
        this.lastStatusChangedAt = registeredAt;
    }

    public boolean hasCapacity() {
        return status == WorkerStatus.ONLINE && activeTaskIds.size() < capacity;
    }

    public int getAvailableSlots() {
        if (status != WorkerStatus.ONLINE) {
            return 0;
        }
        return capacity - activeTaskIds.size();
    }

    public void reserveTask(UUID taskId) {
        Objects.requireNonNull(taskId, "task id must not be null");
        if (activeTaskIds.contains(taskId)) {
            throw new IllegalArgumentException("task is already assigned to this worker");
        }
        if (!hasCapacity()) {
            throw new IllegalStateException("worker has no available capacity");
        }
        activeTaskIds.add(taskId);
    }

    public void releaseTask(UUID taskId) {
        Objects.requireNonNull(taskId, "task id must not be null");
        if (!activeTaskIds.remove(taskId)) {
            throw new IllegalArgumentException("task is not assigned to this worker");
        }
    }

    public void recordHeartbeat(Instant occurredAt) {
        Instant validatedTime = validateTime(occurredAt);
        lastHeartbeatAt = validatedTime;
    }

    public void markOffline(Instant occurredAt) {
        if (status == WorkerStatus.OFFLINE) {
            throw new IllegalStateException("worker is already offline");
        }
        Instant validatedTime = validateTime(occurredAt);
        status = WorkerStatus.OFFLINE;
        lastStatusChangedAt = validatedTime;
    }

    public UUID getId() {
        return id;
    }

    public String getHostname() {
        return hostname;
    }

    public String getOperatingSystem() {
        return operatingSystem;
    }

    public String getJavaVersion() {
        return javaVersion;
    }

    public int getCapacity() {
        return capacity;
    }

    public Instant getRegisteredAt() {
        return registeredAt;
    }

    public WorkerStatus getStatus() {
        return status;
    }

    public Instant getLastHeartbeatAt() {
        return lastHeartbeatAt;
    }

    public Instant getLastStatusChangedAt() {
        return lastStatusChangedAt;
    }

    public Set<UUID> getActiveTaskIds() {
        return Set.copyOf(activeTaskIds);
    }

    private Instant validateTime(Instant occurredAt) {
        Instant validatedTime = Objects.requireNonNull(occurredAt, "event time must not be null");
        if (validatedTime.isBefore(lastStatusChangedAt)
                || validatedTime.isBefore(lastHeartbeatAt)) {
            throw new IllegalArgumentException("worker event time must not move backwards");
        }
        return validatedTime;
    }

    private static String requireNonBlank(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName + " must not be null");
        String trimmedValue = value.trim();
        if (trimmedValue.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return trimmedValue;
    }
}
