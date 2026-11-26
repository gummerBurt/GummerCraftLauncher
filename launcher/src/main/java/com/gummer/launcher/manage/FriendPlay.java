/*
 * Copyright (C) 2021-2026 GummerCraft <http://gummercraft.fun>
 */

package com.gummer.launcher.manage;

import com.skcraft.launcher.Instance;
import com.skcraft.launcher.util.SharedLocale;
import lombok.extern.java.Log;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 26.11.2026 (власник: «гру з другом дуже просять ... автоматизована гра з другом»): логіка помічника «Гра з другом».
 * Ставить мод під завантажувач і версію збірки, сам налаштовує його і, на Windows, дозволяє Java в брандмауері.
 * ТЗ і таблиці версій: «Лаунчер - робоча/НАБОРИ-МОДІВ.md».
 * <ul>
 * <li>одна мережа: LAN World Plug-n-Play ({@code mcwifipnp}, Fabric і Forge 1.15.2+, NeoForge 1.20+), де його немає,
 * Allow Offline Lan Join (Forge 1.8.9-1.12.2), далі EasyLAN (Forge 1.7.10);</li>
 * <li>через інтернет: e4all (форк e4mc з офлайн-акаунтами і голосом), запасний e4mc; Forge 1.7.10-1.16 лише e4mc Retro з
 * CurseForge, автор заборонив сторонні завантаження: лише сторінка мода;</li>
 * <li>голос: Simple Voice Chat обом гравцям.</li>
 * </ul>
 * Список перебирається по черзі: перший мод, для якого Modrinth має версію під цю гру і завантажувач, ставиться (так
 * таблиця версій не зашивається в код і не старіє).
 */
@Log
public final class FriendPlay {

    public enum Mode { LAN, INTERNET }

    /**
     * 26.11.2026: де нема LAN World Plug-n-Play, запасні з вимкненням перевірки ліцензії у «Відкрити для мережі»:
     * OfflineLAN (Fabric 1.14-26.3) і LAN Server Properties (Forge і NeoForge 1.20.5-1.21.3 на Modrinth, старіші на
     * CurseForge), далі як раніше. OfflineLAN раніше: на CurseForge є і стара Fabric-збірка LAN Server Properties 1.0
     * (проба 26.11, Fabric 1.14.4), з іншими налаштуваннями.
     */
    static final String[] LAN_MODS = {"mcwifipnp", "offlinelan", "lan-server-properties", "allow-offline-lan-join", "easylan"};
    static final String[] NET_MODS = {"e4all", "e4mc"};
    static final String VOICE = "simple-voice-chat";
    /** Id модів у jar, що вже дають те саме (не ставити другий). */
    static final String[] LAN_IDS = {"mcwifipnp", "allowofflinelanjoin", "allow_offline_lan_join", "easylan", "lanserverproperties", "lan_server_properties", "offlinelan", "customlan", "e4all"};
    static final String[] NET_IDS = {"e4all", "e4mc", "e4mc_minecraft", "e4mcretro", "e4mc_retro", "worldhost", "world-host", "opentopublic"};
    static final String[] VOICE_IDS = {"voicechat", "plasmovoice"};
    /** e4mc Retro (CurseForge, без сторонніх завантажень): Forge 1.7.10 - 1.16.x. */
    public static final String E4MC_RETRO_PAGE = "https://www.curseforge.com/minecraft/mc-mods/e4mc-retro";

    private FriendPlay() {
    }

    /** Що з помічником для цієї збірки. */
    public enum Availability {
        /** можна ставити моди */
        OK,
        /** ваніль без завантажувача: потрібна копія на Fabric */
        VANILLA,
        /** збірка ГаммерКрафту чи StalDayZ: свої моди не ставимо (StalDayZ кікає за моди поза білим списком) */
        LOCKED
    }

