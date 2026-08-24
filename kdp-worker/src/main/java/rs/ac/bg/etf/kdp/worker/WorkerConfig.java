package rs.ac.bg.etf.kdp.worker;

import rs.ac.bg.etf.kdp.common.protocol.ProtocolConstants;

import java.util.Objects;
import java.util.UUID;

public final class WorkerConfig {

    public static final int DEFAULT_HEARTBEAT_INTERVAL_MILLIS = 2_000;
    public static final int DEFAULT_SERVER_PORT = 5_050;

    private final String serverHost;
    private final int serverPort;
    private final UUID workerId;
    private final int capacity;
    private final int connectTimeoutMillis;
    private final int handshakeTimeoutMillis;
    private final int heartbeatIntervalMillis;

    public WorkerConfig(
            String serverHost,
            int serverPort,
            UUID workerId,
            int capacity,
            int connectTimeoutMillis,
            int handshakeTimeoutMillis,
            int heartbeatIntervalMillis) {
        Objects.requireNonNull(serverHost, "server host must not be null");
        if (serverHost.isBlank()) {
            throw new IllegalArgumentException("server host must not be blank");
        }
        if (serverPort < 1 || serverPort > 65_535) {
            throw new IllegalArgumentException("server port must be between 1 and 65535");
        }
        this.workerId = Objects.requireNonNull(workerId, "worker id must not be null");
        if (capacity <= 0) {
            throw new IllegalArgumentException("worker capacity must be greater than zero");
        }
        requirePositive(connectTimeoutMillis, "connect timeout");
        requirePositive(handshakeTimeoutMillis, "handshake timeout");
        requirePositive(heartbeatIntervalMillis, "heartbeat interval");
        this.serverHost = serverHost.trim();
        this.serverPort = serverPort;
        this.capacity = capacity;
        this.connectTimeoutMillis = connectTimeoutMillis;
        this.handshakeTimeoutMillis = handshakeTimeoutMillis;
        this.heartbeatIntervalMillis = heartbeatIntervalMillis;
    }

    public static WorkerConfig defaults(String serverHost, int serverPort, int capacity) {
        return new WorkerConfig(
                serverHost,
                serverPort,
                UUID.randomUUID(),
                capacity,
                ProtocolConstants.CONNECT_TIMEOUT_MILLIS,
                ProtocolConstants.HANDSHAKE_TIMEOUT_MILLIS,
                DEFAULT_HEARTBEAT_INTERVAL_MILLIS);
    }

    public String getServerHost() {
        return serverHost;
    }

    public int getServerPort() {
        return serverPort;
    }

    public UUID getWorkerId() {
        return workerId;
    }

    public int getCapacity() {
        return capacity;
    }

    public int getConnectTimeoutMillis() {
        return connectTimeoutMillis;
    }

    public int getHandshakeTimeoutMillis() {
        return handshakeTimeoutMillis;
    }

    public int getHeartbeatIntervalMillis() {
        return heartbeatIntervalMillis;
    }

    private static void requirePositive(int value, String fieldName) {
        if (value <= 0) {
            throw new IllegalArgumentException(fieldName + " must be greater than zero");
        }
    }
}
