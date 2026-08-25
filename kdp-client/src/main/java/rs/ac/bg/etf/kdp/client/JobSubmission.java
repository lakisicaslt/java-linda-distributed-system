package rs.ac.bg.etf.kdp.client;

import rs.ac.bg.etf.kdp.common.domain.JobSpecification;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Local files paired with the portable description sent to the server. */
public final class JobSubmission {

    private final JobSpecification specification;
    private final Path jarFile;
    private final Map<String, Path> inputFiles;

    public JobSubmission(
            JobSpecification specification,
            Path jarFile,
            List<Path> inputFiles) {
        this.specification = Objects.requireNonNull(
                specification, "job specification must not be null");
        this.jarFile = validateRegularFile(jarFile, "JAR file");
        if (!this.jarFile.getFileName().toString().equals(specification.getJarFileName())) {
            throw new IllegalArgumentException(
                    "JAR file name must match the job specification");
        }
        this.inputFiles = validateInputFiles(inputFiles, specification);
    }

    public JobSpecification getSpecification() {
        return specification;
    }

    public Path getJarFile() {
        return jarFile;
    }

    public Path getInputFile(String fileName) {
        Path path = inputFiles.get(Objects.requireNonNull(
                fileName, "input file name must not be null"));
        if (path == null) {
            throw new IllegalArgumentException("input file is not part of this submission");
        }
        return path;
    }

    private static Map<String, Path> validateInputFiles(
            List<Path> inputFiles,
            JobSpecification specification) {
        Objects.requireNonNull(inputFiles, "input files must not be null");
        Map<String, Path> byName = new HashMap<>();
        for (Path inputFile : inputFiles) {
            Path validated = validateRegularFile(inputFile, "input file");
            String fileName = validated.getFileName().toString();
            if (byName.putIfAbsent(fileName, validated) != null) {
                throw new IllegalArgumentException("input file names must be unique");
            }
        }
        if (!byName.keySet().equals(Set.copyOf(specification.getInputFileNames()))) {
            throw new IllegalArgumentException(
                    "local input files must match the job specification");
        }
        return Map.copyOf(byName);
    }

    private static Path validateRegularFile(Path path, String fieldName) {
        Objects.requireNonNull(path, fieldName + " must not be null");
        Path normalized = path.toAbsolutePath().normalize();
        if (!Files.isRegularFile(normalized)) {
            throw new IllegalArgumentException(fieldName + " does not exist: " + normalized);
        }
        return normalized;
    }
}
