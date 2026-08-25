package rs.ac.bg.etf.kdp.server;

import rs.ac.bg.etf.kdp.common.gui.SwingTheme;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import javax.swing.WindowConstants;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Server control window with live worker, job and log information. */
public final class ServerFrame extends JFrame {

    private static final int MAX_LOG_LINES = 200;

    private final JTextField bindHostField = new JTextField("0.0.0.0", 13);
    private final JTextField portField = new JTextField(
            Integer.toString(ServerConfig.DEFAULT_PORT), 6);
    private final JButton startButton = new JButton("Pokreni server");
    private final JButton stopButton = new JButton("Zaustavi server");
    private final JLabel stateLabel = new JLabel("Server nije pokrenut");
    private final DefaultTableModel workerModel = readOnlyModel(
            "Računar", "Sistem", "Java", "Kapacitet", "Aktivno", "Status");
    private final DefaultTableModel jobModel = readOnlyModel(
            "Job ID", "JAR", "Status", "Vreme prijema", "Vreme završetka");
    private final JTextArea logArea = new JTextArea();
    private final Timer refreshTimer = new Timer(1_000, event -> refreshOverview());

    private volatile ServerRuntime runtime;

    public ServerFrame() {
        super("KDP - Centralni server");
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        setMinimumSize(new Dimension(900, 650));
        setSize(1060, 760);
        setLocationByPlatform(true);
        setContentPane(createContent());
        refreshTimer.setRepeats(true);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent event) {
                refreshTimer.stop();
                ServerRuntime current = runtime;
                if (current != null) {
                    current.close();
                }
            }
        });
        updateControls(false);
    }

    private JPanel createContent() {
        JPanel content = new JPanel(new BorderLayout(0, 14));
        content.setBorder(BorderFactory.createEmptyBorder(18, 22, 20, 22));
        content.add(createHeader(), BorderLayout.NORTH);
        content.add(createCenter(), BorderLayout.CENTER);
        return content;
    }

    private JPanel createHeader() {
        JPanel outer = new JPanel(new BorderLayout(0, 12));
        JPanel titlePanel = new JPanel(new BorderLayout());
        JLabel title = new JLabel("Centralni server");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 23f));
        JLabel subtitle = new JLabel(
                "Pratite dostupne radne stanice, poslove i događaje sistema.");
        subtitle.setForeground(SwingTheme.MUTED_TEXT);
        titlePanel.add(title, BorderLayout.NORTH);
        titlePanel.add(subtitle, BorderLayout.SOUTH);
        outer.add(titlePanel, BorderLayout.NORTH);

        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT, 9, 8));
        controls.setBackground(SwingTheme.SURFACE);
        controls.setBorder(SwingTheme.cardBorder("Podešavanje servera"));
        controls.add(new JLabel("Adresa:"));
        controls.add(bindHostField);
        controls.add(new JLabel("Port:"));
        controls.add(portField);
        startButton.addActionListener(event -> startServer());
        stopButton.addActionListener(event -> stopServer());
        SwingTheme.markPrimary(startButton);
        controls.add(startButton);
        controls.add(stopButton);
        stateLabel.setForeground(SwingTheme.MUTED_TEXT);
        controls.add(stateLabel);
        outer.add(controls, BorderLayout.CENTER);
        return outer;
    }

    private JSplitPane createCenter() {
        JTable workerTable = new JTable(workerModel);
        workerTable.setFillsViewportHeight(true);
        JTable jobTable = new JTable(jobModel);
        jobTable.setFillsViewportHeight(true);

        javax.swing.JTabbedPane tabs = new javax.swing.JTabbedPane();
        tabs.addTab("Radne stanice", new JScrollPane(workerTable));
        tabs.addTab("Poslovi", new JScrollPane(jobTable));

        logArea.setEditable(false);
        logArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        JPanel logPanel = new JPanel(new BorderLayout());
        logPanel.setBorder(SwingTheme.cardBorder("Serverski log"));
        logPanel.setMinimumSize(new Dimension(0, 140));
        logPanel.setPreferredSize(new Dimension(0, 190));
        logPanel.add(new JScrollPane(logArea), BorderLayout.CENTER);

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, tabs, logPanel);
        split.setResizeWeight(0.6);
        split.setDividerLocation(350);
        split.setBorder(null);
        return split;
    }

    private void startServer() {
        final ServerConfig config;
        try {
            config = new ServerConfig(
                    bindHostField.getText().trim(),
                    ServerGuiSupport.parsePort(portField.getText()),
                    ServerConfig.DEFAULT_BACKLOG,
                    ServerConfig.DEFAULT_MAX_CONNECTIONS,
                    5_000);
        } catch (IllegalArgumentException exception) {
            showError(exception);
            return;
        }

        startButton.setEnabled(false);
        stateLabel.setText("Server se pokreće...");
        stateLabel.setForeground(SwingTheme.PRIMARY);
        new SwingWorker<ServerRuntime, Void>() {
            @Override
            protected ServerRuntime doInBackground() throws Exception {
                ServerRuntime created = ServerRuntime.create(config, Path.of("server-data"));
                try {
                    created.start();
                    return created;
                } catch (Exception exception) {
                    created.close();
                    throw exception;
                }
            }

            @Override
            protected void done() {
                try {
                    runtime = get();
                    stateLabel.setText("Server radi na portu " + runtime.getLocalPort());
                    stateLabel.setForeground(SwingTheme.SUCCESS);
                    updateControls(true);
                    refreshOverview();
                    refreshTimer.start();
                } catch (Exception exception) {
                    stateLabel.setText("Server nije pokrenut");
                    stateLabel.setForeground(SwingTheme.DANGER);
                    updateControls(false);
                    showError(exception);
                }
            }
        }.execute();
    }

    private void stopServer() {
        ServerRuntime current = runtime;
        if (current == null) {
            return;
        }
        stopButton.setEnabled(false);
        stateLabel.setText("Server se zaustavlja...");
        refreshTimer.stop();
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() {
                current.close();
                return null;
            }

            @Override
            protected void done() {
                runtime = null;
                stateLabel.setText("Server nije pokrenut");
                stateLabel.setForeground(SwingTheme.MUTED_TEXT);
                updateControls(false);
            }
        }.execute();
    }

    private void refreshOverview() {
        ServerRuntime current = runtime;
        if (current == null || !current.isRunning()) {
            return;
        }
        ServerOverview overview = current.getOverview();
        workerModel.setRowCount(0);
        for (ServerOverview.WorkerRow worker : overview.workers()) {
            workerModel.addRow(new Object[]{
                    worker.hostname(),
                    worker.operatingSystem(),
                    worker.javaVersion(),
                    worker.capacity(),
                    worker.activeTasks(),
                    worker.status()
            });
        }
        jobModel.setRowCount(0);
        for (ServerOverview.JobRow job : overview.jobs()) {
            jobModel.addRow(new Object[]{
                    job.id(),
                    job.archive(),
                    job.status(),
                    job.createdAt(),
                    job.finishedAt() == null ? "" : job.finishedAt()
            });
        }
        refreshLog(current.getLogFile());
    }

    private void refreshLog(Path logFile) {
        if (!Files.isRegularFile(logFile)) {
            logArea.setText("");
            return;
        }
        try {
            List<String> lines = Files.readAllLines(logFile, StandardCharsets.UTF_8);
            int first = Math.max(0, lines.size() - MAX_LOG_LINES);
            logArea.setText(String.join(System.lineSeparator(), lines.subList(first, lines.size())));
            logArea.setCaretPosition(logArea.getDocument().getLength());
        } catch (IOException exception) {
            logArea.setText("Log trenutno nije dostupan: " + exception.getMessage());
        }
    }

    private void updateControls(boolean running) {
        bindHostField.setEnabled(!running);
        portField.setEnabled(!running);
        startButton.setEnabled(!running);
        stopButton.setEnabled(running);
    }

    private static DefaultTableModel readOnlyModel(Object... columns) {
        return new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
    }

    private void showError(Throwable throwable) {
        Throwable cause = throwable;
        while (cause.getCause() != null
                && cause instanceof java.util.concurrent.ExecutionException) {
            cause = cause.getCause();
        }
        String message = cause.getMessage();
        if (message == null || message.isBlank()) {
            message = cause.getClass().getSimpleName();
        }
        JOptionPane.showMessageDialog(
                this,
                message,
                "Server nije izvršio operaciju",
                JOptionPane.ERROR_MESSAGE);
    }
}
