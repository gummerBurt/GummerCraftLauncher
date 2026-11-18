/*
 * Copyright (C) 2021-2026 GummerCraft <http://gummercraft.fun>
 */

package com.gummer.launcher.look;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 17.11.2026: заокруглені плашки (картки розділів, рядки модів) без згладженої фігури на кожному кадрі. Згладжена
 * фігура в Java2D малюється маскою шматками 32x32: кожен шматок рахується на процесорі і окремо вантажиться у
 * відеокарту. Велика картка розділу це сотні шматків на кожен кадр прокрутки (у вікні «Збірка» було близько 800 на
 * кадр). Тепер один раз малюється маленький зразок плашки (кути і по пікселю країв) у фізичних пікселях, а кадр
 * складається з дев'яти копіювань: кути як є, краї і середина розтягнуті. Пам'ять: кілька КБ на вид плашки,
 * незалежно від її розміру (картка списку модів буває на тисячі пікселів у висоту).
 */
public final class RoundBg {

    private static final int MAX = 64;
    private static final Map<List<Object>, BufferedImage> PATCHES = new LinkedHashMap<List<Object>, BufferedImage>(64, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<List<Object>, BufferedImage> e) {
            return size() > MAX;
        }
    };

    private RoundBg() {
    }

    /**
     * Заливка fill і (якщо line не null) рамка в 1 логічний піксель: те саме, що fillRoundRect(0, 0, w - inset,
     * h - inset, arc, arc) і drawRoundRect(0, 0, w - 1, h - 1, arc, arc) зі згладжуванням.
     */
    public static void paint(Graphics2D g, int w, int h, int arc, Color fill, Color line, int inset) {
        if (w <= 0 || h <= 0) return;
        AffineTransform at = g.getTransform();
        double ds = at.getScaleX();
        if (!DevicePixels.plain(at) || Math.abs(at.getScaleY() - ds) > 1e-6) {
            direct(g, w, h, arc, fill, line, inset);
            return;
        }
        int c = (int) Math.ceil(arc / 2.0 * ds) + 2;
        int s = 2 * c + 1;
        int pw = (int) Math.round(w * ds), ph = (int) Math.round(h * ds);
        if (pw < s + 2 || ph < s + 2) { // дрібна плашка: дешевше намалювати як є
            direct(g, w, h, arc, fill, line, inset);
            return;
        }
        BufferedImage p = patch(c, ds, arc, fill, line, inset);
        int ox = (int) Math.round(at.getTranslateX()), oy = (int) Math.round(at.getTranslateY());
        int[] dx = {ox, ox + c, ox + pw - c, ox + pw}, dy = {oy, oy + c, oy + ph - c, oy + ph};
        int[] sx = {0, c, c + 1, s};
        Graphics2D d = (Graphics2D) g.create();
        try {
            d.setTransform(new AffineTransform()); // фізичні пікселі; область малювання (clip) лишається та сама
            d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            for (int i = 0; i < 3; i++) {
                for (int j = 0; j < 3; j++) {
                    d.drawImage(p, dx[i], dy[j], dx[i + 1], dy[j + 1], sx[i], sx[j], sx[i + 1], sx[j + 1], null);
                }
            }
        } finally {
            d.dispose();
        }
    }

    private static BufferedImage patch(int c, double ds, int arc, Color fill, Color line, int inset) {
        List<Object> key = Arrays.<Object>asList(c, ds, arc, fill, line, inset);
        synchronized (PATCHES) {
            BufferedImage img = PATCHES.get(key);
            if (img != null) return img;
            int s = 2 * c + 1;
            img = new BufferedImage(s, s, BufferedImage.TYPE_INT_ARGB_PRE);
            Graphics2D b = img.createGraphics();
            try {
                b.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                b.scale(ds, ds);
                float l = (float) (s / ds);
                if (fill != null) {
                    b.setColor(fill);
                    b.fill(new RoundRectangle2D.Float(0, 0, l - inset, l - inset, arc, arc));
                }
                if (line != null) {
                    b.setColor(line);
                    b.draw(new RoundRectangle2D.Float(0, 0, l - 1, l - 1, arc, arc));
                }
            } finally {
                b.dispose();
            }
            PATCHES.put(key, img);
            return img;
        }
    }

    private static void direct(Graphics2D g, int w, int h, int arc, Color fill, Color line, int inset) {
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            if (fill != null) {
                g2.setColor(fill);
                g2.fillRoundRect(0, 0, w - inset, h - inset, arc, arc);
            }
            if (line != null) {
                g2.setColor(line);
                g2.drawRoundRect(0, 0, w - 1, h - 1, arc, arc);
            }
        } finally {
            g2.dispose();
        }
    }
}
