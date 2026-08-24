package rs.ac.bg.etf.kdp.worker;

import rs.ac.bg.etf.kdp.common.protocol.ProtocolConstants;

public final class WorkerMain {

    private WorkerMain() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length > 3) {
            System.err.println("Usage: WorkerMain [server-host] [server-port] [capacity]");
            return;
        }

        String host = args.length >= 1 ? args[0] : "127.0.0.1";
        int port = args.length >= 2
                ? Integer.parseInt(args[1])
                : WorkerConfig.DEFAULT_SERVER_PORT;
        int capacity = args.length >= 3
                ? Integer.parseInt(args[2])
                : Math.max(1, Runtime.getRuntime().availableProcessors());

        WorkerConfig config = new WorkerConfig(
                host,
                port,
                java.util.UUID.randomUUID(),
                capacity,
                ProtocolConstants.CONNECT_TIMEOUT_MILLIS,
                ProtocolConstants.HANDSHAKE_TIMEOUT_MILLIS,
                WorkerConfig.DEFAULT_HEARTBEAT_INTERVAL_MILLIS);
        WorkerClient worker = new WorkerClient(config);
        Runtime.getRuntime().addShutdownHook(
                new Thread(worker::close, "kdp-worker-shutdown"));

        worker.start();
        System.out.printf(
                "Worker %s connected to %s:%d with capacity %d%n",
                config.getWorkerId(), host, port, capacity);
        worker.awaitTermination();
    }
}
