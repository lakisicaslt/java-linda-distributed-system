package rs.ac.bg.etf.kdp.worker;

import rs.ac.bg.etf.kdp.common.gui.SwingTheme;

import javax.swing.SwingUtilities;

/** Starts a worker with its graphical interface. */
public final class WorkerGuiMain {

    private WorkerGuiMain() {
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            SwingTheme.install();
            new WorkerFrame().setVisible(true);
        });
    }
}
