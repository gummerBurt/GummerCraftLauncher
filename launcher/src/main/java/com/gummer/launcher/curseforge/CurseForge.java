/*
 * Copyright (C) 2021-2026 GummerCraft <http://gummercraft.fun>
 */

package com.gummer.launcher.curseforge;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gummer.launcher.util.Json;
import com.skcraft.launcher.Launcher;
import com.skcraft.launcher.util.HttpRequest;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

/**
 * 1.40.0 (24.11.2026): збірки CurseForge (zip з {@code manifest.json}). Ключ CurseForge API лише на нашому CDN
 * ({@code cf.php}, адреса {@code curseforgeProxyUrl} у launcher.properties): лаунчер надсилає ID файлів зі збірки й отримує
 * назви, прямі посилання, sha1 і дозвіл автора. Самі файли гравець качає напряму з CDN CurseForge.
 *
 * <p>Моди, автор яких заборонив сторонні завантаження ({@code allowModDistribution: false}, тоді {@code downloadUrl}
 * порожній), лаунчер не качає і не шукає їм обхідних адрес: гравець відкриває сторінку файлу на сайті CurseForge, сайт
 * сам качає файл у «Завантаження», а лаунчер знаходить його там за sha1 і кладе в збірку (як Prism Launcher).</p>
 */
public final class CurseForge {

    private CurseForge() {
    }

    private static final Logger log = Logger.getLogger(CurseForge.class.getName());
    private static final ObjectMapper MAPPER = Json.newMapper();

    /** Адреси, з яких беремо файли CurseForge (edge переадресовує на mediafilez). */
    public static final List<String> HOSTS = Arrays.asList("edge.forgecdn.net", "mediafilez.forgecdn.net");

    /** Класи проєктів CurseForge -> тека в грі. Невідомий клас: mods. */
    private static final Map<Integer, String> FOLDERS = new HashMap<Integer, String>();

    static {
        FOLDERS.put(6, "mods");
        FOLDERS.put(12, "resourcepacks");
        FOLDERS.put(6552, "shaderpacks");
        FOLDERS.put(6945, "datapacks");
    }

    /** Файл збірки з даними проксі. */
    public static final class FileInfo {
        public int id;
        public int modId;
        public String fileName = "";
        public String displayName = "";
        public String downloadUrl;
        public String sha1;
        public long length;
        public String modName = "";
        public String modUrl = "";
        public String author = "";
        public int classId;

        /** Тека в грі для цього файлу. */
        public String folder() {
            String f = FOLDERS.get(classId);
            return f != null ? f : "mods";
        }

        /** Сторінка завантаження цього файлу на сайті CurseForge (сайт сам починає завантаження). */
        public String downloadPage() {
            String base = modUrl;
            if (base == null || !base.startsWith("https://www.curseforge.com/")) {
                return "https://www.curseforge.com/projects/" + modId;
            }
            return base.replaceAll("/+$", "") + "/download/" + id;
        }

        public String title() {
            return modName != null && !modName.isEmpty() ? modName : fileName;
        }
    }

    /** Заборонений автором для сторонніх лаунчерів файл, який гравець качає сам. */
    public static final class Blocked {
        public final FileInfo file;
        public final File target;
        private volatile boolean done;

        public Blocked(FileInfo file, File target) {
            this.file = file;
            this.target = target;
        }

        public boolean isDone() {
            return done;
        }

        /** Файл уже лежить у {@link #target} (узято з Modrinth чи з «Завантажень»). */
        public void markDone() {
            done = true;
        }

        /** 1.40.0: id проєкту Modrinth, якщо файл узято звідти (для mrpack-files.json), або null. */
        public volatile String modrinthProject;
    }

    /**
     * Вікно для заборонених файлів (малює launcher-flat). Викликається з фонового потоку імпорту й повертається, коли
     * гравець закрив вікно (усе знайдено або «Продовжити без них»).
     */
    public interface BlockedHandler {
        void resolve(String packTitle, List<Blocked> files) throws InterruptedException;
    }

    private static volatile BlockedHandler handler;

