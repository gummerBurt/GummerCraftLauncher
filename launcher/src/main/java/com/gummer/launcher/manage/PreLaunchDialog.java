/*
 * Copyright (C) 2021-2026 GummerCraft <http://gummercraft.fun>
 */

package com.gummer.launcher.manage;

import com.skcraft.launcher.Instance;
import com.skcraft.launcher.LauncherException;
import com.skcraft.launcher.util.SharedLocale;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 17.11.2026 ~23:30: вікно перевірки перед запуском ({@link PreLaunchCheck}). Викликається з потоку запуску (Runner):
 * питає гравця в потоці інтерфейсу і чекає відповіді. «Вимкнути і запустити» вимикає зайве (файли лишаються в теці),
 * «Запустити як є» йде далі, «Скасувати» зупиняє запуск.
 */
public final class PreLaunchDialog {

    private static final Logger log = Logger.getLogger(PreLaunchDialog.class.getName());

    private PreLaunchDialog() {
    }

    public static void ask(final Instance instance) throws LauncherException {
        final List<PreLaunchCheck.Issue> issues;
        try {
            issues = PreLaunchCheck.find(instance);
        } catch (RuntimeException e) {
            log.log(Level.WARNING, "Pre-launch check failed", e);
            return; // перевірка не має заважати запуску
        }
        if (issues.isEmpty()) return;
        log.info("Pre-launch check: " + issues.size() + " issue(s)");
        final int[] answer = {-1};
        // 1.36.1: і мод не під завантажувач збірки (той самий мод під потрібний завантажувач з Modrinth, див. LoaderSwap)
        final boolean canFetch = hasMissing(issues) || hasWrongLoader(issues);
        try {
            SwingUtilities.invokeAndWait(() -> answer[0] = show(instance, issues, canFetch));
        } catch (Exception e) {
            return;
        }
        if (answer[0] == FETCH) {
            // 18.11.2026 (власник: «докачати цей мод? Або докачати автоматично»): залежності з Modrinth, потім ще раз
            // перевірка; що лишилось, пропонується вимкнути як раніше (без повторного «Докачати»)
            List<String> missing = new java.util.ArrayList<String>();
            // 1.36.1 (власник: «Шукати відповідники модів з неофордж в форджі та заміняти їх»): спершу заміна модів не під
            // той завантажувач, бо в нових версій можуть бути інші залежності
            for (PreLaunchCheck.Issue is : issues) {
                // 19.11.2026: і мод не під ту версію гри (та сама заміна: версія проекту під гру і завантажувач збірки)
                if ((is.kind != PreLaunchCheck.Kind.WRONG_LOADER && is.kind != PreLaunchCheck.Kind.WRONG_GAME) || is.disable.isEmpty()) continue;
                File f = is.disable.get(0).file;
                try {
                    LoaderSwap.replace(instance, f, ModInfo.of(f), missing);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new LauncherException("Cancelled", SharedLocale.tr("prelaunch.cancelled"));
                }
            }
            for (PreLaunchCheck.Issue is : issues) {
                if (is.kind != PreLaunchCheck.Kind.MISSING || is.disable.isEmpty()) continue;
                if (!is.disable.get(0).file.isFile()) continue; // мод щойно замінено іншою версією
                try {
                    DepFetch.fetch(instance, is.disable.get(0).file, java.util.Arrays.asList(is.detail.split(",\\s*")), missing);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new LauncherException("Cancelled", SharedLocale.tr("prelaunch.cancelled"));
                }
            }
            final List<PreLaunchCheck.Issue> left;
            try {
                left = PreLaunchCheck.find(instance);
            } catch (RuntimeException e) {
                return;
            }
            if (left.isEmpty()) return;
            answer[0] = -1;
            try {
                SwingUtilities.invokeAndWait(() -> answer[0] = show(instance, left, false));
            } catch (Exception e) {
                return;
            }
            if (answer[0] == 0) {
                PreLaunchCheck.disableAll(instance, left);
            } else if (answer[0] != 1) {
                throw new LauncherException("Cancelled by pre-launch check", SharedLocale.tr("prelaunch.cancelled"));
            }
            return;
        }
        if (answer[0] == 0) {
            int n = PreLaunchCheck.disableAll(instance, issues);
            log.info("Pre-launch check: disabled " + n + " mod file(s)");
        } else if (answer[0] != 1) {
            throw new LauncherException("Cancelled by pre-launch check", SharedLocale.tr("prelaunch.cancelled"));
        }
    }

