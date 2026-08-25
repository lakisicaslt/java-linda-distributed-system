package rs.ac.bg.etf.kdp.client;

import rs.ac.bg.etf.kdp.common.gui.SwingTheme;

import javax.swing.SwingUtilities;

/** Starts the graphical client application. */
public final class ClientGuiMain {

    private ClientGuiMain() {
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            SwingTheme.install();
            new ClientFrame().setVisible(true);
        });
    }
}