    public static void setBlockedHandler(BlockedHandler h) {
        handler = h;
    }

    public static BlockedHandler blockedHandler() {
        return handler;
    }

    /** Дані файлів за ID (через наш проксі, ключ CurseForge лише там). Порядок як у {@code ids}. */
    public static List<FileInfo> lookup(Launcher launcher, List<Integer> ids) throws IOException, InterruptedException {
        String proxy = launcher.getProperties().getProperty("curseforgeProxyUrl", "").trim();
        if (proxy.isEmpty()) throw new IOException("curseforgeProxyUrl is not set");
        Map<Integer, FileInfo> byId = new HashMap<Integer, FileInfo>();
        Map<Integer, JsonNode> mods = new HashMap<Integer, JsonNode>();
        for (int from = 0; from < ids.size(); from += 1000) {
            List<Integer> chunk = ids.subList(from, Math.min(ids.size(), from + 1000));
            Map<String, Object> body = Collections.<String, Object>singletonMap("files", chunk);
            JsonNode answer = MAPPER.readTree(HttpRequest.post(new URL(proxy)).bodyJson(body).execute()
                    .expectResponseCode(200).returnContent().asString("UTF-8"));
            for (JsonNode m : answer.path("mods")) mods.put(m.path("id").asInt(), m);
            for (JsonNode f : answer.path("files")) {
                FileInfo i = new FileInfo();
                i.id = f.path("id").asInt();
                i.modId = f.path("modId").asInt();
                i.fileName = f.path("fileName").asText("");
                i.displayName = f.path("displayName").asText("");
                i.downloadUrl = f.hasNonNull("downloadUrl") ? f.path("downloadUrl").asText(null) : null;
                i.sha1 = f.hasNonNull("sha1") ? f.path("sha1").asText(null) : null;
                i.length = f.path("length").asLong(0);
                byId.put(i.id, i);
            }
        }
        List<FileInfo> out = new ArrayList<FileInfo>();
        for (Integer id : ids) {
            FileInfo i = byId.get(id);
            if (i == null) throw new IOException("CurseForge has no file " + id + " (removed by the author?)");
            JsonNode m = mods.get(i.modId);
            if (m != null) {
                i.modName = m.path("name").asText("");
                i.modUrl = m.path("url").asText("");
                i.author = m.path("author").asText("");
                i.classId = m.path("classId").asInt(0);
                // автор заборонив сторонні завантаження: не качаємо, навіть якщо адреса раптом прийшла
                if (!m.path("allowModDistribution").asBoolean(true)) i.downloadUrl = null;
            }
            if (i.downloadUrl != null) {
                URL u = new URL(i.downloadUrl);
                if (!"https".equals(u.getProtocol()) || !HOSTS.contains(u.getHost().toLowerCase(Locale.ROOT))) {
                    log.warning("CurseForge file " + i.id + ": unexpected host " + u.getHost() + ", treated as blocked");
                    i.downloadUrl = null;
                }
            }
            out.add(i);
        }
        return out;
    }

    /**
     * 25.11.2026: fingerprint файлу так, як його рахує CurseForge: MurmurHash2 (32 біти, seed 1) по байтах файлу без
     * табуляції, переносів рядка і пробілів (9, 10, 13, 32). Беззнакове значення.
     */
    public static long fingerprint(File f) throws IOException {
        byte[] raw = java.nio.file.Files.readAllBytes(f.toPath());
        byte[] b = new byte[raw.length];
        int n = 0;
        for (byte x : raw) {
            if (x != 9 && x != 10 && x != 13 && x != 32) b[n++] = x;
        }
        return murmur2(b, n, 1);
    }

