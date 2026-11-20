/*
 * Copyright (C) 2021-2025 GummerCraft <http://gummercraft.fun>
 */

package com.gummer.launcher.dialog;

import com.formdev.flatlaf.util.SystemInfo;
import com.gummer.launcher.i18n.I18n;
import com.gummer.launcher.i18n.Lang;
import com.gummer.launcher.look.LookTheme;
import com.gummer.launcher.look.LookUi;
import net.miginfocom.swing.MigLayout;
import com.gummer.launcher.popup.Notification;
import com.gummer.launcher.shared.BootstrapConfig;
import com.gummer.launcher.swing.SwingExtras;
import com.gummer.launcher.util.FlatClientProperties;
import com.gummer.launcher.util.SelfRestart;
import com.skcraft.launcher.Configuration;
import com.skcraft.launcher.Launcher;
import com.skcraft.launcher.dialog.ConsoleFrame;
import com.skcraft.launcher.launch.runtime.AddJavaRuntime;
import com.skcraft.launcher.launch.runtime.JavaRuntime;
import com.skcraft.launcher.launch.runtime.JavaRuntimeFinder;
import com.skcraft.launcher.persistence.Persistence;
import com.skcraft.launcher.swing.ActionListeners;
import com.skcraft.launcher.swing.FormPanel;
import com.skcraft.launcher.swing.ObjectSwingMapper;
import com.skcraft.launcher.swing.SwingHelper;
import com.skcraft.launcher.util.SharedLocale;
import lombok.NonNull;

import javax.swing.*;
import javax.swing.filechooser.FileFilter;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.util.*;

import static com.gummer.launcher.popup.ShowNotification.showNotification;
import static com.gummer.launcher.util.FlatClientProperties.TABBED_PANE_TAB_WIDTH_MODE_EQUAL;

/**
 * A dialog to modify configuration options.
 */
public class ConfigurationDialogFlat extends JDialog {

    private final Launcher launcher;

    private final Configuration config;
    private final ObjectSwingMapper mapper;

    private final JPanel tabContainer = new JPanel(new BorderLayout());
    private final JTabbedPane tabbedPane = new JTabbedPane();
    private final FormPanel generalSettingsPanel = new FormPanel();
    private final FormPanel javaSettingsPanel = new FormPanel();
    private final JComboBox<JavaRuntime> jvmRuntime = new JComboBox<>();
    private final JTextField jvmArgsText = new JTextField();
    private final JSlider memorySlider = new JSlider();
    private final JSpinner minMemorySpinner = new JSpinner();
    private final JSpinner maxMemorySpinner = new JSpinner();
    private final JSpinner permGenSpinner = new JSpinner();
    private final JCheckBox enforceRuntimeCheck = new JCheckBox(SharedLocale.tr("options.enforceRuntime"));
    private final JCheckBox javaAutoUpdateCheck = new JCheckBox(SharedLocale.tr("settings.javaUpd.auto"));
    private final FormPanel gameSettingsPanel = new FormPanel();
    private final JCheckBox consoleUseColorsCheck = new JCheckBox(SharedLocale.tr("options.consoleUseColors"));
    private final JCheckBox showLauncherConsoleCheck = new JCheckBox(SharedLocale.tr("options.showLauncherConsole"));
    private final JCheckBox showConsoleCheck = new JCheckBox(SharedLocale.tr("options.showConsole"));
    private final JCheckBox enableDebugCheck = new JCheckBox(SharedLocale.tr("options.enableDebug"));
    private final JSpinner widthSpinner = new JSpinner();
    private final JSpinner heightSpinner = new JSpinner();
    private final FormPanel proxySettingsPanel = new FormPanel();
    private final JCheckBox useProxyCheck = new JCheckBox(SharedLocale.tr("options.useProxyCheck"));
    private final JTextField proxyHostText = new JTextField();
    private final JSpinner proxyPortText = new JSpinner();
    private final JTextField proxyUsernameText = new JTextField();
    private final JPasswordField proxyPasswordText = new JPasswordField();
    private final FormPanel advancedPanel = new FormPanel();
    private final JTextField gameKeyText = new JTextField();
    //private final JTextArea  gameKeyText = new JTextArea(5, 10);
    private final JTextField installDirText = new JTextField(BootstrapConfig.get().getWorkingDirectory().toString());
    private final JPanel buttonsPanel = new JPanel(new BorderLayout());

    private final JLabel  bitsWarn = new JLabel(SharedLocale.tr("options.64BitJavaWarning"));
    private final JButton okButton = new JButton(SharedLocale.tr("button.savesm"));
    private final JButton cancelButton = new JButton(SharedLocale.tr("button.cancelsm"));
    private final JButton logButton = new JButton(SharedLocale.tr("options.launcherConsole"));

    private final String generalTabTitle = SharedLocale.tr("options.generalTab");
    private final String jvmTabTitle = SharedLocale.tr("options.javaTab");
    private final String advancedTabTitle = SharedLocale.tr("options.advancedTab");
    private final String minecraftTabTitle = SharedLocale.tr("options.minecraftTab");
    private final String proxyTabTabTitle = SharedLocale.tr("options.proxyTab");

    private final JLabel homedirTitleLabel = new JLabel(SharedLocale.tr("options.dataAndHomedir"));
    private final JLabel consoleTitle = new JLabel(SharedLocale.tr("options.consoleAndLogTitle"));
    private final JLabel langTitle = new JLabel(SharedLocale.tr("options.launcherLang"));

    private final JLabel installDirDescInfo = new JLabel(SharedLocale.tr("options.installDirDesc"));
    private final JLabel installDirDesc2Info =  new JLabel(SharedLocale.tr("options.installDirDesc2"));

