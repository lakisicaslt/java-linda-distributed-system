package rs.ac.bg.etf.kdp;

import java.util.UUID;

/** Creates the remote Linda implementation configured by the worker process. */
public final class LindaFactory {

    public static final String SERVER_HOST_PROPERTY = "kdp.linda.server.host";
    public static final String SERVER_PORT_PROPERTY = "kdp.linda.server.port";
    public static final String BATCH_ID_PROPERTY = "kdp.linda.batch.id";
    public static final String TASK_ID_PROPERTY = "kdp.linda.task.id";

    private LindaFactory() {
    }

    public static Linda getLinda() {
        try {
            String host = requiredProperty(SERVER_HOST_PROPERTY);
            int port = Integer.parseInt(requiredProperty(SERVER_PORT_PROPERTY));
            UUID batchId = UUID.fromString(requiredProperty(BATCH_ID_PROPERTY));
            UUID taskId = UUID.fromString(requiredProperty(TASK_ID_PROPERTY));
            return new RemoteLinda(host, port, batchId, taskId);
        } catch (IllegalArgumentException exception) {
            throw new LindaException("Invalid Linda connection properties", exception);
        }
    }

    private static String requiredProperty(String name) {
        String value = System.getProperty(name);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing system property: " + name);
        }
        return value.trim();
    }
}
