/*
 * Copyright (C) 2021-2026 GummerCraft <http://gummercraft.fun>
 */

package com.gummer.launcher;

import com.gummer.launcher.ui2.Ui;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;

/**
 * Редактор розкладки сцени (редизайн 2, 16.11.2026), вмикається кодом ↑↑↓↓←→←→BA (KonamiCode; спершу Ctrl+Shift+E, потім F2, яку перехоплює оверлей AMD Radeon). Прозорий шар лише над сценою збірки
 * (список збірок і панель запуску лишаються клікабельні): тягни мишею логотип або фон, коліщатко над ним міняє розмір.
 * Навколо логотипа і фону тонкі рамки, внизу сцени підказка з поточними числами. C копіює значення збірки в буфер,
 * R скидає її правки. Правки зберігає {@link FlatBackgroundPanel2} (frame-edit/frame_&lt;ключ&gt;.properties).
 */
final class FrameEditor extends JComponent {

    private final FlatBackgroundPanel2 panel;
    private Point last;
    private Boolean dragLogo;
    private Boolean hoverLogo;
    private String message;
    private final Timer messageTimer;

    FrameEditor(FlatBackgroundPanel2 panel) {
        this.panel = panel;
        setOpaque(false);
        setFocusable(false);
        messageTimer = new Timer(2500, e -> {
            message = null;
            repaint();
        });
        messageTimer.setRepeats(false);
        MouseAdapter mouse = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (!SwingUtilities.isLeftMouseButton(e)) return;
                last = toPanel(e.getPoint());
                dragLogo = overLogo(last);
                setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (last == null || dragLogo == null) return;
                Point p = toPanel(e.getPoint());
                int dx = p.x - last.x, dy = p.y - last.y;
                if (dx == 0 && dy == 0) return;
                // фон тягнеться «за картинку»: рух миші праворуч зсуває картинку праворуч
                panel.editMove(dragLogo, dx, dy);
                last = p;
                repaint();
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                last = null;
                dragLogo = null;
                updateHover(toPanel(e.getPoint()));
            }

            @Override
            public void mouseMoved(MouseEvent e) {
                updateHover(toPanel(e.getPoint()));
            }

            @Override
            public void mouseWheelMoved(MouseWheelEvent e) {
                panel.editScale(overLogo(toPanel(e.getPoint())), e.getWheelRotation());
                repaint();
            }
        };
        addMouseListener(mouse);
        addMouseMotionListener(mouse);
        addMouseWheelListener(mouse);
    }

    void setMessage(String text) {
        message = text;
        if (text != null) messageTimer.restart();
        repaint();
    }

    /** Шар стоїть рівно над сценою панелі (у координатах шару вікна). */
    void syncBounds() {
        Container layered = getParent();
        if (layered == null || !panel.isShowing() && !panel.isDisplayable()) return;
        Rectangle scene = panel.sceneRect();
        setBounds(SwingUtilities.convertRectangle(panel, scene, layered));
        repaint();
    }

    private Point toPanel(Point p) {
        return SwingUtilities.convertPoint(this, p, panel);
    }

    private boolean overLogo(Point panelPoint) {
        Rectangle l = panel.editLogoRect();
        return l != null && l.contains(panelPoint);
    }

    private void updateHover(Point panelPoint) {
        boolean logo = overLogo(panelPoint);
        if (hoverLogo == null || hoverLogo != logo) {
            hoverLogo = logo;
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            repaint();
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = Ui.smooth(g);
        try {
            Rectangle scene = panel.sceneRect();
            // з координат панелі в координати шару
            g2.translate(-scene.x, -scene.y);
            g2.clipRect(scene.x, scene.y, scene.width, scene.height);

            Stroke dash = new BasicStroke(1.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, new float[]{6f, 4f}, 0f);
            Rectangle b = panel.editBgRect();
            g2.setStroke(dash);
            g2.setColor(Boolean.FALSE.equals(hoverLogo) || Boolean.FALSE.equals(dragLogo) ? Ui.WARN : Ui.alpha(Color.WHITE, 150));
            Rectangle bv = b.intersection(new Rectangle(scene.x + 2, scene.y + 2, scene.width - 4, scene.height - 4));
            g2.drawRect(bv.x, bv.y, bv.width - 1, bv.height - 1);
            Rectangle l = panel.editLogoRect();
            if (l != null) {
                g2.setColor(Boolean.TRUE.equals(hoverLogo) || Boolean.TRUE.equals(dragLogo) ? Ui.WARN : Ui.ACCENT_TEXT);
                g2.drawRect(l.x - 2, l.y - 2, l.width + 3, l.height + 3);
            }

            // підказка внизу сцени
            String key = panel.editKey();
            String line1 = key == null ? "Редактор розкладки: спершу вибери збірку ліворуч"
                    : "Редактор розкладки, " + key + ":   " + panel.editSummary();
            String line2 = message != null ? message
                    : "Тягни логотип або фон мишею, коліщатко міняє розмір.   C скопіювати значення   R скинути збірку   Esc вийти";
            Font f1 = Ui.font(Font.BOLD, 13f), f2 = Ui.font(Font.PLAIN, 12f);
            FontMetrics m1 = g2.getFontMetrics(f1), m2 = g2.getFontMetrics(f2);
            int w = Math.min(scene.width - 32, Math.max(m1.stringWidth(line1), m2.stringWidth(line2)) + 32);
            int h = 56;
            int x = scene.x + (scene.width - w) / 2, y = scene.y + scene.height - h - 16;
            g2.setColor(Ui.alpha(Ui.BG, 225));
            g2.fillRoundRect(x, y, w, h, 12, 12);
            g2.setColor(Ui.alpha(Ui.WARN, 200));
            g2.setStroke(new BasicStroke(1f));
            g2.drawRoundRect(x, y, w - 1, h - 1, 12, 12);
            g2.setFont(f1);
            g2.setColor(Ui.TEXT);
            g2.drawString(Ui.fit(line1, m1, w - 32), x + 16, y + 22);
            g2.setFont(f2);
            g2.setColor(message != null ? Ui.WARN : Ui.TEXT_2);
            g2.drawString(Ui.fit(line2, m2, w - 32), x + 16, y + 42);
        } finally {
            g2.dispose();
        }
    }
}
