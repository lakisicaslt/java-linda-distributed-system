package rs.ac.bg.etf.kdp.server;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import rs.ac.bg.etf.kdp.LindaException;
import rs.ac.bg.etf.kdp.RemoteLinda;
import rs.ac.bg.etf.kdp.common.domain.Job;
import rs.ac.bg.etf.kdp.common.domain.JobBatch;
import rs.ac.bg.etf.kdp.common.domain.JobSpecification;
import rs.ac.bg.etf.kdp.common.protocol.ConnectionRole;
import rs.ac.bg.etf.kdp.common.protocol.MessageType;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServerLindaIntegrationTest {

    @TempDir
    Path temporaryDirectory;

    private CentralServer server;
    private LindaSessionHandler lindaHandler;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.close();
        }
        if (lindaHandler != null) {
            lindaHandler.close();
        }
    }

    @Test
    void sharesTuplesInsideBatchAndIsolatesDifferentBatches() throws Exception {
        ServerJobStore store = new ServerJobStore(temporaryDirectory.resolve("jobs"));
        JobSpecification specification = new JobSpecification(
                "job.jar", List.of(), List.of(), List.of(), List.of());
        JobBatch firstBatch = store.createBatch(List.of(specification, specification));
        JobBatch secondBatch = store.createBatch(List.of(specification));
        lindaHandler = new LindaSessionHandler(store);
        server = new CentralServer(
                new ServerConfig("127.0.0.1", 0, 10, 8, 1_000),
                new ServerMessageRouter(
                        Map.of(ConnectionRole.LINDA, lindaHandler),
                        (connection, frame) -> connection.send(frame.reply(
                                MessageType.ERROR, new byte[0]))));
        server.start();

        Job firstJob = firstBatch.getJobs().get(0);
        Job secondJob = firstBatch.getJobs().get(1);
        RemoteLinda producer = remote(firstBatch, firstJob);
        RemoteLinda consumer = remote(firstBatch, secondJob);
        RemoteLinda isolated = remote(secondBatch, secondBatch.getJobs().get(0));

        assertFalse(consumer.rdp(new String[]{"value", null}));
        producer.out(new String[]{"value", "42"});
        String[] read = {"value", null};
        consumer.rd(read);
        assertArrayEquals(new String[]{"value", "42"}, read);
        assertTrue(consumer.rdp(new String[]{"value", null}));
        assertFalse(isolated.rdp(new String[]{"value", null}));

        String[] take = {"value", null};
        assertTrue(consumer.inp(take));
        assertArrayEquals(new String[]{"value", "42"}, take);
        assertFalse(producer.inp(new String[]{"value", null}));

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            String[] blockingTake = {"later", null};
            Future<?> waiting = executor.submit(() -> consumer.in(blockingTake));
            Thread.sleep(100);
            assertFalse(waiting.isDone());
            producer.out(new String[]{"later", "available"});
            waiting.get(2, TimeUnit.SECONDS);
            assertArrayEquals(new String[]{"later", "available"}, blockingTake);
            assertFalse(producer.rdp(new String[]{"later", null}));

            String[] blockingRead = {"persistent", null};
            Future<?> waitingRead = executor.submit(() -> consumer.rd(blockingRead));
            Thread.sleep(100);
            assertFalse(waitingRead.isDone());
            producer.out(new String[]{"persistent", "still here"});
            waitingRead.get(2, TimeUnit.SECONDS);
            assertArrayEquals(new String[]{"persistent", "still here"}, blockingRead);
            assertTrue(producer.inp(new String[]{"persistent", null}));
        } finally {
            executor.shutdownNow();
        }

        RemoteLinda invalid = new RemoteLinda(
                "127.0.0.1",
                server.getLocalPort(),
                firstBatch.getId(),
                UUID.randomUUID());
        assertThrows(LindaException.class,
                () -> invalid.rdp(new String[]{"value", null}));
    }

    private RemoteLinda remote(JobBatch batch, Job job) {
        return new RemoteLinda(
                "127.0.0.1",
                server.getLocalPort(),
                batch.getId(),
                job.getRootTask().getId());
    }
}
