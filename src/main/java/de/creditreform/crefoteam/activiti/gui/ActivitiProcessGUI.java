package de.creditreform.crefoteam.activiti.gui;

import de.creditreform.crefoteam.activiti.*;
import de.creditreform.crefoteam.cte.rest.RestInvokerConfig;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;
import java.util.concurrent.TimeoutException;

/**
 * Swing-GUI fuer die Steuerung und Visualisierung eines Activiti-Prozesses.
 * <p>
 * Unterstuetzt drei Modi:
 * - Neu starten: BPMNs deployen, Prozess starten, Tasks abarbeiten
 * - Fortsetzen: An einem laufenden Prozess beim aktuellen UserTask weitermachen
 * - Unterbrechen: Task-Schleife stoppen, Prozess bleibt auf Activiti erhalten (wiederanlauffaehig)
 */
public class ActivitiProcessGUI extends JFrame {

    private static final String ENV_NAME = "JUNIT";
    private static final String PROCESS_DEF_KEY = ENV_NAME + "-TestAutomationProcess";
    private static final String DEFAULT_MEIN_KEY = "GUI-Test";
    private static final String MAIN_BPMN = "bpmns/CteAutomatedTestProcess.bpmn";
    private static final String SUB_BPMN = "bpmns/CteAutomatedTestProcessSUB.bpmn";

    // GUI-Komponenten
    private final JLabel processImageLabel = new JLabel();
    private final JTextArea logArea = new JTextArea();
    private final JTextField urlField = new JTextField("http://localhost:9090");
    private final JTextField userField = new JTextField("kermit");
    private final JPasswordField passwordField = new JPasswordField("kermit");
    private final JTextField meinKeyField = new JTextField(DEFAULT_MEIN_KEY);
    private final JButton startButton = new JButton("Starten");
    private final JButton stopButton = new JButton("Unterbrechen");
    private final JLabel statusLabel = new JLabel("Bereit");
    private final JProgressBar progressBar = new JProgressBar();

    // Prozess-Status
    private Integer processInstanceID;
    private volatile boolean running = false;
    private Thread workerThread;
    private int taskCount = 0;

    public ActivitiProcessGUI() {
        super("Activiti 6 - Prozess-Steuerung");
        initGUI();
    }

