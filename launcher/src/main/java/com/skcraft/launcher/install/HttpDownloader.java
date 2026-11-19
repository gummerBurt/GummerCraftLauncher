/*
 * SK's Minecraft Launcher
 * Copyright (C) 2010-2014 Albert Pham <http://www.sk89q.com> and contributors
 * Copyright (C) 2021-2025 GummerCraft <http://gummercraft.fun>
 * Please see LICENSE.txt for license information.
 */

package com.skcraft.launcher.install;

import com.google.common.base.Charsets;
import com.google.common.base.Strings;
import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;
import com.google.common.io.Files;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.skcraft.concurrency.ProgressObservable;
import com.skcraft.launcher.util.HttpRequest;
import com.skcraft.launcher.util.SharedLocale;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import lombok.extern.java.Log;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.logging.Level;

import static com.skcraft.launcher.util.SharedLocale.tr;

@Log
public class HttpDownloader implements Downloader {

    private final Random random = new Random();
    private final HashFunction hf = Hashing.sha1();

    private final File tempDir;
    @Getter @Setter private int threadCount = 6;
    @Getter @Setter private int retryDelay = 2000;
    @Getter @Setter private int tryCount = 3;
    /** 1.32.2: скільки разів після основного проходу повторювати файли з помилкою (раніше 1). */
    private static final int RETRY_ROUNDS = 4;
    /** 1.32.2: поточне коло повторів (0 = основний прохід), для статусу у вікні. */
    private int retryRound = 0;
    /**
     * 17.11.2026: коли востаннє файл не скачався (обрив, таймаут з'єднання). Власник бачив «0,0 МБ/с» на 570 з 970 МБ
     * без жодного пояснення: у лозі тоді були десятки «Connect timed out» до resources.download.minecraft.net.
     */
    private volatile long lastFailAt;

    private List<HttpDownloadJob> queue = new ArrayList<HttpDownloadJob>();
    private final Set<String> usedKeys = new HashSet<String>();

    private final List<HttpDownloadJob> running = new ArrayList<HttpDownloadJob>();
    private final List<HttpDownloadJob> failed = new ArrayList<HttpDownloadJob>();
    /** 14.11.2026: файли, чий sha1 не зійшовся і після повторних завантажень з нуля (кола повторів їх не беруть). */
    private final List<HttpDownloadJob> corrupt = new ArrayList<HttpDownloadJob>();
    /**
     * 14.11.2026: скільки разів качати файл заново з нуля (без Range), якщо sha1 не зійшовся. Разом з першим
     * завантаженням виходить 3 спроби.
     */
    private static final int HASH_RETRIES = 2;
    private long downloaded = 0;
    private long total = 0;
    private int left = 0;
    /** 1.32: байти завершених файлів (розмір з маніфесту, а якщо його немає - скільки прийшло). */
    private long doneBytes = 0;
    /** 1.32: заміри {час мс, байти} за останні SPEED_WINDOW_MS для швидкості. */
    private final ArrayDeque<long[]> speedSamples = new ArrayDeque<long[]>();
    private static final long SPEED_WINDOW_MS = 5000;
    private long firstSampleAt = 0;

    /**
     * Create a new downloader using the given executor.
     *
     * @param tempDir the temporary directory
     */
    public HttpDownloader(@NonNull File tempDir) {
        this.tempDir = tempDir;
    }

    /**
     * Make sure that we aren't re-using hash IDs.
     *
     * @param baseKey the key to make unique
     * @return a unique key
     */
    private String createUniqueKey(String baseKey) {
        String key = baseKey;
        int i = 0;
        while (usedKeys.contains(key)) {
            key = baseKey + "_" + (i++);
        }
        usedKeys.add(key);
        return key;
    }

    @Override
    public File download(@NonNull List<URL> urls, @NonNull String key, long size, String name) {
        return download(urls, key, size, name, null);
    }

    @Override
    public synchronized File download(@NonNull List<URL> urls, @NonNull String key, long size, String name, String sha1) {
        if (urls.isEmpty()) {
            throw new IllegalArgumentException("Can't download empty list of URLs");
        }

        String hash = hf.hashString(Strings.nullToEmpty(key) + urls.get(0), Charsets.UTF_8).toString();
        hash = createUniqueKey(hash);
        File tempFile = new File(tempDir, hash.substring(0, 2) + "/" + hash);
        String jobName = name != null ? name : tempFile.getName();
        String expected = Strings.emptyToNull(sha1);

        // 14.11.2026: тимчасова тека install не чиститься, тож файл з минулого (обірваного чи невдалого) встановлення
        // береться повторно. Якщо відомий sha1, спершу звіряємо: зіпсований видаляємо і качаємо заново.
        if (tempFile.exists() && expected != null && !hashMatches(tempFile, expected)) {
            log.warning("Cached download " + tempFile + " (" + jobName + ") does not match sha1 " + expected
                    + ", downloading it again");
            tempFile.delete();
        }

        // If the file is already downloaded (such as from before), then don't re-download
        if (!tempFile.exists()) {
            total += size;
            left++;
            queue.add(new HttpDownloadJob(tempFile, urls, size, jobName, expected));
        }

        return tempFile;
    }


