/*
 * Copyright (C) 2021-2026 GummerCraft <http://gummercraft.fun>
 */

package com.gummer.launcher.quilt;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gummer.launcher.util.Json;
import com.gummer.launcher.vanilla.VanillaVersions;
import com.skcraft.launcher.Instance;
import com.skcraft.launcher.Launcher;
import com.skcraft.launcher.model.minecraft.GameArgument;
import com.skcraft.launcher.model.minecraft.Library;
import com.skcraft.launcher.model.minecraft.MinecraftArguments;
import com.skcraft.launcher.model.minecraft.VersionManifest;
import com.skcraft.launcher.model.modpack.Manifest;
import com.skcraft.launcher.util.HttpRequest;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Розділ Quilt (з 1.33): чистий Minecraft з Quilt Loader (форк Fabric, більшість модів Fabric теж працюють), без модів.
 * Повна копія логіки {@link com.gummer.launcher.fabric.FabricVersions}: формат профілю той самий, інші лише адреси
 * ({@code meta.quiltmc.org/v3}, {@code maven.quiltmc.org}) і файл версії {@code quilt-loader.txt}. Бібліотеки Quilt
 * частково з maven Fabric (intermediary), тому {@code Updater} додає обидва maven.
 *
 * <p>Версія Quilt Loader: найновіша без beta і pre (поля stable у meta Quilt немає).</p>
 */
public final class QuiltVersions {

    public static final String PREFIX = "quilt-";
    public static final String GROUP = "quilt";
    public static final String MAVEN = "https://maven.quiltmc.org/repository/release/";
    private static final String META = "https://meta.quiltmc.org/v3";
    private static final String LOADER_FILE = "quilt-loader.txt";

    private static final Logger log = Logger.getLogger(QuiltVersions.class.getName());
    private static final ObjectMapper MAPPER = Json.newMapper();

    private QuiltVersions() {
    }

    public static boolean isQuilt(Instance instance) {
        return instance != null && isQuiltName(instance.getName());
    }

    public static boolean isQuiltName(String name) {
        return name != null && name.toLowerCase(Locale.ROOT).startsWith(PREFIX);
    }

    public static String nameOf(String versionId) {
        return PREFIX + versionId;
    }

    public static String titleOf(String versionId) {
        return "Quilt " + versionId;
    }

    /** Версія Minecraft з назви паку: quilt-1.21.4 -> 1.21.4. */
    public static String versionIdOf(Instance instance) {
        return instance.getName().substring(PREFIX.length());
    }

    /**
     * Стабільні версії Minecraft, які підтримує Quilt (від новішої), кеш {@code quilt_games.json}: без інтернету
     * останній збережений список.
     */
    public static List<String> games(Launcher launcher) {
        JsonNode root = cached(launcher, META + "/versions/game", "quilt_games.json");
        List<String> out = new ArrayList<String>();
        if (root == null) return out;
        for (JsonNode v : root) {
            String id = v.path("version").asText(null);
            if (id != null && v.path("stable").asBoolean(false) && id.matches("[0-9A-Za-z._-]+")) out.add(id);
        }
        return out;
    }

    /** Версії Quilt Loader без beta від новішої (кеш {@code quilt_loaders.json}). */
    public static List<String> loaders(Launcher launcher) {
        JsonNode root = cached(launcher, META + "/versions/loader", "quilt_loaders.json");
        List<String> out = new ArrayList<String>();
        if (root == null) return out;
        for (JsonNode v : root) {
            String id = v.path("version").asText(null);
            if (id == null || !id.matches("[0-9A-Za-z.+_-]+")) continue;
            String lower = id.toLowerCase(Locale.ROOT);
            if (lower.contains("beta") || lower.contains("pre") || lower.contains("rc")) continue;
            out.add(id);
        }
        return out;
    }

