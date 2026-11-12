/*
 * Copyright (C) 2021-2026 GummerCraft <http://gummercraft.fun>
 */

package com.gummer.launcher.vanilla;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gummer.launcher.util.Json;
import com.skcraft.launcher.Instance;
import com.skcraft.launcher.Launcher;
import com.skcraft.launcher.model.minecraft.VersionManifest;
import com.skcraft.launcher.model.modpack.Manifest;
import com.skcraft.launcher.util.HttpRequest;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Вкладка Vanilla (з 1.32): чисті версії Minecraft прямо з маніфесту Mojang, без модів і без нашого CDN.
 * Кожна версія це звичайний пак лаунчера з назвою {@code vanilla-<версія>} (тека {@code instances/vanilla-1.21.4}),
 * бібліотеки, ресурси і клієнт ті самі, що у модпаків (спільні {@code libraries/}, {@code assets/}, {@code versions/}).
 * Замість маніфесту модпака з CDN маніфест збирається на місці з JSON версії від Mojang
 * ({@link #buildManifest}), далі встановлення і запуск ідуть звичайним шляхом.
 */
public final class VanillaVersions {

    public static final String PREFIX = "vanilla-";
    public static final String GROUP = "vanilla";
    /** Найстаріший реліз у списку: 1.8 (вересень 2014). Старіші версії мають інший формат ресурсів і майже не потрібні. */
    private static final String OLDEST_RELEASE_TIME = "2014-09-02";

    private static final Logger log = Logger.getLogger(VanillaVersions.class.getName());
    private static final ObjectMapper MAPPER = Json.newMapper();

    /** Реліз зі списку Mojang. */
    public static final class Release {
        public final String id;
        public final String url;

        Release(String id, String url) {
            this.id = id;
            this.url = url;
        }
    }

    private VanillaVersions() {
    }

    public static boolean isVanilla(Instance instance) {
        return instance != null && isVanillaName(instance.getName());
    }

    public static boolean isVanillaName(String name) {
        return name != null && name.toLowerCase(Locale.ROOT).startsWith(PREFIX);
    }

    public static String nameOf(String versionId) {
        return PREFIX + versionId;
    }

    public static String titleOf(String versionId) {
        return "Minecraft " + versionId;
    }

    /** Версія з назви паку: vanilla-1.21.4 -> 1.21.4. */
    public static String versionIdOf(Instance instance) {
        return instance.getName().substring(PREFIX.length());
    }

    /**
     * Релізи від новішого до старішого (без снапшотів і бет). Список кешується в {@code <тека>/vanilla_versions.json}:
     * без інтернету показується останній збережений.
     */
    public static List<Release> releases(Launcher launcher) {
        File cache = new File(launcher.getBaseDir(), "vanilla_versions.json");
        JsonNode root = null;
        try {
            URL url = HttpRequest.url(launcher.getProperties().getProperty("versionManifestUrl"));
            byte[] body = HttpRequest.get(url).execute().expectResponseCode(200).returnContent().asBytes();
            root = MAPPER.readTree(body);
            if (root.path("versions").isArray()) {
                try {
                    java.nio.file.Files.write(cache.toPath(), body);
                } catch (IOException e) {
                    log.log(Level.FINE, "Could not cache the vanilla version list", e);
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.log(Level.WARNING, "Could not download the vanilla version list, trying the cached copy", e);
        }
        if ((root == null || !root.path("versions").isArray()) && cache.isFile()) {
            try {
                root = MAPPER.readTree(cache);
            } catch (IOException e) {
                log.log(Level.WARNING, "The cached vanilla version list is unreadable", e);
            }
        }
        List<Release> out = new ArrayList<Release>();
        if (root == null) return out;
        for (JsonNode v : root.path("versions")) {
            String id = v.path("id").asText(null);
            String url = v.path("url").asText(null);
            String type = v.path("type").asText("");
            String time = v.path("releaseTime").asText("");
            if (id == null || url == null || !"release".equals(type)) continue;
            if (time.compareTo(OLDEST_RELEASE_TIME) < 0) continue;
            if (!id.matches("[0-9A-Za-z._-]+")) continue; // стає назвою теки
            out.add(new Release(id, url));
        }
        return out;
    }

    /**
     * Маніфест «паку» для ванільної версії: JSON версії від Mojang, Java з його поля javaVersion
     * (8, 16, 17, 21, 25: ті самі ключі, що в нашому маніфесті Java), без модів, файлів і завантажувачів.
     * Копія пишеться в manifest.json паку, як для звичайного модпака.
     */
    public static Manifest buildManifest(Instance instance) throws IOException, InterruptedException {
        URL url = instance.getManifestURL();
        if (url == null) {
            throw new IOException("No Mojang version URL for " + instance.getName());
        }
        byte[] body = HttpRequest.get(url).execute().expectResponseCode(200).returnContent().asBytes();
        VersionManifest version = MAPPER.readValue(body, VersionManifest.class);
        String id = versionIdOf(instance);

        int java = 8;
        JsonNode jv = MAPPER.readTree(body).path("javaVersion").path("majorVersion");
        if (jv.isInt()) java = jv.asInt();

        Manifest manifest = new Manifest();
        manifest.setName(instance.getName());
        manifest.setTitle(titleOf(id));
        manifest.setVersion(id);
        manifest.setGameVersion(id);
        manifest.setJavaRuntime(String.valueOf(java));
        manifest.setMinimumVersion(0);
        manifest.setBaseUrl(url);
        // Без цього бібліотеки спершу шукались поруч з JSON версії (piston-meta, 404 на кожну), і лише потім
        // на librariesSource. Абсолютна адреса: Manifest.getLibrariesUrl бере її як є.
        manifest.setLibrariesLocation("https://libraries.minecraft.net");
        manifest.setVersionManifest(version);

        instance.setJavaRuntime(manifest.getJavaRuntime());
        instance.setTitle(manifest.getTitle());
        try {
            MAPPER.writerWithDefaultPrettyPrinter().writeValue(instance.getManifestPath(), manifest);
        } catch (Exception e) {
            log.log(Level.FINE, "Could not write manifest.json for " + instance.getName(), e);
        }
        return manifest;
    }
}
