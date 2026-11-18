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
        /** 17.11.2026: вимкнути мод, який звалив гру ({@link Diagnosis#modFile}). */
        DISABLE_MOD,
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
        /** 17.11.2026: увімкнений мод, який можна вимкнути кнопкою (для {@link Action#DISABLE_MOD}), або null. */
        public final File modFile;
        /** 18.11.2026: назва мода для кнопки «Вимкнути», якщо деталь довша (missingDep: «мод → залежність»). */
        public final String modName;
        /** 18.11.2026: id залежності, якої бракує чи яка застара (missingDep, depTooOld), або null. */
        public String depId;
        /** 18.11.2026: jar цієї залежності в збірці (depTooOld: його кнопка «Оновити»), або null. */
        public File depFile;

        Diagnosis(String key, Action action, String detail, File source) {
            this(key, action, detail, source, null);
        }

        Diagnosis(String key, Action action, String detail, File source, File modFile) {
            this(key, action, detail, source, modFile, detail);
        }

        Diagnosis(String key, Action action, String detail, File source, File modFile, String modName) {
            this.modName = modName;
            this.key = key;
            this.action = action;
            this.detail = detail;
            this.source = source;
            this.modFile = modFile;
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
        if (d != null) return withMod(instance, d);
        // відомі коди виходу Windows: 0xC0000005 (-1073741819) доступ до пам'яті, найчастіше драйвер відеокарти
        if (exitCode == -1073741819 || exitCode == -1073740791) {
            return new Diagnosis("driver", Action.DRIVERS, null, source);
        }
        return new Diagnosis("unknown", Action.NONE, null, source);
    }

    /** Пошук відомих причин у тексті звіту чи лога. Пакетний доступ для перевірки. */
    static Diagnosis match(String t, File source) {
        // 18.11.2026 (власник обрав: «помічник при вильоті розпізнає цей текст і пише гравцю по-людськи»): модпак, автори
        // якого дозволяють гру лише зі свого лаунчера (Official Grand Theft Warzone, мод GtwNpc: «Invalid minecraft
        // configuration. Unsupported launcher. Stopping.»). Це їхній захист, лаунчер його не обходить. Першим: у такому
        // лозі є й інші помилки (текстури, моделі), але гру зупинила саме ця перевірка
        if (t.contains("Unsupported launcher") || t.contains("unsupported launcher")) {
            return new Diagnosis("launcherLocked", Action.NONE, null, source);
        }
        if (t.contains("java.lang.OutOfMemoryError") || t.contains("Java heap space")
                || t.contains("GC overhead limit exceeded")) {
            return new Diagnosis("memory", Action.MEMORY, null, source);
        }
        if (t.contains("Could not reserve enough space for") || t.contains("Invalid maximum heap size")
                || t.contains("There is insufficient memory for the Java Runtime Environment")) {
            return new Diagnosis("memoryTooHigh", Action.MEMORY, null, source);
        }
        // 1.32.2: Mac з Apple Silicon, нативна бібліотека під іншу архітектуру (старий лаунчер чи своя Java в налаштуваннях)
        if (t.contains("incompatible architecture") && t.contains("UnsatisfiedLinkError")) {
            return new Diagnosis("macArch", Action.REINSTALL, null, source);
        }
        if (t.contains("UnsupportedClassVersionError") || t.contains("has been compiled by a more recent version of the Java Runtime")) {
            return new Diagnosis("javaVersion", Action.REINSTALL, null, source);
        }
        if (t.contains("Pixel format not accelerated") || t.contains("WGL: The driver does not appear to support OpenGL")
                || t.contains("GLFW error 65542") || t.contains("GLFW error 65543") || t.contains("No OpenGL context found")
                || t.contains("OpenGL 3.2 is required") || t.contains("Couldn't set pixel format")
                // Linux (1.32): Mesa/GLX без потрібного OpenGL або без драйвера
                || t.contains("libGL error") || t.contains("GLXBadFBConfig") || t.contains("GLX: No GLXFBConfigs")
                || t.contains("GLX: Failed to load GLX") || t.contains("Failed to load libGL")
                || DRIVER_DLL.matcher(t).find()) {
            return new Diagnosis("driver", Action.DRIVERS, null, source);
        }
        // 17.11.2026: моду бракує іншого мода. Fabric: «Mod 'Sodium Extra' (sodium-extra) 0.5.4 requires any version of
        // sodium, which is missing!»; Forge і NeoForge: «Mod ID: 'architectury', Requested by: 'rei'»
        Matcher fabricMissing = Pattern.compile("Mod '([^'\\n]+)' \\([A-Za-z0-9_\\-]+\\)[^\\n]*? requires [^\\n]*?(?:of (?:mod )?)'?([^'\\n,]+?)'?(?: \\([A-Za-z0-9_\\-]+\\))?, which is missing").matcher(t);
        if (fabricMissing.find()) {
            return new Diagnosis("missingDep", Action.NONE, fabricMissing.group(1).trim() + " → " + fabricMissing.group(2).trim(), source);
        }
        Matcher forgeMissing = Pattern.compile("Mod ID: '([A-Za-z0-9_\\-]+)', Requested by: '([A-Za-z0-9_\\-]+)'").matcher(t);
        if (forgeMissing.find() && t.contains("[MISSING]")) {
            return new Diagnosis("missingDep", Action.NONE, forgeMissing.group(2) + " → " + forgeMissing.group(1), source);
        }
        // 17.11.2026: залежність стоїть, але стара (The Pixelmon Modpack: «Mod pixelmon only supports byg 1.3.4 or above
        // Currently, byg is 1.1.6»). Forge 1.16: «requires/only supports X V or above»; Forge 1.17+: «Expected range»;
        // Fabric: «requires version V or later of ..., but only the wrong version is present»
        Matcher oldForge = Pattern.compile("Mod ([A-Za-z0-9_\\-]+) (?:requires|only supports) ([A-Za-z0-9_\\-]+) (\\S+) or above\\s*Currently, [A-Za-z0-9_\\-]+ is (\\S+)").matcher(t);
        if (oldForge.find()) {
            // «Currently, byg is not installed»: залежності немає зовсім
            if ("not".equals(oldForge.group(4))) return new Diagnosis("missingDep", Action.NONE, oldForge.group(1) + " → " + oldForge.group(2), source);
            return new Diagnosis("depTooOld", Action.NONE, oldForge.group(1) + " → " + oldForge.group(2) + " " + oldForge.group(3) + "+", source);
        }
        Matcher oldForge2 = Pattern.compile("Mod ID: '([A-Za-z0-9_\\-]+)', Requested by: '([A-Za-z0-9_\\-]+)', Expected range: '[\\[(]([^,'\\]]+)[^']*', Actual version: '([^'\\[]+)'").matcher(t);
        if (oldForge2.find()) {
            return new Diagnosis("depTooOld", Action.NONE, oldForge2.group(2) + " → " + oldForge2.group(1) + " " + oldForge2.group(3) + "+", source);
        }
        Matcher oldFabric = Pattern.compile("Mod '([^'\\n]+)' \\([A-Za-z0-9_\\-]+\\)[^\\n]*? requires version (\\S+) or later of (?:mod )?'?([^'\\n,]+?)'?(?: \\([A-Za-z0-9_\\-]+\\))?, but only the wrong version is present").matcher(t);
        if (oldFabric.find()) {
            return new Diagnosis("depTooOld", Action.NONE, oldFabric.group(1).trim() + " → " + oldFabric.group(3).trim() + " " + oldFabric.group(2) + "+", source);
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
        // 17.11.2026: хто звалив гру. Forge і NeoForge пишуть у звіті «Suspected Mod(s): Назва (modid)», у Fabric
        // помилка в коді мода видна як «... from mod modid» у стеку
        Matcher suspected = Pattern.compile("Suspected Mods?:\\s*([^\\n(]+?)\\s*\\(([a-z0-9_\\-]+)\\)").matcher(t);
        if (suspected.find() && !BUILTIN.contains(suspected.group(2))) {
            return new Diagnosis("modCrash", Action.NONE, suspected.group(2), source);
        }
        Matcher fromMod = Pattern.compile("from mod ([a-z0-9_\\-]+)").matcher(t);
        while (fromMod.find()) {
            if (!BUILTIN.contains(fromMod.group(1))) return new Diagnosis("modCrash", Action.NONE, fromMod.group(1), source);
        }
        return null;
    }

    /** id, які не є «винним модом»: сама гра і завантажувачі. */
    private static final java.util.Set<String> BUILTIN = new java.util.HashSet<String>(java.util.Arrays.asList(
            "minecraft", "java", "forge", "neoforge", "fml", "fabricloader", "fabric", "quilt_loader", "mixinextras", "unknown"));

    /**
     * 17.11.2026: якщо причина в конкретному моді (mixin, «звалив мод»), знайти його увімкнений jar у цій збірці: тоді
     * у вікні буде кнопка «Вимкнути». Назва мода замість id. Мод StalDayZ (заблокований) не пропонуємо.
     */
    private static Diagnosis withMod(Instance instance, Diagnosis d) {
        if (d.detail != null && ("missingDep".equals(d.key) || "depTooOld".equals(d.key))) return withDependent(instance, d);
        if (d.detail == null || !("mixin".equals(d.key) || "modCrash".equals(d.key))) return d;
        try {
            String id = d.detail.toLowerCase(java.util.Locale.ROOT);
            for (com.gummer.launcher.manage.InstanceMods.Mod m : com.gummer.launcher.manage.InstanceMods.list(instance)) {
                if (!m.enabled || m.locked) continue;
                com.gummer.launcher.manage.ModInfo info = com.gummer.launcher.manage.ModInfo.of(m.file);
                if (info.provides.contains(id) || info.name != null && info.name.equalsIgnoreCase(d.detail)) {
                    return new Diagnosis(d.key, Action.DISABLE_MOD, info.name != null ? info.name : d.detail, d.source, m.file);
                }
            }
        } catch (RuntimeException ignored) {
            // без кнопки
        }
        return d;
    }

    /**
     * 18.11.2026 (власник: помічник при вильоті з людською назвою мода і кнопками). «mod → залежність»: мод зліва
     * знайти в збірці, показати його назву замість id і дати кнопку «Вимкнути» саме його: без нього гра запуститься.
     */
    private static Diagnosis withDependent(Instance instance, Diagnosis d) {
        int arrow = d.detail.indexOf(" → ");
        if (arrow <= 0) return d;
        String id = d.detail.substring(0, arrow).trim().toLowerCase(java.util.Locale.ROOT);
        try {
            for (com.gummer.launcher.manage.InstanceMods.Mod m : com.gummer.launcher.manage.InstanceMods.list(instance)) {
                if (!m.enabled || m.locked) continue;
                com.gummer.launcher.manage.ModInfo info = com.gummer.launcher.manage.ModInfo.of(m.file);
                if (!id.equals(info.id == null ? null : info.id.toLowerCase(java.util.Locale.ROOT)) && !info.provides.contains(id)) continue;
                String name = info.name != null ? info.name : id;
                Diagnosis out = new Diagnosis(d.key, Action.DISABLE_MOD, name + d.detail.substring(arrow), d.source, m.file, name);
                String dep = d.detail.substring(arrow + 3).trim();
                int sp = dep.indexOf(' ');
                out.depId = sp > 0 ? dep.substring(0, sp) : dep;
                if ("depTooOld".equals(d.key)) out.depFile = providerOf(instance, out.depId);
                return out;
            }
        } catch (RuntimeException ignored) {
            // без кнопки
        }
        return d;
    }

    /** Увімкнений jar збірки, що дає id (для оновлення застарілої залежності), або null. */
    private static File providerOf(Instance instance, String id) {
        String low = id.toLowerCase(java.util.Locale.ROOT);
        for (com.gummer.launcher.manage.InstanceMods.Mod m : com.gummer.launcher.manage.InstanceMods.list(instance)) {
            if (!m.enabled || m.locked) continue;
            if (com.gummer.launcher.manage.ModInfo.of(m.file).provides.contains(low)) return m.file;
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
