/*
 * Copyright (C) 2021-2026 GummerCraft <http://gummercraft.fun>
 */

package com.gummer.launcher.ui2;

import javax.swing.*;
import java.awt.*;

/**
 * Редизайн 2: головна кнопка вікна. Єдина суцільно зелена річ на екрані. Підпис каже, що саме станеться
 * («Грати», «Встановити», «Оновити»), а дрібний рядок під ним, з чим (версія). Нижній темний край на 4 px робить її
 * «блоком», що натискається: при натисканні кнопка осідає на 2 px. Вимкнена (нічого не вибрано): сіра.
 */
public class PlayButton extends JButton {

    private String caption;

    public PlayButton() {
        setContentAreaFilled(false);
        setBorderPainted(false);
        setFocusPainted(false);
        setOpaque(false);
        setRolloverEnabled(true);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        setFont(Ui.font(Font.BOLD, 22f));
    }

    public void setLabel(String label, String caption) {
        setText(label);
        this.caption = caption;
        repaint();
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(232, 68);
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = Ui.smooth(g);
        try {
            ButtonModel m = getModel();
            boolean enabled = isEnabled();
            boolean hover = enabled && m.isRollover();
            boolean pressed = enabled && m.isArmed() && m.isPressed();
            int w = getWidth(), h = getHeight(), arc = Ui.RADIUS;
            int edge = pressed ? 2 : 4;
            int top = pressed ? 2 : 0;

            Color face = !enabled ? Ui.SURFACE_3 : pressed ? Ui.ACCENT_PRESSED : hover ? Ui.ACCENT_HOVER : Ui.ACCENT;
            Color bottom = !enabled ? Ui.SURFACE_2 : Ui.ACCENT_EDGE;
            g2.setColor(bottom);
            g2.fillRoundRect(0, top, w, h - top, arc, arc);
            g2.setColor(face);
            g2.fillRoundRect(0, top, w, h - top - edge, arc, arc);
            // тонка світла лінія згори: грань блоку
            if (enabled) {
                g2.setColor(Ui.alpha(Color.WHITE, hover ? 60 : 40));
                g2.fillRect(arc / 2, top + 1, w - arc, 1);
            }

            Color fg = enabled ? Color.WHITE : Ui.TEXT_3;
            String label = getText() == null ? "" : getText().toUpperCase(java.util.Locale.ROOT);
            Font big = getFont();
            Font small = Ui.font(Font.PLAIN, 12f);
            FontMetrics fb = g2.getFontMetrics(big);
            FontMetrics fs = g2.getFontMetrics(small);
            boolean twoLines = caption != null && !caption.isEmpty();
            int iconSize = 18;
            int labelW = fb.stringWidth(label);
            int contentW = iconSize + Ui.S3 + labelW;
            int x = (w - contentW) / 2;
            int faceH = h - top - edge;
            int blockH = fb.getAscent() + (twoLines ? fs.getHeight() : 0);
            int baseline = top + (faceH - blockH) / 2 + fb.getAscent() - 1;
            Glyph.PLAY.paint(g2, x, baseline - fb.getAscent() + (fb.getAscent() - iconSize) / 2 + 2, iconSize, fg);
            g2.setFont(big);
            g2.setColor(fg);
            g2.drawString(label, x + iconSize + Ui.S3, baseline);
            if (twoLines) {
                g2.setFont(small);
                g2.setColor(enabled ? Ui.alpha(Color.WHITE, 215) : Ui.TEXT_3);
                String c = Ui.fit(caption, fs, w - Ui.S4 * 2);
                g2.drawString(c, (w - fs.stringWidth(c)) / 2, baseline + fs.getHeight());
            }
            if (isFocusOwner()) Ui.focusRing(g2, w, h, arc);
        } finally {
            g2.dispose();
        }
    }
}
