package rs.ac.bg.etf.kdp;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/** Linda implementation that forwards every tuple operation to the central server. */
public final class RemoteLinda implements Linda {

    private static final long serialVersionUID = 1L;

    private final String serverHost;
    private final int serverPort;
    private final UUID batchId;
    private final UUID taskId;

    public RemoteLinda(String serverHost, int serverPort, UUID batchId, UUID taskId) {
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

    @Override
    public void out(String[] tuple) {
        requireOutputTuple(tuple);
        execute(LindaWireProtocol.Operation.OUT, tuple);
    }

    @Override
    public void in(String[] tuple) {
        copyResult(tuple, execute(LindaWireProtocol.Operation.IN, tuple).tuple());
    }

    @Override
    public boolean inp(String[] tuple) {
        return copyIfMatched(tuple, execute(LindaWireProtocol.Operation.INP, tuple));
    }

    @Override
    public void rd(String[] tuple) {
        copyResult(tuple, execute(LindaWireProtocol.Operation.RD, tuple).tuple());
    }

    @Override
    public boolean rdp(String[] tuple) {
        return copyIfMatched(tuple, execute(LindaWireProtocol.Operation.RDP, tuple));
    }

    @Override
    public void eval(String name, Runnable thread) {
        Objects.requireNonNull(name, "eval name must not be null");
        Objects.requireNonNull(thread, "eval runnable must not be null");
        if (name.isBlank()) {
            throw new IllegalArgumentException("eval name must not be blank");
        }
        if (!(thread instanceof Serializable)) {
            throw new LindaException("Eval runnable must implement Serializable");
        }
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
                output.writeObject(thread);
            }
            LindaWireProtocol.eval(
                    serverHost, serverPort, batchId, taskId, name.trim(), bytes.toByteArray());
        } catch (IOException exception) {
            throw new LindaException("Could not submit Linda eval task", exception);
        }
    }

    private LindaWireProtocol.Result execute(
            LindaWireProtocol.Operation operation,
            String[] tuple) {
        Objects.requireNonNull(tuple, "tuple template must not be null");
        try {
            return LindaWireProtocol.execute(
                    serverHost, serverPort, batchId, taskId, operation, tuple.clone());
        } catch (IOException exception) {
            throw new LindaException("Linda operation failed", exception);
        }
    }

    private static boolean copyIfMatched(
            String[] destination,
            LindaWireProtocol.Result result) {
        if (!result.matched()) {
            return false;
        }
        copyResult(destination, result.tuple());
        return true;
    }

    private static void copyResult(String[] destination, String[] result) {
        if (result == null || result.length != destination.length) {
            throw new LindaException("Server returned an invalid Linda tuple");
        }
        System.arraycopy(result, 0, destination, 0, result.length);
    }

    private static void requireOutputTuple(String[] tuple) {
        Objects.requireNonNull(tuple, "tuple must not be null");
        for (String field : tuple) {
            Objects.requireNonNull(field, "tuple fields must not be null");
        }
    }
}
