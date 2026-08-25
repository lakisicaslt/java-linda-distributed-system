package rs.ac.bg.etf.kdp.client;

import rs.ac.bg.etf.kdp.common.domain.JobSpecification;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/** Values entered for one job in the graphical form. */
final class JobFormData {

    private final Path jarFile;
    private final List<String> jvmArguments;
    private final List<String> programArguments;
    private final List<Path> inputFiles;
    private final List<String> outputFileNames;

    JobFormData(
            Path jarFile,
            List<String> jvmArguments,
            List<String> programArguments,
            List<Path> inputFiles,
            List<String> outputFileNames) {
        this.jarFile = Objects.requireNonNull(jarFile, "JAR file must not be null")
                .toAbsolutePath()
                .normalize();
        this.jvmArguments = List.copyOf(jvmArguments);
        this.programArguments = List.copyOf(programArguments);
        this.inputFiles = inputFiles.stream()
                .map(path -> path.toAbsolutePath().normalize())
                .toList();
        this.outputFileNames = List.copyOf(outputFileNames);
    }

    JobSubmission toSubmission() {
        Path jarName = jarFile.getFileName();
        if (jarName == null) {
            throw new IllegalArgumentException("Izabrana JAR putanja ne predstavlja datoteku.");
        }
        List<String> inputNames = inputFiles.stream()
                .map(Path::getFileName)
                .map(Path::toString)
                .toList();
        JobSpecification specification = new JobSpecification(
                jarName.toString(),
                jvmArguments,
                programArguments,
                inputNames,
                outputFileNames);
        return new JobSubmission(specification, jarFile, inputFiles);
    }

    String summary() {
        return jarFile.getFileName() + "  ·  " + inputFiles.size() + " ulaz(a)  ·  "
                + outputFileNames.size() + " izlaz(a)";
    }

    Path jarFile() {
        return jarFile;
    }

    List<String> jvmArguments() {
        return jvmArguments;
    }

    List<String> programArguments() {
        return programArguments;
    }

    List<Path> inputFiles() {
        return inputFiles;
    }

    List<String> outputFileNames() {
        return outputFileNames;
    }

    static List<String> argumentsFromText(String text) {
        return Arrays.stream(text.split("\\R"))
                .map(String::trim)
                .filter(line -> !line.isEmpty())
                .toList();
    }
}
