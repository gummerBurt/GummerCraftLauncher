/*
 * GummerCraft Launcher
 */

package com.gummer.launcher.manage;

import com.fasterxml.jackson.databind.JsonNode;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.logging.Logger;

/**
 * 27.11.2026 (тікет #139, власник: «якщо Sodium стоїть бета- чи альфа-версією, то "Поставити стабільну Sodium"; інакше
 * "Поставити попередню версію Sodium"»). Мод A не вставився в мод B, бо стоїть не та версія B. Яка саме версія B
 * сумісна з A, лаунчер не знає (автори рідко це пишуть), тож евристика: замість бети чи альфи найновіша стабільна
 * (release) під цю гру і завантажувач, а якщо стоїть стабільна, то попередня за датою. Ставиться через
 * {@link ModUpdates#apply} (перевірка завантажувача по самому jar, залежності), стара версія йде в
 * {@link ModRollback}, тож у наступному вікні вильоту є «Повернути». Лише моди з Modrinth.
 */
public final class ModSwap {

    private static final Logger log = Logger.getLogger(ModSwap.class.getName());
    private static final String API = "https://api.modrinth.com/v2/";
    /** Скільки версій пробувати, якщо найближча не йде під завантажувач збірки. */
    private static final int MAX_TRIES = 4;

    private ModSwap() {
    }

    /** Версія мода не стабільна: бета, альфа, rc, pre (з jar). */
    public static boolean isPrerelease(String version) {
        if (version == null) return false;
        String v = version.toLowerCase(Locale.ROOT);
        return v.matches(".*(alpha|beta|[-.+]rc[-.0-9]|[-.+]rc$|[-.+]pre[-.0-9]|[-.+]pre$|snapshot).*");
    }

    /**
     * Поставити замість jar стабільну ({@code stable}) або попередню версію. Повертає номер поставленої версії.
     * IOException з текстом для гравця, якщо мода немає на Modrinth чи підхожої версії немає.
     */
    public static String swap(File jar, boolean stable, String game, String[] loaders) throws IOException, InterruptedException {
        if (game == null || loaders == null || loaders.length == 0) throw new IOException(com.skcraft.launcher.util.SharedLocale.tr("crash.swapMod.notModrinth"));
        String sha1 = ModSides.sha1Cached(jar);
        JsonNode now;
        try {
            now = ModrinthBrowse.getJson(API + "version_file/" + sha1 + "?algorithm=sha1");
        } catch (IOException e) {
            throw new IOException(com.skcraft.launcher.util.SharedLocale.tr("crash.swapMod.notModrinth"), e);
        }
        String projectId = now.path("project_id").asText("");
        if (!projectId.matches("[A-Za-z0-9]+")) throw new IOException(com.skcraft.launcher.util.SharedLocale.tr("crash.swapMod.notModrinth"));
        String nowId = now.path("id").asText("");
        String nowDate = now.path("date_published").asText("");
        // стоїть стабільна з погляду Modrinth, хоча в jar написано beta: тоді попередня
        boolean toStable = stable && !"release".equals(now.path("version_type").asText(""));
        StringBuilder l = new StringBuilder("[");
        for (int i = 0; i < loaders.length; i++) l.append(i > 0 ? "," : "").append('"').append(loaders[i]).append('"');
        JsonNode versions = ModrinthBrowse.getJson(API + "project/" + projectId + "/version?game_versions="
                + ModrinthBrowse.enc("[\"" + game + "\"]") + "&loaders=" + ModrinthBrowse.enc(l.append(']').toString()));
        List<JsonNode> cands = new ArrayList<JsonNode>();
        for (JsonNode v : versions) { // Modrinth віддає від найновішої
            if (nowId.equals(v.path("id").asText(""))) continue;
            if (toStable) {
                if ("release".equals(v.path("version_type").asText(""))) cands.add(v);
            } else if (v.path("date_published").asText("").compareTo(nowDate) < 0) {
                // попередня: старіша за поставлену, спершу стабільні, якщо поставлена теж стабільна
                cands.add(v);
            }
        }
        if (!toStable) {
            List<JsonNode> rel = new ArrayList<JsonNode>();
            for (JsonNode v : cands) if ("release".equals(v.path("version_type").asText(""))) rel.add(v);
            if (!rel.isEmpty() && "release".equals(now.path("version_type").asText(""))) cands = rel;
        }
        if (cands.isEmpty()) throw new IOException(com.skcraft.launcher.util.SharedLocale.tr(toStable ? "crash.swapMod.noStable" : "crash.swapMod.noOlder", game));
        IOException last = null;
        for (int i = 0; i < cands.size() && i < MAX_TRIES; i++) {
            JsonNode v = cands.get(i);
            JsonNode primary = ModUpdates.primaryFile(v);
            if (primary == null) continue;
            ModUpdates.Update u = new ModUpdates.Update(jar, projectId, v.path("version_number").asText("?"), v, primary,
                    now.path("version_number").asText("?"), "");
            try {
                ModUpdates.apply(u, game, loaders, new ArrayList<String>(), new ArrayList<String>());
                log.info("Mod swapped (" + (toStable ? "stable" : "previous") + "): " + jar.getName() + " "
                        + now.path("version_number").asText("?") + " -> " + u.versionNumber);
                return u.versionNumber;
            } catch (ModUpdates.LoaderTooOld e) {
                last = e;
                log.info("Mod swap: " + u.versionNumber + " does not fit the loader, trying older");
            }
        }
        throw last != null ? last : new IOException(com.skcraft.launcher.util.SharedLocale.tr(toStable ? "crash.swapMod.noStable" : "crash.swapMod.noOlder", game));
    }
}
