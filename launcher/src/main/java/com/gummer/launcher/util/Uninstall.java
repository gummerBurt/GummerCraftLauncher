/*
 * Copyright (C) 2021-2026 GummerCraft <http://gummercraft.fun>
 */

package com.gummer.launcher.util;

import com.skcraft.launcher.util.Environment;
import com.skcraft.launcher.util.Platform;
import lombok.extern.java.Log;

import java.io.File;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

/**
 * Дві червоні кнопки в налаштуваннях (19.11.2026, власник: «кнопку (червону, щоб випадково не натиснули) "Видалення
 * лаунчера та усіх збірок" і окремо кнопку "Видалення усіх збірок" ... щоб гравець не мусів все руками чистити»).
 *
 * <p>Головне правило: видаляється лише те, що точно створив лаунчер, за списком назв у теці даних. Теку даних гравець
 * міг вибрати сам (хоч {@code D:\}), тож «стерти теку цілком» не можна ніколи: чужі файли там лишаються, а сама тека
 * прибирається, лише якщо після нас стала порожньою.
 *
 * <ul>
 * <li>{@link #builds}: збірки та все, що лаунчер докачає знову (бібліотеки, ассети, версії, завантажувачі, Java гри,
 * тимчасове). Бекапи світів лише за окремою галочкою. Акаунти, налаштування і сам лаунчер лишаються. Робиться одразу,
 * у цьому процесі ({@link #deleteNow}), потім лаунчер перезапускається.</li>
 * <li>{@link #everything}: усе наше з теки даних, {@code bootstrap.yml} у Документах, розпакований лаунчер з Java
 * ({@code %LOCALAPPDATA%\GummerCraft\app} на Windows, {@code ~/GummerCraft/app} після install.sh на Linux). У реєстр
 * лаунчер не пише, тож там прибирати нічого. Запущена Java сама себе і свій jar видалити не може, тож
 * {@link #deleteAfterExit} запускає окремий процес, який чекає, поки лаунчер закриється, і тоді видаляє.</li>
 * </ul>
 * Скачаний гравцем GummerCraft.exe (той, що в Завантаженнях чи на робочому столі) лаунчер не знає, де лежить: його
 * гравець видаляє сам, про це сказано у вікні підтвердження.
 */
@Log
public final class Uninstall {

    /** Скільки ігор зараз запущено з лаунчера: поки гра йде, її файли зайняті і видаляти нічого не можна. */
    public static final AtomicInteger RUNNING_GAMES = new AtomicInteger();

    /** Збірки і те, що лаунчер докачає сам. */
    static final String[] BUILD_ENTRIES = {"instances", "libraries", "assets", "versions", "loaders", "runtime",
            "runtime-prev", "temp"};
    /** Решта нашого в теці даних (для «видалити все»). */
    static final String[] OTHER_ENTRIES = {"world-backups", "launcher", "logs", "accounts.dat", "config.json",
            "launcher-ui.json", "packages_cache.json", "news-cache.json", "stats-id.txt", "support.json", "bootstrap.yml",
            "portable.txt", "launcher.log"};
    /** Кеші списків версій: vanilla_versions.json, forge_versions.xml, fabric_games.json, quilt_loaders.json тощо. */
    private static final Pattern VERSION_CACHE = Pattern.compile("[a-z0-9]+_(versions|games|loaders)\\.(json|xml)");
    private static final Pattern LAUNCHER_LOG = Pattern.compile("launcher-\\d+\\.log");

    private Uninstall() {
    }

    /** Що видалити: конкретні файли і теки, плюс теки, які прибрати лише якщо стануть порожніми. */
    public static final class Plan {
        public final List<File> delete = new ArrayList<>();
        public final List<File> removeIfEmpty = new ArrayList<>();
        public long bytes;

        void add(File f) {
            if (f != null && f.exists() && !delete.contains(f.getAbsoluteFile())) {
                delete.add(f.getAbsoluteFile());
                bytes += size(f);
            }
        }
    }

    /** Тека схожа на теку даних лаунчера (захист від видалення за списком у випадковій теці). */
    static boolean looksLikeOurs(File base) {
        return base != null && base.isDirectory()
                && (new File(base, "launcher").isDirectory() || new File(base, "config.json").isFile()
                || new File(base, "instances").isDirectory());
    }

    public static Plan builds(File base, boolean withWorldBackups) {
        Plan p = new Plan();
        if (!looksLikeOurs(base)) return p;
        for (String n : BUILD_ENTRIES) p.add(new File(base, n));
        if (withWorldBackups) p.add(new File(base, "world-backups"));
        return p;
    }

