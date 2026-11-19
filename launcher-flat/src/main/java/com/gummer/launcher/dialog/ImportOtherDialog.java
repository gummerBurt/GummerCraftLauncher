/*
 * Copyright (C) 2021-2026 GummerCraft <http://gummercraft.fun>
 */

package com.gummer.launcher.dialog;

import com.gummer.launcher.look.LookTheme;
import com.gummer.launcher.look.LookUi;
import com.gummer.launcher.manage.LauncherImport;
import com.skcraft.concurrency.ObservableFuture;
import com.skcraft.launcher.Launcher;
import com.skcraft.launcher.dialog.ProgressDialog;
import com.skcraft.launcher.swing.SwingHelper;
import com.skcraft.launcher.util.SharedLocale;
import com.skcraft.launcher.util.SwingExecutor;
import net.miginfocom.swing.MigLayout;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Вікно «Імпорт з інших лаунчерів» (19.11.2026): список знайдених збірок Prism, MultiMC, CurseForge, ATLauncher,
 * Modrinth App, офіційного лаунчера і TLauncher з галочками, «Вибрати теку вручну» для нестандартних місць, галочка
 * «Копіювати світи». Логіка: {@link LauncherImport}. Однакове для трьох виглядів лаунчера.
 */
public final class ImportOtherDialog {

    private ImportOtherDialog() {
    }

    /** @param onImported назва останньої імпортованої збірки (вибрати її і перечитати список); null: була помилка, лише перечитати */
    public static void show(Window owner, Launcher launcher, Consumer<String> onImported) {
        owner.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        launcher.getExecutor().submit(() -> {
            List<LauncherImport.Found> found = LauncherImport.scan();
            for (LauncherImport.Found f : found) if (f.problem == null) f.bytes = LauncherImport.size(f, true);
            SwingUtilities.invokeLater(() -> {
                owner.setCursor(Cursor.getDefaultCursor());
                open(owner, launcher, found, onImported);
            });
        });
    }

