/*
 * SK's Minecraft Launcher
 * Copyright (C) 2010-2014 Albert Pham <http://www.sk89q.com> and contributors
 * Please see LICENSE.txt for license information.
 */

package com.skcraft.launcher.selfupdate;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.net.URL;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class LatestVersionInfo {

    private String version;
    private URL url;
    private boolean packed;
    /** Optional hex SHA-256 of the jar at {@link #url}; when present the download is verified against it. */
    private String sha256;

}
