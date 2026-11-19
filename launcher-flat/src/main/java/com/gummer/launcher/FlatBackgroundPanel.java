/*
 * Copyright (C) 2021-2025 GummerCraft <http://gummercraft.fun>
 */

package com.gummer.launcher;

import com.skcraft.launcher.Launcher;

import com.skcraft.launcher.util.SharedLocale;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Random;

/**
 * Тло головного вікна: фон, скляна бічна панель (дизайн 1.35, замість borders.png), логотип і сплеш. Для вибраної збірки можуть бути власні фон і логотип:
 * {@code ui/frame/bg_<назва збірки>.jpg} і {@code ui/frame/logo_<назва збірки>.png} (назва з маніфесту, малими літерами, наприклад bg_staldayz.jpg).
 * Усі ванільні версії (група vanilla) беруть bg_vanilla.jpg і logo_vanilla.png (FlatLauncherFrame, з 1.32).
 * Немає файлу - малюється стандартний (bg.jpg / logo.png). Налаштування збірки - {@code ui/frame/frame_<назва збірки>.properties}:
 * зсуви background.x/y і logo.x/y у пікселях, background.from (фон іншого оформлення), wiki.url - куди веде кнопка "Вікі та FAQ" (дивись frame_staldayz.properties).
 * Зміна збірки - плавний перехід (старі фон і логотип розчиняються в нові за {@link #FADE_MS}).
 */
public class FlatBackgroundPanel extends JPanel {

