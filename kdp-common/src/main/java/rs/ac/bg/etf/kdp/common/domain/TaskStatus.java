package rs.ac.bg.etf.kdp.common.domain;

public enum TaskStatus {
    READY,
    SCHEDULED,
    RUNNING,
    DONE,
    FAILED,
    ABORTED;

    public boolean isTerminal() {
        return this == DONE || this == ABORTED;
    }
}
