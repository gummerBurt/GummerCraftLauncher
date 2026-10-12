/*
 * Copyright (C) 2021-2025 GummerCraft <http://gummercraft.fun>
 */

package com.gummer.launcher.util;

import com.gummer.launcher.shared.Toolbox;
import lombok.Getter;
import lombok.Setter;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * Uploads text to a hastebin-compatible server ({@code POST <pasteURL>documents}, response {@code {"key": "..."}}).
 * Used only by the "Upload log" button of the console window.
 */
public class PasteUtils {

    @Getter
    @Setter
    private static String pasteURL = "https://hastebin.com/";

    /**
     * Uploads the given text.
     *
     * @param text the text to upload
     * @return the URL of the uploaded document, or {@code null} on any failure
     */
    public static synchronized String paste(String text) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(pasteURL + "documents").openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("User-Agent", Toolbox.USER_AGENT);
            connection.setRequestProperty("Content-Type", "text/plain; charset=utf-8");
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(30000);
            connection.setDoOutput(true);

            try (OutputStream out = connection.getOutputStream()) {
                out.write(text.getBytes(StandardCharsets.UTF_8));
            }

            ByteArrayOutputStream body = new ByteArrayOutputStream();
            try (InputStream in = connection.getInputStream()) {
                byte[] buffer = new byte[4096];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    body.write(buffer, 0, read);
                }
            }
            String key = new JSONObject(new String(body.toByteArray(), StandardCharsets.UTF_8)).getString("key");
            return pasteURL + key;
        } catch (Exception e) {
            return null;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }
}
