/*
 * Copyright (C) 2021-2026 GummerCraft <http://gummercraft.fun>
 */

package com.gummer.launcher.look;

import com.gummer.launcher.prefs.LauncherPrefs;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowEvent;

/**
 * 17.11.2026 (власник: «в повноекранці масштабувати (збільшити) ось це», бічна панель Liquid Glass і плашки
 * «Увійти», «Скіни»; «треба щоб лаунчер пам'ятав про повноекранку»).
 *
 * <p>Множник розміру для частини вікна: предок кладе {@link #KEY} (Double) у client property, нащадки, які малюють
 * себе самі (перемикач «Усі / Встановлені», плашки «Вигляд» і лайк), множать на нього свій розмір і малюють через
 * {@code g2.scale}. Масштаб не через перезапуск і не через малювання всієї панелі в масштабі (тоді миша, підказки і
 * випадні меню їхали б), а справжніми розмірами.</p>
 */
public final class Zoom {

    public static final String KEY = "gummer.zoom";
    /** Розмір вікна, під який зроблено вигляд (найменше вікно). */
    public static final int BASE_W = 1194, BASE_H = 725;
    private static final String MAXIMIZED = "windowMaximized";
    /** Останній звичайний (не розгорнутий) розмір вікна в цьому запуску, для «Відновити» після перемикання вигляду. */
    private static volatile Rectangle lastNormal;

    private Zoom() {
    }

    /** Множник найближчого предка з {@link #KEY}, інакше 1. */
    public static double of(Component c) {
        for (Component p = c; p != null; p = p.getParent()) {
            if (p instanceof JComponent) {
                Object v = ((JComponent) p).getClientProperty(KEY);
                if (v instanceof Double) return (Double) v;
            }
        }
        return 1.0;
    }

    public static int px(double zoom, double v) {
        return (int) Math.round(v * zoom);
    }

    /**
     * Множник для вікна такого розміру: у скільки разів вікно більше за найменше (за меншою стороною), від 1 до 1.75,
     * кроком 0.05 (щоб під час розтягування список не перебудовувався на кожен піксель).
     */
    public static double forWindow(int width, int height) {
        double z = Math.min(width / (double) BASE_W, height / (double) BASE_H);
        z = Math.max(1.0, Math.min(1.75, z));
        return Math.floor(z * 20 + 1e-6) / 20.0;
    }

    /**
     * 17.11.2026 (власник: «досі є цей дебільний перескок ... тільки в повноекранному режимі»): поки розгорнуте вікно
     * не показане, Windows віддає верхню рамку 0 замість 7 px (як у бокових), тож усе розкладалось на 7 px вище і
     * після показу з'їжджало вниз. До показу верхня рамка розгорнутого вікна дорівнює боковій, як буде після.
     */
    public static Insets settledInsets(Frame frame, Insets in) {
        if (in == null || (frame.getExtendedState() & Frame.MAXIMIZED_BOTH) != Frame.MAXIMIZED_BOTH) return in;
        if (frame.isShowing()) {
            // справжні рамки розгорнутого вікна: запам'ятати (і в launcher-ui.json для наступного старту)
            if (in.left > 0 && in.top > 0 && !in.equals(maxInsets)) {
                maxInsets = (Insets) in.clone();
                try {
                    LauncherPrefs.set(MAX_INSETS, in.top + "," + in.left + "," + in.bottom + "," + in.right);
                } catch (RuntimeException ignored) {
                    // без налаштувань лише в пам'яті
                }
            }
            return in;
        }
        // 17.11.2026 (власник: «відкриваєш лаунчер в повноекранному ... контент наче трохи "зсувається"»): на старті
        // Java до показу не знає рамок зовсім (усі 0), при перемиканні вигляду знає лише бокові. Беремо ті, що Windows
        // дала розгорнутому вікну минулого разу, а на першому запуску системну товщину рамки.
        Insets known = knownMaxInsets();
        if (known != null) return (Insets) known.clone();
        if (in.top < in.left) return new Insets(in.left, in.left, in.bottom, in.right);
        return in;
    }

    private static final String MAX_INSETS = "maximizedInsets";
    private static volatile Insets maxInsets;

    private static Insets knownMaxInsets() {
        if (maxInsets != null) return maxInsets;
        try {
            String[] v = LauncherPrefs.getString(MAX_INSETS, "").split(",");
            if (v.length == 4) {
                maxInsets = new Insets(Integer.parseInt(v[0].trim()), Integer.parseInt(v[1].trim()), Integer.parseInt(v[2].trim()), Integer.parseInt(v[3].trim()));
                return maxInsets;
            }
        } catch (RuntimeException ignored) {
            // нижче системна рамка
        }
        int border = WinPlacement.maximizedBorder();
        return border > 0 ? new Insets(border, border, border, border) : null;
    }

