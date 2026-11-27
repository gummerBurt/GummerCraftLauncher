/*
 * Copyright (C) 2021-2026 GummerCraft <http://gummercraft.fun>
 */

package com.gummer.launcher.util;

import com.skcraft.launcher.Launcher;
import com.skcraft.launcher.persistence.Persistence;
import lombok.extern.java.Log;

import java.io.File;
import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.Locale;

/**
 * 27.11.2026 (тікети #134, #138): де лежить Java для гри. Звичайно {@code <тека даних>/runtime}. В обох тікетах тека даних
 * у «Документах», і Windows не давав читати щойно поставлені файли Java чи навіть саму теку {@code runtime/17/windows-x64}
 * (захист від програм-вимагачів чи антивірус стережуть «Документи»). Тоді Java ставиться в
 * {@code %LOCALAPPDATA%\GummerCraft\runtime}: туди такий захист не дивиться, а тека гри лишається де була.
 * <p>
 * Перевірка перед встановленням ({@link #ensureUsable}): існуюча тека Java читається, а файл з тимчасової теки лаунчера
 * переноситься в корінь Java і читається назад (так само, як ставляться файли Java). Не вийшло, а в запасній теці
 * виходить: корінь Java перемикається назавжди ({@code javaRuntimeDir} у config.json), рядок у лог. Запасна тека нічим не
 * гірша, тож назад лаунчер сам не перемикає.
 */
@Log
public final class JavaPlace {

    private JavaPlace() {
    }

    /** Корінь тек Java: запасний з налаштувань або {@code <тека даних>/runtime}. */
    public static File root(Launcher l) {
        String alt = l.getConfig() != null ? l.getConfig().getJavaRuntimeDir() : null;
        if (alt != null && !alt.trim().isEmpty()) return new File(alt.trim());
        return new File(l.getBaseDir(), "runtime");
    }

    /** Запасний корінь: {@code %LOCALAPPDATA%\GummerCraft\runtime} на Windows, інакше null. */
    public static File fallbackRoot() {
        if (!System.getProperty("os.name", "").startsWith("Windows")) return null;
        String local = System.getenv("LOCALAPPDATA");
        if (local == null || local.trim().isEmpty()) return null;
        return new File(new File(local.trim(), "GummerCraft"), "runtime");
    }

    /** Усі корені, де може лежати Java лаунчера (для брандмауера і видалення). */
    public static java.util.List<File> allRoots(File baseDir) {
        java.util.List<File> out = new java.util.ArrayList<File>();
        out.add(new File(baseDir, "runtime"));
        File f = fallbackRoot();
        if (f != null && !out.contains(f)) out.add(f);
        return out;
    }

    /**
     * Тека Java, у яку ставити: jvmDir, якщо там усе читається і пишеться, інакше та сама Java в запасному корені
     * (і корінь перемикається). Нічого не працює: jvmDir як був (встановлення далі покаже справжню помилку).
     */
    public static File ensureUsable(Launcher l, String component, String os, File jvmDir) {
        File root = root(l);
        String problem = probe(l.getInstallerDir(), root, jvmDir);
        if (problem == null) return jvmDir;
        log.warning("Java folder is not usable: " + problem + "; " + WindowsSecurityInfo.lines().replace('\n', ' '));
        File fb = fallbackRoot();
        if (fb == null || same(fb, root)) return jvmDir;
        File fbDir = new File(fb, component + "/" + os);
        String fbProblem = probe(l.getInstallerDir(), fb, fbDir);
        if (fbProblem != null) {
            log.warning("Fallback Java folder is not usable either: " + fbProblem);
            return jvmDir;
        }
        switchTo(l, fb);
        return fbDir;
    }

    /** Перемкнути корінь Java на запасний (після помилки встановлення). null: запасного немає, він уже стоїть чи не працює. */
    public static File switchToFallback(Launcher l) {
        File fb = fallbackRoot();
        if (fb == null || same(fb, root(l))) return null;
        String problem = probe(l.getInstallerDir(), fb, null);
        if (problem != null) {
            log.warning("Fallback Java folder is not usable: " + problem);
            return null;
        }
        switchTo(l, fb);
        return fb;
    }

    private static void switchTo(Launcher l, File fb) {
        log.warning("Java folder switched to " + fb.getAbsolutePath() + " (was " + root(l).getAbsolutePath() + ")");
        l.getConfig().setJavaRuntimeDir(fb.getAbsolutePath());
        Persistence.commitAndForget(l.getConfig());
    }

    /** Помилка доступу до файлу під коренем Java (шлях у тексті помилки). */
    public static boolean deniedUnderRoot(Launcher l, Throwable t) {
        String root = root(l).getAbsolutePath().toLowerCase(Locale.ROOT);
        for (Throwable c = t; c != null; c = c.getCause() == c ? null : c.getCause()) {
            String m = String.valueOf(c.getMessage()).toLowerCase(Locale.ROOT);
            boolean denied = c instanceof AccessDeniedException || m.contains("access is denied") || m.contains("отказано в доступе")
                    || m.contains("відмовлено в доступі") || m.contains("being used by another process");
            if (!denied) continue;
            if (c instanceof FileSystemException) {
                FileSystemException f = (FileSystemException) c;
                m = m + " " + String.valueOf(f.getFile()).toLowerCase(Locale.ROOT) + " " + String.valueOf(f.getOtherFile()).toLowerCase(Locale.ROOT);
            }
            if (m.contains(root)) return true;
        }
        return false;
    }

    /** null: тека придатна; інакше що не так (для логу). */
    static String probe(File tempDir, File root, File jvmDir) {
        File probeDir = new File(root, ".probe");
        File target = new File(probeDir, "probe.bin");
        File tmp = null;
        try {
            Files.createDirectories(root.toPath());
            if (jvmDir != null && jvmDir.exists()) {
                // тікет #138: не читались навіть атрибути теки runtime/17/windows-x64
                Files.readAttributes(jvmDir.toPath(), BasicFileAttributes.class);
                try (java.nio.file.DirectoryStream<java.nio.file.Path> ds = Files.newDirectoryStream(jvmDir.toPath())) {
                    for (java.nio.file.Path ignored : ds) break;
                }
            }
            // як ставляться файли Java: скачаний файл у тимчасовій теці лаунчера, перенесення на місце, читання
            Files.createDirectories(tempDir.toPath());
            tmp = File.createTempFile("javaprobe", ".tmp", tempDir);
            byte[] data = ("GummerCraft " + System.nanoTime()).getBytes("UTF-8");
            Files.write(tmp.toPath(), data);
            Files.createDirectories(probeDir.toPath());
            Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            byte[] back = Files.readAllBytes(target.toPath());
            if (!Arrays.equals(data, back)) return "probe file differs in " + probeDir;
            return null;
        } catch (IOException | RuntimeException e) {
            return root.getAbsolutePath() + ": " + e;
        } finally {
            if (tmp != null && tmp.exists()) tmp.delete();
            if (target.exists()) target.delete();
            probeDir.delete();
        }
    }

    private static boolean same(File a, File b) {
        return a.getAbsoluteFile().toPath().normalize().equals(b.getAbsoluteFile().toPath().normalize());
    }
}
