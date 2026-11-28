/*
 * Copyright (C) 2021-2026 GummerCraft <http://gummercraft.fun>
 */

package com.gummer.launcher.stats;

import com.skcraft.launcher.Instance;
import com.skcraft.launcher.Launcher;

import java.net.URL;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 16.11.2026: анонімна подія «завантаження збірки» ({@code e=dl}), щоб бачити, де в гравців висить установка, не
 * чекаючи звернень: гравці про «зависло на 66%» не пишуть. Рахується за одне встановлення чи оновлення збірки.
 *
 * <p>Що надсилається (один GET на наш CDN наприкінці, той самий адрес, що і в «гру запущено»):</p>
 * <ul>
 *   <li>{@code p}: збірка, як у {@link LaunchStats#packOf};</li>
 *   <li>{@code ok}: 1 вдалось, 0 ні; {@code t}: скільки секунд ішло завантаження;</li>
 *   <li>для кожного типу джерела ({@code c} наш CDN, {@code m} Mojang, {@code v} maven завантажувачів,
 *       {@code o} інше): {@code КБ.мс.файлів.помилок.повільних} (повільний: файл від 256 КБ, що йшов повільніше
 *       за 100 КБ/с);</li>
 *   <li>{@code a}: архів бази Minecraft ({@code -} не потрібен, {@code ok}, {@code resumed}, {@code fail});</li>
 *   <li>{@code i}: анонімний ID установки ({@link InstallId});</li>
 *   <li>з 1.41.6, лише якщо не вдалось: {@code s} крок ({@link #stage}), {@code x} клас помилки ({@link #classify}),
 *       {@code jw} де стоїть Java ({@code d} тека даних, {@code f} запасна, {@code o} своя); {@code r=1}, якщо лаунчер
 *       сам повторив після перемикання теки Java. Невдача шлеться і тоді, коли нічого не качалось.</li>
 * </ul>
 * <p>Ні ніка, ні адрес файлів, ні шляхів. Вимикається так само, як подія запуску ({@code off} у
 * {@code stats-id.txt} або порожній {@code launchStatsUrl}).</p>
 */
public final class DownloadStats {

    private static final Logger log = Logger.getLogger(DownloadStats.class.getName());
    private static final long SLOW_MIN_BYTES = 256 * 1024;
    private static final double SLOW_BYTES_PER_SEC = 100 * 1024;

    private static volatile DownloadStats current;

    private final long startedAt = System.currentTimeMillis();
    private final long[][] perSource = new long[4][5]; // байти, мс, файлів, помилок, повільних
    private volatile String archive = "-";
    private volatile String stage = "-";
    private volatile String error = null;
    private volatile boolean retried;

    private DownloadStats() {
    }

    /** Почати рахувати нове встановлення чи оновлення. */
    public static void begin() {
        current = new DownloadStats();
    }

    /**
     * 1.41.6: на якому кроці зараз встановлення ({@code manifest}, {@code java}, {@code base}, {@code libs},
     * {@code download}, {@code install}, {@code late}). Іде в подію як {@code s}, щоб бачити, де падає.
     */
    public static void stage(String name) {
        DownloadStats s = current;
        if (s != null && name != null) s.stage = name;
    }

    /** 1.41.6: встановлення впало; клас помилки ({@link #classify}) іде в подію як {@code x}. Ніколи не кидає. */
    public static void failed(Throwable t) {
        DownloadStats s = current;
        if (s == null) return;
        try {
            s.error = classify(t);
        } catch (Throwable ignored) {
            s.error = "other";
        }
    }

    /** 1.41.6: була автоматична друга спроба після перемикання теки Java ({@code r=1}). */
    public static void retried() {
        DownloadStats s = current;
        if (s != null) s.retried = true;
    }

    /**
     * Короткий клас помилки без шляхів, назв файлів і тексту: {@code cancel}, {@code javamoved}, {@code javablocked},
     * {@code denied}, {@code notfound}, {@code space}, {@code http404} (чи інший код), {@code net}, {@code hash},
     * {@code launcher}, інакше коротка назва класу винятку.
     */
    static String classify(Throwable t) {
        String first = null;
        for (Throwable c = t; c != null; c = c.getCause() == c ? null : c.getCause()) {
            if (c instanceof InterruptedException || c instanceof java.io.InterruptedIOException
                    || c instanceof java.util.concurrent.CancellationException) return "cancel";
            if (c instanceof com.gummer.launcher.install.JavaBlockedException) {
                return ((com.gummer.launcher.install.JavaBlockedException) c).moved ? "javamoved" : "javablocked";
            }
            String m = String.valueOf(c.getMessage()).toLowerCase(Locale.ROOT);
            if (c instanceof java.nio.file.AccessDeniedException || m.contains("access is denied")
                    || m.contains("отказано в доступе") || m.contains("відмовлено в доступі")
                    || m.contains("being used by another process")) return "denied";
            if (m.contains("not enough space") || m.contains("no space left") || m.contains("недостаточно места")
                    || m.contains("недостатньо місця")) return "space";
            java.util.regex.Matcher code = java.util.regex.Pattern.compile("got (\\d{3})").matcher(m);
            if (code.find()) return "http" + code.group(1);
            if (c instanceof java.net.UnknownHostException || c instanceof java.net.SocketTimeoutException
                    || c instanceof java.net.ConnectException || c instanceof java.net.SocketException
                    || c instanceof javax.net.ssl.SSLException) return "net";
            if (c instanceof java.io.FileNotFoundException || c instanceof java.nio.file.NoSuchFileException) return "notfound";
            if (m.contains("invalid hash") || m.contains("is corrupt")) return "hash";
            if (first == null) {
                first = c instanceof com.skcraft.launcher.LauncherException ? "launcher" : c.getClass().getSimpleName();
            }
        }
        if (first == null || first.isEmpty()) return "other";
        return first.length() > 40 ? first.substring(0, 40) : first;
    }

    /** Результат архіву бази Minecraft: ok, resumed, fail. */
    public static void archive(String result) {
        DownloadStats s = current;
        if (s != null && result != null) s.archive = result;
    }

    /** Файл завантажено (ok) чи ні; bytes прочитано цим запитом. Ніколи не кидає. */
    public static void record(URL url, long bytes, long millis, boolean ok) {
        DownloadStats s = current;
        if (s == null || url == null) return;
        try {
            int k = sourceOf(url);
            synchronized (s) {
                long[] r = s.perSource[k];
                r[0] += Math.max(0, bytes);
                r[1] += Math.max(0, millis);
                if (ok) r[2]++;
                else r[3]++;
                if (ok && bytes >= SLOW_MIN_BYTES && millis > 0 && bytes * 1000.0 / millis < SLOW_BYTES_PER_SEC) r[4]++;
            }
        } catch (Throwable ignored) {
            // статистика не має заважати
        }
    }

    /** Завершити і надіслати у фоні (лише якщо щось качалось). Ніколи не кидає. */
    public static void finish(Launcher launcher, Instance instance, boolean ok) {
        DownloadStats s = current;
        current = null;
        if (s == null) return;
        try {
            final URL url = s.buildUrl(launcher, instance, ok);
            if (url == null) return;
            Thread thread = new Thread(() -> LaunchStats.send(url), "download-stats");
            thread.setDaemon(true);
            thread.start();
        } catch (Throwable t) {
            log.log(Level.FINE, "Download statistics are unavailable", t);
        }
    }

    synchronized URL buildUrl(Launcher launcher, Instance instance, boolean ok) {
        long files = 0;
        for (long[] r : perSource) files += r[2] + r[3];
        // 1.41.6: невдача шлеться і тоді, коли нічого не встигло качатись (до 1.41.6 такі спроби не було видно)
        if (ok && files == 0 && "-".equals(archive)) return null;
        StringBuilder q = new StringBuilder("e=dl&p=").append(LaunchStats.packOf(instance))
                .append("&ok=").append(ok ? 1 : 0)
                .append("&t=").append((System.currentTimeMillis() - startedAt) / 1000);
        String[] keys = {"c", "m", "v", "o"};
        for (int k = 0; k < keys.length; k++) {
            long[] r = perSource[k];
            if (r[2] + r[3] == 0) continue;
            q.append('&').append(keys[k]).append('=').append(r[0] / 1024).append('.').append(r[1])
                    .append('.').append(r[2]).append('.').append(r[3]).append('.').append(r[4]);
        }
        q.append("&a=").append(archive);
        if (!ok) {
            q.append("&s=").append(stage).append("&x=").append(error != null ? error : "other");
            q.append("&jw=").append(javaPlace(launcher));
        }
        if (retried) q.append("&r=1");
        return LaunchStats.eventUrl(launcher, q.toString());
    }

    /** Де зараз корінь Java: {@code d} тека даних, {@code f} запасна в %LOCALAPPDATA%, {@code o} своя з налаштувань. */
    static String javaPlace(Launcher launcher) {
        try {
            java.io.File root = com.gummer.launcher.util.JavaPlace.root(launcher).getAbsoluteFile();
            if (root.equals(new java.io.File(launcher.getBaseDir(), "runtime").getAbsoluteFile())) return "d";
            java.io.File fb = com.gummer.launcher.util.JavaPlace.fallbackRoot();
            return fb != null && root.equals(fb.getAbsoluteFile()) ? "f" : "o";
        } catch (Throwable t) {
            return "-";
        }
    }

    /** 0 наш CDN, 1 Mojang, 2 maven завантажувачів, 3 інше. */
    static int sourceOf(URL url) {
        String host = url.getHost() == null ? "" : url.getHost().toLowerCase(Locale.ROOT);
        if (host.equals("gummercraft.fun") || host.endsWith(".gummercraft.fun")
                || host.equals("gummer.cc") || host.endsWith(".gummer.cc")) return 0;
        if (host.endsWith("minecraft.net") || host.endsWith("mojang.com")) return 1;
        if (host.startsWith("maven.") || host.endsWith("neoforged.net") || host.endsWith("fabricmc.net")
                || host.endsWith("quiltmc.org") || host.endsWith("minecraftforge.net")) return 2;
        return 3;
    }
}
