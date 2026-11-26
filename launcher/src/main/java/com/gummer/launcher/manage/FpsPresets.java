/*
 * Copyright (C) 2021-2026 GummerCraft <http://gummercraft.fun>
 */

package com.gummer.launcher.manage;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.gummer.launcher.util.Json;
import com.skcraft.launcher.Instance;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;

/**
 * 18.11.2026 (власник: «пресети ФПС ... щоб пресети фпс можна було додати на вже існуючі збірки, а не зробити нову
 * таку»; «нічого не має ламати збереження і світи»). Дві незалежні частини для будь-якої вже наявної збірки:
 *
 * <p>1. Налаштування гри в {@code options.txt} (дальність, частинки, тіні, графіка): лише відомі ключі, решта рядків
 * файлу без змін, перед записом копія {@code options.txt.before-preset}. Підходить і для модпаків GummerCraft і
 * StalDayZ. Світів не стосується.</p>
 *
 * <p>2. Клієнтські моди для ФПС з Modrinth (лише збірки, де моди веде гравець): рендерер Sodium (Fabric, Quilt,
 * NeoForge) чи Embeddium (Forge), FerriteCore (пам'ять), Entity Culling, ImmediatelyFast. Вони не міняють ні світу, ні
 * логіки гри. Не ставляться, якщо в збірці вже є інший рендерер чи OptiFine, і прибираються, якщо Modrinth каже про
 * несумісність з наявним модом. Поставлене записується в {@value #ADDED}: «Прибрати моди для ФПС» прибирає саме його.</p>
 */
public final class FpsPresets {

    private static final Logger log = Logger.getLogger(FpsPresets.class.getName());
    private static final ObjectMapper MAPPER = Json.newMapper();
    public static final String ADDED = "fps-mods.json";

    public enum Preset { LOW, BALANCED, HIGH }

    private FpsPresets() {
    }

    // ---------------------------------------------------------------- options.txt

    /** Ключ і значення для LOW, BALANCED, HIGH; since: з якої версії гри (мінорна частина 1.X), 0 для всіх. */
    private static final Object[][] OPTIONS = {
            {"renderDistance", "6", "10", "16", 0},
            {"simulationDistance", "5", "8", "12", 18},
            {"graphicsMode", "0", "1", "1", 16},
            {"particles", "2", "1", "0", 0},
            {"entityShadows", "false", "true", "true", 0},
            {"biomeBlendRadius", "0", "2", "3", 13},
            {"entityDistanceScaling", "0.5", "0.75", "1.0", 16},
    };

