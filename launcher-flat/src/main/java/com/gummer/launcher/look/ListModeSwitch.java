/*
 * GummerCraft Launcher
 * Copyright (C) 2026 GummerCraft <http://gummercraft.fun>
 * Please see LICENSE.txt for license information.
 */

package com.gummer.launcher.look;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * 17.11.2026 (власник: перемикач лівого меню «АБО всі доступні, АБО всі встановлені»): дві вкладки над списком збірок,
 * «Усі» і «Встановлені». Малюється сам (без FlatLaf-кнопок), однаково в трьох виглядах, кольори з палітри вигляду.
 *
 * <p>20.11.2026 (власник: «поміняти їх місцями: зліва "Встановлені", справа "Усі"», «Усі» тепер «Додати інсталяцію»):
 * ліворуч (індекс 0) «Встановлені», праворуч (1) каталог. Половини не рівні, а за довжиною підписів, бо «Додати
 * інсталяцію» помітно довше за «Встановлені» і в рівній половині обрізалось би.</p>
 */
public final class ListModeSwitch extends JComponent {

    public interface Listener {
        void changed(boolean installedOnly);
    }

    private final String[] labels;
    private final Listener listener;
    private int selected;
    private int hover = -1;

    public ListModeSwitch(String all, String installed, boolean installedOnly, Listener listener) {
        this.labels = new String[]{installed, all};
        this.selected = installedOnly ? 0 : 1;
        this.listener = listener;
        setFont(UIManager.getFont("Label.font").deriveFont(Font.BOLD, 12f));
        setFocusable(true);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        MouseAdapter mouse = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                choose(e.getX() < split(getWidth()) ? 0 : 1);
            }

            @Override
            public void mouseMoved(MouseEvent e) {
                int h = e.getX() < split(getWidth()) ? 0 : 1;
                if (h != hover) {
                    hover = h;
                    repaint();
                }
            }

            @Override
            public void mouseExited(MouseEvent e) {
                hover = -1;
                repaint();
            }
        };
        addMouseListener(mouse);
        addMouseMotionListener(mouse);
        addFocusListener(new java.awt.event.FocusAdapter() {
            @Override
            public void focusGained(java.awt.event.FocusEvent e) {
                repaint();
            }

            @Override
            public void focusLost(java.awt.event.FocusEvent e) {
                repaint();
            }
        });
        addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_LEFT) choose(0);
                else if (e.getKeyCode() == KeyEvent.VK_RIGHT) choose(1);
                else if (e.getKeyCode() == KeyEvent.VK_SPACE || e.getKeyCode() == KeyEvent.VK_ENTER) choose(1 - selected);
            }
        });
    }

    public boolean isInstalledOnly() {
        return selected == 0;
    }

    /** Межа між вкладками для ширини w: пропорційно ширині підписів (з однаковим запасом по боках кожного). */
    private int split(int w) {
        FontMetrics fm = getFontMetrics(getFont());
        double a = fm.stringWidth(labels[0]) + 20, b = fm.stringWidth(labels[1]) + 20;
        return (int) Math.round(w * a / (a + b));
    }

    /** 17.11.2026: перемкнути з коду (рядок «+ Додати збірку»), як клац гравця. */
    public void setInstalledOnly(boolean installedOnly) {
        choose(installedOnly ? 0 : 1);
    }

    private void choose(int index) {
        if (index == selected) return;
        selected = index;
        repaint();
        if (listener != null) listener.changed(selected == 0);
    }

    @Override
    public Dimension getPreferredSize() {
        FontMetrics fm = getFontMetrics(getFont());
        // 17.11.2026 (власник: «"встановлені" трохи погано в кнопку залазять»): по 10 px запасу з боків тексту (більше
        // не влазить верхня панель Бібліотеки у найменшому вікні: «Змінити скін» виїжджала за край)
        int w = fm.stringWidth(labels[0]) + fm.stringWidth(labels[1]) + 2 * 20; // 20.11.2026: половини за довжиною підписів
        double z = Zoom.of(this); // 17.11.2026: збільшення в розгорнутому склі
        return new Dimension(Zoom.px(z, w), Zoom.px(z, 32));
    }

    @Override
    public Dimension getMinimumSize() {
        double z = Zoom.of(this);
        return new Dimension(Zoom.px(z, 80), Zoom.px(z, 32));
    }

    @Override
    protected void paintComponent(Graphics g) {
        LookTheme.Palette p = LookTheme.palette();
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        com.gummer.launcher.look.TextAA.apply(g2);
        double z = Zoom.of(this);
        g2.scale(z, z);
        int w = (int) Math.round(getWidth() / z), h = (int) Math.round(getHeight() / z);
        int arc = Math.min(h, Math.max(8, p.buttonArc));
        Color track = p.glass() ? new Color(0, 0, 0, 110) : p.surface2;
        Color knob = p.glass() ? new Color(255, 255, 255, 46) : p.surface3;
        Color hoverFill = p.glass() ? new Color(255, 255, 255, 18) : new Color(p.surface3.getRed(), p.surface3.getGreen(), p.surface3.getBlue(), 110);
        g2.setColor(track);
        g2.fillRoundRect(0, 0, w, h, arc, arc);
        int half = split(w);
        int x = selected == 0 ? 3 : half + 1;
        int kw = (selected == 0 ? half : w - half) - 4;
        if (hover >= 0 && hover != selected) {
            g2.setColor(hoverFill);
            int hx = hover == 0 ? 3 : half + 1;
            g2.fillRoundRect(hx, 3, (hover == 0 ? half : w - half) - 4, h - 6, arc - 4, arc - 4);
        }
        g2.setColor(knob);
        g2.fillRoundRect(x, 3, kw, h - 6, arc - 4, arc - 4);
        if (isFocusOwner() && FocusVisible.on()) {
            Edge.pill(g2, 0, 0, w, h, arc, null, new Color(p.accent.getRed(), p.accent.getGreen(), p.accent.getBlue(), 150));
        }
        g2.setFont(getFont());
        FontMetrics fm = g2.getFontMetrics();
        for (int i = 0; i < 2; i++) {
            int cx = i == 0 ? half / 2 + 1 : half + (w - half) / 2;
            String s = labels[i];
            int room = (i == 0 ? half : w - half) - 10;
            while (fm.stringWidth(s) > room && s.length() > 2) s = s.substring(0, s.length() - 2) + "…";
            g2.setColor(i == selected ? p.text : p.text3);
            g2.drawString(s, cx - fm.stringWidth(s) / 2, (h - fm.getHeight()) / 2 + fm.getAscent());
        }
        g2.dispose();
    }

}
