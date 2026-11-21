/*
 * GummerCraft Launcher
 * Copyright (C) 2021-2026 GummerCraft <http://gummercraft.fun>
 * Please see LICENSE.txt for license information.
 */

package com.gummer.launcher.discord;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.skcraft.launcher.Instance;
import com.skcraft.launcher.Launcher;
import lombok.extern.java.Log;

import com.fasterxml.jackson.databind.JsonNode;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.lang.management.ManagementFactory;
import java.util.Properties;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

import static com.skcraft.launcher.util.SharedLocale.tr;

/**
 * 21.11.2026: статус у Discord (Rich Presence): «Грає в GummerCraft», у лаунчері чи в якій збірці, скільки часу,
 * і дві кнопки (лаунчер і Discord). Лише локально: лаунчер говорить з Discord на цьому ПК, нічого не йде в мережу.
 * Вимикається в налаштуваннях ({@code Configuration.discordPresence}). Без {@code discordClientId} у
 * launcher.properties нічого не робить. Discord не запущено: раз на 30 с пробує ще раз, тихо.
 */
@Log
public final class DiscordPresence {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final long RETRY_SECONDS = 15;
    /**
     * 1.36.8: файл у теці гри, який пише мод (GummerVibe у StalDayZ): {@code {"v":1,"state":"Досліджує Прип'ять"}}.
     * {@code state} іде другим рядком статусу замість «У грі»; немає файлу чи порожній {@code state}: «У грі».
     * Мод пише лише при зміні і лише з прапорцем {@code -Dgummercraft.discord=1} (Runner). Лаунчер читає раз на 15 с
     * і стирає старий файл перед запуском гри.
     */
    public static final String STATUS_FILE = "gummercraft-discord.json";

    private static volatile DiscordPresence instance;

