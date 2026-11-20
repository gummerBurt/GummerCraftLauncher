/*
 * Copyright (C) 2021-2026 GummerCraft <http://gummercraft.fun>
 */

package com.gummer.launcher.look;

import javax.swing.Icon;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Arc2D;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;

/**
 * Іконки меню збірки (правий клік) і налаштувань векторно, 16x16 (20.11.2026, власник: «стрілочка тут шакальна»).
 * Раніше {@code ui/icon/light/*.png} 16x16, які на масштабі 125-150% розтягувались. Форми ті самі, що в png.
 * Малюються з початку по цілому фізичному пікселю.
 */
public enum MenuIcon implements Icon {
    DOWNLOAD, FOLDER, PLAY, REFRESH, SETTINGS, WIDGET, IMAGE, CRASH, REINSTALL, TRASH;

    private static final Color COLOR = new Color(242, 242, 242);
    private Shape shape;

    @Override
    public int getIconWidth() {
        return 16;
    }

    @Override
    public int getIconHeight() {
        return 16;
    }

    @Override
    public void paintIcon(Component c, Graphics g, int x, int y) {
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            AffineTransform at = g2.getTransform();
            double ox = x, oy = y, k = 1;
            if (at.getShearX() == 0 && at.getShearY() == 0 && at.getScaleX() > 0) {
                Point2D o = at.transform(new Point2D.Double(x, y), null);
                ox = Math.round(o.getX());
                oy = Math.round(o.getY());
                k = Math.round(16 * at.getScaleX()) / 16.0;
                g2.setTransform(new AffineTransform());
            }
            AffineTransform t = new AffineTransform();
            t.translate(ox, oy);
            t.scale(k, k);
            Color col = c != null && !c.isEnabled() ? new Color(242, 242, 242, 110) : COLOR;
            g2.setColor(col);
            g2.fill(t.createTransformedShape(shape()));
        } finally {
            g2.dispose();
        }
    }

    private Shape shape() {
        if (shape == null) shape = build();
        return shape;
    }

    private static Area ring(Shape outer, Shape inner) {
        Area a = new Area(outer);
        a.subtract(new Area(inner));
        return a;
    }

    private static Shape stroke(Shape s, float w) {
        return new BasicStroke(w, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND).createStrokedShape(s);
    }

    private Shape build() {
        switch (this) {
            case DOWNLOAD: {
                // стрілка вниз і лоток
                Path2D p = new Path2D.Double();
                p.moveTo(6, 1);
                p.lineTo(10, 1);
                p.lineTo(10, 5.5);
                p.lineTo(12.8, 5.5);
                p.lineTo(8, 11);
                p.lineTo(3.2, 5.5);
                p.lineTo(6, 5.5);
                p.closePath();
                Area a = new Area(p);
                a.add(new Area(new RoundRectangle2D.Double(2, 13, 12, 2, 1, 1)));
                return a;
            }
            case FOLDER: {
                Path2D p = new Path2D.Double();
                p.moveTo(0.5, 3.5);
                p.quadTo(0.5, 2.5, 1.5, 2.5);
                p.lineTo(5.5, 2.5);
                p.lineTo(7, 4);
                p.lineTo(13.5, 4);
                p.quadTo(14.5, 4, 14.5, 5);
                p.lineTo(14.5, 13.5);
                p.lineTo(1.5, 13.5);
                p.quadTo(0.5, 13.5, 0.5, 12.5);
                p.closePath();
                Area a = new Area(p);
                a.subtract(new Area(new Rectangle2D.Double(2, 5.5, 11, 1.4)));
                return a;
            }
            case PLAY: {
                // геймпад
                Area a = new Area(new RoundRectangle2D.Double(0.5, 3.5, 15, 9, 7, 7));
                Path2D cross = new Path2D.Double();
                cross.append(new Rectangle2D.Double(3.3, 6.4, 1.6, 4.2), false);
                cross.append(new Rectangle2D.Double(2.1, 7.7, 4, 1.6), false);
                a.subtract(new Area(cross));
                a.subtract(new Area(new Ellipse2D.Double(10.6, 6.1, 1.9, 1.9)));
                a.subtract(new Area(new Ellipse2D.Double(9, 8.6, 1.9, 1.9)));
                return a;
            }
            case REFRESH: {
                Area a = new Area(stroke(new Arc2D.Double(2.5, 3.5, 11, 11, 70, 290, Arc2D.OPEN), 1.8f));
                Path2D head = new Path2D.Double();
                head.moveTo(7, 0.8);
                head.lineTo(11.2, 4);
                head.lineTo(7, 7.2);
                head.closePath();
                a.add(new Area(head));
                return a;
            }
            case SETTINGS: {
                // повзунки
                Area a = new Area();
                a.add(new Area(new RoundRectangle2D.Double(0.5, 3.2, 15, 1.6, 1.6, 1.6)));
                a.add(new Area(new RoundRectangle2D.Double(0.5, 7.2, 15, 1.6, 1.6, 1.6)));
                a.add(new Area(new RoundRectangle2D.Double(0.5, 11.7, 15, 1.6, 1.6, 1.6)));
                a.add(new Area(new RoundRectangle2D.Double(5.2, 1, 2.2, 6, 1, 1)));
                a.add(new Area(new RoundRectangle2D.Double(10, 5.2, 2.2, 6, 1, 1)));
                a.add(new Area(new RoundRectangle2D.Double(2.8, 9.6, 2.2, 5.8, 1, 1)));
                return a;
            }
            case WIDGET: {
                Area a = new Area(new RoundRectangle2D.Double(1, 1, 6, 6, 1.2, 1.2));
                Path2D d = new Path2D.Double();
                d.moveTo(12, 0.6);
                d.lineTo(15.4, 4);
                d.lineTo(12, 7.4);
                d.lineTo(8.6, 4);
                d.closePath();
                a.add(new Area(d));
                a.add(new Area(new RoundRectangle2D.Double(1, 9, 6, 6, 1.2, 1.2)));
                a.add(new Area(new RoundRectangle2D.Double(9, 9, 6, 6, 1.2, 1.2)));
                return a;
            }
            case IMAGE: {
                Area a = ring(new RoundRectangle2D.Double(0.5, 2.5, 15, 11, 2.5, 2.5),
                        new RoundRectangle2D.Double(2.2, 4.2, 11.6, 7.6, 1, 1));
                a.add(new Area(new Ellipse2D.Double(4, 5, 2.2, 2.2)));
                Path2D m = new Path2D.Double();
                m.moveTo(3.6, 10.8);
                m.lineTo(6.6, 8);
                m.lineTo(8.2, 9.3);
                m.lineTo(10.2, 7);
                m.lineTo(12.6, 10.8);
                m.closePath();
                a.add(new Area(m));
                return a;
            }
            case CRASH: {
                // вікно консолі з запрошенням
                Area a = ring(new RoundRectangle2D.Double(0.5, 2.5, 15, 11, 2.5, 2.5),
                        new RoundRectangle2D.Double(2.2, 4.2, 11.6, 7.6, 1, 1));
                Path2D chev = new Path2D.Double();
                chev.moveTo(3.8, 5.6);
                chev.lineTo(6.2, 7.6);
                chev.lineTo(3.8, 9.6);
                a.add(new Area(stroke(chev, 1.5f)));
                a.add(new Area(new Rectangle2D.Double(7.5, 9.2, 4.2, 1.5)));
                return a;
            }
            case REINSTALL: {
                // дві стрілки по колу
                Area a = new Area(stroke(new Arc2D.Double(2, 2, 12, 12, 100, 150, Arc2D.OPEN), 1.8f));
                a.add(new Area(stroke(new Arc2D.Double(2, 2, 12, 12, 280, 150, Arc2D.OPEN), 1.8f)));
                Path2D h1 = new Path2D.Double();
                h1.moveTo(9.6, -0.2);
                h1.lineTo(13.6, 2.2);
                h1.lineTo(9.6, 4.6);
                h1.closePath();
                Path2D h2 = new Path2D.Double();
                h2.moveTo(6.4, 11.4);
                h2.lineTo(2.4, 13.8);
                h2.lineTo(6.4, 16.2);
                h2.closePath();
                a.add(new Area(h1));
                a.add(new Area(h2));
                a.add(new Area(new Ellipse2D.Double(5.2, 5.2, 5.6, 5.6)));
                return a;
            }
            case TRASH:
            default: {
                Area a = new Area(new RoundRectangle2D.Double(1, 3.4, 14, 1.8, 1, 1));
                a.add(ring(new RoundRectangle2D.Double(5, 0.6, 6, 3.6, 1.5, 1.5), new Rectangle2D.Double(6.5, 1.9, 3, 2.4)));
                Path2D body = new Path2D.Double();
                body.moveTo(2.8, 6);
                body.lineTo(13.2, 6);
                body.lineTo(12.4, 15);
                body.lineTo(3.6, 15);
                body.closePath();
                Area b = new Area(body);
                b.subtract(new Area(new Rectangle2D.Double(5.6, 8, 1.3, 5)));
                b.subtract(new Area(new Rectangle2D.Double(9.1, 8, 1.3, 5)));
                a.add(b);
                return a;
            }
        }
    }
}
