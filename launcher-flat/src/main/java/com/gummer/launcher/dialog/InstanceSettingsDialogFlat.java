/*
 * Copyright (C) 2021-2025 GummerCraft <http://gummercraft.fun>
 */

package com.gummer.launcher.dialog;

import com.gummer.launcher.popup.Notification;
import com.gummer.launcher.swing.SwingExtras;
//import com.skcraft.launcher.Configuration;
import com.skcraft.launcher.Instance;
import com.skcraft.launcher.InstanceSettings;
import com.skcraft.launcher.launch.MemorySettings;
import com.skcraft.launcher.launch.runtime.JavaRuntime;
import com.skcraft.launcher.launch.runtime.JavaRuntimeFinder;
import com.skcraft.launcher.persistence.Persistence;
import com.skcraft.launcher.swing.FormPanel;
import com.skcraft.launcher.swing.LinedBoxPanel;
import com.skcraft.launcher.util.SharedLocale;
import lombok.extern.java.Log;

import javax.swing.*;
import java.awt.*;

import static com.gummer.launcher.popup.ShowNotification.showNotification;

@Log
public class InstanceSettingsDialogFlat extends JDialog {

    private final InstanceSettings settings;

    private final LinedBoxPanel formsPanel = new LinedBoxPanel(false);
    private final FormPanel memorySettingsPanel = new FormPanel();
    private final FormPanel runtimePanel = new FormPanel();

    private final JLabel jvmTitleLabel = new JLabel(SharedLocale.tr("options.javaParamsSectionTitle"));
    private final FormPanel formJvmInfoPanel = new FormPanel();
    private final FormPanel formJvmControls = new FormPanel();
    private final JLabel jvmInfoLabel = new JLabel(SharedLocale.tr("instance.options.jvmRuntimeInfo"));
    private final JToolBar memlimitToolBar = new JToolBar();
    private final JToggleButton memlimitEnableButton = new JToggleButton();
    private final JToggleButton memlimitDisableButton = new JToggleButton();
    private final JComboBox<JavaRuntime> javaRuntimeBox = new JComboBox<>();
    private final JTextField javaArgsBox = new JTextField();
    private final JLabel jvmWarning = new JLabel(SharedLocale.tr("options.64BitJavaWarning"));

    private final JLabel memlimitTitleLabel = new JLabel(SharedLocale.tr("options.javaMemSectionTitle"));
    private final FormPanel formMemInfoPanel = new FormPanel();
    private final JLabel memInfoLabel = new JLabel(SharedLocale.tr("instance.options.memlimitInfo"));
    private final JToolBar runtimeToolBar = new JToolBar();
    private final JToggleButton runtimeEnableButton = new JToggleButton();
    private final JToggleButton runtimeDisableButton = new JToggleButton();
    private final JSpinner minMemorySpinner = new JSpinner();
    private final JSpinner maxMemorySpinner = new JSpinner();
    private final JSlider memorySlider = new JSlider();

    private final LinedBoxPanel buttonsPanel = new LinedBoxPanel(true);
    private final JButton okButton = new JButton(SharedLocale.tr("button.savesm"));
    private final JButton cancelButton = new JButton(SharedLocale.tr("button.cancelsm"));

    private boolean saved = false;

    public InstanceSettingsDialogFlat(Window owner, InstanceSettings settings) {
        super(owner);
        this.settings = settings;

        setTitle(SharedLocale.tr("instance.options.title"));
        setModalityType(DEFAULT_MODALITY_TYPE);
        initComponents();
        setSize(new Dimension(400, 550));
        setResizable(false);
        setLocationRelativeTo(owner);
    }

