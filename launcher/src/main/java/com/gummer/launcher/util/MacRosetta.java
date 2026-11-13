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

    // 1.32.4: офіційна збірка GLFW 3.3.10 для Intel (glfw-3.3.10.bin.MACOS.zip з GitHub GLFW, lib-x86_64/libglfw.3.dylib),
    // викладена на наш CDN під незмінним ім'ям. У 1.32.3 був GLFW 3.4 з LWJGL 3.3.3, але 3.4 на Mac кидає помилку
    // «Cocoa: Regular windows do not have icons on macOS» на glfwSetWindowIcon, і Minecraft 1.16.5 падав уже на створенні вікна.
    private static final String GLFW_NAME = "glfw-3.3.10-macos-x86_64.dylib";
    private static final String GLFW_SHA1 = "34b616ad06906bae049600002edd22aed3fd781c";
    private static final String[] GLFW_URLS = {
            "https://cdn.gummer.cc/launcher/install/libs/" + GLFW_NAME,
            "https://cdn.gummercraft.fun/launcher/install/libs/" + GLFW_NAME,
    };
    private static final String GLFW_OUT = "org/glfw/glfw/3.3.10/macos-x86_64/libglfw.dylib";

    /**
     * 1.32.3: чи треба цій версії гри інший GLFW. Старий GLFW з LWJGL 3.2.x на Apple Silicon (і під Rosetta теж)
     * шукає в IOKit сервіс дисплея {@code IODisplayConnect}, якого там немає, і кидає помилку GLFW
     * «Cocoa: Failed to find service port for display». Раннє вікно Forge 1.16.5 лишає її непрочитаною, і Minecraft
     * падає з {@code GLFW error before init}; поки вікно гри створюється, Minecraft 1.16.5 вважає фатальною будь-яку
     * помилку GLFW (вікно «GLFW error ... up-to-date drivers»). Тому потрібен саме GLFW 3.3.8-3.3.x: у ньому немає ні
     * цієї помилки (з 3.3.8), ні помилки про іконку вікна (вона з'явилась у 3.4). Лише LWJGL 3 до 3.3.0 під Java для Intel.
     */
    public static boolean needsNewGlfw(VersionManifest version) {
        if (!needsIntelJava(version)) return false;
        for (Library lib : version.getLibraries()) {
            String name = lib.getName() != null ? lib.getName().toString() : null;
            if (name == null) continue;
            String[] p = name.split(":");
            if (p.length >= 3 && p[0].equals("org.lwjgl") && p[1].equals("lwjgl")) return olderThan330(p[2]);
        }
        return false;
    }

    /**
     * 1.32.4: {@code libglfw.dylib} (GLFW 3.3.10, x86_64) з нашого CDN (друга адреса про запас), перевірка SHA-1,
     * кладеться в {@code libraries/org/glfw/glfw/3.3.10/macos-x86_64/} і далі береться з диска (SHA-1 звіряється щоразу,
     * файл 230 КБ). LWJGL 3.2.1 вантажить її за повним шляхом з {@code -Dorg.lwjgl.glfw.libname}; усі 112 функцій GLFW,
     * які кличе LWJGL 3.2.1, у ній є (звірено за таблицями експорту Mach-O). Помилка: null, гра стартує зі старим GLFW.
     */
    public static java.io.File ensureNewGlfw(com.skcraft.launcher.Launcher launcher) {
        java.io.File out = new java.io.File(launcher.getLibrariesDir(), GLFW_OUT);
        try {
            if (out.isFile() && GLFW_SHA1.equals(sha1(out))) return out;
            out.getParentFile().mkdirs();
            java.io.File tmp = new java.io.File(out.getPath() + ".part");
            Exception last = null;
            for (String u : GLFW_URLS) {
                try {
                    com.skcraft.launcher.util.HttpRequest.get(new java.net.URL(u)).execute().expectResponseCode(200).saveContent(tmp);
                    String got = sha1(tmp);
                    if (!GLFW_SHA1.equals(got)) throw new java.io.IOException("SHA-1 mismatch for " + u + ": " + got);
                    if (out.exists()) out.delete();
                    if (!tmp.renameTo(out)) throw new java.io.IOException("Can't move " + tmp + " to " + out);
                    return out;
                } catch (Exception e) {
                    last = e;
                    tmp.delete();
                }
            }
            throw last;
        } catch (Exception e) {
            java.util.logging.Logger.getLogger(MacRosetta.class.getName())
                    .log(java.util.logging.Level.WARNING, "Can't prepare GLFW 3.3.10 for Apple Silicon", e);
            return null;
        }
    }

    private static String sha1(java.io.File f) throws java.io.IOException {
        return com.google.common.io.Files.asByteSource(f).hash(com.google.common.hash.Hashing.sha1()).toString();
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
