/*
 * Copyright (C) 2021-2026 GummerCraft <http://gummercraft.fun>
 */

package com.gummer.launcher.look;

import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef;
import com.sun.jna.platform.win32.WinUser;

import java.awt.*;
import java.awt.geom.AffineTransform;

/**
 * 17.11.2026: задати розгорнутому вікну його звичайний розмір (куди воно повернеться кнопкою «Відновити»), не
 * знімаючи розгортання. Java так не вміє: {@code setBounds} на розгорнутому вікні його згортає до звичайного.
 * Лише Windows, інакше нічого не робить.
 */
public final class WinPlacement {

    private WinPlacement() {
    }

    /**
     * Товщина рамки розгорнутого вікна в логічних пікселях Java (SM_CXSIZEFRAME + SM_CXPADDEDBORDER, як Windows
     * виносить її за край екрана), 0, якщо не Windows або не вдалося.
     */
    public static int maximizedBorder() {
        try {
            if (!System.getProperty("os.name", "").toLowerCase().contains("win")) return 0;
            int phys = User32.INSTANCE.GetSystemMetrics(32) + User32.INSTANCE.GetSystemMetrics(92);
            double scale = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice()
                    .getDefaultConfiguration().getDefaultTransform().getScaleX();
            return phys <= 0 ? 0 : (int) Math.round(phys / scale);
        } catch (Throwable t) {
            return 0;
        }
    }

    /** @param normal звичайні межі в логічних пікселях Java; true, якщо Windows прийняв */
    public static boolean setNormalBounds(Window w, Rectangle normal) {
        try {
            if (!System.getProperty("os.name", "").toLowerCase().contains("win") || !w.isDisplayable()) return false;
            Pointer p = Native.getComponentPointer(w);
            if (p == null) return false;
            WinDef.HWND h = new WinDef.HWND(p);
            WinUser.WINDOWPLACEMENT wp = new WinUser.WINDOWPLACEMENT();
            if (!User32.INSTANCE.GetWindowPlacement(h, wp).booleanValue()) return false;
            AffineTransform t = w.getGraphicsConfiguration().getDefaultTransform();
            Rectangle screen = w.getGraphicsConfiguration().getBounds();
            Insets si = Toolkit.getDefaultToolkit().getScreenInsets(w.getGraphicsConfiguration());
            // координати робочої області (без панелі задач зліва чи зверху), у фізичних пікселях
            double sx = t.getScaleX(), sy = t.getScaleY();
            int x = (int) Math.round((normal.x - screen.x - si.left) * sx);
            int y = (int) Math.round((normal.y - screen.y - si.top) * sy);
            wp.rcNormalPosition = new WinDef.RECT();
            wp.rcNormalPosition.left = x;
            wp.rcNormalPosition.top = y;
            wp.rcNormalPosition.right = x + (int) Math.round(normal.width * sx);
            wp.rcNormalPosition.bottom = y + (int) Math.round(normal.height * sy);
            wp.flags = 0;
            return User32.INSTANCE.SetWindowPlacement(h, wp).booleanValue();
        } catch (Throwable t) {
            return false;
        }
    }
}
