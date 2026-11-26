/*
 * Copyright (C) 2021-2026 GummerCraft <http://gummercraft.fun>
 */

package com.gummer.launcher.manage;

import com.fasterxml.jackson.databind.JsonNode;
import com.skcraft.launcher.Instance;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.logging.Logger;

/**
 * 26.11.2026 (скарга гравця, Fabric 26.3: «Iris: бракує sodium»; власник: «1 і 3»). Два моди несумісні лише певними
 * версіями: Sodium 0.9.3 оголошує несумісним Iris до 1.12.0, а найновіший Iris для 26.3 це 1.11.7, який сам вказує
 * Sodium 0.9.2. Замість вимкнення одного з них лаунчер шукає на Modrinth версію одного з двох, з якою вони працюють
 * разом, і ставить її замість поточної (стара йде в {@link ModRollback}, її можна повернути). По черзі: версія, яку
 * вказав автор іншого мода; новіші версії мода, на який скаржаться; старіші версії мода, що скаржиться. Кожну
 * кандидатуру перевіряє по самому jar: несумісність в обидва боки, завантажувач збірки, версія гри, мінімальні версії,
 * яких вимагають інші моди. До {@link #MAX_DOWNLOADS} завантажень на пару. Не з потоку інтерфейсу.
 * <p>
 * Друге: гравець вимкнув мод, якого бракує іншому (вимкнув Sodium, і тепер «Iris: бракує sodium»). Звичайне
 * «Докачати» такий мод пропускало (файл же є), тож кнопка нічого не робила. Тепер вимкнений файл вмикається, а якщо
 * він несумісний, замінюється сумісною версією так само.
 */
public final class CompatFix {

    private static final Logger log = Logger.getLogger(CompatFix.class.getName());
    private static final String API = "https://api.modrinth.com/v2/";
    /** Скільки найбільше jar качаємо, шукаючи сумісну версію для однієї пари. */
    static final int MAX_DOWNLOADS = 6;
    /** Скільки версій одного мода переглядаємо (без завантаження) в кожен бік. */
    private static final int MAX_LISTED = 12;

    private CompatFix() {
    }

    /** Чи є серед проблем несумісність, яку можна спробувати прибрати іншою версією (без мережі). */
    public static boolean canTry(List<PreLaunchCheck.Issue> issues) {
        for (PreLaunchCheck.Issue is : issues) {
            if (is.kind == PreLaunchCheck.Kind.INCOMPATIBLE && is.by != null && !is.disable.isEmpty()) return true;
        }
        return false;
    }

    /**
     * Спершу вмикає вимкнені моди, яких бракує іншим (MISSING), потім для кожної несумісності, заданої версіями,
     * ставить сумісну версію. Повертає назви того, що змінено (для журналу). Нічого не вийшло: лишається як було.
     */
    public static List<String> fix(Instance instance, List<PreLaunchCheck.Issue> issues) throws InterruptedException {
        List<String> done = new ArrayList<String>();
        String game = ModrinthBrowse.gameVersionOf(instance);
        String[] loaders = ModrinthBrowse.loadersOf(ModrinthBrowse.Kind.MOD, instance);
        if (game == null || loaders == null || loaders.length == 0) return done;
        // 1. вимкнений мод, якого бракує: увімкнути (несумісність з ним, якщо є, далі як звичайна)
        boolean enabled = false;
        for (PreLaunchCheck.Issue is : issues) {
            if (is.kind != PreLaunchCheck.Kind.MISSING) continue;
            for (String id : is.detail.split(",\\s*")) {
                InstanceMods.Mod off = disabledProvider(instance, id.trim().toLowerCase(Locale.ROOT));
                if (off == null) continue;
                try {
                    InstanceMods.setEnabled(instance, off, true);
                    log.info("Compat fix: enabled " + off.name + ", " + is.mod + " needs " + id);
                    done.add(off.name);
                    enabled = true;
                } catch (IOException e) {
                    log.info("Compat fix: could not enable " + off.name + ": " + e.getMessage());
                }
            }
        }
        List<PreLaunchCheck.Issue> now = issues;
        if (enabled) {
            try {
                now = PreLaunchCheck.find(instance);
            } catch (RuntimeException e) {
                return done;
            }
        }
        // 2. несумісні пари
        Set<String> pairs = new HashSet<String>();
        for (PreLaunchCheck.Issue is : now) {
            if (is.kind != PreLaunchCheck.Kind.INCOMPATIBLE || is.by == null || is.disable.isEmpty()) continue;
            InstanceMods.Mod target = is.disable.get(0);
            if (!target.file.isFile() || !is.by.file.isFile()) continue; // уже замінено в іншій парі
            if (!pairs.add(is.by.file.getName() + "|" + target.file.getName())) continue;
            try {
                String r = fixPair(instance, is.by, target, game, loaders);
                if (r != null) done.add(r);
            } catch (IOException | RuntimeException e) {
                log.info("Compat fix failed for " + is.by.name + " / " + target.name + ": " + e);
            }
        }
        return done;
    }

