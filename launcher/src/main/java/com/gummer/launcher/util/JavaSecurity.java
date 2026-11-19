/*
 * Copyright (C) 2021-2026 GummerCraft <http://gummercraft.fun>
 */

package com.gummer.launcher.util;

import com.gummer.launcher.model.java.JavaManifest;
import com.skcraft.launcher.Instance;
import com.skcraft.launcher.Launcher;
import com.skcraft.launcher.launch.runtime.JavaRuntime;
import com.skcraft.launcher.model.minecraft.VersionManifest;
import com.skcraft.launcher.util.Environment;
import com.skcraft.launcher.util.Platform;
import lombok.extern.java.Log;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

/**
 * 1.36.1 (19.11.2026, java4shell, CVE-2026-114938: пошкодження пам'яті в {@code java.util.Base64.Decoder} на OpenJDK 17+
 * x86-64 з AVX-512 VBMI, вектор у тому числі TLS-з'єднання клієнта до шкідливого сервера). Три речі:
 * <ul>
 * <li>{@link #runtimeStale}: Java гри примусово оновлюється, коли на CDN у маніфесті Java інша збірка, ніж стоїть у гравця.
 * До 1.36.1 тека {@code runtime/<версія>/<ОС>} звірялась лише під час оновлення модпака, тож нова Java на CDN доходила
 * тільки з наступним оновленням збірки. Тепер після встановлення Java в теку пишеться {@link #MARKER} (sha1 маніфесту
 * цієї Java), і перед запуском, якщо він не збігається з маніфестом CDN, збірка оновлюється (докачуються лише змінені
 * файли Java, решта перевіряється).</li>
 * <li>{@link #addGameFlags}: {@code -XX:-UseBASE64Intrinsics} для Java 17+ (на Java 8 невідомий прапорець не дасть JVM
 * стартувати, тому лише 9+, а зачеплено 17+). Діє і на чужу Java, вибрану гравцем.</li>
 * <li>{@link #protectLauncherJvm}: той самий прапорець для власної Java лаунчера (тека {@code jre} поруч з exe): Launch4j
 * читає додаткові параметри JVM з {@code <назва exe>.l4j.ini} поруч з exe, з наступного запуску лаунчера.</li>
 * </ul>
 */
@Log
public final class JavaSecurity {

    /** Файл у теці Java гри: sha1 маніфесту CDN, з якого її встановлено. Старі лаунчери його видаляють (cleanJvmFiles). */
    public static final String MARKER = ".gc-runtime";
    public static final String BASE64_FLAG = "-XX:-UseBASE64Intrinsics";

    private JavaSecurity() {
    }

    /** sha1 (або адреса) маніфесту Java на CDN: те, що пишеться в {@link #MARKER}. */
    public static String id(JavaManifest m) {
        if (m == null || m.getManifest() == null) return null;
        String h = m.getManifest().getHash();
        return h != null && !h.isEmpty() ? h : m.getManifest().getUrl();
    }

