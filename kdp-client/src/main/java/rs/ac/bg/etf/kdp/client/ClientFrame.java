package rs.ac.bg.etf.kdp.client;

import rs.ac.bg.etf.kdp.common.domain.JobStatus;
import rs.ac.bg.etf.kdp.common.gui.SwingTheme;
import rs.ac.bg.etf.kdp.common.protocol.BatchAcceptedMessage;
import rs.ac.bg.etf.kdp.common.protocol.BatchResultMessage;
import rs.ac.bg.etf.kdp.common.protocol.BatchStatusMessage;
import rs.ac.bg.etf.kdp.common.protocol.JobStatusSnapshot;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import javax.swing.WindowConstants;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;

/** Graphical client for submitting jobs, checking status and downloading results. */
public final class ClientFrame extends JFrame {

    private final JTextField hostField = new JTextField(ClientConfig.DEFAULT_SERVER_HOST, 14);
    private final JTextField portField = new JTextField(
            Integer.toString(ClientConfig.DEFAULT_SERVER_PORT), 6);
    private final JTextField configurationField = new JTextField(35);
    private final JTextField batchIdField = new JTextField(36);
    private final JTextField destinationField = new JTextField(30);
    private final JLabel connectionHint = new JLabel("Unesite adresu centralnog servera.");
    private final JLabel submissionHint = new JLabel("Dodajte bar jedan posao kroz formular.");
    private final JLabel statusHint = new JLabel("Nakon slanja ovde možete pratiti svaki posao.");
    private final JLabel resultHint = new JLabel("Rezultati se mogu preuzeti kada su poslovi završeni.");
    private final JButton submitButton = new JButton("Pošalji poslove");
    private final JButton refreshButton = new JButton("Osveži status");
    private final JButton retryButton = new JButton("Ponovi izabrani posao");
    private final JButton abortButton = new JButton("Odustani od izabranog posla");
    private final JButton downloadButton = new JButton("Preuzmi rezultate");
    private final DefaultTableModel statusModel = new DefaultTableModel(
            new Object[]{"Posao", "Status", "Detalji"}, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };
    private final JTable statusTable = new JTable(statusModel);
    private final JTabbedPane submissionTabs = new JTabbedPane();
    private final DefaultListModel<String> formJobModel = new DefaultListModel<>();
    private final JList<String> formJobList = new JList<>(formJobModel);
    private final List<JobFormData> formJobs = new ArrayList<>();
    private final Timer refreshTimer;

    private volatile JobFailureMonitor failureMonitor;
    private List<JobStatusSnapshot> currentStatuses = List.of();
    private boolean statusRequestRunning;

