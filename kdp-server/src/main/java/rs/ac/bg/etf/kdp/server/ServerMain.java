package rs.ac.bg.etf.kdp.server;

import rs.ac.bg.etf.kdp.common.protocol.MessageType;
import rs.ac.bg.etf.kdp.common.protocol.ConnectionRole;

import java.nio.charset.StandardCharsets;
import java.util.Map;

public final class ServerMain {

    private ServerMain() {
    }

    public static void main(String[] args) throws Exception {
        int port = args.length == 0 ? ServerConfig.DEFAULT_PORT : Integer.parseInt(args[0]);
        ServerConfig config = new ServerConfig(
                "0.0.0.0",
                port,
                ServerConfig.DEFAULT_BACKLOG,
                ServerConfig.DEFAULT_MAX_CONNECTIONS,
                5_000);

        WorkerRegistry workerRegistry = new WorkerRegistry();
        TaskScheduler taskScheduler = new TaskScheduler(workerRegistry);
        WorkerSessionHandler workerHandler = new WorkerSessionHandler(
                workerRegistry, taskScheduler);
        ServerMessageHandler notImplemented = (connection, frame) -> {
            String message = "Message is not implemented yet: " + frame.getType();
            connection.send(frame.reply(MessageType.ERROR, message.getBytes(StandardCharsets.UTF_8)));
        };
        ServerMessageRouter router = new ServerMessageRouter(
                Map.of(ConnectionRole.WORKER, workerHandler), notImplemented);
        CentralServer server = new CentralServer(config, router);

        Runtime.getRuntime().addShutdownHook(new Thread(server::close, "kdp-server-shutdown"));
        server.start();
        System.out.println("KDP server listening on port " + server.getLocalPort());
        server.awaitTermination();
    }
}
