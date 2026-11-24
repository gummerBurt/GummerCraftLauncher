/*
 * Copyright (C) 2021-2026 GummerCraft <http://gummercraft.fun>
 */

package com.gummer.launcher.modrinth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.gummer.launcher.manage.ModrinthBrowse;
import com.gummer.launcher.util.Json;
import com.skcraft.concurrency.ProgressObservable;
import com.skcraft.launcher.Instance;
import com.skcraft.launcher.Launcher;
import com.skcraft.launcher.util.HttpRequest;
import com.skcraft.launcher.util.SharedLocale;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * 17.11.2026 (власник: оновлення модпаків з Modrinth, «може не автооновлювати, а пропонувати?»). Оновлення збірки
 * {@code mrpack-*} до новішої версії того самого модпака, лише на вибір гравця і лише в межах тієї ж версії Minecraft
 * (інакше світи можуть зіпсуватись: нову гру ставлять окремою збіркою).
 *
 * <p>Порівнюються три стани кожного файлу: як його поставив модпак ({@link MrpackPacks#FILES}), як він лежить зараз і як
 * він у новій версії. Файл, який гравець не міняв, оновлюється чи прибирається разом з модпаком. Файл, який гравець
 * змінив (конфіг, свою версію мода), чи свій мод гравця на місці мода модпака лишаються, якщо гравець не вибере «взяти з
 * модпака» (конфлікти показуються списком перед оновленням). Видалене гравцем не повертається. Свої моди, світи,
 * скріншоти й налаштування поза модпаком не чіпаються. Вимкнений мод модпака лишається вимкненим і в новій версії.</p>
 *
 * <p>Перед змінами все, що перезаписується чи прибирається, кладеться в {@value #BACKUP}, а з ним старі
 * {@code mrpack.json} і {@code mrpack-files.json}: «Повернути попередню версію» відновлює збірку як була (лише останнє
 * оновлення). Нові файли спершу качаються й звіряються (sha1, sha512) у тимчасову теку, і лише потім щось міняється.</p>
 */
public final class MrpackUpdate implements ProgressObservable {

    private static final Logger log = Logger.getLogger(MrpackUpdate.class.getName());
    private static final ObjectMapper MAPPER = Json.newMapper();
    public static final String BACKUP = "update-backup";
    private static final String TMP = ".update-tmp";
    private static final long CHECK_TTL = 30 * 60 * 1000L;
    private static final Map<String, Object[]> CHECKS = new ConcurrentHashMap<String, Object[]>();

    /** Що відомо про оновлення збірки. */
    public static final class Check {
        /** Поточна версія на Modrinth (null: не вдалося визначити, наприклад збірку імпортовано з файлу). */
        public ModrinthBrowse.PackVersion current;
        /** Новіша версія для тієї ж гри і завантажувача, або null. */
        public ModrinthBrowse.PackVersion newest;
        /** Новіша версія модпака для іншої версії Minecraft (ставиться окремою збіркою), або null. */
        public ModrinthBrowse.PackVersion otherGame;
        /** Номер версії, записаний у збірці. */
        public String currentNumber;
    }

    /** Одна зміна плану. */
    public static final class Change {
        public enum Type {
            /** Файл модпака оновлюється (можливо, під новою назвою). */
            UPDATE,
            /** Новий файл модпака. */
            ADD,
            /** Модпак цей файл більше не має, гравець його не міняв. */
            REMOVE,
            /** Гравець змінив файл, а модпак теж його міняє (чи прибирає). За замовчуванням лишається версія гравця. */
            CHANGED_BY_YOU,
            /** Гравець поставив свою версію мода модпака. За замовчуванням лишається його версія. */
            OWN_VERSION,
            /** Гравець прибрав цей файл модпака: не повертаємо. */
            DELETED_BY_YOU
        }

        public final Type type;
        /** Шлях у теці гри зараз (для ADD той самий, що {@link #newPath}). */
        public final String path;
        /** Шлях у новій версії, або null (прибирається). */
        public final String newPath;
        final boolean disabled;
        /** Для CHANGED_BY_YOU і OWN_VERSION: true - взяти з модпака (гравець вирішує в списку перед оновленням). */
        public boolean takePack;

        Change(Type type, String path, String newPath, boolean disabled) {
            this.type = type;
            this.path = path;
            this.newPath = newPath;
            this.disabled = disabled;
        }

        public boolean isConflict() {
            return type == Type.CHANGED_BY_YOU || type == Type.OWN_VERSION;
        }
    }

    /** Файл нової версії: з адреси (index) чи з overrides архіву. */
    static final class Entry {
        String path, sha1, sha512, project, zipName;
        final List<String> urls = new ArrayList<String>();
    }

    /** Що зміниться (рахується до будь-яких змін на диску). */
    public static final class Plan {
        public final List<Change> changes = new ArrayList<Change>();
        public ModrinthBrowse.PackVersion target;
        public String fromNumber;
        /** 18.11.2026: що змінилось від поточної версії до нової (усі проміжні версії, простий текст), або null. */
        public String changelog;
        File packFile;
        JsonNode index;
        final Map<String, Entry> entries = new LinkedHashMap<String, Entry>();

        public int count(Change.Type type) {
            int n = 0;
            for (Change c : changes) if (c.type == type) n++;
            return n;
        }

        public List<Change> conflicts() {
            List<Change> out = new ArrayList<Change>();
            for (Change c : changes) if (c.isConflict()) out.add(c);
            return out;
        }

        /** Прибрати тимчасовий файл модпака (гравець передумав). */
        public void discard() {
            if (packFile != null) packFile.delete();
        }
    }

    private final Launcher launcher;
    private final Instance instance;
    private final MrpackPacks download = new MrpackPacks();
    private volatile boolean downloading;
    private volatile String status = "";
    private volatile double progress = -1;

    public MrpackUpdate(Launcher launcher, Instance instance) {
        this.launcher = launcher;
        this.instance = instance;
    }

    /** Збірку можна оновлювати: модпак Modrinth, поставлений зі сторінки Modrinth (відомий проєкт). */
    public static boolean canUpdate(Instance instance) {
        if (instance == null || !instance.isMrpack() || !instance.isLocal()) return false;
        JsonNode info = MrpackPacks.info(instance);
        // 1.40.0: збірки CurseForge (source curseforge, projectId «cf-…») оновлюються не через Modrinth
        return info != null && info.path("projectId").asText(null) != null && info.path("mc").asText(null) != null
                && !"curseforge".equals(info.path("source").asText(null));
    }

    /** Остання перевірка з пам'яті (без мережі), або null. */
    public static Check cached(Instance instance) {
        if (instance == null) return null;
        Object[] c = CHECKS.get(instance.getDir().getAbsolutePath());
        return c != null && System.currentTimeMillis() - (Long) c[0] < CHECK_TTL ? (Check) c[1] : null;
    }

    /** Після оновлення чи відкату: перевірити заново. */
    public static void forget(Instance instance) {
        if (instance != null) CHECKS.remove(instance.getDir().getAbsolutePath());
    }

    /**
     * Перевірити, чи є новіша версія (мережа). Порівнюється з точною версією Modrinth, якщо її записано
     * ({@code mrVersionId}), інакше за номером версії. Бета пропонується, лише якщо гравець уже на беті.
     */
    public static Check check(Instance instance) throws IOException, InterruptedException {
        Check cached = cached(instance);
        if (cached != null) return cached;
        JsonNode info = MrpackPacks.info(instance);
        if (info == null) return null;
        String project = info.path("projectId").asText(null);
        String mc = info.path("mc").asText(null);
        String loader = info.path("loader").asText("");
        if (project == null || mc == null) return null;
        List<ModrinthBrowse.PackVersion> all = ModrinthBrowse.modpackVersions(project);
        String mrId = info.path("mrVersionId").asText(null);
        String number = info.path("versionNumber").asText(info.path("versionId").asText(null));
        Check c = new Check();
        c.currentNumber = number;
        int cur = -1;
        for (int i = 0; i < all.size() && cur < 0; i++) {
            ModrinthBrowse.PackVersion v = all.get(i);
            if (mrId != null ? mrId.equals(v.id) : number != null && number.equals(v.number) && loader.equals(v.loader()) && v.games.contains(mc)) cur = i;
        }
        c.current = cur >= 0 ? all.get(cur) : null;
        boolean beta = c.current != null && !"release".equals(c.current.type);
        // список від новішої: усе до поточної новіше за неї
        for (int i = 0; i < (cur >= 0 ? cur : all.size()); i++) {
            ModrinthBrowse.PackVersion v = all.get(i);
            if (!beta && !"release".equals(v.type)) continue;
            if (!loader.equals(v.loader())) continue;
            if (number != null && number.equals(v.number)) break;
            if (v.games.contains(mc)) {
                if (c.newest == null) c.newest = v;
            } else if (c.otherGame == null && c.newest == null) {
                c.otherGame = v;
            }
        }
        // збірка, поставлена до 17.11.2026 ~21:40, не знає свого id версії на Modrinth, а versionId у старому mrpack.json
        // це те, що автор вписав у свій index (часто просто «1.20.1», а в «2.3-Fix» Zombie Invade просто «2.3»): за номером
        // поточну версію або не знайти, або знайти не ту. Тож для таких збірок новішою вважається лише версія, опублікована
        // пізніше, ніж збірку поставлено (17.11 ~23:15: раніше лише коли номер не знайдено, і Zombie Invade 2.3-Fix
        // показував стрілку «є оновлення до 2.3-Fix», хоча 2.3-Fix і стоїть)
        if (mrId == null) {
            long installedAt = new File(instance.getDir(), MrpackPacks.INFO).lastModified();
            if (c.newest != null && !publishedAfter(c.newest, installedAt)) c.newest = null;
            if (c.otherGame != null && !publishedAfter(c.otherGame, installedAt)) c.otherGame = null;
        }
        CHECKS.put(instance.getDir().getAbsolutePath(), new Object[]{System.currentTimeMillis(), c});
        return c;
    }

    /**
     * 18.11.2026 (власник: «список змін модпака перед оновленням»). Тексти змін від автора для всіх версій новіших за
     * поставлену до {@code target} включно (тієї ж гри і завантажувача), від новішої, простим текстом. null: автор
     * нічого не писав.
     */
    public static String changelog(Instance instance, ModrinthBrowse.PackVersion target) throws IOException, InterruptedException {
        JsonNode info = MrpackPacks.info(instance);
        if (info == null) return null;
        String project = info.path("projectId").asText(null);
        String mc = info.path("mc").asText("");
        String loader = info.path("loader").asText("");
        if (project == null) return null;
        Check c = check(instance);
        long installedAt = new File(instance.getDir(), MrpackPacks.INFO).lastModified();
        StringBuilder out = new StringBuilder();
        boolean started = false;
        int n = 0;
        for (ModrinthBrowse.PackVersion v : ModrinthBrowse.modpackVersions(project)) {
            if (!started) {
                if (!v.id.equals(target.id)) continue;
                started = true;
            }
            if (c != null && c.current != null ? v.id.equals(c.current.id) : !publishedAfter(v, installedAt)) break;
            if (!loader.equals(v.loader()) || !v.games.contains(mc)) continue;
            String text = plain(v.changelog);
            out.append(out.length() > 0 ? "\n\n" : "").append(v.number);
            if (v.date != null && v.date.length() >= 10) out.append("  (").append(v.date, 8, 10).append('.').append(v.date, 5, 7).append('.').append(v.date, 0, 4).append(')');
            out.append('\n').append(text.isEmpty() ? SharedLocale.tr("mrpack.update.noChangelog") : text);
            if (++n >= 15 || out.length() > 20000) break;
        }
        return out.length() == 0 ? null : out.toString();
    }

    /** Markdown автора простим текстом: без заголовків, жирного, посилань і HTML. */
    public static String plain(String md) {
        if (md == null) return "";
        String s = md.replace("\r", "");
        s = s.replaceAll("(?s)<!--.*?-->", "");
        s = s.replaceAll("<br\\s*/?>", "\n");
        s = s.replaceAll("<[^>]{1,200}>", "");
        s = s.replaceAll("!\\[[^\\]]*\\]\\([^)]*\\)", "");
        s = s.replaceAll("\\[([^\\]]*)\\]\\([^)]*\\)", "$1");
        s = s.replaceAll("(?m)^#{1,6}\\s*", "");
        s = s.replaceAll("(?m)^\\s*[-*+]\\s+", "• ");
        s = s.replace("**", "").replace("__", "").replace("`", "");
        s = s.replaceAll("&nbsp;", " ").replaceAll("&amp;", "&").replaceAll("&lt;", "<").replaceAll("&gt;", ">");
        s = s.replaceAll("\n{3,}", "\n\n");
        return s.trim();
    }

    private static boolean publishedAfter(ModrinthBrowse.PackVersion v, long time) {
        if (v.date == null || time <= 0) return false;
        try {
            return java.time.Instant.parse(v.date).toEpochMilli() > time;
        } catch (Exception e) {
            return false;
        }
    }

    // ---------------------------------------------------------------- план

    /** Завантажити нову версію і порахувати, що зміниться. На диску збірки нічого не міняє. */
    public Plan prepare(ModrinthBrowse.PackVersion target) throws Exception {
        return prepare(target, false);
    }

    /**
     * 18.11.2026 (власник: «зробити, щоб збірку, в котрій ти щось наклацав, можна було повернути до початкового стану»).
     * План повернення до встановленої версії модпака {@code current}: файли модпака, змінені чи видалені гравцем,
     * повертаються, вимкнені моди модпака вмикаються, свої моди гравця (у mods, яких немає в модпаку) прибираються.
     * Застосовується тим самим {@link #apply}: усе, що міняється, спершу в {@value #BACKUP}, тож «Повернути попередню
     * версію» вертає як було. Світи, скріншоти, options.txt і конфіги, яких немає в модпаку, не чіпаються.
     */
    public Plan prepareReset(ModrinthBrowse.PackVersion current) throws Exception {
        return prepare(current, true);
    }

    private Plan prepare(ModrinthBrowse.PackVersion target, boolean reset) throws Exception {
        JsonNode info = MrpackPacks.info(instance);
        if (info == null) throw new IOException("No " + MrpackPacks.INFO);
        String mc = info.path("mc").asText("");
        if (!target.games.contains(mc)) throw new IOException("The new version is for another Minecraft version");
        File content = new File(instance.getDir(), "minecraft");
        Plan plan = new Plan();
        plan.target = target;
        plan.fromNumber = info.path("versionNumber").asText(info.path("versionId").asText("?"));

        status = SharedLocale.tr("mrpack.update.status.download", target.number);
        downloading = true;
        try {
            plan.packFile = ModrinthBrowse.downloadPackFile(launcher, target, download);
        } finally {
            downloading = false;
        }
        boolean ok = false;
        try {
            status = SharedLocale.tr("mrpack.update.status.compare");
            progress = -1;
            try (ZipFile zip = new ZipFile(plan.packFile)) {
                plan.index = readIndex(zip);
                if (!mc.equals(plan.index.path("dependencies").path("minecraft").asText(""))) {
                    throw new IOException("The new version is for another Minecraft version");
                }
                readEntries(zip, plan.index, plan.entries);
            }
            if (reset) {
                planReset(plan, content);
            } else {
                Map<String, String[]> base = readBase(info);
                planChanges(plan, base, content);
                try {
                    plan.changelog = changelog(instance, target);
                } catch (IOException e) {
                    plan.changelog = null; // без списку змін оновлення однаково можливе
                }
            }
            ok = true;
            return plan;
        } finally {
            if (!ok) plan.discard();
        }
    }

    private static JsonNode readIndex(ZipFile zip) throws IOException {
        ZipEntry ie = zip.getEntry("modrinth.index.json");
        if (ie == null) throw new IOException("Not a Modrinth modpack: no modrinth.index.json");
        try (InputStream in = zip.getInputStream(ie)) {
            return MAPPER.readTree(in);
        }
    }

    /** Файли версії: з адрес (без тих, що клієнту не потрібні) і з overrides, client-overrides (перекривають). */
    private static void readEntries(ZipFile zip, JsonNode index, Map<String, Entry> out) throws IOException {
        for (JsonNode f : index.path("files")) {
            if ("unsupported".equals(f.path("env").path("client").asText(""))) continue;
            Entry e = new Entry();
            e.path = f.path("path").asText("").replace('\\', '/');
            if (e.path.isEmpty()) continue;
            e.sha1 = f.path("hashes").path("sha1").asText("");
            e.sha512 = f.path("hashes").path("sha512").asText(null);
            e.project = MrpackPacks.projectOf(f);
            for (JsonNode u : f.path("downloads")) e.urls.add(u.asText());
            out.put(e.path, e);
        }
        for (String prefix : new String[]{"overrides/", "client-overrides/"}) {
            java.util.Enumeration<? extends ZipEntry> en = zip.entries();
            while (en.hasMoreElements()) {
                ZipEntry z = en.nextElement();
                if (z.isDirectory() || !z.getName().startsWith(prefix)) continue;
                Entry e = new Entry();
                e.path = z.getName().substring(prefix.length());
                if (e.path.isEmpty()) continue;
                e.zipName = z.getName();
                try (InputStream in = zip.getInputStream(z)) {
                    e.sha1 = sha1(in);
                }
                out.put(e.path, e);
            }
        }
    }

    /**
     * Як модпак поставив файли: {@link MrpackPacks#FILES} (з 17.11.2026 ~21:40). Для старіших збірок: файли поточної
     * версії з Modrinth (качається її .mrpack). path -> {sha1, project}.
     */
    private Map<String, String[]> readBase(JsonNode info) throws Exception {
        Map<String, String[]> base = new HashMap<String, String[]>();
        File f = new File(instance.getDir(), MrpackPacks.FILES);
        if (f.isFile()) {
            JsonNode files = MAPPER.readTree(f).path("files");
            java.util.Iterator<Map.Entry<String, JsonNode>> it = files.fields();
            while (it.hasNext()) {
                Map.Entry<String, JsonNode> e = it.next();
                base.put(e.getKey(), new String[]{e.getValue().path("sha1").asText(""), e.getValue().path("project").asText(null)});
            }
            return base;
        }
        Check c = check(instance);
        if (c == null || c.current == null) throw new IOException(UNKNOWN_BASE);
        status = SharedLocale.tr("mrpack.update.status.downloadOld", c.current.number);
        downloading = true;
        File old;
        try {
            old = ModrinthBrowse.downloadPackFile(launcher, c.current, download);
        } finally {
            downloading = false;
        }
        try (ZipFile zip = new ZipFile(old)) {
            Map<String, Entry> entries = new LinkedHashMap<String, Entry>();
            readEntries(zip, readIndex(zip), entries);
            for (Entry e : entries.values()) base.put(e.path, new String[]{e.sha1, e.project});
        } finally {
            old.delete();
        }
        return base;
    }

    /** Текст помилки, коли невідомо, з якої версії ставилась збірка (вікно показує його гравцю по-людськи). */
    public static final String UNKNOWN_BASE = "UNKNOWN_BASE";

    /** Стан файлу в теці гри: sha1 (чи null, якщо немає), вимкнений (.disabled). */
    private static String[] state(File content, String path) throws IOException {
        File f = new File(content, path);
        if (f.isFile()) return new String[]{sha1(f), "0"};
        File d = new File(content, path + com.gummer.launcher.manage.InstanceMods.DISABLED);
        if (d.isFile()) return new String[]{sha1(d), "1"};
        return null;
    }

    private static boolean protectedPath(String path) {
        String p = path.toLowerCase(Locale.ROOT);
        return p.startsWith("saves/") || p.startsWith("screenshots/") || p.startsWith("logs/") || p.startsWith("crash-reports/");
    }

    private void planChanges(Plan plan, Map<String, String[]> base, File content) throws IOException {
        Set<String> handled = new HashSet<String>();
        File modsDir = new File(content, "mods");
        // 1) моди за проєктом Modrinth: нова версія мода зазвичай має іншу назву файлу
        Map<String, List<String>> baseByProject = new HashMap<String, List<String>>();
        Map<String, List<Entry>> newByProject = new HashMap<String, List<Entry>>();
        for (Map.Entry<String, String[]> b : base.entrySet()) {
            if (b.getValue()[1] != null) baseByProject.computeIfAbsent(b.getValue()[1], k -> new ArrayList<String>()).add(b.getKey());
        }
        for (Entry e : plan.entries.values()) {
            if (e.project != null) newByProject.computeIfAbsent(e.project, k -> new ArrayList<Entry>()).add(e);
        }
        for (Map.Entry<String, List<Entry>> np : newByProject.entrySet()) {
            String project = np.getKey();
            List<String> olds = baseByProject.get(project);
            if (np.getValue().size() != 1) continue;
            Entry t = np.getValue().get(0);
            String own = ModrinthBrowse.recordedFile(modsDir, project);
            String ownPath = own != null ? "mods/" + own : null;
            boolean ownHere = ownPath != null && !base.containsKey(ownPath) && !ownPath.equals(t.path) && state(content, ownPath) != null;
            if (olds == null) {
                // новий мод модпака, а гравець уже поставив його сам
                if (ownHere) {
                    plan.changes.add(new Change(Change.Type.OWN_VERSION, ownPath, t.path, "1".equals(state(content, ownPath)[1])));
                    handled.add(t.path);
                }
                continue;
            }
            if (olds.size() != 1 || olds.get(0).equals(t.path)) continue; // та сама назва: нижче за шляхом
            String b = olds.get(0);
            String[] cur = state(content, b);
            handled.add(b);
            handled.add(t.path);
            if (cur == null) {
                if (ownHere) {
                    plan.changes.add(new Change(Change.Type.OWN_VERSION, ownPath, t.path, "1".equals(state(content, ownPath)[1])));
                } else {
                    plan.changes.add(new Change(Change.Type.DELETED_BY_YOU, b, t.path, false));
                }
            } else if (cur[0].equalsIgnoreCase(base.get(b)[0])) {
                plan.changes.add(new Change(Change.Type.UPDATE, b, t.path, "1".equals(cur[1])));
            } else {
                plan.changes.add(new Change(Change.Type.CHANGED_BY_YOU, b, t.path, "1".equals(cur[1])));
            }
        }
        // 2) решта за шляхом
        Set<String> all = new java.util.TreeSet<String>(base.keySet());
        all.addAll(plan.entries.keySet());
        for (String p : all) {
            if (handled.contains(p)) continue;
            String[] b = base.get(p);
            Entry t = plan.entries.get(p);
            String[] cur = state(content, p);
            boolean disabled = cur != null && "1".equals(cur[1]);
            if (b != null && t != null) {
                if (b[0].equalsIgnoreCase(t.sha1)) continue; // модпак файл не міняв
                if (protectedPath(p)) continue;
                if (cur == null) plan.changes.add(new Change(Change.Type.DELETED_BY_YOU, p, p, false));
                else if (cur[0].equalsIgnoreCase(t.sha1)) continue;
                else if (cur[0].equalsIgnoreCase(b[0])) plan.changes.add(new Change(Change.Type.UPDATE, p, p, disabled));
                else plan.changes.add(new Change(Change.Type.CHANGED_BY_YOU, p, p, disabled));
            } else if (b != null) {
                if (cur == null || protectedPath(p)) continue;
                if (cur[0].equalsIgnoreCase(b[0])) plan.changes.add(new Change(Change.Type.REMOVE, p, null, disabled));
                else plan.changes.add(new Change(Change.Type.CHANGED_BY_YOU, p, null, disabled));
            } else {
                if (cur == null) plan.changes.add(new Change(Change.Type.ADD, p, p, false));
                else if (!cur[0].equalsIgnoreCase(t.sha1) && !protectedPath(p)) plan.changes.add(new Change(Change.Type.CHANGED_BY_YOU, p, p, disabled));
            }
        }
    }

    private static void planReset(Plan plan, File content) throws IOException {
        for (Entry t : plan.entries.values()) {
            String p = t.path;
            if (protectedPath(p)) continue;
            String[] cur = state(content, p);
            if (cur == null) {
                plan.changes.add(new Change(Change.Type.ADD, p, p, false));
            } else if (!cur[0].equalsIgnoreCase(t.sha1)) {
                Change c = new Change(Change.Type.CHANGED_BY_YOU, p, p, false);
                c.takePack = true;
                plan.changes.add(c);
            } else if ("1".equals(cur[1])) {
                plan.changes.add(new Change(Change.Type.UPDATE, p, p, false)); // вимкнений мод модпака: знову увімкнений
            }
        }
        File[] mods = new File(content, "mods").listFiles();
        if (mods == null) return;
        Set<String> seen = new HashSet<String>();
        String off = com.gummer.launcher.manage.InstanceMods.DISABLED;
        for (File f : mods) {
            if (!f.isFile()) continue;
            String name = f.getName();
            if (name.endsWith(off)) name = name.substring(0, name.length() - off.length());
            String p = "mods/" + name;
            if (plan.entries.containsKey(p) || !seen.add(p)) continue;
            plan.changes.add(new Change(Change.Type.REMOVE, p, null, f.getName().endsWith(off)));
        }
    }

    // ---------------------------------------------------------------- застосування

    /** Застосувати план (у фоні). Повертає назву збірки. Помилка посередині: усе повертається з резервної копії. */
    public String apply(Plan plan) throws Exception {
        File dir = instance.getDir();
        File content = new File(dir, "minecraft");
        File tmp = new File(dir, TMP);
        deleteTree(tmp);
        tmp.mkdirs();
        try {
            // 1. нові файли: спершу всі на диск поруч і звірити, нічого в збірці ще не змінено
            List<Change> writes = new ArrayList<Change>();
            for (Change c : plan.changes) {
                boolean write = c.type == Change.Type.UPDATE || c.type == Change.Type.ADD || c.isConflict() && c.takePack && c.newPath != null;
                if (write) writes.add(c);
            }
            fetch(plan, writes, tmp);

            // 2. резервна копія попереднього стану
            status = SharedLocale.tr("mrpack.update.status.apply");
            progress = -1;
            File backup = new File(dir, BACKUP);
            deleteTree(backup);
            File backupFiles = new File(backup, "files");
            backupFiles.mkdirs();
            for (String meta : new String[]{MrpackPacks.INFO, MrpackPacks.FILES}) {
                File m = new File(dir, meta);
                if (m.isFile()) Files.copy(m.toPath(), new File(backup, meta).toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            ObjectNode journal = MAPPER.createObjectNode();
            journal.put("from", plan.fromNumber);
            journal.put("to", plan.target.number);
            journal.put("at", System.currentTimeMillis());
            ArrayNode added = journal.putArray("added");
            ArrayNode moved = journal.putArray("backedUp");
            MAPPER.writerWithDefaultPrettyPrinter().writeValue(new File(backup, "backup.json"), journal);

            try {
                // 3. зміни
                for (Change c : plan.changes) {
                    boolean take = c.type == Change.Type.UPDATE || c.type == Change.Type.REMOVE || c.isConflict() && c.takePack;
                    if (c.type == Change.Type.ADD) {
                        place(tmp, c.newPath, content, c.newPath, false, added);
                        continue;
                    }
                    if (!take) continue;
                    // старий файл (увімкнений чи вимкнений) у резервну копію
                    String[] cur = state(content, c.path);
                    if (cur != null) {
                        String actual = "1".equals(cur[1]) ? c.path + com.gummer.launcher.manage.InstanceMods.DISABLED : c.path;
                        File from = new File(content, actual);
                        File to = MrpackPacks.safe(backupFiles, actual);
                        to.getParentFile().mkdirs();
                        Files.move(from.toPath(), to.toPath(), StandardCopyOption.REPLACE_EXISTING);
                        moved.add(actual);
                    }
                    if (c.newPath != null) place(tmp, c.newPath, content, c.newPath, c.disabled, added);
                }

                // 4. нові mrpack.json і mrpack-files.json
                writeInfo(plan);
                MAPPER.writerWithDefaultPrettyPrinter().writeValue(new File(backup, "backup.json"), journal);
            } catch (Exception e) {
                MAPPER.writerWithDefaultPrettyPrinter().writeValue(new File(backup, "backup.json"), journal);
                log.log(Level.WARNING, "Modpack update failed half-way, restoring " + instance.getName(), e);
                try {
                    restore(instance);
                } catch (Exception r) {
                    log.log(Level.SEVERE, "Restore after a failed update also failed: " + instance.getName(), r);
                }
                throw e;
            }
            forget(instance);
            log.info("Modpack " + instance.getName() + " updated " + plan.fromNumber + " -> " + plan.target.number + ": "
                    + plan.count(Change.Type.UPDATE) + " updated, " + plan.count(Change.Type.ADD) + " added, "
                    + plan.count(Change.Type.REMOVE) + " removed, " + plan.conflicts().size() + " kept or chosen");
            return instance.getName();
        } finally {
            deleteTree(tmp);
            plan.discard();
        }
    }

    /** Покласти файл з тимчасової теки на місце (вимкнений лишається вимкненим), записати в журнал доданих. */
    private static void place(File tmp, String path, File content, String target, boolean disabled, ArrayNode added) throws IOException {
        File from = MrpackPacks.safe(tmp, path);
        String name = disabled ? target + com.gummer.launcher.manage.InstanceMods.DISABLED : target;
        File to = MrpackPacks.safe(content, name);
        to.getParentFile().mkdirs();
        if (to.exists()) {
            // такий файл уже лежить (наприклад, гравець поклав той самий): лишаємо його
            return;
        }
        Files.move(from.toPath(), to.toPath(), StandardCopyOption.REPLACE_EXISTING);
        added.add(name);
    }

    /** Завантажити (6 одночасно) або дістати з архіву потрібні файли, звірити хеші. */
    private void fetch(final Plan plan, List<Change> writes, final File tmp) throws Exception {
        final List<Entry> todo = new ArrayList<Entry>();
        for (Change c : writes) {
            Entry e = plan.entries.get(c.newPath);
            if (e != null && !todo.contains(e)) todo.add(e);
        }
        final int total = todo.size();
        final java.util.concurrent.atomic.AtomicInteger done = new java.util.concurrent.atomic.AtomicInteger();
        status = SharedLocale.tr("mrpack.update.status.files", 0, total);
        progress = 0;
        try (final ZipFile zip = new ZipFile(plan.packFile)) {
            java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(16, // 24.11.2026: 6 -> 16, як HttpDownloader
                     r -> {
                Thread t = new Thread(r, "mrpack-update");
                t.setDaemon(true);
                return t;
            });
            try {
                List<java.util.concurrent.Future<?>> jobs = new ArrayList<java.util.concurrent.Future<?>>();
                for (final Entry e : todo) {
                    jobs.add(pool.submit(() -> {
                        byte[] data;
                        if (e.zipName != null) {
                            try (InputStream in = zip.getInputStream(zip.getEntry(e.zipName))) {
                                data = com.google.common.io.ByteStreams.toByteArray(in);
                            }
                        } else {
                            data = null;
                            IOException last = null;
                            for (String u : e.urls) {
                                URL url = new URL(u);
                                if (!"https".equals(url.getProtocol()) || !MrpackPacks.HOSTS.contains(url.getHost().toLowerCase(Locale.ROOT))) {
                                    last = new IOException("Download host not allowed by the .mrpack format: " + url.getHost());
                                    continue;
                                }
                                try {
                                    data = MrpackPacks.fetch(url, null); // 24.11.2026: з повторами
                                    break;
                                } catch (IOException ex) {
                                    last = ex;
                                }
                            }
                            if (data == null) throw last != null ? last : new IOException("No download for " + e.path);
                            MrpackPacks.check(e.path, data, "SHA-512", e.sha512);
                        }
                        MrpackPacks.check(e.path, data, "SHA-1", e.sha1);
                        File target = MrpackPacks.safe(tmp, e.path);
                        target.getParentFile().mkdirs();
                        Files.write(target.toPath(), data);
                        int n = done.incrementAndGet();
                        status = SharedLocale.tr("mrpack.update.status.files", n, total) + "\n" + e.path;
                        progress = (double) n / Math.max(1, total);
                        return null;
                    }));
                }
                for (java.util.concurrent.Future<?> j : jobs) {
                    try {
                        j.get();
                    } catch (java.util.concurrent.ExecutionException ex) {
                        Throwable c = ex.getCause();
                        if (c instanceof Exception) throw (Exception) c;
                        throw new IOException(c);
                    }
                }
            } finally {
                pool.shutdownNow();
            }
        }
    }

    private void writeInfo(Plan plan) throws IOException {
        File dir = instance.getDir();
        JsonNode old = MrpackPacks.info(instance);
        ObjectNode info = old instanceof ObjectNode ? ((ObjectNode) old).deepCopy() : MAPPER.createObjectNode();
        JsonNode deps = plan.index.path("dependencies");
        String mc = deps.path("minecraft").asText();
        String[][] known = {{"fabric-loader", "fabric"}, {"quilt-loader", "quilt"}, {"forge", "forge"}, {"neoforge", "neoforge"}};
        for (String[] k : known) {
            if (!deps.has(k[0])) continue;
            String lv = deps.path(k[0]).asText();
            if (!lv.matches("[0-9A-Za-z.+_-]+")) throw new IOException("Bad loader version " + lv);
            if ("forge".equals(k[1]) && !lv.startsWith(mc + "-")) lv = mc + "-" + lv;
            info.put("loader", k[1]);
            info.put("loaderVersion", lv);
        }
        info.put("versionId", plan.index.path("versionId").asText(""));
        info.put("mrVersionId", plan.target.id);
        info.put("versionNumber", plan.target.number);
        MAPPER.writerWithDefaultPrettyPrinter().writeValue(new File(dir, MrpackPacks.INFO), info);
        MrpackPacks.forgetInfo(dir);
        ObjectNode files = MAPPER.createObjectNode();
        ObjectNode map = files.putObject("files");
        for (Entry e : plan.entries.values()) {
            ObjectNode o = map.putObject(e.path);
            o.put("sha1", e.sha1);
            if (e.project != null) o.put("project", e.project);
        }
        MAPPER.writerWithDefaultPrettyPrinter().writeValue(new File(dir, MrpackPacks.FILES), files);
    }

    // ---------------------------------------------------------------- відкат

    /** Версія, до якої можна повернутись (з {@value #BACKUP}), або null. */
    public static String backupVersion(Instance instance) {
        File j = new File(new File(instance.getDir(), BACKUP), "backup.json");
        if (!j.isFile()) return null;
        try {
            return MAPPER.readTree(j).path("from").asText(null);
        } catch (IOException e) {
            return null;
        }
    }

    /** Повернути збірку до стану перед останнім оновленням. */
    public static void restore(Instance instance) throws IOException {
        File dir = instance.getDir();
        File content = new File(dir, "minecraft");
        File backup = new File(dir, BACKUP);
        JsonNode journal = MAPPER.readTree(new File(backup, "backup.json"));
        for (JsonNode a : journal.path("added")) {
            File f = MrpackPacks.safe(content, a.asText());
            Files.deleteIfExists(f.toPath());
        }
        File files = new File(backup, "files");
        for (JsonNode b : journal.path("backedUp")) {
            File from = MrpackPacks.safe(files, b.asText());
            if (!from.isFile()) continue;
            File to = MrpackPacks.safe(content, b.asText());
            to.getParentFile().mkdirs();
            Files.move(from.toPath(), to.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
        for (String meta : new String[]{MrpackPacks.INFO, MrpackPacks.FILES}) {
            File m = new File(backup, meta);
            if (m.isFile()) Files.copy(m.toPath(), new File(dir, meta).toPath(), StandardCopyOption.REPLACE_EXISTING);
            else if (meta.equals(MrpackPacks.FILES)) Files.deleteIfExists(new File(dir, meta).toPath());
        }
        deleteTree(backup);
        MrpackPacks.forgetInfo(dir);
        forget(instance);
        log.info("Modpack " + instance.getName() + " restored to " + journal.path("from").asText("?"));
    }

    // ---------------------------------------------------------------- дрібне

    static String sha1(InputStream in) throws IOException {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-1");
            byte[] buf = new byte[65536];
            for (int n; (n = in.read(buf)) > 0; ) md.update(buf, 0, n);
            StringBuilder sb = new StringBuilder();
            for (byte b : md.digest()) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IOException(e);
        }
    }

    static String sha1(File f) throws IOException {
        try (InputStream in = new java.io.FileInputStream(f)) {
            return sha1(in);
        }
    }

    private static void deleteTree(File f) {
        File[] kids = f.listFiles();
        if (kids != null) for (File k : kids) deleteTree(k);
        f.delete();
    }

    /** Шляхи модів модпака (малими літерами, «mods/x.jar»), щоб не пропонувати їх як свої моди гравця. */
    public static Set<String> packPaths(Instance instance) {
        Set<String> out = new HashSet<String>();
        File f = new File(instance.getDir(), MrpackPacks.FILES);
        if (!f.isFile()) return out;
        try {
            java.util.Iterator<String> it = MAPPER.readTree(f).path("files").fieldNames();
            while (it.hasNext()) out.add(it.next().toLowerCase(Locale.ROOT));
        } catch (IOException ignored) {
            // як без журналу
        }
        return out;
    }

    @Override
    public double getProgress() {
        return downloading ? download.getProgress() : progress;
    }

    @Override
    public String getStatus() {
        if (downloading) {
            String d = download.getStatus();
            return d == null || d.isEmpty() ? status : status + "\n" + d;
        }
        return status;
    }

    /** Для тестів і вікна: список змін у зрозумілому порядку (конфлікти перші). */
    public static List<Change> sorted(Plan plan) {
        List<Change> out = new ArrayList<Change>(plan.changes);
        Collections.sort(out, (a, b) -> a.type.ordinal() != b.type.ordinal() ? b.type.ordinal() - a.type.ordinal() : a.path.compareTo(b.path));
        return out;
    }
}
