package rs.ac.bg.etf.kdp.demo;

import rs.ac.bg.etf.kdp.Linda;
import rs.ac.bg.etf.kdp.LindaFactory;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Root job that starts a serializable calculation with Linda eval. */
public final class EvalJob {

    private EvalJob() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            throw new IllegalArgumentException("Expected output file name");
        }
        Linda linda = LindaFactory.getLinda();
        linda.eval("demo calculation", new EvalTask("computed remotely"));

        String[] result = {"eval-result", null, null};
        linda.in(result);
        Files.writeString(
                Path.of(args[0]),
                result[1] + "|" + result[2],
                StandardCharsets.UTF_8);
    }
}
