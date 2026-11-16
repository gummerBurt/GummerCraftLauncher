/*
 * Copyright (C) 2021-2026 GummerCraft <http://gummercraft.fun>
 * Please see LICENSE.txt for license information.
 */

package com.gummer.launcher.stats;

import com.skcraft.launcher.Instance;
import com.skcraft.launcher.Launcher;
import com.skcraft.launcher.util.HttpRequest;

import java.io.File;
import java.net.URL;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * Анонімна подія «гру запущено» (з 1.33.1), щоб бачити, чим користуються гравці: нашими збірками, Vanilla чи
 * завантажувачами модів. Без неї цього не видно, бо файли Mojang, Fabric, Forge лаунчер качає повз наш CDN.
 *
 * <p>Що надсилається (один запит GET на наш CDN, коли гра стартувала):</p>
 * <ul>
 *   <li>{@code e=launch};</li>
 *   <li>{@code p}: яка збірка. Для наших збірок це назва зі списку на CDN ({@code sdayz-beta}, {@code main-26.2}),
 *       для Vanilla і завантажувачів тип і версія Minecraft ({@code vanilla-1.21.4}, {@code fabric-1.20.1}).
 *       Усе інше (імпортовані збірки, локальні теки) іде як {@code mrpack} чи {@code other}: назви, які гравець
 *       дав сам, не надсилаються ніколи;</li>
 *   <li>{@code i}: анонімний ID установки ({@link InstallId}), якщо він є.</li>
 * </ul>
 *
 * <p>Чого тут немає: ніка, акаунта, списку модів, шляхів, даних про комп'ютер (версію лаунчера, ОС і Java, як і в
 * кожному запиті лаунчера, видно із заголовка User-Agent). Слово {@code off} у {@code stats-id.txt} вимикає подію
 * повністю. Порожній {@code launchStatsUrl} у {@code launcher.properties} теж. Запит іде у фоні, помилки мовчки
 * ігноруються: на запуск гри він не впливає.</p>
 */
public final class LaunchStats {

    private static final Logger log = Logger.getLogger(LaunchStats.class.getName());
    /** Лише такі назви йдуть у запит: малі латинські літери, цифри, крапка, дефіс, підкреслення, до 40 символів. */
    private static final Pattern SAFE = Pattern.compile("[a-z0-9][a-z0-9._-]{0,39}");
    private static final String OTHER = "other";

    private LaunchStats() {
    }

    /** Гра стартувала: надіслати подію у фоні. Ніколи не кидає винятків. */
    public static void gameStarted(Launcher launcher, Instance instance) {
        try {
            final URL url = buildUrl(launcher, instance);
            if (url == null) {
                return;
            }
            Thread thread = new Thread(new Runnable() {
                @Override
                public void run() {
                    send(url);
                }
            }, "launch-stats");
            thread.setDaemon(true);
            thread.start();
        } catch (Throwable t) {
            log.log(Level.FINE, "Launch statistics are unavailable", t);
        }
    }

    /** Адреса події або null, якщо надсилати не треба (вимкнено гравцем, не задано адресу, чужий хост). */
    static URL buildUrl(Launcher launcher, Instance instance) {
        if (instance == null) {
            return null;
        }
        return eventUrl(launcher, "e=launch&p=" + packOf(instance));
    }

    /**
     * 16.11.2026: спільна адреса для анонімних подій ({@code launch}, {@code dl}): база з {@code launchStatsUrl},
     * параметри query і анонімний ID. null, якщо гравець вимкнув статистику, адреси немає або хост не наш.
     */
    static URL eventUrl(Launcher launcher, String query) {
        String base = launcher.getProperties().getProperty("launchStatsUrl");
        if (base == null || base.trim().isEmpty()) {
            return null;
        }
        File baseDir = launcher.getBaseDir();
        if (InstallId.isOff(baseDir)) {
            return null;
        }
        URL url = HttpRequest.url(base.trim());
        if (!isOurHost(url)) {
            return null;
        }
        StringBuilder sb = new StringBuilder(url.toString());
        sb.append(url.getQuery() != null ? '&' : '?').append(query);
        String id = InstallId.get(baseDir);
        if (id != null) {
            sb.append("&i=").append(id);
        }
        return HttpRequest.url(sb.toString());
    }

    /** Що піде в параметр {@code p}: назва нашої збірки, тип і версія для Vanilla та завантажувачів, інакше загальне слово. */
    static String packOf(Instance instance) {
        String name = instance.getName();
        if (name == null) {
            return OTHER;
        }
        name = name.toLowerCase(Locale.ROOT);
        if (instance.isMrpack()) {
            return "mrpack";
        }
        boolean generated = instance.isVanilla() || instance.isLoaderPack();
        boolean ours = !generated && isOurHost(instance.getManifestURL());
        if ((generated || ours) && SAFE.matcher(name).matches()) {
            return name;
        }
        return OTHER;
    }

    static boolean isOurHost(URL url) {
        if (url == null || url.getHost() == null) {
            return false;
        }
        String host = url.getHost().toLowerCase(Locale.ROOT);
        return host.equals("gummercraft.fun") || host.endsWith(".gummercraft.fun");
    }

    static void send(URL url) {
        HttpRequest request = null;
        try {
            request = HttpRequest.get(url);
            request.execute();
            log.fine("Launch statistics sent, response " + request.getResponseCode());
        } catch (Throwable t) {
            log.log(Level.FINE, "Launch statistics were not sent", t);
        } finally {
            if (request != null) {
                try {
                    request.close();
                } catch (Throwable ignored) {
                    // нічого: статистика не має заважати грі
                }
            }
        }
    }
}
