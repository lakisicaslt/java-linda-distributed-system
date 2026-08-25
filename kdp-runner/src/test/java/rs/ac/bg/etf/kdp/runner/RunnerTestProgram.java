package rs.ac.bg.etf.kdp.runner;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class RunnerTestProgram {

    private RunnerTestProgram() {
    }

    public static void main(String[] args) throws Exception {
        String mode = args[0];
        String outputFileName = args[1];
        String lindaLibrary = args[args.length - 1];
        String input = Files.readString(Path.of("input.txt"), StandardCharsets.UTF_8);

        System.out.println(System.getProperty("kdp.runner.test") + "|" + lindaLibrary);
        System.err.println("test standard error");
        if (!mode.equals("no-output")) {
            Files.writeString(
                    Path.of(outputFileName), mode + "|" + input, StandardCharsets.UTF_8);
        }

        if (mode.equals("fail")) {
            System.exit(7);
        }
    }
}
