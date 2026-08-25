package rs.ac.bg.etf.kdp.server;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ServerGuiSupportTest {

    @Test
    void acceptsPortInTcpRange() {
        assertEquals(5_050, ServerGuiSupport.parsePort("5050"));
        assertEquals(65_535, ServerGuiSupport.parsePort(" 65535 "));
    }

    @Test
    void rejectsInvalidPort() {
        assertThrows(IllegalArgumentException.class,
                () -> ServerGuiSupport.parsePort("0"));
        assertThrows(IllegalArgumentException.class,
                () -> ServerGuiSupport.parsePort("port"));
    }
}
