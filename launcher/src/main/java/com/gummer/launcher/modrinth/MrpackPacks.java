/*
 * Copyright (C) 2021-2026 GummerCraft <http://gummercraft.fun>
 */

package com.gummer.launcher.modrinth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.gummer.launcher.fabric.FabricVersions;
import com.gummer.launcher.forge.ForgeVersions;
import com.gummer.launcher.quilt.QuiltVersions;
import com.gummer.launcher.util.Json;
import com.gummer.launcher.vanilla.VanillaVersions;
import com.skcraft.concurrency.ProgressObservable;
import com.skcraft.launcher.Instance;
import com.skcraft.launcher.Launcher;
import com.skcraft.launcher.model.modpack.Manifest;
import com.skcraft.launcher.util.HttpRequest;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Збірки з Modrinth ({@code .mrpack}, з 1.33): гравець відкриває файл, лаунчер створює пак {@code mrpack-<назва>}
 * (тека {@code instances/mrpack-<назва>}), качає моди за списком {@code modrinth.index.json} (з перевіркою sha1 і
 * sha512, лише з адрес, які дозволяє формат Modrinth) і копіює {@code overrides/} та {@code client-overrides/}.
 *
 * <p>Гру і завантажувач пак ставить так само, як розділи Vanilla, Fabric, Quilt, Forge, NeoForge (той самий код,
 * версії з {@code mrpack.json} у теці паку). Моди качаються один раз при імпорті; оновлення збірки: імпортувати новий
 * файл.</p>
 */
public final class MrpackPacks implements ProgressObservable {

    /** Imports are hidden until 1.34 (decided 13.11.2026): needs more testing (Forge, NeoForge, Quilt packs). */
    public static final boolean ENABLED = true; // 17.11.2026: увімкнено разом з вкладкою «Модпаки» і експортом
    public static final String PREFIX = "mrpack-";
    public static final String INFO = "mrpack.json";
    /** Адреси, з яких формат Modrinth дозволяє качати файли збірки (специфікація .mrpack). */
    static final List<String> HOSTS = Arrays.asList(
            "cdn.modrinth.com", "github.com", "raw.githubusercontent.com", "gitlab.com");

    private static final Logger log = Logger.getLogger(MrpackPacks.class.getName());
    private static final ObjectMapper MAPPER = Json.newMapper();

    private volatile double progress = -1;
    private volatile String status = "";

    // 17.11.2026 (власник: «дуже довго починається закачка, і в логах нічого не пише, лише після секунд 30 починає
    // писати (1/14)»): перші секунди качався сам файл .mrpack без жодного статусу. Тепер видно мегабайти файлу
    // модпака, а далі файлів модпака в сумі (fileSize з modrinth.index.json).
    private volatile HttpRequest watched;
    private volatile boolean filesPhase;
    private volatile int filesTotal;
    private volatile long bytesTotal;
    private volatile String lastPath = "";
    private final java.util.concurrent.atomic.AtomicInteger filesDone = new java.util.concurrent.atomic.AtomicInteger();
    private final java.util.concurrent.atomic.AtomicLong bytesDone = new java.util.concurrent.atomic.AtomicLong();
    private final java.util.Set<HttpRequest> active = java.util.concurrent.ConcurrentHashMap.newKeySet();

    /**
     * 17.11.2026 (власник: «хочу щоб коли качаєш збірку з modrinth, в неї ставала іконка не modrinth, а самої
     * збірки»): іконка проєкту Modrinth у теці збірки (поза {@code minecraft/}, щоб не змішувати з файлами модпака).
     */
    public static final String PACK_ICON = "pack-icon.png";

    /**
     * 17.11.2026: файли, які поставив сам модпак (шлях у теці гри -> sha1 і проєкт Modrinth), для оновлення модпака:
     * файл, який гравець не міняв, оновлюється, а змінений гравцем лишається (див. {@link MrpackUpdate}).
     */
    public static final String FILES = "mrpack-files.json";

    private volatile String preferredVersionId;
    private volatile String preferredVersionNumber;

    /** 17.11.2026: версія Modrinth, з якої ставиться модпак (id і номер), для перевірки оновлень. */
    public void preferVersion(String versionId, String versionNumber) {
        this.preferredVersionId = versionId;
        this.preferredVersionNumber = versionNumber;
    }

    /** 17.11.2026: чи вже є збірка з цього проєкту Modrinth (записано при встановленні з вкладки «Модпаки»). */
    public static boolean hasProject(Launcher launcher, String projectId) {
        return !dirsOfProject(launcher, projectId).isEmpty();
    }

    /** Теки збірок, встановлених з цього проєкту Modrinth. */
    public static java.util.List<File> dirsOfProject(Launcher launcher, String projectId) {
        java.util.List<File> out = new java.util.ArrayList<File>();
        if (projectId == null) return out;
        File[] dirs = launcher.getInstancesDir().listFiles();
        if (dirs == null) return out;
        for (File d : dirs) {
            if (!isMrpackName(d.getName())) continue;
            File f = new File(d, INFO);
            if (!f.isFile()) continue;
            try {
                if (projectId.equals(MAPPER.readTree(f).path("projectId").asText(null))) out.add(d);
            } catch (IOException ignored) {
                // зіпсований файл: не наш
            }
        }
        return out;
    }

