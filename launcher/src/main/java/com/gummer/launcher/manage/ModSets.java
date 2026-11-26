/*
 * Copyright (C) 2021-2026 GummerCraft <http://gummercraft.fun>
 */

package com.gummer.launcher.manage;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.gummer.launcher.util.Json;
import com.skcraft.launcher.Instance;
import com.skcraft.launcher.util.HttpRequest;
import com.skcraft.launcher.util.SharedLocale;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

/**
 * 26.11.2026 (власник: «Набори модів» у «Керуванні збіркою», «робимо вже», ТЗ «Лаунчер - робоча/НАБОРИ-МОДІВ.md»):
 * готові набори модів для однієї збірки: «Зручність», «Мапа», «Шейдери», «Атмосфера і звук» («ФПС» і «Гра з другом»
 * мають свій код, {@link FpsPresets} і {@link FriendPlay}).
 * <ul>
 * <li>Список у {@code mod-sets.json}: свіжий з CDN ({@code modSetsUrl}), запасний у jar, тож мод у наборі міняється
 * без релізу лаунчера.</li>
 * <li>Набір складається з груп. Група ставить перший варіант із {@code pick}, у якому кожен slug є під цю гру і
 * завантажувач: на Modrinth, а якщо там немає, той самий мод на CurseForge ({@link ModSources}).</li>
 * <li>Аналоги не дублюються: якщо в збірці вже є мод з {@code same} (JEI, REI, EMI ...), група «вже є».</li>
 * <li>Варіант, що порушує {@code exclusive} (два рушії, Iris і Oculus) чи OptiFine ({@code notWithOptifine}),
 * пропускається.</li>
 * <li>Поставлене записується в {@value #ADDED} теки збірки: «Прибрати набір» прибирає саме ці файли. Група, де модрінт
 * чи CurseForge кажуть про несумісність з наявним модом чи брак залежності, відкочується повністю.</li>
 * <li>«Шейдери» ще ставлять один шейдерпак під ПК ({@link FpsPresets#advise()}) і вмикають його.</li>
 * </ul>
 */
public final class ModSets {

    private static final Logger log = Logger.getLogger(ModSets.class.getName());
    private static final ObjectMapper MAPPER = Json.newMapper();
    /** Що поставили набори (тека збірки): набір -> шляхи відносно теки гри (mods/..., shaderpacks/...). */
    public static final String ADDED = "mod-sets.json";
    /** Набори з наборів у json, у порядку карток. */
    public static final String COMFORT = "comfort", MAP = "map", SHADERS = "shaders", AMBIENCE = "ambience";

    private static volatile JsonNode config;
    private static volatile String cdnUrl;

    private ModSets() {
    }

    /** Адреса свіжого списку на CDN (з launcher.properties). Викликає лаунчер на старті. */
    public static void useUrl(String url) {
        cdnUrl = url == null || url.trim().isEmpty() ? null : url.trim();
    }

    /** Список наборів: з CDN (раз за запуск), інакше копія з jar. Не з потоку інтерфейсу. */
    static JsonNode config() {
        JsonNode c = config;
        if (c != null) return c;
        synchronized (ModSets.class) {
            if (config != null) return config;
            JsonNode jar = null;
            try (InputStream in = ModSets.class.getResourceAsStream("/com/gummer/launcher/manage/mod-sets.json")) {
                if (in != null) jar = MAPPER.readTree(in);
            } catch (IOException e) {
                log.warning("mod-sets.json in jar is broken: " + e);
            }
            JsonNode cdn = null;
            if (cdnUrl != null) {
                try {
                    cdn = MAPPER.readTree(HttpRequest.get(new URL(cdnUrl)).execute().expectResponseCode(200).returnContent().asString("UTF-8"));
                    if (cdn.path("version").asInt(0) != 1 || !cdn.path("sets").isArray()) cdn = null;
                } catch (Exception e) {
                    log.info("mod-sets.json from CDN unavailable, using the copy in jar: " + e.getMessage());
                }
            }
            config = cdn != null ? cdn : jar != null ? jar : MAPPER.createObjectNode();
            return config;
        }
    }

    /** Для проб: інший список замість CDN і jar. */
    static void useConfig(JsonNode c) {
        config = c;
    }

    /** Стан групи для цієї збірки. */
    public enum State {
        /** у збірці вже є такий мод чи аналог */
        HAVE,
        /** можна поставити */
        READY,
        /** під цю гру і завантажувач немає ні на Modrinth, ні на CurseForge */
        NONE
    }

