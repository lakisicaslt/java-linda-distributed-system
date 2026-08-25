package rs.ac.bg.etf.kdp.worker;

import rs.ac.bg.etf.kdp.common.gui.SwingTheme;
import rs.ac.bg.etf.kdp.common.protocol.ProtocolConstants;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import javax.swing.WindowConstants;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/** Graphical controls and live status for one worker process. */
public final class WorkerFrame extends JFrame {

    private final JTextField hostField = new JTextField("127.0.0.1", 18);
    private final JTextField portField = new JTextField(
            Integer.toString(WorkerConfig.DEFAULT_SERVER_PORT), 7);
    private final JSpinner capacitySpinner = new JSpinner(new SpinnerNumberModel(
            Math.max(1, Runtime.getRuntime().availableProcessors()), 1, 256, 1));
    private final JTextField lindaLibraryField = new JTextField("kdp-linda.jar", 30);
    private final JButton startButton = new JButton("Poveži radnu stanicu");
    private final JButton stopButton = new JButton("Prekini vezu");
    private final JLabel stateLabel = valueLabel("Nije povezana");
    private final JLabel workerIdLabel = valueLabel("-");
    private final JLabel serverLabel = valueLabel("-");
    private final JLabel platformLabel = valueLabel(platformDescription());
    private final JLabel tasksLabel = valueLabel("0 / 0");
    private final Timer refreshTimer = new Timer(1_000, event -> refreshState());

    private volatile WorkerClient worker;
    private volatile WorkerConfig activeConfig;

