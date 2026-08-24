package rs.ac.bg.etf.kdp.common.protocol;

public final class ProtocolConstants {

    public static final int MAGIC = 0x4B445031; // ASCII: KDP1
    public static final short VERSION = 1;
    public static final int HEADER_SIZE = Integer.BYTES
            + Short.BYTES
            + Short.BYTES
            + 2 * Long.BYTES
            + Integer.BYTES;

    public static final int MAX_CONTROL_PAYLOAD_SIZE = 1024 * 1024;
    public static final int FILE_CHUNK_SIZE = 64 * 1024;
    public static final int CONNECT_TIMEOUT_MILLIS = 5_000;
    public static final int HANDSHAKE_TIMEOUT_MILLIS = 5_000;

    private ProtocolConstants() {
    }
}
