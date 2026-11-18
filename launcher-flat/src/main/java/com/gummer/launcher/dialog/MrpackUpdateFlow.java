/*
 * GummerCraft Launcher
 * Copyright (C) 2026 GummerCraft <http://gummercraft.fun>
 * Please see LICENSE.txt for license information.
 */

package com.gummer.launcher.dialog;

import com.gummer.launcher.look.LookTheme;
import com.gummer.launcher.look.LookUi;
import com.gummer.launcher.manage.ModrinthBrowse;
import com.gummer.launcher.modrinth.MrpackPacks;
import com.gummer.launcher.modrinth.MrpackUpdate;
import com.gummer.launcher.popup.Notification;
import com.skcraft.concurrency.ObservableFuture;
import com.skcraft.launcher.Instance;
import com.skcraft.launcher.Launcher;
import com.skcraft.launcher.dialog.ProgressDialog;
import com.skcraft.launcher.swing.SwingHelper;
import com.skcraft.launcher.util.SwingExecutor;
import net.miginfocom.swing.MigLayout;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.gummer.launcher.popup.ShowNotification.showNotification;
import static com.skcraft.launcher.util.SharedLocale.tr;

/**
 * 17.11.2026 (власник: оновлення модпаків з Modrinth, «не автооновлювати, а пропонувати»). Вікна оновлення збірки
 * Modrinth: завантажити нову версію і порахувати зміни ({@link MrpackUpdate#prepare}), показати гравцю, що
 * зміниться і де він сам міняв файли (галочка «взяти з модпака», за замовчуванням лишається його версія), застосувати;
 * і «Повернути попередню версію». Спільне для сторінки Modrinth і вікна «Збірка» в трьох виглядах.
 */
public final class MrpackUpdateFlow {

    private static final Logger log = Logger.getLogger(MrpackUpdateFlow.class.getName());

    private MrpackUpdateFlow() {
    }

    /** Оновити збірку до {@code target}. {@code done} отримує збірку після успіху (вікно доставляє гру і завантажувач). */
    public static void start(final Window owner, final Launcher launcher, final Instance instance,
                             final ModrinthBrowse.PackVersion target, final Consumer<Instance> done) {
        final MrpackUpdate up = new MrpackUpdate(launcher, instance);
        final com.google.common.util.concurrent.ListenableFuture<MrpackUpdate.Plan> task =
                launcher.getExecutor().submit(() -> up.prepare(target));
        ObservableFuture<MrpackUpdate.Plan> future = new ObservableFuture<MrpackUpdate.Plan>(task, up);
        final String title = MrpackPacks.titleOf(instance);
        ProgressDialog.showProgress(owner, future, tr("mrpack.update.title"), tr("mrpack.update.preparing", title));
        future.addListener(() -> {
            MrpackUpdate.Plan plan;
            try {
                plan = task.get();
            } catch (Exception e) {
                Throwable t = e.getCause() != null ? e.getCause() : e;
                log.log(Level.WARNING, "Modpack update check failed: " + instance.getName(), t);
                String msg = MrpackUpdate.UNKNOWN_BASE.equals(t.getMessage()) ? tr("mrpack.update.unknownBase")
                        : tr("mrpack.update.failed", String.valueOf(t.getMessage()));
                SwingHelper.showErrorDialog(owner, msg, tr("mrpack.update.title"));
                return;
            }
            if (!confirm(owner, title, plan)) {
                plan.discard();
                return;
            }
            apply(owner, launcher, up, instance, plan, title, done, tr("mrpack.update.done", title, plan.target.number));
        }, SwingExecutor.INSTANCE);
    }