    /** Повідомити прогрес ззовні (завантаження самого .mrpack перед імпортом). */
    public void setStatus(String status, double progress) {
        this.status = status;
        this.progress = progress;
    }

    private volatile String preferredTitle;

    /**
     * 17.11.2026: назва збірки з Modrinth (сторінка модпаків). У файлі модпака назву часто пишуть як версію
     * («26.3-NeoForge» у Simply Optimized Reloaded), і збірка в списку звалась саме так.
     */
    public void preferTitle(String title) {
        this.preferredTitle = title;
    }

    /** 17.11.2026: показувати прогрес цього запиту (файл .mrpack з Modrinth); null - більше не показувати. */
    public void watch(HttpRequest request) {
        this.watched = request;
    }

    private long bytesNow() {
        long b = bytesDone.get();
        for (HttpRequest r : active) b += r.getReadBytes();
        return b;
    }

    public static boolean isMrpackName(String name) {
        return name != null && name.toLowerCase(Locale.ROOT).startsWith(PREFIX);
    }

    /** Що записано в mrpack.json паку: назва, версія Minecraft, завантажувач (vanilla, fabric, quilt, forge, neoforge) і його версія. */
    public static JsonNode info(Instance instance) {
        // 17.11.2026 ~22:50: читається і з малювання списку (розділ, оформлення збірки), тож кеш: файл перечитується, лише
        // якщо змінився час його зміни, а час перевіряється не частіше разу на 2 с. Повертається копія (викликачі можуть міняти)
        File dir = instance.peekDir();
        if (dir == null) return null;
        File f = new File(dir, INFO);
        String key = f.getPath();
        long now = System.currentTimeMillis();
        Object[] c = INFO_CACHE.get(key);
        if (c == null || now - (Long) c[0] >= 2000) {
            long mtime = f.lastModified();
            if (c == null || (Long) c[1] != mtime) {
                JsonNode n = null;
                if (mtime != 0) {
                    try {
                        n = MAPPER.readTree(f);
                    } catch (IOException e) {
                        n = null;
                    }
                }
                c = new Object[]{now, mtime, n};
            } else {
                c = new Object[]{now, mtime, c[2]};
            }
            INFO_CACHE.put(key, c);
        }
        return c[2] == null ? null : ((JsonNode) c[2]).deepCopy();
    }

    private static final java.util.Map<String, Object[]> INFO_CACHE = new java.util.concurrent.ConcurrentHashMap<String, Object[]>();

    /** Забути кеш mrpack.json теки збірки (після запису файлу). */
    public static void forgetInfo(File dir) {
        if (dir != null) INFO_CACHE.remove(new File(dir, INFO).getPath());
    }

    public static String mcOf(Instance instance) {
        JsonNode i = info(instance);
        return i != null ? i.path("mc").asText(null) : null;
    }

    public static String titleOf(Instance instance) {
        JsonNode i = info(instance);
        String t = i != null ? i.path("name").asText(null) : null;
        return t != null && !t.trim().isEmpty() ? t.trim() : instance.getName();
    }

    /** Завантажувач паку (fabric, quilt, forge, neoforge або vanilla). */
    public static String loaderOf(Instance instance) {
        JsonNode i = info(instance);
        return i != null ? i.path("loader").asText("vanilla") : "vanilla";
    }

    /** Маніфест: гра і завантажувач тим самим кодом, що й розділи лаунчера. */
    public static Manifest buildManifest(Launcher launcher, Instance instance) throws IOException, InterruptedException {
        JsonNode i = info(instance);
        if (i == null) throw new IOException(instance.getName() + ": no " + INFO);
        String mc = i.path("mc").asText();
        String loader = i.path("loader").asText("vanilla");
        String lv = i.path("loaderVersion").asText(null);
        Manifest m;
        switch (loader) {
            case "fabric":
                m = FabricVersions.buildManifest(launcher, instance, mc, lv);
                break;
            case "quilt":
                m = QuiltVersions.buildManifest(launcher, instance, mc, lv);
                break;
            case "forge":
                m = ForgeVersions.buildManifest(launcher, instance, ForgeVersions.Kind.FORGE, mc, lv);
                break;
            case "neoforge":
                m = ForgeVersions.buildManifest(launcher, instance, ForgeVersions.Kind.NEOFORGE, mc, lv);
                break;
            default:
                m = VanillaVersions.buildManifest(instance);
        }
        // 25.11.2026 (тікет #123): Forge міг поставити іншу версію, ніж у модпаку (тієї для цієї гри не існує): у
        // mrpack.json записати ту, що справді стоїть, щоб перевірки модів (LoaderVersion) і вікно вильоту бачили її
        String prefix = "+" + loader + "-";
        int at = m.getVersion() == null ? -1 : m.getVersion().indexOf(prefix);
        if (lv != null && at > 0 && ("forge".equals(loader) || "neoforge".equals(loader))) {
            String actual = m.getVersion().substring(at + prefix.length());
            if (!actual.equals(lv) && i instanceof com.fasterxml.jackson.databind.node.ObjectNode) {
                ((com.fasterxml.jackson.databind.node.ObjectNode) i).put("loaderVersion", actual);
                MAPPER.writerWithDefaultPrettyPrinter().writeValue(new File(instance.getDir(), INFO), i);
                forgetInfo(instance.getDir());
                log.info(instance.getName() + ": " + loader + " " + lv + " -> " + actual + " in " + INFO);
            }
        }
        m.setName(instance.getName());
        m.setTitle(titleOf(instance));
        instance.setTitle(titleOf(instance));
        return m;
    }

