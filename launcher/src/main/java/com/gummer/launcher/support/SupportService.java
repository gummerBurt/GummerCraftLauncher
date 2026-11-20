/*
 * Copyright (C) 2021-2026 GummerCraft <http://gummercraft.fun>
 */

package com.gummer.launcher.support;

import com.gummer.launcher.shared.Toolbox;
import com.gummer.launcher.util.Json;
import com.skcraft.launcher.Instance;
import com.skcraft.launcher.Launcher;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * 1.32: звернення в підтримку з лаунчера. Форма (нік, тема, текст, контакт, лог) створює розмову у вході Chatwoot
 * «Лаунчер», далі вікно працює як чат цієї розмови. Помічник Chatwoot ({@code Chatwoot/helper/helper.py}) призначає
 * адміна, пише ігрові дані за ніком і сповіщає службовий чат, як для інших входів.
 */
public class SupportService {

    private static final Logger log = Logger.getLogger(SupportService.class.getName());

    /** Ті самі теми, що у формі сайтів (атрибут розмови problem_with у Chatwoot, 01-web-inboxes.rb). */
    public static final List<String> TOPICS = Arrays.asList(
            "StalDayZ", "Main (GummerCraft)", "Інший сервер GummerCraft", "Лаунчер", "Сайт", "Донат", "Інше");
    /** Ніки адмінів: гравець не може ними назватись (як у формі сайтів і боті). */
    private static final List<String> ADMIN_NICKS = Arrays.asList("gummer", "astrynx");
    private static final Pattern NICK = Pattern.compile("[A-Za-z0-9_]{3,16}");
    /** Обмеження частоти нових звернень: не частіше раз на 2 хв і не більше 3 за годину. */
    private static final long MIN_GAP_MS = 2 * 60 * 1000L, HOUR_MS = 60 * 60 * 1000L;
    private static final int MAX_PER_HOUR = 3;
    /** Скільки останніх байтів кожного лога відправляти. */
    private static final int LOG_TAIL_BYTES = 1024 * 1024;

    private final Launcher launcher;
    private final SupportClient client;
    private final File stateFile;
    private SupportState state;

    private SupportService(Launcher launcher, SupportClient client) {
        this.launcher = launcher;
        this.client = client;
        this.stateFile = new File(launcher.getBaseDir(), "support.json");
        this.state = load();
    }

    /** null, якщо в launcher.properties не задано supportUrl або supportInboxId (кнопки підтримки тоді немає). */
    public static SupportService create(Launcher launcher) {
        String url = launcher.getProperties().getProperty("supportUrl", "").trim();
        String inbox = launcher.getProperties().getProperty("supportInboxId", "").trim();
        if (url.isEmpty() || inbox.isEmpty()) return null;
        return new SupportService(launcher, new SupportClient(url, inbox));
    }

    public synchronized SupportState getState() {
        return state;
    }

    public synchronized boolean hasConversation() {
        return state.sourceId != null && state.conversationId > 0;
    }

    /** Нік за замовчуванням: збережений або останнього акаунта лаунчера. */
    public synchronized String defaultNick() {
        if (state.nick != null && !state.nick.isEmpty()) return state.nick;
        try {
            if (launcher.getAccounts().getSize() > 0) {
                String u = launcher.getAccounts().getElementAt(0).getUsername();
                if (u != null && NICK.matcher(u).matches()) return u;
            }
        } catch (RuntimeException ignored) {
        }
        return "";
    }

    /** Тема за замовчуванням за вибраним модпаком. */
    public static String defaultTopic(Instance selected) {
        String n = selected != null && selected.getName() != null ? selected.getName().toLowerCase(Locale.ROOT) : "";
        if (n.contains("staldayz")) return "StalDayZ";
        if (n.startsWith("main")) return "Main (GummerCraft)";
        return "Лаунчер";
    }

    /** Перевірка форми: ключ тексту помилки з lang (support.error*) або null. */
    public static String validate(String nick, String topic, String text) {
        if (nick == null || !NICK.matcher(nick.trim()).matches()) return "support.errorNick";
        if (ADMIN_NICKS.contains(nick.trim().toLowerCase(Locale.ROOT))) return "support.errorAdminNick";
        if (topic == null || !TOPICS.contains(topic)) return "support.errorTopic";
        if (text == null || text.trim().length() < 10) return "support.errorText";
        return null;
    }

    /** Ключ тексту помилки, якщо нове звернення зараз не можна (частота), інакше null. */
    public synchronized String rateLimitError() {
        long now = System.currentTimeMillis();
        for (Iterator<Long> it = state.createdAt.iterator(); it.hasNext(); ) {
            if (now - it.next() > HOUR_MS) it.remove();
        }
        if (!state.createdAt.isEmpty() && now - state.createdAt.get(state.createdAt.size() - 1) < MIN_GAP_MS) {
            return "support.errorTooFast";
        }
        if (state.createdAt.size() >= MAX_PER_HOUR) return "support.errorTooMany";
        return null;
    }

