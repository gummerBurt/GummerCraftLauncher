/*
 * Copyright (C) 2021-2026 GummerCraft <http://gummercraft.fun>
 */

package com.gummer.launcher.manage;

import com.fasterxml.jackson.databind.JsonNode;
import com.skcraft.launcher.Instance;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Logger;

/**
 * 1.36.1 (19.11.2026, власник: «Шукати відповідники модів з неофордж в форджі та заміняти їх»): мод не під завантажувач
 * збірки (NeoForge-мод у Forge-збірці, Fabric-мод у Forge і навпаки) замінюється версією того самого проекту на Modrinth
 * під завантажувач і версію гри збірки. Проект шукається за sha1 файлу, а якщо файлу на Modrinth немає, за id мода
 * ({@link DepFetch#slugOf}). Стабільна версія має перевагу над бетою. Ставиться як оновлення мода ({@link ModUpdates#apply}):
 * sha512, старий файл прибирається, обов'язкові залежності докачуються. Не знайшлось: старий файл лишається як є
 * (перевірка після цього запропонує його вимкнути).
 */
final class LoaderSwap {

    private static final Logger log = Logger.getLogger(LoaderSwap.class.getName());
    private static final String API = "https://api.modrinth.com/v2/";

    private LoaderSwap() {
    }

    /** @return новий файл або null (заміни немає чи не вдалось). */
    static File replace(Instance instance, File jar, ModInfo mi, List<String> missing) throws InterruptedException {
        String game = ModrinthBrowse.gameVersionOf(instance);
        String[] loaders = ModrinthBrowse.loadersOf(ModrinthBrowse.Kind.MOD, instance);
        if (game == null || loaders == null || loaders.length == 0 || jar == null || !jar.isFile()) return null;
        String project = null;
        try {
            project = ModrinthBrowse.getJson(API + "version_file/" + ModrinthBrowse.sha(jar, "SHA-1") + "?algorithm=sha1")
                    .path("project_id").asText(null);
        } catch (IOException e) {
            // файлу на Modrinth немає: шукаємо за id мода
        }
        if (project == null && mi != null && mi.id != null) {
            String slug = DepFetch.slugOf(mi.id);
            if (slug != null) {
                try {
                    JsonNode p = ModrinthBrowse.getJson(API + "project/" + slug);
                    if ("mod".equals(p.path("project_type").asText("mod"))) project = p.path("id").asText(null);
                } catch (IOException e) {
                    // і за id немає
                }
            }
        }
        if (project == null) {
            log.info("Loader swap: " + jar.getName() + " is not on Modrinth");
            return null;
        }
        try {
            StringBuilder l = new StringBuilder("[");
            for (int i = 0; i < loaders.length; i++) l.append(i > 0 ? "," : "").append('"').append(loaders[i]).append('"');
            JsonNode versions = ModrinthBrowse.getJson(API + "project/" + project + "/version?game_versions="
                    + ModrinthBrowse.enc("[\"" + game + "\"]") + "&loaders=" + ModrinthBrowse.enc(l.append(']').toString()));
            JsonNode chosen = null;
            for (JsonNode v : versions) { // від найновішої: перша стабільна, інакше найновіша будь-яка
                if (chosen == null) chosen = v;
                if ("release".equals(v.path("version_type").asText(""))) {
                    chosen = v;
                    break;
                }
            }
            if (chosen == null) {
                log.info("Loader swap: no " + Arrays.toString(loaders) + " " + game + " version of " + project + " (" + jar.getName() + ")");
                return null;
            }
            JsonNode primary = null;
            for (JsonNode f : chosen.path("files")) {
                if (f.path("primary").asBoolean(false)) {
                    primary = f;
                    break;
                }
                if (primary == null) primary = f;
            }
            if (primary == null) return null;
            ModUpdates.Update u = new ModUpdates.Update(jar, project, chosen.path("version_number").asText("?"), chosen,
                    primary, mi != null && mi.version != null ? mi.version : "?", "");
            File out = ModUpdates.apply(u, game, loaders, missing, null);
            log.info("Loader swap: " + jar.getName() + " -> " + out.getName());
            return out;
        } catch (IOException e) {
            log.info("Loader swap failed for " + jar.getName() + ": " + e);
            return null;
        }
    }
}
