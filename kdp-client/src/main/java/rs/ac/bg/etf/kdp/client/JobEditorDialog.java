package rs.ac.bg.etf.kdp.client;

import rs.ac.bg.etf.kdp.common.domain.JobSpecification;
import rs.ac.bg.etf.kdp.common.gui.SwingTheme;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Modal, guided editor for a single submitted job. */
final class JobEditorDialog extends JDialog {

    private final JTextField jarField = new JTextField(34);
    private final JTextArea jvmArgumentsArea = new JTextArea(5, 34);
    private final JTextArea programArgumentsArea = new JTextArea(5, 34);
    private final DefaultListModel<Path> inputModel = new DefaultListModel<>();
    private final DefaultListModel<String> outputModel = new DefaultListModel<>();
    private final JList<Path> inputList = new JList<>(inputModel);
    private final JList<String> outputList = new JList<>(outputModel);

    private JobFormData result;

    private JobEditorDialog(JFrame owner, JobFormData existing) {
        super(owner, existing == null ? "Dodavanje posla" : "Izmena posla", true);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setMinimumSize(new Dimension(650, 560));
        setSize(700, 620);
        setLocationRelativeTo(owner);
        setContentPane(createContent());
        if (existing != null) {
            load(existing);
        }
    }

    static Optional<JobFormData> show(JFrame owner, JobFormData existing) {
        JobEditorDialog dialog = new JobEditorDialog(owner, existing);
        dialog.setVisible(true);
        return Optional.ofNullable(dialog.result);
    }

    private JPanel createContent() {
        JPanel content = new JPanel(new BorderLayout(0, 12));
        content.setBorder(BorderFactory.createEmptyBorder(16, 18, 16, 18));

        JPanel jarPanel = new JPanel(new BorderLayout(8, 0));
        jarPanel.setBorder(SwingTheme.cardBorder("Program za izvršavanje"));
        jarPanel.add(new JLabel("JAR datoteka:"), BorderLayout.WEST);
        jarPanel.add(jarField, BorderLayout.CENTER);
        JButton chooseJarButton = new JButton("Izaberi...");
        chooseJarButton.addActionListener(event -> chooseJar());
        jarPanel.add(chooseJarButton, BorderLayout.EAST);
        content.add(jarPanel, BorderLayout.NORTH);

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Argumenti", createArgumentsPanel());
        tabs.addTab("Ulazne datoteke", createInputPanel());
        tabs.addTab("Izlazne datoteke", createOutputPanel());
        content.add(tabs, BorderLayout.CENTER);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        JButton cancelButton = new JButton("Otkaži");
        cancelButton.addActionListener(event -> dispose());
        JButton saveButton = new JButton("Sačuvaj posao");
        saveButton.addActionListener(event -> save());
        SwingTheme.markPrimary(saveButton);
        buttons.add(cancelButton);
        buttons.add(saveButton);
        content.add(buttons, BorderLayout.SOUTH);
        return content;
    }

    private JPanel createArgumentsPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = 0;
        constraints.weightx = 1;
        constraints.fill = GridBagConstraints.BOTH;
        constraints.insets = new Insets(5, 3, 5, 3);

