/*
 * Copyright (C) 2021-2026 GummerCraft <http://gummercraft.fun>
 */

package com.gummer.launcher.look;

import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;

/**
 * Рамка фокусу клавіатури лише тоді, коли гравець справді ходить клавіатурою (20.11.2026, скан верстки: у Бібліотеці
 * одразу після запуску зелена рамка стояла на кнопці Telegram, бо фокус при старті падає на першу кнопку, і це
 * виглядало як виділена кнопка). Як у браузерах (:focus-visible): Tab або стрілки вмикають рамку, клік мишею вимикає.
 */
public final class FocusVisible {

    private static volatile boolean on;
    private static boolean installed;

    private FocusVisible() {
    }

    public static boolean on() {
        return on;
    }

    public static void install() {
        if (installed) return;
        installed = true;
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(e -> {
            if (e.getID() == KeyEvent.KEY_PRESSED) {
                int k = e.getKeyCode();
                if (k == KeyEvent.VK_TAB || k == KeyEvent.VK_UP || k == KeyEvent.VK_DOWN
                        || k == KeyEvent.VK_LEFT || k == KeyEvent.VK_RIGHT) set(true);
            }
            return false;
        });
        Toolkit.getDefaultToolkit().addAWTEventListener(e -> {
            if (e.getID() == MouseEvent.MOUSE_PRESSED) set(false);
        }, AWTEvent.MOUSE_EVENT_MASK);
    }

    /** Гравець ходить клавіатурою чи геймпадом ({@link PadNav}): показати рамку. */
    public static void keyboard() {
        set(true);
    }

    private static void set(boolean v) {
        if (on == v) return;
        on = v;
        Component f = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
        if (f != null) f.repaint();
        PadNav.Ring.update();
    }
}
