package rs.ac.bg.etf.kdp.demo;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Records the sizes of two transferred files larger than 60 MB. */
public final class LargeFilesJob {

    private LargeFilesJob() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            throw new IllegalArgumentException("Expected output file name");
        }
        long firstSize = Files.size(Path.of("big-a.bin"));
        long secondSize = Files.size(Path.of("big-b.bin"));
        String result = "big-a.bin=" + firstSize + System.lineSeparator()
                + "big-b.bin=" + secondSize + System.lineSeparator();
        Files.writeString(Path.of(args[0]), result, StandardCharsets.UTF_8);
        System.out.print(result);
    }
}
