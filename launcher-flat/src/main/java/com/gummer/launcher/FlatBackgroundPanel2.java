/*
 * Copyright (C) 2021-2026 GummerCraft <http://gummercraft.fun>
 */

package com.gummer.launcher;

import com.skcraft.launcher.Launcher;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.List;

/**
 * Тло головного вікна, редизайн 2 (тест, 16.11.2026): сцена збірки праворуч від рейки і над панеллю запуску.
 *
 * <p>Для вибраної збірки можуть бути власні фон і логотип: {@code ui/frame/bg_<назва збірки>.jpg} і
 * {@code ui/frame/logo_<назва збірки>.png} (назва з маніфесту, малими літерами). Усі ванільні версії беруть
 * bg_vanilla.jpg і logo_vanilla.png (FlatLauncherFrame2). Немає файлу: стандартні bg.jpg і logo.png. Налаштування
 * збірки: {@code ui/frame/frame_<назва збірки>.properties} (wiki.url, telegram.url, discord.url, background.from).</p>
 *
 * <p><b>Розміщення в цьому варіанті.</b> Фон заповнює сцену («cover»: не менший, ніж треба, щоб закрити її) і стоїть
 * по центру; логотип обрізаний по непрозорих пікселях, по центру сцени по горизонталі, центр на {@link #LOGO_CENTER_Y}
 * висоти сцени (під кнопками навігації). Від цього положення відраховуються ключі збірки (логічні пікселі сцени):
 * {@code logo.x}, {@code logo.y} (зсув центру логотипа), {@code logo.scale} (% від стандартного розміру),
 * {@code background.x}, {@code background.y} (зсув фону; фон ніколи не відкриває край сцени), {@code background.scale}
 * (% від «cover», не менше 100). У вшитих frame_*.properties ці ключі пишуться з префіксом {@code v2.} (без префікса
 * там старі зсуви класичного вигляду, вони тут не діють).</p>
 *
 * <p><b>Редактор розкладки</b> (код ↑↑↓↓←→←→BA, {@link FrameEditor}): власник тягне мишею логотип і фон, коліщатком
 * міняє розмір. Правки зберігаються в {@code <тека лаунчера>/launcher/frame-edit/frame_<ключ>.properties} (лише
 * змінені ключі, без префікса) і накладаються поверх вшитих.</p>
 *
 * <p><b>Перехід між збірками</b>: обидва кадри (що було на екрані, і нова збірка) готуються наперед у фізичних
 * пікселях, потім новий проявляється поверх старого за {@link #FADE_MS} з плавним прискоренням і гальмуванням. Так
 * логотип і фон перетікають разом, без проміжних порожніх кадрів і без стрибка, якщо збірку змінили посеред переходу.</p>
 */
public class FlatBackgroundPanel2 extends JPanel {

    /** Тривалість переходу між збірками. */
    private static final int FADE_MS = 420;

    /** Логотип за замовчуванням: не ширший і не вищий за це (без збільшення понад власний розмір картинки). */
    private static final int LOGO_MAX_W = 440, LOGO_MAX_H = 116;
    /** Центр логотипа за замовчуванням по вертикалі: частка висоти сцени (зона під кнопками навігації). */
    private static final double LOGO_CENTER_Y = 0.255;

    static final String[] KEYS = {"logo.x", "logo.y", "logo.scale", "background.x", "background.y", "background.scale"};

    /** Що малюється для однієї збірки. */
    private static final class Scene {
        final String key;
        final Image background;
        final Image logo;
        int logoX, logoY, logoScale = 100;
        int bgX, bgY, bgScale = 100;

        Scene(String key, Image background, Image logo) {
            this.key = key;
            this.background = background;
            this.logo = logo;
        }

        boolean looksSame(Scene o) {
            return o != null && background == o.background && logo == o.logo && logoX == o.logoX && logoY == o.logoY
                    && logoScale == o.logoScale && bgX == o.bgX && bgY == o.bgY && bgScale == o.bgScale;
        }
    }

    private final Image defaultBackground;
    private final Image defaultLogo;

