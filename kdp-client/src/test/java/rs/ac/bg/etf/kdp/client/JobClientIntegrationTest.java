package rs.ac.bg.etf.kdp.client;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import rs.ac.bg.etf.kdp.Linda;
import rs.ac.bg.etf.kdp.RemoteLinda;
import rs.ac.bg.etf.kdp.common.domain.Job;
import rs.ac.bg.etf.kdp.common.domain.JobSpecification;
import rs.ac.bg.etf.kdp.common.domain.JobStatus;
import rs.ac.bg.etf.kdp.common.domain.TaskStatus;
import rs.ac.bg.etf.kdp.common.domain.TaskType;
import rs.ac.bg.etf.kdp.common.domain.WorkerStatus;
import rs.ac.bg.etf.kdp.common.protocol.BatchAcceptedMessage;
import rs.ac.bg.etf.kdp.common.protocol.BatchResultMessage;
import rs.ac.bg.etf.kdp.common.protocol.BatchStatusMessage;
import rs.ac.bg.etf.kdp.common.protocol.ConnectionRole;
import rs.ac.bg.etf.kdp.common.protocol.JobStatusSnapshot;
import rs.ac.bg.etf.kdp.common.protocol.MessageType;
import rs.ac.bg.etf.kdp.server.CentralServer;
import rs.ac.bg.etf.kdp.server.ClientSessionHandler;
import rs.ac.bg.etf.kdp.server.LindaSessionHandler;
import rs.ac.bg.etf.kdp.server.ServerConfig;
import rs.ac.bg.etf.kdp.server.ServerJobStore;
import rs.ac.bg.etf.kdp.server.ServerMessageRouter;
import rs.ac.bg.etf.kdp.server.ServerTaskResultStore;
import rs.ac.bg.etf.kdp.server.TaskScheduler;
import rs.ac.bg.etf.kdp.server.WorkerRegistry;
import rs.ac.bg.etf.kdp.server.WorkerSessionHandler;
import rs.ac.bg.etf.kdp.worker.WorkerClient;
import rs.ac.bg.etf.kdp.worker.WorkerConfig;

