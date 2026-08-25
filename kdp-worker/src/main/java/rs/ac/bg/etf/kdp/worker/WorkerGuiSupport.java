package rs.ac.bg.etf.kdp.worker;

final class WorkerGuiSupport {

    private WorkerGuiSupport() {
    }

    static int parsePort(String value) {
        int port;
        try {
            port = Integer.parseInt(value.trim());
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Port servera mora biti ceo broj.", exception);
        }
        if (port < 1 || port > 65_535) {
            throw new IllegalArgumentException("Port servera mora biti između 1 i 65535.");
        }
        return port;
    }
}
