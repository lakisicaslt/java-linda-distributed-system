package rs.ac.bg.etf.kdp.common.domain;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class TaskStatusTransitions {

    private static final Map<TaskStatus, Set<TaskStatus>> ALLOWED_TRANSITIONS = createTransitions();

    private TaskStatusTransitions() {
    }

    public static boolean canTransition(TaskStatus current, TaskStatus next) {
        Objects.requireNonNull(current, "current status must not be null");
        Objects.requireNonNull(next, "next status must not be null");
        return ALLOWED_TRANSITIONS.get(current).contains(next);
    }

    public static void requireValidTransition(TaskStatus current, TaskStatus next) {
        if (!canTransition(current, next)) {
            throw new IllegalStateException("Invalid task status transition: " + current + " -> " + next);
        }
    }

    private static Map<TaskStatus, Set<TaskStatus>> createTransitions() {
        Map<TaskStatus, Set<TaskStatus>> transitions = new EnumMap<>(TaskStatus.class);
        transitions.put(TaskStatus.READY, Set.of(TaskStatus.SCHEDULED, TaskStatus.ABORTED));
        transitions.put(TaskStatus.SCHEDULED,
                Set.of(TaskStatus.RUNNING, TaskStatus.FAILED, TaskStatus.ABORTED));
        transitions.put(TaskStatus.RUNNING,
                Set.of(TaskStatus.DONE, TaskStatus.FAILED, TaskStatus.ABORTED));
        transitions.put(TaskStatus.FAILED, Set.of(TaskStatus.READY, TaskStatus.ABORTED));
        transitions.put(TaskStatus.DONE, Set.of());
        transitions.put(TaskStatus.ABORTED, Set.of());
        return Map.copyOf(transitions);
    }
}