    private Scene current;
    /** Перехід: кадр, що був на екрані, і кадр нової збірки (у фізичних пікселях); null - переходу немає. */
    private BufferedImage fromFrame, toFrame;
    private float fade = 1.0F;
    private long fadeStart;
    private final javax.swing.Timer fadeTimer;

    private Properties currentLayout = new Properties();

    /** Вже перевірені файли: шлях -> зображення (null - файлу нема). */
    private final Map<String, Properties> layouts = new HashMap<>();
    private final Map<Image, Rectangle> logoBounds = new HashMap<>();

    // редактор
    private File editDir;
    private final Map<String, Properties> edits = new HashMap<>();
    private final javax.swing.Timer saveTimer;
    private FrameEditor editor;

    public FlatBackgroundPanel2() {
        defaultBackground = cached("ui/frame/bg.jpg"); // 18.11.2026: спільний кеш трьох виглядів
        defaultLogo = cached(com.gummer.launcher.look.FrameImages.logoPath()); // 22.11.2026: англійською своє лого
        current = new Scene(null, defaultBackground, defaultLogo);
        // 18.11.2026: фони всіх збірок під розмір вікна наперед (PackPrewarm)
        com.gummer.launcher.look.PackPrewarm.hook(this, this::prewarmBackgrounds);

        fadeTimer = new javax.swing.Timer(15, e -> {
            fade = Math.min(1.0F, (System.currentTimeMillis() - fadeStart) / (float) FADE_MS);
            if (fade >= 1.0F) {
                fromFrame = null;
                toFrame = null;
                ((javax.swing.Timer) e.getSource()).stop();
            }
            repaintScene();
        });
        saveTimer = new javax.swing.Timer(400, e -> saveEdits());
        saveTimer.setRepeats(false);
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
        String key = null;
        Properties layout = new Properties();
        if (packName != null) {
            String k = packName.toLowerCase(Locale.ROOT);
            if (k.matches("[a-z0-9_.-]+")) {
                key = k;
                background = cached("ui/frame/bg_" + k + ".jpg");
                logo = cached("ui/frame/logo_" + k + ".png");
                layout = layout(k);
                // 1.33: background.from=<інший пак> - фон іншого оформлення
                String from = layout.getProperty("background.from");
                if (background == null && from != null && from.matches("[a-z0-9_.-]+")) {
                    background = cached("ui/frame/bg_" + from + ".jpg");
                }
            }
        }
        if (customBackground != null) background = customBackground;
        currentLayout = layout;
        // 17.11.2026: сторінка Modrinth без лого збірки
        Scene next = new Scene(key, background != null ? background : defaultBackground,
                FlatBackgroundPanel.isBrowserLook(key) ? FlatBackgroundPanel.NO_LOGO : logo != null ? logo : defaultLogo);
        applyValues(next);
        if (next.looksSame(current)) {
            current = next;
            return;
        }
        boolean canAnimate = animate && !com.gummer.launcher.look.LookTheme.LITE && isShowing() && getWidth() > 0 && getHeight() > 0;
        if (!canAnimate) {
            fadeTimer.stop();
            fromFrame = toFrame = null;
            current = next;
            fade = 1.0F;
            repaintScene();
            return;
        }
        // кадр, який зараз на екрані (з урахуванням недограного переходу), і кадр нової збірки
        BufferedImage shown = fromFrame != null ? blend(fromFrame, toFrame, ease(fade)) : renderFrame(current);
        current = next;
        fromFrame = shown;
        toFrame = renderFrame(next);
        fade = 0.0F;
        fadeStart = System.currentTimeMillis();
        fadeTimer.restart();
        repaintScene();
    }

    /** Налаштування вибраної збірки з frame_&lt;назва&gt;.properties (наприклад "wiki.url"), або null. Для адрес приймаються лише http(s). */
    public String packSetting(String name) {
        String value = currentLayout.getProperty(name);
        if (value == null || value.trim().isEmpty()) return null;
        value = value.trim();
        if (name.endsWith(".url") && !(value.startsWith("https://") || value.startsWith("http://"))) return null;
        return value;
    }

