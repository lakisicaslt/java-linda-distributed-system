package rs.ac.bg.etf.kdp;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/** Thread-safe in-memory tuple space used by the central server. */
public final class LocalLinda implements Linda {

    private static final long serialVersionUID = 1L;

    private final ReentrantLock lock = new ReentrantLock(true);
    private final Condition tupleAdded = lock.newCondition();
    private final List<String[]> tuples = new ArrayList<>();

    @Override
    public void out(String[] tuple) {
        String[] storedTuple = requireTuple(tuple);
        lock.lock();
        try {
            tuples.add(storedTuple);
            tupleAdded.signalAll();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void in(String[] tuple) {
        copyResult(tuple, waitForMatch(tuple, true));
    }

    @Override
    public boolean inp(String[] tuple) {
        return findImmediately(tuple, true);
    }

    @Override
    public void rd(String[] tuple) {
        copyResult(tuple, waitForMatch(tuple, false));
    }

    @Override
    public boolean rdp(String[] tuple) {
        return findImmediately(tuple, false);
    }

    @Override
    public void eval(String name, Runnable thread) {
        Objects.requireNonNull(name, "eval name must not be null");
        Objects.requireNonNull(thread, "eval runnable must not be null");
        if (name.isBlank()) {
            throw new IllegalArgumentException("eval name must not be blank");
        }
        new Thread(thread, name.trim()).start();
    }

    private String[] waitForMatch(String[] template, boolean remove) {
        requireTemplate(template);
        try {
            lock.lockInterruptibly();
            try {
                int matchIndex;
                while ((matchIndex = findMatch(template)) < 0) {
                    tupleAdded.await();
                }
                String[] match = tuples.get(matchIndex);
                if (remove) {
                    tuples.remove(matchIndex);
                }
                return match.clone();
            } finally {
                lock.unlock();
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new LindaException("Linda operation was interrupted", exception);
        }
    }

    private boolean findImmediately(String[] template, boolean remove) {
        requireTemplate(template);
        lock.lock();
        try {
            int matchIndex = findMatch(template);
            if (matchIndex < 0) {
                return false;
            }
            String[] match = tuples.get(matchIndex);
            if (remove) {
                tuples.remove(matchIndex);
            }
            copyResult(template, match);
            return true;
        } finally {
            lock.unlock();
        }
    }

    private int findMatch(String[] template) {
        for (int tupleIndex = 0; tupleIndex < tuples.size(); tupleIndex++) {
            String[] candidate = tuples.get(tupleIndex);
            if (matches(template, candidate)) {
                return tupleIndex;
            }
        }
        return -1;
    }

    private static boolean matches(String[] template, String[] candidate) {
        if (template.length != candidate.length) {
            return false;
        }
        for (int field = 0; field < template.length; field++) {
            if (template[field] != null && !template[field].equals(candidate[field])) {
                return false;
            }
        }
        return true;
    }

    private static String[] requireTuple(String[] tuple) {
        Objects.requireNonNull(tuple, "tuple must not be null");
        String[] copy = tuple.clone();
        for (String field : copy) {
            Objects.requireNonNull(field, "tuple fields must not be null");
        }
        return copy;
    }

    private static void requireTemplate(String[] template) {
        Objects.requireNonNull(template, "tuple template must not be null");
    }

    private static void copyResult(String[] destination, String[] result) {
        System.arraycopy(result, 0, destination, 0, result.length);
    }
}
