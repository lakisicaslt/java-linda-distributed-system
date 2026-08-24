package rs.ac.bg.etf.kdp.common.protocol;

import java.io.IOException;
import java.util.Objects;

public final class ProtocolException extends IOException {

    private final ProtocolError error;

    public ProtocolException(ProtocolError error, String message) {
        super(message);
        this.error = Objects.requireNonNull(error, "protocol error must not be null");
    }

    public ProtocolException(ProtocolError error, String message, Throwable cause) {
        super(message, cause);
        this.error = Objects.requireNonNull(error, "protocol error must not be null");
    }

    public ProtocolError getError() {
        return error;
    }
}
