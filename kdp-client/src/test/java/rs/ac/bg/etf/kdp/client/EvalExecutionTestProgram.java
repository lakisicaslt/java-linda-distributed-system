package rs.ac.bg.etf.kdp.client;

import rs.ac.bg.etf.kdp.Linda;
import rs.ac.bg.etf.kdp.LindaFactory;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class EvalExecutionTestProgram {

    private EvalExecutionTestProgram() {
    }

    public static void main(String[] args) throws Exception {
        Linda linda = LindaFactory.getLinda();
        linda.eval("remote calculation", new EvalTestRunnable("computed remotely"));

        String[] result = {"eval-result", null, null};
        linda.in(result);
        Files.writeString(
                Path.of(args[0]), result[1] + "|" + result[2], StandardCharsets.UTF_8);
    }
}
