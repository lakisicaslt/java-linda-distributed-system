package rs.ac.bg.etf.kdp.client;

import rs.ac.bg.etf.kdp.Linda;
import rs.ac.bg.etf.kdp.LindaFactory;

import java.io.Serializable;

public final class RecoverableEvalTestRunnable implements Runnable, Serializable {

    private static final long serialVersionUID = 1L;

    @Override
    public void run() {
        Linda linda = LindaFactory.getLinda();
        linda.out(new String[]{"eval-started", "yes"});

        String[] release = {"release-eval"};
        linda.in(release);
        linda.out(new String[]{"eval-result", "retried eval completed"});
    }
}
