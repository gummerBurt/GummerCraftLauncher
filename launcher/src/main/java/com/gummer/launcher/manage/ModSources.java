/*
 * Copyright (C) 2021-2026 GummerCraft <http://gummercraft.fun>
 */

package com.gummer.launcher.manage;

import com.fasterxml.jackson.databind.JsonNode;
import com.gummer.launcher.curseforge.CurseBrowse;
import com.skcraft.launcher.Launcher;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * 26.11.2026 (власник: «шукати і на Modrinth, і на CurseForge ... обидва джерела за потреби»): один мод (шейдерпак)
 * за slug Modrinth з запасним CurseForge. Спершу Modrinth: чи є версія під цю гру і завантажувач; якщо проєкту немає
 * або версії під збірку немає, той самий мод на CurseForge (через наш проксі {@code cf.php}, збіг slug чи назви,
 * {@link ModrinthBrowse#sameMod}), з перевіркою, що там є файл саме під цю гру і завантажувач. Далі звичайне
 * встановлення того джерела: залежності, sha1/sha512, журнал поставленого.
 * <p>
 * Для модів, яких на Modrinth немає зовсім, slug {@code cf:Назва мода} шукає лише на CurseForge за назвою.
 * Результат пошуку пам'ятається до кінця запуску (ключ slug, гра, завантажувачі).
 */
public final class ModSources {

    private static final Logger log = Logger.getLogger(ModSources.class.getName());
    private static final String API = "https://api.modrinth.com/v2/";
    /** Позначка «ніде немає» у пам'яті пошуку. */
    private static final Found NONE = new Found(null, false);
    private static final Map<String, Found> CACHE = new ConcurrentHashMap<String, Found>();

    private ModSources() {
    }

    /** Де знайдено. */
    public static final class Found {
        public final ModrinthBrowse.Hit hit;
        /** true: CurseForge ({@code hit.id} = {@code cf-<id>}), false: Modrinth. */
        public final boolean curse;

        Found(ModrinthBrowse.Hit hit, boolean curse) {
            this.hit = hit;
            this.curse = curse;
        }

        public String title() {
            return hit != null && hit.title != null ? hit.title : "?";
        }
    }

    /**
     * Знайти мод під цю гру і завантажувачі. {@code loaders} для шейдерів: завантажувачі шейдерів ({@code iris},
     * {@code optifine}). null: немає ні там, ні там (або нема мережі). Не з потоку інтерфейсу.
     */
    public static Found find(String slug, ModrinthBrowse.Kind kind, String game, String[] loaders) throws InterruptedException {
        String key = slug + "|" + kind + "|" + game + "|" + (loaders == null ? "" : String.join(",", loaders));
        Found c = CACHE.get(key);
        if (c != null) return c == NONE ? null : c;
        Found f = findNow(slug, kind, game, loaders);
        CACHE.put(key, f == null ? NONE : f);
        return f;
    }

    private static Found findNow(String slug, ModrinthBrowse.Kind kind, String game, String[] loaders) throws InterruptedException {
        ModrinthBrowse.Hit mr = null;
        String cfName = null;
        if (slug.startsWith("cf:")) {
            cfName = slug.substring(3).trim();
        } else {
            try {
                JsonNode p = ModrinthBrowse.getJson(API + "project/" + ModrinthBrowse.enc(slug));
                mr = new ModrinthBrowse.Hit();
                mr.id = p.path("id").asText(slug);
                mr.slug = p.path("slug").asText(slug);
                mr.title = p.path("title").asText(slug);
                mr.description = p.path("description").asText("");
                mr.iconUrl = p.hasNonNull("icon_url") ? p.path("icon_url").asText(null) : null;
                if (hasModrinthVersion(mr.id, kind, game, loaders)) return new Found(mr, false);
            } catch (IOException e) {
                log.info("Mod source: " + slug + " is not on Modrinth: " + e.getMessage());
            }
        }
        // запасне джерело: той самий мод на CurseForge
        Launcher l = CurseBrowse.shared();
        if (l == null) return null;
        ModrinthBrowse.Hit want = mr;
        if (want == null) {
            want = new ModrinthBrowse.Hit();
            want.title = cfName;
        }
        if (want.title == null || want.title.trim().isEmpty()) return null;
        try {
            String loader = kind == ModrinthBrowse.Kind.MOD && loaders != null && loaders.length > 0 ? loaders[loaders.length - 1] : null;
            String query = want.title.replaceAll("[^\\p{L}\\p{Nd}' ]+", " ").trim();
            ModrinthBrowse.Hit same = strictSame(want, CurseBrowse.search(l, kind, query, game, loader, null, CurseBrowse.Sort.BEST, 0).hits);
            if (same == null) {
                log.info("Mod source: " + slug + " for " + game + " " + Arrays.toString(loaders) + " is not on CurseForge either");
                return null;
            }
            String[] tries = kind == ModrinthBrowse.Kind.MOD ? loaders : null;
            if (CurseBrowse.fileChoices(l, kind, same, game, tries).isEmpty()) {
                log.info("Mod source: " + same.title + " on CurseForge has no file for " + game + " " + Arrays.toString(loaders));
                return null;
            }
            log.info("Mod source: " + slug + " for " + game + " " + Arrays.toString(loaders) + " from CurseForge (" + same.slug + ")");
            return new Found(same, true);
        } catch (IOException e) {
            log.info("Mod source: CurseForge unavailable for " + slug + ": " + e.getMessage());
            return null;
        }
    }

    /**
     * Той самий проєкт на CurseForge: той самий slug або та сама назва повністю (без регістру і знаків). Суворіше за
     * {@link ModrinthBrowse#sameMod}: «Presence Footsteps (Forge)» чи «... (Backport)» це порти інших авторів (проба
     * 26.11), для наборів їх не беремо.
     */
    static ModrinthBrowse.Hit strictSame(ModrinthBrowse.Hit a, List<ModrinthBrowse.Hit> found) {
        if (a == null || found == null) return null;
        if (a.slug != null) {
            for (ModrinthBrowse.Hit h : found) if (h.slug != null && a.slug.equalsIgnoreCase(h.slug)) return h;
        }
        String t = plain(a.title);
        if (t.length() < 3) return null;
        for (ModrinthBrowse.Hit h : found) if (t.equals(plain(h.title))) return h;
        return null;
    }

    private static String plain(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{Nd}]", "");
    }

    /** Чи має проєкт Modrinth версію під цю гру і завантажувачі (для ресурспаків без фільтра завантажувача). */
    static boolean hasModrinthVersion(String projectId, ModrinthBrowse.Kind kind, String game, String[] loaders)
            throws IOException, InterruptedException {
        StringBuilder url = new StringBuilder(API).append("project/").append(projectId).append("/version?include_changelog=false");
        if (game != null) url.append("&game_versions=").append(ModrinthBrowse.enc("[\"" + game + "\"]"));
        if (kind != ModrinthBrowse.Kind.RESOURCEPACK && loaders != null && loaders.length > 0) {
            StringBuilder a = new StringBuilder("[");
            for (int i = 0; i < loaders.length; i++) a.append(i > 0 ? "," : "").append('"').append(loaders[i]).append('"');
            url.append("&loaders=").append(ModrinthBrowse.enc(a.append(']').toString()));
        }
        return ModrinthBrowse.getJson(url.toString()).size() > 0;
    }

    /**
     * Поставити знайдене в теку {@code dir}. Повертає назву файлу. Модrinth: {@link ModrinthBrowse#install}, CurseForge:
     * {@link CurseBrowse#installFile} (файл, який автор дозволяє лише на сайті: вікно «Скачай з CurseForge»).
     */
    public static String install(Found f, ModrinthBrowse.Kind kind, String game, String[] loaders, File dir, List<String> installed,
                                 List<String> missing, List<String> conflicts) throws IOException, InterruptedException {
        if (f == null || f.hit == null) throw new IOException(ModrinthBrowse.NO_VERSION);
        if (f.curse) {
            Launcher l = CurseBrowse.shared();
            if (l == null) throw new IOException("CurseForge is not ready");
            return CurseBrowse.installFile(l, kind, f.hit, game, kind == ModrinthBrowse.Kind.MOD ? loaders : null, dir, installed, missing, conflicts);
        }
        return ModrinthBrowse.install(kind, f.hit, game, loaders, dir, installed, missing, conflicts);
    }

    /** Знайти і поставити: назва поставленого (чи вже наявного) мода або null, якщо ніде немає під цю збірку. */
    public static Found installSlug(String slug, ModrinthBrowse.Kind kind, String game, String[] loaders, File dir,
                                    List<String> installed, List<String> missing, List<String> conflicts)
            throws IOException, InterruptedException {
        Found f = find(slug, kind, game, loaders);
        if (f == null) return null;
        if (ModrinthBrowse.looksInstalled(dir, f.hit.id, f.hit.slug)) return f;
        install(f, kind, game, loaders, dir, installed, missing, conflicts);
        return f;
    }

    /** Для журналу і тестів: забути знайдене (наприклад, після зміни версії гри збірки). */
    public static void forget() {
        CACHE.clear();
    }

    static String lower(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT);
    }
}