import java.io.IOException;
import java.io.InputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JobClientIntegrationTest {

    @TempDir
    Path temporaryDirectory;

    private CentralServer server;
    private WorkerClient workerClient;
    private WorkerClient secondWorkerClient;
    private LindaSessionHandler lindaHandler;
    private JobFailureMonitor failureMonitor;

    @AfterEach
    void stopServer() {
        if (failureMonitor != null) {
            failureMonitor.close();
        }
        if (workerClient != null) {
            workerClient.close();
        }
        if (secondWorkerClient != null) {
            secondWorkerClient.close();
        }
        if (lindaHandler != null) {
            lindaHandler.close();
        }
        if (server != null) {
            server.close();
        }
    }

    @Test
    void executesLindaEvalOnAnotherAvailableWorker() throws Exception {
        WorkerRegistry registry = new WorkerRegistry();
        TaskScheduler scheduler = new TaskScheduler(registry);
        ServerJobStore jobStore = new ServerJobStore(
                temporaryDirectory.resolve("eval-jobs"));
        ServerTaskResultStore resultStore = new ServerTaskResultStore(
                temporaryDirectory.resolve("eval-results"));
        lindaHandler = new LindaSessionHandler(jobStore, scheduler);
        server = new CentralServer(
                new ServerConfig("127.0.0.1", 0, 10, 8, 1_000),
                new ServerMessageRouter(
                        Map.of(
                                ConnectionRole.CLIENT,
                                new ClientSessionHandler(jobStore, scheduler, resultStore),
                                ConnectionRole.WORKER,
                                new WorkerSessionHandler(registry, scheduler, resultStore),
                                ConnectionRole.LINDA,
                                lindaHandler),
                        (connection, frame) -> connection.send(frame.reply(
                                MessageType.ERROR, new byte[0]))));
        server.start();

        Path lindaLibrary = temporaryDirectory.resolve("eval-kdp-linda.jar");
        createLindaLibrary(lindaLibrary);
        workerClient = new WorkerClient(workerConfig(
                server.getLocalPort(),
                temporaryDirectory.resolve("eval-worker-one"),
                lindaLibrary));
        secondWorkerClient = new WorkerClient(workerConfig(
                server.getLocalPort(),
                temporaryDirectory.resolve("eval-worker-two"),
                lindaLibrary));
        workerClient.start();
        secondWorkerClient.start();

        Path jobArchive = temporaryDirectory.resolve("eval-job.jar");
        createJobArchive(
                jobArchive,
                EvalExecutionTestProgram.class,
                EvalTestRunnable.class);
        JobSpecification specification = new JobSpecification(
                "eval-job.jar",
                List.of(),
                List.of("result.txt"),
                List.of(),
                List.of("result.txt"));
        JobClient client = new JobClient(new ClientConfig(
                "127.0.0.1", server.getLocalPort(), 1_000, 1_000));

        BatchAcceptedMessage accepted = client.submit(List.of(
                new JobSubmission(specification, jobArchive, List.of())));
        Job job = jobStore.findJob(accepted.getJobIds().get(0)).orElseThrow();

        assertTrue(await(() -> job.getStatus() == JobStatus.DONE));
        assertEquals(2, job.getTasks().size());
        assertEquals(TaskType.EVAL, job.getTasks().get(1).getType());
        assertNotEquals(
                job.getRootTask().getAssignedWorkerId().orElseThrow(),
                job.getTasks().get(1).getAssignedWorkerId().orElseThrow());
        assertEquals("computed remotely|remote calculation", Files.readString(resultStore
                .taskDirectory(job.getRootTask().getId())
                .resolve("outputs")
                .resolve("result.txt"), StandardCharsets.UTF_8));
        assertTrue(Files.readString(resultStore
                .taskDirectory(job.getTasks().get(1).getId())
                .resolve("streams")
                .resolve("stdout.txt"), StandardCharsets.UTF_8)
                .contains("eval task completed"));
        assertEquals(5, resultStore.findResults(job).size());

        Path downloadedDirectory = temporaryDirectory.resolve("eval-downloaded");
        BatchResultMessage downloaded = client.downloadBatchResults(
                accepted.getBatchId(), downloadedDirectory);
        assertEquals(5, downloaded.getFiles().size());
        assertTrue(Files.readString(downloadedDirectory
                .resolve(job.getId().toString())
                .resolve("streams")
                .resolve(job.getTasks().get(1).getId() + "-stdout.txt"),
                StandardCharsets.UTF_8).contains("eval task completed"));
    }

    @Test
    void retriesOnlyFailedEvalAfterItsWorkerDisconnects() throws Exception {
        WorkerRegistry registry = new WorkerRegistry();
        TaskScheduler scheduler = new TaskScheduler(registry);
        ServerJobStore jobStore = new ServerJobStore(
                temporaryDirectory.resolve("eval-retry-jobs"));
        ServerTaskResultStore resultStore = new ServerTaskResultStore(
                temporaryDirectory.resolve("eval-retry-results"));
        startLindaServer(registry, scheduler, jobStore, resultStore);

        Path lindaLibrary = temporaryDirectory.resolve("eval-retry-kdp-linda.jar");
        createLindaLibrary(lindaLibrary);
        UUID firstWorkerId = UUID.randomUUID();
        UUID secondWorkerId = UUID.randomUUID();
        workerClient = new WorkerClient(workerConfig(
                server.getLocalPort(),
                firstWorkerId,
                temporaryDirectory.resolve("eval-retry-worker-one"),
                lindaLibrary));
        secondWorkerClient = new WorkerClient(workerConfig(
                server.getLocalPort(),
                secondWorkerId,
                temporaryDirectory.resolve("eval-retry-worker-two"),
                lindaLibrary));
        workerClient.start();
        secondWorkerClient.start();

        JobClient client = new JobClient(new ClientConfig(
                "127.0.0.1", server.getLocalPort(), 1_000, 1_000));
        Job job = submitRecoverableEvalJob(client, jobStore);
        failureMonitor = client.monitorBatch(job.getBatchId());
        waitUntilRootObservedEval(job);

        TaskType failedType = disconnectEvalWorker(job, firstWorkerId, secondWorkerId);
        JobStatusSnapshot failure = failureMonitor.waitForFailure();
        assertEquals(TaskType.EVAL, failedType);
        assertEquals(job.getId(), failure.getJobId());
        assertEquals("Worker disconnected", failure.getFailureReason().orElseThrow());
        assertTrue(await(() -> job.getStatus() == JobStatus.FAILED));
        assertEquals(TaskStatus.RUNNING, job.getRootTask().getStatus());
        assertEquals(TaskStatus.FAILED, job.getTasks().get(1).getStatus());

        WorkerClient replacement = new WorkerClient(workerConfig(
                server.getLocalPort(),
                UUID.randomUUID(),
                temporaryDirectory.resolve("eval-retry-replacement"),
                lindaLibrary));
        setDisconnectedWorkerSlot(replacement);
        replacement.start();
        client.retryFailedJob(job.getId());

        assertTrue(await(() -> job.getStatus() == JobStatus.DONE));
        assertEquals(1, job.getRootTask().getAttemptNumber());
        assertEquals(2, job.getTasks().get(1).getAttemptNumber());
        assertEquals("retried eval completed", Files.readString(resultStore
                .taskDirectory(job.getRootTask().getId())
                .resolve("outputs")
                .resolve("result.txt"), StandardCharsets.UTF_8));
    }

    @Test
    void abortsRemainingEvalProcessesAndReusesWorkerCapacity() throws Exception {
        WorkerRegistry registry = new WorkerRegistry();
        TaskScheduler scheduler = new TaskScheduler(registry);
        ServerJobStore jobStore = new ServerJobStore(
                temporaryDirectory.resolve("eval-abort-jobs"));
        ServerTaskResultStore resultStore = new ServerTaskResultStore(
                temporaryDirectory.resolve("eval-abort-results"));
        startLindaServer(registry, scheduler, jobStore, resultStore);

        Path lindaLibrary = temporaryDirectory.resolve("eval-abort-kdp-linda.jar");
        createLindaLibrary(lindaLibrary);
        UUID firstWorkerId = UUID.randomUUID();
        UUID secondWorkerId = UUID.randomUUID();
        workerClient = new WorkerClient(workerConfig(
                server.getLocalPort(),
                firstWorkerId,
                temporaryDirectory.resolve("eval-abort-worker-one"),
                lindaLibrary));
        secondWorkerClient = new WorkerClient(workerConfig(
                server.getLocalPort(),
                secondWorkerId,
                temporaryDirectory.resolve("eval-abort-worker-two"),
                lindaLibrary));
        workerClient.start();
        secondWorkerClient.start();

        JobClient client = new JobClient(new ClientConfig(
                "127.0.0.1", server.getLocalPort(), 1_000, 1_000));
        Job job = submitRecoverableEvalJob(client, jobStore);
        failureMonitor = client.monitorBatch(job.getBatchId());
        waitUntilRootObservedEval(job);
        disconnectEvalWorker(job, firstWorkerId, secondWorkerId);
        JobStatusSnapshot failure = failureMonitor.waitForFailure();
        assertEquals(job.getId(), failure.getJobId());
        assertTrue(await(() -> job.getStatus() == JobStatus.FAILED));

        WorkerClient survivingWorker = workerClient != null
                ? workerClient : secondWorkerClient;
        client.abortFailedJob(job.getId());

        assertEquals(JobStatus.ABORTED, job.getStatus());
        assertEquals(TaskStatus.ABORTED, job.getRootTask().getStatus());
        assertEquals(TaskStatus.ABORTED, job.getTasks().get(1).getStatus());
        assertTrue(await(() -> survivingWorker.getAssignedTaskIds().isEmpty()));
        assertTrue(survivingWorker.isRunning());

        Path simpleArchive = temporaryDirectory.resolve("after-abort-job.jar");
        createJobArchive(simpleArchive);
        Path input = temporaryDirectory.resolve("input.txt");
        Files.writeString(input, "worker is reusable", StandardCharsets.UTF_8);
        JobSpecification simpleSpecification = new JobSpecification(
                "after-abort-job.jar",
                List.of(),
                List.of("result.txt"),
                List.of("input.txt"),
                List.of("result.txt"));
        BatchAcceptedMessage accepted = client.submit(List.of(
                new JobSubmission(simpleSpecification, simpleArchive, List.of(input))));
        Job nextJob = jobStore.findJob(accepted.getJobIds().get(0)).orElseThrow();

        assertTrue(await(() -> nextJob.getStatus() == JobStatus.DONE));
        assertEquals("WORKER IS REUSABLE", Files.readString(resultStore
                .taskDirectory(nextJob.getRootTask().getId())
                .resolve("outputs")
                .resolve("result.txt"), StandardCharsets.UTF_8));
    }

    @Test
    void sharesLindaTuplesBetweenJobsInTheSameBatch() throws Exception {
        WorkerRegistry registry = new WorkerRegistry();
        TaskScheduler scheduler = new TaskScheduler(registry);
        ServerJobStore jobStore = new ServerJobStore(
                temporaryDirectory.resolve("linda-jobs"));
        ServerTaskResultStore resultStore = new ServerTaskResultStore(
                temporaryDirectory.resolve("linda-results"));
        lindaHandler = new LindaSessionHandler(jobStore, scheduler);
        server = new CentralServer(
                new ServerConfig("127.0.0.1", 0, 10, 8, 1_000),
                new ServerMessageRouter(
                        Map.of(
                                ConnectionRole.CLIENT,
                                new ClientSessionHandler(jobStore, scheduler, resultStore),
                                ConnectionRole.WORKER,
                                new WorkerSessionHandler(registry, scheduler, resultStore),
                                ConnectionRole.LINDA,
                                lindaHandler),
                        (connection, frame) -> connection.send(frame.reply(
                                MessageType.ERROR, new byte[0]))));
        server.start();

        Path lindaLibrary = temporaryDirectory.resolve("kdp-linda.jar");
        createLindaLibrary(lindaLibrary);
        workerClient = new WorkerClient(new WorkerConfig(
                "127.0.0.1",
                server.getLocalPort(),
                UUID.randomUUID(),
                2,
                1_000,
                1_000,
                100,
                temporaryDirectory.resolve("linda-worker"),
                lindaLibrary));
        workerClient.start();

        Path jobArchive = temporaryDirectory.resolve("linda-job.jar");
        createJobArchive(jobArchive, LindaExecutionTestProgram.class);
        JobSpecification consumer = new JobSpecification(
                "linda-job.jar",
                List.of(),
                List.of("consumer", "consumer.txt"),
                List.of(),
                List.of("consumer.txt"));
        JobSpecification producer = new JobSpecification(
                "linda-job.jar",
                List.of(),
                List.of("producer", "producer.txt"),
                List.of(),
                List.of("producer.txt"));
        JobClient client = new JobClient(new ClientConfig(
                "127.0.0.1", server.getLocalPort(), 1_000, 1_000));

        BatchAcceptedMessage accepted = client.submit(List.of(
                new JobSubmission(consumer, jobArchive, List.of()),
                new JobSubmission(producer, jobArchive, List.of())));
        Job consumerJob = jobStore.findJob(accepted.getJobIds().get(0)).orElseThrow();
        Job producerJob = jobStore.findJob(accepted.getJobIds().get(1)).orElseThrow();

        assertTrue(await(() -> consumerJob.getStatus() == JobStatus.DONE
                && producerJob.getStatus() == JobStatus.DONE));
        assertEquals("distributed tuple", Files.readString(resultStore
                .taskDirectory(consumerJob.getRootTask().getId())
                .resolve("outputs")
                .resolve("consumer.txt"), StandardCharsets.UTF_8));
        assertEquals("sent", Files.readString(resultStore
                .taskDirectory(producerJob.getRootTask().getId())
                .resolve("outputs")
                .resolve("producer.txt"), StandardCharsets.UTF_8));
    }

    @Test
    void submitsDescriptionsAndStreamsAllJobFiles() throws Exception {
        ServerJobStore store = new ServerJobStore(temporaryDirectory.resolve("server-jobs"));
        WorkerRegistry registry = new WorkerRegistry();
        TaskScheduler scheduler = new TaskScheduler(registry);
        ClientSessionHandler clientHandler = new ClientSessionHandler(store, scheduler);
        server = new CentralServer(
                new ServerConfig("127.0.0.1", 0, 10, 4, 1_000),
                new ServerMessageRouter(
                        Map.of(ConnectionRole.CLIENT, clientHandler),
                        (connection, frame) -> connection.send(frame.reply(
                                MessageType.ERROR, new byte[0]))));
        server.start();

        byte[] archiveContent = new byte[70_000];
        for (int index = 0; index < archiveContent.length; index++) {
            archiveContent[index] = (byte) (index % 127);
        }
        Path archive = temporaryDirectory.resolve("calculation.jar");
        Path input = temporaryDirectory.resolve("input.txt");
        Files.write(archive, archiveContent);
        Files.writeString(input, "42");

        JobSpecification specification = new JobSpecification(
                "calculation.jar",
                List.of("-Xmx512m"),
                List.of("fast"),
                List.of("input.txt"),
                List.of("result.txt"));
        JobClient client = new JobClient(new ClientConfig(
                "127.0.0.1", server.getLocalPort(), 1_000, 1_000));

        BatchAcceptedMessage accepted = client.submit(List.of(
                new JobSubmission(specification, archive, List.of(input))));

        assertEquals(1, accepted.getJobIds().size());
        Job storedJob = store.findJob(accepted.getJobIds().get(0)).orElseThrow();
        assertEquals(accepted.getBatchId(), storedJob.getBatchId());
        assertEquals("calculation.jar", storedJob.getSpecification().getJarFileName());
        assertTrue(store.isComplete(storedJob.getId()));
        assertArrayEquals(archiveContent, Files.readAllBytes(store.archivePath(storedJob.getId())));
        assertEquals("42", Files.readString(store.inputPath(storedJob.getId(), "input.txt")));
        assertEquals(1, scheduler.getReadyTaskCount());

        JobClient reconnectedClient = new JobClient(new ClientConfig(
                "127.0.0.1", server.getLocalPort(), 1_000, 1_000));
        BatchStatusMessage status = reconnectedClient.getBatchStatus(accepted.getBatchId());
        assertEquals(JobStatus.READY,
                status.findJob(storedJob.getId()).orElseThrow().getStatus());
        assertThrows(IOException.class, () -> reconnectedClient.downloadBatchResults(
                accepted.getBatchId(), temporaryDirectory.resolve("premature-results")));
        assertThrows(IOException.class, () -> reconnectedClient.abortFailedJob(storedJob.getId()));
        assertThrows(IOException.class, () -> reconnectedClient.retryFailedJob(storedJob.getId()));
    }

    @Test
    void submitsSchedulesExecutesAndStoresResult() throws Exception {
        WorkerRegistry registry = new WorkerRegistry();
        TaskScheduler scheduler = new TaskScheduler(registry);
        ServerJobStore jobStore = new ServerJobStore(
                temporaryDirectory.resolve("end-to-end-jobs"));
        ServerTaskResultStore resultStore = new ServerTaskResultStore(
                temporaryDirectory.resolve("end-to-end-results"));
        ClientSessionHandler clientHandler = new ClientSessionHandler(
                jobStore, scheduler, resultStore);
        WorkerSessionHandler workerHandler = new WorkerSessionHandler(
                registry, scheduler, resultStore);
        server = new CentralServer(
                new ServerConfig("127.0.0.1", 0, 10, 8, 1_000),
                new ServerMessageRouter(
                        Map.of(
                                ConnectionRole.CLIENT, clientHandler,
                                ConnectionRole.WORKER, workerHandler),
                        (connection, frame) -> connection.send(frame.reply(
                                MessageType.ERROR, new byte[0]))));
        server.start();

        Path lindaLibrary = Files.createFile(temporaryDirectory.resolve("kdp-linda.jar"));
        WorkerConfig workerConfig = new WorkerConfig(
                "127.0.0.1",
                server.getLocalPort(),
                UUID.randomUUID(),
                1,
                1_000,
                1_000,
                100,
                temporaryDirectory.resolve("worker"),
                lindaLibrary);
        workerClient = new WorkerClient(workerConfig);
        workerClient.start();

        Path archive = temporaryDirectory.resolve("complete-job.jar");
        createJobArchive(archive);
        Path input = temporaryDirectory.resolve("input.txt");
        Files.writeString(input, "distributed result", StandardCharsets.UTF_8);
        JobSpecification specification = new JobSpecification(
                "complete-job.jar",
                List.of(),
                List.of("result.txt"),
                List.of("input.txt"),
                List.of("result.txt"));
        JobClient client = new JobClient(new ClientConfig(
                "127.0.0.1", server.getLocalPort(), 1_000, 1_000));

        BatchAcceptedMessage accepted = client.submit(List.of(
                new JobSubmission(specification, archive, List.of(input))));
        Job job = jobStore.findJob(accepted.getJobIds().get(0)).orElseThrow();

        assertTrue(await(() -> job.getStatus() == JobStatus.DONE));
        Path resultDirectory = resultStore.taskDirectory(job.getRootTask().getId());
        assertEquals("DISTRIBUTED RESULT", Files.readString(
                resultDirectory.resolve("outputs").resolve("result.txt"),
                StandardCharsets.UTF_8));
        assertTrue(Files.readString(
                resultDirectory.resolve("streams").resolve("stdout.txt"))
                .contains(lindaLibrary.toAbsolutePath().toString()));
        assertTrue(Files.readString(
                resultDirectory.resolve("streams").resolve("stderr.txt"))
                .contains("client end-to-end test"));

        JobClient reconnectedClient = new JobClient(new ClientConfig(
                "127.0.0.1", server.getLocalPort(), 1_000, 1_000));
        BatchStatusMessage status = reconnectedClient.getBatchStatus(accepted.getBatchId());
        assertEquals(JobStatus.DONE,
                status.findJob(job.getId()).orElseThrow().getStatus());

        Path downloadedResults = temporaryDirectory.resolve("downloaded-results");
        BatchResultMessage downloaded = reconnectedClient.downloadBatchResults(
                accepted.getBatchId(), downloadedResults);
        assertEquals(3, downloaded.getFiles().size());
        Path downloadedJob = downloadedResults.resolve(job.getId().toString());
        assertEquals("DISTRIBUTED RESULT", Files.readString(
                downloadedJob.resolve("outputs").resolve("result.txt"),
                StandardCharsets.UTF_8));
        assertTrue(Files.readString(
                downloadedJob.resolve("streams").resolve("stdout.txt"))
                .contains(lindaLibrary.toAbsolutePath().toString()));
        assertTrue(Files.readString(
                downloadedJob.resolve("streams").resolve("stderr.txt"))
                .contains("client end-to-end test"));
    }

    @Test
    void appliesClientDecisionAfterWorkerDisconnects() throws Exception {
        WorkerRegistry registry = new WorkerRegistry();
        TaskScheduler scheduler = new TaskScheduler(registry);
        ServerJobStore jobStore = new ServerJobStore(
                temporaryDirectory.resolve("failure-jobs"));
        ServerTaskResultStore resultStore = new ServerTaskResultStore(
                temporaryDirectory.resolve("failure-results"));
        server = new CentralServer(
                new ServerConfig("127.0.0.1", 0, 10, 8, 1_000),
                new ServerMessageRouter(
                        Map.of(
                                ConnectionRole.CLIENT,
                                new ClientSessionHandler(jobStore, scheduler, resultStore),
                                ConnectionRole.WORKER,
                                new WorkerSessionHandler(registry, scheduler, resultStore)),
                        (connection, frame) -> connection.send(frame.reply(
                                MessageType.ERROR, new byte[0]))));
        server.start();

        Path lindaLibrary = Files.createFile(
                temporaryDirectory.resolve("failure-kdp-linda.jar"));
        UUID firstWorkerId = UUID.randomUUID();
        workerClient = new WorkerClient(new WorkerConfig(
                "127.0.0.1",
                server.getLocalPort(),
                firstWorkerId,
                2,
                1_000,
                1_000,
                100,
                temporaryDirectory.resolve("first-worker"),
                lindaLibrary));
        workerClient.start();

        Path archive = temporaryDirectory.resolve("interruptible-job.jar");
        createJobArchive(archive);
        Path input = temporaryDirectory.resolve("input.txt");
        Files.writeString(input, "retry result", StandardCharsets.UTF_8);
        JobSpecification specification = new JobSpecification(
                "interruptible-job.jar",
                List.of(),
                List.of("result.txt", "2000"),
                List.of("input.txt"),
                List.of("result.txt"));
        JobClient client = new JobClient(new ClientConfig(
                "127.0.0.1", server.getLocalPort(), 1_000, 1_000));

        BatchAcceptedMessage accepted = client.submit(List.of(
                new JobSubmission(specification, archive, List.of(input)),
                new JobSubmission(specification, archive, List.of(input))));
        failureMonitor = client.monitorBatch(accepted.getBatchId());
        Job abortedJob = jobStore.findJob(accepted.getJobIds().get(0)).orElseThrow();
        Job retriedJob = jobStore.findJob(accepted.getJobIds().get(1)).orElseThrow();
        assertTrue(await(() -> abortedJob.getStatus() == JobStatus.RUNNING
                && retriedJob.getStatus() == JobStatus.RUNNING));

        workerClient.close();
        workerClient = null;
        Set<UUID> notifiedJobs = Set.of(
                failureMonitor.waitForFailure().getJobId(),
                failureMonitor.waitForFailure().getJobId());
        assertEquals(Set.of(abortedJob.getId(), retriedJob.getId()), notifiedJobs);
        assertTrue(await(() -> abortedJob.getStatus() == JobStatus.FAILED
                && retriedJob.getStatus() == JobStatus.FAILED));
        assertTrue(await(() -> registry.find(firstWorkerId)
                .map(worker -> worker.getStatus() == WorkerStatus.OFFLINE)
                .orElse(false)));

        BatchStatusMessage failedStatus = client.getBatchStatus(accepted.getBatchId());
        assertEquals("Worker disconnected", failedStatus.findJob(abortedJob.getId())
                .orElseThrow().getFailureReason().orElseThrow());
        client.abortFailedJob(abortedJob.getId());
        client.retryFailedJob(retriedJob.getId());

        assertEquals(JobStatus.ABORTED, abortedJob.getStatus());
        assertEquals(TaskStatus.ABORTED, abortedJob.getRootTask().getStatus());
        assertEquals(JobStatus.READY, retriedJob.getStatus());
        assertEquals(1, scheduler.getReadyTaskCount());

        workerClient = new WorkerClient(new WorkerConfig(
                "127.0.0.1",
                server.getLocalPort(),
                UUID.randomUUID(),
                1,
                1_000,
                1_000,
                100,
                temporaryDirectory.resolve("replacement-worker"),
                lindaLibrary));
        workerClient.start();

        assertTrue(await(() -> retriedJob.getStatus() == JobStatus.DONE));
        assertEquals(2, retriedJob.getRootTask().getAttemptNumber());
        assertEquals("RETRY RESULT", Files.readString(resultStore
                .taskDirectory(retriedJob.getRootTask().getId())
                .resolve("outputs")
                .resolve("result.txt"), StandardCharsets.UTF_8));

        BatchStatusMessage finalStatus = client.getBatchStatus(accepted.getBatchId());
        assertEquals(JobStatus.ABORTED,
                finalStatus.findJob(abortedJob.getId()).orElseThrow().getStatus());
        assertEquals(JobStatus.DONE,
                finalStatus.findJob(retriedJob.getId()).orElseThrow().getStatus());
    }

    @Test
    void abortsJobWhenNoClientIsMonitoringFailures() throws Exception {
        WorkerRegistry registry = new WorkerRegistry();
        TaskScheduler scheduler = new TaskScheduler(registry);
        ServerJobStore jobStore = new ServerJobStore(
                temporaryDirectory.resolve("unavailable-client-jobs"));
        ServerTaskResultStore resultStore = new ServerTaskResultStore(
                temporaryDirectory.resolve("unavailable-client-results"));
        server = new CentralServer(
                new ServerConfig("127.0.0.1", 0, 10, 8, 1_000),
                new ServerMessageRouter(
                        Map.of(
                                ConnectionRole.CLIENT,
                                new ClientSessionHandler(jobStore, scheduler, resultStore),
                                ConnectionRole.WORKER,
                                new WorkerSessionHandler(registry, scheduler, resultStore)),
                        (connection, frame) -> connection.send(frame.reply(
                                MessageType.ERROR, new byte[0]))));
        server.start();

        Path lindaLibrary = Files.createFile(
                temporaryDirectory.resolve("unavailable-client-kdp-linda.jar"));
        workerClient = new WorkerClient(new WorkerConfig(
                "127.0.0.1",
                server.getLocalPort(),
                UUID.randomUUID(),
                1,
                1_000,
                1_000,
                100,
                temporaryDirectory.resolve("unavailable-client-worker"),
                lindaLibrary));
        workerClient.start();

        Path archive = temporaryDirectory.resolve("unavailable-client-job.jar");
        createJobArchive(archive);
        Path input = temporaryDirectory.resolve("input.txt");
        Files.writeString(input, "unused result", StandardCharsets.UTF_8);
        JobSpecification specification = new JobSpecification(
                "unavailable-client-job.jar",
                List.of(),
                List.of("result.txt", "5000"),
                List.of("input.txt"),
                List.of("result.txt"));
        JobClient client = new JobClient(new ClientConfig(
                "127.0.0.1", server.getLocalPort(), 1_000, 1_000));
        BatchAcceptedMessage accepted = client.submit(List.of(
                new JobSubmission(specification, archive, List.of(input))));
        Job job = jobStore.findJob(accepted.getJobIds().get(0)).orElseThrow();
        assertTrue(await(() -> job.getStatus() == JobStatus.RUNNING));

        workerClient.close();
        workerClient = null;

        assertTrue(await(() -> job.getStatus() == JobStatus.ABORTED));
        assertEquals(TaskStatus.ABORTED, job.getRootTask().getStatus());
        BatchStatusMessage status = client.getBatchStatus(accepted.getBatchId());
        assertEquals(JobStatus.ABORTED,
                status.findJob(job.getId()).orElseThrow().getStatus());
    }

    @Test
    void rejectsLocalFilesThatDoNotMatchTheDescription() throws Exception {
        Path archive = temporaryDirectory.resolve("job.jar");
        Path wrongInput = temporaryDirectory.resolve("wrong.txt");
        Files.write(archive, new byte[]{1});
        Files.write(wrongInput, new byte[]{2});
        JobSpecification specification = new JobSpecification(
                "job.jar", List.of(), List.of(), List.of("input.txt"), List.of());

        assertThrows(IllegalArgumentException.class, () ->
                new JobSubmission(specification, archive, List.of(wrongInput)));
    }

    private static boolean await(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) {
            Thread.sleep(10);
        }
        return condition.getAsBoolean();
    }

    private void startLindaServer(
            WorkerRegistry registry,
            TaskScheduler scheduler,
            ServerJobStore jobStore,
            ServerTaskResultStore resultStore) throws IOException {
        lindaHandler = new LindaSessionHandler(jobStore, scheduler);
        server = new CentralServer(
                new ServerConfig("127.0.0.1", 0, 10, 8, 1_000),
                new ServerMessageRouter(
                        Map.of(
                                ConnectionRole.CLIENT,
                                new ClientSessionHandler(jobStore, scheduler, resultStore),
                                ConnectionRole.WORKER,
                                new WorkerSessionHandler(registry, scheduler, resultStore),
                                ConnectionRole.LINDA,
                                lindaHandler),
                        (connection, frame) -> connection.send(frame.reply(
                                MessageType.ERROR, new byte[0]))));
        server.start();
    }

    private Job submitRecoverableEvalJob(
            JobClient client,
            ServerJobStore jobStore) throws Exception {
        Path archive = temporaryDirectory.resolve("recoverable-eval-job.jar");
        createJobArchive(
                archive,
                RecoverableEvalExecutionTestProgram.class,
                RecoverableEvalTestRunnable.class);
        JobSpecification specification = new JobSpecification(
                archive.getFileName().toString(),
                List.of(),
                List.of("result.txt"),
                List.of(),
                List.of("result.txt"));
        BatchAcceptedMessage accepted = client.submit(List.of(
                new JobSubmission(specification, archive, List.of())));
        return jobStore.findJob(accepted.getJobIds().get(0)).orElseThrow();
    }

    private void waitUntilRootObservedEval(Job job) throws InterruptedException {
        assertTrue(await(() -> job.getTasks().size() == 2
                && job.getRootTask().getStatus() == TaskStatus.RUNNING
                && job.getTasks().get(1).getStatus() == TaskStatus.RUNNING));
        RemoteLinda observer = new RemoteLinda(
                "127.0.0.1",
                server.getLocalPort(),
                job.getBatchId(),
                job.getRootTask().getId());
        assertTrue(await(() -> observer.rdp(new String[]{"root-observed-first-start"})));
    }

    private TaskType disconnectEvalWorker(
            Job job,
            UUID firstWorkerId,
            UUID secondWorkerId) {
        var evalTask = job.getTasks().stream()
                .filter(task -> task.getType() == TaskType.EVAL)
                .findFirst()
                .orElseThrow();
        UUID evalWorkerId = evalTask.getAssignedWorkerId().orElseThrow();
        if (evalWorkerId.equals(firstWorkerId)) {
            workerClient.close();
            workerClient = null;
        } else if (evalWorkerId.equals(secondWorkerId)) {
            secondWorkerClient.close();
            secondWorkerClient = null;
        } else {
            throw new IllegalStateException("Eval task was assigned to an unknown worker");
        }
        return evalTask.getType();
    }

    private void setDisconnectedWorkerSlot(WorkerClient replacement) {
        if (workerClient == null) {
            workerClient = replacement;
        } else if (secondWorkerClient == null) {
            secondWorkerClient = replacement;
        } else {
            throw new IllegalStateException("Both original workers are still connected");
        }
    }

    private static void createJobArchive(Path target) throws IOException {
        createJobArchive(target, ClientExecutionTestProgram.class);
    }

    private static WorkerConfig workerConfig(
            int serverPort,
            Path workDirectory,
            Path lindaLibrary) {
        return workerConfig(serverPort, UUID.randomUUID(), workDirectory, lindaLibrary);
    }

    private static WorkerConfig workerConfig(
            int serverPort,
            UUID workerId,
            Path workDirectory,
            Path lindaLibrary) {
        return new WorkerConfig(
                "127.0.0.1",
                serverPort,
                workerId,
                1,
                1_000,
                1_000,
                100,
                workDirectory,
                lindaLibrary);
    }

    private static void createJobArchive(
            Path target,
            Class<?> mainClass,
            Class<?>... additionalClasses) throws IOException {
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        manifest.getMainAttributes().put(
                Attributes.Name.MAIN_CLASS, mainClass.getName());

        try (JarOutputStream archive = new JarOutputStream(
                     Files.newOutputStream(target), manifest)) {
            addClass(archive, mainClass);
            for (Class<?> additionalClass : additionalClasses) {
                addClass(archive, additionalClass);
            }
        }
    }

    private static void addClass(JarOutputStream archive, Class<?> type) throws IOException {
        String classEntryName = type.getName().replace('.', '/') + ".class";
        try (InputStream classBytes = type.getClassLoader()
                .getResourceAsStream(classEntryName)) {
            if (classBytes == null) {
                throw new IOException("Could not find compiled test class: " + type.getName());
            }
            archive.putNextEntry(new JarEntry(classEntryName));
            classBytes.transferTo(archive);
            archive.closeEntry();
        }
    }

    private static void createLindaLibrary(Path target) throws Exception {
        Path classesDirectory = Path.of(Linda.class.getProtectionDomain()
                .getCodeSource()
                .getLocation()
                .toURI());
        try (JarOutputStream archive = new JarOutputStream(Files.newOutputStream(target));
             Stream<Path> files = Files.walk(classesDirectory)) {
            for (Path file : files.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".class"))
                    .toList()) {
                String entryName = classesDirectory.relativize(file)
                        .toString()
                        .replace(File.separatorChar, '/');
                archive.putNextEntry(new JarEntry(entryName));
                Files.copy(file, archive);
                archive.closeEntry();
            }
        }
    }
}
