/*
 * SK's Minecraft Launcher
 * Copyright (C) 2010-2014 Albert Pham <http://www.sk89q.com> and contributors
 * Copyright (C) 2021-2025 GummerCraft <http://gummercraft.fun>
 * Please see LICENSE.txt for license information.
 */

package com.skcraft.launcher.dialog;

import com.gummer.launcher.swing.SwingExtras;
import com.skcraft.launcher.Instance;
import com.skcraft.launcher.InstanceSettings;
import com.skcraft.launcher.dialog.component.BetterComboBox;
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
import java.util.Hashtable;

import static com.skcraft.launcher.util.Environment.getTotalSystemMemory;

@Log
public class InstanceSettingsDialog extends JDialog {
	private final InstanceSettings settings;

	private final LinedBoxPanel formsPanel = new LinedBoxPanel(false);
	private final FormPanel memorySettingsPanel = new FormPanel();

    private final JLabel memlimitTitleLabel = new JLabel(SharedLocale.tr("options.javaMemSectionTitle"));
    private final JCheckBox enableMemorySettings = new JCheckBox(SharedLocale.tr("instance.options.customMemory"));
	private final JSpinner minMemorySpinner = new JSpinner();
	private final JSpinner maxMemorySpinner = new JSpinner();
    private final JSlider memorySlider = new JSlider();

    private final JLabel jvmTitleLabel = new JLabel(SharedLocale.tr("options.javaParamsSectionTitle"));
	private final JCheckBox enableCustomRuntime = new JCheckBox(SharedLocale.tr("instance.options.customJava"));
	private final FormPanel runtimePanel = new FormPanel();
//	private final JComboBox<JavaRuntime> javaRuntimeBox = new BetterComboBox<>();
	private final JComboBox<JavaRuntime> javaRuntimeBox = new JComboBox<>();
	private final JTextField javaArgsBox = new JTextField();

	private final LinedBoxPanel buttonsPanel = new LinedBoxPanel(true);
	private final JButton okButton = new JButton(SharedLocale.tr("button.save"));
	private final JButton cancelButton = new JButton(SharedLocale.tr("button.cancel"));



	private boolean saved = false;

	public InstanceSettingsDialog(Window owner, InstanceSettings settings) {
		super(owner);
		this.settings = settings;

		setTitle(SharedLocale.tr("instance.options.title"));
		setModalityType(DEFAULT_MODALITY_TYPE);
		initComponents();
		setSize(new Dimension(400, 500));
		setLocationRelativeTo(owner);
	}