    @Override
    public File download(URL url, String key, long size, String name) {
        return download(url, key, size, name, null);
    }

    @Override
    public File download(URL url, String key, long size, String name, String sha1) {
        List<URL> urls = new ArrayList<URL>();
        urls.add(url);
        return download(urls, key, size, name, sha1);
    }

    private boolean hashMatches(File file, String expected) {
        try {
            return Files.hash(file, hf).toString().equalsIgnoreCase(expected);
        } catch (IOException e) {
            log.log(Level.WARNING, "Failed to hash " + file, e);
            return false;
        }
    }

    /**
     * Prevent further downloads from being queued and download queued files.
     *
     * @throws InterruptedException thrown on interruption
     * @throws IOException thrown on I/O error
     */
    public void execute() throws InterruptedException, IOException {
        synchronized (this) {
            queue = Collections.unmodifiableList(queue);
            // 19.11.2026: місця не вистачить, тож краще сказати до початку, а не на середині завантаження
            long bytes = 0;
            for (HttpDownloadJob job : queue) if (job.size > 0) bytes += job.size;
            com.gummer.launcher.install.DiskSpaceException.check(tempDir, bytes);
        }

        ListeningExecutorService executor = MoreExecutors.listeningDecorator(
                Executors.newFixedThreadPool(threadCount));

        try {
            List<ListenableFuture<?>> futures = new ArrayList<ListenableFuture<?>>();

            synchronized (this) {
                for (HttpDownloadJob job : queue) {
                    futures.add(executor.submit(job));
                }
            }

            try {
                Futures.allAsList(futures).get();
            } catch (ExecutionException e) {
                throw new IOException("Something went wrong", e);
            }

            // 1.32.2: файли з помилкою повторюються кількома колами з паузою, що росте (3, 6, 9, 12 с), а не одним
            for (int round = 1; round <= RETRY_ROUNDS; round++) {
                List<HttpDownloadJob> retry;
                synchronized (this) {
                    if (failed.isEmpty()) break;
                    retry = new ArrayList<HttpDownloadJob>(failed);
                    failed.clear();
                    left += retry.size();
                    retryRound = round;
                }
                log.info("Retrying " + retry.size() + " failed download(s), round " + round + " of " + RETRY_ROUNDS);
                Thread.sleep(round * 3000L);
                futures.clear();
                for (HttpDownloadJob job : retry) {
                    futures.add(executor.submit(job));
                }
                try {
                    Futures.allAsList(futures).get();
                } catch (ExecutionException e) {
                    throw new IOException("Something went wrong", e);
                }
            }

            synchronized (this) {
                if (!corrupt.isEmpty()) {
                    StringBuilder names = new StringBuilder();
                    for (HttpDownloadJob job : corrupt) {
                        if (names.length() > 0) names.append(", ");
                        names.append(job.getName());
                    }
                    throw new CorruptDownloadException(names.toString(), HASH_RETRIES + 1, null);
                }
                if (failed.size() > 0) {
                    //throw new IOException(failed.size() + " file(s) could not be downloaded");
                    throw new IOException(failed.size() + " file(s) could not be downloaded. Please retry the failed downloads by relaunching the modpack and make sure that your internet connection is stable by disabling downloads, videos, streams.. In case it is still failing after a few trys please contact support");
                }
            }
        } finally {
            executor.shutdownNow();
        }
    }

    @Override
    public synchronized double getProgress() {
        if (total <= 0) {
            return -1;
        }

        long downloaded = this.downloaded;
        for (HttpDownloadJob job : running) {
            downloaded += Math.max(0, job.getProgress() * job.size);
        }
        return downloaded / (double) total;
    }

    /** Скільки байтів уже прийшло: завершені файли плюс прочитане в тих, що качаються зараз. */
    private long receivedBytes() {
        long bytes = doneBytes;
        for (HttpDownloadJob job : running) {
            bytes += job.readBytes();
        }
        return bytes;
    }

