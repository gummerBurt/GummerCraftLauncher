/*
 * Copyright (C) 2021-2026 GummerCraft <http://gummercraft.fun>
 */

package com.gummer.launcher.look;

import javax.swing.*;
import java.awt.*;

/**
 * Вікно лаунчера після закриття гри (21.11.2026, власник: «коли виходиш з гри ... тобі не показує головне меню
 * лаунчера, а якийсь сірий екран»). На час гри вікно знищується ({@code dispose}) і після неї створюється заново.
 * Під gamescope (ігровий режим Steam Deck) і Xwayland заново створене вікно може лишитись сірим, поки його щось не
 * змусить перемалюватись, а фокус лишитись на старому вікні. Тут: вікно наперед, фокус у нього, перемальовка одразу і
 * ще двічі із запізненням; під Steam на Linux ще й зміна розміру на 1 піксель і назад (так X11 точно перемальовує).
 * На Windows і Mac лише вперед, фокус і перемальовка, як і було б при звичайному показі.
 */
public final class GameReturn {

    private GameReturn() {
    }

    public static void shown(final Window w) {
        if (w == null) return;
        w.toFront();
        w.requestFocus();
        refresh(w);
        final boolean steam = PadNav.wanted();
        for (final int delay : new int[]{300, 1500}) {
            Timer t = new Timer(delay, e -> {
                if (!w.isShowing()) return;
                if (steam && delay == 300 && !(w instanceof Frame && (((Frame) w).getExtendedState() & Frame.MAXIMIZED_BOTH) != 0)) {
                    Dimension d = w.getSize();
                    w.setSize(d.width, d.height - 1);
                    w.setSize(d);
                }
                if (steam) {
                    w.toFront();
                    w.requestFocus();
                }
                refresh(w);
            });
            t.setRepeats(false);
            t.start();
        }
    }

    private static void refresh(Window w) {
        w.invalidate();
        w.validate();
        w.repaint();
        if (w instanceof RootPaneContainer) ((RootPaneContainer) w).getRootPane().repaint();
    }
}
