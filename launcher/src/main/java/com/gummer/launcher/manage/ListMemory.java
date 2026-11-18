/*
 * Copyright (C) 2021-2026 GummerCraft <http://gummercraft.fun>
 */

package com.gummer.launcher.manage;

import com.skcraft.launcher.Configuration;
import com.skcraft.launcher.Instance;
import com.skcraft.launcher.persistence.Persistence;
import com.skcraft.launcher.swing.InstanceTableModel;

/**
 * 17.11.2026 (власник: «пам'ять має працювати як "що було відкрите останнє у встановлених" та "що було останнє вибране
 * в усіх"»). У кожного режиму списку свій останній вибір: {@code lastInstanceAll} і {@code lastInstanceInstalled} у
 * config.json. У «Усі» це може бути і рядок Modrinth. {@code lastInstance} лишається останньою справжньою збіркою
 * (з неї стартує вікно, бере тип і фон), рядки-сторінки туди не пишуться.
 */
public final class ListMemory {

    private ListMemory() {
    }

    /** Що вибрати в режимі: його останній вибір, інакше остання збірка. */
    public static String recall(Configuration c, boolean installedOnly) {
        String s = installedOnly ? c.getLastInstanceInstalled() : c.getLastInstanceAll();
        return s != null ? s : c.getLastInstance();
    }

    /** Запам'ятати вибір гравця в поточному режимі. «+ Додати збірку» не запам'ятовується. */
    public static void remember(Configuration c, Instance selected) {
        if (selected == null || InstanceTableModel.isAddRow(selected) || selected.getName() == null) return;
        String name = selected.getName();
        boolean changed = false;
        if (c.isInstalledOnly()) {
            if (!name.equals(c.getLastInstanceInstalled())) {
                c.setLastInstanceInstalled(name);
                changed = true;
            }
        } else if (!name.equals(c.getLastInstanceAll())) {
            c.setLastInstanceAll(name);
            changed = true;
        }
        if (!InstanceTableModel.isBrowser(selected) && !name.equals(c.getLastInstance())) {
            c.setLastInstance(name);
            changed = true;
        }
        if (changed) Persistence.commitAndForget(c);
    }
}
