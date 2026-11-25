/*
 * Copyright (C) 2021-2026 GummerCraft <http://gummercraft.fun>
 */

package com.gummer.launcher.manage;

import com.fasterxml.jackson.databind.JsonNode;
import com.skcraft.launcher.Instance;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

/**
 * 18.11.2026 (власник: «коли моду бракує іншого мода ... а можна дати можливість докачати цей мод? Або докачати
 * автоматично»). Докачати обов'язкові залежності мода з Modrinth у теку модів збірки (з 25.11.2026: чого на Modrinth
 * немає, з CurseForge, лише точний збіг slug чи назви; мод не з Modrinth: його залежності за даними CurseForge). Спершу сам мод шукається на
 * Modrinth за sha1 файлу: його версія знає точні проєкти залежностей (так ставиться саме те, що потрібно, під цю гру і
 * завантажувач). Мод не з Modrinth: залежність шукається за її id (на Modrinth slug зазвичай той самий) з кількома
 * відомими розбіжностями. Нічого не видаляє і не міняє: лише додає файли. Не з потоку інтерфейсу.
 */
public final class DepFetch {

    private static final Logger log = Logger.getLogger(DepFetch.class.getName());
    private static final String API = "https://api.modrinth.com/v2/";

    /** id мода -> slug на Modrinth, де вони різні. */
    private static final Map<String, String> ALIASES = new HashMap<String, String>();

    static {
        ALIASES.put("fabric", "fabric-api");
        ALIASES.put("fabric-api-base", "fabric-api");
        ALIASES.put("cloth-config2", "cloth-config");
        ALIASES.put("cloth_config", "cloth-config");
        ALIASES.put("architectury", "architectury-api");
        ALIASES.put("geckolib3", "geckolib");
        ALIASES.put("forgeconfigapiport", "forge-config-api-port");
        ALIASES.put("owo", "owo-lib");
        ALIASES.put("yet_another_config_lib_v3", "yacl");
        ALIASES.put("yet-another-config-lib", "yacl");
        ALIASES.put("kotlinforforge", "kotlin-for-forge");
        ALIASES.put("fabric_language_kotlin", "fabric-language-kotlin");
        ALIASES.put("puzzleslib", "puzzles-lib");
        ALIASES.put("modmenu", "modmenu");
    }

    private DepFetch() {
    }

    /**
     * @param modFile мод, якому бракує залежностей (може бути null)
     * @param depIds  id залежностей з повідомлення гри чи перевірки (можуть бути порожні)
     * @param missing сюди назви того, що не знайшлося чи не скачалось (може бути null)
     * @return назви поставлених файлів
     */
    public static List<String> fetch(Instance instance, File modFile, List<String> depIds, List<String> missing)
            throws InterruptedException {
        String game = ModrinthBrowse.gameVersionOf(instance);
        String[] loaders = ModrinthBrowse.loadersOf(ModrinthBrowse.Kind.MOD, instance);
        File dir = InstanceMods.modsDir(instance);
        List<String> installed = new ArrayList<String>();
        List<String> miss = new ArrayList<String>();
        Set<String> seen = new HashSet<String>();
        if (game == null || loaders == null || loaders.length == 0) {
            if (missing != null) missing.addAll(depIds);
            return installed;
        }
        boolean viaMod = false;
        if (modFile != null && modFile.isFile()) {
            try {
                JsonNode v = ModrinthBrowse.getJson(API + "version_file/" + ModrinthBrowse.sha(modFile, "SHA-1") + "?algorithm=sha1");
                String own = v.path("project_id").asText("");
                if (!own.isEmpty()) seen.add(own);
                ModrinthBrowse.dependencies(v, game, loaders, dir, installed, miss, null, seen, 0);
                viaMod = true;
            } catch (IOException e) {
                log.info("Mod is not on Modrinth, looking up dependencies on CurseForge and by id: " + modFile.getName());
                // 25.11.2026: мод не з Modrinth: його залежності за даними CurseForge (мод знайдено за fingerprint)
                int before = installed.size();
                com.gummer.launcher.curseforge.CurseBrowse.dependenciesOf(modFile, game, loaders, dir, installed, miss);
                viaMod = installed.size() > before;
            }
        }
        // за id: якщо мода нема на Modrinth або його версія не назвала саме цієї залежності
        for (String id : depIds) {
            if (id == null || id.trim().isEmpty()) continue;
            String slug = slugOf(id);
            if (slug == null) continue;
            if (viaMod && !installed.isEmpty()) break;
            ModrinthBrowse.Hit h = new ModrinthBrowse.Hit();
            h.slug = slug;
            h.title = id;
            try {
                JsonNode proj = ModrinthBrowse.getJson(API + "project/" + slug);
                if (!"mod".equals(proj.path("project_type").asText("mod"))) {
                    miss.add(id);
                    continue;
                }
                h.id = proj.path("id").asText(slug);
                h.slug = proj.path("slug").asText(slug);
                h.title = proj.path("title").asText(id);
                if (seen.contains(h.id) || ModrinthBrowse.looksInstalled(dir, h.id, h.slug)) continue;
                ModrinthBrowse.install(ModrinthBrowse.Kind.MOD, h, game, loaders, dir, installed, miss, null);
                seen.add(h.id);
            } catch (IOException e) {
                // 25.11.2026 (власник: «чого немає на модрінті, потрібно спробувати знайти на курс форджі»): немає на
                // Modrinth чи немає там під цю гру: той самий мод на CurseForge, лише точний збіг slug чи назви
                String got = com.gummer.launcher.curseforge.CurseBrowse.installSameAs(h, game, loaders, dir, installed, miss, null, seen, 0);
                if (got == null) miss.add(id);
                else log.info("Dependency " + id + " fetched from CurseForge: " + got);
            }
        }
        if (missing != null) missing.addAll(miss);
        log.info("Dependencies fetched for " + instance.getName() + ": " + installed + ", missing " + miss);
        return installed;
    }

    /** slug на Modrinth для id мода, або null (вбудоване в гру чи завантажувач). */
    static String slugOf(String id) {
        String s = id.trim().toLowerCase(Locale.ROOT);
        int sp = s.indexOf(' ');
        if (sp > 0) s = s.substring(0, sp); // «sodium 0.5.0+» з повідомлення про версію
        if (s.equals("minecraft") || s.equals("java") || s.equals("fabricloader") || s.equals("forge") || s.equals("neoforge")
                || s.equals("quilt_loader") || s.equals("fml")) return null;
        if (s.startsWith("fabric-") && s.matches(".*-v[0-9]+$")) return "fabric-api"; // модулі Fabric API
        if (ALIASES.containsKey(s)) return ALIASES.get(s);
        s = s.replace('_', '-');
        return s.matches("[a-z0-9-]+") ? s : null;
    }
}
