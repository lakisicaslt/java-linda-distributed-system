package rs.ac.bg.etf.kdp.server;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import rs.ac.bg.etf.kdp.common.domain.Job;
import rs.ac.bg.etf.kdp.common.domain.JobSpecification;
import rs.ac.bg.etf.kdp.common.protocol.ConnectionRole;
import rs.ac.bg.etf.kdp.common.protocol.MessageType;
import rs.ac.bg.etf.kdp.common.protocol.ProtocolFrame;
import rs.ac.bg.etf.kdp.common.protocol.ProtocolFrameCodec;
import rs.ac.bg.etf.kdp.common.protocol.TaskProtocolCodec;
import rs.ac.bg.etf.kdp.common.protocol.WorkerRegistrationMessage;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServerJobLoggerTest {

    private static final Instant NOW = Instant.parse("2026-08-25T14:00:00Z");
    private final ProtocolFrameCodec frameCodec = new ProtocolFrameCodec();
    private final TaskProtocolCodec taskCodec = new TaskProtocolCodec();

    @TempDir
    Path temporaryDirectory;

    @Test
    void recordsCompleteJobLifecycleThroughStoreAndScheduler() throws Exception {
        Path logFile = temporaryDirectory.resolve("server.log");
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        UUID jobId;

        try (ServerJobLogger logger = new ServerJobLogger(logFile)) {
            WorkerRegistry registry = new WorkerRegistry(clock);
            TaskScheduler scheduler = new TaskScheduler(registry, clock, logger);
            ServerJobStore store = new ServerJobStore(
                    temporaryDirectory.resolve("jobs"), clock, logger);
            JobSpecification specification = new JobSpecification(
                    "job.jar", List.of(), List.of(), List.of(), List.of());
            Job job = store.createBatch(List.of(specification)).getJobs().get(0);
            jobId = job.getId();
            Files.write(store.archivePath(job.getId()), new byte[]{1, 2, 3});

            ByteArrayOutputStream workerOutput = new ByteArrayOutputStream();
            ServerConnection workerConnection = new ServerConnection(
                    new Socket(), ConnectionRole.WORKER, frameCodec, workerOutput);
            registry.register(workerConnection, new WorkerRegistrationMessage(
                    UUID.randomUUID(), "worker-host", "Linux", "21", 1));

            scheduler.submit(job, store.archivePath(job.getId()), List.of());
            ProtocolFrame assignment = frameCodec.read(
                    new ByteArrayInputStream(workerOutput.toByteArray()));
            scheduler.accept(workerConnection, assignment.reply(
                    MessageType.TASK_ACCEPTED,
                    taskCodec.encodeTaskReference(job.getRootTask().getId())));
            scheduler.requestExecution(job.getRootTask().getId(), specification);
            scheduler.startTask(workerConnection, ProtocolFrame.request(
                    MessageType.TASK_STARTED,
                    taskCodec.encodeTaskReference(job.getRootTask().getId())));
            scheduler.finishTask(workerConnection, ProtocolFrame.request(
                    MessageType.TASK_FINISHED,
                    taskCodec.encodeTaskReference(job.getRootTask().getId())));
        }

        List<String> lines = Files.readAllLines(logFile);
        assertEquals(4, lines.size());
        assertTrue(lines.get(0).contains("receivedAt=" + NOW));
        assertTrue(lines.get(0).contains("jobId=" + jobId));
        assertTrue(lines.get(0).contains("status=READY"));
        assertTrue(lines.get(0).contains("worker=-"));
        assertTrue(lines.get(1).contains("status=SCHEDULED"));
        assertTrue(lines.get(1).contains("worker=worker-host"));
        assertTrue(lines.get(2).contains("status=RUNNING"));
        assertTrue(lines.get(3).contains("status=DONE"));
        assertTrue(lines.get(3).contains("finishedAt=" + NOW));
    }
}
