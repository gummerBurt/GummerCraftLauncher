/*
 * Copyright (C) 2021-2025 GummerCraft <http://gummercraft.fun>
 */

package com.gummer.launcher.auth.skin;

import com.skcraft.launcher.util.HttpRequest;
import lombok.extern.java.Log;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.logging.Level;

import static com.skcraft.launcher.util.HttpRequest.url;

@Log
public class OfflineSkinService {
    @Nullable
    public static byte[] fetchSkinHead(String name) throws InterruptedException {
        String skinUrl = String.format("https://skin.gummercraft.fun/head/32/%s", name);

        try {
            return HttpRequest.get(url(skinUrl))
                    .execute()
                    .expectResponseCode(200)
                    .returnContent()
                    .asBytes();
        } catch (IOException e) {
            log.log(Level.WARNING, "Failed to download or process skin from Gummercraft", e);
            return null;
        }
    }
}