    /** Вимкнений мод, що дає цей id, або null. */
    private static InstanceMods.Mod disabledProvider(Instance instance, String id) {
        if (id.isEmpty()) return null;
        for (InstanceMods.Mod m : InstanceMods.list(instance)) {
            if (m.enabled || m.locked) continue;
            if (ModInfo.of(m.file).provides.contains(id)) return m;
        }
        return null;
    }

    /** Одна кандидатура: чим замінити який файл. */
    private static final class Cand {
        final JsonNode version;
        /** true: замінити мод, що оголосив несумісність ({@code by}), false: той, з яким несумісно. */
        final boolean replaceBy;

        Cand(JsonNode version, boolean replaceBy) {
            this.version = version;
            this.replaceBy = replaceBy;
        }
    }

    /** Поставити сумісну версію одного з двох. Повертає «стара -> нова» або null, якщо такої не знайшлося. */
    private static String fixPair(Instance instance, InstanceMods.Mod by, InstanceMods.Mod target, String game, String[] loaders)
            throws IOException, InterruptedException {
        File dir = by.file.getParentFile();
        JsonNode byV = versionOf(by.file), tV = versionOf(target.file);
        if (byV == null && tV == null) {
            log.info("Compat fix: neither " + by.name + " nor " + target.name + " is on Modrinth");
            return fixPairCurse(instance, by, target, game, loaders);
        }
        String byPid = byV != null ? byV.path("project_id").asText(null) : null;
        String tPid = tV != null ? tV.path("project_id").asText(null) : null;
        List<Cand> cands = new ArrayList<Cand>();
        // а) версія, яку вказав автор другого мода (Iris 1.11.7 -> Sodium 0.9.2)
        if (tV != null && byPid != null) {
            String vid = pinned(tV, byPid);
            if (vid != null && !vid.equals(byV.path("id").asText(""))) addVersion(cands, vid, true);
        }
        if (byV != null && tPid != null) {
            String vid = pinned(byV, tPid);
            if (vid != null && !vid.equals(tV.path("id").asText(""))) addVersion(cands, vid, false);
        }
        // б) новіші версії мода, на який скаржаться (несумісність зазвичай «до такої версії»), від найстарішої з новіших
        if (tV != null && tPid != null) {
            List<JsonNode> newer = listed(tPid, game, loaders, tV, true);
            java.util.Collections.reverse(newer);
            for (JsonNode v : newer) cands.add(new Cand(v, false));
        }
        // в) старіші версії мода, що скаржиться, від найновішої зі старіших
        if (byV != null && byPid != null) {
            for (JsonNode v : listed(byPid, game, loaders, byV, false)) cands.add(new Cand(v, true));
        }
        LoaderVersion have = LoaderVersion.ofModsDir(dir);
        ModInfo byInfo = ModInfo.of(by.file), tInfo = ModInfo.of(target.file);
        Set<String> tried = new HashSet<String>();
        int downloads = 0;
        for (Cand c : cands) {
            if (downloads >= MAX_DOWNLOADS) break;
            String id = c.version.path("id").asText("");
            if (id.isEmpty() || !tried.add(id)) continue;
            JsonNode primary = ModUpdates.primaryFile(c.version);
            if (primary == null || !primary.path("filename").asText("").toLowerCase(Locale.ROOT).endsWith(".jar")) continue;
            downloads++;
            File part = ModUpdates.fetch(primary, dir);
            boolean used = false;
            try {
                ModInfo ci = ModInfo.of(part);
                ModInfo other = c.replaceBy ? tInfo : byInfo;
                ModInfo oldInfo = c.replaceBy ? byInfo : tInfo;
                InstanceMods.Mod old = c.replaceBy ? by : target;
                // гравець уже відкотив таку заміну після вильоту: сам лаунчер її більше не робить
                String need = "compat " + (other.id != null ? other.id : other.name);
                if (AutoFixLog.declined(instance, oldInfo.id, need)) {
                    log.info("Compat fix: " + oldInfo.id + " for " + need + " was rolled back before, not again");
                    continue;
                }
                String why = rejects(ci, other, have, game, instance, old);
                if (why != null) {
                    log.info("Compat fix: " + c.version.path("version_number").asText("?") + " does not fit: " + why);
                    continue;
                }
                String pid = c.replaceBy ? byPid : tPid;
                String oldVersion = (c.replaceBy ? byV : tV) != null ? (c.replaceBy ? byV : tV).path("version_number").asText("") : "";
                String fileName = primary.path("filename").asText("");
                File dest = new File(dir, fileName);
                if (!dest.equals(old.file) && dest.exists()) {
                    // така версія вже лежить поруч: лишаємо її, стару прибираємо
                    if (!ModRollback.keep(old.file, fileName, oldVersion, pid)) Files.delete(old.file.toPath());
                } else if (dest.equals(old.file)) {
                    ModRollback.keep(old.file, fileName, oldVersion, pid);
                    Files.move(part.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    used = true;
                } else {
                    Files.move(part.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    used = true;
                    if (!ModRollback.keep(old.file, fileName, oldVersion, pid)) Files.deleteIfExists(old.file.toPath());
                }
                if (pid != null) ModrinthBrowse.record(dir, pid, fileName);
                String r = old.name + " -> " + fileName;
                log.info("Compat fix: " + r + " (" + byInfo.name + " / " + tInfo.name + ")");
                // як автовиправлення: якщо гра після цього вилетить, вікно вильоту запропонує повернути попередню версію
                AutoFixLog.record(instance, java.util.Collections.singletonList(new AutoFixLog.Fix(oldInfo.id, need,
                        oldInfo.version != null ? oldInfo.version : oldVersion, ci.version != null ? ci.version : c.version.path("version_number").asText("?"),
                        new File(dir, fileName))));
                // залежності нової версії (вказані автором версії, див. ModrinthBrowse.dependencies)
                try {
                    Set<String> seen = new HashSet<String>();
                    if (byPid != null) seen.add(byPid);
                    if (tPid != null) seen.add(tPid);
                    ModrinthBrowse.dependencies(c.version, game, loaders, dir, null, null, null, seen, 0);
                } catch (IOException e) {
                    log.info("Compat fix: dependencies of " + fileName + ": " + e.getMessage());
                }
                return r;
            } finally {
                if (!used) part.delete();
            }
        }
        log.info("Compat fix: no compatible version of " + by.name + " or " + target.name + " on Modrinth (" + downloads + " checked)");
        return fixPairCurse(instance, by, target, game, loaders);
    }

    /**
     * 26.11.2026 (власник: «шукати і на Modrinth, і на CurseForge»): те саме з CurseForge для модів, знайдених там за
     * fingerprint: новіші файли мода, на який скаржаться, потім старіші файли мода, що скаржиться (за датою, під цю гру і
     * завантажувач). Кожен файл перевіряється по jar так само ({@link #rejects}); до {@link #MAX_DOWNLOADS} завантажень.
     * Версії, вказаної автором, CurseForge не дає, тому цього кроку тут немає.
     */
    private static String fixPairCurse(Instance instance, InstanceMods.Mod by, InstanceMods.Mod target, String game, String[] loaders)
            throws InterruptedException {
        com.skcraft.launcher.Launcher l = com.gummer.launcher.curseforge.CurseBrowse.shared();
        if (l == null) return null;
        java.util.Map<Long, int[]> match;
        long fpBy, fpT;
        try {
            fpBy = com.gummer.launcher.curseforge.CurseForge.fingerprint(by.file);
            fpT = com.gummer.launcher.curseforge.CurseForge.fingerprint(target.file);
            match = com.gummer.launcher.curseforge.CurseForge.matchFingerprints(l, java.util.Arrays.asList(fpBy, fpT));
        } catch (IOException e) {
            log.info("Compat fix: CurseForge unavailable: " + e.getMessage());
            return null;
        }
        int[] mBy = match.get(fpBy), mT = match.get(fpT);
        if ((mBy == null || mBy[1] <= 0) && (mT == null || mT[1] <= 0)) {
            log.info("Compat fix: neither " + by.name + " nor " + target.name + " is on CurseForge");
            return null;
        }
        List<Object[]> cands = new ArrayList<Object[]>(); // {hitId, PackVersion, replaceBy}
        try {
            if (mT != null && mT[1] > 0) {
                String hid = com.gummer.launcher.curseforge.CurseBrowse.ID_PREFIX + mT[1];
                List<ModrinthBrowse.PackVersion> newer = curseListed(l, hid, mT[0], game, loaders, true);
                java.util.Collections.reverse(newer);
                for (ModrinthBrowse.PackVersion v : newer) cands.add(new Object[]{hid, v, Boolean.FALSE});
            }
            if (mBy != null && mBy[1] > 0) {
                String hid = com.gummer.launcher.curseforge.CurseBrowse.ID_PREFIX + mBy[1];
                for (ModrinthBrowse.PackVersion v : curseListed(l, hid, mBy[0], game, loaders, false)) cands.add(new Object[]{hid, v, Boolean.TRUE});
            }
        } catch (IOException e) {
            log.info("Compat fix: CurseForge files unavailable: " + e.getMessage());
            return null;
        }
        File dir = by.file.getParentFile();
        LoaderVersion have = LoaderVersion.ofModsDir(dir);
        ModInfo byInfo = ModInfo.of(by.file), tInfo = ModInfo.of(target.file);
        int downloads = 0;
        for (Object[] c : cands) {
            if (downloads >= MAX_DOWNLOADS) break;
            String hid = (String) c[0];
            ModrinthBrowse.PackVersion v = (ModrinthBrowse.PackVersion) c[1];
            boolean replaceBy = (Boolean) c[2];
            File part = new File(dir, ".compat-" + v.id + ".part");
            boolean used = false;
            try {
                downloads++;
                String fileName = com.gummer.launcher.curseforge.CurseBrowse.downloadFile(l, hid, game, loaders, v.id, part);
                ModInfo ci = ModInfo.of(part);
                ModInfo other = replaceBy ? tInfo : byInfo, oldInfo = replaceBy ? byInfo : tInfo;
                InstanceMods.Mod old = replaceBy ? by : target;
                String need = "compat " + (other.id != null ? other.id : other.name);
                if (AutoFixLog.declined(instance, oldInfo.id, need)) continue;
                String why = rejects(ci, other, have, game, instance, old);
                if (why != null) {
                    log.info("Compat fix: CurseForge " + fileName + " does not fit: " + why);
                    continue;
                }
                String oldVersion = oldInfo.version != null ? oldInfo.version : "";
                File dest = new File(dir, fileName);
                if (!dest.equals(old.file) && dest.exists()) {
                    if (!ModRollback.keep(old.file, fileName, oldVersion, hid)) Files.delete(old.file.toPath());
                } else {
                    if (dest.equals(old.file)) ModRollback.keep(old.file, fileName, oldVersion, hid);
                    Files.move(part.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    used = true;
                    if (!dest.equals(old.file) && !ModRollback.keep(old.file, fileName, oldVersion, hid)) Files.deleteIfExists(old.file.toPath());
                }
                ModrinthBrowse.record(dir, hid, fileName);
                String r = old.name + " -> " + fileName;
                log.info("Compat fix: " + r + " from CurseForge (" + byInfo.name + " / " + tInfo.name + ")");
                AutoFixLog.record(instance, java.util.Collections.singletonList(new AutoFixLog.Fix(oldInfo.id, need,
                        oldVersion.isEmpty() ? "?" : oldVersion, ci.version != null ? ci.version : v.number, dest)));
                com.gummer.launcher.curseforge.CurseBrowse.dependenciesOf(dest, game, loaders, dir, null, null);
                return r;
            } catch (IOException e) {
                log.info("Compat fix: CurseForge file " + v.id + " unavailable: " + e.getMessage());
            } finally {
                if (!used) part.delete();
            }
        }
        log.info("Compat fix: no compatible version of " + by.name + " or " + target.name + " on CurseForge (" + downloads + " checked)");
        return null;
    }

    /** Файли мода CurseForge під цю гру, новіші чи старіші за поточний {@code fileId} за датою, від найновішого. */
    private static List<ModrinthBrowse.PackVersion> curseListed(com.skcraft.launcher.Launcher l, String hid, int fileId, String game,
                                                               String[] loaders, boolean newer) throws IOException, InterruptedException {
        ModrinthBrowse.Hit h = new ModrinthBrowse.Hit();
        h.id = hid;
        List<ModrinthBrowse.PackVersion> all = com.gummer.launcher.curseforge.CurseBrowse.fileChoices(l, ModrinthBrowse.Kind.MOD, h, game, loaders);
        String curDate = com.gummer.launcher.curseforge.CurseBrowse.fileDate(l, Integer.parseInt(hid.substring(com.gummer.launcher.curseforge.CurseBrowse.ID_PREFIX.length())),
                fileId, game, loaders);
        long at = ModUpdates.dateOf(curDate == null ? "" : curDate);
        List<ModrinthBrowse.PackVersion> rel = new ArrayList<ModrinthBrowse.PackVersion>(), beta = new ArrayList<ModrinthBrowse.PackVersion>();
        for (ModrinthBrowse.PackVersion v : all) {
            if (String.valueOf(fileId).equals(v.id)) continue;
            long d = ModUpdates.dateOf(v.date);
            if (at <= 0 || d <= 0 || (newer ? d <= at : d >= at)) continue;
            ("release".equals(v.type) ? rel : beta).add(v);
        }
        List<ModrinthBrowse.PackVersion> out = rel.isEmpty() ? beta : rel;
        if (newer) return out.size() > MAX_LISTED ? new ArrayList<ModrinthBrowse.PackVersion>(out.subList(out.size() - MAX_LISTED, out.size())) : out;
        return out.size() > MAX_LISTED ? new ArrayList<ModrinthBrowse.PackVersion>(out.subList(0, MAX_LISTED)) : out;
    }

    /**
     * Чому кандидатура не підходить, або null. {@code other}: мод, з яким вона має працювати; {@code old}: файл, який
     * вона замінить (його вимоги до інших модів більше не діють, а вимоги інших модів до неї діють).
     */
    static String rejects(ModInfo c, ModInfo other, LoaderVersion have, String game, Instance instance, InstanceMods.Mod old) {
        if (PreLaunchCheck.breaks(c, other)) return "it breaks " + other.name;
        if (PreLaunchCheck.breaks(other, c)) return other.name + " breaks it";
        if (have != null && !have.fits(c)) return "needs " + have.title() + " " + have.needOf(c);
        if (!c.mcFabric.isEmpty() && Boolean.FALSE.equals(McRange.fabric(c.mcFabric, game))) return "not for Minecraft " + game;
        String cv = McRange.plainModVersion(c.version);
        for (InstanceMods.Mod m : InstanceMods.list(instance)) {
            if (!m.enabled || m.file.equals(old.file)) continue;
            ModInfo mi = ModInfo.of(m.file);
            if (PreLaunchCheck.breaks(mi, c) || PreLaunchCheck.breaks(c, mi)) return "conflicts with " + mi.name;
            if (cv == null) continue;
            for (String id : c.provides) {
                String min = mi.minVersions.get(id);
                if (min != null && ModVersionFix.compare(cv, min) < 0) {
                    return mi.name + " needs " + id + " " + min;
                }
            }
        }
        return null;
    }

    /** Версія мода на Modrinth за sha1 файлу або null (не з Modrinth, нема мережі). */
    private static JsonNode versionOf(File f) throws InterruptedException {
        try {
            JsonNode v = ModrinthBrowse.getJson(API + "version_file/" + ModrinthBrowse.sha(f, "SHA-1") + "?algorithm=sha1");
            return v.path("project_id").asText("").isEmpty() ? null : v;
        } catch (IOException e) {
            return null;
        }
    }

    /** Версія проєкту {@code pid}, яку вказано в залежностях версії {@code v}, або null. */
    private static String pinned(JsonNode v, String pid) {
        for (JsonNode d : v.path("dependencies")) {
            String vid = d.path("version_id").asText("");
            if (pid.equals(d.path("project_id").asText("")) && vid.matches("[A-Za-z0-9]+")) return vid;
        }
        return null;
    }

    private static void addVersion(List<Cand> cands, String vid, boolean replaceBy) throws InterruptedException {
        try {
            cands.add(new Cand(ModrinthBrowse.getJson(API + "version/" + vid), replaceBy));
        } catch (IOException e) {
            log.info("Compat fix: version " + vid + " is unavailable: " + e.getMessage());
        }
    }

    /**
     * Версії проєкту під цю гру і завантажувач, новіші ({@code newer}) чи старіші за {@code current} за датою, від
     * найновішої, до {@link #MAX_LISTED}. Стабільні, а бети лише якщо стабільних немає.
     */
    private static List<JsonNode> listed(String pid, String game, String[] loaders, JsonNode current, boolean newer)
            throws InterruptedException {
        List<JsonNode> rel = new ArrayList<JsonNode>(), beta = new ArrayList<JsonNode>();
        StringBuilder l = new StringBuilder("[");
        for (int i = 0; i < loaders.length; i++) l.append(i > 0 ? "," : "").append('"').append(loaders[i]).append('"');
        long at = ModUpdates.dateOf(current.path("date_published").asText(""));
        String cur = current.path("id").asText("");
        try {
            JsonNode all = ModrinthBrowse.getJson(API + "project/" + pid + "/version?game_versions="
                    + ModrinthBrowse.enc("[\"" + game + "\"]") + "&loaders=" + ModrinthBrowse.enc(l.append(']').toString()));
            for (JsonNode v : all) {
                if (cur.equals(v.path("id").asText(""))) continue;
                long d = ModUpdates.dateOf(v.path("date_published").asText(""));
                if (at <= 0 || d <= 0 || (newer ? d <= at : d >= at)) continue;
                ("release".equals(v.path("version_type").asText("release")) ? rel : beta).add(v);
            }
        } catch (IOException e) {
            log.info("Compat fix: versions of " + pid + " are unavailable: " + e.getMessage());
        }
        List<JsonNode> out = rel.isEmpty() ? beta : rel;
        if (newer) {
            // найближчі до поточної в кінці списку (його розвертає виклик): лишаємо MAX_LISTED найстаріших з новіших
            return out.size() > MAX_LISTED ? new ArrayList<JsonNode>(out.subList(out.size() - MAX_LISTED, out.size())) : out;
        }
        return out.size() > MAX_LISTED ? new ArrayList<JsonNode>(out.subList(0, MAX_LISTED)) : out;
    }
}
