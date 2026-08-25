package rs.ac.bg.etf.kdp.client;

import rs.ac.bg.etf.kdp.Linda;
import rs.ac.bg.etf.kdp.LindaFactory;

import java.io.Serializable;

public final class EvalTestRunnable implements Runnable, Serializable {

    private static final long serialVersionUID = 1L;

    private final String value;

    public EvalTestRunnable(String value) {
        this.value = value;
    }

    @Override
    public void run() {
        Linda linda = LindaFactory.getLinda();
        linda.out(new String[]{
                "eval-result", value, Thread.currentThread().getName()});
        System.out.println("eval task completed");
    }
}