    /**
     * Нове звернення (у фоні, не з EDT). Повертає номер розмови.
     *
     * @param withLogs додати лог лаунчера, лог гри і дані системи вкладенням
     */
    public int submit(String nick, String topic, String text, String contact, boolean withLogs, Instance selected) throws IOException {
        nick = nick.trim();
        contact = contact == null ? "" : contact.trim();
        Map<String, Object> contactBody = contactBody(nick, contact);

        String sourceId;
        synchronized (this) {
            sourceId = state.sourceId;
        }
        if (sourceId == null) {
            sourceId = client.createContact(contactBody);
            rememberContact(sourceId, nick, contact); // далі щось впаде: повторна спроба не створить ще один контакт
        } else {
            try {
                client.updateContact(sourceId, contactBody);
            } catch (SupportClient.SupportException e) {
                if (e.code == 404) {
                    sourceId = client.createContact(contactBody); // контакт видалили в Chatwoot: новий
                    rememberContact(sourceId, nick, contact);
                } else {
                    // наприклад, пошта вже в іншого контакту (422): нік і так піде в атрибутах розмови
                    log.log(Level.INFO, "Support: contact update failed, continuing", e);
                }
            }
        }

        Map<String, Object> attrs = new HashMap<String, Object>();
        attrs.put("game_nick", nick);
        attrs.put("problem_with", topic);
        attrs.put("source", "лаунчер");
        attrs.put("launcher_version", launcher.getVersion());
        attrs.put("launcher_os", System.getProperty("os.name") + " " + System.getProperty("os.version"));
        attrs.put("launcher_java", javaLine());
        if (selected != null) attrs.put("launcher_modpack", selected.getTitle() != null ? selected.getTitle() : selected.getName());
        int conv = client.createConversation(sourceId, attrs);

        synchronized (this) {
            state.sourceId = sourceId;
            state.nick = nick;
            state.contact = contact;
            state.conversationId = conv;
            state.topic = topic;
            // 20.11.2026: не скидаємо «прочитано»: чат спільний для всіх звернень, а id повідомлень наскрізні. Раніше
            // скидання тут разом з чатом лише останньої розмови губило непрочитані відповіді (тікет #88)
            state.createdAt.add(System.currentTimeMillis());
            save();
        }

        client.sendMessage(sourceId, conv, text.trim());
        if (withLogs) {
            try {
                client.sendFiles(sourceId, conv, "Лог і дані системи (додано автоматично)", logs(selected));
            } catch (IOException e) {
                log.log(Level.WARNING, "Support: could not attach logs", e);
                client.sendMessage(sourceId, conv, "Лог не вдалося додати: " + e.getMessage() + "\n\n" + systemInfo(selected));
            }
        }
        return conv;
    }

    /**
     * 16.11.2026: фідбек по дизайну лаунчера (кнопка у кожному вигляді). Окрема розмова в тій самій скриньці Chatwoot
     * «Лаунчер», тема «Лаунчер»; поточну розмову гравця з підтримкою (state.conversationId) не підміняє. У фоні.
     *
     * @param nick  нік (може бути порожнім)
     * @param look  ключ вигляду (glass, classic, library), іде в атрибут launcher_design
     * @param title назва вигляду для людини (Liquid Glass, Класика...)
     */
    public int submitDesignFeedback(String nick, String look, String title, String text) throws IOException {
        nick = nick == null ? "" : nick.trim();
        String name = NICK.matcher(nick).matches() ? nick : "Гравець (фідбек по дизайну)";
        String sourceId;
        synchronized (this) {
            sourceId = state.sourceId;
        }
        if (sourceId == null) {
            sourceId = client.createContact(contactBody(name, ""));
            rememberContact(sourceId, nick, "");
        }
        Map<String, Object> attrs = new HashMap<String, Object>();
        if (NICK.matcher(nick).matches()) attrs.put("game_nick", nick);
        attrs.put("problem_with", "Лаунчер");
        attrs.put("source", "лаунчер: фідбек по дизайну");
        attrs.put("launcher_design", look);
        attrs.put("launcher_version", launcher.getVersion());
        attrs.put("launcher_os", System.getProperty("os.name") + " " + System.getProperty("os.version"));
        int conv;
        try {
            conv = client.createConversation(sourceId, attrs);
        } catch (SupportClient.SupportException e) {
            if (e.code != 404) throw e;
            sourceId = client.createContact(contactBody(name, "")); // контакт видалили в Chatwoot: новий
            rememberContact(sourceId, nick, "");
            conv = client.createConversation(sourceId, attrs);
        }
        client.sendMessage(sourceId, conv, "Фідбек по дизайну лаунчера («" + title + "»):\n\n" + text.trim());
        return conv;
    }

