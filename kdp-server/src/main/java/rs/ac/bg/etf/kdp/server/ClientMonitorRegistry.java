package rs.ac.bg.etf.kdp.server;

import rs.ac.bg.etf.kdp.common.domain.Job;
import rs.ac.bg.etf.kdp.common.protocol.BatchStatusMessage;
import rs.ac.bg.etf.kdp.common.protocol.BatchStatusProtocolCodec;
import rs.ac.bg.etf.kdp.common.protocol.JobStatusSnapshot;
import rs.ac.bg.etf.kdp.common.protocol.MessageType;
import rs.ac.bg.etf.kdp.common.protocol.ProtocolFrame;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/** Keeps the long-lived client connections used for failure notifications. */
public final class ClientMonitorRegistry implements JobFailureListener {

    private final BatchStatusProtocolCodec codec = new BatchStatusProtocolCodec();
    private final Map<ServerConnection, Set<UUID>> batchesByConnection =
            new IdentityHashMap<>();
    private final Map<UUID, Set<ServerConnection>> connectionsByBatch = new HashMap<>();
    private final Map<UUID, Job> pendingFailures = new HashMap<>();

    private Consumer<Job> unavailableClientHandler = job -> { };

    public synchronized void setUnavailableClientHandler(Consumer<Job> handler) {
        unavailableClientHandler = Objects.requireNonNull(
                handler, "unavailable client handler must not be null");
    }

    public synchronized void monitor(ServerConnection connection, UUID batchId) {
        Objects.requireNonNull(connection, "client connection must not be null");
        Objects.requireNonNull(batchId, "batch id must not be null");
        batchesByConnection.computeIfAbsent(connection, ignored -> new HashSet<>())
                .add(batchId);
        connectionsByBatch.computeIfAbsent(batchId, ignored -> new HashSet<>())
                .add(connection);
    }

    public void disconnect(ServerConnection connection) {
        List<Job> jobsToAbort = new ArrayList<>();
        Consumer<Job> handler;
        synchronized (this) {
            Set<UUID> monitoredBatches = batchesByConnection.remove(connection);
            if (monitoredBatches == null) {
                return;
            }
            for (UUID batchId : monitoredBatches) {
                Set<ServerConnection> connections = connectionsByBatch.get(batchId);
                if (connections == null) {
                    continue;
                }
                connections.remove(connection);
                if (!connections.isEmpty()) {
                    continue;
                }
                connectionsByBatch.remove(batchId);
                pendingFailures.values().stream()
                        .filter(job -> job.getBatchId().equals(batchId))
                        .forEach(jobsToAbort::add);
            }
            jobsToAbort.forEach(job -> pendingFailures.remove(job.getId()));
            handler = unavailableClientHandler;
        }
        jobsToAbort.forEach(handler);
    }

    public synchronized void decisionMade(Job job) {
        Objects.requireNonNull(job, "job must not be null");
        pendingFailures.remove(job.getId());
    }

    @Override
    public synchronized boolean workerFailed(Job job) {
        Objects.requireNonNull(job, "job must not be null");
        Set<ServerConnection> monitoredConnections = connectionsByBatch.get(job.getBatchId());
        if (monitoredConnections == null || monitoredConnections.isEmpty()) {
            return false;
        }

        BatchStatusMessage notification = new BatchStatusMessage(
                job.getBatchId(),
                List.of(new JobStatusSnapshot(
                        job.getId(), job.getStatus(), job.getFailureReason())));
        byte[] payload = codec.encodeStatus(notification);
        boolean delivered = false;
        for (ServerConnection connection : List.copyOf(monitoredConnections)) {
            try {
                connection.send(ProtocolFrame.request(
                        MessageType.JOB_FAILED_NOTIFICATION, payload));
                delivered = true;
            } catch (IOException exception) {
                closeQuietly(connection);
            }
        }
        if (delivered) {
            pendingFailures.put(job.getId(), job);
        }
        return delivered;
    }

    private static void closeQuietly(ServerConnection connection) {
        try {
            connection.close();
        } catch (IOException ignored) {
            // The failed notification is the relevant connection error.
        }
    }
}
