package rs.ac.bg.etf.kdp.server;

import rs.ac.bg.etf.kdp.common.gui.SwingTheme;

import javax.swing.SwingUtilities;

/** Starts the graphical central server application. */
public final class ServerGuiMain {

    private ServerGuiMain() {
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            SwingTheme.install();
            new ServerFrame().setVisible(true);
        });
    }
}
