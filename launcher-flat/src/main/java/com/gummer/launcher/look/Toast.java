/*
 * Copyright (C) 2021-2026 GummerCraft <http://gummercraft.fun>
 */

package com.gummer.launcher.look;

import com.gummer.launcher.popup.Notification;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Path2D;
import java.util.ArrayList;
import java.util.List;

/**
 * 16.11.2026: сповіщення в стилі вигляду лаунчера замість старого {@code popup.Notification} (світла плашка
 * окремим вікном, ширина «текст x 2.38», вилазила за край вікна і закривала верхні кнопки).
 *
 * <p>Тепер це шар усередині вікна (POPUP_LAYER), рухається разом з ним, ширина не більша за вікно, текст
 * переноситься. Місце: якщо вікно вказало верхню панель ({@link #BELOW}), під нею праворуч; інакше по центру
 * області праворуч від бічної панелі ({@link #LEFT}), згори. Зникає за 6 с (довгі за 9), під мишкою чекає,
 * клік закриває. Кілька підряд стають стовпчиком.</p>
 */
public final class Toast extends JComponent {

    /** Клієнтська властивість rootPane: компонент (верхня панель), під яким з'являються сповіщення. */
    public static final String BELOW = "gc.toastBelow";
    /** Клієнтська властивість rootPane: компонент, над яким сповіщення стають стовпчиком (ліворуч по ньому). */
    public static final String ABOVE = "gc.toastAbove";
    /** Клієнтська властивість rootPane: ліва межа області (ширина бічної панелі), Integer. */
    public static final String LEFT = "gc.toastLeft";

    // MAX_W 520 (було 440): текст, розбитий на рядки в lang (сповіщення підтримки), влазить у два рядки без переносу всередині
    private static final int GAP = 10, MAX_W = 520;

    private final JRootPane root;
    private final Notification.Type type;
    private final String title;
    private final JTextArea text = new JTextArea();
    private final LookTheme.Palette p = LookTheme.palette();
    private float alpha;
    private int slide = 8;
    private boolean hover;
    private boolean closing;
    private Timer life;
    private final ComponentAdapter relayout = new ComponentAdapter() {
        @Override
        public void componentResized(ComponentEvent e) {
            layoutAll(root);
        }
    };

