/*
 * Copyright (C) 2021-2026 GummerCraft <http://gummercraft.fun>
 */

package com.gummer.launcher.look;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.WinDef;
import com.sun.jna.ptr.IntByReference;

import java.awt.*;

/**
 * 17.11.2026 ~23:50 (власник зі скрінами: «вікно спершу стоїть вище, а потім за чверть секунди зсувається вниз»): поки
 * йде анімація появи вікна Windows (DWM), розгорнуте вікно малюється без верхньої рамки, тож уміст на ~8 px вище, і
 * після анімації з'їжджає вниз. Для РОЗГОРНУТОГО вікна (старт і перемикання вигляду) анімацію появи вимкнено лише цьому
 * вікну ({@code DWMWA_TRANSITIONS_FORCEDISABLED}); у звичайному вікні вона лишається (власник о 19:58 просив її).
 * Проби, що знімають саме вікно (PrintWindow), цього не бачать: видно лише на екрані.
 */
public final class WinAnim {

    private interface Dwm extends Library {
        int DwmSetWindowAttribute(WinDef.HWND hwnd, int attr, IntByReference value, int size);
    }

    private static final int DWMWA_TRANSITIONS_FORCEDISABLED = 3;

    private WinAnim() {
    }

    /** Вимкнути анімації DWM цьому вікну (вікно має бути вже створене: addNotify / pack). Не Windows: нічого. */
    public static void disable(Window w) {
        if (w == null || !System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).startsWith("windows")) return;
        try {
            if (!w.isDisplayable()) w.addNotify();
            Pointer p = Native.getWindowPointer(w);
            if (p == null) return;
            Dwm dwm = Native.load("dwmapi", Dwm.class);
            dwm.DwmSetWindowAttribute(new WinDef.HWND(p), DWMWA_TRANSITIONS_FORCEDISABLED, new IntByReference(1), 4);
        } catch (Throwable ignored) {
            // без dwmapi (стара система) чи JNA: просто з анімацією
        }
    }
}
