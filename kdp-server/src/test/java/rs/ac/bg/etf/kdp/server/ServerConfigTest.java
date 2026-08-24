package rs.ac.bg.etf.kdp.server;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ServerConfigTest {

    @Test
    void acceptsEphemeralPortForTests() {
        ServerConfig config = new ServerConfig("127.0.0.1", 0, 10, 4, 1_000);

        assertEquals("127.0.0.1", config.getBindHost());
        assertEquals(0, config.getPort());
        assertEquals(10, config.getBacklog());
        assertEquals(4, config.getMaxConnections());
        assertEquals(1_000, config.getHandshakeTimeoutMillis());
    }

    @Test
    void rejectsInvalidNetworkSettings() {
        assertThrows(IllegalArgumentException.class,
                () -> new ServerConfig(" ", 5_050, 10, 4, 1_000));
        assertThrows(IllegalArgumentException.class,
                () -> new ServerConfig("localhost", 65_536, 10, 4, 1_000));
        assertThrows(IllegalArgumentException.class,
                () -> new ServerConfig("localhost", 5_050, 0, 4, 1_000));
        assertThrows(IllegalArgumentException.class,
                () -> new ServerConfig("localhost", 5_050, 10, 0, 1_000));
        assertThrows(IllegalArgumentException.class,
                () -> new ServerConfig("localhost", 5_050, 10, 4, 0));
    }
}
