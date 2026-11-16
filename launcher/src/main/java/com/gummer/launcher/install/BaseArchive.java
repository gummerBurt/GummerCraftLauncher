/*
 * Copyright (C) 2021-2026 GummerCraft <http://gummercraft.fun>
 */

package com.gummer.launcher.install;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.gummer.launcher.stats.DownloadStats;
import com.skcraft.launcher.Launcher;
import com.skcraft.launcher.model.minecraft.VersionManifest;
import com.skcraft.launcher.util.HttpRequest;
import com.skcraft.launcher.util.SharedLocale;
import com.skcraft.concurrency.ProgressObservable;
import lombok.Data;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * 16.11.2026: «база Minecraft» архівом при першій установці версії гри. Ассети (тисячі дрібних файлів) і бібліотеки
 * Minecraft лаунчер качає одним чи кількома архівами з нашого CDN замість тисяч запитів до Mojang, розпаковує і
 * перевіряє кожен файл. Оновлення і все, чого в архіві немає, далі йдуть пофайлово, як раніше.
 *
 * <p><b>Запобіжники (вимога власника: обрив інтернету чи світла не лишає сміття і не змушує качати заново):</b></p>
 * <ul>
 *   <li>Частина архіву качається в {@code temp/base/<версія>-<n>.zip.part} поруч з описом {@code .json}
 *       (адреса, розмір, sha256). Обрив: наступна спроба (і після перезапуску лаунчера) докачує з місця обриву
 *       через Range. Інший архів на сервері (опис не збігся): частина видаляється, качання з нуля.</li>
 *   <li>Повністю скачана частина звіряється за sha256; не збіглась: видаляється і качається ще раз з нуля, далі
 *       відмова від архіву.</li>
 *   <li>Розпакування: кожен файл пишеться в {@code temp/base/unpack}, звіряється за sha1 (ассет: його ім'я і є
 *       sha1; бібліотека: список {@code sha1.txt} в архіві) і лише тоді переноситься на місце. Наявні файли не
 *       чіпаються. Обрив посеред розпакування нічого не псує: повтор просто продовжує.</li>
 *   <li>Прибирання: після успіху частини і тека розпакування видаляються одразу; при кожному запуску видаляється
 *       все в {@code temp/base}, старше за 7 днів.</li>
 *   <li>Будь-яка інша помилка (немає місця, битий архів, CDN недоступний): лаунчер мовчки качає пофайлово, як
 *       раніше. Скасування гравцем: частини лишаються для докачки.</li>
 * </ul>
 *
 * <p>Архіви збирає {@code tools/cdn/base/build-base.py}; список у {@code baseArchiveIndexUrl}.</p>
 */
public final class BaseArchive {