    public static Availability availability(Instance instance) {
        if (instance == null) return Availability.LOCKED;
        if (!instance.isBuiltIn() || InstanceMods.packModsLocked(instance)) return Availability.LOCKED;
        String section = com.skcraft.launcher.swing.InstanceTableModel.installedSectionOf(instance);
        if ("gummercraft".equals(section)) return Availability.LOCKED;
        if (instance.isVanilla()) return Availability.VANILLA;
        String[] l = ModrinthBrowse.loadersOf(ModrinthBrowse.Kind.MOD, instance);
        if (l == null || l.length == 0 || ModrinthBrowse.gameVersionOf(instance) == null) return Availability.LOCKED;
        return Availability.OK;
    }

    /** Що вийшло. */
    public static final class Result {
        /** назва поставленого (або вже наявного) мода для гри з другом, null якщо нема під цю версію */
        public String mod;
        /** slug цього мода (mcwifipnp, e4all, ...) */
        public String slug;
        public boolean alreadyHad;
        /** 26.11.2026: мод уже стояв до відкриття помічника (нічого не ставили, лише показуємо кроки) */
        public boolean preexisting;
        /** спосіб гри для {@link #existing} */
        public Mode mode;
        /** чи поставлено голос (якщо просили) */
        public String voice;
        public final List<String> installedFiles = new ArrayList<String>();
        public final List<String> notes = new ArrayList<String>();
        /** немає нічого автоматичного, але є сторінка мода (e4mc Retro) */
        public String manualPage;
        /** скільки світів налаштовано (LAN World Plug-n-Play) */
        public int worldsConfigured;
    }

    /** Поставити і налаштувати. Не з потоку інтерфейсу. */
    public static Result setup(Instance instance, Mode mode, boolean offlineFriend, boolean voice) throws InterruptedException {
        Result r = new Result();
        String game = ModrinthBrowse.gameVersionOf(instance);
        String[] loaders = ModrinthBrowse.loadersOf(ModrinthBrowse.Kind.MOD, instance);
        File dir = InstanceMods.modsDir(instance);
        dir.mkdirs();
        Set<String> have = installedIds(instance);
        String[] ids = mode == Mode.LAN ? LAN_IDS : NET_IDS;
        String already = firstPresent(have, ids);
        if (already != null) {
            r.mod = already;
            r.slug = slugOfId(already);
            r.alreadyHad = true;
        } else {
            for (String slug : mode == Mode.LAN ? LAN_MODS : NET_MODS) {
                String title = installOne(slug, game, loaders, dir, r);
                if (title != null) {
                    r.mod = title;
                    r.slug = slug;
                    break;
                }
            }
            if (r.mod == null && mode == Mode.INTERNET && "forge".equalsIgnoreCase(loaders[0]) && minorOf(game) > 0 && minorOf(game) <= 16) {
                r.manualPage = E4MC_RETRO_PAGE;
            }
        }
        if (voice) {
            String v = firstPresent(have, VOICE_IDS);
            r.voice = v != null ? v : installOne(VOICE, game, loaders, dir, r);
        }
        if (r.slug != null) configure(instance, r, offlineFriend);
        FriendReady.recheck(instance); // 26.11.2026: значок «готово до гри з другом» у списку і на «Робочому столі»
        log.info("Friend play " + mode + " for " + instance.getName() + ": " + r.mod + (r.alreadyHad ? " (had)" : "")
                + ", voice " + r.voice + ", files " + r.installedFiles + ", notes " + r.notes + ", worlds " + r.worldsConfigured);
        return r;
    }

