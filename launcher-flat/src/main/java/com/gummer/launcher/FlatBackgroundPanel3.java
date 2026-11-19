/*
 * Copyright (C) 2021-2025 GummerCraft <http://gummercraft.fun>
 */

package com.gummer.launcher;

import com.skcraft.launcher.Launcher;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;

/**
 * Тло головного вікна: фон і логотип збірки (редизайн 3: фон на все вікно, без рамки borders.png, див. нижче). Для вибраної збірки можуть бути власні фон і логотип:
 * {@code ui/frame/bg_<назва збірки>.jpg} і {@code ui/frame/logo_<назва збірки>.png} (назва з маніфесту, малими літерами, наприклад bg_staldayz.jpg).
 * Усі ванільні версії (група vanilla) беруть bg_vanilla.jpg і logo_vanilla.png (FlatLauncherFrame3, з 1.32).
 * Немає файлу - малюється стандартний (bg.jpg / logo.png). Налаштування збірки - {@code ui/frame/frame_<назва збірки>.properties}:
 * зсуви background.x/y і logo.x/y у пікселях, background.from (фон іншого оформлення), wiki.url - куди веде кнопка "Вікі та FAQ" (дивись frame_staldayz.properties).
 * Зміна збірки - плавний перехід (старі фон і логотип розчиняються в нові за {@link #FADE_MS}).
 */
public class FlatBackgroundPanel3 extends JPanel {

    /** Тривалість переходу між збірками. */
    private static final int FADE_MS = 300;

    /** Що малюється для однієї збірки. */
    private static final class Scene {
        final Image background;
        final boolean packBackground;
        final int backgroundDx, backgroundDy;
        final Image logo;
        final int logoDx, logoDy;

        Scene(Image background, boolean packBackground, int backgroundDx, int backgroundDy, Image logo, int logoDx, int logoDy) {
            this.background = background;
            this.packBackground = packBackground;
            this.backgroundDx = backgroundDx;
            this.backgroundDy = backgroundDy;
            this.logo = logo;
            this.logoDx = logoDx;
            this.logoDy = logoDy;
        }

        boolean looksSame(Scene o) {
            return o != null && background == o.background && logo == o.logo && backgroundDx == o.backgroundDx && backgroundDy == o.backgroundDy
                    && logoDx == o.logoDx && logoDy == o.logoDy;
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

    /** Вже перевірені файли: шлях -> зображення (null - файлу нема). */
    private final Map<String, Properties> layouts = new HashMap<>();

    public FlatBackgroundPanel3() {
        defaultBackground = cached("ui/frame/bg.jpg"); // 18.11.2026: спільний кеш трьох виглядів
        defaultLogo = cached("ui/frame/logo.png");
        current = new Scene(defaultBackground, false, 0, 0, defaultLogo, 0, 0);
        // 18.11.2026: фони всіх збірок під розмір вікна наперед (PackPrewarm)
        com.gummer.launcher.look.PackPrewarm.hook(this, this::prewarmBackgrounds);

        fadeTimer = new Timer(16, e -> {
            fade = Math.min(1.0F, (System.currentTimeMillis() - fadeStart) / (float) FADE_MS);
            if (fade >= 1.0F) {
                previous = null;
                ((Timer) e.getSource()).stop();
            }
            repaint();
        });
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
        Properties layout = new Properties();
        if (packName != null) {
            String key = packName.toLowerCase(Locale.ROOT);
            if (key.matches("[a-z0-9_.-]+")) {
                background = cached("ui/frame/bg_" + key + ".jpg");
                logo = cached("ui/frame/logo_" + key + ".png");
                layout = layout(key);
                // 1.33: background.from=<інший пак> - фон іншого оформлення (NeoForge поки бере фон Vanilla)
                String from = layout.getProperty("background.from");
                if (background == null && from != null && from.matches("[a-z0-9_.-]+")) {
                    background = cached("ui/frame/bg_" + from + ".jpg");
                }
            }
        }
        if (customBackground != null) background = customBackground;
        currentLayout = layout;
        Scene next = new Scene(background != null ? background : defaultBackground, background != null,
                intOf(layout, "background.x"), intOf(layout, "background.y"),
                FlatBackgroundPanel.BROWSER_LOOK.equalsIgnoreCase(packName) ? FlatBackgroundPanel.NO_LOGO // 17.11.2026: сторінка Modrinth
                        : logo != null ? logo : defaultLogo, intOf(layout, "logo.x"), intOf(layout, "logo.y"));
        if (next.looksSame(current)) return;

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
            return props;
        });
    }

