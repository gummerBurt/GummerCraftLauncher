/*
 * Copyright (C) 2021-2026 GummerCraft <http://gummercraft.fun>
 */

package com.gummer.launcher.curseforge;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gummer.launcher.manage.ModrinthBrowse;
import com.gummer.launcher.modrinth.MrpackPacks;
import com.gummer.launcher.util.Json;
import com.skcraft.launcher.Launcher;
import com.skcraft.launcher.util.HttpRequest;
import com.skcraft.launcher.util.SharedLocale;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 1.40.0 (24.11.2026, власник: «зроби тут вкладку CurseForge і скачку модпаків з курсфордж точно як з модрінта»):
 * сторінка модпаків CurseForge (та сама ModrinthPage у режимі CurseForge). Пошук, категорії і версії модпака через наш
 * проксі {@code cf.php} (ключ лише там), сам zip модпака і його файли напряму з CDN CurseForge, далі звичайний імпорт
 * zip-збірки ({@link MrpackPacks#importFile}). Збірка позначається {@code projectId = "cf-<id>"} у mrpack.json, тож
 * «Встановлено» і «Грати» працюють як у Modrinth і ID не перетинаються (у Modrinth лише літери й цифри).
 *
 * <p>Модпак, автор якого дозволяє качати його лише з сайту ({@code downloadUrl} порожній): той самий вікно, що для
 * заборонених модів ({@link CurseForge#blockedHandler()}), гравець качає zip на сайті, лаунчер підхоплює його з
 * «Завантажень» і ставить.</p>
 */
public final class CurseBrowse {

    private CurseBrowse() {
    }

    private static final ObjectMapper MAPPER = Json.newMapper();
    /** Префікс projectId збірок з CurseForge у mrpack.json. */
    public static final String ID_PREFIX = "cf-";

    public static boolean isCurseId(String projectId) {
        return projectId != null && projectId.startsWith(ID_PREFIX);
    }

    /** Порядок, як у сторінки Modrinth: популярні, нові, оновлені. */
    public enum Sort {BEST, NEWEST, UPDATED}

    /** 24.11.2026: лаунчер для запитів проксі з місць, де його не передають (залежності модів Modrinth). */
    private static volatile Launcher shared;

    public static void use(Launcher launcher) {
        shared = launcher;
    }

    /** 25.11.2026: лаунчер для запитів проксі (оновлення модів, докачування), або null до старту. */
    public static Launcher shared() {
        return shared;
    }

    /**
     * 25.11.2026 (власник: «обов'язково задіюємо і курсфордж ... чого немає на модрінті, потрібно спробувати знайти на
     * курс форджі та навпаки»): найновіший файл кожного мода під цю гру одним запитом на весь список (проксі
     * {@code modsLatest}). Завантажувачі за порядком переваги (Quilt, потім Fabric): для першого, де щось є.
     * modId -> {найновіший стабільний fileId або 0, найновіший будь-який fileId}. Старий проксі чи CurseForge
     * недоступний: IOException.
     */
    public static Map<Integer, int[]> latestFiles(Launcher launcher, java.util.Collection<Integer> modIds, String game,
                                                 String[] loaders) throws IOException, InterruptedException {
        Map<Integer, int[]> out = new HashMap<Integer, int[]>();
        if (modIds.isEmpty() || game == null || loaders == null || loaders.length == 0) return out;
        Map<String, Object> q = new LinkedHashMap<String, Object>();
        q.put("ids", new ArrayList<Integer>(modIds));
        q.put("game", game);
        q.put("loaders", java.util.Arrays.asList(loaders));
        JsonNode a = call(launcher, Collections.<String, Object>singletonMap("modsLatest", q));
        if (!a.has("latest")) throw new IOException("The CurseForge proxy does not know modsLatest yet");
        Map<Integer, int[]> best = new HashMap<Integer, int[]>(); // modId -> {ранг завантажувача, стабільний, будь-який}
        for (JsonNode x : a.path("latest")) {
            int mod = x.path("modId").asInt(0), file = x.path("file").asInt(0);
            int rank = java.util.Arrays.asList(loaders).indexOf(x.path("loader").asText(""));
            if (mod <= 0 || file <= 0 || rank < 0) continue;
            boolean stable = "release".equals(x.path("type").asText("release"));
            int[] b = best.get(mod);
            if (b == null || rank < b[0]) best.put(mod, b = new int[]{rank, 0, 0});
            if (rank > b[0]) continue;
            if (stable && file > b[1]) b[1] = file;
            if (file > b[2]) b[2] = file;
        }
        for (Map.Entry<Integer, int[]> e : best.entrySet()) out.put(e.getKey(), new int[]{e.getValue()[1], e.getValue()[2]});
        return out;
    }

    /** 25.11.2026: дата публікації файлу мода на CurseForge (ISO), з файлів під цю гру; null, якщо не знайдено. */
    public static String fileDate(Launcher launcher, int modId, int fileId, String game, String[] loaders)
            throws IOException, InterruptedException {
        if (launcher == null || loaders == null) return null;
        for (String l : loaders) {
            for (JsonNode f : projectFiles(launcher, ID_PREFIX + modId, game, l).path("files")) {
                if (f.path("id").asInt() == fileId) return f.path("date").asText(null);
            }
        }
        return null;
    }

    /**
     * 25.11.2026: поставити файл CurseForge {@code info} замість мода {@code old} (оновлення). Качається поруч
     * (заборонений автором для лаунчерів: точна копія з Modrinth за sha1, інакше вікно «Скачай з CurseForge»), звіряється
     * sha1, стара версія йде у сховище відкату ({@link com.gummer.launcher.manage.ModRollback}), вимкнений мод лишається
     * вимкненим. Далі обов'язкові залежності нової версії, як при встановленні з пошуку. Повертає новий файл.
     */
    public static File replace(Launcher launcher, File old, CurseForge.FileInfo info, String currentNumber, String game,
                               String[] loaders, List<String> missing, List<String> conflicts)
            throws IOException, InterruptedException {
        File dir = old.getParentFile();
        String fileName = info.fileName;
        if (fileName == null || fileName.isEmpty() || fileName.contains("/") || fileName.contains("\\") || fileName.startsWith(".")
                || !fileName.toLowerCase(java.util.Locale.ROOT).endsWith(".jar")) {
            throw new IOException("Unexpected file name: " + fileName);
        }
        boolean disabled = old.getName().toLowerCase(java.util.Locale.ROOT).endsWith(com.gummer.launcher.manage.InstanceMods.DISABLED);
        File target = new File(dir, disabled ? fileName + com.gummer.launcher.manage.InstanceMods.DISABLED : fileName);
        String hitId = ID_PREFIX + info.modId;
        CurseForge.ModrinthCopy exact = info.downloadUrl == null && info.sha1 != null
                ? CurseForge.modrinthCopies(Collections.singletonList(info.sha1)).get(info.sha1.toLowerCase(java.util.Locale.ROOT)) : null;
        File part = new File(dir, "." + fileName + ".part");
        try {
            download(info, exact, part, info.title());
            if (!target.equals(old) && target.exists()) {
                // нова версія вже лежить поруч (поставили руками): лишаємо її, прибираємо стару
                if (!com.gummer.launcher.manage.ModRollback.keep(old, target.getName(), currentNumber, hitId)) java.nio.file.Files.delete(old.toPath());
            } else if (target.equals(old)) {
                com.gummer.launcher.manage.ModRollback.keep(old, target.getName(), currentNumber, hitId);
                java.nio.file.Files.move(part.toPath(), target.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            } else {
                java.nio.file.Files.move(part.toPath(), target.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                if (!com.gummer.launcher.manage.ModRollback.keep(old, target.getName(), currentNumber, hitId)) java.nio.file.Files.deleteIfExists(old.toPath());
            }
        } finally {
            part.delete();
        }
        ModrinthBrowse.record(dir, hitId, fileName);
        java.util.logging.Logger.getLogger(CurseBrowse.class.getName()).info("Mod updated from CurseForge: " + old.getName() + " -> " + target.getName());
        // залежності нової версії: у projectFiles під цю гру (там і deps), лише якщо файл там є
        java.util.Set<String> seen = new java.util.HashSet<String>(Collections.singleton(hitId));
        for (String l : loaders) {
            JsonNode file = null;
            try {
                for (JsonNode f : projectFiles(launcher, hitId, game, l).path("files")) {
                    if (f.path("id").asInt() == info.id) file = f;
                }
            } catch (IOException e) {
                break;
            }
            if (file != null) {
                dependencies(launcher, file, game, loaders, dir, null, missing, conflicts, seen, 0);
                break;
            }
        }
        return target;
    }

    /**
     * 25.11.2026: обов'язкові залежності мода {@code modFile} за даними CurseForge (мод знайдено за fingerprint): для
     * «Докачати», коли мода немає на Modrinth. Нічого не знайдено чи CurseForge недоступний: нічого не робить.
     */
    public static void dependenciesOf(File modFile, String game, String[] loaders, File dir, List<String> installed,
                                      List<String> missing) throws InterruptedException {
        Launcher l = shared;
        if (l == null || modFile == null || !modFile.isFile() || game == null || loaders == null) return;
        try {
            long fp = CurseForge.fingerprint(modFile);
            int[] hit = CurseForge.matchFingerprints(l, Collections.singletonList(fp)).get(fp);
            if (hit == null || hit[1] <= 0) return;
            String hitId = ID_PREFIX + hit[1];
            java.util.Set<String> seen = new java.util.HashSet<String>(Collections.singleton(hitId));
            for (String loader : loaders) {
                for (JsonNode f : projectFiles(l, hitId, game, loader).path("files")) {
                    if (f.path("id").asInt() == hit[0]) {
                        dependencies(l, f, game, loaders, dir, installed, missing, null, seen, 0);
                        return;
                    }
                }
            }
        } catch (IOException e) {
            java.util.logging.Logger.getLogger(CurseBrowse.class.getName()).info("CurseForge dependencies unavailable for " + modFile.getName() + ": " + e.getMessage());
        }
    }

    /**
     * 24.11.2026 (власник: «чого немає на курсфордж, можна спробувати знайти на модрінт, і навпаки»): обов'язкова
     * залежність мода Modrinth, якої на Modrinth немає під цю гру, ставиться з CurseForge, якщо там той самий мод
     * (збіг slug або назви, {@link ModrinthBrowse#sameMod}). Далі звичайне встановлення з CurseForge (sha1, його
     * залежності). Повертає назву файлу або null: не знайдено чи не вдалося.
     */
    public static String installSameAs(ModrinthBrowse.Hit dep, String game, String[] loaders, File dir, List<String> installed,
                                       List<String> missing, List<String> conflicts, java.util.Set<String> seen, int depth)
            throws InterruptedException {
        Launcher l = shared;
        if (l == null || dep == null || dep.title == null || loaders == null || loaders.length == 0 || depth > 3) return null;
        try {
            String loader = loaders[loaders.length - 1]; // як пошук модів CurseForge у вікні «Збірка» (Quilt шукає Fabric)
            ModrinthBrowse.Hit same = ModrinthBrowse.sameMod(dep, search(l, ModrinthBrowse.Kind.MOD, dep.title, game, loader, null, Sort.BEST, 0).hits);
            if (same == null || seen.contains(same.id)) return null;
            if (ModrinthBrowse.looksInstalled(dir, same.id, same.slug)) return same.title;
            String file = installFile(l, ModrinthBrowse.Kind.MOD, same, game, loaders, dir, installed, missing, conflicts, seen, depth);
            java.util.logging.Logger.getLogger(CurseBrowse.class.getName()).info("Dependency " + dep.title + " installed from CurseForge instead (" + same.slug + ")");
            return file;
        } catch (IOException e) {
            java.util.logging.Logger.getLogger(CurseBrowse.class.getName()).info("Dependency " + dep.title + " is not on CurseForge either: " + e.getMessage());
            return null;
        }
    }

    private static JsonNode call(Launcher launcher, Map<String, Object> body) throws IOException, InterruptedException {
        String proxy = launcher.getProperties().getProperty("curseforgeProxyUrl", "").trim();
        if (proxy.isEmpty()) throw new IOException("curseforgeProxyUrl is not set");
        return MAPPER.readTree(HttpRequest.post(new URL(proxy)).bodyJson(body).execute()
                .expectResponseCode(200).returnContent().asString("UTF-8"));
    }

    private static final Map<String, Object[]> PAGES = new java.util.concurrent.ConcurrentHashMap<String, Object[]>();
    private static final long PAGE_TTL = 5 * 60 * 1000L;

    /**
     * Скільки результатів CurseForge віддає щонайбільше: {@code totalCount} у нього не більший за 10000, і далі 10000
     * пошук не гортає (перевірено 24.11.2026: модпаків, модів і ресурспаків «10000», шейдерів 976). Справжньої кількості
     * API не дає, тож лаунчер пише «понад 10 000».
     */
    public static final int MAX_RESULTS = 10000;

    /** Клас проєктів CurseForge для виду Modrinth: модпаки 4471, моди 6, ресурспаки 12, шейдери 6552. */
    public static int classOf(ModrinthBrowse.Kind kind) {
        switch (kind) {
            case MOD:
                return 6;
            case RESOURCEPACK:
                return 12;
            case SHADER:
                return 6552;
            default:
                return 4471;
        }
    }

    /** Розділ сайту CurseForge для адреси проєкту. */
    private static String sitePath(ModrinthBrowse.Kind kind) {
        switch (kind) {
            case MOD:
                return "mc-mods";
            case RESOURCEPACK:
                return "texture-packs";
            case SHADER:
                return "shaders";
            default:
                return "modpacks";
        }
    }

    /** Сторінка пошуку модпаків (20 штук), з пам'яті 5 хв. */
    public static ModrinthBrowse.Page search(Launcher launcher, String query, String game, String loader, Integer category,
                                             Sort sort, int offset) throws IOException, InterruptedException {
        return search(launcher, ModrinthBrowse.Kind.MODPACK, query, game, loader, category, sort, offset);
    }

    /**
     * 24.11.2026 (власник: «воно все має працювати ідентично для курсфордж як працювало для модрінта»): пошук модпаків,
     * модів, ресурспаків чи шейдерів CurseForge (20 штук), з пам'яті 5 хв. {@code loader} лише для модів і модпаків.
     * Після 10000 результатів CurseForge не гортає: тоді порожня сторінка.
     */
    public static ModrinthBrowse.Page search(Launcher launcher, ModrinthBrowse.Kind kind, String query, String game, String loader,
                                             Integer category, Sort sort, int offset) throws IOException, InterruptedException {
        if (offset + ModrinthBrowse.PAGE > MAX_RESULTS) {
            ModrinthBrowse.Page end = new ModrinthBrowse.Page();
            end.total = MAX_RESULTS;
            return end;
        }
        Map<String, Object> q = new LinkedHashMap<String, Object>();
        q.put("class", classOf(kind));
        q.put("q", query == null ? "" : query.trim());
        if (game != null) q.put("game", game);
        if (loader != null) q.put("loader", loader);
        if (category != null) q.put("category", category);
        q.put("sort", sort == Sort.NEWEST ? "new" : sort == Sort.UPDATED ? "updated" : "best");
        q.put("offset", offset);
        String key = q.toString();
        Object[] c = PAGES.get(key);
        if (c != null && System.currentTimeMillis() - (Long) c[0] < PAGE_TTL) return (ModrinthBrowse.Page) c[1];
        JsonNode a = call(launcher, Collections.<String, Object>singletonMap("search", q));
        ModrinthBrowse.Page page = new ModrinthBrowse.Page();
        page.total = a.path("total").asInt(0);
        for (JsonNode m : a.path("hits")) page.hits.add(hitOf(m, kind));
        if (PAGES.size() > 200) PAGES.clear();
        PAGES.put(key, new Object[]{System.currentTimeMillis(), page});
        return page;
    }

    private static ModrinthBrowse.Hit hitOf(JsonNode m, ModrinthBrowse.Kind kind) {
        ModrinthBrowse.Hit h = new ModrinthBrowse.Hit();
        h.id = ID_PREFIX + m.path("id").asInt();
        h.slug = m.path("slug").asText(null);
        h.title = m.path("name").asText("");
        h.author = m.path("author").asText("");
        h.description = m.path("summary").asText("");
        h.iconUrl = m.hasNonNull("icon") ? m.path("icon").asText(null) : null;
        h.galleryUrl = m.hasNonNull("gallery") ? m.path("gallery").asText(null) : null;
        h.downloads = m.path("downloads").asLong(0);
        String url = m.path("url").asText("");
        h.pageUrl = url.startsWith("https://www.curseforge.com/") ? url
                : "https://www.curseforge.com/minecraft/" + sitePath(kind) + "/" + (h.slug != null ? h.slug : "");
        return h;
    }

    private static final Map<Integer, List<Object[]>> CATEGORIES = new java.util.concurrent.ConcurrentHashMap<Integer, List<Object[]>>();

    /** Категорії модпаків: {id (Integer), назва англійською}, за абеткою. */
    public static List<Object[]> categories(Launcher launcher) throws IOException, InterruptedException {
        return categories(launcher, ModrinthBrowse.Kind.MODPACK);
    }

    /** Категорії цього виду (лише верхній рівень): {id (Integer), назва англійською}, за абеткою. */
    public static List<Object[]> categories(Launcher launcher, ModrinthBrowse.Kind kind) throws IOException, InterruptedException {
        int cls = classOf(kind);
        List<Object[]> c = CATEGORIES.get(cls);
        if (c != null) return c;
        List<Object[]> out = new ArrayList<Object[]>();
        for (JsonNode n : call(launcher, Collections.<String, Object>singletonMap("categories", cls)).path("categories")) {
            out.add(new Object[]{n.path("id").asInt(), n.path("name").asText("")});
        }
        CATEGORIES.put(cls, out);
        return out;
    }

    // ------------------------------------------------------------ моди, ресурспаки, шейдери

    private static final Map<String, Object[]> PROJECT_FILES = new java.util.concurrent.ConcurrentHashMap<String, Object[]>();

    /** Відповідь проксі {@code projectFiles} (файли під гру і завантажувач, mod, latest), з пам'яті 5 хв. */
    private static JsonNode projectFiles(Launcher launcher, String hitId, String game, String loader)
            throws IOException, InterruptedException {
        int id = numericId(hitId);
        String key = id + "|" + game + "|" + loader;
        Object[] c = PROJECT_FILES.get(key);
        if (c != null && System.currentTimeMillis() - (Long) c[0] < PAGE_TTL) return (JsonNode) c[1];
        Map<String, Object> q = new LinkedHashMap<String, Object>();
        q.put("id", id);
        if (game != null) q.put("game", game);
        if (loader != null) q.put("loader", loader);
        JsonNode a = call(launcher, Collections.<String, Object>singletonMap("projectFiles", q));
        if (PROJECT_FILES.size() > 300) PROJECT_FILES.clear();
        PROJECT_FILES.put(key, new Object[]{System.currentTimeMillis(), a});
        return a;
    }

    /**
     * Де є мод (для меню «Встановити в збірку» і «Нова збірка з цим модом»): найновіший файл на кожну пару гра +
     * завантажувач ({@code latestFilesIndexes} CurseForge), у вигляді версій Modrinth, щоб працювали ті самі
     * {@link ModrinthBrowse#fits} і вибір версії. Лише відомі лаунчеру завантажувачі.
     */
    public static List<ModrinthBrowse.PackVersion> modVersions(Launcher launcher, ModrinthBrowse.Hit hit)
            throws IOException, InterruptedException {
        List<ModrinthBrowse.PackVersion> out = new ArrayList<ModrinthBrowse.PackVersion>();
        for (JsonNode x : projectFiles(launcher, hit.id, null, null).path("latest")) {
            String game = x.path("game").asText(""), loader = x.path("loader").asText("");
            if (!game.matches("[0-9]+([.][0-9]+)+") || loader.isEmpty()) continue;
            ModrinthBrowse.PackVersion v = new ModrinthBrowse.PackVersion();
            v.id = String.valueOf(x.path("file").asInt());
            v.number = v.id;
            v.name = v.id;
            v.type = x.path("type").asText("release");
            v.games.add(game);
            v.loaders.add(loader);
            if (v.loader() != null) out.add(v);
        }
        return out;
    }

    /** Текст помилки «автор дозволяє цей файл лише на сайті, гравець його не скачав». */
    public static final String NOT_DOWNLOADED = "CF_NOT_DOWNLOADED";

    /**
     * 24.11.2026: як {@link ModrinthBrowse#install(ModrinthBrowse.Kind, ModrinthBrowse.Hit, String, String[], File,
     * List, List, List)}, але з CurseForge: найновіший стабільний файл (інакше найновіша бета) під цю гру і
     * завантажувач у теку {@code dir}, звірка sha1 (без sha1 розміру), журнал поставленого той самий
     * ({@link ModrinthBrowse#INDEX}, ID {@code cf-<id>}). Для модів ще обов'язкові залежності (до 3 рівнів, кожна раз) і
     * несумісні з уже наявними. Файл, який автор дозволяє лише на сайті: вікно «Скачай з CurseForge», як в імпорті
     * збірки. Повертає назву файлу.
     */
    public static String installFile(Launcher launcher, ModrinthBrowse.Kind kind, ModrinthBrowse.Hit hit, String game,
                                     String[] loaders, File dir, List<String> installed, List<String> missing,
                                     List<String> conflicts) throws IOException, InterruptedException {
        return installFile(launcher, kind, hit, game, loaders, dir, installed, missing, conflicts, new java.util.HashSet<String>(), 0, null);
    }

    /**
     * 26.11.2026 (власник: «дозволь обирати версії модів перед установкою»): як вище, але саме файл {@code fileId}, який
     * гравець вибрав зі списку ({@link #fileChoices}). null: як раніше, найновіший.
     */
    public static String installFile(Launcher launcher, ModrinthBrowse.Kind kind, ModrinthBrowse.Hit hit, String game,
                                     String[] loaders, File dir, List<String> installed, List<String> missing,
                                     List<String> conflicts, String fileId) throws IOException, InterruptedException {
        return installFile(launcher, kind, hit, game, loaders, dir, installed, missing, conflicts, new java.util.HashSet<String>(), 0, fileId);
    }

    /**
     * 26.11.2026: файли мода (ресурспака, шейдера) під цю гру і завантажувач збірки, від новішого, у вигляді версій
     * Modrinth: для вибору версії перед встановленням. {@code id} це id файлу CurseForge.
     */
    public static List<ModrinthBrowse.PackVersion> fileChoices(Launcher launcher, ModrinthBrowse.Kind kind, ModrinthBrowse.Hit hit,
                                                               String game, String[] loaders) throws IOException, InterruptedException {
        List<ModrinthBrowse.PackVersion> out = new ArrayList<ModrinthBrowse.PackVersion>();
        java.util.Set<String> ids = new java.util.HashSet<String>();
        String[] tries = kind == ModrinthBrowse.Kind.MOD && loaders != null ? loaders : new String[]{null};
        for (String l : tries) {
            for (JsonNode f : projectFiles(launcher, hit.id, game, l).path("files")) {
                boolean untaggedForge = "forge".equals(l) && f.path("loaders").size() == 0;
                if (l != null && !untaggedForge && !contains(f.path("loaders"), l)) continue;
                String id = String.valueOf(f.path("id").asInt());
                if (!ids.add(id)) continue;
                ModrinthBrowse.PackVersion v = new ModrinthBrowse.PackVersion();
                v.id = id;
                v.name = f.path("name").asText(f.path("fileName").asText(id));
                v.number = v.name;
                v.type = f.path("type").asText("release");
                v.date = f.path("date").asText("");
                if (game != null) v.games.add(game);
                if (l != null) v.loaders.add(l);
                out.add(v);
            }
        }
        return out;
    }

    /**
     * 26.11.2026 (сумісні версії з CurseForge, {@link com.gummer.launcher.manage.CompatFix}): лише сам файл {@code fileId}
     * мода {@code hitId} (без залежностей і без запису в журнал) у {@code part}, зі звіркою sha1. Повертає назву файлу,
     * як вона на CurseForge. Файл, який автор дозволяє лише на сайті і якого немає на Modrinth: IOException (без вікна).
     */
    public static String downloadFile(Launcher launcher, String hitId, String game, String[] loaders, String fileId, File part)
            throws IOException, InterruptedException {
        for (String l : loaders) {
            JsonNode a = projectFiles(launcher, hitId, game, l);
            for (JsonNode f : a.path("files")) {
                if (!fileId.equals(String.valueOf(f.path("id").asInt()))) continue;
                ModrinthBrowse.Hit h = new ModrinthBrowse.Hit();
                h.id = hitId;
                CurseForge.FileInfo info = fileInfo(f, a.path("mod"), numericId(hitId), h);
                String fileName = info.fileName;
                if (fileName.isEmpty() || fileName.contains("/") || fileName.contains("\\") || fileName.startsWith(".")
                        || !fileName.toLowerCase(java.util.Locale.ROOT).endsWith(".jar")) {
                    throw new IOException("Unexpected file name: " + fileName);
                }
                CurseForge.ModrinthCopy exact = info.downloadUrl == null && info.sha1 != null
                        ? CurseForge.modrinthCopies(Collections.singletonList(info.sha1)).get(info.sha1.toLowerCase(java.util.Locale.ROOT)) : null;
                if (info.downloadUrl == null && exact == null) throw new IOException(NOT_DOWNLOADED);
                download(info, exact, part, fileName);
                return fileName;
            }
        }
        throw new IOException(ModrinthBrowse.NO_VERSION);
    }

    private static String installFile(Launcher launcher, ModrinthBrowse.Kind kind, ModrinthBrowse.Hit hit, String game,
                                      String[] loaders, File dir, List<String> installed, List<String> missing,
                                      List<String> conflicts, java.util.Set<String> seen, int depth)
            throws IOException, InterruptedException {
        return installFile(launcher, kind, hit, game, loaders, dir, installed, missing, conflicts, seen, depth, null);
    }

    private static String installFile(Launcher launcher, ModrinthBrowse.Kind kind, ModrinthBrowse.Hit hit, String game,
                                      String[] loaders, File dir, List<String> installed, List<String> missing,
                                      List<String> conflicts, java.util.Set<String> seen, int depth, String fileId)
            throws IOException, InterruptedException {
        seen.add(hit.id);
        // як у Modrinth: мод без завантажувача не ставимо (прийшла б Forge-збірка у Fabric)
        if (kind == ModrinthBrowse.Kind.MOD && (loaders == null || loaders.length == 0)) throw new IOException(ModrinthBrowse.NO_VERSION);
        String[] tries = kind == ModrinthBrowse.Kind.MOD ? loaders : new String[]{null};
        JsonNode chosen = null, mod = null;
        for (String l : tries) {
            JsonNode a = projectFiles(launcher, hit.id, game, l);
            mod = a.path("mod");
            JsonNode fallback = null;
            for (JsonNode f : a.path("files")) {
                // 25.11.2026: старі файли (Minecraft 1.12.2 і раніше, Baubles 1.5.2) CurseForge не позначав завантажувачем
                // зовсім, а під фільтр Forge віддає їх сам; тоді інших завантажувачів ще не було, тож лише для Forge
                boolean untaggedForge = "forge".equals(l) && f.path("loaders").size() == 0;
                if (l != null && !untaggedForge && !contains(f.path("loaders"), l)) continue;
                if (fileId != null) {
                    // вибір гравця: лише цей файл
                    if (fileId.equals(String.valueOf(f.path("id").asInt()))) {
                        chosen = f;
                        break;
                    }
                    continue;
                }
                if ("release".equals(f.path("type").asText("release"))) {
                    chosen = f;
                    break;
                }
                if (fallback == null) fallback = f;
            }
            if (chosen == null) chosen = fallback;
            if (chosen != null) break;
        }
        if (chosen == null) throw new IOException(ModrinthBrowse.NO_VERSION);

        CurseForge.FileInfo info = fileInfo(chosen, mod, numericId(hit.id), hit);
        String fileName = info.fileName;
        if (fileName.isEmpty() || fileName.contains("/") || fileName.contains("\\") || fileName.startsWith(".")
                || !fileName.toLowerCase(java.util.Locale.ROOT).endsWith(kind.extension)) {
            throw new IOException("Unexpected file name: " + fileName);
        }
        dir.mkdirs();
        File target = new File(dir, fileName);
        if (target.exists() || new File(dir, fileName + ".disabled").exists()) {
            ModrinthBrowse.record(dir, hit.id, fileName);
            return fileName;
        }
        // 1.40.0: файл, який автор дозволяє лише на сайті CurseForge, спершу шукаємо на Modrinth як точну копію за sha1
        // (той самий файл байт у байт); для залежностей далі ще пошук того самого мода за назвою, і лише тоді браузер
        CurseForge.ModrinthCopy exact = info.downloadUrl == null && info.sha1 != null
                ? CurseForge.modrinthCopies(Collections.singletonList(info.sha1)).get(info.sha1.toLowerCase(java.util.Locale.ROOT)) : null;
        if (exact == null && depth > 0 && info.downloadUrl == null && kind == ModrinthBrowse.Kind.MOD) {
            // 24.11.2026: залежність, яку автор дозволяє лише на сайті CurseForge: якщо той самий мод є на Modrinth,
            // звідти, без браузера
            String mr = ModrinthBrowse.installSameAs(hit, game, loaders, dir, installed, missing, conflicts, seen, depth);
            if (mr != null) return mr;
        }
        File part = new File(dir, "." + fileName + ".part");
        try {
            download(info, exact, part, hit.title);
            if (kind == ModrinthBrowse.Kind.MOD) {
                // мод з таким id уже є під іншою назвою файлу: другого такого самого гра не запустить, лишаємо наявний
                String existing = ModrinthBrowse.alreadyProvided(dir, part);
                if (existing != null) {
                    ModrinthBrowse.record(dir, hit.id, existing);
                    return existing;
                }
            }
            java.nio.file.Files.move(part.toPath(), target.toPath(), java.nio.file.StandardCopyOption.ATOMIC_MOVE);
        } finally {
            part.delete();
        }
        ModrinthBrowse.record(dir, hit.id, fileName);
        java.util.logging.Logger.getLogger(CurseBrowse.class.getName()).info("Installed CurseForge " + kind.projectType + " " + hit.slug + " -> " + target);
        if (installed != null) installed.add(hit.title);
        if (kind == ModrinthBrowse.Kind.MOD) {
            dependencies(launcher, chosen, game, loaders, dir, installed, missing, conflicts, seen, depth);
        }
        return fileName;
    }

    /**
     * Файл CurseForge у {@code part}: напряму з CDN CurseForge, або точна копія з Modrinth ({@code exact}), або (автор
     * дозволяє лише сайт) через вікно «Скачай з CurseForge». Далі звірка sha1 (без sha1 розміру).
     */
    private static void download(CurseForge.FileInfo info, CurseForge.ModrinthCopy exact, File part, String title)
            throws IOException, InterruptedException {
        if (info.downloadUrl != null) {
            HttpRequest.get(CurseForge.fileUrl(info.downloadUrl)).execute().expectResponseCode(200).saveContent(part);
        } else if (exact != null) {
            HttpRequest.get(new URL(exact.url)).execute().expectResponseCode(200).saveContent(part);
            if (!CurseForge.sha512(part).equalsIgnoreCase(exact.sha512)) throw new IOException("sha512 mismatch for " + info.fileName);
        } else {
            CurseForge.BlockedHandler h = CurseForge.blockedHandler();
            if (h == null || info.length <= 0) throw new IOException(NOT_DOWNLOADED);
            CurseForge.Blocked b = new CurseForge.Blocked(info, part);
            h.resolve(title, Collections.singletonList(b));
            if (!b.isDone()) throw new IOException(NOT_DOWNLOADED);
        }
        if (info.sha1 != null) {
            if (!CurseForge.sha1(part).equalsIgnoreCase(info.sha1)) throw new IOException("sha1 mismatch for " + info.fileName);
        } else if (info.length > 0 && part.length() != info.length) {
            throw new IOException("Size mismatch for " + info.fileName);
        }
    }

    /** Обов'язкові залежності файлу ставляться, несумісні, що вже є в теці, йдуть у {@code conflicts}. */
    private static void dependencies(Launcher launcher, JsonNode file, String game, String[] loaders, File dir,
                                     List<String> installed, List<String> missing, List<String> conflicts,
                                     java.util.Set<String> seen, int depth) throws InterruptedException {
        for (JsonNode d : file.path("deps")) {
            String id = ID_PREFIX + d.path("modId").asInt();
            boolean required = "required".equals(d.path("type").asText(""));
            if (seen.contains(id) || !required && conflicts == null) continue;
            ModrinthBrowse.Hit dep = new ModrinthBrowse.Hit();
            dep.id = id;
            try {
                JsonNode mod = projectFiles(launcher, id, null, null).path("mod");
                dep.slug = mod.path("slug").asText(null);
                dep.title = mod.path("name").asText(id);
            } catch (IOException e) {
                // не мод (наприклад, модпак) чи CurseForge не відповів: без нього, як у Modrinth
                java.util.logging.Logger.getLogger(CurseBrowse.class.getName()).info("CurseForge dependency " + id + " unavailable: " + e.getMessage());
                continue;
            }
            boolean here = ModrinthBrowse.looksInstalled(dir, id, dep.slug);
            if (!required) {
                if (here) conflicts.add(dep.title);
                continue;
            }
            seen.add(id);
            if (here || depth >= 3) continue;
            try {
                installFile(launcher, ModrinthBrowse.Kind.MOD, dep, game, loaders, dir, installed, missing, conflicts, seen, depth + 1);
            } catch (IOException e) {
                java.util.logging.Logger.getLogger(CurseBrowse.class.getName()).info("CurseForge dependency " + dep.slug + " not installed: " + e.getMessage());
                // 24.11.2026: тоді той самий мод з Modrinth, і лише якщо там теж немає, попередження
                if (ModrinthBrowse.installSameAs(dep, game, loaders, dir, installed, missing, conflicts, seen, depth + 1) == null
                        && missing != null) missing.add(dep.title);
            }
        }
    }

    private static boolean contains(JsonNode array, String value) {
        for (JsonNode n : array) if (value.equalsIgnoreCase(n.asText())) return true;
        return false;
    }

    /** Дані файлу для завантаження: адреса лише з CDN CurseForge по https і лише якщо автор дозволяє лаунчери. */
    private static CurseForge.FileInfo fileInfo(JsonNode f, JsonNode mod, int modId, ModrinthBrowse.Hit hit) throws IOException {
        CurseForge.FileInfo info = new CurseForge.FileInfo();
        info.id = f.path("id").asInt();
        info.modId = modId;
        info.fileName = f.path("fileName").asText("");
        info.displayName = f.path("name").asText("");
        info.downloadUrl = f.hasNonNull("downloadUrl") ? f.path("downloadUrl").asText(null) : null;
        info.sha1 = f.hasNonNull("sha1") ? f.path("sha1").asText(null) : null;
        info.length = f.path("length").asLong(0);
        info.modName = mod.path("name").asText(hit.title != null ? hit.title : "");
        info.modUrl = mod.path("url").asText(hit.pageUrl != null ? hit.pageUrl : "");
        info.author = mod.path("author").asText(hit.author != null ? hit.author : "");
        info.classId = mod.path("classId").asInt(6);
        if (!mod.path("allowModDistribution").asBoolean(true)) info.downloadUrl = null;
        if (info.downloadUrl != null) {
            URL u = new URL(info.downloadUrl);
            if (!"https".equals(u.getProtocol()) || !CurseForge.HOSTS.contains(u.getHost().toLowerCase(java.util.Locale.ROOT))) {
                info.downloadUrl = null;
            }
        }
        return info;
    }

    /**
     * 24.11.2026: модпак з вікна «Збірка» (вкладка «Модпаки», «Знайти на CurseForge»): найновіша стабільна версія під
     * цю гру і завантажувач (інакше найновіша бета), далі як {@link #install}. Повертає назву нової збірки.
     */
    public static String installModpack(Launcher launcher, ModrinthBrowse.Hit hit, String game, String[] loaders,
                                        MrpackPacks importer) throws Exception {
        ModrinthBrowse.PackVersion pick = null, fallback = null;
        for (ModrinthBrowse.PackVersion v : versions(launcher, hit)) {
            if (game != null && !v.games.contains(game)) continue;
            if (loaders != null && loaders.length > 0 && !loaders[0].equals(v.loader())) continue;
            if ("release".equals(v.type)) {
                pick = v;
                break;
            }
            if (fallback == null) fallback = v;
        }
        if (pick == null) pick = fallback;
        if (pick == null) throw new IOException(ModrinthBrowse.NO_VERSION);
        return install(launcher, hit, pick, importer);
    }

    private static final Map<String, Object[]> VERSIONS = new java.util.concurrent.ConcurrentHashMap<String, Object[]>();

    /** Версії модпака, які лаунчер уміє поставити (відомий завантажувач і версія гри), від новішої; з пам'яті 10 хв. */
    public static List<ModrinthBrowse.PackVersion> versions(Launcher launcher, ModrinthBrowse.Hit hit)
            throws IOException, InterruptedException {
        int id = numericId(hit.id);
        Object[] c = VERSIONS.get(hit.id);
        if (c != null && System.currentTimeMillis() - (Long) c[0] < 10 * 60 * 1000L) {
            @SuppressWarnings("unchecked") List<ModrinthBrowse.PackVersion> l = (List<ModrinthBrowse.PackVersion>) c[1];
            return l;
        }
        JsonNode a = call(launcher, Collections.<String, Object>singletonMap("packFiles", id));
        JsonNode mod = a.path("mod");
        List<ModrinthBrowse.PackVersion> out = new ArrayList<ModrinthBrowse.PackVersion>();
        for (JsonNode f : a.path("files")) {
            ModrinthBrowse.PackVersion v = new ModrinthBrowse.PackVersion();
            v.id = String.valueOf(f.path("id").asInt());
            v.name = f.path("name").asText("");
            v.number = shortName(v.name, hit.title);
            v.type = f.path("type").asText("release");
            v.date = f.path("date").asText("");
            for (JsonNode g : f.path("games")) v.games.add(g.asText());
            for (JsonNode l : f.path("loaders")) v.loaders.add(l.asText());
            if (v.loader() == null || v.games.isEmpty()) continue;
            Collections.sort(v.games, CurseBrowse::compareGame); // game() бере останню, тобто найновішу
            CurseForge.FileInfo info = new CurseForge.FileInfo();
            info.id = f.path("id").asInt();
            info.modId = id;
            info.fileName = f.path("fileName").asText("");
            info.displayName = v.name;
            info.downloadUrl = f.hasNonNull("downloadUrl") ? f.path("downloadUrl").asText(null) : null;
            info.sha1 = f.hasNonNull("sha1") ? f.path("sha1").asText(null) : null;
            info.length = f.path("length").asLong(0);
            info.modName = hit.title;
            info.modUrl = mod.path("url").asText(hit.pageUrl != null ? hit.pageUrl : "");
            info.author = hit.author;
            info.classId = 4471;
            if (!mod.path("allowModDistribution").asBoolean(true)) info.downloadUrl = null;
            if (info.downloadUrl != null) {
                URL u = new URL(info.downloadUrl);
                if (!"https".equals(u.getProtocol()) || !CurseForge.HOSTS.contains(u.getHost().toLowerCase(java.util.Locale.ROOT))) {
                    info.downloadUrl = null;
                }
            }
            v.extra = info;
            out.add(v);
        }
        VERSIONS.put(hit.id, new Object[]{System.currentTimeMillis(), out});
        return out;
    }

    /**
     * Назва версії для списку: як її назвав автор («15.0.0-beta.4 for 26.3»), без «.zip» і без назви модпака на початку
     * («Better MC [FORGE] BMC4 v52» стає «v52»), якщо після неї щось лишається.
     */
    static String shortName(String fileName, String packTitle) {
        if (fileName == null) return "";
        String n = fileName.replaceAll("(?i)\\.zip$", "").trim();
        if (packTitle != null && !packTitle.isEmpty() && n.toLowerCase(java.util.Locale.ROOT).startsWith(packTitle.toLowerCase(java.util.Locale.ROOT))) {
            String rest = n.substring(packTitle.length()).replaceAll("^[\\s_:-]+", "").trim();
            if (!rest.isEmpty()) return rest;
        }
        return n;
    }

    private static int compareGame(String a, String b) {
        String[] x = a.split("\\."), y = b.split("\\.");
        for (int i = 0; i < Math.max(x.length, y.length); i++) {
            int p = i < x.length ? parse(x[i]) : 0, q = i < y.length ? parse(y[i]) : 0;
            if (p != q) return Integer.compare(p, q);
        }
        return 0;
    }

    private static int parse(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    static int numericId(String hitId) throws IOException {
        if (!isCurseId(hitId) || !hitId.substring(ID_PREFIX.length()).matches("[0-9]{1,10}")) throw new IOException("Bad CurseForge id " + hitId);
        return Integer.parseInt(hitId.substring(ID_PREFIX.length()));
    }

    /**
     * Поставити цю версію модпака як нову збірку: zip з CDN CurseForge (звірка sha1, інакше розміру) або, якщо автор
     * дозволяє лише сайт, через вікно «Скачай з CurseForge»; далі імпорт zip-збірки. Повертає назву збірки.
     */
    public static String install(Launcher launcher, ModrinthBrowse.Hit hit, ModrinthBrowse.PackVersion version,
                                 MrpackPacks importer) throws Exception {
        CurseForge.FileInfo f = (CurseForge.FileInfo) version.extra;
        if (f == null) throw new IOException("No CurseForge file");
        launcher.getTemporaryDir().mkdirs();
        File tmp = File.createTempFile("gummer-cfpack-", ".zip", launcher.getTemporaryDir());
        try {
            importer.preferVersion(version.id, version.number);
            importer.preferTitle(hit.title);
            if (f.downloadUrl != null) {
                importer.setStatus(SharedLocale.tr("mrpack.status.connecting"), -1);
                HttpRequest req = HttpRequest.get(CurseForge.fileUrl(f.downloadUrl));
                importer.watch(req);
                try {
                    req.execute().expectResponseCode(200).saveContent(tmp);
                } finally {
                    importer.watch(null);
                }
            } else {
                CurseForge.BlockedHandler h = CurseForge.blockedHandler();
                if (h == null || f.length <= 0) throw new IOException("The author allows this modpack only on the CurseForge website");
                CurseForge.Blocked b = new CurseForge.Blocked(f, tmp);
                importer.setStatus(SharedLocale.tr("curseforge.status.blocked"), -1);
                h.resolve(hit.title, Collections.singletonList(b));
                if (!b.isDone()) throw new InterruptedException("The modpack file was not downloaded");
            }
            importer.setStatus(SharedLocale.tr("mrpack.status.reading"), -1);
            if (f.sha1 != null) {
                if (!CurseForge.sha1(tmp).equalsIgnoreCase(f.sha1)) throw new IOException("sha1 mismatch for the modpack");
            } else if (f.length > 0 && tmp.length() != f.length) {
                throw new IOException("Size mismatch for the modpack");
            }
            String name = importer.importFile(launcher, tmp, hit.id);
            File dir = new File(launcher.getInstancesDir(), name);
            ModrinthBrowse.savePackIcon(dir, hit);
            ModrinthBrowse.savePackBackground(dir, hit);
            return name;
        } finally {
            tmp.delete();
        }
    }

}
