/*
 * Copyright (C) 2026 GummerCraft <http://gummercraft.fun>
 */

package fun.gummercraft.gcreport;

import net.fabricmc.api.ClientModInitializer;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/**
 * GummerCraft Report (gcreport, 13.11.2026): кнопка «Надіслати звіт у техпідтримку» на екрані відключення від сервера в збірках GummerCraft
 * на Fabric. Один jar на всі наші версії: 1.16.5, 1.17.1, 1.18.2, 1.19.4, 1.20.6, 1.21.4, 26.2.
 * <p>
 * Як працює: натискання пише в теку гри файл {@code gummercraft-report.json} (сервер, текст з екрана, час). Лаунчер GummerCraft (1.33+)
 * раз на 2 с його забирає і відкриває свою форму підтримки з темою «Main (GummerCraft)», лог гри додається там. Мод сам у мережу нічого
 * не шле. Формат файла: {@code com.gummer.launcher.support.DisconnectReport} у лаунчері; той самий файл пише GummerVibe у StalDayZ.
 * Що лаунчер слухає, видно з прапорця JVM {@code -Dgummercraft.report=1} (ставить лаунчер 1.33+). Без нього, або якщо лаунчер не
 * забрав запит за 8 с, кнопка відкриває Telegram-бот підтримки.
 * <p>
 * Чому рефлексія: класи Minecraft мають різні імена у версіях (intermediary до 1.21.x, імена Mojang з 26.x) і різний спосіб створення кнопки
 * (конструктор до 1.19.2, builder з 1.19.3). Тому мод не компілюється проти Minecraft: класи і методи шукаються під час запуску за
 * сигнатурами, а там, де сигнатура неоднозначна, за іменами intermediary та Mojang (звірені на jar 1.17.1, 1.19.3, 1.20.6, 1.21.4, 26.2).
 * Події екрана беруться з Fabric API ({@code ScreenEvents.AFTER_INIT}, {@code afterTick}, {@code Screens.getButtons/getWidgets}), він є в
 * усіх наших збірках. Щось не знайшлось: мод пише одне повідомлення в лог і далі нічого не робить, гра не падає.
 */
public final class GcReport implements ClientModInitializer {

    static final String FILE_NAME = "gummercraft-report.json";
    static final String VERSION = "1.0.1";
    private static final long WAIT_MS = 8000L;
    private static final int BTN_W = 200, BTN_H = 20, GAP = 24;

    private static ClassLoader cl;
    private static Class<?> screenCls, disconnectedCls, buttonCls, messageCls, textCls;
    private static Method eventRegister, afterTickEvent, buttonsOf, getString;

    /** Адреса останнього сервера: на екрані відключення поточного сервера вже може не бути. */
    private static String lastServer = "";

