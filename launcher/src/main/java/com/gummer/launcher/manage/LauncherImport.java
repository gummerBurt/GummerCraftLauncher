/*
 * Copyright (C) 2021-2026 GummerCraft <http://gummercraft.fun>
 */

package com.gummer.launcher.manage;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.gummer.launcher.modrinth.MrpackPacks;
import com.gummer.launcher.util.Json;
import com.skcraft.concurrency.ProgressObservable;
import com.skcraft.launcher.Launcher;
import com.skcraft.launcher.util.Environment;
import com.skcraft.launcher.util.Platform;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Імпорт збірок з інших лаунчерів (19.11.2026, власник: «Імпорт з інших лаунчерів -- го»).
 *
 * <p>У всіх популярних лаунчерів збірка це тека гри (моди, конфіги, світи) плюс файл, де записано версію Minecraft і
 * завантажувач. Лаунчер знаходить такі збірки в стандартних місцях ({@link #scan}) або в теці, яку вибрав гравець
 * ({@link #detect}), і створює з кожної свою збірку того ж виду, що імпорт {@code .mrpack}: тека
 * {@code instances/mrpack-<назва>}, у {@code mrpack.json} версія гри і завантажувача, а гру і завантажувач лаунчер
 * ставить своїм звичайним кодом. Файли гри копіюються, оригінал не чіпається.
 *
 * <p>Що розуміє:
 * <ul>
 * <li>Prism Launcher і MultiMC: {@code instance.cfg} і {@code mmc-pack.json} (тека гри {@code .minecraft} або {@code minecraft});</li>
 * <li>CurseForge: {@code minecraftinstance.json} ({@code baseModLoader.name}: forge-47.2.0, fabric-0.15.7-1.20.1, neoforge-21.1.77);</li>
 * <li>ATLauncher: {@code instance.json} ({@code id} версія гри, {@code launcher.loaderVersion});</li>
 * <li>Modrinth App: {@code profile.json} у теці профілю (старі версії App). Нові версії тримають усе в базі SQLite, тож
 * такий профіль показується з поясненням, що його краще експортувати з Modrinth App у .mrpack;</li>
 * <li>офіційний лаунчер, SKlauncher і TLauncher: профілі з {@code launcher_profiles.json}, версія з {@code versions/<id>/<id>.json}
 * (inheritsFrom і бібліотеки завантажувача). Окрема тека гри профілю копіюється цілком; із самої .minecraft усе, крім
 * файлів гри (versions, libraries, assets), лаунчерів і тек інших профілів.</li>
 * </ul>
 * Чого не вміє (і чесно пише): збірки з невідомим форматом, снапшоти і «latest-release» без конкретної версії.
 */
public final class LauncherImport implements ProgressObservable {

    private static final Logger log = Logger.getLogger(LauncherImport.class.getName());
    private static final ObjectMapper MAPPER = Json.newMapper();

    /** Чого не копіювати з теки збірки іншого лаунчера: кеші, логи, файли самого лаунчера. */
    static final Set<String> SKIP = new HashSet<String>(Arrays.asList(
            "logs", "crash-reports", ".cache", "cache", "natives", "libraries", "versions", "assets", ".fabric",
            ".quilt", ".mixin.out", "downloads", "debug", "backups", "minecraftinstance.json", "instance.json",
            "profile.json", "instance.cfg", "mmc-pack.json", "icon.png", ".curseclient", "usercache.json",
            "usernamecache.json", "launcher_profiles.json", "launcher_accounts.json", "launcher_log.txt",
            "launcher_msa_credentials.bin", "launcher_ui_state.json", "launcher_settings.json", "runtime", "bin",
            "TlauncherProfiles.json", "tlauncher_profiles.json", "webcache", "webcache2", "realms_persistence.json"));
    /**
     * Спільна .minecraft офіційного лаунчера, SKlauncher і TLauncher: копіюється все, крім {@link #SKIP} і файлів самих
     * лаунчерів. 21.11.2026 (тікет #97, власник: «зроби імпорт універсальним, ... багато модів люблять робити свої
     * власні теки, як от кастомнпс»): раніше тут був білий список (mods, config, saves...), і губилось усе інше:
     * оформлення меню модпака (resources, fancymenu_data), дані модів у своїх теках (customnpcs, ftbquests,
     * serverconfig, local...). Модів, що пишуть свої теки, сотні, тож чорний список, а не білий.
     */
    static final Set<String> DOT_SKIP = new HashSet<String>(Arrays.asList(
            "jre", "jre-x64", "jre-legacy", "java", "launcher", "skins", "profiles", "updates", "treatment_tags.json",
            // 22.11.2026: Java офіційного лаунчера (runtime) і кеш браузера лаунчера (webcache2), не потрібні збірці
            "runtime", "webcache", "webcache2"));
    /** Початки назв файлів і тек самих лаунчерів у .minecraft (launcher_*, sklauncher*, tlauncher*, звіти JVM). */
    static final String[] DOT_SKIP_PREFIX = {"launcher_", "launcher-", "sklauncher", ".sklauncher", "tlauncher", ".tlauncher",
            "hs_err_pid"};

    private static final Pattern VERSION = Pattern.compile("[0-9A-Za-z._+-]+");

    /** Знайдена збірка. {@code problem} не null: показати, але не імпортувати (ключ lang з поясненням). */
    public static final class Found {
        public String source;      // Prism, CurseForge, ATLauncher, Modrinth App, Minecraft
        public String title;
        public String mc;
        public String loader = "vanilla";
        public String loaderVersion;
        public File gameDir;
        /** Тека гри це сама спільна .minecraft (а не окрема тека профілю): відкидаються ще й файли лаунчерів. */
        public boolean dotMinecraft;
        /** Теки інших профілів усередині .minecraft: їх не тягнемо в цю збірку. */
        public List<File> nested = new ArrayList<File>();
        public String problem;
        public long bytes = -1;

        public String loaderTitle() {
            switch (loader) {
                case "fabric":
                    return "Fabric";
                case "quilt":
                    return "Quilt";
                case "forge":
                    return "Forge";
                case "neoforge":
                    return "NeoForge";
                default:
                    return "Vanilla";
            }
        }

        @Override
        public String toString() {
            return source + ": " + title + " (" + mc + ", " + loader + " " + loaderVersion + ") " + gameDir
                    + (problem != null ? " [" + problem + "]" : "");
        }
    }

    // ------------------------------------------------------------------ пошук

    /** Збірки в стандартних місцях усіх відомих лаунчерів. Не з потоку інтерфейсу. */
    public static List<Found> scan() {
        List<Found> out = new ArrayList<Found>();
        File home = new File(System.getProperty("user.home"));
        Platform pl = Environment.getInstance().getPlatform();
        String appdata = System.getenv("APPDATA");
        List<File> data = new ArrayList<File>(); // теки даних програм на цій ОС
        if (pl == Platform.WINDOWS && appdata != null) data.add(new File(appdata));
        if (pl == Platform.MAC_OS_X) data.add(new File(home, "Library/Application Support"));
        if (pl == Platform.LINUX) {
            String xdg = System.getenv("XDG_DATA_HOME");
            data.add(xdg != null && !xdg.isEmpty() ? new File(xdg) : new File(home, ".local/share"));
            data.add(new File(home, ".var/app/org.prismlauncher.PrismLauncher/data")); // Flatpak
        }
        // 22.11.2026: кожне місце окремо: помилка в одному (дивний instance.cfg, профіль TLauncher) лише в лог, решта знайдеться
        for (File d : data) {
            for (String n : new String[]{"PrismLauncher", "prismlauncher", "PolyMC", "polymc"}) {
                File root = new File(d, n); // PolyMC: попередник Prism, той самий формат
                safely(root, () -> scanPrism(root, n.toLowerCase(Locale.ROOT).startsWith("poly") ? "PolyMC" : "Prism Launcher", out));
            }
            for (String rel : new String[]{"ATLauncher/instances", "atlauncher/instances", "ModrinthApp/profiles",
                    "com.modrinth.theseus/profiles"}) {
                File dir = new File(d, rel);
                safely(dir, () -> scanEach(dir, out));
            }
        }
        for (String rel : new String[]{"curseforge/minecraft/Instances", "Documents/curseforge/minecraft/Instances"}) {
            File dir = new File(home, rel);
            safely(dir, () -> scanEach(dir, out));
        }
        File dot = pl == Platform.WINDOWS && appdata != null ? new File(appdata, ".minecraft")
                : pl == Platform.MAC_OS_X ? new File(home, "Library/Application Support/minecraft") : new File(home, ".minecraft");
        safely(dot, () -> scanDotMinecraft(dot, out));
        return dedupe(out);
    }

    private static void safely(File where, Runnable r) {
        try {
            r.run();
        } catch (RuntimeException e) {
            java.util.logging.Logger.getLogger(LauncherImport.class.getName())
                    .log(java.util.logging.Level.WARNING, "Import: scanning " + where + " failed", e);
        }
    }

    private static List<Found> dedupe(List<Found> in) {
        Map<String, Found> m = new LinkedHashMap<String, Found>();
        for (Found f : in) {
            String key;
            try {
                key = f.gameDir.getCanonicalPath() + "|" + f.mc + "|" + f.loader;
            } catch (IOException e) {
                key = f.gameDir.getAbsolutePath() + "|" + f.mc + "|" + f.loader;
            }
            if (!m.containsKey(key)) m.put(key, f);
        }
        return new ArrayList<Found>(m.values());
    }

    private static void scanPrism(File root, String source, List<Found> out) {
        if (!root.isDirectory()) return;
        File instances = new File(root, "instances");
        String custom = cfg(new File(root, root.getName().toLowerCase(Locale.ROOT).startsWith("poly") ? "polymc.cfg" : "prismlauncher.cfg"), "InstanceDir");
        if (custom != null && !custom.trim().isEmpty()) {
            File c = new File(custom.trim());
            instances = c.isAbsolute() ? c : new File(root, custom.trim());
        }
        File[] kids = instances.listFiles();
        if (kids == null) return;
        for (File k : kids) {
            Found f = prism(k);
            if (f != null) {
                f.source = source;
                out.add(f);
            }
        }
    }

    private static void scanEach(File dir, List<Found> out) {
        File[] kids = dir.listFiles();
        if (kids == null) return;
        for (File k : kids) {
            if (!k.isDirectory()) continue;
            Found f = detectOne(k);
            if (f != null) out.add(f);
        }
    }

    /** Тека, вибрана гравцем: сама збірка, тека зі збірками або .minecraft. */
    public static List<Found> detect(File dir) {
        List<Found> out = new ArrayList<Found>();
        if (dir == null || !dir.isDirectory()) return out;
        Found f = detectOne(dir);
        if (f != null) {
            out.add(f);
            return out;
        }
        if (new File(dir, "launcher_profiles.json").isFile() || new File(dir, "versions").isDirectory()
                && new File(dir, "assets").isDirectory()) {
            scanDotMinecraft(dir, out);
            if (!out.isEmpty()) return dedupe(out);
        }
        if (new File(dir, "instances").isDirectory()) scanPrism(dir, "Prism Launcher", out);
        if (out.isEmpty()) scanEach(dir, out);
        if (out.isEmpty()) scanEach(new File(dir, "instances"), out);
        if (out.isEmpty()) scanEach(new File(dir, "profiles"), out);
        if (out.isEmpty()) scanEach(new File(dir, "Instances"), out);
        // вибрали теку гри всередині збірки (.minecraft Prism)
        if (out.isEmpty() && dir.getParentFile() != null) {
            Found p = detectOne(dir.getParentFile());
            if (p != null) out.add(p);
        }
        return dedupe(out);
    }

    /** Збірка будь-якого відомого формату в цій теці, або null. */
    static Found detectOne(File dir) {
        if (new File(dir, "mmc-pack.json").isFile()) {
            Found f = prism(dir);
            if (f != null) f.source = "Prism Launcher / MultiMC";
            return f;
        }
        if (new File(dir, "minecraftinstance.json").isFile()) return curseForge(dir);
        if (new File(dir, "instance.json").isFile()) return atLauncher(dir);
        if (new File(dir, "profile.json").isFile()) return modrinthOld(dir);
        // Modrinth App 0.8+: профілі без profile.json, опис у базі SQLite самої програми
        File p = dir.getParentFile();
        if (p != null && "profiles".equals(p.getName()) && p.getParentFile() != null
                && p.getParentFile().getName().toLowerCase(Locale.ROOT).contains("modrinth")
                && (new File(dir, "mods").isDirectory() || new File(dir, "saves").isDirectory())) {
            Found f = new Found();
            f.source = "Modrinth App";
            f.title = dir.getName();
            f.gameDir = dir;
            f.problem = "modrinthDb";
            return f;
        }
        return null;
    }

    static Found prism(File dir) {
        File pack = new File(dir, "mmc-pack.json");
        if (!pack.isFile()) return null;
        Found f = new Found();
        f.title = cfg(new File(dir, "instance.cfg"), "name");
        if (f.title == null) f.title = dir.getName();
        File game = new File(dir, ".minecraft");
        if (!game.isDirectory()) game = new File(dir, "minecraft");
        f.gameDir = game;
        try {
            for (JsonNode c : MAPPER.readTree(pack).path("components")) {
                String uid = c.path("uid").asText(""), v = c.path("version").asText(null);
                if (v == null) continue;
                switch (uid) {
                    case "net.minecraft":
                        f.mc = v;
                        break;
                    case "net.fabricmc.fabric-loader":
                        loader(f, "fabric", v);
                        break;
                    case "org.quiltmc.quilt-loader":
                        loader(f, "quilt", v);
                        break;
                    case "net.minecraftforge":
                        loader(f, "forge", v);
                        break;
                    case "net.neoforged":
                        loader(f, "neoforge", v);
                        break;
                    default:
                        break;
                }
            }
        } catch (IOException e) {
            f.problem = "unreadable";
        }
        return finish(f);
    }

    static Found curseForge(File dir) {
        Found f = new Found();
        f.source = "CurseForge";
        f.gameDir = dir;
        f.title = dir.getName();
        try {
            JsonNode j = MAPPER.readTree(new File(dir, "minecraftinstance.json"));
            f.title = j.path("name").asText(dir.getName());
            JsonNode b = j.path("baseModLoader");
            f.mc = b.path("minecraftVersion").asText(null);
            if (f.mc == null || f.mc.isEmpty()) f.mc = j.path("gameVersion").asText(null);
            String name = b.path("name").asText(""); // forge-47.2.0, fabric-0.15.7-1.20.1, neoforge-21.1.77, quilt-0.26.0-1.20.1
            int dash = name.indexOf('-');
            if (dash > 0) {
                String kind = name.substring(0, dash).toLowerCase(Locale.ROOT), v = name.substring(dash + 1);
                if (f.mc != null && v.endsWith("-" + f.mc) && !"forge".equals(kind)) v = v.substring(0, v.length() - f.mc.length() - 1);
                if ("forge".equals(kind) || "fabric".equals(kind) || "quilt".equals(kind) || "neoforge".equals(kind)) loader(f, kind, v);
            }
        } catch (IOException e) {
            f.problem = "unreadable";
        }
        return finish(f);
    }

    static Found atLauncher(File dir) {
        Found f = new Found();
        f.source = "ATLauncher";
        f.gameDir = dir;
        f.title = dir.getName();
        try {
            JsonNode j = MAPPER.readTree(new File(dir, "instance.json"));
            JsonNode l = j.path("launcher");
            if (l.isMissingNode() && !j.has("id")) return null; // чужий instance.json
            f.title = l.path("name").asText(dir.getName());
            f.mc = j.path("id").asText(null);
            JsonNode lv = l.path("loaderVersion");
            String type = lv.path("type").asText("").toLowerCase(Locale.ROOT);
            String v = lv.path("version").asText(null);
            if (v != null && (type.equals("fabric") || type.equals("quilt") || type.equals("forge") || type.equals("neoforge"))) {
                loader(f, type, v);
            }
        } catch (IOException e) {
            f.problem = "unreadable";
        }
        return finish(f);
    }

    static Found modrinthOld(File dir) {
        Found f = new Found();
        f.source = "Modrinth App";
        f.gameDir = dir;
        f.title = dir.getName();
        try {
            JsonNode j = MAPPER.readTree(new File(dir, "profile.json"));
            JsonNode m = j.path("metadata");
            f.title = m.path("name").asText(dir.getName());
            f.mc = m.path("game_version").asText(null);
            String kind = m.path("loader").asText("vanilla").toLowerCase(Locale.ROOT);
            String v = m.path("loader_version").path("id").asText(null);
            if (v != null && !"vanilla".equals(kind)) loader(f, kind, v);
        } catch (IOException e) {
            f.problem = "unreadable";
        }
        return finish(f);
    }

    /** Офіційний лаунчер і TLauncher: профілі з launcher_profiles.json (або всі версії, якщо профілів немає). */
    static void scanDotMinecraft(File dot, List<Found> out) {
        if (!dot.isDirectory()) return;
        List<String[]> profiles = new ArrayList<String[]>(); // name, versionId, gameDir, лаунчер
        // 19.11.2026 (власник: «СКлаунчер визначило як тлаунчер»): .minecraft спільна для офіційного лаунчера, SKlauncher
        // і TLauncher, тож назва береться за їхніми слідами: TLauncher пише свій файл профілів, SKlauncher лишає
        // теку sklauncher і sklauncher-fx.jar
        boolean sk = new File(dot, "sklauncher").isDirectory() || new File(dot, "sklauncher-fx.jar").isFile();
        for (String pf : new String[]{"launcher_profiles.json", "TlauncherProfiles.json", "tlauncher_profiles.json"}) {
            File f = new File(dot, pf);
            String who = pf.toLowerCase(Locale.ROOT).startsWith("tlauncher") ? "TLauncher" : sk ? "SKlauncher" : "Minecraft Launcher";
            if (!f.isFile()) continue;
            try {
                JsonNode ps = MAPPER.readTree(f).path("profiles");
                for (Iterator<Map.Entry<String, JsonNode>> it = ps.fields(); it.hasNext(); ) {
                    JsonNode p = it.next().getValue();
                    String v = p.path("lastVersionId").asText(null);
                    if (v == null || v.startsWith("latest-")) continue;
                    profiles.add(new String[]{p.path("name").asText(v), v, p.path("gameDir").asText(null), who});
                }
            } catch (IOException e) {
                log.info("Unreadable " + f + ": " + e);
            }
        }
        if (profiles.isEmpty()) {
            File[] vs = new File(dot, "versions").listFiles();
            String who = sk ? "SKlauncher" : new File(dot, "TlauncherProfiles.json").isFile() ? "TLauncher" : "Minecraft Launcher";
            if (vs != null) for (File v : vs) if (new File(v, v.getName() + ".json").isFile()) profiles.add(new String[]{v.getName(), v.getName(), null, who});
        }
        // Окрема тека гри профілю (SKlauncher і TLauncher так ставлять модпаки) належить лише цій збірці і копіюється
        // цілком, як збірка Prism. Сама .minecraft спільна: без файлів лаунчерів і без тек інших профілів у ній.
        List<File> own = new ArrayList<File>();
        for (String[] p : profiles) {
            File gd = gameDirOf(dot, p[2]);
            if (!same(gd, dot)) own.add(gd);
        }
        for (String[] p : profiles) {
            Found f = new Found();
            f.source = p[3];
            f.title = p[0].isEmpty() ? p[1] : p[0];
            f.gameDir = gameDirOf(dot, p[2]);
            f.dotMinecraft = same(f.gameDir, dot);
            if (f.dotMinecraft) f.nested.addAll(own);
            versionJson(f, dot, p[1], 0);
            if (f.mc == null) parseVersionId(f, p[1]);
            out.add(finish(f));
        }
    }

    /** versions/<id>/<id>.json: inheritsFrom дає версію гри, бібліотеки дають завантажувач. */
    private static void versionJson(Found f, File dot, String id, int depth) {
        File j = new File(new File(new File(dot, "versions"), id), id + ".json");
        if (!j.isFile() || depth > 3) return;
        try {
            JsonNode v = MAPPER.readTree(j);
            for (JsonNode lib : v.path("libraries")) {
                String n = lib.path("name").asText("");
                String[] g = n.split(":");
                if (g.length < 3) continue;
                String ga = g[0] + ":" + g[1];
                if (ga.equals("net.fabricmc:fabric-loader")) loader(f, "fabric", g[2]);
                else if (ga.equals("org.quiltmc:quilt-loader")) loader(f, "quilt", g[2]);
                else if (ga.equals("net.minecraftforge:forge") || ga.equals("net.minecraftforge:fmlloader")) {
                    String fv = g[2]; // 1.20.1-47.2.0 (finish додасть версію гри назад)
                    loader(f, "forge", fv.contains("-") ? fv.substring(fv.indexOf('-') + 1) : fv);
                } else if (ga.equals("net.neoforged:neoforge")) {
                    loader(f, "neoforge", g[2]);
                }
            }
            String parent = v.path("inheritsFrom").asText(null);
            if (parent != null) {
                if (f.mc == null) f.mc = parent;
                versionJson(f, dot, parent, depth + 1);
            } else if (f.mc == null) {
                f.mc = v.path("id").asText(id);
            }
        } catch (IOException e) {
            log.info("Unreadable " + j + ": " + e);
        }
    }

    private static final Pattern FABRIC_ID = Pattern.compile("(fabric|quilt)-loader-([0-9A-Za-z.+_-]+?)-([0-9][0-9A-Za-z.\\-]*)");
    private static final Pattern FORGE_ID = Pattern.compile("([0-9][0-9.]*)-forge-?([0-9][0-9A-Za-z.\\-]*)");
    private static final Pattern NEO_ID = Pattern.compile("neoforge-(([0-9]+)\\.([0-9]+)\\.[0-9A-Za-z.\\-]+)");
    private static final Pattern PLAIN = Pattern.compile("[0-9]+\\.[0-9]+(\\.[0-9]+)?");

    /** Без json версії: за самою назвою (fabric-loader-0.15.11-1.20.1, 1.20.1-forge-47.2.0, neoforge-21.1.77, 1.20.1). */
    static void parseVersionId(Found f, String id) {
        Matcher m = FABRIC_ID.matcher(id);
        if (m.matches()) {
            f.mc = m.group(3);
            loader(f, m.group(1), m.group(2));
            return;
        }
        m = FORGE_ID.matcher(id);
        if (m.matches()) {
            f.mc = m.group(1);
            loader(f, "forge", m.group(2));
            return;
        }
        m = NEO_ID.matcher(id);
        if (m.matches()) {
            f.mc = neoMc(m.group(2), m.group(3));
            loader(f, "neoforge", m.group(1));
            return;
        }
        Matcher p = Pattern.compile("^(" + PLAIN.pattern() + ")").matcher(id);
        if (p.find()) f.mc = p.group(1); // 1.20.1-OptiFine_HD_U_I6 і подібне: ваніль
    }

    /** NeoForge 21.1.x = Minecraft 1.21.1, 20.4.x = 1.20.4, 21.0.x = 1.21. */
    static String neoMc(String major, String minor) {
        return "0".equals(minor) ? "1." + major : "1." + major + "." + minor;
    }

    private static void loader(Found f, String kind, String v) {
        if (v == null || v.isEmpty()) return;
        f.loader = kind;
        f.loaderVersion = v;
    }

    /** Звести до формату лаунчера і перевірити, що імпортувати можна. */
    private static Found finish(Found f) {
        if (f == null) return null;
        if (f.problem != null) return f;
        if (f.mc == null || !VERSION.matcher(f.mc).matches()) {
            f.problem = "noVersion";
            return f;
        }
        if (f.loaderVersion != null && !VERSION.matcher(f.loaderVersion).matches()) {
            f.problem = "noVersion";
            return f;
        }
        // Forge у лаунчері повною версією maven: 1.20.1-47.2.0
        if ("forge".equals(f.loader) && f.loaderVersion != null && !f.loaderVersion.startsWith(f.mc + "-")) {
            f.loaderVersion = f.mc + "-" + f.loaderVersion;
        }
        if (f.gameDir == null || !f.gameDir.isDirectory()) f.problem = "noFiles";
        return f;
    }

    /** Тека гри профілю launcher_profiles.json: своя, якщо задана і існує (відносна від .minecraft), інакше .minecraft. */
    static File gameDirOf(File dot, String gameDir) {
        if (gameDir == null || gameDir.trim().isEmpty()) return dot;
        File gd = new File(gameDir.trim());
        if (!gd.isAbsolute()) gd = new File(dot, gameDir.trim());
        return gd.isDirectory() ? gd : dot;
    }

    static boolean same(File a, File b) {
        try {
            return a.getCanonicalFile().equals(b.getCanonicalFile());
        } catch (IOException e) {
            return a.getAbsoluteFile().equals(b.getAbsoluteFile());
        }
    }

    /** Чи лежить {@code inner} у {@code outer} (або це вона сама). */
    static boolean inside(File inner, File outer) {
        try {
            return inner.getCanonicalFile().toPath().startsWith(outer.getCanonicalFile().toPath());
        } catch (IOException e) {
            return inner.getAbsoluteFile().toPath().startsWith(outer.getAbsoluteFile().toPath());
        }
    }

    /** Чи копіювати файл чи теку верхнього рівня теки гри. */
    static boolean copyTop(Found f, File k) {
        String n = k.getName();
        String low = n.toLowerCase(Locale.ROOT);
        if (SKIP.contains(n) || SKIP.contains(low) || low.endsWith(".log") || low.endsWith(".tmp")) return false;
        if (!f.dotMinecraft) return true;
        if (DOT_SKIP.contains(low)) return false;
        for (String p : DOT_SKIP_PREFIX) if (low.startsWith(p)) return false;
        if (k.isFile() && (low.endsWith(".jar") || low.endsWith(".exe"))) return false; // sklauncher-fx.jar, TLauncher.exe
        for (File o : f.nested) if (inside(o, k)) return false; // тека іншого профілю чи тека з ними
        return true;
    }

    /** Значення з ini-подібного файлу (instance.cfg, prismlauncher.cfg), або null. */
    static String cfg(File f, String key) {
        if (!f.isFile()) return null;
        try (BufferedReader r = Files.newBufferedReader(f.toPath(), StandardCharsets.UTF_8)) {
            String line;
            while ((line = r.readLine()) != null) {
                if (line.startsWith(key + "=")) return line.substring(key.length() + 1).trim();
            }
        } catch (IOException e) {
            return null;
        }
        return null;
    }

    // ------------------------------------------------------------------ копіювання

    /** Що буде скопійовано (верхній рівень теки гри). */
    static List<File> sources(Found f, boolean worlds) {
        List<File> out = new ArrayList<File>();
        File[] kids = f.gameDir.listFiles();
        if (kids == null) return out;
        for (File k : kids) {
            String n = k.getName();
            if (!worlds && n.equals("saves")) continue;
            if (copyTop(f, k)) out.add(k);
        }
        return out;
    }

    /** Розмір того, що буде скопійовано. */
    public static long size(Found f, boolean worlds) {
        long s = 0;
        for (File k : sources(f, worlds)) s += com.gummer.launcher.util.Uninstall.size(k);
        return s;
    }

    private volatile double progress = -1;
    private volatile String status = "";

    @Override
    public double getProgress() {
        return progress;
    }

    @Override
    public String getStatus() {
        return status;
    }

    /** Створити збірку з знайденої. @return назва нової збірки */
    public String importOne(Launcher launcher, Found f, boolean worlds) throws Exception {
        return importInto(launcher.getInstancesDir(), f, worlds);
    }

    /** Як вище, у вказану теку збірок (для перевірки без лаунчера). */
    public String importInto(File instancesDir, Found f, boolean worlds) throws Exception {
        if (f.problem != null) throw new IOException("Cannot import " + f + ": " + f.problem);
        status = f.title;
        progress = -1;
        List<File> src = sources(f, worlds);
        long total = 0;
        for (File k : src) total += com.gummer.launcher.util.Uninstall.size(k);
        com.gummer.launcher.install.DiskSpaceException.check(instancesDir, total);

        String slug = f.title.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
        if (slug.isEmpty()) slug = "import";
        if (slug.length() > 40) slug = slug.substring(0, 40);
        String name = MrpackPacks.PREFIX + slug;
        File dir = new File(instancesDir, name);
        for (int n = 2; dir.exists(); n++) {
            name = MrpackPacks.PREFIX + slug + "-" + n;
            dir = new File(instancesDir, name);
        }
        final File content = new File(dir, "minecraft");
        content.mkdirs();
        try {
            final long all = Math.max(1, total);
            final long[] done = {0};
            for (File k : src) {
                final Path from = k.toPath();
                final Path to = new File(content, k.getName()).toPath();
                Files.walkFileTree(from, new SimpleFileVisitor<Path>() {
                    @Override
                    public FileVisitResult preVisitDirectory(Path d, BasicFileAttributes a) throws IOException {
                        if (a.isOther() || a.isSymbolicLink()) return FileVisitResult.SKIP_SUBTREE; // junction, symlink
                        Files.createDirectories(to.resolve(from.relativize(d).toString()));
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes a) throws IOException {
                        if (!a.isRegularFile()) return FileVisitResult.CONTINUE;
                        Path t = to.resolve(from.relativize(file).toString());
                        if (t.getParent() != null) Files.createDirectories(t.getParent());
                        Files.copy(file, t, StandardCopyOption.REPLACE_EXISTING, LinkOption.NOFOLLOW_LINKS);
                        done[0] += a.size();
                        progress = (double) done[0] / all;
                        status = f.title + ": " + from.getParent().relativize(file);
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult visitFileFailed(Path file, IOException e) {
                        log.info("Import: skipped " + file + ": " + e); // зайнятий чи без доступу: решта копіюється
                        return FileVisitResult.CONTINUE;
                    }
                });
            }
            ObjectNode info = MAPPER.createObjectNode();
            info.put("name", f.title.length() > InstanceCopies.MAX_TITLE ? f.title.substring(0, InstanceCopies.MAX_TITLE) : f.title);
            info.put("mc", f.mc);
            info.put("loader", f.loader);
            if (f.loaderVersion != null) info.put("loaderVersion", f.loaderVersion);
            info.put("importedFrom", f.source);
            info.put("importedPath", f.gameDir.getAbsolutePath());
            MAPPER.writerWithDefaultPrettyPrinter().writeValue(new File(dir, MrpackPacks.INFO), info);
            MrpackPacks.forgetInfo(dir);
        } catch (Exception e) {
            InstanceCopies.deleteQuietly(dir);
            throw e;
        }
        log.info("Imported " + f + " as " + name);
        return name;
    }
}