    /**
     * 18.11.2026: «Повернути до початкового стану» (див. {@link MrpackUpdate#prepareReset}): той самий шлях, що
     * оновлення, але до встановленої версії і з поверненням усього, що гравець змінив.
     */
    public static void reset(final Window owner, final Launcher launcher, final Instance instance,
                             final ModrinthBrowse.PackVersion current, final Consumer<Instance> done) {
        final MrpackUpdate up = new MrpackUpdate(launcher, instance);
        final com.google.common.util.concurrent.ListenableFuture<MrpackUpdate.Plan> task =
                launcher.getExecutor().submit(() -> up.prepareReset(current));
        ObservableFuture<MrpackUpdate.Plan> future = new ObservableFuture<MrpackUpdate.Plan>(task, up);
        final String title = MrpackPacks.titleOf(instance);
        ProgressDialog.showProgress(owner, future, tr("mrpack.reset.title"), tr("mrpack.reset.preparing", title));
        future.addListener(() -> {
            MrpackUpdate.Plan plan;
            try {
                plan = task.get();
            } catch (Exception e) {
                Throwable t = e.getCause() != null ? e.getCause() : e;
                log.log(Level.WARNING, "Modpack reset check failed: " + instance.getName(), t);
                SwingHelper.showErrorDialog(owner, tr("mrpack.update.failed", String.valueOf(t.getMessage())), tr("mrpack.reset.title"));
                return;
            }
            if (plan.changes.isEmpty()) {
                plan.discard();
                SwingHelper.showMessageDialog(owner, tr("mrpack.reset.nothing", title), tr("mrpack.reset.title"), null, JOptionPane.INFORMATION_MESSAGE);
                return;
            }
            int back = plan.count(MrpackUpdate.Change.Type.ADD) + plan.count(MrpackUpdate.Change.Type.UPDATE)
                    + plan.count(MrpackUpdate.Change.Type.CHANGED_BY_YOU);
            int own = plan.count(MrpackUpdate.Change.Type.REMOVE);
            if (!SwingHelper.confirmDialog(owner, tr("mrpack.reset.confirm", title, plan.target.number, back, own), tr("mrpack.reset.title"))) {
                plan.discard();
                return;
            }
            apply(owner, launcher, up, instance, plan, title, done, tr("mrpack.reset.done", title));
        }, SwingExecutor.INSTANCE);
    }

    private static void apply(final Window owner, final Launcher launcher, final MrpackUpdate up, final Instance instance, final MrpackUpdate.Plan plan,
                              final String title, final Consumer<Instance> done, final String doneText) {
        final com.google.common.util.concurrent.ListenableFuture<String> task =
                launcher.getExecutor().submit(() -> up.apply(plan));
        ObservableFuture<String> future = new ObservableFuture<String>(task, up);
        ProgressDialog.showProgress(owner, future, tr("mrpack.update.title"), tr("mrpack.update.applying", title));
        future.addListener(() -> {
            try {
                task.get();
                instance.setUpdatePending(true); // новий завантажувач чи його версія ставиться наступним кроком
                com.gummer.launcher.manage.UpdateBadges.recheck(instance, null);
                showNotification(LauncherFrameFlat.rootFrame, LauncherFrameFlat.class, Notification.Type.SUCCESS, doneText);
                if (done != null) done.accept(instance);
            } catch (Exception e) {
                Throwable t = e.getCause() != null ? e.getCause() : e;
                log.log(Level.WARNING, "Modpack update failed: " + instance.getName(), t);
                SwingHelper.showErrorDialog(owner, tr("mrpack.update.failedApplied", String.valueOf(t.getMessage())), tr("mrpack.update.title"));
            }
        }, SwingExecutor.INSTANCE);
    }

