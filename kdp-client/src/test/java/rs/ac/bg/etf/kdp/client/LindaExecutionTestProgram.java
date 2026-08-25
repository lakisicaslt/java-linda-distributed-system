package rs.ac.bg.etf.kdp.client;

import rs.ac.bg.etf.kdp.Linda;
import rs.ac.bg.etf.kdp.LindaFactory;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class LindaExecutionTestProgram {

    private LindaExecutionTestProgram() {
    }

    public static void main(String[] args) throws Exception {
        String role = args[0];
        Path output = Path.of(args[1]);
        Linda linda = LindaFactory.getLinda();

        if (role.equals("consumer")) {
            String[] template = {"message", null};
            linda.in(template);
            Files.writeString(output, template[1], StandardCharsets.UTF_8);
        } else if (role.equals("producer")) {
            Thread.sleep(200);
            linda.out(new String[]{"message", "distributed tuple"});
            Files.writeString(output, "sent", StandardCharsets.UTF_8);
        } else {
            throw new IllegalArgumentException("Unknown role: " + role);
        }
    }
}