    /**
     * Імпорт файлу: створює теку паку, качає моди, копіює overrides. Повертає назву нового паку. Нічого не лишає
     * напівготовим: при помилці тека паку видаляється.
     */
    public String importFile(Launcher launcher, File mrpack) throws Exception {
        return importFile(launcher, mrpack, null);
    }

    /** Як вище; {@code projectId} проєкту на Modrinth, звідки модпак (17.11.2026: щоб позначити «Встановлено»). */
    public String importFile(Launcher launcher, File mrpack, String projectId) throws Exception {
        status = com.skcraft.launcher.util.SharedLocale.tr("mrpack.status.reading");
        progress = -1;
        try (ZipFile zip = new ZipFile(mrpack)) {
            ZipEntry ie = zip.getEntry("modrinth.index.json");
            // 1.40.0: zip-збірка CurseForge (manifest.json) іде тим самим шляхом: та сама тека mrpack-*, mrpack.json
            if (ie == null && zip.getEntry("manifest.json") != null) return importCurseForge(launcher, zip, mrpack, projectId);
            if (ie == null) throw new IOException("Not a Modrinth modpack: no modrinth.index.json");
            JsonNode index;
            try (InputStream in = zip.getInputStream(ie)) {
                index = MAPPER.readTree(in);
            }
            if (!"minecraft".equals(index.path("game").asText())) throw new IOException("Not a Minecraft modpack");
            JsonNode deps = index.path("dependencies");
            String mc = deps.path("minecraft").asText(null);
            if (mc == null || !mc.matches("[0-9A-Za-z._-]+")) throw new IOException("The modpack has no Minecraft version");
            String loader = "vanilla", lv = null;
            String[][] known = {{"fabric-loader", "fabric"}, {"quilt-loader", "quilt"}, {"forge", "forge"}, {"neoforge", "neoforge"}};
            for (String[] k : known) {
                if (deps.has(k[0])) {
                    loader = k[1];
                    lv = deps.path(k[0]).asText();
                }
            }
            if (lv != null && !lv.matches("[0-9A-Za-z.+_-]+")) throw new IOException("Bad loader version " + lv);
            // Forge у .mrpack пишеться без версії Minecraft (47.3.0), у maven Forge повна (1.20.1-47.3.0)
            if ("forge".equals(loader) && lv != null && !lv.startsWith(mc + "-")) lv = mc + "-" + lv;

            String title = preferredTitle != null && !preferredTitle.trim().isEmpty() ? preferredTitle.trim()
                    : index.path("name").asText("Modrinth");
            String slug = title.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
            if (slug.isEmpty()) slug = "pack";
            if (slug.length() > 40) slug = slug.substring(0, 40);
            String name = PREFIX + slug;
            File dir = new File(launcher.getInstancesDir(), name);
            for (int n = 2; dir.exists(); n++) {
                name = PREFIX + slug + "-" + n;
                dir = new File(launcher.getInstancesDir(), name);
            }
            File content = new File(dir, "minecraft");
            content.mkdirs();
            try {
                JsonNode files = index.path("files");
                // 17.11.2026: 6 файлів одночасно (раніше по одному: модпак на 200 модів качався хвилинами); кожен
                // звіряється з sha1 і sha512, перша ж помилка зупиняє імпорт
                final java.util.List<JsonNode> todo = new java.util.ArrayList<JsonNode>();
                final ObjectNode manifest = MAPPER.createObjectNode();
                for (JsonNode f : files) {
                    if (!"unsupported".equals(f.path("env").path("client").asText(""))) todo.add(f);
                }
                final int total = todo.size();
                final java.util.concurrent.atomic.AtomicInteger done = new java.util.concurrent.atomic.AtomicInteger();
                long size = 0;
                for (JsonNode f : todo) {
                    long s = f.path("fileSize").asLong(0);
                    if (s <= 0) {
                        size = 0; // розмір не в усіх файлів: рахуємо лише штуки
                        break;
                    }
                    size += s;
                }
                filesTotal = total;
                bytesTotal = size;
                filesDone.set(0);
                bytesDone.set(0);
                filesPhase = true;
                java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(16, // 24.11.2026: 6 -> 16, як HttpDownloader
                         r -> {
                    Thread t = new Thread(r, "mrpack-download");
                    t.setDaemon(true);
                    return t;
                });
                try {
                    java.util.List<java.util.concurrent.Future<?>> jobs = new java.util.ArrayList<java.util.concurrent.Future<?>>();
                    for (final JsonNode f : todo) {
                        jobs.add(pool.submit(() -> {
                            String path = f.path("path").asText("");
                            File target = safe(content, path);
                            byte[] data = null;
                            IOException last = null;
                            for (JsonNode u : f.path("downloads")) {
                                URL url = new URL(u.asText());
                                if (!"https".equals(url.getProtocol()) || !HOSTS.contains(url.getHost().toLowerCase(Locale.ROOT))) {
                                    last = new IOException("Download host not allowed by the .mrpack format: " + url.getHost());
                                    continue;
                                }
                                try {
                                    data = fetch(url, active); // 24.11.2026: з повторами
                                    break;
                                } catch (IOException e) {
                                    last = e;
                                }
                            }
                            if (data == null) throw last != null ? last : new IOException("No download for " + path);
                            check(path, data, "SHA-1", f.path("hashes").path("sha1").asText(null));
                            check(path, data, "SHA-512", f.path("hashes").path("sha512").asText(null));
                            target.getParentFile().mkdirs();
                            Files.write(target.toPath(), data);
                            int n = done.incrementAndGet();
                            bytesDone.addAndGet(data.length);
                            filesDone.set(n);
                            lastPath = path;
                            status = "(" + n + "/" + total + ") " + path;
                            progress = (double) n / Math.max(1, total);
                            return null;
                        }));
                    }
                    for (java.util.concurrent.Future<?> j : jobs) {
                        try {
                            j.get();
                        } catch (java.util.concurrent.ExecutionException e) {
                            Throwable c = e.getCause();
                            if (c instanceof Exception) throw (Exception) c;
                            throw new IOException(c);
                        }
                    }
                } finally {
                    pool.shutdownNow();
                    filesPhase = false;
                }
                // 25.11.2026: моди CurseForge з нашого експорту (MrpackExport.CF_FILE): проект і файл, ставляться через
                // API CurseForge, як у zip-збірках CurseForge
                final java.util.List<com.gummer.launcher.curseforge.CurseForge.FileInfo> cfTodo =
                        new java.util.ArrayList<com.gummer.launcher.curseforge.CurseForge.FileInfo>();
                final java.util.List<com.gummer.launcher.curseforge.CurseForge.Blocked> cfBlockedFiles =
                        new java.util.ArrayList<com.gummer.launcher.curseforge.CurseForge.Blocked>();
                ZipEntry cfe = zip.getEntry(MrpackExport.CF_FILE);
                if (cfe != null) {
                    JsonNode cf;
                    try (InputStream in = zip.getInputStream(cfe)) {
                        cf = MAPPER.readTree(in);
                    }
                    java.util.List<Integer> ids = new java.util.ArrayList<Integer>();
                    for (JsonNode f : cf.path("files")) {
                        int id = f.path("fileID").asInt(0);
                        if (id > 0 && !ids.contains(id)) ids.add(id);
                    }
                    if (!ids.isEmpty()) cfDownload(launcher, ids, content, cfTodo, cfBlockedFiles);
                }
                status = com.skcraft.launcher.util.SharedLocale.tr("mrpack.status.overrides");
                progress = -1;
                java.util.List<String> extracted = new java.util.ArrayList<String>();
                extract(zip, "overrides/", content, extracted);
                extract(zip, "client-overrides/", content, extracted);
                cfBlocked(title, cfBlockedFiles);
                for (JsonNode f : todo) {
                    ObjectNode e = manifest.putObject(f.path("path").asText("").replace('\\', '/'));
                    e.put("sha1", f.path("hashes").path("sha1").asText(""));
                    String project = projectOf(f);
                    if (project != null) e.put("project", project);
                }
                com.fasterxml.jackson.databind.node.ArrayNode cfMissing = MAPPER.createArrayNode();
                cfManifest(manifest, cfMissing, cfTodo, cfBlockedFiles);
                for (String path : extracted) {
                    ObjectNode e = manifest.putObject(path);
                    e.put("sha1", sha1(new File(content, path)));
                }
                ObjectNode filesInfo = MAPPER.createObjectNode();
                filesInfo.set("files", manifest);
                MAPPER.writerWithDefaultPrettyPrinter().writeValue(new File(dir, FILES), filesInfo);

                ObjectNode info = MAPPER.createObjectNode();
                info.put("name", title);
                info.put("mc", mc);
                info.put("loader", loader);
                if (lv != null) info.put("loaderVersion", lv);
                info.put("versionId", index.path("versionId").asText(""));
                if (preferredVersionId != null) info.put("mrVersionId", preferredVersionId);
                if (preferredVersionNumber != null) info.put("versionNumber", preferredVersionNumber);
                info.put("file", mrpack.getName());
                if (projectId != null) info.put("projectId", projectId);
                if (cfMissing.size() > 0) info.set("missing", cfMissing);
                MAPPER.writerWithDefaultPrettyPrinter().writeValue(new File(dir, INFO), info);
                forgetInfo(dir);
            } catch (Exception e) {
                deleteTree(dir);
                throw e;
            }
            log.info("Imported " + mrpack.getName() + " as " + name + " (Minecraft " + mc + ", " + loader + " " + lv + ")");
            return name;
        }
    }

