/*
 * Copyright (C) 2021-2026 GummerCraft <http://gummercraft.fun>
 */

package com.gummer.launcher.manage;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gummer.launcher.util.Json;
import com.skcraft.launcher.Instance;
import com.skcraft.launcher.util.HttpRequest;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;

/**
 * 17.11.2026: ресурспаки і шейдери з Modrinth прямо у вікні «Керування збіркою» (вирішено: «щоб гравці могли собі
 * накидати текстурпаки та шейдери прямо в налаштуванні збірки»). Modrinth, а не CurseForge: у Modrinth відкритий API
 * без ключа, у CurseForge API лише з ключем, який довелось би зашити в публічний лаунчер.
 *
 * <p>Пошук: {@code api.modrinth.com/v2/search} з фільтром за типом (resourcepack, shader) і версією гри збірки.
 * Встановлення: найновіша версія проєкту під цю версію гри (для шейдерів ще під Iris чи OptiFine), головний файл лише з
 * {@code cdn.modrinth.com}, звірка sha512 з відповіді API, запис через {@code .part} і перейменування. Наявний файл
 * з тією ж назвою не перезаписується.</p>
 */
public final class ModrinthBrowse {

    private static final Logger log = Logger.getLogger(ModrinthBrowse.class.getName());
    static final ObjectMapper MAPPER = Json.newMapper();
    private static final String API = "https://api.modrinth.com/v2/";
    static final String CDN_HOST = "cdn.modrinth.com";

    /**
     * 17.11.2026: свої фонові потоки для пошуку й іконок Modrinth. Спільний пул SwingWorker (10 потоків) ділять усі
     * вікна лаунчера, і пошук з іконками стояли в черзі за іншими задачами.
     */
    public static final java.util.concurrent.ExecutorService POOL = java.util.concurrent.Executors.newFixedThreadPool(6, r -> {
        Thread t = new Thread(r, "modrinth-" + System.nanoTime());
        t.setDaemon(true);
        return t;
    });

