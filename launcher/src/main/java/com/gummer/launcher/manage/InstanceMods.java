/*
 * Copyright (C) 2021-2026 GummerCraft <http://gummercraft.fun>
 */

package com.gummer.launcher.manage;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skcraft.launcher.Instance;
import com.skcraft.launcher.install.FeatureCache;
import com.skcraft.launcher.persistence.Persistence;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 17.11.2026: моди збірки для вікна «Керування збіркою». Вимкнений мод це {@code назва.jar.disabled} (так роблять
 * Prism і MultiMC; завантажувачі беруть лише .jar і .zip). Правила:
 * <ul>
 * <li>Vanilla, Fabric, Quilt, Forge, NeoForge і копії: усі моди поклав гравець, вмикаються і вимикаються вільно.</li>
 * <li>Модпак з CDN (GummerCraft): моди паку (є в {@code install_log.json}) теж вимикаються (вирішено 17.11: «На
 * збірках гаммеркрафту можна вимикати»). Оновлення паку бачить, що файлу немає, і качає його знову, тому вимкнені моди
 * паку записуються в {@code disabled-mods.json} теки збірки, і перед кожним запуском гри {@link #reapplyDisabled}
 * вимикає їх знову (і нову версію того самого мода, за modId).</li>
 * <li>StalDayZ: моди паку заблоковані. Сервер з ModWhiteList і реєстрами Forge не пустив би гравця без них.
 * Вільно вмикаються лише моди, які гравець доклав сам, і необов'язкові моди паку («features» у modpack.json,
 * механізм SKCraft): вибір пишеться в {@code features.json}, а файли ставить і прибирає оновлення паку
 * при наступному запуску.</li>
 * </ul>
 */
public final class InstanceMods {

    public static final String DISABLED = ".disabled";

    /** Звідки мод. */
    public enum Source {
        /** Поклав гравець: вмикається і вимикається перейменуванням. */
        OWN,
        /** Частина модпаку (вимикається, крім StalDayZ, див. {@link Mod#locked}). */
        PACK
    }

    /** Мод на диску. */
    public static final class Mod {
        public final File file;
        public final String name;
        public final boolean enabled;
        public final Source source;
        /** Вимкнути не можна: мод паку, без якого сервер не пустить (StalDayZ). */
        public final boolean locked;
        public final long size;

        Mod(File file, String name, boolean enabled, Source source, boolean locked) {
            this.file = file;
            this.name = name;
            this.enabled = enabled;
            this.source = source;
            this.locked = locked;
            this.size = file.length();
        }
    }

    /** Необов'язковий мод (чи набір модів) модпаку. */
    public static final class Optional {
        public final String name;
        public final String description;
        public final boolean selected;
        public final List<String> files;

        Optional(String name, String description, boolean selected, List<String> files) {
            this.name = name;
            this.description = description;
            this.selected = selected;
            this.files = files;
        }
    }

    private static final ObjectMapper MAPPER = com.gummer.launcher.util.Json.newMapper();

    private InstanceMods() {
    }

    public static File modsDir(Instance instance) {
        return new File(instance.getContentDir(), "mods");
    }

    /**
     * Тека mods без створення тек (17.11.2026 ~23:15): {@link #modsDir} через getContentDir створює теку збірки, і меню
     * «Встановити мод у збірку» на сторінці Modrinth, лише перевіряючи «вже є», створювало порожні теки ще не встановлених
     * версій (Forge 1.20.4, 1.20 ...). Лаунчер вважає будь-яку теку збіркою, і в «Встановлених» з'являлись збірки з жовтим
     * «(потрібне оновлення)». Для читання брати це.
     */
    public static File peekModsDir(Instance instance) {
        return new File(new File(instance.peekDir(), "minecraft"), "mods");
    }