    private void initComponents() {

        // styling components
        memlimitTitleLabel.putClientProperty("FlatLaf.styleClass", "h2");
        jvmTitleLabel.putClientProperty("FlatLaf.styleClass", "h2");
        memInfoLabel.putClientProperty("FlatLaf.styleClass", "large");
        jvmInfoLabel.putClientProperty("FlatLaf.styleClass", "large");
        jvmWarning.putClientProperty("FlatLaf.styleClass", "large");
        //jvmWarning.putClientProperty("FlatLaf.styleClass", "small");
        okButton.putClientProperty("FlatLaf.styleClass", "large");
        cancelButton.putClientProperty("FlatLaf.styleClass", "large");

        //
        // jvm panel
        //

        // TODO: Do we keep this list centrally somewhere? Or is actively refreshing good?
        JavaRuntime[] javaRuntimes = JavaRuntimeFinder.getAvailableRuntimes().toArray(new JavaRuntime[0]);
        javaRuntimeBox.setModel(new DefaultComboBoxModel<>(javaRuntimes));

        // setup toolbar
        runtimeToolBar.setFloatable(false);
        runtimeToolBar.setOpaque(true);
        runtimeToolBar.setBorder(BorderFactory.createEmptyBorder());

        // define button enable memlimit override
        runtimeEnableButton.setText(SharedLocale.tr("option.toolbar.on"));
        runtimeEnableButton.putClientProperty("FlatLaf.styleClass", "small");
        runtimeEnableButton.addActionListener(e -> jvmFeatureChange("true"));
        runtimeToolBar.add(runtimeEnableButton);

        // define button disable memlimit override
        runtimeDisableButton.setText(SharedLocale.tr("option.toolbar.off"));
        runtimeDisableButton.putClientProperty("FlatLaf.styleClass", "small");
        runtimeDisableButton.addActionListener(e -> jvmFeatureChange("false"));
        runtimeToolBar.add(runtimeDisableButton);

        // setup button group
        ButtonGroup runtimeButtonGroup = new ButtonGroup();
        runtimeButtonGroup.add(runtimeEnableButton);
        runtimeButtonGroup.add(runtimeDisableButton);

        // create panel for label + button group
        JPanel jvmTitlePanel = new JPanel(new BorderLayout());
        jvmTitlePanel.add(jvmTitleLabel, BorderLayout.WEST); // label to the left (WEST)
        jvmTitlePanel.add(runtimeToolBar, BorderLayout.EAST); // toolbar to the right (EAST)
        runtimePanel.addRow(jvmTitlePanel);

        // setup info panel
        formJvmInfoPanel.addRow(Box.createVerticalStrut(5));
        formJvmInfoPanel.addRow(jvmInfoLabel);
        formJvmInfoPanel.addRow(Box.createVerticalStrut(2));
        runtimePanel.addRow(formJvmInfoPanel);
        formJvmInfoPanel.setVisible(false); // hide InfoPanel

        // fields with labeles
        formJvmControls.addRow(new JLabel(SharedLocale.tr("instance.options.jvmRuntime")), javaRuntimeBox);
        formJvmControls.addRow(new JLabel(SharedLocale.tr("instance.options.jvmArguments")), javaArgsBox);
        formJvmControls.addRow(Box.createVerticalStrut(5));
        runtimePanel.addRow(formJvmControls);

        //
        // memory panel
        //

        // setup toolbar
        memlimitToolBar.setFloatable(false);
        memlimitToolBar.setOpaque(true);
        memlimitToolBar.setBorder(BorderFactory.createEmptyBorder());

        // define button enable memlimit override
        memlimitEnableButton.setText(SharedLocale.tr("option.toolbar.on"));
        memlimitEnableButton.putClientProperty("FlatLaf.styleClass", "small");
        memlimitEnableButton.addActionListener(e -> memlimitFeatureChange("true"));
        memlimitToolBar.add(memlimitEnableButton);

        // define button disable memlimit override
        memlimitDisableButton.setText(SharedLocale.tr("option.toolbar.off"));
        memlimitDisableButton.putClientProperty("FlatLaf.styleClass", "small");
        memlimitDisableButton.addActionListener(e -> memlimitFeatureChange("false"));
        memlimitToolBar.add(memlimitDisableButton);

        // setup button group
        ButtonGroup memlimitButtonGroup = new ButtonGroup();
        memlimitButtonGroup.add(memlimitEnableButton);
        memlimitButtonGroup.add(memlimitDisableButton);

        // create panel for label + button group
        JPanel memlimitTitlePanel = new JPanel(new BorderLayout());
        memlimitTitlePanel.add(memlimitTitleLabel, BorderLayout.WEST); // label to the left (WEST)
        memlimitTitlePanel.add(memlimitToolBar, BorderLayout.EAST); // toolbar to the right (EAST)
        memorySettingsPanel.addRow(memlimitTitlePanel); // add title panel to memorySettingsPanel

        // setup info panel
        formMemInfoPanel.addRow(Box.createVerticalStrut(5));
        formMemInfoPanel.addRow(memInfoLabel);
        formMemInfoPanel.addRow(Box.createVerticalStrut(5));
        memorySettingsPanel.addRow(formMemInfoPanel);
        formMemInfoPanel.setVisible(false); // hide InfoPanel

        // hidden raw legacy spinner, keep them to not change all globals
        memorySettingsPanel.addRow(minMemorySpinner);
        memorySettingsPanel.addRow(maxMemorySpinner);
        minMemorySpinner.setVisible(false);
        maxMemorySpinner.setVisible(false);

        // add memory slider to window
        SwingExtras.configureMemorySlider(memorySlider, minMemorySpinner, maxMemorySpinner);
        memorySettingsPanel.addRow(Box.createVerticalStrut(5));
        memorySettingsPanel.addRow(memorySlider);
        memorySettingsPanel.addRow(Box.createVerticalStrut(5));
        memorySettingsPanel.addRow(jvmWarning);
        memorySettingsPanel.addRow(Box.createVerticalStrut(2));

        //okButton.setMargin(new Insets(0, 10, 0, 10)); // fix button size by removing Margin
        buttonsPanel.addElement(cancelButton);
        buttonsPanel.addGlue();
        buttonsPanel.addElement(okButton);

        okButton.addActionListener(e -> {
            save();
            showNotification(
                LauncherFrameFlat.rootFrame,
                LauncherFrameFlat.class,
                Notification.Type.SUCCESS,
                SharedLocale.tr("notification.popup.modpack.saved")
            );
            dispose();
        });

        cancelButton.addActionListener(e -> dispose());

        formsPanel.addElement(Box.createVerticalStrut(2));
        formsPanel.addElement(runtimePanel);
        formsPanel.addElement(memorySettingsPanel);

        add(formsPanel, BorderLayout.NORTH);
        add(buttonsPanel, BorderLayout.SOUTH);

        updateComponents();
    }

