package rs.ac.bg.etf.kdp.server;

import org.junit.jupiter.api.Test;
import rs.ac.bg.etf.kdp.common.domain.Job;
import rs.ac.bg.etf.kdp.common.domain.JobSpecification;
import rs.ac.bg.etf.kdp.common.protocol.BatchStatusMessage;
import rs.ac.bg.etf.kdp.common.protocol.BatchStatusProtocolCodec;
import rs.ac.bg.etf.kdp.common.protocol.ConnectionRole;
import rs.ac.bg.etf.kdp.common.protocol.MessageType;
import rs.ac.bg.etf.kdp.common.protocol.ProtocolFrame;
import rs.ac.bg.etf.kdp.common.protocol.ProtocolFrameCodec;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.Socket;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientMonitorRegistryTest {

    private static final Instant NOW = Instant.parse("2026-08-25T12:00:00Z");

    @Test
    void notifiesMonitorAndReportsUnavailableClientAfterDisconnect() throws Exception {
        ClientMonitorRegistry registry = new ClientMonitorRegistry();
        AtomicReference<Job> unavailableJob = new AtomicReference<>();
        registry.setUnavailableClientHandler(unavailableJob::set);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ServerConnection connection = new ServerConnection(
                new Socket(),
                ConnectionRole.CLIENT,
                new ProtocolFrameCodec(),
                output);
        Job job = failedJob();
        registry.monitor(connection, job.getBatchId());

        assertTrue(registry.workerFailed(job));

        ProtocolFrame notification = new ProtocolFrameCodec().read(
                new ByteArrayInputStream(output.toByteArray()));
        assertEquals(MessageType.JOB_FAILED_NOTIFICATION, notification.getType());
        BatchStatusMessage status = new BatchStatusProtocolCodec().decodeStatus(
                notification.getPayload());
        assertEquals(job.getId(), status.getJobs().get(0).getJobId());

        registry.disconnect(connection);

        assertSame(job, unavailableJob.get());
    }

    @Test
    void reportsThatUserIsUnavailableWithoutAMonitor() {
        ClientMonitorRegistry registry = new ClientMonitorRegistry();

        assertFalse(registry.workerFailed(failedJob()));
    }

    @Test
    void completedDecisionIsNotAbortedWhenMonitorDisconnects() {
        ClientMonitorRegistry registry = new ClientMonitorRegistry();
        AtomicReference<Job> unavailableJob = new AtomicReference<>();
        registry.setUnavailableClientHandler(unavailableJob::set);
        ServerConnection connection = new ServerConnection(
                new Socket(),
                ConnectionRole.CLIENT,
                new ProtocolFrameCodec(),
                new ByteArrayOutputStream());
        Job job = failedJob();
        registry.monitor(connection, job.getBatchId());
        assertTrue(registry.workerFailed(job));

        registry.decisionMade(job);
        registry.disconnect(connection);

        assertNull(unavailableJob.get());
    }

    private static Job failedJob() {
        Job job = new Job(
                UUID.randomUUID(),
                UUID.randomUUID(),
                new JobSpecification(
                        "job.jar", List.of(), List.of(), List.of(), List.of()),
                NOW);
        job.getRootTask().assignTo(UUID.randomUUID(), NOW.plusSeconds(1));
        job.schedule(NOW.plusSeconds(1));
        job.getRootTask().markRunning(NOW.plusSeconds(2));
        job.markRunning(NOW.plusSeconds(2));
        job.getRootTask().markFailed(NOW.plusSeconds(3), "Worker disconnected");
        job.markFailed(NOW.plusSeconds(3), "Worker disconnected");
        return job;
    }
}
