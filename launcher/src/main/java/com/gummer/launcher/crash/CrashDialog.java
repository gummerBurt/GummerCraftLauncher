/*
 * Copyright (C) 2021-2026 GummerCraft <http://gummercraft.fun>
 */

package com.gummer.launcher.crash;

import com.gummer.launcher.ui.UiHooks;
import com.skcraft.launcher.Instance;
import com.skcraft.launcher.swing.SwingHelper;

import javax.swing.*;
import java.awt.*;

import static com.skcraft.launcher.util.SharedLocale.tr;

/**
 * Вікно «Гра закрилась з помилкою» (з 1.32): причина з {@link CrashAnalyzer} людською мовою, що зробити,
 * кнопка дії (пам'ять, перевстановити пак, драйвер), «Написати в підтримку» (лог і звіт про збій лаунчер
 * додає сам) і «Відкрити консоль» для тих, хто хоче бачити лог. До 1.32 у цьому випадку просто відкривалась консоль.
 */
public final class CrashDialog {

    /** Куди веде «Як оновити драйвер» (launcher.properties driversHelpUrl); порожньо: кнопки немає. */
    public static volatile String driversHelpUrl;

    private CrashDialog() {
    }

    /** Докачати залежність з Modrinth у фоні; кнопка показує, що йде робота. */
    private static void fetchDep(final JDialog dialog, final Window owner, final Instance instance, final CrashAnalyzer.Diagnosis d, final JButton b) {
        b.setEnabled(false);
        b.setText(tr("crash.action.working"));
        new SwingWorker<java.util.List<String>, Void>() {
            final java.util.List<String> missing = new java.util.ArrayList<String>();

            @Override
            protected java.util.List<String> doInBackground() throws Exception {
                return com.gummer.launcher.manage.DepFetch.fetch(instance, d.modFile, java.util.Collections.singletonList(d.depId), missing);
            }

            @Override
            protected void done() {
                java.util.List<String> got;
                try {
                    got = get();
                } catch (Exception ex) {
                    got = new java.util.ArrayList<String>();
                }
                String msg = !got.isEmpty() ? tr("crash.fetchDep.done", String.join(", ", got))
                        + (missing.isEmpty() ? "" : "\n" + tr("crash.fetchDep.partly", String.join(", ", missing)))
                        : tr("crash.fetchDep.none", d.depId);
                if (!got.isEmpty()) dialog.dispose();
                else {
                    b.setText(tr("crash.action.fetchDep", d.depId));
                    b.setEnabled(true);
                }
                JOptionPane.showMessageDialog(got.isEmpty() ? dialog : owner, msg, tr("crash.windowTitle"),
                        got.isEmpty() ? JOptionPane.WARNING_MESSAGE : JOptionPane.INFORMATION_MESSAGE);
            }
        }.execute();
    }

