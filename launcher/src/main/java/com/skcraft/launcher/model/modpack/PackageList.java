/*
 * SK's Minecraft Launcher
 * Copyright (C) 2010-2014 Albert Pham <http://www.sk89q.com> and contributors
 * Please see LICENSE.txt for license information.
 */

package com.skcraft.launcher.model.modpack;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class PackageList {

    public static final int MIN_VERSION = 1;

    private int minimumVersion;
    private List<ManifestInfo> packages;
    /** Лише у списку v2 (1.32+): 2. У списку v1 поля немає (0). */
    private int formatVersion;
    /** Лише у списку v2: опис груп паків за id групи. */
    private Map<String, PackageGroup> groups = new LinkedHashMap<String, PackageGroup>();

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PackageGroup {
        private String title;
        /** Короткий опис групи для другого рядка картки (наприклад «Виживання»), необов'язково. */
        private String subtitle;
    }

}