    static long murmur2(byte[] b, int len, int seed) {
        final int m = 0x5bd1e995;
        int h = seed ^ len;
        int i = 0;
        while (len - i >= 4) {
            int k = (b[i] & 0xff) | (b[i + 1] & 0xff) << 8 | (b[i + 2] & 0xff) << 16 | (b[i + 3] & 0xff) << 24;
            k *= m;
            k ^= k >>> 24;
            k *= m;
            h *= m;
            h ^= k;
            i += 4;
        }
        switch (len - i) {
            case 3:
                h ^= (b[i + 2] & 0xff) << 16;
            case 2:
                h ^= (b[i + 1] & 0xff) << 8;
            case 1:
                h ^= b[i] & 0xff;
                h *= m;
            default:
                break;
        }
        h ^= h >>> 13;
        h *= m;
        h ^= h >>> 15;
        return h & 0xffffffffL;
    }

    /**
     * 25.11.2026: ID файлів CurseForge за fingerprint (через наш проксі, лише точні збіги): fingerprint -> fileId.
     * Старий проксі без цього запиту, CurseForge недоступний: IOException.
     */
    public static Map<Long, Integer> byFingerprint(Launcher launcher, java.util.Collection<Long> fingerprints)
            throws IOException, InterruptedException {
        Map<Long, Integer> out = new HashMap<Long, Integer>();
        for (Map.Entry<Long, int[]> e : matchFingerprints(launcher, fingerprints).entrySet()) out.put(e.getKey(), e.getValue()[0]);
        return out;
    }

    /**
     * 25.11.2026 (оновлення модів з CurseForge): як {@link #byFingerprint}, але fingerprint -> {fileId, modId}.
     * Старий проксі без цього запиту, CurseForge недоступний: IOException.
     */
    public static Map<Long, int[]> matchFingerprints(Launcher launcher, java.util.Collection<Long> fingerprints)
            throws IOException, InterruptedException {
        String proxy = launcher.getProperties().getProperty("curseforgeProxyUrl", "").trim();
        if (proxy.isEmpty()) throw new IOException("curseforgeProxyUrl is not set");
        Map<Long, int[]> out = new HashMap<Long, int[]>();
        List<Long> all = new ArrayList<Long>(fingerprints);
        for (int from = 0; from < all.size(); from += 1000) {
            Map<String, Object> body = Collections.<String, Object>singletonMap("fingerprints", all.subList(from, Math.min(all.size(), from + 1000)));
            JsonNode answer = MAPPER.readTree(HttpRequest.post(new URL(proxy)).bodyJson(body).execute()
                    .expectResponseCode(200).returnContent().asString("UTF-8"));
            if (!answer.has("matches")) throw new IOException("The CurseForge proxy does not know fingerprints yet");
            for (JsonNode m : answer.path("matches")) {
                long fp = m.path("fingerprint").asLong(-1);
                int id = m.path("fileId").asInt(0);
                if (fp >= 0 && id > 0) out.put(fp, new int[]{id, m.path("modId").asInt(0)});
            }
        }
        return out;
    }

    /** 25.11.2026: slug мода з адреси сторінки ({@code https://www.curseforge.com/minecraft/mc-mods/<slug>}), або null. */
    public static String slugOf(String modUrl) {
        if (modUrl == null) return null;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("^https://www\\.curseforge\\.com/minecraft/[a-z-]+/([a-z0-9_-]+)/?$").matcher(modUrl);
        return m.matches() ? m.group(1) : null;
    }

    /**
     * Адреса файлу для запиту. CurseForge віддає {@code downloadUrl} з назвою файлу як є: пробіли, «+», дужки
     * ({@code fabric-api-0.100.0+1.21.1.jar}, {@code Decorative Blocks-forge.jar}). Пробіл у запиті недійсний, а «+»
     * деякі сховища читають як пробіл, тож кодуємо шлях: пробіл як %20, «+» як %2B (і те, і те сервер розкодує в ту
     * саму назву). Уже закодований шлях (є «%») не чіпаємо, лише добиваємо пробіли і «+».
     */
    public static URL fileUrl(String raw) throws IOException {
        URL u = new URL(raw);
        String path = u.getPath();
        if (!path.contains("%")) {
            try {
                path = new java.net.URI(null, null, path, null).toASCIIString();
            } catch (java.net.URISyntaxException e) {
                throw new IOException("Bad CurseForge file address: " + raw, e);
            }
        }
        path = path.replace(" ", "%20").replace("+", "%2B");
        return new URL(u.getProtocol(), u.getHost(), u.getPort(), path);
    }