    /**
     * 18.11.2026 (сторож EdtWatch: клік по збірці в Класиці масштабував її фон під вікно прямо в потоці інтерфейсу,
     * 60-90 мс): ті самі розміри, що порахує paintContent, рахуються наперед у фоні. false - якісь картинки ще
     * розпаковуються, спробувати пізніше.
     */
    private boolean prewarmBackgrounds(double ds) {
        Rectangle scene = sceneRect();
        if (scene.width <= 0 || scene.height <= 0) return true;
        boolean all = true;
        for (String k : com.gummer.launcher.look.PackPrewarm.KEYS) {
            Image bg = "default".equals(k) ? defaultBackground : com.gummer.launcher.look.FrameImages.peek("ui/frame/bg_" + k + ".jpg");
            if (bg == null && !"default".equals(k)) {
                String from = layout(k).getProperty("background.from");
                if (from != null && from.matches("[a-z0-9_.-]+")) bg = com.gummer.launcher.look.FrameImages.peek("ui/frame/bg_" + from + ".jpg");
            }
            if (bg == null) {
                all &= com.gummer.launcher.look.FrameImages.warmedUp();
                continue;
            }
            Scene s = new Scene("default".equals(k) ? null : k, bg, defaultLogo);
            applyValues(s);
            Rectangle b = bgRect(scene, s);
            com.gummer.launcher.look.DevicePixels.prewarm(bg, Math.max(1, (int) Math.round(b.width * ds)), Math.max(1, (int) Math.round(b.height * ds)), false);
        }
        return all;
    }

    /** Лишився для сумісності з LauncherFrameFlat2: логотип стоїть нижче за навігацію, межа не потрібна. */
    public void setLogoLimit(int x) {
    }

    /** 16.11.2026: спільний кеш трьох виглядів, розпакований наперед у фоні (look.FrameImages). */
    private Image cached(String path) {
        com.gummer.launcher.look.FrameImages.warmUp();
        return com.gummer.launcher.look.FrameImages.get(path);
    }

    private Properties layout(String key) {
        return layouts.computeIfAbsent(key, k -> {
            Properties props = new Properties();
            try (InputStream in = com.gummer.launcher.look.FrameImages.layoutStream(k)) {
                if (in != null) props.load(new InputStreamReader(in, StandardCharsets.UTF_8));
            } catch (IOException e) {
                props.clear();
            }
            return props;
        });
    }

    // ------------------------------------------------------------------ значення розкладки

    /** Значення ключа: правка власника (frame-edit), інакше вшите {@code v2.<ключ>}, інакше за замовчуванням. */
    private int value(String key, String name, int fallback) {
        if (key != null) {
            String v = edits(key).getProperty(name);
            if (v == null) v = layout(key).getProperty("v2." + name);
            if (v != null) {
                try {
                    return Integer.parseInt(v.trim());
                } catch (NumberFormatException ignored) {
                    // далі значення за замовчуванням
                }
            }
        }
        return fallback;
    }

