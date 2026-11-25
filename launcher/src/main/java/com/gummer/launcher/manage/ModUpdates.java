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
 *
 * <p>25.11.2026 (власник: «обов'язково задіюємо і курсфордж ... вони мають доповнювати та страхувати одне одного»):
 * файли, яких Modrinth не знає, шукаються на CurseForge за fingerprint (через наш проксі): три запити на весь список
 * (fingerprint -> мод і файл, найновіші файли модів під цю гру, дані файлів). Новіший там, де ID файлу більший
 * (CurseForge видає ID по зростанню). Запити до проксі не частіше разу на 1,5 с (у проксі 60 на хвилину з IP, а бейджі
 * перевіряють усі збірки підряд).</p>
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

        /** 25.11.2026: новий файл з CurseForge (тоді {@code version} і {@code primary} null), або null: з Modrinth. */
        public final com.gummer.launcher.curseforge.CurseForge.FileInfo cf;

        Update(File file, String projectId, String versionNumber, JsonNode version, JsonNode primary, String currentNumber, String currentDate) {
            this(file, projectId, versionNumber, version, primary, currentNumber, currentDate, null);
        }

        private Update(File file, String projectId, String versionNumber, JsonNode version, JsonNode primary, String currentNumber,
                       String currentDate, com.gummer.launcher.curseforge.CurseForge.FileInfo cf) {
            this.file = file;
            this.projectId = projectId;
            this.versionNumber = versionNumber;
            this.version = version;
            this.primary = primary;
            this.currentNumber = currentNumber;
            this.currentDate = currentDate;
            this.cf = cf;
        }

        /** Звідки нова версія: «CurseForge» чи «Modrinth». */
        public String source() {
            return cf != null ? "CurseForge" : "Modrinth";
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
        java.util.Set<String> onModrinth = new java.util.HashSet<String>();
        boolean modrinthOk = true;
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
                if (!now.isMissingNode()) onModrinth.add(e.getKey());
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
            log.info("Mod updates are unavailable on Modrinth: " + e);
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
                return out;
            }
            modrinthOk = false;
        }
        // 25.11.2026: чого Modrinth не знає (чи Modrinth не відповів), шукаємо на CurseForge
        Map<String, File> rest = new LinkedHashMap<String, File>();
        for (Map.Entry<String, File> e : byHash.entrySet()) if (!onModrinth.contains(e.getKey())) rest.put(e.getKey(), e.getValue());
        boolean curseOk = true;
        if (!rest.isEmpty()) {
            try {
                found.putAll(curse(rest, game, loaders));
            } catch (IOException | RuntimeException e) {
                log.info("Mod updates are unavailable on CurseForge: " + e);
                curseOk = false;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return out;
            }
        }
        long now = System.currentTimeMillis();
        for (String h : byHash.keySet()) {
            Update u = found.get(h);
            // кешуємо лише відповіді, а не «не вдалося спитати»: інакше 30 хв без оновлень після збою мережі
            if (u != null || modrinthOk && (curseOk || !rest.containsKey(h))) CACHE.put(h + suffix, new Object[]{now, u != null ? u : NONE});
        }
        for (Map.Entry<String, Update> e : found.entrySet()) out.put(e.getValue().file, e.getValue());
        log.info("Mod updates: " + out.size() + " of " + byHash.size() + " (" + rest.size() + " not on Modrinth) in "
                + (System.nanoTime() - t0) / 1000000 + " ms");
        return out;
    }

    /**
     * 25.11.2026: лише CurseForge, і для файлів, які знає Modrinth: друга спроба, коли найновіша версія з Modrinth не
     * допомогла (виліт через клас, якого в ній немає). Без кешу. Помилки мережі: IOException.
     */
    public static Map<File, Update> checkCurse(List<File> jars, String game, String[] loaders) throws IOException, InterruptedException {
        Map<String, File> byHash = new LinkedHashMap<String, File>();
        for (File f : jars) byHash.put(ModSides.sha1Cached(f), f);
        Map<File, Update> out = new HashMap<File, Update>();
        for (Update u : curse(byHash, game, loaders).values()) out.put(u.file, u);
        return out;
    }

    /**
     * 25.11.2026: що це за мод на CurseForge (за fingerprint): дані поставленого файлу, або null (файлу там немає чи
     * CurseForge недоступний). Для пошуку того самого мода на Modrinth.
     */
    public static com.gummer.launcher.curseforge.CurseForge.FileInfo curseIdentity(File jar) throws InterruptedException {
        com.skcraft.launcher.Launcher launcher = com.gummer.launcher.curseforge.CurseBrowse.shared();
        if (launcher == null || jar == null || !jar.isFile()) return null;
        try {
            long fp = com.gummer.launcher.curseforge.CurseForge.fingerprint(jar);
            gate();
            int[] m = com.gummer.launcher.curseforge.CurseForge.matchFingerprints(launcher, java.util.Collections.singletonList(fp)).get(fp);
            if (m == null) return null;
            gate();
            List<com.gummer.launcher.curseforge.CurseForge.FileInfo> l = lookupSome(launcher, java.util.Collections.singletonList(m[0]));
            return l.isEmpty() ? null : l.get(0);
        } catch (IOException | RuntimeException e) {
            log.info("CurseForge identity unavailable for " + jar.getName() + ": " + e);
            return null;
        }
    }

    /**
     * 25.11.2026 («і навпаки»): мод, якого Modrinth за файлом не знає (поставлений з CurseForge), але той самий мод там
     * є (точний збіг slug чи назви, {@link ModrinthBrowse#sameMod}): версія з Modrinth під цю гру і завантажувач,
     * опублікована пізніше за поставлений файл ({@code currentDate}, ISO; серед новіших спершу стабільна), як оновлення
     * цього файлу. Без дати поставленого файлу нічого не пропонується (стабільна на Modrinth буває старішою за бету з
     * CurseForge: JEI 1.12.2 4.22 проти 4.24). null: немає чи Modrinth недоступний.
     */
    public static Update modrinthSame(File jar, String title, String slug, String currentNumber, String currentDate,
                                      String game, String[] loaders) throws InterruptedException {
        if (jar == null || title == null || currentDate == null || currentDate.isEmpty() || game == null || loaders == null
                || loaders.length == 0 || dateOf(currentDate) <= 0) return null;
        try {
            ModrinthBrowse.Hit a = new ModrinthBrowse.Hit();
            a.title = title;
            a.slug = slug;
            ModrinthBrowse.Hit same = ModrinthBrowse.sameMod(a, ModrinthBrowse.search(ModrinthBrowse.Kind.MOD, title, game, loaders, 0).hits);
            if (same == null || same.id == null || !same.id.matches("[A-Za-z0-9]+")) return null;
            StringBuilder l = new StringBuilder("[");
            for (int i = 0; i < loaders.length; i++) l.append(i > 0 ? "," : "").append('"').append(loaders[i]).append('"');
            JsonNode versions = ModrinthBrowse.getJson(API + "project/" + same.id + "/version?game_versions="
                    + ModrinthBrowse.enc("[\"" + game + "\"]") + "&loaders=" + ModrinthBrowse.enc(l.append(']').toString()));
            JsonNode chosen = null;
            for (JsonNode v : versions) { // від найновішої; лише новіші за поставлений файл, серед них спершу стабільна
                if (dateOf(v.path("date_published").asText("")) <= dateOf(currentDate)) break;
                if (chosen == null) chosen = v;
                if ("release".equals(v.path("version_type").asText(""))) {
                    chosen = v;
                    break;
                }
            }
            if (chosen == null) return null;
            JsonNode primary = primaryFile(chosen);
            if (primary == null) return null;
            if (ModSides.sha1Cached(jar).equalsIgnoreCase(primary.path("hashes").path("sha1").asText(""))) return null;
            log.info("Same mod on Modrinth for " + jar.getName() + ": " + same.slug + " " + chosen.path("version_number").asText("?")
                    + " (" + chosen.path("date_published").asText("") + " > " + currentDate + ")");
            return new Update(jar, same.id, chosen.path("version_number").asText("?"), chosen, primary,
                    currentNumber != null ? currentNumber : "?", "");
        } catch (IOException | RuntimeException e) {
            log.info("Same mod on Modrinth unavailable for " + jar.getName() + ": " + e);
            return null;
        }
    }

    /** Дата ISO (Modrinth «2026-11-21T10:00:00.123Z», CurseForge «2026-11-21T10:00:00.12Z») у мс; не розібрано: 0. */
    static long dateOf(String iso) {
        try {
            return java.time.Instant.parse(iso).toEpochMilli();
        } catch (RuntimeException e) {
            try {
                return java.time.OffsetDateTime.parse(iso).toInstant().toEpochMilli();
            } catch (RuntimeException e2) {
                return 0;
            }
        }
    }

    /** sha1 файлу -> fingerprint CurseForge (рахується раз на вміст). */
    private static final Map<String, Long> FINGERPRINTS = new ConcurrentHashMap<String, Long>();
    private static final Object GATE = new Object();
    private static long nextCall;

    /** Не частіше разу на 1,5 с усім лаунчером (ліміт проксі 60 запитів на хвилину з IP). */
    private static void gate() throws InterruptedException {
        long wait;
        synchronized (GATE) {
            long now = System.currentTimeMillis();
            wait = nextCall - now;
            nextCall = Math.max(now, nextCall) + 1500;
        }
        if (wait > 0) Thread.sleep(wait);
    }

    /** Оновлення з CurseForge для цих файлів (sha1 -> файл): sha1 -> Update. */
    private static Map<String, Update> curse(Map<String, File> byHash, String game, String[] loaders)
            throws IOException, InterruptedException {
        Map<String, Update> found = new HashMap<String, Update>();
        com.skcraft.launcher.Launcher launcher = com.gummer.launcher.curseforge.CurseBrowse.shared();
        if (launcher == null || byHash.isEmpty() || game == null || loaders == null || loaders.length == 0) return found;
        Map<Long, String> byFp = new LinkedHashMap<Long, String>();
        for (Map.Entry<String, File> e : byHash.entrySet()) {
            Long fp = FINGERPRINTS.get(e.getKey());
            if (fp == null) {
                fp = com.gummer.launcher.curseforge.CurseForge.fingerprint(e.getValue());
                FINGERPRINTS.put(e.getKey(), fp);
            }
            byFp.put(fp, e.getKey());
        }
        gate();
        Map<Long, int[]> matches = com.gummer.launcher.curseforge.CurseForge.matchFingerprints(launcher, byFp.keySet());
        Map<Integer, Integer> currentFile = new HashMap<Integer, Integer>(); // modId -> поставлений fileId
        Map<Integer, String> hashOfMod = new HashMap<Integer, String>();
        for (Map.Entry<Long, int[]> e : matches.entrySet()) {
            if (e.getValue()[1] <= 0) continue;
            currentFile.put(e.getValue()[1], e.getValue()[0]);
            hashOfMod.put(e.getValue()[1], byFp.get(e.getKey()));
        }
        if (currentFile.isEmpty()) return found;
        gate();
        Map<Integer, int[]> latest = com.gummer.launcher.curseforge.CurseBrowse.latestFiles(launcher, currentFile.keySet(), game, loaders);
        Map<Integer, Integer> nextFile = new HashMap<Integer, Integer>();
        List<Integer> ids = new ArrayList<Integer>();
        for (Map.Entry<Integer, Integer> e : currentFile.entrySet()) {
            int[] lf = latest.get(e.getKey());
            if (lf == null) continue;
            int cur = e.getValue();
            // новіший файл має більший ID. Спершу новіший стабільний; якщо новішого стабільного немає, найновіший
            // будь-який (так само робить Modrinth version_files/update); на старіший файл нічого не міняємо
            int next = lf[0] > cur ? lf[0] : lf[1] > cur ? lf[1] : 0;
            if (next > 0) {
                nextFile.put(e.getKey(), next);
                ids.add(next);
                ids.add(cur);
            }
        }
        if (ids.isEmpty()) return found;
        Map<Integer, com.gummer.launcher.curseforge.CurseForge.FileInfo> info = new HashMap<Integer, com.gummer.launcher.curseforge.CurseForge.FileInfo>();
        gate();
        for (com.gummer.launcher.curseforge.CurseForge.FileInfo i : lookupSome(launcher, ids)) info.put(i.id, i);
        for (Map.Entry<Integer, Integer> e : currentFile.entrySet()) {
            Integer next = nextFile.get(e.getKey());
            if (next == null) continue;
            com.gummer.launcher.curseforge.CurseForge.FileInfo nx = info.get(next), cur = info.get(e.getValue());
            String hash = hashOfMod.get(e.getKey());
            if (nx == null || hash == null || hash.equalsIgnoreCase(nx.sha1)) continue;
            found.put(hash, new Update(byHash.get(hash), com.gummer.launcher.curseforge.CurseBrowse.ID_PREFIX + e.getKey(),
                    label(nx), null, null, cur != null ? label(cur) : "?", "", nx));
        }
        return found;
    }

    /** Дані файлів CurseForge; файл, якого вже немає (автор прибрав), просто пропускається. */
    private static List<com.gummer.launcher.curseforge.CurseForge.FileInfo> lookupSome(com.skcraft.launcher.Launcher launcher, List<Integer> ids)
            throws IOException, InterruptedException {
        try {
            return com.gummer.launcher.curseforge.CurseForge.lookup(launcher, ids);
        } catch (IOException e) {
            if (e.getMessage() == null || !e.getMessage().startsWith("CurseForge has no file")) throw e;
            List<com.gummer.launcher.curseforge.CurseForge.FileInfo> out = new ArrayList<com.gummer.launcher.curseforge.CurseForge.FileInfo>();
            for (Integer id : ids) {
                try {
                    out.addAll(com.gummer.launcher.curseforge.CurseForge.lookup(launcher, java.util.Collections.singletonList(id)));
                } catch (IOException gone) {
                    // цього файлу на CurseForge вже немає
                }
            }
            return out;
        }
    }

    /** Назва версії CurseForge для списку: як її назвав автор, інакше назва файлу без .jar. */
    private static String label(com.gummer.launcher.curseforge.CurseForge.FileInfo i) {
        String n = i.displayName != null && !i.displayName.trim().isEmpty() ? i.displayName.trim() : i.fileName;
        return n == null ? "?" : n.replaceAll("(?i)\\.jar$", "");
    }

    /**
     * Поставити нову версію замість старого файлу: качається поруч, звіряється sha512, потім старий файл прибирається.
     * Вимкнений мод лишається вимкненим. Обов'язкові залежності нової версії ставляться, як при встановленні з
     * пошуку ({@code missing}, {@code conflicts} як у {@link ModrinthBrowse#install}). Повертає новий файл.
     */
    public static File apply(Update u, String game, String[] loaders, List<String> missing, List<String> conflicts)
            throws IOException, InterruptedException {
        if (u.cf != null) {
            com.skcraft.launcher.Launcher launcher = com.gummer.launcher.curseforge.CurseBrowse.shared();
            if (launcher == null) throw new IOException("CurseForge is not ready");
            return com.gummer.launcher.curseforge.CurseBrowse.replace(launcher, u.file, u.cf, u.currentNumber, game, loaders, missing, conflicts);
        }
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
        return new Update(f, u.projectId, u.versionNumber, u.version, u.primary, u.currentNumber, u.currentDate, u.cf);
    }

    /**
     * 18.11.2026 (власник: «список змін і для модів» «хороша ідея»): що написав автор мода про версії між поставленою
     * і новою (під цю гру і завантажувач, від нової вниз, до 10 версій), простим текстом. Не вдалось отримати список
     * версій: лише текст нової версії. Порожньо: автор нічого не написав. Не з потоку інтерфейсу.
     */
    public static String changelog(Update u, String game, String[] loaders) {
        // 25.11.2026: список змін CurseForge наш проксі поки не віддає
        if (u.cf != null) return com.skcraft.launcher.util.SharedLocale.tr("manage.mods.curseNoChangelog");
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