	private void initComponents() {
        memlimitTitleLabel.putClientProperty("FlatLaf.styleClass", "h2");
        memorySettingsPanel.addRow(memlimitTitleLabel);

		//memorySettingsPanel.addRow(new JLabel(SharedLocale.tr("options.minMemory")), minMemorySpinner);
		//memorySettingsPanel.addRow(new JLabel(SharedLocale.tr("options.maxMemory")), maxMemorySpinner);
        memorySettingsPanel.addRow(minMemorySpinner);
        memorySettingsPanel.addRow(maxMemorySpinner);
        minMemorySpinner.setVisible(false);
        maxMemorySpinner.setVisible(false);

        // add memory slider to window
        SwingExtras.configureMemorySlider(memorySlider, minMemorySpinner, maxMemorySpinner);
        memorySettingsPanel.addRow(memorySlider);
        memorySettingsPanel.addRow(Box.createVerticalStrut(5));
        memorySettingsPanel.addRow(new JLabel(SharedLocale.tr("options.64BitJavaWarning")));
        memorySettingsPanel.addRow(Box.createVerticalStrut(2));
        memorySettingsPanel.addRow(enableMemorySettings);

		// TODO: Do we keep this list centrally somewhere? Or is actively refreshing good?
		JavaRuntime[] javaRuntimes = JavaRuntimeFinder.getAvailableRuntimes().toArray(new JavaRuntime[0]);
		javaRuntimeBox.setModel(new DefaultComboBoxModel<>(javaRuntimes));

        jvmTitleLabel.putClientProperty("FlatLaf.styleClass", "h2");
        runtimePanel.addRow(jvmTitleLabel);
        //runtimePanel.addRow(Box.createVerticalStrut(5));
        javaArgsBox.putClientProperty("JTextField.placeholderText", SharedLocale.tr("options.automaticPlaceholder"));
		runtimePanel.addRow(new JLabel(SharedLocale.tr("instance.options.jvmRuntime")), javaRuntimeBox);
		runtimePanel.addRow(new JLabel(SharedLocale.tr("instance.options.jvmArguments")), javaArgsBox);
        runtimePanel.addRow(Box.createVerticalStrut(2));
        runtimePanel.addRow(enableCustomRuntime);

        //okButton.setMargin(new Insets(0, 10, 0, 10)); // fix button size by removing Margin
        buttonsPanel.addElement(cancelButton);
		buttonsPanel.addGlue();
		buttonsPanel.addElement(okButton);

		enableMemorySettings.addActionListener(e -> {
			if (enableMemorySettings.isSelected()) {
				settings.setMemorySettings(new MemorySettings());
			} else {
				settings.setMemorySettings(null);
			}

			updateComponents();
		});

		enableCustomRuntime.addActionListener(e -> {
			runtimePanel.setEnabled(enableCustomRuntime.isSelected());
		});

		okButton.addActionListener(e -> {
			save();
			dispose();
		});

		cancelButton.addActionListener(e -> dispose());

        formsPanel.addElement(runtimePanel);
		formsPanel.addElement(memorySettingsPanel);

		add(formsPanel, BorderLayout.NORTH);
		add(buttonsPanel, BorderLayout.SOUTH);

		updateComponents();
	}

	private void updateComponents() {
		if (settings.getMemorySettings() != null) {
			memorySettingsPanel.setEnabled(true);
			enableMemorySettings.setSelected(true);

			minMemorySpinner.setValue(settings.getMemorySettings().getMinMemory());
			maxMemorySpinner.setValue(settings.getMemorySettings().getMaxMemory());
            memorySlider.setValue((int) settings.getMemorySettings().getMaxMemory());
		} else {
            //memorySlider.setValue((int) settings.getMemorySettings().getMaxMemory());
			memorySettingsPanel.setEnabled(false);
			enableMemorySettings.setSelected(false);
		}

		if (settings.getRuntime() != null) {
			runtimePanel.setEnabled(true);
			enableCustomRuntime.setSelected(true);
		} else {
			runtimePanel.setEnabled(false);
			enableCustomRuntime.setSelected(false);
		}

		javaRuntimeBox.setSelectedItem(settings.getRuntime());
		javaArgsBox.setText(settings.getCustomJvmArgs());
	}

	private void save() {
		if (enableMemorySettings.isSelected()) {
			MemorySettings memorySettings = settings.getMemorySettings();

			//memorySettings.setMinMemory((int) minMemorySpinner.getValue());
			//memorySettings.setMaxMemory((int) maxMemorySpinner.getValue());
            memorySettings.setMinMemory((int) memorySlider.getValue());
            memorySettings.setMaxMemory((int) memorySlider.getValue());
		} else {
			settings.setMemorySettings(null);
		}

		if (enableCustomRuntime.isSelected()) {
			settings.setRuntime((JavaRuntime) javaRuntimeBox.getSelectedItem());
			settings.setCustomJvmArgs(javaArgsBox.getText());
		} else {
			settings.setRuntime(null);
			settings.setCustomJvmArgs(null);
		}

		saved = true;
	}

	public static boolean open(Window parent, Instance instance) {
		InstanceSettingsDialog dialog = new InstanceSettingsDialog(parent, instance.getSettings());
		dialog.setVisible(true);

		if (dialog.saved) {
			Persistence.commitAndForget(instance);
		}

		return dialog.saved;
	}

}