    /**
     * 1.32: перший рядок статусу (його показує вікно прогресу): скільки завантажено з загального розміру, швидкість
     * за останні 5 с і орієнтовно скільки лишилось. null, поки нічого не качається.
     */
    private String summaryLine() {
        long now = System.currentTimeMillis();
        long bytes = receivedBytes();
        if (!speedSamples.isEmpty() && bytes < speedSamples.peekLast()[1]) {
            speedSamples.clear(); // повтор файлу з нуля: старі заміри вже не про те
        }
        speedSamples.addLast(new long[]{now, bytes});
        if (firstSampleAt == 0) firstSampleAt = now;
        while (speedSamples.size() > 2 && now - speedSamples.peekFirst()[0] > SPEED_WINDOW_MS) {
            speedSamples.removeFirst();
        }
        long[] oldest = speedSamples.peekFirst();
        double speed = now > oldest[0] ? (bytes - oldest[1]) * 1000.0 / (now - oldest[0]) : 0; // байт/с

        double mb = 1024.0 * 1024.0;
        String line = total > 0
                ? tr("downloader.summary", Math.min(bytes, total) / mb, total / mb, speed / mb)
                : tr("downloader.summaryNoTotal", bytes / mb, speed / mb);
        if (total > bytes && speed > 1024 && now - firstSampleAt >= 3000) { // перші секунди швидкість ще стрибає
            long seconds = Math.round((total - bytes) / speed);
            line += seconds < 60 ? tr("downloader.etaSeconds", Math.max(1, seconds))
                    : tr("downloader.etaMinutes", (seconds + 59) / 60);
        }
        // 17.11.2026: швидкість біля нуля і щойно був обрив: гравець бачить, що лаунчер не завис, а чекає на мережу
        if (speed < 64 * 1024 && now - lastFailAt < 30_000) line += " · " + tr("downloader.connectionTrouble");
        return line;
    }

    @Override
    public synchronized String getStatus() {
        // 1.32.2: у колі повтору показуємо, яке це коло і скільки файлів ще качається заново (left), і в паузі між колами теж
        String failMessage = retryRound > 0 ? tr("downloader.retrying", retryRound, RETRY_ROUNDS, left)
                : failed.isEmpty() ? "" : tr("downloader.failedCount", failed.size());
        if (!running.isEmpty()) {
            return summaryLine() + "\n" + getFilesStatus(failMessage);
        }
        if (retryRound > 0) {
            return failMessage;
        }
        return SharedLocale.tr("downloader.noDownloads");
    }

    private String getFilesStatus(String failMessage) {
        if (running.size() == 1) {
            return tr("downloader.downloadingItem", running.get(0).getName()) +
                    "\n" + running.get(0).getStatus() +
                    "\n" + failMessage;
        } else if (running.size() > 0) {
            StringBuilder builder = new StringBuilder();
            for (HttpDownloadJob job : running) {
                builder.append("\n");
                builder.append(job.getStatus());
            }
            return tr("downloader.downloadingList", queue.size(), left, failed.size()) +
                    builder.toString() +
                    "\n" + failMessage;
        } else {
            return SharedLocale.tr("downloader.noDownloads");
        }
    }

    public class HttpDownloadJob implements Runnable, ProgressObservable {
        private final File destFile;
        private final List<URL> urls;
        private final long size;
        @Getter private String name;
        private HttpRequest request;
        /** 14.11.2026: очікуваний sha1 (null, якщо невідомий). */
        private final String sha1;
        /** 14.11.2026: після розбіжності sha1 файл качається лише з нуля, без докачки через Range. */
        private boolean noResume = false;

        private HttpDownloadJob(File destFile, List<URL> urls, long size, String name, String sha1) {
            this.destFile = destFile;
            this.urls = urls;
            this.size = size;
            this.name = name;
            this.sha1 = sha1;
        }

        @Override
        public void run() {
            try {
                synchronized (HttpDownloader.this) {
                    running.add(this);
                }

                download();

                synchronized (HttpDownloader.this) {
                    downloaded += size;
                    doneBytes += size > 0 ? size : readBytes();
                }
            } catch (CorruptDownloadException e) {
                log.log(Level.WARNING, "Giving up on " + name + ": " + e.getMessage());
                synchronized (HttpDownloader.this) {
                    corrupt.add(this);
                }
            } catch (IOException e) {
                synchronized (HttpDownloader.this) {
                    failed.add(this);
                }
            } catch (InterruptedException e) {
                log.info("Download of " + destFile + " was interrupted");
            } finally {
                synchronized (HttpDownloader.this) {
                    left--;
                    running.remove(this);
                }
            }
        }

