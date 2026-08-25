package rs.ac.bg.etf.kdp.client;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import rs.ac.bg.etf.kdp.common.domain.Job;
import rs.ac.bg.etf.kdp.common.domain.JobSpecification;
import rs.ac.bg.etf.kdp.common.protocol.BatchAcceptedMessage;
import rs.ac.bg.etf.kdp.common.protocol.ConnectionRole;
import rs.ac.bg.etf.kdp.common.protocol.MessageType;
import rs.ac.bg.etf.kdp.server.CentralServer;
import rs.ac.bg.etf.kdp.server.ClientSessionHandler;
import rs.ac.bg.etf.kdp.server.ServerConfig;
import rs.ac.bg.etf.kdp.server.ServerJobStore;
import rs.ac.bg.etf.kdp.server.ServerMessageRouter;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JobClientIntegrationTest {

    @TempDir
    Path temporaryDirectory;

    private CentralServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.close();
        }
    }

    @Test
    void submitsDescriptionsAndStreamsAllJobFiles() throws Exception {
        ServerJobStore store = new ServerJobStore(temporaryDirectory.resolve("server-jobs"));
        ClientSessionHandler clientHandler = new ClientSessionHandler(store);
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
}
