package rs.ac.bg.etf.kdp.common.domain;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Defines the only state transitions that a job is allowed to make.
 */
public final class JobStatusTransitions {

    private static final Map<JobStatus, Set<JobStatus>> ALLOWED_TRANSITIONS = createTransitions();

    private JobStatusTransitions() {
    }

    public static boolean canTransition(JobStatus current, JobStatus next) {
        Objects.requireNonNull(current, "current status must not be null");
        Objects.requireNonNull(next, "next status must not be null");

        return ALLOWED_TRANSITIONS.get(current).contains(next);
    }

    public static void requireValidTransition(JobStatus current, JobStatus next) {
        if (!canTransition(current, next)) {
            throw new IllegalStateException("Invalid job status transition: " + current + " -> " + next);
        }
    }

    private static Map<JobStatus, Set<JobStatus>> createTransitions() {
        Map<JobStatus, Set<JobStatus>> transitions = new EnumMap<>(JobStatus.class);

        transitions.put(JobStatus.READY, Set.of(JobStatus.SCHEDULED, JobStatus.ABORTED));
        transitions.put(JobStatus.SCHEDULED,
                Set.of(JobStatus.RUNNING, JobStatus.FAILED, JobStatus.ABORTED));
        transitions.put(JobStatus.RUNNING,
                Set.of(JobStatus.DONE, JobStatus.FAILED, JobStatus.ABORTED));
        transitions.put(JobStatus.FAILED, Set.of(JobStatus.READY, JobStatus.ABORTED));
        transitions.put(JobStatus.DONE, Set.of());
        transitions.put(JobStatus.ABORTED, Set.of());

        return Map.copyOf(transitions);
    }
}
