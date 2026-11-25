/*
 * Copyright (C) 2021-2026 GummerCraft <http://gummercraft.fun>
 */

package com.gummer.launcher.forge;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gummer.launcher.util.Json;
import com.skcraft.launcher.Instance;
import com.skcraft.launcher.Launcher;
import com.skcraft.launcher.model.loader.LoaderManifest;
import com.skcraft.launcher.model.loader.ProcessorEntry;
import com.skcraft.launcher.model.loader.SidedData;
import com.skcraft.launcher.model.loader.VersionInfo;
import com.skcraft.launcher.model.loader.profiles.ModernForgeInstallProfile;
import com.skcraft.launcher.model.minecraft.GameArgument;
import com.skcraft.launcher.model.minecraft.Library;
import com.skcraft.launcher.model.minecraft.MavenName;
import com.skcraft.launcher.model.minecraft.MinecraftArguments;
import com.skcraft.launcher.model.minecraft.Side;
import com.skcraft.launcher.model.minecraft.VersionManifest;
import com.skcraft.launcher.model.modpack.DownloadableFile;
import com.skcraft.launcher.model.modpack.Manifest;
import com.skcraft.launcher.util.HttpRequest;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.jar.JarFile;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;

/**
 * Розділи Forge і NeoForge (з 1.33): чистий Minecraft з сучасним Forge (1.13 і новіші) або NeoForge, без модів (моди
 * гравець кладе сам у {@code mods/}). Як Vanilla і Fabric: кожна версія Minecraft це пак {@code forge-<версія>} чи
 * {@code neoforge-<версія>}, версія вибирається блоком біля «Запуск».
 *
 * <p>Установка без офіційного установника в окремому вікні: лаунчер качає установник з maven Forge чи NeoForge
 * (з перевіркою sha1), бере з нього {@code version.json} (бібліотеки, головний клас, аргументи) і
 * {@code install_profile.json} (processors: перейменування і латки клієнта Mojang) і виконує processors тим самим
 * механізмом, що й для наших модпаків з Forge ({@code ProcessorTask}). Логіка та сама, що в
 * {@code launcher-builder} {@code ModernForgeLoaderProcessor}, лише на ПК гравця.</p>
 *
 * <p>Версія завантажувача закріплюється в теці паку ({@code forge-loader.txt}, {@code neoforge-loader.txt}): перша
 * установка бере рекомендовану (Forge) чи найновішу стабільну (NeoForge), далі сама не міняється.</p>
 */
public final class ForgeVersions {

    public enum Kind {
        FORGE("forge-", "forge", "Forge", "https://maven.minecraftforge.net/",
                "net/minecraftforge/forge/", "forge"),
        NEOFORGE("neoforge-", "neoforge", "NeoForge", "https://maven.neoforged.net/releases/",
                "net/neoforged/neoforge/", "neoforge");

        public final String prefix;
        public final String group;
        public final String title;
        public final String maven;
        final String path;
        final String artifact;

        Kind(String prefix, String group, String title, String maven, String path, String artifact) {
            this.prefix = prefix;
            this.group = group;
            this.title = title;
            this.maven = maven;
            this.path = path;
            this.artifact = artifact;
        }

        String loaderFile() {
            return group + "-loader.txt";
        }
    }

    private static final Logger log = Logger.getLogger(ForgeVersions.class.getName());
    private static final ObjectMapper MAPPER = Json.newMapper();
    private static final String FORGE_PROMOS = "https://files.minecraftforge.net/net/minecraftforge/forge/promotions_slim.json";
    private static final Pattern SAFE = Pattern.compile("[0-9A-Za-z.+_-]+");

    private ForgeVersions() {
    }

    /** Forge чи NeoForge за назвою паку, інакше null. */
    public static Kind kindOf(Instance instance) {
        return instance != null ? kindOfName(instance.getKindName()) : null;
    }

    public static Kind kindOfName(String name) {
        if (name == null) return null;
        String n = name.toLowerCase(Locale.ROOT);
        if (n.startsWith(Kind.NEOFORGE.prefix)) return Kind.NEOFORGE;
        if (n.startsWith(Kind.FORGE.prefix)) return Kind.FORGE;
        return null;
    }

    public static String nameOf(Kind kind, String mc) {
        return kind.prefix + mc;
    }

    public static String titleOf(Kind kind, String mc) {
        return kind.title + " " + mc;
    }

    public static String versionIdOf(Instance instance) {
        Kind k = kindOf(instance);
        return instance.getKindName().substring(k.prefix.length());
    }

