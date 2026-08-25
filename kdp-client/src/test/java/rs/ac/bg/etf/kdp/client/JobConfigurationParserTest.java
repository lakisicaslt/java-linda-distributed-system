package rs.ac.bg.etf.kdp.client;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import rs.ac.bg.etf.kdp.common.domain.JobSpecification;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JobConfigurationParserTest {

    @TempDir
    Path temporaryDirectory;

    private final JobConfigurationParser parser = new JobConfigurationParser();

    @Test
    void readsMultipleJobsAndResolvesRelativePaths() throws Exception {
        Path filesDirectory = Files.createDirectories(
                temporaryDirectory.resolve("job files"));
        Path firstJar = Files.write(
                filesDirectory.resolve("first-job.jar"), new byte[]{1});
        Path secondJar = Files.write(
                filesDirectory.resolve("second-job.jar"), new byte[]{2});
        Path input = Files.writeString(
                filesDirectory.resolve("input.txt"), "test", StandardCharsets.UTF_8);
        Path configuration = writeConfiguration("""
                jobs=2
                job.1.jar=job files/first-job.jar
                job.1.jvmArgument.count=1
                job.1.jvmArgument.1=-Xmx512m
                job.1.argument.count=2
                job.1.argument.1=result.txt
                job.1.argument.2=value with spaces
                job.1.input.count=1
                job.1.input.1=job files/input.txt
                job.1.output.count=1
                job.1.output.1=result.txt
                job.2.jar=job files/second-job.jar
                """);

        List<JobSubmission> jobs = parser.parse(configuration);

        assertEquals(2, jobs.size());
        JobSubmission first = jobs.get(0);
        JobSpecification specification = first.getSpecification();
        assertEquals(firstJar.toAbsolutePath(), first.getJarFile());
        assertEquals(List.of("-Xmx512m"), specification.getJvmArguments());
        assertEquals(
                List.of("result.txt", "value with spaces"),
                specification.getProgramArguments());
        assertEquals(List.of("input.txt"), specification.getInputFileNames());
        assertEquals(List.of("result.txt"), specification.getOutputFileNames());
        assertEquals(input.toAbsolutePath(), first.getInputFile("input.txt"));
        assertEquals(secondJar.toAbsolutePath(), jobs.get(1).getJarFile());
        assertTrue(jobs.get(1).getSpecification().getJvmArguments().isEmpty());
    }

    @Test
    void rejectsMoreThanSixInputFiles() throws Exception {
        Files.write(temporaryDirectory.resolve("job.jar"), new byte[]{1});
        Path configuration = writeConfiguration("""
                jobs=1
                job.1.jar=job.jar
                job.1.input.count=7
                """);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> parser.parse(configuration));

        assertTrue(exception.getMessage().contains("job.1.input.count"));
    }

    @Test
    void rejectsMissingIndexedValue() throws Exception {
        Files.write(temporaryDirectory.resolve("job.jar"), new byte[]{1});
        Path configuration = writeConfiguration("""
                jobs=1
                job.1.jar=job.jar
                job.1.output.count=1
                """);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> parser.parse(configuration));

        assertTrue(exception.getMessage().contains("job.1.output.1"));
    }

    @Test
    void rejectsUnknownProperties() throws Exception {
        Files.write(temporaryDirectory.resolve("job.jar"), new byte[]{1});
        Path configuration = writeConfiguration("""
                jobs=1
                job.1.jar=job.jar
                job.1.command=java -jar job.jar
                """);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> parser.parse(configuration));

        assertTrue(exception.getMessage().contains("job.1.command"));
    }

    @Test
    void rejectsMissingLocalFiles() throws Exception {
        Path configuration = writeConfiguration("""
                jobs=1
                job.1.jar=missing.jar
                """);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> parser.parse(configuration));

        assertTrue(exception.getMessage().contains("does not exist"));
    }

    private Path writeConfiguration(String content) throws Exception {
        return Files.writeString(
                temporaryDirectory.resolve("jobs.properties"),
                content,
                StandardCharsets.UTF_8);
    }
}