    private static String installOne(String slug, String game, String[] loaders, File dir, Result r) throws InterruptedException {
        List<String> conflicts = new ArrayList<String>(), missing = new ArrayList<String>(), got = new ArrayList<String>();
        // 26.11.2026: Modrinth, а якщо там немає під цю збірку, той самий мод з CurseForge (ModSources)
        ModSources.Found h;
        try {
            h = ModSources.find(slug, ModrinthBrowse.Kind.MOD, game, loaders);
            if (h == null) return null; // немає версії під цю гру і завантажувач: наступний у списку
            if (ModrinthBrowse.looksInstalled(dir, h.hit.id, h.hit.slug)) return h.title();
            Set<String> before = names(dir);
            try {
                ModSources.install(h, ModrinthBrowse.Kind.MOD, game, loaders, dir, null, missing, conflicts);
            } finally {
                for (String f : names(dir)) if (!before.contains(f)) got.add(f);
            }
        } catch (IOException e) {
            for (String f : got) new File(dir, f).delete();
            return null;
        }
        if (!conflicts.isEmpty() || !missing.isEmpty()) {
            for (String f : got) new File(dir, f).delete();
            r.notes.add(h.title() + ": " + (conflicts.isEmpty() ? SharedLocale.tr("fps.mods.noDeps")
                    : SharedLocale.tr("fps.mods.conflict", String.join(", ", conflicts))));
            return null;
        }
        r.installedFiles.addAll(got);
        return h.title();
    }

    // ------------------------------------------------------------------ автоконфіг

    private static void configure(Instance instance, Result r, boolean offlineFriend) {
        File game = instance.getContentDir();
        try {
            if ("mcwifipnp".equals(r.slug)) r.worldsConfigured = configureWifiPnp(new File(game, "saves"), offlineFriend);
            else if ("e4all".equals(r.slug)) configureE4all(new File(game, "config"), offlineFriend);
            else if ("lan-server-properties".equals(r.slug)) configureLsp(new File(game, "config"), offlineFriend);
        } catch (IOException e) {
            log.warning("Friend play config failed: " + e);
        }
    }

    /**
     * LAN World Plug-n-Play тримає налаштування в кожному світі ({@code saves/<світ>/mcwifipnp.json}, Gson) і за
     * замовчуванням перевіряє ліцензію ({@code OnlineMode: true}). Друг з локальним акаунтом тоді не зайде, тож у кожному
     * наявному світі ставимо {@code OnlineMode} як треба. Наявний файл правиться лише в цьому полі; нового світу після
     * цього це не торкнеться (у грі той самий перемикач у вікні «Відкрити для мережі»).
     */
    static int configureWifiPnp(File saves, boolean offlineFriend) throws IOException {
        File[] worlds = saves.listFiles(File::isDirectory);
        if (worlds == null) return 0;
        com.fasterxml.jackson.databind.ObjectMapper m = new com.fasterxml.jackson.databind.ObjectMapper();
        int n = 0;
        for (File w : worlds) {
            if (!new File(w, "level.dat").isFile()) continue;
            File f = new File(w, "mcwifipnp.json");
            com.fasterxml.jackson.databind.node.ObjectNode o;
            if (f.isFile()) {
                com.fasterxml.jackson.databind.JsonNode j = m.readTree(f);
                if (!(j instanceof com.fasterxml.jackson.databind.node.ObjectNode)) continue;
                o = (com.fasterxml.jackson.databind.node.ObjectNode) j;
            } else {
                // усі поля, як Config мода за замовчуванням (Gson лишив би пропущені нулями: порт 0)
                o = m.createObjectNode();
                o.put("port", 25565);
                o.put("maxPlayers", 8);
                o.put("GameMode", "survival");
                o.put("motd", w.getName());
                o.put("AllPlayersCheats", false);
                o.put("Whitelist", false);
                o.put("UseUPnP", true);
                o.put("AllowCommands", false);
                o.put("EnableUUIDFixer", false);
                o.putArray("ForceOfflinePlayers");
                o.put("PvP", true);
                o.put("CopyToClipboard", true);
            }
            o.put("OnlineMode", !offlineFriend);
            Files.write(f.toPath(), m.writerWithDefaultPrettyPrinter().writeValueAsBytes(o));
            n++;
        }
        return n;
    }

