/*
 * Copyright (C) 2021-2026 GummerCraft <http://gummercraft.fun>
 */

package com.gummer.launcher.install;

import com.skcraft.launcher.Launcher;
import com.skcraft.launcher.install.InstallTask;
import lombok.NonNull;
import lombok.extern.java.Log;

import java.io.File;

/**
 * 1.41.3 (26.11.2026): копії системних бібліотек Windows у теці Java ({@code bin/ucrtbase.dll}, {@code bin/api-ms-win-*.dll}).
 * Їх кладуть у збірки Java лише для Windows 7 і 8.1; на Windows 10 і 11 ця бібліотека (Universal CRT) є частиною системи,
 * і Java бере її з {@code System32}, навіть коли копія лежить поруч не та чи її немає (перевірено 26.11 на Java 11, 16, 17,
 * 21, 25: без цих файлів усе працює, {@code ucrtbase.dll} завантажується з {@code System32}).
 * <p>
 * Антивіруси такі файли не на своєму місці чіпають: тікет #109 (файл зникав між переносом і перевіркою, шлях латиницею)
 * і скрін власника 26.11 (Java 21, «api-ms-win-core-util-l1-1-0.dll (Отказано в доступе)», шлях з кирилицею, але кирилиця
 * ні до чого). Встановлення Java тоді падало цілком.
 * <p>
 * Поведінка не змінюється, поки все працює: файли так само качаються, переносяться і звіряються тим самим кодом. Лише якщо
 * на Windows 10+ з таким файлом сталась помилка (не читається, не переноситься, не той sha1), лаунчер пише рядок у лог і
 * ставить Java далі без нього. Java 8, Windows 7 і 8.1, решта файлів: як раніше, помилка зупиняє встановлення.
 */
@Log
public final class SystemDlls {
    private SystemDlls() {
    }

    /** Windows 10 чи новіша ({@code os.version} 10.0 і вище; Windows 11 теж віддає 10.0). */
    static boolean windows10Plus() {
        String os = System.getProperty("os.name", "");
        if (!os.startsWith("Windows")) return false;
        try {
            String v = System.getProperty("os.version", "0");
            int dot = v.indexOf('.');
            return Integer.parseInt(dot > 0 ? v.substring(0, dot) : v) >= 10;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /**
     * Файл Java, без якого Java на цій системі обійдеться: копія системної бібліотеки Windows (лише Windows 10+) або
     * текст ліцензії чи довідки ({@link #notice}).
     */
    public static boolean optional(File f) {
        return systemDll(f) || notice(f);
    }

    /** Копія системної бібліотеки Windows на Windows 10+. */
    static boolean systemDll(File f) {
        if (f == null || !windows10Plus()) return false;
        String n = f.getName().toLowerCase(java.util.Locale.ROOT);
        return n.endsWith(".dll") && (n.equals("ucrtbase.dll") || n.startsWith("api-ms-win-"));
    }

    /**
     * 1.41.6 (28.11.2026, тікет #150 і 44 невдачі встановлення за 21-28.11): тексти ліцензій і довідки в теці Java
     * ({@code legal/<модуль>/ASSEMBLY_EXCEPTION}, {@code LICENSE}, {@code ADDITIONAL_LICENSE_INFO}, {@code DISCLAIMER},
     * {@code Welcome.html}). Java їх ніколи не читає. У #150 антивірус не давав створити саме {@code legal/java.rmi},
     * і в «Документах», і в запасній теці {@code %LOCALAPPDATA%}, тож Java не ставилась ніде. На будь-якій ОС: файли так
     * само качаються і кладуться, лише помилка з ними не зупиняє встановлення.
     */
    static boolean notice(File f) {
        if (f == null) return false;
        String n = f.getName().toLowerCase(java.util.Locale.ROOT);
        if (n.endsWith(".dll") || n.endsWith(".exe") || n.endsWith(".jar") || n.endsWith(".so") || n.endsWith(".dylib")) return false;
        File p = f.getParentFile();
        for (int i = 0; i < 3 && p != null; i++, p = p.getParentFile()) {
            if ("legal".equalsIgnoreCase(p.getName())) return true;
        }
        return n.equals("disclaimer") || n.equals("welcome.html") || n.equals("readme") || n.equals("readme.txt")
                || n.equals("readme.html");
    }

    /**
     * Необов'язкові файли, з якими вже сталась помилка, і теки, куди не дали писати (за час роботи лаунчера). Лише для
     * {@link #optional} файлів: решта встановлюється і падає як раніше.
     */
    private static final java.util.Set<File> SKIPPED = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private static final java.util.Set<File> DENIED_DIRS = java.util.concurrent.ConcurrentHashMap.newKeySet();

    /** У ланцюжку причин відмова в доступі (Windows: «Отказано в доступе», «Access is denied»). */
    static boolean denied(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof java.nio.file.AccessDeniedException) return true;
            String m = String.valueOf(t.getMessage()).toLowerCase(java.util.Locale.ROOT);
            if (m.contains("access is denied") || m.contains("отказано в доступе") || m.contains("відмовлено в доступі")) return true;
        }
        return false;
    }

    /** Рядок у лог про пропущений файл. */
    public static void skipped(File f, Throwable cause) {
        if (systemDll(f)) {
            log.warning("Skipping Windows system DLL copy " + f.getAbsolutePath() + " (Windows 10+ loads its own from System32): " + cause);
        } else {
            log.warning("Skipping Java notice file " + f.getAbsolutePath() + " (licence or readme text, Java does not read it): " + cause);
        }
    }

    /**
     * Обгортка задачі встановлення: для файлу {@link #optional} помилка задачі не зупиняє встановлення, а лише пишеться в лог.
     * Для решти файлів повертає саму задачу без змін.
     */
    public static InstallTask tolerant(@NonNull File target, @NonNull InstallTask task) {
        if (!optional(target)) return task;
        return new InstallTask() {
            @Override
            public void execute(Launcher launcher) throws Exception {
                File file = target.getAbsoluteFile();
                File dir = file.getParentFile();
                // 1.41.6: перенесення робить до 5 спроб з паузами, перевірка ще 3; на 182 файлах ліцензій у заблокованій теці
                // це 3+ хвилини зайвого «зависання» (наскрізна проба 28.11: 220 с проти 17). Файл чи тека вже відмовили: одразу далі
                if (SKIPPED.contains(file) || (dir != null && DENIED_DIRS.contains(dir))) {
                    log.info("Skipping " + file + " right away (this file or its folder already failed)");
                    return;
                }
                try {
                    task.execute(launcher);
                } catch (InterruptedException e) {
                    throw e;
                } catch (Exception e) {
                    SKIPPED.add(file);
                    if (dir != null && (!dir.isDirectory() || denied(e))) DENIED_DIRS.add(dir);
                    skipped(target, e);
                }
            }

            @Override
            public double getProgress() {
                return task.getProgress();
            }

            @Override
            public String getStatus() {
                return task.getStatus();
            }
        };
    }
}
