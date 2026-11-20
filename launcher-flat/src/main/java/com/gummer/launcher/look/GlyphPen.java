/*
 * Copyright (C) 2021-2026 GummerCraft <http://gummercraft.fun>
 */

package com.gummer.launcher.look;

import java.awt.BasicStroke;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Arc2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.PathIterator;
import java.awt.geom.Point2D;

/**
 * «Блочні» значки (ui2/ui3 Glyph) у фізичних пікселях екрана (20.11.2026, власник: «шакальні іконки»). Значок
 * креслиться в сітці 18x18 лініями товщиною 2; раніше сітку просто масштабували, і на 125% (крок 1.25 px) лінії
 * лягали між пікселями та розмивались. Тут кожна координата сітки округлюється до цілого фізичного пікселя, а
 * товщина лінії до цілого числа пікселів, тож горизонталі і вертикалі різкі; похилі лінії згладжені.
 */
public final class GlyphPen {

    private final Graphics2D g;
    private final double ox, oy, k;
    private final int sw;

    /**
     * Готує g (його копію) до малювання у фізичних пікселях: x, y, size у логічних пікселях, як для значка.
     * Після роботи g треба dispose(), як і раніше.
     */
    public GlyphPen(Graphics2D g, int x, int y, int size, int grid) {
        this.g = g;
        AffineTransform at = g.getTransform();
        if (at.getShearX() == 0 && at.getShearY() == 0 && at.getScaleX() > 0 && at.getScaleY() > 0) {
            Point2D o = at.transform(new Point2D.Double(x, y), null);
            ox = Math.round(o.getX());
            oy = Math.round(o.getY());
            k = size * at.getScaleX() / grid;
            g.setTransform(new AffineTransform());
        } else {
            ox = x;
            oy = y;
            k = size / (double) grid;
        }
        sw = Math.max(1, (int) Math.round(2 * k));
        g.setStroke(new BasicStroke(sw, BasicStroke.CAP_SQUARE, BasicStroke.JOIN_MITER));
    }

    /** Фізичні пікселі одиниці сітки (для знаків сервісів і кругів). */
    public double unit() {
        return k;
    }

    public double originX() {
        return ox;
    }

    public double originY() {
        return oy;
    }

    private int px(double v) {
        return (int) Math.round(ox + v * k);
    }

    private int py(double v) {
        return (int) Math.round(oy + v * k);
    }

    /** Центр лінії товщиною sw: для непарної товщини посередині пікселя. */
    private double mid() {
        return sw % 2 == 1 ? 0.5 : 0;
    }

    /** Лінія товщиною 2 одиниці з квадратними кінцями (як drawLine з BasicStroke(2, CAP_SQUARE)). */
    public void line(double x1, double y1, double x2, double y2) {
        int h = sw / 2;
        if (y1 == y2) {
            int a = px(Math.min(x1, x2)), b = px(Math.max(x1, x2)), c = py(y1);
            g.fillRect(a - h, c - h, b - a + sw, sw);
        } else if (x1 == x2) {
            int a = py(Math.min(y1, y2)), b = py(Math.max(y1, y2)), c = px(x1);
            g.fillRect(c - h, a - h, sw, b - a + sw);
        } else {
            double m = mid();
            g.draw(new Line2D.Double(px(x1) + m, py(y1) + m, px(x2) + m, py(y2) + m));
        }
    }

    /** Контур прямокутника (як drawRect). */
    public void rect(double x, double y, double w, double h) {
        line(x, y, x + w, y);
        line(x + w, y, x + w, y + h);
        line(x + w, y + h, x, y + h);
        line(x, y + h, x, y);
    }

    /** Суцільний прямокутник (як fillRect), краї по пікселях. */
    public void fillRect(double x, double y, double w, double h) {
        int a = px(x), b = py(y);
        g.fillRect(a, b, Math.max(1, px(x + w) - a), Math.max(1, py(y + h) - b));
    }

    /** Контур фігури з сітки: вершини по пікселях. */
    public void draw(Path2D p) {
        g.draw(snapped(p));
    }

    /** Заливка фігури з сітки. */
    public void fill(Shape s) {
        g.fill(transformed(s));
    }

    /** Дуга (як drawArc). */
    public void arc(double x, double y, double w, double h, double start, double extent) {
        g.draw(new Arc2D.Double(ox + x * k, oy + y * k, w * k, h * k, start, extent, Arc2D.OPEN));
    }

    private Shape transformed(Shape s) {
        AffineTransform t = new AffineTransform();
        t.translate(ox, oy);
        t.scale(k, k);
        return t.createTransformedShape(s);
    }

    private Path2D snapped(Path2D p) {
        Path2D.Double out = new Path2D.Double();
        double m = mid();
        double[] c = new double[6];
        for (PathIterator it = p.getPathIterator(null); !it.isDone(); it.next()) {
            switch (it.currentSegment(c)) {
                case PathIterator.SEG_MOVETO:
                    out.moveTo(px(c[0]) + m, py(c[1]) + m);
                    break;
                case PathIterator.SEG_LINETO:
                    out.lineTo(px(c[0]) + m, py(c[1]) + m);
                    break;
                case PathIterator.SEG_CLOSE:
                    out.closePath();
                    break;
                default:
                    return (Path2D) transformed(p);
            }
        }
        return out;
    }
}
