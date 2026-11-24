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
 * системна Java). Файл старого exe (перший запис classpath, з 1.36.17) після закриття лаунчера (cmd, з 1.36.18 з повторними спробами) перейменовується в {@code .old}
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
    /** 1.36.18: результат cmd заміни старого exe, наступний запуск пише його в лог і видаляє. */
    static final String OLD_EXE_RESULT = "old-exe-result.txt";
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
            if (p == null || p.isEmpty()) {
                // 1.36.17: наш Launch4j не передає -Dlaunch4j.exefile (перевірено 24.11 на exe 1.36.2: командний рядок
                // без жодного -D), але сам exe завжди перший у classpath (як у SelfRestart.bootstrapCommand)
                p = System.getProperty("java.class.path", "").split(java.util.regex.Pattern.quote(File.pathSeparator))[0];
            }
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
        // 1.36.17: поки лаунчер працює, файл старого exe тримає відкритим сама Java (він у classpath: jar усередині exe),
        // і Windows не дає його перейменувати (перевірено 24.11: «being used by another process»). Тому заміну робить cmd
        // вже після закриття лаунчера.
        // 1.36.18: у 1.36.17 cmd чекав фіксовані 5 с і пробував один раз, а гравець ці ~6 с бачив порожнечу і запускав
        // старий exe вдруге; той знову тримав файл, заміна не вдавалась (24.11 у власника на робочому столі). Тепер
        // окремий cmd-файл: чекає, поки закриється саме цей процес Java (до ~60 с), одразу запускає новий exe (гравець
        // бачить установку), а старий файл міняє в циклі до ~90 с; результат у OLD_EXE_RESULT, наступний запуск пише його
        // в лог. Шляхи лише через змінні середовища в лапках (%VAR% без відкладеного розгортання), тож & ^ ! ( ) і пробіли
        // в шляху нічого не ламають; сам cmd-файл ASCII. ProcessBuilder запускає cmd без вікна (CREATE_NO_WINDOW).
        File bak = new File(old.getParentFile(), old.getName() + ".old");
        try {
            Files.write(new File(launcher.getBaseDir(), OLD_EXE).toPath(), bak.getPath().getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            log.info("Can't write " + OLD_EXE + ": " + e);
        }
        final File result = new File(launcher.getBaseDir(), OLD_EXE_RESULT);
        final File bat = new File(exe.getParentFile(), "gc-replace-exe.cmd");
        Files.write(bat.toPath(), REPLACE_SCRIPT.getBytes(StandardCharsets.US_ASCII));
        final String pid = currentPid();
        SwingExecutor.INSTANCE.execute(() -> {
            try {
                ProcessBuilder pb = new ProcessBuilder(Arrays.asList("cmd.exe", "/d", "/c", ".\\" + bat.getName()))
                        .directory(exe.getParentFile());
                pb.environment().put("GC_OLD", old.getAbsolutePath());
                pb.environment().put("GC_BAK", bak.getAbsolutePath());
                pb.environment().put("GC_NEW", exe.getAbsolutePath());
                pb.environment().put("GC_PID", pid);
                pb.environment().put("GC_RES", result.getAbsolutePath());
                pb.start();
                log.info("Old exe " + old + " will be replaced by " + exe + " after exit of pid " + pid
                        + " (previous kept as " + bak.getName() + ")");
                System.exit(0);
            } catch (IOException e) {
                log.log(Level.WARNING, "Can't start the old exe replacement", e);
            }
        });
    }

    /**
     * 1.36.18: cmd-файл заміни старого exe (див. {@link #replaceOldExe}). Коди в OLD_EXE_RESULT: {@code ok N} (замінено
     * з N-ї повторної спроби), {@code copy-failed} (старий повернуто на місце), {@code move-failed} (файл весь час був
     * зайнятий). Новий exe запускається в будь-якому разі, тож гравець отримує новий лаунчер і без заміни файлу.
     */
    static final String REPLACE_SCRIPT = String.join("\r\n",
            "@echo off",
            "set n=0",
            ":wait",
            "if \"%GC_PID%\"==\"\" goto gone",
            "tasklist /nh /fi \"PID eq %GC_PID%\" 2>nul | find \" %GC_PID% \" >nul",
            "if errorlevel 1 goto gone",
            "set /a n+=1",
            "if %n% geq 60 goto gone",
            "ping -n 2 127.0.0.1 >nul",
            "goto wait",
            ":gone",
            "start \"\" \"%GC_NEW%\"",
            "set n=0",
            ":move",
            "move /y \"%GC_OLD%\" \"%GC_BAK%\" >nul 2>&1",
            "if errorlevel 1 goto retry",
            "copy /b /y \"%GC_NEW%\" \"%GC_OLD%\" >nul 2>&1",
            "if errorlevel 1 goto restore",
            "del /f \"%GC_BAK%\" >nul 2>&1",
            "> \"%GC_RES%\" echo ok %n%",
            "goto end",
            ":restore",
            "move /y \"%GC_BAK%\" \"%GC_OLD%\" >nul 2>&1",
            "> \"%GC_RES%\" echo copy-failed %n%",
            "goto end",
            ":retry",
            "set /a n+=1",
            "if %n% geq 30 goto fail",
            "ping -n 4 127.0.0.1 >nul",
            "goto move",
            ":fail",
            "> \"%GC_RES%\" echo move-failed %n%",
            ":end",
            "(goto) 2>nul & del \"%~f0\"",
            "");

    /** PID цього процесу Java (Java 8 сумісно) або "" (тоді cmd не чекає). */
    static String currentPid() {
        try {
            String n = java.lang.management.ManagementFactory.getRuntimeMXBean().getName();
            String p = n.substring(0, n.indexOf('@'));
            return p.matches("[0-9]+") ? p : "";
        } catch (RuntimeException e) {
            return "";
        }
    }

    /** Прибрати {@code .old} після заміни (при першому запуску він ще міг бути зайнятий). */
    private static void cleanupOldExe(Launcher launcher) throws IOException {
        File res = new File(launcher.getBaseDir(), OLD_EXE_RESULT);
        File note = new File(launcher.getBaseDir(), OLD_EXE);
        if (res.isFile()) {
            log.info("Old exe replacement result: " + new String(Files.readAllBytes(res.toPath()), StandardCharsets.US_ASCII).trim());
            Files.deleteIfExists(res.toPath());
        } else if (note.isFile() && System.currentTimeMillis() - note.lastModified() < 10 * 60 * 1000L) {
            // 1.36.18: cmd заміни ще працює (новий лаунчер він запускає раніше, ніж міняє файл): .old не чіпати,
            // інакше між move і copy можна видалити єдину копію старого exe
            return;
        }
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
