package rs.ac.bg.etf.kdp.common.protocol;

import java.util.Objects;
import java.util.UUID;

/** Information sent by a worker when it opens a server session. */
public final class WorkerRegistrationMessage {

    private final UUID workerId;
    private final String hostname;
    private final String operatingSystem;
    private final String javaVersion;
    private final int capacity;

    public WorkerRegistrationMessage(
            UUID workerId,
            String hostname,
            String operatingSystem,
            String javaVersion,
            int capacity) {
        this.workerId = Objects.requireNonNull(workerId, "worker id must not be null");
        this.hostname = requireNonBlank(hostname, "hostname");
        this.operatingSystem = requireNonBlank(operatingSystem, "operating system");
        this.javaVersion = requireNonBlank(javaVersion, "Java version");
        if (capacity <= 0) {
            throw new IllegalArgumentException("worker capacity must be greater than zero");
        }
        this.capacity = capacity;
    }

    public UUID getWorkerId() {
        return workerId;
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

    private static String requireNonBlank(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName + " must not be null");
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return trimmed;
    }
}
