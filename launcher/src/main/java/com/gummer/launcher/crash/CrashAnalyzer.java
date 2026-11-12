/*
 * Copyright (C) 2021-2026 GummerCraft <http://gummercraft.fun>
 */

package com.gummer.launcher.crash;

import com.skcraft.launcher.Instance;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Розбір вильоту гри (з 1.32): після завершення гри з помилкою лаунчер дивиться у свіжий звіт про збій
 * ({@code crash-reports}), {@code hs_err_pid*.log} JVM, {@code logs/latest.log} і вивід консолі, шукає відомі причини
 * і каже гравцю людською мовою, що сталось і що зробити. Невідома причина: загальна порада і підтримка.
 */
public final class CrashAnalyzer {

    /** Що робити: від цього залежить головна кнопка вікна вильоту. */
    public enum Action {
        /** Відкрити налаштування паку (пам'ять). */
        MEMORY,
        /** Перевстановити пак (зіпсовані або зайві файли, моди). */
        REINSTALL,
        /** Оновити драйвер відеокарти: відкрити інструкцію. */
        DRIVERS,
        /** Без особливої дії: лише підтримка. */
        NONE
    }

    /** Результат розбору: ключі текстів у lang (crash.&lt;key&gt;.title / .advice) і дія. */
    public static final class Diagnosis {
        public final String key;
        public final Action action;
        /** Деталь для тексту (ім'я мода, файл), або null. */
        public final String detail;
        /** Файл, з якого взято причину (звіт про збій), або null. */
        public final File source;

        Diagnosis(String key, Action action, String detail, File source) {
            this.key = key;
            this.action = action;
            this.detail = detail;
            this.source = source;
        }
    }

    private static final int TAIL = 512 * 1024;
    /** Драйвери відеокарт AMD, NVIDIA, Intel у стеку вильоту JVM. */
    private static final Pattern DRIVER_DLL = Pattern.compile(
            "(atio6axx|atioglxx|nvoglv64|nvoglv32|ig[0-9a-z]+icd(64|32)|igxelpicd64)\\.dll", Pattern.CASE_INSENSITIVE);

    private CrashAnalyzer() {
    }

    /**
     * @param instance пак, що впав
     * @param startedAt коли гру запущено (свіжіші файли належать цьому запуску)
     * @param console вивід консолі гри (може бути порожнім)
     * @param exitCode код виходу процесу
     */
    public static Diagnosis analyze(Instance instance, long startedAt, String console, int exitCode) {
        File dir = instance.getContentDir();
        File crash = newest(new File(dir, "crash-reports"), "crash-", ".txt", startedAt);
        File hsErr = newest(dir, "hs_err_pid", ".log", startedAt);
        File latest = new File(dir, "logs/latest.log");

        StringBuilder text = new StringBuilder();
        if (crash != null) text.append(tail(crash)).append('\n');
        if (hsErr != null) text.append(tail(hsErr)).append('\n');
        if (latest.isFile() && latest.lastModified() >= startedAt - 5000) text.append(tail(latest)).append('\n');
        if (console != null) text.append(console.length() > TAIL ? console.substring(console.length() - TAIL) : console);
        String t = text.toString();
        File source = crash != null ? crash : hsErr;

        Diagnosis d = match(t, source);
        if (d != null) return d;
        // відомі коди виходу Windows: 0xC0000005 (-1073741819) доступ до пам'яті, найчастіше драйвер відеокарти
        if (exitCode == -1073741819 || exitCode == -1073740791) {
            return new Diagnosis("driver", Action.DRIVERS, null, source);
        }
        return new Diagnosis("unknown", Action.NONE, null, source);
    }

