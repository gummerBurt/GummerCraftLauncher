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

    /**
     * 21.11.2026 (власник на Deck з 1.36.8: «коли вийшов зі сталдейзу попав не в лаунчер, а на сірий екран»): в ігровому
     * режимі Steam Deck (gamescope) вікно на час гри не знищується. Перемальовки нижче не допомогли: gamescope погано
     * підхоплює вікно, яке зникло і з'явилось заново (нове вікно X11 того ж застосунку), і показує сіру порожнечу.
     * Вікно, що лишилось, gamescope просто знову показує, коли гра закривається, а поки гра йде, на екрані гра (як у
     * Prism Launcher, який під час гри своє вікно не ховає). Лише gamescope: у режимі робочого столу, на Windows і Mac
     * усе як раніше. {@code GC_KEEP_WINDOW=1} / {@code 0} примусово (для перевірки).
     */
    public static boolean keepDuringGame() {
        String force = System.getenv("GC_KEEP_WINDOW");
        if ("1".equals(force)) return true;
        if ("0".equals(force)) return false;
        if (!System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("linux")) return false;
        String desktop = String.valueOf(System.getenv("XDG_CURRENT_DESKTOP")).toLowerCase(java.util.Locale.ROOT);
        String gw = System.getenv("GAMESCOPE_WAYLAND_DISPLAY");
        return desktop.contains("gamescope") || gw != null && !gw.isEmpty() || "1".equals(System.getenv("SteamGamepadUI"));
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
