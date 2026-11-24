/*
 * Copyright (C) 2021-2026 GummerCraft <http://gummercraft.fun>
 */

package com.gummer.launcher.vanilla;

import com.skcraft.launcher.Instance;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 1.32.1: перший запуск версії Vanilla як у збірок GummerCraft. Гра одразу українською (лише коли
 * {@code options.txt} ще немає, тобто налаштування гравця не чіпаємо) і сервер GummerCraft першим у списку
 * мережевої гри (один раз на версію: якщо гравець його видалить, він не повернеться; з 1.40 лише чиста Vanilla, без
 * завантажувачів і модових збірок). Збірки GummerCraft
 * мають те саме у своїх файлах ({@code options.txt}, {@code servers.dat}), тут лише Vanilla.
 * Будь-яка помилка лише пишеться в лог: запуск гри через це не зупиняється.
 */
public final class VanillaDefaults {

    public static final String SERVER_NAME = "GummerCraft";
    public static final String SERVER_ADDRESS = "play.gummercraft.fun";
    /** Позначка «сервер уже додавали» в теці гри версії. */
    static final String SERVER_MARKER = ".gummercraft-server-added";
    /** 1.36.1: мову і розмір інтерфейсу вже поставлено при першому запуску (далі options.txt лише гравця). */
    static final String FIRST_MARKER = ".gummercraft-first-settings";

    private static final Logger log = Logger.getLogger(VanillaDefaults.class.getName());

    private VanillaDefaults() {
    }

    public static void apply(Instance instance) {
        if (!instance.isBuiltIn()) {
            // 22.11.2026: модпаки GummerCraft приносять options.txt з українською; на неукраїнській системі перший
            // запуск англійською (власник: «в кого укр/рус - тому українська, всім іншим англ»)
            try {
                englishForModpack(instance.getContentDir());
            } catch (Exception e) {
                log.log(Level.WARNING, "Modpack: could not set the game language", e);
            }
            return;
        }
        File dir = instance.getContentDir();
        try {
            applyLanguage(dir, instance.isFabric() ? com.gummer.launcher.fabric.FabricVersions.versionIdOf(instance)
                    : instance.isQuilt() ? com.gummer.launcher.quilt.QuiltVersions.versionIdOf(instance)
                    : instance.isMrpack() ? com.gummer.launcher.modrinth.MrpackPacks.mcOf(instance)
                    : instance.isForgeLike() ? com.gummer.launcher.forge.ForgeVersions.versionIdOf(instance)
                    : VanillaVersions.versionIdOf(instance));
        } catch (Exception e) {
            log.log(Level.WARNING, "Vanilla: could not write options.txt", e);
        }
        // 24.11.2026 (1.40, власник: «прибери "рекламу гаммеркрафту" в модових збірках ... залиш лише на ванілі.
        // гравець все одно не приєднається»): сервер GummerCraft у «Гра в мережі» лише для чистої Vanilla. Збірки
        // Fabric, Quilt, Forge, NeoForge, Modrinth і CurseForge його більше не отримують (у кого вже додано, лишається:
        // servers.dat гравця не чіпаємо)
        if (!instance.isVanilla()) return;
        try {
            applyServer(dir);
        } catch (Exception e) {
            log.log(Level.WARNING, "Vanilla: could not add the GummerCraft server", e);
        }
    }

    /**
     * 22.11.2026 (власник: брат з чеською Windows отримав гру українською; «мова самого майну, в кого укр/рус - тому
     * українська, всім іншим англ»): чи система українська чи російська, так само, як мова лаунчера для нової теки
     * даних ({@link com.gummer.launcher.i18n.I18n#detect}).
     */
    static boolean ukrainianSystem() {
        return com.gummer.launcher.i18n.I18n.detect() == com.gummer.launcher.i18n.Lang.UKRAINIAN;
    }

    /** Мова гри за замовчуванням для версії: українська на системі uk/ru, інакше англійська. */
    static String defaultLang(String versionId) {
        if (ukrainianSystem()) return langCode(versionId);
        return "uk_UA".equals(langCode(versionId)) ? "en_US" : "en_us";
    }