    /** Вікно «що зміниться»: підсумок, конфлікти з галочками. true - оновлювати. */
    static boolean confirm(Window owner, String title, MrpackUpdate.Plan plan) {
        LookTheme.Palette p = LookTheme.palette();
        JPanel panel = new JPanel(new MigLayout("insets 0, wrap 1, gap 0 6, fillx", "[grow,fill]", ""));
        panel.setOpaque(false);
        int upd = plan.count(MrpackUpdate.Change.Type.UPDATE), add = plan.count(MrpackUpdate.Change.Type.ADD),
                rem = plan.count(MrpackUpdate.Change.Type.REMOVE), gone = plan.count(MrpackUpdate.Change.Type.DELETED_BY_YOU);
        panel.add(LookUi.title(tr("mrpack.update.summaryTitle", title, plan.fromNumber, plan.target.number), 15f), "wmin 0");
        panel.add(LookUi.body(tr("mrpack.update.summary", upd, add, rem), true), "wmin 0, w 460!");
        panel.add(LookUi.body(tr("mrpack.update.keeps"), true), "wmin 0, w 460!");
        // 18.11.2026 (власник: «список змін модпака перед оновленням»): що написав автор про нові версії
        if (plan.changelog != null) {
            panel.add(LookUi.title(tr("mrpack.update.changelogTitle"), 13f), "wmin 0, gaptop 6");
            JTextArea log = new JTextArea(plan.changelog);
            log.setEditable(false);
            log.setLineWrap(true);
            log.setWrapStyleWord(true);
            log.setOpaque(false);
            log.setForeground(p.text2);
            log.setFont(log.getFont().deriveFont(12f));
            log.setCaretPosition(0);
            JScrollPane ls = new JScrollPane(log, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
            ls.setBorder(BorderFactory.createEmptyBorder());
            ls.setOpaque(false);
            ls.getViewport().setOpaque(false);
            ls.getVerticalScrollBar().setUnitIncrement(20);
            panel.add(ls, "wmin 0, w 460!, hmax 200");
        }
        if (gone > 0) panel.add(LookUi.body(tr("mrpack.update.deletedByYou", gone), true), "wmin 0, w 460!");
        final List<MrpackUpdate.Change> conflicts = plan.conflicts();
        final List<JCheckBox> boxes = new ArrayList<JCheckBox>();
        if (!conflicts.isEmpty()) {
            JTextArea c = LookUi.body(tr("mrpack.update.conflicts", conflicts.size()), true);
            c.setForeground(p.warn);
            panel.add(c, "wmin 0, w 460!, gaptop 6");
            JPanel list = new JPanel(new MigLayout("insets 4 0 4 0, wrap 1, gap 0 2, fillx", "[grow,fill]", ""));
            list.setOpaque(false);
            for (MrpackUpdate.Change ch : conflicts) {
                String kind = ch.type == MrpackUpdate.Change.Type.OWN_VERSION ? tr("mrpack.update.ownVersion")
                        : ch.newPath == null ? tr("mrpack.update.changedRemoved") : tr("mrpack.update.changed");
                JCheckBox b = new JCheckBox("<html>" + SwingHelper.htmlEscape(ch.path) + "&nbsp;&nbsp;<span style=\"color:"
                        + LookUi.hex(p.text3) + "\">" + SwingHelper.htmlEscape(kind) + "</span></html>", false);
                b.setOpaque(false);
                b.setToolTipText(tr("mrpack.update.takePackTip"));
                boxes.add(b);
                list.add(b, "wmin 0");
            }
            JScrollPane sp = new JScrollPane(list, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
            sp.setBorder(BorderFactory.createEmptyBorder());
            sp.setOpaque(false);
            sp.getViewport().setOpaque(false);
            sp.getVerticalScrollBar().setUnitIncrement(20);
            panel.add(sp, "wmin 0, w 460!, hmax 220");
            panel.add(LookUi.body(tr("mrpack.update.conflictsHint"), true), "wmin 0, w 460!");
        }
        Object[] options = {tr("mrpack.update.go"), tr("button.cancel")};
        LookUi.freeze(panel);
        int r = JOptionPane.showOptionDialog(owner, panel, tr("mrpack.update.title"), JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE, null, options, options[0]);
        if (r != 0) return false;
        for (int i = 0; i < conflicts.size(); i++) conflicts.get(i).takePack = boxes.get(i).isSelected();
        return true;
    }

    /** «Повернути попередню версію» (з резервної копії останнього оновлення). */
    public static void rollback(final Window owner, final Launcher launcher, final Instance instance, final Consumer<Instance> done) {
        final String from = MrpackUpdate.backupVersion(instance);
        if (from == null) return;
        final String title = MrpackPacks.titleOf(instance);
        if (!SwingHelper.confirmDialog(owner, tr("mrpack.update.rollbackConfirm", title, from), tr("mrpack.update.rollbackTitle"))) return;
        final com.google.common.util.concurrent.ListenableFuture<Object> task = launcher.getExecutor().submit(() -> {
            MrpackUpdate.restore(instance);
            return null;
        });
        task.addListener(() -> {
            try {
                task.get();
                instance.setUpdatePending(true);
                com.gummer.launcher.manage.UpdateBadges.recheck(instance, null);
                showNotification(LauncherFrameFlat.rootFrame, LauncherFrameFlat.class, Notification.Type.SUCCESS,
                        tr("mrpack.update.rolledBack", title, from));
                if (done != null) done.accept(instance);
            } catch (Exception e) {
                Throwable t = e.getCause() != null ? e.getCause() : e;
                log.log(Level.WARNING, "Modpack rollback failed: " + instance.getName(), t);
                SwingHelper.showErrorDialog(owner, tr("mrpack.update.rollbackFailed", String.valueOf(t.getMessage())), tr("mrpack.update.rollbackTitle"));
            }
        }, SwingExecutor.INSTANCE);
    }
}