    /** Точна копія файлу на Modrinth: адреса на cdn.modrinth.com, sha512 і id проєкту. */
    public static final class ModrinthCopy {
        public final String url, sha512, projectId;

        ModrinthCopy(String url, String sha512, String projectId) {
            this.url = url;
            this.sha512 = sha512;
            this.projectId = projectId;
        }
    }

    /**
     * 1.40.0 (власник: «безумовно роби, це кілерфіча»): файли, які автор дозволяє качати лише з сайту CurseForge,
     * шукаємо на Modrinth за sha1, тобто той самий файл байт у байт (автор виклав його і туди), а не «схожий мод». Один
     * запит {@code POST /v2/version_files} на весь список (до 500 за раз). Повертає sha1 -> копія; Modrinth
     * недоступний: порожньо (тоді все йде у вікно, як раніше).
     */
    public static Map<String, ModrinthCopy> modrinthCopies(java.util.Collection<String> sha1s) {
        Map<String, ModrinthCopy> out = new HashMap<String, ModrinthCopy>();
        List<String> hashes = new ArrayList<String>();
        for (String h : sha1s) if (h != null && h.matches("[0-9a-fA-F]{40}")) hashes.add(h.toLowerCase(Locale.ROOT));
        for (int from = 0; from < hashes.size(); from += 500) {
            List<String> chunk = hashes.subList(from, Math.min(hashes.size(), from + 500));
            try {
                Map<String, Object> body = new HashMap<String, Object>();
                body.put("hashes", chunk);
                body.put("algorithm", "sha1");
                JsonNode versions = MAPPER.readTree(HttpRequest.post(new URL("https://api.modrinth.com/v2/version_files"))
                        .bodyJson(body).execute().expectResponseCode(200).returnContent().asString("UTF-8"));
                for (String h : chunk) {
                    JsonNode v = versions.path(h);
                    if (v.isMissingNode()) continue;
                    for (JsonNode f : v.path("files")) {
                        if (!h.equalsIgnoreCase(f.path("hashes").path("sha1").asText(""))) continue;
                        String url = f.path("url").asText("");
                        String sha512 = f.path("hashes").path("sha512").asText("");
                        URL u = new URL(url);
                        if (!"https".equals(u.getProtocol()) || !"cdn.modrinth.com".equalsIgnoreCase(u.getHost())
                                || !sha512.matches("[0-9a-fA-F]{128}")) continue;
                        out.put(h, new ModrinthCopy(url, sha512.toLowerCase(Locale.ROOT), v.path("project_id").asText(null)));
                    }
                }
            } catch (IOException | RuntimeException e) {
                log.info("Modrinth sha1 lookup for CurseForge files failed: " + e);
                return out;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return out;
            }
        }
        return out;
    }

    /** sha512 файлу (звірка копії з Modrinth). */
    public static String sha512(File f) throws IOException {
        try (InputStream in = new java.io.FileInputStream(f)) {
            MessageDigest md = MessageDigest.getInstance("SHA-512");
            byte[] buf = new byte[65536];
            for (int n; (n = in.read(buf)) > 0; ) md.update(buf, 0, n);
            StringBuilder sb = new StringBuilder();
            for (byte b : md.digest()) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IOException(e);
        }
    }