    /** Модпак GummerCraft, перший запуск на неукраїнській системі: lang з uk на англійську, решту options.txt не чіпати. */
    static void englishForModpack(File dir) throws IOException {
        File options = new File(dir, "options.txt");
        File marker = new File(dir, FIRST_MARKER);
        if (ukrainianSystem() || marker.exists() || new File(dir, "logs").exists() || !options.isFile()) return;
        java.util.List<String> lines = new ArrayList<String>(java.nio.file.Files.readAllLines(options.toPath(), StandardCharsets.UTF_8));
        boolean changed = false;
        for (int i = 0; i < lines.size(); i++) {
            String l = lines.get(i);
            if (l.startsWith("lang:") && l.substring(5).trim().toLowerCase(java.util.Locale.ROOT).startsWith("uk_")) {
                lines.set(i, "lang:" + (l.substring(5).trim().equals("uk_UA") ? "en_US" : "en_us"));
                changed = true;
            }
        }
        if (changed) {
            try (Writer w = new OutputStreamWriter(new FileOutputStream(options), StandardCharsets.UTF_8)) {
                for (String l : lines) w.write(l + "\n");
            }
            log.info("Modpack: not a Ukrainian or Russian system, first launch in English");
        }
        marker.createNewFile();
    }

    /** Код мови для версії: до 1.11 Minecraft знав лише uk_UA, з 1.11 коди в нижньому регістрі. */
    static String langCode(String versionId) {
        String[] p = versionId.split("\\.");
        try {
            int major = Integer.parseInt(p[0]);
            int minor = p.length > 1 ? Integer.parseInt(p[1].replaceAll("[^0-9].*$", "")) : 0;
            if (major == 1 && minor < 11) return "uk_UA";
        } catch (NumberFormatException e) {
            // невідомий формат версії: новий
        }
        return "uk_ua";
    }

