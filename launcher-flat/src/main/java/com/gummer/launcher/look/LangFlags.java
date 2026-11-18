/*
 * Copyright (C) 2021-2026 GummerCraft <http://gummercraft.fun>
 */

package com.gummer.launcher.look;

import com.gummer.launcher.i18n.I18n;
import com.gummer.launcher.i18n.Lang;
import com.skcraft.launcher.Launcher;
import com.skcraft.launcher.persistence.Persistence;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Line2D;
import java.awt.geom.RoundRectangle2D;

/**
 * Перемикач мови прапорцями на головній сторінці (18.11.2026, власник: «винеси перемикання мов на головну сторінку
 * лаунчера. Просто прапорцями, щоб було зрозуміло, якщо цей лаунчер скачає хтось НЕукраїномовний, щоб він не мусів лізти
 * в налаштування»). Плашка на кожну мову: прапорець і код (UA, EN), поточна підсвічена. Клік по іншій: мова
 * зберігається в налаштуваннях, тексти перечитуються, і головне вікно перевідкривається, як при зміні вигляду, без
 * перезапуску лаунчера. Прапорці намальовані кодом (без картинок), щоб були чіткими при будь-якому масштабі.
 */
public final class LangFlags extends JComponent {

    // 18.11.2026 ~13:40 (власник: «трішки замалі кнопки, UA майже впритик»): 52x28 -> 62x32, вміст по центру плашки
    private static final int PILL_W = 62, GAP = 6, H = 32, FLAG_W = 21, FLAG_H = 15;

    private final Launcher launcher;
    private final Lang[] langs = Lang.values();
    private final Lang current;
    private int hover = -1;
    private double zoom = 1.0;
    private boolean switching;
    /** Плашки на всю ширину компонента (Скло: рівно під плашками акаунта і скіна), інакше своєї ширини праворуч. */
    private boolean fill;

    /** 18.11.2026 ~13:40 (власник: у склі «ширина сумарна кнопок мови не дотягує зліва до кнопок скіни та увійти»). */
    public void setFill(boolean fill) {
        this.fill = fill;
        repaint();
    }

    public LangFlags(Launcher launcher) {
        this.launcher = launcher;
        this.current = Lang.getFromName(launcher.getConfig().getLang());
        setOpaque(false);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        setToolTipText("");
        MouseAdapter m = new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                setHover(indexAt(e.getX(), e.getY()));
            }

