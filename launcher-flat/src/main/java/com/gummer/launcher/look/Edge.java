/*
 * Copyright (C) 2021-2026 GummerCraft <http://gummercraft.fun>
 */

package com.gummer.launcher.look;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.RoundRectangle2D;

/**
 * Заокруглена плашка з рамкою рівно по фізичних пікселях (20.11.2026, власник: «червона обводка знизу менша ніж
 * зверху, вона ОБРІЗАНА»).
 *
 * <p>Раніше рамку малювали як {@code drawRoundRect(0, 0, w - 1, h - 1)} або фігурою з відступом 0.5. На масштабі
 * 125% і 150% плашка починається з дробового фізичного пікселя, і Java обрізає те, що за межею компонента: верх
 * рамки ставав тоншим за низ (або навпаки), рамка виглядала зрізаною. Тут межі плашки округлюються до тих самих
 * фізичних пікселів, які Swing дає компоненту, а рамка цілої товщини лягає всередину, однаково з усіх боків.</p>
 */
public final class Edge {

    private Edge() {
    }

    /** Плашка x, y, w, h (логічні пікселі): заливка fill (може бути null) і рамка line товщиною 1 (може бути null). */
    public static void pill(Graphics2D g, double x, double y, double w, double h, double arc, Paint fill, Color line) {
        pill(g, x, y, w, h, arc, fill, line, 1.0);
    }

    /** Те саме з рамкою товщиною lineW логічних пікселів (кільце фокусу 2, пульсація 2.5). */
    public static void pill(Graphics2D g, double x, double y, double w, double h, double arc, Paint fill, Color line, double lineW) {
        if (w <= 0 || h <= 0) return;
        AffineTransform at = g.getTransform();
        Graphics2D d = (Graphics2D) g.create();
        try {
            d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            d.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            double sx = at.getScaleX(), sy = at.getScaleY();
            if (at.getShearX() != 0 || at.getShearY() != 0 || sx <= 0 || sy <= 0) { // поворот: як раніше, без пікселів
                if (fill != null) {
                    d.setPaint(fill);
                    d.fill(new RoundRectangle2D.Double(x, y, w, h, arc, arc));
                }
                if (line != null) {
                    d.setColor(line);
                    d.setStroke(new BasicStroke((float) lineW));
                    d.draw(new RoundRectangle2D.Double(x + lineW / 2, y + lineW / 2, w - lineW, h - lineW, Math.max(0, arc - lineW), Math.max(0, arc - lineW)));
                }
                return;
            }
            // ті самі фізичні пікселі, що Swing дає області компонента: піксель її, якщо його центр усередині
            int x0 = snap(at.getTranslateX() + x * sx), y0 = snap(at.getTranslateY() + y * sy);
            int x1 = snap(at.getTranslateX() + (x + w) * sx), y1 = snap(at.getTranslateY() + (y + h) * sy);
            double a = arc * sx;
            d.setTransform(new AffineTransform());
            if (fill != null) {
                d.setPaint(transformed(fill, at)); // градієнт заданий у логічних пікселях компонента
                d.fill(new RoundRectangle2D.Double(x0, y0, x1 - x0, y1 - y0, a, a));
            }
            if (line != null) {
                int t = Math.max(1, (int) Math.floor(lineW * sx + 0.25));
                d.setColor(line);
                d.setStroke(new BasicStroke(t));
                double ia = Math.max(0, a - t);
                d.draw(new RoundRectangle2D.Double(x0 + t / 2.0, y0 + t / 2.0, x1 - x0 - t, y1 - y0 - t, ia, ia));
            }
        } finally {
            d.dispose();
        }
    }

    private static int snap(double v) {
        return (int) Math.ceil(v - 0.5);
    }

    private static Paint transformed(Paint p, AffineTransform at) {
        if (p instanceof GradientPaint) {
            GradientPaint gp = (GradientPaint) p;
            java.awt.geom.Point2D a = at.transform(gp.getPoint1(), null), b = at.transform(gp.getPoint2(), null);
            return new GradientPaint(a, gp.getColor1(), b, gp.getColor2(), gp.isCyclic());
        }
        return p;
    }
}
