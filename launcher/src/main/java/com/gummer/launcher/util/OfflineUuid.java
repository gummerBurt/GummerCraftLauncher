/*
 * GummerCraft Launcher
 * Copyright (C) 2021-2026 GummerCraft <http://gummercraft.fun>
 * Please see LICENSE.txt for license information.
 */

package com.gummer.launcher.util;

import lombok.extern.java.Log;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * 22.11.2026 (тікет #97: після імпорту зі SKlauncher у світі зникли квести FTB, дані ProjectE і доступ до привату FTB
 * Chunks; власник: «Варіант А, 100%»). UUID гравця без акаунта Microsoft для однієї збірки.
 *
 * <p>Гра, сервери в офлайн-режимі та інші лаунчери рахують офлайн UUID як MD5 від {@code "OfflinePlayer:" + нік}
 * ({@link #standard}). Лаунчер (ще з коду SKCraft, {@code OfflineSession}) рахував від голого ніка ({@link #legacy}).
 * В одиночній грі прогрес гравця (інвентар, квести, EMC, привати) прив'язаний до UUID, тож різні UUID це різні гравці.
 * Просто змінити формулу не можна: наші гравці втратили б прогрес у вже награних світах.</p>
 *
 * <p>Тому вибір для кожної збірки перед кожним запуском, за файлами гравця у світах ({@code saves/*}/playerdata):</p>
 * <ol>
 * <li>є файл зі стандартним UUID: стандартний (світ зі SKlauncher, офіційного лаунчера та інших);</li>
 * <li>є лише файли зі старим UUID: старий (світи, награні в нашому лаунчері до 1.36.12);</li>
 * <li>інакше (світів немає або в них лише чужі гравці): стандартний.</li>
 * </ol>
 * Сервери це не зачіпає: в офлайн-режимі UUID рахує сам сервер. Акаунти Microsoft мають справжній UUID і сюди не йдуть.
 */
@Log
public final class OfflineUuid {

    private OfflineUuid() {
    }

    /** UUID з нашого старого коду: MD5 від ніка (байти в кодуванні системи, як було). */
    public static String legacy(String name) {
        return UUID.nameUUIDFromBytes(name.getBytes()).toString();
    }

    /** Стандартний офлайн UUID гри: MD5 від {@code "OfflinePlayer:" + нік}. */
    public static String standard(String name) {
        return UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8)).toString();
    }

    /** UUID для запуску збірки з текою гри {@code contentDir} (у ній {@code saves}). */
    public static String forBuild(File contentDir, String name) {
        String std = standard(name);
        String old = legacy(name);
        File[] worlds = contentDir == null ? null : new File(contentDir, "saves").listFiles(File::isDirectory);
        boolean hasStd = false, hasOld = false;
        if (worlds != null) {
            for (File w : worlds) {
                File pd = new File(w, "playerdata");
                if (new File(pd, std + ".dat").isFile()) hasStd = true;
                if (new File(pd, old + ".dat").isFile()) hasOld = true;
            }
        }
        String pick = hasStd || !hasOld ? std : old;
        log.info("Offline UUID " + pick + " (" + (pick.equals(std) ? "standard" : "old launcher formula")
                + "; worlds with standard player file: " + hasStd + ", with old: " + hasOld + ")");
        return pick;
    }
}
