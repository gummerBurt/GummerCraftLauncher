/*
 * SK's Minecraft Launcher
 * Copyright (C) 2010-2014 Albert Pham <http://www.sk89q.com> and contributors
 * Copyright (C) 2021-2025 GummerCraft <http://gummercraft.fun>
 * Please see LICENSE.txt for license information.
 */

package com.skcraft.launcher.selfupdate;

import com.google.common.hash.Hashing;
import com.google.common.io.Files;
import com.skcraft.concurrency.DefaultProgress;
import com.skcraft.concurrency.ProgressObservable;
import com.skcraft.launcher.Launcher;
import com.skcraft.launcher.LauncherException;
import com.skcraft.launcher.install.FileMover;
import com.skcraft.launcher.install.Installer;
import com.skcraft.launcher.util.SharedLocale;
import lombok.NonNull;

import java.io.File;
import java.net.URL;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SelfUpdater implements Callable<File>, ProgressObservable {

    private final Launcher launcher;
    private final URL url;
    private final Installer installer;
    private ProgressObservable progress = new DefaultProgress(0, SharedLocale.tr("updater.updating"));

    private final String sha256;

    public SelfUpdater(@NonNull Launcher launcher, @NonNull URL url) {
        this(launcher, url, null);
    }

    public SelfUpdater(@NonNull Launcher launcher, @NonNull URL url, String sha256) {
        this.launcher = launcher;
        this.url = url;
        this.sha256 = sha256;
        this.installer = new Installer(launcher.getInstallerDir());
    }

    @Override
    public File call() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();

        try {
            File dir = launcher.getLauncherBinariesDir();
            File file = new File(dir, System.currentTimeMillis() + ".jar");
            File tempFile = installer.getDownloader().download(url, "", 10000, "launcher.jar");

            progress = installer.getDownloader();
            installer.download();

            if (sha256 != null && !sha256.isEmpty()) {
                String actual = Files.asByteSource(tempFile).hash(Hashing.sha256()).toString();
                if (!actual.equalsIgnoreCase(sha256.trim())) {
                    tempFile.delete();
                    String message = "Downloaded launcher update does not match the published SHA-256 checksum";
                    throw new LauncherException(message + " (expected " + sha256 + ", got " + actual + ")", message);
                }
            }

            installer.queue(new FileMover(tempFile, file));

            progress = installer;
            installer.execute(launcher);

            return file;
        } finally {
            executor.shutdownNow();
        }
    }

    @Override
    public double getProgress() {
        return progress.getProgress();
    }

    @Override
    public String getStatus() {
        return progress.getStatus();
    }

}
