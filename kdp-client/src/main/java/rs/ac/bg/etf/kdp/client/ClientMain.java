package rs.ac.bg.etf.kdp.client;

import rs.ac.bg.etf.kdp.common.protocol.BatchAcceptedMessage;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/** Command-line client entry point for submitting a text configuration. */
public final class ClientMain {

    private ClientMain() {
    }

    public static void main(String[] args) {
        if (args.length < 1 || args.length > 3) {
            System.err.println(
                    "Usage: ClientMain <configuration-file> [server-host] [server-port]");
            return;
        }

        String serverHost = args.length >= 2
                ? args[1] : ClientConfig.DEFAULT_SERVER_HOST;
        int serverPort;
        try {
            serverPort = args.length == 3
                    ? Integer.parseInt(args[2]) : ClientConfig.DEFAULT_SERVER_PORT;
        } catch (NumberFormatException exception) {
            System.err.println("Server port must be an integer");
            return;
        }

        try {
            Path configurationFile = Path.of(args[0]);
            List<JobSubmission> jobs = new JobConfigurationParser().parse(configurationFile);
            JobClient client = new JobClient(new ClientConfig(serverHost, serverPort));
            BatchAcceptedMessage accepted = client.submit(jobs);
            System.out.println("Batch ID: " + accepted.getBatchId());
            for (int index = 0; index < accepted.getJobIds().size(); index++) {
                System.out.printf(
                        "Job %d ID: %s%n", index + 1, accepted.getJobIds().get(index));
            }
        } catch (IOException | IllegalArgumentException exception) {
            System.err.println("Could not submit configuration: " + exception.getMessage());
        }
    }
}
