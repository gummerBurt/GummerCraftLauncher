/*
 * Copyright (C) 2021-2026 GummerCraft <http://gummercraft.fun>
 */

package com.gummer.launcher.look;

import com.formdev.flatlaf.ui.FlatLabelUI;

import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.UIManager;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.basic.BasicHTML;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.font.FontRenderContext;
import java.awt.geom.AffineTransform;

/**
 * 22.11.2026 (власник: «пошукай де ще щось стирчить, недоназване»): Swing міряє текст для масштабу 100%, а на екрані
 * з масштабом Windows 125-150% (Java 17) малює ширшим на 1-10 px, і кінець напису обрізався без «…» («gummer»,
 * «Акаунт ГК», «Скіни», «Налаштуван…»). Тут ширина рядка береться найбільшою з усіх можливих малювань, а
 * {@link FitLabelUI} дає кожному JLabel стільки місця, скільки він справді займе.
 */
public final class TextFit {

    private static final double[] SCALES = {1, 1.25, 1.5, 1.75, 2};

    private TextFit() {
    }

    /** Найбільша ширина рядка серед масштабів 1-2, з дробовими метриками і без, у логічних пікселях, з округленням вгору. */
    public static int widest(Font f, String s) {
        if (f == null || s == null || s.isEmpty()) return 0;
        // кеш: getPreferredSize кличуть часто (рядки списків, кожна розкладка), а тут 10 вимірів на рядок
        String key = f.getFontName() + '|' + f.getStyle() + '|' + f.getSize2D() + '|' + s;
        Integer cached = CACHE.get(key);
        if (cached != null) return cached;
        if (CACHE.size() > 4000) CACHE.clear();
        int r = measure(f, s);
        CACHE.put(key, r);
        return r;
    }

    private static final java.util.concurrent.ConcurrentHashMap<String, Integer> CACHE = new java.util.concurrent.ConcurrentHashMap<String, Integer>();

    private static int measure(Font f, String s) {
        double w = 0;
        for (double sc : SCALES) {
            for (boolean frac : new boolean[]{true, false}) {
                FontRenderContext frc = new FontRenderContext(AffineTransform.getScaleInstance(sc, sc), true, frac);
                w = Math.max(w, f.getStringBounds(s, frc).getWidth());
            }
        }
        return (int) Math.ceil(w) + 1;
    }

    /** Поставити {@link FitLabelUI} для всіх JLabel (після кожного UIManager.setLookAndFeel). */
    public static void install() {
        UIManager.put("LabelUI", FitLabelUI.class.getName());
        UIManager.put(FitLabelUI.class.getName(), FitLabelUI.class); // клас напряму: лаунчер живе в своєму завантажувачі класів
    }

    /** Напис FlatLaf, що просить ширину з запасом на масштаб Windows. */
    public static class FitLabelUI extends FlatLabelUI {

        public FitLabelUI() {
            super(false);
        }

        public static ComponentUI createUI(JComponent c) {
            return new FitLabelUI();
        }

        @Override
        public Dimension getPreferredSize(JComponent c) {
            Dimension d = super.getPreferredSize(c);
            if (d == null || !(c instanceof JLabel)) return d;
            JLabel l = (JLabel) c;
            String t = l.getText();
            Font f = l.getFont();
            if (t == null || t.isEmpty() || f == null || BasicHTML.isHTMLString(t)) return d;
            int extra = widest(f, t) - l.getFontMetrics(f).stringWidth(t);
            return extra > 0 ? new Dimension(d.width + extra, d.height) : d;
        }
    }
}
