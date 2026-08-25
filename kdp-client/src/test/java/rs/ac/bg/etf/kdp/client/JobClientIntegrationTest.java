package rs.ac.bg.etf.kdp.client;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import rs.ac.bg.etf.kdp.common.domain.Job;
import rs.ac.bg.etf.kdp.common.domain.JobSpecification;
import rs.ac.bg.etf.kdp.common.domain.JobStatus;
import rs.ac.bg.etf.kdp.common.protocol.BatchAcceptedMessage;
import rs.ac.bg.etf.kdp.common.protocol.BatchStatusMessage;
import rs.ac.bg.etf.kdp.common.protocol.ConnectionRole;
import rs.ac.bg.etf.kdp.common.protocol.MessageType;
import rs.ac.bg.etf.kdp.server.CentralServer;
import rs.ac.bg.etf.kdp.server.ClientSessionHandler;
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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JobClientIntegrationTest {

    @TempDir
    Path temporaryDirectory;

    private CentralServer server;
    private WorkerClient workerClient;

    @AfterEach
    void stopServer() {
        if (workerClient != null) {
            workerClient.close();
        }
        if (server != null) {
            server.close();
        }
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
    }

    @Test
    void submitsSchedulesExecutesAndStoresResult() throws Exception {
        WorkerRegistry registry = new WorkerRegistry();
        TaskScheduler scheduler = new TaskScheduler(registry);
        ServerJobStore jobStore = new ServerJobStore(
                temporaryDirectory.resolve("end-to-end-jobs"));
        ServerTaskResultStore resultStore = new ServerTaskResultStore(
                temporaryDirectory.resolve("end-to-end-results"));
        ClientSessionHandler clientHandler = new ClientSessionHandler(jobStore, scheduler);
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

    private static void createJobArchive(Path target) throws IOException {
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        manifest.getMainAttributes().put(
                Attributes.Name.MAIN_CLASS, ClientExecutionTestProgram.class.getName());

        String classEntryName = ClientExecutionTestProgram.class.getName()
                .replace('.', '/') + ".class";
        try (InputStream classBytes = ClientExecutionTestProgram.class.getClassLoader()
                .getResourceAsStream(classEntryName);
             JarOutputStream archive = new JarOutputStream(
                     Files.newOutputStream(target), manifest)) {
            if (classBytes == null) {
                throw new IOException("Could not find compiled client test program");
            }
            archive.putNextEntry(new JarEntry(classEntryName));
            classBytes.transferTo(archive);
            archive.closeEntry();
        }
    }
}
