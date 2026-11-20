/*
 * Copyright (C) 2021-2026 GummerCraft <http://gummercraft.fun>
 */

package com.gummer.launcher.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gummer.launcher.shared.Toolbox;
import com.gummer.launcher.shared.TrustedTls;
import com.gummer.launcher.util.Json;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 1.32: публічний Client API Chatwoot (вхід типу API «Лаунчер» на support.gummercraft.fun).
 *
 * <p>Секретів тут немає: ідентифікатор входу публічний, як токен віджета на сайті. Гравця визначає {@code source_id}
 * контакту, який Chatwoot видає при створенні контакту і який лаунчер зберігає в {@code support.json}. Приватні
 * нотатки адмінів Client API не віддає ({@code MessageFinder filter_internal_messages}).</p>
 */
public class SupportClient {

    private static final int TIMEOUT_MS = 20000;

    private final String base;
    private final ObjectMapper mapper = Json.newMapper();

    /**
     * @param baseUrl    адреса Chatwoot, наприклад https://support.gummercraft.fun
     * @param inboxId    ідентифікатор входу типу API (Channel::Api.identifier)
     */
    public SupportClient(String baseUrl, String inboxId) {
        String b = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.base = b + "/public/api/v1/inboxes/" + enc(inboxId);
    }

    /** Новий контакт. Повертає source_id. */
    public String createContact(Map<String, Object> contact) throws IOException {
        JsonNode r = json("POST", "/contacts", contact);
        String sourceId = r.path("source_id").asText("");
        if (sourceId.isEmpty()) throw new IOException("Chatwoot did not return source_id");
        return sourceId;
    }

    /** Оновити нік, пошту, Telegram контакту. */
    public void updateContact(String sourceId, Map<String, Object> contact) throws IOException {
        json("PATCH", "/contacts/" + enc(sourceId), contact);
    }

    /** Нова розмова, повертає її номер (display_id). */
    public int createConversation(String sourceId, Map<String, Object> customAttributes) throws IOException {
        java.util.Map<String, Object> body = new java.util.HashMap<String, Object>();
        body.put("custom_attributes", customAttributes);
        JsonNode r = json("POST", "/contacts/" + enc(sourceId) + "/conversations", body);
        int id = r.path("id").asInt(0);
        if (id <= 0) throw new IOException("Chatwoot did not return conversation id");
        return id;
    }

    /** Стан розмови: open, resolved, pending, snoozed. */
    public String conversationStatus(String sourceId, int conversationId) throws IOException {
        return json("GET", "/contacts/" + enc(sourceId) + "/conversations/" + conversationId, null).path("status").asText("");
    }

    public void sendMessage(String sourceId, int conversationId, String content) throws IOException {
        java.util.Map<String, Object> body = new java.util.HashMap<String, Object>();
        body.put("content", content);
        body.put("echo_id", UUID.randomUUID().toString());
        json("POST", messagesPath(sourceId, conversationId), body);
    }

    /** Повідомлення з файлами (лог). Кожен файл: ім'я -> вміст. */
    public void sendFiles(String sourceId, int conversationId, String content, List<Map.Entry<String, byte[]>> files) throws IOException {
        String boundary = "----GummerCraft" + UUID.randomUUID().toString().replace("-", "");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        part(out, boundary, "content", null, content.getBytes(StandardCharsets.UTF_8));
        for (Map.Entry<String, byte[]> f : files) {
            part(out, boundary, "attachments[]", f.getKey(), f.getValue());
        }
        out.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        request("POST", messagesPath(sourceId, conversationId), out.toByteArray(), "multipart/form-data; boundary=" + boundary);
    }

    /** Останні повідомлення розмови (гравця і адмінів, без нотаток), від старих до нових. */
    public List<SupportMessage> messages(String sourceId, int conversationId) throws IOException {
        JsonNode arr = json("GET", messagesPath(sourceId, conversationId), null);
        java.util.List<SupportMessage> list = new java.util.ArrayList<SupportMessage>();
        if (arr.isArray()) {
            for (JsonNode m : arr) list.add(parse(m, conversationId));
        }
        list.sort((a, b) -> Long.compare(a.id, b.id));
        return list;
    }