    /**
     * 20.11.2026 (власник: «Нехай гравець матиме в лаунчері "чат", зі всіма ЙОГО зверненнями та НАШИМИ відповідями»):
     * повідомлення всіх розмов гравця одним запитом, від старих до нових. Якщо список розмов не вдався (наприклад,
     * інший сервер підтримки без цього запиту), лише поточна розмова, як раніше.
     */
    public List<SupportMessage> messages() throws IOException {
        String s;
        int c;
        synchronized (this) {
            s = state.sourceId;
            c = state.conversationId;
        }
        if (s == null || c <= 0) return new ArrayList<SupportMessage>();
        List<SupportMessage> all;
        try {
            all = client.allMessages(s);
        } catch (SupportClient.SupportException e) {
            if (e.code != 404) throw e;
            all = client.messages(s, c);
        }
        migrateSeen(all, c);
        return all;
    }

    /**
     * Один раз після оновлення зі старого лаунчера: там «прочитано» скидалось з кожним новим зверненням, тож старі
     * відповіді в попередніх розмовах інакше раптом стали б «новими». Вважаємо прочитаними відповіді, що прийшли
     * раніше за перше повідомлення поточної розмови; відповіді в поточній лишаються як є.
     */
    private synchronized void migrateSeen(List<SupportMessage> all, int current) {
        if (state.unifiedChat) return;
        long firstOfCurrent = Long.MAX_VALUE;
        for (SupportMessage m : all) if (m.conversationId == current) firstOfCurrent = Math.min(firstOfCurrent, m.id);
        if (firstOfCurrent == Long.MAX_VALUE) return; // поточної розмови в списку немає: спробуємо наступного разу
        long seen = state.lastSeenMessageId;
        for (SupportMessage m : all) {
            if (!m.isFromPlayer() && m.id < firstOfCurrent) seen = Math.max(seen, m.id);
        }
        state.lastSeenMessageId = seen;
        state.unifiedChat = true;
        save();
    }

    public void send(String text) throws IOException {
        String s;
        int c;
        synchronized (this) {
            s = state.sourceId;
            c = state.conversationId;
        }
        if (s == null || c <= 0) throw new IOException("no conversation");
        client.sendMessage(s, c, text.trim());
    }

    /** Скільки відповідей адміна гравець ще не бачив (для позначки на кнопці при старті). */
    public int unreadReplies() throws IOException {
        return unreadReplyMessages().size();
    }

    /** 20.11.2026: самі непрочитані відповіді адміна (для вікна з текстом відповіді), від старих до нових. */
    public List<SupportMessage> unreadReplyMessages() throws IOException {
        long seen;
        synchronized (this) {
            seen = state.lastSeenMessageId;
        }
        List<SupportMessage> out = new ArrayList<SupportMessage>();
        for (SupportMessage m : messages()) {
            if (!m.isFromPlayer() && m.messageType == 1 && m.id > seen) out.add(m);
        }
        return out;
    }

    public synchronized void markSeen(long messageId) {
        if (messageId > state.lastSeenMessageId) {
            state.lastSeenMessageId = messageId;
            save();
        }
    }

    /** Дані системи для адмінів (текстом, у файлі вкладення). */
    public String systemInfo(Instance selected) {
        StringBuilder sb = new StringBuilder();
        sb.append("Лаунчер: ").append(launcher.getVersion()).append('\n');
        sb.append("User-Agent: ").append(Toolbox.USER_AGENT).append('\n');
        sb.append("ОС: ").append(System.getProperty("os.name")).append(' ').append(System.getProperty("os.version"))
                .append(" (").append(System.getProperty("os.arch")).append(")\n");
        sb.append("Java лаунчера: ").append(javaLine()).append('\n');
        sb.append("Java home: ").append(System.getProperty("java.home")).append('\n');
        Runtime rt = Runtime.getRuntime();
        sb.append("Пам'ять лаунчера: ").append(rt.maxMemory() / (1024 * 1024)).append(" МБ max\n");
        long total = com.skcraft.launcher.util.Environment.getTotalSystemMemory();
        if (total > 0) sb.append("RAM системи: ").append(total / (1024 * 1024)).append(" МБ\n");
        sb.append("Тека даних: ").append(launcher.getBaseDir()).append('\n');
        sb.append("Пам'ять для гри (налаштування): ").append(launcher.getConfig().getMaxMemory()).append(" МБ\n");
        if (selected != null) {
            sb.append("Модпак: ").append(selected.getTitle()).append(" (").append(selected.getName()).append(")")
                    .append(selected.isInstalled() ? ", встановлено" : ", не встановлено")
                    .append(selected.isUpdatePending() ? ", є оновлення" : "").append('\n');
            sb.append("Тека модпака: ").append(selected.getContentDir()).append('\n');
        }
        return sb.toString();
    }

