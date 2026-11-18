/*
 * Copyright (C) 2021-2026 GummerCraft <http://gummercraft.fun>
 */

package com.gummer.launcher.manage;

import com.skcraft.launcher.Instance;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;

/**
 * 17.11.2026: мод вимагає новішу версію іншого мода, ніж та, що стоїть у збірці, і лаунчер ставить потрібну з
 * Modrinth сам, перед запуском гри.
 * <p>
 * Звідки береться: збірки з Modrinth інколи кладуть стару залежність. The Pixelmon Modpack 9.1.13 (Forge 1.16.5) у
 * своєму списку має byg 1.1.6, а Pixelmon 9.1.13 у {@code mods.toml} вимагає byg {@code [1.3.4,)}: гра зупиняється
 * на «Mod pixelmon only supports byg 1.3.4 or above». Те саме буває, коли гравець оновлює один мод, а його
 * залежність ні.
 * <p>
 * Як: вимоги до версій з самих jar ({@link ModInfo#minVersions}), порівняння з версією мода, що стоїть; лише «надто
 * стара» (нижня межа), на новішу ніколи не міняємо і назад не відкочуємо. Знайдене оновлюється тим самим кодом, що й
 * «Оновити» в «Мої моди» ({@link ModUpdates}): найновіша версія під гру і завантажувач збірки, файл лише з
 * cdn.modrinth.com зі звіркою sha512. Якщо найновіша на Modrinth все одно стара чи мода там немає, нічого не міняється.
 * <p>
 * Лише збірки, моди яких веде гравець: Vanilla і завантажувачі, копії, збірки Modrinth. Модпаки GummerCraft і
 * StalDayZ не чіпаються: їхні моди задає наш CDN.
 */
public final class ModVersionFix {

    private static final Logger log = Logger.getLogger(ModVersionFix.class.getName());

    /** Одна невідповідність: моду {@code by} потрібна версія {@code need} мода {@code dep}, а стоїть {@code have}. */
    public static final class Problem {
        public final String by;
        public final String dep;
        public final String need;
        public final String have;
        public final File file;

        Problem(String by, String dep, String need, String have, File file) {
            this.by = by;
            this.dep = dep;
            this.need = need;
            this.have = have;
            this.file = file;
        }

        @Override
        public String toString() {
            return by + " needs " + dep + " >= " + need + ", has " + have + " (" + file.getName() + ")";
        }
    }

    private ModVersionFix() {
    }

    /** Чи стосується це збірки: лише ті, де моди веде гравець. */
    public static boolean applies(Instance instance) {
        return instance != null && (instance.isBuiltIn() || instance.isCopy()) && !instance.isVanilla()
                && !InstanceMods.packModsLocked(instance);
    }

    /** Знайти невідповідності версій серед увімкнених модів (без мережі; jar читаються з кешу ModInfo). */
    public static List<Problem> find(Instance instance) {
        List<Problem> out = new ArrayList<Problem>();
        if (!applies(instance)) return out;
        List<InstanceMods.Mod> mods = new ArrayList<InstanceMods.Mod>();
        Map<String, InstanceMods.Mod> byId = new HashMap<String, InstanceMods.Mod>();
        for (InstanceMods.Mod m : InstanceMods.list(instance)) {
            if (!m.enabled) continue;
            mods.add(m);
            String id = ModInfo.of(m.file).id;
            if (id != null) byId.put(id.toLowerCase(Locale.ROOT), m);
        }
        String game = InstanceCopies.minecraftVersionOf(instance);
        Map<File, Problem> worst = new LinkedHashMap<File, Problem>();
        for (InstanceMods.Mod m : mods) {
            ModInfo info = ModInfo.of(m.file);
            for (Map.Entry<String, String> need : info.minVersions.entrySet()) {
                InstanceMods.Mod dep = byId.get(need.getKey());
                if (dep == null || dep == m) continue;
                String have = ModInfo.of(dep.file).version;
                if (have == null || have.contains("${")) continue;
                if (compare(strip(have, game), strip(need.getValue(), game)) >= 0) continue;
                Problem p = new Problem(info.name, need.getKey(), need.getValue(), have, dep.file);
                Problem before = worst.get(dep.file);
                // кілька модів вимагають різне: найвища вимога
                if (before == null || compare(strip(p.need, game), strip(before.need, game)) > 0) worst.put(dep.file, p);
            }
        }
        out.addAll(worst.values());
        return out;
    }

