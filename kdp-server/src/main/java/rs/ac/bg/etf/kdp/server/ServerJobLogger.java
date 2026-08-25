package rs.ac.bg.etf.kdp.server;

import rs.ac.bg.etf.kdp.common.domain.Job;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Objects;
import java.util.UUID;
import java.util.logging.Formatter;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.logging.StreamHandler;

/** Writes the job information required by the project specification. */
final class ServerJobLogger implements JobStatusListener, AutoCloseable {

    private final Logger logger;
    private final StreamHandler handler;
    private boolean closed;

    ServerJobLogger(Path logFile) throws IOException {
        Objects.requireNonNull(logFile, "log file must not be null");
        Path normalized = logFile.toAbsolutePath().normalize();
        Files.createDirectories(normalized.getParent());
        OutputStream output = Files.newOutputStream(
                normalized,
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND);

        logger = Logger.getLogger("kdp-job-log-" + UUID.randomUUID());
        logger.setUseParentHandlers(false);
        logger.setLevel(Level.INFO);
        handler = new StreamHandler(output, new LineFormatter());
        handler.setLevel(Level.INFO);
        handler.setEncoding("UTF-8");
        logger.addHandler(handler);
    }

    @Override
    public synchronized void statusChanged(Job job, String workerHostname) {
        Objects.requireNonNull(job, "job must not be null");
        if (closed) {
            throw new IllegalStateException("server job logger is closed");
        }
        String worker = workerHostname == null ? "-" : sanitize(workerHostname);
        String finishedAt = job.getFinishedAt() == null
                ? "-"
                : job.getFinishedAt().toString();
        String line = "receivedAt=" + job.getCreatedAt()
                + "\tjobId=" + job.getId()
                + "\tworker=" + worker
                + "\tfinishedAt=" + finishedAt
                + "\tstatus=" + job.getStatus();
        logger.info(line);
        handler.flush();
    }

    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }
        closed = true;
        logger.removeHandler(handler);
        handler.close();
    }

    private static String sanitize(String value) {
        return value.replace('\t', ' ')
                .replace('\r', ' ')
                .replace('\n', ' ');
    }

    private static final class LineFormatter extends Formatter {

        @Override
        public String format(LogRecord record) {
            return record.getMessage() + System.lineSeparator();
        }
    }
}
