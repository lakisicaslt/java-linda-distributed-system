package rs.ac.bg.etf.kdp.demo;

import rs.ac.bg.etf.kdp.Linda;
import rs.ac.bg.etf.kdp.LindaFactory;

import java.io.Serializable;

/** Serializable part of the eval demonstration. */
public final class EvalTask implements Runnable, Serializable {

    private static final long serialVersionUID = 1L;

    private final String value;

    public EvalTask(String value) {
        this.value = value;
    }

    @Override
    public void run() {
        Linda linda = LindaFactory.getLinda();
        linda.out(new String[]{"eval-result", value, "demo calculation"});
    }
}
