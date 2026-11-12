/*
 * Copyright (C) 2021-2026 GummerCraft <http://gummercraft.fun>
 */

package com.gummer.launcher;

import com.gummer.launcher.util.SelfRestart;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 1.32: масштаб вікна під екран.
 *
 * <p>Java 9+ на Windows збільшує все вікно на масштаб Windows (125%, 150%...). Головне вікно не змінює розмір
 * ({@link #NEED_WIDTH} x {@link #NEED_HEIGHT} логічних пікселів), тож на малих екранах з великим масштабом
 * (1366x768 на 125%, 1920x1080 на 150%) воно виходило за край. Тоді беремо найбільший масштаб із кроком 0.25
 * (як у Windows), за якого вікно влазить у робочу область екрана (без панелі задач), але не менше 100%
 * і не більше масштабу Windows.</p>
 *
 * <p>Масштаб Java можна задати лише до старту AWT ({@code sun.java2d.uiScale}), а розмір екрана відомий лише після.
 * Тому вибраний масштаб пишеться в {@code <dir>/launcher/ui-scale.txt}, лаунчер один раз перезапускається
 * через бутстрапер і на наступних запусках бере масштаб з файлу ще до AWT. Влазить і так: файлу немає.</p>
 *
 * <p>Java 8 (старий exe на системній Java) масштабу не має взагалі: там нічого не робимо. Якщо гравець сам задав
 * {@code sun.java2d.uiScale}, теж не втручаємось.</p>
 */
public final class UiScaleFit {

    /** Мінімальний розмір головного вікна (LauncherFrameFlat.setMinimumSize), у логічних пікселях. */
    static final int NEED_WIDTH = 1194;
    static final int NEED_HEIGHT = 725;

    private static final String PROPERTY = "sun.java2d.uiScale";
    private static final Logger log = Logger.getLogger(UiScaleFit.class.getName());

    /** Масштаб, який ми виставили з файлу до AWT (null - не виставляли). */
    private static Double applied;
    /** Гравець задав масштаб сам: не чіпаємо. */
    private static boolean userDefined;

    private UiScaleFit() {
    }

    /** Викликати першим ділом у main, до будь-якого AWT/Swing. */
    static void applySaved(File baseDir) {
        if (!supported() || baseDir == null) return;
        if (System.getProperty(PROPERTY) != null) {
            userDefined = true;
            return;
        }
        Double saved = read(file(baseDir));
        if (saved != null) {
            System.setProperty(PROPERTY, format(saved));
            applied = saved;
        }
    }

    /**
     * Після старту AWT: чи влазить вікно. Якщо потрібен інший масштаб, ніж зараз, записує його і перезапускає лаунчер
     * (повертається лише якщо перезапуск не потрібен або не вдався).
     */
    static void fitOrRelaunch(File baseDir) {
        if (!supported() || baseDir == null || userDefined || GraphicsEnvironment.isHeadless()) return;
        try {
            GraphicsConfiguration gc = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice().getDefaultConfiguration();
            double current = gc.getDefaultTransform().getScaleX();
            // масштаб Windows: без нашого перевизначення це і є current, з ним - системний DPI (Java 17 віддає справжній)
            double windows = applied == null ? current : Toolkit.getDefaultToolkit().getScreenResolution() / 96.0;
            Rectangle b = gc.getBounds();
            Insets in = Toolkit.getDefaultToolkit().getScreenInsets(gc);
            double usableW = (b.width - in.left - in.right) * current;   // фізичні пікселі
            double usableH = (b.height - in.top - in.bottom) * current;
            double desired = choose(windows, usableW, usableH);

            File f = file(baseDir);
            if (Math.abs(desired - windows) < 0.001) {
                if (applied != null) Files.deleteIfExists(f.toPath()); // влазить і з масштабом Windows: повертаємо його
            } else {
                f.getParentFile().mkdirs();
                Files.write(f.toPath(), format(desired).getBytes(StandardCharsets.UTF_8));
            }
            if (Math.abs(desired - current) < 0.001) return;

            log.info(String.format(Locale.ROOT, "UI scale: windows %.2f, current %.2f, screen %.0fx%.0f px usable, need %.2f",
                    windows, current, usableW, usableH, desired));
            Double next = Math.abs(desired - windows) < 0.001 ? null : desired;
            if (sameValue(next, applied)) {
                // уже пробували саме цей масштаб, а AWT стартував раніше (наприклад, бутстрапер показував вікно завантаження): без циклу
                return;
            }
            if (!SelfRestart.relaunchBootstrap()) {
                log.info("UI scale: could not relaunch, keeping current scale for this run");
            }
        } catch (IOException | RuntimeException e) {
            log.log(Level.WARNING, "UI scale check failed", e);
        }
    }

    /** Найбільший масштаб кроком 0.25 у межах [1, windows], за якого вікно влазить; масштаб Windows, якщо влазить і так. */
    static double choose(double windows, double usableW, double usableH) {
        if (windows <= 1.0) return windows;
        double fit = Math.min(usableW / NEED_WIDTH, usableH / NEED_HEIGHT);
        if (fit >= windows) return windows;
        return Math.max(1.0, Math.floor(fit * 4 + 1e-9) / 4.0);
    }

    private static boolean sameValue(Double a, Double b) {
        return a == null ? b == null : b != null && Math.abs(a - b) < 0.001;
    }

    private static boolean supported() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows")
                && !System.getProperty("java.specification.version", "1.8").startsWith("1.");
    }

    private static File file(File baseDir) {
        return new File(baseDir, "launcher/ui-scale.txt");
    }

    private static Double read(File f) {
        try {
            if (!f.isFile()) return null;
            double v = Double.parseDouble(new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8).trim());
            return v >= 1.0 && v <= 4.0 ? v : null;
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    private static String format(double v) {
        return String.format(Locale.ROOT, "%.2f", v);
    }
}
