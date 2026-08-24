package rs.ac.bg.etf.kdp.common.protocol;

public enum ConnectionRole {
    CLIENT(1),
    WORKER(2),
    LINDA(3);

    private final int code;

    ConnectionRole(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    public static ConnectionRole fromCode(int code) throws ProtocolException {
        for (ConnectionRole role : values()) {
            if (role.code == code) {
                return role;
            }
        }
        throw new ProtocolException(
                ProtocolError.INVALID_HANDSHAKE, "Unknown connection role code: " + code);
    }
}
