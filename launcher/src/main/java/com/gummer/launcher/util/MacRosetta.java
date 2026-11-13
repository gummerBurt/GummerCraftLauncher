/*
 * Copyright (C) 2021-2026 GummerCraft <http://gummercraft.fun>
 */

package com.gummer.launcher.util;

import com.skcraft.launcher.model.minecraft.Library;
import com.skcraft.launcher.model.minecraft.VersionManifest;
import com.skcraft.launcher.util.Environment;
import com.skcraft.launcher.util.Platform;

/**
 * 1.32.2: Mac з Apple Silicon (M1 і новіші). Minecraft до 1.19 (LWJGL 2 або LWJGL 3 до 3.3.0) має нативні бібліотеки
 * лише під Intel (x86_64), і Java під arm64 падає з {@code UnsatisfiedLinkError ... incompatible architecture}
 * ще до вікна гри. Для таких версій беремо Java для Intel Mac ({@code mac-os}) і гра йде через Rosetta 2,
 * як і в офіційному лаунчері Mojang. Нові версії (LWJGL 3.3+) і далі на рідній arm64 Java.
 */
public final class MacRosetta {

    /** Ключ Java для Intel Mac у маніфесті runtime і в теці {@code runtime/<версія>/}. */
    public static final String INTEL_OS = "mac-os";

    private MacRosetta() {
    }

    /** Чи треба цій версії гри Java для Intel (лише на Mac з Apple Silicon). */
    public static boolean needsIntelJava(VersionManifest version) {
        Environment env = Environment.getInstance();
        if (env.getPlatform() != Platform.MAC_OS_X || env.getArchitecture() != Architecture.ARM64) return false;
        if (version == null || version.getLibraries() == null) return false;
        for (Library lib : version.getLibraries()) {
            String name = lib.getName() != null ? lib.getName().toString() : null; // MavenName.toString() = повна назва group:name:version
            if (name == null) continue;
            String[] p = name.split(":");
            if (p.length < 3) continue;
            if (p[0].equals("org.lwjgl.lwjgl") && p[1].equals("lwjgl")) return true; // LWJGL 2 (Minecraft до 1.13)
            if (p[0].equals("org.lwjgl") && p[1].equals("lwjgl")) return olderThan330(p[2]);
        }
        return false;
    }

    /** ОС для Java цієї версії гри: {@code mac-os} для старих версій на Apple Silicon, інакше як завжди. */
    public static String runtimeOs(VersionManifest version) throws Exception {
        return needsIntelJava(version) ? INTEL_OS : Environment.getInstance().getMojangOs();
    }

    /**
     * Чи бракує встановленій збірці Java для Intel: Apple Silicon, стара версія гри, а теки {@code runtime/<версія>/mac-os}
     * з Java немає (збірку ставив лаунчер до 1.32.2, який брав arm64 Java). Будь-яка помилка читання: false.
     */
    public static boolean intelJavaMissing(com.skcraft.launcher.Launcher launcher, com.skcraft.launcher.Instance instance) {
        try {
            VersionManifest version = Json.newMapper().readValue(instance.getVersionPath(), VersionManifest.class);
            return needsIntelJava(version)
                    && !BundledJava.hasJavaBinary(BundledJava.getJavaDir(launcher, instance.getJavaRuntime(), INTEL_OS));
        } catch (Exception e) {
            return false;
        }
    }

    static boolean olderThan330(String v) {
        String[] p = v.split("[.\\-+]");
        try {
            int major = Integer.parseInt(p[0]);
            int minor = p.length > 1 ? Integer.parseInt(p[1]) : 0;
            return major < 3 || (major == 3 && minor < 3);
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
