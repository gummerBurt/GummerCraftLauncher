/*
 * Copyright (C) 2021-2026 GummerCraft <http://gummercraft.fun>
 */

package com.gummer.launcher.modrinth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.gummer.launcher.fabric.FabricVersions;
import com.gummer.launcher.forge.ForgeVersions;
import com.gummer.launcher.quilt.QuiltVersions;
import com.gummer.launcher.util.Json;
import com.gummer.launcher.vanilla.VanillaVersions;
import com.skcraft.concurrency.ProgressObservable;
import com.skcraft.launcher.Instance;
import com.skcraft.launcher.Launcher;
import com.skcraft.launcher.model.modpack.Manifest;
import com.skcraft.launcher.util.HttpRequest;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Збірки з Modrinth ({@code .mrpack}, з 1.33): гравець відкриває файл, лаунчер створює пак {@code mrpack-<назва>}
 * (тека {@code instances/mrpack-<назва>}), качає моди за списком {@code modrinth.index.json} (з перевіркою sha1 і
 * sha512, лише з адрес, які дозволяє формат Modrinth) і копіює {@code overrides/} та {@code client-overrides/}.
 *
 * <p>Гру і завантажувач пак ставить так само, як розділи Vanilla, Fabric, Quilt, Forge, NeoForge (той самий код,
 * версії з {@code mrpack.json} у теці паку). Моди качаються один раз при імпорті; оновлення збірки: імпортувати новий
 * файл.</p>
 */
public final class MrpackPacks implements ProgressObservable {

    /** Imports are hidden until 1.34 (decided 13.11.2026): needs more testing (Forge, NeoForge, Quilt packs). */
    public static final boolean ENABLED = false;
    public static final String PREFIX = "mrpack-";
    public static final String INFO = "mrpack.json";
    /** Адреси, з яких формат Modrinth дозволяє качати файли збірки (специфікація .mrpack). */
    private static final List<String> HOSTS = Arrays.asList(
            "cdn.modrinth.com", "github.com", "raw.githubusercontent.com", "gitlab.com");

    private static final Logger log = Logger.getLogger(MrpackPacks.class.getName());
    private static final ObjectMapper MAPPER = Json.newMapper();

    private volatile double progress = -1;
    private volatile String status = "";

    public static boolean isMrpackName(String name) {
        return name != null && name.toLowerCase(Locale.ROOT).startsWith(PREFIX);
    }

    /** Що записано в mrpack.json паку: назва, версія Minecraft, завантажувач (vanilla, fabric, quilt, forge, neoforge) і його версія. */
    public static JsonNode info(Instance instance) {
        File f = new File(instance.getDir(), INFO);
        if (!f.isFile()) return null;
        try {
            return MAPPER.readTree(f);
        } catch (IOException e) {
            return null;
        }
    }

    public static String mcOf(Instance instance) {
        JsonNode i = info(instance);
        return i != null ? i.path("mc").asText(null) : null;
    }

    public static String titleOf(Instance instance) {
        JsonNode i = info(instance);
        String t = i != null ? i.path("name").asText(null) : null;
        return t != null && !t.trim().isEmpty() ? t.trim() : instance.getName();
    }

    /** Завантажувач паку (fabric, quilt, forge, neoforge або vanilla). */
    public static String loaderOf(Instance instance) {
        JsonNode i = info(instance);
        return i != null ? i.path("loader").asText("vanilla") : "vanilla";
    }

    /** Маніфест: гра і завантажувач тим самим кодом, що й розділи лаунчера. */
    public static Manifest buildManifest(Launcher launcher, Instance instance) throws IOException, InterruptedException {
        JsonNode i = info(instance);
        if (i == null) throw new IOException(instance.getName() + ": no " + INFO);
        String mc = i.path("mc").asText();
        String loader = i.path("loader").asText("vanilla");
        String lv = i.path("loaderVersion").asText(null);
        Manifest m;
        switch (loader) {
            case "fabric":
                m = FabricVersions.buildManifest(launcher, instance, mc, lv);
                break;
            case "quilt":
                m = QuiltVersions.buildManifest(launcher, instance, mc, lv);
                break;
            case "forge":
                m = ForgeVersions.buildManifest(launcher, instance, ForgeVersions.Kind.FORGE, mc, lv);
                break;
            case "neoforge":
                m = ForgeVersions.buildManifest(launcher, instance, ForgeVersions.Kind.NEOFORGE, mc, lv);
                break;
            default:
                m = VanillaVersions.buildManifest(instance);
        }
        m.setName(instance.getName());
        m.setTitle(titleOf(instance));
        instance.setTitle(titleOf(instance));
        return m;
    }