    /**
     * @param base       тека даних ({@code launcher.getBaseDir()})
     * @param bootstrapDir тека з {@code bootstrap.yml} (Документи\GummerCraft), може збігатися з base
     * @param appDir     розпакований лаунчер з Java або null
     */
    public static Plan everything(File base, File bootstrapDir, File appDir) {
        Plan p = new Plan();
        if (looksLikeOurs(base)) {
            for (String n : BUILD_ENTRIES) p.add(new File(base, n));
            for (String n : OTHER_ENTRIES) p.add(new File(base, n));
            File[] top = base.listFiles();
            if (top != null) {
                for (File f : top) {
                    String n = f.getName().toLowerCase(Locale.ROOT);
                    if (f.isFile() && (VERSION_CACHE.matcher(n).matches() || LAUNCHER_LOG.matcher(n).matches())) p.add(f);
                }
            }
            p.removeIfEmpty.add(base.getAbsoluteFile());
        }
        if (bootstrapDir != null && !bootstrapDir.getAbsoluteFile().equals(base.getAbsoluteFile())) {
            p.add(new File(bootstrapDir, "bootstrap.yml"));
            p.removeIfEmpty.add(bootstrapDir.getAbsoluteFile());
        }
        if (isApp(appDir)) {
            p.add(appDir);
            File parent = appDir.getAbsoluteFile().getParentFile();
            if (parent != null && !p.removeIfEmpty.contains(parent)) p.removeIfEmpty.add(parent);
        }
        return p;
    }

    /** Розпакований лаунчер: Windows (Inno Setup) або Linux (install.sh). Чужу теку з назвою app не чіпаємо. */
    static boolean isApp(File dir) {
        return dir != null && dir.isDirectory() && "app".equals(dir.getName())
                && (new File(dir, "GummerCraft.exe").isFile() || new File(dir, "gummercraft.sh").isFile());
    }

    /** Де лежить розпакований лаунчер на цій ОС, або null. */
    public static File appDir(File base) {
        Platform pl = Environment.getInstance().getPlatform();
        if (pl == Platform.WINDOWS) {
            String lad = System.getenv("LOCALAPPDATA");
            File d = lad == null ? null : new File(new File(lad, "GummerCraft"), "app");
            return isApp(d) ? d : null;
        }
        if (pl == Platform.LINUX) {
            File d = new File(new File(System.getProperty("user.home"), "GummerCraft"), "app");
            if (isApp(d)) return d;
            d = new File(base, "app");
            return isApp(d) ? d : null;
        }
        return null;
    }

    /** Ярлики, які створює install.sh на Linux. */
    public static void addLinuxShortcuts(Plan p) {
        if (Environment.getInstance().getPlatform() != Platform.LINUX) return;
        File home = new File(System.getProperty("user.home"));
        p.add(new File(home, ".local/share/applications/gummercraft.desktop"));
        p.add(new File(home, "Desktop/GummerCraft.desktop"));
    }

    /** Видалити одразу (для збірок). @return скільки файлів не вдалося видалити (зайняті) */
    public static int deleteNow(Plan p) {
        int[] failed = {0};
        for (File f : p.delete) {
            if (holdsOwnJava(f)) continue;
            deleteTree(f, failed);
        }
        for (File d : p.removeIfEmpty) {
            String[] left = d.list();
            if (left != null && left.length == 0 && !d.delete()) failed[0]++;
        }
        return failed[0];
    }

    private static void deleteTree(File f, int[] failed) {
        // посилання (junction, symlink) не розгортаємо: видаляється саме посилання, не те, на що воно вказує
        if (f.isDirectory() && !isLink(f)) {
            File[] kids = f.listFiles();
            if (kids != null) for (File k : kids) deleteTree(k, failed);
        }
        if (f.exists() && !f.delete()) failed[0]++;
    }

    private static boolean isLink(File f) {
        try {
            java.nio.file.attribute.BasicFileAttributes a = java.nio.file.Files.readAttributes(f.toPath(),
                    java.nio.file.attribute.BasicFileAttributes.class, java.nio.file.LinkOption.NOFOLLOW_LINKS);
            return a.isSymbolicLink() || a.isOther(); // junction на Windows: isOther
        } catch (IOException | RuntimeException e) {
            return true; // не знаємо, що це: не розгортаємо
        }
    }

    /** Тека Java, на якій працює сам лаунчер: її в цьому процесі не видаляємо (зламало б лаунчер до перезапуску). */
    private static boolean holdsOwnJava(File f) {
        try {
            String home = new File(System.getProperty("java.home")).getCanonicalPath() + File.separator;
            return home.startsWith(f.getCanonicalPath() + File.separator);
        } catch (IOException | RuntimeException e) {
            return false;
        }
    }

