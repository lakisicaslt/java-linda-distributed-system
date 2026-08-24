package rs.ac.bg.etf.kdp.server;

import rs.ac.bg.etf.kdp.common.protocol.ConnectionRole;
import rs.ac.bg.etf.kdp.common.protocol.MessageType;
import rs.ac.bg.etf.kdp.common.protocol.ProtocolError;
import rs.ac.bg.etf.kdp.common.protocol.ProtocolException;
import rs.ac.bg.etf.kdp.common.protocol.ProtocolFrame;
import rs.ac.bg.etf.kdp.common.protocol.ProtocolFrameCodec;
import rs.ac.bg.etf.kdp.common.protocol.ProtocolHandshake;
import rs.ac.bg.etf.kdp.common.protocol.ProtocolRoleValidator;
import rs.ac.bg.etf.kdp.common.protocol.ProtocolSockets;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.Set;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class CentralServer implements AutoCloseable {

    private static final Logger LOGGER = Logger.getLogger(CentralServer.class.getName());

    private final ServerConfig config;
    private final ServerMessageHandler messageHandler;
    private final ProtocolFrameCodec codec = new ProtocolFrameCodec();
    private final ProtocolHandshake handshake = new ProtocolHandshake();
    private final AtomicBoolean running = new AtomicBoolean();
    private final Set<Socket> activeSockets = ConcurrentHashMap.newKeySet();

    private volatile ServerSocket serverSocket;
    private volatile Thread acceptThread;
    private volatile ThreadPoolExecutor connectionExecutor;

    public CentralServer(ServerConfig config, ServerMessageHandler messageHandler) {
        this.config = Objects.requireNonNull(config, "server config must not be null");
        this.messageHandler = Objects.requireNonNull(
                messageHandler, "message handler must not be null");
    }

    public synchronized void start() throws IOException {
        if (running.get()) {
            throw new IllegalStateException("server is already running");
        }

        ServerSocket createdSocket = new ServerSocket();
        try {
            createdSocket.setReuseAddress(true);
            createdSocket.bind(
                    new InetSocketAddress(config.getBindHost(), config.getPort()),
                    config.getBacklog());
        } catch (IOException | RuntimeException exception) {
            closeQuietly(createdSocket);
            throw exception;
        }

        ThreadPoolExecutor createdExecutor = createConnectionExecutor();
        serverSocket = createdSocket;
        connectionExecutor = createdExecutor;
        running.set(true);

        acceptThread = new Thread(this::acceptLoop, "kdp-server-accept");
        acceptThread.start();
    }

    public boolean isRunning() {
        return running.get();
    }

    public int getLocalPort() {
        ServerSocket socket = serverSocket;
        if (socket == null || !socket.isBound()) {
            throw new IllegalStateException("server is not started");
        }
        return socket.getLocalPort();
    }

    public void awaitTermination() throws InterruptedException {
        Thread thread = acceptThread;
        if (thread != null) {
            thread.join();
        }
    }

    private void acceptLoop() {
        while (running.get()) {
            try {
                Socket socket = serverSocket.accept();
                try {
                    ProtocolSockets.configure(socket, config.getHandshakeTimeoutMillis());
                    submitConnection(socket);
                } catch (IOException | RuntimeException exception) {
                    closeQuietly(socket);
                    throw exception;
                }
            } catch (SocketException exception) {
                if (running.get()) {
                    LOGGER.log(Level.WARNING, "Server socket failed", exception);
                }
            } catch (IOException exception) {
                if (running.get()) {
                    LOGGER.log(Level.WARNING, "Could not accept client connection", exception);
                }
            }
        }
    }

    private void submitConnection(Socket socket) {
        activeSockets.add(socket);
        try {
            connectionExecutor.execute(() -> handleConnection(socket));
        } catch (RejectedExecutionException exception) {
            activeSockets.remove(socket);
            closeQuietly(socket);
            LOGGER.fine("Connection rejected because the server reached its connection limit");
        }
    }

    private void handleConnection(Socket socket) {
        ServerConnection connection = null;
        boolean connectedCallbackCompleted = false;

        try (socket;
             InputStream input = socket.getInputStream();
             OutputStream output = socket.getOutputStream()) {
            ProtocolFrame helloFrame = codec.read(input);
            ConnectionRole role = handshake.readHello(helloFrame);
            codec.write(output, handshake.accept(helloFrame));

            socket.setSoTimeout(0);
            connection = new ServerConnection(socket, role, codec, output);
            messageHandler.onConnected(connection);
            connectedCallbackCompleted = true;

            while (running.get() && !socket.isClosed()) {
                ProtocolFrame frame = codec.read(input);
                rejectRepeatedHandshake(frame);
                ProtocolRoleValidator.requireAllowed(role, frame.getType());
                messageHandler.onMessage(connection, frame);
            }
        } catch (ProtocolException exception) {
            LOGGER.fine(() -> "Rejected protocol connection from "
                    + socket.getRemoteSocketAddress() + ": " + exception.getMessage());
        } catch (SocketException exception) {
            if (running.get()) {
                LOGGER.fine(() -> "Connection closed: " + exception.getMessage());
            }
        } catch (IOException exception) {
            if (running.get()) {
                LOGGER.log(Level.FINE, "Connection handler failed", exception);
            }
        } catch (RuntimeException exception) {
            LOGGER.log(Level.WARNING, "Unexpected connection handler failure", exception);
        } finally {
            if (connection != null && connectedCallbackCompleted) {
                try {
                    messageHandler.onDisconnected(connection);
                } catch (RuntimeException exception) {
                    LOGGER.log(Level.WARNING, "Disconnect callback failed", exception);
                }
            }
            activeSockets.remove(socket);
        }
    }

    private static void rejectRepeatedHandshake(ProtocolFrame frame) throws ProtocolException {
        if (frame.getType() == MessageType.HELLO
                || frame.getType() == MessageType.HELLO_ACCEPTED) {
            throw new ProtocolException(
                    ProtocolError.INVALID_HANDSHAKE,
                    "Handshake messages are only allowed at the start of a connection");
        }
    }

    private ThreadPoolExecutor createConnectionExecutor() {
        return new ThreadPoolExecutor(
                config.getMaxConnections(),
                config.getMaxConnections(),
                0L,
                TimeUnit.MILLISECONDS,
                new SynchronousQueue<>(),
                new ConnectionThreadFactory(),
                new ThreadPoolExecutor.AbortPolicy());
    }

    @Override
    public synchronized void close() {
        if (!running.getAndSet(false)) {
            return;
        }

        closeQuietly(serverSocket);
        activeSockets.forEach(CentralServer::closeQuietly);
        activeSockets.clear();

        ThreadPoolExecutor executor = connectionExecutor;
        if (executor != null) {
            executor.shutdownNow();
            try {
                if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                    LOGGER.warning("Connection handlers did not stop within five seconds");
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private static void closeQuietly(AutoCloseable closeable) {
        if (closeable == null) {
            return;
        }
        try {
            closeable.close();
        } catch (Exception ignored) {
            // The original connection event is more useful than a cleanup failure.
        }
    }

    private static final class ConnectionThreadFactory implements ThreadFactory {

        private final AtomicInteger sequence = new AtomicInteger();

        @Override
        public Thread newThread(Runnable task) {
            Thread thread = new Thread(task, "kdp-connection-" + sequence.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        }
    }
}
