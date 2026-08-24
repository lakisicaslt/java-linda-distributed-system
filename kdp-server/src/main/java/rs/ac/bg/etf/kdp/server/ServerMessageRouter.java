package rs.ac.bg.etf.kdp.server;

import rs.ac.bg.etf.kdp.common.protocol.ConnectionRole;
import rs.ac.bg.etf.kdp.common.protocol.ProtocolFrame;

import java.io.IOException;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/** Sends each connection to the handler responsible for its declared role. */
public final class ServerMessageRouter implements ServerMessageHandler {

    private final Map<ConnectionRole, ServerMessageHandler> handlers;
    private final ServerMessageHandler fallback;

    public ServerMessageRouter(
            Map<ConnectionRole, ServerMessageHandler> handlers,
            ServerMessageHandler fallback) {
        Objects.requireNonNull(handlers, "handlers must not be null");
        this.handlers = new EnumMap<>(ConnectionRole.class);
        this.handlers.putAll(handlers);
        this.fallback = Objects.requireNonNull(fallback, "fallback handler must not be null");
    }

    @Override
    public void onConnected(ServerConnection connection) throws IOException {
        handlerFor(connection).onConnected(connection);
    }

    @Override
    public void onMessage(ServerConnection connection, ProtocolFrame frame) throws IOException {
        handlerFor(connection).onMessage(connection, frame);
    }

    @Override
    public void onDisconnected(ServerConnection connection) {
        handlerFor(connection).onDisconnected(connection);
    }

    private ServerMessageHandler handlerFor(ServerConnection connection) {
        return handlers.getOrDefault(connection.getRole(), fallback);
    }
}
