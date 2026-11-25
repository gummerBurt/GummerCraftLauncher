/*
 * Copyright (C) 2021-2026 GummerCraft <http://gummercraft.fun>
 */

package com.gummer.launcher.manage;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.skcraft.launcher.Instance;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.logging.Logger;

/**
 * 25.11.2026 (тікет #122): що лаунчер сам оновив перед запуском ({@link ModVersionFix}), у {@code .gummer/autofix.json}
 * теки гри. Щоб автовиправлення не ходило по колу: після нього гра має хоч раз закритись без вильоту
 * ({@link #markOk}), інакше наступні запуски нічого самі не міняють, а вікно вильоту пропонує відкотити зроблене.
 * Відкочене запам'ятовується ({@code declined}): ту саму вимогу лаунчер більше сам не виправляє.
 */
public final class AutoFixLog {

    private static final Logger log = Logger.getLogger(AutoFixLog.class.getName());
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String FILE = ".gummer/autofix.json";

    /** Одне автовиправлення: залежність {@code dep} з {@code from} на {@code to}, новий файл {@code file}. */
    public static final class Fix {
        public final String dep;
        public final String need;
        public final String from;
        public final String to;
        public final File file;

        Fix(String dep, String need, String from, String to, File file) {
            this.dep = dep;
            this.need = need;
            this.from = from;
            this.to = to;
            this.file = file;
        }
    }

    private AutoFixLog() {
    }

    private static File file(Instance instance) {
        return new File(instance.getContentDir(), FILE);
    }

    private static ObjectNode read(Instance instance) {
        File f = file(instance);
        try {
            if (f.isFile()) {
                JsonNode n = MAPPER.readTree(f);
                if (n instanceof ObjectNode) return (ObjectNode) n;
            }
        } catch (IOException | RuntimeException e) {
            log.info("Auto-fix log unreadable: " + e);
        }
        return MAPPER.createObjectNode();
    }

    private static void write(Instance instance, ObjectNode n) {
        File f = file(instance);
        try {
            f.getParentFile().mkdirs();
            File tmp = new File(f.getPath() + ".tmp");
            MAPPER.writerWithDefaultPrettyPrinter().writeValue(tmp, n);
            Files.move(tmp.toPath(), f.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException | RuntimeException e) {
            log.info("Auto-fix log not written: " + e);
        }
    }

    private static String key(String dep, String need) {
        return (dep == null ? "" : dep.toLowerCase(Locale.ROOT)) + ">=" + (need == null ? "" : need);
    }

    /** Записати зроблене перед цим запуском (додається до ще не підтвердженого). */
    static synchronized void record(Instance instance, List<Fix> fixes) {
        if (fixes.isEmpty()) return;
        ObjectNode n = read(instance);
        ArrayNode a = n.has("pending") && n.get("pending").isArray() ? (ArrayNode) n.get("pending") : n.putArray("pending");
        for (Fix f : fixes) {
            ObjectNode o = a.addObject();
            o.put("dep", f.dep);
            o.put("need", f.need);
            o.put("from", f.from);
            o.put("to", f.to);
            o.put("file", f.file.getName());
        }
        n.put("time", System.currentTimeMillis());
        write(instance, n);
    }

    /** Чи є автовиправлення, після якого гра ще жодного разу не закрилась без вильоту. */
    static boolean unconfirmed(Instance instance) {
        JsonNode a = read(instance).path("pending");
        return a.isArray() && a.size() > 0;
    }

    /** Чи гравець відкотив автовиправлення саме цієї вимоги (тоді лаунчер її сам не чіпає). */
    static boolean declined(Instance instance, String dep, String need) {
        String k = key(dep, need);
        for (JsonNode d : read(instance).path("declined")) if (k.equals(d.asText())) return true;
        return false;
    }

    /** Гра закрилась без вильоту: автовиправлення спрацювало, запис більше не потрібен. */
    public static synchronized void markOk(Instance instance) {
        try {
            if (!file(instance).isFile()) return;
            ObjectNode n = read(instance);
            if (!n.has("pending")) return;
            n.remove("pending");
            n.remove("time");
            write(instance, n);
        } catch (RuntimeException ignored) {
            // без запису: наступний запуск просто не виправлятиме сам
        }
    }

    /** Ще не підтверджені автовиправлення, які можна відкотити (стара версія збережена в {@link ModRollback}). */
    public static List<Fix> pending(Instance instance) {
        List<Fix> out = new ArrayList<Fix>();
        try {
            File mods = new File(instance.getContentDir(), "mods");
            for (JsonNode o : read(instance).path("pending")) {
                File f = new File(mods, o.path("file").asText("-"));
                if (!f.isFile()) f = new File(mods, f.getName() + InstanceMods.DISABLED);
                if (!f.isFile() || ModRollback.recentFor(f) == null) continue;
                out.add(new Fix(o.path("dep").asText(""), o.path("need").asText(""), o.path("from").asText(""), o.path("to").asText(""), f));
            }
        } catch (RuntimeException e) {
            log.info("Auto-fix log: " + e);
        }
        return out;
    }

    /**
     * Відкотити все ще не підтверджене: старі версії повертаються ({@link ModRollback}), а ці вимоги лаунчер більше
     * сам не виправляє. Повертає описи відкоченого («create 6.0.8 → 6.0.6»).
     */
    public static synchronized List<String> rollbackAll(Instance instance) throws IOException {
        List<String> done = new ArrayList<String>();
        ObjectNode n = read(instance);
        ArrayNode declined = n.has("declined") && n.get("declined").isArray() ? (ArrayNode) n.get("declined") : n.putArray("declined");
        IOException first = null;
        for (Fix f : pending(instance)) {
            try {
                ModRollback.Entry e = ModRollback.recentFor(f.file);
                if (e == null) continue;
                ModRollback.rollback(e);
                done.add(f.dep + " " + f.to + " → " + f.from);
                String k = key(f.dep, f.need);
                boolean seen = false;
                for (JsonNode d : declined) seen |= k.equals(d.asText());
                if (!seen) declined.add(k);
            } catch (IOException e) {
                if (first == null) first = e;
            }
        }
        n.remove("pending");
        n.remove("time");
        write(instance, n);
        if (first != null && done.isEmpty()) throw first;
        return done;
    }
}
