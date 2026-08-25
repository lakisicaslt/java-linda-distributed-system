package rs.ac.bg.etf.kdp.common.domain;

/**
 * Status of a job as defined by the project specification.
 */
public enum JobStatus {
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