    /**
     * 1.40.0 (24.11.2026): zip-збірка CurseForge. {@code manifest.json}: версія Minecraft, завантажувач
     * ({@code forge-47.2.0}, {@code neoforge-21.1.77}, {@code fabric-0.16.5}, {@code quilt-0.26.0}), список
     * {@code projectID}/{@code fileID} і тека {@code overrides}. Дані файлів через наш проксі ({@link
     * com.gummer.launcher.curseforge.CurseForge#lookup}), файли напряму з CDN CurseForge з перевіркою sha1. Заборонені
     * автором для сторонніх лаунчерів файли гравець качає з сайту сам (вікно, яке малює launcher-flat), лаунчер бере їх з
     * «Завантажень». Чого гравець так і не скачав, записано в {@code mrpack.json} ({@code missing}).
     */
    private String importCurseForge(Launcher launcher, ZipFile zip, File source, String projectId) throws Exception {
        JsonNode m;
        try (InputStream in = zip.getInputStream(zip.getEntry("manifest.json"))) {
            m = MAPPER.readTree(in);
        }
        if (!"minecraftModpack".equals(m.path("manifestType").asText("minecraftModpack"))) {
            throw new IOException("Not a CurseForge Minecraft modpack");
        }
        String mc = m.path("minecraft").path("version").asText(null);
        if (mc == null || !mc.matches("[0-9A-Za-z._-]+")) throw new IOException("The modpack has no Minecraft version");
        String loader = "vanilla", lv = null;
        JsonNode chosen = null;
        for (JsonNode l : m.path("minecraft").path("modLoaders")) {
            if (chosen == null || l.path("primary").asBoolean(false)) chosen = l;
        }
        if (chosen != null) {
            String id = chosen.path("id").asText("");
            int dash = id.indexOf('-');
            if (dash <= 0) throw new IOException("Unknown mod loader " + id);
            String kind = id.substring(0, dash).toLowerCase(Locale.ROOT);
            lv = id.substring(dash + 1);
            switch (kind) {
                case "forge":
                    loader = "forge";
                    if (!lv.startsWith(mc + "-")) lv = mc + "-" + lv;
                    break;
                case "neoforge":
                    loader = "neoforge";
                    break;
                case "fabric":
                    loader = "fabric";
                    break;
                case "quilt":
                    loader = "quilt";
                    break;
                default:
                    throw new IOException("Unknown mod loader " + id);
            }
            if (!lv.matches("[0-9A-Za-z.+_-]+")) throw new IOException("Bad loader version " + lv);
        }

        String title = preferredTitle != null && !preferredTitle.trim().isEmpty() ? preferredTitle.trim()
                : m.path("name").asText("CurseForge").trim();
        if (title.isEmpty()) title = "CurseForge";
        String slug = title.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
        if (slug.isEmpty()) slug = "curseforge";
        if (slug.length() > 40) slug = slug.substring(0, 40);
        String name = PREFIX + slug;
        File dir = new File(launcher.getInstancesDir(), name);
        for (int n = 2; dir.exists(); n++) {
            name = PREFIX + slug + "-" + n;
            dir = new File(launcher.getInstancesDir(), name);
        }
        final File content = new File(dir, "minecraft");
        content.mkdirs();
        try {
            java.util.List<Integer> ids = new java.util.ArrayList<Integer>();
            for (JsonNode f : m.path("files")) {
                // required: false - необов'язковий файл, який автор збірки вимкнув; CurseForge App його теж не ставить
                if (!f.path("required").asBoolean(true)) continue;
                int id = f.path("fileID").asInt(0);
                if (id > 0 && !ids.contains(id)) ids.add(id);
            }
            final java.util.List<com.gummer.launcher.curseforge.CurseForge.FileInfo> todo =
                    new java.util.ArrayList<com.gummer.launcher.curseforge.CurseForge.FileInfo>();
            final java.util.List<com.gummer.launcher.curseforge.CurseForge.Blocked> blocked =
                    new java.util.ArrayList<com.gummer.launcher.curseforge.CurseForge.Blocked>();
            cfDownload(launcher, ids, content, todo, blocked);

            status = com.skcraft.launcher.util.SharedLocale.tr("mrpack.status.overrides");
            progress = -1;
            String overrides = m.path("overrides").asText("overrides").replace('\\', '/');
            if (overrides.isEmpty()) overrides = "overrides";
            java.util.List<String> extracted = new java.util.ArrayList<String>();
            extract(zip, overrides.endsWith("/") ? overrides : overrides + "/", content, extracted);

            int fromModrinth = cfBlocked(title, blocked);

            ObjectNode manifest = MAPPER.createObjectNode();
            com.fasterxml.jackson.databind.node.ArrayNode missing = MAPPER.createArrayNode();
            cfManifest(manifest, missing, todo, blocked);
            for (String path : extracted) {
                ObjectNode e = manifest.putObject(path);
                e.put("sha1", sha1(new File(content, path)));
            }
            ObjectNode filesInfo = MAPPER.createObjectNode();
            filesInfo.set("files", manifest);
            MAPPER.writerWithDefaultPrettyPrinter().writeValue(new File(dir, FILES), filesInfo);

            ObjectNode info = MAPPER.createObjectNode();
            info.put("name", title);
            info.put("mc", mc);
            info.put("loader", loader);
            if (lv != null) info.put("loaderVersion", lv);
            info.put("source", "curseforge");
            // 1.40.0: зі сторінки CurseForge лаунчера «cf-<id>» (як projectId Modrinth: «Встановлено», «Грати»)
            if (projectId != null) info.put("projectId", projectId);
            if (preferredVersionId != null) info.put("cfFileId", preferredVersionId);
            info.put("versionNumber", preferredVersionNumber != null ? preferredVersionNumber : m.path("version").asText(""));
            info.put("author", m.path("author").asText(""));
            info.put("file", source.getName());
            if (missing.size() > 0) info.set("missing", missing);
            MAPPER.writerWithDefaultPrettyPrinter().writeValue(new File(dir, INFO), info);
            forgetInfo(dir);
            log.info("Imported CurseForge pack " + source.getName() + " as " + name + " (Minecraft " + mc + ", " + loader
                    + " " + lv + ", " + todo.size() + " files, " + blocked.size() + " blocked by authors (" + fromModrinth
                    + " of them from Modrinth by sha1), "
                    + missing.size() + " still missing)");
        } catch (Exception e) {
            deleteTree(dir);
            throw e;
        }
        return name;
    }

