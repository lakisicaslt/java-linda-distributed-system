package rs.ac.bg.etf.kdp.common.protocol;

import org.junit.jupiter.api.Test;

import java.net.Socket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProtocolSocketsTest {

    @Test
    void configuresReadTimeoutAndTcpOptions() throws Exception {
        try (Socket socket = new Socket()) {
            ProtocolSockets.configure(socket, 1_500);

            assertEquals(1_500, socket.getSoTimeout());
            assertTrue(socket.getTcpNoDelay());
            assertTrue(socket.getKeepAlive());
        }
    }

    @Test
    void rejectsInvalidTimeout() throws Exception {
        try (Socket socket = new Socket()) {
            assertThrows(IllegalArgumentException.class,
                    () -> ProtocolSockets.configure(socket, 0));
        }
    }
}