    private final Launcher launcher;
    private final String clientId;
    private final ScheduledExecutorService worker = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "GummerCraft Discord status");
        t.setDaemon(true);
        return t;
    });
    private final long launcherSince = System.currentTimeMillis() / 1000;

    // стан, який має бути в Discord (міняється з будь-якого потоку, відправляє лише worker)
    private volatile boolean enabled;
    private volatile String gameTitle;   // null: гравець у лаунчері
    private volatile String gameName;    // назва збірки на диску (staldayz, forge-1.16.5): своя картинка discordLargeImage.<назва>
    private volatile long gameSince;
    private volatile File gameDir;       // тека гри: звідти STATUS_FILE від мода
    private String gameDetail;           // рядок від мода (лише в потоці worker)

    private DiscordIpc ipc;              // лише в потоці worker
    private String sent;                 // останній відправлений статус, щоб не слати те саме

    private DiscordPresence(Launcher launcher, String clientId) {
        this.launcher = launcher;
        this.clientId = clientId;
        this.enabled = launcher.getConfig().isDiscordPresence();
    }

    /** На старті лаунчера (після показу вікна). */
    public static synchronized void start(Launcher launcher) {
        if (instance != null) return;
        String id = launcher.getProperties().getProperty("discordClientId", "").trim();
        if (id.isEmpty() || id.startsWith("$")) {
            log.info("Discord status: no discordClientId, off");
            return;
        }
        DiscordPresence p = new DiscordPresence(launcher, id);
        instance = p;
        p.worker.scheduleWithFixedDelay(p::sync, 2, RETRY_SECONDS, TimeUnit.SECONDS);
    }

    /** Гра запущена (з LaunchProcessHandler). */
    public static void gameStarted(Instance inst, long startedAtMillis) {
        DiscordPresence p = instance;
        if (p == null || inst == null) return;
        p.gameTitle = inst.getTitle() != null ? inst.getTitle() : inst.getName();
        p.gameName = inst.getName();
        p.gameSince = startedAtMillis / 1000;
        File dir = inst.getContentDir();
        if (dir != null) new File(dir, STATUS_FILE).delete(); // залишок з минулої гри
        p.gameDir = dir;
        p.kick();
    }

    /** Гра закрилась: знову «У лаунчері». */
    public static void gameEnded() {
        DiscordPresence p = instance;
        if (p == null) return;
        p.gameTitle = null;
        p.gameDir = null;
        p.kick();
    }

    /** Перемикач у налаштуваннях. */
    public static void setEnabled(boolean on) {
        DiscordPresence p = instance;
        if (p == null) return;
        p.enabled = on;
        p.kick();
    }

    private void kick() {
        worker.execute(this::sync);
    }

    /** Привести статус у Discord до поточного стану (лише в потоці worker). */
    private void sync() {
        try {
            if (!enabled) {
                if (ipc != null) {
                    try {
                        ipc.command(setActivity(null));
                    } catch (IOException ignored) {
                    }
                    disconnect();
                }
                return;
            }
            gameDetail = gameTitle != null ? readGameStatus(gameDir) : null;
            String want = setActivity(activity());
            if (ipc == null) {
                ipc = DiscordIpc.connect(clientId);
                if (ipc == null) return; // Discord не запущено: спробуємо за 30 с
                log.info("Discord status: connected");
                sent = null;
            }
            if (want.equals(sent)) return;
            ipc.command(want);
            sent = want;
        } catch (IOException e) {
            log.log(Level.FINE, "Discord status: connection lost", e);
            disconnect();
        } catch (RuntimeException e) {
            log.log(Level.WARNING, "Discord status failed", e);
            disconnect();
        }
    }

    /** Рядок від мода з {@link #STATUS_FILE} або null. */
    private static String readGameStatus(File dir) {
        if (dir == null) return null;
        File f = new File(dir, STATUS_FILE);
        if (!f.isFile() || f.length() > 4096) return null;
        try {
            JsonNode n = JSON.readTree(Files.readAllBytes(f.toPath()));
            String s = n.path("state").asText("").replaceAll("\\s+", " ").trim();
            return s.length() < 2 ? null : s;
        } catch (IOException | RuntimeException e) {
            return null; // мод саме пише файл: наступна спроба за 15 с
        }
    }

    private void disconnect() {
        if (ipc != null) ipc.close();
        ipc = null;
        sent = null;
    }

    /** Що показати зараз. */
    private ObjectNode activity() {
        ObjectNode a = JSON.createObjectNode();
        String title = gameTitle;
        if (title != null) {
            a.put("details", cut(title));
            String detail = gameDetail;
            a.put("state", cut(detail != null ? detail : tr("discord.inGame")));
            a.putObject("timestamps").put("start", gameSince);
        } else {
            a.put("details", cut(tr("discord.inLauncher")));
            a.putObject("timestamps").put("start", launcherSince);
        }
        Properties props = launcher.getProperties();
        // 21.11.2026: своя картинка для збірки (discordLargeImage.staldayz=staldayz), інакше загальна
        String image = props.getProperty("discordLargeImage", "").trim();
        String largeText = "GummerCraft";
        String name = gameName;
        if (title != null && name != null) {
            String own = props.getProperty("discordLargeImage." + name.toLowerCase(java.util.Locale.ROOT), "").trim();
            if (!own.isEmpty()) {
                image = own;
                largeText = cut(title);
            }
        }
        if (!image.isEmpty()) {
            a.putObject("assets").put("large_image", image).put("large_text", largeText);
        }
        ArrayNode buttons = JSON.createArrayNode();
        button(buttons, tr("discord.button.launcher"), props.getProperty("discordButtonLauncherUrl", ""));
        button(buttons, tr("discord.button.discord"), props.getProperty("discordButtonDiscordUrl", ""));
        if (buttons.size() > 0) a.set("buttons", buttons);
        return a;
    }

    private static void button(ArrayNode buttons, String label, String url) {
        url = url.trim();
        if (url.isEmpty() || label == null || label.isEmpty()) return;
        // 21.11.2026: Discord рахує ліміт підпису (32) у байтах UTF-8, не в символах. Довший підпис локальний клієнт
        // приймає і показує тобі, а сервер Discord мовчки відкидає весь статус: друзі не бачили нічого
        // («Завантажити лаунчер» = 37 байт). Перевірено пробами на справжньому Discord.
        buttons.addObject().put("label", fitBytes(label, 32)).put("url", url);
    }

    /** Discord приймає рядки від 2 до 128 байт UTF-8 (див. button). */
    private static String cut(String s) {
        if (s == null) s = "";
        s = s.trim();
        if (s.length() < 2) s = s + "  ";
        String fit = fitBytes(s, 128);
        return fit.equals(s) ? s : fitBytes(s, 125) + "…"; // «…» займає 3 байти
    }

    /** Найдовший початок рядка, що влазить у max байт UTF-8 (не ріже символ навпіл). */
    static String fitBytes(String s, int max) {
        if (s.getBytes(java.nio.charset.StandardCharsets.UTF_8).length <= max) return s;
        int bytes = 0, i = 0;
        while (i < s.length()) {
            int cp = s.codePointAt(i);
            int n = cp < 0x80 ? 1 : cp < 0x800 ? 2 : cp < 0x10000 ? 3 : 4;
            if (bytes + n > max) break;
            bytes += n;
            i += Character.charCount(cp);
        }
        return s.substring(0, i).trim();
    }

    /** Команда SET_ACTIVITY. Nonce сталий: відповіді читаються по черзі, а однакові статуси так порівнюються рядком. */
    private String setActivity(ObjectNode activity) {
        ObjectNode cmd = JSON.createObjectNode();
        cmd.put("cmd", "SET_ACTIVITY");
        ObjectNode args = cmd.putObject("args");
        args.put("pid", pid());
        if (activity != null) args.set("activity", activity);
        cmd.put("nonce", "gc");
        return cmd.toString();
    }

    private static long pid() {
        String name = ManagementFactory.getRuntimeMXBean().getName(); // «1234@host», працює і на Java 8
        try {
            return Long.parseLong(name.substring(0, name.indexOf('@')));
        } catch (RuntimeException e) {
            return 0;
        }
    }
}
