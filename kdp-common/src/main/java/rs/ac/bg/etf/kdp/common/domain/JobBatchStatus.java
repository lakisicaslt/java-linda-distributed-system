package rs.ac.bg.etf.kdp.common.domain;

/**
 * Status of a submitted group of jobs, derived from the statuses of its members.
 */
public enum JobBatchStatus {
    IN_PROGRESS,
    REQUIRES_ATTENTION,
    DONE,
    ABORTED;

    public boolean isTerminal() {
        return this == DONE || this == ABORTED;
    }
}
