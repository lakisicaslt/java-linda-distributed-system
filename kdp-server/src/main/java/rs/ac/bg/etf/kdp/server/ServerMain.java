package rs.ac.bg.etf.kdp.server;

import rs.ac.bg.etf.kdp.common.protocol.ConnectionRole;
import rs.ac.bg.etf.kdp.common.protocol.MessageType;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Clock;
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

        ServerJobLogger jobLogger = new ServerJobLogger(
                Path.of("server-data", "server.log"));
        WorkerRegistry workerRegistry = new WorkerRegistry();
        TaskScheduler taskScheduler = new TaskScheduler(
                workerRegistry, Clock.systemUTC(), jobLogger);
        ServerTaskResultStore resultStore = new ServerTaskResultStore(
                Path.of("server-data", "results"));
        WorkerSessionHandler workerHandler = new WorkerSessionHandler(
                workerRegistry, taskScheduler, resultStore);
        ServerJobStore jobStore = new ServerJobStore(
                Path.of("server-data", "jobs"), Clock.systemUTC(), jobLogger);
        ClientSessionHandler clientHandler = new ClientSessionHandler(
                jobStore, taskScheduler, resultStore);
        LindaSessionHandler lindaHandler = new LindaSessionHandler(jobStore);
        ServerMessageHandler notImplemented = (connection, frame) -> {
            String message = "Message is not implemented yet: " + frame.getType();
            connection.send(frame.reply(MessageType.ERROR, message.getBytes(StandardCharsets.UTF_8)));
        };
        ServerMessageRouter router = new ServerMessageRouter(
                Map.of(
                        ConnectionRole.CLIENT, clientHandler,
                        ConnectionRole.WORKER, workerHandler,
                        ConnectionRole.LINDA, lindaHandler),
                notImplemented);
        CentralServer server = new CentralServer(config, router);
        WorkerLivenessMonitor livenessMonitor = new WorkerLivenessMonitor(
                workerRegistry,
                WorkerLivenessMonitor.DEFAULT_CHECK_INTERVAL_MILLIS,
                WorkerLivenessMonitor.DEFAULT_HEARTBEAT_TIMEOUT_MILLIS);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            livenessMonitor.close();
            server.close();
            lindaHandler.close();
            jobLogger.close();
        }, "kdp-server-shutdown"));
        server.start();
        livenessMonitor.start();
        System.out.println("KDP server listening on port " + server.getLocalPort());
        server.awaitTermination();
        lindaHandler.close();
        jobLogger.close();
    }
}
