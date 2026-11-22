/*
 * GummerCraft Launcher
 * Copyright (C) 2021-2026 GummerCraft <http://gummercraft.fun>
 * Please see LICENSE.txt for license information.
 */

package com.gummer.launcher.util;

import com.skcraft.launcher.launch.runtime.JavaRuntime;
import lombok.extern.java.Log;

import java.util.Arrays;
import java.util.List;

/**
 * 22.11.2026 (тікет #97: у SKlauncher гравця налаштований G1 і «трішки краща продуктивність»; власник: «го»). Налаштування
 * збирача пам'яті G1 для гри на Java 17+ (Minecraft 1.18 і новіші): коротші паузи прибирання пам'яті, менше мікрофризів.
 * Старт і ФПС від цього не змінюються.
 *
 * <ul>
 * <li>Лише Java 17+: на Java 8 (1.12.2, 1.7.10) старі збірки мають свої поради, не чіпаємо.</li>
 * <li>Якщо гравець сам задав будь-що про збирач в «Аргументах JVM» (GC, G1, ParallelRefProc), не додаємо нічого: його
 * набір головний, наш не змішується з ним.</li>
 * <li>{@code G1HeapRegionSize=16M} лише від 4 ГБ пам'яті: на меншій купі області по 16 МБ завеликі.</li>
 * </ul>
 */
@Log
public final class GcFlags {

    private static final List<String> FLAGS = Arrays.asList(
            "-XX:+UseG1GC",
            "-XX:+ParallelRefProcEnabled",
            "-XX:MaxGCPauseMillis=50",
            "-XX:+UnlockExperimentalVMOptions",
            "-XX:G1NewSizePercent=20",
            "-XX:G1ReservePercent=20");

    private static final String REGION = "-XX:G1HeapRegionSize=16M";

    private GcFlags() {
    }

    /** Додає налаштування G1 у {@code flags} (уже з аргументами гравця), якщо Java 17+ і гравець не задав свої. */
    public static void addGameFlags(JavaRuntime runtime, int maxMemoryMb, List<String> flags) {
        int major;
        try {
            major = runtime != null ? runtime.getMajorVersion() : 0;
        } catch (RuntimeException e) {
            major = 0;
        }
        if (major < 17) return;
        for (String f : flags) {
            if (f == null || !f.startsWith("-XX:")) continue;
            if (f.contains("GC") || f.contains("G1") || f.contains("ParallelRefProc")) {
                log.info("GC flags: the player set their own (" + f + "), the launcher adds none");
                return;
            }
        }
        flags.addAll(FLAGS);
        if (maxMemoryMb >= 4096) flags.add(REGION);
        log.info("GC flags: tuned G1 for Java " + major + (maxMemoryMb >= 4096 ? ", 16M regions" : ""));
    }
}
