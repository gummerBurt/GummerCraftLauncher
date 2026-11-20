package com.gummer.launcher.dialog;

import com.skcraft.launcher.Launcher;
import com.skcraft.launcher.persistence.Persistence;
import com.skcraft.launcher.util.SharedLocale;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.HierarchyBoundsAdapter;
import java.awt.event.HierarchyEvent;
import java.awt.event.HierarchyListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.List;

/**
 * 17.11.2026: підказка-виноска зі стрілкою над кнопкою «Збірка» (вирішено: «кнопку налаштування збірки варто якось
 * захайлайтити»). Поки гравець жодного разу не відкрив вікно «Керування збіркою», кнопка має зелену рамку
 * ({@link #ATTENTION}), а через півтори секунди після старту над нею з'являється ця виноска. Клік по виносці
 * відкриває вікно, хрестик лише ховає її до наступного запуску. Після першого відкриття вікна прапорець
 * {@code manageTipSeen} у конфігу, і більше нічого не показується. Без анімації: малюється один раз, на FPS не впливає.
 */
public final class CoachMark extends JComponent {
    /** Клієнтська властивість кнопки: true малює зелену рамку уваги (Btn класики і бібліотеки, HotbarSlot скла). */
    public static final String ATTENTION = "gc.attention";
    public static final Color RING = new Color(0x5C, 0xC2, 0x3F);

    private static final int W = 300, PAD = 14, ARROW = 9, ARC = 14, CLOSE = 22;
    private static CoachMark current;

    private final JComponent target;
    private final String title, text, hint;
    private final Font titleFont, textFont, hintFont;
    private List<String> lines = new ArrayList<String>();
    private boolean below;
    private int arrowX;
    private boolean overClose;

    private CoachMark(JComponent target, String title, String text, String hint) {
        this.target = target;
        this.title = title;
        this.text = text;
        this.hint = hint;
        Font base = UIManager.getFont("Label.font");
        if (base == null) base = new Font(Font.SANS_SERIF, Font.PLAIN, 12);
        titleFont = base.deriveFont(Font.BOLD, 14f);
        textFont = base.deriveFont(Font.PLAIN, 12.5f);
        hintFont = base.deriveFont(Font.BOLD, 12f);
        setOpaque(false);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        MouseAdapter mouse = new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (inClose(e.getPoint())) {
                    close();
                } else {
                    close();
                    if (CoachMark.this.target instanceof AbstractButton) ((AbstractButton) CoachMark.this.target).doClick();
                }
            }

            @Override
            public void mouseMoved(MouseEvent e) {
                boolean over = inClose(e.getPoint());
                if (over != overClose) {
                    overClose = over;
                    repaint();
                }
            }

