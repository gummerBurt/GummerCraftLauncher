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
    /** Найстаріший реліз для завантажувачів: 1.7.10 (у Mojang дата 14.05.2014; старий Forge). */
    private static final String OLDEST_LOADER_TIME = "2014-05-01";

    private static final Logger log = Logger.getLogger(VanillaVersions.class.getName());
    private static final ObjectMapper MAPPER = Json.newMapper();

    /** Реліз зі списку Mojang. */
    public static final class Release {
        public final String id;
        public final String url;
        /**
         * «Музей» (з 16.11.2026): pre-classic, Classic, Indev, Infdev, Alpha, Beta і релізи старіші за 1.7.10.
         * Лише у Vanilla, без завантажувачів. Версії до 1.6 запускаються без звуків: їм треба ассети в
         * {@code <гра>/resources} ({@code map_to_resources}), цього лаунчер свідомо не робить.
         */
        public final boolean museum;
        /**
         * 17.11.2026: снапшот, пре-реліз чи кандидат у реліз (у Mojang тип {@code snapshot}). Лише у Vanilla, без
         * завантажувачів і без фільтра версій Modrinth.
         */
        public final boolean snapshot;
        /**
         * Чи показувати у списку Vanilla. Снапшоти лише новіші за останній реліз (зараз 26.4-snapshot-N після 26.3);
         * старіші снапшоти тут лише для адреси JSON, якщо така версія вже встановлена чи з неї зроблено копію.
         */
        public final boolean listed;

        Release(String id, String url, boolean museum, boolean snapshot, boolean listed) {
            this.id = id;
            this.url = url;
            this.museum = museum;
            this.snapshot = snapshot;
            this.listed = listed;
        }
    }

    /** Порядок «музею» з маніфесту Mojang (назва паку -> 0 для найновішої), для сортування списку. */
    private static volatile java.util.Map<String, Integer> museumOrder = java.util.Collections.emptyMap();

    /**
     * 17.11.2026 (власник: «додай також ванільні снапшоти ... завжди між версіями - після останньої стабільної
     * версії»): снапшоти, новіші за останній реліз (назва паку -> 0 для найновішого), і сам цей реліз. У списку
     * вони стоять одразу під ним, від новішого до старішого.
     */
    private static volatile java.util.Map<String, Integer> snapshotOrder = java.util.Collections.emptyMap();
    private static volatile String latestRelease;

    private VanillaVersions() {
    }

    /**
     * Місце версії в «музеї» (0 найновіша) або -1, якщо це не музейна версія чи список ще не завантажений.
     * Номери з назв тут не годяться: Beta 1.7.3 стала б поруч з релізом 1.7.3, а rd-20090515 вище rd-161348.
     */
    public static int museumIndex(Instance instance) {
        if (!isVanilla(instance)) return -1;
        Integer i = museumOrder.get(instance.getKindName().toLowerCase(Locale.ROOT));
        return i != null ? i : -1;
    }

    /** Місце снапшота серед новіших за останній реліз (0 найновіший) або -1. */
    public static int snapshotIndex(Instance instance) {
        if (!isVanilla(instance)) return -1;
        Integer i = snapshotOrder.get(instance.getKindName().toLowerCase(Locale.ROOT));
        return i != null ? i : -1;
    }

    /** Для снапшота, новішого за останній реліз: сам цей реліз («26.3»), за ним снапшот стоїть у списку; інакше null. */
    public static String snapshotBase(Instance instance) {
        return snapshotIndex(instance) >= 0 ? latestRelease : null;
    }

    public static boolean isVanilla(Instance instance) {
        return instance != null && isVanillaName(instance.getKindName());
    }

    public static boolean isVanillaName(String name) {
        return name != null && name.toLowerCase(Locale.ROOT).startsWith(PREFIX);
    }

    public static String nameOf(String versionId) {
        return PREFIX + versionId;
    }

    public static String titleOf(String versionId) {
        return "Minecraft " + museumName(versionId);
    }

    /** Людська назва старих версій: b1.7.3 -> Beta 1.7.3, c0.30_01c -> Classic 0.30_01c, rd-132211 -> Pre-classic rd-132211. */
    public static String museumName(String id) {
        // 17.11.2026: снапшоти нової нумерації: 26.4-snapshot-10 -> 26.4 Snapshot 10, 26.4-pre-1, 26.4-rc-1
        java.util.regex.Matcher s = SNAPSHOT_ID.matcher(id);
        if (s.matches()) {
            String kind = s.group(2).equals("snapshot") ? "Snapshot" : s.group(2).equals("pre") ? "Pre-Release" : "Release Candidate";
            return s.group(1) + " " + kind + " " + s.group(3);
        }
        if (id.startsWith("rd-")) return "Pre-classic " + id;
        if (id.startsWith("c0.")) return "Classic " + id.substring(1);
        if (id.startsWith("in-")) return "Indev " + id.substring(3);
        if (id.startsWith("inf-")) return "Infdev " + id.substring(4);
        if (id.matches("a1\\..*")) return "Alpha " + id.substring(1);
        if (id.matches("b1\\..*")) return "Beta " + id.substring(1);
        return id;
    }

    private static final java.util.regex.Pattern SNAPSHOT_ID = java.util.regex.Pattern.compile("(\\d+(?:\\.\\d+)+)-(snapshot|pre|rc)-(\\d+)");

    /** Версія з назви паку: vanilla-1.21.4 -> 1.21.4. */
    public static String versionIdOf(Instance instance) {
        return instance.getKindName().substring(PREFIX.length());
    }

    /**
     * Релізи від новішого до старішого; снапшоти ({@link Release#snapshot}, у списку лише новіші за останній реліз,
     * {@link Release#listed}); наприкінці «музей» ({@link Release#museum}). Список кешується в {@code <тека>/vanilla_versions.json}:
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
        java.util.Map<String, Integer> order = new java.util.HashMap<String, Integer>();
        java.util.Map<String, Integer> snaps = new java.util.HashMap<String, Integer>();
        String latest = null;
        // Mojang перелічує від новішої: усе, що до першого релізу, новіше за нього (снапшоти наступної версії)
        for (JsonNode v : root.path("versions")) {
            String id = v.path("id").asText(null);
            String url = v.path("url").asText(null);
            String type = v.path("type").asText("");
            String time = v.path("releaseTime").asText("");
            boolean release = "release".equals(type);
            boolean snapshot = "snapshot".equals(type);
            if (id == null || url == null || !(release || snapshot || "old_beta".equals(type) || "old_alpha".equals(type))) continue;
            if (!id.matches("[0-9A-Za-z._-]+")) continue; // стає назвою теки
            if (snapshot) {
                boolean current = latest == null;
                if (current) snaps.put(nameOf(id).toLowerCase(Locale.ROOT), snaps.size());
                out.add(new Release(id, url, false, true, current));
                continue;
            }
            if (release && latest == null) latest = id;
            boolean museum = !release || time.compareTo(OLDEST_LOADER_TIME) < 0;
            if (museum) order.put(nameOf(id).toLowerCase(Locale.ROOT), order.size());
            out.add(new Release(id, url, museum, false, true));
        }
        museumOrder = order;
        latestRelease = latest;
        snapshotOrder = latest != null ? snaps : java.util.Collections.<String, Integer>emptyMap();
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
