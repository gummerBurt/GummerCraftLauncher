/*
 * Copyright (C) 2021-2025 GummerCraft <http://gummercraft.fun>
 */

package com.gummer.launcher.dialog;

import com.formdev.flatlaf.util.SystemInfo;
import com.gummer.launcher.i18n.I18n;
import com.gummer.launcher.i18n.Lang;
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
    private final static ImageIcon folderIcon = new ImageIcon(Objects.requireNonNull(SwingExtras.readIconImage(Launcher.class, "ui/icon/light/folder.png")));
    private final static Map<Lang, ImageIcon> flagIcons = new HashMap<>();

    /**
     * Create a new configuration dialog.
     *
     * @param owner the window owner
     * @param launcher the launcher
     */
    public ConfigurationDialogFlat(Window owner, @NonNull Launcher launcher, LauncherFrameFlat window) {
        super(owner, ModalityType.DOCUMENT_MODAL);

        this.launcher = launcher;

        this.config = launcher.getConfig();
        mapper = new ObjectSwingMapper(config);

        JavaRuntime[] javaRuntimes = JavaRuntimeFinder.getAvailableRuntimes().toArray(new JavaRuntime[0]);
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
        if( SystemInfo.isMacOS ) {
            setSize(new Dimension(430, 560));
        } else {
            setSize(new Dimension(400, 560));
        }
        setResizable(false);
        setLocationRelativeTo(owner);

        mapper.map(jvmArgsText, "jvmArgs");
        //mapper.map(minMemorySpinner, "minMemory"); // use slider instead
        //mapper.map(maxMemorySpinner, "maxMemory");
        mapper.map(memorySlider, "minMemory");
        mapper.map(memorySlider, "maxMemory");
        mapper.map(permGenSpinner, "permGen");
        mapper.map(enforceRuntimeCheck, "enforceRuntime");
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

    private void initComponents() {
        // style components
        logButton.putClientProperty("FlatLaf.styleClass", "small");
        okButton.putClientProperty("FlatLaf.styleClass", "large");
        cancelButton.putClientProperty("FlatLaf.styleClass", "large");

        installDirDescInfo.putClientProperty("FlatLaf.styleClass", "large");
        //consoleUseColorsCheck.putClientProperty("FlatLaf.styleClass", "large");
        //showConsoleCheck.putClientProperty("FlatLaf.styleClass", "large");
        //showLauncherConsoleCheck.putClientProperty("FlatLaf.styleClass", "large");
        bitsWarn.putClientProperty("FlatLaf.styleClass", "large");
        devBetaDesc.putClientProperty("FlatLaf.styleClass", "large");

        //langChangeRestartInfo.putClientProperty("FlatLaf.styleClass", "medium");
        //installDirDesc2Info.putClientProperty("FlatLaf.styleClass", "medium");

        homedirTitleLabel.putClientProperty("FlatLaf.styleClass", "h2");
        consoleTitle.putClientProperty("FlatLaf.styleClass", "h2");
        langTitle.putClientProperty("FlatLaf.styleClass", "h2");
        jvmTitleLabel.putClientProperty("FlatLaf.styleClass", "h2");
        memlimitTitleLabel.putClientProperty("FlatLaf.styleClass", "h2");
        devTitleLabel.putClientProperty("FlatLaf.styleClass", "h2");
        buildInfoTitle.putClientProperty("FlatLaf.styleClass", "h2");

        memorySlider.putClientProperty("FlatLaf.styleClass", "large");

        // init lang selector
        JComboBox<Lang> languageSelector = new JComboBox<>(Lang.values());
        languageSelector.setSelectedItem(Lang.getFromName(launcher.getConfig().getLang())); // setting the selected lang

        languageSelector.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                          boolean isSelected, boolean cellHasFocus) {
                JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof Lang) {
                    Lang langValue = (Lang) value;
                    // Format the text as desired
                    label.setText(" " + langValue.getName() + "  " + langValue.getTranslataor());
                    // Get the flag icon from our mapping
                    label.setIcon(flagIcons.get(langValue));
                }
                label.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 25));
                return label;
            }
        });

        // populate build info
        Properties launcherProps = launcher.getProperties();
        final JLabel buildVersion = new JLabel(launcherProps.getProperty("version", "N/A"));
        final JLabel pipelineId = new JLabel(launcherProps.getProperty("pipelineId", "N/A"));
        final JLabel branch = new JLabel(launcherProps.getProperty("branch", "N/A"));
        final JLabel commit = new JLabel(launcherProps.getProperty("commit", "N/A"));
        final JLabel targetJvm = new JLabel(launcherProps.getProperty("targetJvm", "N/A"));

        //
        // generalSettingsPanel
        //

        // homedir
        generalSettingsPanel.addRow(homedirTitleLabel);
        generalSettingsPanel.addRow(installDirDescInfo);
        generalSettingsPanel.addRow(Box.createVerticalStrut(1));
        installDirText.putClientProperty( FlatClientProperties.TEXT_FIELD_TRAILING_ICON, folderIcon);
        generalSettingsPanel.addRow(installDirText);
        //generalSettingsPanel.addRow(Box.createVerticalStrut(1));
        generalSettingsPanel.addRow(installDirDesc2Info);

        generalSettingsPanel.addRow(Box.createVerticalStrut(21));

        // console params
        // Create a panel just for the label and style it
        JPanel consoleTitlePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        consoleTitlePanel.add(consoleTitle);
        consoleTitlePanel.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));

        // Then create another panel for the button
        JPanel consoleButtonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        consoleButtonPanel.add(logButton);
        consoleButtonPanel.setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0));
        consoleButtonPanel.setToolTipText(SharedLocale.tr("tooltip.launcherConsole"));

        // And add both panels to a parent container (with an appropriate layout)
        JPanel consoleTitleLabel = new JPanel(new BorderLayout());
        consoleTitleLabel.add(consoleTitlePanel, BorderLayout.WEST);
        consoleTitleLabel.add(consoleButtonPanel, BorderLayout.EAST);
        consoleTitleLabel.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));

        // remove extra padding
        consoleUseColorsCheck.setBorder(BorderFactory.createEmptyBorder(0, 4, 0, 0));
        enableDebugCheck.setBorder(BorderFactory.createEmptyBorder(0, 4, 0, 0));
        showLauncherConsoleCheck.setBorder(BorderFactory.createEmptyBorder(2, 4, 0, 0));
        showConsoleCheck.setBorder(BorderFactory.createEmptyBorder(0, 4, 0, 0));

        // add panel for console label + button
        generalSettingsPanel.addRow(consoleTitleLabel);
        generalSettingsPanel.addRow(showLauncherConsoleCheck);
        generalSettingsPanel.addRow(showConsoleCheck);
        //generalSettingsPanel.addRow(consoleUseColorsCheck);
        generalSettingsPanel.addRow(enableDebugCheck);

        generalSettingsPanel.addRow(Box.createVerticalStrut(21));

        // language
        // Create a panel just for the label and style it
        JPanel langTitlePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        langTitlePanel.add(langTitle);
        langTitlePanel.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));

        // Then create another panel for the button
        JPanel langSelectorPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        langSelectorPanel.add(languageSelector);
        langSelectorPanel.setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0));

        // And add both panels to a parent container (with an appropriate layout)
        JPanel langTitleLabel = new JPanel(new BorderLayout());
        langTitleLabel.add(langTitlePanel, BorderLayout.WEST);
        langTitleLabel.add(langSelectorPanel, BorderLayout.EAST);
        langTitleLabel.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));

        // add titile + dropdown to panel
        generalSettingsPanel.addRow(langTitleLabel);

        // add restart notice label
        langChangeRestartInfo.setHorizontalAlignment(SwingConstants.CENTER);
        JPanel langChangeRestartInfoPanel = new JPanel(new BorderLayout());
        langChangeRestartInfoPanel.add(langChangeRestartInfo, BorderLayout.CENTER);
        langChangeRestartInfoPanel.setBorder(BorderFactory.createEmptyBorder(8, 0, 0, 0));
        langChangeRestartInfo.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
        //generalSettingsPanel.addRow(langChangeRestartInfoPanel);

        SwingHelper.removeOpaqueness(generalSettingsPanel);
        SwingExtras.addTabWidthMode(
            tabbedPane,
            generalTabTitle,
            TABBED_PANE_TAB_WIDTH_MODE_EQUAL,
            //settingsIcon,
            SwingHelper.alignTabbedPane(generalSettingsPanel)
        );

        //
        // javaSettingsPanel
        //


        javaSettingsPanel.addRow(jvmTitleLabel);
        javaSettingsPanel.addRow(Box.createVerticalStrut(3));
        jvmArgsText.putClientProperty("JTextField.placeholderText", SharedLocale.tr("options.automaticPlaceholder"));
        enforceRuntimeCheck.setBorder(BorderFactory.createEmptyBorder(3, 0, 0, 0));
        javaSettingsPanel.addRow(new JLabel(SharedLocale.tr("options.jvmPath")), jvmRuntime);
        javaSettingsPanel.addRow(new JLabel(SharedLocale.tr("options.jvmArguments")), jvmArgsText);
        javaSettingsPanel.addRow(enforceRuntimeCheck);


        javaSettingsPanel.addRow(Box.createVerticalStrut(15));

        javaSettingsPanel.addRow(memlimitTitleLabel);
        // add memory slider to window
        javaSettingsPanel.addRow(minMemorySpinner);
        javaSettingsPanel.addRow(maxMemorySpinner);
        minMemorySpinner.setVisible(false);
        maxMemorySpinner.setVisible(false);
        javaSettingsPanel.addRow(Box.createVerticalStrut(3));
        SwingExtras.configureMemorySlider(memorySlider, minMemorySpinner, maxMemorySpinner);
        javaSettingsPanel.addRow(memorySlider);
        javaSettingsPanel.addRow(Box.createVerticalStrut(5));
        javaSettingsPanel.addRow(bitsWarn);

        SwingHelper.removeOpaqueness(javaSettingsPanel);
        SwingExtras.addTabWidthMode(
            tabbedPane,
            jvmTabTitle,
            TABBED_PANE_TAB_WIDTH_MODE_EQUAL,
            //javaIcon,
            SwingHelper.alignTabbedPane(javaSettingsPanel)
        );

        //
        // gameSettingsPanel
        //

        gameSettingsPanel.addRow(new JLabel(SharedLocale.tr("options.windowWidth")), widthSpinner);
        gameSettingsPanel.addRow(new JLabel(SharedLocale.tr("options.windowHeight")), heightSpinner);

        SwingHelper.removeOpaqueness(gameSettingsPanel);
        // hide game tab
        //tabbedPane.addTab(SharedLocale.tr("options.minecraftTab"), SwingHelper.alignTabbedPane(gameSettingsPanel));

        //
        // proxySettingsPanel
        //

        proxySettingsPanel.addRow(useProxyCheck);
        proxySettingsPanel.addRow(new JLabel(SharedLocale.tr("options.proxyHost")), proxyHostText);
        proxySettingsPanel.addRow(new JLabel(SharedLocale.tr("options.proxyPort")), proxyPortText);
        proxySettingsPanel.addRow(new JLabel(SharedLocale.tr("options.proxyUsername")), proxyUsernameText);
        proxySettingsPanel.addRow(new JLabel(SharedLocale.tr("options.proxyPassword")), proxyPasswordText);

        SwingHelper.removeOpaqueness(proxySettingsPanel);
        // hide proxy tab
        // tabbedPane.addTab(SharedLocale.tr("options.proxyTab"), SwingHelper.alignTabbedPane(proxySettingsPanel));

        //
        // advancedPanel
        //

        advancedPanel.addRow(devTitleLabel);
        advancedPanel.addRow(devBetaDesc);
        //gameKeyText.setLineWrap(true);
        //gameKeyText.setWrapStyleWord(true);
        gameKeyText.setEditable(true);
        gameKeyText.putClientProperty("JTextField.placeholderText", SharedLocale.tr("options.devBetaPlaceholder"));
        advancedPanel.addRow(gameKeyText);

        advancedPanel.addRow(Box.createVerticalStrut(20));

        // build info
        advancedPanel.addRow(buildInfoTitle);
        advancedPanel.addRow(Box.createVerticalStrut(5),0,0,0,0);
        advancedPanel.addRow(
            buidInfoVersion, buildVersion,
            0,5,2,3
        );
        advancedPanel.addRow(
            buidInfoPipeline, pipelineId,
            0,5,2,3
        );
        advancedPanel.addRow(
            buidInfoCommit, commit,
            0,5,2,3
        );
        advancedPanel.addRow(
            buidInfoBranch, branch,
            0,5,2,3
        );
        int runtimeCount = jvmRuntime.getItemCount();
        for (int i = 0; i < runtimeCount - 1; i++) {
            JavaRuntime runtime = jvmRuntime.getItemAt(i);

            JLabel runtimeLabel = new JLabel(runtime.toString());
            runtimeLabel.putClientProperty("FlatLaf.styleClass", "small");

            if(i == 0) {
                advancedPanel.addRow(buidInfoJvm, runtimeLabel, 0, 5, 2, 3);
            } else {
                advancedPanel.addRow(new JLabel(""), runtimeLabel, 0, 5, 2, 3);
            }
        }

        SwingHelper.removeOpaqueness(advancedPanel);
        SwingExtras.addTabWidthMode(
            tabbedPane,
            advancedTabTitle,
            TABBED_PANE_TAB_WIDTH_MODE_EQUAL,
            //invaderIcon,
            SwingHelper.alignTabbedPane(advancedPanel)
        );


        //
        // window buttons
        //

        //buttonsPanel.addElement(logButton);

        buttonsPanel.add(cancelButton, BorderLayout.WEST);
        buttonsPanel.add(okButton, BorderLayout.EAST);
        buttonsPanel.setBorder(BorderFactory.createEmptyBorder(0, 20, 17, 20));

        tabContainer.add(tabbedPane, BorderLayout.CENTER);
        tabContainer.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        add(tabContainer, BorderLayout.CENTER);
        add(buttonsPanel, BorderLayout.SOUTH);

        SwingHelper.equalWidth(okButton, cancelButton);

        languageSelector.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                I18n.changeLang((Lang) Lang.values()[languageSelector.getSelectedIndex()], launcher);
                //restartRequired("lang");
                save();
                SelfRestart.restartRequiredDialog(
                    ConfigurationDialogFlat.class,
                    SharedLocale.tr("options.needrestartforsuretitle"),
                    SharedLocale.tr("options.needrestartforsure")
                );
                //RestartRequiredDialog.show(ConfigurationDialogFlat.this);
            }
        });

        logButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                ConsoleFrame.showMessagesColor(config.isConsoleUseColors());
            }
        });

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
                        //RestartRequiredDialog.show(ConfigurationDialogFlat.this);
                        save();
                        SelfRestart.restartRequiredDialog(
                            ConfigurationDialogFlat.class,
                            SharedLocale.tr("options.needrestartforsuretitle"),
                            SharedLocale.tr("options.needrestartforsure")
                        );
                        //RestartRequiredDialog.show(ConfigurationDialogFlat.this);
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
     * Save the configuration and close the dialog.
     */
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