    /**
     * 20.11.2026: усі розмови цього контакту з їх повідомленнями одним запитом (Client API «list conversations»: лише
     * розмови цього source_id; повідомлення в scope chat, тобто без приватних нотаток і службових подій), від старих
     * до нових. Для спільного чату всіх звернень гравця.
     */
    public List<SupportMessage> allMessages(String sourceId) throws IOException {
        JsonNode arr = json("GET", "/contacts/" + enc(sourceId) + "/conversations", null);
        java.util.List<SupportMessage> list = new java.util.ArrayList<SupportMessage>();
        if (arr.isArray()) {
            for (JsonNode c : arr) {
                int conv = c.path("id").asInt(0);
                for (JsonNode m : c.path("messages")) list.add(parse(m, conv));
            }
        }
        list.sort((a, b) -> Long.compare(a.id, b.id));
        return list;
    }

    private static SupportMessage parse(JsonNode m, int conversationId) {
        SupportMessage msg = new SupportMessage();
        msg.id = m.path("id").asLong();
        msg.conversationId = m.path("conversation_id").asInt(conversationId);
        msg.content = m.path("content").isNull() ? "" : m.path("content").asText("");
        msg.messageType = m.path("message_type").asInt();
        msg.createdAt = m.path("created_at").asLong() * 1000L;
        JsonNode sender = m.path("sender");
        msg.senderName = sender.path("available_name").asText(sender.path("name").asText(""));
        for (JsonNode a : m.path("attachments")) {
            String url = a.path("data_url").asText("");
            if (!url.isEmpty()) msg.attachments.add(url);
        }
        return msg;
    }

    private String messagesPath(String sourceId, int conversationId) {
        return "/contacts/" + enc(sourceId) + "/conversations/" + conversationId + "/messages";
    }

    private JsonNode json(String method, String path, Object body) throws IOException {
        byte[] bytes = body != null ? mapper.writeValueAsBytes(body) : null;
        byte[] resp = request(method, path, bytes, "application/json");
        return resp.length == 0 ? mapper.createObjectNode() : mapper.readTree(resp);
    }

    private byte[] request(String method, String path, byte[] body, String contentType) throws IOException {
        TrustedTls.install();
        HttpURLConnection conn = (HttpURLConnection) new URL(base + path).openConnection();
        try {
            conn.setConnectTimeout(TIMEOUT_MS);
            conn.setReadTimeout(TIMEOUT_MS);
            conn.setUseCaches(false);
            conn.setRequestProperty("User-Agent", Toolbox.USER_AGENT);
            conn.setRequestProperty("Accept", "application/json");
            if ("PATCH".equals(method)) {
                // HttpURLConnection не знає PATCH: Rails розуміє POST з _method
                conn.setRequestMethod("POST");
                conn.setRequestProperty("X-HTTP-Method-Override", "PATCH");
            } else {
                conn.setRequestMethod(method);
            }
            if (body != null) {
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", contentType);
                conn.setFixedLengthStreamingMode(body.length);
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(body);
                }
            }
            int code = conn.getResponseCode();
            InputStream in = code >= 200 && code < 300 ? conn.getInputStream() : conn.getErrorStream();
            byte[] resp = in != null ? readAll(in) : new byte[0];
            if (code < 200 || code >= 300) {
                throw new SupportException(code, method + " " + path + ": HTTP " + code);
            }
            return resp;
        } finally {
            conn.disconnect();
        }
    }

    private static void part(OutputStream out, String boundary, String name, String filename, byte[] data) throws IOException {
        StringBuilder h = new StringBuilder("--").append(boundary).append("\r\n")
                .append("Content-Disposition: form-data; name=\"").append(name).append('"');
        if (filename != null) {
            h.append("; filename=\"").append(filename.replace("\"", "")).append('"').append("\r\n")
                    .append("Content-Type: text/plain; charset=utf-8");
        }
        h.append("\r\n\r\n");
        out.write(h.toString().getBytes(StandardCharsets.UTF_8));
        out.write(data);
        out.write("\r\n".getBytes(StandardCharsets.UTF_8));
    }

    private static byte[] readAll(InputStream in) throws IOException {
        try (InputStream is = in) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = is.read(buf)) > 0) out.write(buf, 0, n);
            return out.toByteArray();
        }
    }

    private static String enc(String s) {
        try {
            return URLEncoder.encode(s, "UTF-8").replace("+", "%20");
        } catch (java.io.UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Помилка HTTP з кодом (404 - розмову чи контакт видалено, 429 - забагато запитів). */
    public static class SupportException extends IOException {
        public final int code;

        SupportException(int code, String message) {
            super(message);
            this.code = code;
        }
    }
}
