package rs.ac.bg.etf.kdp.client;

import rs.ac.bg.etf.kdp.common.protocol.ProtocolConstants;

import java.util.Objects;

public final class ClientConfig {

    public static final String DEFAULT_SERVER_HOST = "127.0.0.1";
    public static final int DEFAULT_SERVER_PORT = 5_050;

    private final String serverHost;
    private final int serverPort;
    private final int connectTimeoutMillis;
    private final int handshakeTimeoutMillis;

    public ClientConfig(String serverHost, int serverPort) {
        this(
                serverHost,
                serverPort,
                ProtocolConstants.CONNECT_TIMEOUT_MILLIS,
                ProtocolConstants.HANDSHAKE_TIMEOUT_MILLIS);
    }

    public ClientConfig(
            String serverHost,
            int serverPort,
            int connectTimeoutMillis,
            int handshakeTimeoutMillis) {
        this.serverHost = requireNonBlank(serverHost, "server host");
        if (serverPort < 1 || serverPort > 65_535) {
            throw new IllegalArgumentException("server port must be between 1 and 65535");
        }
        if (connectTimeoutMillis <= 0 || handshakeTimeoutMillis <= 0) {
            throw new IllegalArgumentException("client timeouts must be positive");
        }
        this.serverPort = serverPort;
        this.connectTimeoutMillis = connectTimeoutMillis;
        this.handshakeTimeoutMillis = handshakeTimeoutMillis;
    }

    public String getServerHost() {
        return serverHost;
    }

    public int getServerPort() {
        return serverPort;
    }

    public int getConnectTimeoutMillis() {
        return connectTimeoutMillis;
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
