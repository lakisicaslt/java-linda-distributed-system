package rs.ac.bg.etf.kdp.runner;

import java.util.Objects;
import java.util.UUID;

/** Connection data passed to the Linda client inside a task process. */
public final class LindaConnectionInfo {

    private final String serverHost;
    private final int serverPort;
    private final UUID batchId;
    private final UUID taskId;

    public LindaConnectionInfo(
            String serverHost,
            int serverPort,
            UUID batchId,
            UUID taskId) {
        Objects.requireNonNull(serverHost, "server host must not be null");
        if (serverHost.isBlank()) {
            throw new IllegalArgumentException("server host must not be blank");
        }
        if (serverPort < 1 || serverPort > 65_535) {
            throw new IllegalArgumentException("server port must be between 1 and 65535");
        }
        this.serverHost = serverHost.trim();
        this.serverPort = serverPort;
        this.batchId = Objects.requireNonNull(batchId, "batch id must not be null");
        this.taskId = Objects.requireNonNull(taskId, "task id must not be null");
    }

    public String getServerHost() {
        return serverHost;
    }

    public int getServerPort() {
        return serverPort;
    }

    public UUID getBatchId() {
        return batchId;
    }

    public UUID getTaskId() {
        return taskId;
    }
}
