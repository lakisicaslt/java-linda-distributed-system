package rs.ac.bg.etf.kdp.server;

final class ServerGuiSupport {

    private ServerGuiSupport() {
    }

    static int parsePort(String value) {
        int port;
        try {
            port = Integer.parseInt(value.trim());
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Port mora biti ceo broj.", exception);
        }
        if (port < 1 || port > 65_535) {
            throw new IllegalArgumentException("Port mora biti između 1 i 65535.");
        }
        return port;
    }
}
