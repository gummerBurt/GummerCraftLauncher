/*
 * Copyright (C) 2021-2026 GummerCraft <http://gummercraft.fun>
 */

package com.gummer.launcher.manage;

import com.fasterxml.jackson.databind.JsonNode;
import com.skcraft.launcher.util.HttpRequest;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * 17.11.2026: оновлення модів, які гравець поставив сам. Modrinth знаходить мод за sha1 файлу (назва файлу не важлива)
 * і віддає найновішу версію під цю гру і завантажувач: два запити на весь список ({@code POST /v2/version_files}
 * поточні версії і {@code POST /v2/version_files/update} найновіші), без запиту на кожен мод. Оновлення пропонується,
 * лише коли нова версія опублікована пізніше за поточну (бета, яку гравець поставив сам, не відкотиться на старіший
 * реліз). Моди модпака сюди не йдуть: їх оновлює сам модпак.
 */
public final class ModUpdates {

    private static final Logger log = Logger.getLogger(ModUpdates.class.getName());
    private static final String API = "https://api.modrinth.com/v2/";
    private static final long TTL = 30 * 60 * 1000L;
    /**
     * Хеш файлу + гра + завантажувачі -> {час, Update або NONE}. 17.11.2026: по кожному файлу, а не за весь список
     * (власник: «десь секунда проходить поки мені покаже кнопку "оновити все"»): фонова перевірка бейджа (UpdateBadges)
     * наповнює кеш, і вікно «Збірка» показує оновлення одразу, навіть якщо список модів трохи інший.
     */
    private static final Map<String, Object[]> CACHE = new ConcurrentHashMap<String, Object[]>();
    private static final Object NONE = new Object();

    /** Доступне оновлення одного мода. */
    public static final class Update {
        public final File file;
        public final String projectId;
        public final String versionNumber;
        final JsonNode version;
        final JsonNode primary;
        /** 18.11.2026: поставлена зараз версія (номер і дата публікації), для списку змін. */
        public final String currentNumber;
        final String currentDate;

        Update(File file, String projectId, String versionNumber, JsonNode version, JsonNode primary, String currentNumber, String currentDate) {
            this.file = file;
            this.projectId = projectId;
            this.versionNumber = versionNumber;
            this.version = version;
            this.primary = primary;
            this.currentNumber = currentNumber;
            this.currentDate = currentDate;
        }
    }

    private ModUpdates() {
    }