    private void applyValues(Scene s) {
        s.logoX = value(s.key, "logo.x", 0);
        s.logoY = value(s.key, "logo.y", 0);
        s.logoScale = clamp(value(s.key, "logo.scale", 100), 20, 400);
        s.bgX = value(s.key, "background.x", 0);
        s.bgY = value(s.key, "background.y", 0);
        s.bgScale = clamp(value(s.key, "background.scale", 100), 100, 300);
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    // ------------------------------------------------------------------ геометрія

    Rectangle sceneRect() {
        int x = com.gummer.launcher.ui2.Ui.RAIL_W;
        return new Rectangle(x, 0, Math.max(1, getWidth() - x), Math.max(1, getHeight() - com.gummer.launcher.ui2.Ui.DOCK_H));
    }

    /** Де лежить фон (може виходити за сцену, але ніколи не відкриває її край). Зсуви підрізаються до допустимих. */
    private Rectangle bgRect(Rectangle scene, Scene s) {
        Image image = s.background;
        if (image == null) return new Rectangle(scene);
        int iw = image.getWidth(null), ih = image.getHeight(null);
        if (iw <= 0 || ih <= 0) return new Rectangle(scene);
        double k = Math.max(scene.width / (double) iw, scene.height / (double) ih) * s.bgScale / 100.0;
        int w = (int) Math.ceil(iw * k), h = (int) Math.ceil(ih * k);
        int maxX = (w - scene.width) / 2, maxY = (h - scene.height) / 2;
        int x = scene.x + (scene.width - w) / 2 + clamp(s.bgX, -maxX, maxX);
        int y = scene.y + (scene.height - h) / 2 + clamp(s.bgY, -maxY, maxY);
        return new Rectangle(x, y, w, h);
    }

    private Rectangle logoRect(Rectangle scene, Scene s) {
        Image image = s.logo;
        if (image == null) return null;
        Rectangle r = logoBounds.computeIfAbsent(image, FlatBackgroundPanel2::opaqueBounds);
        if (r.width <= 0 || r.height <= 0) return null;
        int maxW = Math.min(LOGO_MAX_W, scene.width - 2 * com.gummer.launcher.ui2.Ui.S6);
        int maxH = Math.min(LOGO_MAX_H, scene.height / 4);
        if (maxW <= 0 || maxH <= 0) return null;
        double k = Math.min(1.0, Math.min(maxW / (double) r.width, maxH / (double) r.height)) * s.logoScale / 100.0;
        int w = Math.max(1, (int) Math.round(r.width * k)), h = Math.max(1, (int) Math.round(r.height * k));
        int cx = scene.x + scene.width / 2 + s.logoX;
        // 17.11.2026 (власник: «в класиці проблеми тільки з логотипами - їх треба в повноекранці попіднімати»): у
        // найменшому вікні центр лого ~150 px від верху сцени; вище вікно лого вже не опускає
        int cy = scene.y + (int) Math.min(Math.round(scene.height * LOGO_CENTER_Y), 150) + s.logoY;
        return new Rectangle(cx - w / 2, cy - h / 2, w, h);
    }

    // ------------------------------------------------------------------ малювання

    @Override
    protected void paintChildren(Graphics g) {
        // Картинки всередині вікна (піксельні PNG 1x) на екрані 125-150% інакше розтягуються білінійно і розмиваються:
        // нащадкам за замовчуванням найближчий піксель; де потрібна гладкість, компонент ставить своє на копії Graphics.
        if (g instanceof Graphics2D) {
            ((Graphics2D) g).setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        }
        super.paintChildren(g);
    }

    private void repaintScene() {
        Rectangle r = sceneRect();
        repaint(r.x, r.y, r.width, r.height);
        if (editor != null && editor.isVisible()) editor.repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setColor(com.gummer.launcher.ui2.Ui.BG);
            g2.fillRect(0, 0, getWidth(), getHeight());
            Rectangle scene = sceneRect();
            g2.clipRect(scene.x, scene.y, scene.width, scene.height);
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            if (fromFrame != null && toFrame != null) {
                // кадри вже у фізичних пікселях: копіюються 1:1, без масштабування на кожному кроці (16.11.2026)
                com.gummer.launcher.look.DevicePixels.draw(g2, fromFrame, scene.x, scene.y, scene.width, scene.height);
                g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, ease(fade)));
                com.gummer.launcher.look.DevicePixels.draw(g2, toFrame, scene.x, scene.y, scene.width, scene.height);
            } else {
                paintContent(g2, scene, current, deviceScale(g2));
            }
        } finally {
            g2.dispose();
        }
    }

    /** Фон, затемнення під текстом і логотип однієї збірки. */
    private void paintContent(Graphics2D g2, Rectangle scene, Scene s, double ds) {
        Rectangle b = bgRect(scene, s);
        if (s.background != null) {
            com.gummer.launcher.look.DevicePixels.draw(g2, scaledBackground(s.background, b.width, b.height, ds), b.x, b.y, b.width, b.height);
        } else {
            g2.setColor(com.gummer.launcher.ui2.Ui.SURFACE);
            g2.fillRect(scene.x, scene.y, scene.width, scene.height);
        }
        // затемнення згори під навігацією і знизу над панеллю запуску
        g2.setPaint(new GradientPaint(0, scene.y, new Color(0, 0, 0, 150), 0, scene.y + 120, new Color(0, 0, 0, 0)));
        g2.fillRect(scene.x, scene.y, scene.width, 120);
        int bottom = scene.y + scene.height;
        g2.setPaint(new GradientPaint(0, bottom - 200, new Color(0, 0, 0, 0), 0, bottom, new Color(15, 16, 19, 220)));
        g2.fillRect(scene.x, bottom - 200, scene.width, 200);

        Rectangle l = logoRect(scene, s);
        if (l != null) {
            // 16.11.2026: логотип масштабується один раз під розмір і масштаб екрана, кадр переходу лише копіює пікселі
            Rectangle r = logoBounds.get(s.logo);
            int lw = com.gummer.launcher.look.DevicePixels.px(l.width, ds), lh = com.gummer.launcher.look.DevicePixels.px(l.height, ds);
            BufferedImage li = com.gummer.launcher.look.DevicePixels.cached(s.logo, r.x, r.y, r.width, r.height, lw, lh, true);
            com.gummer.launcher.look.DevicePixels.draw(g2, li, l.x, l.y, l.width, l.height);
        }
    }

    /** Фон у фізичних пікселях (на екрані 125% малюється 1:1, без другого розтягування). */
    private BufferedImage scaledBackground(Image image, int w, int h, double ds) {
        int pw = Math.max(1, (int) Math.round(w * ds)), ph = Math.max(1, (int) Math.round(h * ds));
        // 17.11.2026: спільний кеш з тимчасовою картинкою, поки якісна рахується у фоні (DevicePixels.cachedQuick)
        return com.gummer.launcher.look.DevicePixels.cachedQuick(image, pw, ph, false, this);
    }

    private static double deviceScale(Graphics2D g2) {
        double s = g2.getTransform().getScaleX();
        return s > 0 ? s : 1.0;
    }

    private double deviceScale() {
        GraphicsConfiguration gc = getGraphicsConfiguration();
        double s = gc != null ? gc.getDefaultTransform().getScaleX() : 1.0;
        return s > 0 ? s : 1.0;
    }

    /** Готовий кадр сцени для переходу, у фізичних пікселях. */
    private BufferedImage renderFrame(Scene s) {
        Rectangle scene = sceneRect();
        double ds = deviceScale();
        BufferedImage img = new BufferedImage(Math.max(1, (int) Math.ceil(scene.width * ds)), Math.max(1, (int) Math.ceil(scene.height * ds)), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.scale(ds, ds);
        g.translate(-scene.x, -scene.y);
        paintContent(g, scene, s, ds);
        g.dispose();
        return img;
    }

    private static BufferedImage blend(BufferedImage a, BufferedImage b, float alpha) {
        BufferedImage out = new BufferedImage(a.getWidth(), a.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.drawImage(a, 0, 0, null);
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(b, 0, 0, a.getWidth(), a.getHeight(), null);
        g.dispose();
        return out;
    }

    /** Плавний початок і кінець переходу (ease-in-out, кубічна). */
    private static float ease(float t) {
        t = Math.max(0F, Math.min(1F, t));
        return t < 0.5F ? 4F * t * t * t : 1F - (float) Math.pow(-2F * t + 2F, 3) / 2F;
    }

    /** Межі непрозорих пікселів картинки (логотипи лежать по центру великого прозорого полотна). */
    private static Rectangle opaqueBounds(Image image) {
        int w = image.getWidth(null), h = image.getHeight(null);
        if (!(image instanceof BufferedImage)) return new Rectangle(0, 0, w, h);
        BufferedImage b = (BufferedImage) image;
        if (!b.getColorModel().hasAlpha()) return new Rectangle(0, 0, w, h);
        int minX = w, minY = h, maxX = -1, maxY = -1;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if ((b.getRGB(x, y) >>> 24) > 24) {
                    if (x < minX) minX = x;
                    if (x > maxX) maxX = x;
                    if (y < minY) minY = y;
                    if (y > maxY) maxY = y;
                }
            }
        }
        return maxX < 0 ? new Rectangle(0, 0, w, h) : new Rectangle(minX, minY, maxX - minX + 1, maxY - minY + 1);
    }

    // ------------------------------------------------------------------ редактор розкладки (код ↑↑↓↓←→←→BA)

    /** Підключити редактор до вікна: шар над сценою і клавіші. Правки лягають у {@code dir}. */
    public void installEditor(JFrame frame, File dir) {
        this.editDir = dir;
        editor = new FrameEditor(this);
        frame.getLayeredPane().add(editor, JLayeredPane.PALETTE_LAYER);
        editor.setVisible(false);
        addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override
            public void componentResized(java.awt.event.ComponentEvent e) {
                editor.syncBounds();
            }

            @Override
            public void componentMoved(java.awt.event.ComponentEvent e) {
                editor.syncBounds();
            }
        });
        JRootPane root = frame.getRootPane();
        InputMap im = root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap am = root.getActionMap();
        // 16.11.2026: код ↑↑↓↓←→←→BA замість F2 (власник: гравцям редактор не потрібен), Esc закриває
        com.gummer.launcher.KonamiCode.install(frame, () -> setEditing(!isEditing()));
        im.put(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ESCAPE, 0), "frameEditClose");
        am.put("frameEditClose", new EditorOnlyAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                setEditing(false);
            }
        });
        im.put(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_C, 0), "frameEditCopy");
        am.put("frameEditCopy", new EditorOnlyAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                editCopy();
            }
        });
        im.put(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_R, 0), "frameEditReset");
        am.put("frameEditReset", new EditorOnlyAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                editReset();
            }
        });
    }

    /** Клавіші C і R діють лише в режимі редактора (поза ним дія вимкнена і клавіша йде далі як звичайно). */
    private abstract class EditorOnlyAction extends AbstractAction {
        @Override
        public boolean isEnabled() {
            return isEditing();
        }
    }

    public boolean isEditing() {
        return editor != null && editor.isVisible();
    }

    public void setEditing(boolean on) {
        if (editor == null) return;
        if (!on) saveEdits();
        editor.syncBounds();
        editor.setVisible(on);
        editor.setMessage(null);
        repaintScene();
    }

    String editKey() {
        return current.key;
    }

    Rectangle editLogoRect() {
        return logoRect(sceneRect(), current);
    }

    Rectangle editBgRect() {
        return bgRect(sceneRect(), current);
    }

    /** Зсунути логотип або фон мишею (логічні пікселі). */
    void editMove(boolean logo, int dx, int dy) {
        if (current.key == null) return;
        stopFade();
        if (logo) {
            current.logoX += dx;
            current.logoY += dy;
            setEdit("logo.x", current.logoX);
            setEdit("logo.y", current.logoY);
        } else {
            Rectangle scene = sceneRect();
            Rectangle before = bgRect(scene, current);
            int maxX = (before.width - scene.width) / 2, maxY = (before.height - scene.height) / 2;
            current.bgX = clamp(current.bgX + dx, -maxX, maxX);
            current.bgY = clamp(current.bgY + dy, -maxY, maxY);
            setEdit("background.x", current.bgX);
            setEdit("background.y", current.bgY);
        }
        repaintScene();
    }

    /** Коліщатко: крок 5%, вгору більше. Фон не менший за «cover». */
    void editScale(boolean logo, int notches) {
        if (current.key == null || notches == 0) return;
        stopFade();
        if (logo) {
            current.logoScale = clamp(current.logoScale - notches * 5, 20, 400);
            setEdit("logo.scale", current.logoScale);
        } else {
            current.bgScale = clamp(current.bgScale - notches * 5, 100, 300);
            setEdit("background.scale", current.bgScale);
            Rectangle scene = sceneRect();
            Rectangle b = bgRect(scene, current);
            int maxX = (b.width - scene.width) / 2, maxY = (b.height - scene.height) / 2;
            current.bgX = clamp(current.bgX, -maxX, maxX);
            current.bgY = clamp(current.bgY, -maxY, maxY);
            if (edits(current.key).containsKey("background.x")) setEdit("background.x", current.bgX);
            if (edits(current.key).containsKey("background.y")) setEdit("background.y", current.bgY);
        }
        repaintScene();
    }

    /** Поточні значення збірки одним рядком для підказки редактора. */
    String editSummary() {
        Scene s = current;
        return String.format(Locale.ROOT, "логотип x %d, y %d, %d%%   ·   фон x %d, y %d, %d%%",
                s.logoX, s.logoY, s.logoScale, s.bgX, s.bgY, s.bgScale);
    }

    /** C: усі значення збірки в буфер обміну (щоб вшити у frame_*.properties). */
    void editCopy() {
        Scene s = current;
        if (s.key == null) return;
        StringBuilder sb = new StringBuilder();
        sb.append("# frame_").append(s.key).append(".properties, лаунчер редизайн 2 (вшивати з префіксом v2.)\n");
        sb.append("v2.logo.x=").append(s.logoX).append('\n');
        sb.append("v2.logo.y=").append(s.logoY).append('\n');
        sb.append("v2.logo.scale=").append(s.logoScale).append('\n');
        sb.append("v2.background.x=").append(s.bgX).append('\n');
        sb.append("v2.background.y=").append(s.bgY).append('\n');
        sb.append("v2.background.scale=").append(s.bgScale).append('\n');
        try {
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(sb.toString()), null);
            if (editor != null) editor.setMessage("Скопійовано значення " + s.key);
        } catch (Exception e) {
            if (editor != null) editor.setMessage("Не вдалось скопіювати: " + e.getMessage());
        }
    }

    /** R: скинути правки вибраної збірки (файл правок видаляється, діють вшиті значення). */
    void editReset() {
        Scene s = current;
        if (s.key == null) return;
        saveTimer.stop();
        edits.put(s.key, new Properties());
        File f = editFile(s.key);
        if (f != null && f.isFile() && !f.delete()) {
            if (editor != null) editor.setMessage("Не вдалось видалити " + f.getName());
        } else if (editor != null) {
            editor.setMessage("Правки " + s.key + " скинуто");
        }
        applyValues(s);
        repaintScene();
    }

    String editMessageFile() {
        File f = current.key != null ? editFile(current.key) : null;
        return f != null ? f.getPath() : "";
    }

    private void stopFade() {
        if (fromFrame != null) {
            fadeTimer.stop();
            fromFrame = toFrame = null;
            fade = 1.0F;
        }
    }

    private File editFile(String key) {
        return editDir != null ? new File(editDir, "frame_" + key + ".properties") : null;
    }

    private Properties edits(String key) {
        return edits.computeIfAbsent(key, k -> {
            Properties p = new Properties();
            File f = editFile(k);
            if (f != null && f.isFile()) {
                try (Reader r = new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8)) {
                    p.load(r);
                } catch (IOException e) {
                    p.clear();
                }
            }
            return p;
        });
    }

    private final Set<String> dirtyKeys = new HashSet<>();

    private void setEdit(String name, int value) {
        edits(current.key).setProperty(name, String.valueOf(value));
        dirtyKeys.add(current.key);
        saveTimer.restart();
    }

    private void saveEdits() {
        saveTimer.stop();
        for (String key : new ArrayList<>(dirtyKeys)) {
            File f = editFile(key);
            if (f == null) continue;
            Properties p = edits(key);
            try {
                f.getParentFile().mkdirs();
                // лише змінені ключі, у сталому порядку
                StringBuilder sb = new StringBuilder();
                sb.append("# Правки розкладки з редактора лаунчера, збірка ").append(key).append('\n');
                for (String k : KEYS) {
                    String v = p.getProperty(k);
                    if (v != null) sb.append(k).append('=').append(v).append('\n');
                }
                try (Writer w = new OutputStreamWriter(new FileOutputStream(f), StandardCharsets.UTF_8)) {
                    w.write(sb.toString());
                }
                dirtyKeys.remove(key);
            } catch (IOException e) {
                if (editor != null) editor.setMessage("Не вдалось зберегти " + f.getName() + ": " + e.getMessage());
            }
        }
    }
}
