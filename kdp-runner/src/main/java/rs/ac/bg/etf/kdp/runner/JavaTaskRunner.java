package rs.ac.bg.etf.kdp.runner;

import rs.ac.bg.etf.kdp.LindaFactory;
import rs.ac.bg.etf.kdp.common.domain.JobSpecification;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.jar.Attributes;
import java.util.jar.JarFile;

/** Starts a submitted Java archive in a separate JVM process. */
public final class JavaTaskRunner {

    private static final String CAPTURE_DIRECTORY = ".kdp";
    private static final String STANDARD_OUTPUT_FILE = "stdout.txt";
    private static final String STANDARD_ERROR_FILE = "stderr.txt";
    private static final String EVAL_PAYLOAD_FILE = "eval-task.bin";

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
        return run(specification, taskDirectory, lindaLibrary, null);
    }

    public JavaTaskResult run(
            JobSpecification specification,
            Path taskDirectory,
            Path lindaLibrary,
            LindaConnectionInfo lindaConnection) throws IOException, InterruptedException {
        Objects.requireNonNull(specification, "job specification must not be null");
        Path workingDirectory = requireDirectory(taskDirectory, "task directory");
        Path jobArchive = requireRegularFile(
                workingDirectory.resolve(specification.getJarFileName()), "job archive");
        Path lindaArchive = requireRegularFile(lindaLibrary, "Linda library")
                .toAbsolutePath()
                .normalize();
        validateInputFiles(specification, workingDirectory);

        List<Path> outputFiles = specification.getOutputFileNames().stream()
                .map(workingDirectory::resolve)
                .toList();
        return runProcess(
                buildRootCommand(specification, jobArchive, lindaArchive, lindaConnection),
                workingDirectory,
                outputFiles);
    }

    public JavaTaskResult runEval(
            JobSpecification specification,
            Path taskDirectory,
            Path lindaLibrary,
            LindaConnectionInfo lindaConnection,
            String evalName,
            byte[] serializedRunnable) throws IOException, InterruptedException {
        Objects.requireNonNull(specification, "job specification must not be null");
        Objects.requireNonNull(lindaConnection, "Linda connection must not be null");
        Objects.requireNonNull(evalName, "eval name must not be null");
        if (evalName.isBlank()) {
            throw new IllegalArgumentException("eval name must not be blank");
        }
        Objects.requireNonNull(serializedRunnable, "serialized runnable must not be null");
        if (serializedRunnable.length == 0) {
            throw new IllegalArgumentException("serialized runnable must not be empty");
        }
        Path workingDirectory = requireDirectory(taskDirectory, "task directory");
        Path jobArchive = requireRegularFile(
                workingDirectory.resolve(specification.getJarFileName()), "job archive");
        Path lindaArchive = requireRegularFile(lindaLibrary, "Linda library")
                .toAbsolutePath()
                .normalize();
        validateInputFiles(specification, workingDirectory);

        Path captureDirectory = workingDirectory.resolve(CAPTURE_DIRECTORY);
        Files.createDirectories(captureDirectory);
        Path evalPayload = captureDirectory.resolve(EVAL_PAYLOAD_FILE);
        Files.write(evalPayload, serializedRunnable);
        try {
            return runProcess(
                    buildEvalCommand(
                            specification,
                            jobArchive,
                            lindaArchive,
                            lindaConnection,
                            evalPayload,
                            evalName.trim()),
                    workingDirectory,
                    List.of());
        } finally {
            Files.deleteIfExists(evalPayload);
        }
    }

    private JavaTaskResult runProcess(
            List<String> command,
            Path workingDirectory,
            List<Path> outputFiles) throws IOException, InterruptedException {
        Path captureDirectory = workingDirectory.resolve(CAPTURE_DIRECTORY);
        Files.createDirectories(captureDirectory);
        Path standardOutput = captureDirectory.resolve(STANDARD_OUTPUT_FILE);
        Path standardError = captureDirectory.resolve(STANDARD_ERROR_FILE);

        ProcessBuilder processBuilder = new ProcessBuilder(command);
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
            process.onExit().join();
            Thread.currentThread().interrupt();
            throw exception;
        }

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

    private List<String> buildRootCommand(
            JobSpecification specification,
            Path jobArchive,
            Path lindaArchive,
            LindaConnectionInfo lindaConnection) throws IOException {
        List<String> command = baseCommand(specification, lindaConnection);
        command.add("-cp");
        command.add(jobArchive + File.pathSeparator + lindaArchive);
        command.add(mainClass(jobArchive));
        command.addAll(specification.getProgramArguments());
        command.add(lindaArchive.toString());
        return command;
    }

    private List<String> buildEvalCommand(
            JobSpecification specification,
            Path jobArchive,
            Path lindaArchive,
            LindaConnectionInfo lindaConnection,
            Path evalPayload,
            String evalName) throws IOException {
        List<String> command = baseCommand(specification, lindaConnection);
        command.add("-cp");
        command.add(jobArchive
                + File.pathSeparator + lindaArchive
                + File.pathSeparator + runnerLocation());
        command.add(EvalTaskMain.class.getName());
        command.add(evalPayload.toString());
        command.add(evalName);
        command.add(lindaArchive.toString());
        return command;
    }

    private List<String> baseCommand(
            JobSpecification specification,
            LindaConnectionInfo lindaConnection) {
        List<String> command = new ArrayList<>();
        command.add(javaExecutable.toString());
        command.addAll(specification.getJvmArguments());
        if (lindaConnection != null) {
            command.add(systemProperty(
                    LindaFactory.SERVER_HOST_PROPERTY, lindaConnection.getServerHost()));
            command.add(systemProperty(
                    LindaFactory.SERVER_PORT_PROPERTY,
                    Integer.toString(lindaConnection.getServerPort())));
            command.add(systemProperty(
                    LindaFactory.BATCH_ID_PROPERTY,
                    lindaConnection.getBatchId().toString()));
            command.add(systemProperty(
                    LindaFactory.TASK_ID_PROPERTY,
                    lindaConnection.getTaskId().toString()));
        }
        return command;
    }

    private static Path runnerLocation() throws IOException {
        try {
            return Path.of(EvalTaskMain.class.getProtectionDomain()
                    .getCodeSource()
                    .getLocation()
                    .toURI());
        } catch (URISyntaxException exception) {
            throw new IOException("Could not locate the eval task runner", exception);
        }
    }

    private static String mainClass(Path jobArchive) throws IOException {
        try (JarFile archive = new JarFile(jobArchive.toFile())) {
            if (archive.getManifest() == null) {
                throw new IllegalArgumentException("Job archive does not have a manifest");
            }
            String mainClass = archive.getManifest()
                    .getMainAttributes()
                    .getValue(Attributes.Name.MAIN_CLASS);
            if (mainClass == null || mainClass.isBlank()) {
                throw new IllegalArgumentException(
                        "Job archive manifest does not declare Main-Class");
            }
            return mainClass.trim();
        }
    }

    private static String systemProperty(String name, String value) {
        return "-D" + name + "=" + value;
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
