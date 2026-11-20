package com.gummer.launcher.look;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.util.Map;

/**
 * Згладжування тексту в намальованих вручну компонентах: те саме, що в системі (на Windows ClearType), а не примусово
 * сіре. 20.11.2026: сіре згладжування разом з масштабом Windows 125% стискало Segoe UI 12.5-13 px і робило нерівні
 * відступи між літерами («текст мерехтить»). Якщо система нічого не каже, лишається сіре.
 */
public final class TextAA {

    private TextAA() {
    }

    public static void apply(Graphics2D g) {
        Object hints = Toolkit.getDefaultToolkit().getDesktopProperty("awt.font.desktophints");
        if (hints instanceof Map) {
            g.addRenderingHints((Map<?, ?>) hints);
        } else {
            // 20.11.2026, 1.36.6: тут був виклик самого себе (переповнення стеку там, де система підказок не дає, напр. Linux)
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        }
    }
}
