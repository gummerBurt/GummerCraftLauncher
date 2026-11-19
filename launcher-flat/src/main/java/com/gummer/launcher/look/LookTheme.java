/*
 * Copyright (C) 2021-2026 GummerCraft <http://gummercraft.fun>
 */

package com.gummer.launcher.look;

import com.gummer.launcher.FancyLauncher;

import javax.swing.*;
import javax.swing.plaf.ColorUIResource;
import java.awt.*;

/**
 * 16.11.2026: свій стиль вікон (налаштування, підтримка, збірка, акаунт, фідбек, вікна повідомлень, прогрес) для
 * кожного вигляду лаунчера. Раніше всі діалоги були стандартним FlatLaf, однаковим для трьох виглядів.
 *
 * <ul>
 *     <li><b>Liquid Glass</b>: темне скло. Власні вікна лаунчера мають розмите тло головного вікна
 *     ({@link Backdrop}), картки напівпрозорі, великі заокруглення, салатовий акцент.</li>
 *     <li><b>Класика</b>: строгий темний з пакета {@code ui2}: суцільні поверхні, тонкі лінії, заокруглення 8,
 *     зелений акцент.</li>
 *     <li><b>Бібліотека</b>: палітра {@code ui3}, кнопки-«таблетки», заокруглення 12, навігація пігулками.</li>
 * </ul>
 *
 * <p>Ставиться через UIManager перед створенням головного вікна ({@link #apply}), тож кожне нове вікно бере
 * стиль поточного вигляду. Відкриті вікна не перефарбовуються: при зміні вигляду діалог налаштувань закривається,
 * а головне вікно створюється наново.</p>
 */
public final class LookTheme {

    /**
     * 19.11.2026 (власник: «зроби спрощені версії лаунчера для мак та лінукс - я охуїв як на маку наш лаунчер глючить»):
     * спрощений режим на macOS і Linux. Без розмиття під вікнами, плавних переходів фону, пульсацій кнопки запуску,
     * сплешу і скіна, анімації перебудови вікна та появи сповіщень; на Linux рамка вікна системна, а не намальована
     * лаунчером. Вигляди, кольори і всі функції ті самі. На Retina кожен кадр анімації це вчетверо більше пікселів, а
     * Java на Mac і драйвери на Linux малюють Swing помітно повільніше за Windows. {@code -Dgummer.lite=true|false}
     * вмикає чи вимикає режим примусово (для перевірки на Windows).
     */
    public static final boolean LITE = detectLite();