    private static String esc(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /** Відповідь «Докачати і запустити». */
    private static final int FETCH = 10;

    private static boolean hasMissing(List<PreLaunchCheck.Issue> issues) {
        for (PreLaunchCheck.Issue is : issues) if (is.kind == PreLaunchCheck.Kind.MISSING) return true;
        return false;
    }

    private static boolean hasWrongLoader(List<PreLaunchCheck.Issue> issues) {
        for (PreLaunchCheck.Issue is : issues) if (is.kind == PreLaunchCheck.Kind.WRONG_LOADER || is.kind == PreLaunchCheck.Kind.WRONG_GAME) return true;
        return false;
    }

    /** 0 вимкнути і запустити, 1 запустити як є, {@link #FETCH} докачати, інше скасувати. */
    private static int show(Instance instance, List<PreLaunchCheck.Issue> issues, boolean canFetch) {
        StringBuilder html = new StringBuilder("<html><body style='width:460px'>");
        html.append("<b>").append(esc(SharedLocale.tr("prelaunch.title", instance.getTitle()))).append("</b><br><br>");
        int shown = 0;
        for (PreLaunchCheck.Issue is : issues) {
            if (shown++ == 8) {
                html.append(esc(SharedLocale.tr("prelaunch.more", issues.size() - 8))).append("<br>");
                break;
            }
            String key = is.kind == PreLaunchCheck.Kind.DUPLICATE ? "prelaunch.duplicate"
                    : is.kind == PreLaunchCheck.Kind.WRONG_LOADER ? "prelaunch.wrongLoader"
                    : is.kind == PreLaunchCheck.Kind.WRONG_GAME ? "prelaunch.wrongGame"
                    : is.kind == PreLaunchCheck.Kind.INCOMPATIBLE ? "prelaunch.incompatible" : "prelaunch.missing";
            html.append("&#8226; ").append(esc(SharedLocale.tr(key, is.mod, is.detail, is.extra))).append("<br>");
        }
        boolean swap = canFetch && hasWrongLoader(issues);
        if (swap) html.append("<br>").append(esc(SharedLocale.tr("prelaunch.swapHint")));
        html.append("<br>").append(esc(SharedLocale.tr("prelaunch.hint"))).append("</body></html>");
        Object[] options = canFetch
                ? new Object[]{SharedLocale.tr(swap ? "prelaunch.swap" : "prelaunch.fetch"), SharedLocale.tr("prelaunch.fix"), SharedLocale.tr("prelaunch.anyway"), SharedLocale.tr("prelaunch.cancel")}
                : new Object[]{SharedLocale.tr("prelaunch.fix"), SharedLocale.tr("prelaunch.anyway"), SharedLocale.tr("prelaunch.cancel")};
        Window owner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getActiveWindow();
        if (owner == null) {
            for (Frame f : Frame.getFrames()) if (f.isShowing()) owner = f;
        }
        int r = JOptionPane.showOptionDialog(owner, html.toString(), SharedLocale.tr("prelaunch.windowTitle"),
                JOptionPane.DEFAULT_OPTION, JOptionPane.WARNING_MESSAGE, null, options, options[0]);
        if (!canFetch) return r;
        return r == 0 ? FETCH : r > 0 ? r - 1 : r;
    }
}