    /**
     * Версії завантажувача за версією Minecraft, кожен список від новішої. Forge 1.7.10 і новіші (старий формат
     * установника до 1.12.2 теж). NeoForge з 1.20.2 (для 1.20.1 у NeoForge інший артефакт, його не беремо).
     */
    public static Map<String, List<String>> versions(Launcher launcher, Kind kind) {
        Map<String, List<String>> out = new LinkedHashMap<String, List<String>>();
        byte[] xml = cached(launcher, kind.maven + kind.path + "maven-metadata.xml", kind.group + "_versions.xml");
        if (xml == null) return out;
        Matcher m = Pattern.compile("<version>([^<]+)</version>").matcher(new String(xml, StandardCharsets.UTF_8));
        while (m.find()) {
            String v = m.group(1).trim();
            if (!SAFE.matcher(v).matches()) continue;
            String mc = kind == Kind.FORGE ? forgeMc(v) : neoMc(v);
            if (mc == null) continue;
            if (kind == Kind.FORGE && !supportedMc(mc)) continue;
            List<String> list = out.get(mc);
            if (list == null) out.put(mc, list = new ArrayList<String>());
            list.add(v);
        }
        for (List<String> list : out.values()) {
            Collections.sort(list, ForgeVersions::compareVersions);
            Collections.reverse(list);
        }
        return out;
    }

    /** Рекомендована версія: Forge з promotions (recommended, інакше latest), NeoForge найновіша не beta. */
    public static String recommended(Launcher launcher, Kind kind, String mc, List<String> all) {
        if (all == null || all.isEmpty()) return null;
        if (kind == Kind.FORGE) {
            byte[] body = cached(launcher, FORGE_PROMOS, "forge_promotions.json");
            if (body != null) {
                try {
                    JsonNode promos = MAPPER.readTree(body).path("promos");
                    for (String key : new String[]{mc + "-recommended", mc + "-latest"}) {
                        String b = promos.path(key).asText(null);
                        String v = b == null ? null : inList(all, mc, mc + "-" + b);
                        if (v != null) return v;
                    }
                } catch (IOException e) {
                    log.log(Level.FINE, "Bad Forge promotions", e);
                }
            }
            return all.get(0);
        }
        for (String v : all) {
            if (!isBeta(v)) return v;
        }
        return all.get(0);
    }

    /**
     * 25.11.2026 (тікет #115): назва версії Forge так, як вона лежить у maven, або null. У Forge 1.7.10 - 1.10.2 у maven
     * хвіст гілки: {@code 1.7.10-10.13.4.1614-1.7.10}, {@code 1.10.2-12.18.3.2511-1.10.0}, а .mrpack, CurseForge і
     * promotions дають {@code 1.7.10-10.13.4.1614}. Без цього установник шукався за адресою без хвоста і отримував 404.
     * Спершу точна назва, потім з хвостом {@code -mc}, потім будь-який хвіст.
     */
    static String inList(List<String> all, String mc, String loader) {
        if (all == null || loader == null) return null;
        if (all.contains(loader)) return loader;
        if (all.contains(loader + "-" + mc)) return loader + "-" + mc;
        for (String v : all) {
            if (v.startsWith(loader + "-")) return v;
        }
        return null;
    }

    public static boolean isBeta(String v) {
        String s = v.toLowerCase(Locale.ROOT);
        return s.contains("beta") || s.contains("alpha") || s.contains("-rc") || s.contains("pre");
    }

