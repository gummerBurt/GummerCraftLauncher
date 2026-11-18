/*
 * Copyright (C) 2021-2026 GummerCraft <http://gummercraft.fun>
 */

package com.gummer.launcher.ui2;

import com.skcraft.launcher.Launcher;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.InputStream;

/**
 * Редизайн 2: іконки інтерфейсу. Свої малюються векторно в сітці 18x18 прямими «блочними» лініями (кінці і кути
 * без заокруглень, як піксельні іконки старих кнопок), тож чіткі на будь-якому масштабі екрана. Telegram і Discord
 * це знаки сервісів: вирізані з наявних кнопок у {@code ui/icon/v2/*.png}.
 */
public enum Glyph {
    SETTINGS, WIKI, SUPPORT, UPDATE, PLAY, COFFEE, REFRESH, CHEVRON, BOX, TELEGRAM, DISCORD;

    private BufferedImage image;
    private boolean imageLoaded;

    /** Намалювати іконку розміром size x size у точці x, y кольором color. */
    public void paint(Graphics2D g, int x, int y, int size, Color color) {
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            if (this == TELEGRAM || this == DISCORD) {
                paintImage(g2, x, y, size, color);
                return;
            }
            g2.translate(x, y);
            double k = size / 18.0;
            g2.scale(k, k);
            g2.setColor(color);
            g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_SQUARE, BasicStroke.JOIN_MITER));
            switch (this) {
                case SETTINGS:
                    // три повзунки
                    g2.drawLine(2, 4, 16, 4);
                    g2.drawLine(2, 9, 16, 9);
                    g2.drawLine(2, 14, 16, 14);
                    g2.fillRect(5, 1, 3, 6);
                    g2.fillRect(11, 6, 3, 6);
                    g2.fillRect(6, 11, 3, 6);
                    break;
                case WIKI:
                    // аркуш з рядками
                    g2.drawRect(3, 1, 12, 16);
                    g2.drawLine(6, 6, 12, 6);
                    g2.drawLine(6, 9, 12, 9);
                    g2.drawLine(6, 12, 9, 12);
                    break;
                case SUPPORT: {
                    // хмарка розмови з трьома крапками
                    Path2D p = new Path2D.Float();
                    p.moveTo(2, 2);
                    p.lineTo(16, 2);
                    p.lineTo(16, 12);
                    p.lineTo(8, 12);
                    p.lineTo(4, 16);
                    p.lineTo(4, 12);
                    p.lineTo(2, 12);
                    p.closePath();
                    g2.draw(p);
                    g2.fillRect(5, 6, 2, 2);
                    g2.fillRect(8, 6, 2, 2);
                    g2.fillRect(11, 6, 2, 2);
                    break;
                }
                case UPDATE:
                    // стрілка вниз у лоток
                    g2.drawLine(9, 2, 9, 11);
                    g2.drawLine(5, 7, 9, 11);
                    g2.drawLine(13, 7, 9, 11);
                    g2.drawLine(2, 12, 2, 16);
                    g2.drawLine(2, 16, 16, 16);
                    g2.drawLine(16, 16, 16, 12);
                    break;
                case PLAY: {
                    Path2D p = new Path2D.Float();
                    p.moveTo(4, 2);
                    p.lineTo(16, 9);
                    p.lineTo(4, 16);
                    p.closePath();
                    g2.fill(p);
                    break;
                }
                case COFFEE:
                    // чашка з парою
                    g2.drawRect(2, 7, 10, 9);
                    g2.drawLine(12, 9, 15, 9);
                    g2.drawLine(15, 9, 15, 13);
                    g2.drawLine(15, 13, 12, 13);
                    g2.drawLine(5, 1, 5, 4);
                    g2.drawLine(9, 1, 9, 4);
                    break;
                case BOX:
                    // скринька: «Збірка» (17.11.2026, вікно «Керування збіркою»)
                    g2.drawRect(2, 4, 14, 12);
                    g2.drawLine(2, 8, 16, 8);
                    g2.fillRect(8, 6, 3, 5);
                    break;
                case REFRESH: {
                    g2.drawArc(3, 3, 12, 12, 30, 270);
                    Path2D p = new Path2D.Float();
                    p.moveTo(11, 1);
                    p.lineTo(16, 4);
                    p.lineTo(11, 7);
                    p.closePath();
                    g2.fill(p);
                    break;
                }
                case CHEVRON:
                    g2.drawLine(7, 4, 12, 9);
                    g2.drawLine(12, 9, 7, 14);
                    break;
                default:
                    break;
            }
        } finally {
            g2.dispose();
        }
    }

    private void paintImage(Graphics2D g2, int x, int y, int size, Color color) {
        if (!imageLoaded) {
            imageLoaded = true;
            String file = this == TELEGRAM ? "telegram" : "discord";
            try (InputStream in = Launcher.class.getResourceAsStream("ui/icon/v2/" + file + ".png")) {
                if (in != null) image = ImageIO.read(in);
            } catch (Exception ignored) {
                image = null;
            }
        }
        if (image == null) return;
        // біла маска, перефарбована в потрібний колір, вписана в size x size
        double sc = Math.min(size / (double) image.getWidth(), size / (double) image.getHeight());
        int w = (int) Math.round(image.getWidth() * sc), h = (int) Math.round(image.getHeight() * sc);
        BufferedImage tinted = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D t = tinted.createGraphics();
        t.drawImage(image, 0, 0, null);
        t.setComposite(AlphaComposite.SrcIn);
        t.setColor(color);
        t.fillRect(0, 0, image.getWidth(), image.getHeight());
        t.dispose();
        // одразу у фізичних пікселях, щоб на екрані 125-150% знак не розмивався
        new com.skcraft.launcher.swing.CrispIcon(tinted, w, h).paintIcon(null, g2, x + (size - w) / 2, y + (size - h) / 2);
    }
}
