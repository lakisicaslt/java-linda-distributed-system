package rs.ac.bg.etf.kdp.common.protocol;

import java.util.Objects;

public final class ProtocolRoleValidator {

    private ProtocolRoleValidator() {
    }

    public static void requireAllowed(ConnectionRole role, MessageType type)
            throws ProtocolException {
        Objects.requireNonNull(role, "connection role must not be null");
        Objects.requireNonNull(type, "message type must not be null");
        if (!type.isAllowedFor(role)) {
            throw new ProtocolException(
                    ProtocolError.ROLE_VIOLATION,
                    type + " is not allowed on a " + role + " connection");
        }
    }
}