    static void applyLanguage(File dir, String versionId) throws IOException {
        File options = new File(dir, "options.txt");
        // 1.36.1 (власник: «При першому включенні ставити розмір інтерфейсу та мову майну як в інших збірках»): мова і
        // розмір інтерфейсу з тієї збірки, у яку гравець грав останньою; таких немає: українська, розмір гри за
        // замовчуванням. Лише перший запуск: гра в цій теці ще не стартувала (немає logs) і позначки ще немає. Модпаки
        // Modrinth часто приносять свій options.txt (англійська, свій розмір): у ньому міняються лише ці два рядки.
        File marker = new File(dir, FIRST_MARKER);
        if (marker.exists() || new File(dir, "logs").exists()) return;
        dir.mkdirs();
        String[] other = fromOtherBuilds(dir);
        // 1.36.2: спершу те, що гравець сам змінив у грі (GamePrefs, game-prefs.json у теці лаунчера: тека instances
        // лежить у ній), а не найсвіжіший options.txt (його міг щойно перезаписати модпак)
        File self = dir.getAbsoluteFile().getParentFile();
        File base = self != null && self.getParentFile() != null ? self.getParentFile().getParentFile() : null;
        java.util.Map<String, String> prefs = GamePrefs.load(base);
        if (prefs.containsKey(GamePrefs.LANG)) other[0] = prefs.get(GamePrefs.LANG);
        if (prefs.containsKey(GamePrefs.GUI)) other[1] = prefs.get(GamePrefs.GUI);
        // 22.11.2026: на неукраїнській системі українська з інших збірок не береться (це могла бути мова модпака
        // GummerCraft за замовчуванням, а не вибір гравця; вибір гравця в грі вже в prefs вище)
        if (other[0] != null && !prefs.containsKey(GamePrefs.LANG) && !ukrainianSystem()
                && other[0].toLowerCase(java.util.Locale.ROOT).startsWith("uk_")) other[0] = null;
        String lang = other[0] != null ? langFor(other[0], versionId) : defaultLang(versionId);
        java.util.List<String> lines = options.isFile()
                ? new ArrayList<String>(java.nio.file.Files.readAllLines(options.toPath(), StandardCharsets.UTF_8))
                : new ArrayList<String>();
        boolean hasLang = false, hasGui = false;
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).startsWith("lang:")) {
                lines.set(i, "lang:" + lang);
                hasLang = true;
            } else if (other[1] != null && lines.get(i).startsWith("guiScale:")) {
                lines.set(i, "guiScale:" + other[1]);
                hasGui = true;
            }
        }
        if (!hasLang) lines.add("lang:" + lang);
        if (other[1] != null && !hasGui) lines.add("guiScale:" + other[1]);
        try (Writer w = new OutputStreamWriter(new FileOutputStream(options), StandardCharsets.UTF_8)) {
            for (String l : lines) w.write(l + "\n");
        }
        marker.createNewFile();
        log.info("Vanilla: options.txt created with lang " + lang + (other[1] != null ? ", guiScale " + other[1] : "")
                + (other[2] != null ? " (from " + other[2] + ")" : ""));
    }

    /**
     * {мова, guiScale, звідки} з options.txt інших збірок гравця (тека instances): найсвіжіший файл з мовою. Що не
     * знайшлось: null. Помилка читання: файл пропускається.
     */
    static String[] fromOtherBuilds(File contentDir) {
        String[] best = new String[3];
        long bestTime = -1;
        File self = contentDir.getAbsoluteFile().getParentFile();
        File root = self != null ? self.getParentFile() : null;
        File[] builds = root != null ? root.listFiles(File::isDirectory) : null;
        if (builds == null) return best;
        for (File b : builds) {
            if (b.equals(self)) continue;
            File f = new File(new File(b, "minecraft"), "options.txt");
            if (!f.isFile() || f.lastModified() <= bestTime) continue;
            String lang = null, gui = null;
            try {
                for (String line : java.nio.file.Files.readAllLines(f.toPath(), StandardCharsets.UTF_8)) {
                    if (line.startsWith("lang:")) lang = line.substring(5).trim();
                    else if (line.startsWith("guiScale:")) gui = line.substring(9).trim();
                }
            } catch (IOException | RuntimeException e) {
                continue;
            }
            if (lang == null || !lang.matches("[A-Za-z]{2,3}_[A-Za-z0-9]{2,4}")) continue;
            if (gui != null && !gui.matches("[0-9]{1,2}")) gui = null;
            best[0] = lang;
            best[1] = gui;
            best[2] = b.getName();
            bestTime = f.lastModified();
        }
        return best;
    }

    /** Код мови з іншої збірки під цю версію гри: до 1.11 країна великими (uk_UA), з 1.11 усе малими (uk_ua). */
    static String langFor(String lang, String versionId) {
        String low = lang.toLowerCase(java.util.Locale.ROOT);
        if (!"uk_UA".equals(langCode(versionId))) return low;
        int u = low.indexOf('_');
        return low.substring(0, u + 1) + low.substring(u + 1).toUpperCase(java.util.Locale.ROOT);
    }

    static void applyServer(File dir) throws IOException {
        File marker = new File(dir, SERVER_MARKER);
        if (marker.exists()) return;
        dir.mkdirs();
        File servers = new File(dir, "servers.dat");

        Map<String, Tag> root;
        if (servers.exists() && servers.length() > 0) {
            try (DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(servers)))) {
                if (in.readByte() != TAG_COMPOUND) throw new IOException("servers.dat: root is not a compound");
                in.readUTF();
                root = readCompound(in);
            }
        } else {
            root = new LinkedHashMap<String, Tag>();
        }

        Tag list = root.get("servers");
        if (list == null) {
            list = new Tag(TAG_LIST, new ListValue(TAG_COMPOUND, new ArrayList<Object>()));
            root.put("servers", list);
        }
        if (list.type != TAG_LIST) throw new IOException("servers.dat: 'servers' is not a list");
        ListValue lv = (ListValue) list.value;
        if (lv.items.isEmpty()) lv.elementType = TAG_COMPOUND;
        if (lv.elementType != TAG_COMPOUND) throw new IOException("servers.dat: 'servers' has wrong element type");

        boolean present = false;
        for (Object o : lv.items) {
            @SuppressWarnings("unchecked")
            Tag ip = ((Map<String, Tag>) o).get("ip");
            if (ip != null && ip.type == TAG_STRING) {
                String a = ((String) ip.value).trim().toLowerCase(java.util.Locale.ROOT);
                if (a.equals(SERVER_ADDRESS) || a.startsWith(SERVER_ADDRESS + ":")) present = true;
            }
        }
        if (!present) {
            Map<String, Tag> entry = new LinkedHashMap<String, Tag>();
            entry.put("ip", new Tag(TAG_STRING, SERVER_ADDRESS));
            entry.put("name", new Tag(TAG_STRING, SERVER_NAME));
            lv.items.add(0, entry);

            File tmp = new File(dir, "servers.dat.gummercraft-tmp");
            try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(tmp)))) {
                out.writeByte(TAG_COMPOUND);
                out.writeUTF("");
                writeCompound(out, root);
            }
            if (servers.exists() && !servers.delete()) {
                tmp.delete();
                throw new IOException("servers.dat: could not replace");
            }
            if (!tmp.renameTo(servers)) throw new IOException("servers.dat: could not rename temp file");
            log.info("Vanilla: GummerCraft server added to servers.dat");
        }
        new FileOutputStream(marker).close();
    }

    // ---- мінімальний NBT (без стиснення, як у servers.dat): читає і пише все без втрат ----

    static final byte TAG_END = 0, TAG_BYTE = 1, TAG_SHORT = 2, TAG_INT = 3, TAG_LONG = 4, TAG_FLOAT = 5,
            TAG_DOUBLE = 6, TAG_BYTE_ARRAY = 7, TAG_STRING = 8, TAG_LIST = 9, TAG_COMPOUND = 10,
            TAG_INT_ARRAY = 11, TAG_LONG_ARRAY = 12;

    static final class Tag {
        final byte type;
        final Object value;

        Tag(byte type, Object value) {
            this.type = type;
            this.value = value;
        }
    }

    static final class ListValue {
        byte elementType;
        final List<Object> items;

        ListValue(byte elementType, List<Object> items) {
            this.elementType = elementType;
            this.items = items;
        }
    }

    static Map<String, Tag> readCompound(DataInputStream in) throws IOException {
        Map<String, Tag> map = new LinkedHashMap<String, Tag>();
        while (true) {
            byte type = in.readByte();
            if (type == TAG_END) return map;
            String name = in.readUTF();
            map.put(name, new Tag(type, readPayload(in, type)));
        }
    }

    static Object readPayload(DataInputStream in, byte type) throws IOException {
        switch (type) {
            case TAG_BYTE: return in.readByte();
            case TAG_SHORT: return in.readShort();
            case TAG_INT: return in.readInt();
            case TAG_LONG: return in.readLong();
            case TAG_FLOAT: return in.readFloat();
            case TAG_DOUBLE: return in.readDouble();
            case TAG_BYTE_ARRAY: {
                byte[] a = new byte[checkedLength(in.readInt())];
                in.readFully(a);
                return a;
            }
            case TAG_STRING: return in.readUTF();
            case TAG_LIST: {
                byte et = in.readByte();
                int n = checkedLength(in.readInt());
                List<Object> items = new ArrayList<Object>(n);
                for (int i = 0; i < n; i++) items.add(readPayload(in, et));
                return new ListValue(et, items);
            }
            case TAG_COMPOUND: return readCompound(in);
            case TAG_INT_ARRAY: {
                int[] a = new int[checkedLength(in.readInt())];
                for (int i = 0; i < a.length; i++) a[i] = in.readInt();
                return a;
            }
            case TAG_LONG_ARRAY: {
                long[] a = new long[checkedLength(in.readInt())];
                for (int i = 0; i < a.length; i++) a[i] = in.readLong();
                return a;
            }
            default: throw new IOException("NBT: unknown tag type " + type);
        }
    }

    private static int checkedLength(int n) throws IOException {
        if (n < 0 || n > 16 * 1024 * 1024) throw new IOException("NBT: bad length " + n);
        return n;
    }

    static void writeCompound(DataOutputStream out, Map<String, Tag> map) throws IOException {
        for (Map.Entry<String, Tag> e : map.entrySet()) {
            out.writeByte(e.getValue().type);
            out.writeUTF(e.getKey());
            writePayload(out, e.getValue().type, e.getValue().value);
        }
        out.writeByte(TAG_END);
    }

    @SuppressWarnings("unchecked")
    static void writePayload(DataOutputStream out, byte type, Object v) throws IOException {
        switch (type) {
            case TAG_BYTE: out.writeByte((Byte) v); break;
            case TAG_SHORT: out.writeShort((Short) v); break;
            case TAG_INT: out.writeInt((Integer) v); break;
            case TAG_LONG: out.writeLong((Long) v); break;
            case TAG_FLOAT: out.writeFloat((Float) v); break;
            case TAG_DOUBLE: out.writeDouble((Double) v); break;
            case TAG_BYTE_ARRAY: {
                byte[] a = (byte[]) v;
                out.writeInt(a.length);
                out.write(a);
                break;
            }
            case TAG_STRING: out.writeUTF((String) v); break;
            case TAG_LIST: {
                ListValue lv = (ListValue) v;
                out.writeByte(lv.items.isEmpty() ? TAG_END : lv.elementType);
                out.writeInt(lv.items.size());
                for (Object o : lv.items) writePayload(out, lv.elementType, o);
                break;
            }
            case TAG_COMPOUND: writeCompound(out, (Map<String, Tag>) v); break;
            case TAG_INT_ARRAY: {
                int[] a = (int[]) v;
                out.writeInt(a.length);
                for (int x : a) out.writeInt(x);
                break;
            }
            case TAG_LONG_ARRAY: {
                long[] a = (long[]) v;
                out.writeInt(a.length);
                for (long x : a) out.writeLong(x);
                break;
            }
            default: throw new IOException("NBT: unknown tag type " + type);
        }
    }
}
