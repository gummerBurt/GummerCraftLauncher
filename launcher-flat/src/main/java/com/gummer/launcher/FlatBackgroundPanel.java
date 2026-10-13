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
 * Тло головного вікна: фон, рамка (borders.png) і логотип. Для вибраної збірки можуть бути власні фон і логотип:
 * {@code ui/frame/bg_<назва збірки>.jpg} і {@code ui/frame/logo_<назва збірки>.png} (назва з маніфесту, малими літерами, наприклад bg_staldayz.jpg).
 * Немає файлу - малюється стандартний (bg.jpg / logo.png). Налаштування збірки - {@code ui/frame/frame_<назва збірки>.properties}:
 * зсуви background.x/y і logo.x/y у пікселях, wiki.url - куди веде кнопка "Вікі та FAQ" (дивись frame_staldayz.properties).
 * Зміна збірки - плавний перехід (старі фон і логотип розчиняються в нові за {@link #FADE_MS}).
 */
public class FlatBackgroundPanel extends JPanel {

    /** Зона логотипа (як у стандартного logo.png 724x180): логотип збірки вписується в неї без розтягування, по центру. */
    private static final int LOGO_X = 346, LOGO_Y = 5, LOGO_W = 724, LOGO_H = 180;
    /** Стандартний фон малюється з X = 100 (так підібрано під bg.jpg); фон збірки - по центру тієї ж видимої зони (центр = центр логотипа), по висоті - по центру панелі. */
    private static final int DEFAULT_BG_X = 100;
    private static final int VIEW_CENTER_X = LOGO_X + LOGO_W / 2;
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
    private final Image borders;

    private Scene current;
    private Scene previous;
    private float fade = 1.0F;
    private long fadeStart;
    private final Timer fadeTimer;

    private Properties currentLayout = new Properties();

    /** Вже перевірені файли: шлях -> зображення (null - файлу нема). */
    private final Map<String, Image> cache = new HashMap<>();
    private final Map<String, Properties> layouts = new HashMap<>();

    public FlatBackgroundPanel() {
        defaultBackground = read("ui/frame/bg.jpg");
        borders = read("ui/frame/borders.png");
        defaultLogo = read("ui/frame/logo.png");
        current = new Scene(defaultBackground, false, 0, 0, defaultLogo, 0, 0);

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
        Image background = null;
        Image logo = null;
        Properties layout = new Properties();
        if (packName != null) {
            String key = packName.toLowerCase(Locale.ROOT);
            if (key.matches("[a-z0-9_.-]+")) {
                background = cached("ui/frame/bg_" + key + ".jpg");
                logo = cached("ui/frame/logo_" + key + ".png");
                layout = layout(key);
            }
        }
        currentLayout = layout;
        Scene next = new Scene(background != null ? background : defaultBackground, background != null,
                intOf(layout, "background.x"), intOf(layout, "background.y"),
                logo != null ? logo : defaultLogo, intOf(layout, "logo.x"), intOf(layout, "logo.y"));
        if (next.looksSame(current)) return;

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

    private Image cached(String path) {
        if (!cache.containsKey(path)) {
            Image image = null;
            try (InputStream in = Launcher.class.getResourceAsStream(path)) {
                if (in != null) image = ImageIO.read(in);
            } catch (IOException e) {
                image = null;
            }
            cache.put(path, image);
        }
        return cache.get(path);
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

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

            // фони: старий повністю, новий поверх з прозорістю переходу
            if (previous != null) paintBackground(g2, previous, 1.0F);
            paintBackground(g2, current, previous != null ? fade : 1.0F);

            if (borders != null) {
                g2.setComposite(AlphaComposite.SrcOver);
                g2.drawImage(borders, 0, 0, null);
            }

            // логотипи: старий згасає, новий проявляється
            if (previous != null) paintLogo(g2, previous, 1.0F - fade);
            paintLogo(g2, current, previous != null ? fade : 1.0F);
        } finally {
            g2.dispose();
        }
    }

    private void paintBackground(Graphics2D g2, Scene scene, float alpha) {
        Image image = scene.background;
        if (image == null || alpha <= 0.0F) return;
        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, Math.min(1.0F, alpha)));
        if (scene.packBackground) {
            int w = image.getWidth(null);
            int h = image.getHeight(null);
            int x = VIEW_CENTER_X - w / 2 + scene.backgroundDx;
            int y = (getHeight() - h) / 2 + scene.backgroundDy;
            // не далі, ніж фон ще закриває панель (ліворуч до DEFAULT_BG_X, як у стандартного фону, - там список збірок)
            x = Math.max(getWidth() - w, Math.min(DEFAULT_BG_X, x));
            y = Math.max(getHeight() - h, Math.min(0, y));
            g2.drawImage(image, x, y, null);
        } else {
            g2.drawImage(image, DEFAULT_BG_X, 0, null);
        }
    }

    private void paintLogo(Graphics2D g2, Scene scene, float alpha) {
        Image image = scene.logo;
        if (image == null || alpha <= 0.0F) return;
        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, Math.min(1.0F, alpha)));
        // вписати в зону LOGO_* зі збереженням пропорцій, по центру (стандартний logo.png 724x180 заповнює її повністю)
        double scale = Math.min((double) LOGO_W / image.getWidth(null), (double) LOGO_H / image.getHeight(null));
        int w = (int) Math.round(image.getWidth(null) * scale);
        int h = (int) Math.round(image.getHeight(null) * scale);
        g2.drawImage(image, LOGO_X + (LOGO_W - w) / 2 + scene.logoDx, LOGO_Y + (LOGO_H - h) / 2 + scene.logoDy, w, h, null);
    }

}
