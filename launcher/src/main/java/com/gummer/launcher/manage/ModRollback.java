/*
 * Copyright (C) 2021-2026 GummerCraft <http://gummercraft.fun>
 */

package com.gummer.launcher.manage;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.logging.Logger;

/**
 * 19.11.2026 (власник: «Відкат моду після оновлення. -- та, це легко, давай»). Коли мод оновлюється з Modrinth
 * ({@link ModUpdates#apply}), стара версія не видаляється, а кладеться в {@code <гра>/.gummer/mod-prev/}. Якщо після
 * цього гра впала і винним названо саме цей мод (помічник при вильоті), з'являється кнопка «Повернути попередню версію».
 * Копія живе 30 днів; для мода лишається лише одна, остання. Тека поза {@code mods/}, тож гра її не бачить.
 */
public final class ModRollback {

    private static final Logger log = Logger.getLogger(ModRollback.class.getName());
    private static final ObjectMapper MAPPER = new ObjectMapper();
    /** Скільки тримати копію старої версії. */
    private static final long KEEP_MS = 30L * 24 * 3600 * 1000;
    /** Кнопку пропонуємо, якщо мод оновлено не раніше ніж стільки тому. */
    public static final long OFFER_MS = 14L * 24 * 3600 * 1000;

    /** Що можна повернути. */
    public static final class Entry {
        /** Поточний (новий) файл у mods. */
        public final File current;
        /** Збережена стара версія. */
        public final File prev;
        public final String prevVersion;
        public final long time;
        final String projectId;

        Entry(File current, File prev, String prevVersion, long time, String projectId) {
            this.current = current;
            this.prev = prev;
            this.prevVersion = prevVersion;
            this.time = time;
            this.projectId = projectId;
        }
    }

    private ModRollback() {
    }

    private static File store(File modsDir) {
        return new File(new File(modsDir.getParentFile(), ".gummer"), "mod-prev");
    }

    private static File index(File modsDir) {
        return new File(store(modsDir), "index.json");
    }

    private static ArrayNode read(File modsDir) {
        File f = index(modsDir);
        try {
            if (f.isFile()) {
                JsonNode n = MAPPER.readTree(f);
                if (n.isArray()) return (ArrayNode) n;
            }
        } catch (IOException | RuntimeException e) {
            log.info("Mod rollback index unreadable: " + e);
        }
        return MAPPER.createArrayNode();
    }

    private static void write(File modsDir, ArrayNode a) throws IOException {
        File f = index(modsDir);
        f.getParentFile().mkdirs();
        File tmp = new File(f.getPath() + ".tmp");
        MAPPER.writerWithDefaultPrettyPrinter().writeValue(tmp, a);
        Files.move(tmp.toPath(), f.toPath(), StandardCopyOption.REPLACE_EXISTING);
    }

    private static String base(String name) {
        return name.toLowerCase(Locale.ROOT).endsWith(InstanceMods.DISABLED) ? name.substring(0, name.length() - InstanceMods.DISABLED.length()) : name;
    }

    /**
     * Перед заміною: перенести старий файл у сховище замість видалення. Не вийшло: як раніше (виклик потім його видалить).
     *
     * @return true, якщо старий файл уже перенесено
     */
    public static boolean keep(File old, String newName, String prevVersion, String projectId) { // 25.11.2026: public для оновлень з CurseForge
        File modsDir = old.getParentFile();
        try {
            File dir = store(modsDir);
            dir.mkdirs();
            ArrayNode a = read(modsDir);
            prune(modsDir, a, base(newName), base(old.getName()));
            File dest = new File(dir, System.currentTimeMillis() + "-" + base(old.getName()));
            Files.move(old.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
            ObjectNode e = a.addObject();
            e.put("current", base(newName));
            e.put("prev", dest.getName());
            e.put("prevName", base(old.getName()));
            e.put("prevVersion", prevVersion == null ? "" : prevVersion);
            e.put("time", System.currentTimeMillis());
            if (projectId != null) e.put("project", projectId);
            write(modsDir, a);
            return true;
        } catch (IOException | RuntimeException e) {
            log.info("Mod rollback: could not keep " + old.getName() + ": " + e);
            return false;
        }
    }

    /** Прибрати старі копії (старші за 30 днів) і попередні копії цього ж мода. */
    private static void prune(File modsDir, ArrayNode a, String newBase, String oldBase) {
        long now = System.currentTimeMillis();
        for (int i = a.size() - 1; i >= 0; i--) {
            JsonNode e = a.get(i);
            String cur = e.path("current").asText("");
            boolean same = cur.equals(oldBase) || cur.equals(newBase);
            if (same || now - e.path("time").asLong(0) > KEEP_MS) {
                new File(store(modsDir), e.path("prev").asText("-")).delete();
                a.remove(i);
            }
        }
    }

    /** Нещодавнє оновлення цього файлу, яке можна відкотити, або null. */
    public static Entry recentFor(File modFile) {
        if (modFile == null) return null;
        File modsDir = modFile.getParentFile();
        String name = base(modFile.getName());
        ArrayNode a = read(modsDir);
        for (int i = a.size() - 1; i >= 0; i--) {
            JsonNode e = a.get(i);
            if (!name.equals(e.path("current").asText(""))) continue;
            long t = e.path("time").asLong(0);
            if (System.currentTimeMillis() - t > OFFER_MS) return null;
            File prev = new File(store(modsDir), e.path("prev").asText("-"));
            if (!prev.isFile()) return null;
            return new Entry(modFile, prev, e.path("prevVersion").asText(""), t, e.path("project").asText(null));
        }
        return null;
    }

    /** Повернути стару версію: новий файл видаляється, старий стає на його місце (вимкнений лишається вимкненим). */
    public static File rollback(Entry en) throws IOException {
        File modsDir = en.current.getParentFile();
        ArrayNode a = read(modsDir);
        String prevName = null;
        for (int i = a.size() - 1; i >= 0; i--) {
            JsonNode e = a.get(i);
            if (en.prev.getName().equals(e.path("prev").asText(""))) {
                prevName = e.path("prevName").asText(null);
                a.remove(i);
            }
        }
        if (prevName == null) prevName = en.prev.getName().replaceFirst("^[0-9]+-", "");
        boolean disabled = en.current.getName().toLowerCase(Locale.ROOT).endsWith(InstanceMods.DISABLED);
        File target = new File(modsDir, disabled ? prevName + InstanceMods.DISABLED : prevName);
        Files.move(en.prev.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
        if (!target.equals(en.current)) Files.deleteIfExists(en.current.toPath());
        write(modsDir, a);
        if (en.projectId != null) ModrinthBrowse.record(modsDir, en.projectId, prevName);
        log.info("Mod rolled back: " + en.current.getName() + " -> " + target.getName());
        return target;
    }
}