        constraints.gridy = 0;
        constraints.weighty = 0;
        panel.add(new JLabel("JVM argumenti - jedan u svakom redu (opciono)"), constraints);
        constraints.gridy = 1;
        constraints.weighty = 1;
        panel.add(new JScrollPane(jvmArgumentsArea), constraints);
        constraints.gridy = 2;
        constraints.weighty = 0;
        panel.add(new JLabel("Argumenti programa - jedan u svakom redu (opciono)"), constraints);
        constraints.gridy = 3;
        constraints.weighty = 1;
        panel.add(new JScrollPane(programArgumentsArea), constraints);
        return panel;
    }

    private JPanel createInputPanel() {
        inputList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        JPanel panel = listPanel(
                "Datoteke koje treba poslati radnoj stanici (najviše 6)",
                new JScrollPane(inputList));
        JButton addButton = new JButton("Dodaj datoteke...");
        addButton.addActionListener(event -> addInputs());
        JButton removeButton = new JButton("Ukloni izabrane");
        removeButton.addActionListener(event -> removeSelected(inputList, inputModel));
        addListButtons(panel, addButton, removeButton);
        return panel;
    }

    private JPanel createOutputPanel() {
        outputList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        JPanel panel = listPanel(
                "Imena datoteka koje program treba da napravi (najviše 6)",
                new JScrollPane(outputList));
        JButton addButton = new JButton("Dodaj ime...");
        addButton.addActionListener(event -> addOutput());
        JButton removeButton = new JButton("Ukloni izabrane");
        removeButton.addActionListener(event -> removeSelected(outputList, outputModel));
        addListButtons(panel, addButton, removeButton);
        return panel;
    }

    private static JPanel listPanel(String description, JScrollPane scroll) {
        JPanel panel = new JPanel(new BorderLayout(0, 8));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        panel.add(new JLabel(description), BorderLayout.NORTH);
        panel.add(scroll, BorderLayout.CENTER);
        return panel;
    }

    private static void addListButtons(JPanel panel, JButton addButton, JButton removeButton) {
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        buttons.add(addButton);
        buttons.add(removeButton);
        panel.add(buttons, BorderLayout.SOUTH);
    }

    private void chooseJar() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Izaberite izvršni JAR");
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            jarField.setText(chooser.getSelectedFile().getAbsolutePath());
        }
    }

    private void addInputs() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Izaberite ulazne datoteke");
        chooser.setMultiSelectionEnabled(true);
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        java.io.File[] selectedFiles = chooser.getSelectedFiles();
        if (selectedFiles.length == 0 && chooser.getSelectedFile() != null) {
            selectedFiles = new java.io.File[]{chooser.getSelectedFile()};
        }
        for (java.io.File selectedFile : selectedFiles) {
            Path path = selectedFile.toPath().toAbsolutePath().normalize();
            if (!containsPath(path)) {
                inputModel.addElement(path);
            }
        }
        if (inputModel.size() > JobSpecification.MAX_INPUT_FILES) {
            while (inputModel.size() > JobSpecification.MAX_INPUT_FILES) {
                inputModel.remove(inputModel.size() - 1);
            }
            showMessage("Jedan posao može imati najviše 6 ulaznih datoteka.");
        }
    }

    private void addOutput() {
        if (outputModel.size() >= JobSpecification.MAX_OUTPUT_FILES) {
            showMessage("Jedan posao može imati najviše 6 izlaznih datoteka.");
            return;
        }
        String name = JOptionPane.showInputDialog(
                this,
                "Unesite ime izlazne datoteke, na primer rezultat.txt:",
                "Nova izlazna datoteka",
                JOptionPane.PLAIN_MESSAGE);
        if (name == null) {
            return;
        }
        String trimmed = name.trim();
        if (trimmed.isEmpty()) {
            showMessage("Ime izlazne datoteke ne sme biti prazno.");
        } else if (containsOutput(trimmed)) {
            showMessage("Ovo ime izlazne datoteke je već dodato.");
        } else {
            outputModel.addElement(trimmed);
        }
    }

    private void save() {
        try {
            String jarText = jarField.getText().trim();
            if (jarText.isEmpty()) {
                throw new IllegalArgumentException("Izaberite JAR datoteku.");
            }
            JobFormData candidate = new JobFormData(
                    Path.of(jarText),
                    JobFormData.argumentsFromText(jvmArgumentsArea.getText()),
                    JobFormData.argumentsFromText(programArgumentsArea.getText()),
                    modelValues(inputModel),
                    modelValues(outputModel));
            candidate.toSubmission();
            result = candidate;
            dispose();
        } catch (IllegalArgumentException exception) {
            showMessage(exception.getMessage());
        }
    }

    private void load(JobFormData data) {
        jarField.setText(data.jarFile().toString());
        jvmArgumentsArea.setText(String.join(System.lineSeparator(), data.jvmArguments()));
        programArgumentsArea.setText(String.join(System.lineSeparator(), data.programArguments()));
        data.inputFiles().forEach(inputModel::addElement);
        data.outputFileNames().forEach(outputModel::addElement);
    }

    private boolean containsPath(Path candidate) {
        for (int index = 0; index < inputModel.size(); index++) {
            if (inputModel.get(index).equals(candidate)) {
                return true;
            }
        }
        return false;
    }

    private boolean containsOutput(String candidate) {
        for (int index = 0; index < outputModel.size(); index++) {
            if (outputModel.get(index).equals(candidate)) {
                return true;
            }
        }
        return false;
    }

    private static <T> List<T> modelValues(DefaultListModel<T> model) {
        List<T> values = new ArrayList<>(model.size());
        for (int index = 0; index < model.size(); index++) {
            values.add(model.get(index));
        }
        return List.copyOf(values);
    }

    private static <T> void removeSelected(JList<T> list, DefaultListModel<T> model) {
        int[] selected = list.getSelectedIndices();
        for (int index = selected.length - 1; index >= 0; index--) {
            model.remove(selected[index]);
        }
    }

    private void showMessage(String message) {
        JOptionPane.showMessageDialog(
                this,
                message,
                "Proverite podatke posla",
                JOptionPane.WARNING_MESSAGE);
    }
}