    private void initGUI() {
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                onExit();
            }
        });
        setSize(1200, 850);
        setLocationRelativeTo(null);

        JPanel mainPanel = new JPanel(new BorderLayout(5, 5));
        mainPanel.setBorder(new EmptyBorder(8, 8, 8, 8));

        mainPanel.add(createConfigPanel(), BorderLayout.NORTH);
        mainPanel.add(createDiagramPanel(), BorderLayout.CENTER);
        mainPanel.add(createLogPanel(), BorderLayout.SOUTH);

        // Status-Bar
        JPanel statusBar = new JPanel(new BorderLayout(5, 0));
        statusBar.setBorder(new EmptyBorder(2, 5, 2, 5));
        progressBar.setPreferredSize(new Dimension(200, 18));
        progressBar.setIndeterminate(false);
        statusBar.add(statusLabel, BorderLayout.CENTER);
        statusBar.add(progressBar, BorderLayout.EAST);
        mainPanel.add(statusBar, BorderLayout.PAGE_END);

        setContentPane(mainPanel);

        stopButton.setEnabled(false);
        startButton.addActionListener(e -> onStart());
        stopButton.addActionListener(e -> onStop());
    }

    private JPanel createConfigPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(new TitledBorder("Verbindung & Prozess"));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(3, 5, 3, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        gbc.gridy = 0;
        gbc.gridx = 0; panel.add(new JLabel("Activiti URL:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1; panel.add(urlField, gbc);
        gbc.gridx = 2; gbc.weightx = 0; panel.add(new JLabel("User:"), gbc);
        gbc.gridx = 3; gbc.weightx = 0.3; panel.add(userField, gbc);
        gbc.gridx = 4; gbc.weightx = 0; panel.add(new JLabel("Passwort:"), gbc);
        gbc.gridx = 5; gbc.weightx = 0.3; panel.add(passwordField, gbc);

        gbc.gridy = 1; gbc.weightx = 0;
        gbc.gridx = 0; panel.add(new JLabel("Business-Key:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1; panel.add(meinKeyField, gbc);
        gbc.gridx = 2; gbc.weightx = 0; panel.add(startButton, gbc);
        gbc.gridx = 3; panel.add(stopButton, gbc);

        return panel;
    }

    private JComponent createDiagramPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(new TitledBorder("Prozess-Diagramm"));
        processImageLabel.setHorizontalAlignment(SwingConstants.CENTER);
        processImageLabel.setText("Kein Prozess aktiv");
        JScrollPane scrollPane = new JScrollPane(processImageLabel);
        scrollPane.setPreferredSize(new Dimension(1100, 400));
        panel.add(scrollPane, BorderLayout.CENTER);
        return panel;
    }

    private JComponent createLogPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(new TitledBorder("Protokoll"));
        logArea.setEditable(false);
        logArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        logArea.setRows(10);
        JScrollPane scrollPane = new JScrollPane(logArea);
        scrollPane.setPreferredSize(new Dimension(1100, 200));
        panel.add(scrollPane, BorderLayout.CENTER);
        JButton clearButton = new JButton("Log leeren");
        clearButton.addActionListener(e -> logArea.setText(""));
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttonPanel.add(clearButton);
        panel.add(buttonPanel, BorderLayout.SOUTH);
        return panel;
    }

    // ============================================= Aktionen =============================================

    private CteActivitiServiceRestImpl createService() {
        RestInvokerConfig config = new RestInvokerConfig(
                urlField.getText().trim(),
                userField.getText().trim(),
                new String(passwordField.getPassword()));
        return new CteActivitiServiceRestImpl(config);
    }

    private void onStart() {
        String meinKey = meinKeyField.getText().trim();
        if (urlField.getText().trim().isEmpty() || userField.getText().trim().isEmpty() || meinKey.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Bitte alle Felder ausfuellen!", "Fehler", JOptionPane.ERROR_MESSAGE);
            return;
        }

        setInputEnabled(false);
        stopButton.setEnabled(true);
        progressBar.setIndeterminate(true);

        workerThread = new Thread(() -> {
            try {
                CteActivitiServiceRestImpl service = createService();
                String userName = userField.getText().trim();

                // 1. Pruefen ob Prozess bereits laeuft
                log("Pruefe ob Prozess '%s' mit Key '%s' bereits laeuft...", PROCESS_DEF_KEY, meinKey);
                StartAction action = checkExistingProcess(service, meinKey);

                switch (action) {
                    case CANCEL:
                        log("Vorgang abgebrochen durch Benutzer.");
                        return;

                    case RESUME:
                        log("Setze laufenden Prozess fort (ID = %d)...", processInstanceID);
                        updateProcessImage(service, processInstanceID);
                        break;

                    case NEW:
                        // BPMNs deployen und neuen Prozess starten
                        log("Deploye BPMNs...");
                        deployBpmns(service);

                        log("Starte Prozess '%s'...", PROCESS_DEF_KEY);
                        Map<String, Object> startParams = buildProcessParams(meinKey);
                        CteActivitiProcess process = service.startProcess(PROCESS_DEF_KEY, startParams);
                        processInstanceID = process.getId();
                        taskCount = 0;
                        log("Prozess gestartet: ID = %d", processInstanceID);
                        updateProcessImage(service, processInstanceID);
                        break;
                }

                // 2. Task-Schleife
                running = true;
                Map<String, Object> taskParams = buildProcessParams(meinKey);
                runTaskLoop(service, userName, meinKey, taskParams);

            } catch (Exception ex) {
                if (running) {
                    log("FEHLER: %s", ex.getMessage());
                    showError("Fehler: " + ex.getMessage());
                }
            } finally {
                running = false;
                SwingUtilities.invokeLater(() -> {
                    setInputEnabled(true);
                    stopButton.setEnabled(false);
                    progressBar.setIndeterminate(false);
                });
            }
        }, "ActivitiWorker");
        workerThread.setDaemon(true);
        workerThread.start();
    }

    /**
     * Stoppt nur die lokale Task-Schleife.
     * Der Prozess bleibt auf Activiti erhalten und kann spaeter fortgesetzt werden.
     */
    private void onStop() {
        if (running) {
            running = false;
            log("Task-Verarbeitung unterbrochen. Prozess %d laeuft weiter auf Activiti.", processInstanceID);
            setStatus("Unterbrochen (Prozess %d aktiv)", processInstanceID);
        }
    }

    private void onExit() {
        if (running) {
            int option = JOptionPane.showConfirmDialog(this,
                    "Die Task-Verarbeitung laeuft noch.\n" +
                    "Der Prozess bleibt auf Activiti erhalten.\n\n" +
                    "Wirklich beenden?",
                    "Beenden", JOptionPane.YES_NO_OPTION);
            if (option != JOptionPane.YES_OPTION) return;
            running = false;
        }
        dispose();
        System.exit(0);
    }

    // ============================================= Prozess-Logik =============================================

    private enum StartAction { NEW, RESUME, CANCEL }

    /**
     * Prueft ob ein Prozess mit dem Key bereits laeuft.
     * Bietet 3 Optionen: Loeschen & Neu / Fortsetzen / Abbrechen.
     * Setzt processInstanceID bei Fortsetzen.
     */
    private StartAction checkExistingProcess(CteActivitiServiceRestImpl service, String meinKey) throws Exception {
        Map<String, Object> params = new HashMap<>();
        params.put(ActivitProcessConstants.UT_TASK_PARAM_NAME_MEIN_KEY, meinKey);
        List<CteActivitiProcess> existing = service.queryProcessInstances(PROCESS_DEF_KEY, params);

        if (existing.isEmpty()) {
            log("Kein laufender Prozess gefunden.");
            return StartAction.NEW;
        }

        log("Es laufen %d Prozess-Instanz(en) mit Key '%s'.", existing.size(), meinKey);

        // Aktuellen Task des ersten Prozesses ermitteln fuer die Anzeige
        CteActivitiProcess firstProcess = existing.get(0);
        String currentTaskInfo = "";
        try {
            CteActivitiTask currentTask = service.selectTaskForBusinessKey(firstProcess.getId(), meinKey);
            if (currentTask != null) {
                currentTaskInfo = String.format("\n\nAktueller Task: %s (%s)",
                        currentTask.getTaskDefinitionKey(), currentTask.getName());
            }
        } catch (Exception ignore) {
        }

        Object[] options = {"Loeschen & Neu starten", "Fortsetzen", "Abbrechen"};
        int choice = JOptionPane.showOptionDialog(this,
                String.format("Es laufen %d Prozess-Instanz(en) mit Key '%s'.%s\n\n" +
                        "Was moechten Sie tun?", existing.size(), meinKey, currentTaskInfo),
                "Laufender Prozess gefunden",
                JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE,
                null, options, options[1]);

        switch (choice) {
            case 0: // Loeschen & Neu
                for (CteActivitiProcess proc : existing) {
                    log("Loesche Prozess-Instanz %d...", proc.getId());
                    service.deleteProcessInstance(proc.getId());
                }
                log("Alle bestehenden Prozess-Instanzen geloescht.");
                return StartAction.NEW;

            case 1: // Fortsetzen
                processInstanceID = firstProcess.getId();
                return StartAction.RESUME;

            default: // Abbrechen oder Fenster geschlossen
                return StartAction.CANCEL;
        }
    }

    /**
     * Task-Schleife: Holt den naechsten Task, claimed und completed ihn.
     * Laeuft bis der Prozess beendet ist oder der User unterbricht.
     */
    private void runTaskLoop(CteActivitiServiceRestImpl service, String userName,
                             String meinKey, Map<String, Object> taskParams) throws Exception {
        while (running) {
            // Prozess-Status pruefen
            if (isProcessEnded(service, processInstanceID)) {
                log("Prozess %d ist beendet nach %d Tasks.", processInstanceID, taskCount);
                setStatus("Prozess beendet (%d Tasks)", taskCount);
                break;
            }

            // Naechsten Task holen
            CteActivitiTask task;
            try {
                task = service.selectTaskForBusinessKey(processInstanceID, meinKey);
            } catch (TimeoutException e) {
                if (isProcessEnded(service, processInstanceID)) {
                    log("Prozess %d ist beendet nach %d Tasks.", processInstanceID, taskCount);
                    setStatus("Prozess beendet (%d Tasks)", taskCount);
                } else {
                    log("Timeout bei Task-Abfrage. Prozess laeuft noch.");
                    setStatus("Timeout - Prozess %d aktiv", processInstanceID);
                }
                break;
            }
            if (task == null) break;
            if (!running) break;

            taskCount++;
            String taskDefKey = task.getTaskDefinitionKey();
            String taskName = task.getName();
            log("Task %d: %s (%s)", taskCount, taskDefKey, taskName);
            setStatus("Task %d: %s", taskCount, taskDefKey);

            // Prozess-Bild des aktuellen Tasks anzeigen (Haupt- oder Sub-Prozess)
            Integer taskProcessId = task.getProcessInstanceId();
            boolean inSubProcess = !taskProcessId.equals(processInstanceID);
            String testPhase = task.getVariables().get("TEST_PHASE");
            if (inSubProcess) {
                log("  (Sub-Prozess, ID = %d, Phase = %s)", taskProcessId, testPhase);
            }

            // Claim & Complete
            service.claimTask(task, userName);
            updateProcessImage(service, taskProcessId, testPhase);

            if (!running) {
                // Zwischen Claim und Complete unterbrochen: Task unclaimen
                log("Unterbrochen nach Claim von '%s'. Task wird freigegeben.", taskDefKey);
                service.unclaimTask(task);
                break;
            }

            service.completeTask(task, taskParams);
            updateProcessImage(service, taskProcessId, testPhase);
        }

        if (!running && !isProcessEnded(service, processInstanceID)) {
            log("Task-Verarbeitung unterbrochen bei Task %d. Prozess %d kann fortgesetzt werden.",
                    taskCount, processInstanceID);
        }
    }

    // ============================================= Hilfsmethoden =============================================

    private Map<String, Object> buildProcessParams(String meinKey) {
        Map<String, Object> params = new HashMap<>();
        params.put(ActivitProcessConstants.UT_TASK_PARAM_NAME_MEIN_KEY, meinKey);
        params.put(ActivitProcessConstants.UT_TASK_PARAM_NAME_TEST_TYPE, "PHASE1_AND_PHASE2");
        params.put("TIME_BEFORE_BTLG_IMPORT", "0");
        params.put("TIME_BEFORE_CT_IMPORT", "0");
        params.put("TIME_BEFORE_EXPORT", "0");
        return params;
    }

    private void deployBpmns(CteActivitiServiceRestImpl service) throws Exception {
        List<CteActivitiDeployment> oldDeployments = service.listDeploymentsForNameLike(ENV_NAME);
        for (CteActivitiDeployment d : oldDeployments) {
            log("Loesche altes Deployment '%s'...", d.getName());
            try {
                service.deleteCteActivitiDeployment(d);
            } catch (Exception e) {
                log("  Warnung: %s", e.getMessage());
            }
        }

        String mainBpmnPath = getClass().getClassLoader().getResource(MAIN_BPMN).getPath();
        File mainFile = service.prepareBpmnFileForEnvironment(mainBpmnPath, ENV_NAME);
        service.uploadDeploymentFile(mainFile);
        mainFile.delete();
        log("Deployed: %s", MAIN_BPMN);

        String subBpmnPath = getClass().getClassLoader().getResource(SUB_BPMN).getPath();
        File subFile = service.prepareBpmnFileForEnvironment(subBpmnPath, ENV_NAME);
        service.uploadDeploymentFile(subFile);
        subFile.delete();
        log("Deployed: %s", SUB_BPMN);
    }

    private boolean isProcessEnded(CteActivitiServiceRestImpl service, Integer processInstanceID) {
        try {
            CteActivitiProcess process = service.getProcessInstanceByID(processInstanceID);
            return process.isEnded();
        } catch (Exception e) {
            return true;
        }
    }

    /**
     * Aktualisiert das Prozess-Diagramm.
     * Zeigt das Bild der uebergebenen Prozess-Instanz (kann Haupt- oder Sub-Prozess sein).
     */
    private void updateProcessImage(CteActivitiServiceRestImpl service, Integer imageProcessId) {
        updateProcessImage(service, imageProcessId, null);
    }

    private void updateProcessImage(CteActivitiServiceRestImpl service, Integer imageProcessId, String testPhase) {
        if (imageProcessId == null) return;
        try {
            InputStream imageStream = service.getProcessImage(imageProcessId);
            if (imageStream != null) {
                BufferedImage image = ImageIO.read(imageStream);
                imageStream.close();
                if (image != null) {
                    boolean isSub = !imageProcessId.equals(processInstanceID);
                    SwingUtilities.invokeLater(() -> {
                        processImageLabel.setIcon(new ImageIcon(image));
                        processImageLabel.setText(null);
                        // Titel des Diagramm-Panels aktualisieren
                        Container parent = processImageLabel.getParent();
                        while (parent != null) {
                            if (parent instanceof JPanel) {
                                javax.swing.border.Border border = ((JPanel) parent).getBorder();
                                if (border instanceof TitledBorder) {
                                    String title;
                                    if (isSub && testPhase != null) {
                                        title = String.format("Prozess-Diagramm (Sub-Prozess %d - %s)", imageProcessId, testPhase);
                                    } else if (isSub) {
                                        title = String.format("Prozess-Diagramm (Sub-Prozess %d)", imageProcessId);
                                    } else {
                                        title = "Prozess-Diagramm (Hauptprozess)";
                                    }
                                    ((TitledBorder) border).setTitle(title);
                                    parent.repaint();
                                    break;
                                }
                            }
                            parent = parent.getParent();
                        }
                    });
                }
            }
        } catch (Exception e) {
            // Bild nicht verfuegbar
        }
    }

    private void log(String format, Object... args) {
        String timestamp = new SimpleDateFormat("HH:mm:ss").format(new Date());
        String message = String.format("[%s] %s", timestamp, String.format(format, args));
        SwingUtilities.invokeLater(() -> {
            logArea.append(message + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }

    private void setStatus(String format, Object... args) {
        String text = String.format(format, args);
        SwingUtilities.invokeLater(() -> statusLabel.setText(text));
    }

    private void showError(String message) {
        SwingUtilities.invokeLater(() ->
                JOptionPane.showMessageDialog(this, message, "Fehler", JOptionPane.ERROR_MESSAGE));
    }

    private void setInputEnabled(boolean enabled) {
        SwingUtilities.invokeLater(() -> {
            urlField.setEnabled(enabled);
            userField.setEnabled(enabled);
            passwordField.setEnabled(enabled);
            meinKeyField.setEnabled(enabled);
            startButton.setEnabled(enabled);
        });
    }

    // ============================================= Main =============================================

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignore) {
        }
        SwingUtilities.invokeLater(() -> new ActivitiProcessGUI().setVisible(true));
    }
}