    /** Після повного встановлення Java гри (Updater, після фази install). Помилка запису: лише в лог. */
    public static void writeMarker(File jvmDir, JavaManifest m) {
        String id = id(m);
        if (jvmDir == null || id == null || !BundledJava.hasJavaBinary(jvmDir)) return;
        try {
            Files.write(new File(jvmDir, MARKER).toPath(), id.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            log.log(Level.WARNING, "Can't write " + MARKER + " in " + jvmDir, e);
        }
    }

    /**
     * Чи треба перед запуском оновити Java гри: збірку встановлено, Java стоїть, а маніфест CDN для неї інший, ніж у
     * {@link #MARKER} (або маркера немає: Java ставив лаунчер до 1.36.1). Без маніфесту Java (немає мережі, нема запису
     * для цієї ОС) або без адреси модпака: false. Будь-яка помилка: false (запуск як раніше).
     */
    public static boolean runtimeStale(Launcher launcher, Instance instance) {
        try {
            if (instance.getManifestURL() == null || instance.getJavaRuntime() == null) return false;
            VersionManifest version = Json.newMapper().readValue(instance.getVersionPath(), VersionManifest.class);
            String os = MacRosetta.runtimeOs(version);
            JavaManifest want = null;
            for (Map.Entry<String, JavaManifest[]> e : launcher.getInstances().getJavaVersionsFor(os).entrySet()) {
                if (e.getKey().equalsIgnoreCase(instance.getJavaRuntime())) {
                    if (e.getValue() != null && e.getValue().length > 0) want = e.getValue()[0];
                    break;
                }
            }
            String id = id(want);
            if (id == null) return false;
            File dir = BundledJava.getJavaDir(launcher, instance.getJavaRuntime(), os);
            if (!BundledJava.hasJavaBinary(dir)) return false; // Java немає зовсім: це звичайне встановлення, не тут
            // 1.36.3: автооновлення вимкнено в налаштуваннях або гравець відкотив цю Java
            if (JavaUpdates.keepInstalled(launcher, instance.getJavaRuntime(), os)) return false;
            File marker = new File(dir, MARKER);
            String have = marker.isFile() ? new String(Files.readAllBytes(marker.toPath()), StandardCharsets.UTF_8).trim() : "";
            if (id.equals(have)) return false;
            log.info("Java runtime " + instance.getJavaRuntime() + "/" + os + " differs from the CDN manifest ("
                    + (have.isEmpty() ? "no " + MARKER : have) + " -> " + id + "), updating " + instance.getName());
            return true;
        } catch (Exception e) {
            log.log(Level.FINE, "Runtime freshness check failed for " + instance.getName(), e);
            return false;
        }
    }

    /** {@link #BASE64_FLAG} у прапорці гри, якщо Java 17+ і гравець сам не задав {@code UseBASE64Intrinsics}. */
    public static void addGameFlags(JavaRuntime runtime, List<String> flags) {
        int major;
        try {
            major = runtime != null ? runtime.getMajorVersion() : 0;
        } catch (RuntimeException e) {
            major = 0;
        }
        if (major < 17) return;
        for (String f : flags) {
            if (f != null && f.contains("UseBASE64Intrinsics")) return;
        }
        flags.add(BASE64_FLAG);
    }

    /**
     * Прапорець для JVM самого лаунчера: лише Windows і лише вбудована Java (java.home = тека {@code jre} поруч з exe).
     * Для кожного exe в теці лаунчера дописує рядок у {@code <exe>.l4j.ini} (Launch4j: додаткові параметри JVM). Java 8
     * (jre8 для Windows 7/8.1) не зачеплена, і прапорець їй невідомий: пропускаємо.
     */
    public static void protectLauncherJvm() {
        try {
            if (Environment.getInstance().getPlatform() != Platform.WINDOWS) return;
            String spec = System.getProperty("java.specification.version", "");
            if (spec.startsWith("1.") || spec.isEmpty()) return;
            File home = new File(System.getProperty("java.home", "")).getCanonicalFile();
            if (!home.getName().equalsIgnoreCase("jre")) return;
            File appDir = home.getParentFile();
            File[] exes = appDir != null ? appDir.listFiles((d, n) -> n.toLowerCase().endsWith(".exe")) : null;
            if (exes == null) return;
            for (File exe : exes) {
                String n = exe.getName();
                File ini = new File(appDir, n.substring(0, n.length() - 4) + ".l4j.ini");
                List<String> lines = ini.isFile()
                        ? new ArrayList<>(Files.readAllLines(ini.toPath(), StandardCharsets.UTF_8)) : new ArrayList<>();
                boolean has = false;
                for (String l : lines) has |= l.contains("UseBASE64Intrinsics");
                if (has) continue;
                // спершу IgnoreUnrecognizedVMOptions: якщо в цю теку колись стане Java 8, невідомий прапорець не
                // завадить лаунчеру стартувати (без нього JVM 8 не запуститься зовсім)
                lines.add("-XX:+IgnoreUnrecognizedVMOptions");
                lines.add(BASE64_FLAG);
                Files.write(ini.toPath(), lines, StandardCharsets.UTF_8);
                log.info("Launcher JVM: " + BASE64_FLAG + " added to " + ini.getAbsolutePath() + " (next start)");
            }
        } catch (Exception e) {
            log.log(Level.FINE, "Can't protect the launcher JVM", e);
        }
    }
}
