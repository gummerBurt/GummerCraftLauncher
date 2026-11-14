/*
 * GummerCraft Launcher
 * Copyright (C) 2026 GummerCraft <http://gummercraft.fun>
 * Please see LICENSE.txt for license information.
 */

package com.skcraft.launcher.install;

import java.io.IOException;

import static com.skcraft.launcher.util.SharedLocale.tr;

/**
 * 14.11.2026: файл завантажився, але його sha1 не збігся з маніфестом навіть після повторних завантажень з нуля.
 * Англійський текст (getMessage) для логу і ErrorExplainer (там «hash mismatch» дає пояснення «corrupt»),
 * український (getLocalizedMessage) для вікна помилки.
 */
public class CorruptDownloadException extends IOException {

    private final String files;
    private final int attempts;

    public CorruptDownloadException(String files, int attempts, String details) {
        super("Hash mismatch after " + attempts + " download(s): " + files + (details != null ? " (" + details + ")" : ""));
        this.files = files;
        this.attempts = attempts;
    }

    public String getFiles() {
        return files;
    }

    @Override
    public String getLocalizedMessage() {
        return tr("downloader.corrupt", files, attempts);
    }
}
