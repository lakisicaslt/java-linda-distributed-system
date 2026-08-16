package rs.ac.bg.etf.kdp.common.domain;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JobSpecificationTest {

    @Test
    void createsValidSpecification() {
        JobSpecification specification = new JobSpecification(
                "Matrix calculation",
                "matrix-job.jar",
                List.of("-Xmx512m"),
                List.of("input.txt", "result.txt"),
                List.of("input.txt"),
                List.of("result.txt"));

        assertEquals("Matrix calculation", specification.getName());
        assertEquals("matrix-job.jar", specification.getJarFileName());
        assertEquals(List.of("-Xmx512m"), specification.getJvmArguments());
        assertEquals(List.of("input.txt", "result.txt"), specification.getProgramArguments());
        assertEquals(List.of("input.txt"), specification.getInputFileNames());
        assertEquals(List.of("result.txt"), specification.getOutputFileNames());
    }

    @Test
    void acceptsMaximumAllowedNumberOfFiles() {
        List<String> sixFiles = List.of("1.txt", "2.txt", "3.txt", "4.txt", "5.txt", "6.txt");

        JobSpecification specification = specificationWithFiles(sixFiles, sixFiles);

        assertEquals(6, specification.getInputFileNames().size());
        assertEquals(6, specification.getOutputFileNames().size());
    }

    @Test
    void rejectsMoreThanSixFiles() {
        List<String> sevenFiles = List.of(
                "1.txt", "2.txt", "3.txt", "4.txt", "5.txt", "6.txt", "7.txt");

        assertThrows(IllegalArgumentException.class,
                () -> specificationWithFiles(sevenFiles, List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> specificationWithFiles(List.of(), sevenFiles));
    }

    @Test
    void rejectsInvalidJarFileName() {
        assertThrows(IllegalArgumentException.class, () -> new JobSpecification(
                "Job",
                "program.zip",
                List.of(),
                List.of(),
                List.of(),
                List.of()));
    }

    @Test
    void rejectsPathsAndDuplicateFileNames() {
        assertThrows(IllegalArgumentException.class,
                () -> specificationWithFiles(List.of("../secret.txt"), List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> specificationWithFiles(List.of("input.txt", "input.txt"), List.of()));
    }

    @Test
    void makesDefensiveCopiesOfLists() {
        List<String> mutableInputs = new ArrayList<>(List.of("input.txt"));
        JobSpecification specification = specificationWithFiles(mutableInputs, List.of());

        mutableInputs.add("another.txt");

        assertEquals(List.of("input.txt"), specification.getInputFileNames());
        assertThrows(UnsupportedOperationException.class,
                () -> specification.getInputFileNames().add("forbidden.txt"));
    }

    private static JobSpecification specificationWithFiles(
            List<String> inputFiles,
            List<String> outputFiles) {
        return new JobSpecification(
                "Test job",
                "test-job.jar",
                List.of(),
                List.of(),
                inputFiles,
                outputFiles);
    }
}
