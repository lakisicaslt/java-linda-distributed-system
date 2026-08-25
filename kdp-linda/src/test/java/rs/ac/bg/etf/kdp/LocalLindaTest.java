package rs.ac.bg.etf.kdp;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalLindaTest {

    @Test
    void matchesWildcardsAndKeepsTupleAfterRead() {
        LocalLinda linda = new LocalLinda();
        String[] inserted = {"result", "42"};
        linda.out(inserted);
        inserted[1] = "changed outside";

        String[] readTemplate = {"result", null};
        assertTrue(linda.rdp(readTemplate));
        assertArrayEquals(new String[]{"result", "42"}, readTemplate);

        String[] takeTemplate = {"result", null};
        assertTrue(linda.inp(takeTemplate));
        assertArrayEquals(new String[]{"result", "42"}, takeTemplate);
        assertFalse(linda.rdp(new String[]{"result", null}));
    }

    @Test
    void nonBlockingOperationsLeaveTemplateUnchangedWhenNoTupleMatches() {
        LocalLinda linda = new LocalLinda();
        linda.out(new String[]{"value", "one"});
        String[] template = {"other", null};

        assertFalse(linda.inp(template));
        assertArrayEquals(new String[]{"other", null}, template);
        assertFalse(linda.rdp(new String[]{"value"}));
    }

    @Test
    void blockingTakeWaitsUntilMatchingTupleIsInserted() throws Exception {
        LocalLinda linda = new LocalLinda();
        CountDownLatch started = new CountDownLatch(1);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            String[] template = {"ready", null};
            Future<?> waitingTake = executor.submit(() -> {
                started.countDown();
                linda.in(template);
            });

            assertTrue(started.await(1, TimeUnit.SECONDS));
            assertFalse(waitingTake.isDone());
            linda.out(new String[]{"ready", "now"});
            waitingTake.get(1, TimeUnit.SECONDS);

            assertArrayEquals(new String[]{"ready", "now"}, template);
            assertFalse(linda.rdp(new String[]{"ready", null}));
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void rejectsNullTupleAndNullOutputFields() {
        LocalLinda linda = new LocalLinda();

        assertThrows(NullPointerException.class, () -> linda.out(null));
        assertThrows(NullPointerException.class,
                () -> linda.out(new String[]{"value", null}));
        assertThrows(NullPointerException.class, () -> linda.in(null));
    }
}
