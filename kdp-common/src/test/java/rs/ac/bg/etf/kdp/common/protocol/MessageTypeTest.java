package rs.ac.bg.etf.kdp.common.protocol;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

class MessageTypeTest {

    @Test
    void wireCodesAreUnique() {
        Set<Integer> usedCodes = new HashSet<>();

        for (MessageType type : MessageType.values()) {
            assertTrue(usedCodes.add(type.getCode()),
                    () -> "Duplicate message type code: " + type.getCode());
        }
    }
}