    /**
     * Які з цих файлів мають новішу версію на Modrinth під гру {@code game} і завантажувач. Без інтернету чи для
     * модів поза Modrinth: порожньо. Викликати не з потоку інтерфейсу.
     */
    public static Map<File, Update> check(List<File> jars, String game, String[] loaders) {
        Map<File, Update> out = new HashMap<File, Update>();
        if (jars.isEmpty() || game == null || loaders == null || loaders.length == 0) return out;
        long t0 = System.nanoTime();
        Map<String, File> byHash = new LinkedHashMap<String, File>();
        for (File f : jars) {
            try {
                byHash.put(ModSides.sha1Cached(f), f);
            } catch (IOException e) {
                // файл зник чи зайнятий
            }
        }
        if (byHash.isEmpty()) return out;
        String suffix = "|" + game + "|" + Arrays.toString(loaders);
        boolean all = true;
        long now0 = System.currentTimeMillis();
        for (Map.Entry<String, File> e : byHash.entrySet()) {
            Object[] hit = CACHE.get(e.getKey() + suffix);
            if (hit == null || now0 - (Long) hit[0] >= TTL) {
                all = false;
                break;
            }
            if (hit[1] instanceof Update) {
                Update u = (Update) hit[1];
                out.put(e.getValue(), u.file.equals(e.getValue()) ? u : rebind(u, e.getValue()));
            }
        }
        if (all) return out;
        out.clear();
        Map<String, Update> found = new HashMap<String, Update>();
        try {
            Map<String, Object> body = new HashMap<String, Object>();
            body.put("hashes", new ArrayList<String>(byHash.keySet()));
            body.put("algorithm", "sha1");
            JsonNode current = post("version_files", body);
            body.put("loaders", Arrays.asList(loaders));
            body.put("game_versions", java.util.Collections.singletonList(game));
            JsonNode latest = post("version_files/update", body);
            for (Map.Entry<String, File> e : byHash.entrySet()) {
                JsonNode now = current.path(e.getKey());
                JsonNode next = latest.path(e.getKey());
                if (now.isMissingNode() || next.isMissingNode()) continue;
                if (now.path("id").asText("").equals(next.path("id").asText(""))) continue;
                // новіша лише за датою: поставлену руками бету не міняємо на старіший реліз
                if (next.path("date_published").asText("").compareTo(now.path("date_published").asText("")) <= 0) continue;
                JsonNode primary = primaryFile(next);
                if (primary == null) continue;
                if (e.getKey().equalsIgnoreCase(primary.path("hashes").path("sha1").asText(""))) continue;
                found.put(e.getKey(), new Update(e.getValue(), next.path("project_id").asText(null),
                        next.path("version_number").asText("?"), next, primary,
                        now.path("version_number").asText("?"), now.path("date_published").asText("")));
            }
        } catch (IOException | InterruptedException | RuntimeException e) {
            log.info("Mod updates are unavailable: " + e);
            return out;
        }
        long now = System.currentTimeMillis();
        for (String h : byHash.keySet()) {
            Update u = found.get(h);
            CACHE.put(h + suffix, new Object[]{now, u != null ? u : NONE});
        }
        for (Map.Entry<String, Update> e : found.entrySet()) out.put(e.getValue().file, e.getValue());
        log.info("Mod updates: " + out.size() + " of " + byHash.size() + " in " + (System.nanoTime() - t0) / 1000000 + " ms");
        return out;
    }