    /**
     * Записати пресет в options.txt збірки. Ключі, яких у файлі ще немає, додаються лише для версій гри, де вони є.
     * Повертає скільки значень змінено. Гра має бути закрита (вона перезаписує файл при виході).
     */
    public static int applyOptions(Instance instance, Preset preset) throws IOException {
        File content = instance.getContentDir();
        File file = new File(content, "options.txt");
        int minor = minorOf(ModrinthBrowse.gameVersionOf(instance));
        Map<String, String> lines = new LinkedHashMap<String, String>();
        List<String> order = new ArrayList<String>();
        if (file.isFile()) {
            for (String l : new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8).split("\r?\n")) {
                int c = l.indexOf(':');
                String k = c > 0 ? l.substring(0, c) : null;
                if (k == null || lines.containsKey(k)) {
                    order.add("\u0000" + l);
                    continue;
                }
                lines.put(k, l.substring(c + 1));
                order.add(k);
            }
            // 18.11.2026: копія лише перед першим пресетом (стан гравця до пресетів), щоб «Повернути налаштування гри»
            // вертало саме його, а не результат попереднього пресета
            File backup = new File(content, BACKUP);
            if (!backup.exists()) Files.copy(file.toPath(), backup.toPath());
        } else {
            content.mkdirs();
            File backup = new File(content, BACKUP);
            if (!backup.exists()) Files.write(backup.toPath(), new byte[0]); // налаштувань ще не було
        }
        int idx = preset.ordinal() + 1, changed = 0;
        for (Object[] o : OPTIONS) {
            String key = (String) o[0], value = (String) o[idx];
            int since = (Integer) o[4];
            if (!lines.containsKey(key) && (minor < since || minor <= 0 && since > 0)) continue;
            if (!value.equals(lines.get(key))) changed++;
            if (!lines.containsKey(key)) order.add(key);
            lines.put(key, value);
        }
        // плавне освітлення: формат різний (до 1.19 число 0-2, далі true/false), тож лише якщо вже є у файлі
        String ao = lines.get("ao");
        if (ao != null) {
            String v = ao.equals("true") || ao.equals("false") ? (preset == Preset.LOW ? "false" : "true") : (preset == Preset.LOW ? "0" : "2");
            if (!v.equals(ao)) changed++;
            lines.put("ao", v);
        }
        if (minor < 16 && lines.containsKey("fancyGraphics")) {
            String v = preset == Preset.LOW ? "false" : "true";
            if (!v.equals(lines.get("fancyGraphics"))) changed++;
            lines.put("fancyGraphics", v);
        }
        StringBuilder out = new StringBuilder();
        for (String k : order) {
            if (k.startsWith("\u0000")) out.append(k.substring(1));
            else out.append(k).append(':').append(lines.get(k));
            out.append('\n');
        }
        content.mkdirs();
        File tmp = new File(content, "options.txt.tmp");
        Files.write(tmp.toPath(), out.toString().getBytes(StandardCharsets.UTF_8));
        Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
        log.info("FPS preset " + preset + " for " + instance.getName() + ": " + changed + " option(s) changed");
        return changed;
    }

    /** Копія options.txt перед першим пресетом (порожня, якщо файлу ще не було). */
    public static final String BACKUP = "options.txt.before-preset";

    /** Чи є що повертати («Повернути налаштування гри»). */
    public static boolean canRestoreOptions(Instance instance) {
        return new File(instance.getContentDir(), BACKUP).isFile();
    }

    /**
     * 18.11.2026 (власник: кнопка «Повернути налаштування гри» «збс»). Повертає лише ті налаштування, які міняють
     * пресети (дальність, частинки, тіні, графіка, ao, fancyGraphics), до значень перед першим пресетом; клавіші, звук,
     * мова і все, що гравець змінив у грі після пресета, лишаються. Копія після цього прибирається. Повертає скільки
     * значень змінено.
     */
    public static int restoreOptions(Instance instance) throws IOException {
        File content = instance.getContentDir();
        File backup = new File(content, BACKUP), file = new File(content, "options.txt");
        if (!backup.isFile()) return 0;
        Map<String, String> before = new LinkedHashMap<String, String>();
        for (String l : new String(Files.readAllBytes(backup.toPath()), StandardCharsets.UTF_8).split("\r?\n")) {
            int c = l.indexOf(':');
            if (c > 0 && !before.containsKey(l.substring(0, c))) before.put(l.substring(0, c), l.substring(c + 1));
        }
        java.util.Set<String> managed = new java.util.HashSet<String>();
        for (Object[] o : OPTIONS) managed.add((String) o[0]);
        managed.add("ao");
        managed.add("fancyGraphics");
        int changed = 0;
        StringBuilder out = new StringBuilder();
        java.util.Set<String> seen = new java.util.HashSet<String>();
        if (file.isFile()) {
            for (String l : new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8).split("\r?\n")) {
                int c = l.indexOf(':');
                String k = c > 0 ? l.substring(0, c) : null;
                if (k == null || !managed.contains(k) || !seen.add(k)) {
                    if (!l.isEmpty()) out.append(l).append('\n');
                    continue;
                }
                String v = before.get(k);
                if (v == null) {
                    changed++; // ключ додав пресет: прибрати, гра поставить своє стандартне
                    continue;
                }
                if (!v.equals(l.substring(c + 1))) changed++;
                out.append(k).append(':').append(v).append('\n');
            }
        }
        if (out.length() == 0) {
            Files.deleteIfExists(file.toPath()); // до пресетів налаштувань не було
        } else {
            File tmp = new File(content, "options.txt.tmp");
            Files.write(tmp.toPath(), out.toString().getBytes(StandardCharsets.UTF_8));
            Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
        Files.deleteIfExists(backup.toPath());
        log.info("FPS preset options restored for " + instance.getName() + ": " + changed + " option(s)");
        return changed;
    }

    /** Порада пресета під цей ПК: пресет і з чого вона (пам'ять, відеокарта). */
    public static final class Advice {
        public final Preset preset;
        public final long ramGb;
        /** Назва відеокарти (найсильнішої з відомих) або null, якщо не вдалось дізнатись. */
        public final String gpu;

        Advice(Preset preset, long ramGb, String gpu) {
            this.preset = preset;
            this.ramGb = ramGb;
            this.gpu = gpu;
        }
    }

    private static volatile Advice advice;

    /**
     * 18.11.2026 (власник: «пресет ФПС за залізом ПК» «теж збс»). Пам'ять ПК і відеокарти з реєстру Windows (клас
     * пристроїв Display). Лише вбудована графіка (Intel HD/UHD/Iris, AMD Radeon Graphics) чи пам'яті менше 8 ГБ:
     * «Слабкий ПК»; окрема відеокарта нового покоління (RTX, RX 6000+, Arc) і від 16 ГБ: «Красиво»; решта
     * «Збалансовано». Рахується раз, не з потоку інтерфейсу (перший виклик читає реєстр).
     */
    /** 24.11.2026: порада, якщо її вже пораховано ({@link #advise()} на старті лаунчера у фоні), інакше null. Без чекання. */
    public static Advice peek() {
        return advice;
    }

    public static Advice advise() {
        Advice a = advice;
        if (a != null) return a;
        long ram = 0;
        try {
            java.lang.management.OperatingSystemMXBean os = java.lang.management.ManagementFactory.getOperatingSystemMXBean();
            if (os instanceof com.sun.management.OperatingSystemMXBean) {
                ram = Math.round(((com.sun.management.OperatingSystemMXBean) os).getTotalPhysicalMemorySize() / (1024.0 * 1024 * 1024));
            }
        } catch (Throwable ignored) {
            // невідомо
        }
        List<String> gpus = new ArrayList<String>();
        if (System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("win")) {
            String cls = "SYSTEM\\CurrentControlSet\\Control\\Class\\{4d36e968-e325-11ce-bfc1-08002be10318}";
            try {
                for (String sub : com.skcraft.launcher.util.WinRegistry.readStringSubKeys(com.sun.jna.platform.win32.WinReg.HKEY_LOCAL_MACHINE, cls)) {
                    if (!sub.matches("\\d{4}")) continue;
                    try {
                        String d = com.skcraft.launcher.util.WinRegistry.readString(com.sun.jna.platform.win32.WinReg.HKEY_LOCAL_MACHINE, cls + "\\" + sub, "DriverDesc");
                        if (d != null && !d.trim().isEmpty() && !gpus.contains(d.trim())) gpus.add(d.trim());
                    } catch (Throwable ignored) {
                        // підключ без опису
                    }
                }
            } catch (Throwable ignored) {
                // реєстр недоступний: лише за пам'яттю
            }
        }
        String best = null;
        int bestTier = -1;
        for (String g : gpus) {
            int t = gpuTier(g);
            if (t > bestTier) {
                bestTier = t;
                best = g;
            }
        }
        Preset p;
        if (ram > 0 && ram < 8 || bestTier == 0) p = Preset.LOW;
        else if (bestTier == 2 && ram >= 16) p = Preset.HIGH;
        else p = Preset.BALANCED;
        a = new Advice(p, ram, best);
        advice = a;
        log.info("FPS advice: " + p + " (RAM " + ram + " GB, GPUs " + gpus + ")");
        return a;
    }

    /** 0 - вбудована графіка, 1 - окрема відеокарта, 2 - окрема нового покоління; -1 - не відеокарта (віртуальна тощо). */
    static int gpuTier(String name) {
        String n = name.toLowerCase(java.util.Locale.ROOT);
        if (n.contains("basic display") || n.contains("remote") || n.contains("virtual") || n.contains("parsec") || n.contains("mirror")) return -1;
        if (n.matches(".*\\b(rtx|arc a\\d|arc b\\d|rx ?[6-9]\\d{3})\\b.*")) return 2;
        if (n.contains("geforce") || n.contains("quadro") || n.matches(".*\\brx ?\\d{3,4}\\b.*") || n.contains("radeon pro") || n.contains("rx vega")
                || n.matches(".*\\br9 \\d{3}.*") || n.contains("firepro")) return 1;
        if (n.contains("intel") || n.contains("radeon") || n.contains("vega")) return 0;
        return 1; // невідома назва: вважаємо окремою, щоб не занижувати
    }

    /** 1.20.1 -> 20; невідомо: 0. */
    static int minorOf(String game) {
        if (game == null) return 0;
        String[] p = game.split("[.]");
        if (p.length < 2 || !"1".equals(p[0])) return 0;
        try {
            return Integer.parseInt(p[1].replaceAll("[^0-9].*$", ""));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    // ---------------------------------------------------------------- моди

    /** Чи можна ставити моди: збірка, де моди веде гравець, з відомим завантажувачем і грою. */
    public static boolean canAddMods(Instance instance) {
        if (instance == null || instance.isVanilla() || InstanceMods.packModsLocked(instance)) return false;
        if (!(instance.isMrpack() || instance.isCopy() || instance.isBuiltIn())) return false;
        String[] l = ModrinthBrowse.loadersOf(ModrinthBrowse.Kind.MOD, instance);
        return l != null && l.length > 0 && ModrinthBrowse.gameVersionOf(instance) != null;
    }

    /** Що вийшло: поставлені файли, пропущене з причиною. */
    public static final class Result {
        public final List<String> installed = new ArrayList<String>();
        public final List<String> skipped = new ArrayList<String>();
    }

    /** Поставити моди для ФПС. Не з потоку інтерфейсу. */
    public static Result installMods(Instance instance) throws InterruptedException {
        Result r = new Result();
        String game = ModrinthBrowse.gameVersionOf(instance);
        String[] loaders = ModrinthBrowse.loadersOf(ModrinthBrowse.Kind.MOD, instance);
        String loader = loaders[0].toLowerCase(Locale.ROOT);
        File dir = InstanceMods.modsDir(instance);
        dir.mkdirs();
        int minor = minorOf(game);
        boolean renderer = false;
        java.util.Set<String> have = new java.util.HashSet<String>();
        for (InstanceMods.Mod m : InstanceMods.list(instance)) {
            String n = m.file.getName().toLowerCase(Locale.ROOT);
            ModInfo mi = ModInfo.of(m.file);
            have.addAll(mi.provides);
            for (String id : new String[]{"sodium", "embeddium", "rubidium", "magnesium", "xenon", "optifine", "optifabric", "nvidium", "celeritas"}) {
                if (mi.provides.contains(id) || n.startsWith(id)) renderer = true;
            }
        }
        List<String> slugs = new ArrayList<String>();
        if (!renderer) {
            if ("forge".equals(loader)) slugs.add("embeddium");
            else {
                slugs.add("sodium");
                // до 1.21 Sodium без Fabric Rendering API: моди, яким він потрібен, без Indium малюються неправильно
                if (("fabric".equals(loader) || "quilt".equals(loader)) && minor > 0 && minor < 21) slugs.add("indium");
            }
        } else {
            r.skipped.add("Sodium / Embeddium: " + com.skcraft.launcher.util.SharedLocale.tr("fps.mods.hasRenderer"));
        }
        slugs.add("ferrite-core");
        slugs.add("entityculling");
        slugs.add("immediatelyfast");
        List<String> added = readAdded(instance);
        for (String slug : slugs) {
            // id мода в jar не завжди як slug на Modrinth (ferrite-core -> ferritecore)
            if (have.contains(slug.replace("-", "")) || have.contains(slug) || ModrinthBrowse.looksInstalled(dir, null, slug)) continue;
            List<String> got = new ArrayList<String>(), conflicts = new ArrayList<String>(), missing = new ArrayList<String>();
            // 26.11.2026: Modrinth, а якщо там немає під цю збірку, той самий мод з CurseForge (ModSources)
            ModSources.Found h;
            try {
                h = ModSources.find(slug, ModrinthBrowse.Kind.MOD, game, loaders);
                if (h == null) throw new IOException(ModrinthBrowse.NO_VERSION);
                if (ModrinthBrowse.looksInstalled(dir, h.hit.id, h.hit.slug)) continue;
                java.util.Set<String> before = names(dir);
                try {
                    ModSources.install(h, ModrinthBrowse.Kind.MOD, game, loaders, dir, null, missing, conflicts);
                } finally {
                    // install дає назви модів, а прибирати треба файли: що з'явилось у теці
                    for (String f : names(dir)) if (!before.contains(f)) got.add(f);
                }
            } catch (IOException e) {
                r.skipped.add(slug + ": " + com.skcraft.launcher.util.SharedLocale.tr("fps.mods.noVersion"));
                continue;
            }
            if (!conflicts.isEmpty() || !missing.isEmpty()) {
                // несумісний з наявним модом чи без залежності: краще без нього, ніж зламана збірка
                for (String f : got) new File(dir, f).delete();
                r.skipped.add(h.title() + ": " + (conflicts.isEmpty() ? com.skcraft.launcher.util.SharedLocale.tr("fps.mods.noDeps")
                        : com.skcraft.launcher.util.SharedLocale.tr("fps.mods.conflict", String.join(", ", conflicts))));
                continue;
            }
            r.installed.addAll(got);
            added.addAll(got);
        }
        writeAdded(instance, added);
        log.info("FPS mods for " + instance.getName() + ": " + r.installed + ", skipped " + r.skipped);
        return r;
    }

    private static java.util.Set<String> names(File dir) {
        java.util.Set<String> out = new java.util.HashSet<String>();
        String[] n = dir.list();
        if (n != null) out.addAll(java.util.Arrays.asList(n));
        return out;
    }

    /** Файли, які поставив пресет і які ще лежать у теці (увімкнені чи вимкнені). */
    public static List<String> addedMods(Instance instance) {
        List<String> out = new ArrayList<String>();
        File dir = InstanceMods.modsDir(instance);
        for (String f : readAdded(instance)) {
            if (new File(dir, f).isFile() || new File(dir, f + InstanceMods.DISABLED).isFile()) out.add(f);
        }
        return out;
    }

    /** Прибрати поставлене пресетом (лише ці файли). Повертає скільки прибрано. */
    public static int removeMods(Instance instance) throws IOException {
        File dir = InstanceMods.modsDir(instance);
        int n = 0;
        for (String f : readAdded(instance)) {
            if (f.contains("/") || f.contains("\\") || f.contains("..")) continue;
            for (File x : new File[]{new File(dir, f), new File(dir, f + InstanceMods.DISABLED)}) {
                if (x.isFile() && x.delete()) n++;
            }
        }
        writeAdded(instance, new ArrayList<String>());
        return n;
    }

    private static List<String> readAdded(Instance instance) {
        List<String> out = new ArrayList<String>();
        File f = new File(instance.getDir(), ADDED);
        if (!f.isFile()) return out;
        try {
            for (com.fasterxml.jackson.databind.JsonNode n : MAPPER.readTree(f).path("files")) out.add(n.asText());
        } catch (IOException ignored) {
            // зіпсований файл: як порожній
        }
        return out;
    }

    private static void writeAdded(Instance instance, List<String> files) {
        try {
            ObjectNode root = MAPPER.createObjectNode();
            ArrayNode a = root.putArray("files");
            for (String s : new java.util.LinkedHashSet<String>(files)) a.add(s);
            MAPPER.writerWithDefaultPrettyPrinter().writeValue(new File(instance.getDir(), ADDED), root);
        } catch (IOException e) {
            log.warning("Could not write " + ADDED + ": " + e.getMessage());
        }
    }
}
