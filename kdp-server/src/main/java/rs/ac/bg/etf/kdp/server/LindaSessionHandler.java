package rs.ac.bg.etf.kdp.server;

import rs.ac.bg.etf.kdp.LindaException;
import rs.ac.bg.etf.kdp.LocalLinda;
import rs.ac.bg.etf.kdp.common.domain.Job;
import rs.ac.bg.etf.kdp.common.protocol.ConnectionRole;
import rs.ac.bg.etf.kdp.common.protocol.LindaEvalRequestMessage;
import rs.ac.bg.etf.kdp.common.protocol.LindaProtocolCodec;
import rs.ac.bg.etf.kdp.common.protocol.LindaRequestMessage;
import rs.ac.bg.etf.kdp.common.protocol.LindaResultMessage;
import rs.ac.bg.etf.kdp.common.protocol.MessageType;
import rs.ac.bg.etf.kdp.common.protocol.ProtocolError;
import rs.ac.bg.etf.kdp.common.protocol.ProtocolException;
import rs.ac.bg.etf.kdp.common.protocol.ProtocolFrame;

import java.io.IOException;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/** Handles remote operations on tuple spaces shared by jobs in one batch. */
public final class LindaSessionHandler implements ServerMessageHandler, AutoCloseable {

    private final ServerJobStore jobStore;
    private final TaskScheduler scheduler;
    private final LindaTupleSpaceRegistry spaceRegistry;
    private final LindaProtocolCodec codec = new LindaProtocolCodec();
    private final ExecutorService blockingOperations;
    private final Map<ServerConnection, Set<PendingOperation>> pending =
            new IdentityHashMap<>();

    public LindaSessionHandler(ServerJobStore jobStore, TaskScheduler scheduler) {
        this(jobStore, scheduler, new LindaTupleSpaceRegistry());
    }

