/*
 * Copyright (C) 2021-2026 GummerCraft <http://gummercraft.fun>
 */

package com.gummer.launcher.manage;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.skcraft.launcher.Instance;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.logging.Logger;

/**
 * 25.11.2026 (тікет #122): який завантажувач і якої версії стоїть у збірці, і як його підняти.
 * <p>
 * Звідки: збірки Modrinth (.mrpack) пишуть завантажувач у {@code mrpack.json} ({@code loader}, {@code loaderVersion};
 * Forge там як «1.20.1-47.2.0»). Розділи Fabric, Quilt, Forge, NeoForge і їхні копії закріплюють версію у файлі
 * теки збірки ({@code fabric-loader.txt}, {@code quilt-loader.txt}, {@code forge-loader.txt}, {@code neoforge-loader.txt}),
 * який пишеться при першому встановленні. Немає ні того, ні іншого: версія невідома, і перевірки її не чіпають.
 * <p>
 * Підняти: записати нову версію туди ж і позначити збірку до оновлення, як це робить меню «Версія завантажувача»;
 * при наступному запуску лаунчер поставить новий завантажувач сам. Моди, світи і налаштування не змінюються.
 */
public final class LoaderVersion {

    private static final Logger log = Logger.getLogger(LoaderVersion.class.getName());
    private static final String SAFE = "[0-9A-Za-z.+_-]+";

    /** Завантажувач (fabric, quilt, forge, neoforge) і його версія без версії гри (47.2.0, не 1.20.1-47.2.0). */
    public final String loader;
    public final String version;
    /** Версія гри збірки (для Forge версія пишеться як «гра-версія»). */
    public final String game;
    final boolean mrpack;

    private LoaderVersion(String loader, String version, String game, boolean mrpack) {
        this.loader = loader;
        this.version = version;
        this.game = game;
        this.mrpack = mrpack;
    }

    /** Назва для гравця: «Fabric Loader», «Quilt Loader», «Forge», «NeoForge». */
    public static String title(String loader) {
        if ("fabric".equals(loader)) return "Fabric Loader";
        if ("quilt".equals(loader)) return "Quilt Loader";
        if ("neoforge".equals(loader)) return "NeoForge";
        if ("forge".equals(loader)) return "Forge";
        return loader;
    }

    public String title() {
        return title(loader);
    }

    /** Що стоїть у збірці, або null (не збірка з модами, модпак GummerCraft, версія невідома). */
    public static LoaderVersion of(Instance instance) {
        if (instance == null || instance.isVanilla() || !(instance.isBuiltIn() || instance.isCopy())) return null;
        File dir = instance.peekDir();
        if (dir == null) return null;
        String game = InstanceCopies.minecraftVersionOf(instance);
        if (instance.isMrpack()) {
            JsonNode i = com.gummer.launcher.modrinth.MrpackPacks.info(instance);
            if (i == null) return null;
            String l = i.path("loader").asText("vanilla");
            String v = i.path("loaderVersion").asText(null);
            String mc = i.path("mc").asText(game);
            if (v == null || !v.matches(SAFE) || !isLoader(l)) return null;
            return new LoaderVersion(l, plain(l, v, mc), mc, true);
        }
        String l = instance.isQuilt() ? "quilt" : instance.isFabric() ? "fabric" : null;
        if (l == null && instance.isForgeLike()) {
            l = com.gummer.launcher.forge.ForgeVersions.kindOf(instance) == com.gummer.launcher.forge.ForgeVersions.Kind.FORGE ? "forge" : "neoforge";
        }
        if (l == null) return null;
        String v = read(new File(dir, l + "-loader.txt"));
        return v == null ? null : new LoaderVersion(l, plain(l, v, game), game, false);
    }

    /**
     * Те саме за текою модів ({@code <збірка>/minecraft/mods}), без самої збірки: для {@link ModUpdates#apply}, куди
     * приходить лише файл мода. Модпаки GummerCraft і StalDayZ цих файлів не мають: null.
     */
    public static LoaderVersion ofModsDir(File modsDir) {
        File content = modsDir == null ? null : modsDir.getParentFile();
        File dir = content == null ? null : content.getParentFile();
        if (dir == null || !"mods".equals(modsDir.getName())) return null;
        File info = new File(dir, com.gummer.launcher.modrinth.MrpackPacks.INFO);
        if (info.isFile()) {
            try {
                JsonNode i = com.gummer.launcher.util.Json.newMapper().readTree(info);
                String l = i.path("loader").asText("vanilla");
                String v = i.path("loaderVersion").asText(null);
                String mc = i.path("mc").asText(null);
                if (v == null || !v.matches(SAFE) || !isLoader(l)) return null;
                return new LoaderVersion(l, plain(l, v, mc), mc, true);
            } catch (IOException | RuntimeException e) {
                return null;
            }
        }
        LoaderVersion found = null;
        for (String l : new String[]{"fabric", "quilt", "forge", "neoforge"}) {
            String v = read(new File(dir, l + "-loader.txt"));
            if (v == null) continue;
            if (found != null) return null; // два файли: не знаємо, який діє
            found = new LoaderVersion(l, plain(l, v, null), null, false);
        }
        return found;
    }

