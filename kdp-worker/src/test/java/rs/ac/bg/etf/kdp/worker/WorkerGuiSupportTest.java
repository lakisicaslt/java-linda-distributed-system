package rs.ac.bg.etf.kdp.worker;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WorkerGuiSupportTest {

    @Test
    void parsesServerPort() {
        assertEquals(5_050, WorkerGuiSupport.parsePort(" 5050 "));
    }

    @Test
    void rejectsInvalidServerPort() {
        assertThrows(IllegalArgumentException.class,
                () -> WorkerGuiSupport.parsePort("-1"));
        assertThrows(IllegalArgumentException.class,
                () -> WorkerGuiSupport.parsePort("unknown"));
    }
}
