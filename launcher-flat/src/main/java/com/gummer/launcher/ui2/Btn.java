/*
 * Copyright (C) 2021-2026 GummerCraft <http://gummercraft.fun>
 */

package com.gummer.launcher.ui2;

import javax.swing.*;
import java.awt.*;

/**
 * Редизайн 2: другорядна кнопка з іконкою і підписом (або лише іконкою, тоді підпис у підказці).
 * Справжня JButton: клавіатура (Tab, пробіл), фокус, підказки і слухачі як у звичайної кнопки.
 * Стани: звичайна, наведення, натиснута, фокус (зелена рамка), вимкнена (напівпрозора), позначка (червона крапка).
 */
public class Btn extends JButton {

    public enum Style {
        /** Прозора на фоні сцени чи панелі, підсвічується при наведенні: навігація вгорі. */
        GHOST,
        /** Заповнена поверхня: кнопки в рейці. */
        TONAL,
        /** Зелений контур: помітна, але не головна дія. */
        OUTLINE,
        /** Суцільний зелений (як «Грати»): лише якщо дія справді головна. */
        ACCENT
    }

    private final Style style;
    private Glyph glyph;
    private int badge;
    /** Постійне місце під значок справа (кнопка «Підтримка»): значок не налазить на текст і ширина не стрибає. */
    private int badgeSpace;
    private int height = 36;
    private boolean onScene;

    public Btn(String text, Glyph glyph, Style style) {
        super(text);
        this.glyph = glyph;
        this.style = style;
        setContentAreaFilled(false);
        setBorderPainted(false);
        setFocusPainted(false);
        setOpaque(false);
        setRolloverEnabled(true);
        setFont(Ui.font(Font.BOLD, 13f));
        setForeground(Ui.TEXT);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }

    /** Кнопка над картинкою сцени: під текстом легка тінь, щоб читався на світлому фоні. */
    public Btn onScene() {
        onScene = true;
        return this;
    }

    /**
     * 22.11.2026: підпис з кількох слів у два рядки (розрив на першому пробілі), щоб довга назва («Налаштування
     * збірки») не забирала ширину в сусідів. Кнопка має бути заввишки під два рядки (44).
     */
    public Btn twoLines() {
        twoLines = true;
        revalidate();
        return this;
    }

    private boolean twoLines;

    private String[] lines() {
        String t = getText();
        int sp = twoLines && t != null ? t.indexOf(' ') : -1;
        return sp > 0 ? new String[]{t.substring(0, sp), t.substring(sp + 1)} : new String[]{t};
    }

    private int textWidth(Font f) {
        int tw = 0;
        for (String l : lines()) tw = Math.max(tw, com.gummer.launcher.look.TextFit.widest(f, l));
        return tw;
    }

    public Btn height(int h) {
        height = h;
        return this;
    }

    public void setBadge(boolean badge) {
        setBadge(badge ? 1 : 0);
    }

    /** 16.11.2026: зарезервувати місце під значок непрочитаних (для кнопки «Підтримка»). */
    public Btn reserveBadge() {
        badgeSpace = 12;
        revalidate();
        return this;
    }

    /** 16.11.2026: число непрочитаних відповідей підтримки (0: значка немає). Ширина кнопки від нього не залежить. */
    public void setBadge(int count) {
        if (badge != count) {
            badge = count;
            repaint();
        }
    }

    public void setGlyph(Glyph glyph) {
        this.glyph = glyph;
        repaint();
    }

    private boolean iconOnly() {
        return getText() == null || getText().isEmpty();
    }

    @Override
    public Dimension getPreferredSize() {
        if (isPreferredSizeSet()) return super.getPreferredSize();
        if (iconOnly()) return new Dimension(height, height);
        FontMetrics fm = getFontMetrics(getFont());
        int w = Ui.S3 + (glyph != null ? 18 + Ui.S2 : 0) + textWidth(fm.getFont()) + Ui.S3 + badgeSpace; // 22.11.2026: з запасом на масштаб Windows
        return new Dimension(w, height);
    }

