/*
 * Copyright (C) 2021-2026 GummerCraft <http://gummercraft.fun>
 */

package com.gummer.launcher.manage;

import com.skcraft.launcher.Instance;
import com.skcraft.launcher.Launcher;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * 17.11.2026 (власник: «Світи: бекап світу, копіювання світу в іншу збірку, "Продовжити останній світ" - ок, го»).
 * Світи збірки ({@code saves/*} з {@code level.dat}): бекап у zip, відновлення з бекапу (завжди в нову теку, наявний
 * світ не перезаписується), копія в іншу збірку і разовий запит «одразу в цей світ» для наступного запуску
 * ({@code --quickPlaySingleplayer}, 1.20+; див. Runner.addQuickJoin).
 *
 * <p>Бекапи лежать поза збірками: {@code <тека лаунчера>/world-backups/<збірка>/<світ> <дата>.zip}, тож переживають
 * видалення збірки.</p>
 */
public final class Worlds {

    private static final Logger log = Logger.getLogger(Worlds.class.getName());
    public static final String BACKUPS = "world-backups";
    /** Тека збірки -> назва теки світу для наступного запуску. */
    private static final Map<String, String> QUICK = new ConcurrentHashMap<String, String>();

    private Worlds() {
    }

    public static File savesOf(Instance instance) {
        return new File(instance.getContentDir(), "saves");
    }

    /** Світи збірки від останнього зіграного. */
    public static List<File> list(Instance instance) {
        File[] worlds = savesOf(instance).listFiles(f -> f.isDirectory() && new File(f, "level.dat").isFile());
        List<File> out = new ArrayList<File>();
        if (worlds == null) return out;
        Arrays.sort(worlds, (a, b) -> Long.compare(playedAt(b), playedAt(a)));
        out.addAll(Arrays.asList(worlds));
        return out;
    }

    public static long playedAt(File world) {
        return new File(world, "level.dat").lastModified();
    }

    /** Останній зіграний світ збірки, або null. */
    public static File last(Instance instance) {
        if (instance == null || !instance.isLocal() || !instance.isInstalled()) return null;
        List<File> l = list(instance);
        return l.isEmpty() ? null : l.get(0);
    }

    /**
     * Чи вміє гра цієї збірки відкрити світ одразу ({@code --quickPlaySingleplayer}): Minecraft 1.20 і новіші, у тому
     * числі нові номери 26.x. Знімки (23w..) і невідоме: ні.
     */
    public static boolean canQuickPlay(Instance instance) {
        String v = ModrinthBrowse.gameVersionOf(instance);
        if (v == null) v = instance.getVersion();
        if (v == null) return false;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("^(\\d+)\\.(\\d+)").matcher(v);
        if (!m.find()) return false;
        int major = Integer.parseInt(m.group(1)), minor = Integer.parseInt(m.group(2));
        return major > 1 || minor >= 20;
    }

    /** Наступний запуск цієї збірки відкриє цей світ (разово). */
    public static void requestQuickPlay(Instance instance, String worldFolder) {
        if (instance != null && worldFolder != null) QUICK.put(instance.getDir().getAbsolutePath(), worldFolder);
    }

    /** Забрати разовий запит (Runner перед запуском), або null. */
    public static String takeQuickPlay(Instance instance) {
        return instance == null || instance.getDir() == null ? null : QUICK.remove(instance.getDir().getAbsolutePath());
    }

    /** Тека бекапів цієї збірки. */
    public static File backupsDir(Launcher launcher, Instance instance) {
        String name = instance.getName().replaceAll("[^A-Za-z0-9._-]+", "_");
        return new File(new File(launcher.getBaseDir(), BACKUPS), name);
    }

    /** Бекапи світів цієї збірки, від новішого. */
    public static List<File> backups(Launcher launcher, Instance instance) {
        File[] zips = backupsDir(launcher, instance).listFiles(f -> f.isFile() && f.getName().toLowerCase(Locale.ROOT).endsWith(".zip"));
        List<File> out = new ArrayList<File>();
        if (zips == null) return out;
        Arrays.sort(zips, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
        out.addAll(Arrays.asList(zips));
        return out;
    }

    /** Зробити бекап світу в zip (у фоні). Файл блокування гри ({@code session.lock}) не копіюється. */
    public static File backup(Launcher launcher, Instance instance, File world) throws IOException {
        File dir = backupsDir(launcher, instance);
        dir.mkdirs();
        String stamp = new SimpleDateFormat("yyyy-MM-dd HH-mm", Locale.ROOT).format(new Date());
        String base = world.getName().replaceAll("[\\\\/:*?\"<>|]+", "_") + " " + stamp;
        File zip = new File(dir, base + ".zip");
        for (int n = 2; zip.exists(); n++) zip = new File(dir, base + " (" + n + ").zip");
        File part = new File(dir, "." + zip.getName() + ".part");
        try (ZipOutputStream out = new ZipOutputStream(new FileOutputStream(part))) {
            zipDir(world, world.getName() + "/", out);
        } catch (IOException e) {
            part.delete();
            throw e;
        }
        Files.move(part.toPath(), zip.toPath(), StandardCopyOption.ATOMIC_MOVE);
        log.info("World backup: " + world + " -> " + zip + " (" + zip.length() / 1024 + " KB)");
        return zip;
    }

    private static void zipDir(File dir, String prefix, ZipOutputStream out) throws IOException {
        File[] kids = dir.listFiles();
        if (kids == null) return;
        byte[] buf = new byte[65536];
        for (File k : kids) {
            if (k.getName().equals("session.lock")) continue;
            if (k.isDirectory()) {
                zipDir(k, prefix + k.getName() + "/", out);
                continue;
            }
            out.putNextEntry(new ZipEntry(prefix + k.getName()));
            try (InputStream in = new FileInputStream(k)) {
                for (int n; (n = in.read(buf)) > 0; ) out.write(buf, 0, n);
            }
            out.closeEntry();
        }
    }

    /**
     * Відновити світ з бекапу в збірку: завжди нова тека ({@code <світ> (бекап)}, далі з номером), наявний світ не
     * перезаписується. Повертає теку світу.
     */
    public static File restore(Instance instance, File zip) throws IOException {
        File saves = savesOf(instance);
        saves.mkdirs();
        String root = null;
        try (ZipInputStream in = new ZipInputStream(new FileInputStream(zip))) {
            for (ZipEntry e; (e = in.getNextEntry()) != null; ) {
                int slash = e.getName().indexOf('/');
                if (slash > 0) {
                    root = e.getName().substring(0, slash);
                    break;
                }
            }
        }
        if (root == null) throw new IOException("Not a world backup: " + zip.getName());
        File target = unique(saves, root + " (" + com.skcraft.launcher.util.SharedLocale.tr("manage.worlds.backupSuffix") + ")");
        File part = new File(saves, "." + target.getName() + ".part");
        deleteTree(part);
        try (ZipInputStream in = new ZipInputStream(new FileInputStream(zip))) {
            byte[] buf = new byte[65536];
            for (ZipEntry e; (e = in.getNextEntry()) != null; ) {
                if (e.isDirectory() || !e.getName().startsWith(root + "/")) continue;
                File f = safe(part, e.getName().substring(root.length() + 1));
                f.getParentFile().mkdirs();
                try (FileOutputStream o = new FileOutputStream(f)) {
                    for (int n; (n = in.read(buf)) > 0; ) o.write(buf, 0, n);
                }
            }
        } catch (IOException e) {
            deleteTree(part);
            throw e;
        }
        if (!new File(part, "level.dat").isFile()) {
            deleteTree(part);
            throw new IOException("No level.dat in " + zip.getName());
        }
        Files.move(part.toPath(), target.toPath());
        log.info("World restored: " + zip + " -> " + target);
        return target;
    }

    /** Скопіювати світ в іншу збірку (нова тека, якщо така назва вже є). Повертає теку світу там. */
    public static File copyTo(File world, Instance target) throws IOException {
        File saves = savesOf(target);
        saves.mkdirs();
        File to = unique(saves, world.getName());
        File part = new File(saves, "." + to.getName() + ".part");
        deleteTree(part);
        try {
            copyTree(world, part);
        } catch (IOException e) {
            deleteTree(part);
            throw e;
        }
        Files.move(part.toPath(), to.toPath());
        log.info("World copied: " + world + " -> " + to);
        return to;
    }

    /**
     * Порівняти версії гри: від'ємне, якщо {@code a} старіша за {@code b}. Світ з новішої гри в старішій може
     * зіпсуватись, тож вікно попереджає. Невідомі версії: 0.
     */
    public static int compareGame(String a, String b) {
        if (a == null || b == null) return 0;
        String[] x = a.split("[.-]"), y = b.split("[.-]");
        for (int i = 0; i < Math.max(x.length, y.length); i++) {
            int p = i < x.length ? num(x[i]) : 0, q = i < y.length ? num(y[i]) : 0;
            if (p < 0 || q < 0) return 0;
            if (p != q) return p < q ? -1 : 1;
        }
        return 0;
    }

    private static int num(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static void copyTree(File from, File to) throws IOException {
        if (from.isDirectory()) {
            if (!to.mkdirs() && !to.isDirectory()) throw new IOException("Cannot create " + to);
            File[] kids = from.listFiles();
            if (kids != null) {
                for (File k : kids) {
                    if (k.getName().equals("session.lock")) continue;
                    copyTree(k, new File(to, k.getName()));
                }
            }
        } else {
            Files.copy(from.toPath(), to.toPath(), StandardCopyOption.COPY_ATTRIBUTES);
        }
    }

    private static File unique(File dir, String name) {
        String clean = name.replaceAll("[\\\\/:*?\"<>|]+", "_").trim();
        if (clean.isEmpty()) clean = "World";
        File f = new File(dir, clean);
        for (int n = 2; f.exists(); n++) f = new File(dir, clean + " " + n);
        return f;
    }

    private static File safe(File base, String rel) throws IOException {
        File f = new File(base, rel).getCanonicalFile();
        if (!f.toPath().startsWith(base.getCanonicalFile().toPath())) throw new IOException("Bad path in the backup: " + rel);
        return f;
    }

    private static void deleteTree(File f) {
        File[] kids = f.listFiles();
        if (kids != null) for (File k : kids) deleteTree(k);
        f.delete();
    }
}
