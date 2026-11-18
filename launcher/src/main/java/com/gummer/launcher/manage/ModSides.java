/*
 * Copyright (C) 2021-2026 GummerCraft <http://gummercraft.fun>
 */

package com.gummer.launcher.manage;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gummer.launcher.util.Json;
import com.skcraft.launcher.util.HttpRequest;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLEncoder;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 17.11.2026: де мод працює: лише у грі гравця (клієнт), лише на сервері чи потрібен і там, і там. Джерело: Modrinth
 * (поля проєкту {@code client_side}, {@code server_side}; мод знаходимо за sha1 файлу, тож назва файлу не важлива,
 * у модпаках GummerCraft файли названі кодами), а для модів, яких Modrinth не знає, {@code environment} з
 * {@code fabric.mod.json}. Forge 1.16.5 цього в описі мода не має: такі лишаються без позначки.
 */
public final class ModSides {

    public enum Side {
        /** Лише у грі гравця, серверу не потрібен. */
        CLIENT,
        /** Потрібен у грі, на сервері за бажанням. */
        CLIENT_PLUS,
        /** Лише на сервері. */
        SERVER,
        /** Потрібен на сервері, у грі за бажанням. */
        SERVER_PLUS,
        /** Має бути і в грі, і на сервері. */
        BOTH,
        /** Працює будь-де: у грі, на сервері чи разом. */
        ANY
    }

    private static final ObjectMapper MAPPER = Json.newMapper();
    private static final String API = "https://api.modrinth.com/v2/";
    /** Файл (шлях, розмір, час) -> сторона; NONE: перевірено, невідомо. */
    private static final Map<String, Object> CACHE = new ConcurrentHashMap<String, Object>();
    private static final Object NONE = new Object();

    private ModSides() {
    }

    /** З полів Modrinth ({@code required}, {@code optional}, {@code unsupported}, {@code unknown}), або null. */
    public static Side of(String client, String server) {
        boolean cReq = "required".equals(client), cOpt = "optional".equals(client), cNo = "unsupported".equals(client);
        boolean sReq = "required".equals(server), sOpt = "optional".equals(server), sNo = "unsupported".equals(server);
        if ((cReq || cOpt) && sNo) return Side.CLIENT;
        if ((sReq || sOpt) && cNo) return Side.SERVER;
        if (cReq && sReq) return Side.BOTH;
        if (cReq && sOpt) return Side.CLIENT_PLUS;
        if (sReq && cOpt) return Side.SERVER_PLUS;
        if (cOpt && sOpt) return Side.ANY;
        return null;
    }

    /** З {@code environment} у fabric.mod.json чи quilt.mod.json ({@code client}, {@code server}; {@code *} нічого не каже). */
    static Side ofEnvironment(String env) {
        if ("client".equalsIgnoreCase(env)) return Side.CLIENT;
        if ("server".equalsIgnoreCase(env) || "dedicated_server".equalsIgnoreCase(env)) return Side.SERVER;
        return null;
    }

    /** Лише з кешу, без мережі (для першого показу списку), або null. */
    public static Side cached(File jar) {
        Object o = CACHE.get(key(jar));
        return o instanceof Side ? (Side) o : null;
    }