    /**
     * Файли CurseForge за ID: дані через проксі, дозволені качаються в {@code content} (16 потоків, sha1 і розмір) і
     * йдуть у {@code todo}, заборонені автором у {@code blocked} (їх далі {@link #cfBlocked}). 25.11.2026: винесено з
     * {@link #importCurseForge}, щоб тим самим кодом ставити моди CurseForge з нашого експорту .mrpack.
     */
    private void cfDownload(Launcher launcher, java.util.List<Integer> ids, final File content,
                            final java.util.List<com.gummer.launcher.curseforge.CurseForge.FileInfo> todo,
                            final java.util.List<com.gummer.launcher.curseforge.CurseForge.Blocked> blocked) throws Exception {
        {
            status = com.skcraft.launcher.util.SharedLocale.tr("curseforge.status.lookup", ids.size());
            progress = -1;
            java.util.List<com.gummer.launcher.curseforge.CurseForge.FileInfo> infos = ids.isEmpty()
                    ? new java.util.ArrayList<com.gummer.launcher.curseforge.CurseForge.FileInfo>()
                    : com.gummer.launcher.curseforge.CurseForge.lookup(launcher, ids);
            long size = 0;
            for (com.gummer.launcher.curseforge.CurseForge.FileInfo i : infos) {
                File target = safe(content, i.folder() + "/" + i.fileName);
                if (i.downloadUrl == null) {
                    blocked.add(new com.gummer.launcher.curseforge.CurseForge.Blocked(i, target));
                } else {
                    todo.add(i);
                    size += Math.max(0, i.length);
                }
            }
            final int total = todo.size();
            final java.util.concurrent.atomic.AtomicInteger done = new java.util.concurrent.atomic.AtomicInteger();
            filesTotal = total;
            bytesTotal = size;
            filesDone.set(0);
            bytesDone.set(0);
            filesPhase = true;
            java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(16, // 24.11.2026: 6 -> 16, як HttpDownloader
                     r -> {
                Thread t = new Thread(r, "curseforge-download");
                t.setDaemon(true);
                return t;
            });
            try {
                java.util.List<java.util.concurrent.Future<?>> jobs = new java.util.ArrayList<java.util.concurrent.Future<?>>();
                for (final com.gummer.launcher.curseforge.CurseForge.FileInfo i : todo) {
                    jobs.add(pool.submit(() -> {
                        String path = i.folder() + "/" + i.fileName;
                        File target = safe(content, path);
                        byte[] data = fetch(com.gummer.launcher.curseforge.CurseForge.fileUrl(i.downloadUrl), active); // 24.11.2026: з повторами
                        check(path, data, "SHA-1", i.sha1);
                        // без sha1 (буває в CurseForge) звіряємо хоча б розмір
                        if (i.length > 0 && data.length != i.length) {
                            throw new IOException(path + ": size " + data.length + " instead of " + i.length);
                        }
                        target.getParentFile().mkdirs();
                        Files.write(target.toPath(), data);
                        int n = done.incrementAndGet();
                        bytesDone.addAndGet(data.length);
                        filesDone.set(n);
                        lastPath = path;
                        status = "(" + n + "/" + total + ") " + path;
                        progress = (double) n / Math.max(1, total);
                        return null;
                    }));
                }
                for (java.util.concurrent.Future<?> j : jobs) {
                    try {
                        j.get();
                    } catch (java.util.concurrent.ExecutionException e) {
                        Throwable c = e.getCause();
                        if (c instanceof Exception) throw (Exception) c;
                        throw new IOException(c);
                    }
                }
            } finally {
                pool.shutdownNow();
                filesPhase = false;
            }
        }
    }

