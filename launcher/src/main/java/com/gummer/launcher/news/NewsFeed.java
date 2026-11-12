/*
 * Copyright (C) 2021-2026 GummerCraft <http://gummercraft.fun>
 */

package com.gummer.launcher.news;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gummer.launcher.util.Json;
import com.skcraft.launcher.Launcher;
import com.skcraft.launcher.util.HttpRequest;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Картки новин і реклами в головному вікні (з 1.32): {@code news.json} на CDN (launcher.properties newsJsonUrl).
 * Міняється без релізу лаунчера: достатньо викласти новий файл. Формат:
 * <pre>
 * {"items": [{
 *   "id": "staldayz-wipe",                 унікальний, для «закрити»
 *   "tab": "gummercraft" | "vanilla" | "all",
 *   "title": "...", "text": "...",
 *   "image": "staldayz.jpg",               відносно news.json або повна адреса (необов'язково)
 *   "button": "Грати на StalDayZ",
 *   "action": "pack:staldayz" | "tab:gummercraft" | "url:https://...",
 *   "hideIfInstalled": "staldayz",         не показувати, якщо цей пак уже встановлено (перехресна реклама)
 *   "online": "play.staldayz.com",         показати онлайн цього сервера на картці
 *   "from": "2026-11-13", "until": "2026-12-31",
 *   "priority": 10                         більший раніше
 * }]}
 * </pre>
 * Без інтернету: остання збережена копія ({@code <тека>/news-cache.json}).
 */
public final class NewsFeed {

    /** Одна картка. */
    public static final class Item {
        public String id, tab, title, text, button, action, hideIfInstalled, online;
        public URL image;
        public int priority;
        String from, until;
    }

    private static final Logger log = Logger.getLogger(NewsFeed.class.getName());
    private static final ObjectMapper MAPPER = Json.newMapper();
    private static final Map<String, BufferedImage> images = Collections.synchronizedMap(new HashMap<String, BufferedImage>());

    private NewsFeed() {
    }

    /** Усі актуальні картки (за датами), від більшого пріоритету. Блокує: не з EDT. */
    public static List<Item> load(Launcher launcher) {
        String raw = launcher.getProperties().getProperty("newsJsonUrl");
        List<Item> out = new ArrayList<Item>();
        if (raw == null || raw.trim().isEmpty()) return out;
        File cache = new File(launcher.getBaseDir(), "news-cache.json");
        JsonNode root = null;
        URL base = null;
        try {
            base = HttpRequest.url(raw.trim());
            byte[] body = HttpRequest.get(base).execute().expectResponseCode(200).returnContent().asBytes();
            root = MAPPER.readTree(body);
            try {
                java.nio.file.Files.write(cache.toPath(), body);
            } catch (Exception ignored) {
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.log(Level.INFO, "News are unavailable, using the cached copy", e);
        }
        if (root == null && cache.isFile()) {
            try {
                root = MAPPER.readTree(cache);
            } catch (Exception e) {
                return out;
            }
        }
        if (root == null) return out;
        String today = new SimpleDateFormat("yyyy-MM-dd").format(new Date());
        for (JsonNode n : root.path("items")) {
            Item i = new Item();
            i.id = n.path("id").asText("");
            i.tab = n.path("tab").asText("all");
            i.title = n.path("title").asText("");
            i.text = n.path("text").asText("");
            i.button = n.path("button").asText(null);
            i.action = n.path("action").asText(null);
            i.hideIfInstalled = n.path("hideIfInstalled").asText(null);
            i.online = n.path("online").asText(null);
            i.priority = n.path("priority").asInt(0);
            i.from = n.path("from").asText("");
            i.until = n.path("until").asText("");
            String img = n.path("image").asText(null);
            if (img != null && !img.isEmpty() && base != null) {
                try {
                    i.image = new URL(base, img);
                } catch (Exception ignored) {
                }
            } else if (img != null && img.startsWith("http")) {
                try {
                    i.image = new URL(img);
                } catch (Exception ignored) {
                }
            }
            if (i.title.isEmpty() && i.text.isEmpty()) continue;
            if (!i.from.isEmpty() && today.compareTo(i.from) < 0) continue;
            if (!i.until.isEmpty() && today.compareTo(i.until) > 0) continue;
            out.add(i);
        }
        Collections.sort(out, (a, b) -> Integer.compare(b.priority, a.priority));
        return out;
    }

    /** Картинка картки (кеш у пам'яті), або null. Блокує: не з EDT. */
    public static BufferedImage image(URL url) {
        if (url == null) return null;
        String key = url.toString();
        if (images.containsKey(key)) return images.get(key);
        BufferedImage img = null;
        try {
            byte[] b = HttpRequest.get(url).execute().expectResponseCode(200).returnContent().asBytes();
            img = ImageIO.read(new ByteArrayInputStream(b));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.log(Level.FINE, "News image unavailable: " + url, e);
        }
        images.put(key, img);
        return img;
    }
}
