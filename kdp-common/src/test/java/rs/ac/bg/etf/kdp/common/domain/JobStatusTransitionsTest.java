package rs.ac.bg.etf.kdp.common.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JobStatusTransitionsTest {

    @Test
    void acceptsNormalSuccessfulExecutionFlow() {
        assertTrue(JobStatusTransitions.canTransition(JobStatus.READY, JobStatus.SCHEDULED));
        assertTrue(JobStatusTransitions.canTransition(JobStatus.SCHEDULED, JobStatus.RUNNING));
        assertTrue(JobStatusTransitions.canTransition(JobStatus.RUNNING, JobStatus.DONE));
    }

    @Test
    void failedJobCanBeRetriedOrAborted() {
        assertTrue(JobStatusTransitions.canTransition(JobStatus.FAILED, JobStatus.READY));
        assertTrue(JobStatusTransitions.canTransition(JobStatus.FAILED, JobStatus.ABORTED));
    }

    @Test
    void terminalStatusCannotChange() {
        for (JobStatus next : JobStatus.values()) {
            assertFalse(JobStatusTransitions.canTransition(JobStatus.DONE, next));
            assertFalse(JobStatusTransitions.canTransition(JobStatus.ABORTED, next));
        }
    }

    @Test
    void rejectsSkippedExecutionSteps() {
        assertFalse(JobStatusTransitions.canTransition(JobStatus.READY, JobStatus.RUNNING));
        assertFalse(JobStatusTransitions.canTransition(JobStatus.READY, JobStatus.DONE));
        assertFalse(JobStatusTransitions.canTransition(JobStatus.SCHEDULED, JobStatus.READY));
        assertFalse(JobStatusTransitions.canTransition(JobStatus.SCHEDULED, JobStatus.DONE));
    }

    @Test
    void requireValidTransitionThrowsForInvalidTransition() {
        assertDoesNotThrow(() -> JobStatusTransitions.requireValidTransition(
                JobStatus.RUNNING, JobStatus.DONE));

        assertThrows(IllegalStateException.class, () -> JobStatusTransitions.requireValidTransition(
                JobStatus.DONE, JobStatus.READY));
    }
}
