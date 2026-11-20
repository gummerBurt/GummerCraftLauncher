/*
 * Copyright (C) 2021-2026 GummerCraft <http://gummercraft.fun>
 */

package com.skcraft.launcher.swing;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;

/**
 * Іконка, чітка на будь-якому масштабі екрана (16.11.2026, редизайн 2).
 *
 * <p>Звичайна {@code ImageIcon(image.getScaledInstance(32, 32))} спершу стискає оригінал до 32 логічних пікселів,
 * а на Windows 125-150% Java ще раз розтягує ці 32 px до 40-48 фізичних, звідси розмиття. Ця іконка тримає оригінал
 * і в {@link #paintIcon} готує картинку одразу у фізичному розмірі (усереднення при стисканні, найближчий піксель
 * при збільшенні, щоб піксель-арт лишався різким), а малює її з одиничним масштабом по цілому пікселю.</p>
 */
public class CrispIcon implements Icon {

    private final BufferedImage source;
    private final int width, height;
    private BufferedImage cached;

    public CrispIcon(Image source, int width, int height) {
        this.source = toBuffered(source);
        this.width = width;
        this.height = height;
    }

    /** Іконка з ресурсу або порожня, якщо файлу немає (як {@link SwingHelper#createIcon(Class, String, int, int)}). */
    public static Icon of(Class<?> clazz, String name, int width, int height) {
        Image image = SwingHelper.createImage(clazz, name);
        return image != null ? new CrispIcon(image, width, height) : SwingHelper.createIcon(clazz, name, width, height);
    }

    /** Та сама картинка іншого логічного розміру (наприклад, велика іконка в панелі запуску). */
    public CrispIcon resized(int w, int h) {
        return new CrispIcon(source, w, h);
    }

    public BufferedImage getSource() {
        return source;
    }

    @Override
    public int getIconWidth() {
        return width;
    }

    @Override
    public int getIconHeight() {
        return height;
    }

    @Override
    public void paintIcon(Component c, Graphics g, int x, int y) {
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            AffineTransform at = g2.getTransform();
            if (at.getShearX() != 0 || at.getShearY() != 0 || at.getScaleX() <= 0 || at.getScaleY() <= 0) {
                g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                g2.drawImage(source, x, y, width, height, null);
                return;
            }
            Point2D p = at.transform(new Point2D.Double(x, y), null);
            int pw = (int) Math.round(width * at.getScaleX()), ph = (int) Math.round(height * at.getScaleY());
            BufferedImage img = physical(pw, ph);
            g2.setTransform(new AffineTransform());
            g2.drawImage(img, (int) Math.round(p.getX()), (int) Math.round(p.getY()), null);
        } finally {
            g2.dispose();
        }
    }

    private BufferedImage physical(int pw, int ph) {
        pw = Math.max(1, pw);
        ph = Math.max(1, ph);
        if (cached != null && cached.getWidth() == pw && cached.getHeight() == ph) return cached;
        BufferedImage out;
        if (pw == source.getWidth() && ph == source.getHeight()) {
            out = source;
        } else if (pw < source.getWidth() || ph < source.getHeight()) {
            out = toBuffered(source.getScaledInstance(pw, ph, Image.SCALE_AREA_AVERAGING));
        } else {
            // 20.11.2026 (власник: «супер шакальна іконка»): піксель-арт на дробовому масштабі (125%) найближчим пікселем
            // виходив з пікселями різної ширини, а плавно розмитим. Тепер «гостро»: спершу найближчим пікселем у ціле
            // число разів (не менше потрібного), потім усередненням до точного розміру: краї чіткі, пікселі рівні
            int n = (int) Math.ceil(Math.max(pw / (double) source.getWidth(), ph / (double) source.getHeight()) - 1e-9);
            BufferedImage big = new BufferedImage(source.getWidth() * n, source.getHeight() * n, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = big.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            g.drawImage(source, 0, 0, big.getWidth(), big.getHeight(), null);
            g.dispose();
            out = big.getWidth() == pw && big.getHeight() == ph ? big
                    : toBuffered(big.getScaledInstance(pw, ph, Image.SCALE_AREA_AVERAGING));
        }
        cached = out;
        return out;
    }

    private static BufferedImage toBuffered(Image image) {
        if (image instanceof BufferedImage && ((BufferedImage) image).getType() == BufferedImage.TYPE_INT_ARGB) {
            return (BufferedImage) image;
        }
        ImageIcon loaded = new ImageIcon(image); // дочекатись завантаження getScaledInstance
        int w = Math.max(1, loaded.getIconWidth()), h = Math.max(1, loaded.getIconHeight());
        BufferedImage b = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = b.createGraphics();
        g.drawImage(loaded.getImage(), 0, 0, null);
        g.dispose();
        return b;
    }
}
