/*
 * SK's Minecraft Launcher
 * Copyright (C) 2010-2014 Albert Pham <http://www.sk89q.com> and contributors
 * Copyright (C) 2021-2025 GummerCraft <http://gummercraft.fun>
 * Please see LICENSE.txt for license information.
 */

package com.skcraft.launcher.dialog;

import com.gummer.launcher.dialog.RestartRequiredDialog;
import com.gummer.launcher.swing.SwingExtras;
import com.skcraft.launcher.Configuration;
import com.skcraft.launcher.Launcher;
import com.gummer.launcher.i18n.I18n;
import com.gummer.launcher.i18n.Lang;
import com.gummer.launcher.i18n.LanguageDialog;
import com.skcraft.launcher.launch.runtime.AddJavaRuntime;
import com.skcraft.launcher.launch.runtime.JavaRuntime;
import com.skcraft.launcher.launch.runtime.JavaRuntimeFinder;
import com.skcraft.launcher.persistence.Persistence;
import com.gummer.launcher.shared.BootstrapConfig;
import com.skcraft.launcher.swing.*;
import com.gummer.launcher.util.FlatClientProperties;
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
import java.util.Arrays;
import java.util.Objects;

import static com.gummer.launcher.util.FlatClientProperties.TABBED_PANE_TAB_WIDTH_MODE_EQUAL;

/**
 * A dialog to modify configuration options.
 */
public class ConfigurationDialog extends JDialog {

    private final Launcher launcher;

    private final Configuration config;
    private final ObjectSwingMapper mapper;

    private final JPanel tabContainer = new JPanel(new BorderLayout());
    private final JTabbedPane tabbedPane = new JTabbedPane();
    private final FormPanel generalSettingsPanel = new FormPanel();
    private final FormPanel javaSettingsPanel = new FormPanel();
    //private final JComboBox<JavaRuntime> jvmRuntime = new BetterComboBox<>();
    private final JComboBox<JavaRuntime> jvmRuntime = new JComboBox<>();
    private final JTextField jvmArgsText = new JTextField();
    private final JSlider memorySlider = new JSlider();
    private final JSpinner minMemorySpinner = new JSpinner();
    private final JSpinner maxMemorySpinner = new JSpinner();
    private final JSpinner permGenSpinner = new JSpinner();
    private final FormPanel gameSettingsPanel = new FormPanel();
    private final JCheckBox showConsoleCheck = new JCheckBox(SharedLocale.tr("options.showConsole"));
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
    private final JTextField installDirText = new JTextField(BootstrapConfig.get().getWorkingDirectory().toString());
    private final JTextField hwIdentity = new JTextField();
    private final LinedBoxPanel buttonsPanel = new LinedBoxPanel(true);

    private final JButton okButton = new JButton(SharedLocale.tr("button.save"));
    private final JButton cancelButton = new JButton(SharedLocale.tr("button.cancel"));
    private final JButton aboutButton = new JButton(SharedLocale.tr("options.about"));
    private final JButton logButton = new JButton(SharedLocale.tr("options.launcherConsole"));
    private final JButton languageButton = new JButton(SharedLocale.tr("options.lang"));

    private final static ImageIcon folderIcon = new ImageIcon(Objects.requireNonNull(SwingExtras.readIconImage(Launcher.class, "ui/icon/light/folder.png")));
    //private final ImageIcon settingsIcon = new ImageIcon(Objects.requireNonNull(SwingExtras.readIconImage(Launcher.class, "ui/icon/light/settings.png")));
    //private final ImageIcon javaIcon = new ImageIcon(Objects.requireNonNull(SwingExtras.readIconImage(Launcher.class, "ui/icon/light/java.png")));
    //private final ImageIcon invaderIcon = new ImageIcon(Objects.requireNonNull(SwingExtras.readIconImage(Launcher.class, "ui/icon/light/invader.png")));

