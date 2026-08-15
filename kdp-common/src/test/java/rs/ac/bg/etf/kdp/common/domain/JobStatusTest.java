package rs.ac.bg.etf.kdp.common.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JobStatusTest {

    @Test
    void doneAndAbortedAreTerminalStatuses() {
        assertTrue(JobStatus.DONE.isTerminal());
        assertTrue(JobStatus.ABORTED.isTerminal());
    }

    @Test
    void activeAndRetryableStatusesAreNotTerminal() {
        assertFalse(JobStatus.READY.isTerminal());
        assertFalse(JobStatus.SCHEDULED.isTerminal());
        assertFalse(JobStatus.RUNNING.isTerminal());
        assertFalse(JobStatus.FAILED.isTerminal());
    }
}