    /** Іконки за адресою, поки лаунчер працює (повторний пошук і повторне відкриття вікна без мережі). */
    private static final Map<String, BufferedImage> ICONS = java.util.Collections.synchronizedMap(
            new java.util.LinkedHashMap<String, BufferedImage>(64, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, BufferedImage> e) {
                    return size() > 400;
                }
            });

    /** Сторінки пошуку на 10 хвилин: повторне відкриття вікна чи вкладки показує результат одразу. */
    private static final Map<String, Object[]> PAGES = new java.util.concurrent.ConcurrentHashMap<String, Object[]>();
    private static final long PAGE_TTL = 10 * 60 * 1000L;
    public static final int PAGE = 20;
    /** Текст винятку, коли під цю версію гри (і завантажувач шейдерів) версії проєкту немає. */
    public static final String NO_VERSION = "NO_VERSION";

    /**
     * 17.11.2026: вибір у пошуку модів. CLIENT: лише для гри гравця (на сервері не потрібні: Sodium, Iris), SERVER:
     * потрібні серверу, у грі за бажанням чи зовсім не потрібні. Бібліотеки «за бажанням і там, і там» лише в POPULAR.
     */
    public enum Filter {
        POPULAR(""),
        CLIENT(",[\"client_side:required\",\"client_side:optional\"],[\"server_side:unsupported\"]"),
        SERVER(",[\"server_side:required\"],[\"client_side:optional\",\"client_side:unsupported\"]");

        final String facets;

        Filter(String facets) {
            this.facets = facets;
        }
    }

    /** 17.11.2026: порядок результатів. BEST: без запиту найпопулярніші, із запитом найкращий збіг. */
    public enum Sort {
        BEST, NEWEST, UPDATED
    }

    /**
     * 17.11.2026: категорії Modrinth для фільтра сторінки (назви категорій API; підписи в lang {@code mpage.cat.<назва>}).
     * Модпаки і моди мають різні набори.
     */
    public static final String[] MODPACK_CATEGORIES = {"adventure", "challenging", "combat", "kitchen-sink", "lightweight",
            "magic", "multiplayer", "optimization", "quests", "technology"};
    public static final String[] MOD_CATEGORIES = {"adventure", "decoration", "economy", "equipment", "food",
            "game-mechanics", "library", "magic", "management", "minigame", "mobs", "optimization", "social", "storage",
            "technology", "transportation", "utility", "worldgen"};

    public enum Kind {
        RESOURCEPACK("resourcepack", "resourcepacks", ".zip"),
        SHADER("shader", "shaderpacks", ".zip"),
        MOD("mod", "mods", ".jar"),
        /** 17.11.2026: модпак ставиться окремою збіркою (MrpackPacks), а не в теку. */
        MODPACK("modpack", "", ".mrpack");

        public final String projectType;
        public final String folder;
        public final String extension;

        Kind(String projectType, String folder, String extension) {
            this.projectType = projectType;
            this.folder = folder;
            this.extension = extension;
        }
    }

    /**
     * 24.11.2026: проти X-ray, а не сам X-ray (Xray Snitch, XrayReporter, AntiXray: ловлять тих, хто з ним грає;
     * Block Overlay Fix: виправляє баг «бачу крізь блок»). Хоч одне таке слово в назві чи описі: позначки немає.
     */
    private static final java.util.regex.Pattern ANTI_XRAY = java.util.regex.Pattern.compile(
            "(?iu)anti[\\s_.-]?x[\\s_.-]?ray|snitch|\\bdetect(s|ed|ing|or)?\\b|\\breport(s|er|ing)?\\b|x[\\s_.-]?rayers"
                    + "|\\bcombat\\b|\\bprevent|\\bfix(es)?\\s+x[\\s_.-]?ray");
    /** 24.11.2026: xray у будь-якому місці, «x-ray» / «x ray» / «x_ray» окремим словом, іксрей / ікс-рей / иксрей. */
    private static final java.util.regex.Pattern XRAY = java.util.regex.Pattern.compile(
            "(?iu)xray|(?<![\\p{L}\\d])x[\\s_.-]ray|[іи]кс[\\s-]?рей");

    /**
     * 24.11.2026 (власник: «всі моди та ресурспаки, де зустрічається іксрей, додай червону помітку»): чи схоже на
     * X-ray (назва, slug, опис, ім'я файлу). Лише позначка для гравця, встановлювати не заважає.
     */
    public static boolean isXray(String... texts) {
        boolean found = false;
        for (String t : texts) {
            if (t == null || t.isEmpty()) continue;
            if (ANTI_XRAY.matcher(t).find()) return false;
            if (XRAY.matcher(t).find()) found = true;
        }
        return found;
    }

    /** Те саме для результату пошуку. */
    public static boolean isXray(Hit h) {
        return h != null && isXray(h.title, h.slug, h.description);
    }

    /** Один результат пошуку. */
    public static final class Hit {
        public String id, slug, title, author, description, iconUrl;
        /** 17.11.2026 ~23:15: головна картинка галереї проєкту (featured_gallery, інакше перша), або null: фон збірки модпака. */
        public String galleryUrl;
        /** 1.40.0: сторінка проєкту на сайті (CurseForge), або null: тоді адреса Modrinth за slug. */
        public String pageUrl;
        /** 17.11.2026: де мод працює (з client_side і server_side пошуку), або null. */
        public ModSides.Side side;
        /** 17.11.2026: чи вже стоїть у збірці (рахується у фоні разом з пошуком, а не в потоці інтерфейсу). */
        public volatile boolean installed;
        public long downloads;
    }

    /** Результат сторінки пошуку. */
    public static final class Page {
        public final List<Hit> hits = new ArrayList<Hit>();
        public int total;
    }

    private ModrinthBrowse() {
    }

    /**
     * Версія гри для фільтра: як у {@link InstanceCopies#minecraftVersionOf}, а для модпаків з CDN з їхнього
     * {@code manifest.json} ({@code gameVersion}). null - невідомо (пошук без фільтра версії).
     */
    public static String gameVersionOf(Instance instance) {
        String mc = InstanceCopies.minecraftVersionOf(instance);
        if (mc != null && !mc.isEmpty()) return mc;
        try {
            File manifest = new File(instance.getDir(), "manifest.json");
            if (manifest.isFile()) {
                String v = MAPPER.readTree(manifest).path("gameVersion").asText(null);
                if (v != null && !v.isEmpty()) return v;
            }
        } catch (IOException | RuntimeException ignored) {
            // невідомо
        }
        // 17.11.2026: модпак з CDN ще не встановлений (manifest.json немає): StalDayZ завжди 1.16.5, решта модпаків
        // GummerCraft мають версію в кінці назви (main-26.2)
        if (!instance.isBuiltIn() && !instance.isCopy()) {
            if (InstanceMods.packModsLocked(instance)) return STALDAYZ_GAME;
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("-([0-9]+[.][0-9]+([.][0-9]+)?)$").matcher(String.valueOf(instance.getName()));
            if (m.find()) return m.group(1);
        }
        return null;
    }

    /** Версія гри StalDayZ (Forge 1.16.5 з OptiFine). */
    static final String STALDAYZ_GAME = "1.16.5";

    /**
     * Завантажувачі шейдерів, з якими збірка їх покаже: Fabric і Quilt (і модпаки на Fabric) - Iris; Forge і NeoForge -
     * OptiFine чи Oculus (Oculus читає шейдери Iris). Vanilla - жодного (null).
     */
    public static String[] shaderLoadersOf(Instance instance) {
        if (instance.isVanilla()) return null;
        // StalDayZ: Forge 1.16.5 з OptiFine у модпаку (opt.jar); шейдери OptiFine і Iris одного формату
        if (instance.isForgeLike() || InstanceMods.packModsLocked(instance)) return new String[]{"iris", "optifine"};
        return new String[]{"iris"};
    }

    /**
     * Завантажувачі для фільтра Modrinth: шейдери (див. {@link #shaderLoadersOf}), моди: завантажувач збірки. Для
     * модів null означає «сюди моди з Modrinth не ставимо»: Vanilla і модпаки з CDN (сервер з ModWhiteList кікне за
     * чужий мод, а моди паку оновлює сам модпак). Ресурспаки: null (без фільтра).
     */
    public static String[] loadersOf(Kind kind, Instance instance) {
        if (kind == Kind.SHADER) return shaderLoadersOf(instance);
        if (kind == Kind.MODPACK) {
            // 17.11.2026: модпаки лише в розділах Fabric, Quilt, Forge, NeoForge і їхніх копіях (не в модпаках
            // GummerCraft і не в Modrinth-збірках: там «ще один модпак» плутав би)
            if (!(instance.isBuiltIn() || instance.isCopy()) || instance.isVanilla() || instance.isMrpack()) return null;
            String[] l = loadersOf(Kind.MOD, instance);
            return l == null ? null : new String[]{l[0]};
        }
        if (kind != Kind.MOD) return null;
        if (instance.isVanilla()) return null;
        // 17.11.2026: модпаки GummerCraft з CDN теж (вони на Fabric, сервер клієнтські моди не звіряє); StalDayZ ні:
        // його сервер звіряє моди (ModWhiteList), packBaseOf для нього дає null
        if (!instance.isBuiltIn() && !instance.isCopy()) {
            return InstanceCopies.packBaseOf(instance) != null ? new String[]{"fabric"} : null;
        }
        if (instance.isQuilt()) return new String[]{"quilt", "fabric"};
        if (instance.isFabric()) return new String[]{"fabric"};
        if (instance.isForgeLike()) {
            return com.gummer.launcher.forge.ForgeVersions.kindOf(instance) == com.gummer.launcher.forge.ForgeVersions.Kind.FORGE
                    ? new String[]{"forge"} : new String[]{"neoforge"};
        }
        if (instance.isMrpack()) {
            String l = com.gummer.launcher.modrinth.MrpackPacks.loaderOf(instance);
            if ("quilt".equals(l)) return new String[]{"quilt", "fabric"};
            if ("fabric".equals(l) || "forge".equals(l) || "neoforge".equals(l)) return new String[]{l};
        }
        return null;
    }

    /**
     * Чи вже стоїть цей проєкт у теці. Спершу журнал того, що лаунчер сам поставив з Modrinth
     * ({@value #INDEX} у теці гри: тека/ID проєкту -> файл, файл ще на місці чи вимкнений). Для решти файлів (поставлених
     * руками, модів модпака): назва починається зі slug, а далі розділювач і версія чи завантажувач
     * ({@code fabric-api-0.92.jar}, {@code sodium-fabric-0.5.jar}), а не інше слово ({@code sodium-extra-...} не є Sodium).
     */
    public static boolean looksInstalled(File dir, String projectId, String slug) {
        String recorded = projectId != null ? readIndex(dir).path(dir.getName() + "/" + projectId).asText(null) : null;
        if (recorded != null && (new File(dir, recorded).exists() || new File(dir, recorded + ".disabled").exists())) return true;
        if (slug == null || slug.length() < 3) return false;
        java.util.regex.Pattern p = java.util.regex.Pattern.compile(java.util.regex.Pattern.quote(slug.toLowerCase(Locale.ROOT))
                + "([-_+.](v?\\d|mc\\d|fabric|forge|neoforge|quilt|universal|release)|[.](jar|zip)).*");
        File[] files = dir.listFiles();
        if (files == null) return false;
        for (File f : files) {
            if (p.matcher(f.getName().toLowerCase(Locale.ROOT)).matches()) return true;
        }
        // 17.11.2026: у модпаках GummerCraft файли названі кодами (fap.jar), тож для модів ще за id мода: у більшості
        // модів він збігається зі slug на Modrinth (fabric-api, sodium, iris); ModInfo кешується, тож це дешево
        if ("mods".equalsIgnoreCase(dir.getName())) {
            String s = slug.toLowerCase(Locale.ROOT);
            if (providerOf(dir, s) != null || s.indexOf('-') >= 0 && providerOf(dir, s.replace('-', '_')) != null) return true;
            // 18.11.2026 (власник: збірка GummerCraft пропонувала поставити Architectury API і Better Advancements, які в
            // ній уже є): id мода часто інший, ніж slug. Спершу дешеві варіанти з опису мода (betteradvancements,
            // architectury), потім, не в потоці інтерфейсу, за хешем файлу на Modrinth (точно, навіть для fap.jar)
            if (s.indexOf('-') >= 0 && providerOf(dir, s.replace("-", "")) != null) return true;
            for (String tail : new String[]{"-api", "-mod", "-lib", "-library"}) {
                if (s.endsWith(tail) && s.length() > tail.length() + 2) {
                    String base = s.substring(0, s.length() - tail.length());
                    if (providerOf(dir, base) != null || providerOf(dir, base.replace("-", "")) != null || providerOf(dir, base.replace('-', '_')) != null) return true;
                }
            }
            if (projectId != null && !javax.swing.SwingUtilities.isEventDispatchThread()) return projectIdsIn(dir).contains(projectId);
        }
        return false;
    }

    /** sha1 -> id проєкту на Modrinth ("" - файл не з Modrinth). Хеш файлу рахується раз (ModSides.sha1Cached). */
    private static final Map<String, String> PROJECT_BY_SHA1 = new java.util.concurrent.ConcurrentHashMap<String, String>();
    /** Тека -> (підпис теки, id проєктів), щоб пошук на 20 результатів не перебирав теку 20 разів. */
    private static final Map<String, Object[]> PROJECTS_IN_DIR = new java.util.concurrent.ConcurrentHashMap<String, Object[]>();

    /**
     * 18.11.2026: id проєктів Modrinth усіх модів теки за їхніми хешами (один запит {@code POST version_files} на всі
     * нові файли, далі з пам'яті). Без інтернету: те, що вже відомо. Не викликати з потоку інтерфейсу.
     */
    public static java.util.Set<String> projectIdsIn(File dir) {
        File[] files = dir.listFiles();
        java.util.Set<String> out = new java.util.HashSet<String>();
        if (files == null) return out;
        String sig = dir.lastModified() + "|" + files.length;
        Object[] memo = PROJECTS_IN_DIR.get(dir.getAbsolutePath());
        if (memo != null && sig.equals(memo[0])) {
            @SuppressWarnings("unchecked") java.util.Set<String> known = (java.util.Set<String>) memo[1];
            return known;
        }
        Map<String, Boolean> hashes = new java.util.LinkedHashMap<String, Boolean>();
        java.util.List<String> unknown = new ArrayList<String>();
        for (File f : files) {
            String n = f.getName().toLowerCase(Locale.ROOT);
            if (!f.isFile() || !(n.endsWith(".jar") || n.endsWith(".jar" + InstanceMods.DISABLED))) continue;
            try {
                String h = ModSides.sha1Cached(f);
                hashes.put(h, Boolean.TRUE);
                if (!PROJECT_BY_SHA1.containsKey(h)) unknown.add(h);
            } catch (IOException e) {
                // файл зайнятий чи зник: без нього
            }
        }
        boolean complete = true;
        if (!unknown.isEmpty()) {
            try {
                Map<String, Object> body = new java.util.HashMap<String, Object>();
                body.put("hashes", unknown);
                body.put("algorithm", "sha1");
                JsonNode versions = MAPPER.readTree(HttpRequest.post(new URL(API + "version_files")).bodyJson(body)
                        .execute().expectResponseCode(200).returnContent().asString("UTF-8"));
                for (String h : unknown) PROJECT_BY_SHA1.put(h, versions.path(h).path("project_id").asText(""));
            } catch (IOException | InterruptedException | RuntimeException e) {
                complete = false; // Modrinth недоступний: лише відоме, і без запам'ятовування теки
                log.info("Modrinth hash lookup failed: " + e);
            }
        }
        for (String h : hashes.keySet()) {
            String pid = PROJECT_BY_SHA1.get(h);
            if (pid != null && !pid.isEmpty()) out.add(pid);
        }
        if (complete) PROJECTS_IN_DIR.put(dir.getAbsolutePath(), new Object[]{sig, out});
        return out;
    }

    /**
     * 24.11.2026: щойно скачаний мод {@code jar} (ще не в теці) уже є в теці під іншою назвою файлу: назва наявного
     * файлу (без {@code .disabled}), інакше null. Для CurseForge (curseforge.CurseBrowse), як нижче в {@link #install}.
     */
    public static String alreadyProvided(File dir, File jar) {
        File same = providerOf(dir, ModInfo.idOf(jar));
        if (same == null) return null;
        String n = same.getName();
        return n.endsWith(InstanceMods.DISABLED) ? n.substring(0, n.length() - InstanceMods.DISABLED.length()) : n;
    }

    /** Мод у теці (увімкнений чи вимкнений), який дає грі цей id, або null. */
    static File providerOf(File dir, String modId) {
        if (modId == null || modId.trim().isEmpty()) return null;
        String id = modId.trim().toLowerCase(Locale.ROOT);
        File[] files = dir.listFiles();
        if (files == null) return null;
        for (File f : files) {
            String n = f.getName().toLowerCase(Locale.ROOT);
            if (n.startsWith(".") || !(n.endsWith(".jar") || n.endsWith(".jar" + InstanceMods.DISABLED))) continue;
            if (ModInfo.of(f).provides.contains(id)) return f;
        }
        return null;
    }

    /** Журнал поставленого з Modrinth у теці гри збірки (поруч з mods, resourcepacks, shaderpacks). */
    public static final String INDEX = "gummer-modrinth.json";

    private static File indexFile(File dir) {
        return new File(dir.getAbsoluteFile().getParentFile(), INDEX);
    }

    private static synchronized JsonNode readIndex(File dir) {
        File f = indexFile(dir);
        try {
            if (f.isFile()) return MAPPER.readTree(f);
        } catch (IOException | RuntimeException ignored) {
            // зіпсований журнал: як порожній
        }
        return MAPPER.createObjectNode();
    }

    /** 17.11.2026: файл, який лаунчер поставив у цю теку з цього проєкту Modrinth (журнал {@value #INDEX}), або null. */
    public static String recordedFile(File dir, String projectId) {
        return projectId == null ? null : readIndex(dir).path(dir.getName() + "/" + projectId).asText(null);
    }

    /** 24.11.2026: і з CurseForge (curseforge.CurseBrowse, ID {@code cf-<id>}), журнал той самий. */
    public static synchronized void record(File dir, String projectId, String fileName) {
        try {
            JsonNode n = readIndex(dir);
            com.fasterxml.jackson.databind.node.ObjectNode o = n instanceof com.fasterxml.jackson.databind.node.ObjectNode
                    ? (com.fasterxml.jackson.databind.node.ObjectNode) n : MAPPER.createObjectNode();
            o.put(dir.getName() + "/" + projectId, fileName);
            File f = indexFile(dir);
            File tmp = new File(f.getPath() + ".tmp");
            MAPPER.writerWithDefaultPrettyPrinter().writeValue(tmp, o);
            Files.move(tmp.toPath(), f.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException | RuntimeException e) {
            log.log(java.util.logging.Level.FINE, "Could not record Modrinth install", e);
        }
    }

    /** Пошук; порожній запит - найпопулярніші. */
    public static Page search(Kind kind, String query, String gameVersion, String[] shaderLoaders, int offset)
            throws IOException, InterruptedException {
        return search(kind, query, gameVersion, shaderLoaders, offset, Filter.POPULAR);
    }

    /** Пошук з вибором «популярне / клієнт / сервер» (вибір лише для модів). */
    public static Page search(Kind kind, String query, String gameVersion, String[] shaderLoaders, int offset, Filter filter)
            throws IOException, InterruptedException {
        return search(kind, query, gameVersion, shaderLoaders, offset, filter, Sort.BEST, null);
    }

    /**
     * 17.11.2026 (власник: сторінка Modrinth «сортування: популярні, нові, оновлені; фільтри за категоріями»): як вище,
     * плюс порядок і категорія Modrinth ({@code adventure}, {@code optimization}...; null усі).
     */
    public static Page search(Kind kind, String query, String gameVersion, String[] shaderLoaders, int offset, Filter filter,
                              Sort sort, String category) throws IOException, InterruptedException {
        if (filter == null || kind != Kind.MOD) filter = Filter.POPULAR;
        if (sort == null) sort = Sort.BEST;
        if (category != null && !category.matches("[a-z0-9-]{2,32}")) category = null;
        String key = kind + "|" + filter + "|" + sort + "|" + category + "|" + (query == null ? "" : query.trim().toLowerCase(Locale.ROOT)) + "|" + gameVersion + "|"
                + (shaderLoaders == null ? "" : String.join(",", shaderLoaders)) + "|" + offset;
        Object[] hit = PAGES.get(key);
        if (hit != null && System.currentTimeMillis() - (Long) hit[0] < PAGE_TTL) return (Page) hit[1];
        long t0 = System.nanoTime();
        Page page = searchNow(kind, query, gameVersion, shaderLoaders, offset, filter, sort, category);
        PAGES.put(key, new Object[]{System.currentTimeMillis(), page});
        log.info("Modrinth search " + kind + (filter != Filter.POPULAR ? " " + filter : "") + " mc=" + gameVersion + " offset=" + offset + ": " + page.hits.size() + " of "
                + page.total + " in " + (System.nanoTime() - t0) / 1000000 + " ms");
        return page;
    }

    /** Іконка вже в пам'яті (або null): показати без потоку і мережі. */
    public static BufferedImage cachedIcon(String iconUrl) {
        return iconUrl == null ? null : ICONS.get(iconUrl);
    }

    private static Page searchNow(Kind kind, String query, String gameVersion, String[] shaderLoaders, int offset, Filter filter,
                                  Sort sort, String category) throws IOException, InterruptedException {
        StringBuilder facets = new StringBuilder("[[\"project_type:").append(kind.projectType).append("\"]");
        if (gameVersion != null) facets.append(",[\"versions:").append(gameVersion).append("\"]");
        if (kind != Kind.RESOURCEPACK && shaderLoaders != null) {
            facets.append(",[");
            for (int i = 0; i < shaderLoaders.length; i++) {
                if (i > 0) facets.append(',');
                facets.append("\"categories:").append(shaderLoaders[i]).append('"');
            }
            facets.append(']');
        }
        facets.append(filter.facets);
        if (category != null) facets.append(",[\"categories:").append(category).append("\"]");
        facets.append(']');
        String q = query == null ? "" : query.trim();
        String index = sort == Sort.NEWEST ? "newest" : sort == Sort.UPDATED ? "updated" : q.isEmpty() ? "downloads" : "relevance";
        String url = API + "search?limit=" + PAGE + "&offset=" + Math.max(0, offset)
                + "&index=" + index
                + "&facets=" + enc(facets.toString()) + (q.isEmpty() ? "" : "&query=" + enc(q));
        JsonNode root = getJson(url);
        Page page = new Page();
        page.total = root.path("total_hits").asInt(0);
        for (JsonNode h : root.path("hits")) {
            Hit hit = new Hit();
            hit.id = h.path("project_id").asText(null);
            hit.slug = h.path("slug").asText(null);
            hit.title = h.path("title").asText("?");
            hit.author = h.path("author").asText("");
            hit.description = h.path("description").asText("");
            hit.iconUrl = h.path("icon_url").asText(null);
            String featured = h.path("featured_gallery").asText("");
            if (featured.isEmpty() && h.path("gallery").isArray() && h.path("gallery").size() > 0) featured = h.path("gallery").get(0).asText("");
            hit.galleryUrl = featured.isEmpty() ? null : featured;
            hit.downloads = h.path("downloads").asLong(0);
            hit.side = ModSides.of(h.path("client_side").asText(null), h.path("server_side").asText(null));
            if (hit.id != null && hit.id.matches("[A-Za-z0-9]+")) page.hits.add(hit);
        }
        return page;
    }

    /**
     * Поставити найновішу версію проєкту під цю збірку в теку {@code dir}. Повертає назву файлу.
     *
     * @throws IOException з текстом для гравця, якщо версії під цю гру немає, файл не з Modrinth чи не збігся хеш
     */
    public static String install(Kind kind, Hit hit, String gameVersion, String[] shaderLoaders, File dir)
            throws IOException, InterruptedException {
        return install(kind, hit, gameVersion, shaderLoaders, dir, null);
    }

    /**
     * Як {@link #install(Kind, Hit, String, String[], File)}, і ще (для модів) ставить обов'язкові залежності
     * вибраної версії (наприклад Fabric API), якщо схожого файлу в теці ще немає. Глибина до 3, кожен проєкт раз.
     *
     * @param installed сюди дописуються назви всього поставленого (сам проєкт і залежності), може бути null
     */
    public static String install(Kind kind, Hit hit, String gameVersion, String[] shaderLoaders, File dir, List<String> installed)
            throws IOException, InterruptedException {
        return install(kind, hit, gameVersion, shaderLoaders, dir, installed, null, null);
    }

    /**
     * 17.11.2026: як вище, і ще звіт про залежності: {@code missing} обов'язкові, яких для цієї гри немає чи не вдалося
     * поставити (сам мод при цьому лишається, гравця треба попередити), {@code conflicts} уже наявні моди, з якими
     * цей мод несумісний (на Modrinth {@code incompatible}). Обидва можуть бути null.
     */
    public static String install(Kind kind, Hit hit, String gameVersion, String[] shaderLoaders, File dir, List<String> installed,
                                 List<String> missing, List<String> conflicts) throws IOException, InterruptedException {
        return install(kind, hit, gameVersion, shaderLoaders, dir, installed, missing, conflicts, new java.util.HashSet<String>(), 0);
    }

    private static String install(Kind kind, Hit hit, String gameVersion, String[] shaderLoaders, File dir, List<String> installed,
                                  List<String> missing, List<String> conflicts, java.util.Set<String> seen, int depth)
            throws IOException, InterruptedException {
        seen.add(hit.id);
        // 17.11.2026: мод без завантажувача не ставимо: без фільтра API віддає будь-яку версію, і для Fabric
        // приходили Forge-збірки залежностей (architectury-...-forge.jar), з якими гра падає на старті
        if (kind == Kind.MOD && (shaderLoaders == null || shaderLoaders.length == 0)) throw new IOException(NO_VERSION);
        StringBuilder url = new StringBuilder(API).append("project/").append(hit.id).append("/version");
        String sep = "?";
        if (gameVersion != null) {
            url.append(sep).append("game_versions=").append(enc("[\"" + gameVersion + "\"]"));
            sep = "&";
        }
        if (kind != Kind.RESOURCEPACK && shaderLoaders != null) {
            StringBuilder l = new StringBuilder("[");
            for (int i = 0; i < shaderLoaders.length; i++) {
                if (i > 0) l.append(',');
                l.append('"').append(shaderLoaders[i]).append('"');
            }
            url.append(sep).append("loaders=").append(enc(l.append(']').toString()));
        }
        JsonNode versions = getJson(url.toString());
        // API віддає від найновішої: перша стабільна (release), а якщо таких немає, найновіша бета
        List<JsonNode[]> releases = new ArrayList<JsonNode[]>(), others = new ArrayList<JsonNode[]>();
        for (JsonNode v : versions) {
            JsonNode primary = null;
            for (JsonNode f : v.path("files")) {
                if (f.path("primary").asBoolean(false)) primary = f;
            }
            if (primary == null && v.path("files").size() > 0) primary = v.path("files").get(0);
            if (primary == null) continue;
            ("release".equals(v.path("version_type").asText("release")) ? releases : others).add(new JsonNode[]{v, primary});
        }
        List<JsonNode[]> list = releases.isEmpty() ? others : releases;
        if (list.isEmpty()) throw new IOException(NO_VERSION);
        dir.mkdirs();
        // 25.11.2026 (тікет #122): мод ставиться найновішою версією, яка йде під завантажувач збірки (вимога з самого
        // jar, LoaderVersion): найновіший Create для Fabric 1.20.1 хоче Fabric Loader 0.17.2, Fabric API 0.92.12 хоче
        // 0.16.10, і на старішому гра не запускається. Вимога з часом лише росте, тож якщо найновіша не підходить,
        // двійковий пошук по списку: до MAX_LOADER_TRIES завантажень навіть на сотню версій
        LoaderVersion have = kind == Kind.MOD ? LoaderVersion.ofModsDir(dir) : null;
        String[] need = {null};
        int best = -1;
        File bestPart = null;
        try {
            Object first = fetchFitting(kind, hit, list.get(0), dir, have, need);
            if (first instanceof String) return (String) first;
            if (first != null) {
                best = 0;
                bestPart = (File) first;
            } else {
                int lo = 1, hi = list.size() - 1, tries = 1;
                while (lo <= hi && tries < MAX_LOADER_TRIES) {
                    int mid = (lo + hi) >>> 1;
                    tries++;
                    Object got = fetchFitting(kind, hit, list.get(mid), dir, have, need);
                    if (got instanceof String) return (String) got;
                    if (got != null) {
                        if (bestPart != null) bestPart.delete();
                        best = mid;
                        bestPart = (File) got;
                        hi = mid - 1;
                    } else {
                        lo = mid + 1;
                    }
                }
            }
            if (bestPart == null) {
                if (need[0] != null && have != null) throw new ModUpdates.LoaderTooOld(hit.title, have, need[0]);
                throw new IOException(NO_VERSION);
            }
            JsonNode chosen = list.get(best)[0];
            String fileName = list.get(best)[1].path("filename").asText("");
            File target = new File(dir, fileName);
            if (kind == Kind.MOD) {
                // Мод з таким id уже є під іншою назвою файлу (у копіях модпаків файли названі кодами: fap.jar це
                // Fabric API): другий такий самий мод гра не запустить, тож лишаємо наявний
                File same = providerOf(dir, ModInfo.idOf(bestPart));
                if (same != null) {
                    String existing = same.getName().endsWith(InstanceMods.DISABLED)
                            ? same.getName().substring(0, same.getName().length() - InstanceMods.DISABLED.length()) : same.getName();
                    log.info("Modrinth " + hit.slug + " is already here as " + same.getName() + ", not adding a second copy");
                    record(dir, hit.id, existing);
                    return existing;
                }
            }
            Files.move(bestPart.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE);
            record(dir, hit.id, fileName);
            log.info("Installed " + kind.projectType + " " + hit.slug + " -> " + target
                    + (best == 0 ? "" : " (" + chosen.path("version_number").asText("?") + ", the newest does not fit " + have + ")"));
            if (installed != null) installed.add(hit.title);
            // обов'язкові залежності мода (Fabric API, Cloth Config тощо) і несумісні
            if (kind == Kind.MOD) dependencies(chosen, gameVersion, shaderLoaders, dir, installed, missing, conflicts, seen, depth);
            return fileName;
        } finally {
            if (bestPart != null) bestPart.delete();
        }
    }

    /** Скільки найбільше версій качаємо, шукаючи ту, що йде під завантажувач збірки (двійковий пошук). */
    private static final int MAX_LOADER_TRIES = 8;

    /**
     * Скачати версію {@code c} ({версія, файл}) поруч як {@code .назва.part} і звірити sha512. Повертає: назву файлу
     * (String), якщо такий файл уже лежить у теці (нічого не качається, як було завжди); скачаний файл (File), якщо
     * він іде під завантажувач {@code have}; null, якщо ні (вимога дописується в {@code need[0]}, найменша з бачених).
     */
    private static Object fetchFitting(Kind kind, Hit hit, JsonNode[] c, File dir, LoaderVersion have, String[] need)
            throws IOException, InterruptedException {
        JsonNode file = c[1];
        String fileName = file.path("filename").asText("");
        String sha512 = file.path("hashes").path("sha512").asText(null);
        URL u = new URL(file.path("url").asText(""));
        if (!"https".equals(u.getProtocol()) || !CDN_HOST.equalsIgnoreCase(u.getHost())) throw new IOException("Not a Modrinth file: " + u.getHost());
        if (fileName.isEmpty() || fileName.contains("/") || fileName.contains("\\") || fileName.startsWith(".")
                || !fileName.toLowerCase(Locale.ROOT).endsWith(kind.extension)) {
            throw new IOException("Unexpected file name: " + fileName);
        }
        if (sha512 == null || !sha512.matches("[0-9a-fA-F]{128}")) throw new IOException("No sha512 for " + fileName);
        if (new File(dir, fileName).exists() || new File(dir, fileName + ".disabled").exists()) {
            record(dir, hit.id, fileName);
            return fileName;
        }
        File part = new File(dir, "." + fileName + ".part");
        boolean keep = false;
        try {
            HttpRequest.get(u).execute().expectResponseCode(200).saveContent(part);
            if (!sha(part, "SHA-512").equalsIgnoreCase(sha512)) throw new IOException("sha512 mismatch for " + fileName);
            if (kind == Kind.MOD && have != null && !have.fits(ModInfo.of(part))) {
                String n = have.needOf(ModInfo.of(part));
                log.info("Modrinth " + hit.slug + " " + c[0].path("version_number").asText("?") + " needs " + have.title() + " " + n
                        + ", the pack has " + have.version);
                if (need[0] == null || ModVersionFix.compare(n, need[0]) < 0) need[0] = n;
                return null;
            }
            keep = true;
            return part;
        } finally {
            if (!keep) part.delete();
        }
    }

    /**
     * 17.11.2026: залежності вибраної версії мода. Обов'язкові ставляться (до 3 рівнів, кожен проєкт раз), а якщо для
     * цієї гри їх немає, сам мод лишається, а назва йде в {@code missing}. Залежність, задана лише версією (без проєкту),
     * теж враховується. Несумісні, що вже лежать у теці, йдуть у {@code conflicts}. Дані про всі проєкти одним запитом.
     */
    static void dependencies(JsonNode version, String gameVersion, String[] loaders, File dir, List<String> installed,
                                     List<String> missing, List<String> conflicts, java.util.Set<String> seen, int depth)
            throws IOException, InterruptedException {
        java.util.Map<String, String> wanted = new java.util.LinkedHashMap<String, String>(); // проєкт -> required / incompatible
        for (JsonNode dep : version.path("dependencies")) {
            String type = dep.path("dependency_type").asText("");
            if (!"required".equals(type) && !"incompatible".equals(type)) continue;
            String pid = dep.path("project_id").asText(null);
            String vid = dep.path("version_id").asText(null);
            if ((pid == null || pid.isEmpty()) && vid != null && vid.matches("[A-Za-z0-9]+")) {
                try {
                    pid = getJson(API + "version/" + vid).path("project_id").asText(null);
                } catch (IOException e) {
                    pid = null;
                }
            }
            if (pid == null || !pid.matches("[A-Za-z0-9]+") || seen.contains(pid) || wanted.containsKey(pid)) continue;
            wanted.put(pid, type);
        }
        if (wanted.isEmpty()) return;
        JsonNode projects;
        try {
            projects = getJson(API + "projects?ids=" + enc(MAPPER.writeValueAsString(new ArrayList<String>(wanted.keySet()))));
        } catch (IOException e) {
            log.log(java.util.logging.Level.INFO, "Modrinth dependencies are unavailable", e);
            return;
        }
        for (JsonNode proj : projects) {
            String pid = proj.path("id").asText("");
            String type = wanted.get(pid);
            if (type == null || seen.contains(pid)) continue;
            Hit d = new Hit();
            d.id = pid;
            d.slug = proj.path("slug").asText(null);
            d.title = proj.path("title").asText(pid);
            boolean here = looksInstalled(dir, pid, d.slug);
            if ("incompatible".equals(type)) {
                if (here && conflicts != null) conflicts.add(d.title);
                continue;
            }
            seen.add(pid);
            if (!"mod".equals(proj.path("project_type").asText("mod")) || here || depth >= 3) continue;
            try {
                install(Kind.MOD, d, gameVersion, loaders, dir, installed, missing, conflicts, seen, depth + 1);
            } catch (ModUpdates.LoaderTooOld e) {
                // 25.11.2026 (тікет #122): жодна версія залежності не йде під завантажувач збірки; з CurseForge теж не
                // беремо (там вимоги до завантажувача не звіряються), гравця попередить вікно
                log.info("Modrinth dependency " + d.slug + " not installed: " + e.getMessage());
                if (missing != null) missing.add(d.title);
            } catch (IOException e) {
                // залежності під цю гру немає чи не скачалась: 24.11.2026 той самий мод з CurseForge, а якщо і там
                // немає, мод уже поставлено, гравця попередить вікно
                log.info("Modrinth dependency " + d.slug + " not installed: " + e.getMessage());
                if (com.gummer.launcher.curseforge.CurseBrowse.installSameAs(d, gameVersion, loaders, dir, installed, missing,
                        conflicts, seen, depth + 1) == null && missing != null) missing.add(d.title);
            }
        }
    }

    /**
     * 17.11.2026: поставити модпак як нову збірку: найновіша стабільна версія під цю гру і завантажувач, файл .mrpack
     * лише з cdn.modrinth.com зі звіркою sha512, далі звичайний імпорт (MrpackPacks). Повертає назву нової збірки.
     */
    /** 17.11.2026: одна версія модпака для вибору на сторінці Modrinth головного вікна. */
    public static final class PackVersion {
        public String id, number, name, type, date;
        /** 18.11.2026: список змін цієї версії від автора (markdown), може бути порожнім. */
        public String changelog;
        public final List<String> games = new ArrayList<String>();
        public final List<String> loaders = new ArrayList<String>();
        JsonNode file;
        /** 1.40.0: файл версії модпака CurseForge (curseforge.CurseForge.FileInfo), для Modrinth null. */
        public Object extra;

        /** Завантажувач, яким лаунчер поставить цю версію (fabric, quilt, forge, neoforge), або null. */
        public String loader() {
            for (String l : loaders) if (SUPPORTED_PACK_LOADERS.contains(l)) return l;
            return null;
        }

        /** Найновіша версія гри цієї версії модпака (зазвичай одна). */
        public String game() {
            return games.isEmpty() ? null : games.get(games.size() - 1);
        }
    }

    /** Завантажувачі, з якими лаунчер вміє ставити модпак (MrpackPacks). */
    static final java.util.Set<String> SUPPORTED_PACK_LOADERS = new java.util.HashSet<String>(
            java.util.Arrays.asList("fabric", "quilt", "forge", "neoforge"));
    private static final Map<String, Object[]> PACK_VERSIONS = new java.util.concurrent.ConcurrentHashMap<String, Object[]>();

    /**
     * 17.11.2026: усі версії модпака, які лаунчер може поставити (з файлом .mrpack і відомим завантажувачем), від
     * новішої. Для сторінки Modrinth: гравець вибирає версію, і модпак стає в розділ свого завантажувача.
     */
    public static List<PackVersion> modpackVersions(String projectId) throws IOException, InterruptedException {
        if (projectId == null || !projectId.matches("[A-Za-z0-9]+")) throw new IOException("Bad project id");
        Object[] hit = PACK_VERSIONS.get(projectId);
        if (hit != null && System.currentTimeMillis() - (Long) hit[0] < PAGE_TTL) {
            @SuppressWarnings("unchecked") List<PackVersion> cached = (List<PackVersion>) hit[1];
            return cached;
        }
        List<PackVersion> out = new ArrayList<PackVersion>();
        for (JsonNode v : getJson(API + "project/" + projectId + "/version")) {
            JsonNode primary = null;
            for (JsonNode f : v.path("files")) {
                if (f.path("primary").asBoolean(false) || primary == null && f.path("filename").asText("").endsWith(".mrpack")) primary = f;
            }
            if (primary == null || !primary.path("filename").asText("").toLowerCase(Locale.ROOT).endsWith(".mrpack")) continue;
            PackVersion pv = new PackVersion();
            pv.id = v.path("id").asText(null);
            pv.number = v.path("version_number").asText("?");
            pv.name = v.path("name").asText(pv.number);
            pv.type = v.path("version_type").asText("release");
            pv.date = v.path("date_published").asText("");
            pv.changelog = v.path("changelog").asText("");
            for (JsonNode g : v.path("game_versions")) pv.games.add(g.asText());
            for (JsonNode l : v.path("loaders")) pv.loaders.add(l.asText().toLowerCase(Locale.ROOT));
            pv.file = primary;
            if (pv.loader() != null && pv.game() != null) out.add(pv);
        }
        PACK_VERSIONS.put(projectId, new Object[]{System.currentTimeMillis(), out});
        return out;
    }

    private static final Map<String, Object[]> MOD_VERSIONS = new java.util.concurrent.ConcurrentHashMap<String, Object[]>();

    /**
     * 17.11.2026 (власник: «щоб гравець і моди міг качати прямо з Modrinth ... і обирати, в який вже скачаний модпак їх
     * встановити (який підходить по лоадеру та версії)»): усі версії мода з .jar і завантажувачем, який лаунчер уміє
     * (fabric, quilt, forge, neoforge), від новішої. З них видно, у які збірки мод можна поставити.
     */
    public static List<PackVersion> modVersions(String projectId) throws IOException, InterruptedException {
        if (projectId == null || !projectId.matches("[A-Za-z0-9]+")) throw new IOException("Bad project id");
        Object[] hit = MOD_VERSIONS.get(projectId);
        if (hit != null && System.currentTimeMillis() - (Long) hit[0] < PAGE_TTL) {
            @SuppressWarnings("unchecked") List<PackVersion> cached = (List<PackVersion>) hit[1];
            return cached;
        }
        List<PackVersion> out = new ArrayList<PackVersion>();
        for (JsonNode v : getJson(API + "project/" + projectId + "/version")) {
            JsonNode primary = null;
            for (JsonNode f : v.path("files")) {
                if (f.path("primary").asBoolean(false) || primary == null && f.path("filename").asText("").endsWith(".jar")) primary = f;
            }
            if (primary == null || !primary.path("filename").asText("").toLowerCase(Locale.ROOT).endsWith(".jar")) continue;
            PackVersion pv = new PackVersion();
            pv.id = v.path("id").asText(null);
            pv.number = v.path("version_number").asText("?");
            pv.name = v.path("name").asText(pv.number);
            pv.type = v.path("version_type").asText("release");
            pv.date = v.path("date_published").asText("");
            pv.changelog = v.path("changelog").asText("");
            for (JsonNode g : v.path("game_versions")) pv.games.add(g.asText());
            for (JsonNode l : v.path("loaders")) pv.loaders.add(l.asText().toLowerCase(Locale.ROOT));
            pv.file = primary;
            if (pv.loader() != null && pv.game() != null) out.add(pv);
        }
        MOD_VERSIONS.put(projectId, new Object[]{System.currentTimeMillis(), out});
        return out;
    }

    /** Чи є серед версій мода така, що підходить грі {@code game} і одному з завантажувачів {@code loaders}. */
    public static boolean fits(List<PackVersion> versions, String game, String[] loaders) {
        if (versions == null || game == null || loaders == null) return false;
        for (PackVersion v : versions) {
            if (!v.games.contains(game)) continue;
            for (String l : loaders) if (v.loaders.contains(l)) return true;
        }
        return false;
    }

    /** 17.11.2026: поставити саме цю версію модпака як нову збірку (див. {@link #installModpack}). */
    public static String installModpackVersion(com.skcraft.launcher.Launcher launcher, Hit hit, PackVersion version,
                                               com.gummer.launcher.modrinth.MrpackPacks importer) throws Exception {
        importer.preferVersion(version.id, version.number);
        return downloadModpack(launcher, hit, version.file, importer);
    }

    /** 17.11.2026: завантажити файл .mrpack цієї версії (для оновлення збірки), з перевіркою sha512. */
    public static File downloadPackFile(com.skcraft.launcher.Launcher launcher, PackVersion version,
                                        com.gummer.launcher.modrinth.MrpackPacks progress) throws Exception {
        JsonNode file = version.file;
        URL u = new URL(file.path("url").asText(""));
        String sha512 = file.path("hashes").path("sha512").asText(null);
        if (!"https".equals(u.getProtocol()) || !CDN_HOST.equalsIgnoreCase(u.getHost())) throw new IOException("Not a Modrinth file: " + u.getHost());
        if (sha512 == null || !sha512.matches("[0-9a-fA-F]{128}")) throw new IOException("No sha512 for the modpack");
        launcher.getTemporaryDir().mkdirs();
        File tmp = File.createTempFile("gummer-modpack-", ".mrpack", launcher.getTemporaryDir());
        try {
            HttpRequest req = HttpRequest.get(u);
            if (progress != null) progress.watch(req);
            try {
                req.execute().expectResponseCode(200).saveContent(tmp);
            } finally {
                if (progress != null) progress.watch(null);
            }
            if (!sha(tmp, "SHA-512").equalsIgnoreCase(sha512)) throw new IOException("sha512 mismatch for the modpack");
            return tmp;
        } catch (Exception e) {
            tmp.delete();
            throw e;
        }
    }

    public static String installModpack(com.skcraft.launcher.Launcher launcher, Hit hit, String gameVersion, String[] loaders,
                                        com.gummer.launcher.modrinth.MrpackPacks importer) throws Exception {
        StringBuilder url = new StringBuilder(API).append("project/").append(hit.id).append("/version?");
        if (gameVersion != null) url.append("game_versions=").append(enc("[\"" + gameVersion + "\"]")).append('&');
        if (loaders != null && loaders.length > 0) url.append("loaders=").append(enc("[\"" + loaders[0] + "\"]"));
        JsonNode file = null, fallback = null, chosen = null, fallbackVersion = null;
        for (JsonNode v : getJson(url.toString())) {
            JsonNode primary = null;
            for (JsonNode f : v.path("files")) {
                if (f.path("primary").asBoolean(false) || primary == null && f.path("filename").asText("").endsWith(".mrpack")) primary = f;
            }
            if (primary == null) continue;
            if ("release".equals(v.path("version_type").asText("release"))) {
                file = primary;
                chosen = v;
                break;
            }
            if (fallback == null) {
                fallback = primary;
                fallbackVersion = v;
            }
        }
        if (file == null) {
            file = fallback;
            chosen = fallbackVersion;
        }
        if (file == null) throw new IOException(NO_VERSION);
        if (chosen != null) importer.preferVersion(chosen.path("id").asText(null), chosen.path("version_number").asText(null));
        return downloadModpack(launcher, hit, file, importer);
    }

    private static String downloadModpack(com.skcraft.launcher.Launcher launcher, Hit hit, JsonNode file,
                                          com.gummer.launcher.modrinth.MrpackPacks importer) throws Exception {
        URL u = new URL(file.path("url").asText(""));
        String sha512 = file.path("hashes").path("sha512").asText(null);
        if (!"https".equals(u.getProtocol()) || !CDN_HOST.equalsIgnoreCase(u.getHost())) throw new IOException("Not a Modrinth file: " + u.getHost());
        if (sha512 == null || !sha512.matches("[0-9a-fA-F]{128}")) throw new IOException("No sha512 for the modpack");
        launcher.getTemporaryDir().mkdirs(); // на свіжому ПК теки ще немає
        File tmp = File.createTempFile("gummer-modpack-", ".mrpack", launcher.getTemporaryDir());
        try {
            // 17.11.2026: мегабайти файлу модпака видно одразу (раніше перші ~30 с вікно мовчало)
            importer.setStatus(com.skcraft.launcher.util.SharedLocale.tr("mrpack.status.connecting"), -1);
            HttpRequest req = HttpRequest.get(u);
            importer.watch(req);
            importer.preferTitle(hit.title);
            try {
                req.execute().expectResponseCode(200).saveContent(tmp);
            } finally {
                importer.watch(null);
            }
            importer.setStatus(com.skcraft.launcher.util.SharedLocale.tr("mrpack.status.reading"), -1);
            if (!sha(tmp, "SHA-512").equalsIgnoreCase(sha512)) throw new IOException("sha512 mismatch for the modpack");
            String name = importer.importFile(launcher, tmp, hit.id);
            savePackIcon(new File(launcher.getInstancesDir(), name), hit);
            savePackBackground(new File(launcher.getInstancesDir(), name), hit);
            return name;
        } finally {
            tmp.delete();
        }
    }

    /**
     * 17.11.2026: іконка проєкту модпака в теку збірки ({@link com.gummer.launcher.modrinth.MrpackPacks#PACK_ICON}, до
     * 128x128), щоб у списку була іконка самої збірки, а не Modrinth. Для збірок, поставлених раніше, викликається зі
     * сторінки Modrinth. Без мережі чи іконки нічого не робить (лишається іконка Modrinth).
     */
    public static void savePackIcon(File instanceDir, Hit hit) {
        if (hit == null || hit.iconUrl == null || instanceDir == null || !instanceDir.isDirectory()) return;
        File target = new File(instanceDir, com.gummer.launcher.modrinth.MrpackPacks.PACK_ICON);
        if (target.isFile()) return;
        try {
            BufferedImage src = icon(hit.iconUrl);
            if (src == null) return;
            int s = Math.min(128, Math.max(src.getWidth(), src.getHeight()));
            BufferedImage out = new BufferedImage(s, s, BufferedImage.TYPE_INT_ARGB);
            java.awt.Graphics2D g = out.createGraphics();
            try {
                java.awt.Image scaled = src.getWidth() == s && src.getHeight() == s ? src
                        : src.getScaledInstance(s, s, java.awt.Image.SCALE_AREA_AVERAGING); // без «драбинок» при зменшенні
                g.drawImage(scaled, 0, 0, null);
            } finally {
                g.dispose();
            }
            File tmp = new File(target.getPath() + ".tmp");
            if (!ImageIO.write(out, "png", tmp)) return;
            Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            Logger.getLogger(ModrinthBrowse.class.getName()).log(java.util.logging.Level.FINE, "Pack icon not saved", e);
        }
    }

    /**
     * 17.11.2026 ~23:15 (власник: «дуже бракує бекграундів, особливо збіркам, скачаним з модрінта. хоча б якогось скріна
     * з модпака»): головний скрін галереї модпака стає фоном збірки ({@link InstanceArt#BACKGROUND}, той самий файл, що
     * «Оформлення» у вікні «Збірка», тож гравець може поставити свій). Лише якщо фону ще немає; без мережі чи галереї
     * лишається фон завантажувача.
     */
    public static void savePackBackground(File instanceDir, Hit hit) {
        // galleryUrl може бути null (доповнення за projectId з mrpack.json): оригінал однаково береться з картки проєкту
        if (hit == null || hit.id == null || instanceDir == null || !instanceDir.isDirectory()) return;
        File target = new File(instanceDir, InstanceArt.BACKGROUND);
        if (target.isFile()) return;
        try {
            // пошук дає лише мініатюру галереї (…_350.webp, 350 px); оригінал (raw_url) з картки проєкту
            String url = hit.galleryUrl;
            if (!hit.id.matches("[A-Za-z0-9]+")) url = hit.galleryUrl; // 1.40.0: CurseForge (cf-…), картка Modrinth не потрібна
            else try {
                JsonNode best = null;
                for (JsonNode g : getJson(API + "project/" + enc(hit.id)).path("gallery")) {
                    if (best == null || g.path("featured").asBoolean(false) && !best.path("featured").asBoolean(false)) best = g;
                }
                if (best != null) url = best.path("raw_url").asText(best.path("url").asText(url));
            } catch (Exception e) {
                // лишається мініатюра, нижче вона відсіється за розміром
            }
            if (url == null) return;
            BufferedImage src = imageNow(url, 16 * 1024 * 1024);
            if (src == null || src.getWidth() < 640) return; // дрібна картинка на весь екран буде «миля»
            InstanceArt.writeBackground(src, target);
        } catch (Exception e) {
            Logger.getLogger(ModrinthBrowse.class.getName()).log(java.util.logging.Level.FINE, "Pack background not saved", e);
        }
    }

    /** Іконка проєкту (лише з cdn.modrinth.com), або null. */
    public static BufferedImage icon(String iconUrl) {
        if (iconUrl == null) return null;
        BufferedImage cached = ICONS.get(iconUrl);
        if (cached != null) return cached;
        BufferedImage img = iconNow(iconUrl);
        if (img != null) ICONS.put(iconUrl, img);
        return img;
    }

    private static BufferedImage iconNow(String iconUrl) {
        return imageNow(iconUrl, 2 * 1024 * 1024);
    }

    /** Картинка лише з cdn.modrinth.com, не більша за maxBytes (PNG, JPG, WebP), або null. */
    private static BufferedImage imageNow(String url, int maxBytes) {
        try {
            URL u = new URL(url);
            // 1.40.0: ще картинки CurseForge (іконки і скріни модпаків зі сторінки CurseForge)
            if (!"https".equals(u.getProtocol()) || !(CDN_HOST.equalsIgnoreCase(u.getHost())
                    || "media.forgecdn.net".equalsIgnoreCase(u.getHost()))) return null;
            byte[] data = HttpRequest.get(u).execute().expectResponseCode(200).returnContent().asBytes();
            if (data.length > maxBytes) return null;
            if (data.length > 12 && data[0] == 'R' && data[1] == 'I' && data[2] == 'F' && data[3] == 'F'
                    && data[8] == 'W' && data[9] == 'E' && data[10] == 'B' && data[11] == 'P') {
                return webp(data); // більшість іконок Modrinth у WebP, якого ImageIO в Java 8 не знає
            }
            return ImageIO.read(new ByteArrayInputStream(data));
        } catch (Exception e) {
            return null;
        }
    }

    private static final com.twelvemonkeys.imageio.plugins.webp.WebPImageReaderSpi WEBP = new com.twelvemonkeys.imageio.plugins.webp.WebPImageReaderSpi();

    /** WebP через TwelveMonkeys напряму, без реєстрації в ImageIO (не міняє читання інших картинок лаунчера). */
    private static BufferedImage webp(byte[] data) throws IOException {
        javax.imageio.ImageReader reader = WEBP.createReaderInstance(null);
        // 17.11.2026: напряму з масиву байтів; ImageIO.createImageInputStream ішов через тимчасовий файл, і розпаковка
        // однієї іконки 96x96 займала близько 75 мс процесора замість 2 мс
        try (javax.imageio.stream.ImageInputStream in = new com.twelvemonkeys.imageio.stream.ByteArrayImageInputStream(data)) {
            reader.setInput(in);
            return reader.read(0);
        } finally {
            reader.dispose();
        }
    }

    public static String projectUrl(Kind kind, Hit hit) {
        if (hit.pageUrl != null) return hit.pageUrl;
        return "https://modrinth.com/" + kind.projectType + "/" + (hit.slug != null ? hit.slug : hit.id);
    }

    /**
     * 24.11.2026 (власник: «в нас є два джерела, це потрібно використати»): обов'язкова залежність мода CurseForge, якої
     * там немає під цю гру (або автор дозволяє її лише на сайті), ставиться з Modrinth, якщо там той самий мод: збіг
     * slug або назви (без регістру, пробілів і розділових знаків), пошук Modrinth уже відфільтровано під цю гру і
     * завантажувач. Далі звичайне встановлення з Modrinth (sha512, її залежності, мод з тим самим id вдруге не
     * ставиться). Повертає назву файлу або null: не знайдено чи не вдалося, тоді гравця попереджають, як раніше.
     */
    public static String installSameAs(Hit dep, String gameVersion, String[] loaders, File dir, List<String> installed,
                                       List<String> missing, List<String> conflicts, java.util.Set<String> seen, int depth)
            throws InterruptedException {
        if (dep == null || dep.title == null || loaders == null || loaders.length == 0 || depth > 3) return null;
        try {
            Hit same = sameMod(dep, search(Kind.MOD, dep.title, gameVersion, loaders, 0).hits);
            if (same == null && dep.slug != null && dep.slug.matches("[a-z0-9_-]{2,64}")) {
                try {
                    JsonNode p = getJson(API + "project/" + dep.slug);
                    if ("mod".equals(p.path("project_type").asText(""))) {
                        Hit h = new Hit();
                        h.id = p.path("id").asText(null);
                        h.slug = p.path("slug").asText(null);
                        h.title = p.path("title").asText("");
                        same = sameMod(dep, java.util.Collections.singletonList(h));
                    }
                } catch (IOException notThere) {
                    // на Modrinth такого slug немає
                }
            }
            if (same == null || same.id == null || !same.id.matches("[A-Za-z0-9]+") || seen.contains(same.id)) return null;
            if (looksInstalled(dir, same.id, same.slug)) return same.title;
            seen.add(same.id);
            String file = install(Kind.MOD, same, gameVersion, loaders, dir, installed, missing, conflicts, seen, depth);
            log.info("Dependency " + dep.title + " installed from Modrinth instead (" + same.slug + ")");
            return file;
        } catch (IOException e) {
            log.info("Dependency " + dep.title + " is not on Modrinth either: " + e.getMessage());
            return null;
        }
    }

    /** Той самий проєкт серед знайдених в іншому джерелі: спершу за slug, потім за назвою; null, якщо немає. */
    public static Hit sameMod(Hit a, List<Hit> found) {
        if (a == null || found == null) return null;
        if (a.slug != null) {
            for (Hit h : found) if (h.slug != null && a.slug.equalsIgnoreCase(h.slug)) return h;
        }
        String t = plainName(a.title);
        if (t.length() < 3) return null;
        for (Hit h : found) if (t.equals(plainName(h.title))) return h;
        return null;
    }

    /** Назва без регістру, пробілів і розділових знаків, без хвоста в дужках: «Cloth Config API (Fabric/Forge)» -> «clothconfigapi». */
    static String plainName(String s) {
        if (s == null) return "";
        String n = s.replaceAll("\\s*[(\\[][^)\\]]*[)\\]]\\s*$", "");
        return n.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{Nd}]", "");
    }

    static JsonNode getJson(String url) throws IOException, InterruptedException {
        String body = HttpRequest.get(new URL(url)).execute().expectResponseCode(200).returnContent().asString("UTF-8");
        return MAPPER.readTree(body);
    }

    static String enc(String s) {
        try {
            return URLEncoder.encode(s, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }

    public static String sha(File f, String algo) throws IOException {
        MessageDigest md;
        try {
            md = MessageDigest.getInstance(algo);
        } catch (NoSuchAlgorithmException e) {
            throw new IOException(e);
        }
        byte[] buf = new byte[64 * 1024];
        try (InputStream in = new FileInputStream(f)) {
            int n;
            while ((n = in.read(buf)) > 0) md.update(buf, 0, n);
        }
        StringBuilder sb = new StringBuilder();
        for (byte b : md.digest()) sb.append(String.format("%02x", b));
        return sb.toString();
    }
}