    /** Закріплена версія завантажувача або null. */
    public static String pinnedLoader(Instance instance) {
        Kind kind = kindOf(instance);
        if (kind == null) return null;
        File f = new File(instance.getDir(), kind.loaderFile());
        if (!f.isFile()) return null;
        try {
            String v = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8).trim();
            return SAFE.matcher(v).matches() ? v : null;
        } catch (IOException e) {
            return null;
        }
    }

    public static void pinLoader(Instance instance, String loader) throws IOException {
        Kind kind = kindOf(instance);
        instance.getDir().mkdirs();
        Files.write(new File(instance.getDir(), kind.loaderFile()).toPath(), loader.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Маніфест паку: JSON версії Mojang + {@code version.json} установника; processors з {@code install_profile.json}
     * ідуть у {@code manifest.tasks}, їхні дані ({@code data/client.lzma} тощо) у {@code manifest.loaders}.
     */
    public static Manifest buildManifest(Launcher launcher, Instance instance) throws IOException, InterruptedException {
        return buildManifest(launcher, instance, kindOf(instance), versionIdOf(instance), pinnedLoader(instance));
    }

    /** Те саме з явними версіями (збірки .mrpack). */
    public static Manifest buildManifest(Launcher launcher, Instance instance, Kind kind, String mc, String loader)
            throws IOException, InterruptedException {
        URL url = instance.getManifestURL();
        if (url == null) throw new IOException("No Mojang version URL for " + instance.getName());
        if (loader == null) {
            List<String> all = versions(launcher, kind).get(mc);
            loader = recommended(launcher, kind, mc, all);
            if (loader == null) throw new IOException("The " + kind.title + " version list for " + mc + " is unavailable");
        } else if (kind == Kind.FORGE) {
            // тікет #115: «1.7.10-10.13.4.1614» з .mrpack -> «1.7.10-10.13.4.1614-1.7.10», як у maven
            String named = inList(versions(launcher, kind).get(mc), mc, loader);
            if (named != null && !named.equals(loader)) {
                log.info("Forge " + loader + " is " + named + " in the Forge maven");
                loader = named;
            }
        }

        byte[] body = HttpRequest.get(url).execute().expectResponseCode(200).returnContent().asBytes();
        VersionManifest version = MAPPER.readValue(body, VersionManifest.class);
        int java = 8;
        JsonNode jv = MAPPER.readTree(body).path("javaVersion").path("majorVersion");
        if (jv.isInt()) java = jv.asInt();

        File dir = new File(new File(launcher.getBaseDir(), "loaders"), kind.group + "-" + loader);
        File installer = downloadInstaller(kind, loader, dir);

        Manifest manifest = new Manifest();
        try (JarFile jar = new JarFile(installer)) {
            ZipEntry pe0 = jar.getEntry("install_profile.json");
            JsonNode profile0 = null;
            if (pe0 != null) {
                try (InputStream in = jar.getInputStream(pe0)) {
                    profile0 = MAPPER.readTree(in);
                }
            }
            if (profile0 != null && profile0.has("versionInfo")) {
                // старий Forge (1.7.10 - 1.12.1 і ранні 1.12.2): усе в install_profile.json, universal у корені установника
                legacy(launcher, jar, profile0, version);
            } else {
                modern(launcher, jar, installer, dir, kind, loader, manifest, version);
            }
        }

        manifest.setName(instance.getName());
        manifest.setTitle(titleOf(kind, mc));
        manifest.setVersion(mc + "+" + kind.group + "-" + loader);
        manifest.setGameVersion(mc);
        manifest.setJavaRuntime(String.valueOf(java));
        manifest.setMinimumVersion(0);
        manifest.setBaseUrl(url);
        manifest.setLibrariesLocation("https://libraries.minecraft.net");
        manifest.setVersionManifest(version);

        if (kindOf(instance) == kind) pinLoader(instance, loader);
        instance.setJavaRuntime(manifest.getJavaRuntime());
        instance.setTitle(manifest.getTitle());
        log.info(instance.getName() + ": Minecraft " + mc + ", " + kind.title + " " + loader);
        return manifest;
    }

    /** Старий формат: versionInfo (бібліотеки з url maven, minecraftArguments з --tweakClass), universal з кореня. */
    private static void legacy(Launcher launcher, JarFile jar, JsonNode profile, VersionManifest version) throws IOException {
        JsonNode install = profile.path("install");
        String path = install.path("path").asText(null);
        String filePath = install.path("filePath").asText(null);
        if (path == null || filePath == null || filePath.contains("/") || filePath.contains("..")) {
            throw new IOException("Unsupported old Forge installer");
        }
        ZipEntry universal = jar.getEntry(filePath);
        if (universal == null) throw new IOException("No " + filePath + " in the installer");
        File target = new File(launcher.getLibrariesDir(), MavenName.from(path).getFilePath());
        if (!(target.isFile() && target.length() == universal.getSize())) {
            target.getParentFile().mkdirs();
            try (InputStream in = jar.getInputStream(universal)) {
                Files.copy(in, target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        }
        VersionInfo info = MAPPER.treeToValue(profile.path("versionInfo"), VersionInfo.class);
        mergeVersion(info, version);
    }

    private static void mergeVersion(VersionInfo info, VersionManifest version) {
        LinkedHashSet<Library> libs = new LinkedHashSet<Library>();
        if (info.getLibraries() != null) libs.addAll(info.getLibraries());
        if (version.getLibraries() != null) libs.addAll(version.getLibraries());
        version.setLibraries(libs);
        if (info.getMainClass() != null) version.setMainClass(info.getMainClass());
        MinecraftArguments ia = info.getArguments();
        if (ia != null) {
            MinecraftArguments va = version.getArguments();
            if (va == null || info.isOverridingArguments()) {
                version.setArguments(ia);
            } else {
                if (ia.getJvmArguments() != null) for (GameArgument a : ia.getJvmArguments()) va.getJvmArguments().add(a);
                if (ia.getGameArguments() != null) for (GameArgument a : ia.getGameArguments()) va.getGameArguments().add(a);
            }
        }
        if (info.getLogging() != null) version.setLogging(info.getLogging());
    }

    /** Сучасний формат (1.12.2 з 2847, 1.13+, NeoForge): version.json + install_profile.json з processors. */
    private static void modern(Launcher launcher, JarFile jar, File installer, File dir, Kind kind, String loader,
                               Manifest manifest, VersionManifest version) throws IOException {
        {
            // version.json: бібліотеки завантажувача першими, головний клас, аргументи
            ZipEntry ve = jar.getEntry("version.json");
            if (ve == null) throw new IOException(installer.getName() + " has no version.json");
            VersionInfo info;
            try (InputStream in = jar.getInputStream(ve)) {
                info = MAPPER.readValue(in, VersionInfo.class);
            }
            mergeVersion(info, version);
            java.util.Collection<Library> libs = version.getLibraries();

            // бібліотеки, вкладені в установник (universal, shim): одразу в теку бібліотек
            File libDir = launcher.getLibrariesDir();
            Enumeration<? extends ZipEntry> en = jar.entries();
            while (en.hasMoreElements()) {
                ZipEntry e = en.nextElement();
                if (e.isDirectory() || !e.getName().startsWith("maven/")) continue;
                String rel = e.getName().substring("maven/".length());
                if (rel.contains("..")) continue;
                File target = new File(libDir, rel);
                if (target.isFile() && target.length() == e.getSize()) continue;
                target.getParentFile().mkdirs();
                try (InputStream in = jar.getInputStream(e)) {
                    Files.copy(in, target.toPath(), StandardCopyOption.REPLACE_EXISTING);
                }
            }

            // install_profile.json: processors
            ZipEntry pe = jar.getEntry("install_profile.json");
            if (pe == null) throw new IOException(installer.getName() + " has no install_profile.json");
            ModernForgeInstallProfile profile;
            try (InputStream in = jar.getInputStream(pe)) {
                profile = MAPPER.readValue(in, ModernForgeInstallProfile.class);
            }
            if (profile.getProcessors() == null) {
                throw new IOException(installer.getName() + " is not a modern installer (no processors)");
            }
            Map<String, SidedData<String>> data = profile.getData() != null
                    ? profile.getData() : new HashMap<String, SidedData<String>>();
            List<DownloadableFile> files = new ArrayList<DownloadableFile>();
            for (Map.Entry<String, SidedData<String>> d : data.entrySet()) {
                String client = d.getValue() != null ? d.getValue().getClient() : null;
                if (client == null || !client.startsWith("/")) continue;
                ZipEntry de = jar.getEntry(client.substring(1));
                if (de == null) continue;
                File out = new File(dir, client.substring(client.lastIndexOf('/') + 1));
                if (!(out.isFile() && out.length() == de.getSize())) {
                    try (InputStream in = jar.getInputStream(de)) {
                        Files.copy(in, out.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    }
                }
                files.add(localFile(out, d.getKey().toLowerCase(Locale.ROOT) + "-" + out.getName()));
                d.getValue().setClient("&" + d.getKey().toLowerCase(Locale.ROOT) + "-" + out.getName() + "&");
            }
            files.add(localFile(installer, "installer.jar"));
            data.put("INSTALLER", SidedData.of("&installer.jar&"));
            data.put("SIDE", SidedData.create("client", "server"));
            profile.setData(data);

            String loaderName = kind.group + "-" + loader;
            manifest.getLoaders().put(loaderName, new LoaderManifest(profile.getLibraries(), data, files));

            // те, що створюють processors (клієнт з латками Forge), не качати
            SidedData<String> patched = data.get("PATCHED");
            String patchedName = patched != null && patched.getClient() != null && patched.getClient().startsWith("[")
                    ? patched.getClient().substring(1, patched.getClient().length() - 1) : null;
            for (Library lib : libs) {
                Library.Artifact a = lib.getDownloads() != null ? lib.getDownloads().getArtifact() : null;
                boolean noUrl = a != null && (a.getUrl() == null || a.getUrl().isEmpty());
                if ((patchedName != null && lib.matches(patchedName))
                        || (noUrl && !new File(libDir, a.getPath()).isFile())) {
                    lib.setGenerated(true);
                }
            }

            for (ProcessorEntry entry : profile.toProcessorEntries(loaderName)) {
                entry.setManifest(manifest);
                manifest.getTasks().add(entry);
            }
        }
    }

    private static DownloadableFile localFile(File file, String name) {
        DownloadableFile f = new DownloadableFile();
        f.setName(name);
        f.setLocation(file.toURI().toString());
        f.setSide(Side.CLIENT);
        f.setSize((int) file.length());
        return f;
    }

    /** Установник з maven, перевірений sha1 з того ж maven; кеш у {@code loaders/<назва>/}. */
    private static File downloadInstaller(Kind kind, String loader, File dir) throws IOException, InterruptedException {
        dir.mkdirs();
        String file = kind.artifact + "-" + loader + "-installer.jar";
        File out = new File(dir, file);
        String base = kind.maven + kind.path + loader + "/" + file;
        String sha1 = new String(HttpRequest.get(HttpRequest.url(base + ".sha1")).execute().expectResponseCode(200)
                .returnContent().asBytes(), StandardCharsets.US_ASCII).trim().toLowerCase(Locale.ROOT);
        if (sha1.length() > 40) sha1 = sha1.substring(0, 40);
        if (out.isFile() && sha1.equals(sha1(Files.readAllBytes(out.toPath())))) return out;
        byte[] jar = HttpRequest.get(HttpRequest.url(base)).execute().expectResponseCode(200).returnContent().asBytes();
        String got = sha1(jar);
        if (!got.equals(sha1)) throw new IOException(file + ": sha1 " + got + " does not match " + sha1);
        Files.write(out.toPath(), jar);
        return out;
    }

    private static String sha1(byte[] data) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-1").digest(data);
            StringBuilder sb = new StringBuilder();
            for (byte b : d) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** 1.21.1-52.1.20 -> 1.21.1 */
    static String forgeMc(String v) {
        int i = v.indexOf('-');
        if (i <= 0) return null;
        String mc = v.substring(0, i);
        return mc.matches("\\d+\\.\\d+(\\.\\d+)?") ? mc : null;
    }

    /** NeoForge: 21.1.77 -> 1.21.1, 21.0.5 -> 1.21, 26.3.0.100 -> 26.3, 26.1.2.126 -> 26.1.2 */
    static String neoMc(String v) {
        Matcher m = Pattern.compile("(\\d+)\\.(\\d+)\\.(\\d+)(?:\\.(\\d+))?(?:-.*)?").matcher(v);
        if (!m.matches()) return null;
        int a = Integer.parseInt(m.group(1));
        int b = Integer.parseInt(m.group(2));
        int c = Integer.parseInt(m.group(3));
        if (m.group(4) != null) {
            if (a < 26) return null;
            return a + "." + b + (c != 0 ? "." + c : "");
        }
        if (a < 20 || a >= 26 || (a == 20 && b < 2)) return null;
        return "1." + a + (b != 0 ? "." + b : "");
    }

    /** Forge 1.7.10 і новіші (старіші версії мають інший установник або не мають його зовсім). */
    static boolean supportedMc(String mc) {
        String[] p = mc.split("\\.");
        int major = Integer.parseInt(p[0]);
        if (major > 1) return true;
        int minor = p.length > 1 ? Integer.parseInt(p[1]) : 0;
        int patch = p.length > 2 ? Integer.parseInt(p[2]) : 0;
        return minor > 7 || (minor == 7 && patch >= 10);
    }

    static int compareVersions(String x, String y) {
        String[] a = x.split("[.\\-+]");
        String[] b = y.split("[.\\-+]");
        for (int i = 0; i < Math.max(a.length, b.length); i++) {
            String p = i < a.length ? a[i] : "";
            String q = i < b.length ? b[i] : "";
            boolean pn = p.matches("\\d+"), qn = q.matches("\\d+");
            int c;
            if (pn && qn) c = Long.compare(Long.parseLong(p), Long.parseLong(q));
            else if (pn) c = 1;
            else if (qn) c = -1;
            else c = p.compareTo(q);
            if (c != 0) return c;
        }
        return 0;
    }

    private static byte[] cached(Launcher launcher, String address, String cacheName) {
        File cache = new File(launcher.getBaseDir(), cacheName);
        try {
            byte[] body = HttpRequest.get(HttpRequest.url(address)).execute().expectResponseCode(200).returnContent().asBytes();
            try {
                Files.write(cache.toPath(), body);
            } catch (IOException e) {
                log.log(Level.FINE, "Could not cache " + cacheName, e);
            }
            return body;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.log(Level.WARNING, "Could not download " + address + ", trying the cached copy", e);
        }
        if (cache.isFile()) {
            try {
                return Files.readAllBytes(cache.toPath());
            } catch (IOException e) {
                log.log(Level.WARNING, "The cached " + cacheName + " is unreadable", e);
            }
        }
        return null;
    }
}
