package rs.ac.bg.etf.kdp.client;

import org.junit.jupiter.api.Test;
import rs.ac.bg.etf.kdp.common.domain.JobStatus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ClientGuiSupportTest {

    @Test
    void parsesValidPortAndBatchId() {
        assertEquals(5_050, ClientGuiSupport.parsePort(" 5050 "));
        assertEquals(
                "f3f8420f-48af-4c87-b77a-9cf449783b59",
                ClientGuiSupport.parseBatchId(
                        "f3f8420f-48af-4c87-b77a-9cf449783b59").toString());
    }

    @Test
    void rejectsInvalidConnectionValues() {
        assertThrows(IllegalArgumentException.class,
                () -> ClientGuiSupport.parsePort("server"));
        assertThrows(IllegalArgumentException.class,
                () -> ClientGuiSupport.parsePort("70000"));
        assertThrows(IllegalArgumentException.class,
                () -> ClientGuiSupport.parseBatchId("not-a-batch-id"));
    }

    @Test
    void givesReadableLabelsForEveryJobStatus() {
        for (JobStatus status : JobStatus.values()) {
            String label = ClientGuiSupport.statusLabel(status);
            org.junit.jupiter.api.Assertions.assertFalse(label.isBlank());
            org.junit.jupiter.api.Assertions.assertNotEquals(status.name(), label);
        }
    }
}
