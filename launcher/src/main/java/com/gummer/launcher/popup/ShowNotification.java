/*
 * Copyright (C) 2021-2025 GummerCraft <http://gummercraft.fun>
 */

package com.gummer.launcher.popup;

import java.awt.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ShowNotification {

    // with translated title
    public static void showNotification(Frame frame, Class<?> clazz, Notification.Type type, String message, String title) {
        com.gummer.launcher.ui.UiHooks.Toast hook = com.gummer.launcher.ui.UiHooks.toast;
        if (hook != null && hook.show(frame, type, message, title)) return; // 16.11.2026: у стилі вигляду
        Notification panel;
        try {
            panel = new Notification(
                frame,
                type,
                Notification.Location.TOP_CENTER,
                message,
                title
            );
            panel.showNotification();
        } catch (InterruptedException ex) {
            Logger.getLogger(clazz.getName()).log(Level.SEVERE, null, ex);
        }
    }

    // with default title
    public static void showNotification(Frame frame, Class<?> clazz, Notification.Type type, String message) {
        com.gummer.launcher.ui.UiHooks.Toast hook = com.gummer.launcher.ui.UiHooks.toast;
        if (hook != null && hook.show(frame, type, message, null)) return; // 16.11.2026: у стилі вигляду
        Notification panel;
        try {
            panel = new Notification(
                frame,
                type,
                Notification.Location.TOP_CENTER,
                message
            );
            panel.showNotification();
        } catch (InterruptedException ex) {
            Logger.getLogger(clazz.getName()).log(Level.SEVERE, null, ex);
        }
    }
}
