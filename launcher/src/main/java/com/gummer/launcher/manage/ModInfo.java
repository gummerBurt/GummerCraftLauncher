/*
 * Copyright (C) 2021-2026 GummerCraft <http://gummercraft.fun>
 */

package com.gummer.launcher.manage;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * 17.11.2026: що за мод лежить у jar, для списку модів у вікні «Збірка». Читається з того, що кожен завантажувач
 * вимагає від мода: {@code fabric.mod.json} (Fabric), {@code quilt.mod.json} (Quilt), {@code META-INF/mods.toml} і
 * {@code META-INF/neoforge.mods.toml} (Forge, NeoForge), {@code mcmod.info} (старий Forge до 1.13). Звідти назва,
 * версія, опис, сайт і шлях до іконки всередині jar. Нічого не знайшли: лише назва файлу.
 * <p>
 * Читання jar займає мілісекунди, але модів у збірці буває 200, тож це лише у фоновому потоці; результат кешується
 * за шляхом, розміром і часом зміни файлу (перейменування в .disabled дає новий шлях, але той самий вміст: ключ без
 * суфікса .disabled).
 */
public final class ModInfo {

    public final String id;
    public final String name;
    public final String version;
    public final String description;
    public final String url;
    /** Шлях до іконки всередині jar або null. */
    final String iconPath;
    /**
     * Які id цей jar дає грі: свій, «provides» і моди, вкладені всередину (Fabric API це ~50 модулів у
     * META-INF/jars, і інші моди залежать саме від них). Малими літерами.
     */
    public final java.util.Set<String> provides = new java.util.TreeSet<String>();
    /** Без яких модів цей не запуститься (обов'язкові залежності, без самої гри, Java і завантажувача). */
    public final java.util.Set<String> depends = new java.util.TreeSet<String>();
    /**
     * 17.11.2026: найменша версія залежності, яку мод приймає (id -> версія), з {@code versionRange} Forge і NeoForge
     * (і для необов'язкових: якщо такий мод стоїть, Forge все одно вимагає діапазон) та {@code depends} Fabric
     * ({@code >=1.2}, {@code ^1.2}, {@code ~1.2}). Для {@link ModVersionFix}: модпак з Modrinth може класти стару
     * версію залежності (The Pixelmon Modpack 9.1.13: byg 1.1.6, а Pixelmon вимагає 1.3.4 і новіше).
     */
    public final Map<String, String> minVersions = new java.util.TreeMap<String, String>();
    /** 17.11.2026: {@code environment} з fabric.mod.json чи quilt.mod.json (client, server, *), або null ({@link ModSides}). */
    public String environment;
    /**
     * 17.11.2026 ~23:30: під які завантажувачі jar має опис (fabric, quilt, forge, neoforge); порожньо, якщо опису немає
     * (бібліотека, старий порт). Для {@link PreLaunchCheck}: Fabric-мод у Forge-збірці гра не завантажить.
     */
    public final java.util.Set<String> loaders = new java.util.TreeSet<String>();

    /**
     * 19.11.2026: умови версії Minecraft з опису мода для {@link PreLaunchCheck}. Fabric: {@code depends.minecraft} (рядок
     * або масив, досить будь-якої з умов). Forge і NeoForge: {@code versionRange} залежності {@code minecraft} (кожна з
     * них має виконатись). Перевіряє {@link McRange} за правилами самих завантажувачів.
     */
    public final java.util.List<String> mcFabric = new java.util.ArrayList<String>();
    public final java.util.List<String> mcMaven = new java.util.ArrayList<String>();
    /**
     * 19.11.2026: з якими модами цей мод сам себе оголосив несумісним (id -> умова версії): Fabric {@code breaks} (будь-яка
     * з умов масиву), NeoForge {@code type = "incompatible"} (Maven-діапазон, «*» будь-яка версія). Завантажувач з
     * такою парою гру не запускає.
     */
    public final Map<String, java.util.List<String>> breaksFabric = new java.util.TreeMap<String, java.util.List<String>>();
    public final Map<String, String> breaksMaven = new java.util.TreeMap<String, String>();
    /**
     * 25.11.2026 (тікет #122): найменша версія завантажувача, яку вимагає мод (fabric, quilt, forge, neoforge -> версія), з
     * {@code depends.fabricloader} Fabric, {@code depends quilt_loader} Quilt і {@code versionRange} залежності forge чи
     * neoforge. Для {@link ModUpdates}: новіша версія мода може вимагати новіший завантажувач, ніж стоїть у збірці (Create
     * 6.0.8 для Fabric хоче Fabric Loader 0.17.2, а збірка на 0.16.0), і тоді гра не запуститься взагалі.
     */
    public final Map<String, String> loaderMin = new java.util.TreeMap<String, String>();

