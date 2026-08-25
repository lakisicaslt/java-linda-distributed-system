package rs.ac.bg.etf.kdp.common.domain;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Immutable description of a Java job submitted by a client.
 */
public final class JobSpecification {

    public static final int MAX_INPUT_FILES = 6;
    public static final int MAX_OUTPUT_FILES = 6;

    private final String jarFileName;
    private final List<String> jvmArguments;
    private final List<String> programArguments;
    private final List<String> inputFileNames;
    private final List<String> outputFileNames;

    public JobSpecification(
            String jarFileName,
            List<String> jvmArguments,
            List<String> programArguments,
            List<String> inputFileNames,
            List<String> outputFileNames) {
        this.jarFileName = validateJarFileName(jarFileName);
        this.jvmArguments = copyArguments(jvmArguments, "JVM arguments");
        this.programArguments = copyArguments(programArguments, "program arguments");
        this.inputFileNames = copyAndValidateFileNames(
                inputFileNames, MAX_INPUT_FILES, "input files");
        this.outputFileNames = copyAndValidateFileNames(
                outputFileNames, MAX_OUTPUT_FILES, "output files");
    }

    public String getJarFileName() {
        return jarFileName;
    }

    public List<String> getJvmArguments() {
        return jvmArguments;
    }

    public List<String> getProgramArguments() {
        return programArguments;
    }

    public List<String> getInputFileNames() {
        return inputFileNames;
    }

    public List<String> getOutputFileNames() {
        return outputFileNames;
    }

    private static String validateJarFileName(String jarFileName) {
        String validatedName = validateSimpleFileName(jarFileName, "JAR file name");
        if (!validatedName.toLowerCase(Locale.ROOT).endsWith(".jar")) {
            throw new IllegalArgumentException("JAR file name must end with .jar");
        }
        return validatedName;
    }

    private static List<String> copyArguments(List<String> arguments, String fieldName) {
        Objects.requireNonNull(arguments, fieldName + " must not be null");
        if (arguments.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException(fieldName + " must not contain null");
        }
        return List.copyOf(arguments);
    }

    private static List<String> copyAndValidateFileNames(
            List<String> fileNames,
            int maximumCount,
            String fieldName) {
        Objects.requireNonNull(fileNames, fieldName + " must not be null");
        if (fileNames.size() > maximumCount) {
            throw new IllegalArgumentException(
                    fieldName + " must contain at most " + maximumCount + " files");
        }

        List<String> validatedNames = fileNames.stream()
                .map(fileName -> validateSimpleFileName(fileName, fieldName))
                .toList();

        if (new HashSet<>(validatedNames).size() != validatedNames.size()) {
            throw new IllegalArgumentException(fieldName + " must not contain duplicate file names");
        }

        return List.copyOf(validatedNames);
    }

    private static String validateSimpleFileName(String fileName, String fieldName) {
        String validatedName = requireNonBlank(fileName, fieldName);
        if (validatedName.equals(".")
                || validatedName.equals("..")
                || validatedName.contains("/")
                || validatedName.contains("\\")) {
            throw new IllegalArgumentException(fieldName + " must contain simple file names only");
        }
        return validatedName;
    }

    private static String requireNonBlank(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName + " must not be null");
        String trimmedValue = value.trim();
        if (trimmedValue.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return trimmedValue;
    }
}
