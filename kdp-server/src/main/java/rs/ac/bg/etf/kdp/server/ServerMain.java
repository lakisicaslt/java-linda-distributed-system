package rs.ac.bg.etf.kdp.server;

import rs.ac.bg.etf.kdp.common.protocol.MessageType;

import java.nio.charset.StandardCharsets;

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

        CentralServer server = new CentralServer(config, (connection, frame) -> {
            String message = "Message is not implemented yet: " + frame.getType();
            connection.send(frame.reply(MessageType.ERROR, message.getBytes(StandardCharsets.UTF_8)));
        });

        Runtime.getRuntime().addShutdownHook(new Thread(server::close, "kdp-server-shutdown"));
        server.start();
        System.out.println("KDP server listening on port " + server.getLocalPort());
        server.awaitTermination();
    }
}