    /** Чи це файл мода (увімкнений чи вимкнений). */
    public static boolean isModFile(File f) {
        if (!f.isFile()) return false;
        String n = f.getName().toLowerCase(Locale.ROOT);
        if (n.endsWith(DISABLED)) n = n.substring(0, n.length() - DISABLED.length());
        return n.endsWith(".jar") || n.endsWith(".zip") || n.endsWith(".litemod");
    }

    /**
     * Чи заблоковані моди самого паку. Лише StalDayZ: його сервер звіряє моди гравця (ModWhiteList, реєстри Forge).
     * Сервери GummerCraft (Main) клієнтські моди не звіряють, тож там вимикати можна.
     */
    public static boolean packModsLocked(Instance instance) {
        if (instance == null || instance.isBuiltIn()) return false;
        String n = instance.getKindName().toLowerCase(Locale.ROOT);
        return n.contains("staldayz") || n.contains("dayz");
    }

    /** Моди в теці mods (без підтек: туди Forge кладе бібліотеки за версією гри), за назвою. */
    public static List<Mod> list(Instance instance) {
        List<Mod> out = new ArrayList<Mod>();
        File[] files = modsDir(instance).listFiles();
        if (files == null) return out;
        Set<String> pack = instance.isBuiltIn() ? Collections.<String>emptySet() : packFiles(instance);
        boolean lock = packModsLocked(instance);
        for (File f : files) {
            if (!isModFile(f)) continue;
            boolean enabled = !f.getName().toLowerCase(Locale.ROOT).endsWith(DISABLED);
            String name = enabled ? f.getName() : f.getName().substring(0, f.getName().length() - DISABLED.length());
            boolean fromPack = pack.contains(("mods/" + name).toLowerCase(Locale.ROOT));
            out.add(new Mod(f, name, enabled, fromPack ? Source.PACK : Source.OWN, fromPack && lock));
        }
        Collections.sort(out, (a, b) -> a.name.compareToIgnoreCase(b.name));
        return out;
    }

    /**
     * Увімкнути чи вимкнути мод. Заблокований мод паку (StalDayZ) не чіпається (IOException). Повертає новий файл. Якщо
     * поруч уже є файл з такою назвою (той самий мод і увімкнений, і вимкнений), не перезаписує його. Мод паку
     * записується в disabled-mods.json (вимкнено) або прибирається звідти (увімкнено).
     */
    public static File setEnabled(Instance instance, Mod mod, boolean enabled) throws IOException {
        if (mod.locked) throw new IOException("Mods of this modpack cannot be disabled: " + mod.name);
        if (mod.enabled == enabled) return mod.file;
        File target = new File(mod.file.getParentFile(), enabled ? mod.name : mod.name + DISABLED);
        if (target.exists()) throw new IOException("File already exists: " + target.getName());
        String id = mod.source == Source.PACK ? ModInfo.idOf(mod.file) : null;
        if (!mod.file.renameTo(target)) {
            throw new IOException("Cannot rename " + mod.file.getName() + " (is the game running?)");
        }
        if (mod.source == Source.PACK) {
            Disabled d = Disabled.read(instance);
            if (enabled) {
                d.files.remove(mod.name);
                if (id != null) d.ids.remove(id);
            } else {
                d.files.add(mod.name);
                if (id != null) d.ids.add(id);
            }
            d.write(instance);
        }
        return target;
    }

    /** Вимкнені гравцем моди паку: назви файлів і modId (нова версія мода в паку має іншу назву файлу). */
    static final class Disabled {
        final Set<String> files = new java.util.TreeSet<String>();
        final Set<String> ids = new java.util.TreeSet<String>();
        long checked;

        static File file(Instance instance) {
            return new File(instance.getDir(), "disabled-mods.json");
        }

        static Disabled read(Instance instance) {
            Disabled d = new Disabled();
            File f = file(instance);
            if (!f.isFile()) return d;
            try {
                JsonNode n = MAPPER.readTree(f);
                for (JsonNode x : n.path("files")) d.files.add(x.asText());
                for (JsonNode x : n.path("ids")) d.ids.add(x.asText());
                d.checked = n.path("checked").asLong(0);
            } catch (IOException | RuntimeException ignored) {
                // зіпсований файл: нічого не вимикаємо самі (гравець побачить мод увімкненим і вимкне знову)
            }
            return d;
        }

