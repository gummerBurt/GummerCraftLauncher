/*
 * Copyright (C) 2021-2025 GummerCraft <http://gummercraft.fun>
 */

package com.gummer.launcher.i18n;

import java.util.Locale;
import java.util.logging.Level;

import com.skcraft.launcher.Launcher;
import com.skcraft.launcher.util.SharedLocale;

import lombok.extern.java.Log;

@Log
public class I18n {

    public static void changeLang(Lang la, Launcher launcher) {
        launcher.getConfig().setLang(la.getName());
        log.log(Level.INFO, "Changed locale to {0}", la.getName());
    }

    public static void load(Launcher launcher) {
        String saved = launcher.getConfig().getLang();
        if (saved == null || saved.trim().isEmpty()) {
            Lang detected = detect();
            launcher.getConfig().setLang(detected.getName()); // збережеться разом з рештою config.json
            log.log(Level.INFO, "No language chosen yet, system locale {0} / {1} -> {2}", new Object[]{
                    Locale.getDefault(Locale.Category.DISPLAY), Locale.getDefault(Locale.Category.FORMAT), detected.getId()});
        }
        Lang lang = Lang.getFromName(launcher.getConfig().getLang());
        SharedLocale.loadBundle("com.gummer.launcher.lang.Launcher_" + lang.getId(), Locale.getDefault());
        log.log(Level.INFO, "Loaded locale {0}", lang.getId());
    }

    /**
     * 21.11.2026 (власник: «в кого мова на компі укр або ру, ставити укр мову лаунчера, в кого англ, англійську»): мова
     * для нової теки даних. Дивимось і мову інтерфейсу системи, і регіональні формати: українець з англійською Windows
     * часто має український регіон, тож українська, якщо uk чи ru хоч у чомусь. Решта мов (і англійська) отримує
     * англійську, бо інших перекладів поки немає. Гравець, який уже вибрав мову, цього не помітить: вона в config.json.
     */
    public static Lang detect() {
        for (Locale.Category c : Locale.Category.values()) {
            String l = Locale.getDefault(c).getLanguage();
            if ("uk".equals(l) || "ru".equals(l)) return Lang.UKRAINIAN;
        }
        return Lang.ENGLISH;
    }

}