    private static boolean isLoader(String l) {
        return "fabric".equals(l) || "quilt".equals(l) || "forge".equals(l) || "neoforge".equals(l);
    }

    /** «1.20.1-47.2.0» -> «47.2.0» (Forge пише версію гри попереду, моди вимагають без неї). */
    static String plain(String loader, String v, String game) {
        if (!"forge".equals(loader) || v == null) return v;
        if (game != null && v.startsWith(game + "-")) return v.substring(game.length() + 1);
        int dash = v.indexOf('-');
        return dash > 0 && v.substring(0, dash).matches("1\\.[0-9.]+") ? v.substring(dash + 1) : v;
    }

    private static String read(File f) {
        if (!f.isFile()) return null;
        try {
            String v = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8).trim();
            return v.matches(SAFE) ? v : null;
        } catch (IOException e) {
            return null;
        }
    }

    /** Чи досить цієї версії для вимоги {@code need} (null: вимоги немає). */
    public boolean satisfies(String need) {
        return need == null || ModVersionFix.compare(version, need) >= 0;
    }

    /** Чи досить завантажувача збірки для мода (лише вимога під завантажувач самої збірки). */
    public boolean fits(ModInfo info) {
        if (info == null) return true;
        return satisfies(info.loaderMin.get(loader));
    }

    /** Що вимагає мод від завантажувача збірки, або null. */
    public String needOf(ModInfo info) {
        return info == null ? null : info.loaderMin.get(loader);
    }

    /**
     * Найменша відома стабільна версія завантажувача, не нижча за {@code need}, для цієї гри (списки з кешу лаунчера).
     * Немає списку: сама {@code need}, як її назвав мод чи завантажувач (Fabric у тексті помилки називає точну версію).
     */
    public String pick(com.skcraft.launcher.Launcher launcher, String need) {
        List<String> all = null;
        try {
            if (launcher != null) {
                if ("fabric".equals(loader)) all = com.gummer.launcher.fabric.FabricVersions.loaders(launcher);
                else if ("quilt".equals(loader)) all = com.gummer.launcher.quilt.QuiltVersions.loaders(launcher);
                else all = com.gummer.launcher.forge.ForgeVersions.versions(launcher, "forge".equals(loader)
                            ? com.gummer.launcher.forge.ForgeVersions.Kind.FORGE : com.gummer.launcher.forge.ForgeVersions.Kind.NEOFORGE).get(game);
            }
        } catch (RuntimeException e) {
            all = null;
        }
        String best = null;
        if (all != null) {
            for (String v : all) {
                String p = plain(loader, v, game);
                if (ModVersionFix.compare(p, need) < 0 || com.gummer.launcher.forge.ForgeVersions.isBeta(p)) continue;
                if (best == null || ModVersionFix.compare(p, best) < 0) best = p;
            }
        }
        return best != null ? best : need;
    }

    /**
     * Поставити версію {@code version} (без версії гри) і позначити збірку до оновлення: новий завантажувач лаунчер
     * поставить при наступному запуску. Нижче поточної не опускає.
     */
    public static void raise(Instance instance, String version) throws IOException {
        LoaderVersion now = of(instance);
        if (now == null) throw new IOException("Unknown loader of " + instance.getName());
        if (version == null || !version.matches(SAFE)) throw new IOException("Bad loader version " + version);
        if (ModVersionFix.compare(now.version, version) >= 0) return;
        String stored = "forge".equals(now.loader) && now.game != null ? now.game + "-" + version : version;
        File dir = instance.getDir();
        if (now.mrpack) {
            File f = new File(dir, com.gummer.launcher.modrinth.MrpackPacks.INFO);
            JsonNode i = com.gummer.launcher.util.Json.newMapper().readTree(f);
            if (!(i instanceof ObjectNode)) throw new IOException("Bad " + f.getName());
            ((ObjectNode) i).put("loaderVersion", stored);
            com.gummer.launcher.util.Json.newMapper().writerWithDefaultPrettyPrinter().writeValue(f, i);
            com.gummer.launcher.modrinth.MrpackPacks.forgetInfo(dir);
        } else {
            Files.write(new File(dir, now.loader + "-loader.txt").toPath(), stored.getBytes(StandardCharsets.UTF_8));
        }
        if (instance.isLocal()) {
            instance.setUpdatePending(true);
            com.skcraft.launcher.persistence.Persistence.commitAndForget(instance);
        }
        log.info(instance.getName() + ": " + now.title() + " " + now.version + " -> " + version + " (needed by a mod)");
    }

    @Override
    public String toString() {
        return loader + " " + version;
    }
}