    public static final class Group {
        public final String id;
        public State state = State.NONE;
        /** назва наявного аналога (HAVE) */
        public String have;
        /** що ставити (READY): знайдене, без того, що вже є */
        public final List<ModSources.Found> install = new ArrayList<ModSources.Found>();

        Group(String id) {
            this.id = id;
        }

        /** Назви модів групи для картки. */
        public String titles() {
            if (state == State.HAVE) return have;
            List<String> t = new ArrayList<String>();
            for (ModSources.Found f : install) t.add(f.title() + (f.curse ? " (CurseForge)" : ""));
            return String.join(", ", t);
        }
    }

    /** План набору для збірки. */
    public static final class Plan {
        public final String id;
        public final List<Group> groups = new ArrayList<Group>();
        /** «Шейдери»: вибраний під ПК шейдерпак (null: немає під цю гру чи вже є такий) */
        public ModSources.Found pack;
        /** «Шейдери»: пресет ПК, за яким вибрано пак */
        public FpsPresets.Preset preset;
        /** у збірці є OptiFine */
        public boolean optifine;
        /** Мережа недоступна: план неповний. */
        public boolean offline;

        Plan(String id) {
            this.id = id;
        }

        public int count(State s) {
            int n = 0;
            for (Group g : groups) if (g.state == s) n++;
            return n;
        }

        public Group group(String gid) {
            for (Group g : groups) if (g.id.equals(gid)) return g;
            return null;
        }
    }

    /** Чи можна ставити набори в цю збірку (свої моди гравця, не GummerCraft і не StalDayZ). */
    public static boolean available(Instance instance) {
        return FriendPlay.availability(instance) == FriendPlay.Availability.OK && FpsPresets.canAddMods(instance);
    }

    /** Порахувати план (мережа: Modrinth, CurseForge). Не з потоку інтерфейсу. */
    public static Plan plan(Instance instance, String setId) throws InterruptedException {
        Plan p = new Plan(setId);
        JsonNode set = setOf(setId);
        if (set == null) return p;
        String game = ModrinthBrowse.gameVersionOf(instance);
        String[] loaders = ModrinthBrowse.loadersOf(ModrinthBrowse.Kind.MOD, instance);
        Map<String, String> have = installedIds(instance);
        p.optifine = have.containsKey("optifine");
        Set<String> notOf = strings(config().path("notWithOptifine"));
        for (JsonNode g : set.path("groups")) {
            Group gr = new Group(g.path("id").asText(""));
            p.groups.add(gr);
            for (String id : strings(g.path("same"))) {
                if (have.containsKey(id)) {
                    gr.state = State.HAVE;
                    gr.have = have.get(id);
                    break;
                }
            }
            if (gr.state == State.HAVE || loaders == null || game == null) continue;
            for (JsonNode option : g.path("pick")) {
                List<String> slugs = new ArrayList<String>();
                for (JsonNode s : option.isArray() ? option : MAPPER.createArrayNode().add(option)) slugs.add(s.asText());
                if (!allowed(slugs, have.keySet(), p.optifine, notOf)) continue;
                List<ModSources.Found> got = new ArrayList<ModSources.Found>();
                boolean ok = true;
                for (String slug : slugs) {
                    if (has(have.keySet(), slug)) continue; // ця частина варіанту вже є (Embeddium до Oculus)
                    ModSources.Found f = ModSources.find(slug, ModrinthBrowse.Kind.MOD, game, loaders);
                    if (f == null) {
                        ok = false;
                        break;
                    }
                    got.add(f);
                }
                if (ok) {
                    gr.state = got.isEmpty() ? State.HAVE : State.READY;
                    gr.install.addAll(got);
                    if (got.isEmpty()) gr.have = String.join(", ", slugs);
                    break;
                }
            }
        }
        JsonNode packs = set.path("packs");
        // шейдерпак без шейдерного мода не ввімкнеться: пропонуємо його, лише якщо мод є чи буде
        boolean modOk = false;
        for (Group g : p.groups) modOk |= g.state != State.NONE;
        if (packs.isObject() && modOk) {
            p.preset = FpsPresets.advise().preset;
            String[] sl = ModrinthBrowse.shaderLoadersOf(instance);
            List<String> tries = new ArrayList<String>(strings(packs.path(p.preset.name())));
            tries.addAll(strings(packs.path("fallback")));
            File dir = new File(instance.getContentDir(), "shaderpacks");
            for (String slug : tries) {
                ModSources.Found f = ModSources.find(slug, ModrinthBrowse.Kind.SHADER, game, sl);
                if (f == null) continue;
                if (!ModrinthBrowse.looksInstalled(dir, f.hit.id, f.hit.slug)) p.pack = f;
                break;
            }
        }
        log.info("Mod set " + setId + " for " + instance.getName() + ": ready " + p.count(State.READY) + ", have " + p.count(State.HAVE)
                + ", none " + p.count(State.NONE) + (p.pack != null ? ", pack " + p.pack.title() : ""));
        return p;
    }

