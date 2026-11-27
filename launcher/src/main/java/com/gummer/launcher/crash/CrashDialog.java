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
    /** 19.11.2026: лаунчер (загальні параметри Java для «Прибрати параметр»), ставить LaunchProcessHandler. */
    public static volatile com.skcraft.launcher.Launcher launcher;

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

    /**
     * 25.11.2026: modClassMissing. Залежність є: оновити її і перевірити, що потрібний клас з'явився (інакше стару
     * повернуто). Залежності немає: докачати. Див. {@link com.gummer.launcher.manage.ClassFix}.
     */
    private static void fixClass(final JDialog dialog, final Window owner, final Instance instance, final CrashAnalyzer.Diagnosis d, final JButton b) {
        final String label = b.getText();
        b.setEnabled(false);
        b.setText(tr("crash.action.working"));
        new SwingWorker<com.gummer.launcher.manage.ClassFix.Result, Void>() {
            @Override
            protected com.gummer.launcher.manage.ClassFix.Result doInBackground() throws Exception {
                return d.depFile != null
                        ? com.gummer.launcher.manage.ClassFix.updateDep(instance, d.depFile, d.missingClass)
                        : com.gummer.launcher.manage.ClassFix.fetchDep(instance, d.modFile, d.depId, d.missingClass);
            }

            @Override
            protected void done() {
                com.gummer.launcher.manage.ClassFix.Result r;
                try {
                    r = get();
                } catch (Exception ex) {
                    b.setText(label);
                    b.setEnabled(true);
                    Throwable t = ex.getCause() != null ? ex.getCause() : ex;
                    JOptionPane.showMessageDialog(dialog, tr("crash.updateMod.failed", String.valueOf(t.getLocalizedMessage())), tr("crash.windowTitle"), JOptionPane.WARNING_MESSAGE);
                    return;
                }
                String culprit = d.modName != null ? d.modName : "?";
                String msg;
                switch (r.outcome) {
                    case FIXED:
                        dialog.dispose();
                        JOptionPane.showMessageDialog(owner, d.depFile != null ? tr("crash.classFix.done", d.depId, r.what)
                                : tr("crash.classFix.fetched", r.what), tr("crash.windowTitle"), JOptionPane.INFORMATION_MESSAGE);
                        return;
                    case NO_UPDATE:
                        msg = tr("crash.classFix.noUpdate", d.depId, culprit);
                        break;
                    case NO_CLASS:
                        msg = tr(d.depFile != null ? "crash.classFix.noClass" : "crash.classFix.fetchedNoClass", d.depId, culprit, r.what);
                        break;
                    default:
                        msg = tr("crash.fetchDep.none", d.depId);
                        break;
                }
                // повторне натискання нічого не змінить, крім «не знайшлося» (інтернет)
                b.setText(label);
                b.setEnabled(r.outcome == com.gummer.launcher.manage.ClassFix.Outcome.NOT_FOUND);
                JOptionPane.showMessageDialog(dialog, msg, tr("crash.windowTitle"), JOptionPane.WARNING_MESSAGE);
            }
        }.execute();
    }

    /** Оновити мод (чи застарілу залежність) до новішої версії з Modrinth під цю гру; старий файл замінюється. */
    /** Заголовок причини: деталь у дужках і, для нових причин, назви модів і версії ({@link #args}). */
    private static String title(CrashAnalyzer.Diagnosis d, String detail) {
        return tr("crash." + d.key + ".title", args(d, detail));
    }

    /**
     * 27.11.2026: аргументи текстів причини: {0} деталь у дужках (як раніше), {1} мод (A), {2} мод B, {3} версія B,
     * {4} версія гри, для якої зроблено мод, {5} версія гри збірки. Старим текстам зайві аргументи не заважають.
     */
    private static Object[] args(CrashAnalyzer.Diagnosis d, String detail) {
        return new Object[]{detail, d.modName != null ? d.modName : "?", d.depName != null ? d.depName : "?",
                d.depVersion != null ? d.depVersion : "?", d.modFor != null ? d.modFor : "?", d.gameVersion != null ? d.gameVersion : "?"};
    }

    /**
     * 27.11.2026 (тікет #139, власник: «Вимикати мод разом з тими, кому він потрібен»): кнопка «Вимкнути «Veil» і
     * «Sable»». Список рахується одразу ({@link com.gummer.launcher.manage.ModDependents}), щоб назва кнопки казала
     * правду про те, що буде вимкнено.
     */
    private static JButton disableButton(final JDialog dialog, final Window owner, final Instance instance,
                                         java.util.List<java.io.File> roots, String firstName) {
        final java.util.List<com.gummer.launcher.manage.InstanceMods.Mod> chain = com.gummer.launcher.manage.ModDependents.withDependents(instance, roots);
        final java.util.List<String> names = new java.util.ArrayList<String>();
        for (com.gummer.launcher.manage.InstanceMods.Mod m : chain) names.add(roots.contains(m.file) && firstName != null ? firstName : modTitle(m));
        if (names.isEmpty() && firstName != null) names.add(firstName);
        final String list = com.gummer.launcher.manage.ModDependents.names(names, tr("crash.and"));
        final String first = names.isEmpty() ? "?" : names.get(0);
        JButton b = new JButton(names.size() > 1 ? tr("crash.action.disableModWith", list) : tr("crash.action.disableMod", first));
        if (names.size() > 1) b.setToolTipText(tr("crash.action.disableModWithTip", first));
        b.addActionListener(e -> {
            java.util.List<com.gummer.launcher.manage.InstanceMods.Mod> todo = chain;
            if (todo.isEmpty()) {
                todo = new java.util.ArrayList<com.gummer.launcher.manage.InstanceMods.Mod>();
                for (com.gummer.launcher.manage.InstanceMods.Mod m : com.gummer.launcher.manage.InstanceMods.list(instance)) {
                    if (roots.contains(m.file) && m.enabled) todo.add(m);
                }
            }
            for (com.gummer.launcher.manage.InstanceMods.Mod m : todo) {
                try {
                    com.gummer.launcher.manage.InstanceMods.setEnabled(instance, m, false);
                } catch (java.io.IOException ex) {
                    SwingHelper.showErrorDialog(dialog, tr("crash.modDisableFailed", modTitle(m)), tr("errorTitle"), ex);
                    return;
                }
            }
            dialog.dispose();
            JOptionPane.showMessageDialog(owner, names.size() > 1 ? tr("crash.modsDisabled", list) : tr("crash.modDisabled", first),
                    tr("crash.windowTitle"), JOptionPane.INFORMATION_MESSAGE);
        });
        return b;
    }

    /** 27.11.2026 (тікет #139): поставити стабільну чи попередню версію мода B у фоні ({@link com.gummer.launcher.manage.ModSwap}). */
    private static void swapMod(final JDialog dialog, final Instance instance, final CrashAnalyzer.Diagnosis d, final JButton b) {
        final String label = b.getText();
        b.setEnabled(false);
        b.setText(tr("crash.action.working"));
        new SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() throws Exception {
                String game = com.gummer.launcher.manage.ModrinthBrowse.gameVersionOf(instance);
                String[] loaders = com.gummer.launcher.manage.ModrinthBrowse.loadersOf(com.gummer.launcher.manage.ModrinthBrowse.Kind.MOD, instance);
                return com.gummer.launcher.manage.ModSwap.swap(d.depFile, d.depPrerelease, game, loaders);
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
                    String msg = t instanceof com.gummer.launcher.manage.ModUpdates.LoaderTooOld ? t.getLocalizedMessage()
                            : tr("crash.swapMod.failed", d.depName, String.valueOf(t.getLocalizedMessage()));
                    JOptionPane.showMessageDialog(dialog, msg, tr("crash.windowTitle"), JOptionPane.WARNING_MESSAGE);
                    return;
                }
                dialog.dispose();
                JOptionPane.showMessageDialog(dialog.getOwner(), tr("crash.swapMod.done", d.depName, v), tr("crash.windowTitle"),
                        JOptionPane.INFORMATION_MESSAGE);
            }
        }.execute();
    }

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
                    JOptionPane.showMessageDialog(dialog, tr("crash.updateMod.failed", String.valueOf(t.getLocalizedMessage())), tr("crash.windowTitle"), JOptionPane.WARNING_MESSAGE);
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

    /**
     * 19.11.2026: параметр Java з налаштувань гравця, якого Java не знає. Шукається в параметрах збірки, потім у
     * загальних; прибирається лише цей параметр, решта рядка лишається як була.
     */
    static final class JvmArg {
        final String token;
        final com.skcraft.launcher.Launcher launcher;
        final Instance instance;
        final boolean inInstance;

        private JvmArg(String token, com.skcraft.launcher.Launcher launcher, Instance instance, boolean inInstance) {
            this.token = token;
            this.launcher = launcher;
            this.instance = instance;
            this.inInstance = inInstance;
        }

        static JvmArg find(com.skcraft.launcher.Launcher launcher, Instance instance, String option) {
            if (option == null || option.isEmpty()) return null;
            String own = instance.getSettings() == null ? null : instance.getSettings().getCustomJvmArgs();
            String t = match(own, option);
            if (t != null) return new JvmArg(t, launcher, instance, true);
            String global = launcher == null ? null : launcher.getConfig().getJvmArgs();
            t = match(global, option);
            return t != null ? new JvmArg(t, launcher, instance, false) : null;
        }

        /** Параметр у рядку: «-XX:+Opt», «-XX:Opt=1» для «Opt», або точний збіг («-foo» з «Unrecognized option»). */
        private static String match(String raw, String option) {
            if (raw == null || raw.trim().isEmpty()) return null;
            String opt = option.replaceFirst("^-XX:[+-]?", "");
            int eq = opt.indexOf('=');
            if (eq > 0) opt = opt.substring(0, eq);
            for (String a : com.skcraft.launcher.launch.JavaProcessBuilder.splitArgs(raw)) {
                if (a.equals(option)) return a;
                if (a.matches("-XX:[+-]?" + java.util.regex.Pattern.quote(opt) + "(=.*)?")) return a;
            }
            return null;
        }

        void remove() {
            if (inInstance) {
                instance.getSettings().setCustomJvmArgs(cut(instance.getSettings().getCustomJvmArgs()));
                com.skcraft.launcher.persistence.Persistence.commitAndForget(instance);
            } else {
                launcher.getConfig().setJvmArgs(cut(launcher.getConfig().getJvmArgs()));
                com.skcraft.launcher.persistence.Persistence.commitAndForget(launcher.getConfig());
            }
        }

        private String cut(String raw) {
            int i = raw.indexOf(token);
            if (i < 0) return raw;
            return (raw.substring(0, i) + raw.substring(i + token.length())).replaceAll("\\s{2,}", " ").trim();
        }
    }

    /** Причини в модах: для них кнопка «Моди збірки». */
    private static final java.util.Set<String> MOD_KEYS = new java.util.HashSet<String>(java.util.Arrays.asList(
            "duplicateMod", "modConflict", "mixin", "missingDep", "depTooOld", "modCrash", "modClassMissing",
            "modBreaksMod", "modSuspect", "modStack"));
    /** Причини, де стара порада радить перевстановити пак: для збірок зі своїми модами є інша (adviceOwn). */
    private static final java.util.Set<String> OWN_ADVICE = new java.util.HashSet<String>(java.util.Arrays.asList(
            "duplicateMod", "modConflict", "mixin"));

    /** Назва мода для гравця: з jar (кешовано), інакше файл. */
    private static String modTitle(com.gummer.launcher.manage.InstanceMods.Mod m) {
        com.gummer.launcher.manage.ModInfo info = com.gummer.launcher.manage.ModInfo.peek(m.file);
        if (info == null) info = com.gummer.launcher.manage.ModInfo.of(m.file);
        return info != null && info.name != null ? info.name : m.name;
    }

    /** 25.11.2026 (тікет #122): абзац «Перед цим запуском лаунчер сам оновив» під порадою, або порожньо. */
    private static String autoFixHtml(java.util.List<com.gummer.launcher.manage.AutoFixLog.Fix> fixes) {
        if (fixes.isEmpty()) return "";
        java.util.List<String> what = new java.util.ArrayList<String>();
        for (com.gummer.launcher.manage.AutoFixLog.Fix f : fixes) what.add(f.dep + " " + f.from + " → " + f.to);
        return "<br><br><b>" + SwingHelper.htmlEscape(tr("crash.autoFix.title")) + "</b><br>"
                + SwingHelper.htmlEscape(tr("crash.autoFix.text", String.join(", ", what)));
    }

    /** 25.11.2026: абзац «Нещодавно додані моди» під порадою, або порожньо. */
    private static String recentHtml(CrashAnalyzer.Diagnosis d) {
        if (d.recentMods.isEmpty()) return "";
        java.util.List<String> names = new java.util.ArrayList<String>();
        for (com.gummer.launcher.manage.InstanceMods.Mod m : d.recentMods) names.add(modTitle(m));
        return "<br><br><b>" + SwingHelper.htmlEscape(tr("crash.recent.title")) + "</b><br>"
                + SwingHelper.htmlEscape(tr("crash.recent.text", String.join(", ", names)));
    }

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
        // 25.11.2026 (тікет #122): завантажувач збірки, який можна підняти кнопкою (своя збірка, той самий завантажувач)
        final com.gummer.launcher.manage.LoaderVersion lv = d.action == CrashAnalyzer.Action.UPDATE_LOADER && ownMods
                ? com.gummer.launcher.manage.LoaderVersion.of(instance) : null;
        final boolean loaderButton = lv != null && lv.loader.equals(d.loader) && !lv.satisfies(d.loaderNeed);
        // 25.11.2026 (тікет #122): що лаунчер сам оновив перед цим запуском, а гра після того ще не закрилась без вильоту
        final java.util.List<com.gummer.launcher.manage.AutoFixLog.Fix> autoFixes = ownMods
                ? com.gummer.launcher.manage.AutoFixLog.pending(instance) : java.util.Collections.<com.gummer.launcher.manage.AutoFixLog.Fix>emptyList();
        final String adviceKey = "loaderTooOld".equals(d.key) && !loaderButton ? "crash.loaderTooOld.adviceManual"
                : ownMods && OWN_ADVICE.contains(d.key) ? "crash." + d.key + ".adviceOwn" : "crash." + d.key + ".advice";
        String html = "<html><div style=\"width: 440px\">"
                + "<span style=\"font-size: 15px\"><b>" + SwingHelper.htmlEscape(tr("crash.headline", instance.getTitle())) + "</b></span><br><br>"
                + "<b>" + SwingHelper.htmlEscape(title(d, detail)) + "</b><br>"
                + SwingHelper.htmlEscape(tr(adviceKey, args(d, detail))).replace("\n", "<br>")
                + autoFixHtml(autoFixes)
                + recentHtml(d)
                + ("modClassMissing".equals(d.key) && d.depLocked
                        ? "<br><br>" + SwingHelper.htmlEscape(tr("crash.classFix.locked", d.depId, d.modName != null ? d.modName : "?")) : "")
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
                    primary = disableButton(dialog, owner, instance, java.util.Collections.singletonList(d.modFile), d.modName);
                }
                break;
            case FIX_OTHER_MOD:
                // 27.11.2026 (тікет #139): мод A не вставився в мод B. Головна дія: інша версія B (стабільна замість бети,
                // інакше попередня); поруч «Вимкнути A» разом з тими, кому A потрібен
                if (ownMods && d.depFile != null) {
                    final JButton swap = new JButton(tr(d.depPrerelease ? "crash.action.stableMod" : "crash.action.prevMod", d.depName));
                    swap.setToolTipText(tr(d.depPrerelease ? "crash.action.stableModTip" : "crash.action.prevModTip", d.depName,
                            d.depVersion != null ? d.depVersion : "?"));
                    swap.addActionListener(e -> swapMod(dialog, instance, d, swap));
                    primary = swap;
                }
                if (d.modFile != null) {
                    JButton off = disableButton(dialog, owner, instance, java.util.Collections.singletonList(d.modFile), d.modName);
                    if (primary == null) primary = off;
                    else buttons.add(off);
                }
                break;
            case RESET_CONFIG:
                // 19.11.2026: конфіг переноситься в .gummer/broken-config (не видаляється), мод створить новий
                if (d.configFile != null && d.configFile.isFile()) {
                    final String cfgName = d.configFile.getName();
                    primary = new JButton(tr("crash.action.resetConfig", cfgName));
                    primary.setToolTipText(tr("crash.action.resetConfigTip"));
                    primary.addActionListener(e -> {
                        try {
                            java.io.File dir = new java.io.File(new java.io.File(instance.getContentDir(), ".gummer"), "broken-config");
                            dir.mkdirs();
                            java.nio.file.Files.move(d.configFile.toPath(), new java.io.File(dir, System.currentTimeMillis() + "-" + cfgName).toPath());
                            dialog.dispose();
                            JOptionPane.showMessageDialog(owner, tr("crash.configReset", cfgName), tr("crash.windowTitle"), JOptionPane.INFORMATION_MESSAGE);
                        } catch (java.io.IOException ex) {
                            SwingHelper.showErrorDialog(dialog, tr("crash.configResetFailed", cfgName), tr("errorTitle"), ex);
                        }
                    });
                }
                break;
            case JVM_ARG: {
                // 19.11.2026: кнопка лише якщо параметр вписав гравець (у збірці чи в загальних налаштуваннях)
                final JvmArg arg = JvmArg.find(launcher, instance, d.detail);
                if (arg != null) {
                    primary = new JButton(tr("crash.action.removeArg", arg.token));
                    primary.addActionListener(e -> {
                        arg.remove();
                        dialog.dispose();
                        JOptionPane.showMessageDialog(owner, tr("crash.argRemoved", arg.token), tr("crash.windowTitle"), JOptionPane.INFORMATION_MESSAGE);
                    });
                }
                break;
            }
            case MOVE_DATA_DIR:
                // 20.11.2026 (тікет #88): лише після справжнього вильоту, щоб не чіпати тих, у кого гра з Документів іде
                if (launcher != null) {
                    primary = new JButton(tr("datadir.moveButton"));
                    primary.addActionListener(e -> {
                        dialog.dispose();
                        com.gummer.launcher.util.DataDirCheck.offerMove(owner, launcher);
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
            case EARLY_WINDOW:
                // 25.11.2026 (тікет #121): ранній екран Forge вимикається в config/fml.toml, драйвер лишається запасною порадою
                if (d.configFile != null) {
                    primary = new JButton(tr("crash.action.earlyWindow"));
                    primary.setToolTipText(tr("crash.action.earlyWindowTip"));
                    primary.addActionListener(e -> {
                        try {
                            CrashAnalyzer.disableEarlyWindow(d.configFile);
                            dialog.dispose();
                            JOptionPane.showMessageDialog(owner, tr("crash.earlyWindowDone"), tr("crash.windowTitle"), JOptionPane.INFORMATION_MESSAGE);
                        } catch (java.io.IOException ex) {
                            SwingHelper.showErrorDialog(dialog, tr("crash.earlyWindowFailed"), tr("errorTitle"), ex);
                        }
                    });
                    final String drv = driversHelpUrl;
                    if (drv != null && !drv.isEmpty()) {
                        JButton b = new JButton(tr("crash.action.drivers"));
                        b.addActionListener(e -> SwingHelper.openURL(drv, dialog));
                        buttons.add(b);
                    }
                }
                break;
            case WINDOWS_PROTECTION:
                // 25.11.2026 (тікети #111, #113): сторінка «Захист від програм-вимагачів» у «Безпеці Windows» (Windows 10+);
                // не відкрилась: лише текст поради
                if (com.skcraft.launcher.util.Environment.detectPlatform() == com.skcraft.launcher.util.Platform.WINDOWS) {
                    primary = new JButton(tr("crash.action.windowsProtection"));
                    primary.addActionListener(e -> {
                        try {
                            java.awt.Desktop.getDesktop().browse(new java.net.URI("windowsdefender://ransomwareprotection"));
                        } catch (Exception ex) {
                            JOptionPane.showMessageDialog(dialog, tr("crash.windowsProtectionManual"), tr("crash.windowTitle"),
                                    JOptionPane.INFORMATION_MESSAGE);
                        }
                    });
                }
                break;
            case UPDATE_LOADER:
                // 25.11.2026 (тікет #122): найменша стабільна версія, не нижча за ту, яку назвав сам завантажувач
                if (loaderButton) {
                    final String to = lv.pick(launcher, d.loaderNeed);
                    primary = new JButton(tr("crash.action.updateLoader", lv.title(), to));
                    primary.setToolTipText(tr("crash.action.updateLoaderTip", lv.title(), lv.version));
                    primary.addActionListener(e -> {
                        try {
                            com.gummer.launcher.manage.LoaderVersion.raise(instance, to);
                            dialog.dispose();
                            JOptionPane.showMessageDialog(owner, tr("crash.loaderUpdated", lv.title(), to), tr("crash.windowTitle"), JOptionPane.INFORMATION_MESSAGE);
                        } catch (java.io.IOException ex) {
                            SwingHelper.showErrorDialog(dialog, tr("crash.loaderUpdateFailed", lv.title()), tr("errorTitle"), ex);
                        }
                    });
                }
                break;
            default:
                break;
        }
        // 25.11.2026 (тікет #122): гра впала після того, як лаунчер сам оновив моди перед запуском: повернути як було.
        // Головна дія лише якщо іншої немає; відкочене лаунчер більше сам не оновлює (AutoFixLog)
        if (!autoFixes.isEmpty()) {
            JButton undo = new JButton(tr("crash.action.undoAutoFix"));
            undo.setToolTipText(tr("crash.action.undoAutoFixTip"));
            undo.addActionListener(e -> {
                try {
                    java.util.List<String> back = com.gummer.launcher.manage.AutoFixLog.rollbackAll(instance);
                    dialog.dispose();
                    JOptionPane.showMessageDialog(owner, tr("crash.autoFixUndone", String.join(", ", back)), tr("crash.windowTitle"),
                            JOptionPane.INFORMATION_MESSAGE);
                } catch (java.io.IOException ex) {
                    SwingHelper.showErrorDialog(dialog, tr("crash.autoFixUndoFailed"), tr("errorTitle"), ex);
                }
            });
            if (primary == null) primary = undo;
            else buttons.add(undo);
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
                : "modCrash".equals(d.key) || "mixin".equals(d.key) || "modClassMissing".equals(d.key)
                        || "modSuspect".equals(d.key) || "modStack".equals(d.key) ? d.modFile : null;
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
        // 25.11.2026 (власник: «а чому б не запропонувати скачати правильний ентіті кулінг? ... Якщо є якась залежність, щоб
        // лаунчер її детектив і докачував»): головна дія лагодить залежність (оновити чи докачати з перевіркою класу),
        // «Вимкнути» мод, що звертається, лишається поруч
        // залежність з модпаку GummerCraft не чіпаємо (depLocked): там лише порада в тексті
        if ("modClassMissing".equals(d.key) && d.depId != null && !d.depLocked) {
            final boolean have = d.depFile != null;
            final JButton fix = new JButton(tr(have ? "crash.action.updateMod" : "crash.action.fetchDep", d.depId));
            fix.setToolTipText(tr(have ? "crash.action.fixDepTip" : "crash.action.fetchDepTip"));
            fix.addActionListener(e -> fixClass(dialog, owner, instance, d, fix));
            if (primary != null) buttons.add(primary);
            primary = fix;
        }
        // 19.11.2026 (власник: «Відкат моду після оновлення ... давай»): винний мод оновлено нещодавно, а стара версія
        // збережена (ModRollback): повернути її головною дією, бо до оновлення гра працювала
        // 27.11.2026 (тікет #139): після «Поставити стабільну Sodium» гра знову впала на тій самій парі: «Повернути Sodium»
        final boolean breaks = "modBreaksMod".equals(d.key);
        final com.gummer.launcher.manage.ModRollback.Entry prev = !ownMods ? null
                : breaks ? com.gummer.launcher.manage.ModRollback.recentFor(d.depFile)
                : "modCrash".equals(d.key) || "mixin".equals(d.key) || "modClassMissing".equals(d.key)
                        || "modSuspect".equals(d.key) || "modStack".equals(d.key)
                ? com.gummer.launcher.manage.ModRollback.recentFor(d.modFile) : null;
        if (prev != null) {
            final String name = breaks ? d.depName : d.modName != null ? d.modName : d.modFile.getName();
            final String ver = prev.prevVersion == null || prev.prevVersion.isEmpty() || "?".equals(prev.prevVersion) ? "" : " " + prev.prevVersion;
            JButton back = new JButton(tr("crash.action.modBack", name, ver));
            back.setToolTipText(tr("crash.action.modBackTip"));
            back.addActionListener(e -> {
                try {
                    com.gummer.launcher.manage.ModRollback.rollback(prev);
                    dialog.dispose();
                    JOptionPane.showMessageDialog(owner, tr("crash.modBackDone", name, ver), tr("crash.windowTitle"), JOptionPane.INFORMATION_MESSAGE);
                } catch (java.io.IOException ex) {
                    SwingHelper.showErrorDialog(dialog, tr("crash.modBackFailed", name), tr("errorTitle"), ex);
                }
            });
            if (breaks) {
                buttons.add(back);
            } else {
                if (primary != null) buttons.add(primary);
                primary = back;
            }
        }
        // 25.11.2026 (власник: «виною були три нові моди, жоден з них наш лаунчер не задетектив»): винного мода не названо,
        // але після останньої вдалої гри з'явились нові моди: вимкнути їх одним кліком. Головна дія, якщо іншої немає або
        // це «невідомо» чи «пошкоджені файли» (перевстановлення нових модів гравця не прибирає)
        if (!d.recentMods.isEmpty()) {
            // 27.11.2026: разом з модами, які без нових не запустяться (ModDependents)
            java.util.List<java.io.File> roots = new java.util.ArrayList<java.io.File>();
            for (com.gummer.launcher.manage.InstanceMods.Mod m : d.recentMods) roots.add(m.file);
            java.util.List<com.gummer.launcher.manage.InstanceMods.Mod> chain = com.gummer.launcher.manage.ModDependents.withDependents(instance, roots);
            final java.util.List<com.gummer.launcher.manage.InstanceMods.Mod> recent = chain.size() >= d.recentMods.size() ? chain : d.recentMods;
            JButton off = new JButton(recent.size() > d.recentMods.size()
                    ? tr("crash.action.disableRecentWith", d.recentMods.size(), recent.size() - d.recentMods.size())
                    : tr("crash.action.disableRecent", recent.size()));
            off.addActionListener(e -> {
                java.util.List<String> done = new java.util.ArrayList<String>();
                for (com.gummer.launcher.manage.InstanceMods.Mod m : recent) {
                    try {
                        com.gummer.launcher.manage.InstanceMods.setEnabled(instance, m, false);
                        done.add(modTitle(m));
                    } catch (java.io.IOException ex) {
                        SwingHelper.showErrorDialog(dialog, tr("crash.recentDisableFailed", modTitle(m)), tr("errorTitle"), ex);
                        return;
                    }
                }
                dialog.dispose();
                JOptionPane.showMessageDialog(owner, tr("crash.recentDisabled", String.join(", ", done)), tr("crash.windowTitle"),
                        JOptionPane.INFORMATION_MESSAGE);
            });
            if (primary == null || "unknown".equals(d.key) || "corrupt".equals(d.key)) {
                if (primary != null) buttons.add(primary);
                primary = off;
            } else {
                buttons.add(off);
            }
        }
        // 19.11.2026: виліт на істоті чи блоці у світі: «Світи збірки», якщо є бекап світів цієї збірки
        final UiHooks.InstanceAction worlds = UiHooks.openWorlds;
        if ("worldTick".equals(d.key) && worlds != null && launcher != null
                && !com.gummer.launcher.manage.Worlds.backups(launcher, instance).isEmpty()) {
            JButton b = new JButton(tr("crash.action.worlds"));
            b.setToolTipText(tr("crash.action.worldsTip"));
            b.addActionListener(e -> {
                dialog.dispose();
                worlds.run(owner, instance);
            });
            buttons.add(b);
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
                String text = tr("crash.supportText", instance.getTitle(), title(d, detail));
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