            @Override
            public void mouseExited(MouseEvent e) {
                if (overClose) {
                    overClose = false;
                    repaint();
                }
            }
        };
        addMouseListener(mouse);
        addMouseMotionListener(mouse);
    }

    /** Рамка на кнопці і (з затримкою) виноска, якщо вікно збірки ще ні разу не відкривали. */
    public static void manageTip(final RootPaneContainer frame, final Launcher launcher, final JComponent button) {
        if (launcher == null || button == null || launcher.getConfig().isManageTipSeen()) return;
        button.putClientProperty(ATTENTION, Boolean.TRUE);
        button.repaint();
        // кожні пів секунди: чекаємо, поки вікно покажеться, і ще секунду після того (до 20 с, далі не набридаємо)
        final int[] tries = {0, 0};
        final Timer t = new Timer(500, null);
        t.addActionListener(e -> {
            if (launcher.getConfig().isManageTipSeen() || ++tries[0] > 40) {
                t.stop();
                return;
            }
            if (!button.isShowing()) {
                tries[1] = 0;
                return;
            }
            if (++tries[1] < 3) return;
            t.stop();
            show(frame, button, SharedLocale.tr("manage.tip.title"), SharedLocale.tr("manage.tip.text"), SharedLocale.tr("manage.tip.open"));
        });
        t.start();
    }

    /** Вікно збірки відкрили: рамку і виноску прибрати назавжди. */
    public static void manageSeen(Launcher launcher, JComponent button) {
        if (button != null && button.getClientProperty(ATTENTION) != null) {
            button.putClientProperty(ATTENTION, null);
            button.repaint();
        }
        if (current != null) current.close();
        if (launcher != null && !launcher.getConfig().isManageTipSeen()) {
            launcher.getConfig().setManageTipSeen(true);
            Persistence.commitAndForget(launcher.getConfig());
        }
    }

    /** Рамка уваги для paintComponent кнопок: зелений контур з тим самим заокругленням. */
    public static void paintRing(Graphics2D g, JComponent c, int arc) {
        if (c.getClientProperty(ATTENTION) == null) return;
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            g2.setColor(RING);
            g2.setStroke(new BasicStroke(2f));
            g2.draw(new RoundRectangle2D.Float(1f, 1f, c.getWidth() - 2f, c.getHeight() - 2f, arc, arc));
        } finally {
            g2.dispose();
        }
    }

    private static void show(RootPaneContainer frame, JComponent target, String title, String text, String hint) {
        if (current != null) current.close();
        final JLayeredPane layers = frame.getLayeredPane();
        final CoachMark tip = new CoachMark(target, title, text, hint);
        current = tip;
        layers.add(tip, JLayeredPane.POPUP_LAYER);
        tip.place();
        final ComponentAdapter onResize = new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                tip.place();
            }

            @Override
            public void componentMoved(ComponentEvent e) {
                tip.place();
            }
        };
        layers.addComponentListener(onResize);
        target.addComponentListener(onResize);
        target.addHierarchyBoundsListener(new HierarchyBoundsAdapter() {
            @Override
            public void ancestorMoved(HierarchyEvent e) {
                tip.place();
            }

            @Override
            public void ancestorResized(HierarchyEvent e) {
                tip.place();
            }
        });
        // 17.11.2026: кнопка зникла разом з батьком (сторінка Modrinth ховає «Збірку» і «Запуск»): виноска теж ховається
        // і повертається з кнопкою (раніше лишалась висіти над сторінкою Modrinth, вказуючи в порожнечу)
        final HierarchyListener onShowing = e -> {
            if ((e.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0) SwingUtilities.invokeLater(tip::place);
        };
        target.addHierarchyListener(onShowing);
        tip.putClientProperty("gc.cleanup", (Runnable) () -> {
            layers.removeComponentListener(onResize);
            target.removeComponentListener(onResize);
            target.removeHierarchyListener(onShowing);
        });
    }

    private void close() {
        Container parent = getParent();
        Object cleanup = getClientProperty("gc.cleanup");
        if (cleanup instanceof Runnable) ((Runnable) cleanup).run();
        if (parent != null) {
            Rectangle r = getBounds();
            parent.remove(this);
            parent.repaint(r.x, r.y, r.width, r.height);
        }
        if (current == this) current = null;
    }

    /** Над кнопкою, якщо там є місце, інакше під нею; стрілка на центр кнопки. */
    private void place() {
        Container layers = getParent();
        if (layers == null) return;
        if (!target.isShowing()) {
            setVisible(false);
            return;
        }
        FontMetrics tm = getFontMetrics(textFont);
        lines = wrap(text, tm, W - 2 * PAD);
        int h = bodyHeight() + ARROW;
        Point p = SwingUtilities.convertPoint(target.getParent(), target.getLocation(), layers);
        int cx = p.x + target.getWidth() / 2;
        int x = Math.max(8, Math.min(cx - W / 2, layers.getWidth() - W - 8));
        below = p.y - h - 2 < 8;
        int y = below ? p.y + target.getHeight() + 2 : p.y - h - 2;
        arrowX = Math.max(ARC + ARROW, Math.min(cx - x, W - ARC - ARROW));
        setBounds(x, y, W, h);
        setVisible(true);
        revalidate();
        repaint();
    }

    private int bodyHeight() {
        FontMetrics t = getFontMetrics(titleFont), b = getFontMetrics(textFont), hm = getFontMetrics(hintFont);
        return PAD + t.getHeight() + 6 + lines.size() * b.getHeight() + 8 + hm.getHeight() + PAD;
    }

    private boolean inClose(Point p) {
        int top = below ? ARROW : 0;
        return p.x >= W - CLOSE - 6 && p.y >= top + 4 && p.y <= top + CLOSE + 6;
    }

    private static List<String> wrap(String s, FontMetrics fm, int width) {
        List<String> out = new ArrayList<String>();
        StringBuilder line = new StringBuilder();
        for (String word : s.split(" ")) {
            String t = line.length() == 0 ? word : line + " " + word;
            if (fm.stringWidth(t) > width && line.length() > 0) {
                out.add(line.toString());
                line.setLength(0);
                line.append(word);
            } else {
                line.setLength(0);
                line.append(t);
            }
        }
        if (line.length() > 0) out.add(line.toString());
        return out;
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            com.gummer.launcher.look.TextAA.apply(g2);
            g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            int bh = getHeight() - ARROW;
            int top = below ? ARROW : 0;
            // бульбашка зі стрілкою одним контуром
            Path2D.Float shape = new Path2D.Float();
            shape.append(new RoundRectangle2D.Float(0.75f, top + 0.75f, W - 1.5f, bh - 1.5f, ARC, ARC), false);
            Path2D.Float arrow = new Path2D.Float();
            if (below) {
                arrow.moveTo(arrowX - ARROW, top + 1);
                arrow.lineTo(arrowX, 0.75f);
                arrow.lineTo(arrowX + ARROW, top + 1);
            } else {
                arrow.moveTo(arrowX - ARROW, bh - 1);
                arrow.lineTo(arrowX, getHeight() - 0.75f);
                arrow.lineTo(arrowX + ARROW, bh - 1);
            }
            arrow.closePath();
            java.awt.geom.Area area = new java.awt.geom.Area(shape);
            area.add(new java.awt.geom.Area(arrow));
            g2.setColor(new Color(0x1B, 0x1E, 0x22, 250));
            g2.fill(area);
            g2.setColor(RING);
            g2.setStroke(new BasicStroke(1.5f));
            g2.draw(area);

            int y = top + PAD;
            FontMetrics tm = g2.getFontMetrics(titleFont);
            g2.setFont(titleFont);
            g2.setColor(Color.WHITE);
            g2.drawString(title, PAD, y + tm.getAscent());
            y += tm.getHeight() + 6;
            FontMetrics bm = g2.getFontMetrics(textFont);
            g2.setFont(textFont);
            g2.setColor(new Color(0xC8, 0xCD, 0xD4));
            for (String l : lines) {
                g2.drawString(l, PAD, y + bm.getAscent());
                y += bm.getHeight();
            }
            y += 8;
            FontMetrics hm = g2.getFontMetrics(hintFont);
            g2.setFont(hintFont);
            g2.setColor(RING);
            g2.drawString(hint + "  →", PAD, y + hm.getAscent());

            // хрестик
            int cx = W - PAD - 5, cy = top + PAD + 5;
            g2.setColor(overClose ? Color.WHITE : new Color(0x8A, 0x90, 0x99));
            g2.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.drawLine(cx - 5, cy - 5, cx + 5, cy + 5);
            g2.drawLine(cx + 5, cy - 5, cx - 5, cy + 5);
        } finally {
            g2.dispose();
        }
    }
}
