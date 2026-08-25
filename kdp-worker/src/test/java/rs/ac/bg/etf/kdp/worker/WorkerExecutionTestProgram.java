package rs.ac.bg.etf.kdp.worker;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class WorkerExecutionTestProgram {

    private WorkerExecutionTestProgram() {
    }

    public static void main(String[] args) throws Exception {
        String input = Files.readString(Path.of("input.txt"), StandardCharsets.UTF_8);
        Files.writeString(Path.of(args[0]), input.toUpperCase(), StandardCharsets.UTF_8);
        System.out.println("Linda library: " + args[args.length - 1]);
        System.err.println("test stderr");
    }
}
