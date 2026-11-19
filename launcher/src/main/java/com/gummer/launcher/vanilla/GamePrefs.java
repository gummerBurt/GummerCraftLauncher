/*
 * Copyright (C) 2021-2026 GummerCraft <http://gummercraft.fun>
 */

package com.gummer.launcher.vanilla;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gummer.launcher.util.Json;
import com.skcraft.launcher.Instance;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 1.36.2 (19.11.2026, власник: «Потрібно щоб вони залишились змінені, або навіть щоб наступні збірки повторювали їх зі
 * змінених гравцем»): мова і розмір інтерфейсу, які гравець сам змінив у грі. Перед запуском гри запам'ятовуються
 * значення з {@code options.txt} збірки ({@link #snapshot}), після закриття гри порівнюються ({@link #afterGame}); що
 * змінилось, те змінив гравець (модпак, який перезаписує налаштування, міняє їх до запуску, а не під час гри), і воно
 * пишеться в {@code game-prefs.json} у теці лаунчера. Нові збірки беруть мову і розмір звідти ({@link VanillaDefaults}).
 * Наявні збірки не чіпаються. Будь-яка помилка: лише лог.
 */
public final class GamePrefs {

    public static final String FILE = "game-prefs.json";
    static final String LANG = "lang";
    static final String GUI = "guiScale";

    private static final Logger log = Logger.getLogger(GamePrefs.class.getName());
    /** Значення до запуску гри: тека гри -> {lang, guiScale}. */
    private static final Map<String, String[]> BEFORE = new ConcurrentHashMap<String, String[]>();

    private GamePrefs() {
    }

    /** {lang, guiScale} з options.txt теки гри (чого немає: null). */
    static String[] read(File contentDir) {
        String[] out = new String[2];
        File f = new File(contentDir, "options.txt");
        if (!f.isFile()) return out;
        try {
            for (String line : Files.readAllLines(f.toPath(), StandardCharsets.UTF_8)) {
                if (line.startsWith("lang:")) out[0] = line.substring(5).trim();
                else if (line.startsWith("guiScale:")) out[1] = line.substring(9).trim();
            }
        } catch (IOException | RuntimeException e) {
            return new String[2];
        }
        if (out[0] != null && !out[0].matches("[A-Za-z]{2,3}_[A-Za-z0-9]{2,4}")) out[0] = null;
        if (out[1] != null && !out[1].matches("[0-9]{1,2}")) out[1] = null;
        return out;
    }

    /** Перед запуском гри (Runner, після VanillaDefaults). */
    public static void snapshot(Instance instance) {
        try {
            File dir = instance.getContentDir();
            BEFORE.put(dir.getAbsolutePath(), read(dir));
        } catch (RuntimeException e) {
            log.log(Level.FINE, "GamePrefs snapshot failed", e);
        }
    }

    /** Після закриття гри (LaunchProcessHandler). */
    public static void afterGame(File baseDir, Instance instance) {
        try {
            File dir = instance.getContentDir();
            String[] was = BEFORE.remove(dir.getAbsolutePath());
            if (was == null) return;
            String[] now = read(dir);
            Map<String, String> prefs = load(baseDir);
            boolean changed = false;
            // до гри значення не було: гра сама записала типове (en_us, авто), це не вибір гравця
            if (now[0] != null && was[0] != null && !now[0].equalsIgnoreCase(was[0])) {
                prefs.put(LANG, now[0].toLowerCase(java.util.Locale.ROOT));
                changed = true;
            }
            if (now[1] != null && was[1] != null && !now[1].equals(was[1])) {
                prefs.put(GUI, now[1]);
                changed = true;
            }
            if (!changed) return;
            ObjectMapper m = Json.newMapper();
            m.writerWithDefaultPrettyPrinter().writeValue(new File(baseDir, FILE), prefs);
            log.info("Game prefs changed by the player in " + instance.getName() + ": " + prefs);
        } catch (IOException | RuntimeException e) {
            log.log(Level.WARNING, "GamePrefs save failed", e);
        }
    }

    /** Збережені уподобання (порожньо, якщо файлу ще немає чи він зіпсований). */
    @SuppressWarnings("unchecked")
    static Map<String, String> load(File baseDir) {
        File f = new File(baseDir, FILE);
        if (baseDir == null || !f.isFile()) return new LinkedHashMap<String, String>();
        try {
            Map<String, Object> raw = Json.newMapper().readValue(f, Map.class);
            Map<String, String> out = new LinkedHashMap<String, String>();
            Object l = raw.get(LANG), g = raw.get(GUI);
            if (l instanceof String && ((String) l).matches("[A-Za-z]{2,3}_[A-Za-z0-9]{2,4}")) out.put(LANG, (String) l);
            if (g != null && String.valueOf(g).matches("[0-9]{1,2}")) out.put(GUI, String.valueOf(g));
            return out;
        } catch (IOException | RuntimeException e) {
            return new LinkedHashMap<String, String>();
        }
    }
}