    private final JLabel langChangeRestartInfo = new JLabel(SharedLocale.tr("options.needrestartforsure"));

    private final JLabel jvmTitleLabel = new JLabel(SharedLocale.tr("options.javaParamsSectionTitle"));
    private final JLabel memlimitTitleLabel = new JLabel(SharedLocale.tr("options.javaMemSectionTitle"));

    private final JLabel devTitleLabel = new JLabel(SharedLocale.tr("options.devBetaTitle"));
    private final JLabel devBetaDesc = new JLabel(SharedLocale.tr("options.devBetaDesc"));

    private final JLabel buildInfoTitle = new JLabel(SharedLocale.tr("options.buildInfoTitle"));
    private final JLabel buidInfoVersion = new JLabel(SharedLocale.tr("options.buidinfo.version"));
    private final JLabel buidInfoPipeline = new JLabel(SharedLocale.tr("options.buidinfo.pipeline"));
    private final JLabel buidInfoBranch = new JLabel(SharedLocale.tr("options.buidinfo.branch"));
    private final JLabel buidInfoCommit = new JLabel(SharedLocale.tr("options.buidinfo.commit"));
    private final JLabel buidInfoJvm = new JLabel(SharedLocale.tr("options.buidinfo.jvm"));

    //private final ImageIcon settingsIcon = new ImageIcon(Objects.requireNonNull(SwingExtras.readIconImage(Launcher.class, "ui/icon/light/settings.png")));
    //private final ImageIcon javaIcon = new ImageIcon(Objects.requireNonNull(SwingExtras.readIconImage(Launcher.class, "ui/icon/light/t/java.png")));
    //private final ImageIcon invaderIcon = new ImageIcon(Objects.requireNonNull(SwingExtras.readIconImage(Launcher.class, "ui/icon/light/t/invader.png")));
    private final static Icon folderIcon = com.gummer.launcher.look.MenuIcon.FOLDER; // 20.11.2026: вектор, чітко на 125-150%
    private final static Map<Lang, ImageIcon> flagIcons = new HashMap<>();

    /**
     * Create a new configuration dialog.
     *
     * @param owner the window owner
     * @param launcher the launcher
     */
    public ConfigurationDialogFlat(Window owner, @NonNull Launcher launcher, JFrame window) {
        super(owner, ModalityType.DOCUMENT_MODAL);

        this.launcher = launcher;

        this.config = launcher.getConfig();
        mapper = new ObjectSwingMapper(config);

        JavaRuntime[] javaRuntimes = JavaRuntimeFinder.getAvailableRuntimesCached().toArray(new JavaRuntime[0]);
        DefaultComboBoxModel<JavaRuntime> model = new DefaultComboBoxModel<>(javaRuntimes);

        // Put the runtime from the config in the model if it isn't
        boolean configRuntimeFound = Arrays.stream(javaRuntimes).anyMatch(r -> r.equals(config.getJavaRuntime()));
        if (!configRuntimeFound && config.getJavaRuntime() != null) {
            model.insertElementAt(config.getJavaRuntime(), 0);
        }

        jvmRuntime.setModel(model);
        jvmRuntime.addItem(AddJavaRuntime.ADD_RUNTIME_SENTINEL);

        jvmRuntime.setSelectedItem(config.getJavaRuntime());

        setTitle(SharedLocale.tr("options.title"));
        initComponents(); // Must be called after jvmRuntime model setup
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        // 16.11.2026: під розмір екрана (на малому екрані і з масштабом Windows старе 400x560 обрізалось), розтягується
        Dimension size = LookTheme.fitToScreen(this, 760, 560, 560, 420);
        setSize(size);
        setMinimumSize(new Dimension(Math.min(560, size.width), Math.min(420, size.height)));
        setResizable(true);
        LookUi.decorate(this, owner);
        setLocationRelativeTo(owner);

        mapper.map(jvmArgsText, "jvmArgs");
        //mapper.map(minMemorySpinner, "minMemory"); // use slider instead
        //mapper.map(maxMemorySpinner, "maxMemory");
        mapper.map(memorySlider, "minMemory");
        mapper.map(memorySlider, "maxMemory");
        mapper.map(permGenSpinner, "permGen");
        mapper.map(enforceRuntimeCheck, "enforceRuntime");
        mapper.map(javaAutoUpdateCheck, "javaAutoUpdate");
        mapper.map(consoleUseColorsCheck, "consoleUseColors");
        mapper.map(showLauncherConsoleCheck, "showLauncherConsole");
        mapper.map(showConsoleCheck, "showConsole");
        mapper.map(enableDebugCheck, "enableDebug");
        mapper.map(widthSpinner, "windowWidth");
        mapper.map(heightSpinner, "windowHeight");
        mapper.map(useProxyCheck, "proxyEnabled");
        mapper.map(proxyHostText, "proxyHost");
        mapper.map(proxyPortText, "proxyPort");
        mapper.map(proxyUsernameText, "proxyUsername");
        mapper.map(proxyPasswordText, "proxyPassword");
        mapper.map(gameKeyText, "gameKey");

        mapper.copyFromObject();
    }