    private static int intOf(Properties props, String name) {
        try {
            return Integer.parseInt(props.getProperty(name, "0").trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    // ------------------------------------------------------------------ редизайн 3 (тест, 16.11.2026)
    //
    // Фон вибраної збірки заповнює все вікно (обрізка «cover», без порожнеч). Затемнення лише там, де поверх картинки
    // текст: згори під верхню панель, ліворуч під героя (логотип, опис, кнопки) і знизу плавний перехід у суцільну
    // смугу полиці збірок. Логотип обрізаний по непрозорих пікселях і стоїть у «слоті» героя (setLogoSlot), притиснутий
    // до лівого нижнього кута слота. background.x/y з frame_*.properties діють у масштабі вікна, logo.x/y не потрібні.

    /** Масштабовані фони під розмір панелі: картинка -> готове зображення. */
    private final Map<Image, java.awt.image.BufferedImage> scaled = new HashMap<>();
    private final Map<Image, Rectangle> logoBounds = new HashMap<>();
    private Dimension scaledFor;
    private Rectangle logoSlot;

    /** Де малювати логотип (координати цієї панелі): логотип вписується, притиснутий до лівого нижнього кута. */
    public void setLogoSlot(Rectangle slot) {
        if (slot != null && !slot.equals(logoSlot)) {
            logoSlot = new Rectangle(slot);
            repaint();
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            int W = getWidth(), H = getHeight();
            g2.setColor(com.gummer.launcher.ui3.Ui.BG);
            g2.fillRect(0, 0, W, H);
            if (scaledFor == null || scaledFor.width != W || scaledFor.height != H) {
                scaled.clear();
                scaledFor = getSize();
            }
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            if (previous != null) paintScene(g2, previous, 1.0F);
            paintScene(g2, current, previous != null ? fade : 1.0F);

            g2.setComposite(AlphaComposite.SrcOver);
            int shelfTop = H - com.gummer.launcher.ui3.Ui.SHELF_H;
            Color bg = com.gummer.launcher.ui3.Ui.BG;
            // згори: під верхню панель
            g2.setPaint(new GradientPaint(0, 0, new Color(0, 0, 0, 170), 0, 150, new Color(0, 0, 0, 0)));
            g2.fillRect(0, 0, W, 150);
            // ліворуч: під текст героя
            int heroW = (int) (W * 0.68);
            g2.setPaint(new GradientPaint(0, 0, new Color(0, 0, 0, 190), heroW, 0, new Color(0, 0, 0, 0)));
            g2.fillRect(0, 0, heroW, shelfTop);
            // знизу: плавно в суцільну смугу полиці
            g2.setPaint(new GradientPaint(0, shelfTop - 190, new Color(bg.getRed(), bg.getGreen(), bg.getBlue(), 0), 0, shelfTop,
                    new Color(bg.getRed(), bg.getGreen(), bg.getBlue(), 250)));
            g2.fillRect(0, shelfTop - 190, W, 190);
            g2.setColor(bg);
            g2.fillRect(0, shelfTop, W, H - shelfTop);

            if (previous != null) paintLogo3(g2, previous, 1.0F - fade);
            paintLogo3(g2, current, previous != null ? fade : 1.0F);
        } finally {
            g2.dispose();
        }
    }

    private void paintScene(Graphics2D g2, Scene s, float alpha) {
        Image image = s.background;
        if (image == null || alpha <= 0.0F) return;
        int iw = image.getWidth(null), ih = image.getHeight(null);
        if (iw <= 0 || ih <= 0) return;
        // Фон рахується одразу у фізичних пікселях екрана: при масштабі Windows 125-150% картинка розміром з логічне
        // вікно розтягувалась би ще раз і розмивалась (16.11.2026). Малюється з одиничним масштабом по цілому пікселю.
        java.awt.geom.AffineTransform at = g2.getTransform();
        boolean plain = at.getShearX() == 0 && at.getShearY() == 0 && at.getScaleX() > 0 && at.getScaleY() > 0;
        double sx = plain ? at.getScaleX() : 1, sy = plain ? at.getScaleY() : 1;
        int W = (int) Math.round(getWidth() * sx), H = (int) Math.round(getHeight() * sy);
        double k = Math.max(W / (double) iw, H / (double) ih);
        int w = (int) Math.ceil(iw * k), h = (int) Math.ceil(ih * k);
        // 16.11.2026: кроки навпіл + бікубічний замість getScaledInstance (той стояв у потоці інтерфейсу), спільний кеш
        java.awt.image.BufferedImage img = com.gummer.launcher.look.DevicePixels.cachedQuick(image, w, h, false, this);
        double kl = k / sx; // зсув фону з frame_*.properties задано в пікселях картинки
        int x = (W - w) / 2 + (int) Math.round(s.backgroundDx * kl * sx);
        int y = (H - h) / 2 + (int) Math.round(s.backgroundDy * kl * sy);
        x = Math.max(W - w, Math.min(0, x));
        y = Math.max(H - h, Math.min(0, y));
        Graphics2D d = (Graphics2D) g2.create();
        try {
            d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, Math.min(1.0F, alpha)));
            if (plain) {
                d.setTransform(new java.awt.geom.AffineTransform(1, 0, 0, 1, Math.round(at.getTranslateX()), Math.round(at.getTranslateY())));
                if (img.getWidth() != w || img.getHeight() != h) {
                    // тимчасова картинка іншого розміру, поки якісна рахується у фоні
                    d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
                    d.drawImage(img, x, y, w, h, null);
                } else {
                    d.drawImage(img, x, y, null);
                }
            } else {
                d.drawImage(img, x, y, getWidth() * w / Math.max(1, W), getHeight() * h / Math.max(1, H), null);
            }
        } finally {
            d.dispose();
        }
    }

    /**
     * Дочірні компоненти з картинками (іконки, голова скіна) при масштабі Windows 125%, 150% малюються без розмиття:
     * найближчий піксель замість білінійного розтягування (як у варіанті 1, 16.11.2026). Хінт успадковується всіма
     * нащадками; фон і логотип ставлять своє згладжування на власній копії Graphics, плитки полиці теж.
     */
    @Override
    protected void paintChildren(Graphics g) {
        if (g instanceof Graphics2D) {
            ((Graphics2D) g).setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        }
        super.paintChildren(g);
    }

    private void paintLogo3(Graphics2D g2, Scene s, float alpha) {
        Image image = s.logo;
        if (image == null || alpha <= 0.0F || logoSlot == null) return;
        Rectangle r = logoBounds.computeIfAbsent(image, FlatBackgroundPanel3::opaqueBounds);
        if (r.width <= 0 || r.height <= 0) return;
        double k = Math.min(1.0, Math.min(logoSlot.width / (double) r.width, logoSlot.height / (double) r.height));
        int w = (int) Math.round(r.width * k), h = (int) Math.round(r.height * k);
        int x = logoSlot.x;
        int y = logoSlot.y + logoSlot.height - h;
        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, Math.min(1.0F, alpha)));
        // 16.11.2026: логотип масштабується один раз під розмір і масштаб екрана, а не на кожному кадрі
        double ds = com.gummer.launcher.look.DevicePixels.scale(g2);
        int pw = com.gummer.launcher.look.DevicePixels.px(w, ds), ph = com.gummer.launcher.look.DevicePixels.px(h, ds);
        java.awt.image.BufferedImage li = com.gummer.launcher.look.DevicePixels.cached(image, r.x, r.y, r.width, r.height, pw, ph, true);
        com.gummer.launcher.look.DevicePixels.draw(g2, li, x, y, w, h);
    }

    /** Межі непрозорих пікселів картинки (логотипи лежать по центру великого прозорого полотна). */
    private static Rectangle opaqueBounds(Image image) {
        int w = image.getWidth(null), h = image.getHeight(null);
        if (!(image instanceof java.awt.image.BufferedImage)) return new Rectangle(0, 0, w, h);
        java.awt.image.BufferedImage b = (java.awt.image.BufferedImage) image;
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

    // ------------------------------------------------------------------ ключ вигляду і мініатюри (для полиці)

    /**
     * Ключ оформлення збірки: усі ванільні версії і .mrpack беруть vanilla, Fabric, Quilt, Forge, NeoForge свої групи,
     * решта за назвою паку (немає файлів bg_/logo_ - стандартні). Раніше це рахував FlatLauncherFrame3.
     */
    public static String lookKey(com.skcraft.launcher.Instance i) {
        if (i == null) return null;
        return i.isVanilla() ? com.gummer.launcher.vanilla.VanillaVersions.GROUP
                : i.isFabric() ? com.gummer.launcher.fabric.FabricVersions.GROUP
                : i.isQuilt() ? com.gummer.launcher.quilt.QuiltVersions.GROUP
                : i.isMrpack() ? com.gummer.launcher.manage.InstanceCopies.sectionOf(i) // 17.11.2026: оформлення свого завантажувача
                : i.isForgeLike() ? com.gummer.launcher.forge.ForgeVersions.kindOf(i).group : i.getName();
    }

    /** Те саме за назвою паку (до завантаження списку збірок, з config.lastInstance). */
    public static String lookKeyOfName(String last) {
        if (last == null) return null;
        return last.startsWith(com.gummer.launcher.vanilla.VanillaVersions.GROUP + "-") ? com.gummer.launcher.vanilla.VanillaVersions.GROUP
                : last.startsWith(com.gummer.launcher.fabric.FabricVersions.PREFIX) ? com.gummer.launcher.fabric.FabricVersions.GROUP
                : last.startsWith(com.gummer.launcher.quilt.QuiltVersions.PREFIX) ? com.gummer.launcher.quilt.QuiltVersions.GROUP
                : last.startsWith(com.gummer.launcher.modrinth.MrpackPacks.PREFIX) ? com.gummer.launcher.vanilla.VanillaVersions.GROUP
                : com.gummer.launcher.forge.ForgeVersions.kindOfName(last) != null ? com.gummer.launcher.forge.ForgeVersions.kindOfName(last).group : last;
    }

    /**
     * 18.11.2026 (сторож EdtWatch: клік по збірці в Бібліотеці масштабував фон у потоці інтерфейсу): ті самі розміри,
     * що порахує paintScene, наперед у фоні. false - якісь картинки ще розпаковуються.
     */
    private boolean prewarmBackgrounds(double ds) {
        int W = (int) Math.round(getWidth() * ds), H = (int) Math.round(getHeight() * ds);
        if (W <= 0 || H <= 0) return true;
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
            int iw = bg.getWidth(null), ih = bg.getHeight(null);
            if (iw <= 0 || ih <= 0) continue;
            double kk = Math.max(W / (double) iw, H / (double) ih);
            com.gummer.launcher.look.DevicePixels.prewarm(bg, (int) Math.ceil(iw * kk), (int) Math.ceil(ih * kk), false);
        }
        return all;
    }

    private static final Map<String, Image> THUMB_SOURCES = new HashMap<>();

    /** Картинка фону для оформлення key (з background.from), або стандартний bg.jpg. Кешується. */
    public static synchronized Image backgroundFor(String key) {
        String k = key == null ? "" : key.toLowerCase(Locale.ROOT);
        if (THUMB_SOURCES.containsKey(k)) return THUMB_SOURCES.get(k);
        Image img = null;
        if (k.matches("[a-z0-9_.-]+")) {
            // 18.11.2026: зі спільного кешу, розпакованого у фоні від старту (раніше JPEG читався тут, у потоці
            // інтерфейсу, при першому показі Бібліотеки: сторож EdtWatch 0,26 с)
            img = com.gummer.launcher.look.FrameImages.get("ui/frame/bg_" + k + ".jpg");
            if (img == null) {
                Properties props = new Properties();
                try (InputStream in = Launcher.class.getResourceAsStream("ui/frame/frame_" + k + ".properties")) {
                    if (in != null) props.load(new InputStreamReader(in, StandardCharsets.UTF_8));
                } catch (IOException ignored) {
                    // без налаштувань
                }
                String from = props.getProperty("background.from");
                if (from != null && from.matches("[a-z0-9_.-]+")) img = com.gummer.launcher.look.FrameImages.get("ui/frame/bg_" + from + ".jpg");
            }
        }
        if (img == null) img = com.gummer.launcher.look.FrameImages.get("ui/frame/bg.jpg");
        THUMB_SOURCES.put(k, img);
        return img;
    }

}
