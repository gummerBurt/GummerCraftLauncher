/*
 * Copyright (C) 2021-2026 GummerCraft <http://gummercraft.fun>
 */

package com.gummer.launcher.look;

import javax.swing.*;
import java.awt.*;
import java.awt.event.AWTEventListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.HierarchyEvent;
import java.awt.geom.AffineTransform;

/**
 * Підготовка фонів збірок наперед для Класики і Бібліотеки (18.11.2026, за логом сторожа EdtWatch з тестового
 * лаунчера власника: клік по збірці масштабував її фон під вікно прямо в потоці інтерфейсу, 60-100 мс на клік, і так
 * при кожному новому розмірі вікна). Через 1 с після показу панелі чи зміни розміру вікна панель передає фони всіх
 * збірок з оформленням у {@link DevicePixels#prewarm}, той рахує їх у фоновому потоці. Скло робить те саме своїм
 * prewarmScenes (там ще й розмите скло бічної панелі).
 * <p>
 * Тут же лічильник тиші ({@link #idleMs()}): важку підготовку на кшталт вікна налаштувань краще робити, коли гравець
 * не рухає мишею і не натискає клавіш.
 */
public final class PackPrewarm {

    /** Збірки з власним фоном (як у FrameImages) і стандартний фон (GummerCraft, свої збірки, Modrinth). */
    public static final String[] KEYS = {"staldayz", "vanilla", "fabric", "quilt", "forge", "neoforge", "default"};

    /** Порахувати фони під масштаб екрана ds; false - якісь картинки ще розпаковуються, спробувати пізніше. */
    public interface Task {
        boolean run(double ds);
    }

    private static volatile long lastInput = System.currentTimeMillis();

    static {
        try {
            Toolkit.getDefaultToolkit().addAWTEventListener((AWTEventListener) e -> lastInput = System.currentTimeMillis(),
                    AWTEvent.MOUSE_EVENT_MASK | AWTEvent.MOUSE_MOTION_EVENT_MASK | AWTEvent.MOUSE_WHEEL_EVENT_MASK | AWTEvent.KEY_EVENT_MASK);
        } catch (RuntimeException ignored) {
            // без лічильника: idleMs рахує від завантаження класу
        }
    }

    private PackPrewarm() {
    }

    /** Скільки мілісекунд не було руху миші, коліщатка чи клавіш у лаунчері. */
    public static long idleMs() {
        return System.currentTimeMillis() - lastInput;
    }

    /** Запускати task через 1 с після показу панелі і після кожної зміни її розміру (до 20 повторів, поки false). */
    public static void hook(final JComponent panel, final Task task) {
        final int[] tries = {0};
        final Timer timer = new Timer(1000, null);
        timer.setRepeats(false);
        timer.addActionListener(e -> {
            if (!panel.isShowing() || panel.getWidth() <= 0 || panel.getHeight() <= 0) return;
            boolean done;
            try {
                done = task.run(scaleOf(panel));
            } catch (RuntimeException ex) {
                done = true; // не вийшло: порахується при показі, як раніше
            }
            if (!done && ++tries[0] < 20) timer.restart();
        });
        panel.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                tries[0] = 0;
                timer.restart();
            }
        });
        panel.addHierarchyListener(e -> {
            if ((e.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0 && panel.isShowing()) {
                tries[0] = 0;
                timer.restart();
            }
        });
    }

    private static double scaleOf(Component c) {
        GraphicsConfiguration gc = c.getGraphicsConfiguration();
        AffineTransform dt = gc != null ? gc.getDefaultTransform() : new AffineTransform();
        return dt.getScaleX() == dt.getScaleY() && dt.getShearX() == 0 && dt.getShearY() == 0 && dt.getScaleX() > 0 ? dt.getScaleX() : 1.0;
    }
}
