package rs.ac.bg.etf.kdp.worker;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import rs.ac.bg.etf.kdp.common.domain.JobSpecification;
import rs.ac.bg.etf.kdp.common.domain.Task;
import rs.ac.bg.etf.kdp.common.domain.TaskStatus;
import rs.ac.bg.etf.kdp.common.domain.Worker;
import rs.ac.bg.etf.kdp.common.domain.WorkerStatus;
import rs.ac.bg.etf.kdp.common.protocol.ProtocolConstants;
import rs.ac.bg.etf.kdp.common.protocol.TransferFileKind;
import rs.ac.bg.etf.kdp.server.CentralServer;
import rs.ac.bg.etf.kdp.server.ServerConfig;
import rs.ac.bg.etf.kdp.server.ServerTaskResultStore;
import rs.ac.bg.etf.kdp.server.TaskScheduler;
import rs.ac.bg.etf.kdp.server.WorkerRegistry;
import rs.ac.bg.etf.kdp.server.WorkerSessionHandler;
import rs.ac.bg.etf.kdp.server.WorkerLivenessMonitor;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkerClientIntegrationTest {

    @TempDir
    Path temporaryDirectory;

    private CentralServer server;
    private WorkerClient client;
    private WorkerLivenessMonitor livenessMonitor;

    @AfterEach
    void cleanUp() {
        if (client != null) {
            client.close();
        }
        if (livenessMonitor != null) {
            livenessMonitor.close();
        }
        if (server != null) {
            server.close();
        }
    }

    @Test
    void registersSendsHeartbeatsAndBecomesOfflineAfterDisconnect() throws Exception {
        WorkerRegistry registry = new WorkerRegistry();
        server = new CentralServer(
                new ServerConfig("127.0.0.1", 0, 10, 4, 1_000),
                new WorkerSessionHandler(registry));
        server.start();

        UUID workerId = UUID.randomUUID();
        WorkerConfig config = new WorkerConfig(
                "127.0.0.1",
                server.getLocalPort(),
                workerId,
                3,
                ProtocolConstants.CONNECT_TIMEOUT_MILLIS,
                ProtocolConstants.HANDSHAKE_TIMEOUT_MILLIS,
                50);
        client = new WorkerClient(config);
        client.start();

        Worker worker = registry.find(workerId).orElseThrow();
        Instant registeredAt = worker.getRegisteredAt();
        assertEquals(WorkerStatus.ONLINE, worker.getStatus());
        assertEquals(3, worker.getCapacity());
        assertTrue(await(() -> registry.find(workerId)
                .map(registered -> registered.getLastHeartbeatAt().isAfter(registeredAt))
                .orElse(false)));

        client.close();
        assertTrue(await(() -> registry.find(workerId)
                .map(registered -> registered.getStatus() == WorkerStatus.OFFLINE)
                .orElse(false)));
    }

    @Test
    void receivesAndAcknowledgesTaskAssignment() throws Exception {
        WorkerRegistry registry = new WorkerRegistry();
        TaskScheduler scheduler = new TaskScheduler(registry);
        server = new CentralServer(
                new ServerConfig("127.0.0.1", 0, 10, 4, 1_000),
                new WorkerSessionHandler(registry, scheduler));
        server.start();

        UUID workerId = UUID.randomUUID();
        WorkerConfig config = new WorkerConfig(
                "127.0.0.1",
                server.getLocalPort(),
                workerId,
                1,
                ProtocolConstants.CONNECT_TIMEOUT_MILLIS,
                ProtocolConstants.HANDSHAKE_TIMEOUT_MILLIS,
                100);
        AtomicReference<UUID> receivedTaskId = new AtomicReference<>();
        client = new WorkerClient(
                config,
                assignment -> receivedTaskId.set(assignment.getTaskId()));
        client.start();

        Task task = Task.root(
                UUID.randomUUID(), UUID.randomUUID(), "Integration task", Instant.now());
        scheduler.submit(task);

        assertTrue(await(() -> task.getId().equals(receivedTaskId.get())));
        assertTrue(await(() -> scheduler.isAccepted(task.getId())));
        assertEquals(TaskStatus.SCHEDULED, task.getStatus());
        assertTrue(client.getAssignedTaskIds().contains(task.getId()));
        assertEquals(workerId, task.getAssignedWorkerId().orElseThrow());
    }

    @Test
    void receivesTaskFileAndAcknowledgesTransfer() throws Exception {
        WorkerRegistry registry = new WorkerRegistry();
        TaskScheduler scheduler = new TaskScheduler(registry);
        server = new CentralServer(
                new ServerConfig("127.0.0.1", 0, 10, 4, 1_000),
                new WorkerSessionHandler(registry, scheduler));
        server.start();

        UUID workerId = UUID.randomUUID();
        WorkerConfig config = new WorkerConfig(
                "127.0.0.1",
                server.getLocalPort(),
                workerId,
                1,
                ProtocolConstants.CONNECT_TIMEOUT_MILLIS,
                ProtocolConstants.HANDSHAKE_TIMEOUT_MILLIS,
                100,
                temporaryDirectory.resolve("worker"));
        client = new WorkerClient(config);
        client.start();

        Task task = Task.root(
                UUID.randomUUID(), UUID.randomUUID(), "File transfer task", Instant.now());
        scheduler.submit(task);
        assertTrue(await(() -> scheduler.isAccepted(task.getId())));

        byte[] content = new byte[ProtocolConstants.FILE_CHUNK_SIZE + 321];
        for (int index = 0; index < content.length; index++) {
            content[index] = (byte) (index % 127);
        }
        Path source = temporaryDirectory.resolve("client-input.dat");
        Files.write(source, content);
        UUID transferId = scheduler.sendFile(
                task.getId(), source, TransferFileKind.INPUT, "input.dat");

        assertTrue(await(() -> scheduler.isFileAcknowledged(transferId)));
        Path received = client.getTaskDirectory(task.getId()).resolve("input.dat");
        assertTrue(Files.isRegularFile(received));
        assertEquals(content.length, Files.size(received));
        assertArrayEquals(content, Files.readAllBytes(received));
    }

    @Test
    void transfersAndExecutesJavaTask() throws Exception {
        WorkerRegistry registry = new WorkerRegistry();
        TaskScheduler scheduler = new TaskScheduler(registry);
        ServerTaskResultStore resultStore = new ServerTaskResultStore(
                temporaryDirectory.resolve("server-results"));
        server = new CentralServer(
                new ServerConfig("127.0.0.1", 0, 10, 4, 1_000),
                new WorkerSessionHandler(registry, scheduler, resultStore));
        server.start();

        Path jobArchive = temporaryDirectory.resolve("test-job.jar");
        createJobArchive(jobArchive);
        Path inputFile = temporaryDirectory.resolve("input.txt");
        Files.writeString(inputFile, "distributed execution", StandardCharsets.UTF_8);
        Path lindaLibrary = Files.createFile(temporaryDirectory.resolve("kdp-linda.jar"));
        UUID workerId = UUID.randomUUID();
        WorkerConfig config = new WorkerConfig(
                "127.0.0.1",
                server.getLocalPort(),
                workerId,
                1,
                ProtocolConstants.CONNECT_TIMEOUT_MILLIS,
                ProtocolConstants.HANDSHAKE_TIMEOUT_MILLIS,
                100,
                temporaryDirectory.resolve("execution-worker"),
                lindaLibrary);
        client = new WorkerClient(config);
        client.start();

        Task task = Task.root(
                UUID.randomUUID(), UUID.randomUUID(), "Execution task", Instant.now());
        scheduler.submit(task);
        assertTrue(await(() -> scheduler.isAccepted(task.getId())));
        UUID jarTransfer = scheduler.sendFile(
                task.getId(), jobArchive, TransferFileKind.JOB_ARCHIVE, "test-job.jar");
        assertTrue(await(() -> scheduler.isFileAcknowledged(jarTransfer)));
        UUID inputTransfer = scheduler.sendFile(
                task.getId(), inputFile, TransferFileKind.INPUT, "input.txt");
        assertTrue(await(() -> scheduler.isFileAcknowledged(inputTransfer)));

        JobSpecification specification = new JobSpecification(
                "test-job.jar",
                List.of(),
                List.of("result.txt"),
                List.of("input.txt"),
                List.of("result.txt"));
        scheduler.requestExecution(task.getId(), specification);

        assertTrue(await(() -> task.getStatus() == TaskStatus.DONE));
        Path taskDirectory = client.getTaskDirectory(task.getId());
        assertEquals("DISTRIBUTED EXECUTION", Files.readString(
                taskDirectory.resolve("result.txt"), StandardCharsets.UTF_8));
        assertTrue(Files.readString(taskDirectory.resolve(".kdp").resolve("stdout.txt"))
                .contains(lindaLibrary.toAbsolutePath().toString()));
        assertTrue(Files.readString(taskDirectory.resolve(".kdp").resolve("stderr.txt"))
                .contains("test stderr"));
        Path serverTaskDirectory = resultStore.taskDirectory(task.getId());
        assertEquals("DISTRIBUTED EXECUTION", Files.readString(
                serverTaskDirectory.resolve("outputs").resolve("result.txt"),
                StandardCharsets.UTF_8));
        assertTrue(Files.readString(
                serverTaskDirectory.resolve("streams").resolve("stdout.txt"))
                .contains(lindaLibrary.toAbsolutePath().toString()));
        assertTrue(Files.readString(
                serverTaskDirectory.resolve("streams").resolve("stderr.txt"))
                .contains("test stderr"));
        assertTrue(client.getAssignedTaskIds().isEmpty());
        assertTrue(registry.find(workerId).orElseThrow().getActiveTaskIds().isEmpty());
    }

    @Test
    void serverDisconnectsWorkerThatStopsSendingHeartbeats() throws Exception {
        WorkerRegistry registry = new WorkerRegistry();
        server = new CentralServer(
                new ServerConfig("127.0.0.1", 0, 10, 4, 1_000),
                new WorkerSessionHandler(registry));
        server.start();
        livenessMonitor = new WorkerLivenessMonitor(registry, 20, 100);
        livenessMonitor.start();

        UUID workerId = UUID.randomUUID();
        WorkerConfig config = new WorkerConfig(
                "127.0.0.1",
                server.getLocalPort(),
                workerId,
                1,
                ProtocolConstants.CONNECT_TIMEOUT_MILLIS,
                ProtocolConstants.HANDSHAKE_TIMEOUT_MILLIS,
                10_000,
                temporaryDirectory.resolve("silent-worker"));
        client = new WorkerClient(config);
        client.start();

        assertTrue(await(() -> registry.find(workerId)
                .map(worker -> worker.getStatus() == WorkerStatus.OFFLINE)
                .orElse(false)));
        assertTrue(await(() -> !client.isRunning()));
    }

    @Test
    void reportsExecutionFailureWithoutAutomaticRetry() throws Exception {
        WorkerRegistry registry = new WorkerRegistry();
        TaskScheduler scheduler = new TaskScheduler(registry);
        server = new CentralServer(
                new ServerConfig("127.0.0.1", 0, 10, 4, 1_000),
                new WorkerSessionHandler(registry, scheduler));
        server.start();

        Path lindaLibrary = Files.createFile(
                temporaryDirectory.resolve("failure-kdp-linda.jar"));
        WorkerConfig config = new WorkerConfig(
                "127.0.0.1",
                server.getLocalPort(),
                UUID.randomUUID(),
                1,
                ProtocolConstants.CONNECT_TIMEOUT_MILLIS,
                ProtocolConstants.HANDSHAKE_TIMEOUT_MILLIS,
                100,
                temporaryDirectory.resolve("failing-worker"),
                lindaLibrary);
        client = new WorkerClient(config);
        client.start();

        Task task = Task.root(
                UUID.randomUUID(), UUID.randomUUID(), "Missing JAR task", Instant.now());
        scheduler.submit(task);
        assertTrue(await(() -> scheduler.isAccepted(task.getId())));
        scheduler.requestExecution(task.getId(), new JobSpecification(
                "missing.jar", List.of(), List.of(), List.of(), List.of()));

        assertTrue(await(() -> task.getStatus() == TaskStatus.FAILED));
        assertTrue(
                task.getFailureReason().contains("does not exist"),
                () -> "Unexpected failure reason: " + task.getFailureReason());
        assertEquals(0, scheduler.getReadyTaskCount());
        assertTrue(client.getAssignedTaskIds().isEmpty());
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
                Attributes.Name.MAIN_CLASS, WorkerExecutionTestProgram.class.getName());

        String classEntryName = WorkerExecutionTestProgram.class.getName()
                .replace('.', '/') + ".class";
        try (InputStream classBytes = WorkerExecutionTestProgram.class.getClassLoader()
                .getResourceAsStream(classEntryName);
             JarOutputStream archive = new JarOutputStream(
                     Files.newOutputStream(target), manifest)) {
            if (classBytes == null) {
                throw new IOException("Could not find compiled test program");
            }
            archive.putNextEntry(new JarEntry(classEntryName));
            classBytes.transferTo(archive);
            archive.closeEntry();
        }
    }
}
