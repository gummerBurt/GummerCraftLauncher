/*
 * Copyright (C) 2021-2026 GummerCraft <http://gummercraft.fun>
 */

package com.gummer.launcher.ui3;

import javax.swing.*;
import java.awt.*;

/**
 * Редизайн 3 (тест, 16.11.2026): спільні розміри, відступи, кольори і шрифти головного вікна.
 *
 * <p>Сітка: фон вибраної збірки на все вікно, верхня панель ({@link #TOPBAR_H}), полиця збірок унизу ({@link #SHELF_H}),
 * між ними герой: логотип, опис і дії ліворуч унизу, повідомлення праворуч. Відступи лише з кроку 4: 4, 8, 12, 16, 24, 32.</p>
 *
 * <p>Кольори: нейтральні темні поверхні і один акцент (зелений «Грати»), щоб головна дія була одна. Білий текст на
 * {@link #ACCENT} має контраст понад 4.5:1, другорядний {@link #TEXT_2} на {@link #SURFACE} понад 7:1,
 * найтьмяніший {@link #TEXT_3} понад 4.5:1.</p>
 */
public final class Ui {

    private Ui() {
    }

    // ---------------------------------------------------------------- сітка
    /** Верхня панель (спільнота, навігація, профіль) і полиця збірок унизу. */
    public static final int TOPBAR_H = 64;
    public static final int SHELF_H = 152;

    public static final int S1 = 4, S2 = 8, S3 = 12, S4 = 16, S5 = 24, S6 = 32;
    public static final int RADIUS = 8;

    // ---------------------------------------------------------------- кольори
    public static final Color BG = new Color(0x0F1013);
    public static final Color SURFACE = new Color(0x16171B);
    public static final Color SURFACE_2 = new Color(0x1F2126);
    public static final Color SURFACE_3 = new Color(0x292C33);
    public static final Color LINE = new Color(0x2A2D33);

    public static final Color TEXT = new Color(0xF2F3F5);
    public static final Color TEXT_2 = new Color(0xB0B5BD);
    public static final Color TEXT_3 = new Color(0x868C96);

    /** Зелений трави: лише головна дія (Грати) і позначка вибору. */
    public static final Color ACCENT = new Color(0x3C8A2A);
    public static final Color ACCENT_HOVER = new Color(0x45A031);
    public static final Color ACCENT_PRESSED = new Color(0x33781F);
    /** Нижній край кнопки «Грати»: «блок» на 4 px, натиснута кнопка осідає на нього. */
    public static final Color ACCENT_EDGE = new Color(0x245A17);
    /** Світліший зелений для тексту і рамок на темному (контраст понад 7:1). */
    public static final Color ACCENT_TEXT = new Color(0x8FD16F);

    public static final Color ONLINE = new Color(0x7FD36A);
    public static final Color WARN = new Color(0xE3B24F);
    public static final Color DANGER = new Color(0xEF5A50);
    public static final Color FOCUS = new Color(0x8FD16F);

    // ---------------------------------------------------------------- шрифт
    /** Системний шрифт FlatLaf (Segoe UI на Windows) потрібного розміру; розміри шкали: 11, 12, 13, 14, 18, 22. */
    public static Font font(int style, float size) {
        Font base = UIManager.getFont("Label.font");
        if (base == null) base = new Font(Font.SANS_SERIF, Font.PLAIN, 13);
        return base.deriveFont(style, size);
    }

    public static Graphics2D smooth(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        return g2;
    }

    public static Color alpha(Color c, int a) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), Math.max(0, Math.min(255, a)));
    }

    /** Рамка фокусу клавіатури: 2 px акцентом навколо елемента. */
    public static void focusRing(Graphics2D g2, int w, int h, int arc) {
        g2.setColor(FOCUS);
        g2.setStroke(new BasicStroke(2f));
        g2.drawRoundRect(1, 1, w - 3, h - 3, arc, arc);
    }

    /** Текст, що не влазить у ширину, обрізається з «…». */
    public static String fit(String text, FontMetrics fm, int width) {
        if (text == null) return "";
        if (fm.stringWidth(text) <= width) return text;
        String dots = "…";
        int end = text.length();
        while (end > 0 && fm.stringWidth(text.substring(0, end) + dots) > width) end--;
        return text.substring(0, end).trim() + dots;
    }
}
