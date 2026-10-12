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
