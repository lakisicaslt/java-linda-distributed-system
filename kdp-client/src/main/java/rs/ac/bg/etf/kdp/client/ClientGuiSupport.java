package rs.ac.bg.etf.kdp.client;

import rs.ac.bg.etf.kdp.common.domain.JobStatus;

import java.util.UUID;

final class ClientGuiSupport {

    private ClientGuiSupport() {
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

    static UUID parseBatchId(String value) {
        try {
            return UUID.fromString(value.trim());
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Batch ID nije u ispravnom formatu.", exception);
        }
    }

    static String statusLabel(JobStatus status) {
        return switch (status) {
            case READY -> "Čeka raspoređivanje";
            case SCHEDULED -> "Šalje se radnoj stanici";
            case RUNNING -> "Izvršavanje u toku";
            case DONE -> "Uspešno završen";
            case FAILED -> "Prekinut greškom";
            case ABORTED -> "Korisnik je odustao";
        };
    }
}
