package rs.ac.bg.etf.kdp.client;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

public final class ClientExecutionTestProgram {

    private ClientExecutionTestProgram() {
    }

    public static void main(String[] args) throws Exception {
        String outputFileName = args[0];
        String lindaLibrary = args[args.length - 1];
        if (args.length > 2) {
            Thread.sleep(Long.parseLong(args[1]));
        }
        String input = Files.readString(Path.of("input.txt"), StandardCharsets.UTF_8);

        Files.writeString(
                Path.of(outputFileName),
                input.toUpperCase(Locale.ROOT),
                StandardCharsets.UTF_8);
        System.out.println("Linda library: " + lindaLibrary);
        System.err.println("client end-to-end test");
    }
}
