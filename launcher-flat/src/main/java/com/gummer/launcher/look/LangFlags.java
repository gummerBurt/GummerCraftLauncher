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
import java.awt.geom.AffineTransform;
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
            com.gummer.launcher.look.TextAA.apply(g);
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
                // 20.11.2026: плашка і рамка по фізичних пікселях (look.Edge)
                Edge.pill(g, r.x, r.y, r.width, r.height, arc + 1, new Color(0, 0, 0, on ? 110 : hot ? 90 : 60),
                        on || hot ? (on ? LookTheme.alpha(p.accent, 200) : new Color(255, 255, 255, 70)) : null, Math.max(1.0, zoom));
                int fw = px(FLAG_W), fh = px(FLAG_H);
                String c = code(langs[i]);
                // 21.11.2026 (власник: «UA наліплено на правий край кнопки»): у склі плашки тягнуться під акаунт і скіни і
                // бувають вужчі за PILL_W (~57 замість 62). Раніше вміст центрувався за fm.stringWidth з лівим відступом не
                // менше 4 px, тож нестача ширини і різниця між stringWidth та намальованими літерами йшли з правого боку.
                // Тепер ширина тексту береться з контуру літер, а вільне місце ділиться порівну: зліва, між прапорцем і
                // текстом, справа (проміжок не менше 4 px, решта порівну по краях).
                java.awt.geom.Rectangle2D vb = f.createGlyphVector(g.getFontRenderContext(), c).getVisualBounds();
                double free = r.width - fw - vb.getWidth();
                double gap = Math.max(px(4), Math.min(px(7), free / 3.0));
                double pad = Math.max(0, (free - gap) / 2.0);
                int fx = r.x + (int) Math.round(pad), fy = r.y + (r.height - fh) / 2;
                Graphics2D fg = (Graphics2D) g.create();
                fg.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, on || hot ? 1f : 0.6f));
                paintFlag(fg, langs[i], fx, fy, fw, fh);
                fg.dispose();
                g.setColor(on ? p.text : hot ? p.text : p.text2);
                double tx = fx + fw + gap - vb.getX(); // vb.getX(): лівий відступ першої літери всередині її клітинки
                // 20.11.2026: по центру за висотою великих літер (як прапорець), базова лінія по цілому фізичному пікселю
                double cap = f.createGlyphVector(g.getFontRenderContext(), "UAEN").getVisualBounds().getHeight();
                drawSnapped(g, c, tx, r.y + r.height / 2.0 + cap / 2.0);
            }
        } finally {
            g.dispose();
        }
    }

    /** Текст з базовою лінією по цілому фізичному пікселю (на 125% інакше розмивається по вертикалі). */
    private static void drawSnapped(Graphics2D g, String text, double x, double baseline) {
        AffineTransform at = g.getTransform();
        if (at.getShearX() != 0 || at.getShearY() != 0 || at.getScaleY() <= 0) {
            g.drawString(text, (float) x, (float) baseline);
            return;
        }
        double by = Math.round(at.getTranslateY() + baseline * at.getScaleY());
        g.drawString(text, (float) x, (float) ((by - at.getTranslateY()) / at.getScaleY()));
    }

    /** Кеш прапорців у фізичних пікселях: мова|ширина|висота. */
    private static final java.util.Map<String, java.awt.image.BufferedImage> FLAGS = new java.util.HashMap<>();

    /**
     * 20.11.2026 (власник: «замісти трішки краще прапорці»): прапорець малюється у фізичних пікселях екрана: межа
     * смуг і краї по цілих пікселях, кути згладжені (раніше обрізка без згладжування в логічних координатах, на 125%
     * краї розмивались, а кути були зубчасті).
     */
    private static void paintFlag(Graphics2D g, Lang l, int x, int y, int w, int h) {
        AffineTransform at = g.getTransform();
        double sx = at.getScaleX(), sy = at.getScaleY();
        if (at.getShearX() != 0 || at.getShearY() != 0 || sx <= 0 || sy <= 0) {
            sx = sy = 1;
        }
        int dx = (int) Math.round(at.getTranslateX() + x * sx), dy = (int) Math.round(at.getTranslateY() + y * sy);
        int dw = Math.max(4, (int) Math.round(w * sx)), dh = Math.max(3, (int) Math.round(h * sy));
        String key = l + "|" + dw + "|" + dh;
        java.awt.image.BufferedImage img = FLAGS.get(key);
        if (img == null) {
            img = renderFlag(l, dw, dh);
            FLAGS.put(key, img);
        }
        Graphics2D d = (Graphics2D) g.create();
        try {
            if (sx != 1 || sy != 1 || at.getTranslateX() != 0 || at.getTranslateY() != 0) d.setTransform(new AffineTransform());
            d.drawImage(img, dx, dy, null);
        } finally {
            d.dispose();
        }
    }

    private static java.awt.image.BufferedImage renderFlag(Lang l, int w, int h) {
        java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        if (l == Lang.UKRAINIAN) {
            g.setColor(new Color(0x0057B7));
            g.fillRect(0, 0, w, h / 2);
            g.setColor(new Color(0xFFD500));
            g.fillRect(0, h / 2, w, h - h / 2);
        } else {
            // прапор Великої Британії (спрощений): синій, білі і червоні діагоналі, білий і червоний хрест
            g.setColor(new Color(0x012169));
            g.fillRect(0, 0, w, h);
            float s = h / 14f;
            g.setStroke(new BasicStroke(3f * s));
            g.setColor(Color.WHITE);
            g.draw(new Line2D.Float(0, 0, w, h));
            g.draw(new Line2D.Float(0, h, w, 0));
            g.setStroke(new BasicStroke(1.1f * s));
            g.setColor(new Color(0xC8102E));
            g.draw(new Line2D.Float(0, 0, w, h));
            g.draw(new Line2D.Float(0, h, w, 0));
            g.setColor(Color.WHITE);
            int cw = Math.max(3, Math.round(4.4f * s)), rw = Math.max(1, Math.round(2.6f * s));
            if ((w - cw) % 2 != 0) cw++;
            if ((w - rw) % 2 != 0) rw++;
            g.fillRect((w - cw) / 2, 0, cw, h);
            g.fillRect(0, (h - cw) / 2, w, cw);
            g.setColor(new Color(0xC8102E));
            g.fillRect((w - rw) / 2, 0, rw, h);
            g.fillRect(0, (h - rw) / 2, w, rw);
        }
        // заокруглені кути зі згладжуванням: лишаємо лише те, що всередині заокругленого прямокутника
        float arc = Math.max(3f, h / 3.5f);
        java.awt.image.BufferedImage mask = new java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        Graphics2D m = mask.createGraphics();
        m.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        m.setColor(Color.WHITE);
        m.fill(new RoundRectangle2D.Float(0, 0, w, h, arc, arc));
        m.dispose();
        g.setComposite(AlphaComposite.DstIn);
        g.drawImage(mask, 0, 0, null);
        g.setComposite(AlphaComposite.SrcOver);
        g.setColor(new Color(0, 0, 0, 70));
        g.setStroke(new BasicStroke(1f));
        g.draw(new RoundRectangle2D.Float(0.5f, 0.5f, w - 1f, h - 1f, arc - 1, arc - 1));
        g.dispose();
        return img;
    }
}
