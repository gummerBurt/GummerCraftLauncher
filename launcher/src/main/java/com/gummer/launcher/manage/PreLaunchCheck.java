/*
 * Copyright (C) 2021-2026 GummerCraft <http://gummercraft.fun>
 */

package com.gummer.launcher.manage;

import com.skcraft.launcher.Instance;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 17.11.2026 ~23:30 (власник: «перевірка перед запуском: однакові моди двічі в одній збірці, нестача залежностей, мод
 * не під той завантажувач ... так, го»). Без мережі, з кешу {@link ModInfo}: що з увімкнених модів не дасть грі
 * запуститись. Застаріла версія залежності тут не рахується: її сам ставить {@link ModVersionFix} до цієї перевірки.
 */
public final class PreLaunchCheck {

    public enum Kind { DUPLICATE, WRONG_LOADER, WRONG_GAME, INCOMPATIBLE, MISSING }

    /** Одна проблема: що сказати гравцю і які файли вимкнути, щоб її прибрати. */
    public static final class Issue {
        public final Kind kind;
        /** Назва мода, якого стосується проблема. */
        public final String mod;
        /** Деталь: файли дублікатів, завантажувачі мода, яких модів бракує. */
        public final String detail;
        /** Ще одна деталь для тексту: версія гри збірки (WRONG_GAME). */
        public String extra = "";
        public final List<InstanceMods.Mod> disable = new ArrayList<InstanceMods.Mod>();
        /**
         * 26.11.2026: INCOMPATIBLE: мод, який оголосив несумісність саме з версіями (не «з усіма»), або null. Тоді
         * {@link CompatFix} може поставити іншу версію одного з двох замість вимкнення.
         */
        public InstanceMods.Mod by;

        Issue(Kind kind, String mod, String detail) {
            this.kind = kind;
            this.mod = mod;
            this.detail = detail;
        }
    }

    private PreLaunchCheck() {
    }

    /** Завантажувач збірки (fabric, quilt, forge, neoforge) або null: збірка без модів гравця, перевірка не потрібна. */
    static String loaderOf(Instance i) {
        if (i == null || i.isVanilla() || InstanceMods.packModsLocked(i)) return null;
        String[] l = ModrinthBrowse.loadersOf(ModrinthBrowse.Kind.MOD, i);
        return l == null || l.length == 0 ? null : l[0].toLowerCase(Locale.ROOT);
    }

    /** Чи завантажить завантажувач збірки jar з такими описами. Без опису (бібліотека) вважаємо, що так. */
    static boolean fits(Set<String> jar, String loader, boolean connector) {
        if (jar.isEmpty() || jar.contains(loader)) return true;
        if ("quilt".equals(loader) && jar.contains("fabric")) return true; // Quilt вантажить Fabric-моди
        if ("neoforge".equals(loader) && jar.contains("forge")) return true; // NeoForge 1.20.1 і багато портів
        if (connector && ("forge".equals(loader) || "neoforge".equals(loader)) && jar.contains("fabric")) return true; // Sinytra Connector
        return false;
    }

    /** Sinytra Connector (Fabric-моди на Forge / NeoForge): за id чи назвою файлу (його jar без звичного опису). */
    public static boolean isConnector(File file, ModInfo mi) {
        return mi.provides.contains("connector") || file.getName().toLowerCase(Locale.ROOT).startsWith("connector-");
    }

    /** Завантажувач збірки для перевірки або null (див. {@link #loaderOf}). */
    public static String loader(Instance i) {
        return loaderOf(i);
    }

    /**
     * Чи перевіряти «бракує обов'язкового мода» для цього jar. 18.11.2026: ті самі правила і для списку модів у вікні
     * «Збірка» (там з Connector було хибне «Не запуститься без: fabric-api»). Для Forge і NeoForge ні: вкладені моди
     * (jarjar) лаунчер не читає, а jar під кілька завантажувачів описаний з боку Fabric. З Connector ні для Fabric-модів:
     * їхні залежності дає сам Connector і Forgified Fabric API
     */
    public static boolean checkDepends(String loader, ModInfo mi, boolean connector) {
        if (loader == null) return false;
        if (connector && !mi.loaders.contains(loader)) return false;
        return "fabric".equals(loader) || "quilt".equals(loader);
    }

