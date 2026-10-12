/*
 * Copyright (C) 2021-2025 GummerCraft <http://gummercraft.fun>
 */

package com.gummer.launcher.model.java;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class JavaManifestFile {
    private String url;

    private int size;

    @JsonProperty("sha1")
    private String hash;
}