    public WorkerFrame() {
        super("KDP - Radna stanica");
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        setMinimumSize(new Dimension(690, 560));
        setSize(760, 620);
        setLocationByPlatform(true);
        setContentPane(createContent());
        refreshTimer.setRepeats(true);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent event) {
                refreshTimer.stop();
                WorkerClient current = worker;
                if (current != null) {
                    current.close();
                }
            }
        });
        updateControls(false);
    }

    private JPanel createContent() {
        JPanel content = new JPanel(new BorderLayout(0, 16));
        content.setBorder(BorderFactory.createEmptyBorder(20, 24, 22, 24));
        content.add(createHeader(), BorderLayout.NORTH);

        JPanel center = new JPanel();
        center.setLayout(new javax.swing.BoxLayout(center, javax.swing.BoxLayout.Y_AXIS));
        center.add(createConfigurationPanel());
        center.add(javax.swing.Box.createVerticalStrut(14));
        center.add(createStatusPanel());
        content.add(center, BorderLayout.CENTER);
        return content;
    }

    private JPanel createHeader() {
        JPanel panel = new JPanel(new BorderLayout());
        JLabel title = new JLabel("Radna stanica");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 23f));
        JLabel subtitle = new JLabel(
                "Povežite ovaj računar sa serverom i ponudite mu kapacitet za izvršavanje.");
        subtitle.setForeground(SwingTheme.MUTED_TEXT);
        panel.add(title, BorderLayout.NORTH);
        panel.add(subtitle, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel createConfigurationPanel() {
        JPanel panel = card("Podešavanje veze");
        GridBagConstraints constraints = constraints();
        addField(panel, constraints, 0, "Adresa servera", hostField);
        addField(panel, constraints, 1, "Port", portField);

        constraints.gridx = 0;
        constraints.gridy = 2;
        constraints.weightx = 0;
        JLabel capacityLabel = new JLabel("Paralelnih poslova:", SwingConstants.RIGHT);
        capacityLabel.setLabelFor(capacitySpinner);
        panel.add(capacityLabel, constraints);
        constraints.gridx = 1;
        constraints.weightx = 1;
        constraints.anchor = GridBagConstraints.WEST;
        constraints.fill = GridBagConstraints.NONE;
        panel.add(capacitySpinner, constraints);

        constraints.fill = GridBagConstraints.HORIZONTAL;
        addField(panel, constraints, 3, "Linda biblioteka", lindaLibraryField);
        JButton browseButton = new JButton("Izaberi...");
        browseButton.addActionListener(event -> chooseLindaLibrary());
        constraints.gridx = 2;
        constraints.gridy = 3;
        constraints.weightx = 0;
        panel.add(browseButton, constraints);

        JPanel buttons = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 8, 0));
        buttons.setBackground(SwingTheme.SURFACE);
        startButton.addActionListener(event -> startWorker());
        stopButton.addActionListener(event -> stopWorker());
        SwingTheme.markPrimary(startButton);
        buttons.add(startButton);
        buttons.add(stopButton);
        constraints.gridx = 1;
        constraints.gridy = 4;
        constraints.gridwidth = 2;
        constraints.anchor = GridBagConstraints.WEST;
        panel.add(buttons, constraints);
        return panel;
    }

    private JPanel createStatusPanel() {
        JPanel panel = card("Trenutno stanje");
        GridBagConstraints constraints = constraints();
        addStatusRow(panel, constraints, 0, "Veza", stateLabel);
        addStatusRow(panel, constraints, 1, "ID radne stanice", workerIdLabel);
        addStatusRow(panel, constraints, 2, "Server", serverLabel);
        addStatusRow(panel, constraints, 3, "Platforma", platformLabel);
        addStatusRow(panel, constraints, 4, "Aktivni poslovi", tasksLabel);
        return panel;
    }

    private void startWorker() {
        final WorkerConfig config;
        try {
            config = readConfiguration();
        } catch (IllegalArgumentException exception) {
            showError(exception);
            return;
        }

        startButton.setEnabled(false);
        stateLabel.setText("Povezivanje...");
        stateLabel.setForeground(SwingTheme.PRIMARY);
        new SwingWorker<WorkerClient, Void>() {
            @Override
            protected WorkerClient doInBackground() throws Exception {
                WorkerClient created = new WorkerClient(config);
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
                    worker = get();
                    activeConfig = config;
                    workerIdLabel.setText(config.getWorkerId().toString());
                    serverLabel.setText(config.getServerHost() + ":" + config.getServerPort());
                    stateLabel.setText("Povezana i spremna");
                    stateLabel.setForeground(SwingTheme.SUCCESS);
                    updateControls(true);
                    refreshState();
                    refreshTimer.start();
                } catch (Exception exception) {
                    stateLabel.setText("Povezivanje nije uspelo");
                    stateLabel.setForeground(SwingTheme.DANGER);
                    updateControls(false);
                    showError(exception);
                }
            }
        }.execute();
    }

    private void stopWorker() {
        WorkerClient current = worker;
        if (current == null) {
            return;
        }
        stopButton.setEnabled(false);
        stateLabel.setText("Prekidanje veze...");
        refreshTimer.stop();
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() {
                current.close();
                return null;
            }

            @Override
            protected void done() {
                showDisconnected("Veza je prekinuta");
            }
        }.execute();
    }

    private WorkerConfig readConfiguration() {
        String host = hostField.getText().trim();
        if (host.isEmpty()) {
            throw new IllegalArgumentException("Unesite adresu servera.");
        }
        int port = WorkerGuiSupport.parsePort(portField.getText());
        int capacity = (Integer) capacitySpinner.getValue();
        String libraryText = lindaLibraryField.getText().trim();
        if (libraryText.isEmpty()) {
            throw new IllegalArgumentException("Izaberite kdp-linda.jar biblioteku.");
        }
        Path lindaLibrary = Path.of(libraryText).toAbsolutePath().normalize();
        if (!Files.isRegularFile(lindaLibrary)) {
            throw new IllegalArgumentException(
                    "Linda biblioteka ne postoji: " + lindaLibrary);
        }
        return new WorkerConfig(
                host,
                port,
                UUID.randomUUID(),
                capacity,
                ProtocolConstants.CONNECT_TIMEOUT_MILLIS,
                ProtocolConstants.HANDSHAKE_TIMEOUT_MILLIS,
                WorkerConfig.DEFAULT_HEARTBEAT_INTERVAL_MILLIS,
                Path.of("worker-work"),
                lindaLibrary);
    }

    private void refreshState() {
        WorkerClient current = worker;
        WorkerConfig config = activeConfig;
        if (current == null || config == null) {
            return;
        }
        if (!current.isRunning()) {
            refreshTimer.stop();
            showDisconnected("Veza sa serverom je izgubljena");
            return;
        }
        int activeTasks = current.getAssignedTaskIds().size();
        tasksLabel.setText(activeTasks + " / " + config.getCapacity());
    }

    private void showDisconnected(String message) {
        WorkerClient current = worker;
        worker = null;
        activeConfig = null;
        if (current != null) {
            current.close();
        }
        stateLabel.setText(message);
        stateLabel.setForeground(SwingTheme.MUTED_TEXT);
        workerIdLabel.setText("-");
        serverLabel.setText("-");
        tasksLabel.setText("0 / 0");
        updateControls(false);
    }

    private void chooseLindaLibrary() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Izaberite kdp-linda.jar");
        chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            lindaLibraryField.setText(chooser.getSelectedFile().getAbsolutePath());
        }
    }

    private void updateControls(boolean connected) {
        hostField.setEnabled(!connected);
        portField.setEnabled(!connected);
        capacitySpinner.setEnabled(!connected);
        lindaLibraryField.setEnabled(!connected);
        startButton.setEnabled(!connected);
        stopButton.setEnabled(connected);
    }

    private static JPanel card(String title) {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(SwingTheme.SURFACE);
        panel.setBorder(SwingTheme.cardBorder(title));
        panel.setAlignmentX(LEFT_ALIGNMENT);
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 310));
        return panel;
    }

    private static GridBagConstraints constraints() {
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.insets = new Insets(7, 6, 7, 6);
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.anchor = GridBagConstraints.WEST;
        return constraints;
    }

    private static void addField(
            JPanel panel,
            GridBagConstraints constraints,
            int row,
            String text,
            JTextField field) {
        constraints.gridx = 0;
        constraints.gridy = row;
        constraints.gridwidth = 1;
        constraints.weightx = 0;
        JLabel label = new JLabel(text + ":", SwingConstants.RIGHT);
        label.setLabelFor(field);
        panel.add(label, constraints);
        constraints.gridx = 1;
        constraints.weightx = 1;
        panel.add(field, constraints);
    }

    private static void addStatusRow(
            JPanel panel,
            GridBagConstraints constraints,
            int row,
            String text,
            JLabel value) {
        constraints.gridx = 0;
        constraints.gridy = row;
        constraints.weightx = 0;
        panel.add(new JLabel(text + ":", SwingConstants.RIGHT), constraints);
        constraints.gridx = 1;
        constraints.weightx = 1;
        panel.add(value, constraints);
    }

    private static JLabel valueLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(label.getFont().deriveFont(Font.BOLD));
        return label;
    }

    private static String platformDescription() {
        return System.getProperty("os.name", "Nepoznat sistem") + " · Java "
                + System.getProperty("java.version", "nepoznata");
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
                "Radna stanica nije izvršila operaciju",
                JOptionPane.ERROR_MESSAGE);
    }
}