    /**
     * 26.11.2026: LAN Server Properties тримає вибір вікна «Відкрити для мережі» в {@code config/lsp.json} (Gson,
     * {@code rikka.lanserverproperties.Preferences}, перевірено javap 1.13.2) і за замовчуванням вантажить його
     * ({@code enablePreference}). Друг без ліцензії: {@code onlineMode = false}, {@code fixUUID} лишається (гравці з
     * ліцензією зберігають свої UUID і речі). Наявний файл правиться лише в цих полях.
     */
    static void configureLsp(File config, boolean offlineFriend) throws IOException {
        File f = new File(config, "lsp.json");
        com.fasterxml.jackson.databind.ObjectMapper m = new com.fasterxml.jackson.databind.ObjectMapper();
        com.fasterxml.jackson.databind.node.ObjectNode o = null;
        if (f.isFile()) {
            com.fasterxml.jackson.databind.JsonNode j = m.readTree(f);
            if (j instanceof com.fasterxml.jackson.databind.node.ObjectNode) o = (com.fasterxml.jackson.databind.node.ObjectNode) j;
        }
        if (o == null) {
            // усі поля, як Preferences мода за замовчуванням (Gson лишив би пропущені нулями: порт 0)
            o = m.createObjectNode();
            o.put("gameMode", "SURVIVAL");
            o.put("allowCheat", false);
            o.put("defaultPort", 25565);
            o.put("fixUUID", true);
            o.put("allowPVP", true);
            o.put("maxPlayer", 8);
            o.putArray("playersAlwaysOffline");
        }
        o.put("enablePreference", true);
        o.put("onlineMode", !offlineFriend);
        config.mkdirs();
        Files.write(f.toPath(), m.writerWithDefaultPrettyPrinter().writeValueAsBytes(o));
    }

    /** e4all: один файл на збірку, {@code config/e4all/e4all.toml}, ключ {@code offlineMode} (за замовчуванням false). */
    static void configureE4all(File config, boolean offlineFriend) throws IOException {
        File f = new File(new File(config, "e4all"), "e4all.toml");
        f.getParentFile().mkdirs();
        String line = "offlineMode = " + offlineFriend;
        if (f.isFile()) {
            List<String> lines = new ArrayList<String>(Files.readAllLines(f.toPath(), StandardCharsets.UTF_8));
            boolean done = false;
            for (int i = 0; i < lines.size(); i++) {
                if (lines.get(i).trim().matches("offlineMode\\s*=.*")) {
                    lines.set(i, line);
                    done = true;
                }
            }
            if (!done) lines.add(line);
            Files.write(f.toPath(), lines, StandardCharsets.UTF_8);
        } else {
            Files.write(f.toPath(), Arrays.asList("# GummerCraft Launcher: гра з другом", line), StandardCharsets.UTF_8);
        }
    }

    // ------------------------------------------------------------------ брандмауер Windows

    public static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    public enum Firewall { ALLOWED, BLOCKED, NOT_SET, UNKNOWN }

    /** Наше правило для Java (одне на всі javaw лаунчера). */
    static final String RULE = "GummerCraft - game with a friend";

    /** Усі javaw.exe, якими лаунчер запускає гру: рантайми лаунчера і Java самого лаунчера. */
    public static List<File> javaws(File launcherBaseDir) {
        List<File> out = new ArrayList<File>();
        collect(new File(launcherBaseDir, "runtime"), out, 0);
        File own = new File(System.getProperty("java.home", ""), "bin" + File.separator + "javaw.exe");
        if (own.isFile() && !out.contains(own)) out.add(own);
        return out;
    }

    private static void collect(File d, List<File> out, int depth) {
        if (depth > 6) return;
        File[] fs = d.listFiles();
        if (fs == null) return;
        for (File f : fs) {
            if (f.isDirectory()) collect(f, out, depth + 1);
            else if (f.getName().equalsIgnoreCase("javaw.exe")) out.add(f.getAbsoluteFile());
        }
    }

