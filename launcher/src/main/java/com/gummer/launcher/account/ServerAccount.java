package com.gummer.launcher.account;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gummer.launcher.prefs.LauncherPrefs;
import com.gummer.launcher.shared.Toolbox;
import com.gummer.launcher.shared.TrustedTls;
import com.gummer.launcher.util.Json;
import com.skcraft.launcher.util.SharedLocale;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.UUID;

/**
 * 1.33: необов'язковий акаунт сервера (AuthMe) у лаунчері: вхід, реєстрація заздалегідь і скіни.
 *
 * <p>Гравець і далі може грати з будь-яким локальним акаунтом без цього. Акаунт сервера потрібен лише для
 * того, щоб завантажити скін на наші сервери (SkinsRestorer) або зареєструвати нік до першого входу в гру
 * (тоді на сервері лишається лише {@code /login пароль}).</p>
 *
 * <p>Сервер: {@code /api/launcher/<дія>} на сайті скінів (node01, {@code resources/server/launcherApi.php}).
 * Хост вибирає сервер скінів: {@code dayz.gummercraft.fun} для StalDayZ, {@code skin.gummercraft.fun} для
 * GummerCraft (Main). Акаунти AuthMe в обох спільні, тож один токен діє на обох.</p>
 *
 * <p>Пароль ніде не зберігається: після входу лишається лише токен (30 днів) у {@code launcher-ui.json}.
 * Зміна пароля в грі чи адміном робить токен недійсним, тоді лаунчер просить увійти знову.</p>
 *
 * <p>Лише модуль {@code launcher}: класи {@code launcher-shared} старий exe (bootstrap 1.29-1.31) підміняє своїми.</p>
 */
public final class ServerAccount {

    /** Сервери скінів. */
    public enum Target {
        STALDAYZ("serverAccountUrl.staldayz", "https://dayz.gummercraft.fun/api/launcher", "StalDayZ"),
        GUMMERCRAFT("serverAccountUrl.gummercraft", "https://skin.gummercraft.fun/api/launcher", "GummerCraft");

        final String property;
        final String fallback;
        public final String title;

        Target(String property, String fallback, String title) {
            this.property = property;
            this.fallback = fallback;
            this.title = title;
        }
    }

    public static final class Login {
        public final String token;
        public final String nick;
        public final String uuid;
        public final long expires;

        Login(String token, String nick, String uuid, long expires) {
            this.token = token;
            this.nick = nick;
            this.uuid = uuid;
            this.expires = expires;
        }
    }

    public static final class Status {
        public final String nick;
        public final String uuid;
        public final boolean hasSkin;

        Status(String nick, String uuid, boolean hasSkin) {
            this.nick = nick;
            this.uuid = uuid;
            this.hasSkin = hasSkin;
        }
    }

    /** Відмова сервера з кодом ({@code bad_credentials}, {@code nick_taken}...) або {@code network}. */
    public static final class AccountException extends IOException {
        public final String code;
        public final int retryAfter;

        AccountException(String code, int retryAfter, Throwable cause) {
            super(code, cause);
            this.code = code;
            this.retryAfter = retryAfter;
        }

        /** Текст для гравця з мовного файлу ({@code serverAccount.error.<код>}). */
        public String text() {
            String key = "serverAccount.error." + code;
            String s = SharedLocale.tr(key);
            if (s == null || s.startsWith("${")) s = SharedLocale.tr("serverAccount.error.unknown", code);
            return s;
        }

        public boolean isTokenInvalid() {
            return "token_invalid".equals(code);
        }
    }

    private static final int TIMEOUT_MS = 20000;
    private static final int SKIN_TIMEOUT_MS = 90000;
    private static final ObjectMapper MAPPER = Json.newMapper();

    private static final String K_TOKEN = "serverAccountToken";
    private static final String K_NICK = "serverAccountNick";
    private static final String K_UUID = "serverAccountUuid";
    private static final String K_EXPIRES = "serverAccountExpires";

    private final Properties properties;

    public ServerAccount(Properties launcherProperties) {
        this.properties = launcherProperties;
    }

    // ------------------------------------------------------------------ збережений вхід

