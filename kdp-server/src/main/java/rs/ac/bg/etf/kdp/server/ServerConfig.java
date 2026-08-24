package rs.ac.bg.etf.kdp.server;

import rs.ac.bg.etf.kdp.common.protocol.ProtocolConstants;

import java.util.Objects;

public final class ServerConfig {

    public static final int DEFAULT_PORT = 5_050;
    public static final int DEFAULT_BACKLOG = 50;
    public static final int DEFAULT_MAX_CONNECTIONS = 64;

    private final String bindHost;
    private final int port;
    private final int backlog;
    private final int maxConnections;
    private final int handshakeTimeoutMillis;

    public ServerConfig(
            String bindHost,
            int port,
            int backlog,
            int maxConnections,
            int handshakeTimeoutMillis) {
        this.bindHost = requireNonBlank(bindHost, "bind host");
        if (port < 0 || port > 65_535) {
            throw new IllegalArgumentException("port must be between 0 and 65535");
        }
        if (backlog <= 0) {
            throw new IllegalArgumentException("backlog must be greater than zero");
        }
        if (maxConnections <= 0) {
            throw new IllegalArgumentException("maximum connections must be greater than zero");
        }
        if (handshakeTimeoutMillis <= 0) {
            throw new IllegalArgumentException("handshake timeout must be greater than zero");
        }
        this.port = port;
        this.backlog = backlog;
        this.maxConnections = maxConnections;
        this.handshakeTimeoutMillis = handshakeTimeoutMillis;
    }

    public static ServerConfig defaults() {
        return new ServerConfig(
                "0.0.0.0",
                DEFAULT_PORT,
                DEFAULT_BACKLOG,
                DEFAULT_MAX_CONNECTIONS,
                ProtocolConstants.HANDSHAKE_TIMEOUT_MILLIS);
    }

    public String getBindHost() {
        return bindHost;
    }

    public int getPort() {
        return port;
    }

    public int getBacklog() {
        return backlog;
    }

    public int getMaxConnections() {
        return maxConnections;
    }

    public int getHandshakeTimeoutMillis() {
        return handshakeTimeoutMillis;
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
