/*
 * Copyright (C) 2021-2026 GummerCraft <http://gummercraft.fun>
 */

package com.gummer.launcher.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.gummer.launcher.model.java.JavaFile;
import com.gummer.launcher.model.java.JavaFileDownload;
import com.gummer.launcher.model.java.JavaManifest;
import com.gummer.launcher.model.java.JavaVersionManifest;
import com.skcraft.launcher.Configuration;
import com.skcraft.launcher.Instance;
import com.skcraft.launcher.InstanceList;
import com.skcraft.launcher.Launcher;
import com.skcraft.launcher.persistence.Persistence;
import com.skcraft.launcher.util.FileUtils;
import com.skcraft.launcher.util.HttpRequest;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.IntConsumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 1.36.3 (19.11.2026, власник: «зроби автооновлення джави опціональним ... окрему плитку "Оновлення Java" ... кнопки
 * оновити/відкатити»). Плитка в налаштуваннях «Java та пам'ять» і все, що за нею:
 * <ul>
 * <li>{@link #list}: Java самого лаунчера і кожна встановлена Java гри ({@code runtime/<версія>/<ОС>}), яка версія
 * стоїть, яка на CDN, які збірки її беруть.</li>
 * <li>{@link #update}: перед оновленням тека копіюється в {@code runtime-prev/<версія>/<ОС>} (для відкату), далі
 * докачуються лише змінені файли за маніфестом CDN. Помилка посередині: файли повертаються з копії.</li>
 * <li>{@link #rollback}: тека і копія міняються місцями, ця Java закріплюється ({@link Configuration#getJavaPinned}),
 * щоб автооновлення і оновлення збірки не поставили нову назад. «Оновити» знімає закріплення.</li>
 * <li>{@link #keepInstalled}: автооновлення вимкнено ({@link Configuration#isJavaAutoUpdate}) або Java закріплена:
 * наявну Java гри не чіпаємо (ні примусове оновлення перед запуском, ні звірка при оновленні збірки). Java, якої ще
 * немає, ставиться як завжди.</li>
 * </ul>
 * Гра, що працює на цій Java, тримає її файли (Windows), тож оновлення і відкат спершу перевіряють {@link #inUse}.
 */
public final class JavaUpdates {

    public static final String PREV_DIR = "runtime-prev";
    /** Поруч з {@link JavaSecurity#MARKER}: назва версії з маніфесту CDN (для показу, коли в {@code release} її немає). */
    public static final String VERSION_FILE = ".gc-runtime-version";
    private static final Logger log = Logger.getLogger(JavaUpdates.class.getName());

    private JavaUpdates() {
    }

    /** Рядок плитки. */
    public static final class Entry {
        /** null для Java лаунчера. */
        public String component;
        public String os;
        public File dir;
        public String installed;
        /** Версія на CDN (для Java лаунчера: minJava з installer.json), null: невідомо (немає мережі). */
        public String latest;
        public boolean updateAvailable;
        public boolean canUpdate;
        public boolean canRollback;
        public String prevVersion;
        public boolean pinned;
        /** Java лаунчера, яку лаунчер сам не оновлює (портативний zip, системна Java, не Windows). */
        public boolean external;
        public List<String> usedBy = new ArrayList<String>();

        public boolean isLauncher() {
            return component == null;
        }
    }

    public static String key(String component, String os) {
        return component + "/" + os;
    }

    public static boolean pinned(Launcher launcher, String component, String os) {
        Set<String> p = launcher.getConfig().getJavaPinned();
        return p != null && p.contains(key(component, os));
    }

    /** Наявну Java гри не оновлювати (автооновлення вимкнено або гравець відкотив її). */
    public static boolean keepInstalled(Launcher launcher, String component, String os) {
        return !launcher.getConfig().isJavaAutoUpdate() || pinned(launcher, component, os);
    }

    private static void setPinned(Launcher launcher, String component, String os, boolean on) {
        Configuration c = launcher.getConfig();
        Set<String> p = c.getJavaPinned() != null ? new LinkedHashSet<String>(c.getJavaPinned()) : new LinkedHashSet<String>();
        if (on ? p.add(key(component, os)) : p.remove(key(component, os))) {
            c.setJavaPinned(p);
            Persistence.commitAndForget(c);
        }
    }

    /** Усі рядки плитки. Мережа (installer.json): лише тут, тож викликати не з потоку Swing. */
    public static List<Entry> list(Launcher launcher) {
        List<Entry> out = new ArrayList<Entry>();
        out.add(launcherEntry());

        InstanceList il = launcher.getInstances();
        File root = JavaPlace.root(launcher);
        File[] comps = root.listFiles(File::isDirectory);
        if (comps == null) comps = new File[0];
        java.util.Arrays.sort(comps, (a, b) -> Integer.compare(major(a.getName()), major(b.getName())));
        for (File comp : comps) {
            File[] oss = comp.listFiles(File::isDirectory);
            if (oss == null) continue;
            for (File osDir : oss) {
                if (!BundledJava.hasJavaBinary(osDir)) continue;
                Entry e = new Entry();
                e.component = comp.getName();
                e.os = osDir.getName();
                e.dir = osDir;
                e.installed = installedVersion(osDir);
                JavaManifest want = manifestFor(launcher, e.component, e.os);
                if (want != null && want.getVersion() != null) e.latest = want.getVersion().getName();
                String id = JavaSecurity.id(want);
                String have = read(new File(osDir, JavaSecurity.MARKER));
                e.updateAvailable = id != null && !id.equals(have);
                // 20.11.2026 (власник зі скріном: «Java 8 1.8.0_442, Є нова: 1.8.0_442»): маркера немає (Java ставив лаунчер до
                // 1.36.1) або маркер від старого маніфесту з тією ж версією. Якщо версія та сама, звіряємо файли з маніфестом: усі
                // збігаються, значить Java вже актуальна, пишемо маркер і не пропонуємо «оновити» на те саме.
                if (e.updateAvailable && e.latest != null && e.latest.equals(e.installed) && filesMatch(osDir, want)) {
                    JavaSecurity.writeMarker(osDir, want);
                    e.updateAvailable = false;
                }
                e.canUpdate = want != null;
                File prev = prevDir(launcher, e.component, e.os);
                e.canRollback = BundledJava.hasJavaBinary(prev);
                if (e.canRollback) e.prevVersion = installedVersion(prev);
                e.pinned = pinned(launcher, e.component, e.os);
                for (int i = 0; i < il.size(); i++) {
                    Instance inst = il.get(i);
                    if (inst.isInstalled() && e.component.equalsIgnoreCase(inst.getJavaRuntime())) e.usedBy.add(inst.getTitle());
                }
                Collections.sort(e.usedBy, String.CASE_INSENSITIVE_ORDER);
                out.add(e);
            }
        }
        return out;
    }

    private static Entry launcherEntry() {
        Entry e = new Entry();
        e.installed = System.getProperty("java.version", "?");
        File app = LauncherJreUpdate.appDir();
        e.external = app == null;
        if (app != null) {
            e.dir = new File(app, "jre");
            JsonNode info = LauncherJreUpdate.fetchInfo();
            if (info != null) {
                String min = info.path("minJava").asText("");
                if (!min.isEmpty()) {
                    e.latest = LauncherJreUpdate.compare(e.installed, min) >= 0 ? e.installed : min;
                    e.updateAvailable = LauncherJreUpdate.compare(e.installed, min) < 0;
                    e.canUpdate = e.updateAvailable;
                }
            }
        }
        return e;
    }

    static int major(String component) {
        try {
            return Integer.parseInt(component.replaceAll("[^0-9].*$", ""));
        } catch (NumberFormatException e) {
            return 999;
        }
    }

    /**
     * Версія Java в теці: {@code JAVA_VERSION} з файлу {@code release} (так само назване, як версія в маніфесті CDN:
     * 17.0.21.1, 1.8.0_442), інакше {@link #VERSION_FILE}.
     */
    static String installedVersion(File dir) {
        File rel = new File(dir, "release");
        if (rel.isFile()) {
            try {
                for (String l : Files.readAllLines(rel.toPath(), StandardCharsets.UTF_8)) {
                    if (!l.startsWith("JAVA_VERSION=")) continue;
                    String v = unq(l.substring(13));
                    if (!v.isEmpty()) return v;
                }
            } catch (IOException | RuntimeException ignored) {
                // далі запасний варіант
            }
        }
        String v = read(new File(dir, VERSION_FILE));
        return v.isEmpty() ? "?" : v;
    }

    private static String unq(String s) {
        s = s.trim();
        return s.length() >= 2 && s.startsWith("\"") && s.endsWith("\"") ? s.substring(1, s.length() - 1) : s;
    }

    /** Усі файли маніфесту Java є в теці і збігаються за sha1 (зайві файли не рахуються). Будь-яка помилка: false. */
    static boolean filesMatch(File dir, JavaManifest want) {
        try {
            JavaVersionManifest vm = HttpRequest.get(HttpRequest.url(want.getManifest().getUrl())).execute()
                    .expectResponseCode(200).returnContent().asJson(JavaVersionManifest.class);
            if (vm == null || vm.files == null || vm.files.isEmpty()) return false;
            Path base = dir.getAbsoluteFile().toPath();
            for (Map.Entry<String, JavaFile> en : vm.files.entrySet()) {
                JavaFile jf = en.getValue();
                if (!"file".equals(jf.getType())) continue;
                JavaFileDownload raw = jf.getDownloads() != null ? jf.getDownloads().getRaw() : null;
                if (raw == null || raw.getHash() == null) return false;
                File f = base.resolve(en.getKey()).normalize().toFile();
                String have;
                try {
                    have = f.isFile() ? FileUtils.getShaHash(f) : null;
                } catch (IOException e) {
                    // 1.41.3: копія системної DLL, яку не дає прочитати антивірус, на Windows 10+ не заважає Java
                    if (com.gummer.launcher.install.SystemDlls.optional(f)) continue;
                    throw e;
                }
                if (have == null || !raw.getHash().equalsIgnoreCase(have)) return false;
            }
            log.info("Java in " + dir + " matches the CDN manifest file by file, marker written");
            return true;
        } catch (Exception e) {
            log.log(Level.FINE, "Can't compare Java files in " + dir, e);
            return false;
        }
    }

    static String read(File f) {
        try {
            return f.isFile() ? new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8).trim() : "";
        } catch (IOException e) {
            return "";
        }
    }

    static JavaManifest manifestFor(Launcher launcher, String component, String os) {
        try {
            for (Map.Entry<String, JavaManifest[]> e : launcher.getInstances().getJavaVersionsFor(os).entrySet()) {
                if (e.getKey().equalsIgnoreCase(component)) {
                    return e.getValue() != null && e.getValue().length > 0 ? e.getValue()[0] : null;
                }
            }
        } catch (Exception e) {
            log.log(Level.FINE, "No Java manifest for " + component + "/" + os, e);
        }
        return null;
    }

    static File prevDir(Launcher launcher, String component, String os) {
        return new File(launcher.getBaseDir(), PREV_DIR + "/" + component + "/" + os);
    }

    /**
     * Чи працює зараз гра на цій Java. Windows не дає відкрити на запис завантажений exe чи DLL (перейменувати дає, тож
     * перейменування тут не годиться: перевірено 19.11), тож пробуємо відкрити на запис {@code jvm.dll} і {@code java(w).exe},
     * нічого не пишучи. На Linux і Mac файли не блокуються: false.
     */
    public static boolean inUse(File dir) {
        String[] names = {"bin/server/jvm.dll", "bin/client/jvm.dll", "bin/java.exe", "bin/javaw.exe"};
        for (String n : names) {
            File f = new File(dir, n);
            if (!f.isFile()) continue;
            try (java.io.RandomAccessFile raf = new java.io.RandomAccessFile(f, "rw")) {
                // відкрилось: файл ніхто не тримає
            } catch (IOException e) {
                return true;
            }
        }
        return false;
    }

    /**
     * Оновити Java гри до маніфесту CDN (кнопка «Оновити»): копія в {@link #PREV_DIR}, докачка змінених файлів,
     * маркер, закріплення знімається. {@code progress}: відсотки 0..100.
     */
    public static void update(Launcher launcher, String component, String os, IntConsumer progress) throws Exception {
        JavaManifest want = manifestFor(launcher, component, os);
        if (want == null || want.getManifest() == null) throw new IOException("No CDN manifest for Java " + key(component, os));
        File dir = BundledJava.getJavaDir(launcher, component, os);
        if (inUse(dir)) throw new InUseException();
        JavaVersionManifest vm = HttpRequest.get(HttpRequest.url(want.getManifest().getUrl())).execute()
                .expectResponseCode(200).returnContent().asJson(JavaVersionManifest.class);

        File prev = prevDir(launcher, component, os);
        boolean backedUp = false;
        if (BundledJava.hasJavaBinary(dir)) {
            deleteTree(prev);
            copyTree(dir, prev);
            backedUp = true;
        }
        try {
            sync(dir, vm, progress);
        } catch (Exception e) {
            if (backedUp) {
                log.log(Level.WARNING, "Java " + key(component, os) + " update failed, restoring the previous files", e);
                try {
                    mirror(prev, dir);
                } catch (IOException r) {
                    log.log(Level.WARNING, "Restore of " + dir + " failed", r);
                }
            }
            throw e;
        }
        JavaSecurity.writeMarker(dir, want);
        writeVersion(dir, want);
        setPinned(launcher, component, os, false);
        log.info("Java " + key(component, os) + " updated to " + (want.getVersion() != null ? want.getVersion().getName() : "?")
                + (backedUp ? ", previous kept in " + prev : ""));
    }

    /** Відкат (кнопка «Відкатити»): тека і копія міняються місцями, Java закріплюється. */
    public static void rollback(Launcher launcher, String component, String os) throws Exception {
        File dir = BundledJava.getJavaDir(launcher, component, os);
        File prev = prevDir(launcher, component, os);
        if (!BundledJava.hasJavaBinary(prev)) throw new IOException("No previous Java for " + key(component, os));
        if (inUse(dir)) throw new InUseException();
        File tmp = new File(dir.getParentFile(), os + ".gc-swap");
        deleteTree(tmp);
        try {
            Files.move(dir.toPath(), tmp.toPath());
        } catch (java.nio.file.AccessDeniedException e) {
            throw new InUseException(); // Windows: теку тримає запущена гра (або відкритий у ній файл)
        }
        try {
            prev.getParentFile().mkdirs();
            Files.move(prev.toPath(), dir.toPath());
        } catch (IOException e) {
            Files.move(tmp.toPath(), dir.toPath());
            throw e;
        }
        Files.move(tmp.toPath(), prev.toPath());
        setPinned(launcher, component, os, true);
        log.info("Java " + key(component, os) + " rolled back to " + installedVersion(dir) + ", pinned; newer kept in " + prev);
    }

    /** Гра запущена на цій Java. */
    public static final class InUseException extends IOException {
        public InUseException() {
            super("Java is in use by a running game");
        }
    }

    public static void writeVersion(File dir, JavaManifest m) {
        if (m == null || m.getVersion() == null || m.getVersion().getName() == null) return;
        try {
            Files.write(new File(dir, VERSION_FILE).toPath(), m.getVersion().getName().getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            log.log(Level.FINE, "Can't write " + VERSION_FILE, e);
        }
    }

    /** Теку до стану маніфесту: зайве прибрати (наші маркери лишаються), змінене докачати зі звіркою sha1. */
    private static void sync(File dir, JavaVersionManifest vm, IntConsumer progress) throws Exception {
        final Path base = dir.getAbsoluteFile().toPath();
        dir.mkdirs();
        final Set<Path> keep = new HashSet<Path>();
        List<JavaFile> files = new ArrayList<JavaFile>();
        for (Map.Entry<String, JavaFile> en : vm.files.entrySet()) {
            JavaFile jf = en.getValue();
            jf.setPath(en.getKey());
            files.add(jf);
            keep.add(base.resolve(en.getKey()).normalize());
        }
        keep.add(base.resolve(JavaSecurity.MARKER));
        keep.add(base.resolve(VERSION_FILE));
        Files.walkFileTree(base, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path f, BasicFileAttributes a) throws IOException {
                if (!keep.contains(f.normalize())) Files.delete(f);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path d, IOException exc) throws IOException {
                if (!d.equals(base) && !keep.contains(d.normalize())) {
                    try (java.util.stream.Stream<Path> s = Files.list(d)) {
                        if (!s.findAny().isPresent()) Files.delete(d);
                    }
                }
                return FileVisitResult.CONTINUE;
            }
        });

        long total = 0, done = 0;
        for (JavaFile jf : files) {
            JavaFileDownload raw = jf.getDownloads() != null ? jf.getDownloads().getRaw() : null;
            if ("file".equals(jf.getType()) && raw != null) total += Math.max(1, raw.getSize());
        }
        for (JavaFile jf : files) {
            if (Thread.interrupted()) throw new InterruptedException();
            File out = base.resolve(jf.getPath()).normalize().toFile();
            if (!out.toPath().startsWith(base)) throw new IOException("Bad path in Java manifest: " + jf.getPath());
            if ("directory".equals(jf.getType())) {
                if (out.isFile()) out.delete();
                out.mkdirs();
                continue;
            }
            if (!"file".equals(jf.getType())) continue;
            JavaFileDownload raw = jf.getDownloads() != null ? jf.getDownloads().getRaw() : null;
            if (raw == null) throw new IOException("No raw download for " + jf.getPath());
            try {
                if (!out.isFile() || raw.getHash() != null && !raw.getHash().equalsIgnoreCase(FileUtils.getShaHash(out))) {
                    out.getParentFile().mkdirs();
                    File part = new File(out.getParentFile(), out.getName() + ".gc-part");
                    HttpRequest.get(new URL(raw.getUrl())).execute().expectResponseCode(200).saveContent(part);
                    if (raw.getHash() != null && !raw.getHash().equalsIgnoreCase(FileUtils.getShaHash(part))) {
                        part.delete();
                        throw new IOException("sha1 mismatch for " + jf.getPath());
                    }
                    Files.move(part.toPath(), out.toPath(), StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (IOException e) {
                // 1.41.3: копію системної DLL Windows чіпає антивірус; на Windows 10+ Java бере свою з System32
                if (!com.gummer.launcher.install.SystemDlls.optional(out)) throw e;
                com.gummer.launcher.install.SystemDlls.skipped(out, e);
                new File(out.getParentFile(), out.getName() + ".gc-part").delete();
            }
            if (jf.isExecutable()) out.setExecutable(true, false);
            done += Math.max(1, raw.getSize());
            if (progress != null && total > 0) progress.accept((int) (done * 100 / total));
        }
    }

    static void copyTree(File from, File to) throws IOException {
        final Path src = from.toPath(), dst = to.toPath();
        Files.walkFileTree(src, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path d, BasicFileAttributes a) throws IOException {
                Files.createDirectories(dst.resolve(src.relativize(d).toString()));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path f, BasicFileAttributes a) throws IOException {
                Path t = dst.resolve(src.relativize(f).toString());
                Files.copy(f, t, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    /** Відновлення після невдалого оновлення: усе з копії поверх (що не вдалось, пропускаємо), зайве прибрати. */
    static void mirror(File from, File to) throws IOException {
        final Path src = from.toPath(), dst = to.toPath();
        final Set<Path> keep = new HashSet<Path>();
        Files.walkFileTree(src, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path d, BasicFileAttributes a) throws IOException {
                Path t = dst.resolve(src.relativize(d).toString());
                Files.createDirectories(t);
                keep.add(t.normalize());
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path f, BasicFileAttributes a) {
                Path t = dst.resolve(src.relativize(f).toString());
                keep.add(t.normalize());
                try {
                    Files.copy(f, t, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
                } catch (IOException e) {
                    log.warning("Restore: can't copy " + t + ": " + e);
                }
                return FileVisitResult.CONTINUE;
            }
        });
        Files.walkFileTree(dst, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path f, BasicFileAttributes a) {
                if (!keep.contains(f.normalize())) f.toFile().delete();
                return FileVisitResult.CONTINUE;
            }
        });
    }

    static void deleteTree(File f) throws IOException {
        if (!f.exists()) return;
        Files.walkFileTree(f.toPath(), new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path p, BasicFileAttributes a) throws IOException {
                Files.delete(p);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path d, IOException exc) throws IOException {
                Files.delete(d);
                return FileVisitResult.CONTINUE;
            }
        });
    }
}
