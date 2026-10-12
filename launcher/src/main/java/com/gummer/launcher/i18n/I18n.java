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
        Lang lang = Lang.getFromName(launcher.getConfig().getLang());
        SharedLocale.loadBundle("com.gummer.launcher.lang.Launcher_" + lang.getId(), Locale.getDefault());
        log.log(Level.INFO, "Loaded locale {0}", lang.getId());
    }

}