    private List<Map.Entry<String, byte[]>> logs(Instance selected) {
        List<Map.Entry<String, byte[]>> files = new ArrayList<Map.Entry<String, byte[]>>();
        files.add(entry("system.txt", systemInfo(selected).getBytes(StandardCharsets.UTF_8)));
        files.add(entry("launcher.log", tail(SupportLogBuffer.text().getBytes(StandardCharsets.UTF_8))));
        // 16.11.2026: лог попереднього запуску (гравець часто пише вже після перезапуску лаунчера)
        File previous = LauncherLogFile.previousLog(launcher.getBaseDir());
        if (previous != null) addFile(files, previous, "launcher-previous.log");
        if (selected != null) {
            File logs = new File(selected.getContentDir(), "logs");
            addFile(files, new File(logs, "latest.log"), "game-latest.log");
            File[] crashes = new File(selected.getContentDir(), "crash-reports").listFiles((d, n) -> n.endsWith(".txt"));
            if (crashes != null && crashes.length > 0) {
                File newest = crashes[0];
                for (File f : crashes) if (f.lastModified() > newest.lastModified()) newest = f;
                if (System.currentTimeMillis() - newest.lastModified() < 3L * 24 * HOUR_MS) { // свіжий, за 3 дні
                    addFile(files, newest, "crash-" + newest.getName());
                }
            }
        }
        return files;
    }

    private static void addFile(List<Map.Entry<String, byte[]>> files, File f, String name) {
        if (!f.isFile()) return;
        try (RandomAccessFile raf = new RandomAccessFile(f, "r")) {
            long len = raf.length();
            int n = (int) Math.min(len, LOG_TAIL_BYTES);
            byte[] b = new byte[n];
            raf.seek(len - n);
            raf.readFully(b);
            files.add(entry(name, SupportLogBuffer.redact(new String(b, StandardCharsets.UTF_8)).getBytes(StandardCharsets.UTF_8)));
        } catch (IOException e) {
            log.log(Level.INFO, "Support: could not read " + f, e);
        }
    }

    private static byte[] tail(byte[] b) {
        return b.length <= LOG_TAIL_BYTES ? b : Arrays.copyOfRange(b, b.length - LOG_TAIL_BYTES, b.length);
    }

    private static Map.Entry<String, byte[]> entry(String k, byte[] v) {
        return new AbstractMap.SimpleImmutableEntry<String, byte[]>(k, v);
    }

    private static String javaLine() {
        return System.getProperty("java.version") + " " + System.getProperty("java.vendor") + " "
                + System.getProperty("os.arch") + " " + com.gummer.launcher.util.JavaKind.get();
    }

    private static Map<String, Object> contactBody(String nick, String contact) {
        Map<String, Object> body = new HashMap<String, Object>();
        body.put("name", nick);
        Map<String, Object> custom = new HashMap<String, Object>();
        custom.put("game_nick", nick);
        if (contact.contains("@") && contact.indexOf('@') > 0 && contact.contains(".")) {
            body.put("email", contact);
        } else if (!contact.isEmpty()) {
            custom.put("telegram", contact.startsWith("@") ? contact : "@" + contact);
        }
        body.put("custom_attributes", custom);
        return body;
    }

    private SupportState load() {
        try {
            if (stateFile.isFile()) {
                SupportState s = Json.newMapper().readValue(stateFile, SupportState.class);
                if (s.createdAt == null) s.createdAt = new ArrayList<Long>();
                return s;
            }
        } catch (IOException | RuntimeException e) {
            log.log(Level.WARNING, "Support: could not read " + stateFile, e);
        }
        return new SupportState();
    }

    /**
     * Контакт створено, розмови ще немає: зберегти source_id одразу. 13.11.2026 у гравця зі старим exe все падало після створення
     * контакту, і кожна повторна спроба плодила новий порожній контакт у Chatwoot (чотири за хвилину).
     */
    private synchronized void rememberContact(String sourceId, String nick, String contact) {
        state.sourceId = sourceId;
        state.nick = nick;
        state.contact = contact;
        state.conversationId = 0;
        save();
    }

    private void save() {
        try {
            File tmp = new File(stateFile.getPath() + ".tmp");
            Json.newMapper().writerWithDefaultPrettyPrinter().writeValue(tmp, state);
            Files.move(tmp.toPath(), stateFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException | RuntimeException e) {
            log.log(Level.WARNING, "Support: could not save " + stateFile, e);
        }
    }
}
