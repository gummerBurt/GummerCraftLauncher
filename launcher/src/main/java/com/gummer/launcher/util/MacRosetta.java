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

    // 1.32.3: GLFW з LWJGL 3.3.3 (бібліотеки Mojang, та сама адреса, що в 1.20.5+), збірка для Intel (x86_64)
    private static final String GLFW_JAR = "org/lwjgl/lwjgl-glfw/3.3.3/lwjgl-glfw-3.3.3-natives-macos.jar";
    private static final String GLFW_JAR_SHA1 = "a1bf400f6bc64e6195596cb1430dafda46090751";
    private static final String GLFW_ENTRY = "macos/x64/org/lwjgl/glfw/libglfw.dylib";
    private static final String GLFW_OUT = "org/lwjgl/lwjgl-glfw/3.3.3/gummer-macos-x64/libglfw.dylib";

    /**
     * 1.32.3: чи треба цій версії гри новіший GLFW. Старий GLFW з LWJGL 3.2.x на Apple Silicon (і під Rosetta теж)
     * шукає в IOKit сервіс дисплея {@code IODisplayConnect}, якого там немає, і кидає помилку GLFW
     * «Cocoa: Failed to find service port for display». Раннє вікно Forge 1.16.5 лишає її непрочитаною, і Minecraft
     * падає з {@code GLFW error before init}; після створення вікна та сама помилка з {@code glfwPollEvents} теж
     * може валити гру. GLFW 3.3.8+ і 3.4 цієї помилки не кидають. Лише LWJGL 3 до 3.3.0 під Java для Intel (LWJGL 2 без GLFW).
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
     * 1.32.3: {@code libglfw.dylib} (GLFW 3.4, x86_64) з {@code lwjgl-glfw-3.3.3-natives-macos.jar}: докачує jar з
     * {@code librariesSource} (перевірка SHA-1, як у Mojang), розпаковує один раз. LWJGL 3.2.1 вантажить її за повним
     * шляхом з {@code -Dorg.lwjgl.glfw.libname}; усі 112 функцій GLFW, які кличе LWJGL 3.2.1, у ній є (звірено за
     * таблицями експорту Mach-O). Помилка: null, гра стартує зі старим GLFW.
     */
    public static java.io.File ensureNewGlfw(com.skcraft.launcher.Launcher launcher) {
        java.io.File out = new java.io.File(launcher.getLibrariesDir(), GLFW_OUT);
        try {
            if (out.isFile() && out.length() > 0) return out;
            java.io.File jar = new java.io.File(launcher.getLibrariesDir(), GLFW_JAR);
            if (!jar.isFile() || !GLFW_JAR_SHA1.equals(sha1(jar))) {
                jar.getParentFile().mkdirs();
                java.io.File tmp = new java.io.File(jar.getPath() + ".part");
                java.net.URL url = new java.net.URL(launcher.propUrl("librariesSource"), GLFW_JAR);
                com.skcraft.launcher.util.HttpRequest.get(url).execute().expectResponseCode(200).saveContent(tmp);
                String got = sha1(tmp);
                if (!GLFW_JAR_SHA1.equals(got)) {
                    tmp.delete();
                    throw new java.io.IOException("SHA-1 mismatch for " + GLFW_JAR + ": " + got);
                }
                if (jar.exists()) jar.delete();
                if (!tmp.renameTo(jar)) throw new java.io.IOException("Can't move " + tmp + " to " + jar);
            }
            out.getParentFile().mkdirs();
            java.io.File tmp = new java.io.File(out.getPath() + ".part");
            try (java.util.zip.ZipFile zip = new java.util.zip.ZipFile(jar)) {
                java.util.zip.ZipEntry entry = zip.getEntry(GLFW_ENTRY);
                if (entry == null) throw new java.io.IOException(GLFW_ENTRY + " not found in " + jar);
                try (java.io.InputStream in = zip.getInputStream(entry)) {
                    java.nio.file.Files.copy(in, tmp.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
            }
            if (out.exists()) out.delete();
            if (!tmp.renameTo(out)) throw new java.io.IOException("Can't move " + tmp + " to " + out);
            return out;
        } catch (Exception e) {
            java.util.logging.Logger.getLogger(MacRosetta.class.getName())
                    .log(java.util.logging.Level.WARNING, "Can't prepare the newer GLFW for Apple Silicon", e);
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
