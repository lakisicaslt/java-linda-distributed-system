package rs.ac.bg.etf.kdp.common.protocol;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProtocolRoleValidatorTest {

    @Test
    void acceptsMessagesBelongingToConnectionRole() {
        assertDoesNotThrow(() -> ProtocolRoleValidator.requireAllowed(
                ConnectionRole.CLIENT, MessageType.SUBMIT_BATCH));
        assertDoesNotThrow(() -> ProtocolRoleValidator.requireAllowed(
                ConnectionRole.WORKER, MessageType.HEARTBEAT));
        assertDoesNotThrow(() -> ProtocolRoleValidator.requireAllowed(
                ConnectionRole.LINDA, MessageType.LINDA_OUT));
    }

    @Test
    void rejectsMessagesFromAnotherProtocolRole() {
        ProtocolException exception = assertThrows(ProtocolException.class, () ->
                ProtocolRoleValidator.requireAllowed(
                        ConnectionRole.CLIENT, MessageType.HEARTBEAT));

        assertEquals(ProtocolError.ROLE_VIOLATION, exception.getError());
    }
}
