package rs.ac.bg.etf.kdp.client;

import rs.ac.bg.etf.kdp.Linda;
import rs.ac.bg.etf.kdp.LindaFactory;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class RecoverableEvalExecutionTestProgram {

    private RecoverableEvalExecutionTestProgram() {
    }

    public static void main(String[] args) throws Exception {
        Linda linda = LindaFactory.getLinda();
        linda.eval("recoverable calculation", new RecoverableEvalTestRunnable());

        String[] firstStart = {"eval-started", null};
        linda.in(firstStart);
        linda.out(new String[]{"root-observed-first-start"});

        String[] secondStart = {"eval-started", null};
        linda.in(secondStart);
        linda.out(new String[]{"release-eval"});

        String[] result = {"eval-result", null};
        linda.in(result);
        Files.writeString(Path.of(args[0]), result[1], StandardCharsets.UTF_8);
    }
}