    /** Пошук відомих причин у тексті звіту чи лога. Пакетний доступ для перевірки. */
    static Diagnosis match(String t, File source) {
        if (t.contains("java.lang.OutOfMemoryError") || t.contains("Java heap space")
                || t.contains("GC overhead limit exceeded")) {
            return new Diagnosis("memory", Action.MEMORY, null, source);
        }
        if (t.contains("Could not reserve enough space for") || t.contains("Invalid maximum heap size")
                || t.contains("There is insufficient memory for the Java Runtime Environment")) {
            return new Diagnosis("memoryTooHigh", Action.MEMORY, null, source);
        }
        if (t.contains("UnsupportedClassVersionError") || t.contains("has been compiled by a more recent version of the Java Runtime")) {
            return new Diagnosis("javaVersion", Action.REINSTALL, null, source);
        }
        if (t.contains("Pixel format not accelerated") || t.contains("WGL: The driver does not appear to support OpenGL")
                || t.contains("GLFW error 65542") || t.contains("GLFW error 65543") || t.contains("No OpenGL context found")
                || t.contains("OpenGL 3.2 is required") || t.contains("Couldn't set pixel format")
                || DRIVER_DLL.matcher(t).find()) {
            return new Diagnosis("driver", Action.DRIVERS, null, source);
        }
        Matcher dup = Pattern.compile("Mod ([A-Za-z0-9_\\-]+) is present multiple times|Found duplicate mods?[^\\n]*?([A-Za-z0-9_\\-]+)").matcher(t);
        if (dup.find()) {
            return new Diagnosis("duplicateMod", Action.REINSTALL, firstGroup(dup), source);
        }
        Matcher incompatible = Pattern.compile("(?:Incompatible mods? found!|ModResolutionException)[\\s\\S]{0,600}?(?:Mod '([^']+)'|mod ([A-Za-z0-9_\\-]+))").matcher(t);
        if (incompatible.find() || t.contains("Incompatible mods found!") || t.contains("ModResolutionException")) {
            return new Diagnosis("modConflict", Action.REINSTALL, firstGroupOrNull(incompatible), source);
        }
        Matcher mixin = Pattern.compile("Mixin apply(?: for mod ([A-Za-z0-9_\\-]+))? failed").matcher(t);
        if (mixin.find()) {
            return new Diagnosis("mixin", Action.REINSTALL, mixin.group(1), source);
        }
        if (t.contains("java.util.zip.ZipException") || t.contains("zip END header not found") || t.contains("Invalid or corrupt jarfile")
                || t.contains("java.lang.NoClassDefFoundError") || t.contains("java.lang.ClassNotFoundException: net.minecraft")) {
            return new Diagnosis("corrupt", Action.REINSTALL, null, source);
        }
        if (t.contains("There is not enough space on the disk") || t.contains("No space left on device")) {
            return new Diagnosis("disk", Action.NONE, null, source);
        }
        return null;
    }

    private static String firstGroup(Matcher m) {
        for (int i = 1; i <= m.groupCount(); i++) {
            if (m.group(i) != null) return m.group(i);
        }
        return null;
    }

    private static String firstGroupOrNull(Matcher m) {
        try {
            return firstGroup(m);
        } catch (IllegalStateException e) {
            return null; // збігу регулярки не було, причина знайдена за простим рядком
        }
    }

    private static File newest(File dir, String prefix, String suffix, long since) {
        File[] files = dir.listFiles();
        if (files == null) return null;
        File best = null;
        for (File f : files) {
            String n = f.getName();
            if (!f.isFile() || !n.startsWith(prefix) || !n.endsWith(suffix)) continue;
            if (f.lastModified() < since - 5000) continue;
            if (best == null || f.lastModified() > best.lastModified()) best = f;
        }
        return best;
    }

    private static String tail(File f) {
        try (RandomAccessFile raf = new RandomAccessFile(f, "r")) {
            long len = raf.length();
            long start = Math.max(0, len - TAIL);
            byte[] b = new byte[(int) (len - start)];
            raf.seek(start);
            raf.readFully(b);
            return new String(b, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "";
        }
    }
}
