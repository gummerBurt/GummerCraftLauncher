/*
 * Copyright (C) 2021-2026 GummerCraft <http://gummercraft.fun>
 */

package com.gummer.launcher.look;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.WindowEvent;

/**
 * Esc закриває вікно-діалог лаунчера (налаштування, акаунт, «Збірка», підтримка тощо) так само, як хрестик
 * (20.11.2026, власник: «в лаунчері ніде не використовується Esc кнопка»). Закриття йде звичайною подією
 * WINDOW_CLOSING, тож спрацьовують ті самі перевірки, що й на хрестику (наприклад, «зберегти зміни?»).
 *
 * <p>Не чіпає: головне вікно; відкрите меню чи випадний список (Esc спершу закриває їх); вікно без хрестика;
 * компонент, що сам обробляє Esc (своя прив'язка клавіші, наприклад редагування в таблиці).</p>
 */
public final class EscCloses {

    private static boolean installed;

    private EscCloses() {
    }

    public static void install() {
        if (installed) return;
        installed = true;
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(EscCloses::dispatch);
    }

    private static boolean dispatch(KeyEvent e) {
        if (e.getID() != KeyEvent.KEY_PRESSED || e.getKeyCode() != KeyEvent.VK_ESCAPE || e.getModifiersEx() != 0) return false;
        if (MenuSelectionManager.defaultManager().getSelectedPath().length > 0) return false;
        Component src = e.getComponent();
        Window w = src instanceof Window ? (Window) src : SwingUtilities.getWindowAncestor(src);
        if (!(w instanceof JDialog) || !w.isShowing()) return false;
        JDialog d = (JDialog) w;
        if (d.getDefaultCloseOperation() == WindowConstants.DO_NOTHING_ON_CLOSE && d.getWindowListeners().length == 0) return false;
        if (d.isUndecorated() && d.getRootPane().getWindowDecorationStyle() == JRootPane.NONE) return false;
        // вікно має свою прив'язку Esc (AboutDialog, LanguageDialog, редактор розкладки): працює вона
        InputMap rim = d.getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        if (rim != null && rim.get(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0)) != null) return false;
        for (Component c = src; c != null && c != d; c = c.getParent()) {
            if (c instanceof JComboBox && ((JComboBox<?>) c).isPopupVisible()) return false;
            if (c instanceof JComponent && c != d.getRootPane() && hasEscBinding((JComponent) c)) return false;
        }
        d.dispatchEvent(new WindowEvent(d, WindowEvent.WINDOW_CLOSING));
        return true;
    }

    /** Компонент сам прив'язав Esc (у фокусі чи в межах вікна): даємо йому відпрацювати. */
    private static boolean hasEscBinding(JComponent c) {
        KeyStroke esc = KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0);
        if (c instanceof JTable && ((JTable) c).isEditing()) return true;
        for (int cond : new int[]{JComponent.WHEN_FOCUSED, JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT}) {
            InputMap im = c.getInputMap(cond);
            if (im == null) continue;
            Object key = im.get(esc);
            if (key != null && c.getActionMap() != null && c.getActionMap().get(key) != null
                    && !(c instanceof JTable) && !(c instanceof JList) && !(c instanceof JTree)
                    && !(c instanceof javax.swing.text.JTextComponent) && !(c instanceof JScrollPane)) {
                return true;
            }
        }
        return false;
    }
}
