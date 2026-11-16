/*
 * Copyright (C) 2021-2026 GummerCraft <http://gummercraft.fun>
 */

package com.gummer.launcher;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.*;

/**
 * Редактор розкладки (дизайн 1.35, власник 16.11.2026): код ↑↑↓↓←→←→BA (KonamiCode, до 16.11 F2) вмикає прозорий шар над сценою вибраної збірки.
 * Тягни логотип, жовтий напис або фон мишею; коліщатко над об'єктом міняє розмір, Shift+коліщатко над написом крутить його.
 * Правки зберігаються самі (FlatBackgroundPanel.editSave) у &lt;--dir&gt;/launcher/frame-edit/frame_&lt;збірка&gt;.properties.
 * Шар ловить мишу лише над сценою: список збірок і нижня панель працюють як звичайно, збірку можна перемикати.
 */
final class LayoutEditor extends JComponent {

    private static final int SIDEBAR_W = 220;
    /** Висота нижньої панелі (кнопки, версія, «Запуск»), куди шар не лізе. */
    private static final int BOTTOM_H = 150;

    private final FlatBackgroundPanel panel;
    private final JLabel info = new JLabel();
    private final Timer saveTimer;
    private String target;
    private Point last;
    private String hover;
    private String status = "";

    private LayoutEditor(FlatBackgroundPanel panel) {
        this.panel = panel;
        setLayout(null);
        setOpaque(false);

        JButton copy = button("Копіювати значення", e -> {
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(panel.editExport()), null);
            flash("Скопійовано: " + panel.editKey());
        });
        JButton reset = button("Скинути цю збірку", e -> {
            panel.editReset();
            flash("Скинуто: " + panel.editKey());
        });
        JButton done = button("Готово", e -> setVisible(false));
        info.setForeground(Color.WHITE);
        info.setFont(info.getFont().deriveFont(Font.BOLD, 12f));
        for (JComponent c : new JComponent[]{copy, reset, done, info}) add(c);

        saveTimer = new Timer(350, e -> {
            try {
                panel.editSave();
                status = "збережено";
            } catch (Exception ex) {
                status = "не вдалося зберегти: " + ex.getMessage();
            }
            updateInfo();
        });
        saveTimer.setRepeats(false);

