package rs.ac.bg.etf.kdp.server;

import rs.ac.bg.etf.kdp.common.protocol.ConnectionRole;
import rs.ac.bg.etf.kdp.common.protocol.ProtocolFrame;
import rs.ac.bg.etf.kdp.common.protocol.ProtocolFrameCodec;
import rs.ac.bg.etf.kdp.common.protocol.ProtocolRoleValidator;

import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketAddress;
import java.util.Objects;

public final class ServerConnection implements AutoCloseable {

    private final Socket socket;
    private final ConnectionRole role;
    private final ProtocolFrameCodec codec;
    private final OutputStream output;
    private final Object writeLock = new Object();

    ServerConnection(
            Socket socket,
            ConnectionRole role,
            ProtocolFrameCodec codec,
            OutputStream output) {
        this.socket = Objects.requireNonNull(socket, "socket must not be null");
        this.role = Objects.requireNonNull(role, "connection role must not be null");
        this.codec = Objects.requireNonNull(codec, "frame codec must not be null");
        this.output = Objects.requireNonNull(output, "output stream must not be null");
    }

    public void send(ProtocolFrame frame) throws IOException {
        Objects.requireNonNull(frame, "frame must not be null");
        ProtocolRoleValidator.requireAllowed(role, frame.getType());
        synchronized (writeLock) {
            codec.write(output, frame);
        }
    }

    public ConnectionRole getRole() {
        return role;
    }

    public SocketAddress getRemoteAddress() {
        return socket.getRemoteSocketAddress();
    }

    public boolean isOpen() {
        return !socket.isClosed();
    }

    @Override
    public void close() throws IOException {
        socket.close();
    }
}