    public static List<Issue> find(Instance instance) {
        List<Issue> out = new ArrayList<Issue>();
        String loader = loaderOf(instance);
        if (loader == null) return out;
        Map<InstanceMods.Mod, ModInfo> info = new LinkedHashMap<InstanceMods.Mod, ModInfo>();
        boolean connector = false;
        for (InstanceMods.Mod m : InstanceMods.list(instance)) {
            if (!m.enabled) continue;
            ModInfo mi = ModInfo.of(m.file);
            info.put(m, mi);
            // Sinytra Connector (Fabric-моди на Forge / NeoForge): за id чи назвою файлу (його jar без звичного опису)
            if (isConnector(m.file, mi)) connector = true;
        }
        Set<InstanceMods.Mod> off = new HashSet<InstanceMods.Mod>();
        // 1. не під той завантажувач
        for (Map.Entry<InstanceMods.Mod, ModInfo> e : info.entrySet()) {
            ModInfo mi = e.getValue();
            if (fits(mi.loaders, loader, connector)) continue;
            Issue is = new Issue(Kind.WRONG_LOADER, mi.name, String.join(", ", mi.loaders));
            is.disable.add(e.getKey());
            out.add(is);
            off.add(e.getKey());
        }
        // 1б. 19.11.2026 (власник: «Мод не під ту версію Minecraft. -- давай»): умова версії гри з опису мода, за правилами
        // самого завантажувача (McRange). Лише точне «не підходить»: незвичну умову (снапшоти, літери) пропускаємо. Мод
        // під інший завантажувач (Connector) і jar під кілька завантажувачів у Forge-збірці (опис читається з боку Fabric)
        // не перевіряються
        String game = ModrinthBrowse.gameVersionOf(instance);
        if (McRange.parse(game) != null) {
            boolean fabricLike = "fabric".equals(loader) || "quilt".equals(loader);
            boolean forgeLike = "forge".equals(loader) || "neoforge".equals(loader);
            for (Map.Entry<InstanceMods.Mod, ModInfo> e : info.entrySet()) {
                if (off.contains(e.getKey())) continue;
                ModInfo mi = e.getValue();
                Boolean ok = null;
                String want = null;
                if (fabricLike && mi.loaders.contains("fabric") && !mi.mcFabric.isEmpty()) {
                    ok = McRange.fabric(mi.mcFabric, game);
                    want = String.join(" | ", mi.mcFabric);
                } else if (forgeLike && (mi.loaders.contains("forge") || mi.loaders.contains("neoforge")) && !mi.mcMaven.isEmpty()) {
                    ok = Boolean.TRUE;
                    for (String r : mi.mcMaven) {
                        Boolean x = McRange.maven(r, game);
                        if (x == null) ok = null;
                        else if (!x) {
                            ok = Boolean.FALSE;
                            want = r;
                            break;
                        }
                    }
                }
                if (ok == null || ok) continue;
                Issue is = new Issue(Kind.WRONG_GAME, mi.name, want);
                is.extra = game;
                is.disable.add(e.getKey());
                out.add(is);
                off.add(e.getKey());
            }
        }
        // 2. той самий мод двічі: лишається новіша версія
        Map<String, List<InstanceMods.Mod>> byId = new LinkedHashMap<String, List<InstanceMods.Mod>>();
        for (Map.Entry<InstanceMods.Mod, ModInfo> e : info.entrySet()) {
            if (off.contains(e.getKey()) || e.getValue().id == null) continue;
            // 18.11.2026 (власник: «на скріні відверта брехня - це не однакові моди, і якщо їх вимкнути, збірка не
            // запускається»): у Grand Theft Warzone (1.12.2) BasicsAddon і PTRLib обидва з шаблонним id «examplemod», а
            // ACsGuis у mcmod.info має id «sqript». За старим mcmod.info дублікати не шукаються взагалі
            if (e.getValue().loaders.contains("mcmod") || "examplemod".equalsIgnoreCase(e.getValue().id)) continue;
            String id = e.getValue().id.toLowerCase(Locale.ROOT);
            if (!byId.containsKey(id)) byId.put(id, new ArrayList<InstanceMods.Mod>());
            byId.get(id).add(e.getKey());
        }
        for (List<InstanceMods.Mod> same : byId.values()) {
            if (same.size() < 2) continue;
            InstanceMods.Mod keep = same.get(0);
            for (InstanceMods.Mod m : same) {
                String a = info.get(m).version, b = info.get(keep).version;
                if (a != null && b != null && ModVersionFix.compare(a, b) > 0) keep = m;
            }
            StringBuilder files = new StringBuilder();
            for (InstanceMods.Mod m : same) files.append(files.length() > 0 ? ", " : "").append(m.file.getName());
            Issue is = new Issue(Kind.DUPLICATE, info.get(keep).name, files.toString());
            for (InstanceMods.Mod m : same) {
                if (m == keep) continue;
                is.disable.add(m);
                off.add(m);
            }
            out.add(is);
        }
        // 2б. 19.11.2026 (власник: «Несумісні пари модів. -- теж імба, давай»): мод сам оголосив себе несумісним з іншим
        // (Fabric breaks, NeoForge incompatible), і такий мод стоїть: завантажувач гру не запустить. Вимикається той, з
        // яким несумісно (Sodium «ламає» OptiFabric: вимкнути OptiFabric). Плюс OptiFine, який сам нічого не оголошує, з
        // модами, що замінюють його рендер (Sodium, Embeddium, Rubidium, Iris, Oculus): вимикається OptiFine
        incompatible(info, off, out);
        // 3. бракує обов'язкового мода (серед того, що лишиться увімкненим)
        Set<String> have = new HashSet<String>();
        for (Map.Entry<InstanceMods.Mod, ModInfo> e : info.entrySet()) if (!off.contains(e.getKey())) have.addAll(e.getValue().provides);
        for (Map.Entry<InstanceMods.Mod, ModInfo> e : info.entrySet()) {
            if (off.contains(e.getKey())) continue;
            List<String> miss = new ArrayList<String>();
            // Collective у Forge-збірці «вимагав» fabric: хибна тривога гірша за пропуск
            if (!checkDepends(loader, e.getValue(), connector)) continue;
            for (String d : e.getValue().depends) if (!have.contains(d)) miss.add(d);
            if (miss.isEmpty()) continue;
            Issue is = new Issue(Kind.MISSING, e.getValue().name, String.join(", ", miss));
            is.disable.add(e.getKey());
            out.add(is);
        }
        return out;
    }