        MouseAdapter mouse = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (!SwingUtilities.isLeftMouseButton(e)) return;
                target = hit(e.getPoint());
                last = e.getPoint();
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (target == null || last == null) return;
                int dx = e.getX() - last.x, dy = e.getY() - last.y;
                if (dx == 0 && dy == 0) return;
                panel.editMove(target, dx, dy);
                last = e.getPoint();
                changed();
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                target = null;
                last = null;
            }

            @Override
            public void mouseMoved(MouseEvent e) {
                String h = hit(e.getPoint());
                if (!java.util.Objects.equals(h, hover)) {
                    hover = h;
                    repaint();
                }
            }

            @Override
            public void mouseWheelMoved(MouseWheelEvent e) {
                String what = hit(e.getPoint());
                double rot = e.getPreciseWheelRotation();
                if ("splash".equals(what) && e.isShiftDown()) {
                    panel.editRotate(rot);
                } else {
                    panel.editScale(what, Math.pow(1.04, -rot));
                }
                changed();
            }
        };
        addMouseListener(mouse);
        addMouseMotionListener(mouse);
        addMouseWheelListener(mouse);
        setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));

        // поки шар відкритий, сцена перемальовується часто (рамки за написом, що «дихає»)
        new Timer(100, e -> {
            if (isVisible()) {
                updateInfo();
                repaint();
            }
        }).start();
    }

    private static JButton button(String text, ActionListener l) {
        JButton b = new JButton(text);
        b.setFocusable(false);
        b.addActionListener(l);
        return b;
    }

    private void flash(String s) {
        status = s;
        updateInfo();
    }

    private void changed() {
        status = "…";
        saveTimer.restart();
        updateInfo();
        repaint();
    }

    private void updateInfo() {
        info.setText(panel.editSummary() + (status.isEmpty() ? "" : "   (" + status + ")"));
    }

    /** Що під мишею: напис (найменший, тож першим), логотип, інакше фон. */
    private String hit(Point p) {
        Point q = SwingUtilities.convertPoint(this, p, panel);
        Rectangle s = panel.editSplashBounds();
        if (s != null && s.contains(q)) return "splash";
        if (panel.editLogoBounds().contains(q)) return "logo";
        return "background";
    }

    /** Миша ловиться лише над сценою (і над кнопками редактора), решта вікна працює. */
    @Override
    public boolean contains(int x, int y) {
        if (!isVisible()) return false;
        for (Component c : getComponents()) {
            if (c.getBounds().contains(x, y)) return true;
        }
        Point q = SwingUtilities.convertPoint(this, x, y, panel);
        return q.x >= SIDEBAR_W && q.y >= 0 && q.y < panel.getHeight() - BOTTOM_H;
    }

    @Override
    public void doLayout() {
        Point o = SwingUtilities.convertPoint(panel, SIDEBAR_W + 12, 10, this);
        int x = o.x, y = o.y;
        for (Component c : getComponents()) {
            if (c == info) continue;
            Dimension d = c.getPreferredSize();
            c.setBounds(x, y, d.width, d.height);
            x += d.width + 6;
        }
        Point b = SwingUtilities.convertPoint(panel, SIDEBAR_W + 12, panel.getHeight() - BOTTOM_H - 28, this);
        info.setBounds(b.x, b.y, getWidth() - b.x - 10, 22);
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            // рамка зони, яку можна редагувати
            Rectangle area = SwingUtilities.convertRectangle(panel,
                    new Rectangle(SIDEBAR_W, 0, panel.getWidth() - SIDEBAR_W, panel.getHeight() - BOTTOM_H), this);
            g2.setColor(new Color(255, 200, 0, 160));
            g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 1f, new float[]{8f, 6f}, 0f));
            g2.drawRect(area.x + 1, area.y + 1, area.width - 3, area.height - 3);
            // підкладка під кнопки і числа
            g2.setColor(new Color(0, 0, 0, 170));
            Rectangle ib = info.getBounds();
            g2.fillRoundRect(ib.x - 6, ib.y - 2, Math.min(ib.width + 12, info.getPreferredSize().width + 12), ib.height + 4, 8, 8);
            outline(g2, panel.editLogoBounds(), "logo");
            outline(g2, panel.editSplashBounds(), "splash");
        } finally {
            g2.dispose();
        }
    }

    private void outline(Graphics2D g2, Rectangle r, String what) {
        if (r == null) return;
        Rectangle q = SwingUtilities.convertRectangle(panel, r, this);
        boolean on = what.equals(hover) || what.equals(target);
        g2.setStroke(new BasicStroke(on ? 2f : 1f));
        g2.setColor(on ? new Color(255, 255, 85) : new Color(255, 255, 255, 140));
        g2.drawRect(q.x, q.y, q.width, q.height);
    }

    /**
     * Поставити редактор у вікно: код ↑↑↓↓←→←→BA вмикає і вимикає (KonamiCode; до 16.11.2026 F2, гравцям її не треба),
     * Esc і «Готово» закривають.
     */
    static void install(JFrame frame, FlatBackgroundPanel panel, java.io.File editDir) {
        panel.setEditDir(editDir);
        LayoutEditor editor = new LayoutEditor(panel);
        frame.setGlassPane(editor);
        editor.setVisible(false);
        KonamiCode.install(frame, () -> {
            editor.setVisible(!editor.isVisible());
            if (editor.isVisible()) {
                editor.status = "";
                editor.updateInfo();
                editor.revalidate();
                editor.doLayout();
            }
            editor.repaint();
        });
        JRootPane root = frame.getRootPane();
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "gc.layoutEditorClose");
        root.getActionMap().put("gc.layoutEditorClose", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (editor.isVisible()) editor.setVisible(false);
            }

            @Override
            public boolean isEnabled() {
                return editor.isVisible(); // поза редактором дія вимкнена, і Esc іде далі як завжди
            }
        });
    }
}
