/*
 * Copyright (C) 2021-2026 GummerCraft <http://gummercraft.fun>
 */

package com.gummer.launcher.dialog;

import javax.swing.DefaultListCellRenderer;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * 18.11.2026 (власник: «випадаючий список minecraft з fabric-ядром - затуп на пів секунди, доки не зявився список»).
 * Рядок списку версій це HTML-текст, і спільний рендерер на кожному виклику міняв текст, тож Swing щоразу заново
 * розбирав HTML (BasicHTML.createHTMLView) для кожного з ~56 рядків, і при замірі ширини поля, і при показі списку.
 * Тут свій готовий рядок на кожен елемент: якщо текст той самий, setText нічого не робить і HTML не розбирається.
 * Окремо для закритого поля (index < 0) і відкритого списку: у них різний текст і шрифт.
 */
final class HtmlCellCache {

    private final Map<Object, DefaultListCellRenderer> open = new WeakHashMap<Object, DefaultListCellRenderer>();
    private final Map<Object, DefaultListCellRenderer> closed = new WeakHashMap<Object, DefaultListCellRenderer>();
    private final DefaultListCellRenderer none = new DefaultListCellRenderer();

    /**
     * Порахувати рядки і розмір відкритого списку заздалегідь, коли потік інтерфейсу вільний (а не в момент кліку):
     * JList при першому показі міряє кожен рядок. Після заповнення списку версій.
     */
    static void prewarm(final javax.swing.JComboBox<?> box) {
        javax.swing.Timer t = new javax.swing.Timer(120, e -> {
            try {
                Object popup = box.getUI().getAccessibleChild(box, 0);
                if (!(popup instanceof javax.swing.plaf.basic.ComboPopup)) return;
                ((javax.swing.plaf.basic.ComboPopup) popup).getList().getPreferredSize();
                box.getPreferredSize(); // розмір закритого поля теж міряє всі рядки
            } catch (RuntimeException ignored) {
                // не вийшло: порахується при першому показі, як раніше
            }
        });
        t.setRepeats(false);
        t.start();
    }

    DefaultListCellRenderer of(Object value, int index) {
        if (value == null) return none;
        Map<Object, DefaultListCellRenderer> m = index < 0 ? closed : open;
        DefaultListCellRenderer r = m.get(value);
        if (r == null) {
            r = new DefaultListCellRenderer();
            m.put(value, r);
        }
        return r;
    }
}
