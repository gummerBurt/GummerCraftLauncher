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

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 18.11.2026 (власник: «повернення до початкового стану для копій збірок; зроби ... щоб воно не ламало збереження і
 * світи»). Копія збірки запам'ятовує свій стан у момент копіювання: перелік файлів з sha1 ({@value #MANIFEST}) і знімок
 * ({@value #ORIGINAL}): дрібні файли (конфіги) копіями, моди й великі файли жорсткими посиланнями (на тому ж диску місця
 * не займають, а мод при оновленні замінюється новим файлом, тож посилання лишається старим). «Повернути до початкового
 * стану» вертає змінені й видалені файли (зі знімка, інакше з Modrinth за sha1), вмикає вимкнені моди, а свої моди
 * прибирає. Усе, що міняється, спершу в {@value #BACKUP}; «Скасувати повернення» вертає як було.
 *
 * <p>Не чіпаються ніколи: світи (saves), скріншоти, логи, налаштування гри (options*.txt, servers.dat). Копії,
 * зроблені до 18.11.2026, знімка не мають: для них повернення недоступне.</p>
 */
public final class CopyReset {

    private static final Logger log = Logger.getLogger(CopyReset.class.getName());
    private static final ObjectMapper MAPPER = Json.newMapper();
    public static final String MANIFEST = "copy-files.json";
    public static final String ORIGINAL = "copy-original";
    public static final String BACKUP = "reset-backup";
    private static final long SMALL = 2L * 1024 * 1024;
    private static final long SMALL_TOTAL = 200L * 1024 * 1024;
    private static final String API = "https://api.modrinth.com/v2/";

    /** Верхні теки і файли в minecraft, які не входять у «стан збірки». */
    private static final Set<String> SKIP_TOP = new HashSet<String>(java.util.Arrays.asList(
            "saves", "screenshots", "logs", "crash-reports", "debug", ".mixin.out", "natives", ".fabric", "options.txt",
            "optionsof.txt", "optionsshaders.txt", "servers.dat", "servers.dat_old", "usercache.json", "usernamecache.json",
            "realms_persistence.json", "command_history.txt", "patchouli_data.json", "backups", "schematics", "journeymap",
            "xaero", "replay_recordings", ModSides.HASH_FILE));

    private CopyReset() {
    }

    // ---------------------------------------------------------------- запис при копіюванні

    /** Записати стан щойно скопійованої збірки (тека збірки, де є minecraft). Помилка не зупиняє копіювання. */
    public static void record(File instanceDir) {
        File content = new File(instanceDir, "minecraft");
        File snap = new File(instanceDir, ORIGINAL);
        try {
            ObjectNode root = MAPPER.createObjectNode();
            root.put("at", System.currentTimeMillis());
            ObjectNode files = root.putObject("files");
            long[] small = {0};
            walk(content, "", files, snap, small);
            MAPPER.writerWithDefaultPrettyPrinter().writeValue(new File(instanceDir, MANIFEST), root);
        } catch (Exception e) {
            log.log(Level.WARNING, "Could not record the initial state of the copy " + instanceDir.getName(), e);
        }
    }

    private static void walk(File dir, String rel, ObjectNode files, File snap, long[] small) throws IOException {
        File[] items = dir.listFiles();
        if (items == null) return;
        for (File f : items) {
            String name = f.getName();
            String path = rel.isEmpty() ? name : rel + "/" + name;
            if (rel.isEmpty() && SKIP_TOP.contains(name.toLowerCase(Locale.ROOT))) continue;
            if (f.isDirectory()) {
                walk(f, path, files, snap, small);
                continue;
            }
            if (!f.isFile()) continue;
            String key = path;
            boolean disabled = name.endsWith(InstanceMods.DISABLED);
            if (disabled) key = path.substring(0, path.length() - InstanceMods.DISABLED.length());
            ObjectNode e = files.putObject(key);
            e.put("sha1", ModrinthBrowse.sha(f, "SHA-1"));
            if (disabled) e.put("disabled", true);
            File to = new File(snap, key);
            to.getParentFile().mkdirs();
            boolean copied = false;
            if (f.length() <= SMALL && !key.startsWith("mods/") && small[0] + f.length() <= SMALL_TOTAL) {
                Files.copy(f.toPath(), to.toPath(), StandardCopyOption.REPLACE_EXISTING);
                small[0] += f.length();
                copied = true;
            } else {
                try {
                    Files.deleteIfExists(to.toPath());
                    Files.createLink(to.toPath(), f.toPath());
                    copied = true;
                } catch (IOException | UnsupportedOperationException | SecurityException ex) {
                    // інший диск чи файлова система без посилань: при поверненні з Modrinth за sha1
                }
            }
            e.put("snap", copied);
        }
    }

    // ---------------------------------------------------------------- план

    public static boolean canReset(Instance instance) {
        return instance != null && instance.isCopy() && new File(instance.getDir(), MANIFEST).isFile();
    }

    public static boolean hasBackup(Instance instance) {
        return instance != null && new File(new File(instance.getDir(), BACKUP), "backup.json").isFile();
    }

    /** Що зробить повернення (рахується локально, без мережі). */
    public static final class Plan {
        public final List<String> restore = new ArrayList<String>();
        public final List<String> enable = new ArrayList<String>();
        public final List<String> disable = new ArrayList<String>();
        public final List<String> remove = new ArrayList<String>();
        final Map<String, String> sha = new LinkedHashMap<String, String>();

        public boolean isEmpty() {
            return restore.isEmpty() && enable.isEmpty() && disable.isEmpty() && remove.isEmpty();
        }
    }

    public static Plan plan(Instance instance) throws IOException {
        Plan plan = new Plan();
        File content = new File(instance.getDir(), "minecraft");
        JsonNode files = MAPPER.readTree(new File(instance.getDir(), MANIFEST)).path("files");
        Iterator<Map.Entry<String, JsonNode>> it = files.fields();
        Set<String> known = new HashSet<String>();
        while (it.hasNext()) {
            Map.Entry<String, JsonNode> e = it.next();
            String p = e.getKey();
            if (!safePath(p)) continue;
            known.add(p);
            String sha = e.getValue().path("sha1").asText("");
            boolean wasOff = e.getValue().path("disabled").asBoolean(false);
            plan.sha.put(p, sha);
            File on = new File(content, p), off = new File(content, p + InstanceMods.DISABLED);
            File cur = on.isFile() ? on : off.isFile() ? off : null;
            if (cur == null || !sha.equalsIgnoreCase(ModrinthBrowse.sha(cur, "SHA-1"))) plan.restore.add(p);
            else if (cur == off && !wasOff) plan.enable.add(p);
            else if (cur == on && wasOff) plan.disable.add(p);
        }
        File[] mods = new File(content, "mods").listFiles();
        if (mods != null) {
            Set<String> seen = new HashSet<String>();
            for (File f : mods) {
                if (!f.isFile()) continue;
                String n = f.getName();
                if (n.endsWith(InstanceMods.DISABLED)) n = n.substring(0, n.length() - InstanceMods.DISABLED.length());
                String p = "mods/" + n;
                if (!known.contains(p) && seen.add(p)) plan.remove.add(p);
            }
        }
        return plan;
    }

    private static boolean safePath(String p) {
        return !p.isEmpty() && !p.startsWith("/") && !p.contains("..") && !p.contains(":") && !p.contains("\\")
                && !SKIP_TOP.contains(p.split("/")[0].toLowerCase(Locale.ROOT));
    }

    // ---------------------------------------------------------------- застосування

    /**
     * Повернути. Повертає шляхи, які не вдалося відновити (немає знімка і немає на Modrinth): вони лишаються як є.
     * Помилка посередині: усе вертається з резервної копії.
     */
    public static List<String> apply(Instance instance, Plan plan) throws IOException, InterruptedException {
        File dir = instance.getDir();
        File content = new File(dir, "minecraft");
        File snap = new File(dir, ORIGINAL);
        File tmp = new File(dir, ".reset-tmp");
        deleteTree(tmp);
        tmp.mkdirs();
        List<String> lost = new ArrayList<String>();
        try {
            // 1. усе, що треба повернути, спершу поруч і звірити (нічого в збірці ще не змінено)
            Map<String, File> ready = new LinkedHashMap<String, File>();
            for (String p : plan.restore) {
                if (Thread.interrupted()) throw new InterruptedException();
                String sha = plan.sha.get(p);
                File t = new File(tmp, p);
                t.getParentFile().mkdirs();
                File s = new File(snap, p);
                boolean ok = false;
                if (s.isFile() && sha.equalsIgnoreCase(ModrinthBrowse.sha(s, "SHA-1"))) {
                    Files.copy(s.toPath(), t.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    ok = true;
                } else {
                    ok = fromModrinth(sha, t);
                }
                if (ok) ready.put(p, t);
                else lost.add(p);
            }

            // 2. резервна копія і журнал
            File backup = new File(dir, BACKUP);
            deleteTree(backup);
            File backupFiles = new File(backup, "files");
            backupFiles.mkdirs();
            ObjectNode journal = MAPPER.createObjectNode();
            journal.put("at", System.currentTimeMillis());
            ArrayNode added = journal.putArray("added");
            ArrayNode moved = journal.putArray("backedUp");
            ArrayNode renamed = journal.putArray("renamed");
            File jf = new File(backup, "backup.json");
            MAPPER.writerWithDefaultPrettyPrinter().writeValue(jf, journal);
            try {
                for (Map.Entry<String, File> e : ready.entrySet()) {
                    String p = e.getKey();
                    for (String actual : new String[]{p, p + InstanceMods.DISABLED}) moveToBackup(content, backupFiles, actual, moved);
                    File target = new File(content, p);
                    target.getParentFile().mkdirs();
                    Files.move(e.getValue().toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    added.add(p);
                }
                for (String p : plan.enable) rename(content, p + InstanceMods.DISABLED, p, renamed);
                for (String p : plan.disable) rename(content, p, p + InstanceMods.DISABLED, renamed);
                for (String p : plan.remove) {
                    for (String actual : new String[]{p, p + InstanceMods.DISABLED}) moveToBackup(content, backupFiles, actual, moved);
                }
                MAPPER.writerWithDefaultPrettyPrinter().writeValue(jf, journal);
            } catch (IOException e) {
                MAPPER.writerWithDefaultPrettyPrinter().writeValue(jf, journal);
                log.log(Level.WARNING, "Copy reset failed half-way, restoring " + instance.getName(), e);
                try {
                    restore(instance);
                } catch (IOException r) {
                    log.log(Level.SEVERE, "Restore after a failed reset also failed: " + instance.getName(), r);
                }
                throw e;
            }
            log.info("Copy " + instance.getName() + " reset: " + ready.size() + " restored, " + plan.enable.size() + " enabled, "
                    + plan.remove.size() + " own mods moved to backup, " + lost.size() + " not restorable");
            return lost;
        } finally {
            deleteTree(tmp);
        }
    }

    private static void moveToBackup(File content, File backupFiles, String actual, ArrayNode moved) throws IOException {
        File from = new File(content, actual);
        if (!from.isFile()) return;
        File to = new File(backupFiles, actual);
        to.getParentFile().mkdirs();
        Files.move(from.toPath(), to.toPath(), StandardCopyOption.REPLACE_EXISTING);
        moved.add(actual);
    }

    private static void rename(File content, String from, String to, ArrayNode renamed) throws IOException {
        File a = new File(content, from), b = new File(content, to);
        if (!a.isFile() || b.exists()) return;
        Files.move(a.toPath(), b.toPath());
        ObjectNode r = renamed.addObject();
        r.put("from", from);
        r.put("to", to);
    }

    /** Файл з Modrinth за sha1 (лише з їхнього CDN, звірка sha1). */
    private static boolean fromModrinth(String sha1, File to) throws InterruptedException {
        if (sha1 == null || !sha1.matches("[0-9a-fA-F]{40}")) return false;
        try {
            JsonNode v = ModrinthBrowse.getJson(API + "version_file/" + sha1 + "?algorithm=sha1");
            for (JsonNode f : v.path("files")) {
                if (!sha1.equalsIgnoreCase(f.path("hashes").path("sha1").asText(""))) continue;
                URL u = new URL(f.path("url").asText(""));
                if (!"https".equals(u.getProtocol()) || !ModrinthBrowse.CDN_HOST.equalsIgnoreCase(u.getHost())) return false;
                HttpRequest.get(u).execute().expectResponseCode(200).saveContent(to);
                if (sha1.equalsIgnoreCase(ModrinthBrowse.sha(to, "SHA-1"))) return true;
                to.delete();
                return false;
            }
        } catch (IOException e) {
            log.info("Not on Modrinth: " + sha1);
        }
        return false;
    }

    /** «Скасувати повернення»: прибрати повернуте, покласти назад відкладене, вернути перейменування. */
    public static void restore(Instance instance) throws IOException {
        File dir = instance.getDir();
        File content = new File(dir, "minecraft");
        File backup = new File(dir, BACKUP);
        File jf = new File(backup, "backup.json");
        if (!jf.isFile()) return;
        JsonNode j = MAPPER.readTree(jf);
        for (JsonNode a : j.path("added")) {
            String p = a.asText("");
            if (safePath(p)) new File(content, p).delete();
        }
        List<JsonNode> renamed = new ArrayList<JsonNode>();
        for (JsonNode r : j.path("renamed")) renamed.add(r);
        for (int i = renamed.size() - 1; i >= 0; i--) {
            File b = new File(content, renamed.get(i).path("to").asText("")), a = new File(content, renamed.get(i).path("from").asText(""));
            if (b.isFile() && !a.exists()) Files.move(b.toPath(), a.toPath());
        }
        File files = new File(backup, "files");
        for (JsonNode m : j.path("backedUp")) {
            String p = m.asText("");
            if (!safePath(p)) continue;
            File from = new File(files, p), to = new File(content, p);
            if (!from.isFile()) continue;
            to.getParentFile().mkdirs();
            Files.move(from.toPath(), to.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
        deleteTree(backup);
        log.info("Copy reset undone: " + instance.getName());
    }

    private static void deleteTree(File f) {
        File[] kids = f.listFiles();
        if (kids != null) for (File k : kids) deleteTree(k);
        f.delete();
    }
}