    /**
     * Create a new configuration dialog.
     *
     * @param owner the window owner
     * @param launcher the launcher
     */
    public ConfigurationDialog(Window owner, @NonNull Launcher launcher) {
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
        setSize(new Dimension(400, 550));
        setResizable(false);
        setLocationRelativeTo(owner);

        mapper.map(jvmArgsText, "jvmArgs");
        //mapper.map(minMemorySpinner, "minMemory"); // use slider instead
        //mapper.map(maxMemorySpinner, "maxMemory");
        mapper.map(memorySlider, "minMemory");
        mapper.map(memorySlider, "maxMemory");
        mapper.map(permGenSpinner, "permGen");
        mapper.map(showConsoleCheck, "showConsole");
        mapper.map(widthSpinner, "windowWidth");
        mapper.map(heightSpinner, "windowHeight");
        mapper.map(useProxyCheck, "proxyEnabled");
        mapper.map(proxyHostText, "proxyHost");
        mapper.map(proxyPortText, "proxyPort");
        mapper.map(proxyUsernameText, "proxyUsername");
        mapper.map(proxyPasswordText, "proxyPassword");
        mapper.map(gameKeyText, "gameKey");
        mapper.map(hwIdentity, "uuid");

        mapper.copyFromObject();
    }

    private void initComponents() {

        //
        // generalSettingsPanel
        //

        // homedir
        JLabel homedirTitleLabel = new JLabel(SharedLocale.tr("options.dataAndHomedir"));
        homedirTitleLabel.putClientProperty("FlatLaf.styleClass", "h2");
        generalSettingsPanel.addRow(homedirTitleLabel);
        generalSettingsPanel.addRow(new JLabel(SharedLocale.tr("options.installDirDesc")));
        installDirText.putClientProperty( FlatClientProperties.TEXT_FIELD_TRAILING_ICON, folderIcon);
        generalSettingsPanel.addRow(installDirText);
        JLabel installDirDesc2Info = new JLabel(SharedLocale.tr("options.installDirDesc2"));
        installDirDesc2Info.putClientProperty("FlatLaf.styleClass", "medium");
        generalSettingsPanel.addRow(installDirDesc2Info);

        generalSettingsPanel.addRow(Box.createVerticalStrut(15));

        // console and logs
        JLabel consoleTitleLabel = new JLabel(SharedLocale.tr("options.consoleAndLogTitle"));
        consoleTitleLabel.putClientProperty("FlatLaf.styleClass", "h2");
        generalSettingsPanel.addRow(consoleTitleLabel);
        generalSettingsPanel.addRow(showConsoleCheck);
        generalSettingsPanel.addRow(logButton);

        generalSettingsPanel.addRow(Box.createVerticalStrut(15));

        // language
        JLabel langTitleLabel = new JLabel(SharedLocale.tr("options.launcherLang"));
        langTitleLabel.putClientProperty("FlatLaf.styleClass", "h2");
        generalSettingsPanel.addRow(langTitleLabel);

        Lang lang = Lang.getFromName(launcher.getConfig().getLang());
        String[] stl = new String[Lang.values().length];
        for (int i = 0; i < Lang.values().length; i++) {
            stl[i] = Lang.values()[i].getName() + "  " + Lang.values()[i].getTranslataor();
        }
        JComboBox<String> language_selector = new JComboBox<String>(stl);
        for (int i = 0; i < Lang.values().length; i++) {
            if (lang == Lang.values()[i]) {
                language_selector.setSelectedItem(stl[i]);
            }
        }
        generalSettingsPanel.addRow(language_selector);
        JLabel langChangeRestartInfo = new JLabel(SharedLocale.tr("options.needrestartforsure"));
        langChangeRestartInfo.putClientProperty("FlatLaf.styleClass", "medium");
        generalSettingsPanel.addRow(langChangeRestartInfo);

        SwingHelper.removeOpaqueness(generalSettingsPanel);
//        tabbedPane.addTab(
//                SharedLocale.tr("options.generalTab"),
//                SwingHelper.alignTabbedPane(generalSettingsPanel)
//        );
        SwingExtras.addTabWidthMode(
            tabbedPane,
            SharedLocale.tr("options.generalTab"),
            TABBED_PANE_TAB_WIDTH_MODE_EQUAL,
            //settingsIcon,
            SwingHelper.alignTabbedPane(generalSettingsPanel)
        );

        //
        // javaSettingsPanel
        //

        JLabel jvmTitleLabel = new JLabel(SharedLocale.tr("options.javaParamsSectionTitle"));
        jvmTitleLabel.putClientProperty("FlatLaf.styleClass", "h2");
        javaSettingsPanel.addRow(jvmTitleLabel);
        jvmArgsText.putClientProperty("JTextField.placeholderText", SharedLocale.tr("options.autoPlaceholder"));
        javaSettingsPanel.addRow(new JLabel(SharedLocale.tr("options.jvmPath")), jvmRuntime);
        javaSettingsPanel.addRow(new JLabel(SharedLocale.tr("options.jvmArguments")), jvmArgsText);

        javaSettingsPanel.addRow(Box.createVerticalStrut(15));

        JLabel memlimitTitleLabel = new JLabel(SharedLocale.tr("options.javaMemSectionTitle"));
        memlimitTitleLabel.putClientProperty("FlatLaf.styleClass", "h2");
        javaSettingsPanel.addRow(memlimitTitleLabel);

        //updateCheck.setVisible(false);

        // add memory slider to window
        //javaSettingsPanel.addRow(new JLabel(SharedLocale.tr("options.minMemory")), minMemorySpinner);
        //javaSettingsPanel.addRow(new JLabel(SharedLocale.tr("options.maxMemory")), maxMemorySpinner);
        //javaSettingsPanel.addRow(Box.createVerticalStrut(5));
        javaSettingsPanel.addRow(minMemorySpinner);
        javaSettingsPanel.addRow(maxMemorySpinner);
        minMemorySpinner.setVisible(false);
        maxMemorySpinner.setVisible(false);
        javaSettingsPanel.addRow(Box.createVerticalStrut(2));
        SwingExtras.configureMemorySlider(memorySlider, minMemorySpinner, maxMemorySpinner);
        javaSettingsPanel.addRow(memorySlider);
        javaSettingsPanel.addRow(Box.createVerticalStrut(5));
        javaSettingsPanel.addRow(new JLabel(SharedLocale.tr("options.64BitJavaWarning")));

        SwingHelper.removeOpaqueness(javaSettingsPanel);
        //tabbedPane.addTab(SharedLocale.tr("options.javaTab"), SwingHelper.alignTabbedPane(javaSettingsPanel));
        SwingExtras.addTabWidthMode(
            tabbedPane,
            SharedLocale.tr("options.javaTab"),
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

        JLabel devTitleLabel = new JLabel(SharedLocale.tr("options.devBetaTitle"));
        devTitleLabel.putClientProperty("FlatLaf.styleClass", "h2");
        advancedPanel.addRow(devTitleLabel);
        advancedPanel.addRow(new JLabel(SharedLocale.tr("options.devBetaDesc")));
        advancedPanel.addRow(gameKeyText);


        /*
        hide uuid field for now
        advancedPanel.addRow(new JLabel(SharedLocale.tr("options.identify")), hwIdentity);
        hwIdentity.setEditable(false);
        */

        SwingHelper.removeOpaqueness(advancedPanel);
        //tabbedPane.addTab(SharedLocale.tr("options.advancedTab"), SwingHelper.alignTabbedPane(advancedPanel));
        SwingExtras.addTabWidthMode(
            tabbedPane,
            SharedLocale.tr("options.advancedTab"),
            TABBED_PANE_TAB_WIDTH_MODE_EQUAL,
            //invaderIcon,
            SwingHelper.alignTabbedPane(advancedPanel)
        );


        //
        // window buttons
        //

        //buttonsPanel.addElement(logButton);
        //buttonsPanel.addElement(aboutButton); // hide about button
        //buttonsPanel.addElement(languageButton); // hide default lang button
        buttonsPanel.addElement(cancelButton);
        buttonsPanel.addGlue();
        buttonsPanel.addElement(okButton);
        //buttonsPanel.addElement(cancelButton); // hide cancel button

        tabContainer.add(tabbedPane, BorderLayout.CENTER);
        tabContainer.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        add(tabContainer, BorderLayout.CENTER);
        add(buttonsPanel, BorderLayout.SOUTH);

        SwingHelper.equalWidth(okButton, cancelButton);

        cancelButton.addActionListener(ActionListeners.dispose(this));

        language_selector.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                I18n.changeLang((Lang) Lang.values()[language_selector.getSelectedIndex()], launcher);
            }
        });

        aboutButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                AboutDialog.showAboutDialog(ConfigurationDialog.this);
            }
        });

        languageButton.addActionListener(e -> LanguageDialog.showLanguageDialog(ConfigurationDialog.this, launcher));

        okButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                save();
            }
        });

        logButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                ConsoleFrame.showMessages();
            }
        });


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
                        RestartRequiredDialog.show(ConfigurationDialog.this);
                    }
                });
                fc.showDialog(ConfigurationDialog.this, SharedLocale.tr("options.installDir.fileChooser.buttonAccept"));
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


}
