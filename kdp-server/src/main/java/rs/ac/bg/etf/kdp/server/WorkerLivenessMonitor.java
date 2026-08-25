package rs.ac.bg.etf.kdp.server;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Periodically disconnects workers that stopped sending heartbeat messages. */
public final class WorkerLivenessMonitor implements AutoCloseable {

    public static final int DEFAULT_CHECK_INTERVAL_MILLIS = 2_000;
    public static final int DEFAULT_HEARTBEAT_TIMEOUT_MILLIS = 6_000;

    private static final Logger LOGGER = Logger.getLogger(WorkerLivenessMonitor.class.getName());

    private final WorkerRegistry workerRegistry;
    private final int checkIntervalMillis;
    private final int heartbeatTimeoutMillis;
    private final Clock clock;
    private final AtomicBoolean running = new AtomicBoolean();

    private ScheduledExecutorService executor;

    public WorkerLivenessMonitor(
            WorkerRegistry workerRegistry,
            int checkIntervalMillis,
            int heartbeatTimeoutMillis) {
        this(workerRegistry, checkIntervalMillis, heartbeatTimeoutMillis, Clock.systemUTC());
    }

    WorkerLivenessMonitor(
            WorkerRegistry workerRegistry,
            int checkIntervalMillis,
            int heartbeatTimeoutMillis,
            Clock clock) {
        this.workerRegistry = Objects.requireNonNull(
                workerRegistry, "worker registry must not be null");
        if (checkIntervalMillis <= 0) {
            throw new IllegalArgumentException("check interval must be greater than zero");
        }
        if (heartbeatTimeoutMillis <= 0) {
            throw new IllegalArgumentException("heartbeat timeout must be greater than zero");
        }
        this.checkIntervalMillis = checkIntervalMillis;
        this.heartbeatTimeoutMillis = heartbeatTimeoutMillis;
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    public synchronized void start() {
        if (!running.compareAndSet(false, true)) {
            throw new IllegalStateException("worker liveness monitor is already running");
        }
        executor = Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(task, "kdp-worker-liveness");
            thread.setDaemon(true);
            return thread;
        });
        executor.scheduleWithFixedDelay(
                this::checkWorkers,
                checkIntervalMillis,
                checkIntervalMillis,
                TimeUnit.MILLISECONDS);
    }

    void checkWorkers() {
        Instant cutoff = clock.instant().minusMillis(heartbeatTimeoutMillis);
        for (ServerConnection connection
                : workerRegistry.findUnresponsiveConnections(cutoff)) {
            try {
                connection.close();
            } catch (IOException exception) {
                LOGGER.log(Level.FINE, "Could not close an unresponsive worker", exception);
            }
        }
    }

    @Override
    public synchronized void close() {
        if (!running.getAndSet(false)) {
            return;
        }
        if (executor != null) {
            executor.shutdownNow();
        }
    }
}