    /** Зона логотипа (як у стандартного logo.png 724x180): логотип збірки вписується в неї без розтягування, по центру. */
    private static final int LOGO_X = 346, LOGO_Y = 5, LOGO_W = 724, LOGO_H = 180;
    /** Стандартний фон малюється з X = 100 (так підібрано під bg.jpg); фон збірки - по центру тієї ж видимої зони (центр = центр логотипа), по висоті - по центру панелі. */
    private static final int DEFAULT_BG_X = 100;
    private static final int VIEW_CENTER_X = LOGO_X + LOGO_W / 2;
    /** 17.11.2026: оформлення сторінки Modrinth (bg_modrinth.jpg, без лого збірки і написів); те саме ім'я в Класиці і Бібліотеці. */
    public static final String BROWSER_LOOK = "modrinth";
    /** Порожнє лого: сцена без логотипа (сторінка Modrinth). */
    static final Image NO_LOGO = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);

    /**
     * 17.11.2026 (власник: «в повноекранному логотип з'їжджає вліво замість центру»): центр правої частини вікна.
     * У найменшому вікні це {@link #VIEW_CENTER_X} (708), ширше вікно зсуває центр на половину приросту (бічна панель
     * ліворуч не росте).
     */
    private int viewCenterX() {
        // центр правої частини: (ширина + бічна панель + 16) / 2, у найменшому вікні 708
        return Math.max(VIEW_CENTER_X, (getWidth() + sidebarW + 2 * VIEW_CENTER_X - 1180 - SIDEBAR_BASE_W) / 2);
    }
    /** Тривалість переходу між збірками. */
    private static final int FADE_MS = 450;
    /** Дизайн 1.35: ширина бічної панелі (скляна смуга над фоном замість суцільної borders.png). */
    private static final int SIDEBAR_BASE_W = 220;
    /**
     * 17.11.2026 (власник на знімку розгорнутого скла: збільшена бічна панель вилазила за матову підкладку 220 px, і
     * текст губився на яскравому фоні): ширина підкладки дорівнює ширині бічної панелі ({@link #setSidebarWidth}).
     */
    private int sidebarW = SIDEBAR_BASE_W;

    public void setSidebarWidth(int width) {
        if (width == sidebarW) return;
        sidebarW = width;
        repaint();
    }
    /** Висота затемнення внизу (під кнопками). */
    private static final int BOTTOM_SHADE_H = 230;
    /** Нахил сплешу, як у Minecraft. */
    private static final double SPLASH_ANGLE = 20.0;
    /** Період «дихання» сплешу. */
    private static final long SPLASH_PERIOD_MS = 1600L;

    /** Що малюється для однієї збірки. Положення і розміри читаються з layout щокадру: редактор розкладки міняє його наживо. */
    private static final class Scene {
        final String key;
        final Image background;
        final boolean packBackground;
        final Image logo;
        final Properties layout;
        /** Розмита і затемнена смуга фону під бічною панеллю і для якого положення фону її пораховано. */
        BufferedImage glass;
        String glassFor;
        /** Фон, уже масштабований у фізичні пікселі (для скла); сам кеш спільний, look.DevicePixels. */
        BufferedImage bgDev;

        Scene(String key, Image background, boolean packBackground, Image logo, Properties layout) {
            this.key = key;
            this.background = background;
            this.packBackground = packBackground;
            this.logo = logo;
            this.layout = layout;
        }

        int bgDx() { return intOf(layout, "background.x"); }
        int bgDy() { return intOf(layout, "background.y"); }
        double bgScale() { return doubleOf(layout, "background.scale", 1.0); }
        int logoDx() { return intOf(layout, "logo.x"); }
        int logoDy() { return intOf(layout, "logo.y"); }
        double logoScale() { return doubleOf(layout, "logo.scale", 1.0); }

        String bgGeometry() {
            return bgDx() + "," + bgDy() + "," + bgScale();
        }

        boolean looksSame(Scene o) {
            return o != null && background == o.background && logo == o.logo && bgGeometry().equals(o.bgGeometry())
                    && logoDx() == o.logoDx() && logoDy() == o.logoDy() && logoScale() == o.logoScale();
        }
    }

    private final Image defaultBackground;
    private final Image defaultLogo;

    private Scene current;
    private Scene previous;
    private float fade = 1.0F;
    private long fadeStart;
    private final Timer fadeTimer;

    private Properties currentLayout = new Properties();

    /** Сплеш біля логотипа (як жовтий напис у меню Minecraft): написи групи і поточний. */
    private String[] splashes = new String[0];
    private int splashIndex = -1;
    private final Random random = new Random();
    private final Timer splashTimer;
    private final long splashStart = System.currentTimeMillis();
    /** Де сплеш намальовано востаннє (для перемальовки і кліку). */
    private Rectangle splashBounds;
    private final Rectangle logoBounds = new Rectangle(LOGO_X, LOGO_Y, LOGO_W, LOGO_H);
    private Image logoImage;
    /** Непрозора частина кожного логотипа (частка 0..1 від картинки): у PNG бувають порожні поля. */
    private final Map<Image, double[]> logoOpaque = new HashMap<>();

    /** Вже перевірені файли: шлях -> зображення (null - файлу нема). */
    private final Map<String, Properties> layouts = new HashMap<>();

    public FlatBackgroundPanel() {
        // 18.11.2026: зі спільного кешу трьох виглядів (раніше свій примірник у кожному вікні, тож кеш масштабованих
        // фонів після перемикання вигляду не знаходив готового)
        defaultBackground = cached("ui/frame/bg.jpg");
        defaultLogo = cached("ui/frame/logo.png");
        current = new Scene("default", defaultBackground, false, defaultLogo, new Properties());

        fadeTimer = new Timer(16, e -> {
            fade = Math.min(1.0F, (System.currentTimeMillis() - fadeStart) / (float) FADE_MS);
            if (fade >= 1.0F) {
                previous = null;
                ((Timer) e.getSource()).stop();
            }
            repaint();
        });

        // пульсація сплешу: перемальовується лише його ділянка, 30 кадрів на секунду (до 16.11.2026 було 60, а розмах
        // лише 5%, різниці на око немає); у згорнутому вікні стоїть
        splashTimer = new Timer(33, e -> {
            // paintImmediately, а не repaint: інакше менеджер перемальовки зливає ділянку напису з ділянкою сяйва кнопки
            // в один великий прямокутник, і разом з ними щоразу перемальовувався список збірок
            if (splashBounds != null && isShowing() && !com.gummer.launcher.look.DevicePixels.iconified(this)) paintImmediately(splashBounds);
        });
        // клік по сплешу: наступний напис
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (splashBounds != null && splashBounds.contains(e.getPoint()) && splashes.length > 1) {
                    splashIndex = (splashIndex + 1) % splashes.length;
                    repaint(splashBounds);
                }
            }
        });
    }

    @Override
    public void addNotify() {
        super.addNotify();
        if (!com.gummer.launcher.look.LookTheme.LITE) splashTimer.start(); // спрощений режим: сплеш без пульсації
        if (!prewarmHooked) {
            prewarmHooked = true;
            addComponentListener(new java.awt.event.ComponentAdapter() {
                @Override
                public void componentResized(java.awt.event.ComponentEvent e) {
                    schedulePrewarm();
                }
            });
        }
        schedulePrewarm();
    }

    private boolean prewarmHooked;
    private Timer prewarmTimer;
    /** Збірки з власним фоном (look.FrameImages), Modrinth і без оформлення (GummerCraft, свої збірки). */
    private static final String[] PREWARM_KEYS = {"staldayz", "vanilla", "fabric", "quilt", "forge", "neoforge", "modrinth", "default"};

    /**
     * 18.11.2026 (власник: «лаунчер включився на модрінті, я клацнув на сталдейз - затуп пів секунди перш ніж
     * бекграунд підвантажило»). Сторож EdtWatch показав: перший показ кожного фону масштабував його під вікно
     * (DevicePixels.resize) і розмивав скло (scaleBlur) прямо в потоці інтерфейсу, у малому вікні ~80 мс, у
     * розгорнутому на великому екрані в рази більше. Тепер через 1 с після показу чи зміни розміру вікна це робиться
     * наперед у фоновому потоці для всіх збірок з оформленням; клік бере готове з кешу.
     */
    private void schedulePrewarm() {
        if (prewarmTimer == null) {
            prewarmTimer = new Timer(1000, e -> prewarmScenes());
            prewarmTimer.setRepeats(false);
        }
        prewarmTimer.restart();
    }

    private void prewarmScenes() {
        if (!isShowing() || getWidth() <= 0 || getHeight() <= 0) return;
        GraphicsConfiguration gc = getGraphicsConfiguration();
        AffineTransform dt = gc != null ? gc.getDefaultTransform() : new AffineTransform();
        final double ds = dt.getScaleX() == dt.getScaleY() && dt.getShearX() == 0 && dt.getShearY() == 0 ? dt.getScaleX() : 1.0;
        final int w = getWidth(), h = getHeight(), side = sidebarW;
        // розкладки (HashMap) беремо тут, картинки читаються і рахуються у фоновому потоці
        final java.util.List<Object[]> layouts = new java.util.ArrayList<Object[]>();
        for (String key : PREWARM_KEYS) layouts.add(new Object[]{key, layout(key)});
        Thread t = new Thread(() -> {
            for (Object[] kl : layouts) {
                String key = (String) kl[0];
                Properties layout = (Properties) kl[1];
                try {
                    Image bg = "default".equals(key) ? null : cached("ui/frame/bg_" + key + ".jpg");
                    String from = layout.getProperty("background.from");
                    if (bg == null && from != null && from.matches("[a-z0-9_.-]+")) bg = cached("ui/frame/bg_" + from + ".jpg");
                    Scene sc = new Scene(key, bg != null ? bg : defaultBackground, bg != null, null, layout);
                    if (sc.background == null || sc.background.getWidth(null) <= 0) continue;
                    Rectangle r = backgroundRect(sc, sc.background);
                    final java.util.List<Object> gkey = java.util.Arrays.<Object>asList(sc.background, sc.bgGeometry() + "," + w + "x" + h + "," + side);
                    BufferedImage dev = com.gummer.launcher.look.DevicePixels.cached(sc.background,
                            com.gummer.launcher.look.DevicePixels.px(r.width, ds), com.gummer.launcher.look.DevicePixels.px(r.height, ds), false);
                    final BufferedImage glass = makeGlass(sc.background, dev, r, side, h);
                    SwingUtilities.invokeLater(() -> {
                        if (!glassCache.containsKey(gkey)) glassCache.put(gkey, glass);
                    });
                } catch (RuntimeException | OutOfMemoryError ignored) {
                    // не вийшло: порахується при першому показі, як раніше
                }
            }
        }, "Background prewarm");
        t.setDaemon(true);
        t.setPriority(Thread.MIN_PRIORITY);
        t.start();
    }

    @Override
    public void removeNotify() {
        splashTimer.stop();
        super.removeNotify();
    }

    /** Написи групи з мови (look.splash.&lt;група&gt;, через |), випадковий перший. */
    private void pickSplashes(String look) {
        String group = PackLook.groupOf(look);
        String text = SharedLocale.tr("look.splash." + group);
        if (text == null || text.startsWith("${")) text = SharedLocale.tr("look.splash.main");
        if (text == null || text.startsWith("${")) {
            splashes = new String[0];
            splashIndex = -1;
            return;
        }
        java.util.List<String> list = new java.util.ArrayList<>();
        for (String part : text.split("\\|")) {
            if (!part.trim().isEmpty()) list.add(part.trim());
        }
        splashes = list.toArray(new String[0]);
        splashIndex = splashes.length == 0 ? -1 : random.nextInt(splashes.length);
    }

    private static Image read(String path) {
        try {
            return ImageIO.read(Objects.requireNonNull(Launcher.class.getResourceAsStream(path)));
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    /** Показати фон і логотип вибраної збірки (або стандартні, якщо своїх нема чи збірку не вибрано) з плавним переходом. */
    public void showPack(String packName) {
        showPack(packName, true);
    }

    /** @param animate false - одразу, без переходу (старт лаунчера) */
    public void showPack(String packName, boolean animate) {
        showPack(packName, animate, null);
    }

    /** 17.11.2026: customBackground - свій фон копії збірки (InstanceArt), замість фону типу збірки; null - звичайний. */
    public void showPack(String packName, boolean animate, Image customBackground) {
        Image background = null;
        Image logo = null;
        String key = packName != null ? packName.toLowerCase(Locale.ROOT) : "default";
        if (!key.matches("[a-z0-9_.-]+")) key = "default";
        Properties layout = layout(key);
        if (packName != null) {
            if (key.matches("[a-z0-9_.-]+")) {
                background = cached("ui/frame/bg_" + key + ".jpg");
                logo = cached("ui/frame/logo_" + key + ".png");
                // 1.33: background.from=<інший пак> - фон іншого оформлення (NeoForge поки бере фон Vanilla)
                String from = layout.getProperty("background.from");
                if (background == null && from != null && from.matches("[a-z0-9_.-]+")) {
                    background = cached("ui/frame/bg_" + from + ".jpg");
                }
            }
        }
        if (customBackground != null) background = customBackground;
        currentLayout = layout;
        boolean browser = BROWSER_LOOK.equals(key);
        pickSplashes(packName);
        if (browser) {
            splashes = new String[0];
            splashIndex = -1;
        }
        Scene next = new Scene(key, background != null ? background : defaultBackground, background != null,
                browser ? NO_LOGO : logo != null ? logo : defaultLogo, layout);
        if (next.looksSame(current)) {
            repaint();
            return;
        }

        if (!animate || com.gummer.launcher.look.LookTheme.LITE) {
            fadeTimer.stop();
            previous = null;
            current = next;
            fade = 1.0F;
            repaint();
            return;
        }
        previous = current;
        current = next;
        fade = 0.0F;
        fadeStart = System.currentTimeMillis();
        fadeTimer.restart();
        repaint();
    }

    /** Налаштування вибраної збірки з frame_&lt;назва&gt;.properties (наприклад "wiki.url"), або null. Для адрес приймаються лише http(s). */
    public String packSetting(String name) {
        String value = currentLayout.getProperty(name);
        if (value == null || value.trim().isEmpty()) return null;
        value = value.trim();
        if (name.endsWith(".url") && !(value.startsWith("https://") || value.startsWith("http://"))) return null;
        return value;
    }

    /** 16.11.2026: спільний кеш трьох виглядів, розпакований наперед у фоні (look.FrameImages). */
    private Image cached(String path) {
        com.gummer.launcher.look.FrameImages.warmUp();
        return com.gummer.launcher.look.FrameImages.get(path);
    }

    private Properties layout(String key) {
        return layouts.computeIfAbsent(key, k -> {
            Properties props = new Properties();
            try (InputStream in = Launcher.class.getResourceAsStream("ui/frame/frame_" + k + ".properties")) {
                if (in != null) props.load(new InputStreamReader(in, StandardCharsets.UTF_8));
            } catch (IOException e) {
                props.clear();
            }
            // правки з редактора розкладки (код ↑↑↓↓←→←→BA) поверх вшитих
            java.io.File own = editFile(k);
            if (own != null && own.isFile()) {
                try (InputStream in = new java.io.FileInputStream(own)) {
                    Properties edits = new Properties();
                    edits.load(new InputStreamReader(in, StandardCharsets.UTF_8));
                    props.putAll(edits);
                } catch (IOException e) {
                    // зіпсований файл правок: лишаємо вшите
                }
            }
            return props;
        });
    }

    // ------------------------------------------------------------------ редактор розкладки (дизайн 1.35)

    /** Ключі, які міняє редактор (лише їх пишемо у файл правок). */
    static final String[] EDIT_KEYS = {"background.x", "background.y", "background.scale", "logo.x", "logo.y", "logo.scale",
            "splash.dx", "splash.dy", "splash.size", "splash.angle", "splash.manual"};

    private java.io.File editDir;

    /** Тека правок редактора (&lt;--dir&gt;/launcher/frame-edit). */
    public void setEditDir(java.io.File dir) {
        this.editDir = dir;
        layouts.clear();
    }

    private java.io.File editFile(String key) {
        return editDir == null ? null : new java.io.File(editDir, "frame_" + key + ".properties");
    }

    public String editKey() {
        return current.key;
    }

    /** Видима частина логотипа (без прозорих полів PNG): за неї хапає і її обводить редактор. */
    public Rectangle editLogoBounds() {
        return visibleLogoBounds();
    }

    public Rectangle editSplashBounds() {
        return splashBounds != null ? new Rectangle(splashBounds) : null;
    }

    public Rectangle editBackgroundBounds() {
        return current.background != null ? backgroundRect(current, current.background) : null;
    }

    private void putNumber(String name, double v) {
        current.layout.setProperty(name, Math.abs(v - Math.rint(v)) < 1e-9 ? String.valueOf((long) Math.rint(v))
                : String.format(Locale.ROOT, "%.3f", v));
    }

    /** Зсунути об'єкт ("logo", "splash", "background") на dx, dy пікселів. */
    public void editMove(String what, int dx, int dy) {
        Properties l = current.layout;
        if ("logo".equals(what)) {
            putNumber("logo.x", intOf(l, "logo.x") + dx);
            putNumber("logo.y", intOf(l, "logo.y") + dy);
        } else if ("splash".equals(what)) {
            putNumber("splash.dx", intOf(l, "splash.dx") + dx);
            putNumber("splash.dy", intOf(l, "splash.dy") + dy);
            l.setProperty("splash.manual", "true");
        } else if ("background".equals(what) && current.background != null) {
            Rectangle before = backgroundRect(current, current.background);
            putNumber("background.x", intOf(l, "background.x") + dx);
            putNumber("background.y", intOf(l, "background.y") + dy);
            normalizeBackground(before, dx, dy);
        }
        splashDrops.clear();
        repaint();
    }

    /** Фон упирається в край (має закривати все вікно): не накопичувати зсув, який нічого не міняє. */
    private void normalizeBackground(Rectangle before, int dx, int dy) {
        Rectangle after = backgroundRect(current, current.background);
        Properties l = current.layout;
        if (after.x - before.x != dx) putNumber("background.x", intOf(l, "background.x") - (dx - (after.x - before.x)));
        if (after.y - before.y != dy) putNumber("background.y", intOf(l, "background.y") - (dy - (after.y - before.y)));
    }

    /** Змінити розмір об'єкта у factor разів (коліщатко). */
    public void editScale(String what, double factor) {
        Properties l = current.layout;
        String name = "logo".equals(what) ? "logo.scale" : "splash".equals(what) ? "splash.size" : "background.scale";
        double v = doubleOf(l, name, 1.0) * factor;
        v = Math.max("background".equals(what) ? 1.0 : 0.2, Math.min(4.0, v));
        putNumber(name, v);
        if ("splash".equals(what)) l.setProperty("splash.manual", "true");
        if ("background".equals(what) && current.background != null) {
            Rectangle r = backgroundRect(current, current.background);
            normalizeBackground(r, 0, 0);
        }
        splashDrops.clear();
        repaint();
    }

    /** Повернути сплеш на deg градусів. */
    public void editRotate(double deg) {
        Properties l = current.layout;
        putNumber("splash.angle", Math.max(-60, Math.min(60, doubleOf(l, "splash.angle", SPLASH_ANGLE) + deg)));
        l.setProperty("splash.manual", "true");
        splashDrops.clear();
        repaint();
    }

    /** Зберегти правки вибраної збірки у файл (лише ключі редактора). */
    public void editSave() throws IOException {
        java.io.File f = editFile(current.key);
        if (f == null) return;
        Properties out = new Properties();
        for (String k : EDIT_KEYS) {
            String v = current.layout.getProperty(k);
            if (v != null) out.setProperty(k, v);
        }
        f.getParentFile().mkdirs();
        try (java.io.Writer w = new java.io.OutputStreamWriter(new java.io.FileOutputStream(f), StandardCharsets.UTF_8)) {
            out.store(w, "GummerCraft launcher: layout edits for " + current.key);
        }
    }

    /** Скинути правки вибраної збірки: стерти файл і перечитати вшите. */
    public void editReset() {
        java.io.File f = editFile(current.key);
        if (f != null && f.isFile() && !f.delete()) f.deleteOnExit();
        String key = current.key;
        layouts.remove(key);
        Properties fresh = layout(key);
        current.layout.clear();
        current.layout.putAll(fresh);
        layouts.put(key, current.layout);
        splashDrops.clear();
        repaint();
    }

    /** Значення вибраної збірки текстом (для «Копіювати»). */
    public String editExport() {
        StringBuilder sb = new StringBuilder("# frame_" + current.key + ".properties\n");
        for (String k : EDIT_KEYS) {
            String v = current.layout.getProperty(k);
            if (v != null) sb.append(k).append('=').append(v).append('\n');
        }
        return sb.toString();
    }

    public String editSummary() {
        Properties l = current.layout;
        return String.format(Locale.ROOT, "%s   лого %d,%d x%.2f   фон %d,%d x%.2f   напис %d,%d x%.2f %.0f°",
                current.key, intOf(l, "logo.x"), intOf(l, "logo.y"), doubleOf(l, "logo.scale", 1.0),
                intOf(l, "background.x"), intOf(l, "background.y"), doubleOf(l, "background.scale", 1.0),
                intOf(l, "splash.dx"), intOf(l, "splash.dy"), doubleOf(l, "splash.size", 1.0),
                doubleOf(l, "splash.angle", SPLASH_ANGLE));
    }

    private static int intOf(Properties props, String name) {
        try {
            return Integer.parseInt(props.getProperty(name, "0").trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

            // перехід: м'яка крива (smoothstep), фон новий поверх старого, логотипи перетікають
            float e = previous != null ? fade * fade * (3 - 2 * fade) : 1.0F;
            if (previous != null) paintBackground(g2, previous, 1.0F);
            paintBackground(g2, current, e);

            g2.setComposite(AlphaComposite.SrcOver);
            paintShades(g2);

            // скляна бічна панель: розмитий фон під нею, теж з переходом
            if (previous != null) paintGlass(g2, previous, 1.0F);
            paintGlass(g2, current, e);
            g2.setComposite(AlphaComposite.SrcOver);
            paintSidebarEdge(g2);

            // логотипи: старий згасає швидше, новий проявляється з ледь помітним наближенням (без «провалу» посередині)
            if (previous != null) paintLogo(g2, previous, (float) Math.pow(1.0F - e, 1.6), 1.0);
            paintLogo(g2, current, previous != null ? (float) Math.pow(e, 0.7) : 1.0F, previous != null ? 0.96 + 0.04 * e : 1.0);

            paintSplash(g2, e);
        } finally {
            g2.dispose();
        }
    }

    /**
     * Дочірні компоненти (кнопки-картинки з піксельним шрифтом, іконки збірок, голова скіна) при масштабі Windows 125%, 150%
     * малюються без розмиття: найближчий піксель замість білінійного розтягування (16.11.2026, «шрифт наче з малим
     * розширенням»). Хінт успадковується всіма нащадками; фон, скло і логотип самі ставлять білінійне у paintComponent.
     */
    @Override
    protected void paintChildren(Graphics g) {
        if (g instanceof Graphics2D) {
            ((Graphics2D) g).setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        }
        super.paintChildren(g);
    }

    /** Де і якого розміру малюється фон сцени (той самий розрахунок для фону, скла і редактора). */
    private Rectangle backgroundRect(Scene scene, Image image) {
        int iw = image.getWidth(null), ih = image.getHeight(null);
        int pw = Math.max(1, getWidth()), ph = Math.max(1, getHeight());
        // фон завжди закриває все вікно: масштаб не менший, ніж для цього треба
        double cover = Math.max((double) pw / iw, (double) ph / ih);
        double sc = Math.max(cover, scene.bgScale());
        int w = (int) Math.ceil(iw * sc), h = (int) Math.ceil(ih * sc);
        int x, y;
        if (scene.packBackground) {
            // по центру правої частини, але без порожніх смуг з боків
            x = Math.min(0, Math.max(pw - w, viewCenterX() - w / 2 + scene.bgDx()));
            y = (ph - h) / 2 + scene.bgDy();
        } else {
            x = DEFAULT_BG_X - SIDEBAR_BASE_W / 2 + scene.bgDx();
            y = scene.bgDy();
        }
        x = Math.max(pw - w, Math.min(0, x));
        y = Math.max(ph - h, Math.min(0, y));
        return new Rectangle(x, y, w, h);
    }

    private void paintBackground(Graphics2D g2, Scene scene, float alpha) {
        Image image = scene.background;
        if (image == null || alpha <= 0.0F) return;
        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, Math.min(1.0F, alpha)));
        Rectangle r = backgroundRect(scene, image);
        // 16.11.2026: масштабується один раз під розмір вікна, кадр (напис, сяйво кнопки, перехід) лише копіює пікселі
        double ds = com.gummer.launcher.look.DevicePixels.scale(g2);
        scene.bgDev = com.gummer.launcher.look.DevicePixels.cachedQuick(image, com.gummer.launcher.look.DevicePixels.px(r.width, ds), com.gummer.launcher.look.DevicePixels.px(r.height, ds), false, this);
        com.gummer.launcher.look.DevicePixels.draw(g2, scene.bgDev, r.x, r.y, r.width, r.height);
    }

    /** Легке затемнення згори (під логотип) і сильніше знизу (під кнопки), щоб усе читалось на будь-якому фоні. */
    private void paintShades(Graphics2D g2) {
        int w = getWidth(), h = getHeight();
        g2.setPaint(new GradientPaint(0, 0, new Color(0, 0, 0, 70), 0, 150, new Color(0, 0, 0, 0)));
        g2.fillRect(sidebarW, 0, w - sidebarW, 150);
        g2.setPaint(new GradientPaint(0, h - BOTTOM_SHADE_H, new Color(0, 0, 0, 0), 0, h, new Color(0, 0, 0, 200)));
        g2.fillRect(sidebarW, h - BOTTOM_SHADE_H, w - sidebarW, BOTTOM_SHADE_H);
    }

    /** 18.11.2026: спільний для всіх вікон скла (вікно створюється наново після гри і перемикання вигляду). */
    private static final Map<java.util.List<Object>, BufferedImage> glassCache = new java.util.LinkedHashMap<java.util.List<Object>, BufferedImage>(16, 0.75F, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<java.util.List<Object>, BufferedImage> eldest) {
            return size() > 12; // 18.11.2026: 8 збірок з оформленням наперед (prewarmScenes) плюс свої фони
        }
    };

    private void paintGlass(Graphics2D g2, Scene scene, float alpha) {
        if (alpha <= 0.0F || getHeight() <= 0) return;
        BufferedImage glass = scene.glass;
        String stamp = scene.bgGeometry() + "," + getWidth() + "x" + getHeight() + "," + sidebarW;
        if (glass == null || !stamp.equals(scene.glassFor)) {
            // 16.11.2026: сцена створюється заново при кожному перемиканні, а розмите скло те саме: тримаємо кілька
            java.util.List<Object> key = java.util.Arrays.<Object>asList(scene.background, stamp);
            glass = glassCache.get(key);
            if (glass == null) {
                glass = makeGlass(scene);
                glassCache.put(key, glass);
            }
            scene.glass = glass;
            scene.glassFor = stamp;
        }
        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, Math.min(1.0F, alpha)));
        g2.drawImage(glass, 0, 0, null);
    }

    /** Смуга фону 0..sidebarW, розмита (зменшення і збільшення з інтерполяцією, тричі) і затемнена. */
    private BufferedImage makeGlass(Scene scene) {
        return makeGlass(scene.background, scene.bgDev, scene.background != null ? backgroundRect(scene, scene.background) : null, sidebarW, getHeight());
    }

    /** Те саме без звернень до панелі: можна в фоновому потоці (prewarmScenes). */
    private static BufferedImage makeGlass(Image background, BufferedImage bgDev, Rectangle r, int sidebarW, int h) {
        BufferedImage strip = new BufferedImage(sidebarW, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D sg = strip.createGraphics();
        sg.setColor(new Color(22, 22, 22));
        sg.fillRect(0, 0, sidebarW, h);
        if (background != null && r != null) {
            sg.drawImage(bgDev != null ? bgDev : background, r.x, r.y, r.width, r.height, null);
        }
        sg.dispose();
        BufferedImage blurred = strip;
        for (int pass = 0; pass < 3; pass++) {
            blurred = scaleBlur(blurred, pass == 0 ? 12 : 6);
        }
        Graphics2D bg = blurred.createGraphics();
        bg.setColor(new Color(14, 14, 16, 175));
        bg.fillRect(0, 0, sidebarW, h);
        // ледь помітний відблиск згори скла
        bg.setPaint(new GradientPaint(0, 0, new Color(255, 255, 255, 18), 0, 260, new Color(255, 255, 255, 0)));
        bg.fillRect(0, 0, sidebarW, 260);
        bg.dispose();
        return blurred;
    }

    private static BufferedImage scaleBlur(BufferedImage src, int factor) {
        int w = src.getWidth(), h = src.getHeight();
        int sw = Math.max(1, w / factor), sh = Math.max(1, h / factor);
        BufferedImage small = new BufferedImage(sw, sh, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = small.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(src, 0, 0, sw, sh, null);
        g.dispose();
        BufferedImage big = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        g = big.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.drawImage(small, 0, 0, w, h, null);
        g.dispose();
        return big;
    }

    /** Край скла: світла лінія і м'яка тінь праворуч. */
    private void paintSidebarEdge(Graphics2D g2) {
        int h = getHeight();
        g2.setPaint(new GradientPaint(sidebarW, 0, new Color(0, 0, 0, 110), sidebarW + 14, 0, new Color(0, 0, 0, 0)));
        g2.fillRect(sidebarW, 0, 14, h);
        g2.setColor(new Color(255, 255, 255, 34));
        g2.fillRect(sidebarW - 1, 0, 1, h);
    }

    /** Де логотип видно насправді (без прозорих полів картинки), у координатах панелі. */
    private Rectangle visibleLogoBounds() {
        Rectangle lb = logoBounds;
        if (!(logoImage instanceof BufferedImage)) return lb;
        double[] f = logoOpaque.computeIfAbsent(logoImage, im -> {
            BufferedImage bi = (BufferedImage) im;
            int w = bi.getWidth(), h = bi.getHeight(), x0 = w, y0 = h, x1 = -1, y1 = -1;
            for (int y = 0; y < h; y += 2) {
                for (int x = 0; x < w; x += 2) {
                    if ((bi.getRGB(x, y) >>> 24) > 40) {
                        if (x < x0) x0 = x;
                        if (x > x1) x1 = x;
                        if (y < y0) y0 = y;
                        if (y > y1) y1 = y;
                    }
                }
            }
            if (x1 < 0) return new double[]{0, 0, 1, 1};
            return new double[]{x0 / (double) w, y0 / (double) h, (x1 + 1) / (double) w, (y1 + 1) / (double) h};
        });
        return new Rectangle((int) (lb.x + f[0] * lb.width), (int) (lb.y + f[1] * lb.height),
                (int) ((f[2] - f[0]) * lb.width), (int) ((f[3] - f[1]) * lb.height));
    }

    /** Готовий напис (текст з тінню) у 2x, щоб пульс масштабував картинку цілком, а не кожну літеру окремо (без мерехтіння). */
    private BufferedImage splashImage;
    private String splashImageText;

    private static Font splashFont;

    private static Font splashFont() {
        if (splashFont == null) {
            try (java.io.InputStream in = Launcher.class.getResourceAsStream("ui/font/minecraftfontukr.ttf")) {
                splashFont = Font.createFont(Font.TRUETYPE_FONT, Objects.requireNonNull(in)).deriveFont(Font.PLAIN, (float) SPLASH_FONT_PX);
            } catch (Exception e) {
                splashFont = new Font(Font.SANS_SERIF, Font.BOLD, SPLASH_FONT_PX); // без файлу як було
            }
        }
        return splashFont;
    }

    private static final int SPLASH_FONT_PX = 40;

    private BufferedImage splashImage(String text) {
        if (splashImage != null && text.equals(splashImageText)) return splashImage;
        // 17.11.2026 (власник: «для жовтого тексту використай цей шрифт minecraftfontukr.ttf»): піксельний шрифт
        // Minecraft з українськими літерами, 40 px (5 px на піксель шрифту), тінь на один піксель шрифту
        Font font = splashFont();
        BufferedImage probe = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D pg = probe.createGraphics();
        FontMetrics fm = pg.getFontMetrics(font);
        int w = fm.stringWidth(text) + 8, h = fm.getHeight() + 8;
        pg.dispose();
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        g.setFont(font);
        int y = fm.getAscent() + 2;
        int shadow = font.getFamily().startsWith("Minecraft") ? 5 : 4;
        g.setColor(new Color(63, 63, 21));
        g.drawString(text, 2 + shadow, y + shadow);
        g.setColor(new Color(255, 255, 85));
        g.drawString(text, 2, y);
        g.dispose();
        splashImage = img;
        splashImageText = text;
        return img;
    }

    /** Жовтий напис під кутом біля логотипа, плавно «дихає» (як у меню Minecraft). */
    private void paintSplash(Graphics2D g2, float alpha) {
        if (splashIndex < 0 || splashIndex >= splashes.length || alpha <= 0.01F) {
            splashBounds = null;
            return;
        }
        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, Math.min(1.0F, alpha)));
        BufferedImage img = splashImage(splashes[splashIndex]);
        // плавна синусоїда без зламу (abs(sin) давав ривок унизу), період 1.6 с, розмах 5%
        double t = (System.currentTimeMillis() - splashStart) % SPLASH_PERIOD_MS / (double) SPLASH_PERIOD_MS;
        double pulse = 1.0 + 0.05 * (0.5 - 0.5 * Math.cos(t * Math.PI * 2));
        // картинка у 2x; довгий напис зменшується, щоб не вилазив за вікно
        // як у Minecraft: розмір від висоти логотипа (шрифт ~25% його видимої висоти, 20..36 px), довгий напис менший
        Rectangle lb = visibleLogoBounds();
        double fontPx = Math.max(20, Math.min(36, lb.height * 0.25)) * doubleOf(currentLayout, "splash.size", 1.0);
        double base = fontPx / SPLASH_FONT_PX; // картинка намальована шрифтом 40 (2x)
        if (!currentLayout.containsKey("splash.size")) base = Math.min(base, lb.width * 0.55 / Math.max(1, img.getWidth()));
        double scale = base * pulse;

        // якір збірки (frame_<збірка>.properties: splash.x, splash.y частки видимого логотипа, splash.angle):
        // це ПОЧАТОК напису (лівий нижній край), біля правого нижнього кута логотипа, як у Minecraft; напис іде
        // вгору-праворуч, тож різна довжина тексту не зсуває його початок
        double angle = doubleOf(currentLayout, "splash.angle", SPLASH_ANGLE);
        double rad = Math.toRadians(angle);
        double half = img.getWidth() * base / 2.0;
        double startX = lb.x + lb.width * doubleOf(currentLayout, "splash.x", 0.86) + intOf(currentLayout, "splash.dx");
        double startY = lb.y + lb.height * doubleOf(currentLayout, "splash.y", 0.92) + intOf(currentLayout, "splash.dy");
        double cx = startX + half * Math.cos(rad);
        double cy = startY - half * Math.sin(rad);
        boolean manual = "true".equals(currentLayout.getProperty("splash.manual"));
        double overflow = cx + half * Math.cos(rad) + 10 - getWidth(); // не за край вікна
        if (overflow > 0 && !manual) {
            cx -= overflow;
        }
        // кожен напис різної довжини: якщо налазить на літери логотипа, опускаємо, доки не перестане (до 90 px)
        if (!manual) cy += splashDrop(img, base, cx, cy, angle); // виставлене руками не чіпаємо
        AffineTransform at = AffineTransform.getTranslateInstance(cx, cy);
        at.rotate(Math.toRadians(-angle));
        at.scale(scale, scale);
        at.translate(-img.getWidth() / 2.0, -img.getHeight() / 2.0);
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2.drawImage(img, at, null);

        // межі з запасом під найбільший розмір
        AffineTransform big = AffineTransform.getTranslateInstance(cx, cy);
        big.rotate(Math.toRadians(-angle));
        big.scale(base * 1.06, base * 1.06);
        big.translate(-img.getWidth() / 2.0, -img.getHeight() / 2.0);
        Rectangle r = big.createTransformedShape(new Rectangle(0, 0, img.getWidth(), img.getHeight())).getBounds();
        r.grow(3, 3);
        splashBounds = r;
    }

    private final Map<String, Integer> splashDrops = new HashMap<>();

    /** На скільки пікселів опустити напис, щоб його літери не лягали на непрозорі пікселі логотипа (кешується). */
    private int splashDrop(BufferedImage img, double base, double cx, double cy, double angle) {
        if (!(logoImage instanceof BufferedImage) || logoBounds.width <= 0 || logoBounds.height <= 0) return 0;
        String key = System.identityHashCode(logoImage) + "|" + splashImageText + "|" + (int) cx + "|" + (int) cy + "|" + angle + "|" + logoBounds;
        Integer cached = splashDrops.get(key);
        if (cached != null) return cached;
        BufferedImage logo = (BufferedImage) logoImage;
        int drop = 0;
        for (; drop <= 90; drop += 2) {
            if (!splashHitsLogo(img, base * 1.06, cx, cy + drop, angle, logo)) break;
        }
        drop = Math.min(drop, 90);
        splashDrops.put(key, drop);
        return drop;
    }

    private boolean splashHitsLogo(BufferedImage img, double scale, double cx, double cy, double angle, BufferedImage logo) {
        AffineTransform t = AffineTransform.getTranslateInstance(cx, cy);
        t.rotate(Math.toRadians(-angle));
        t.scale(scale, scale);
        t.translate(-img.getWidth() / 2.0, -img.getHeight() / 2.0);
        java.awt.geom.Point2D.Double src = new java.awt.geom.Point2D.Double(), dst = new java.awt.geom.Point2D.Double();
        int hits = 0;
        for (int y = 0; y < img.getHeight(); y += 3) {
            for (int x = 0; x < img.getWidth(); x += 3) {
                if ((img.getRGB(x, y) >>> 24) < 100) continue; // лише самі літери напису
                src.setLocation(x, y);
                t.transform(src, dst);
                int lx = (int) ((dst.x - logoBounds.x) * logo.getWidth() / logoBounds.width);
                int ly = (int) ((dst.y - logoBounds.y) * logo.getHeight() / logoBounds.height);
                if (lx < 0 || ly < 0 || lx >= logo.getWidth() || ly >= logo.getHeight()) continue;
                if ((logo.getRGB(lx, ly) >>> 24) > 60 && ++hits > 2) return true;
            }
        }
        return false;
    }

    private static double doubleOf(Properties props, String name, double def) {
        try {
            String v = props.getProperty(name);
            return v == null ? def : Double.parseDouble(v.trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private void paintLogo(Graphics2D g2, Scene scene, float alpha, double zoom) {
        Image image = scene.logo;
        if (image == null || alpha <= 0.0F) return;
        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, Math.min(1.0F, alpha)));
        // вписати в зону LOGO_* зі збереженням пропорцій, по центру; logo.scale з налаштувань або редактора
        double scale = Math.min((double) LOGO_W / image.getWidth(null), (double) LOGO_H / image.getHeight(null)) * scene.logoScale();
        int w = (int) Math.round(image.getWidth(null) * scale);
        int h = (int) Math.round(image.getHeight(null) * scale);
        int x = viewCenterX() - LOGO_W / 2 + (LOGO_W - w) / 2 + scene.logoDx(), y = LOGO_Y + (LOGO_H - h) / 2 + scene.logoDy();
        if (scene == current) {
            logoBounds.setBounds(x, y, w, h);
            logoImage = image;
        }
        // 16.11.2026: логотип теж масштабується один раз (раніше з 724-776 px на кожному кадрі напису поруч)
        double ds = com.gummer.launcher.look.DevicePixels.scale(g2);
        BufferedImage logo = com.gummer.launcher.look.DevicePixels.cached(image, com.gummer.launcher.look.DevicePixels.px(w, ds), com.gummer.launcher.look.DevicePixels.px(h, ds), true);
        if (zoom != 1.0) {
            int zw = (int) Math.round(w * zoom), zh = (int) Math.round(h * zoom);
            g2.drawImage(logo, x + (w - zw) / 2, y + (h - zh) / 2, zw, zh, null);
        } else {
            com.gummer.launcher.look.DevicePixels.draw(g2, logo, x, y, w, h);
        }
    }

}
