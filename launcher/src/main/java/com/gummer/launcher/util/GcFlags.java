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
 * <li>З 25.11.2026 Java 8+, а не лише 17+. Порівняння StalDayZ TEST 24.11 (Forge 1.16.5, Java 8, та сама точка): без
 * прапорців Java 8 бере ParallelGC, паузи 85-90 мс і повна зупинка 536 мс; SKlauncher з G1 на тій самій збірці мав паузи
 * до 27 мс при тих самих ~105 ФПС. Такий самий набір G1 давав і офіційний лаунчер Mojang для Java 8. Java 7 і старші не
 * чіпаємо.</li>
 * <li>Якщо гравець в «Аргументах JVM» або сама збірка (прапорці модпака, напр. StalDayZ) уже задали будь-що про збирач
 * (GC, G1, ParallelRefProc), не додаємо нічого: їхній набір головний, наш не змішується з ним.</li>
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

    /**
     * Додає налаштування G1 у {@code flags} (уже з аргументами гравця), якщо Java 8+ і ні гравець, ні збірка не задали свої.
     *
     * @param buildFlags прапорці самої збірки (LaunchModifier модпака), які додаються пізніше; можуть бути {@code null}
     */
    public static void addGameFlags(JavaRuntime runtime, int maxMemoryMb, List<String> flags, List<String> buildFlags) {
        int major;
        try {
            major = runtime != null ? runtime.getMajorVersion() : 0;
        } catch (RuntimeException e) {
            major = 0;
        }
        if (major < 8) return;
        if (hasOwnGc(flags, "the player")) return;
        if (buildFlags != null && hasOwnGc(buildFlags, "the build")) return;
        flags.addAll(FLAGS);
        // Рішення 22.11.2026 (В-ПЛАНІ, «G1 на Java 8 через замір»): на Java 8 повне прибирання G1 однопотокове (паралельне
        // лише з Java 10), тож при пам'яті до 4 ГБ резерв 10% замість 20%, щоб купи для гри лишалось більше і до повного
        // прибирання доходило рідше. Java 17+ лишається як з 1.36.11.
        boolean smallReserve = major < 17 && maxMemoryMb <= 4096;
        if (smallReserve) flags.add("-XX:G1ReservePercent=10"); // пізніший прапорець JVM перекриває 20 з FLAGS
        if (maxMemoryMb >= 4096) flags.add(REGION);
        log.info("GC flags: tuned G1 for Java " + major + (maxMemoryMb >= 4096 ? ", 16M regions" : "")
                + (smallReserve ? ", reserve 10%" : ""));
    }

    private static boolean hasOwnGc(List<String> list, String who) {
        for (String f : list) {
            if (f == null || !f.startsWith("-XX:")) continue;
            if (f.contains("GC") || f.contains("G1") || f.contains("ParallelRefProc")) {
                log.info("GC flags: " + who + " set their own (" + f + "), the launcher adds none");
                return true;
            }
        }
        return false;
    }
}
