/*
 * SK's Minecraft Launcher
 * Copyright (C) 2010-2014 Albert Pham <http://www.sk89q.com> and contributors
 * Please see LICENSE.txt for license information.
 */

package com.skcraft.launcher.model.modpack;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@JsonIgnoreProperties(ignoreUnknown = true)
public class ManifestInfo extends BaseManifest implements Comparable<ManifestInfo> {

    private String location;
    private int priority;
    /** Лише у списку v2 (1.32+): група паків, що показується однією карткою з вибором версії (наприклад "main"). */
    private String group;
    /** Лише у списку v2: у паку є свій сервер. */
    private boolean server;
    /** Лише у списку v2: рекомендована пам'ять у МБ, або null. */
    private Integer recommendedMemory;
    /** Список v2 (1.32+): адреса сервера паку, наприклад play.staldayz.com. */
    private String serverAddress;

    @Override
    public int compareTo(ManifestInfo o) {
        if (priority > o.getPriority()) {
            return -1;
        } else if (priority < o.getPriority()) {
            return 1;
        } else {
            return 0;
        }
    }

}
