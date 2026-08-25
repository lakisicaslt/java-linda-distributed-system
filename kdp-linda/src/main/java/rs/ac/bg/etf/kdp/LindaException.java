package rs.ac.bg.etf.kdp;

/** Reports a communication or interruption failure during a Linda operation. */
public final class LindaException extends RuntimeException {

    public LindaException(String message) {
        super(message);
    }

    public LindaException(String message, Throwable cause) {
        super(message, cause);
    }
}
