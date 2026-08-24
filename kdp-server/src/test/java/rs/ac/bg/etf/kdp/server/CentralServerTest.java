package rs.ac.bg.etf.kdp.server;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import rs.ac.bg.etf.kdp.common.protocol.ConnectionRole;
import rs.ac.bg.etf.kdp.common.protocol.MessageType;
import rs.ac.bg.etf.kdp.common.protocol.ProtocolConstants;
import rs.ac.bg.etf.kdp.common.protocol.ProtocolFrame;
import rs.ac.bg.etf.kdp.common.protocol.ProtocolFrameCodec;
import rs.ac.bg.etf.kdp.common.protocol.ProtocolHandshake;
import rs.ac.bg.etf.kdp.common.protocol.ProtocolSockets;

import java.io.IOException;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CentralServerTest {

    private final ProtocolFrameCodec codec = new ProtocolFrameCodec();
    private final ProtocolHandshake handshake = new ProtocolHandshake();
    private CentralServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.close();
        }
    }

    @Test
    void acceptsHandshakeAndDispatchesRoleCheckedMessage() throws Exception {
        RecordingHandler handler = new RecordingHandler();
        startServer(handler);

        try (Socket client = connectAndHandshake(ConnectionRole.CLIENT)) {
            ProtocolFrame request = ProtocolFrame.request(
                    MessageType.GET_BATCH_STATUS, new byte[0]);
            codec.write(client.getOutputStream(), request);

            assertTrue(handler.messageReceived.await(2, TimeUnit.SECONDS));
            assertEquals(1, handler.connected.get());
            assertEquals(ConnectionRole.CLIENT, handler.lastRole);
            assertEquals(MessageType.GET_BATCH_STATUS, handler.lastMessageType);
        }

        assertTrue(handler.disconnected.await(2, TimeUnit.SECONDS));
    }

    @Test
    void telnetAndHttpGarbageDoNotStopAcceptLoop() throws Exception {
        RecordingHandler handler = new RecordingHandler();
        startServer(handler);

        sendRaw("nonsense telnet command\r\n");
        sendRaw("GET / HTTP/1.1\r\nHost: localhost\r\n\r\n");

        try (Socket ignored = connectAndHandshake(ConnectionRole.CLIENT)) {
            assertTrue(server.isRunning());
            awaitConnectedCount(handler, 1);
            assertEquals(1, handler.connected.get());
        }
    }

    @Test
    void messageFromWrongRoleClosesOnlyThatConnection() throws Exception {
        RecordingHandler handler = new RecordingHandler();
        startServer(handler);

        try (Socket worker = connectAndHandshake(ConnectionRole.WORKER)) {
            ProtocolFrame clientOnlyMessage = ProtocolFrame.request(
                    MessageType.SUBMIT_BATCH, new byte[0]);
            codec.write(worker.getOutputStream(), clientOnlyMessage);
            assertTrue(handler.disconnected.await(2, TimeUnit.SECONDS));
        }

        assertEquals(0, handler.messages.get());
        try (Socket ignored = connectAndHandshake(ConnectionRole.CLIENT)) {
            assertTrue(server.isRunning());
            awaitConnectedCount(handler, 2);
            assertEquals(2, handler.connected.get());
        }
    }

    @Test
    void repeatedHandshakeIsRejected() throws Exception {
        RecordingHandler handler = new RecordingHandler();
        startServer(handler);

        try (Socket client = connectAndHandshake(ConnectionRole.CLIENT)) {
            codec.write(client.getOutputStream(), handshake.createHello(ConnectionRole.CLIENT));
            assertTrue(handler.disconnected.await(2, TimeUnit.SECONDS));
        }

        assertEquals(0, handler.messages.get());
        assertTrue(server.isRunning());
    }

    @Test
    void closeStopsServerAndIsIdempotent() throws Exception {
        startServer(new RecordingHandler());
        int port = server.getLocalPort();

        server.close();
        server.close();

        assertFalse(server.isRunning());
        assertThrows(IOException.class, () -> ProtocolSockets.connect(
                "127.0.0.1", port, 300, ProtocolConstants.HANDSHAKE_TIMEOUT_MILLIS));
    }

    @Test
    void silentConnectionIsClosedAfterHandshakeTimeout() throws Exception {
        RecordingHandler handler = new RecordingHandler();
        startServer(handler, 150);

        try (Socket silentClient = ProtocolSockets.connect(
                "127.0.0.1", server.getLocalPort(), 1_000, 1_000)) {
            assertEquals(-1, silentClient.getInputStream().read());
        }

        try (Socket ignored = connectAndHandshake(ConnectionRole.CLIENT)) {
            assertTrue(server.isRunning());
            awaitConnectedCount(handler, 1);
            assertEquals(1, handler.connected.get());
        }
    }

    private void startServer(ServerMessageHandler handler) throws IOException {
        startServer(handler, 1_000);
    }

    private void startServer(ServerMessageHandler handler, int handshakeTimeoutMillis)
            throws IOException {
        ServerConfig config = new ServerConfig(
                "127.0.0.1", 0, 10, 8, handshakeTimeoutMillis);
        server = new CentralServer(config, handler);
        server.start();
    }

    private Socket connectAndHandshake(ConnectionRole role) throws Exception {
        Socket socket = ProtocolSockets.connect(
                "127.0.0.1",
                server.getLocalPort(),
                1_000,
                1_000);
        try {
            ProtocolFrame hello = handshake.createHello(role);
            codec.write(socket.getOutputStream(), hello);
            ProtocolFrame response = codec.read(socket.getInputStream());
            handshake.verifyAccepted(response, hello.getRequestId());
            return socket;
        } catch (Exception exception) {
            socket.close();
            throw exception;
        }
    }

    private void sendRaw(String text) throws IOException {
        try (Socket socket = ProtocolSockets.connect(
                "127.0.0.1", server.getLocalPort(), 1_000, 1_000)) {
            socket.getOutputStream().write(text.getBytes(StandardCharsets.US_ASCII));
            socket.getOutputStream().flush();
        }
    }

    private static void awaitConnectedCount(RecordingHandler handler, int expected)
            throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (handler.connected.get() < expected && System.nanoTime() < deadline) {
            Thread.sleep(10);
        }
    }

    private static final class RecordingHandler implements ServerMessageHandler {

        private final AtomicInteger connected = new AtomicInteger();
        private final AtomicInteger messages = new AtomicInteger();
        private final CountDownLatch messageReceived = new CountDownLatch(1);
        private final CountDownLatch disconnected = new CountDownLatch(1);
        private volatile ConnectionRole lastRole;
        private volatile MessageType lastMessageType;

        @Override
        public void onConnected(ServerConnection connection) {
            lastRole = connection.getRole();
            connected.incrementAndGet();
        }

        @Override
        public void onMessage(ServerConnection connection, ProtocolFrame frame) {
            lastMessageType = frame.getType();
            messages.incrementAndGet();
            messageReceived.countDown();
        }

        @Override
        public void onDisconnected(ServerConnection connection) {
            disconnected.countDown();
        }
    }
}
