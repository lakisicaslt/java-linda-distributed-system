package rs.ac.bg.etf.kdp.server;

import rs.ac.bg.etf.kdp.common.domain.JobStatus;
import rs.ac.bg.etf.kdp.common.domain.WorkerStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Immutable data shown by the server GUI. */
public record ServerOverview(
        List<WorkerRow> workers,
        List<JobRow> jobs) {

    public ServerOverview {
        workers = List.copyOf(workers);
        jobs = List.copyOf(jobs);
    }

    public record WorkerRow(
            UUID id,
            String hostname,
            String operatingSystem,
            String javaVersion,
            int capacity,
            int activeTasks,
            WorkerStatus status,
            Instant lastHeartbeatAt) {
    }

    public record JobRow(
            UUID id,
            UUID batchId,
            String archive,
            JobStatus status,
            Instant createdAt,
            Instant finishedAt) {
    }
}
