package rs.ac.bg.etf.kdp.server;

import rs.ac.bg.etf.kdp.common.domain.Job;

/** Receives the current state whenever a server-side job changes status. */
@FunctionalInterface
interface JobStatusListener {

    JobStatusListener NONE = (job, workerHostname) -> {
    };

    void statusChanged(Job job, String workerHostname);
}
