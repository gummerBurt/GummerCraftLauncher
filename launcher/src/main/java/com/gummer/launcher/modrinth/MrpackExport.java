/*
 * Copyright (C) 2021-2026 GummerCraft <http://gummercraft.fun>
 */

package com.gummer.launcher.modrinth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.gummer.launcher.manage.InstanceCopies;
import com.gummer.launcher.manage.InstanceMods;
import com.gummer.launcher.util.Json;
import com.skcraft.concurrency.ProgressObservable;
import com.skcraft.launcher.Instance;
import com.skcraft.launcher.util.HttpRequest;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * 17.11.2026: експорт збірки у файл {@code .mrpack} (формат Modrinth), щоб друг поставив такий самий набір: у нашому
 * лаунчері («Імпортувати збірку»), у Prism чи Modrinth App. Моди, які Modrinth знає (за sha1, один запит на всі),
 * ідуть посиланнями на cdn.modrinth.com, тож файл маленький. Решта модів (CurseForge, свої) кладеться у файл
 * ({@code overrides/mods}). Також конфіги ({@code config}, {@code defaultconfigs}, {@code kubejs}) і за вибором
 * ресурспаки та шейдери. Світів, налаштувань гри ({@code options.txt}), серверів, логів і скріншотів немає.
 *
 * <p>17.11.2026 (власник: «нехай гравці зможуть експортувати її і в зіпці»): {@code selfContained} пише .zip того самого
 * формату, але всі моди лежать у самому файлі ({@code overrides/mods}), без посилань на Modrinth. Його відкриває наш
 * лаунчер («Відкрити файл збірки») і Prism.</p>
 *
 * <p>Копія модпаку GummerCraft ({@value #FROM_PACK} у теці збірки): моди поза Modrinth не експортуються (це файли
 * наших модпаків), вони йдуть у {@link #skipped}.</p>
 */
public final class MrpackExport implements Callable<File>, ProgressObservable {

    /** Позначка в теці копії модпаку GummerCraft (пише {@code InstanceCopies.Copier}). */
    public static final String FROM_PACK = "gummer-pack.txt";
    private static final Logger log = Logger.getLogger(MrpackExport.class.getName());
    private static final ObjectMapper MAPPER = Json.newMapper();
    private static final String[] CONFIG_DIRS = {"config", "defaultconfigs", "kubejs"};

    private final Instance instance;
    private final File target;
    private final boolean packs;
    private final boolean selfContained;
    /** Що не потрапило у файл (моди модпака GummerCraft поза Modrinth). */
    public final List<String> skipped = new ArrayList<String>();
    private volatile String status = "";
    private volatile double progress = -1;

    public MrpackExport(Instance instance, File target, boolean packs) {
        this(instance, target, packs, false);
    }

    /** selfContained: усі моди у файлі (.zip), без посилань на Modrinth. */
    public MrpackExport(Instance instance, File target, boolean packs, boolean selfContained) {
        this.instance = instance;
        this.target = target;
        this.packs = packs;
        this.selfContained = selfContained;
    }

    /** Чи можна експортувати: встановлені Fabric, Quilt, Forge, NeoForge, їхні копії і Modrinth-збірки (не модпаки з CDN). */
    public static boolean canExport(Instance instance) {
        if (instance == null || instance.isVanilla() || !instance.isInstalled()) return false;
        if (!(instance.isBuiltIn() || instance.isCopy())) return false;
        return loader(instance) != null;
    }

    /** {завантажувач у форматі .mrpack, версія}, або null. */
    static String[] loader(Instance instance) {
        if (instance.isMrpack()) {
            JsonNode i = MrpackPacks.info(instance);
            if (i == null) return null;
            String l = i.path("loader").asText("vanilla"), v = i.path("loaderVersion").asText(null);
            if (v == null) return null;
            if ("forge".equals(l) && v.contains("-")) v = v.substring(v.indexOf('-') + 1);
            String key = "fabric".equals(l) ? "fabric-loader" : "quilt".equals(l) ? "quilt-loader" : l;
            return new String[]{key, v};
        }
        if (instance.isQuilt()) {
            String v = com.gummer.launcher.quilt.QuiltVersions.pinnedLoader(instance);
            return v != null ? new String[]{"quilt-loader", v} : null;
        }
        if (instance.isFabric()) {
            String v = com.gummer.launcher.fabric.FabricVersions.pinnedLoader(instance);
            return v != null ? new String[]{"fabric-loader", v} : null;
        }
        if (instance.isForgeLike()) {
            String v = com.gummer.launcher.forge.ForgeVersions.pinnedLoader(instance);
            if (v == null) return null;
            boolean forge = com.gummer.launcher.forge.ForgeVersions.kindOf(instance) == com.gummer.launcher.forge.ForgeVersions.Kind.FORGE;
            // у maven Forge версія повна (1.20.1-47.3.0), у .mrpack без версії гри
            if (forge && v.contains("-")) v = v.substring(v.indexOf('-') + 1);
            return new String[]{forge ? "forge" : "neoforge", v};
        }
        return null;
    }

    @Override
    public File call() throws Exception {
        String mc = InstanceCopies.minecraftVersionOf(instance);
        String[] loader = loader(instance);
        if (mc == null || loader == null) throw new IOException("Cannot export " + instance.getName() + ": unknown game or loader version");
        boolean fromPack = new File(instance.getDir(), FROM_PACK).isFile();
        File content = instance.getContentDir();

        // 1. увімкнені моди і їхні sha1
        status = instance.getTitle();
        List<File> mods = new ArrayList<File>();
        for (InstanceMods.Mod m : InstanceMods.list(instance)) if (m.enabled) mods.add(m.file);
        Map<String, File> byHash = new LinkedHashMap<String, File>();
        for (File f : mods) byHash.put(hash(f, "SHA-1"), f);

        // 2. що з них знає Modrinth (один запит)
        Map<File, JsonNode> known = new HashMap<File, JsonNode>();
        if (!byHash.isEmpty() && !selfContained) {
            try {
                Map<String, Object> body = new HashMap<String, Object>();
                body.put("hashes", new ArrayList<String>(byHash.keySet()));
                body.put("algorithm", "sha1");
                JsonNode versions = MAPPER.readTree(HttpRequest.post(new URL("https://api.modrinth.com/v2/version_files"))
                        .bodyJson(body).execute().expectResponseCode(200).returnContent().asString("UTF-8"));
                for (Map.Entry<String, File> e : byHash.entrySet()) {
                    for (JsonNode f : versions.path(e.getKey()).path("files")) {
                        if (e.getKey().equalsIgnoreCase(f.path("hashes").path("sha1").asText(""))
                                && f.path("url").asText("").startsWith("https://cdn.modrinth.com/")) {
                            known.put(e.getValue(), f);
                        }
                    }
                }
            } catch (IOException e) {
                // без інтернету: усі моди у файл (крім копій модпаків)
                log.info("Modrinth is unavailable for export, mods go into the file: " + e);
            }
        }

        // 3. modrinth.index.json
        ObjectNode index = MAPPER.createObjectNode();
        index.put("formatVersion", 1);
        index.put("game", "minecraft");
        index.put("versionId", "1");
        index.put("name", instance.getTitle());
        index.put("summary", "GummerCraft Launcher");
        ArrayNode files = index.putArray("files");
        List<File[]> overrides = new ArrayList<File[]>(); // {файл, шлях у zip}
        for (Map.Entry<String, File> e : byHash.entrySet()) {
            File f = e.getValue();
            JsonNode k = known.get(f);
            if (k != null) {
                ObjectNode o = files.addObject();
                o.put("path", "mods/" + f.getName());
                ObjectNode h = o.putObject("hashes");
                h.put("sha1", e.getKey());
                h.put("sha512", k.path("hashes").path("sha512").asText(hash(f, "SHA-512")));
                o.putArray("downloads").add(k.path("url").asText());
                o.put("fileSize", f.length());
            } else if (fromPack) {
                skipped.add(f.getName());
            } else {
                overrides.add(new File[]{f, new File("overrides/mods/" + f.getName())});
            }
        }
        ObjectNode deps = index.putObject("dependencies");
        deps.put("minecraft", mc);
        deps.put(loader[0], loader[1]);

        // 4. конфіги і за вибором ресурспаки, шейдери
        List<String> dirs = new ArrayList<String>(java.util.Arrays.asList(CONFIG_DIRS));
        if (packs) {
            dirs.add("resourcepacks");
            dirs.add("shaderpacks");
        }
        for (String d : dirs) collect(new File(content, d), "overrides/" + d, overrides);

        // 5. запис у тимчасовий файл поруч, потім заміна (недописаний файл не лишиться)
        File part = new File(target.getPath() + ".part");
        try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(part))) {
            zip.putNextEntry(new ZipEntry("modrinth.index.json"));
            zip.write(MAPPER.writerWithDefaultPrettyPrinter().writeValueAsBytes(index));
            zip.closeEntry();
            byte[] buf = new byte[64 * 1024];
            int n = 0;
            for (File[] o : overrides) {
                if (Thread.interrupted()) throw new InterruptedException();
                status = o[0].getName();
                progress = (double) ++n / Math.max(1, overrides.size());
                zip.putNextEntry(new ZipEntry(o[1].getPath().replace('\\', '/')));
                try (InputStream in = new FileInputStream(o[0])) {
                    int r;
                    while ((r = in.read(buf)) > 0) zip.write(buf, 0, r);
                }
                zip.closeEntry();
            }
        } catch (Exception e) {
            part.delete();
            throw e;
        }
        Files.move(part.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
        log.info("Exported " + instance.getName() + " to " + target + ": " + known.size() + " mods as links, "
                + overrides.size() + " files inside, " + skipped.size() + " skipped");
        return target;
    }

    private static void collect(File dir, String prefix, List<File[]> out) {
        File[] kids = dir.listFiles();
        if (kids == null) return;
        for (File k : kids) {
            if (k.getName().startsWith(".")) continue;
            if (k.isDirectory()) collect(k, prefix + "/" + k.getName(), out);
            else out.add(new File[]{k, new File(prefix + "/" + k.getName())});
        }
    }

    private static String hash(File f, String algo) throws IOException {
        try {
            MessageDigest md = MessageDigest.getInstance(algo);
            byte[] buf = new byte[64 * 1024];
            try (InputStream in = new FileInputStream(f)) {
                int n;
                while ((n = in.read(buf)) > 0) md.update(buf, 0, n);
            }
            StringBuilder sb = new StringBuilder();
            for (byte b : md.digest()) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IOException(e);
        }
    }

    @Override
    public double getProgress() {
        return progress;
    }

    @Override
    public String getStatus() {
        return status;
    }

    /** Назва файлу за замовчуванням: назва збірки латиницею чи цифрами, інакше «pack». */
    public static String fileNameOf(Instance instance) {
        String t = instance.getTitle() == null ? "" : instance.getTitle().replaceAll("[\\\\/:*?\"<>|]+", " ").trim();
        if (t.isEmpty()) t = "pack";
        if (t.length() > 60) t = t.substring(0, 60).trim();
        return t + ".mrpack";
    }
}