        private void download() throws IOException, InterruptedException {
            log.log(Level.INFO, "Downloading " + destFile + " from " + urls);

            File destDir = destFile.getParentFile();
            File tempFile = new File(destDir, destFile.getName() + ".tmp");
            destDir.mkdirs();

            // Try to download. 14.11.2026: якщо sha1 відомий, звіряємо одразу (до встановлення): зіпсований файл
            // (обрив з докачкою, антивірус, проксі мобільного оператора) видаляємо і качаємо заново з нуля без Range.
            for (int attempt = 0; ; attempt++) {
                download(tempFile);
                if (sha1 == null) {
                    break;
                }
                String actual = Files.hash(tempFile, hf).toString();
                if (actual.equalsIgnoreCase(sha1)) {
                    if (attempt > 0) {
                        log.info("Downloaded " + name + " correctly on attempt " + (attempt + 1));
                    }
                    break;
                }
                String details = "expected sha1 " + sha1 + ", got " + actual + ", " + tempFile.length()
                        + " bytes" + (size > 0 ? " of " + size : "");
                log.warning("Downloaded file " + name + " from " + urls + " is corrupt: " + details
                        + (attempt < HASH_RETRIES ? ", downloading it again from scratch" : ""));
                tempFile.delete();
                if (attempt >= HASH_RETRIES) {
                    throw new CorruptDownloadException(name, attempt + 1, details);
                }
                noResume = true;
            }

            destFile.delete();
            if (!tempFile.renameTo(destFile)) {
                throw new IOException(String.format("Failed to rename %s to %s", tempFile, destFile));
            }
        }

        private void download(File file) throws IOException, InterruptedException {
            int trial = 0;
            boolean first = true;
            IOException lastException = null;

            do {
                for (URL url : urls) {
                    // Sleep between each trial
                    if (!first) {
                        Thread.sleep((long) (retryDelay / 2 + (random.nextDouble() * retryDelay)));
                    }
                    first = false;

                    try {
                        tryDownloadFrom(url, file, null, 0);
                        return;
                    } catch (IOException e) {
                        lastException = e;
                    }
                }
            } while (++trial < tryCount);

            throw new IOException("Failed to download from " + urls, lastException);
        }

        private void tryDownloadFrom(URL url, File file, HttpRequest.PartialDownloadInfo retryDetails, int tries)
                throws InterruptedException, IOException {
            long startedAt = System.currentTimeMillis();
            try {
                request = HttpRequest.get(url);
                request.setResumeInfo(retryDetails).execute().expectResponseCode(200).saveContent(file);
                com.gummer.launcher.stats.DownloadStats.record(url, request.getReadBytes(),
                        System.currentTimeMillis() - startedAt, true);
            } catch (IOException e) {
                log.log(Level.WARNING, "Failed to download " + url, e);
                lastFailAt = System.currentTimeMillis();
                com.gummer.launcher.stats.DownloadStats.record(url, request != null ? request.getReadBytes() : 0,
                        System.currentTimeMillis() - startedAt, false);

                // We only want to try to resume a partial download if the request succeeded before
                // throwing an exception halfway through. If it didn't succeed, just throw the error.
                if (noResume || tries >= tryCount || !request.isConnected() || !request.isSuccessCode()) {
                    throw e;
                }

                Optional<HttpRequest.PartialDownloadInfo> byteRangeSupport = request.canRetryPartial();
                if (byteRangeSupport.isPresent()) {
                    log.info("Resuming " + name + " from byte " + byteRangeSupport.get().getCurrentLength()
                            + " of " + byteRangeSupport.get().getExpectedLength());
                    tryDownloadFrom(url, file, byteRangeSupport.get(), tries + 1);
                } else {
                    // 14.11.2026: раніше без підтримки Range метод мовчки повертався, і обрізаний файл ішов як успішний
                    throw e;
                }
                return;
            }

            // 14.11.2026: після докачки довжина має збігтися з повною довжиною з першої відповіді. Не збіглась:
            // виняток, і файл качається заново з нуля (зовнішній цикл download(File) іде з retryDetails = null).
            if (retryDetails != null && retryDetails.getExpectedLength() > 0
                    && file.length() != retryDetails.getExpectedLength()) {
                throw new IOException(String.format("Resumed download of %s has %d bytes, expected %d",
                        name, file.length(), retryDetails.getExpectedLength()));
            }
        }

        /** Прочитано поточним запитом цього файлу (0, поки запиту немає). */
        long readBytes() {
            HttpRequest request = this.request;
            return request != null ? request.getReadBytes() : 0;
        }

        @Override
        public double getProgress() {
            HttpRequest request = this.request;
            return request != null ? request.getProgress() : -1;
        }

        @Override
        public String getStatus() {
            double progress = getProgress();
            if (progress >= 0) {
                return tr("downloader.jobProgress", name, Math.round(progress * 100 * 100) / 100.0);
            } else {
                return tr("downloader.jobPending", name);
            }
        }
    }
}