    private static final Logger log = Logger.getLogger(BaseArchive.class.getName());
    private static final long MAX_AGE_MS = 7L * 24 * 60 * 60 * 1000;
    private static final int DOWNLOAD_TRIES = 4;
    private static final Pattern SAFE_ENTRY = Pattern.compile("(assets/objects/[0-9a-f]{2}/[0-9a-f]{40}|libraries/[A-Za-z0-9._+/-]+)");
    private static final Pattern SHA1 = Pattern.compile("[0-9a-f]{40}");

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Index {
        private int v;
        private Map<String, Base> bases = new HashMap<String, Base>();
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Base {
        private String assetIndex;
        private List<Part> parts = new ArrayList<Part>();
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Part {
        private String url;
        private long size;
        private String sha256;
    }

    private final Launcher launcher;
    private final File dir;
    private final File unpackDir;
    private final File assetsDir;
    private final File librariesDir;

    private volatile long doneBytes;
    private volatile long totalBytes;
    private volatile HttpRequest request;
    private volatile String stage = "";
    private boolean resumed;

    BaseArchive(Launcher launcher, File dir, File assetsDir, File librariesDir) {
        this.launcher = launcher;
        this.dir = dir;
        this.unpackDir = new File(dir, "unpack");
        this.assetsDir = assetsDir;
        this.librariesDir = librariesDir;
    }

    /**
     * Спробувати поставити базу Minecraft архівом. Ніколи не кидає, крім InterruptedException (скасування).
     *
     * @param progress куди показати хід (вікно оновлення)
     */
    public static void prefetch(Launcher launcher, VersionManifest version, Consumer<ProgressObservable> progress)
            throws InterruptedException {
        BaseArchive a = new BaseArchive(launcher, new File(launcher.getTemporaryDir(), "base"),
                launcher.getAssets().getDir(), launcher.getLibrariesDir());
        a.cleanupOld();
        String indexUrl = launcher.getProperties().getProperty("baseArchiveIndexUrl");
        if (indexUrl == null || indexUrl.trim().isEmpty() || version == null || version.getId() == null) {
            return;
        }
        // Індекс ассетів цієї версії вже є: гру ставили раніше, далі лише пофайлово (оновлення, докачка)
        if (new File(a.assetsDir, "indexes/" + version.getAssetId() + ".json").isFile()) {
            return;
        }
        URL base;
        Base b;
        try {
            base = HttpRequest.url(indexUrl.trim());
            Index index = HttpRequest.get(base).execute().expectResponseCode(200).returnContent().asJson(Index.class);
            b = index.getBases() != null ? index.getBases().get(version.getId()) : null;
        } catch (InterruptedException e) {
            throw e;
        } catch (Throwable t) {
            // списку архівів немає (ще не викладено, CDN недоступний): просто пофайлово, без «fail» у статистиці
            log.log(Level.INFO, "Minecraft base archive list is unavailable, using per-file downloads", t);
            return;
        }
        if (b == null || b.getParts() == null || b.getParts().isEmpty()) {
            return;
        }
        if (b.getAssetIndex() != null && !b.getAssetIndex().equals(version.getAssetId())) {
            log.warning("Minecraft base archive for " + version.getId() + " has asset index " + b.getAssetIndex()
                    + ", the game wants " + version.getAssetId() + "; using per-file downloads");
            return;
        }
        try {
            progress.accept(a.observable());
            a.install(base, version.getId(), b);
            DownloadStats.archive(a.resumed ? "resumed" : "ok");
        } catch (InterruptedException e) {
            throw e;
        } catch (Throwable t) {
            if (t instanceof java.nio.channels.ClosedByInterruptException || Thread.currentThread().isInterrupted()) {
                // гравець скасував: частини лишаються для докачки, це не помилка архіву
                Thread.interrupted();
                throw new InterruptedException();
            }
            log.log(Level.WARNING, "Minecraft base archive failed, falling back to per-file downloads", t);
            DownloadStats.archive("fail");
            deleteTree(a.unpackDir);
        }
    }

    void install(URL base, String versionId, Base b) throws IOException, InterruptedException {
        long need = 0;
        for (Part p : b.getParts()) need += p.getSize();
        totalBytes = need;
        // запас: архів плюс розпаковане плюс 200 МБ
        long free = dir.getParentFile() != null ? dir.getParentFile().getUsableSpace() : 0;
        dir.mkdirs();
        if (free > 0 && free < need * 2 + 200L * 1024 * 1024) {
            throw new IOException("Not enough disk space for the base archive: " + free + " bytes free, " + need + " needed");
        }
        String safeId = versionId.replaceAll("[^A-Za-z0-9._-]", "_");
        List<File> files = new ArrayList<File>();
        long before = 0;
        for (int i = 0; i < b.getParts().size(); i++) {
            Part p = b.getParts().get(i);
            File f = new File(dir, safeId + "-" + (i + 1) + ".zip.part");
            stage = SharedLocale.tr("baseArchive.downloading", i + 1, b.getParts().size());
            fetch(new URL(base, p.getUrl()), f, p, before);
            before += p.getSize();
            files.add(f);
        }
        doneBytes = need;
        for (int i = 0; i < files.size(); i++) {
            stage = SharedLocale.tr("baseArchive.unpacking", i + 1, files.size());
            unpack(files.get(i));
        }
        for (File f : files) {
            f.delete();
            sidecar(f).delete();
        }
        deleteTree(unpackDir);
        log.info("Minecraft base " + versionId + " installed from " + files.size() + " archive(s)");
    }

    /** Скачати частину з докачкою; повністю скачану звірити за sha256. */
    void fetch(URL url, File f, Part p, long before) throws IOException, InterruptedException {
        File meta = sidecar(f);
        String expectedMeta = url + "\n" + p.getSize() + "\n" + p.getSha256().toLowerCase(Locale.ROOT);
        if (!f.isFile() || !meta.isFile() || !expectedMeta.equals(read(meta)) || f.length() > p.getSize()) {
            f.delete();
            write(meta, expectedMeta);
        } else if (f.length() > 0) {
            resumed = true;
        }
        for (int hashTry = 0; ; hashTry++) {
            IOException last = null;
            for (int t = 0; t < DOWNLOAD_TRIES && f.length() < p.getSize(); t++) {
                checkInterrupted();
                long have = f.length();
                long started = System.currentTimeMillis();
                HttpRequest r = HttpRequest.get(url);
                doneBytes = before + have;
                request = r;
                try {
                    if (have > 0) {
                        log.info("Resuming base archive " + f.getName() + " from byte " + have + " of " + p.getSize());
                        r.setResumeInfo(new HttpRequest.PartialDownloadInfo(p.getSize(), have));
                    }
                    r.execute().expectResponseCode(200, 206).saveContent(f);
                    DownloadStats.record(url, r.getReadBytes(), System.currentTimeMillis() - started, true);
                } catch (IOException e) {
                    if (e instanceof java.nio.channels.ClosedByInterruptException || Thread.currentThread().isInterrupted()) {
                        throw e;
                    }
                    DownloadStats.record(url, r.getReadBytes(), System.currentTimeMillis() - started, false);
                    log.log(Level.WARNING, "Base archive download interrupted at " + f.length() + " bytes", e);
                    last = e;
                    Thread.sleep(2000L * (t + 1));
                } finally {
                    request = null;
                    doneBytes = before + f.length();
                }
            }
            if (f.length() != p.getSize()) {
                throw last != null ? last : new IOException(f.getName() + ": " + f.length() + " bytes, expected " + p.getSize());
            }
            String actual = hash(f, "SHA-256");
            if (actual.equalsIgnoreCase(p.getSha256())) {
                return;
            }
            log.warning("Base archive " + f.getName() + " is corrupt (sha256 " + actual + "), downloading it again");
            f.delete();
            if (hashTry >= 1) {
                meta.delete();
                throw new IOException("Base archive " + f.getName() + " is corrupt twice");
            }
        }
    }

    /** Розпакувати частину: кожен файл через temp/base/unpack, звірка sha1, перенос на місце. */
    void unpack(File zip) throws IOException, InterruptedException {
        Map<String, String> libSha1 = new HashMap<String, String>();
        // перший прохід: список sha1 бібліотек (може лежати будь-де в архіві)
        try (ZipInputStream zin = new ZipInputStream(new FileInputStream(zip))) {
            ZipEntry e;
            while ((e = zin.getNextEntry()) != null) {
                if ("sha1.txt".equals(e.getName())) {
                    BufferedReader br = new BufferedReader(new InputStreamReader(zin, StandardCharsets.UTF_8));
                    String line;
                    while ((line = br.readLine()) != null) {
                        int sp = line.indexOf(' ');
                        if (sp == 40) libSha1.put(line.substring(41).trim(), line.substring(0, 40).toLowerCase(Locale.ROOT));
                    }
                    break;
                }
            }
        }
        unpackDir.mkdirs();
        int placed = 0;
        int skipped = 0;
        try (ZipInputStream zin = new ZipInputStream(new FileInputStream(zip))) {
            ZipEntry e;
            byte[] buf = new byte[64 * 1024];
            while ((e = zin.getNextEntry()) != null) {
                checkInterrupted();
                String name = e.getName();
                if (e.isDirectory() || "sha1.txt".equals(name)) continue;
                if (!SAFE_ENTRY.matcher(name).matches() || name.contains("..")) {
                    log.warning("Skipping unexpected base archive entry " + name);
                    continue;
                }
                String expected;
                File target;
                if (name.startsWith("assets/")) {
                    expected = name.substring(name.lastIndexOf('/') + 1);
                    target = new File(assetsDir, name.substring("assets/".length()));
                } else {
                    String rel = name.substring("libraries/".length());
                    expected = libSha1.get(rel);
                    target = new File(librariesDir, rel);
                    if (expected == null || !SHA1.matcher(expected).matches()) {
                        log.warning("No sha1 for base archive library " + rel + ", leaving it to the normal download");
                        continue;
                    }
                }
                if (target.isFile()) {
                    skipped++;
                    continue;
                }
                File tmp = new File(unpackDir, expected + ".tmp");
                MessageDigest md = digest("SHA-1");
                try (OutputStream out = new FileOutputStream(tmp)) {
                    int n;
                    while ((n = zin.read(buf)) > 0) {
                        out.write(buf, 0, n);
                        md.update(buf, 0, n);
                    }
                }
                String actual = hex(md.digest());
                if (!actual.equals(expected)) {
                    tmp.delete();
                    log.warning("Base archive entry " + name + " has sha1 " + actual + ", expected " + expected
                            + "; it will be downloaded normally");
                    continue;
                }
                target.getParentFile().mkdirs();
                if (!tmp.renameTo(target)) {
                    java.nio.file.Files.move(tmp.toPath(), target.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
                placed++;
            }
        }
        log.info("Base archive " + zip.getName() + ": " + placed + " file(s) placed, " + skipped + " already present");
    }

    /** Видалити в temp/base усе, старше за 7 днів (покинуті докачки, залишки розпакування). */
    void cleanupOld() {
        File[] files = dir.listFiles();
        if (files == null) return;
        long now = System.currentTimeMillis();
        for (File f : files) {
            if (now - f.lastModified() > MAX_AGE_MS) {
                if (f.isDirectory()) deleteTree(f);
                else f.delete();
            }
        }
    }

    ProgressObservable observable() {
        return new ProgressObservable() {
            @Override
            public double getProgress() {
                long total = totalBytes;
                if (total <= 0) return -1;
                long done = doneBytes;
                HttpRequest r = request;
                if (r != null) done += r.getReadBytes();
                return Math.min(1.0, done / (double) total);
            }

            @Override
            public String getStatus() {
                double mb = 1024.0 * 1024.0;
                long done = doneBytes;
                HttpRequest r = request;
                if (r != null) done += r.getReadBytes();
                return stage + "\n" + SharedLocale.tr("baseArchive.size", Math.min(done, totalBytes) / mb, totalBytes / mb);
            }
        };
    }

    private static File sidecar(File part) {
        return new File(part.getPath() + ".json");
    }

    private static String read(File f) throws IOException {
        return new String(java.nio.file.Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
    }

    private static void write(File f, String s) throws IOException {
        java.nio.file.Files.write(f.toPath(), s.getBytes(StandardCharsets.UTF_8));
    }

    static String hash(File f, String algo) throws IOException {
        MessageDigest md = digest(algo);
        byte[] buf = new byte[64 * 1024];
        try (InputStream in = new FileInputStream(f)) {
            int n;
            while ((n = in.read(buf)) > 0) md.update(buf, 0, n);
        }
        return hex(md.digest());
    }

    private static MessageDigest digest(String algo) {
        try {
            return MessageDigest.getInstance(algo);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String hex(byte[] d) {
        StringBuilder sb = new StringBuilder(d.length * 2);
        for (byte x : d) sb.append(String.format("%02x", x));
        return sb.toString();
    }

    private static void checkInterrupted() throws InterruptedException {
        if (Thread.interrupted()) throw new InterruptedException();
    }

    static void deleteTree(File f) {
        if (f == null || !f.exists()) return;
        File[] children = f.listFiles();
        if (children != null) for (File c : children) deleteTree(c);
        f.delete();
    }
}