    /** Закріплена версія лоадера паку або null (ще не встановлено). */
    public static String pinnedLoader(Instance instance) {
        File f = new File(instance.getDir(), LOADER_FILE);
        if (!f.isFile()) return null;
        try {
            String v = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8).trim();
            return v.matches("[0-9A-Za-z.+_-]+") ? v : null;
        } catch (IOException e) {
            return null;
        }
    }

    /** Закріпити версію лоадера (застосовується при наступному оновленні паку). */
    public static void pinLoader(Instance instance, String loader) throws IOException {
        instance.getDir().mkdirs();
        Files.write(new File(instance.getDir(), LOADER_FILE).toPath(), loader.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Маніфест «паку» Quilt: JSON версії Mojang (адреса в {@code instance.getManifestURL()}, як у Vanilla) плюс
     * профіль Quilt для закріпленої (або найновішої стабільної) версії лоадера.
     */
    public static Manifest buildManifest(Launcher launcher, Instance instance) throws IOException, InterruptedException {
        return buildManifest(launcher, instance, versionIdOf(instance), pinnedLoader(instance));
    }

    /** Те саме з явними версіями (збірки .mrpack: версії з mrpack.json, не з назви паку). */
    public static Manifest buildManifest(Launcher launcher, Instance instance, String mc, String loader)
            throws IOException, InterruptedException {
        URL url = instance.getManifestURL();
        if (url == null) {
            throw new IOException("No Mojang version URL for " + instance.getName());
        }
        if (loader == null) {
            List<String> all = loaders(launcher);
            if (all.isEmpty()) throw new IOException("The Quilt Loader list is unavailable");
            loader = all.get(0);
        }

        byte[] body = HttpRequest.get(url).execute().expectResponseCode(200).returnContent().asBytes();
        VersionManifest version = MAPPER.readValue(body, VersionManifest.class);
        int java = 8;
        JsonNode jv = MAPPER.readTree(body).path("javaVersion").path("majorVersion");
        if (jv.isInt()) java = jv.asInt();

        URL profileUrl = HttpRequest.url(META + "/versions/loader/" + mc + "/" + loader + "/profile/json");
        byte[] pbody = HttpRequest.get(profileUrl).execute().expectResponseCode(200).returnContent().asBytes();
        VersionManifest profile = MAPPER.readValue(pbody, VersionManifest.class);

        // бібліотеки Quilt спершу (як у classpath офіційного установника), далі Mojang
        LinkedHashSet<Library> libs = new LinkedHashSet<Library>();
        if (profile.getLibraries() != null) libs.addAll(profile.getLibraries());
        if (version.getLibraries() != null) libs.addAll(version.getLibraries());
        version.setLibraries(libs);
        if (profile.getMainClass() != null) version.setMainClass(profile.getMainClass());
        MinecraftArguments pa = profile.getArguments();
        if (pa != null) {
            MinecraftArguments va = version.getArguments();
            if (va == null) {
                version.setArguments(pa);
            } else {
                for (GameArgument a : pa.getJvmArguments()) va.getJvmArguments().add(a);
                for (GameArgument a : pa.getGameArguments()) va.getGameArguments().add(a);
            }
        }

        Manifest manifest = new Manifest();
        manifest.setName(instance.getName());
        manifest.setTitle(titleOf(mc));
        manifest.setVersion(mc + "+quilt-" + loader);
        manifest.setGameVersion(mc);
        manifest.setJavaRuntime(String.valueOf(java));
        manifest.setMinimumVersion(0);
        manifest.setBaseUrl(url);
        // Mojang перша адреса; бібліотеки Quilt знайдуться на MAVEN і maven Fabric (Updater додає обидва)
        manifest.setLibrariesLocation("https://libraries.minecraft.net");
        manifest.setVersionManifest(version);

        pinLoader(instance, loader);
        instance.setJavaRuntime(manifest.getJavaRuntime());
        instance.setTitle(manifest.getTitle());
        try {
            MAPPER.writerWithDefaultPrettyPrinter().writeValue(instance.getManifestPath(), manifest);
        } catch (Exception e) {
            log.log(Level.FINE, "Could not write manifest.json for " + instance.getName(), e);
        }
        log.info(instance.getName() + ": Minecraft " + mc + ", Quilt Loader " + loader);
        return manifest;
    }

    /** Чи підтримує Quilt цю ванільну версію (для списку: лише ті, що є і в Mojang, і в Quilt). */
    public static boolean supports(List<String> games, VanillaVersions.Release r) {
        return games.contains(r.id);
    }

    private static JsonNode cached(Launcher launcher, String address, String cacheName) {
        File cache = new File(launcher.getBaseDir(), cacheName);
        JsonNode root = null;
        try {
            byte[] body = HttpRequest.get(HttpRequest.url(address)).execute().expectResponseCode(200).returnContent().asBytes();
            root = MAPPER.readTree(body);
            if (root.isArray()) {
                try {
                    Files.write(cache.toPath(), body);
                } catch (IOException e) {
                    log.log(Level.FINE, "Could not cache " + cacheName, e);
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.log(Level.WARNING, "Could not download " + address + ", trying the cached copy", e);
        }
        if ((root == null || !root.isArray()) && cache.isFile()) {
            try {
                root = MAPPER.readTree(cache);
            } catch (IOException e) {
                log.log(Level.WARNING, "The cached " + cacheName + " is unreadable", e);
            }
        }
        return root != null && root.isArray() ? root : null;
    }
}