        void write(Instance instance) throws IOException {
            File f = file(instance);
            if (files.isEmpty() && ids.isEmpty()) {
                if (f.isFile() && !f.delete()) throw new IOException("Cannot delete " + f);
                return;
            }
            Map<String, Object> m = new LinkedHashMap<String, Object>();
            m.put("files", new ArrayList<String>(files));
            m.put("ids", new ArrayList<String>(ids));
            m.put("checked", checked);
            File tmp = new File(f.getPath() + ".tmp");
            MAPPER.writerWithDefaultPrettyPrinter().writeValue(tmp, m);
            java.nio.file.Files.move(tmp.toPath(), f.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /**
     * Перед запуском гри: вимкнути знову моди паку, які гравець вимкнув, а оновлення паку поставило знову (той самий
     * файл або нова версія того самого мода). Стара вимкнена копія, якої вже немає в паку, прибирається: так само
     * її прибрало б оновлення паку, якби вона не була перейменована. Свої моди гравця не чіпаються ніколи.
     * Помилки не зупиняють запуск: у найгіршому разі мод запуститься увімкненим.
     */
    public static void reapplyDisabled(Instance instance) {
        if (instance == null || instance.isBuiltIn() || packModsLocked(instance)) return;
        if (!Disabled.file(instance).isFile()) return;
        try {
            Disabled d = Disabled.read(instance);
            if (d.files.isEmpty() && d.ids.isEmpty()) return;
            File dir = modsDir(instance);
            File[] files = dir.listFiles();
            if (files == null) return;
            File log = new File(instance.getDir(), "install_log.json");
            // modId читаємо з jar лише після оновлення паку (журнал новіший за минулу перевірку): інакше лише назви
            boolean updated = log.lastModified() > d.checked;
            Set<String> pack = updated ? packFiles(instance) : Collections.<String>emptySet();
            Set<String> newlyDisabledIds = new HashSet<String>();
            for (File f : files) {
                if (!isModFile(f) || f.getName().toLowerCase(Locale.ROOT).endsWith(DISABLED)) continue;
                String name = f.getName();
                boolean byName = d.files.contains(name);
                boolean packFile = pack.contains(("mods/" + name).toLowerCase(Locale.ROOT));
                // за modId лише файли паку: свій мод гравця з тим самим id не чіпаємо
                String id = !byName && packFile && !d.ids.isEmpty() ? ModInfo.idOf(f) : null;
                if (!byName && (id == null || !d.ids.contains(id))) continue;
                File target = new File(dir, name + DISABLED);
                if (target.exists() && !target.delete()) continue; // стара копія того самого файлу: свіжа з паку новіша
                if (f.renameTo(target)) {
                    d.files.add(name);
                    if (id != null) newlyDisabledIds.add(id);
                }
            }
            if (updated) {
                // стара версія мода, вимкнена раніше: її вже немає в паку, а нову версію щойно вимкнено
                for (File f : files) {
                    String n = f.getName();
                    if (!f.isFile() || !n.toLowerCase(Locale.ROOT).endsWith(DISABLED)) continue;
                    String base = n.substring(0, n.length() - DISABLED.length());
                    if (!d.files.contains(base) || pack.contains(("mods/" + base).toLowerCase(Locale.ROOT))) continue;
                    if (newlyDisabledIds.contains(ModInfo.idOf(f)) && f.delete()) d.files.remove(base);
                }
                d.checked = System.currentTimeMillis();
            }
            d.write(instance);
        } catch (IOException | RuntimeException e) {
            java.util.logging.Logger.getLogger(InstanceMods.class.getName()).log(java.util.logging.Level.WARNING,
                    "Could not re-disable mods of " + instance.getName(), e);
        }
    }

    /** Шляхи файлів, які поставив модпак (з install_log.json), малими літерами, з «/». */
    static Set<String> packFiles(Instance instance) {
        Set<String> out = new HashSet<String>();
        File log = new File(instance.getDir(), "install_log.json");
        if (!log.isFile()) return out;
        try {
            JsonNode entries = MAPPER.readTree(log).path("entries");
            java.util.Iterator<JsonNode> groups = entries.elements();
            while (groups.hasNext()) {
                for (JsonNode p : groups.next()) {
                    out.add(p.asText("").replace('\\', '/').toLowerCase(Locale.ROOT));
                }
            }
        } catch (IOException | RuntimeException ignored) {
            // зіпсований журнал: які моди з паку, невідомо, тож краще заблокувати всі, ніж дати зламати пак
            return allMods(instance);
        }
        return out;
    }

    /** Якщо журналу паку не прочитати: усі моди теки вважаються модами паку (вимикати нічого не можна). */
    private static Set<String> allMods(Instance instance) {
        Set<String> out = new HashSet<String>();
        File[] files = modsDir(instance).listFiles();
        if (files != null) {
            for (File f : files) {
                String n = f.getName();
                if (n.toLowerCase(Locale.ROOT).endsWith(DISABLED)) n = n.substring(0, n.length() - DISABLED.length());
                out.add(("mods/" + n).toLowerCase(Locale.ROOT));
            }
        }
        return out;
    }

    /**
     * Необов'язкові моди модпаку з його маніфесту (manifest.json, який зберігає оновлення паку). Для збірок без
     * маніфесту з features порожньо. Вибір: з features.json, інакше як задано в паку.
     */
    public static List<Optional> optionals(Instance instance) {
        List<Optional> out = new ArrayList<Optional>();
        if (instance.isBuiltIn()) return out;
        File manifest = instance.getManifestPath();
        if (!manifest.isFile()) return out;
        try {
            JsonNode m = MAPPER.readTree(manifest);
            Map<String, List<String>> filesOf = new LinkedHashMap<String, List<String>>();
            for (JsonNode task : m.path("tasks")) {
                JsonNode when = task.path("when");
                if (when.isMissingNode() || when.isNull()) continue;
                for (JsonNode f : when.path("features")) {
                    String fname = f.isTextual() ? f.asText() : f.path("name").asText(null);
                    if (fname == null) continue;
                    List<String> list = filesOf.get(fname);
                    if (list == null) filesOf.put(fname, list = new ArrayList<String>());
                    String to = task.path("to").asText(null);
                    if (to != null) list.add(to);
                }
            }
            FeatureCache cache = Persistence.read(new File(instance.getDir(), "features.json"), FeatureCache.class);
            for (JsonNode f : m.path("features")) {
                String name = f.path("name").asText(null);
                if (name == null) continue;
                Boolean chosen = cache != null && cache.getSelected() != null ? cache.getSelected().get(name) : null;
                boolean selected = chosen != null ? chosen : f.path("selected").asBoolean(false);
                List<String> files = filesOf.get(name);
                out.add(new Optional(name, f.path("description").asText(""), selected,
                        files != null ? files : Collections.<String>emptyList()));
            }
        } catch (IOException | RuntimeException e) {
            return out;
        }
        return out;
    }

    /**
     * Вибрати чи зняти необов'язковий мод паку: запис у features.json і позначка «потрібне оновлення». Файли поставить
     * чи прибере оновлення паку при наступному запуску (BaseUpdater порівнює журнал встановлених файлів).
     */
    public static void setOptional(Instance instance, String feature, boolean selected) throws IOException {
        File file = new File(instance.getDir(), "features.json");
        FeatureCache cache = file.isFile() ? Persistence.read(file, FeatureCache.class) : new FeatureCache();
        if (cache.getSelected() == null) cache.setSelected(new java.util.HashMap<String, Boolean>());
        cache.getSelected().put(feature, selected);
        Persistence.write(file, cache);
        instance.setUpdatePending(true);
        Persistence.commitAndForget(instance);
    }
}
