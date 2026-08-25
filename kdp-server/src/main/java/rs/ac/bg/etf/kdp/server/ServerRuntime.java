package rs.ac.bg.etf.kdp.server;

import rs.ac.bg.etf.kdp.common.domain.Job;
import rs.ac.bg.etf.kdp.common.domain.Worker;
import rs.ac.bg.etf.kdp.common.protocol.ConnectionRole;
import rs.ac.bg.etf.kdp.common.protocol.MessageType;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/** Owns all components needed by one central server process. */
public final class ServerRuntime implements AutoCloseable {

    private final CentralServer server;
    private final WorkerLivenessMonitor livenessMonitor;
    private final LindaSessionHandler lindaHandler;
    private final ServerJobLogger jobLogger;
    private final WorkerRegistry workerRegistry;
    private final ServerJobStore jobStore;
    private final Path logFile;
    private final AtomicBoolean closed = new AtomicBoolean();

    private ServerRuntime(
            CentralServer server,
            WorkerLivenessMonitor livenessMonitor,
            LindaSessionHandler lindaHandler,
            ServerJobLogger jobLogger,
            WorkerRegistry workerRegistry,
            ServerJobStore jobStore,
            Path logFile) {
        this.server = server;
        this.livenessMonitor = livenessMonitor;
        this.lindaHandler = lindaHandler;
        this.jobLogger = jobLogger;
        this.workerRegistry = workerRegistry;
        this.jobStore = jobStore;
        this.logFile = logFile;
    }

    public static ServerRuntime create(ServerConfig config, Path dataDirectory)
            throws IOException {
        Objects.requireNonNull(config, "server config must not be null");
        Objects.requireNonNull(dataDirectory, "data directory must not be null");
        Path data = dataDirectory.toAbsolutePath().normalize();
        Path logFile = data.resolve("server.log");

        ServerJobLogger jobLogger = new ServerJobLogger(logFile);
        try {
            WorkerRegistry workerRegistry = new WorkerRegistry();
            TaskScheduler taskScheduler = new TaskScheduler(
                    workerRegistry, Clock.systemUTC(), jobLogger);
            ServerTaskResultStore resultStore = new ServerTaskResultStore(
                    data.resolve("results"));
            WorkerSessionHandler workerHandler = new WorkerSessionHandler(
                    workerRegistry, taskScheduler, resultStore);
            ServerJobStore jobStore = new ServerJobStore(
                    data.resolve("jobs"), Clock.systemUTC(), jobLogger);
            ClientSessionHandler clientHandler = new ClientSessionHandler(
                    jobStore, taskScheduler, resultStore);
            LindaSessionHandler lindaHandler = new LindaSessionHandler(jobStore, taskScheduler);
            ServerMessageHandler unsupportedMessage = (connection, frame) -> {
                String message = "Unsupported message: " + frame.getType();
                connection.send(frame.reply(
                        MessageType.ERROR, message.getBytes(StandardCharsets.UTF_8)));
            };
            ServerMessageRouter router = new ServerMessageRouter(
                    Map.of(
                            ConnectionRole.CLIENT, clientHandler,
                            ConnectionRole.WORKER, workerHandler,
                            ConnectionRole.LINDA, lindaHandler),
                    unsupportedMessage);
            CentralServer server = new CentralServer(config, router);
            WorkerLivenessMonitor monitor = new WorkerLivenessMonitor(
                    workerRegistry,
                    WorkerLivenessMonitor.DEFAULT_CHECK_INTERVAL_MILLIS,
                    WorkerLivenessMonitor.DEFAULT_HEARTBEAT_TIMEOUT_MILLIS);
            return new ServerRuntime(
                    server,
                    monitor,
                    lindaHandler,
                    jobLogger,
                    workerRegistry,
                    jobStore,
                    logFile);
        } catch (RuntimeException exception) {
            jobLogger.close();
            throw exception;
        }
    }

    public void start() throws IOException {
        if (closed.get()) {
            throw new IllegalStateException("server runtime is closed");
        }
        server.start();
        livenessMonitor.start();
    }

    public boolean isRunning() {
        return server.isRunning();
    }

    public int getLocalPort() {
        return server.getLocalPort();
    }

    public Path getLogFile() {
        return logFile;
    }

    public void awaitTermination() throws InterruptedException {
        server.awaitTermination();
    }

    public ServerOverview getOverview() {
        List<ServerOverview.WorkerRow> workers;
        synchronized (workerRegistry) {
            workers = workerRegistry.getWorkers().stream()
                    .map(ServerRuntime::workerRow)
                    .toList();
        }
        List<ServerOverview.JobRow> jobs;
        synchronized (jobStore) {
            jobs = jobStore.getJobs().stream()
                    .map(ServerRuntime::jobRow)
                    .toList();
        }
        return new ServerOverview(workers, jobs);
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        livenessMonitor.close();
        server.close();
        lindaHandler.close();
        jobLogger.close();
    }

    private static ServerOverview.WorkerRow workerRow(Worker worker) {
        return new ServerOverview.WorkerRow(
                worker.getId(),
                worker.getHostname(),
                worker.getOperatingSystem(),
                worker.getJavaVersion(),
                worker.getCapacity(),
                worker.getActiveTaskIds().size(),
                worker.getStatus(),
                worker.getLastHeartbeatAt());
    }

    private static ServerOverview.JobRow jobRow(Job job) {
        return new ServerOverview.JobRow(
                job.getId(),
                job.getBatchId(),
                job.getSpecification().getJarFileName(),
                job.getStatus(),
                job.getCreatedAt(),
                job.getFinishedAt());
    }
}