    /** Теки, куди браузер кладе завантаження: «Завантаження» Windows (і перенесена в інше місце), ~/Downloads. */
    public static List<File> downloadDirs() {
        Set<File> out = new LinkedHashSet<File>();
        String home = System.getProperty("user.home", "");
        if (!home.isEmpty()) out.add(new File(home, "Downloads"));
        if (System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")) {
            try {
                Process p = new ProcessBuilder("reg", "query",
                        "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Explorer\\User Shell Folders",
                        "/v", "{374DE290-123F-4565-9164-39C4925E467B}").redirectErrorStream(true).start();
                String text;
                try (InputStream in = p.getInputStream()) {
                    text = new String(readAll(in), java.nio.charset.Charset.defaultCharset());
                }
                p.waitFor();
                java.util.regex.Matcher m = java.util.regex.Pattern.compile("REG_(?:EXPAND_)?SZ\\s+(.+)").matcher(text);
                if (m.find()) {
                    String path = m.group(1).trim();
                    java.util.regex.Matcher env = java.util.regex.Pattern.compile("%([^%]+)%").matcher(path);
                    StringBuffer sb = new StringBuffer();
                    while (env.find()) {
                        String v = System.getenv(env.group(1));
                        env.appendReplacement(sb, java.util.regex.Matcher.quoteReplacement(v != null ? v : env.group(0)));
                    }
                    env.appendTail(sb);
                    out.add(new File(sb.toString()));
                }
            } catch (IOException e) {
                log.info("Downloads folder from the registry: " + e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        List<File> list = new ArrayList<File>();
        for (File f : out) if (f.isDirectory()) list.add(f);
        return list;
    }

    private static final Map<String, String> SHA_CACHE = new java.util.concurrent.ConcurrentHashMap<String, String>();

    /**
     * Знайти в теках завантажень файли, яких чекаємо (за розміром і sha1, тож назва файлу в браузері може бути
     * будь-яка), і покласти їх у збірку. Повертає, скільки знайдено цього разу.
     */
    public static int pickup(List<Blocked> wanted, List<File> dirs) {
        int found = 0;
        for (File dir : dirs) {
            File[] files = dir.listFiles();
            if (files == null) continue;
            for (File f : files) {
                if (!f.isFile()) continue;
                String n = f.getName().toLowerCase(Locale.ROOT);
                if (n.endsWith(".crdownload") || n.endsWith(".part") || n.endsWith(".tmp")) continue;
                for (Blocked b : wanted) {
                    if (b.done || b.file.length <= 0 || f.length() != b.file.length) continue;
                    if (b.file.sha1 != null) {
                        String sha = sha1Cached(f);
                        if (sha == null || !sha.equalsIgnoreCase(b.file.sha1)) continue;
                    } else if (!sameName(f.getName(), b.file.fileName)) {
                        // у частини файлів CurseForge немає sha1: тоді розмір і назва (браузер міг дописати « (1)»)
                        continue;
                    }
                    try {
                        b.target.getParentFile().mkdirs();
                        Files.copy(f.toPath(), b.target.toPath(), StandardCopyOption.REPLACE_EXISTING);
                        b.done = true;
                        found++;
                        log.info("CurseForge: picked up " + f + " for " + b.target.getName());
                    } catch (IOException e) {
                        log.info("CurseForge: can't copy " + f + ": " + e);
                    }
                }
            }
        }
        return found;
    }

    /** Та сама назва файлу, з точністю до « (1)», яку браузер дописує до повторного завантаження. */
    static boolean sameName(String got, String want) {
        if (want == null || want.isEmpty()) return false;
        if (got.equalsIgnoreCase(want)) return true;
        int dot = want.lastIndexOf('.');
        String base = dot > 0 ? want.substring(0, dot) : want, ext = dot > 0 ? want.substring(dot) : "";
        return got.toLowerCase(Locale.ROOT).matches(java.util.regex.Pattern.quote(base.toLowerCase(Locale.ROOT))
                + " \\(\\d+\\)" + java.util.regex.Pattern.quote(ext.toLowerCase(Locale.ROOT)));
    }

    private static String sha1Cached(File f) {
        String key = f.getPath() + "|" + f.length() + "|" + f.lastModified();
        String s = SHA_CACHE.get(key);
        if (s != null) return s;
        try {
            s = sha1(f);
            SHA_CACHE.put(key, s);
            return s;
        } catch (IOException e) {
            return null; // ще пишеться браузером або зайнятий: наступна спроба
        }
    }

    public static String sha1(File f) throws IOException {
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

    private static byte[] readAll(InputStream in) throws IOException {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        for (int n; (n = in.read(buf)) > 0; ) out.write(buf, 0, n);
        return out.toByteArray();
    }
}
