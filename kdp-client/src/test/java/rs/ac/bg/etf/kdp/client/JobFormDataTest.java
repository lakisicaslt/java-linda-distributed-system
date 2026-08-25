package rs.ac.bg.etf.kdp.client;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JobFormDataTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void createsSubmissionFromValuesEnteredInTheForm() throws Exception {
        Path jar = Files.writeString(temporaryDirectory.resolve("calculation.jar"), "jar");
        Path input = Files.writeString(temporaryDirectory.resolve("data.txt"), "input");
        JobFormData form = new JobFormData(
                jar,
                List.of("-Xmx512m"),
                List.of("10"),
                List.of(input),
                List.of("result.txt"));

        JobSubmission submission = form.toSubmission();

        assertEquals(jar, submission.getJarFile());
        assertEquals(List.of("-Xmx512m"),
                submission.getSpecification().getJvmArguments());
        assertEquals(List.of("10"),
                submission.getSpecification().getProgramArguments());
        assertEquals(List.of("data.txt"),
                submission.getSpecification().getInputFileNames());
        assertEquals(List.of("result.txt"),
                submission.getSpecification().getOutputFileNames());
    }

    @Test
    void readsOneArgumentPerNonEmptyLine() {
        assertEquals(
                List.of("first", "second value", "third"),
                JobFormData.argumentsFromText(
                        " first \n\nsecond value\r\n third "));
    }
}
