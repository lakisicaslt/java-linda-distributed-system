package rs.ac.bg.etf.kdp.client;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertThrows;

class JobClientProtocolTest {

    @Test
    void rejectsHttpResponseAsWrongProtocol() throws Exception {
        try (ServerSocket webServer = new ServerSocket(0)) {
            ExecutorService executor = Executors.newSingleThreadExecutor();
            try {
                Future<?> response = executor.submit(() -> sendHttpResponse(webServer));
                JobClient client = new JobClient(new ClientConfig(
                        "127.0.0.1", webServer.getLocalPort(), 1_000, 1_000));

                assertThrows(IOException.class,
                        () -> client.getBatchStatus(UUID.randomUUID()));

                response.get(1, TimeUnit.SECONDS);
            } finally {
                executor.shutdownNow();
            }
        }
    }

    private static void sendHttpResponse(ServerSocket webServer) {
        try (Socket socket = webServer.accept()) {
            socket.getOutputStream().write((
                    "HTTP/1.1 400 Bad Request\r\n"
                            + "Content-Length: 0\r\n"
                            + "Connection: close\r\n\r\n")
                    .getBytes(StandardCharsets.US_ASCII));
            socket.getOutputStream().flush();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }
}
