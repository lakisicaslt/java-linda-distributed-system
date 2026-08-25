package rs.ac.bg.etf.kdp.runner;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/** Result of one user JAR process. */
public final class JavaTaskResult {

    private final int exitCode;
    private final Path standardOutput;
    private final Path standardError;
    private final List<Path> outputFiles;
    private final List<Path> missingOutputFiles;

    JavaTaskResult(
            int exitCode,
            Path standardOutput,
            Path standardError,
            List<Path> outputFiles,
            List<Path> missingOutputFiles) {
        this.exitCode = exitCode;
        this.standardOutput = Objects.requireNonNull(
                standardOutput, "standard output path must not be null");
        this.standardError = Objects.requireNonNull(
                standardError, "standard error path must not be null");
        this.outputFiles = List.copyOf(Objects.requireNonNull(
                outputFiles, "output files must not be null"));
        this.missingOutputFiles = List.copyOf(Objects.requireNonNull(
                missingOutputFiles, "missing output files must not be null"));
    }

    public int getExitCode() {
        return exitCode;
    }

    public boolean isSuccessful() {
        return exitCode == 0 && missingOutputFiles.isEmpty();
    }

    public Path getStandardOutput() {
        return standardOutput;
    }

    public Path getStandardError() {
        return standardError;
    }

    public List<Path> getOutputFiles() {
        return outputFiles;
    }

    public List<Path> getMissingOutputFiles() {
        return missingOutputFiles;
    }
}
