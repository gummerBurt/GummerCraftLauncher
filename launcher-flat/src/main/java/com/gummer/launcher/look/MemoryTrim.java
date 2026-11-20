/*
 * Copyright (C) 2021-2026 GummerCraft <http://gummercraft.fun>
 */

package com.gummer.launcher.look;

import javax.swing.SwingUtilities;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Віддати пам'ять системі, поки йде гра (20.11.2026, замір: процес лаунчера 475-720 МБ, з них ~133 МБ готові
 * картинки фонів, і вікно на час гри лише ховається, тож уся ця пам'ять лишалась зайнятою до виходу з гри).
 * На старті гри викидаються кеші картинок (DevicePixels, скло Liquid Glass), а через кілька секунд, коли гра вже
 * вантажиться, Java прибирає купу і повертає вільне системі. Після гри фони порахуються знову (перший показ
 * вікна трохи довший, далі фоновий прогрів як завжди).
 */
public final class MemoryTrim {

    private static final Logger log = Logger.getLogger(MemoryTrim.class.getName());
    private static final long DELAY_MS = 5000;

    private MemoryTrim() {
    }

    public static void forGame() {
        Runnable clear = () -> {
            DevicePixels.clear();
            com.gummer.launcher.FlatBackgroundPanel.clearGlassCache();
        };
        if (SwingUtilities.isEventDispatchThread()) clear.run();
        else SwingUtilities.invokeLater(clear);
        Thread t = new Thread(() -> {
            try {
                Thread.sleep(DELAY_MS);
                Runtime rt = Runtime.getRuntime();
                long before = rt.totalMemory() - rt.freeMemory();
                long committed = rt.totalMemory();
                System.gc();
                long after = rt.totalMemory() - rt.freeMemory();
                log.info(String.format("Memory trim for the game: used %d -> %d MB, heap committed %d -> %d MB",
                        before >> 20, after >> 20, committed >> 20, rt.totalMemory() >> 20));
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            } catch (RuntimeException e) {
                log.log(Level.FINE, "Memory trim failed", e);
            }
        }, "GummerCraft memory trim");
        t.setDaemon(true);
        t.setPriority(Thread.MIN_PRIORITY);
        t.start();
    }
}
