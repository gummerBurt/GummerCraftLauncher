/*
 * Copyright (C) 2021-2026 GummerCraft <http://gummercraft.fun>
 */

package com.gummer.launcher.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.skcraft.concurrency.ObservableFuture;
import com.skcraft.launcher.Launcher;
import com.skcraft.launcher.dialog.ProgressDialog;
import com.skcraft.launcher.util.Environment;
import com.skcraft.launcher.util.HttpRequest;
import com.skcraft.launcher.util.Platform;
import com.skcraft.launcher.util.SharedLocale;
import com.skcraft.launcher.util.SwingExecutor;

import java.awt.Window;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 1.36.2 (19.11.2026, java4shell, власник: «го»): Java самого лаунчера ({@code %LOCALAPPDATA%\GummerCraft\app\jre})
 * оновлюється новим {@code GummerCraft.exe}. Лаунчер сам працює на цій Java, і Windows не дає замінити її файли, тож:
 * лаунчер читає {@code installer.json} на CDN; якщо його Java старша за {@code minJava}, качає exe з вікном
 * «Оновлюю лаунчер» (sha256 звіряється), запускає його через 3 с після свого закриття і закривається. Exe (Inno Setup)
 * бачить, що встановлена версія старша, тихо розпаковує нову jre і відкриває лаунчер.
 * <p>Лише Windows, лише установка з вбудованою Java (поруч з {@code jre} лежать {@code version.txt} і
 * {@code GummerCraft.exe}); портативний zip не чіпається. Одну адресу exe пробуємо не більше двох разів (файл
 * {@link #TRIED} у теці лаунчера), щоб не зациклитись, якщо exe не поставився. Немає мережі чи будь-яка помилка: лише
 * лог, лаунчер працює далі.
 * <p>1.36.15 (24.11.2026, перший підписаний exe, власник: «вкрути примусову заміну екзешників»), ще дві причини
 * поставити той самий exe з {@code installer.json}:
 * <ul>
 * <li>{@code minApp}: розпакована установка ({@code version.txt}) старша, тобто в ній непідписаний exe. Діє і на
 * Java 8 (Windows 7/8.1), бо порівнюється версія установки, а не Java.</li>
 * <li>{@code replaceOldExe: true}: лаунчер запущено старим exe без вбудованої Java ({@code Gummer_Craft_launcher.exe},
 * системна Java). Файл старого exe (шлях дає Launch4j у {@code launch4j.exefile}) перейменовується в {@code .old}
 * (запущений exe Windows перейменувати дає, видалити ні), на його місце з тим самим ім'ям кладеться новий
 * {@code GummerCraft.exe}, тож ярлики гравця далі ведуть на нього, і він запускається. Дані гравця не переносяться:
 * обидва exe беруть теку з {@code Документи\GummerCraft\bootstrap.yml}. Лише 64-бітна Windows на x64 (exe з
 * вбудованою Java лише x64). Не вдалось замінити файл: новий exe просто запускається з тимчасової теки (Java лаунчера
 * однаково стає вбудованою), старий лишається. {@code .old} прибирається при наступному запуску.</li>
 * </ul>
 * {@code installer.json} окремо від {@code release.json}, бо лаунчери до 1.31 падають на нових полях у ньому.
 */
public final class LauncherJreUpdate {

    static final String[] INFO_URLS = {
            "https://cdn.gummer.cc/launcher/install/installer.json",
            "https://cdn.gummercraft.fun/launcher/install/installer.json",
    };
    static final String TRIED = "jre-update-tried.txt";
    /** 1.36.15: шлях {@code .old} старого exe після заміни, прибирається при наступному запуску. */
    static final String OLD_EXE = "old-exe-replaced.txt";
    private static final Logger log = Logger.getLogger(LauncherJreUpdate.class.getName());

    private LauncherJreUpdate() {
    }

    /** Після показу головного вікна. Перевірка у фоні, вікно прогресу лише якщо справді треба оновлюватись. */
    public static void start(final Launcher launcher, final Window window) {
        launcher.getExecutor().submit(() -> {
            try {
                cleanupOldExe(launcher);
            } catch (Exception e) {
                log.log(Level.INFO, "Old exe cleanup skipped: " + e);
            }
            try {
                File app = appDir(true);
                if (app != null) {
                    // 1.36.3: гравець може вимкнути автооновлення Java в налаштуваннях («Оновлення Java»); 1.36.15:
                    // це вимикає лише оновлення за minJava, заміна непідписаного exe (minApp) діє завжди
                    boolean javaAuto = launcher.getConfig().isJavaAutoUpdate();
                    if (!javaAuto) log.info("Launcher Java auto-update is off in settings");
                    check(launcher, window, app, false, javaAuto);
                } else {
                    File old = oldExe();
                    if (old != null) replaceOldExe(launcher, window, old);
                }
            } catch (Exception e) {
                log.log(Level.INFO, "Launcher Java update check skipped: " + e);
            }
        });
    }

    /**
     * 1.36.3: кнопка «Оновити» в плитці «Оновлення Java» (не з потоку Swing). Діє і при вимкненому автооновленні та без
     * ліміту двох спроб. false: оновлювати нічого (не установка, Java не старша за minJava, немає мережі).
     */
    public static boolean startNow(Launcher launcher, Window window) throws Exception {
        File app = appDir(true);
        return app != null && check(launcher, window, app, true, true);
    }

    /** installer.json з CDN або null (немає мережі). */
    static JsonNode fetchInfo() {
        for (String u : INFO_URLS) {
            try {
                return Json.newMapper().readTree(HttpRequest.get(new URL(u)).execute().expectResponseCode(200)
                        .returnContent().asString("UTF-8"));
            } catch (IOException e) {
                log.info("installer.json unavailable at " + u + ": " + e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            }
        }
        return null;
    }

    /** Тека установки з Java 17 (там {@code jre}, {@code version.txt}, {@code GummerCraft.exe}) або null. */
    static File appDir() {
        return appDir(false);
    }

    /** Тека установки або null: оновлювати нічого. {@code java8}: рахувати і установку з Java 8 (Windows 7/8.1). */
    static File appDir(boolean java8) {
        try {
            if (Environment.getInstance().getPlatform() != Platform.WINDOWS) return null;
            String spec = System.getProperty("java.specification.version", "");
            if (spec.isEmpty() || (!java8 && spec.startsWith("1."))) return null;
            File home = new File(System.getProperty("java.home", "")).getCanonicalFile();
            if (!home.getName().equalsIgnoreCase("jre")) return null;
            File app = home.getParentFile();
            if (app == null || !new File(app, "version.txt").isFile() || !new File(app, "GummerCraft.exe").isFile()) return null;
            return app;
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    /**
     * 1.36.15: файл старого exe без вбудованої Java, який запустив цей лаунчер, або null (не Windows x64, не Launch4j,
     * вбудована Java, портативний zip).
     */
    static File oldExe() {
        try {
            if (Environment.getInstance().getPlatform() != Platform.WINDOWS) return null;
            if (!isX64Windows()) return null;
            if (JavaKind.isBundled()) return null;
            String p = System.getProperty("launch4j.exefile");
            if (p == null || p.isEmpty()) return null;
            File exe = new File(p).getCanonicalFile();
            if (!exe.isFile() || !exe.getName().toLowerCase(Locale.ROOT).endsWith(".exe")) return null;
            // exe з вбудованою Java (тека установки чи портативна) завжди має поруч jre
            if (new File(exe.getParentFile(), "jre").isDirectory()) return null;
            return exe;
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    /** 64-бітна Windows на x64 (не ARM і не 32-біт): лише там стартує exe з вбудованою Java x64. */
    static boolean isX64Windows() {
        String a = System.getenv("PROCESSOR_ARCHITEW6432"); // 32-бітна Java на 64-бітній Windows
        if (a == null || a.isEmpty()) a = System.getenv("PROCESSOR_ARCHITECTURE");
        return "AMD64".equalsIgnoreCase(a);
    }

    /** Версія розпакованої установки з {@code version.txt} або "". */
    static String appVersion(File app) {
        try {
            return new String(Files.readAllBytes(new File(app, "version.txt").toPath()), StandardCharsets.UTF_8).trim();
        } catch (IOException e) {
            return "";
        }
    }

    private static boolean check(final Launcher launcher, final Window window, final File app, boolean manual,
                                 boolean javaAuto) throws Exception {
        JsonNode info = fetchInfo();
        if (info == null) return false;
        String minJava = info.path("minJava").asText("");
        String minApp = info.path("minApp").asText("");
        String have = System.getProperty("java.version", "");
        String haveApp = appVersion(app);
        boolean java8 = System.getProperty("java.specification.version", "").startsWith("1.");
        boolean oldJava = javaAuto && !java8 && !minJava.isEmpty() && compare(have, minJava) < 0;
        boolean oldApp = !minApp.isEmpty() && !haveApp.isEmpty() && compare(haveApp, minApp) < 0;
        if (!oldJava && !oldApp) return false;
        String why = oldJava ? "Launcher Java " + have + " < " + minJava : "Launcher install " + haveApp + " < " + minApp;
        File exe = download(launcher, window, info, why, manual, oldJava ? "launcher.jreUpdateStatus" : "launcher.exeUpdateStatus");
        if (exe == null) return false;
        runAndExit(exe, launcher);
        return true;
    }

    /** 1.36.15: замінити старий exe новим (див. опис класу). */
    private static void replaceOldExe(final Launcher launcher, final Window window, final File old) throws Exception {
        JsonNode info = fetchInfo();
        if (info == null || !info.path("replaceOldExe").asBoolean(false)) return;
        final File exe = download(launcher, window, info, "Old exe " + old, false, "launcher.exeUpdateStatus");
        if (exe == null) return;
        File run = exe;
        File bak = new File(old.getParentFile(), old.getName() + ".old");
        try {
            Files.deleteIfExists(bak.toPath());
            Files.move(old.toPath(), bak.toPath());
            try {
                Files.copy(exe.toPath(), old.toPath(), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                Files.deleteIfExists(old.toPath());
                Files.move(bak.toPath(), old.toPath()); // повернути старий exe на місце
                throw e;
            }
            run = old;
            Files.write(new File(launcher.getBaseDir(), OLD_EXE).toPath(), bak.getPath().getBytes(StandardCharsets.UTF_8));
            log.info("Old exe replaced: " + old + " (previous kept as " + bak.getName() + ")");
        } catch (IOException | RuntimeException e) {
            log.log(Level.INFO, "Can't replace old exe " + old + ", starting the new one from " + exe + ": " + e);
        }
        runAndExit(run, launcher);
    }

    /** Прибрати {@code .old} після заміни (при першому запуску він ще міг бути зайнятий). */
    private static void cleanupOldExe(Launcher launcher) throws IOException {
        File note = new File(launcher.getBaseDir(), OLD_EXE);
        if (!note.isFile()) return;
        File bak = new File(new String(Files.readAllBytes(note.toPath()), StandardCharsets.UTF_8).trim());
        if (bak.getName().toLowerCase(Locale.ROOT).endsWith(".exe.old")) {
            Files.deleteIfExists(bak.toPath());
        }
        if (!bak.exists()) Files.deleteIfExists(note.toPath());
    }

    /**
     * Скачати exe з {@code installer.json} у теку {@code temp} лаунчера (sha256 звіряється), з вікном «Оновлюю лаунчер».
     * null: адреса підозріла, ліміт спроб, помилка. Викликати не з потоку Swing: чекає завантаження.
     */
    private static File download(Launcher launcher, final Window window, JsonNode info, String why, boolean manual,
                                 final String statusKey) throws Exception {
        final String url = info.path("url").asText("");
        final String sha256 = info.path("sha256").asText("");
        URL u = new URL(url);
        if (!"https".equals(u.getProtocol()) || !u.getHost().toLowerCase(Locale.ROOT).endsWith("gummercraft.fun")
                && !u.getHost().toLowerCase(Locale.ROOT).endsWith("gummer.cc")) {
            log.warning("installer.json: unexpected host " + u.getHost());
            return null;
        }
        if (!sha256.matches("[0-9a-fA-F]{64}")) return null;
        File tried = new File(launcher.getBaseDir(), TRIED);
        int attempts = 0;
        if (tried.isFile()) {
            String[] p = new String(Files.readAllBytes(tried.toPath()), StandardCharsets.UTF_8).trim().split("\\|");
            if (p.length == 2 && p[0].equals(url)) attempts = Integer.parseInt(p[1].trim());
        }
        if (attempts >= 2 && !manual) {
            log.info(why + ", but " + url + " was already tried twice");
            return null;
        }
        Files.write(tried.toPath(), (url + "|" + (attempts + 1)).getBytes(StandardCharsets.UTF_8));
        log.info(why + ", updating the launcher from " + url);

        final File dir = new File(launcher.getBaseDir(), "temp");
        dir.mkdirs();
        String name = new File(u.getPath()).getName();
        if (!name.toLowerCase(Locale.ROOT).endsWith(".exe") || name.contains("..")) name = "GummerCraft-update.exe";
        final File exe = new File(dir, name);
        final File part = new File(dir, name + ".part");
        final HttpRequest req = HttpRequest.get(u);
        ListenableFuture<File> f = launcher.getExecutor().submit(() -> {
            if (!exe.isFile() || !sha256.equalsIgnoreCase(com.gummer.launcher.manage.ModrinthBrowse.sha(exe, "SHA-256"))) {
                req.execute().expectResponseCode(200).saveContent(part);
                if (!sha256.equalsIgnoreCase(com.gummer.launcher.manage.ModrinthBrowse.sha(part, "SHA-256"))) {
                    part.delete();
                    throw new IOException("sha256 mismatch for " + url);
                }
                Files.move(part.toPath(), exe.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            return exe;
        });
        final ObservableFuture<File> future = new ObservableFuture<File>(f, req);
        SwingExecutor.INSTANCE.execute(() -> ProgressDialog.showProgress(window, future,
                SharedLocale.tr("launcher.jreUpdateTitle"), SharedLocale.tr(statusKey)));
        try {
            return future.get();
        } catch (Exception e) {
            log.info("Launcher update download failed: " + e);
            return null;
        }
    }

    /** Запустити exe через ~3 с, коли цей процес уже закриється і відпустить файли, і закритись. */
    private static void runAndExit(final File exe, Launcher launcher) {
        SwingExecutor.INSTANCE.execute(() -> {
            try {
                new ProcessBuilder(Arrays.asList("cmd.exe", "/c", "ping -n 4 127.0.0.1 >nul & start \"\" \""
                        + exe.getAbsolutePath() + "\"")).directory(exe.getParentFile()).start();
                log.info("Launcher update: started " + exe + ", exiting");
                System.exit(0);
            } catch (IOException e) {
                log.log(Level.WARNING, "Can't start " + exe, e);
            }
        });
    }

    /** Порівняння версій за числами: 17.0.21 < 17.0.21.1 < 17.0.22, 1.36.9 < 1.36.15. */
    static int compare(String a, String b) {
        String[] x = a.split("[^0-9]+"), y = b.split("[^0-9]+");
        for (int i = 0; i < Math.max(x.length, y.length); i++) {
            int p = i < x.length && !x[i].isEmpty() ? Integer.parseInt(x[i]) : 0;
            int q = i < y.length && !y[i].isEmpty() ? Integer.parseInt(y[i]) : 0;
            if (p != q) return p < q ? -1 : 1;
        }
        return 0;
    }
}