    private ModInfo(String id, String name, String version, String description, String url, String iconPath) {
        this.id = blankToNull(id);
        this.name = name;
        this.version = blankToNull(version);
        this.description = blankToNull(description);
        this.url = cleanUrl(url);
        this.iconPath = blankToNull(iconPath);
        if (this.id != null) provides.add(this.id.toLowerCase(Locale.ROOT));
    }

    /** Залежності, які дає сама гра чи завантажувач: їх не перевіряємо. */
    private static final java.util.Set<String> SYSTEM = new java.util.HashSet<String>(java.util.Arrays.asList(
            "minecraft", "java", "fabricloader", "fabric-loader", "quilt_loader", "quilt_base", "forge", "neoforge",
            "fml", "javafml", "mixinextras"));

    private void depend(String id) {
        if (id == null) return;
        id = id.trim().toLowerCase(Locale.ROOT);
        if (!id.isEmpty() && !SYSTEM.contains(id)) depends.add(id);
    }

    private void provide(String id) {
        if (id != null && !id.trim().isEmpty()) provides.add(id.trim().toLowerCase(Locale.ROOT));
    }

    // 17.11.2026 ~23:30: поблажливо, як сам Fabric: у fabric.mod.json частини модів сирі переноси рядків в описі
    // (genshinstrument, mega_showdown), коментарі; без цього мод лишався без id, і перевірка перед запуском казала «бракує»
    private static final ObjectMapper MAPPER = com.gummer.launcher.util.Json.newMapper()
            .configure(com.fasterxml.jackson.core.JsonParser.Feature.ALLOW_UNQUOTED_CONTROL_CHARS, true)
            .configure(com.fasterxml.jackson.core.JsonParser.Feature.ALLOW_COMMENTS, true);
    private static final Map<String, ModInfo> CACHE = new ConcurrentHashMap<String, ModInfo>();
    /** Найбільший файл метаданих чи іконки, який читаємо (захист від битого чи дивного jar). */
    private static final int MAX_ENTRY = 2 * 1024 * 1024;

    /** Відомості про мод (з кешу, якщо файл не змінився). Ніколи не null. */
    public static ModInfo of(File jar) {
        String key = key(jar);
        ModInfo cached = CACHE.get(key);
        if (cached != null) return cached;
        ModInfo info;
        try {
            info = read(jar);
        } catch (IOException | RuntimeException e) {
            info = null;
        }
        if (info == null) info = new ModInfo(null, baseName(jar), null, null, null, null);
        CACHE.put(key, info);
        return info;
    }

    /** 24.11.2026: відомості з кешу без читання jar, або null, якщо цей файл (з таким розміром і датою) ще не читали. */
    public static ModInfo peek(File jar) {
        return CACHE.get(key(jar));
    }

    /** Лише ідентифікатор мода (modId), або null. */
    public static String idOf(File jar) {
        return of(jar).id;
    }

    private static String key(File jar) {
        String path = jar.getAbsolutePath();
        if (path.toLowerCase(Locale.ROOT).endsWith(InstanceMods.DISABLED)) path = path.substring(0, path.length() - InstanceMods.DISABLED.length());
        return path + "|" + jar.length() + "|" + jar.lastModified();
    }