    private static boolean detectLite() {
        String forced = System.getProperty("gummer.lite");
        if (forced != null && !forced.trim().isEmpty()) return Boolean.parseBoolean(forced.trim());
        return !System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).startsWith("windows");
    }

    /** Кольори і форми одного вигляду. */
    public static final class Palette {
        public final String look;
        public final Color bg, surface, surface2, surface3, line, text, text2, text3, accent, accentHover, accentPressed,
                accentText, onAccent, danger, warn, info, success;
        /** Заокруглення карток і полів, кнопок (999: таблетка). */
        public final int radius, buttonArc;

        Palette(String look, int bg, int surface, int surface2, int surface3, int line, int text, int text2, int text3,
                int accent, int accentHover, int accentPressed, int accentText, int onAccent, int radius, int buttonArc) {
            this.look = look;
            this.bg = new Color(bg);
            this.surface = new Color(surface);
            this.surface2 = new Color(surface2);
            this.surface3 = new Color(surface3);
            this.line = new Color(line);
            this.text = new Color(text);
            this.text2 = new Color(text2);
            this.text3 = new Color(text3);
            this.accent = new Color(accent);
            this.accentHover = new Color(accentHover);
            this.accentPressed = new Color(accentPressed);
            this.accentText = new Color(accentText);
            this.onAccent = new Color(onAccent);
            this.danger = new Color(0xEF5A50);
            this.warn = new Color(0xE3B24F);
            this.info = new Color(0x5AA9E6);
            this.success = new Color(0x7FD36A);
            this.radius = radius;
            this.buttonArc = buttonArc;
        }

        public boolean glass() {
            return FancyLauncher.LOOK_GLASS.equals(look);
        }

        public boolean library() {
            return FancyLauncher.LOOK_LIBRARY.equals(look);
        }
    }

    public static final Palette GLASS = new Palette(FancyLauncher.LOOK_GLASS,
            0x111317, 0x1B1E23, 0x24282E, 0x2E333A, 0x343941,
            0xF4F5F7, 0xC2C7CE, 0x939AA4,
            0x4E9E2C, 0x5DB436, 0x428A24, 0x9BE06A, 0xFFFFFF, 14, 12);
    public static final Palette CLASSIC = new Palette(FancyLauncher.LOOK_CLASSIC,
            0x0F1013, 0x16171B, 0x1F2126, 0x292C33, 0x2A2D33,
            0xF2F3F5, 0xB0B5BD, 0x868C96,
            0x3C8A2A, 0x45A031, 0x33781F, 0x8FD16F, 0xFFFFFF, 8, 8);
    public static final Palette LIBRARY = new Palette(FancyLauncher.LOOK_LIBRARY,
            0x0F1013, 0x17181D, 0x202228, 0x2A2D34, 0x2C2F36,
            0xF2F3F5, 0xB0B5BD, 0x868C96,
            0x3C8A2A, 0x45A031, 0x33781F, 0x8FD16F, 0xFFFFFF, 12, 999);

    private static volatile Palette current = GLASS;

    private LookTheme() {
    }

    public static Palette palette() {
        return current;
    }

    public static Palette of(String look) {
        if (FancyLauncher.LOOK_CLASSIC.equals(look)) return CLASSIC;
        if (FancyLauncher.LOOK_LIBRARY.equals(look)) return LIBRARY;
        return GLASS;
    }

    /** Поставити стиль вигляду для всіх нових вікон. Викликати в потоці Swing перед створенням головного вікна. */
    public static void apply(String look) {
        Palette p = of(look);
        current = p;
        put("Panel.background", p.bg);
        put("RootPane.background", p.bg);
        put("OptionPane.background", p.bg);
        put("TitlePane.background", p.glass() ? new Color(0x0C0D10) : p.bg);
        put("TitlePane.inactiveBackground", p.glass() ? new Color(0x0C0D10) : p.bg);
        put("TitlePane.foreground", p.text2);
        put("TitlePane.inactiveForeground", p.text3);
        put("TitlePane.embeddedForeground", p.text2);
        put("Label.foreground", p.text);
        put("Label.disabledForeground", p.text3);
        put("Component.borderColor", p.line);
        put("Component.disabledBorderColor", p.line);
        put("Component.focusedBorderColor", p.accentHover);
        put("Component.accentColor", p.accent);
        put("Component.focusColor", p.accentHover);
        put("Component.error.focusedBorderColor", p.danger);

        UIManager.put("Component.arc", Math.min(p.radius, 10));
        UIManager.put("TextComponent.arc", Math.min(p.radius, 10));
        UIManager.put("Button.arc", p.buttonArc);
        UIManager.put("CheckBox.arc", p.library() ? 6 : 4);
        UIManager.put("ProgressBar.arc", p.library() ? 999 : p.glass() ? 6 : 0);
        UIManager.put("Button.borderWidth", 1);
        UIManager.put("Button.margin", new Insets(6, 16, 6, 16));

        put("Button.background", p.surface3);
        put("Button.foreground", p.text);
        put("Button.hoverBackground", lighter(p.surface3, 10));
        put("Button.pressedBackground", p.surface2);
        put("Button.borderColor", p.glass() ? new Color(0x3B4049) : p.line);
        put("Button.hoverBorderColor", p.glass() ? new Color(0x4A505A) : p.line);
        put("Button.focusedBorderColor", p.accentHover);
        put("Button.default.background", p.accent);
        put("Button.default.foreground", p.onAccent);
        put("Button.default.hoverBackground", p.accentHover);
        put("Button.default.pressedBackground", p.accentPressed);
        put("Button.default.borderColor", p.accent);
        put("Button.default.hoverBorderColor", p.accentHover);
        put("Button.default.focusedBorderColor", p.accentText);
        put("Button.default.focusedBackground", p.accent);
        put("Button.toolbar.hoverBackground", p.surface3);
        put("Button.toolbar.pressedBackground", p.surface2);

        put("TextField.background", p.surface2);
        put("PasswordField.background", p.surface2);
        put("FormattedTextField.background", p.surface2);
        put("TextArea.background", p.surface2);
        put("TextPane.background", p.surface2);
        put("EditorPane.background", p.surface);
        put("Spinner.background", p.surface2);
        put("TextField.foreground", p.text);
        put("TextArea.foreground", p.text);
        put("TextField.placeholderForeground", p.text3);
        put("TextField.inactiveBackground", p.surface);
        put("TextArea.inactiveBackground", p.surface);
        put("TextComponent.selectionBackground", p.accentPressed);
        put("TextComponent.selectionForeground", Color.WHITE);

        put("ComboBox.background", p.surface2);
        put("ComboBox.buttonBackground", p.surface2);
        put("ComboBox.buttonEditableBackground", p.surface2);
        put("ComboBox.popupBackground", p.surface);
        put("ComboBox.selectionBackground", p.accentPressed);
        put("ComboBox.selectionForeground", Color.WHITE);
        put("ComboBox.buttonArrowColor", p.text2);
        put("List.background", p.surface);
        put("List.selectionBackground", p.accentPressed);
        put("List.selectionForeground", Color.WHITE);
        put("PopupMenu.background", p.surface);
        put("PopupMenu.borderColor", p.line);
        put("MenuItem.background", p.surface);
        put("MenuItem.selectionBackground", p.surface3);
        put("RadioButtonMenuItem.selectionBackground", p.surface3);
        put("CheckBoxMenuItem.selectionBackground", p.surface3);
        UIManager.put("PopupMenu.borderCornerRadius", p.radius);

        put("CheckBox.icon.background", p.surface2);
        put("CheckBox.icon.borderColor", p.text3);
        put("CheckBox.icon.selectedBackground", p.accent);
        put("CheckBox.icon.selectedBorderColor", p.accent);
        put("CheckBox.icon.checkmarkColor", Color.WHITE);
        put("CheckBox.icon.focusedBorderColor", p.accentHover);
        put("CheckBox.icon.hoverBorderColor", p.text2);
        put("RadioButton.icon.centerColor", Color.WHITE);

        put("Slider.trackValueColor", p.accent);
        put("Slider.trackColor", p.surface3);
        put("Slider.thumbColor", p.accentText);
        put("Slider.focusedColor", p.accentHover);
        put("Slider.hoverThumbColor", p.success);
        put("Slider.tickColor", p.text3);

        put("ScrollBar.thumb", p.surface3);
        put("ScrollBar.hoverThumbColor", lighter(p.surface3, 18));
        put("ScrollBar.track", p.glass() ? new Color(0, 0, 0, 0) : p.bg);
        put("ScrollPane.background", p.bg);
        put("Viewport.background", p.bg);
        put("Separator.foreground", p.line);

        put("TabbedPane.background", p.bg);
        put("TabbedPane.selectedBackground", p.surface2);
        put("TabbedPane.hoverColor", p.surface2);
        put("TabbedPane.underlineColor", p.accentHover);
        put("TabbedPane.focusColor", p.surface2);
        put("TabbedPane.contentAreaColor", p.line);

        put("ProgressBar.foreground", p.accent);
        put("ProgressBar.background", p.surface3);
        put("ToolTip.background", p.surface3);
        put("ToolTip.foreground", p.text);
        put("Table.background", p.surface);
        put("Table.selectionBackground", p.accentPressed);
        put("Table.gridColor", p.line);
        put("TableHeader.background", p.surface2);
    }

    private static void put(String key, Color c) {
        UIManager.put(key, c.getAlpha() < 255 ? c : new ColorUIResource(c)); // ColorUIResource губить прозорість
    }

    public static Color lighter(Color c, int d) {
        return new Color(Math.min(255, c.getRed() + d), Math.min(255, c.getGreen() + d), Math.min(255, c.getBlue() + d), c.getAlpha());
    }

    public static Color alpha(Color c, int a) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), a);
    }

    /** Розмір вікна, що влазить у робочу область екрана (без панелі задач) з полями 5%. */
    public static Dimension fitToScreen(Window w, int prefW, int prefH, int minW, int minH) {
        GraphicsConfiguration gc = w.getGraphicsConfiguration();
        if (gc == null) gc = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice().getDefaultConfiguration();
        Rectangle b = gc.getBounds();
        Insets in = Toolkit.getDefaultToolkit().getScreenInsets(gc);
        int aw = (int) ((b.width - in.left - in.right) * 0.95), ah = (int) ((b.height - in.top - in.bottom) * 0.92);
        return new Dimension(Math.max(Math.min(minW, aw), Math.min(prefW, aw)), Math.max(Math.min(minH, ah), Math.min(prefH, ah)));
    }
}
