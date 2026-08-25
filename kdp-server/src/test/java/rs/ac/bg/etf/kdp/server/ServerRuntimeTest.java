package rs.ac.bg.etf.kdp.server;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServerRuntimeTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void startsTheSameServerUsedByTheGui() throws Exception {
        ServerConfig config = new ServerConfig("127.0.0.1", 0, 10, 4, 2_000);
        ServerRuntime runtime = ServerRuntime.create(config, temporaryDirectory);

        try {
            runtime.start();

            assertTrue(runtime.isRunning());
            assertTrue(runtime.getLocalPort() > 0);
            assertEquals(temporaryDirectory.resolve("server.log"), runtime.getLogFile());
            assertTrue(runtime.getOverview().workers().isEmpty());
            assertTrue(runtime.getOverview().jobs().isEmpty());
        } finally {
            runtime.close();
        }

        assertFalse(runtime.isRunning());
    }
}
