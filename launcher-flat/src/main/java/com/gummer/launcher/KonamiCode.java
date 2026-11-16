/*
 * Copyright (C) 2021-2026 GummerCraft <http://gummercraft.fun>
 */

package com.gummer.launcher;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;

/**
 * Прихований вхід у редактор розкладки (16.11.2026, власник: «в білд для гравців додавати редактор на F2 не треба. Додай його на
 * комбінацію клавіш чіт-кодів нінтендо»): ↑ ↑ ↓ ↓ ← → ← → B A у вікні лаунчера. Клавіші не перехоплюються (список збірок
 * так само ходить стрілками), а після коду вибір у списку чи випадаючому меню, де код почали набирати, повертається, як був,
 * щоб редактор відкрився на тій самій збірці. B і A беруться за кодом клавіші, тож працюють і в українській розкладці.
 */
public final class KonamiCode {

    private static final int[] CODE = {KeyEvent.VK_UP, KeyEvent.VK_UP, KeyEvent.VK_DOWN, KeyEvent.VK_DOWN,
            KeyEvent.VK_LEFT, KeyEvent.VK_RIGHT, KeyEvent.VK_LEFT, KeyEvent.VK_RIGHT, KeyEvent.VK_B, KeyEvent.VK_A};

    private KonamiCode() {
    }

    /** Код у вікні {@code window} виконує {@code action}. */
    public static void install(Window window, Runnable action) {
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(new KeyEventDispatcher() {
            private int pos;
            private Component where;
            private int selection = -1;

            @Override
            public boolean dispatchKeyEvent(KeyEvent e) {
                if (e.getID() != KeyEvent.KEY_PRESSED || !window.isDisplayable()) return false;
                if (SwingUtilities.getWindowAncestor(e.getComponent()) != window && e.getComponent() != window) return false;
                int k = e.getKeyCode();
                if (k == KeyEvent.VK_SHIFT || k == KeyEvent.VK_CONTROL || k == KeyEvent.VK_ALT) return false;
                // збій: ↑ може почати код заново (а третя ↑ після ↑↑ лишає набране ↑↑)
                if (k != CODE[pos]) pos = k != CODE[0] ? -1 : pos == 2 ? 1 : 0;
                if (pos < 0) {
                    pos = 0;
                    return false;
                }
                if (pos == 0) remember(e.getComponent());
                if (++pos == CODE.length) {
                    pos = 0;
                    SwingUtilities.invokeLater(() -> {
                        restore();
                        action.run();
                    });
                }
                return false;
            }

            private void remember(Component c) {
                where = c;
                selection = c instanceof JTable ? ((JTable) c).getSelectedRow()
                        : c instanceof JList ? ((JList<?>) c).getSelectedIndex()
                        : c instanceof JComboBox ? ((JComboBox<?>) c).getSelectedIndex() : -1;
            }

            private void restore() {
                if (where == null || selection < 0) return;
                try {
                    if (where instanceof JTable && selection < ((JTable) where).getRowCount()) {
                        ((JTable) where).setRowSelectionInterval(selection, selection);
                    } else if (where instanceof JList && selection < ((JList<?>) where).getModel().getSize()) {
                        ((JList<?>) where).setSelectedIndex(selection);
                    } else if (where instanceof JComboBox && selection < ((JComboBox<?>) where).getItemCount()) {
                        ((JComboBox<?>) where).setSelectedIndex(selection);
                    }
                } catch (RuntimeException ignored) {
                    // список міг змінитись за час набору коду: тоді лишаємо як є
                }
            }
        });
    }
}
