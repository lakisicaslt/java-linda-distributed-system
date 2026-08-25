package rs.ac.bg.etf.kdp.common.protocol;

public enum TransferFileKind {
    JOB_ARCHIVE(1),
    INPUT(2),
    OUTPUT(3),
    STANDARD_OUTPUT(4),
    STANDARD_ERROR(5);

    private final int code;

    TransferFileKind(int code) {
        this.code = code;
    }

    int getCode() {
        return code;
    }

    static TransferFileKind fromCode(int code) {
        for (TransferFileKind kind : values()) {
            if (kind.code == code) {
                return kind;
            }
        }
        throw new IllegalArgumentException("unknown file kind: " + code);
    }
}