    /**
     * Сторони для списку модів: два запити до Modrinth на весь список (за хешами, потім проєкти), решта з опису мода.
     * Без інтернету: лише з опису. Викликати не з потоку інтерфейсу.
     */
    public static Map<File, Side> lookup(List<File> jars) {
        Map<File, Side> out = new HashMap<File, Side>();
        Map<String, File> byHash = new LinkedHashMap<String, File>();
        for (File f : jars) {
            Object c = CACHE.get(key(f));
            if (c instanceof Side) out.put(f, (Side) c);
            else if (c == null) {
                try {
                    byHash.put(sha1Cached(f), f);
                } catch (IOException e) {
                    // файл зник чи зайнятий: просто без позначки
                }
            }
        }
        if (!byHash.isEmpty()) {
            long t0 = System.nanoTime();
            Map<File, Side> remote = new HashMap<File, Side>();
            try {
                Map<String, Object> body = new HashMap<String, Object>();
                body.put("hashes", new ArrayList<String>(byHash.keySet()));
                body.put("algorithm", "sha1");
                JsonNode versions = MAPPER.readTree(HttpRequest.post(new URL(API + "version_files")).bodyJson(body)
                        .execute().expectResponseCode(200).returnContent().asString("UTF-8"));
                Map<String, List<File>> byProject = new LinkedHashMap<String, List<File>>();
                for (Map.Entry<String, File> e : byHash.entrySet()) {
                    String pid = versions.path(e.getKey()).path("project_id").asText(null);
                    if (pid == null || !pid.matches("[A-Za-z0-9]+")) continue;
                    List<File> l = byProject.get(pid);
                    if (l == null) byProject.put(pid, l = new ArrayList<File>());
                    l.add(e.getValue());
                }
                if (!byProject.isEmpty()) {
                    String ids = MAPPER.writeValueAsString(new ArrayList<String>(byProject.keySet()));
                    JsonNode projects = MAPPER.readTree(HttpRequest.get(new URL(API + "projects?ids=" + URLEncoder.encode(ids, "UTF-8")))
                            .execute().expectResponseCode(200).returnContent().asString("UTF-8"));
                    for (JsonNode p : projects) {
                        Side s = of(p.path("client_side").asText(null), p.path("server_side").asText(null));
                        List<File> l = byProject.get(p.path("id").asText(""));
                        if (s != null && l != null) for (File f : l) remote.put(f, s);
                    }
                }
            } catch (IOException | InterruptedException | RuntimeException e) {
                // Modrinth недоступний: лише з опису мода, і без запису «невідомо» в кеш (спробуємо наступного разу)
                for (File f : byHash.values()) {
                    Side s = ofEnvironment(ModInfo.of(f).environment);
                    if (s != null) out.put(f, s);
                }
                return out;
            }
            java.util.logging.Logger.getLogger(ModSides.class.getName()).info("Mod sides: " + remote.size() + " of " + byHash.size()
                    + " from Modrinth in " + (System.nanoTime() - t0) / 1000000 + " ms");
            for (File f : byHash.values()) {
                Side s = remote.get(f);
                if (s == null) s = ofEnvironment(ModInfo.of(f).environment);
                CACHE.put(key(f), s != null ? s : NONE);
                if (s != null) out.put(f, s);
            }
        }
        return out;
    }

    /** sha1 файлів за (шлях, розмір, час): позначки і перевірка оновлень рахують хеш один раз. */
    private static final Map<String, String> HASHES = new ConcurrentHashMap<String, String>();

    /**
     * sha1 файлу з кешу або порахувати (17.11.2026: спільне для позначок і оновлень). 18.11.2026 (власник:
     * «запам'ятовувати хеші модів між запусками» «оце дуже треба»): ще й на диску, файл {@link #HASH_FILE} у теці гри
     * збірки (поруч з mods). Запис: ім'я файлу (без .disabled), розмір, час зміни, sha1; не збігся розмір чи час -
     * рахується заново. Раніше після кожного запуску лаунчера перше «Встановити», позначки сторін і перевірка
     * оновлень знову читали всі jar збірки (на великих модпаках 1-3 с).
     */
    static String sha1Cached(File f) throws IOException {
        String k = key(f);
        String h = HASHES.get(k);
        if (h != null) return h;
        File dir = f.getAbsoluteFile().getParentFile();
        File game = dir != null ? dir.getParentFile() : null;
        String rel = dir != null ? dir.getName() + "/" + baseName(f) : null;
        String size = String.valueOf(f.length()), time = String.valueOf(f.lastModified());
        Map<String, String[]> disk = game != null ? disk(game) : null;
        if (disk != null) {
            String[] e = disk.get(rel);
            if (e != null && e.length == 3 && size.equals(e[0]) && time.equals(e[1]) && e[2].matches("[0-9a-f]{40}")) {
                HASHES.put(k, e[2]);
                return e[2];
            }
        }
        h = sha1(f);
        HASHES.put(k, h);
        if (disk != null) {
            disk.put(rel, new String[]{size, time, h});
            scheduleSave(game);
        }
        return h;
    }