    private static void open(Window owner, Launcher launcher, List<LauncherImport.Found> initial, Consumer<String> onImported) {
        final JDialog d = new JDialog(owner, SharedLocale.tr("import.title"), Dialog.ModalityType.DOCUMENT_MODAL);
        LookTheme.Palette pal = LookTheme.palette();
        JPanel root = new JPanel(new MigLayout("insets 18 20 16 20, wrap 1, gap 0 8, fill", "[grow,fill]", "[][][grow,fill][][]"));
        root.setBackground(pal.surface);
        root.add(LookUi.title(SharedLocale.tr("import.title"), 16f));
        root.add(LookUi.body(SharedLocale.tr("import.hint"), true), "w 520!, wmin 0");

        final JPanel list = new JPanel(new MigLayout("insets 4, wrap 1, gap 0 6, fillx", "[grow,fill]", ""));
        list.setOpaque(false);
        final Map<LauncherImport.Found, JCheckBox> boxes = new LinkedHashMap<LauncherImport.Found, JCheckBox>();
        final JButton go = LookUi.primary(new JButton(SharedLocale.tr("import.go")));
        go.putClientProperty("FlatLaf.styleClass", null);
        Runnable refreshGo = () -> {
            boolean any = false;
            for (JCheckBox b : boxes.values()) any |= b.isSelected();
            go.setEnabled(any);
        };
        Consumer<List<LauncherImport.Found>> fill = found -> {
            for (LauncherImport.Found f : found) {
                if (boxes.containsKey(f) || containsSame(boxes, f)) continue;
                JPanel row = new JPanel(new MigLayout("insets 0, wrap 1, gap 0 0, fillx", "[grow,fill]", ""));
                row.setOpaque(false);
                JCheckBox b = new JCheckBox(f.title);
                b.setOpaque(false);
                b.setFont(b.getFont().deriveFont(Font.BOLD));
                String what = f.source + "  ·  " + (f.mc != null ? f.loaderTitle() + " " + f.mc : "?")
                        + (f.bytes > 0 ? "  ·  " + com.gummer.launcher.util.Uninstall.human(f.bytes,
                        SharedLocale.tr("settings.danger.gb"), SharedLocale.tr("settings.danger.mb")) : "");
                JLabel sub = new JLabel(what);
                sub.setForeground(pal.text3);
                sub.setFont(sub.getFont().deriveFont(12f));
                row.add(b);
                row.add(sub, "gapleft 26");
                if (f.problem != null) {
                    b.setEnabled(false);
                    JTextArea why = LookUi.body(SharedLocale.tr("import.problem." + f.problem), true);
                    row.add(why, "gapleft 26, wmin 0");
                } else {
                    b.setSelected(!f.dotMinecraft); // .minecraft часто спільна на всі версії: лише якщо гравець сам вибере
                    b.addActionListener(e -> refreshGo.run());
                }
                boxes.put(f, b);
                list.add(row, "wmin 0");
            }
            if (boxes.isEmpty()) {
                list.add(LookUi.body(SharedLocale.tr("import.none"), true), "wmin 0");
            }
            refreshGo.run();
            list.revalidate();
            list.repaint();
        };
        fill.accept(initial);
        JScrollPane scroll = new JScrollPane(list);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        root.add(scroll, "h 120:300:360, wmin 0");

        final JCheckBox worlds = new JCheckBox(SharedLocale.tr("import.worlds"), true);
        worlds.setOpaque(false);
        root.add(worlds);

        JButton pick = LookUi.secondary(new JButton(SharedLocale.tr("import.pick")));
        pick.putClientProperty("FlatLaf.styleClass", null);
        JButton cancel = LookUi.secondary(new JButton(SharedLocale.tr("button.cancel")));
        cancel.putClientProperty("FlatLaf.styleClass", null);
        JPanel buttons = new JPanel(new MigLayout("insets 0, gap 8", "[]push[][]", "[34!]"));
        buttons.setOpaque(false);
        buttons.add(pick, "h 34!");
        buttons.add(cancel, "h 34!");
        buttons.add(go, "h 34!");
        root.add(buttons, "gaptop 6");

        pick.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setDialogTitle(SharedLocale.tr("import.pickTitle"));
            chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            if (chooser.showOpenDialog(d) != JFileChooser.APPROVE_OPTION) return;
            File dir = chooser.getSelectedFile();
            d.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
            launcher.getExecutor().submit(() -> {
                List<LauncherImport.Found> found = LauncherImport.detect(dir);
                for (LauncherImport.Found f : found) if (f.problem == null) f.bytes = LauncherImport.size(f, true);
                SwingUtilities.invokeLater(() -> {
                    d.setCursor(Cursor.getDefaultCursor());
                    if (found.isEmpty()) {
                        JOptionPane.showMessageDialog(d, SharedLocale.tr("import.notFound", dir.getAbsolutePath()),
                                SharedLocale.tr("import.title"), JOptionPane.INFORMATION_MESSAGE);
                        return;
                    }
                    if (boxes.isEmpty()) list.removeAll();
                    fill.accept(found);
                    for (LauncherImport.Found f : found) {
                        JCheckBox b = boxes.get(f);
                        if (b != null && b.isEnabled() && found.size() == 1) b.setSelected(true);
                    }
                    refreshGo.run();
                });
            });
        });
        cancel.addActionListener(e -> d.dispose());
        go.addActionListener(e -> {
            final List<LauncherImport.Found> chosen = new ArrayList<LauncherImport.Found>();
            for (Map.Entry<LauncherImport.Found, JCheckBox> en : boxes.entrySet()) {
                if (en.getValue().isSelected() && en.getKey().problem == null) chosen.add(en.getKey());
            }
            if (chosen.isEmpty()) return;
            d.dispose();
            run(owner, launcher, chosen, worlds.isSelected(), onImported);
        });

        d.setContentPane(root);
        d.pack();
        d.setMinimumSize(new Dimension(560, 300));
        d.setLocationRelativeTo(owner);
        d.setVisible(true);
    }

    private static boolean containsSame(Map<LauncherImport.Found, JCheckBox> boxes, LauncherImport.Found f) {
        for (LauncherImport.Found o : boxes.keySet()) {
            if (o.gameDir != null && o.gameDir.equals(f.gameDir) && String.valueOf(o.mc).equals(String.valueOf(f.mc))
                    && o.loader.equals(f.loader)) return true;
        }
        return false;
    }

    private static void run(Window owner, Launcher launcher, List<LauncherImport.Found> chosen, boolean worlds,
                            Consumer<String> onImported) {
        final LauncherImport importer = new LauncherImport();
        com.google.common.util.concurrent.ListenableFuture<String> task = launcher.getExecutor().submit(() -> {
            String last = null;
            for (LauncherImport.Found f : chosen) last = importer.importOne(launcher, f, worlds);
            return last;
        });
        ObservableFuture<String> future = new ObservableFuture<String>(task, importer);
        ProgressDialog.showProgress(owner, future, SharedLocale.tr("import.progressTitle"),
                SharedLocale.tr("import.progressText", chosen.size()));
        SwingHelper.addErrorDialogCallback(owner, future);
        future.addListener(() -> {
            try {
                String name = task.get();
                if (name != null) onImported.accept(name);
            } catch (Exception ignored) {
                // помилку вже показав addErrorDialogCallback; збірки до помилки вже створено, тож список перечитати
                onImported.accept(null);
            }
        }, SwingExecutor.INSTANCE);
    }
}