    /**
     * Імпорт файлу: створює теку паку, качає моди, копіює overrides. Повертає назву нового паку. Нічого не лишає
     * напівготовим: при помилці тека паку видаляється.
     */
    public String importFile(Launcher launcher, File mrpack) throws Exception {
        status = "Reading " + mrpack.getName();
        try (ZipFile zip = new ZipFile(mrpack)) {
            ZipEntry ie = zip.getEntry("modrinth.index.json");
            if (ie == null) throw new IOException("Not a Modrinth modpack: no modrinth.index.json");
            JsonNode index;
            try (InputStream in = zip.getInputStream(ie)) {
                index = MAPPER.readTree(in);
            }
            if (!"minecraft".equals(index.path("game").asText())) throw new IOException("Not a Minecraft modpack");
            JsonNode deps = index.path("dependencies");
            String mc = deps.path("minecraft").asText(null);
            if (mc == null || !mc.matches("[0-9A-Za-z._-]+")) throw new IOException("The modpack has no Minecraft version");
            String loader = "vanilla", lv = null;
            String[][] known = {{"fabric-loader", "fabric"}, {"quilt-loader", "quilt"}, {"forge", "forge"}, {"neoforge", "neoforge"}};
            for (String[] k : known) {
                if (deps.has(k[0])) {
                    loader = k[1];
                    lv = deps.path(k[0]).asText();
                }
            }
            if (lv != null && !lv.matches("[0-9A-Za-z.+_-]+")) throw new IOException("Bad loader version " + lv);
            // Forge у .mrpack пишеться без версії Minecraft (47.3.0), у maven Forge повна (1.20.1-47.3.0)
            if ("forge".equals(loader) && lv != null && !lv.startsWith(mc + "-")) lv = mc + "-" + lv;

            String title = index.path("name").asText("Modrinth");
            String slug = title.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
            if (slug.isEmpty()) slug = "pack";
            if (slug.length() > 40) slug = slug.substring(0, 40);
            String name = PREFIX + slug;
            File dir = new File(launcher.getInstancesDir(), name);
            for (int n = 2; dir.exists(); n++) {
                name = PREFIX + slug + "-" + n;
                dir = new File(launcher.getInstancesDir(), name);
            }
            File content = new File(dir, "minecraft");
            content.mkdirs();
            try {
                JsonNode files = index.path("files");
                int total = files.size(), done = 0;
                for (JsonNode f : files) {
                    done++;
                    String path = f.path("path").asText("");
                    if ("unsupported".equals(f.path("env").path("client").asText(""))) continue;
                    File target = safe(content, path);
                    status = "(" + done + "/" + total + ") " + path;
                    progress = (double) done / Math.max(1, total);
                    byte[] data = null;
                    IOException last = null;
                    for (JsonNode u : f.path("downloads")) {
                        URL url = new URL(u.asText());
                        if (!"https".equals(url.getProtocol()) || !HOSTS.contains(url.getHost().toLowerCase(Locale.ROOT))) {
                            last = new IOException("Download host not allowed by the .mrpack format: " + url.getHost());
                            continue;
                        }
                        try {
                            data = HttpRequest.get(url).execute().expectResponseCode(200).returnContent().asBytes();
                            break;
                        } catch (IOException e) {
                            last = e;
                        }
                    }
                    if (data == null) throw last != null ? last : new IOException("No download for " + path);
                    check(path, data, "SHA-1", f.path("hashes").path("sha1").asText(null));
                    check(path, data, "SHA-512", f.path("hashes").path("sha512").asText(null));
                    target.getParentFile().mkdirs();
                    Files.write(target.toPath(), data);
                }
                status = "Copying overrides";
                progress = -1;
                extract(zip, "overrides/", content);
                extract(zip, "client-overrides/", content);

                ObjectNode info = MAPPER.createObjectNode();
                info.put("name", title);
                info.put("mc", mc);
                info.put("loader", loader);
                if (lv != null) info.put("loaderVersion", lv);
                info.put("versionId", index.path("versionId").asText(""));
                info.put("file", mrpack.getName());
                MAPPER.writerWithDefaultPrettyPrinter().writeValue(new File(dir, INFO), info);
            } catch (Exception e) {
                deleteTree(dir);
                throw e;
            }
            log.info("Imported " + mrpack.getName() + " as " + name + " (Minecraft " + mc + ", " + loader + " " + lv + ")");
            return name;
        }
    }

    private static void extract(ZipFile zip, String prefix, File content) throws IOException {
        Enumeration<? extends ZipEntry> en = zip.entries();
        while (en.hasMoreElements()) {
            ZipEntry e = en.nextElement();
            if (e.isDirectory() || !e.getName().startsWith(prefix)) continue;
            File target = safe(content, e.getName().substring(prefix.length()));
            target.getParentFile().mkdirs();
            try (InputStream in = zip.getInputStream(e)) {
                Files.copy(in, target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    /** Шлях лише всередині теки гри (без .., без абсолютних шляхів). */
    private static File safe(File base, String rel) throws IOException {
        if (rel.isEmpty() || rel.startsWith("/") || rel.startsWith("\\") || rel.contains(":")) {
            throw new IOException("Bad path in the modpack: " + rel);
        }
        File f = new File(base, rel).getCanonicalFile();
        if (!f.toPath().startsWith(base.getCanonicalFile().toPath())) throw new IOException("Bad path in the modpack: " + rel);
        return f;
    }

    private static void check(String path, byte[] data, String algo, String expected) throws IOException {
        if (expected == null || expected.isEmpty()) return;
        try {
            byte[] d = MessageDigest.getInstance(algo).digest(data);
            StringBuilder sb = new StringBuilder();
            for (byte b : d) sb.append(String.format("%02x", b));
            if (!sb.toString().equalsIgnoreCase(expected)) throw new IOException(path + ": " + algo + " does not match");
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IOException(e);
        }
    }

    private static void deleteTree(File f) {
        File[] kids = f.listFiles();
        if (kids != null) for (File k : kids) deleteTree(k);
        f.delete();
    }

    @Override
    public double getProgress() {
        return progress;
    }

    @Override
    public String getStatus() {
        return status;
    }
}
