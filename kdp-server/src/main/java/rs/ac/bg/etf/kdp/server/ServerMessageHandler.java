package rs.ac.bg.etf.kdp.server;

import rs.ac.bg.etf.kdp.common.protocol.ProtocolFrame;

import java.io.IOException;

public interface ServerMessageHandler {

    default void onConnected(ServerConnection connection) throws IOException {
    }

    void onMessage(ServerConnection connection, ProtocolFrame frame) throws IOException;

    default void onDisconnected(ServerConnection connection) {
    }
}