    /** Іконка мода з jar (як є, без масштабу), або null. */
    public BufferedImage readIcon(File jar) {
        if (iconPath == null) return null;
        try (ZipFile zip = new ZipFile(jar)) {
            ZipEntry e = zip.getEntry(iconPath.startsWith("/") ? iconPath.substring(1) : iconPath);
            if (e == null || e.getSize() > MAX_ENTRY) return null;
            try (InputStream in = zip.getInputStream(e)) {
                return ImageIO.read(in);
            }
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    // ------------------------------------------------------------------ читання

    private static ModInfo read(File jar) throws IOException {
        try (ZipFile zip = new ZipFile(jar)) {
            ModInfo m = readFrom(zip, jar);
            if (m != null) {
                if (zip.getEntry("fabric.mod.json") != null) m.loaders.add("fabric");
                if (zip.getEntry("quilt.mod.json") != null) m.loaders.add("quilt");
                if (zip.getEntry("META-INF/mods.toml") != null || zip.getEntry("mcmod.info") != null) m.loaders.add("forge");
                if (zip.getEntry("META-INF/neoforge.mods.toml") != null) m.loaders.add("neoforge");
                // старий опис 1.12 і раніше: id там ненадійні (шаблонний «examplemod», чужий id в бібліотеці), див. PreLaunchCheck
                if (zip.getEntry("mcmod.info") != null) m.loaders.add("mcmod");
                // 19.11.2026: OptiFine (у новіших jar є mods.toml з id optifine, у старих лише класи встановлювача)
                if (zip.getEntry("optifine/Installer.class") != null || zip.getEntry("optifine/OptiFineClassTransformer.class") != null) m.provide("optifine");
            }
            return m;
        }
    }

    private static ModInfo readFrom(ZipFile zip, File jar) throws IOException {
        {
            String fabric = text(zip, "fabric.mod.json");
            if (fabric != null) return fabric(MAPPER.readTree(fabric), zip, jar);
            String quilt = text(zip, "quilt.mod.json");
            if (quilt != null) return quilt(MAPPER.readTree(quilt), jar);
            String toml = text(zip, "META-INF/neoforge.mods.toml");
            if (toml == null) toml = text(zip, "META-INF/mods.toml");
            if (toml != null) return forge(toml, zip, jar);
            String mcmod = text(zip, "mcmod.info");
            if (mcmod != null) return mcmod(MAPPER.readTree(mcmod), jar);
            // без опису мода (старі порти, бібліотеки): назва і версія з MANIFEST.MF, якщо є
            String[] mf = manifestTitle(zip);
            if (mf[0] != null) return new ModInfo(null, mf[0], mf[1], null, null, null);
        }
        return null;
    }

    private static ModInfo fabric(JsonNode n, ZipFile zip, File jar) {
        JsonNode contact = n.path("contact");
        ModInfo m = new ModInfo(n.path("id").asText(null), nameOr(n.path("name").asText(null), jar), n.path("version").asText(null),
                n.path("description").asText(null), firstOf(contact.path("homepage"), contact.path("sources")), icon(n.path("icon")));
        fabricIds(n, m);
        m.environment = n.path("environment").asText(null);
        for (Iterator<Map.Entry<String, JsonNode>> it = n.path("depends").fields(); it.hasNext(); ) {
            Map.Entry<String, JsonNode> d = it.next();
            m.depend(d.getKey());
            m.needs(d.getKey(), fabricMin(d.getValue()));
            if ("minecraft".equalsIgnoreCase(d.getKey().trim())) m.mcFabric.addAll(strings(d.getValue()));
        }
        for (Iterator<Map.Entry<String, JsonNode>> it = n.path("breaks").fields(); it.hasNext(); ) {
            Map.Entry<String, JsonNode> d = it.next();
            java.util.List<String> cond = strings(d.getValue());
            if (!d.getKey().trim().isEmpty() && !cond.isEmpty()) m.breaksFabric.put(d.getKey().trim().toLowerCase(Locale.ROOT), cond);
        }
        // вкладені моди (Fabric API: fabric-api-base, fabric-lifecycle-events-v1...): інші моди залежать саме від них
        for (JsonNode j : n.path("jars")) nested(zip, j.path("file").asText(null), m);
        return m;
    }

    /** Рядок або масив рядків з JSON (умови версій Fabric). */
    private static java.util.List<String> strings(JsonNode v) {
        java.util.List<String> out = new java.util.ArrayList<String>();
        if (v == null) return out;
        if (v.isTextual()) out.add(v.asText());
        else if (v.isArray()) for (JsonNode x : v) if (x.isTextual()) out.add(x.asText());
        return out;
    }

    private static void fabricIds(JsonNode n, ModInfo m) {
        m.provide(n.path("id").asText(null));
        for (JsonNode p : n.path("provides")) m.provide(p.asText(null));
    }

    /** id вкладеного jar: лише читання його fabric.mod.json чи quilt.mod.json з потоку, без розпакування на диск. */
    private static void nested(ZipFile zip, String path, ModInfo m) {
        if (path == null) return;
        ZipEntry e = zip.getEntry(path.startsWith("/") ? path.substring(1) : path);
        if (e == null || e.getSize() > 64L * 1024 * 1024) return;
        try (java.util.zip.ZipInputStream in = new java.util.zip.ZipInputStream(zip.getInputStream(e))) {
            ZipEntry x;
            while ((x = in.getNextEntry()) != null) {
                if (x.getName().equals("fabric.mod.json") || x.getName().equals("quilt.mod.json")) {
                    ByteArrayOutputStream out = new ByteArrayOutputStream();
                    byte[] buf = new byte[8192];
                    int r;
                    while ((r = in.read(buf)) > 0 && out.size() <= MAX_ENTRY) out.write(buf, 0, r);
                    JsonNode j = MAPPER.readTree(out.toByteArray());
                    if (x.getName().startsWith("fabric")) fabricIds(j, m);
                    else quiltIds(j.path("quilt_loader"), m);
                    // 17.11.2026 ~23:30: бібліотеці Loom дає id з групи Maven (org_incendo_cloud-annotations), а моди
                    // вимагають коротке («cloud» в Impactor): ще і хвіст після групи, і його перше слово
                    String nid = j.path("id").asText("");
                    int us = nid.lastIndexOf('_');
                    if (us > 0 && us < nid.length() - 1) {
                        String tail = nid.substring(us + 1).toLowerCase(Locale.ROOT);
                        m.provide(tail);
                        if (tail.indexOf('-') > 0) m.provide(tail.substring(0, tail.indexOf('-')));
                    }
                    return;
                }
            }
            // 17.11.2026 ~23:30: вкладена бібліотека без опису: Fabric дає їй id сам. Impactor вимагає «cloud», а несе
            // cloud-annotations-2.0.0.jar та інші: id з назви файлу без версії і перше слово
            String stem = path.substring(path.lastIndexOf('/') + 1).replaceAll("(?i)[.]jar$", "").replaceAll("-[0-9].*$", "").toLowerCase(Locale.ROOT);
            if (!stem.isEmpty()) {
                m.provide(stem);
                int dash = stem.indexOf('-');
                if (dash > 0) m.provide(stem.substring(0, dash));
            }
        } catch (IOException | RuntimeException ignored) {
            // битий вкладений jar: просто без його id
        }
    }

    private static void quiltIds(JsonNode q, ModInfo m) {
        m.provide(q.path("id").asText(null));
        for (JsonNode p : q.path("provides")) m.provide(p.isTextual() ? p.asText() : p.path("id").asText(null));
    }

    private static ModInfo quilt(JsonNode n, File jar) {
        JsonNode q = n.path("quilt_loader");
        JsonNode meta = q.path("metadata");
        JsonNode contact = meta.path("contact");
        ModInfo m = new ModInfo(q.path("id").asText(null), nameOr(meta.path("name").asText(null), jar), q.path("version").asText(null),
                meta.path("description").asText(null), firstOf(contact.path("homepage"), contact.path("sources")), icon(meta.path("icon")));
        quiltIds(q, m);
        m.environment = n.path("minecraft").path("environment").asText(null);
        for (JsonNode d : q.path("depends")) {
            if (d.isTextual()) m.depend(d.asText());
            else if (!d.path("optional").asBoolean(false) && !d.has("any")) {
                m.depend(d.path("id").asText(null));
                if (d.has("versions")) m.needs(d.path("id").asText(null), fabricMin(d.path("versions")));
            }
        }
        return m;
    }

    private static ModInfo mcmod(JsonNode n, File jar) {
        JsonNode list = n.isArray() ? n : n.path("modList");
        JsonNode m = list.isArray() && list.size() > 0 ? list.get(0) : n;
        String logo = m.path("logoFile").asText(null);
        return new ModInfo(m.path("modid").asText(null), nameOr(m.path("name").asText(null), jar), m.path("version").asText(null),
                m.path("description").asText(null), m.path("url").asText(null), logo);
    }

    /** Fabric: "icon": "assets/x/icon.png" або {"16": "...", "128": "..."} (беремо найбільшу до 128, інакше найменшу). */
    private static String icon(JsonNode icon) {
        if (icon.isTextual()) return icon.asText();
        if (!icon.isObject()) return null;
        String best = null, smallest = null;
        int bestSize = -1, smallestSize = Integer.MAX_VALUE;
        for (Iterator<Map.Entry<String, JsonNode>> it = icon.fields(); it.hasNext(); ) {
            Map.Entry<String, JsonNode> e = it.next();
            int s;
            try {
                s = Integer.parseInt(e.getKey());
            } catch (NumberFormatException ex) {
                continue;
            }
            if (s <= 128 && s > bestSize) {
                bestSize = s;
                best = e.getValue().asText(null);
            }
            if (s < smallestSize) {
                smallestSize = s;
                smallest = e.getValue().asText(null);
            }
        }
        return best != null ? best : smallest;
    }

    private static String firstOf(JsonNode... nodes) {
        for (JsonNode n : nodes) {
            String s = n.asText(null);
            if (s != null && !s.trim().isEmpty()) return s;
        }
        return null;
    }

    /** Forge і NeoForge: перший блок [[mods]] файлу TOML. Повний розбір TOML не потрібен: лише кілька рядкових ключів. */
    private static ModInfo forge(String toml, ZipFile zip, File jar) throws IOException {
        Map<String, String> top = new java.util.HashMap<String, String>(), mod = null;
        Map<String, String> cur = top;
        java.util.List<Map<String, String>> deps = new java.util.ArrayList<Map<String, String>>();
        int i = 0, n = toml.length();
        while (i < n) {
            int eol = toml.indexOf('\n', i);
            if (eol < 0) eol = n;
            String line = toml.substring(i, eol).trim();
            if (line.startsWith("[")) {
                int hash = line.indexOf('#');
                String header = (hash > 0 ? line.substring(0, hash) : line).trim(); // "[[mods]] #mandatory"
                if (header.equals("[[mods]]")) cur = mod == null ? (mod = new java.util.HashMap<String, String>()) : null;
                else if (header.startsWith("[[dependencies.")) deps.add(cur = new java.util.HashMap<String, String>());
                else cur = null; // [mc-publish] та інше
                i = eol + 1;
                continue;
            }
            int eq = line.indexOf('=');
            if (cur == null || line.startsWith("#") || eq <= 0) {
                i = eol + 1;
                continue;
            }
            String k = line.substring(0, eq).trim();
            int vStart = toml.indexOf('=', i) + 1;
            while (vStart < n && (toml.charAt(vStart) == ' ' || toml.charAt(vStart) == '\t')) vStart++;
            String rest = toml.substring(vStart);
            String value = null;
            int next = eol + 1;
            for (String q : new String[]{"'''", "\"\"\""}) {
                if (rest.startsWith(q)) {
                    int end = rest.indexOf(q, 3);
                    if (end < 0) end = rest.length();
                    value = rest.substring(3, end);
                    if (q.equals("\"\"\"")) value = unescape(value);
                    int consumed = vStart + Math.min(rest.length(), end + 3);
                    int lineEnd = toml.indexOf('\n', consumed);
                    next = lineEnd < 0 ? n : lineEnd + 1;
                    break;
                }
            }
            if (value == null) {
                String v = line.substring(eq + 1).trim();
                if (v.startsWith("\"")) {
                    int end = closingQuote(v);
                    value = unescape(v.substring(1, end));
                } else if (v.startsWith("'")) {
                    int end = v.indexOf('\'', 1);
                    value = v.substring(1, end < 0 ? v.length() : end);
                } else {
                    int hash = v.indexOf('#'); // mandatory = true #коментар
                    value = (hash >= 0 ? v.substring(0, hash) : v).trim();
                }
            }
            if (value != null) cur.put(k, value);
            i = next;
        }
        if (mod == null) mod = new java.util.HashMap<String, String>();
        String version = mod.get("version");
        if (version != null && version.contains("${")) {
            version = jarVersion(zip);
        }
        String logo = mod.containsKey("logoFile") ? mod.get("logoFile") : top.get("logoFile");
        String url = mod.containsKey("displayURL") ? mod.get("displayURL") : top.get("displayURL");
        ModInfo m = new ModInfo(mod.get("modId"), nameOr(mod.get("displayName"), jar), version, mod.get("description"), url, logo);
        for (Map<String, String> d : deps) {
            // Forge: mandatory = true; NeoForge 1.20.2+: type = "required"; лише серверні залежності клієнту не потрібні
            boolean required = "true".equalsIgnoreCase(d.get("mandatory")) || "required".equalsIgnoreCase(d.get("type"));
            if (required && !"SERVER".equalsIgnoreCase(d.get("side"))) m.depend(d.get("modId"));
            if (!"SERVER".equalsIgnoreCase(d.get("side"))) m.needs(d.get("modId"), mavenMin(d.get("versionRange")));
            String depId = d.get("modId") == null ? "" : d.get("modId").trim().toLowerCase(Locale.ROOT);
            // 19.11.2026: діапазон версій гри Forge перевіряє і для необов'язкової залежності (minecraft є завжди)
            if ("minecraft".equals(depId) && d.get("versionRange") != null && !"SERVER".equalsIgnoreCase(d.get("side"))) m.mcMaven.add(d.get("versionRange"));
            if ("incompatible".equalsIgnoreCase(d.get("type")) && !depId.isEmpty()) m.breaksMaven.put(depId, d.get("versionRange") == null ? "*" : d.get("versionRange"));
        }
        return m;
    }

    private void needs(String id, String min) {
        if (id == null || min == null) return;
        id = id.trim().toLowerCase(Locale.ROOT);
        String loader = LOADER_IDS.get(id);
        if (loader != null) {
            String was = loaderMin.get(loader);
            if (was == null || ModVersionFix.compare(min, was) > 0) loaderMin.put(loader, min);
        }
        if (!id.isEmpty() && !SYSTEM.contains(id)) minVersions.put(id, min);
    }

    /** id залежності -> завантажувач, як його звуть {@link LoaderVersion} (fabric, quilt, forge, neoforge). */
    private static final Map<String, String> LOADER_IDS = new java.util.HashMap<String, String>();
    static {
        LOADER_IDS.put("fabricloader", "fabric");
        LOADER_IDS.put("fabric-loader", "fabric");
        LOADER_IDS.put("quilt_loader", "quilt");
        LOADER_IDS.put("forge", "forge");
        LOADER_IDS.put("neoforge", "neoforge");
    }

    /** Нижня межа діапазону Maven: «[1.3.4,)» -> 1.3.4, «(1.2,2)» -> 1.2; «1.0» (м'яка вимога) і «*» -> null. */
    static String mavenMin(String range) {
        if (range == null) return null;
        String r = range.trim();
        if (r.isEmpty() || r.contains("${") || !(r.startsWith("[") || r.startsWith("("))) return null;
        int comma = r.indexOf(',');
        String low = (comma < 0 ? r.substring(1, r.length() - 1) : r.substring(1, comma)).trim();
        return low.isEmpty() ? null : low;
    }

    /**
     * Нижня межа версії Fabric: «>=1.3.4», «>1.3», «^1.2.0», «~1.2» -> версія; «*», точна версія і «<...» -> null.
     * Масив (будь-яка з умов): найменша з нижніх меж, і лише якщо межа є в усіх умовах.
     */
    static String fabricMin(JsonNode v) {
        if (v == null) return null;
        if (v.isArray()) {
            String best = null;
            for (JsonNode x : v) {
                String m = fabricMin(x);
                if (m == null) return null;
                if (best == null || ModVersionFix.compare(m, best) < 0) best = m;
            }
            return best;
        }
        String s = v.asText("").trim();
        String low = null;
        for (String part : s.split("\\s+")) { // «>=1.2 <2.0»
            String p = part.trim();
            if (p.startsWith(">=")) low = p.substring(2);
            else if (p.startsWith(">")) low = p.substring(1);
            else if (p.startsWith("^") || p.startsWith("~")) low = p.substring(1);
        }
        if (low == null) return null;
        low = low.trim();
        return low.isEmpty() || low.contains("x") || low.contains("*") || low.contains("${") ? null : low;
    }

    private static int closingQuote(String v) {
        for (int i = 1; i < v.length(); i++) {
            char c = v.charAt(i);
            if (c == '\\') i++;
            else if (c == '"') return i;
        }
        return v.length();
    }

    private static String unescape(String s) {
        return s.replace("\\n", "\n").replace("\\t", " ").replace("\\\"", "\"").replace("\\\\", "\\");
    }

    /** Implementation-Title і Implementation-Version з MANIFEST.MF (або null). */
    private static String[] manifestTitle(ZipFile zip) {
        try {
            ZipEntry e = zip.getEntry("META-INF/MANIFEST.MF");
            if (e == null) return new String[2];
            try (InputStream in = zip.getInputStream(e)) {
                java.util.jar.Attributes a = new Manifest(in).getMainAttributes();
                return new String[]{a.getValue("Implementation-Title"), a.getValue("Implementation-Version")};
            }
        } catch (IOException | RuntimeException e) {
            return new String[2];
        }
    }

    /** ${file.jarVersion}: версія з MANIFEST.MF (Implementation-Version), так її підставляє і сам Forge. */
    private static String jarVersion(ZipFile zip) {
        try {
            ZipEntry e = zip.getEntry("META-INF/MANIFEST.MF");
            if (e == null) return null;
            try (InputStream in = zip.getInputStream(e)) {
                return new Manifest(in).getMainAttributes().getValue("Implementation-Version");
            }
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    private static String text(ZipFile zip, String name) throws IOException {
        ZipEntry e = zip.getEntry(name);
        if (e == null || e.isDirectory() || e.getSize() > MAX_ENTRY) return null;
        try (InputStream in = zip.getInputStream(e)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int r;
            while ((r = in.read(buf)) > 0) {
                out.write(buf, 0, r);
                if (out.size() > MAX_ENTRY) return null;
            }
            String s = new String(out.toByteArray(), StandardCharsets.UTF_8);
            return s.startsWith("﻿") ? s.substring(1) : s;
        }
    }

    // ------------------------------------------------------------------ дрібниці

    private static String nameOr(String name, File jar) {
        name = blankToNull(name);
        return name != null ? clean(name) : baseName(jar);
    }

    /** Назва файлу без .jar і .disabled. */
    static String baseName(File jar) {
        String n = jar.getName();
        if (n.toLowerCase(Locale.ROOT).endsWith(InstanceMods.DISABLED)) n = n.substring(0, n.length() - InstanceMods.DISABLED.length());
        int dot = n.lastIndexOf('.');
        return dot > 0 ? n.substring(0, dot) : n;
    }

    private static String blankToNull(String s) {
        if (s == null) return null;
        s = clean(s);
        return s.isEmpty() ? null : s;
    }

    /** Без кольорових кодів Minecraft (§a) і зайвих пробілів і переносів. */
    private static String clean(String s) {
        return fixMojibake(s).replaceAll("§.", "").replaceAll("\\s+", " ").trim();
    }

    /**
     * Мод зібрано з неправильним кодуванням (UTF-8 прочитано як Latin-1 і записано знову): «ÐÐµÑ...» замість
     * кирилиці. Так зіпсовано, наприклад, опис ModWhiteList. Якщо всі символи вміщаються в Latin-1, а їх байти дають
     * чистий UTF-8, повертаємо розкодоване.
     */
    static String fixMojibake(String s) {
        if (s.indexOf('Ð') < 0 && s.indexOf('Ñ') < 0 && s.indexOf('Ã') < 0) return s;
        for (int i = 0; i < s.length(); i++) if (s.charAt(i) > 0xFF) return s;
        try {
            byte[] b = s.getBytes(StandardCharsets.ISO_8859_1);
            return StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(b)).toString();
        } catch (java.nio.charset.CharacterCodingException e) {
            return s;
        }
    }

    /** Лише http(s): посилання з jar відкривається в браузері гравця, тож file: і подібне не пускаємо. */
    private static String cleanUrl(String url) {
        url = blankToNull(url);
        if (url == null) return null;
        String l = url.toLowerCase(Locale.ROOT);
        if (l.startsWith("http://") || l.startsWith("https://")) return url;
        return null;
    }
}
