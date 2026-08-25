package rs.ac.bg.etf.kdp.worker;

import rs.ac.bg.etf.kdp.common.protocol.TaskAssignmentMessage;

@FunctionalInterface
public interface WorkerTaskHandler {

    void onTaskAssigned(TaskAssignmentMessage assignment);
}