    @Override
    public Dimension getMinimumSize() {
        return getPreferredSize();
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = Ui.smooth(g);
        try {
            ButtonModel m = getModel();
            boolean enabled = isEnabled();
            boolean hover = enabled && m.isRollover();
            boolean pressed = enabled && m.isArmed() && m.isPressed();
            if (!enabled) g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.4f));
            int w = getWidth(), h = getHeight(), arc = Ui.RADIUS;

            Color fill = null;
            Color stroke = null;
            Color fg = Ui.TEXT;
            switch (style) {
                case GHOST:
                    fill = pressed ? Ui.alpha(Color.WHITE, 46) : hover ? Ui.alpha(Color.WHITE, 28) : null;
                    break;
                case TONAL:
                    fill = pressed ? Ui.SURFACE_3.darker() : hover ? Ui.SURFACE_3 : Ui.SURFACE_2;
                    break;
                case OUTLINE:
                    fill = pressed ? Ui.alpha(Ui.ACCENT_HOVER, 70) : hover ? Ui.alpha(Ui.ACCENT_HOVER, 40) : null;
                    stroke = Ui.ACCENT_HOVER;
                    break;
                case ACCENT:
                    fill = pressed ? Ui.ACCENT_PRESSED : hover ? Ui.ACCENT_HOVER : Ui.ACCENT;
                    break;
                default:
                    break;
            }
            // над картинкою сцени: темна підкладка, щоб текст читався на світлому фоні
            if (onScene && fill == null) fill = Ui.alpha(Color.BLACK, 110);
            if (fill != null) {
                g2.setColor(fill);
                g2.fillRoundRect(0, 0, w, h, arc, arc);
            }
            if (stroke != null) {
                g2.setColor(stroke);
                g2.setStroke(new BasicStroke(1.5f));
                g2.drawRoundRect(1, 1, w - 2, h - 2, arc, arc);
            }
            paintExtra(g2, w, h);

            int dy = pressed ? 1 : 0;
            FontMetrics fm = g2.getFontMetrics(getFont());
            if (iconOnly()) {
                if (glyph != null) glyph.paint(g2, (w - 18) / 2, (h - 18) / 2 + dy, 18, fg);
            } else {
                int x = Ui.S3;
                String[] ls = lines();
                int lw = 0;
                for (String l : ls) lw = Math.max(lw, fm.stringWidth(l));
                int contentW = (glyph != null ? 18 + Ui.S2 : 0) + lw;
                if (getHorizontalAlignment() == CENTER || w > getPreferredSize().width + Ui.S2) x = (w - badgeSpace - contentW) / 2;
                if (glyph != null) {
                    glyph.paint(g2, x, (h - 18) / 2 + dy, 18, fg);
                    x += 18 + Ui.S2;
                }
                g2.setFont(getFont());
                int lineH = fm.getHeight() - 2;
                int ty = (h - lineH * ls.length) / 2 + fm.getAscent() - 1 + dy;
                g2.setColor(fg);
                for (int i = 0; i < ls.length; i++) {
                    g2.drawString(Ui.fit(ls[i], fm, w - x - Ui.S2 - badgeSpace), x, ty + i * lineH);
                }
            }
            // нові відповіді підтримки: червоний значок з числом у правому верхньому куті
            com.gummer.launcher.dialog.SupportUnread.paint(g2, badge, w - 2, 2, Ui.BG);
            if (isFocusOwner()) Ui.focusRing(g2, w, h, arc);
            com.gummer.launcher.dialog.CoachMark.paintRing(g2, this, arc);
        } finally {
            g2.dispose();
        }
    }

    /** Для підкласів: малювання між фоном і вмістом. */
    protected void paintExtra(Graphics2D g2, int w, int h) {
    }
}
