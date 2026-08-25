package rs.ac.bg.etf.kdp.demo;

import rs.ac.bg.etf.kdp.Linda;
import rs.ac.bg.etf.kdp.LindaFactory;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Producer or consumer used to verify a shared tuple space. */
public final class LindaJob {

    private LindaJob() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 3) {
            throw new IllegalArgumentException("Expected role and output file name");
        }
        String role = args[0];
        Path outputFile = Path.of(args[1]);
        Linda linda = LindaFactory.getLinda();

        if ("consumer".equals(role)) {
            String[] template = {"message", null};
            linda.in(template);
            Files.writeString(outputFile, template[1], StandardCharsets.UTF_8);
        } else if ("producer".equals(role)) {
            Thread.sleep(200);
            linda.out(new String[]{"message", "distributed tuple"});
            Files.writeString(outputFile, "sent", StandardCharsets.UTF_8);
        } else {
            throw new IllegalArgumentException("Unknown role: " + role);
        }
    }
}