    /**
     * Знайти і виправити (мережа: викликати не з потоку інтерфейсу). Повертає описи виправленого («byg 1.1.6 ->
     * 1.3.4»); що виправити не вдалось, лише в журналі. Без інтернету: порожньо, гра запускається як є.
     */
    public static List<String> fixBeforeLaunch(Instance instance) {
        List<String> done = new ArrayList<String>();
        List<Problem> problems;
        try {
            problems = find(instance);
        } catch (RuntimeException e) {
            log.info("Mod version check failed: " + e);
            return done;
        }
        if (problems.isEmpty()) return done;
        for (Problem p : problems) log.info("Mod version problem: " + p);
        String game = ModrinthBrowse.gameVersionOf(instance);
        String[] loaders = ModrinthBrowse.loadersOf(ModrinthBrowse.Kind.MOD, instance);
        if (game == null || loaders == null || loaders.length == 0) return done;
        List<File> files = new ArrayList<File>();
        for (Problem p : problems) files.add(p.file);
        Map<File, ModUpdates.Update> updates = ModUpdates.check(files, game, loaders);
        for (Problem p : problems) {
            ModUpdates.Update u = updates.get(p.file);
            if (u == null) {
                log.info("No newer " + p.dep + " on Modrinth for " + game + " " + String.join(",", loaders));
                continue;
            }
            // номер версії на Modrinth не завжди той, що в jar, тож відмовляємось лише коли він явно старий
            if (Character.isDigit(u.versionNumber.isEmpty() ? 'x' : u.versionNumber.charAt(0))
                    && compare(strip(u.versionNumber, game), strip(p.need, game)) < 0) {
                log.info("Newest " + p.dep + " on Modrinth (" + u.versionNumber + ") is still older than " + p.need);
                continue;
            }
            try {
                List<String> missing = new ArrayList<String>(), conflicts = new ArrayList<String>();
                File nf = ModUpdates.apply(u, game, loaders, missing, conflicts);
                done.add(p.dep + " " + p.have + " → " + u.versionNumber);
                log.info("Mod version fixed: " + p.file.getName() + " -> " + nf.getName() + " (" + p.by + " needs " + p.need + ")");
            } catch (Exception e) {
                log.info("Mod version fix failed for " + p.file.getName() + ": " + e);
            }
        }
        return done;
    }

    /** «1.16.5-4.0.1» при грі 1.16.5 -> «4.0.1»: багато модів пишуть версію гри перед своєю. */
    static String strip(String v, String game) {
        if (v == null) return "";
        v = v.trim();
        if (v.startsWith("v") || v.startsWith("V")) v = v.substring(1);
        if (game != null) {
            for (String sep : new String[]{"-", "+", "_"}) {
                if (v.startsWith(game + sep) && v.length() > game.length() + 1) return v.substring(game.length() + 1);
                if (v.startsWith("mc" + game + sep)) return v.substring(game.length() + 3);
            }
        }
        return v;
    }

    /**
     * Порівняння версій як у Maven і semver: числа за значенням, «1.0» = «1.0.0», «1.0-beta» менше за «1.0»,
     * «1.0.1» більше за «1.0-rc». Мітки збірки після «+» не враховуються.
     */
    public static int compare(String a, String b) {
        List<Object> x = tokens(a), y = tokens(b);
        int n = Math.max(x.size(), y.size());
        for (int i = 0; i < n; i++) {
            Object p = i < x.size() ? x.get(i) : null, q = i < y.size() ? y.get(i) : null;
            int c = compareToken(p, q);
            if (c != 0) return c;
        }
        return 0;
    }

    private static int compareToken(Object p, Object q) {
        if (p == null && q == null) return 0;
        // бракує частини: «1.0» проти «1.0.0» рівні, проти «1.0.1» менше, проти «1.0-beta» більше; проти «1.0-forge»
        // чи «5.8.5p6» менше: як у Maven (так порівнює і Forge), невідома мітка це не передреліз, а щось після релізу
        if (p == null) return q instanceof Long ? (((Long) q) == 0 ? 0 : -1) : PRE.contains(q) ? 1 : -1;
        if (q == null) return p instanceof Long ? (((Long) p) == 0 ? 0 : 1) : PRE.contains(p) ? -1 : 1;
        if (p instanceof Long && q instanceof Long) return Long.compare((Long) p, (Long) q);
        if (p instanceof Long) return 1; // число новіше за мітку (1.0.1 > 1.0-rc)
        if (q instanceof Long) return -1;
        return ((String) p).compareTo((String) q);
    }

    /** Мітки передрелізу (менші за реліз); решта міток (forge, p, sp, final) не менші. */
    private static final java.util.Set<Object> PRE = new java.util.HashSet<Object>(java.util.Arrays.<Object>asList(
            "alpha", "a", "beta", "b", "milestone", "m", "rc", "cr", "pre", "snapshot", "dev", "build"));

    private static List<Object> tokens(String v) {
        List<Object> out = new ArrayList<Object>();
        if (v == null) return out;
        int plus = v.indexOf('+');
        if (plus > 0) v = v.substring(0, plus);
        StringBuilder cur = new StringBuilder();
        int kind = 0; // 1 цифри, 2 літери
        for (int i = 0; i <= v.length(); i++) {
            char c = i < v.length() ? Character.toLowerCase(v.charAt(i)) : '.';
            int k = Character.isDigit(c) ? 1 : Character.isLetter(c) ? 2 : 0;
            if (k != kind || k == 0) {
                if (cur.length() > 0) {
                    if (kind == 1) {
                        try {
                            out.add(Long.parseLong(cur.length() > 18 ? cur.substring(0, 18) : cur.toString()));
                        } catch (NumberFormatException e) {
                            out.add(0L);
                        }
                    } else {
                        out.add(cur.toString());
                    }
                }
                cur.setLength(0);
                kind = k;
            }
            if (k != 0) cur.append(c);
        }
        // хвостові нулі не значать нічого: 1.0.0 = 1
        while (!out.isEmpty() && Long.valueOf(0L).equals(out.get(out.size() - 1))) out.remove(out.size() - 1);
        return out;
    }
}