    /**
     * Заборонені автором файли CurseForge: спершу точні копії на Modrinth за sha1, потім те, що гравець уже колись
     * качав («Завантаження»), потім вікно з браузером. Повертає, скільки взято з Modrinth. 25.11.2026: винесено з
     * {@link #importCurseForge}.
     */
    private int cfBlocked(String title, java.util.List<com.gummer.launcher.curseforge.CurseForge.Blocked> blocked) throws InterruptedException {
        int fromModrinth = 0;
        if (!blocked.isEmpty()) {
            // 1.40.0: спершу точні копії на Modrinth за sha1 (той самий файл, без браузера)
            fromModrinth = takeFromModrinth(blocked);
        }
        if (!blocked.isEmpty()) {
            // спершу те, що гравець уже колись качав (збірку ставлять вдруге), потім вікно
            com.gummer.launcher.curseforge.CurseForge.pickup(blocked, com.gummer.launcher.curseforge.CurseForge.downloadDirs());
            boolean left = false;
            for (com.gummer.launcher.curseforge.CurseForge.Blocked b : blocked) left |= !b.isDone();
            com.gummer.launcher.curseforge.CurseForge.BlockedHandler h = com.gummer.launcher.curseforge.CurseForge.blockedHandler();
            if (left && h != null) {
                // у вікні лише те, чого немає ні на Modrinth, ні в «Завантаженнях»
                java.util.List<com.gummer.launcher.curseforge.CurseForge.Blocked> rest =
                        new java.util.ArrayList<com.gummer.launcher.curseforge.CurseForge.Blocked>();
                for (com.gummer.launcher.curseforge.CurseForge.Blocked b : blocked) if (!b.isDone()) rest.add(b);
                status = com.skcraft.launcher.util.SharedLocale.tr("curseforge.status.blocked", rest.size());
                h.resolve(title, rest);
            }
        }
        return fromModrinth;
    }