    /**
     * Стан для цих javaw. Не з потоку інтерфейсу: PowerShell стартує секунду-дві. Читаємо через COM
     * {@code HNetCfg.FwPolicy2}: {@code Get-NetFirewallApplicationFilter} без прав адміністратора дає «Access is denied»
     * (перевірено 26.11), а вивід {@code netsh} перекладено мовою Windows. Direction 1 = вхідні, Action 0 = блокувати,
     * 1 = дозволити.
     */
    public static Firewall firewallState(List<File> javaws) {
        if (!isWindows() || javaws.isEmpty()) return Firewall.UNKNOWN;
        StringBuilder ps = new StringBuilder("$ProgressPreference='SilentlyContinue';$p=@(");
        for (int i = 0; i < javaws.size(); i++) ps.append(i == 0 ? "" : ",").append(quote(javaws.get(i).getAbsolutePath().toLowerCase(Locale.ROOT)));
        ps.append(");$b=0;$a=0;(New-Object -ComObject HNetCfg.FwPolicy2).Rules | Where-Object { $_.Direction -eq 1 -and $_.Enabled")
                .append(" -and $_.ApplicationName -and $p -contains [Environment]::ExpandEnvironmentVariables($_.ApplicationName).ToLower() }")
                .append(" | ForEach-Object { if ($_.Action -eq 0) { $b++ } elseif ($_.Action -eq 1) { $a++ } };")
                .append("Write-Output \"$b $a\"");
        String out = powershell(ps.toString(), false);
        if (out == null) return Firewall.UNKNOWN;
        // перший запуск PowerShell дописує службовий CLIXML («Preparing modules for first use»): шукаємо сам рядок «N N»
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(?m)^\\s*(\\d+) (\\d+)\\s*$").matcher(out);
        if (!m.find()) {
            log.warning("Friend play firewall state: unexpected output " + out.replaceAll("\\s+", " "));
            return Firewall.UNKNOWN;
        }
        int b = Integer.parseInt(m.group(1)), a = Integer.parseInt(m.group(2));
        return b > 0 ? Firewall.BLOCKED : a > 0 ? Firewall.ALLOWED : Firewall.NOT_SET;
    }

    /**
     * Дозволити ці javaw у брандмауері (вікно адміністратора Windows): прибрати правила «Блокувати» саме для них (їх
     * Windows пише, коли гравець натиснув «Скасувати»; блокування сильніше за дозвіл) і додати наш дозвіл для всіх
     * профілів мережі (домашня Wi-Fi у Windows 11 часто «Загальнодоступна»). Повертає true, якщо після цього дозволено.
     */
    public static boolean firewallAllow(List<File> javaws) {
        if (!isWindows() || javaws.isEmpty()) return false;
        StringBuilder s = new StringBuilder("$ErrorActionPreference='SilentlyContinue';$p=@(");
        for (int i = 0; i < javaws.size(); i++) s.append(i == 0 ? "" : ",").append(quote(javaws.get(i).getAbsolutePath()));
        s.append(");$l=$p | ForEach-Object { $_.ToLower() };")
                .append("Get-NetFirewallApplicationFilter | Where-Object { $_.Program -and $l -contains $_.Program.ToLower() }")
                .append(" | Get-NetFirewallRule | Where-Object { $_.Action -eq 'Block' } | Remove-NetFirewallRule;")
                .append("Remove-NetFirewallRule -DisplayName ").append(quote(RULE)).append(";")
                .append("foreach ($j in $p) { New-NetFirewallRule -DisplayName ").append(quote(RULE))
                .append(" -Direction Inbound -Program $j -Action Allow -Profile Any | Out-Null }");
        String inner = Base64.getEncoder().encodeToString(s.toString().getBytes(StandardCharsets.UTF_16LE));
        String outer = "try { $pr = Start-Process powershell -Verb RunAs -Wait -PassThru -WindowStyle Hidden -ArgumentList '-NoProfile','-ExecutionPolicy','Bypass','-EncodedCommand','"
                + inner + "'; Write-Output ('ok ' + $pr.ExitCode) } catch { Write-Output 'cancel' }";
        String out = powershell(outer, true);
        log.info("Friend play firewall: " + out);
        return firewallState(javaws) == Firewall.ALLOWED;
    }

