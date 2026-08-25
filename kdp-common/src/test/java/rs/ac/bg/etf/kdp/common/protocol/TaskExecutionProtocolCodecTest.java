package rs.ac.bg.etf.kdp.common.protocol;

import org.junit.jupiter.api.Test;
import rs.ac.bg.etf.kdp.common.domain.JobSpecification;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TaskExecutionProtocolCodecTest {

    private final TaskExecutionProtocolCodec codec = new TaskExecutionProtocolCodec();

    @Test
    void roundTripsExecutionRequest() throws Exception {
        UUID taskId = UUID.randomUUID();
        JobSpecification specification = new JobSpecification(
                "calculation.jar",
                List.of("-Xmx512m"),
                List.of("10", "20"),
                List.of("input.txt"),
                List.of("result.txt"));

        TaskExecutionMessage decoded = codec.decode(codec.encode(
                new TaskExecutionMessage(taskId, specification)));

        assertEquals(taskId, decoded.getTaskId());
        assertEquals("calculation.jar", decoded.getSpecification().getJarFileName());
        assertEquals(List.of("-Xmx512m"), decoded.getSpecification().getJvmArguments());
        assertEquals(List.of("10", "20"), decoded.getSpecification().getProgramArguments());
        assertEquals(List.of("input.txt"), decoded.getSpecification().getInputFileNames());
        assertEquals(List.of("result.txt"), decoded.getSpecification().getOutputFileNames());
    }

    @Test
    void rejectsTruncatedPayloadAndTooManyArguments() {
        ProtocolException exception = assertThrows(
                ProtocolException.class, () -> codec.decode(new byte[15]));
        assertEquals(ProtocolError.INVALID_MESSAGE_PAYLOAD, exception.getError());

        List<String> arguments = new ArrayList<>();
        for (int index = 0; index < 257; index++) {
            arguments.add("argument-" + index);
        }
        JobSpecification specification = new JobSpecification(
                "job.jar", List.of(), arguments, List.of(), List.of());
        assertThrows(IllegalArgumentException.class, () -> codec.encode(
                new TaskExecutionMessage(UUID.randomUUID(), specification)));
    }
}
