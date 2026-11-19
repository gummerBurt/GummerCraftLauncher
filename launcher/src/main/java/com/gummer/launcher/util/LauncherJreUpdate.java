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
 * {@code GummerCraft.exe}); портативний zip і системна Java не чіпаються. Java 8 (Windows 7/8.1) не зачеплена. Одну адресу
 * exe пробуємо не більше двох разів (файл {@link #TRIED} у теці лаунчера), щоб не зациклитись, якщо exe не поставився.
 * Немає мережі чи будь-яка помилка: лише лог, лаунчер працює далі.
 * <p>{@code installer.json} окремо від {@code release.json}, бо лаунчери до 1.31 падають на нових полях у ньому.
 */
public final class LauncherJreUpdate {

    static final String[] INFO_URLS = {
            "https://cdn.gummer.cc/launcher/install/installer.json",
            "https://cdn.gummercraft.fun/launcher/install/installer.json",
    };
    static final String TRIED = "jre-update-tried.txt";
    private static final Logger log = Logger.getLogger(LauncherJreUpdate.class.getName());

    private LauncherJreUpdate() {
    }

    /** Після показу головного вікна. Перевірка у фоні, вікно прогресу лише якщо справді треба оновлюватись. */
    public static void start(final Launcher launcher, final Window window) {
        final File app = appDir();
        if (app == null) return;
        // 1.36.3: гравець може вимкнути автооновлення Java в налаштуваннях («Оновлення Java»)
        if (!launcher.getConfig().isJavaAutoUpdate()) {
            log.info("Launcher Java auto-update is off in settings");
            return;
        }
        launcher.getExecutor().submit(() -> {
            try {
                check(launcher, window, app, false);
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
        File app = appDir();
        return app != null && check(launcher, window, app, true);
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

    /** Тека установки (там {@code jre}, {@code version.txt}, {@code GummerCraft.exe}) або null: оновлювати нічого. */
    static File appDir() {
        try {
            if (Environment.getInstance().getPlatform() != Platform.WINDOWS) return null;
            String spec = System.getProperty("java.specification.version", "");
            if (spec.isEmpty() || spec.startsWith("1.")) return null; // Java 8: не зачеплена
            File home = new File(System.getProperty("java.home", "")).getCanonicalFile();
            if (!home.getName().equalsIgnoreCase("jre")) return null;
            File app = home.getParentFile();
            if (app == null || !new File(app, "version.txt").isFile() || !new File(app, "GummerCraft.exe").isFile()) return null;
            return app;
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    private static boolean check(final Launcher launcher, final Window window, final File app, boolean manual) throws Exception {
        JsonNode info = fetchInfo();
        if (info == null) return false;
        String minJava = info.path("minJava").asText("");
        final String url = info.path("url").asText("");
        final String sha256 = info.path("sha256").asText("");
        String have = System.getProperty("java.version", "");
        if (minJava.isEmpty() || compare(have, minJava) >= 0) return false;
        URL u = new URL(url);
        if (!"https".equals(u.getProtocol()) || !u.getHost().toLowerCase(Locale.ROOT).endsWith("gummercraft.fun")
                && !u.getHost().toLowerCase(Locale.ROOT).endsWith("gummer.cc")) {
            log.warning("installer.json: unexpected host " + u.getHost());
            return false;
        }
        if (!sha256.matches("[0-9a-fA-F]{64}")) return false;
        final File tried = new File(launcher.getBaseDir(), TRIED);
        int attempts = 0;
        if (tried.isFile()) {
            String[] p = new String(Files.readAllBytes(tried.toPath()), StandardCharsets.UTF_8).trim().split("\\|");
            if (p.length == 2 && p[0].equals(url)) attempts = Integer.parseInt(p[1].trim());
        }
        if (attempts >= 2 && !manual) {
            log.info("Launcher Java " + have + " < " + minJava + ", but " + url + " was already tried twice");
            return false;
        }
        Files.write(tried.toPath(), (url + "|" + (attempts + 1)).getBytes(StandardCharsets.UTF_8));
        log.info("Launcher Java " + have + " < " + minJava + ", updating the launcher install from " + url);

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
                SharedLocale.tr("launcher.jreUpdateTitle"), SharedLocale.tr("launcher.jreUpdateStatus")));
        Futures.addCallback(future, new FutureCallback<File>() {
            @Override
            public void onSuccess(File result) {
                try {
                    // exe стартує через ~3 с, коли цей процес уже закриється і відпустить файли jre
                    new ProcessBuilder(Arrays.asList("cmd.exe", "/c", "ping -n 4 127.0.0.1 >nul & start \"\" \""
                            + result.getAbsolutePath() + "\"")).directory(dir).start();
                    log.info("Launcher Java update: started " + result + ", exiting");
                    System.exit(0);
                } catch (IOException e) {
                    log.log(Level.WARNING, "Can't start " + result, e);
                }
            }

            @Override
            public void onFailure(Throwable t) {
                log.info("Launcher Java update failed: " + t);
            }
        }, SwingExecutor.INSTANCE);
        return true;
    }

    /** Порівняння версій Java за числами: 17.0.21 < 17.0.21.1 < 17.0.22. */
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