    private static String quote(String s) {
        return "'" + s.replace("'", "''") + "'";
    }

    private static String powershell(String script, boolean longWait) {
        try {
            String enc = Base64.getEncoder().encodeToString(script.getBytes(StandardCharsets.UTF_16LE));
            ProcessBuilder pb = new ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass", "-EncodedCommand", enc);
            pb.redirectErrorStream(true);
            Process p = pb.start();
            p.getOutputStream().close();
            byte[] buf = readAll(p.getInputStream());
            if (!p.waitFor(longWait ? 300 : 60, TimeUnit.SECONDS)) {
                p.destroyForcibly();
                return null;
            }
            return new String(buf, java.nio.charset.Charset.defaultCharset());
        } catch (Exception e) {
            log.warning("powershell failed: " + e);
            return null;
        }
    }

    private static byte[] readAll(java.io.InputStream in) throws IOException {
        java.io.ByteArrayOutputStream b = new java.io.ByteArrayOutputStream();
        byte[] t = new byte[4096];
        int n;
        while ((n = in.read(t)) > 0) b.write(t, 0, n);
        return b.toByteArray();
    }

    // ------------------------------------------------------------------ допоміжне

    /**
     * 26.11.2026: чи вже стоїть мод для гри з другом (LAN чи через інтернет). Читає моди збірки, не з потоку інтерфейсу
     * для великих збірок.
     */
    public static boolean hasFriendMod(Instance instance) {
        Set<String> have = installedIds(instance);
        return firstPresent(have, LAN_IDS) != null || firstPresent(have, NET_IDS) != null;
    }

    /**
     * 26.11.2026 (власник: кнопка «Видалити моди мультиплеєра», «про всяк»): моди для гри з другом у збірці, увімкнені
     * чи вимкнені: LAN (LAN World Plug-n-Play, OfflineLAN, LAN Server Properties, ...), через інтернет (e4all, e4mc,
     * World Host, ...) і голосовий чат (Simple Voice Chat, Plasmo Voice). Моди модпака під замком не беруться. Бібліотеки,
     * які помічник поставив як залежність (Fabric API тощо), лишаються: їх можуть потребувати інші моди. Читає моди, не з
     * потоку інтерфейсу.
     */
    public static List<InstanceMods.Mod> friendMods(Instance instance) {
        Set<String> ids = new HashSet<String>();
        ids.addAll(Arrays.asList(LAN_IDS));
        ids.addAll(Arrays.asList(NET_IDS));
        ids.addAll(Arrays.asList(VOICE_IDS));
        List<InstanceMods.Mod> out = new ArrayList<InstanceMods.Mod>();
        for (InstanceMods.Mod m : InstanceMods.list(instance)) {
            if (m.locked) continue;
            Set<String> have = new HashSet<String>(ModInfo.of(m.file).provides);
            have.add(m.name.toLowerCase(Locale.ROOT).replaceAll("[-_ ]?\\d.*$", ""));
            boolean hit = false;
            for (String h : have) for (String id : ids) if (sameMod(h, id)) hit = true;
            if (hit) out.add(m);
        }
        return out;
    }

    /** Видалити ці моди з диска. Повертає скільки файлів видалено. */
    public static int removeFriendMods(Instance instance, List<InstanceMods.Mod> mods) {
        int n = 0;
        List<String> gone = new ArrayList<String>();
        for (InstanceMods.Mod m : mods) {
            if (m.file.isFile() && m.file.delete()) {
                n++;
                gone.add(m.file.getName());
            }
        }
        log.info("Friend play mods removed from " + instance.getName() + ": " + gone);
        FriendReady.recheck(instance);
        return n;
    }