    /**
     * 16.11.2026: нова розкладка. Раніше вкладки (на малому екрані і з масштабом Windows переносились у два ряди)
     * у вікні 400x560 без прокрутки, текст обрізався. Тепер навігація ліворуч, розділи картками з прокруткою,
     * вікно під розмір екрана і розтягується, стиль свій для кожного вигляду (look.LookTheme). Поля, зв'язки з
     * налаштуваннями (mapper) і дії ті самі.
     */
    private void initComponents() {
        LookTheme.Palette pal = LookTheme.palette();

        // мова
        JComboBox<Lang> languageSelector = new JComboBox<>(Lang.values());
        languageSelector.setSelectedItem(Lang.getFromName(launcher.getConfig().getLang()));
        languageSelector.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                          boolean isSelected, boolean cellHasFocus) {
                JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof Lang) {
                    Lang langValue = (Lang) value;
                    label.setText(" " + langValue.getName() + "  " + langValue.getTranslataor());
                    label.setIcon(flagIcons.get(langValue));
                }
                label.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 16));
                return label;
            }
        });

        Properties launcherProps = launcher.getProperties();

        // ---------------------------------------------------------- Загальні
        JPanel general = page();
        JPanel lookCard = section(SharedLocale.tr("settings.look.title"), SharedLocale.tr("settings.look.desc"));
        lookCard.add(createLookBox(), "growx 0, gaptop 6");
        general.add(lookCard);

        JPanel langCard = section(SharedLocale.tr("settings.lang.title"), SharedLocale.tr("settings.lang.desc"));
        langCard.add(languageSelector, "growx 0, gaptop 6");
        general.add(langCard);

        JPanel dirCard = section(SharedLocale.tr("settings.dir.title"), SharedLocale.tr("settings.dir.desc"));
        installDirText.putClientProperty(FlatClientProperties.TEXT_FIELD_TRAILING_ICON, folderIcon);
        installDirText.setEditable(false);
        installDirText.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        installDirText.setToolTipText(SharedLocale.tr("settings.dir.change"));
        dirCard.add(installDirText, "gaptop 6, h 34!, wmin 0");
        dirCard.add(LookUi.body(SharedLocale.tr("options.installDirDesc2"), true), "gaptop 4, wmin 0");
        general.add(dirCard);

        // ---------------------------------------------------------- Java і пам'ять
        JPanel java = page();
        JPanel memCard = section(SharedLocale.tr("settings.memory.title"), SharedLocale.tr("settings.memory.desc"));
        minMemorySpinner.setVisible(false);
        maxMemorySpinner.setVisible(false);
        SwingExtras.configureMemorySlider(memorySlider, minMemorySpinner, maxMemorySpinner);
        memorySlider.setOpaque(false);
        memCard.add(memorySlider, "gaptop 6, wmin 0");
        memCard.add(minMemorySpinner, "hidemode 3");
        memCard.add(maxMemorySpinner, "hidemode 3");
        // 17.11.2026: попередження, коли більше половини пам'яті ПК
        final JTextArea memWarn = LookUi.body(" ", true);
        memWarn.setForeground(LookTheme.palette().warn);
        memCard.add(memWarn, "gaptop 2, wmin 0, hidemode 3");
        Runnable memCheck = () -> {
            int mb = memorySlider.getValue();
            boolean much = com.gummer.launcher.manage.MemoryAdvice.tooMuch(mb);
            memWarn.setText(much ? SharedLocale.tr("memory.tooMuchSetting", com.gummer.launcher.manage.MemoryAdvice.gb(mb),
                    com.gummer.launcher.manage.MemoryAdvice.gb(com.gummer.launcher.manage.MemoryAdvice.totalMb())) : " ");
            memWarn.setVisible(much);
        };
        memorySlider.addChangeListener(e -> memCheck.run());
        SwingUtilities.invokeLater(memCheck);
        memCard.add(LookUi.body(SharedLocale.tr("options.64BitJavaWarning"), true), "gaptop 2, wmin 0");
        java.add(memCard);

        JPanel jvmCard = section(SharedLocale.tr("settings.java.title"), SharedLocale.tr("settings.java.desc"));
        JPanel jvmGrid = new JPanel(new MigLayout("insets 0, gap 12 8, fillx", "[][grow,fill]", ""));
        jvmGrid.setOpaque(false);
        jvmGrid.add(label(SharedLocale.tr("settings.java.runtime")));
        jvmGrid.add(jvmRuntime, "wmin 0, wrap");
        jvmArgsText.putClientProperty("JTextField.placeholderText", SharedLocale.tr("options.automaticPlaceholder"));
        jvmGrid.add(label(SharedLocale.tr("settings.java.args")));
        jvmGrid.add(jvmArgsText, "wmin 0, h 32!, wrap");
        jvmCard.add(jvmGrid, "gaptop 6, wmin 0");
        enforceRuntimeCheck.setOpaque(false);
        jvmCard.add(enforceRuntimeCheck, "gaptop 4");
        java.add(jvmCard);
        java.add(createJavaUpdatesCard());

        // ---------------------------------------------------------- Додатково
        JPanel adv = page();
        JPanel consoleCard = section(SharedLocale.tr("settings.console.title"), null);
        logButton.setText(SharedLocale.tr("settings.console.open"));
        logButton.setToolTipText(SharedLocale.tr("tooltip.launcherConsole"));
        logButton.putClientProperty("FlatLaf.styleClass", null);
        LookUi.secondary(logButton);
        for (JCheckBox c : new JCheckBox[]{showLauncherConsoleCheck, showConsoleCheck, enableDebugCheck}) {
            c.setOpaque(false);
            consoleCard.add(c, "gaptop 2, wmin 0");
        }
        consoleCard.add(logButton, "growx 0, gaptop 8");
        adv.add(consoleCard);

        JPanel betaCard = section(SharedLocale.tr("settings.beta.title"), SharedLocale.tr("options.devBetaDesc"));
        gameKeyText.setEditable(true);
        gameKeyText.putClientProperty("JTextField.placeholderText", SharedLocale.tr("options.devBetaPlaceholder"));
        betaCard.add(gameKeyText, "gaptop 6, h 32!, wmin 0");
        adv.add(betaCard);

        JPanel aboutCard = section(SharedLocale.tr("settings.about.title"), null);
        JPanel about = new JPanel(new MigLayout("insets 0, gap 16 4, fillx", "[][grow,fill]", ""));
        about.setOpaque(false);
        aboutRow(about, SharedLocale.tr("options.buidinfo.version"), launcherProps.getProperty("version", "N/A"));
        aboutRow(about, SharedLocale.tr("options.buidinfo.commit"), launcherProps.getProperty("commit", "N/A"));
        aboutRow(about, SharedLocale.tr("options.buidinfo.branch"), launcherProps.getProperty("branch", "N/A"));
        aboutRow(about, SharedLocale.tr("options.buidinfo.pipeline"), launcherProps.getProperty("pipelineId", "N/A"));
        int runtimeCount = jvmRuntime.getItemCount();
        for (int i = 0; i < runtimeCount - 1; i++) {
            aboutRow(about, i == 0 ? SharedLocale.tr("options.buidinfo.jvm") : "", String.valueOf(jvmRuntime.getItemAt(i)));
        }
        aboutCard.add(about, "gaptop 4, wmin 0");
        adv.add(aboutCard);
        adv.add(createDangerCard());

        // ---------------------------------------------------------- каркас
        final CardLayout pages = new CardLayout();
        final JPanel pageHost = new JPanel(pages);
        pageHost.setOpaque(false);
        pageHost.add(scroll(general), "0");
        pageHost.add(scroll(java), "1");
        pageHost.add(scroll(adv), "2");
        LookUi.Nav nav = new LookUi.Nav(new String[]{SharedLocale.tr("settings.nav.general"),
                SharedLocale.tr("settings.nav.java"), SharedLocale.tr("settings.nav.advanced")},
                i -> {
                    pages.show(pageHost, String.valueOf(i));
                    // 19.11.2026: прихована сторінка рахувала висоту текстів з нульовою шириною (порожнє місце під
                    // списком Java); після показу розкласти її ще раз
                    Component shown = pageHost.getComponent(i);
                    if (shown instanceof JScrollPane) InstanceManageDialog.relayoutLater(((JScrollPane) shown).getViewport().getView());
                });

        JPanel body = new JPanel(new MigLayout("insets 14 14 0 14, gap 14 0, fill", "[170!][grow,fill]", "[grow,fill]"));
        body.setOpaque(false);
        JPanel navCol = new JPanel(new MigLayout("insets 0, wrap 1, fillx", "[grow,fill]", "[]push[]"));
        navCol.setOpaque(false);
        navCol.add(nav, "growx");
        JLabel version = new JLabel(launcher.getVersion());
        version.setFont(version.getFont().deriveFont(11.5f));
        version.setForeground(pal.text3);
        navCol.add(version, "gapleft 14, gapbottom 4");
        body.add(navCol, "grow");
        body.add(pageHost, "grow, wmin 0");

        okButton.setText(SharedLocale.tr("settings.save"));
        cancelButton.setText(SharedLocale.tr("settings.cancel"));
        okButton.putClientProperty("FlatLaf.styleClass", null);
        cancelButton.putClientProperty("FlatLaf.styleClass", null);
        LookUi.primary(okButton);
        LookUi.secondary(cancelButton);
        buttonsPanel.setLayout(new MigLayout("insets 12 16 14 16, gap 8", "push[][]", "[34!]"));
        buttonsPanel.setOpaque(false);
        buttonsPanel.add(cancelButton, "w 120!");
        buttonsPanel.add(okButton, "w 140!");
        getRootPane().setDefaultButton(okButton);

        JPanel content = new JPanel(new BorderLayout());
        content.setBackground(pal.bg);
        content.add(body, BorderLayout.CENTER);
        content.add(buttonsPanel, BorderLayout.SOUTH);
        setContentPane(content);
        nav.select(0);

        languageSelector.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                I18n.changeLang((Lang) Lang.values()[languageSelector.getSelectedIndex()], launcher);
                save();
                SelfRestart.restartRequiredDialog(
                    ConfigurationDialogFlat.class,
                    SharedLocale.tr("options.needrestartforsuretitle"),
                    SharedLocale.tr("options.needrestartforsure")
                );
            }
        });

        logButton.addActionListener(e -> ConsoleFrame.showMessagesColor(config.isConsoleUseColors()));

        okButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                save();
                showNotification(
                    LauncherFrameFlat.rootFrame,
                    LauncherFrameFlat.class,
                    Notification.Type.SUCCESS,
                    SharedLocale.tr("notification.popup.settings.saved")
                );
            }
        });

        cancelButton.addActionListener(ActionListeners.dispose(this));

        installDirText.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent mouseEvent) {
                JFileChooser fc = new JFileChooser(BootstrapConfig.get().getWorkingDirectory());
                fc.setFileFilter(new FileFilter() {
                    @Override
                    public boolean accept(File f) {
                        return f.isDirectory();
                    }
                    @Override
                    public String getDescription() {
                        return SharedLocale.tr("options.installDir.fileChooser.fileType");
                    }
                });
                fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
                fc.addActionListener(action -> {
                    if (action.getActionCommand().equals("ApproveSelection")) {
                        fc.setVisible(false);
                        BootstrapConfig.get().setWorkingDirectory(fc.getSelectedFile());
                        BootstrapConfig.get().save();
                        installDirText.setText(BootstrapConfig.get().getWorkingDirectory().getAbsolutePath());
                        save();
                        SelfRestart.restartRequiredDialog(
                            ConfigurationDialogFlat.class,
                            SharedLocale.tr("options.needrestartforsuretitle"),
                            SharedLocale.tr("options.needrestartforsure")
                        );
                    }
                });
                fc.showDialog(ConfigurationDialogFlat.this, SharedLocale.tr("options.installDir.fileChooser.buttonAccept"));
            }
        });

        jvmRuntime.addActionListener(e -> {
            // A little fun hack...
            if (jvmRuntime.getSelectedItem() == AddJavaRuntime.ADD_RUNTIME_SENTINEL) {
                jvmRuntime.setSelectedItem(null);
                jvmRuntime.setPopupVisible(false);

                JFileChooser chooser = new JFileChooser();
                chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
                chooser.setFileFilter(new JavaRuntimeFileFilter());
                chooser.setDialogTitle("Choose a Java executable");

                int result = chooser.showOpenDialog(this);
                if (result == JFileChooser.APPROVE_OPTION) {
                    JavaRuntime runtime = JavaRuntimeFinder.getRuntimeFromPath(chooser.getSelectedFile().getAbsolutePath());

                    MutableComboBoxModel<JavaRuntime> model = (MutableComboBoxModel<JavaRuntime>) jvmRuntime.getModel();
                    model.insertElementAt(runtime, 0);
                    jvmRuntime.setSelectedItem(runtime);
                }
            }
        });
    }

    /**
     * 1.36.3 (19.11.2026, власник): плитка «Оновлення Java». Перемикач автооновлення (зберігається кнопкою «Зберегти»)
     * і список Java: лаунчера та кожна встановлена Java гри, версія, чи є нова, «Оновити» / «Відкатити» (діють одразу).
     * Логіка: {@link com.gummer.launcher.util.JavaUpdates}.
     */
    private JPanel createJavaUpdatesCard() {
        JPanel card = section(SharedLocale.tr("settings.javaUpd.title"), SharedLocale.tr("settings.javaUpd.desc"));
        javaAutoUpdateCheck.setOpaque(false);
        card.add(javaAutoUpdateCheck, "gaptop 6");
        final JTextArea offNote = LookUi.body(SharedLocale.tr("settings.javaUpd.autoOff"), true);
        offNote.setForeground(LookTheme.palette().warn);
        card.add(offNote, "gaptop 2, wmin 0, hidemode 3");
        javaAutoUpdateCheck.addItemListener(e -> offNote.setVisible(!javaAutoUpdateCheck.isSelected()));
        SwingUtilities.invokeLater(() -> offNote.setVisible(!javaAutoUpdateCheck.isSelected()));
        final JPanel rows = new JPanel(new MigLayout("insets 0, wrap 1, gap 0 12, fillx", "[grow,fill]", ""));
        rows.setOpaque(false);
        card.add(rows, "gaptop 10, wmin 0");
        reloadJavaRows(rows);
        return card;
    }

    private void reloadJavaRows(final JPanel rows) {
        rows.removeAll();
        rows.add(LookUi.body(SharedLocale.tr("settings.javaUpd.loading"), true), "wmin 0");
        rows.revalidate();
        rows.repaint();
        launcher.getExecutor().submit(() -> {
            final java.util.List<com.gummer.launcher.util.JavaUpdates.Entry> list = com.gummer.launcher.util.JavaUpdates.list(launcher);
            SwingUtilities.invokeLater(() -> {
                rows.removeAll();
                for (com.gummer.launcher.util.JavaUpdates.Entry e : list) rows.add(javaRow(rows, e), "wmin 0");
                rows.revalidate();
                rows.repaint();
                // 19.11.2026 (власник: «Знову багато місця в кінці списку джав»): тексти з переносом рахували висоту
                // до того, як отримали ширину; розкласти сторінку ще раз, як у вікні «Збірка»
                InstanceManageDialog.relayoutLater(rows);
            });
            return null;
        });
    }

    private JPanel javaRow(final JPanel rows, final com.gummer.launcher.util.JavaUpdates.Entry e) {
        JPanel r = new JPanel(new MigLayout("insets 0, gap 8 2, fillx", "[grow,fill][][]", "[][]"));
        r.setOpaque(false);
        String title = e.isLauncher() ? SharedLocale.tr("settings.javaUpd.launcher")
                : SharedLocale.tr("settings.javaUpd.game", e.component);
        JLabel name = new JLabel(title + "   " + e.installed);
        name.setFont(name.getFont().deriveFont(Font.BOLD));

        String status;
        boolean warn = false;
        if (e.isLauncher() && e.external) {
            status = SharedLocale.tr("settings.javaUpd.external");
        } else if (e.latest == null) {
            status = SharedLocale.tr("settings.javaUpd.unknown");
        } else if (e.updateAvailable) {
            status = SharedLocale.tr("settings.javaUpd.newer", e.latest);
            warn = true;
        } else {
            status = SharedLocale.tr("settings.javaUpd.upToDate");
        }
        if (e.pinned) status = SharedLocale.tr("settings.javaUpd.pinned") + (e.updateAvailable ? ". " + status : "");
        if (!e.isLauncher()) {
            status += "\n" + (e.usedBy.isEmpty() ? SharedLocale.tr("settings.javaUpd.unused")
                    : SharedLocale.tr("settings.javaUpd.usedBy", String.join(", ", e.usedBy)));
        }
        final JTextArea info = LookUi.body(status, true);
        if (warn) info.setForeground(LookTheme.palette().warn);

        final JButton upd = new JButton(SharedLocale.tr("settings.javaUpd.update"));
        final JButton rb = new JButton(SharedLocale.tr("settings.javaUpd.rollback"));
        LookUi.secondary(upd);
        LookUi.secondary(rb);
        upd.setEnabled(e.canUpdate && (e.updateAvailable || e.pinned));
        if (e.isLauncher()) {
            rb.setEnabled(false);
            rb.setToolTipText(SharedLocale.tr("settings.javaUpd.launcherNoRollback"));
        } else {
            rb.setEnabled(e.canRollback);
            rb.setToolTipText(e.canRollback ? SharedLocale.tr("settings.javaUpd.rollbackTip", e.prevVersion)
                    : SharedLocale.tr("settings.javaUpd.noRollback"));
        }

        upd.addActionListener(a -> {
            upd.setEnabled(false);
            rb.setEnabled(false);
            info.setText(SharedLocale.tr("settings.javaUpd.updating", 0));
            launcher.getExecutor().submit(() -> {
                try {
                    if (e.isLauncher()) {
                        // вікно «Оновлюю лаунчер», далі лаунчер закривається сам; false: оновлювати нічого
                        if (!com.gummer.launcher.util.LauncherJreUpdate.startNow(launcher, ConfigurationDialogFlat.this)) {
                            SwingUtilities.invokeLater(() -> reloadJavaRows(rows));
                        }
                    } else {
                        com.gummer.launcher.util.JavaUpdates.update(launcher, e.component, e.os, pct ->
                                SwingUtilities.invokeLater(() -> info.setText(SharedLocale.tr("settings.javaUpd.updating", pct))));
                        SwingUtilities.invokeLater(() -> reloadJavaRows(rows));
                    }
                } catch (Exception ex) {
                    javaActionFailed(rows, ex);
                }
                return null;
            });
        });
        rb.addActionListener(a -> {
            upd.setEnabled(false);
            rb.setEnabled(false);
            launcher.getExecutor().submit(() -> {
                try {
                    com.gummer.launcher.util.JavaUpdates.rollback(launcher, e.component, e.os);
                    SwingUtilities.invokeLater(() -> {
                        JOptionPane.showMessageDialog(ConfigurationDialogFlat.this,
                                SharedLocale.tr("settings.javaUpd.rolledBack", e.prevVersion),
                                SharedLocale.tr("settings.javaUpd.title"), JOptionPane.INFORMATION_MESSAGE);
                        reloadJavaRows(rows);
                    });
                } catch (Exception ex) {
                    javaActionFailed(rows, ex);
                }
                return null;
            });
        });

        r.add(name, "wmin 0");
        r.add(upd, "spany 2, aligny center, h 30!");
        r.add(rb, "spany 2, aligny center, h 30!, wrap");
        r.add(info, "wmin 0");
        return r;
    }

    private void javaActionFailed(final JPanel rows, final Exception ex) {
        java.util.logging.Logger.getLogger(ConfigurationDialogFlat.class.getName())
                .log(java.util.logging.Level.WARNING, "Java update action failed", ex);
        final String msg = ex instanceof com.gummer.launcher.util.JavaUpdates.InUseException
                ? SharedLocale.tr("settings.javaUpd.inUse")
                : SharedLocale.tr("settings.javaUpd.failed", String.valueOf(ex.getMessage()));
        SwingUtilities.invokeLater(() -> {
            JOptionPane.showMessageDialog(ConfigurationDialogFlat.this, msg, SharedLocale.tr("settings.javaUpd.title"),
                    JOptionPane.WARNING_MESSAGE);
            reloadJavaRows(rows);
        });
    }

    /** Сторінка розділу: картки одна під одною. */
    private static JPanel page() {
        JPanel p = new JPanel(new MigLayout("insets 0 0 14 4, wrap 1, gap 0 12, fillx", "[grow,fill]", ""));
        p.setOpaque(false);
        return p;
    }

    private static JScrollPane scroll(JPanel page) {
        // ширина сторінки йде за вікном, прокрутка лише вертикальна
        JPanel track = new ScrollTrack(new BorderLayout());
        track.setOpaque(false);
        track.add(page, BorderLayout.NORTH);
        JScrollPane sp = new JScrollPane(track, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        sp.setBorder(null);
        sp.setOpaque(false);
        sp.getViewport().setOpaque(false);
        sp.getVerticalScrollBar().setUnitIncrement(18);
        return sp;
    }

    /** Панель, що в прокрутці тягнеться по ширині (без горизонтальної прокрутки). */
    private static final class ScrollTrack extends JPanel implements Scrollable {
        ScrollTrack(LayoutManager l) {
            super(l);
        }

        @Override
        public Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }

        @Override
        public int getScrollableUnitIncrement(Rectangle r, int o, int d) {
            return 18;
        }

        @Override
        public int getScrollableBlockIncrement(Rectangle r, int o, int d) {
            return r.height - 36;
        }

        @Override
        public boolean getScrollableTracksViewportWidth() {
            return true;
        }

        @Override
        public boolean getScrollableTracksViewportHeight() {
            return false;
        }
    }

    // ---------------------------------------------------------- Видалення (1.36.3)

    /**
     * Дві червоні кнопки (19.11.2026, власник: «кнопку (червону, щоб випадково не натиснули) ... щоб гравець не мусів
     * все руками чистити»). Від випадкового натискання: червоний контур замість заливки, вікно підтвердження з
     * розміром, кнопка «Видалити» вмикається лише після галочки «Розумію, що це не можна скасувати». Що саме
     * видаляється: {@link com.gummer.launcher.util.Uninstall}.
     */
    private JPanel createDangerCard() {
        JPanel c = section(SharedLocale.tr("settings.danger.title"), SharedLocale.tr("settings.danger.desc"));
        JButton builds = LookUi.danger(new JButton(SharedLocale.tr("settings.danger.builds")));
        JButton all = LookUi.danger(new JButton(SharedLocale.tr("settings.danger.all")));
        JPanel row = new JPanel(new MigLayout("insets 0, gap 8 8", "", ""));
        row.setOpaque(false);
        row.add(builds);
        row.add(all);
        c.add(row, "gaptop 8, growx 0");
        builds.addActionListener(e -> askDelete(false));
        all.addActionListener(e -> askDelete(true));
        return c;
    }

    private void askDelete(boolean everything) {
        if (com.gummer.launcher.util.Uninstall.RUNNING_GAMES.get() > 0) {
            JOptionPane.showMessageDialog(this, SharedLocale.tr("settings.danger.gameRunning"),
                    SharedLocale.tr("settings.danger.title"), JOptionPane.WARNING_MESSAGE);
            return;
        }
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        launcher.getExecutor().submit(() -> {
            final com.gummer.launcher.util.Uninstall.Plan plan;
            long backups = 0;
            File base = launcher.getBaseDir();
            if (everything) {
                plan = com.gummer.launcher.util.Uninstall.everything(base, bootstrapDirFor(base), ownAppDir(base));
                com.gummer.launcher.util.Uninstall.addLinuxShortcuts(plan);
            } else {
                plan = com.gummer.launcher.util.Uninstall.builds(base, false);
                File wb = new File(base, com.gummer.launcher.manage.Worlds.BACKUPS);
                backups = wb.isDirectory() ? com.gummer.launcher.util.Uninstall.size(wb) : 0;
            }
            final long backupBytes = backups;
            SwingUtilities.invokeLater(() -> {
                setCursor(Cursor.getDefaultCursor());
                showDeleteDialog(everything, plan, backupBytes);
            });
        });
    }

    /** Тека bootstrap.yml у Документах: лише якщо вона вказує саме на цю теку даних (портативний лаунчер чужу не чіпає). */
    private static File bootstrapDirFor(File base) {
        try {
            File boot = new File(com.gummer.launcher.shared.SharedData.getFileChooseDefaultDir(), "GummerCraft");
            File wd = BootstrapConfig.get().getWorkingDirectory();
            if (wd != null && wd.getCanonicalFile().equals(base.getCanonicalFile())) return boot;
        } catch (Exception ignored) {
            // нема bootstrap.yml: нема що прибирати
        }
        return null;
    }

    /** Розпакований лаунчер з Java, лише якщо цей лаунчер запущено саме з нього. */
    private static File ownAppDir(File base) {
        File app = com.gummer.launcher.util.Uninstall.appDir(base);
        if (app == null) return null;
        try {
            String home = new File(System.getProperty("java.home")).getCanonicalPath() + File.separator;
            return home.startsWith(app.getCanonicalPath() + File.separator) ? app : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static String size(long bytes) {
        return com.gummer.launcher.util.Uninstall.human(bytes, SharedLocale.tr("settings.danger.gb"),
                SharedLocale.tr("settings.danger.mb"));
    }

    private void showDeleteDialog(boolean everything, com.gummer.launcher.util.Uninstall.Plan plan, long backupBytes) {
        String k = everything ? "settings.danger.all" : "settings.danger.builds";
        JPanel body = new JPanel(new MigLayout("insets 0, wrap 1, gap 0 8, fillx", "[grow,fill]", ""));
        body.setOpaque(false);
        body.add(LookUi.body(SharedLocale.tr(k + "Text"), true), "w 460!, wmin 0");
        JLabel sizeLabel = new JLabel(SharedLocale.tr("settings.danger.size", size(plan.bytes)));
        sizeLabel.setFont(sizeLabel.getFont().deriveFont(Font.BOLD));
        body.add(sizeLabel, "gaptop 4");
        final JCheckBox withBackups = new JCheckBox(SharedLocale.tr("settings.danger.backups", size(backupBytes)));
        withBackups.setOpaque(false);
        if (!everything && backupBytes > 0) {
            body.add(withBackups);
            withBackups.addActionListener(e -> sizeLabel.setText(SharedLocale.tr("settings.danger.size",
                    size(plan.bytes + (withBackups.isSelected() ? backupBytes : 0)))));
        }
        JCheckBox sure = new JCheckBox(SharedLocale.tr("settings.danger.confirm"));
        sure.setOpaque(false);
        body.add(sure, "gaptop 6");

        JButton delete = LookUi.dangerSolid(new JButton(SharedLocale.tr("settings.danger.delete")));
        delete.setEnabled(false);
        JButton cancel = LookUi.secondary(new JButton(SharedLocale.tr("button.cancel")));
        sure.addActionListener(e -> delete.setEnabled(sure.isSelected()));
        JOptionPane pane = new JOptionPane(body, JOptionPane.WARNING_MESSAGE, JOptionPane.DEFAULT_OPTION, null,
                new Object[]{delete, cancel}, cancel);
        JDialog d = pane.createDialog(this, SharedLocale.tr(k + "Title"));
        delete.addActionListener(e -> pane.setValue(delete));
        cancel.addActionListener(e -> pane.setValue(cancel));
        d.setVisible(true);
        d.dispose();
        if (pane.getValue() != delete || !sure.isSelected()) return;
        if (com.gummer.launcher.util.Uninstall.RUNNING_GAMES.get() > 0) { // гру могли запустити, поки вікно було відкрите
            JOptionPane.showMessageDialog(this, SharedLocale.tr("settings.danger.gameRunning"),
                    SharedLocale.tr("settings.danger.title"), JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (everything) deleteEverything(plan);
        else deleteBuilds(withBackups.isSelected());
    }

    private void deleteBuilds(boolean withBackups) {
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        launcher.getExecutor().submit(() -> {
            // 19.11.2026: значення відеокарти в реєстрі, які записав лаунчер, прибрати разом з Java, для якої вони
            com.gummer.launcher.util.Gpu.forgetAll(launcher.getBaseDir());
            int failed = com.gummer.launcher.util.Uninstall.deleteNow(
                    com.gummer.launcher.util.Uninstall.builds(launcher.getBaseDir(), withBackups));
            SwingUtilities.invokeLater(() -> {
                setCursor(Cursor.getDefaultCursor());
                String msg = failed == 0 ? SharedLocale.tr("settings.danger.buildsDone")
                        : SharedLocale.tr("settings.danger.buildsPartial", failed);
                JOptionPane.showMessageDialog(this, msg, SharedLocale.tr("settings.danger.builds"),
                        failed == 0 ? JOptionPane.INFORMATION_MESSAGE : JOptionPane.WARNING_MESSAGE);
                // у пам'яті лишились списки збірок і кешів: найпростіше і найнадійніше почати з чистого запуску
                if (!SelfRestart.relaunchBootstrap()) {
                    JOptionPane.showMessageDialog(this, SharedLocale.tr("settings.danger.reopen"),
                            SharedLocale.tr("settings.danger.builds"), JOptionPane.INFORMATION_MESSAGE);
                    System.exit(0);
                }
            });
        });
    }

    private void deleteEverything(com.gummer.launcher.util.Uninstall.Plan plan) {
        try {
            com.gummer.launcher.util.Gpu.forgetAll(launcher.getBaseDir()); // 19.11.2026: записи відеокарти в реєстрі
            com.gummer.launcher.util.Uninstall.deleteAfterExit(plan);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, SharedLocale.tr("settings.danger.failed", String.valueOf(e.getMessage())),
                    SharedLocale.tr("settings.danger.all"), JOptionPane.ERROR_MESSAGE);
            return;
        }
        JOptionPane.showMessageDialog(this, SharedLocale.tr("settings.danger.allBye"), SharedLocale.tr("settings.danger.all"),
                JOptionPane.INFORMATION_MESSAGE);
        System.exit(0);
    }

    /** Картка розділу з заголовком і (необов'язково) поясненням. */
    private static JPanel section(String title, String desc) {
        JPanel c = LookUi.card("insets 16 18 16 18, wrap 1, gap 0 4, fillx", "[grow,fill]", "");
        c.add(LookUi.title(title, 15f));
        if (desc != null) c.add(LookUi.body(desc, false), "wmin 0");
        return c;
    }

    private static JLabel label(String text) {
        JLabel l = new JLabel(text);
        l.setForeground(LookTheme.palette().text2);
        return l;
    }

    private static void aboutRow(JPanel p, String key, String value) {
        JLabel k = new JLabel(key);
        k.setForeground(LookTheme.palette().text3);
        k.setFont(k.getFont().deriveFont(12f));
        JLabel v = new JLabel(value);
        v.setForeground(LookTheme.palette().text2);
        v.setFont(v.getFont().deriveFont(12f));
        p.add(k);
        p.add(v, "wmin 0, wrap");
    }

    /**
     * Вибір вигляду лаунчера (16.11.2026): Liquid Glass, Класика, Бібліотека. Зміна зберігає налаштування
     * (як вибір мови) і одразу перевідкриває головне вікно в новому вигляді, без перезапуску лаунчера.
     */
    private JComboBox<String> createLookBox() {
        final String[] ids = {com.gummer.launcher.FancyLauncher.LOOK_GLASS, com.gummer.launcher.FancyLauncher.LOOK_CLASSIC,
                com.gummer.launcher.FancyLauncher.LOOK_LIBRARY};
        final String[] names = {SharedLocale.tr("options.look.glass"), SharedLocale.tr("options.look.classic"),
                SharedLocale.tr("options.look.library")};
        final JComboBox<String> box = new JComboBox<>(names);
        int current = java.util.Arrays.asList(ids).indexOf(config.getLauncherLook());
        box.setSelectedIndex(current >= 0 ? current : 0);
        box.setToolTipText(SharedLocale.tr("options.look.tooltip"));
        box.addActionListener(e -> {
            String chosen = ids[Math.max(0, box.getSelectedIndex())];
            if (chosen.equals(config.getLauncherLook())) return;
            config.setLauncherLook(chosen);
            save(); // зберігає всі налаштування і закриває діалог
            com.gummer.launcher.FancyLauncher.reopenLauncherWindow(launcher, LauncherFrameFlat.rootFrame);
        });
        return box;
    }

    public void save() {
        mapper.copyFromSwing();
        config.setJavaRuntime((JavaRuntime) jvmRuntime.getSelectedItem());

        Persistence.commitAndForget(config);
        dispose();
    }

    static class JavaRuntimeFileFilter extends FileFilter {
        @Override
        public boolean accept(File f) {
            return f.isDirectory() || f.getName().startsWith("java") && f.canExecute();
        }

        @Override
        public String getDescription() {
            return "Java runtime executables";
        }
    }

    static {
        // Assuming Lang has a method or field that returns a code or name in lowercase
        for (Lang lang : Lang.values()) {
            // Replace getCode() with the appropriate method or use lang.name().toLowerCase()
            String langCode = lang.getId(); // or: lang.name().toLowerCase()
            String iconPath = "ui/icon/color/" + langCode + ".png";
            // Load the image icon from resources (make sure the resource exists)
            ImageIcon icon = new ImageIcon(
                Objects.requireNonNull(
                    SwingExtras.readIconImage(Launcher.class, iconPath),
                    "Icon resource not found: " + iconPath
                )
            );
            flagIcons.put(lang, icon);
        }
    }


}
