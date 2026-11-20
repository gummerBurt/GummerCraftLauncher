/*
 * Copyright (C) 2021-2026 GummerCraft <http://gummercraft.fun>
 */

package com.gummer.launcher.look;

import javax.swing.Icon;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Point2D;

/**
 * Значок «акаунт» (біле коло з силуетом) векторно, чіткий на будь-якому масштабі (20.11.2026, власник: «супер
 * шакальна оця іконка»). Раніше {@code ui/icon/light/user.png} 16x16, розтягнутий до 26-40 фізичних пікселів.
 * Форма та сама: коло, у ньому вирізані голова і плечі.
 */
public final class UserIcon implements Icon {

    private static Area shape;
    private final int size;
    private final Color color;

    public UserIcon(int size) {
        this(size, Color.WHITE);
    }

    public UserIcon(int size, Color color) {
        this.size = size;
        this.color = color;
    }

    /** Форма в сітці 16x16. */
    private static Area shape() {
        if (shape == null) {
            Area a = new Area(new Ellipse2D.Double(1, 1, 14, 14));
            a.subtract(new Area(new Ellipse2D.Double(5.3, 3.6, 5.4, 5.4)));
            Area body = new Area(new Ellipse2D.Double(3, 10, 10, 9));
            body.intersect(new Area(new Ellipse2D.Double(2.4, 2.4, 11.2, 11.2)));
            a.subtract(body);
            shape = a;
        }
        return shape;
    }

    @Override
    public void paintIcon(Component c, Graphics g, int x, int y) {
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            AffineTransform at = g2.getTransform();
            double ox = x, oy = y, k = size / 16.0;
            if (at.getShearX() == 0 && at.getShearY() == 0 && at.getScaleX() > 0) {
                // початок по цілому фізичному пікселю, розмір у фізичних пікселях
                Point2D o = at.transform(new Point2D.Double(x, y), null);
                ox = Math.round(o.getX());
                oy = Math.round(o.getY());
                k = Math.round(size * at.getScaleX()) / 16.0;
                g2.setTransform(new AffineTransform());
            }
            AffineTransform t = new AffineTransform();
            t.translate(ox, oy);
            t.scale(k, k);
            g2.setColor(color);
            g2.fill(t.createTransformedShape(shape()));
        } finally {
            g2.dispose();
        }
    }

    @Override
    public int getIconWidth() {
        return size;
    }

    @Override
    public int getIconHeight() {
        return size;
    }
}
