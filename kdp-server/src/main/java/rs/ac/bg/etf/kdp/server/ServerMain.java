package rs.ac.bg.etf.kdp.server;

import java.nio.file.Path;

/** Headless central server entry point. */
public final class ServerMain {

    private ServerMain() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length > 1) {
            System.err.println("Usage: ServerMain [port]");
            return;
        }
        int port = args.length == 0 ? ServerConfig.DEFAULT_PORT : Integer.parseInt(args[0]);
        ServerConfig config = new ServerConfig(
                "0.0.0.0",
                port,
                ServerConfig.DEFAULT_BACKLOG,
                ServerConfig.DEFAULT_MAX_CONNECTIONS,
                5_000);

        ServerRuntime runtime = ServerRuntime.create(config, Path.of("server-data"));
        Runtime.getRuntime().addShutdownHook(
                new Thread(runtime::close, "kdp-server-shutdown"));
        runtime.start();
        System.out.println("KDP server listening on port " + runtime.getLocalPort());
        runtime.awaitTermination();
    }
}