    private static Object curScreen, curButton;
    /** Усі кнопки, які ми коли-небудь додали (слабкі посилання): щоб не додати другу на той самий екран. */
    private static final java.util.Map<Object, Boolean> OURS = java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());
    private static int scrW, scrH;
    private static Object stateScreen;
    private static String state; // null | waiting | opened | bot | failed
    private static final String HELP_BOT = "https://t.me/gummerCraft_help_bot";
    private static long sentAt;
    private static boolean disabled;

    @Override
    public void onInitializeClient() {
        try {
            setup();
            log("готовий");
        } catch (Throwable t) {
            disabled = true;
            log("вимкнено, не знайшов потрібне в цій версії: " + t);
        }
    }

    private static void setup() throws Exception {
        cl = GcReport.class.getClassLoader();
        screenCls = cls("net.minecraft.class_437", "net.minecraft.client.gui.screens.Screen");
        disconnectedCls = cls("net.minecraft.class_419", "net.minecraft.client.gui.screens.DisconnectedScreen");
        buttonCls = cls("net.minecraft.class_4185", "net.minecraft.client.gui.components.Button");
        messageCls = Class.forName("com.mojang.brigadier.Message", false, cl);
        getString = messageCls.getMethod("getString");

        Class<?> events = Class.forName("net.fabricmc.fabric.api.client.screen.v1.ScreenEvents", true, cl);
        Class<?> screens = Class.forName("net.fabricmc.fabric.api.client.screen.v1.Screens", true, cl);
        eventRegister = Class.forName("net.fabricmc.fabric.api.event.Event", false, cl).getMethod("register", Object.class);
        afterTickEvent = staticMethod(events, 1, "afterTick");
        buttonsOf = staticMethod(screens, 1, "getButtons", "getWidgets");
        if (afterTickEvent == null || buttonsOf == null) throw new NoSuchMethodException("ScreenEvents.afterTick / Screens.getButtons");
        textCls = textClass();

        Object afterInit = events.getField("AFTER_INIT").get(null);
        Class<?> afterInitCls = Class.forName(events.getName() + "$AfterInit", false, cl);
        eventRegister.invoke(afterInit, proxy(afterInitCls, a -> onAfterInit(a[0], a[1], (Integer) a[2], (Integer) a[3])));
    }

    // ---------------------------------------------------------------- події

    private static void onAfterInit(Object client, Object screen, int w, int h) throws Exception {
        if (disabled) return;
        rememberServer(client);
        if (!disconnectedCls.isInstance(screen)) return;
        if (stateScreen != screen) {
            stateScreen = null;
            state = null;
        }
        scrW = w;
        scrH = h;
        @SuppressWarnings("unchecked")
        List<Object> list = (List<Object>) buttonsOf.invoke(null, screen);
        // 20.11.2026 (власник зі скріном Main 26.2: дві кнопки «Надіслати звіт у техпідтримку»): у нових версіях гри зміна розміру
        // вікна кличе AFTER_INIT ще раз, а кнопки екрана не очищаються (repositionElements замість init). Наша кнопка вже є: лише
        // переставляємо її, нову не додаємо і слухача тіку вдруге не реєструємо.
        for (Object existing : new ArrayList<>(list)) {
            if (OURS.containsKey(existing)) {
                curScreen = screen;
                curButton = existing;
                applyState();
                place();
                return;
            }
        }
        Object button = newButton(text(label()), () -> send(screen));
        if (button == null) return;
        OURS.put(button, Boolean.TRUE);
        list.add(button);
        curScreen = screen;
        curButton = button;
        applyState();
        place();
        Object tick = afterTickEvent.invoke(null, screen);
        Class<?> afterTickCls = Class.forName("net.fabricmc.fabric.api.client.screen.v1.ScreenEvents$AfterTick", false, cl);
        final Object mine = button;
        eventRegister.invoke(tick, proxy(afterTickCls, a -> onTick(a[0], mine)));
    }

    private static void onTick(Object screen, Object button) throws Exception {
        if (screen != curScreen || button != curButton) return;
        place(); // з 1.20.2 екран переставляє свої кнопки при зміні розміру вікна без повторного init
        if (!"waiting".equals(state)) return;
        File f = reportFile();
        if (!f.isFile()) {
            setState("opened");
        } else if (System.currentTimeMillis() - sentAt > WAIT_MS) {
            // лаунчер не забрав: гра запущена не ним або старим. Прибираємо, щоб наступний запуск лаунчера не відкрив форму несподівано.
            if (!f.delete()) f.deleteOnExit();
            openBot();
            setState("bot");
            log("лаунчер не забрав запит за " + WAIT_MS + " мс, відкрито Telegram-бот");
        }
    }

    /** Гру запустив лаунчер GummerCraft 1.33+, який забирає запити (прапорець JVM від лаунчера). */
    private static boolean launcherListens() {
        return "1".equals(System.getProperty("gummercraft.report"));
    }

    /** Telegram-бот підтримки в браузері: Util.getOperatingSystem().open(url) (intermediary) / Util.getPlatform().openUri(url) (26.x). */
    private static void openBot() {
        try {
            Class<?> util = cls("net.minecraft.class_156", "net.minecraft.util.Util");
            Object os = null;
            for (Method m : util.getMethods()) {
                if (Modifier.isStatic(m.getModifiers()) && m.getParameterTypes().length == 0
                        && (m.getName().equals("method_668") || m.getName().equals("getPlatform"))) os = m.invoke(null);
            }
            Method open = os == null ? null : method(os.getClass(), 1, "method_670", "openUri");
            if (open == null) throw new NoSuchMethodException("Util.OS.open(String)");
            open.invoke(os, HELP_BOT);
        } catch (Throwable t) {
            log("не вдалося відкрити " + HELP_BOT + ": " + t);
        }
    }

    private static void send(Object screen) {
        if (!launcherListens()) {
            stateScreen = screen;
            openBot();
            setState("bot");
            return;
        }
        try {
            // 1.0.1 (28.11.2026, тікети #148, #151): спершу лог гри. lastServer лишався від попереднього сервера: гравець ішов з
            // play.gummercraft.fun на чужий ліцензійний сервер, той відкидав його з «Недійсна сесія», а звіт писав наш сервер.
            String server = lastConnected();
            if (server.isEmpty()) server = currentServer(null);
            if (server.isEmpty()) server = lastServer;
            String title = firstText(screen, screenCls, false);
            String reason = firstText(screen, disconnectedCls, true);
            String text = title.isEmpty() ? reason : reason.isEmpty() ? title : title + ": " + reason;
            String json = "{\"v\":1,\"kind\":\"disconnect\",\"game\":\"gummercraft\",\"time\":" + System.currentTimeMillis()
                    + ",\"server\":" + q(server) + ",\"reason\":" + q(text) + ",\"source\":" + q("gcreport " + VERSION) + "}";
            File out = reportFile();
            File tmp = new File(out.getParentFile(), FILE_NAME + ".tmp");
            Files.write(tmp.toPath(), json.getBytes(StandardCharsets.UTF_8));
            try { // атомарно: лаунчер не має прочитати напівзаписаний файл
                Files.move(tmp.toPath(), out.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp.toPath(), out.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            stateScreen = screen;
            sentAt = System.currentTimeMillis();
            setState("waiting");
            log("запит на звіт записано для лаунчера (" + server + "): " + text);
        } catch (Throwable t) {
            log("не вдалося записати " + FILE_NAME + ": " + t);
            stateScreen = screen;
            setState("failed");
        }
    }

    private static String label() {
        return state == null ? "gui.gcreport.button" : "gui.gcreport." + state;
    }

    private static void setState(String s) {
        state = s;
        applyState();
    }

    private static void applyState() {
        if (curButton == null) return;
        try {
            Object t = text(label());
            Method m = method(curButton.getClass(), 1, "method_25355", "setMessage");
            if (m != null && t != null) m.invoke(curButton, t);
            Field active = field(curButton.getClass(), "field_22763", "active");
            if (active != null) active.setBoolean(curButton, state == null || "failed".equals(state));
        } catch (Throwable t) {
            log("не вдалося оновити кнопку: " + t);
        }
    }

    // ---------------------------------------------------------------- розкладка

    /** Під найнижчою кнопкою екрана, по її центру; кнопок немає: по центру екрана. */
    private static void place() throws Exception {
        @SuppressWarnings("unchecked")
        List<Object> list = new ArrayList<>((List<Object>) buttonsOf.invoke(null, curScreen));
        int bestY = Integer.MIN_VALUE, cx = scrW / 2;
        for (Object w : list) {
            if (w == curButton) continue;
            int y = intOf(w, "method_46427", "getY", "field_22761", "y");
            if (y > bestY) {
                bestY = y;
                cx = intOf(w, "method_46426", "getX", "field_22760", "x") + intOf(w, "method_25368", "getWidth", null, null) / 2;
            }
        }
        int x = cx - BTN_W / 2, y = bestY == Integer.MIN_VALUE ? scrH / 2 + 40 : bestY + GAP;
        setInt(curButton, x, "method_46421", "setX", "field_22760", "x");
        setInt(curButton, y, "method_46419", "setY", "field_22761", "y");
    }

    private static int intOf(Object o, String getter, String mojangGetter, String fieldName, String mojangField) throws Exception {
        Method m = method(o.getClass(), 0, getter, mojangGetter);
        if (m != null) return (Integer) m.invoke(o);
        Field f = fieldName == null ? null : field(o.getClass(), fieldName, mojangField);
        return f == null ? 0 : f.getInt(o);
    }

    private static void setInt(Object o, int v, String setter, String mojangSetter, String fieldName, String mojangField) throws Exception {
        Method m = method(o.getClass(), 1, setter, mojangSetter);
        if (m != null && m.getParameterTypes()[0] == int.class) {
            m.invoke(o, v);
            return;
        }
        Field f = field(o.getClass(), fieldName, mojangField);
        if (f != null) f.setInt(o, v);
    }

    // ---------------------------------------------------------------- кнопка і текст

    /** Клас тексту (Text / Component) з сигнатури кнопки. */
    private static Class<?> textClass() throws Exception {
        for (Method m : buttonCls.getDeclaredMethods()) {
            Class<?>[] p = m.getParameterTypes();
            if (isBuilder(m) && messageCls.isAssignableFrom(p[0])) return p[0];
        }
        for (Constructor<?> c : buttonCls.getDeclaredConstructors()) {
            Class<?>[] p = c.getParameterTypes();
            if (p.length >= 6 && messageCls.isAssignableFrom(p[4])) return p[4];
        }
        throw new NoSuchMethodException("Button(text)");
    }

    /** {@code static Builder builder(Text, PressAction)} з 1.19.3: публічний, не синтетичний (не лямбда), повертає вкладений клас кнопки. */
    private static boolean isBuilder(Method m) {
        Class<?>[] p = m.getParameterTypes();
        return Modifier.isStatic(m.getModifiers()) && Modifier.isPublic(m.getModifiers()) && !m.isSynthetic() && p.length == 2
                && p[1].isInterface() && m.getReturnType().getDeclaringClass() == buttonCls;
    }

    /** Перекладний текст: Text.translatable (1.19+) або new TranslatableText (1.16-1.18). */
    private static Object text(String key) throws Exception {
        for (Method m : textCls.getDeclaredMethods()) {
            if (Modifier.isStatic(m.getModifiers()) && (m.getName().equals("method_43471") || m.getName().equals("translatable"))
                    && m.getParameterTypes().length == 1 && m.getParameterTypes()[0] == String.class) {
                return m.invoke(null, key);
            }
        }
        try {
            Class<?> tt = Class.forName("net.minecraft.class_2588", false, cl);
            if (textCls.isAssignableFrom(tt)) return tt.getConstructor(String.class).newInstance(key);
        } catch (ClassNotFoundException | NoSuchMethodException ignored) {
        }
        throw new NoSuchMethodException("translatable text");
    }

    /** Кнопка 200x20: Button.builder(...).dimensions(...).build() з 1.19.3, конструктор до 1.19.2. */
    private static Object newButton(Object label, Runnable onPress) throws Exception {
        for (Method m : buttonCls.getDeclaredMethods()) {
            Class<?>[] p = m.getParameterTypes();
            if (!isBuilder(m) || !p[0].isInstance(label)) continue;
            Object builder = m.invoke(null, label, proxy(p[1], a -> onPress.run()));
            Method dims = null, build = null;
            for (Method b : builder.getClass().getMethods()) {
                Class<?>[] bp = b.getParameterTypes();
                if (bp.length == 4 && bp[0] == int.class && bp[1] == int.class && bp[2] == int.class && bp[3] == int.class
                        && b.getReturnType().isInstance(builder)) dims = b;
                if (bp.length == 0 && buttonCls.isAssignableFrom(b.getReturnType())) build = b;
            }
            if (dims == null || build == null) throw new NoSuchMethodException("Button.Builder");
            dims.invoke(builder, 0, 0, BTN_W, BTN_H);
            return build.invoke(builder);
        }
        for (Constructor<?> c : buttonCls.getConstructors()) {
            Class<?>[] p = c.getParameterTypes();
            if (p.length == 6 && p[0] == int.class && p[4].isInstance(label) && p[5].isInterface()) {
                return c.newInstance(0, 0, BTN_W, BTN_H, label, proxy(p[5], a -> onPress.run()));
            }
        }
        throw new NoSuchMethodException("Button constructor");
    }

    // ---------------------------------------------------------------- дані з гри

    private static void rememberServer(Object client) {
        String s = currentServer(client);
        if (!s.isEmpty()) lastServer = s;
    }

    private static Object client;

    private static String currentServer(Object c) {
        try {
            if (c != null) client = c;
            if (client == null) return "";
            Method m = method(client.getClass(), 0, "method_1558", "getCurrentServer");
            Object entry = m == null ? null : m.invoke(client);
            if (entry == null) return "";
            Field f = field(entry.getClass(), "field_3761", "ip");
            Object v = f == null ? null : f.get(entry);
            return v == null ? "" : v.toString().trim();
        } catch (Throwable t) {
            return "";
        }
    }

    /**
     * Перший текст у полях класу {@code owner} за порядком оголошення. {@code nested}: якщо поле не текст, а об'єкт (не екран), дивимось
     * на рівень глибше: так з 1.20.5 причина лежить у record {@code DisconnectionInfo}/{@code DisconnectionDetails}, а текст кнопки
     * («До списку серверів») оголошено вже після нього.
     */
    private static String firstText(Object o, Class<?> owner, boolean nested) {
        try {
            for (Field f : owner.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers()) || f.getType().isPrimitive()) continue;
                f.setAccessible(true);
                Object v = f.get(o);
                if (v == null) continue;
                if (messageCls.isInstance(v)) return ((String) getString.invoke(v)).trim();
                if (!nested || screenCls.isInstance(v) || v instanceof Iterable || v.getClass().getName().startsWith("java.")) continue;
                for (Field g : v.getClass().getDeclaredFields()) {
                    if (Modifier.isStatic(g.getModifiers()) || !messageCls.isAssignableFrom(g.getType())) continue;
                    g.setAccessible(true);
                    Object t = g.get(v);
                    if (t != null) return ((String) getString.invoke(t)).trim();
                }
            }
        } catch (Throwable t) {
            log("не вдалося прочитати текст екрана: " + t);
        }
        return "";
    }

    private static final java.util.regex.Pattern CONNECTING = java.util.regex.Pattern.compile("Connecting to ([^,\\s]+), (\\d+)");

    /** Адреса з останнього рядка {@code Connecting to <host>, <port>} у {@code logs/latest.log} (порт 25565 не пишемо), або "". */
    private static String lastConnected() {
        try {
            File f = new File(new File(reportFile().getParentFile(), "logs"), "latest.log");
            if (!f.isFile()) return "";
            try (java.io.RandomAccessFile raf = new java.io.RandomAccessFile(f, "r")) {
                long len = raf.length(), from = Math.max(0, len - 2L * 1024 * 1024);
                byte[] buf = new byte[(int) (len - from)];
                raf.seek(from);
                raf.readFully(buf);
                java.util.regex.Matcher m = CONNECTING.matcher(new String(buf, StandardCharsets.ISO_8859_1));
                String last = "";
                while (m.find()) last = "25565".equals(m.group(2)) ? m.group(1) : m.group(1) + ":" + m.group(2);
                return last;
            }
        } catch (Throwable t) {
            return "";
        }
    }

    private static File reportFile() throws Exception {
        Class<?> loader = Class.forName("net.fabricmc.loader.api.FabricLoader", true, cl);
        Object inst = loader.getMethod("getInstance").invoke(null);
        Path dir = (Path) loader.getMethod("getGameDir").invoke(inst);
        return new File(dir.toFile(), FILE_NAME);
    }

    // ---------------------------------------------------------------- рефлексія

    private interface Body {
        void run(Object[] args) throws Exception;
    }

    private static Object proxy(Class<?> iface, Body body) {
        InvocationHandler h = (p, m, a) -> {
            if (m.getDeclaringClass() == Object.class) {
                switch (m.getName()) {
                    case "equals":
                        return p == a[0];
                    case "hashCode":
                        return System.identityHashCode(p);
                    default:
                        return "gcreport$" + iface.getSimpleName();
                }
            }
            if (m.isDefault()) return null;
            try {
                body.run(a == null ? new Object[0] : a);
            } catch (Throwable t) {
                log("помилка в обробнику " + iface.getSimpleName() + ": " + t);
            }
            return null;
        };
        return Proxy.newProxyInstance(iface.getClassLoader(), new Class<?>[]{iface}, h);
    }

    private static Class<?> cls(String... names) throws ClassNotFoundException {
        for (String n : names) {
            try {
                return Class.forName(n, false, cl);
            } catch (ClassNotFoundException ignored) {
            }
        }
        throw new ClassNotFoundException(String.join(" / ", names));
    }

    private static Method staticMethod(Class<?> c, int params, String... names) {
        for (String n : names) {
            for (Method m : c.getMethods()) {
                if (m.getName().equals(n) && m.getParameterTypes().length == params && Modifier.isStatic(m.getModifiers())) return m;
            }
        }
        return null;
    }

    /** Метод за одним з імен (intermediary, Mojang) з {@code params} параметрами, по всій ієрархії класу. */
    private static Method method(Class<?> c, int params, String... names) {
        for (Class<?> k = c; k != null && k != Object.class; k = k.getSuperclass()) {
            for (Method m : k.getDeclaredMethods()) {
                if (m.getParameterTypes().length != params || Modifier.isStatic(m.getModifiers())) continue;
                for (String n : names) {
                    if (n != null && n.equals(m.getName())) {
                        m.setAccessible(true);
                        return m;
                    }
                }
            }
        }
        return null;
    }

    private static Field field(Class<?> c, String... names) {
        for (Class<?> k = c; k != null && k != Object.class; k = k.getSuperclass()) {
            for (Field f : k.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers())) continue;
                for (String n : names) {
                    if (n != null && n.equals(f.getName())) {
                        f.setAccessible(true);
                        return f;
                    }
                }
            }
        }
        return null;
    }

    private static String q(String s) {
        StringBuilder b = new StringBuilder("\"");
        for (char ch : s.toCharArray()) {
            switch (ch) {
                case '"': b.append("\\\""); break;
                case '\\': b.append("\\\\"); break;
                case '\n': b.append("\\n"); break;
                case '\r': b.append("\\r"); break;
                case '\t': b.append("\\t"); break;
                default:
                    if (ch < 0x20) b.append(String.format("\\u%04x", (int) ch));
                    else b.append(ch);
            }
        }
        return b.append('"').toString();
    }

    static void log(String s) {
        System.out.println("[gcreport] " + s);
    }
}