    private static Set<String> installedIds(Instance instance) {
        Set<String> have = new HashSet<String>();
        for (InstanceMods.Mod m : InstanceMods.list(instance)) {
            if (m.file.getName().endsWith(InstanceMods.DISABLED)) continue;
            have.addAll(ModInfo.of(m.file).provides);
            have.add(m.file.getName().toLowerCase(Locale.ROOT).replaceAll("[-_ ]?\\d.*$", ""));
        }
        return have;
    }

    /**
     * Перший id зі списку, що є в збірці. 26.11.2026 (власник: «на сталдейз тест іконка не поставилась»): збіг і з
     * варіантами того самого мода, «id_щось» чи «id-щось» (e4all для Forge 1.16 це {@code e4all_legacy}); раніше лише
     * точний id, тож такий мод не впізнавався: ні значка, ні «вже є», і помічник ставив би його вдруге.
     */
    private static String firstPresent(Set<String> have, String[] ids) {
        for (String id : ids) {
            for (String h : have) if (sameMod(h, id)) return h;
        }
        return null;
    }

    private static boolean sameMod(String have, String id) {
        return have.equals(id) || have.startsWith(id + "_") || have.startsWith(id + "-");
    }

    /**
     * 26.11.2026 (власник: «якшо вже моди поставлені, нашо показувати те ж діалогове вікно?»): що вже стоїть у
     * збірці, щоб помічник одразу показав кроки в грі. Спосіб: через інтернет, якщо є мод для цього (e4all, e4mc, ...),
     * інакше поруч. null: мода для гри з другом немає. Читає моди, не для великих збірок на потоці інтерфейсу.
     */
    public static Result existing(Instance instance) {
        Set<String> have = installedIds(instance);
        String net = firstPresent(have, NET_IDS), lan = firstPresent(have, LAN_IDS);
        String id = net != null ? net : lan;
        if (id == null) return null;
        Result r = new Result();
        r.slug = slugOfId(id);
        r.mod = titleOf(r.slug);
        r.alreadyHad = true;
        r.preexisting = true;
        r.mode = net != null ? Mode.INTERNET : Mode.LAN;
        r.voice = firstPresent(have, VOICE_IDS);
        return r;
    }

    /** Назва мода для людей. */
    static String titleOf(String slug) {
        if ("mcwifipnp".equals(slug)) return "LAN World Plug-n-Play";
        if ("lan-server-properties".equals(slug)) return "LAN Server Properties";
        if ("offlinelan".equals(slug)) return "OfflineLAN";
        if ("worldhost".equals(slug) || "world-host".equals(slug)) return "World Host";
        return slug;
    }

    private static String slugOfId(String id) {
        if (id.startsWith("e4all")) return "e4all";
        if (id.startsWith("e4mc")) return "e4mc";
        if (id.equals("mcwifipnp")) return "mcwifipnp";
        if (id.startsWith("offlinelan")) return "offlinelan";
        if (id.startsWith("lanserverproperties") || id.startsWith("lan_server_properties")) return "lan-server-properties";
        return id;
    }

    private static Set<String> names(File dir) {
        Set<String> out = new HashSet<String>();
        String[] n = dir.list();
        if (n != null) out.addAll(Arrays.asList(n));
        return out;
    }

    static int minorOf(String game) {
        if (game == null) return 0;
        String[] p = game.split("\\.");
        if (p.length < 2 || !"1".equals(p[0])) return p.length > 0 && p[0].matches("\\d+") && !"1".equals(p[0]) ? 100 : 0;
        try {
            return Integer.parseInt(p[1].replaceAll("[^0-9].*$", ""));
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