    /** Файли CurseForge у {@value #FILES} (поставлені) і в {@code missing} (так і не скачані). 25.11.2026: винесено. */
    private static void cfManifest(ObjectNode manifest, com.fasterxml.jackson.databind.node.ArrayNode missing,
                                   java.util.List<com.gummer.launcher.curseforge.CurseForge.FileInfo> todo,
                                   java.util.List<com.gummer.launcher.curseforge.CurseForge.Blocked> blocked) {
        for (com.gummer.launcher.curseforge.CurseForge.FileInfo i : todo) {
            ObjectNode e = manifest.putObject(i.folder() + "/" + i.fileName);
            e.put("sha1", i.sha1 != null ? i.sha1 : "");
            e.put("curseforge", i.modId);
        }
        for (com.gummer.launcher.curseforge.CurseForge.Blocked b : blocked) {
            if (b.isDone()) {
                ObjectNode e = manifest.putObject(b.file.folder() + "/" + b.file.fileName);
                e.put("sha1", b.file.sha1 != null ? b.file.sha1 : "");
                e.put("curseforge", b.file.modId);
                if (b.modrinthProject != null) e.put("modrinth", b.modrinthProject);
            } else {
                ObjectNode x = missing.addObject();
                x.put("name", b.file.title());
                x.put("file", b.file.folder() + "/" + b.file.fileName);
                x.put("page", b.file.downloadPage());
                x.put("sha1", b.file.sha1 != null ? b.file.sha1 : "");
                x.put("length", b.file.length);
            }
        }
    }