    /** Варіант не ставить другий рушій чи шейдерний мод і не йде проти OptiFine. */
    private static boolean allowed(List<String> slugs, Set<String> have, boolean optifine, Set<String> notWithOptifine) {
        for (String slug : slugs) {
            String id = slug.replace("-", "");
            if (optifine && (notWithOptifine.contains(slug) || notWithOptifine.contains(id))) return false;
            for (JsonNode ex : config().path("exclusive")) {
                Set<String> e = strings(ex);
                if (!e.contains(slug) && !e.contains(id)) continue;
                for (String other : e) {
                    if (!other.equals(slug) && !other.equals(id) && have.contains(other)) return false;
                }
            }
        }
        return true;
    }

    private static boolean has(Set<String> have, String slug) {
        return have.contains(slug) || have.contains(slug.replace("-", "")) || have.contains(slug.replace('-', '_'));
    }

    /** Шейдерний мод, що вже стоїть (Iris, Oculus, OptiFine), або null. Без мережі, не з потоку інтерфейсу. */
    public static String shaderModIn(Instance instance) {
        Map<String, String> have = installedIds(instance);
        for (String id : new String[]{"iris", "oculus", "optifine", "optifabric"}) if (have.containsKey(id)) return have.get(id);
        return null;
    }

    /** Що вийшло. */
    public static final class Result {
        public final List<String> installed = new ArrayList<String>();
        public final List<String> skipped = new ArrayList<String>();
        /** «Шейдери»: назва поставленого і ввімкненого шейдерпака */
        public String pack;
    }

    /**
     * Поставити групи {@code groupIds} з плану (null: усі READY) і, якщо {@code withPack}, шейдерпак. Не з потоку
     * інтерфейсу. Гра має бути закрита (конфіг шейдерів).
     */
    public static Result install(Instance instance, Plan plan, Set<String> groupIds, boolean withPack) throws InterruptedException {
        Result r = new Result();
        String game = ModrinthBrowse.gameVersionOf(instance);
        String[] loaders = ModrinthBrowse.loadersOf(ModrinthBrowse.Kind.MOD, instance);
        File content = instance.getContentDir();
        File dir = InstanceMods.modsDir(instance);
        dir.mkdirs();
        List<String> added = readAdded(instance, plan.id);
        for (Group g : plan.groups) {
            if (g.state != State.READY || groupIds != null && !groupIds.contains(g.id)) continue;
            Set<String> before = names(dir);
            List<String> missing = new ArrayList<String>(), conflicts = new ArrayList<String>(), titles = new ArrayList<String>();
            String failed = null;
            for (ModSources.Found f : g.install) {
                try {
                    if (ModrinthBrowse.looksInstalled(dir, f.hit.id, f.hit.slug)) continue;
                    ModSources.install(f, ModrinthBrowse.Kind.MOD, game, loaders, dir, null, missing, conflicts);
                    titles.add(f.title());
                } catch (IOException e) {
                    failed = f.title() + ": " + (com.gummer.launcher.curseforge.CurseBrowse.NOT_DOWNLOADED.equals(e.getMessage())
                            ? SharedLocale.tr("sets.notDownloaded") : SharedLocale.tr("fps.mods.noVersion"));
                    break;
                }
            }
            List<String> got = new ArrayList<String>();
            for (String f : names(dir)) if (!before.contains(f)) got.add(f);
            if (failed == null && (!conflicts.isEmpty() || !missing.isEmpty())) {
                failed = String.join(", ", titles) + ": " + (conflicts.isEmpty() ? SharedLocale.tr("fps.mods.noDeps")
                        : SharedLocale.tr("fps.mods.conflict", String.join(", ", conflicts)));
            }
            if (failed != null) {
                // група цілком або ніяк: Iris без Sodium чи мапа без світу гірше, ніж нічого
                for (String f : got) new File(dir, f).delete();
                r.skipped.add(failed);
                continue;
            }
            for (String f : got) {
                added.add(dir.getName() + "/" + f);
                r.installed.add(f);
            }
        }
        if (withPack && plan.pack != null) {
            File packs = new File(content, "shaderpacks");
            packs.mkdirs();
            try {
                String[] sl = ModrinthBrowse.shaderLoadersOf(instance);
                String file = ModSources.install(plan.pack, ModrinthBrowse.Kind.SHADER, game, sl, packs, null, null, null);
                added.add("shaderpacks/" + file);
                r.installed.add(file);
                r.pack = plan.pack.title();
                enableShaderPack(instance, file);
            } catch (IOException e) {
                r.skipped.add(plan.pack.title() + ": " + SharedLocale.tr("fps.mods.noVersion"));
            }
        }
        writeAdded(instance, plan.id, added);
        log.info("Mod set " + plan.id + " for " + instance.getName() + ": installed " + r.installed + ", skipped " + r.skipped);
        return r;
    }