    /** Збережений вхід або null (немає чи прострочений). */
    public static Login saved() {
        String token = LauncherPrefs.getString(K_TOKEN, "");
        if (token.isEmpty()) return null;
        long exp;
        try {
            exp = Long.parseLong(LauncherPrefs.getString(K_EXPIRES, "0"));
        } catch (NumberFormatException e) {
            exp = 0;
        }
        if (exp * 1000L < System.currentTimeMillis()) {
            forget();
            return null;
        }
        return new Login(token, LauncherPrefs.getString(K_NICK, ""), LauncherPrefs.getString(K_UUID, ""), exp);
    }

    private static void remember(Login l) {
        LauncherPrefs.set(K_NICK, l.nick);
        LauncherPrefs.set(K_UUID, l.uuid);
        LauncherPrefs.set(K_EXPIRES, String.valueOf(l.expires));
        LauncherPrefs.set(K_TOKEN, l.token);
    }

    /** Вийти: забути токен (на сервері нічого не міняється). */
    public static void forget() {
        LauncherPrefs.set(K_TOKEN, "");
        LauncherPrefs.set(K_EXPIRES, "0");
    }

    // ------------------------------------------------------------------ запити

    public Login login(String nick, String password) throws IOException {
        JsonNode n = post(Target.STALDAYZ, "login", form("username", nick, "password", password), TIMEOUT_MS);
        Login l = loginFrom(n);
        remember(l);
        return l;
    }

    public Login register(String nick, String password) throws IOException {
        JsonNode n = post(Target.STALDAYZ, "register", form("username", nick, "password", password), TIMEOUT_MS);
        Login l = loginFrom(n);
        remember(l);
        return l;
    }

    public Status status(Target target, Login login) throws IOException {
        JsonNode n = post(target, "status", form("token", login.token), TIMEOUT_MS);
        return new Status(n.path("nick").asText(login.nick), n.path("uuid").asText(login.uuid), n.path("skin").asBoolean(false));
    }

    /**
     * 1.33: «Особистий кабінет» (дія profile, лише дані самого гравця за токеном). Поля: registered, last_login (мс),
     * stats (чи є дані Plan і LuckPerms), first_join, total_ms, servers [{name, ms, last, sessions}], kills, deaths,
     * groups [{net, name, expires (с, 0 = назавжди)}], regions [{server, name}] (якщо сервер їх знає).
     */
    public JsonNode profile(Login login) throws IOException {
        return post(Target.STALDAYZ, "profile", form("token", login.token), TIMEOUT_MS);
    }

    public void uploadSkin(Target target, Login login, byte[] png, boolean slim) throws IOException {
        String boundary = "----GummerCraft" + UUID.randomUUID().toString().replace("-", "");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        part(out, boundary, "token", null, login.token.getBytes(StandardCharsets.UTF_8));
        part(out, boundary, "slim", null, (slim ? "1" : "0").getBytes(StandardCharsets.UTF_8));
        part(out, boundary, "file", "skin.png", png);
        out.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        post(target, "skin", new Body(out.toByteArray(), "multipart/form-data; boundary=" + boundary), SKIN_TIMEOUT_MS);
        // 14.11.2026: «готово» лише тоді, коли сервер справді прив'язав скін до гравця, а не лише відповів ok
        if (!status(target, login).hasSkin) throw new AccountException("skin_not_saved", 0, null);
    }

    /**
     * Скін для голови в кабінеті і на кнопці «Акаунт» (14.11.2026): спершу GummerCraft, потім StalDayZ, але лише там,
     * де гравець справді має скін (status). Маршрут download для гравця без скіна віддає стандартного Стіва з кодом 200,
     * тож раніше голова бралась лише зі StalDayZ, і в того, хто ставив скін тільки на GummerCraft, був Стів.
     * Скіна ніде немає: стандартний з GummerCraft (як і раніше), або null без мережі.
     */
    public byte[] ownSkin(Login login) {
        if (login == null) return null;
        for (Target t : new Target[]{Target.GUMMERCRAFT, Target.STALDAYZ}) {
            try {
                Status st = status(t, login);
                if (st.hasSkin) {
                    byte[] png = currentSkin(t, st.uuid);
                    if (png != null) return png;
                }
            } catch (IOException | RuntimeException e) {
                // недійсний токен чи немає мережі: пробуємо далі, у кінці стандартний
            }
        }
        return currentSkin(Target.GUMMERCRAFT, login.uuid);
    }