    private static void extract(ZipFile zip, String prefix, File content, java.util.List<String> paths) throws IOException {
        Enumeration<? extends ZipEntry> en = zip.entries();
        while (en.hasMoreElements()) {
            ZipEntry e = en.nextElement();
            if (e.isDirectory() || !e.getName().startsWith(prefix)) continue;
            String rel = e.getName().substring(prefix.length());
            File target = safe(content, rel);
            target.getParentFile().mkdirs();
            try (InputStream in = zip.getInputStream(e)) {
                Files.copy(in, target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            if (paths != null && !paths.contains(rel)) paths.add(rel);
        }
    }

    /** Проєкт Modrinth файлу модпака з адреси {@code https://cdn.modrinth.com/data/<проєкт>/versions/...}, або null. */
    static String projectOf(JsonNode file) {
        for (JsonNode u : file.path("downloads")) {
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("^https://cdn\\.modrinth\\.com/data/([A-Za-z0-9]+)/").matcher(u.asText(""));
            if (m.find()) return m.group(1);
        }
        return null;
    }

    static String sha1(File f) throws IOException {
        try (InputStream in = new java.io.FileInputStream(f)) {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] buf = new byte[65536];
            for (int n; (n = in.read(buf)) > 0; ) md.update(buf, 0, n);
            StringBuilder sb = new StringBuilder();
            for (byte b : md.digest()) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IOException(e);
        }
    }

    /** Шлях лише всередині теки гри (без .., без абсолютних шляхів). */
    static File safe(File base, String rel) throws IOException {
        if (rel.isEmpty() || rel.startsWith("/") || rel.startsWith("\\") || rel.contains(":")) {
            throw new IOException("Bad path in the modpack: " + rel);
        }
        File f = new File(base, rel).getCanonicalFile();
        if (!f.toPath().startsWith(base.getCanonicalFile().toPath())) throw new IOException("Bad path in the modpack: " + rel);
        return f;
    }

    /** 24.11.2026: скільки разів пробувати один файл збірки (перша спроба і два повтори). */
    private static final int FETCH_TRIES = 3;
    /**
     * 24.11.2026: повтори всіх збірок разом ідуть не більше ніж у 4 з'єднання. Перша спроба качає в 16 потоків, а файл
     * не скачався, найчастіше, на слабкому чи нестабільному зв'язку, де 16 одночасних з'єднань лише душать канал.
     */
    private static final java.util.concurrent.Semaphore RETRY_SLOTS = new java.util.concurrent.Semaphore(4);
    private static final java.util.regex.Pattern HTTP_CODE = java.util.regex.Pattern.compile("got (\\d{3})");

    /**
     * 24.11.2026: файл збірки з повторами. До цього один обрив будь-якого з сотень файлів зривав установку або
     * оновлення всього модпака (з 16 потоками це стало б імовірніше). Повтор через 3 і 6 с, у слот {@link #RETRY_SLOTS};
     * 4xx (крім 408 і 429) не повторюється: файлу там немає чи доступ заборонено. Скасування (переривання потоку) не
     * повторюється.
     *
     * @param active запити, що качаються зараз (для швидкості в рядку прогресу), або null
     */
    /**
     * 1.40.0: заборонені на CurseForge файли збірки, які автор виклав і на Modrinth (точно той самий файл за sha1),
     * качаються звідти (cdn.modrinth.com, звірка sha512 і sha1). Не вийшло: файл лишається для вікна. Повертає, скільки
     * взято.
     */
    private int takeFromModrinth(java.util.List<com.gummer.launcher.curseforge.CurseForge.Blocked> blocked) throws InterruptedException {
        java.util.List<String> hashes = new java.util.ArrayList<String>();
        for (com.gummer.launcher.curseforge.CurseForge.Blocked b : blocked) if (b.file.sha1 != null) hashes.add(b.file.sha1);
        if (hashes.isEmpty()) return 0;
        status = com.skcraft.launcher.util.SharedLocale.tr("curseforge.status.modrinth", hashes.size());
        progress = -1;
        java.util.Map<String, com.gummer.launcher.curseforge.CurseForge.ModrinthCopy> copies =
                com.gummer.launcher.curseforge.CurseForge.modrinthCopies(hashes);
        int taken = 0;
        for (com.gummer.launcher.curseforge.CurseForge.Blocked b : blocked) {
            if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
            com.gummer.launcher.curseforge.CurseForge.ModrinthCopy c = b.file.sha1 == null ? null
                    : copies.get(b.file.sha1.toLowerCase(java.util.Locale.ROOT));
            if (c == null) continue;
            try {
                byte[] data = fetch(new URL(c.url), null);
                check(b.target.getName(), data, "SHA-512", c.sha512);
                check(b.target.getName(), data, "SHA-1", b.file.sha1);
                b.target.getParentFile().mkdirs();
                Files.write(b.target.toPath(), data);
                b.modrinthProject = c.projectId;
                b.markDone();
                taken++;
                status = "(Modrinth " + taken + ") " + b.file.folder() + "/" + b.file.fileName;
            } catch (IOException e) {
                log.info("CurseForge file " + b.file.fileName + " from Modrinth failed, left for the browser: " + e);
            }
        }
        log.info("CurseForge pack: " + taken + " of " + blocked.size() + " author-restricted files taken from Modrinth by sha1");
        return taken;
    }

    static byte[] fetch(URL url, java.util.Collection<HttpRequest> active) throws IOException, InterruptedException {
        IOException last = null;
        for (int attempt = 0; attempt < FETCH_TRIES; attempt++) {
            if (attempt > 0) {
                if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
                Thread.sleep(attempt * 3000L);
                RETRY_SLOTS.acquire();
            }
            HttpRequest req = HttpRequest.get(url);
            if (active != null) active.add(req);
            try {
                return req.execute().expectResponseCode(200).returnContent().asBytes();
            } catch (IOException e) {
                last = e;
                java.util.regex.Matcher m = HTTP_CODE.matcher(String.valueOf(e.getMessage()));
                if (m.find()) {
                    int code = Integer.parseInt(m.group(1));
                    if (code >= 400 && code < 500 && code != 408 && code != 429) throw e;
                }
                if (Thread.currentThread().isInterrupted()) throw e;
                log.info("Retrying " + url + " (" + (attempt + 1) + " of " + FETCH_TRIES + "): " + e);
            } finally {
                if (active != null) active.remove(req);
                if (attempt > 0) RETRY_SLOTS.release();
            }
        }
        throw last;
    }

    static void check(String path, byte[] data, String algo, String expected) throws IOException {
        if (expected == null || expected.isEmpty()) return;
        try {
            byte[] d = MessageDigest.getInstance(algo).digest(data);
            StringBuilder sb = new StringBuilder();
            for (byte b : d) sb.append(String.format("%02x", b));
            if (!sb.toString().equalsIgnoreCase(expected)) throw new IOException(path + ": " + algo + " does not match");
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IOException(e);
        }
    }

    private static void deleteTree(File f) {
        File[] kids = f.listFiles();
        if (kids != null) for (File k : kids) deleteTree(k);
        f.delete();
    }

    @Override
    public double getProgress() {
        HttpRequest w = watched;
        if (w != null) return w.getProgress();
        if (filesPhase && bytesTotal > 0) return Math.min(1.0, bytesNow() / (double) bytesTotal);
        return progress;
    }

    @Override
    public String getStatus() {
        double mb = 1024.0 * 1024.0;
        HttpRequest w = watched;
        if (w != null) {
            long read = w.getReadBytes(), len = w.getContentLength();
            if (len > 0) return com.skcraft.launcher.util.SharedLocale.tr("mrpack.status.packFile", read / mb, len / mb);
            if (read > 0) return com.skcraft.launcher.util.SharedLocale.tr("mrpack.status.packFileNoTotal", read / mb);
            return com.skcraft.launcher.util.SharedLocale.tr("mrpack.status.connecting");
        }
        if (filesPhase) {
            String head = bytesTotal > 0
                    ? com.skcraft.launcher.util.SharedLocale.tr("mrpack.status.files", filesDone.get(), filesTotal, bytesNow() / mb, bytesTotal / mb)
                    : com.skcraft.launcher.util.SharedLocale.tr("mrpack.status.filesNoSize", filesDone.get(), filesTotal);
            String p = lastPath;
            return p.isEmpty() ? head : head + "\n" + p;
        }
        return status;
    }
}
