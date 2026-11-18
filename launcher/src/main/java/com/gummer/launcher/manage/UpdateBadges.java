/*
 * Copyright (C) 2021-2026 GummerCraft <http://gummercraft.fun>
 */

package com.gummer.launcher.manage;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.gummer.launcher.modrinth.MrpackUpdate;
import com.gummer.launcher.util.Json;
import com.skcraft.launcher.Instance;
import com.skcraft.launcher.Launcher;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 17.11.2026 (власник: «бейдж "є оновлення модів" на картці збірки - го»). Для кожної встановленої збірки у фоні:
 * скільки своїх модів гравця мають новішу версію на Modrinth ({@link ModUpdates}) і чи вийшла новіша версія модпака
 * Modrinth ({@link MrpackUpdate}). Перевірка не частіше разу на 6 годин на збірку (результат у {@value #FILE} теки
 * збірки, тож і після перезапуску лаунчера бейдж видно одразу, без мережі). Моди модпака сюди не йдуть: їх оновлює модпак.
 */
public final class UpdateBadges {

    private static final Logger log = Logger.getLogger(UpdateBadges.class.getName());
    private static final ObjectMapper MAPPER = Json.newMapper();
    public static final String FILE = "updates-check.json";
    private static final long TTL = 6 * 60 * 60 * 1000L;
    private static final Map<String, Info> INFO = new ConcurrentHashMap<String, Info>();
    private static volatile boolean running;
    private static final Set<String> ART_TRIED = java.util.Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());

    /** Що знайдено для збірки. */
    public static final class Info {
        /** Своїх модів з оновленням. */
        public final int mods;
        /** Номер новішої версії модпака, або null. */
        public final String pack;
        final long at;

        Info(int mods, String pack, long at) {
            this.mods = mods;
            this.pack = pack;
            this.at = at;
        }

        public boolean any() {
            return mods > 0 || pack != null;
        }
    }

    private UpdateBadges() {
    }

    /** Відоме для збірки (з пам'яті чи файлу), без мережі; null - ще не перевірялось. */
    public static Info of(Instance instance) {
        // рядки «Modrinth» і «+ Додати збірку» не мають теки: спершу isLocal.
        // 17.11.2026 ~22:50: викликається на кожне малювання кожного рядка списку, тож без диска: ключ за шляхом теки без
        // getDir (той щоразу створює теку), і «файлу немає» теж запам'ятовується (раніше isFile на кожен кадр, це була
        // половина роботи потоку інтерфейсу при перемиканні збірок у склі)
        if (instance == null || !instance.isLocal()) return null;
        File dir = instance.peekDir();
        if (dir == null) return null;
        String key = dir.getPath();
        Info i = INFO.get(key);
        if (i == null) {
            i = NONE;
            File f = new File(dir, FILE);
            if (f.isFile()) {
                try {
                    JsonNode n = MAPPER.readTree(f);
                    i = new Info(n.path("mods").asInt(0), n.path("pack").asText(null), n.path("at").asLong(0));
                } catch (Exception e) {
                    i = NONE;
                }
            }
            INFO.put(key, i);
        }
        return i == NONE ? null : i;
    }

    /** «Ще не перевірялось» у кеші (щоб не питати диск щоразу). */
    private static final Info NONE = new Info(0, null, 0);

    /** Збірка, для якої взагалі є що перевіряти. */
    static boolean eligible(Instance i) {
        // isInstalled не потрібен: модпак, поставлений і ще не запущений, теж має свої моди і mrpack.json у теці
        if (i == null || !i.isLocal() || i.getDir() == null || !i.getDir().isDirectory()) return false;
        if (MrpackUpdate.canUpdate(i)) return true;
        return ModrinthBrowse.loadersOf(ModrinthBrowse.Kind.MOD, i) != null && ModrinthBrowse.gameVersionOf(i) != null;
    }

    /**
     * Перевірити застарілі (старші за 6 годин) у фоні, потім {@code changed} у потоці інтерфейсу, якщо щось змінилось.
     * Повторний виклик, поки перевірка йде, нічого не робить.
     */
    public static void refresh(final Launcher launcher, final Runnable changed) {
        if (running || launcher == null) return;
        final List<Instance> todo = new ArrayList<Instance>();
        synchronized (launcher.getInstances()) {
            for (Instance i : launcher.getInstances().getInstances()) {
                if (!eligible(i)) continue;
                Info known = of(i);
                // 17.11.2026: збірки з уже відомим оновленням теж одразу: це нагріває кеш ModUpdates і MrpackUpdate, і у вікні
                // «Збірка» «Оновити все» та «Оновити до X» є без очікування мережі (власник: «десь секунда проходить»)
                if (known == null || System.currentTimeMillis() - known.at > TTL || known.any()) todo.add(i);
            }
        }
        // 17.11.2026 ~23:30 (власник: «на Stellar Horizons бекграунд не став»): фон зі скріна галереї доповнювався лише
        // для модпаків з першої сторінки пошуку Modrinth. Тепер для кожної встановленої збірки Modrinth без фону, раз за
        // запуск лаунчера, у фоні (модпак без галереї просто лишається з фоном завантажувача)
        final List<Instance> art = new ArrayList<Instance>();
        synchronized (launcher.getInstances()) {
            for (Instance i : launcher.getInstances().getInstances()) {
                if (i.isLocal() && i.isMrpack() && i.peekDir() != null && !new File(i.peekDir(), InstanceArt.BACKGROUND).isFile()
                        && ART_TRIED.add(i.peekDir().getPath())) art.add(i);
            }
        }
        if (!art.isEmpty()) {
            ModrinthBrowse.POOL.submit(() -> {
                boolean got = false;
                for (Instance i : art) {
                    try {
                        JsonNode info = com.gummer.launcher.modrinth.MrpackPacks.info(i);
                        String pid = info == null ? null : info.path("projectId").asText(null);
                        if (pid == null || !pid.matches("[A-Za-z0-9]+")) continue;
                        ModrinthBrowse.Hit h = new ModrinthBrowse.Hit();
                        h.id = pid;
                        ModrinthBrowse.savePackBackground(i.peekDir(), h);
                        if (new File(i.peekDir(), InstanceArt.BACKGROUND).isFile()) got = true;
                    } catch (Exception e) {
                        log.log(Level.FINE, "Pack background backfill failed for " + i.getName(), e);
                    }
                }
                if (got && changed != null) javax.swing.SwingUtilities.invokeLater(changed);
            });
        }
        if (todo.isEmpty()) return;
        running = true;
        ModrinthBrowse.POOL.submit(() -> {
            boolean any = false;
            try {
                for (Instance i : todo) {
                    Info before = of(i);
                    Info now = compute(i);
                    if (now == null) continue;
                    store(i, now);
                    if (before == null || before.mods != now.mods || !eq(before.pack, now.pack)) any = true;
                }
            } finally {
                running = false;
            }
            if (any && changed != null) javax.swing.SwingUtilities.invokeLater(changed);
        });
    }

    /** Перевірити одну збірку зараз (після оновлення модів чи модпака), у фоні. */
    public static void recheck(final Instance instance, final Runnable changed) {
        if (!eligible(instance)) return;
        ModrinthBrowse.POOL.submit(() -> {
            MrpackUpdate.forget(instance);
            Info now = compute(instance);
            if (now == null) return;
            store(instance, now);
            if (changed != null) javax.swing.SwingUtilities.invokeLater(changed);
        });
    }

    private static boolean eq(String a, String b) {
        return a == null ? b == null : a.equals(b);
    }

    private static Info compute(Instance i) {
        try {
            int mods = 0;
            String game = ModrinthBrowse.gameVersionOf(i);
            String[] loaders = ModrinthBrowse.loadersOf(ModrinthBrowse.Kind.MOD, i);
            // збірка Modrinth, поставлена до 17.11.2026 ~21:40, не має списку файлів модпака: які моди свої, невідомо,
            // тож моди не рахуються (інакше моди модпака йшли б у бейдж як свої)
            boolean unknownPack = i.isMrpack() && !new File(i.getDir(), com.gummer.launcher.modrinth.MrpackPacks.FILES).isFile();
            if (game != null && loaders != null && !unknownPack) {
                Set<String> pack = i.isMrpack() ? MrpackUpdate.packPaths(i) : java.util.Collections.<String>emptySet();
                List<File> own = new ArrayList<File>();
                for (InstanceMods.Mod m : InstanceMods.list(i)) {
                    if (m.source != InstanceMods.Source.OWN) continue;
                    if (pack.contains(("mods/" + m.name).toLowerCase(Locale.ROOT))) continue;
                    own.add(m.file);
                }
                if (!own.isEmpty()) mods = ModUpdates.check(own, game, loaders).size();
            }
            String packVersion = null;
            if (MrpackUpdate.canUpdate(i)) {
                MrpackUpdate.Check c = MrpackUpdate.check(i);
                if (c != null && c.newest != null) packVersion = c.newest.number;
            }
            return new Info(mods, packVersion, System.currentTimeMillis());
        } catch (Exception e) {
            log.log(Level.FINE, "Update check failed for " + i.getName(), e);
            return null;
        }
    }

    private static void store(Instance i, Info info) {
        INFO.put(i.peekDir().getPath(), info);
        try {
            ObjectNode n = MAPPER.createObjectNode();
            n.put("at", info.at);
            n.put("mods", info.mods);
            if (info.pack != null) n.put("pack", info.pack);
            MAPPER.writeValue(new File(i.getDir(), FILE), n);
        } catch (Exception e) {
            log.log(Level.FINE, "Could not save " + FILE, e);
        }
    }
}
