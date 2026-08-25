package rs.ac.bg.etf.kdp.runner;

import rs.ac.bg.etf.kdp.common.domain.JobSpecification;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Starts a submitted Java archive in a separate JVM process. */
public final class JavaTaskRunner {

    private static final String CAPTURE_DIRECTORY = ".kdp";
    private static final String STANDARD_OUTPUT_FILE = "stdout.txt";
    private static final String STANDARD_ERROR_FILE = "stderr.txt";

    private final Path javaExecutable;

    public JavaTaskRunner() {
        this(defaultJavaExecutable());
    }

    public JavaTaskRunner(Path javaExecutable) {
        this.javaExecutable = requireRegularFile(
                javaExecutable, "Java executable").toAbsolutePath().normalize();
    }

    public JavaTaskResult run(
            JobSpecification specification,
            Path taskDirectory,
            Path lindaLibrary) throws IOException, InterruptedException {
        Objects.requireNonNull(specification, "job specification must not be null");
        Path workingDirectory = requireDirectory(taskDirectory, "task directory");
        Path jobArchive = requireRegularFile(
                workingDirectory.resolve(specification.getJarFileName()), "job archive");
        Path lindaArchive = requireRegularFile(lindaLibrary, "Linda library")
                .toAbsolutePath()
                .normalize();
        validateInputFiles(specification, workingDirectory);

        Path captureDirectory = workingDirectory.resolve(CAPTURE_DIRECTORY);
        Files.createDirectories(captureDirectory);
        Path standardOutput = captureDirectory.resolve(STANDARD_OUTPUT_FILE);
        Path standardError = captureDirectory.resolve(STANDARD_ERROR_FILE);

        ProcessBuilder processBuilder = new ProcessBuilder(buildCommand(
                specification, jobArchive, lindaArchive));
        processBuilder.directory(workingDirectory.toFile());
        processBuilder.redirectOutput(standardOutput.toFile());
        processBuilder.redirectError(standardError.toFile());

        Process process = processBuilder.start();
        process.getOutputStream().close();
        int exitCode;
        try {
            exitCode = process.waitFor();
        } catch (InterruptedException exception) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
            throw exception;
        }

        List<Path> outputFiles = specification.getOutputFileNames().stream()
                .map(workingDirectory::resolve)
                .toList();
        List<Path> missingOutputFiles = exitCode == 0
                ? outputFiles.stream().filter(path -> !Files.isRegularFile(path)).toList()
                : List.of();
        return new JavaTaskResult(
                exitCode,
                standardOutput,
                standardError,
                outputFiles,
                missingOutputFiles);
    }

    private List<String> buildCommand(
            JobSpecification specification,
            Path jobArchive,
            Path lindaArchive) {
        List<String> command = new ArrayList<>();
        command.add(javaExecutable.toString());
        command.addAll(specification.getJvmArguments());
        command.add("-jar");
        command.add(jobArchive.toString());
        command.addAll(specification.getProgramArguments());
        command.add(lindaArchive.toString());
        return command;
    }

    private static void validateInputFiles(
            JobSpecification specification,
            Path taskDirectory) {
        for (String inputFileName : specification.getInputFileNames()) {
            requireRegularFile(taskDirectory.resolve(inputFileName), "input file");
        }
    }

    private static Path requireDirectory(Path path, String description) {
        Objects.requireNonNull(path, description + " path must not be null");
        Path normalized = path.toAbsolutePath().normalize();
        if (!Files.isDirectory(normalized)) {
            throw new IllegalArgumentException(description + " does not exist: " + normalized);
        }
        return normalized;
    }

    private static Path requireRegularFile(Path path, String description) {
        Objects.requireNonNull(path, description + " path must not be null");
        Path normalized = path.toAbsolutePath().normalize();
        if (!Files.isRegularFile(normalized)) {
            throw new IllegalArgumentException(description + " does not exist: " + normalized);
        }
        return normalized;
    }

    private static Path defaultJavaExecutable() {
        String executableName = System.getProperty("os.name", "")
                .toLowerCase()
                .contains("win") ? "java.exe" : "java";
        return Path.of(System.getProperty("java.home"), "bin", executableName);
    }
}