    /** Моди, які разом з OptiFine не працюють (замінюють його рендер). */
    private static final String[] NOT_WITH_OPTIFINE = {"sodium", "embeddium", "rubidium", "magnesium", "iris", "oculus"};

    private static void incompatible(Map<InstanceMods.Mod, ModInfo> info, Set<InstanceMods.Mod> off, List<Issue> out) {
        Map<String, InstanceMods.Mod> byId = new LinkedHashMap<String, InstanceMods.Mod>();
        for (Map.Entry<InstanceMods.Mod, ModInfo> e : info.entrySet()) {
            if (off.contains(e.getKey())) continue;
            for (String id : e.getValue().provides) if (!byId.containsKey(id)) byId.put(id, e.getKey());
        }
        for (Map.Entry<InstanceMods.Mod, ModInfo> e : info.entrySet()) {
            if (off.contains(e.getKey())) continue;
            ModInfo a = e.getValue();
            for (Map.Entry<String, List<String>> b : a.breaksFabric.entrySet()) {
                InstanceMods.Mod target = byId.get(b.getKey());
                if (target == null || target == e.getKey() || off.contains(target)) continue;
                Boolean hit = b.getValue().contains("*") ? Boolean.TRUE : McRange.fabric(b.getValue(), versionFor(info.get(target), b.getKey()));
                if (Boolean.TRUE.equals(hit)) conflict(info, target, a.name, off, out).by = b.getValue().contains("*") ? null : e.getKey();
            }
            for (Map.Entry<String, String> b : a.breaksMaven.entrySet()) {
                InstanceMods.Mod target = byId.get(b.getKey());
                if (target == null || target == e.getKey() || off.contains(target)) continue;
                String r = b.getValue().trim();
                Boolean hit = r.isEmpty() || r.equals("*") ? Boolean.TRUE : McRange.maven(r, versionFor(info.get(target), b.getKey()));
                if (Boolean.TRUE.equals(hit)) conflict(info, target, a.name, off, out).by = r.isEmpty() || r.equals("*") ? null : e.getKey();
            }
        }
        InstanceMods.Mod optifine = byId.get("optifine");
        if (optifine != null && !off.contains(optifine)) {
            for (String id : NOT_WITH_OPTIFINE) {
                InstanceMods.Mod other = byId.get(id);
                if (other == null || other == optifine || off.contains(other)) continue;
                conflict(info, optifine, info.get(other).name, off, out);
                break;
            }
        }
    }

