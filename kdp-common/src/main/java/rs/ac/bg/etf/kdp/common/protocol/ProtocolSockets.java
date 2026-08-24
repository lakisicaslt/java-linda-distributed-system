package rs.ac.bg.etf.kdp.common.protocol;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Objects;

public final class ProtocolSockets {

    private ProtocolSockets() {
    }

    public static Socket connect(
            String host,
            int port,
            int connectTimeoutMillis,
            int readTimeoutMillis) throws IOException {
        Objects.requireNonNull(host, "host must not be null");
        if (host.isBlank()) {
            throw new IllegalArgumentException("host must not be blank");
        }
        if (port < 1 || port > 65_535) {
            throw new IllegalArgumentException("port must be between 1 and 65535");
        }
        validateTimeout(connectTimeoutMillis, "connect timeout");
        validateTimeout(readTimeoutMillis, "read timeout");

        Socket socket = new Socket();
        try {
            socket.connect(new InetSocketAddress(host, port), connectTimeoutMillis);
            configure(socket, readTimeoutMillis);
            return socket;
        } catch (IOException | RuntimeException exception) {
            try {
                socket.close();
            } catch (IOException closeException) {
                exception.addSuppressed(closeException);
            }
            throw exception;
        }
    }

    public static void configure(Socket socket, int readTimeoutMillis) throws IOException {
        Objects.requireNonNull(socket, "socket must not be null");
        validateTimeout(readTimeoutMillis, "read timeout");
        socket.setSoTimeout(readTimeoutMillis);
        socket.setTcpNoDelay(true);
        socket.setKeepAlive(true);
    }

    private static void validateTimeout(int timeoutMillis, String fieldName) {
        if (timeoutMillis <= 0) {
            throw new IllegalArgumentException(fieldName + " must be greater than zero");
        }
    }
}
