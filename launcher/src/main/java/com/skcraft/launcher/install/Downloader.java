/*
 * SK's Minecraft Launcher
 * Copyright (C) 2010-2014 Albert Pham <http://www.sk89q.com> and contributors
 * Please see LICENSE.txt for license information.
 */

package com.skcraft.launcher.install;

import com.skcraft.concurrency.ProgressObservable;

import java.io.File;
import java.net.URL;
import java.util.List;


public interface Downloader extends ProgressObservable {

    File download(List<URL> urls, String key, long size, String name);

    File download(URL url, String key, long size, String name);

    /**
     * 14.11.2026: те саме, але з очікуваним sha1 файлу (null, якщо невідомий). Завантажувач звіряє хеш одразу після
     * завантаження і при розбіжності качає файл заново з нуля, див. {@link HttpDownloader}.
     */
    File download(List<URL> urls, String key, long size, String name, String sha1);

    File download(URL url, String key, long size, String name, String sha1);
}
