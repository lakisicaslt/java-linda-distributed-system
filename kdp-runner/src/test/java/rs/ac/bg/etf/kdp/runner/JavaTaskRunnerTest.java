package rs.ac.bg.etf.kdp.runner;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import rs.ac.bg.etf.kdp.common.domain.JobSpecification;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JavaTaskRunnerTest {

    @TempDir
    Path taskDirectory;

    private final JavaTaskRunner runner = new JavaTaskRunner();

    @Test
    void executesJarAndCapturesDeclaredResults() throws Exception {
        Path jobArchive = taskDirectory.resolve("test-job.jar");
        createJobArchive(jobArchive);
        Path lindaLibrary = Files.createFile(taskDirectory.resolve("kdp-linda.jar"));
        Files.writeString(taskDirectory.resolve("input.txt"), "input-content");
        JobSpecification specification = specification("success");

        JavaTaskResult result = runner.run(
                specification, taskDirectory, lindaLibrary);

        assertTrue(result.isSuccessful());
        assertEquals(0, result.getExitCode());
        assertEquals("success|input-content", Files.readString(
                taskDirectory.resolve("result.txt"), StandardCharsets.UTF_8));
        assertEquals(List.of(taskDirectory.resolve("result.txt").toAbsolutePath()),
                result.getOutputFiles());
        assertTrue(Files.readString(result.getStandardOutput())
                .contains("configured-value|" + lindaLibrary.toAbsolutePath()));
        assertTrue(Files.readString(result.getStandardError())
                .contains("test standard error"));
    }

    @Test
    void returnsNonZeroExitCodeAsFailedResult() throws Exception {
        createJobArchive(taskDirectory.resolve("test-job.jar"));
        Path lindaLibrary = Files.createFile(taskDirectory.resolve("kdp-linda.jar"));
        Files.writeString(taskDirectory.resolve("input.txt"), "input-content");

        JavaTaskResult result = runner.run(
                specification("fail"), taskDirectory, lindaLibrary);

        assertFalse(result.isSuccessful());
        assertEquals(7, result.getExitCode());
    }

    @Test
    void rejectsMissingJobArchiveOrInputFile() throws Exception {
        Path lindaLibrary = Files.createFile(taskDirectory.resolve("kdp-linda.jar"));

        assertThrows(IllegalArgumentException.class, () ->
                runner.run(specification("success"), taskDirectory, lindaLibrary));

        createJobArchive(taskDirectory.resolve("test-job.jar"));
        assertThrows(IllegalArgumentException.class, () ->
                runner.run(specification("success"), taskDirectory, lindaLibrary));
    }

    @Test
    void rejectsSuccessfulProcessThatDidNotCreateDeclaredOutput() throws Exception {
        createJobArchive(taskDirectory.resolve("test-job.jar"));
        Path lindaLibrary = Files.createFile(taskDirectory.resolve("kdp-linda.jar"));
        Files.writeString(taskDirectory.resolve("input.txt"), "input-content");

        assertThrows(IOException.class, () ->
                runner.run(specification("no-output"), taskDirectory, lindaLibrary));
    }

    private static JobSpecification specification(String mode) {
        return new JobSpecification(
                "test-job.jar",
                List.of("-Dkdp.runner.test=configured-value"),
                List.of(mode, "result.txt"),
                List.of("input.txt"),
                List.of("result.txt"));
    }

    private static void createJobArchive(Path target) throws IOException {
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        manifest.getMainAttributes().put(
                Attributes.Name.MAIN_CLASS, RunnerTestProgram.class.getName());

        String classEntryName = RunnerTestProgram.class.getName()
                .replace('.', '/') + ".class";
        try (InputStream classBytes = RunnerTestProgram.class.getClassLoader()
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