    /** @return false, якщо вікна не видно (тоді показує старе сповіщення) */
    public static boolean show(Frame frame, Notification.Type type, String message, String title) {
        if (!(frame instanceof RootPaneContainer) || !frame.isShowing()) return false;
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(() -> show(frame, type, message, title));
            return true;
        }
        JRootPane root = ((RootPaneContainer) frame).getRootPane();
        Toast t = new Toast(root, type == null ? Notification.Type.INFO : type, message,
                title == null || "none".equals(title) ? null : title);
        root.getLayeredPane().add(t, JLayeredPane.POPUP_LAYER);
        layoutAll(root);
        t.start();
        return true;
    }

    private Toast(JRootPane root, Notification.Type type, String message, String title) {
        this.root = root;
        this.type = type;
        this.title = title;
        setLayout(null);
        setOpaque(false);
        text.setText(message == null ? "" : LookUi.stripHtml(message));
        text.setLineWrap(true);
        text.setWrapStyleWord(true);
        text.setEditable(false);
        text.setFocusable(false);
        text.setOpaque(false);
        text.setBorder(null);
        text.setForeground(p.text);
        text.setFont(UIManager.getFont("Label.font").deriveFont(title == null ? Font.BOLD : Font.PLAIN, 13.5f));
        add(text);
        MouseAdapter m = new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                hover = true;
            }

            @Override
            public void mouseExited(MouseEvent e) {
                hover = false;
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                close();
            }
        };
        addMouseListener(m);
        text.addMouseListener(m);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        text.setCursor(getCursor());
        root.addComponentListener(relayout);
    }

    private void start() {
        if (LookTheme.LITE) { // спрощений режим: одразу на місці, без появи
            alpha = 1f;
            slide = 0;
            layoutAll(root);
        }
        Timer in = new Timer(16, null);
        final long t0 = System.currentTimeMillis();
        in.addActionListener(e -> {
            float f = Math.min(1f, (System.currentTimeMillis() - t0) / 180f);
            alpha = f;
            slide = Math.round(8 * (1 - f) * (1 - f));
            layoutAll(root);
            if (f >= 1f) in.stop();
        });
        if (!LookTheme.LITE) in.start();
        int ms = text.getText().length() > 90 ? 9000 : 6000;
        life = new Timer(ms, e -> {
            if (hover) {
                life.restart();
            } else {
                close();
            }
        });
        life.setRepeats(false);
        life.start();
    }

    private void close() {
        if (closing) return;
        closing = true;
        if (life != null) life.stop();
        Timer out = new Timer(16, null);
        final long t0 = System.currentTimeMillis();
        out.addActionListener(e -> {
            float f = LookTheme.LITE ? 1f : Math.min(1f, (System.currentTimeMillis() - t0) / 160f);
            alpha = 1 - f;
            repaint();
            if (f >= 1f) {
                out.stop();
                root.removeComponentListener(relayout);
                Container parent = getParent();
                if (parent != null) {
                    parent.remove(this);
                    parent.repaint();
                }
                layoutAll(root);
            }
        });
        out.start();
    }

    /** Розставити всі сповіщення вікна стовпчиком. */
    private static void layoutAll(JRootPane root) {
        JLayeredPane lp = root.getLayeredPane();
        List<Toast> toasts = new ArrayList<Toast>();
        for (Component c : lp.getComponentsInLayer(JLayeredPane.POPUP_LAYER)) {
            if (c instanceof Toast) toasts.add(0, (Toast) c); // старі вгорі
        }
        if (toasts.isEmpty()) return;
        int lw = lp.getWidth();
        Object above = root.getClientProperty(ABOVE);
        if (above instanceof Component && ((Component) above).isShowing()) {
            // Liquid Glass: над рядом кнопок унизу сцени, там вільне тло (згори лого збірки)
            Component a = (Component) above;
            Rectangle r = SwingUtilities.convertRectangle(a.getParent(), a.getBounds(), lp);
            int maxW = Math.max(220, Math.min(MAX_W, lw - r.x - 16));
            List<Dimension> sizes = new ArrayList<Dimension>();
            int total = 0;
            for (Toast t : toasts) {
                Dimension d = t.measure(maxW);
                sizes.add(d);
                total += d.height + 8;
            }
            int y = r.y - GAP - total + 8;
            for (int i = 0; i < toasts.size(); i++) {
                Toast t = toasts.get(i);
                Dimension d = sizes.get(i);
                t.setBounds(Math.max(8, r.x), Math.max(8, y + t.slide), d.width, d.height);
                t.doLayoutInside();
                t.repaint();
                y += d.height + 8;
            }
            lp.revalidate();
            lp.repaint();
            return;
        }
        Object below = root.getClientProperty(BELOW);
        Object leftProp = root.getClientProperty(LEFT);
        int areaLeft = leftProp instanceof Integer ? (Integer) leftProp : 0;
        int y, rightEdge = -1;
        if (below instanceof Component && ((Component) below).isShowing()) {
            Component b = (Component) below;
            Rectangle r = null; // панель: межі її видимих кнопок (поля MigLayout не рахуються)
            if (b instanceof Container && ((Container) b).getComponentCount() > 0) {
                for (Component k : ((Container) b).getComponents()) {
                    if (!k.isVisible() || k.getWidth() == 0) continue;
                    Rectangle kr = SwingUtilities.convertRectangle(b, k.getBounds(), lp);
                    r = r == null ? kr : r.union(kr);
                }
            }
            if (r == null) r = SwingUtilities.convertRectangle(b.getParent(), b.getBounds(), lp);
            y = r.y + r.height + GAP;
            rightEdge = r.x + r.width;
        } else {
            y = 16;
        }
        for (Toast t : toasts) {
            int maxW = Math.max(220, Math.min(MAX_W, (rightEdge > 0 ? rightEdge : lw - areaLeft) - 32));
            Dimension d = t.measure(maxW);
            int x = rightEdge > 0 ? rightEdge - d.width : areaLeft + (lw - areaLeft - d.width) / 2;
            x = Math.max(8, Math.min(x, lw - d.width - 8));
            t.setBounds(x, y - t.slide, d.width, d.height);
            t.doLayoutInside();
            t.repaint();
            y += d.height + 8;
        }
        lp.revalidate();
        lp.repaint();
    }

    private int padL() {
        return 52;
    }

    private Dimension measure(int maxW) {
        FontMetrics fm = text.getFontMetrics(text.getFont());
        int natural = 0;
        for (String line : text.getText().split("\n")) natural = Math.max(natural, fm.stringWidth(line));
        int titleW = title == null ? 0 : getFontMetrics(titleFont()).stringWidth(title);
        int w = Math.min(maxW, Math.max(natural, titleW) + padL() + 44);
        text.setSize(w - padL() - 40, Short.MAX_VALUE);
        int th = text.getPreferredSize().height;
        int h = Math.max(52, th + (title == null ? 28 : 48));
        return new Dimension(w, h);
    }

    private Font titleFont() {
        return UIManager.getFont("Label.font").deriveFont(Font.BOLD, 13.5f);
    }

    private void doLayoutInside() {
        int th = text.getPreferredSize().height;
        int top = title == null ? (getHeight() - th) / 2 : 32;
        text.setBounds(padL(), top, getWidth() - padL() - 40, th);
    }

    private Color typeColor() {
        switch (type) {
            case SUCCESS:
                return p.success;
            case WARNING:
                return p.warn;
            case UPDATE:
                return p.accentText;
            default:
                return p.info;
        }
    }

    @Override
    public void paint(Graphics g) {
        if (alpha <= 0f) return;
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, Math.max(0f, Math.min(1f, alpha))));
        super.paint(g2);
        g2.dispose();
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        com.gummer.launcher.look.TextAA.apply(g2);
        int w = getWidth(), h = getHeight();
        int arc = p.glass() ? 18 : p.library() ? 16 : 10;
        // тінь
        for (int i = 4; i >= 1; i--) {
            g2.setColor(new Color(0, 0, 0, 14));
            g2.fillRoundRect(-i + 2, -i + 4, w + 2 * i - 4, h + 2 * i - 4, arc + i * 2, arc + i * 2);
        }
        Color c = typeColor();
        if (p.glass()) {
            // 20.11.2026: плашка і рамка по фізичних пікселях (Edge)
            Edge.pill(g2, 0, 0, w, h, arc, new Color(18, 20, 25, 236), null);
            Edge.pill(g2, 0, 0, w, h, arc, new GradientPaint(0, 0, new Color(255, 255, 255, 22), 0, h, new Color(255, 255, 255, 4)),
                    new Color(255, 255, 255, 46));
        } else if (p.library()) {
            Edge.pill(g2, 0, 0, w, h, arc, p.surface3, LookTheme.alpha(c, 90));
        } else {
            Edge.pill(g2, 0, 0, w, h, arc, p.surface2, p.line);
            g2.setColor(c);
            g2.fillRoundRect(0, 0, 4, h, 4, 4);
        }
        // значок типу
        int d = 26, ix = 16, iy = title == null ? (h - d) / 2 : 14;
        g2.setColor(LookTheme.alpha(c, p.glass() ? 60 : 48));
        g2.fillOval(ix, iy, d, d);
        g2.setColor(c);
        g2.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        int cx = ix + d / 2, cy = iy + d / 2;
        if (type == Notification.Type.SUCCESS) {
            Path2D ck = new Path2D.Float();
            ck.moveTo(cx - 5, cy);
            ck.lineTo(cx - 1.5, cy + 4);
            ck.lineTo(cx + 5.5, cy - 4);
            g2.draw(ck);
        } else if (type == Notification.Type.WARNING) {
            g2.drawLine(cx, cy - 6, cx, cy + 1);
            g2.fillOval(cx - 1, cy + 4, 3, 3);
        } else {
            g2.fillOval(cx - 1, cy - 7, 3, 3);
            g2.drawLine(cx, cy - 1, cx, cy + 6);
        }
        if (title != null) {
            g2.setFont(titleFont());
            g2.setColor(p.text);
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(title, padL(), 14 + (d - fm.getHeight()) / 2 + fm.getAscent());
        }
        // хрестик
        g2.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.setColor(hover ? p.text : p.text3);
        int xx = w - 22, xy = title == null ? h / 2 : 26;
        g2.drawLine(xx - 4, xy - 4, xx + 4, xy + 4);
        g2.drawLine(xx - 4, xy + 4, xx + 4, xy - 4);
        g2.dispose();
    }
}