    /** Версія мода, якщо умову записано саме про його головний id (вкладені моди мають свої версії), інакше null. */
    private static String versionFor(ModInfo mi, String id) {
        if (mi == null || mi.id == null || !mi.id.equalsIgnoreCase(id)) return null;
        return McRange.plainModVersion(mi.version);
    }

    private static Issue conflict(Map<InstanceMods.Mod, ModInfo> info, InstanceMods.Mod disable, String with,
                                  Set<InstanceMods.Mod> off, List<Issue> out) {
        Issue is = new Issue(Kind.INCOMPATIBLE, info.get(disable).name, with);
        is.disable.add(disable);
        out.add(is);
        off.add(disable);
        return is;
    }

    /**
     * 26.11.2026: чи оголосив мод {@code a} несумісність з модом {@code b} саме такої версії (Fabric breaks, NeoForge
     * incompatible), за тими самими правилами, що і перевірка. Для {@link CompatFix}: чи підходить інша версія.
     */
    static boolean breaks(ModInfo a, ModInfo b) {
        if (a == null || b == null) return false;
        for (Map.Entry<String, List<String>> e : a.breaksFabric.entrySet()) {
            if (!b.provides.contains(e.getKey())) continue;
            Boolean hit = e.getValue().contains("*") ? Boolean.TRUE : McRange.fabric(e.getValue(), versionFor(b, e.getKey()));
            if (Boolean.TRUE.equals(hit)) return true;
        }
        for (Map.Entry<String, String> e : a.breaksMaven.entrySet()) {
            if (!b.provides.contains(e.getKey())) continue;
            String r = e.getValue().trim();
            Boolean hit = r.isEmpty() || r.equals("*") ? Boolean.TRUE : McRange.maven(r, versionFor(b, e.getKey()));
            if (Boolean.TRUE.equals(hit)) return true;
        }
        return false;
    }

    /** Вимкнути файли, які пропонують проблеми (лишаються в теці як вимкнені, вмикаються у вікні «Збірка»). */
    public static int disableAll(Instance instance, List<Issue> issues) {
        int n = 0;
        Set<File> done = new HashSet<File>();
        for (Issue is : issues) {
            for (InstanceMods.Mod m : is.disable) {
                if (!done.add(m.file)) continue;
                try {
                    InstanceMods.setEnabled(instance, m, false);
                    n++;
                } catch (Exception ignored) {
                    // файл зайнятий чи зник: лишається як є
                }
            }
        }
        return n;
    }
}