    /**
     * Поставити нову версію замість старого файлу: качається поруч, звіряється sha512, потім старий файл прибирається.
     * Вимкнений мод лишається вимкненим. Обов'язкові залежності нової версії ставляться, як при встановленні з
     * пошуку ({@code missing}, {@code conflicts} як у {@link ModrinthBrowse#install}). Повертає новий файл.
     */
    public static File apply(Update u, String game, String[] loaders, List<String> missing, List<String> conflicts)
            throws IOException, InterruptedException {
        File dir = u.file.getParentFile();
        String fileName = u.primary.path("filename").asText("");
        String sha512 = u.primary.path("hashes").path("sha512").asText(null);
        URL url = new URL(u.primary.path("url").asText(""));
        if (!"https".equals(url.getProtocol()) || !ModrinthBrowse.CDN_HOST.equalsIgnoreCase(url.getHost())) {
            throw new IOException("Not a Modrinth file: " + url.getHost());
        }
        if (fileName.isEmpty() || fileName.contains("/") || fileName.contains("\\") || fileName.startsWith(".")
                || !fileName.toLowerCase(Locale.ROOT).endsWith(".jar")) {
            throw new IOException("Unexpected file name: " + fileName);
        }
        if (sha512 == null || !sha512.matches("[0-9a-fA-F]{128}")) throw new IOException("No sha512 for " + fileName);
        boolean disabled = u.file.getName().toLowerCase(Locale.ROOT).endsWith(InstanceMods.DISABLED);
        File target = new File(dir, disabled ? fileName + InstanceMods.DISABLED : fileName);
        File part = new File(dir, "." + fileName + ".part");
        try {
            HttpRequest.get(url).execute().expectResponseCode(200).saveContent(part);
            if (!ModrinthBrowse.sha(part, "SHA-512").equalsIgnoreCase(sha512)) throw new IOException("sha512 mismatch for " + fileName);
            // 19.11.2026 (власник: «Відкат моду після оновлення ... давай»): стара версія не видаляється, а йде в
            // .gummer/mod-prev (ModRollback): після вильоту через цей мод помічник запропонує повернути її
            if (!target.equals(u.file) && target.exists()) {
                // нова версія вже лежить поруч (поставили руками): лишаємо її, прибираємо стару
                if (!ModRollback.keep(u.file, target.getName(), u.currentNumber, u.projectId)) Files.delete(u.file.toPath());
            } else if (target.equals(u.file)) {
                // та сама назва файлу: стару спершу в сховище, інакше нова її перезапише
                ModRollback.keep(u.file, target.getName(), u.currentNumber, u.projectId);
                Files.move(part.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            } else {
                Files.move(part.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
                if (!ModRollback.keep(u.file, target.getName(), u.currentNumber, u.projectId)) Files.deleteIfExists(u.file.toPath());
            }
        } finally {
            part.delete();
        }
        if (u.projectId != null) ModrinthBrowse.record(dir, u.projectId, fileName);
        log.info("Mod updated: " + u.file.getName() + " -> " + target.getName());
        ModrinthBrowse.dependencies(u.version, game, loaders, dir, null, missing, conflicts,
                new java.util.HashSet<String>(java.util.Collections.singleton(String.valueOf(u.projectId))), 0);
        return target;
    }

    private static Update rebind(Update u, File f) {
        return new Update(f, u.projectId, u.versionNumber, u.version, u.primary, u.currentNumber, u.currentDate);
    }

    /**
     * 18.11.2026 (власник: «список змін і для модів» «хороша ідея»): що написав автор мода про версії між поставленою
     * і новою (під цю гру і завантажувач, від нової вниз, до 10 версій), простим текстом. Не вдалось отримати список
     * версій: лише текст нової версії. Порожньо: автор нічого не написав. Не з потоку інтерфейсу.
     */
    public static String changelog(Update u, String game, String[] loaders) {
        StringBuilder out = new StringBuilder();
        String newDate = u.version.path("date_published").asText("");
        try {
            int n = 0;
            for (ModrinthBrowse.PackVersion v : ModrinthBrowse.modVersions(u.projectId)) {
                if (v.date == null || v.date.compareTo(u.currentDate) <= 0 || v.date.compareTo(newDate) > 0) continue;
                if (game != null && !v.games.contains(game)) continue;
                boolean loaderOk = loaders == null;
                if (loaders != null) for (String l : loaders) loaderOk |= v.loaders.contains(l);
                if (!loaderOk) continue;
                append(out, v.number, v.date, v.changelog);
                if (++n >= 10 || out.length() > 12000) break;
            }
        } catch (IOException | InterruptedException | RuntimeException e) {
            log.info("Mod changelog list unavailable for " + u.projectId + ": " + e);
        }
        if (out.length() == 0) append(out, u.versionNumber, newDate, u.version.path("changelog").asText(""));
        return out.toString().trim();
    }

    private static void append(StringBuilder out, String number, String date, String md) {
        String text = com.gummer.launcher.modrinth.MrpackUpdate.plain(md);
        out.append(out.length() > 0 ? "\n\n" : "").append(number);
        if (date != null && date.length() >= 10) out.append("  (").append(date, 8, 10).append('.').append(date, 5, 7).append('.').append(date, 0, 4).append(')');
        out.append('\n').append(text.isEmpty() ? com.skcraft.launcher.util.SharedLocale.tr("mrpack.update.noChangelog") : text);
    }

    private static JsonNode primaryFile(JsonNode version) {
        JsonNode first = null;
        for (JsonNode f : version.path("files")) {
            if (f.path("primary").asBoolean(false)) return f;
            if (first == null) first = f;
        }
        return first;
    }

    private static JsonNode post(String path, Map<String, Object> body) throws IOException, InterruptedException {
        return ModrinthBrowse.MAPPER.readTree(HttpRequest.post(new URL(API + path)).bodyJson(body)
                .execute().expectResponseCode(200).returnContent().asString("UTF-8"));
    }
}