    /** Файл кешу sha1 у теці гри збірки. */
    public static final String HASH_FILE = "gummer-hashes.json";
    /** Тека гри -> (mods/ім'я -> {розмір, час, sha1}). */
    private static final Map<String, Map<String, String[]>> DISK = new ConcurrentHashMap<String, Map<String, String[]>>();
    private static final java.util.Set<String> DIRTY = java.util.Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());
    private static final java.util.concurrent.ScheduledExecutorService SAVER = java.util.concurrent.Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "GummerCraft mod hashes");
        t.setDaemon(true);
        return t;
    });

    static {
        try {
            Runtime.getRuntime().addShutdownHook(new Thread(ModSides::saveAll, "GummerCraft mod hashes on exit"));
        } catch (RuntimeException ignored) {
            // без запису при виході: запишеться за таймером
        }
    }

    private static String baseName(File f) {
        String n = f.getName();
        return n.toLowerCase(Locale.ROOT).endsWith(InstanceMods.DISABLED) ? n.substring(0, n.length() - InstanceMods.DISABLED.length()) : n;
    }

    private static Map<String, String[]> disk(File game) {
        return DISK.computeIfAbsent(game.getAbsolutePath(), p -> {
            Map<String, String[]> m = new ConcurrentHashMap<String, String[]>();
            File file = new File(game, HASH_FILE);
            if (file.isFile()) {
                try {
                    JsonNode root = MAPPER.readTree(file);
                    java.util.Iterator<Map.Entry<String, JsonNode>> it = root.path("files").fields();
                    while (it.hasNext()) {
                        Map.Entry<String, JsonNode> e = it.next();
                        JsonNode a = e.getValue();
                        if (a.isArray() && a.size() == 3) m.put(e.getKey(), new String[]{a.get(0).asText(), a.get(1).asText(), a.get(2).asText()});
                    }
                } catch (IOException | RuntimeException e) {
                    // битий файл: рахуємо заново і перезапишемо
                }
            }
            return m;
        });
    }

    private static void scheduleSave(File game) {
        if (DIRTY.add(game.getAbsolutePath())) {
            SAVER.schedule(() -> save(game.getAbsolutePath()), 3, java.util.concurrent.TimeUnit.SECONDS);
        }
    }

    private static void saveAll() {
        for (String g : new ArrayList<String>(DIRTY)) save(g);
    }

    /** Записати кеш теки гри; записи про файли, яких уже немає, прибираються. */
    private static synchronized void save(String gamePath) {
        if (!DIRTY.remove(gamePath)) return;
        Map<String, String[]> m = DISK.get(gamePath);
        File game = new File(gamePath);
        if (m == null || !game.isDirectory()) return;
        try {
            com.fasterxml.jackson.databind.node.ObjectNode root = MAPPER.createObjectNode();
            root.put("v", 1);
            com.fasterxml.jackson.databind.node.ObjectNode files = root.putObject("files");
            for (Map.Entry<String, String[]> e : new java.util.TreeMap<String, String[]>(m).entrySet()) {
                File f = new File(game, e.getKey());
                if (!f.isFile() && !new File(game, e.getKey() + InstanceMods.DISABLED).isFile()) {
                    m.remove(e.getKey());
                    continue;
                }
                com.fasterxml.jackson.databind.node.ArrayNode a = files.putArray(e.getKey());
                for (String v : e.getValue()) a.add(v);
            }
            File tmp = new File(game, HASH_FILE + ".tmp");
            MAPPER.writeValue(tmp, root);
            java.nio.file.Files.move(tmp.toPath(), new File(game, HASH_FILE).toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException | RuntimeException e) {
            java.util.logging.Logger.getLogger(ModSides.class.getName()).info("Mod hashes not saved: " + e);
        }
    }

    static String key(File jar) {
        String path = jar.getAbsolutePath();
        if (path.toLowerCase(Locale.ROOT).endsWith(InstanceMods.DISABLED)) path = path.substring(0, path.length() - InstanceMods.DISABLED.length());
        return path + "|" + jar.length() + "|" + jar.lastModified();
    }

    private static String sha1(File f) throws IOException {
        MessageDigest md;
        try {
            md = MessageDigest.getInstance("SHA-1");
        } catch (java.security.NoSuchAlgorithmException e) {
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
