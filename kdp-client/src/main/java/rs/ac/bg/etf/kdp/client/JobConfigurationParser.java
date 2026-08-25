package rs.ac.bg.etf.kdp.client;

import rs.ac.bg.etf.kdp.common.domain.JobSpecification;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;

/** Reads a batch of jobs from a UTF-8 properties file. */
public final class JobConfigurationParser {

    private static final int MAX_JOBS = 256;
    private static final int MAX_ARGUMENTS = 256;

    public List<JobSubmission> parse(Path configurationFile) throws IOException {
        Objects.requireNonNull(
                configurationFile, "configuration file path must not be null");
        Path file = configurationFile.toAbsolutePath().normalize();
        if (!Files.isRegularFile(file)) {
            throw new IllegalArgumentException(
                    "configuration file does not exist: " + file);
        }

        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            properties.load(reader);
        }

        Set<String> usedKeys = new HashSet<>();
        int jobCount = requiredCount(properties, usedKeys, "jobs", 1, MAX_JOBS);
        List<JobSubmission> submissions = new ArrayList<>(jobCount);
        Path baseDirectory = file.getParent();
        for (int jobIndex = 1; jobIndex <= jobCount; jobIndex++) {
            submissions.add(readJob(properties, usedKeys, baseDirectory, jobIndex));
        }
        rejectUnknownKeys(properties, usedKeys);
        return List.copyOf(submissions);
    }

    private static JobSubmission readJob(
            Properties properties,
            Set<String> usedKeys,
            Path baseDirectory,
            int jobIndex) {
        String prefix = "job." + jobIndex + ".";
        Path jarFile = resolvePath(
                baseDirectory,
                requiredValue(properties, usedKeys, prefix + "jar"),
                prefix + "jar");
        List<String> jvmArguments = readValues(
                properties,
                usedKeys,
                prefix + "jvmArgument",
                MAX_ARGUMENTS,
                false);
        List<String> programArguments = readValues(
                properties,
                usedKeys,
                prefix + "argument",
                MAX_ARGUMENTS,
                false);
        List<String> inputValues = readValues(
                properties,
                usedKeys,
                prefix + "input",
                JobSpecification.MAX_INPUT_FILES,
                true);
        List<String> outputFileNames = readValues(
                properties,
                usedKeys,
                prefix + "output",
                JobSpecification.MAX_OUTPUT_FILES,
                true);

        List<Path> inputFiles = inputValues.stream()
                .map(value -> resolvePath(baseDirectory, value, prefix + "input"))
                .toList();
        String jarFileName = fileName(jarFile, prefix + "jar");
        List<String> inputFileNames = inputFiles.stream()
                .map(path -> fileName(path, prefix + "input"))
                .toList();
        JobSpecification specification = new JobSpecification(
                jarFileName,
                jvmArguments,
                programArguments,
                inputFileNames,
                outputFileNames);
        return new JobSubmission(specification, jarFile, inputFiles);
    }

    private static List<String> readValues(
            Properties properties,
            Set<String> usedKeys,
            String prefix,
            int maximumCount,
            boolean requireNonBlank) {
        String countKey = prefix + ".count";
        int count = optionalCount(properties, usedKeys, countKey, maximumCount);
        List<String> values = new ArrayList<>(count);
        for (int index = 1; index <= count; index++) {
            String key = prefix + "." + index;
            String value = requireNonBlank
                    ? requiredValue(properties, usedKeys, key)
                    : requiredArgument(properties, usedKeys, key);
            values.add(value);
        }
        return List.copyOf(values);
    }

    private static int requiredCount(
            Properties properties,
            Set<String> usedKeys,
            String key,
            int minimum,
            int maximum) {
        String value = requiredValue(properties, usedKeys, key);
        return parseCount(key, value, minimum, maximum);
    }

    private static int optionalCount(
            Properties properties,
            Set<String> usedKeys,
            String key,
            int maximum) {
        String value = properties.getProperty(key);
        if (value == null) {
            return 0;
        }
        usedKeys.add(key);
        return parseCount(key, value, 0, maximum);
    }

    private static int parseCount(String key, String value, int minimum, int maximum) {
        int count;
        try {
            count = Integer.parseInt(value.trim());
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(key + " must be an integer", exception);
        }
        if (count < minimum || count > maximum) {
            throw new IllegalArgumentException(
                    key + " must be between " + minimum + " and " + maximum);
        }
        return count;
    }

    private static String requiredValue(
            Properties properties,
            Set<String> usedKeys,
            String key) {
        String value = properties.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("missing or blank property: " + key);
        }
        usedKeys.add(key);
        return value.trim();
    }

    private static String requiredArgument(
            Properties properties,
            Set<String> usedKeys,
            String key) {
        String value = properties.getProperty(key);
        if (value == null) {
            throw new IllegalArgumentException("missing property: " + key);
        }
        usedKeys.add(key);
        return value;
    }

    private static Path resolvePath(Path baseDirectory, String value, String key) {
        Path path;
        try {
            path = Path.of(value);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("invalid path in property " + key, exception);
        }
        if (!path.isAbsolute()) {
            path = baseDirectory.resolve(path);
        }
        return path.toAbsolutePath().normalize();
    }

    private static String fileName(Path path, String key) {
        Path fileName = path.getFileName();
        if (fileName == null) {
            throw new IllegalArgumentException("property does not identify a file: " + key);
        }
        return fileName.toString();
    }

    private static void rejectUnknownKeys(Properties properties, Set<String> usedKeys) {
        List<String> unknownKeys = properties.stringPropertyNames().stream()
                .filter(key -> !usedKeys.contains(key))
                .sorted()
                .toList();
        if (!unknownKeys.isEmpty()) {
            throw new IllegalArgumentException(
                    "unknown configuration properties: " + String.join(", ", unknownKeys));
        }
    }
}
