/*
 * Copyright (C) 2021-2025 GummerCraft <http://gummercraft.fun>
 */

package com.gummer.launcher.dialog;

import com.gummer.launcher.look.LookTheme;
import com.gummer.launcher.look.LookUi;
import com.gummer.launcher.popup.Notification;
import net.miginfocom.swing.MigLayout;
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
        // 16.11.2026: під розмір екрана, розтягується, стиль вигляду (було 400x550 без зміни розміру)
        Dimension size = LookTheme.fitToScreen(this, 560, 540, 440, 380);
        setSize(size);
        setMinimumSize(new Dimension(Math.min(440, size.width), Math.min(380, size.height)));
        setResizable(true);
        LookUi.decorate(this, owner);
        setLocationRelativeTo(owner);
    }

    /**
     * 16.11.2026: нова розкладка в стилі вигляду лаунчера (look.LookTheme): дві картки з перемикачем справа,
     * вікно під розмір екрана, прокрутка. Стан, як і раніше, у перемикачах Увімк/Вимк і в settings.
     */
    private void initComponents() {
        LookTheme.Palette pal = LookTheme.palette();

        JavaRuntime[] javaRuntimes = JavaRuntimeFinder.getAvailableRuntimes().toArray(new JavaRuntime[0]);
        javaRuntimeBox.setModel(new DefaultComboBoxModel<>(javaRuntimes));

        styleSwitch(runtimeToolBar, runtimeEnableButton, runtimeDisableButton);
        runtimeEnableButton.addActionListener(e -> jvmFeatureChange("true"));
        runtimeDisableButton.addActionListener(e -> jvmFeatureChange("false"));
        styleSwitch(memlimitToolBar, memlimitEnableButton, memlimitDisableButton);
        memlimitEnableButton.addActionListener(e -> memlimitFeatureChange("true"));
        memlimitDisableButton.addActionListener(e -> memlimitFeatureChange("false"));

        // Java
        JPanel jvmCard = LookUi.card("insets 16 18 16 18, wrap 1, gap 0 6, fillx", "[grow,fill]", "");
        jvmCard.add(header(SharedLocale.tr("instance.look.java.title"), runtimeToolBar));
        formJvmInfoPanel.removeAll();
        formJvmInfoPanel.setLayout(new BorderLayout());
        formJvmInfoPanel.setOpaque(false);
        formJvmInfoPanel.add(LookUi.body(SharedLocale.tr("instance.options.jvmRuntimeInfo"), false));
        jvmCard.add(formJvmInfoPanel, "hidemode 3, wmin 0");
        formJvmControls.removeAll();
        formJvmControls.setLayout(new MigLayout("insets 0, gap 12 8, fillx", "[][grow,fill]", ""));
        formJvmControls.setOpaque(false);
        formJvmControls.add(label(SharedLocale.tr("settings.java.runtime")));
        formJvmControls.add(javaRuntimeBox, "wmin 0, wrap");
        formJvmControls.add(label(SharedLocale.tr("settings.java.args")));
        formJvmControls.add(javaArgsBox, "wmin 0, h 32!, wrap");
        jvmCard.add(formJvmControls, "hidemode 3, wmin 0");

        // Пам'ять
        JPanel memCard = LookUi.card("insets 16 18 16 18, wrap 1, gap 0 6, fillx", "[grow,fill]", "");
        memCard.add(header(SharedLocale.tr("instance.look.memory.title"), memlimitToolBar));
        formMemInfoPanel.removeAll();
        formMemInfoPanel.setLayout(new BorderLayout());
        formMemInfoPanel.setOpaque(false);
        formMemInfoPanel.add(LookUi.body(SharedLocale.tr("instance.options.memlimitInfo"), false));
        memCard.add(formMemInfoPanel, "hidemode 3, wmin 0");
        minMemorySpinner.setVisible(false);
        maxMemorySpinner.setVisible(false);
        memCard.add(minMemorySpinner, "hidemode 3");
        memCard.add(maxMemorySpinner, "hidemode 3");
        SwingExtras.configureMemorySlider(memorySlider, minMemorySpinner, maxMemorySpinner);
        memorySlider.setOpaque(false);
        memCard.add(memorySlider, "hidemode 3, wmin 0");
        JTextArea warn = LookUi.body(SharedLocale.tr("options.64BitJavaWarning"), true);
        jvmWarningArea = warn;
        memCard.add(warn, "hidemode 3, wmin 0");

        JPanel page = new JPanel(new MigLayout("insets 14 16 8 16, wrap 1, gap 0 12, fillx", "[grow,fill]", ""));
        page.setOpaque(false);
        page.add(LookUi.body(SharedLocale.tr("instance.look.intro"), true), "wmin 0");
        page.add(jvmCard);
        page.add(memCard);
        JPanel track = new JPanel(new BorderLayout()) {
        };
        track.setOpaque(false);
        track.add(page, BorderLayout.NORTH);
        JScrollPane sp = new JScrollPane(new WidthTrack(track), ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        sp.setBorder(null);
        sp.setOpaque(false);
        sp.getViewport().setOpaque(false);
        sp.getVerticalScrollBar().setUnitIncrement(18);

        okButton.setText(SharedLocale.tr("settings.save"));
        cancelButton.setText(SharedLocale.tr("settings.cancel"));
        okButton.putClientProperty("FlatLaf.styleClass", null);
        cancelButton.putClientProperty("FlatLaf.styleClass", null);
        LookUi.primary(okButton);
        LookUi.secondary(cancelButton);
        JPanel buttons = new JPanel(new MigLayout("insets 10 16 14 16, gap 8", "push[][]", "[34!]"));
        buttons.setOpaque(false);
        buttons.add(cancelButton, "w 120!");
        buttons.add(okButton, "w 140!");
        getRootPane().setDefaultButton(okButton);

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

        JPanel content = new JPanel(new BorderLayout());
        content.setBackground(pal.bg);
        content.add(sp, BorderLayout.CENTER);
        content.add(buttons, BorderLayout.SOUTH);
        setContentPane(content);

        updateComponents();
    }

    private JTextArea jvmWarningArea;

    /** Перемикач «Увімк / Вимк» таблетками в стилі вигляду. */
    private static void styleSwitch(JToolBar bar, JToggleButton on, JToggleButton off) {
        LookTheme.Palette p = LookTheme.palette();
        bar.setFloatable(false);
        bar.setOpaque(false);
        bar.setBorder(BorderFactory.createEmptyBorder());
        on.setText(SharedLocale.tr("option.toolbar.on"));
        off.setText(SharedLocale.tr("option.toolbar.off"));
        String style = "arc: " + p.buttonArc + "; selectedBackground: " + LookUi.hex(p.accent)
                + "; selectedForeground: #FFFFFF; toolbar.selectedBackground: " + LookUi.hex(p.accent)
                + "; toolbar.hoverBackground: " + LookUi.hex(p.surface3) + "; margin: 4,12,4,12";
        for (JToggleButton b : new JToggleButton[]{on, off}) {
            b.putClientProperty("FlatLaf.styleClass", null);
            b.putClientProperty("FlatLaf.style", style);
            b.setFocusable(false);
            bar.add(b);
        }
        ButtonGroup g = new ButtonGroup();
        g.add(on);
        g.add(off);
    }

    private static JPanel header(String title, JComponent right) {
        JPanel h = new JPanel(new MigLayout("insets 0, fillx", "[grow][]", "[]"));
        h.setOpaque(false);
        h.add(LookUi.title(title, 15f));
        h.add(right);
        return h;
    }

    private static JLabel label(String text) {
        JLabel l = new JLabel(text);
        l.setForeground(LookTheme.palette().text2);
        return l;
    }

    /** Обгортка для прокрутки: по ширині тягнеться за вікном. */
    private static final class WidthTrack extends JPanel implements Scrollable {
        WidthTrack(JComponent inner) {
            super(new BorderLayout());
            setOpaque(false);
            add(inner);
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
            jvmWarningArea.setVisible(true);
        } else {
            memorySettingsPanel.setEnabled(false);
            memlimitDisableButton.setSelected(true);
            memlimitEnableButton.setSelected(false);
            // hide controls and display info
            memorySlider.setVisible(false);
            jvmWarningArea.setVisible(false);
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