    /**
     * Запустити окремий процес, який дочекається закриття лаунчера і видалить план. Після виклику лаунчер має
     * закритися ({@code System.exit}). Шляхи передаються без лапок у тексті команди: на Windows скрипт PowerShell
     * у {@code -EncodedCommand} (UTF-16, тож кирилиця в шляхах не ламається), на Mac і Linux аргументами sh.
     */
    public static void deleteAfterExit(Plan p) throws IOException {
        long pid = pid();
        List<String> cmd;
        if (Environment.getInstance().getPlatform() == Platform.WINDOWS) {
            StringBuilder s = new StringBuilder();
            s.append("$ErrorActionPreference='SilentlyContinue';");
            if (pid > 0) s.append("Wait-Process -Id ").append(pid).append(" -Timeout 120;");
            s.append("$del=@(").append(psList(p.delete)).append(");");
            s.append("$dirs=@(").append(psList(p.removeIfEmpty)).append(");");
            // exe лаунчера може відпустити файли на секунду пізніше за Java: кілька спроб. Теки через rmdir /s: він не
            // заходить у junction і symlink (Remove-Item -Recurse у PowerShell 5.1 може зайти і стерти чуже)
            s.append("for($i=0;$i -lt 30;$i++){$left=$false;foreach($f in $del){if(Test-Path -LiteralPath $f){"
                    + "if((Get-Item -LiteralPath $f -Force) -is [System.IO.DirectoryInfo]){cmd /c rmdir /s /q \"$f\"}"
                    + "else{Remove-Item -LiteralPath $f -Force};if(Test-Path -LiteralPath $f){$left=$true}}};"
                    + "if(-not $left){break};Start-Sleep -Seconds 1};");
            s.append("foreach($d in $dirs){if((Test-Path -LiteralPath $d) -and -not (Get-ChildItem -LiteralPath $d -Force)){"
                    + "Remove-Item -LiteralPath $d -Force}}");
            String enc = Base64.getEncoder().encodeToString(s.toString().getBytes(StandardCharsets.UTF_16LE));
            cmd = Arrays.asList("powershell", "-NoProfile", "-NonInteractive", "-WindowStyle", "Hidden",
                    "-ExecutionPolicy", "Bypass", "-EncodedCommand", enc);
        } else {
            // sh -c '...' sh <pid> <файли...> -- <теки якщо порожні...>
            String script = "pid=$1; shift; if [ \"$pid\" -gt 0 ]; then n=0; while kill -0 \"$pid\" 2>/dev/null && [ $n -lt 120 ];"
                    + " do sleep 1; n=$((n+1)); done; fi;"
                    + " while [ $# -gt 0 ] && [ \"$1\" != \"--\" ]; do rm -rf -- \"$1\"; shift; done; [ $# -gt 0 ] && shift;"
                    + " for d in \"$@\"; do rmdir -- \"$d\" 2>/dev/null; done";
            cmd = new ArrayList<>(Arrays.asList("sh", "-c", script, "sh", String.valueOf(pid)));
            for (File f : p.delete) cmd.add(f.getAbsolutePath());
            cmd.add("--");
            for (File f : p.removeIfEmpty) cmd.add(f.getAbsolutePath());
        }
        log.info("Uninstall after exit: " + p.delete.size() + " entries, " + p.removeIfEmpty.size() + " dirs if empty");
        new ProcessBuilder(cmd).directory(new File(System.getProperty("java.io.tmpdir"))).start();
    }

    private static String psList(List<File> files) {
        StringBuilder b = new StringBuilder();
        for (File f : files) {
            if (b.length() > 0) b.append(',');
            b.append('\'').append(f.getAbsolutePath().replace("'", "''")).append('\'');
        }
        return b.toString();
    }

    static long pid() {
        try {
            String n = ManagementFactory.getRuntimeMXBean().getName(); // «pid@host» на HotSpot, Java 8 без ProcessHandle
            return Long.parseLong(n.substring(0, n.indexOf('@')));
        } catch (RuntimeException e) {
            return -1;
        }
    }

    /** Розмір файлу чи теки (посилання не розгортаються, захист від циклів). */
    public static long size(File f) {
        return size(f, new HashSet<>());
    }

    private static long size(File f, Set<String> seen) {
        if (f.isFile()) return f.length();
        if (!f.isDirectory() || isLink(f)) return 0;
        if (!seen.add(f.getAbsolutePath())) return 0;
        long s = 0;
        File[] kids = f.listFiles();
        if (kids != null) for (File k : kids) s += size(k, seen);
        return s;
    }

    /** «12,3 ГБ» / «850 МБ» без локалізованих одиниць: одиниці беруться з lang. */
    public static String human(long bytes, String gb, String mb) {
        double g = bytes / (1024.0 * 1024 * 1024);
        if (g >= 1) return String.format(Locale.ROOT, "%.1f %s", g, gb);
        return Math.max(1, Math.round(bytes / (1024.0 * 1024))) + " " + mb;
    }
}