    /**
     * Увімкнути шейдерпак, щоб гравець побачив шейдери одразу після запуску, без пошуку в меню. Iris і Oculus:
     * {@code config/iris.properties} / {@code config/oculus.properties} ({@code shaderPack}, {@code enableShaders}),
     * OptiFine: {@code optionsshaders.txt}. Якщо гравець уже вибрав свій пак, його вибір лишається.
     */
    static void enableShaderPack(Instance instance, String file) {
        Map<String, String> have = installedIds(instance);
        File content = instance.getContentDir();
        try {
            if (have.containsKey("optifine")) {
                setKeys(new File(content, "optionsshaders.txt"), file, false);
            } else {
                String name = have.containsKey("oculus") ? "oculus.properties" : "iris.properties";
                setKeys(new File(new File(content, "config"), name), file, true);
            }
        } catch (IOException e) {
            log.warning("Could not enable shader pack " + file + ": " + e);
        }
    }

    private static void setKeys(File f, String pack, boolean iris) throws IOException {
        List<String> lines = f.isFile() ? new ArrayList<String>(Files.readAllLines(f.toPath(), StandardCharsets.ISO_8859_1)) : new ArrayList<String>();
        String current = null;
        for (String l : lines) if (l.startsWith("shaderPack=")) current = l.substring("shaderPack=".length()).trim();
        if (current != null && !current.isEmpty() && !current.equalsIgnoreCase("OFF") && !current.equals("(internal)")) {
            log.info("Shader pack already chosen in " + f.getName() + ": " + current + ", not changed");
            return;
        }
        Map<String, String> want = new LinkedHashMap<String, String>();
        // properties: пробіли і : у назві файлу екрануються
        want.put("shaderPack", iris ? pack.replace("\\", "\\\\").replace(":", "\\:").replace(" ", "\\ ") : pack);
        if (iris) want.put("enableShaders", "true");
        for (int i = 0; i < lines.size(); i++) {
            String l = lines.get(i);
            int eq = l.indexOf('=');
            if (eq <= 0) continue;
            String k = l.substring(0, eq).trim();
            if (want.containsKey(k)) lines.set(i, k + "=" + want.remove(k));
        }
        for (Map.Entry<String, String> e : want.entrySet()) lines.add(e.getKey() + "=" + e.getValue());
        f.getParentFile().mkdirs();
        Files.write(f.toPath(), lines, StandardCharsets.ISO_8859_1);
    }

    /** Файли, які поставив набір і які ще лежать на місці (увімкнені чи вимкнені). */
    public static List<String> added(Instance instance, String setId) {
        List<String> out = new ArrayList<String>();
        File content = instance.getContentDir();
        for (String f : readAdded(instance, setId)) {
            File x = resolve(instance, content, f);
            if (x != null && (x.exists() || new File(x.getPath() + InstanceMods.DISABLED).isFile())) out.add(f);
        }
        return out;
    }