            @Override
            public void mouseExited(MouseEvent e) {
                setHover(-1);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                // як плашки вигляду і акаунта (18.11.2026): по відпусканню всередині, клік з рухом миші теж рахується
                if (!SwingUtilities.isLeftMouseButton(e)) return;
                int i = indexAt(e.getX(), e.getY());
                if (i >= 0) select(langs[i]);
            }
        };
        addMouseListener(m);
        addMouseMotionListener(m);
    }

    @Override
    public javax.accessibility.AccessibleContext getAccessibleContext() {
        // у голого JComponent його немає (null), а екранним читачам потрібна назва
        if (accessibleContext == null) {
            accessibleContext = new AccessibleJComponent() {
                @Override
                public javax.accessibility.AccessibleRole getAccessibleRole() {
                    return javax.accessibility.AccessibleRole.PANEL;
                }
            };
            accessibleContext.setAccessibleName("Language / Мова");
        }
        return accessibleContext;
    }

    /** Збільшення в розгорнутому склі. */
    public void setZoom(double z) {
        zoom = z > 0 ? z : 1.0;
        revalidate();
        repaint();
    }

    private int px(double v) {
        return (int) Math.round(v * zoom);
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(px(langs.length * PILL_W + (langs.length - 1) * GAP), px(H));
    }

    @Override
    public Dimension getMinimumSize() {
        return fill ? new Dimension(px(80), px(H)) : getPreferredSize();
    }

    @Override
    public Dimension getMaximumSize() {
        return fill ? new Dimension(Short.MAX_VALUE, px(H)) : getPreferredSize();
    }

    private Rectangle pill(int i) {
        int h = Math.min(getHeight(), px(H));
        if (fill) {
            int w = Math.max(1, (getWidth() - (langs.length - 1) * px(GAP)) / langs.length);
            int x = i == langs.length - 1 ? getWidth() - w : i * (w + px(GAP)); // остання впритул до правого краю
            return new Rectangle(x, (getHeight() - h) / 2, w, h);
        }
        int w = px(PILL_W);
        int total = px(langs.length * PILL_W + (langs.length - 1) * GAP);
        int x0 = Math.max(0, getWidth() - total); // притиснуто праворуч, якщо комірка ширша
        return new Rectangle(x0 + i * (w + px(GAP)), (getHeight() - h) / 2, w, h);
    }

    private int indexAt(int x, int y) {
        for (int i = 0; i < langs.length; i++) if (pill(i).contains(x, y)) return i;
        return -1;
    }

    private void setHover(int i) {
        if (i == hover) return;
        hover = i;
        repaint();
    }

    @Override
    public String getToolTipText(MouseEvent e) {
        int i = indexAt(e.getX(), e.getY());
        if (i < 0) return null;
        return langs[i] == current ? langs[i].getName() : langs[i].getName() + "  (" + (langs[i] == Lang.ENGLISH ? "switch language" : "змінити мову") + ")";
    }

    private void select(Lang lang) {
        if (lang == current || switching) return;
        switching = true;
        I18n.changeLang(lang, launcher);
        Persistence.commitAndForget(launcher.getConfig());
        I18n.load(launcher);
        Window w = SwingUtilities.getWindowAncestor(this);
        com.gummer.launcher.FancyLauncher.reopenLauncherWindow(launcher, w);
    }

    private static String code(Lang l) {
        String id = l.getId();
        int u = id.indexOf('_');
        return (u > 0 && l != Lang.ENGLISH ? id.substring(u + 1) : id.substring(0, Math.max(2, u))).toUpperCase(java.util.Locale.ROOT);
    }

    @Override
    protected void paintComponent(Graphics g0) {
        Graphics2D g = (Graphics2D) g0.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            LookTheme.Palette p = LookTheme.palette();
            Font f = UIManager.getFont("Label.font").deriveFont(Font.BOLD, (float) (12.5 * zoom));
            g.setFont(f);
            FontMetrics fm = g.getFontMetrics();
            for (int i = 0; i < langs.length; i++) {
                Rectangle r = pill(i);
                boolean on = langs[i] == current, hot = i == hover;
                float arc = (float) px(8);
                RoundRectangle2D shape = new RoundRectangle2D.Float(r.x + 0.5f, r.y + 0.5f, r.width - 1f, r.height - 1f, arc, arc);
                g.setColor(new Color(0, 0, 0, on ? 110 : hot ? 90 : 60));
                g.fill(shape);
                if (on || hot) {
                    g.setColor(on ? LookTheme.alpha(p.accent, 200) : new Color(255, 255, 255, 70));
                    g.setStroke(new BasicStroke((float) Math.max(1.0, zoom)));
                    g.draw(shape);
                }
                int fw = px(FLAG_W), fh = px(FLAG_H);
                String c = code(langs[i]);
                int content = fw + px(7) + fm.stringWidth(c);
                int fx = r.x + Math.max(px(4), (r.width - content) / 2), fy = r.y + (r.height - fh) / 2;
                Graphics2D fg = (Graphics2D) g.create();
                fg.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, on || hot ? 1f : 0.6f));
                paintFlag(fg, langs[i], fx, fy, fw, fh);
                fg.dispose();
                g.setColor(on ? p.text : hot ? p.text : p.text2);
                int tx = fx + fw + px(7);
                g.drawString(c, tx, r.y + (r.height - fm.getHeight()) / 2 + fm.getAscent());
            }
        } finally {
            g.dispose();
        }
    }

    private static void paintFlag(Graphics2D g, Lang l, int x, int y, int w, int h) {
        Shape clip = new RoundRectangle2D.Float(x, y, w, h, Math.max(2, h / 4f), Math.max(2, h / 4f));
        Shape old = g.getClip();
        g.clip(clip);
        if (l == Lang.UKRAINIAN) {
            g.setColor(new Color(0x0057B7));
            g.fillRect(x, y, w, h / 2 + (h % 2));
            g.setColor(new Color(0xFFD500));
            g.fillRect(x, y + h / 2, w, h - h / 2);
        } else {
            // спрощений прапор Великої Британії: синій, білі і червоні діагоналі, білий і червоний хрест
            g.setColor(new Color(0x012169));
            g.fillRect(x, y, w, h);
            float s = h / 14f;
            g.setStroke(new BasicStroke(3f * s));
            g.setColor(Color.WHITE);
            g.draw(new Line2D.Float(x, y, x + w, y + h));
            g.draw(new Line2D.Float(x, y + h, x + w, y));
            g.setStroke(new BasicStroke(1.1f * s));
            g.setColor(new Color(0xC8102E));
            g.draw(new Line2D.Float(x, y, x + w, y + h));
            g.draw(new Line2D.Float(x, y + h, x + w, y));
            g.setColor(Color.WHITE);
            float cw = 4.4f * s;
            g.fill(new java.awt.geom.Rectangle2D.Float(x + (w - cw) / 2f, y, cw, h));
            g.fill(new java.awt.geom.Rectangle2D.Float(x, y + (h - cw) / 2f, w, cw));
            g.setColor(new Color(0xC8102E));
            float rw = 2.6f * s;
            g.fill(new java.awt.geom.Rectangle2D.Float(x + (w - rw) / 2f, y, rw, h));
            g.fill(new java.awt.geom.Rectangle2D.Float(x, y + (h - rw) / 2f, w, rw));
        }
        g.setClip(old);
        g.setColor(new Color(0, 0, 0, 60));
        g.setStroke(new BasicStroke(1f));
        g.draw(new RoundRectangle2D.Float(x + 0.5f, y + 0.5f, w - 1f, h - 1f, Math.max(2, h / 4f), Math.max(2, h / 4f)));
    }
}
