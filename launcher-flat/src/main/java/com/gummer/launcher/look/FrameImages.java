/*
 * Copyright (C) 2021-2026 GummerCraft <http://gummercraft.fun>
 */

package com.gummer.launcher.look;

import com.skcraft.launcher.Launcher;

import javax.imageio.ImageIO;
import java.awt.*;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Фони і логотипи збірок ({@code ui/frame/bg_*.jpg}, {@code logo_*.png}), спільні для всіх трьох виглядів (16.11.2026).
 * До цього кожна сцена (FlatBackgroundPanel, 2, 3) розпаковувала картинку при першому показі збірки прямо в потоці
 * інтерфейсу: перший перехід, скажімо, зі StalDayZ на Vanilla стояв на JPEG (~80 мс з ~200-300 мс кроку, власник:
 * «затуп десь на секунду»). Тепер {@link #warmUp()} після старту розпаковує їх у фоні, а {@link #get} бере готові.
 */
public final class FrameImages {

    /** Збірки з власним оформленням (є bg_ і logo_ у ui/frame). */
    private static final String[] KEYS = {"staldayz", "vanilla", "fabric", "quilt", "forge", "neoforge"};

    private static final Map<String, Optional<Image>> CACHE = new ConcurrentHashMap<String, Optional<Image>>();
    private static final AtomicBoolean WARMING = new AtomicBoolean();

    private FrameImages() {
    }

    /** Картинка з ресурсів лаунчера (шлях відносно Launcher.class) або null, якщо її немає чи вона бита. */
    public static Image get(String path) {
        return CACHE.computeIfAbsent(path, FrameImages::read).orElse(null);
    }

    private static Optional<Image> read(String path) {
        try (InputStream in = Launcher.class.getResourceAsStream(path)) {
            return Optional.ofNullable(in != null ? (Image) ImageIO.read(in) : null);
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    /** Один раз за запуск: розпакувати всі фони і логотипи у фоновому потоці з низьким пріоритетом. */
    public static void warmUp() {
        if (!WARMING.compareAndSet(false, true)) return;
        Thread t = new Thread(() -> {
            for (String key : KEYS) {
                get("ui/frame/bg_" + key + ".jpg");
                get("ui/frame/logo_" + key + ".png");
            }
        }, "GummerCraft frame images");
        t.setDaemon(true);
        t.setPriority(Thread.MIN_PRIORITY);
        t.start();
    }
}