    /** Прибрати поставлене набором (лише ці файли). Повертає скільки прибрано. */
    public static int remove(Instance instance, String setId) throws IOException {
        File content = instance.getContentDir();
        int n = 0;
        List<String> mine = readAdded(instance, setId);
        // бібліотека (Fabric API, Kotlin, CreativeCore), яку набір поставив як залежність, а тепер вона потрібна й іншим
        // модам збірки, лишається: інакше «Прибрати набір» ламав би чужі моди
        Set<String> needed = new HashSet<String>();
        Set<String> files = new HashSet<String>();
        for (String f : mine) files.add(f.substring(f.indexOf('/') + 1));
        for (InstanceMods.Mod m : InstanceMods.list(instance)) {
            String name = m.file.getName().replaceAll(java.util.regex.Pattern.quote(InstanceMods.DISABLED) + "$", "");
            if (files.contains(name) || !m.enabled) continue;
            needed.addAll(ModInfo.of(m.file).depends);
        }
        List<String> kept = new ArrayList<String>();
        for (String f : mine) {
            File x = resolve(instance, content, f);
            if (x == null) continue;
            if (x.isFile() && f.startsWith(InstanceMods.modsDir(instance).getName() + "/")) {
                Set<String> prov = new HashSet<String>(ModInfo.of(x).provides);
                prov.retainAll(needed);
                if (!prov.isEmpty()) {
                    kept.add(f);
                    continue;
                }
            }
            for (File y : new File[]{x, new File(x.getPath() + InstanceMods.DISABLED)}) {
                if (y.isFile() && y.delete()) n++;
            }
        }
        writeAdded(instance, setId, kept);
        log.info("Mod set " + setId + " removed from " + instance.getName() + ": " + n + " file(s), kept as needed by other mods " + kept);
        return n;
    }

    /** Лише mods/x і shaderpacks/x без виходу з теки. */
    private static File resolve(Instance instance, File content, String rel) {
        if (rel.contains("..") || rel.contains("\\")) return null;
        int s = rel.indexOf('/');
        if (s <= 0 || rel.indexOf('/', s + 1) >= 0) return null;
        String folder = rel.substring(0, s), name = rel.substring(s + 1);
        if (folder.equals("shaderpacks")) return new File(new File(content, "shaderpacks"), name);
        File mods = InstanceMods.modsDir(instance);
        return folder.equals(mods.getName()) ? new File(mods, name) : null;
    }

    // ------------------------------------------------------------------ допоміжне

    static JsonNode setOf(String id) {
        for (JsonNode s : config().path("sets")) if (id.equals(s.path("id").asText())) return s;
        return null;
    }

    /** Групи набору (id) у порядку json, без мережі. */
    public static List<String> groupIds(String setId) {
        List<String> out = new ArrayList<String>();
        JsonNode s = setOf(setId);
        if (s != null) for (JsonNode g : s.path("groups")) out.add(g.path("id").asText(""));
        return out;
    }

    /** Id модів, що стоять і ввімкнені (з jar і з назви файлу), -> назва мода. */
    static Map<String, String> installedIds(Instance instance) {
        Map<String, String> have = new LinkedHashMap<String, String>();
        for (InstanceMods.Mod m : InstanceMods.list(instance)) {
            if (m.file.getName().endsWith(InstanceMods.DISABLED)) continue;
            ModInfo mi = ModInfo.of(m.file);
            String name = mi.name != null && !mi.name.isEmpty() ? mi.name : m.name;
            for (String id : mi.provides) if (!have.containsKey(id)) have.put(id, name);
            String base = m.file.getName().toLowerCase(Locale.ROOT).replaceAll("[-_ ]?\\d.*$", "");
            if (!base.isEmpty() && !have.containsKey(base)) have.put(base, name);
        }
        if (have.containsKey("optifine")) have.put("optifine", "OptiFine");
        return have;
    }

    private static Set<String> strings(JsonNode a) {
        Set<String> out = new LinkedHashSet<String>();
        for (JsonNode n : a) out.add(n.asText().toLowerCase(Locale.ROOT));
        return out;
    }

    private static Set<String> names(File dir) {
        Set<String> out = new HashSet<String>();
        String[] n = dir.list();
        if (n != null) out.addAll(Arrays.asList(n));
        return out;
    }

    private static List<String> readAdded(Instance instance, String setId) {
        List<String> out = new ArrayList<String>();
        File f = new File(instance.getDir(), ADDED);
        if (!f.isFile()) return out;
        try {
            for (JsonNode n : MAPPER.readTree(f).path(setId)) out.add(n.asText());
        } catch (IOException ignored) {
            // зіпсований файл: як порожній
        }
        return out;
    }

    private static synchronized void writeAdded(Instance instance, String setId, List<String> files) {
        File f = new File(instance.getDir(), ADDED);
        try {
            ObjectNode root = MAPPER.createObjectNode();
            if (f.isFile()) {
                JsonNode old = MAPPER.readTree(f);
                if (old instanceof ObjectNode) root = (ObjectNode) old;
            }
            ArrayNode a = root.putArray(setId);
            for (String s : new LinkedHashSet<String>(files)) a.add(s);
            MAPPER.writerWithDefaultPrettyPrinter().writeValue(f, root);
        } catch (IOException e) {
            log.warning("Could not write " + ADDED + ": " + e.getMessage());
        }
    }
}
