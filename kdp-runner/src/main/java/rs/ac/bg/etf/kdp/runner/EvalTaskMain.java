package rs.ac.bg.etf.kdp.runner;

import java.io.ObjectInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.FutureTask;

/** Child JVM entry point that deserializes and executes one Linda eval task. */
public final class EvalTaskMain {

    private EvalTaskMain() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 3) {
            throw new IllegalArgumentException(
                    "Eval task expects a serialized runnable, a name and a Linda library path");
        }
        Object object;
        try (ObjectInputStream input = new ObjectInputStream(
                Files.newInputStream(Path.of(args[0])))) {
            object = input.readObject();
        }
        if (!(object instanceof Runnable runnable)) {
            throw new IllegalArgumentException("Serialized eval object is not Runnable");
        }
        FutureTask<Void> task = new FutureTask<>(runnable, null);
        Thread thread = new Thread(task, args[1]);
        thread.start();
        task.get();
    }
}