    /** Поточний скін гравця на сервері (PNG) або null. Той самий маршрут, що «Завантажити» на сайті скінів. */
    public byte[] currentSkin(Target target, String uuid) {
        if (uuid == null || uuid.isEmpty()) return null;
        TrustedTls.install();
        try {
            URL url = new URL(base(target).replaceAll("/api/launcher/?$", "") + "/api/client/skin/download/" + URLEncoder.encode(uuid, "UTF-8"));
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            try {
                conn.setConnectTimeout(TIMEOUT_MS);
                conn.setReadTimeout(TIMEOUT_MS);
                conn.setRequestProperty("User-Agent", Toolbox.USER_AGENT);
                if (conn.getResponseCode() != 200) return null;
                String type = conn.getContentType();
                if (type == null || !type.toLowerCase().startsWith("image/")) return null;
                return readAll(conn.getInputStream(), 1 << 20);
            } finally {
                conn.disconnect();
            }
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    // ------------------------------------------------------------------ HTTP

    private static Login loginFrom(JsonNode n) throws AccountException {
        String token = n.path("token").asText("");
        if (token.isEmpty()) throw new AccountException("server_error", 0, null);
        return new Login(token, n.path("nick").asText(""), n.path("uuid").asText(""), n.path("expires").asLong(0));
    }

    private String base(Target target) {
        String v = properties == null ? null : properties.getProperty(target.property);
        v = v == null || v.trim().isEmpty() ? target.fallback : v.trim();
        return v.endsWith("/") ? v.substring(0, v.length() - 1) : v;
    }

    private static final class Body {
        final byte[] data;
        final String type;

        Body(byte[] data, String type) {
            this.data = data;
            this.type = type;
        }
    }

    private static Body form(String... kv) throws IOException {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            if (sb.length() > 0) sb.append('&');
            sb.append(URLEncoder.encode(kv[i], "UTF-8")).append('=').append(URLEncoder.encode(kv[i + 1] == null ? "" : kv[i + 1], "UTF-8"));
        }
        return new Body(sb.toString().getBytes(StandardCharsets.UTF_8), "application/x-www-form-urlencoded; charset=UTF-8");
    }

    private JsonNode post(Target target, String action, Body body, int timeout) throws IOException {
        HttpURLConnection conn;
        int code;
        byte[] resp;
        TrustedTls.install();
        try {
            conn = (HttpURLConnection) new URL(base(target) + "/" + action).openConnection();
            conn.setConnectTimeout(TIMEOUT_MS);
            conn.setReadTimeout(timeout);
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setRequestProperty("User-Agent", Toolbox.USER_AGENT);
            conn.setRequestProperty("Accept", "application/json");
            conn.setRequestProperty("Content-Type", body.type);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(body.data);
            }
            code = conn.getResponseCode();
            InputStream in = code >= 400 ? conn.getErrorStream() : conn.getInputStream();
            resp = in == null ? new byte[0] : readAll(in, 1 << 20);
        } catch (IOException e) {
            throw new AccountException("network", 0, e);
        }
        JsonNode n;
        try {
            n = MAPPER.readTree(resp);
        } catch (IOException e) {
            n = null;
        }
        if (n == null || !n.isObject()) {
            throw new AccountException(code >= 500 || code == 0 ? "server_down" : "server_error", 0, null);
        }
        if (!n.path("ok").asBoolean(false)) {
            String err = n.path("error").asText("server_error");
            if ("token_invalid".equals(err)) forget();
            throw new AccountException(err, n.path("retryAfter").asInt(0), null);
        }
        return n;
    }

    private static void part(OutputStream out, String boundary, String name, String filename, byte[] data) throws IOException {
        StringBuilder h = new StringBuilder("--").append(boundary).append("\r\n")
                .append("Content-Disposition: form-data; name=\"").append(name).append('"');
        if (filename != null) h.append("; filename=\"").append(filename).append('"');
        h.append("\r\n");
        if (filename != null) h.append("Content-Type: image/png\r\n");
        h.append("\r\n");
        out.write(h.toString().getBytes(StandardCharsets.UTF_8));
        out.write(data);
        out.write("\r\n".getBytes(StandardCharsets.UTF_8));
    }

    private static byte[] readAll(InputStream in, int limit) throws IOException {
        try (InputStream is = in) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int r;
            while ((r = is.read(buf)) != -1) {
                out.write(buf, 0, r);
                if (out.size() > limit) throw new IOException("response too large");
            }
            return out.toByteArray();
        }
    }
}