    public ClientFrame() {
        super("KDP - Korisnički program");
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        setMinimumSize(new Dimension(860, 680));
        setSize(940, 760);
        setLocationByPlatform(true);
        setContentPane(createContent());

        refreshTimer = new Timer(3_000, event -> refreshStatus(false));
        refreshTimer.setRepeats(true);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent event) {
                refreshTimer.stop();
                closeFailureMonitor();
            }
        });
        updateActionButtons();
    }

    private JPanel createContent() {
        JPanel content = new JPanel(new BorderLayout(0, 14));
        content.setBorder(BorderFactory.createEmptyBorder(18, 22, 20, 22));
        content.add(createHeader(), BorderLayout.NORTH);

        JPanel steps = new JPanel();
        steps.setLayout(new javax.swing.BoxLayout(steps, javax.swing.BoxLayout.Y_AXIS));
        steps.add(createConnectionPanel());
        steps.add(javax.swing.Box.createVerticalStrut(12));
        steps.add(createSubmissionPanel());
        steps.add(javax.swing.Box.createVerticalStrut(12));
        steps.add(createStatusPanel());
        steps.add(javax.swing.Box.createVerticalStrut(12));
        steps.add(createResultPanel());
        content.add(new JScrollPane(
                steps,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER), BorderLayout.CENTER);
        return content;
    }

    private JPanel createHeader() {
        JPanel panel = new JPanel(new BorderLayout());
        JLabel title = new JLabel("Distribuirano izvršavanje Java poslova");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 23f));
        JLabel subtitle = new JLabel(
                "Pošaljite posao, pratite izvršavanje i sačuvajte rezultate na jednom mestu.");
        subtitle.setForeground(SwingTheme.MUTED_TEXT);
        panel.add(title, BorderLayout.NORTH);
        panel.add(subtitle, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel createConnectionPanel() {
        JPanel panel = card("1. Povezivanje sa serverom");
        GridBagConstraints constraints = constraints();
        addField(panel, constraints, 0, "Adresa servera", hostField);
        addField(panel, constraints, 1, "Port", portField);
        constraints.gridx = 0;
        constraints.gridy = 2;
        constraints.gridwidth = 2;
        connectionHint.setForeground(SwingTheme.MUTED_TEXT);
        panel.add(connectionHint, constraints);
        return panel;
    }

    private JPanel createSubmissionPanel() {
        JPanel panel = card("2. Slanje novih poslova");
        GridBagConstraints constraints = constraints();
        submissionTabs.addTab("Unos kroz formular", createFormSubmissionTab());
        submissionTabs.addTab("Tekstualna konfiguracija", createConfigurationTab());
        submissionTabs.addChangeListener(event -> submissionHint.setText(
                submissionTabs.getSelectedIndex() == 0
                        ? "Dodajte bar jedan posao kroz formular."
                        : "Izaberite postojeću .properties konfiguraciju."));
        constraints.gridx = 0;
        constraints.gridy = 0;
        constraints.gridwidth = 3;
        constraints.weightx = 1;
        constraints.fill = GridBagConstraints.BOTH;
        panel.add(submissionTabs, constraints);

        submitButton.addActionListener(event -> submitJobs());
        SwingTheme.markPrimary(submitButton);
        constraints.gridx = 1;
        constraints.gridy = 1;
        constraints.gridwidth = 1;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.anchor = GridBagConstraints.WEST;
        panel.add(submitButton, constraints);

        constraints.gridx = 0;
        constraints.gridy = 2;
        constraints.gridwidth = 3;
        submissionHint.setForeground(SwingTheme.MUTED_TEXT);
        panel.add(submissionHint, constraints);
        return panel;
    }

    private JPanel createFormSubmissionTab() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        formJobList.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
        JScrollPane scroll = new JScrollPane(formJobList);
        scroll.setPreferredSize(new Dimension(680, 100));
        panel.add(scroll, BorderLayout.CENTER);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        JButton addButton = new JButton("Dodaj posao...");
        addButton.addActionListener(event -> addFormJob());
        JButton editButton = new JButton("Izmeni izabrani...");
        editButton.addActionListener(event -> editFormJob());
        JButton removeButton = new JButton("Ukloni izabrani");
        removeButton.addActionListener(event -> removeFormJob());
        buttons.add(addButton);
        buttons.add(editButton);
        buttons.add(removeButton);
        panel.add(buttons, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel createConfigurationTab() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(10, 8, 10, 8));
        GridBagConstraints constraints = constraints();
        addField(panel, constraints, 0, "Konfiguraciona datoteka", configurationField);
        JButton browseButton = new JButton("Izaberi...");
        browseButton.addActionListener(event -> chooseConfiguration());
        constraints.gridx = 2;
        constraints.gridy = 0;
        constraints.weightx = 0;
        panel.add(browseButton, constraints);
        return panel;
    }

    private JPanel createStatusPanel() {
        JPanel panel = card("3. Praćenje poslova");
        GridBagConstraints constraints = constraints();
        addField(panel, constraints, 0, "Batch ID", batchIdField);

        refreshButton.addActionListener(event -> refreshStatus(true));
        constraints.gridx = 2;
        constraints.gridy = 0;
        constraints.weightx = 0;
        panel.add(refreshButton, constraints);

        statusTable.setFillsViewportHeight(true);
        statusTable.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
        statusTable.getSelectionModel().addListSelectionListener(event -> updateActionButtons());
        JScrollPane tableScroll = new JScrollPane(statusTable);
        tableScroll.setPreferredSize(new Dimension(760, 155));
        constraints.gridx = 0;
        constraints.gridy = 1;
        constraints.gridwidth = 3;
        constraints.fill = GridBagConstraints.BOTH;
        constraints.weighty = 1;
        panel.add(tableScroll, constraints);

        JPanel decisions = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        retryButton.addActionListener(event -> decideForSelectedJob(true));
        abortButton.addActionListener(event -> decideForSelectedJob(false));
        decisions.add(retryButton);
        decisions.add(abortButton);
        constraints.gridy = 2;
        constraints.weighty = 0;
        panel.add(decisions, constraints);

        constraints.gridy = 3;
        statusHint.setForeground(SwingTheme.MUTED_TEXT);
        panel.add(statusHint, constraints);
        return panel;
    }

    private JPanel createResultPanel() {
        JPanel panel = card("4. Preuzimanje rezultata");
        GridBagConstraints constraints = constraints();
        addField(panel, constraints, 0, "Direktorijum za rezultate", destinationField);

        JButton browseButton = new JButton("Izaberi...");
        browseButton.addActionListener(event -> chooseDestination());
        constraints.gridx = 2;
        constraints.gridy = 0;
        constraints.weightx = 0;
        panel.add(browseButton, constraints);

        downloadButton.addActionListener(event -> downloadResults());
        SwingTheme.markPrimary(downloadButton);
        constraints.gridx = 1;
        constraints.gridy = 1;
        constraints.anchor = GridBagConstraints.WEST;
        panel.add(downloadButton, constraints);

        constraints.gridx = 0;
        constraints.gridy = 2;
        constraints.gridwidth = 3;
        resultHint.setForeground(SwingTheme.MUTED_TEXT);
        panel.add(resultHint, constraints);
        return panel;
    }

    private static JPanel card(String title) {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(SwingTheme.SURFACE);
        panel.setBorder(SwingTheme.cardBorder(title));
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 360));
        return panel;
    }

    private static GridBagConstraints constraints() {
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.insets = new Insets(5, 5, 5, 5);
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.anchor = GridBagConstraints.WEST;
        return constraints;
    }

    private static void addField(
            JPanel panel,
            GridBagConstraints constraints,
            int row,
            String labelText,
            JTextField field) {
        constraints.gridx = 0;
        constraints.gridy = row;
        constraints.gridwidth = 1;
        constraints.weightx = 0;
        JLabel label = new JLabel(labelText + ":", SwingConstants.RIGHT);
        panel.add(label, constraints);

        constraints.gridx = 1;
        constraints.weightx = 1;
        panel.add(field, constraints);
        label.setLabelFor(field);
    }

    private void chooseConfiguration() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Izaberite konfiguracionu datoteku");
        chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            configurationField.setText(chooser.getSelectedFile().getAbsolutePath());
        }
    }

    private void addFormJob() {
        JobEditorDialog.show(this, null).ifPresent(job -> {
            formJobs.add(job);
            formJobModel.addElement((formJobs.size()) + ". " + job.summary());
            formJobList.setSelectedIndex(formJobs.size() - 1);
            submissionHint.setText("Spremno je " + formJobs.size() + " posao/la za slanje.");
            submissionHint.setForeground(SwingTheme.MUTED_TEXT);
        });
    }

    private void editFormJob() {
        int selected = formJobList.getSelectedIndex();
        if (selected < 0) {
            showError(new IllegalArgumentException("Izaberite posao koji želite da izmenite."));
            return;
        }
        JobEditorDialog.show(this, formJobs.get(selected)).ifPresent(job -> {
            formJobs.set(selected, job);
            rebuildFormJobModel(selected);
        });
    }

    private void removeFormJob() {
        int selected = formJobList.getSelectedIndex();
        if (selected < 0) {
            showError(new IllegalArgumentException("Izaberite posao koji želite da uklonite."));
            return;
        }
        formJobs.remove(selected);
        rebuildFormJobModel(Math.min(selected, formJobs.size() - 1));
        submissionHint.setText(formJobs.isEmpty()
                ? "Dodajte bar jedan posao kroz formular."
                : "Spremno je " + formJobs.size() + " posao/la za slanje.");
    }

    private void rebuildFormJobModel(int selectedIndex) {
        formJobModel.clear();
        for (int index = 0; index < formJobs.size(); index++) {
            formJobModel.addElement((index + 1) + ". " + formJobs.get(index).summary());
        }
        if (selectedIndex >= 0) {
            formJobList.setSelectedIndex(selectedIndex);
        }
    }

    private void chooseDestination() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Izaberite direktorijum za rezultate");
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            destinationField.setText(chooser.getSelectedFile().getAbsolutePath());
        }
    }

    private void submitJobs() {
        final JobClient client;
        final Callable<List<JobSubmission>> submissionLoader;
        try {
            client = createClient();
            if (submissionTabs.getSelectedIndex() == 0) {
                if (formJobs.isEmpty()) {
                    throw new IllegalArgumentException("Dodajte bar jedan posao kroz formular.");
                }
                List<JobFormData> jobs = List.copyOf(formJobs);
                submissionLoader = () -> jobs.stream()
                        .map(JobFormData::toSubmission)
                        .toList();
            } else {
                String path = configurationField.getText().trim();
                if (path.isEmpty()) {
                    throw new IllegalArgumentException("Izaberite konfiguracionu datoteku.");
                }
                Path configuration = Path.of(path);
                submissionLoader = () -> new JobConfigurationParser().parse(configuration);
            }
        } catch (IllegalArgumentException exception) {
            showError(exception);
            return;
        }

        setSubmissionBusy(true, "Proveravam poslove i šaljem datoteke...");
        new SwingWorker<BatchAcceptedMessage, Void>() {
            @Override
            protected BatchAcceptedMessage doInBackground() throws Exception {
                return client.submit(submissionLoader.call());
            }

            @Override
            protected void done() {
                setSubmissionBusy(false, "Poslovi su spremni za slanje.");
                try {
                    BatchAcceptedMessage accepted = get();
                    batchIdField.setText(accepted.getBatchId().toString());
                    submissionHint.setText(
                            "Server je prihvatio " + accepted.getJobIds().size() + " posao/la.");
                    submissionHint.setForeground(SwingTheme.SUCCESS);
                    refreshStatus(true);
                    startFailureMonitor(accepted.getBatchId());
                } catch (Exception exception) {
                    showError(exception);
                }
            }
        }.execute();
    }

    private void refreshStatus(boolean showErrors) {
        if (statusRequestRunning) {
            return;
        }
        final JobClient client;
        final UUID batchId;
        try {
            client = createClient();
            batchId = ClientGuiSupport.parseBatchId(batchIdField.getText());
        } catch (IllegalArgumentException exception) {
            if (showErrors) {
                showError(exception);
            }
            return;
        }

        statusRequestRunning = true;
        refreshButton.setEnabled(false);
        statusHint.setText("Učitavam trenutni status...");
        new SwingWorker<BatchStatusMessage, Void>() {
            @Override
            protected BatchStatusMessage doInBackground() throws Exception {
                return client.getBatchStatus(batchId);
            }

            @Override
            protected void done() {
                statusRequestRunning = false;
                refreshButton.setEnabled(true);
                try {
                    BatchStatusMessage status = get();
                    showStatus(status);
                    boolean hasActiveJob = status.getJobs().stream()
                            .anyMatch(job -> !job.getStatus().isTerminal());
                    JobFailureMonitor currentMonitor = failureMonitor;
                    if (hasActiveJob && (currentMonitor == null
                            || !currentMonitor.getBatchId().equals(batchId))) {
                        startFailureMonitor(batchId);
                    }
                    if (!refreshTimer.isRunning()) {
                        refreshTimer.start();
                    }
                } catch (Exception exception) {
                    statusHint.setText("Status trenutno nije dostupan.");
                    statusHint.setForeground(SwingTheme.DANGER);
                    if (showErrors) {
                        showError(exception);
                    }
                }
            }
        }.execute();
    }

    private void showStatus(BatchStatusMessage status) {
        statusModel.setRowCount(0);
        currentStatuses = status.getJobs();
        boolean allTerminal = true;
        for (JobStatusSnapshot job : status.getJobs()) {
            String details = job.getFailureReason().orElse("");
            statusModel.addRow(new Object[]{
                    job.getJobId(),
                    ClientGuiSupport.statusLabel(job.getStatus()),
                    details
            });
            allTerminal &= job.getStatus().isTerminal();
        }
        statusHint.setText(allTerminal
                ? "Svi poslovi su završeni. Rezultati su spremni za preuzimanje."
                : "Status se automatski osvežava na svake 3 sekunde.");
        statusHint.setForeground(allTerminal ? SwingTheme.SUCCESS : SwingTheme.MUTED_TEXT);
        if (allTerminal) {
            refreshTimer.stop();
        }
        updateActionButtons();
    }

    private void decideForSelectedJob(boolean retry) {
        int selectedRow = statusTable.getSelectedRow();
        if (selectedRow < 0) {
            return;
        }
        UUID jobId = (UUID) statusModel.getValueAt(selectedRow, 0);
        String action = retry ? "ponovite" : "prekinete";
        int answer = JOptionPane.showConfirmDialog(
                this,
                "Da li želite da " + action + " izabrani posao?",
                "Potvrda odluke",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE);
        if (answer != JOptionPane.YES_OPTION) {
            return;
        }

        runDecision(jobId, retry);
    }

    private void runDecision(UUID jobId, boolean retry) {
        final JobClient client;
        try {
            client = createClient();
        } catch (IllegalArgumentException exception) {
            showError(exception);
            return;
        }
        retryButton.setEnabled(false);
        abortButton.setEnabled(false);
        statusHint.setText(retry ? "Zahtev za ponavljanje se šalje..." : "Zahtev za prekid se šalje...");
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                if (retry) {
                    client.retryFailedJob(jobId);
                } else {
                    client.abortFailedJob(jobId);
                }
                return null;
            }

            @Override
            protected void done() {
                try {
                    get();
                    refreshStatus(true);
                } catch (Exception exception) {
                    showError(exception);
                    updateActionButtons();
                }
            }
        }.execute();
    }

    private void downloadResults() {
        final JobClient client;
        final UUID batchId;
        final Path destination;
        try {
            client = createClient();
            batchId = ClientGuiSupport.parseBatchId(batchIdField.getText());
            String path = destinationField.getText().trim();
            if (path.isEmpty()) {
                throw new IllegalArgumentException("Izaberite direktorijum za rezultate.");
            }
            destination = Path.of(path);
        } catch (IllegalArgumentException exception) {
            showError(exception);
            return;
        }

        downloadButton.setEnabled(false);
        resultHint.setText("Preuzimam rezultate...");
        new SwingWorker<BatchResultMessage, Void>() {
            @Override
            protected BatchResultMessage doInBackground() throws Exception {
                return client.downloadBatchResults(batchId, destination);
            }

            @Override
            protected void done() {
                downloadButton.setEnabled(true);
                try {
                    BatchResultMessage result = get();
                    resultHint.setText(
                            "Sačuvano je " + result.getFiles().size() + " datoteka u "
                                    + destination.toAbsolutePath().normalize());
                    resultHint.setForeground(SwingTheme.SUCCESS);
                } catch (Exception exception) {
                    resultHint.setText("Rezultati nisu preuzeti.");
                    resultHint.setForeground(SwingTheme.DANGER);
                    showError(exception);
                }
            }
        }.execute();
    }

    private void startFailureMonitor(UUID batchId) {
        closeFailureMonitor();
        final JobClient client;
        try {
            client = createClient();
        } catch (IllegalArgumentException exception) {
            return;
        }
        Thread monitorThread = new Thread(() -> {
            JobFailureMonitor monitor = null;
            try {
                monitor = client.monitorBatch(batchId);
                failureMonitor = monitor;
                while (monitor.isOpen()) {
                    JobStatusSnapshot failedJob = monitor.waitForFailure();
                    SwingUtilities.invokeLater(() -> notifyFailure(failedJob));
                }
            } catch (IOException | IllegalStateException exception) {
                // Manual status refresh remains available if monitoring is interrupted.
            } finally {
                if (failureMonitor == monitor) {
                    failureMonitor = null;
                }
                if (monitor != null) {
                    monitor.close();
                }
            }
        }, "kdp-client-failure-monitor");
        monitorThread.setDaemon(true);
        monitorThread.start();
    }

    private void notifyFailure(JobStatusSnapshot failedJob) {
        refreshStatus(false);
        Object[] options = {"Ponovi prekinuti deo", "Odustani od posla", "Odluči kasnije"};
        int choice = JOptionPane.showOptionDialog(
                this,
                "Posao " + failedJob.getJobId() + " je prekinut.\nRazlog: "
                        + failedJob.getFailureReason().orElse("Nepoznata greška"),
                "Potrebna je odluka korisnika",
                JOptionPane.DEFAULT_OPTION,
                JOptionPane.WARNING_MESSAGE,
                null,
                options,
                options[0]);
        if (choice == 0) {
            runDecision(failedJob.getJobId(), true);
        } else if (choice == 1) {
            runDecision(failedJob.getJobId(), false);
        }
    }

    private JobClient createClient() {
        String host = hostField.getText().trim();
        if (host.isEmpty()) {
            throw new IllegalArgumentException("Unesite adresu servera.");
        }
        int port = ClientGuiSupport.parsePort(portField.getText());
        return new JobClient(new ClientConfig(host, port));
    }

    private void updateActionButtons() {
        int row = statusTable.getSelectedRow();
        boolean failed = row >= 0
                && row < currentStatuses.size()
                && currentStatuses.get(row).getStatus() == JobStatus.FAILED;
        retryButton.setEnabled(failed);
        abortButton.setEnabled(failed);
    }

    private void setSubmissionBusy(boolean busy, String message) {
        submitButton.setEnabled(!busy);
        submissionHint.setText(message);
        submissionHint.setForeground(busy ? SwingTheme.PRIMARY : SwingTheme.MUTED_TEXT);
    }

    private void closeFailureMonitor() {
        JobFailureMonitor monitor = failureMonitor;
        failureMonitor = null;
        if (monitor != null) {
            monitor.close();
        }
    }

    private void showError(Throwable throwable) {
        Throwable cause = throwable;
        while (cause.getCause() != null
                && (cause instanceof java.util.concurrent.ExecutionException
                || cause instanceof java.util.concurrent.CompletionException)) {
            cause = cause.getCause();
        }
        String message = cause.getMessage();
        if (message == null || message.isBlank()) {
            message = cause.getClass().getSimpleName();
        }
        JOptionPane.showMessageDialog(
                this,
                message,
                "Operacija nije uspela",
                JOptionPane.ERROR_MESSAGE);
    }
}
