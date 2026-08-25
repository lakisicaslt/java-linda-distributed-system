package rs.ac.bg.etf.kdp.server;

import rs.ac.bg.etf.kdp.common.domain.Job;

/** Reports a worker failure to an available client. */
@FunctionalInterface
public interface JobFailureListener {

    JobFailureListener NONE = job -> true;

    boolean workerFailed(Job job);
}
