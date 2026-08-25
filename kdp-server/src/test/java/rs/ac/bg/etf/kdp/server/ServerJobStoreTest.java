package rs.ac.bg.etf.kdp.server;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import rs.ac.bg.etf.kdp.common.domain.Job;
import rs.ac.bg.etf.kdp.common.domain.JobSpecification;
import rs.ac.bg.etf.kdp.common.protocol.ConnectionRole;
import rs.ac.bg.etf.kdp.common.protocol.FileTransferMetadata;
import rs.ac.bg.etf.kdp.common.protocol.FileTransferProtocolCodec;
import rs.ac.bg.etf.kdp.common.protocol.MessageType;
import rs.ac.bg.etf.kdp.common.protocol.ProtocolException;
import rs.ac.bg.etf.kdp.common.protocol.ProtocolFrame;
import rs.ac.bg.etf.kdp.common.protocol.ProtocolFrameCodec;
import rs.ac.bg.etf.kdp.common.protocol.TransferFileKind;

import java.io.ByteArrayOutputStream;
import java.net.Socket;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;

class ServerJobStoreTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void rejectsFilesNotDeclaredByTheSubmittedJob() throws Exception {
        ServerJobStore store = new ServerJobStore(temporaryDirectory);
        JobSpecification specification = new JobSpecification(
                "job.jar", List.of(), List.of(), List.of("input.txt"), List.of());
        Job job = store.createBatch(List.of(specification)).getJobs().get(0);
        ServerConnection connection = new ServerConnection(
                new Socket(),
                ConnectionRole.CLIENT,
                new ProtocolFrameCodec(),
                new ByteArrayOutputStream());
        FileTransferMetadata metadata = new FileTransferMetadata(
                job.getId(), TransferFileKind.INPUT, "other.txt", 10);
        ProtocolFrame begin = new ProtocolFrame(
                MessageType.FILE_BEGIN,
                UUID.randomUUID(),
                new FileTransferProtocolCodec().encodeBegin(metadata));

        assertThrows(ProtocolException.class, () ->
                store.accept(connection, begin, Set.of(job.getId())));
    }
}