    /** Розгорнути вікно, якщо гравець закрив лаунчер розгорнутим, і далі запам'ятовувати стан (усі три вигляди). */
    public static void rememberMaximized(JFrame frame) {
        try {
            Window from = com.gummer.launcher.FancyLauncher.reopeningFrom();
            if (from != null) {
                // 17.11.2026 (власник: «коли я перемикаю в фулскріні дизайни вони перемикаються і за секунду трішки
                // розтягуються вверх»): нове вікно стає точно на місце старого і в тому ж стані. Анімацію Windows
                // власник просив лишити («вона була плавна і приємна»).
                if (from instanceof Frame && (((Frame) from).getExtendedState() & Frame.MAXIMIZED_BOTH) == Frame.MAXIMIZED_BOTH) {
                    frame.setBounds(from.getBounds());
                    frame.setExtendedState(frame.getExtendedState() | Frame.MAXIMIZED_BOTH);
                    WinAnim.disable(frame); // 17.11.2026 ~23:50: без зсуву вниз після анімації появи
                } else if (from.getWidth() >= frame.getMinimumSize().width && from.getHeight() >= frame.getMinimumSize().height) {
                    frame.setBounds(from.getBounds());
                }
            } else if (LauncherPrefs.getBoolean(MAXIMIZED, false)) {
                // 17.11.2026: розгорнуте вікно Windows ставить на товщину рамки за край робочого столу (у власника
                // 2062 x 1126 при столі 2048 x 1112). Якщо до показу взяти лише стіл, перший кадр розкладався на 14 px
                // вужчим і нижчим, а потім усе розсувалось («контент наче трохи зсувається»).
                Rectangle work = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
                Insets b = knownMaxInsets();
                if (b != null) work = new Rectangle(work.x - b.left, work.y - b.top, work.width + b.left + b.right, work.height + b.top + b.bottom);
                frame.setBounds(work);
                frame.setExtendedState(frame.getExtendedState() | Frame.MAXIMIZED_BOTH);
                WinAnim.disable(frame); // 17.11.2026 ~23:50: див. WinAnim
            }
        } catch (RuntimeException ignored) {
            // без налаштувань звичайне вікно
        }
        frame.addWindowStateListener((WindowEvent e) -> {
            if ((e.getNewState() & Frame.ICONIFIED) != 0) return; // згорнуте в панель задач: стан не міняємо
            boolean max = (e.getNewState() & Frame.MAXIMIZED_BOTH) == Frame.MAXIMIZED_BOTH;
            if (max != LauncherPrefs.getBoolean(MAXIMIZED, false)) LauncherPrefs.set(MAXIMIZED, max);
        });
        // 17.11.2026 (власник: «коли перемикаєш між повноекранним та віконним воно так різко перебудовується»): вікно,
        // відкрите розгорнутим, не мало свого звичайного розміру, тож «Відновити» спершу давало весь робочий стіл, а
        // потім стискалось до найменшого (дві перебудови). Тепер одразу після показу звичайний розмір задається
        // заздалегідь через Windows (WinPlacement; setBounds Java зняв би розгортання): останній звичайний розмір у
        // цьому запуску, інакше найменший по центру.
        frame.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowOpened(WindowEvent e) {
                SwingUtilities.invokeLater(() -> {
                    if ((frame.getExtendedState() & Frame.MAXIMIZED_BOTH) != Frame.MAXIMIZED_BOTH) return;
                    Rectangle work = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
                    Rectangle normal = lastNormal;
                    if (normal == null || !work.contains(normal)) {
                        Dimension min = frame.getMinimumSize();
                        normal = new Rectangle(work.x + (work.width - min.width) / 2, work.y + (work.height - min.height) / 2, min.width, min.height);
                    }
                    WinPlacement.setNormalBounds(frame, normal);
                });
            }
        });
        frame.addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override
            public void componentResized(java.awt.event.ComponentEvent e) {
                remember();
            }

            @Override
            public void componentMoved(java.awt.event.ComponentEvent e) {
                remember();
            }

            private void remember() {
                if (frame.isShowing() && frame.getExtendedState() == Frame.NORMAL) lastNormal = frame.getBounds();
            }
        });
    }
}
