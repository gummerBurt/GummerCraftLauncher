/*
 * Copyright (C) 2021-2026 GummerCraft <http://gummercraft.fun>
 */

package com.gummer.launcher.prefs;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.gummer.launcher.util.Json;

import java.io.File;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Дрібні налаштування інтерфейсу з 1.32 («одразу на сервер», закриті новини) у {@code <тека>/launcher-ui.json}.
 * Окремо від config.json: лаунчер 1.31 падає на невідомих полях config.json, а відкат на 1.31 має лишатись робочим.
 */
public final class LauncherPrefs {

    private static final Logger log = Logger.getLogger(LauncherPrefs.class.getName());
    private static final ObjectMapper MAPPER = Json.newMapper();
    private static File file;
    private static ObjectNode data = MAPPER.createObjectNode();

    private LauncherPrefs() {
    }

    /** Один раз при старті лаунчера. */
    public static synchronized void init(File baseDir) {
        file = new File(baseDir, "launcher-ui.json");
        if (file.isFile()) {
            try {
                Object read = MAPPER.readTree(file);
                if (read instanceof ObjectNode) data = (ObjectNode) read;
            } catch (Exception e) {
                log.log(Level.INFO, "launcher-ui.json is unreadable, starting from defaults", e);
            }
        }
    }

    public static synchronized String getString(String key, String def) {
        return data.hasNonNull(key) ? data.get(key).asText() : def;
    }

    public static synchronized boolean getBoolean(String key, boolean def) {
        return data.hasNonNull(key) ? data.get(key).asBoolean(def) : def;
    }

    public static synchronized void set(String key, String value) {
        data.put(key, value);
        save();
    }

    public static synchronized void set(String key, boolean value) {
        data.put(key, value);
        save();
    }

    private static void save() {
        if (file == null) return;
        try {
            MAPPER.writerWithDefaultPrettyPrinter().writeValue(file, data);
        } catch (Exception e) {
            log.log(Level.INFO, "Could not save launcher-ui.json", e);
        }
    }

    // ключі
    public static final String QUICK_JOIN = "quickJoin";
    public static final String HIDDEN_NEWS = "hiddenNews";
}