    private void updateComponents() {
        if (settings.getMemorySettings() != null) {
            memorySettingsPanel.setEnabled(true);
            memlimitDisableButton.setSelected(false);
            memlimitEnableButton.setSelected(true);
            // populate values
            minMemorySpinner.setValue(settings.getMemorySettings().getMinMemory());
            maxMemorySpinner.setValue(settings.getMemorySettings().getMaxMemory());
            memorySlider.setValue((int) settings.getMemorySettings().getMaxMemory());
            // hide display info and show controls
            formMemInfoPanel.setVisible(false);
            memorySlider.setVisible(true);
            jvmWarning.setVisible(true);
        } else {
            memorySettingsPanel.setEnabled(false);
            memlimitDisableButton.setSelected(true);
            memlimitEnableButton.setSelected(false);
            // hide controls and display info
            memorySlider.setVisible(false);
            jvmWarning.setVisible(false);
            formMemInfoPanel.setVisible(true);
        }

        if (runtimeEnableButton.isSelected() || settings.getRuntime() != null || settings.getCustomJvmArgs() != null) {
            runtimeDisableButton.setSelected(false);
            runtimeEnableButton.setSelected(true);
            // populate values
            javaRuntimeBox.setSelectedItem(settings.getRuntime());
            javaArgsBox.setText(settings.getCustomJvmArgs());
            // hide display info and show controls
            formJvmInfoPanel.setVisible(false);
            formJvmControls.setVisible(true);
        } else {
            runtimeDisableButton.setSelected(true);
            runtimeEnableButton.setSelected(false);
            // hide controls and display info
            formJvmControls.setVisible(false);
            formJvmInfoPanel.setVisible(true);
        }
    }


    private void save() {
        if (memlimitEnableButton.isSelected()) {
            MemorySettings memorySettings = settings.getMemorySettings();
            memorySettings.setMinMemory((int) minMemorySpinner.getValue());
            memorySettings.setMaxMemory((int) maxMemorySpinner.getValue());
        } else {
            settings.setMemorySettings(null);
        }

        if (runtimeEnableButton.isSelected()) {
            settings.setRuntime((JavaRuntime) javaRuntimeBox.getSelectedItem());
            settings.setCustomJvmArgs(javaArgsBox.getText());
        } else {
            settings.setRuntime(null);
            settings.setCustomJvmArgs(null);
        }

        saved = true;
    }

    public static boolean open(Window parent, Instance instance) {
        InstanceSettingsDialogFlat dialog = new InstanceSettingsDialogFlat(parent, instance.getSettings());
        dialog.setVisible(true);

        if (dialog.saved) {
            Persistence.commitAndForget(instance);
        }

        return dialog.saved;
    }

    private void memlimitFeatureChange(String state) {
        if (Boolean.parseBoolean(state)) {
            memlimitEnableButton.setSelected(true);
            memlimitDisableButton.setSelected(false);
            settings.setMemorySettings(new MemorySettings());
        } else {
            memlimitEnableButton.setSelected(false);
            memlimitDisableButton.setSelected(true);
            settings.setMemorySettings(null);
        }
        updateComponents();
    }

    private void jvmFeatureChange(String state) {
        if (Boolean.parseBoolean(state)) {
            runtimeEnableButton.setSelected(true);
            runtimeDisableButton.setSelected(false);
        } else {
            runtimeEnableButton.setSelected(false);
            runtimeDisableButton.setSelected(true);
            settings.setRuntime(null);
            settings.setCustomJvmArgs(null);
        }
        updateComponents();
    }

}
