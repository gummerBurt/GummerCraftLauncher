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

    /** Файл є копією системної бібліотеки Windows, без якої Java на цій системі обійдеться. */
    public static boolean optional(File f) {
        if (f == null || !windows10Plus()) return false;
        String n = f.getName().toLowerCase(java.util.Locale.ROOT);
        return n.endsWith(".dll") && (n.equals("ucrtbase.dll") || n.startsWith("api-ms-win-"));
    }

    /** Рядок у лог про пропущений файл. */
    public static void skipped(File f, Throwable cause) {
        log.warning("Skipping Windows system DLL copy " + f.getAbsolutePath() + " (Windows 10+ loads its own from System32): " + cause);
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
                try {
                    task.execute(launcher);
                } catch (InterruptedException e) {
                    throw e;
                } catch (Exception e) {
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
