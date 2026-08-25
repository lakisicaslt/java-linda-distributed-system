package rs.ac.bg.etf.kdp.demo;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/** Small file-processing job used during laboratory checks. */
public final class FileProcessingJob {

    private FileProcessingJob() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            throw new IllegalArgumentException("Expected output file name");
        }
        String outputFileName = args[0];
        if (args.length > 2) {
            Thread.sleep(Long.parseLong(args[1]));
        }

        String input = Files.readString(Path.of("input.txt"), StandardCharsets.UTF_8);
        Files.writeString(
                Path.of(outputFileName),
                input.toUpperCase(Locale.ROOT),
                StandardCharsets.UTF_8);
        System.out.println("Processed input.txt into " + outputFileName);
        System.err.println("File processing job finished");
    }
}
