/*
 * SK's Minecraft Launcher
 * Copyright (C) 2010-2014 Albert Pham <http://www.sk89q.com> and contributors
 * Copyright (C) 2021-2025 GummerCraft <http://gummercraft.fun>
 * Please see LICENSE.txt for license information.
 */

package com.skcraft.launcher.auth;

//import com.gummer.launcher.auth.skin.OfflineSkinService;
import lombok.Getter;
import lombok.NonNull;

import java.util.Collections;
import java.util.Map;
import java.util.UUID;

/**
 * An offline session.
 */
public class OfflineSession implements Session {

    //private static Map<String, String> dummyProperties = Collections.emptyMap();
    private static final Map<String, String> dummyProperties = Collections.emptyMap();

    @Getter
    private final String name;

    /**
     * Create a new offline session using the given player name.
     *
     * @param name the player name
     */
    public OfflineSession(@NonNull String name) {
        this(name, null);
    }

    /**
     * 1.32: гра без інтернету зі збереженим акаунтом Microsoft: його нік і справжній UUID (той самий гравець у
     * світах одиночної гри), але без токена.
     */
    public OfflineSession(@NonNull String name, String uuid) {
        // 1.41.6 (28.11.2026, тікет #145): вікно офлайн-входу перевіряло нік без пробілів по краях, а зберігало як ввели,
        // тож у грі був « Mirakl» і StalDayZ не пускав («недопустимі символи»). Обрізаємо тут, тож лікуються і вже
        // збережені акаунти.
        this.name = name.trim();
        this.uuid = uuid;
    }

    private final String uuid;

    /**
     * 22.11.2026: UUID не заданий (звичайний офлайн-акаунт, а не збережений акаунт Microsoft без інтернету). Тоді
     * Runner вибирає UUID для збірки сам ({@link com.gummer.launcher.util.OfflineUuid#forBuild}).
     */
    public boolean isGeneratedUuid() {
        return uuid == null;
    }

    @Override
    public String getUuid() {
        //return (new UUID(0, 0)).toString(); // try to fix accounts duplication
        return uuid != null ? uuid : UUID.nameUUIDFromBytes(name.getBytes()).toString();
    }

    @Override
    public String getAccessToken() {
        return "0";
    }

    @Override
    public Map<String, String> getUserProperties() {
        return dummyProperties;
    }

    @Override
    public String getSessionToken() {
        return "-";
    }

    @Override
    public UserType getUserType() {
        return UserType.LEGACY;
    }

    @Override
    public byte[] getAvatarImage() {
        return null;
//        try {
//            return OfflineSkinService.fetchSkinHead(name);
//        } catch (InterruptedException e) {
//            return null;
//        }
    }

    @Override
    public boolean isOnline() {
        return false;
    }

}
