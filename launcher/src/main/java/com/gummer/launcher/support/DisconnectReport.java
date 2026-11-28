/*
 * Copyright (C) 2021-2026 GummerCraft <http://gummercraft.fun>
 */

package com.gummer.launcher.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.gummer.launcher.util.Json;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.skcraft.launcher.util.SharedLocale.tr;

/**
 * 1.33 (13.11.2026): звіт про відключення від сервера, який гравець надсилає кнопкою «Надіслати звіт у техпідтримку» на екрані
 * відключення гри. Кнопку додають наші моди: GummerVibe (StalDayZ, Forge 1.16.5) і міні-мод {@code gcreport} у збірках GummerCraft на
 * Fabric. Мод пише в теку гри файл {@value #FILE_NAME}:
 * <pre>{"v":1,"kind":"disconnect","game":"staldayz"|"gummercraft","time":1731500000000,"server":"play.staldayz.com",
 *  "reason":"текст з екрана відключення","source":"gummervibe 2.x"}</pre>
 * Лаунчер, поки гра запущена, раз на 2 с перевіряє файл ({@code LaunchProcessHandler}), забирає його (видаляє, щоб мод бачив,
 * що звіт прийнято) і відкриває форму підтримки з готовою темою і текстом; лог гри додається галочкою, як завжди. Сам мод у мережу
 * нічого не шле: адреса Chatwoot, ліміти частоти і приховування токенів лишаються в лаунчері.
 * <p>
 * Другий вид запиту (13.11.2026): {@code "kind":"password"} з полем {@code "nick"} від «Забув пароль?» у вікні входу GummerVibe;
 * форма відкривається з текстом «Я забув пароль від акаунта ...». Що лаунчер слухає, мод знає з прапорця JVM
 * {@code -Dgummercraft.report=1} ({@code Runner.addPlatformArgs}); без нього мод одразу веде в Telegram-бот підтримки.
 */
public final class DisconnectReport {

    public static final String FILE_NAME = "gummercraft-report.json";
    /** Старіші запити (гра була запущена не з лаунчера, лаунчер тоді не працював) просто видаляються. */
    private static final long MAX_AGE_MS = 24L * 60 * 60 * 1000;
    private static final int MAX_REASON = 2000;
    private static final Logger log = Logger.getLogger(DisconnectReport.class.getName());

    /** {@code disconnect} (кнопка на екрані відключення) або {@code password} («Забув пароль?» у вікні входу GummerVibe). */
    public final String kind;
    public final String game;
    public final long time;
    public final String server;
    public final String reason;
    public final String source;
    /** Нік гравця (для {@code password}). */
    public final String nick;

    private DisconnectReport(String kind, String nick, String game, long time, String server, String reason, String source) {
        this.kind = kind;
        this.nick = nick;
        this.game = game;
        this.time = time;
        this.server = server;
        this.reason = reason;
        this.source = source;
    }

    /** Забирає запит з теки гри: читає і видаляє файл. null, якщо файлу немає, він зіпсований або застарий. */
    public static DisconnectReport take(File gameDir) {
        if (gameDir == null) return null;
        File f = new File(gameDir, FILE_NAME);
        if (!f.isFile()) return null;
        try {
            JsonNode n = Json.newMapper().readTree(f);
            long time = n.path("time").asLong(f.lastModified());
            if (System.currentTimeMillis() - time > MAX_AGE_MS) {
                log.info("Disconnect report is too old, dropped: " + f);
                return null;
            }
            String reason = n.path("reason").asText("").trim();
            if (reason.length() > MAX_REASON) reason = reason.substring(0, MAX_REASON) + "...";
            return new DisconnectReport(n.path("kind").asText("disconnect"), n.path("nick").asText("").trim(), n.path("game").asText(""), time, n.path("server").asText("").trim(), reason,
                    n.path("source").asText(""));
        } catch (IOException | RuntimeException e) {
            log.log(Level.WARNING, "Bad disconnect report " + f, e);
            return null;
        } finally {
            if (!f.delete()) f.deleteOnExit();
        }
    }

    /** Тема форми підтримки (ті самі, що в {@link SupportService#TOPICS}). */
    public String topic() {
        return "staldayz".equalsIgnoreCase(game) ? "StalDayZ" : "Main (GummerCraft)";
    }

    /** Текст звернення: що сталося, гравець дописує решту. */
    public String text() {
        String when = new SimpleDateFormat("dd.MM.yyyy HH:mm").format(new Date(time));
        String srv = server.isEmpty() ? tr("support.disconnectUnknownServer") : server;
        if (isPassword()) return tr("support.passwordText", nick.isEmpty() ? "?" : nick, srv);
        String why = reason.isEmpty() ? tr("support.disconnectUnknownReason") : reason;
        return tr("support.disconnectText", srv, when, why);
    }

    public boolean isPassword() {
        return "password".equalsIgnoreCase(kind);
    }

    /**
     * 1.41.6 (28.11.2026, тікети #146, #148, #149): сервер (AuthMe) викинув, бо не дочекався {@code /login} чи пароль не
     * той. Нові гравці реєструють нік у лаунчері, а на сервері чекають {@code /register} чи не пам'ятають пароль.
     */
    public boolean isAuthTimeout() {
        if (isPassword()) return false;
        String r = reason.toLowerCase(java.util.Locale.ROOT);
        return r.contains("час очікування авторизації") || r.contains("время авторизации") || r.contains("login timeout")
                || r.contains("неправильний пароль") || r.contains("неверный пароль") || r.contains("wrong password");
    }

    /** Текст звернення «забув пароль» для цього відключення. */
    public String passwordText(String fallbackNick) {
        String n = !nick.isEmpty() ? nick : (fallbackNick != null && !fallbackNick.trim().isEmpty() ? fallbackNick.trim() : "?");
        return tr("support.passwordText", n, server.isEmpty() ? tr("support.disconnectUnknownServer") : server);
    }

    @Override
    public String toString() {
        return "DisconnectReport{kind=" + kind + ", nick=" + nick + ", game=" + game + ", server=" + server + ", source=" + source + ", reason=" + reason + "}";
    }
}
