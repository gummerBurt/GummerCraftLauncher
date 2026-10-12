/*
 * SK's Minecraft Launcher
 * Copyright (C) 2010-2014 Albert Pham <http://www.sk89q.com> and contributors
 * Please see LICENSE.txt for license information.
 */

package com.skcraft.launcher.bootstrap;

import com.skcraft.launcher.Bootstrap;
import lombok.extern.java.Log;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;

import javax.swing.*;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;

import static com.skcraft.launcher.bootstrap.BootstrapUtils.checkInterrupted;
import static com.skcraft.launcher.bootstrap.SharedLocale.tr;

@Log
public class Downloader implements Runnable, ProgressObservable {

    private final Bootstrap bootstrap;
    /** Already installed launchers to start if the download fails (empty on a first install). */
    private final List<LauncherBinary> fallback;
    private DownloadFrame dialog;
    private HttpRequest httpRequest;
    private Thread thread;

    public Downloader(Bootstrap bootstrap) {
        this(bootstrap, Collections.<LauncherBinary>emptyList());
    }

    public Downloader(Bootstrap bootstrap, List<LauncherBinary> fallback) {
        this.bootstrap = bootstrap;
        this.fallback = fallback;
    }

    @Override
    public void run() {
        this.thread = Thread.currentThread();

        try {
            execute();
        } catch (InterruptedException e) {
            log.log(Level.WARNING, "Interrupted");
            System.exit(0);
        } catch (Throwable t) {
            log.log(Level.WARNING, "Failed to download launcher", t);
            if (!fallback.isEmpty()) {
                try {
                    log.info("Starting the installed launcher instead");
                    bootstrap.launchExisting(fallback, false);
                    return;
                } catch (Throwable t2) {
                    log.log(Level.WARNING, "Failed to start the installed launcher", t2);
                }
            }
            SwingHelper.showErrorDialog(null, tr("errors.failedDownloadError"), tr("errorTitle"), t);
            System.exit(0);
        }
    }

    private void execute() throws Exception {
        SwingUtilities.invokeAndWait(new Runnable() {
            @Override
            public void run() {
                Bootstrap.setSwingLookAndFeel();
                dialog = new DownloadFrame(Downloader.this);
                dialog.setVisible(true);
                dialog.setDownloader(Downloader.this);
            }
        });

        URL updateUrl = HttpRequest.url(bootstrap.getProperties().getProperty("latestUrl"));

        log.info("Reading update URL " + updateUrl + "...");
        List<LauncherBinary> binaries = new ArrayList<LauncherBinary>();

        try {
            String data = HttpRequest
                    .get(updateUrl)
                    .execute()
                    .expectResponseCode(200)
                    .returnContent()
                    .asString("UTF-8");

            Object object = JSONValue.parse(data);
            URL url;
            String sha256 = null;

            if (object instanceof JSONObject) {
                String rawUrl = String.valueOf(((JSONObject) object).get("url"));
                Object rawHash = ((JSONObject) object).get("sha256");
                if (rawHash != null) {
                    sha256 = String.valueOf(rawHash).trim();
                }
                if (rawUrl != null) {
                    url = HttpRequest.url(rawUrl.trim());
                } else {
                    log.warning("Did not get valid update document - got:\n\n" + data);
                    throw new IOException("Update URL did not return a valid result");
                }
            } else {
                log.warning("Did not get valid update document - got:\n\n" + data);
                throw new IOException("Update URL did not return a valid result");
            }

            checkInterrupted();

            File finalFile = new File(bootstrap.getBinariesDir(), System.currentTimeMillis() + ".jar");
            File tempFile = new File(finalFile.getParentFile(), finalFile.getName() + ".tmp");

            log.info("Downloading " + url + " to " + tempFile.getAbsolutePath());

            httpRequest = HttpRequest.get(url);
            httpRequest
                    .execute()
                    .expectResponseCode(200)
                    .saveContent(tempFile);

            if (sha256 != null && !sha256.isEmpty()) {
                String actual = sha256Hex(tempFile);
                if (!actual.equalsIgnoreCase(sha256)) {
                    tempFile.delete();
                    throw new IOException("Downloaded launcher does not match the published SHA-256 checksum"
                            + " (expected " + sha256 + ", got " + actual + ")");
                }
            }

            finalFile.delete();
            tempFile.renameTo(finalFile);

            LauncherBinary binary = new LauncherBinary(finalFile);
            binaries.add(binary);
        } finally {
            SwingUtilities.invokeLater(new Runnable() {
                @Override
                public void run() {
                    dialog.setDownloader(null);
                    dialog.dispose();
                }
            });
        }

        bootstrap.launchExisting(binaries, false);
    }

    private static String sha256Hex(File file) throws IOException {
        try (InputStream in = new FileInputStream(file)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
            StringBuilder hex = new StringBuilder();
            for (byte b : digest.digest()) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IOException(e);
        }
    }

    public void cancel() {
        thread.interrupt();
    }

    public String getStatus() {
        HttpRequest httpRequest = this.httpRequest;
        if (httpRequest != null) {
            double progress = httpRequest.getProgress();
            if (progress >= 0) {
                return String.format(tr("downloader.progressStatus"), progress * 100);
            }
        }

        return tr("downloader.status");
    }

    @Override
    public double getProgress() {
        HttpRequest httpRequest = this.httpRequest;
        return httpRequest != null ? httpRequest.getProgress() : -1;
    }
}
