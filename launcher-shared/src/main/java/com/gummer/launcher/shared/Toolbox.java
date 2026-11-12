/*
 * Copyright (C) 2021-2025 GummerCraft <http://gummercraft.fun>
 */

package com.gummer.launcher.shared;

import java.util.Locale;

public class Toolbox {

    /**
     * User-Agent усіх запитів лаунчера і стартера.
     *
     * <p>З 1.32: {@code GCLauncher/<версія> (<ОС>; os <розрядність ОС>; java <мажор> <розрядність Java> <bundled|system>; +https://gummercraft.fun/)},
     * наприклад {@code GCLauncher/1.32 (Windows 10; os x64; java 8 x64 bundled; +https://gummercraft.fun/)}. Початок
     * {@code GCLauncher/<версія> (<ОС>;} такий самий, як до 1.32, бо за ним рахується статистика з логів CDN. Нові поля
     * показують, скільки гравців на 32-бітній Windows чи 32-бітній Java і скільки вже на вбудованій Java.</p>
     */
    public static final String USER_AGENT = buildUserAgent();

    private static String buildUserAgent() {
        String os = System.getProperty("os.name");
        try {
            return String.format("GCLauncher/@version@ (%s; os %s; java %s %s %s; +https://gummercraft.fun/)",
                    os, osArch(), javaMajor(), javaArch(), javaKind());
        } catch (RuntimeException e) {
            return String.format("GCLauncher/@version@ (%s; +https://gummercraft.fun/)", os);
        }
    }

    /** Розрядність ОС: на Windows зі змінних середовища (32-бітна Java бачить справжню в PROCESSOR_ARCHITEW6432). */
    static String osArch() {
        String arch = System.getProperty("os.arch", "");
        if (System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")) {
            String wow64 = System.getenv("PROCESSOR_ARCHITEW6432");
            String nativeArch = System.getenv("PROCESSOR_ARCHITECTURE");
            if (wow64 != null && !wow64.trim().isEmpty()) {
                arch = wow64;
            } else if (nativeArch != null && !nativeArch.trim().isEmpty()) {
                arch = nativeArch;
            }
        }
        return normalizeArch(arch);
    }

    static String javaArch() {
        return normalizeArch(System.getProperty("os.arch", ""));
    }

    static String normalizeArch(String arch) {
        String a = arch.trim().toLowerCase(Locale.ROOT);
        if (a.equals("amd64") || a.equals("x86_64") || a.equals("x64")) {
            return "x64";
        }
        if (a.equals("aarch64") || a.startsWith("arm64")) {
            return "arm64";
        }
        if (a.equals("x86") || a.equals("i386") || a.equals("i686")) {
            return "x86";
        }
        return a.isEmpty() ? "unknown" : a;
    }

    /** "1.8" -> "8", "17" -> "17". */
    static String javaMajor() {
        String v = System.getProperty("java.specification.version", "");
        return v.startsWith("1.") ? v.substring(2) : (v.isEmpty() ? "unknown" : v);
    }

    /**
     * bundled: вбудована Java нового exe (тека {@code jre} поруч з {@code GummerCraft.exe}, план «Лаунчер без окремої Java», 3.1),
     * інакше system. Java гри з {@code runtime\...} (якою може бути запущений тестовий jar) рахується як system.
     */
    public static String javaKind() {
        java.io.File home = new java.io.File(System.getProperty("java.home", ""));
        // JDK 8 теж має java.home = ...\jdk\jre, тому «вбудована» лише коли поруч з jre лежить сам GummerCraft.exe
        for (java.io.File jre = home; jre != null && jre.getParentFile() != null; jre = jre.getParentFile()) {
            if (jre.getName().equalsIgnoreCase("jre")) {
                return new java.io.File(jre.getParentFile(), "GummerCraft.exe").isFile() ? "bundled" : "system";
            }
        }
        return "system";
    }

}
