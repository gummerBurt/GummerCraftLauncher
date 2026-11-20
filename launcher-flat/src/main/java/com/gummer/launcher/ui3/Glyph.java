/*
 * Copyright (C) 2021-2026 GummerCraft <http://gummercraft.fun>
 */

package com.gummer.launcher.ui3;

import com.skcraft.launcher.Launcher;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.InputStream;

/**
 * Редизайн 3: іконки інтерфейсу. Свої малюються векторно в сітці 18x18 прямими «блочними» лініями (кінці і кути
 * без заокруглень, як піксельні іконки старих кнопок), тож чіткі на будь-якому масштабі екрана. Telegram і Discord
 * це знаки сервісів: з 20.11.2026 векторні ({@code look.BrandShapes}), раніше вирізки з кнопок у {@code ui/icon/v3/*.png}.
 */
public enum Glyph {
    SETTINGS, WIKI, SUPPORT, UPDATE, PLAY, COFFEE, REFRESH, CHEVRON, BOX, MORE, TELEGRAM, DISCORD;


    /** Намалювати іконку розміром size x size у точці x, y кольором color. */
    public void paint(Graphics2D g, int x, int y, int size, Color color) {
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            g2.setColor(color);
            // 20.11.2026: у фізичних пікселях екрана (look.GlyphPen), знаки сервісів векторні (look.BrandShapes)
            com.gummer.launcher.look.GlyphPen pen = new com.gummer.launcher.look.GlyphPen(g2, x, y, size, 18);
            if (this == TELEGRAM || this == DISCORD) {
                double ps = 18 * pen.unit();
                g2.fill(this == TELEGRAM ? com.gummer.launcher.look.BrandShapes.telegram(pen.originX(), pen.originY(), ps)
                        : com.gummer.launcher.look.BrandShapes.discord(pen.originX(), pen.originY(), ps));
                return;
            }
            switch (this) {
                case SETTINGS:
                    // три повзунки
                    pen.line(2, 4, 16, 4);
                    pen.line(2, 9, 16, 9);
                    pen.line(2, 14, 16, 14);
                    pen.fillRect(5, 1, 3, 6);
                    pen.fillRect(11, 6, 3, 6);
                    pen.fillRect(6, 11, 3, 6);
                    break;
                case WIKI:
                    // аркуш з рядками
                    pen.rect(3, 1, 12, 16);
                    pen.line(6, 6, 12, 6);
                    pen.line(6, 9, 12, 9);
                    pen.line(6, 12, 9, 12);
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
                    pen.draw(p);
                    pen.fillRect(5, 6, 2, 2);
                    pen.fillRect(8, 6, 2, 2);
                    pen.fillRect(11, 6, 2, 2);
                    break;
                }
                case UPDATE:
                    // стрілка вниз у лоток
                    pen.line(9, 2, 9, 11);
                    pen.line(5, 7, 9, 11);
                    pen.line(13, 7, 9, 11);
                    pen.line(2, 12, 2, 16);
                    pen.line(2, 16, 16, 16);
                    pen.line(16, 16, 16, 12);
                    break;
                case PLAY: {
                    Path2D p = new Path2D.Float();
                    p.moveTo(4, 2);
                    p.lineTo(16, 9);
                    p.lineTo(4, 16);
                    p.closePath();
                    pen.fill(p);
                    break;
                }
                case COFFEE:
                    // чашка з парою
                    pen.rect(2, 7, 10, 9);
                    pen.line(12, 9, 15, 9);
                    pen.line(15, 9, 15, 13);
                    pen.line(15, 13, 12, 13);
                    pen.line(5, 1, 5, 4);
                    pen.line(9, 1, 9, 4);
                    break;
                case BOX:
                    // скринька: «Збірка» (17.11.2026, вікно «Керування збіркою»)
                    pen.rect(2, 4, 14, 12);
                    pen.line(2, 8, 16, 8);
                    pen.fillRect(8, 6, 3, 5);
                    break;
                case REFRESH: {
                    pen.arc(3, 3, 12, 12, 30, 270);
                    Path2D p = new Path2D.Float();
                    p.moveTo(11, 1);
                    p.lineTo(16, 4);
                    p.lineTo(11, 7);
                    p.closePath();
                    pen.fill(p);
                    break;
                }
                case MORE:
                    // три квадратні крапки: меню збірки
                    pen.fillRect(2, 8, 3, 3);
                    pen.fillRect(8, 8, 3, 3);
                    pen.fillRect(14, 8, 3, 3);
                    break;
                case CHEVRON:
                    pen.line(7, 4, 12, 9);
                    pen.line(12, 9, 7, 14);
                    break;
                default:
                    break;
            }
        } finally {
            g2.dispose();
        }
    }
}