    /** Оновити мод (чи застарілу залежність) до новішої версії з Modrinth під цю гру; старий файл замінюється. */
    private static void updateMod(final JDialog dialog, final Instance instance, final java.io.File file, final JButton b) {
        final String label = b.getText();
        b.setEnabled(false);
        b.setText(tr("crash.action.working"));
        new SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() throws Exception {
                String game = com.gummer.launcher.manage.ModrinthBrowse.gameVersionOf(instance);
                String[] loaders = com.gummer.launcher.manage.ModrinthBrowse.loadersOf(com.gummer.launcher.manage.ModrinthBrowse.Kind.MOD, instance);
                java.util.Map<java.io.File, com.gummer.launcher.manage.ModUpdates.Update> ups =
                        com.gummer.launcher.manage.ModUpdates.check(java.util.Collections.singletonList(file), game, loaders);
                com.gummer.launcher.manage.ModUpdates.Update u = ups.get(file);
                if (u == null) return null;
                com.gummer.launcher.manage.ModUpdates.apply(u, game, loaders, new java.util.ArrayList<String>(), new java.util.ArrayList<String>());
                return u.versionNumber;
            }

            @Override
            protected void done() {
                String v;
                try {
                    v = get();
                } catch (Exception ex) {
                    b.setText(label);
                    b.setEnabled(true);
                    Throwable t = ex.getCause() != null ? ex.getCause() : ex;
                    JOptionPane.showMessageDialog(dialog, tr("crash.updateMod.failed", String.valueOf(t.getMessage())), tr("crash.windowTitle"), JOptionPane.WARNING_MESSAGE);
                    return;
                }
                if (v == null) {
                    b.setText(label);
                    JOptionPane.showMessageDialog(dialog, tr("crash.updateMod.none"), tr("crash.windowTitle"), JOptionPane.INFORMATION_MESSAGE);
                    return;
                }
                dialog.dispose();
                JOptionPane.showMessageDialog(dialog.getOwner(), tr("crash.updateMod.done", v), tr("crash.windowTitle"), JOptionPane.INFORMATION_MESSAGE);
            }
        }.execute();
    }

    /** Причини в модах: для них кнопка «Моди збірки». */
    private static final java.util.Set<String> MOD_KEYS = new java.util.HashSet<String>(java.util.Arrays.asList(
            "duplicateMod", "modConflict", "mixin", "missingDep", "depTooOld", "modCrash"));
    /** Причини, де стара порада радить перевстановити пак: для збірок зі своїми модами є інша (adviceOwn). */
    private static final java.util.Set<String> OWN_ADVICE = new java.util.HashSet<String>(java.util.Arrays.asList(
            "duplicateMod", "modConflict", "mixin"));

    /** Показати з EDT. openConsole: що робить «Відкрити консоль» (може бути null). */
    public static void show(final Window owner, final Instance instance, final CrashAnalyzer.Diagnosis d, final Runnable openConsole) {
        final JDialog dialog = new JDialog(owner, tr("crash.windowTitle"), Dialog.ModalityType.MODELESS);
        dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

        JPanel p = new JPanel(new BorderLayout(0, 14));
        p.setBorder(BorderFactory.createEmptyBorder(18, 20, 16, 20));

        // назва мода в заголовку в дужках; без назви нічого, щоб не було «null» і зайвого пробілу
        final String detail = d.detail != null && !d.detail.isEmpty() ? " (" + d.detail + ")" : "";
        // 18.11.2026: у збірках Modrinth, копіях і розділах «Перевстановити» моди не чіпає (HardResetter), тож для
        // причин у модах там радимо вкладку модів і «Повернути до початкового стану», а не перевстановлення
        final boolean ownMods = (instance.isMrpack() || instance.isCopy() || instance.isBuiltIn()) && !instance.isVanilla()
                && !com.gummer.launcher.manage.InstanceMods.packModsLocked(instance);
        final boolean modCause = MOD_KEYS.contains(d.key);
        final String adviceKey = ownMods && OWN_ADVICE.contains(d.key) ? "crash." + d.key + ".adviceOwn" : "crash." + d.key + ".advice";
        String html = "<html><div style=\"width: 440px\">"
                + "<span style=\"font-size: 15px\"><b>" + SwingHelper.htmlEscape(tr("crash.headline", instance.getTitle())) + "</b></span><br><br>"
                + "<b>" + SwingHelper.htmlEscape(tr("crash." + d.key + ".title", detail)) + "</b><br>"
                + SwingHelper.htmlEscape(tr(adviceKey, detail)).replace("\n", "<br>")
                + "</div></html>";
        p.add(new JLabel(html), BorderLayout.CENTER);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        JButton primary = null;
        switch (d.action) {
            case MEMORY:
                if (UiHooks.instanceSettings != null) {
                    primary = new JButton(tr("crash.action.memory"));
                    primary.addActionListener(e -> {
                        dialog.dispose();
                        UiHooks.instanceSettings.run(owner, instance);
                    });
                }
                break;
            case REINSTALL:
                if (ownMods && modCause) break; // кнопка «Моди збірки» нижче стане головною
                if (UiHooks.reinstall != null) {
                    primary = new JButton(tr("crash.action.reinstall"));
                    primary.addActionListener(e -> {
                        dialog.dispose();
                        UiHooks.reinstall.run(owner, instance);
                    });
                }
                break;
            case DISABLE_MOD:
                if (d.modFile != null) {
                    final String modName = d.modName != null ? d.modName : d.modFile.getName();
                    primary = new JButton(tr("crash.action.disableMod", modName));
                    primary.addActionListener(e -> {
                        try {
                            for (com.gummer.launcher.manage.InstanceMods.Mod m : com.gummer.launcher.manage.InstanceMods.list(instance)) {
                                if (m.file.equals(d.modFile)) com.gummer.launcher.manage.InstanceMods.setEnabled(instance, m, false);
                            }
                            dialog.dispose();
                            JOptionPane.showMessageDialog(owner, tr("crash.modDisabled", modName), tr("crash.windowTitle"),
                                    JOptionPane.INFORMATION_MESSAGE);
                        } catch (java.io.IOException ex) {
                            SwingHelper.showErrorDialog(dialog, tr("crash.modDisableFailed", modName), tr("errorTitle"), ex);
                        }
                    });
                }
                break;
            case DRIVERS:
                final String url = driversHelpUrl;
                if (url != null && !url.isEmpty()) {
                    primary = new JButton(tr("crash.action.drivers"));
                    primary.addActionListener(e -> SwingHelper.openURL(url, dialog));
                }
                break;
            default:
                break;
        }
        // 18.11.2026 (власник: «а можна дати можливість докачати цей мод?», «Оновити цей мод ... а можна додати?»)
        if (ownMods && "missingDep".equals(d.key) && d.depId != null) {
            final JButton fetch = new JButton(tr("crash.action.fetchDep", d.depId));
            fetch.setToolTipText(tr("crash.action.fetchDepTip"));
            fetch.addActionListener(e -> fetchDep(dialog, owner, instance, d, fetch));
            if (primary != null) buttons.add(primary); // «Вимкнути» лишається, але головна дія тепер докачати
            primary = fetch;
        }
        final java.io.File updatable = "depTooOld".equals(d.key) ? d.depFile
                : "modCrash".equals(d.key) || "mixin".equals(d.key) ? d.modFile : null;
        if (ownMods && updatable != null) {
            String name = "depTooOld".equals(d.key) ? d.depId : d.modName;
            final JButton upd = new JButton(tr("crash.action.updateMod", name != null ? name : updatable.getName()));
            upd.addActionListener(e -> updateMod(dialog, instance, updatable, upd));
            if ("depTooOld".equals(d.key)) {
                if (primary != null) buttons.add(primary);
                primary = upd;
            } else {
                buttons.add(upd);
            }
        }
        // 18.11.2026: «Моди збірки» (там оновлення і вимкнення) і «Повернути версію» модпака після його оновлення
        final UiHooks.InstanceAction mods = UiHooks.openMods;
        if (ownMods && modCause && mods != null) {
            JButton b = new JButton(tr("crash.action.mods"));
            b.addActionListener(e -> {
                dialog.dispose();
                mods.run(owner, instance);
            });
            if (primary == null) primary = b;
            else buttons.add(b);
        }
        final UiHooks.InstanceAction rollback = UiHooks.rollback;
        final String backup = instance.isMrpack() ? com.gummer.launcher.modrinth.MrpackUpdate.backupVersion(instance) : null;
        if (backup != null && rollback != null) {
            JButton b = new JButton(tr("crash.action.rollback", backup));
            b.setToolTipText(tr("crash.action.rollbackTip"));
            b.addActionListener(e -> {
                dialog.dispose();
                rollback.run(owner, instance);
            });
            buttons.add(b);
        }
        if (openConsole != null) {
            JButton console = new JButton(tr("crash.action.console"));
            console.addActionListener(e -> openConsole.run());
            buttons.add(console);
        }
        final UiHooks.Support support = UiHooks.support;
        if (support != null) {
            JButton write = new JButton(tr("crash.action.support"));
            write.addActionListener(e -> {
                dialog.dispose();
                String text = tr("crash.supportText", instance.getTitle(), tr("crash." + d.key + ".title", detail));
                support.open(owner, com.gummer.launcher.support.SupportService.defaultTopic(instance), text);
            });
            buttons.add(write);
        }
        if (primary != null) {
            primary.putClientProperty("JButton.buttonType", "default");
            buttons.add(primary);
            dialog.getRootPane().setDefaultButton(primary);
        }
        JButton close = new JButton(tr("crash.action.close"));
        close.addActionListener(e -> dialog.dispose());
        buttons.add(close);
        p.add(buttons, BorderLayout.SOUTH);

        dialog.setContentPane(p);
        dialog.pack();
        dialog.setResizable(false);
        dialog.setLocationRelativeTo(owner);
        dialog.setVisible(true);
        dialog.toFront();
        // лаунчер повертається на екран слідом за цим вікном (після гри він схований); якщо власника немає
        // або лаунчер створив нове вікно, ще раз підняти це вікно, коли лаунчер уже показано
        Timer raise = new Timer(700, e -> {
            if (dialog.isShowing()) {
                if (owner != null && owner.isShowing()) dialog.setLocationRelativeTo(owner);
                dialog.toFront();
            }
        });
        raise.setRepeats(false);
        raise.start();
    }
}