    LindaSessionHandler(
            ServerJobStore jobStore,
            TaskScheduler scheduler,
            LindaTupleSpaceRegistry spaceRegistry) {
        this.jobStore = Objects.requireNonNull(jobStore, "job store must not be null");
        this.scheduler = Objects.requireNonNull(scheduler, "task scheduler must not be null");
        this.spaceRegistry = Objects.requireNonNull(
                spaceRegistry, "Linda space registry must not be null");
        AtomicInteger sequence = new AtomicInteger();
        blockingOperations = Executors.newCachedThreadPool(task -> {
            Thread thread = new Thread(
                    task, "kdp-linda-blocking-" + sequence.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        });
    }

    @Override
    public synchronized void onConnected(ServerConnection connection) throws ProtocolException {
        if (connection.getRole() != ConnectionRole.LINDA) {
            throw new ProtocolException(
                    ProtocolError.ROLE_VIOLATION,
                    "Linda session handler only accepts Linda connections");
        }
        pending.put(connection, new HashSet<>());
    }

    @Override
    public void onMessage(ServerConnection connection, ProtocolFrame frame) throws IOException {
        if (frame.getType() == MessageType.LINDA_EVAL) {
            submitEval(connection, frame);
            return;
        }
        LindaRequestMessage request = codec.decodeRequest(frame.getPayload());
        validateContext(request);
        LocalLinda space = spaceRegistry.getOrCreate(request.getBatchId());
        String[] tuple = request.getTuple();

        if (frame.getType() == MessageType.LINDA_OUT) {
            requireOutputTuple(tuple);
            space.out(tuple);
            sendResult(connection, frame, true, tuple);
        } else if (frame.getType() == MessageType.LINDA_INP) {
            boolean matched = space.inp(tuple);
            sendResult(connection, frame, matched, matched ? tuple : null);
        } else if (frame.getType() == MessageType.LINDA_RDP) {
            boolean matched = space.rdp(tuple);
            sendResult(connection, frame, matched, matched ? tuple : null);
        } else if (frame.getType() == MessageType.LINDA_IN
                || frame.getType() == MessageType.LINDA_RD) {
            startBlockingOperation(connection, frame, space, tuple);
        } else {
            throw new ProtocolException(
                    ProtocolError.INVALID_MESSAGE_PAYLOAD,
                    "Unexpected Linda message: " + frame.getType());
        }
    }

    private void submitEval(ServerConnection connection, ProtocolFrame frame) throws IOException {
        LindaEvalRequestMessage request = codec.decodeEvalRequest(frame.getPayload());
        Job job = jobStore.findJobForTask(
                request.getBatchId(), request.getParentTaskId()).orElseThrow(() ->
                new ProtocolException(
                        ProtocolError.INVALID_MESSAGE_PAYLOAD,
                        "Linda task does not belong to the requested batch"));
        try {
            scheduler.submitEval(
                    job,
                    request.getParentTaskId(),
                    request.getName(),
                    request.getSerializedRunnable());
        } catch (IllegalArgumentException | IllegalStateException exception) {
            throw new ProtocolException(
                    ProtocolError.INVALID_MESSAGE_PAYLOAD,
                    exception.getMessage(),
                    exception);
        }
        sendResult(connection, frame, true, new String[0]);
    }

    @Override
    public void onDisconnected(ServerConnection connection) {
        Set<PendingOperation> operations;
        synchronized (this) {
            operations = pending.remove(connection);
        }
        if (operations != null) {
            operations.forEach(PendingOperation::cancel);
        }
    }

    @Override
    public synchronized void close() {
        pending.values().forEach(operations ->
                operations.forEach(PendingOperation::cancel));
        pending.clear();
        blockingOperations.shutdownNow();
    }

    private void validateContext(LindaRequestMessage request) throws ProtocolException {
        if (jobStore.findJobForTask(request.getBatchId(), request.getTaskId()).isEmpty()) {
            throw new ProtocolException(
                    ProtocolError.INVALID_MESSAGE_PAYLOAD,
                    "Linda task does not belong to the requested batch");
        }
    }

    private void startBlockingOperation(
            ServerConnection connection,
            ProtocolFrame frame,
            LocalLinda space,
            String[] tuple) throws ProtocolException {
        PendingOperation operation = new PendingOperation(connection, frame, space, tuple);
        synchronized (this) {
            requireSession(connection).add(operation);
        }
        blockingOperations.execute(operation);
    }

    private void sendResult(
            ServerConnection connection,
            ProtocolFrame request,
            boolean matched,
            String[] tuple) throws IOException {
        connection.send(request.reply(
                MessageType.LINDA_RESULT,
                codec.encodeResult(new LindaResultMessage(matched, tuple))));
    }

    private synchronized void completed(
            ServerConnection connection,
            PendingOperation operation) {
        Set<PendingOperation> operations = pending.get(connection);
        if (operations != null) {
            operations.remove(operation);
        }
    }

    private Set<PendingOperation> requireSession(ServerConnection connection)
            throws ProtocolException {
        Set<PendingOperation> operations = pending.get(connection);
        if (operations == null) {
            throw new ProtocolException(
                    ProtocolError.INVALID_MESSAGE_PAYLOAD,
                    "Linda connection is not registered in the session handler");
        }
        return operations;
    }

    private static void requireOutputTuple(String[] tuple) throws ProtocolException {
        for (String field : tuple) {
            if (field == null) {
                throw new ProtocolException(
                        ProtocolError.INVALID_MESSAGE_PAYLOAD,
                        "LINDA_OUT tuple must not contain null fields");
            }
        }
    }

    private final class PendingOperation implements Runnable {

        private final ServerConnection connection;
        private final ProtocolFrame frame;
        private final LocalLinda space;
        private final String[] tuple;
        private volatile Thread thread;
        private volatile boolean cancelled;

        private PendingOperation(
                ServerConnection connection,
                ProtocolFrame frame,
                LocalLinda space,
                String[] tuple) {
            this.connection = connection;
            this.frame = frame;
            this.space = space;
            this.tuple = tuple;
        }

        @Override
        public void run() {
            thread = Thread.currentThread();
            try {
                if (cancelled) {
                    return;
                }
                if (frame.getType() == MessageType.LINDA_IN) {
                    space.in(tuple);
                } else {
                    space.rd(tuple);
                }
                if (!cancelled) {
                    sendResult(connection, frame, true, tuple);
                }
            } catch (LindaException exception) {
                if (!cancelled) {
                    throw exception;
                }
            } catch (IOException ignored) {
                // The connection loop performs the normal session cleanup.
            } finally {
                thread = null;
                completed(connection, this);
            }
        }

        private void cancel() {
            cancelled = true;
            Thread runningThread = thread;
            if (runningThread != null) {
                runningThread.interrupt();
            }
        }
    }
}
