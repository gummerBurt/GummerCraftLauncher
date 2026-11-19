/*
 * Copyright (C) 2021-2026 GummerCraft <http://gummercraft.fun>
 */

package com.gummer.launcher.look;

import com.skcraft.launcher.util.SharedLocale;
import net.miginfocom.swing.MigLayout;

import javax.swing.*;

/**
 * 19.11.2026 (власник: «Якщо жодного модпака ще не встановлено, клік на кнопку "встановлені" просто збивається»).
 * Раніше в порожніх «Встановлених» був лише рядок «+ Додати збірку», лаунчер сам його вибирав і одразу перемикав
 * список назад на «Усі». Тепер «Встановлені» лишаються відкритими, а в головній області ця картка: що тут буде і кнопка
 * до всіх збірок. Однакова в трьох виглядах.
 */
public final class EmptyInstalled extends JPanel {

    /** @param importOther 19.11.2026: «Імпортувати з іншого лаунчера» (ImportOtherDialog), null: без кнопки */
    public EmptyInstalled(final Runnable showAll, final Runnable importOther) {
        super(new MigLayout("insets 0, fill", "push[460!]push", "push[]push"));
        setOpaque(false);
        JPanel card = LookUi.card("insets 22 24 22 24, wrap 1, gap 0 8, fillx", "[grow,fill]", "");
        card.add(LookUi.title(SharedLocale.tr("launcher.emptyInstalled.title"), 17f));
        card.add(LookUi.body(SharedLocale.tr("launcher.emptyInstalled.desc"), false), "wmin 0");
        JButton go = new JButton(SharedLocale.tr("launcher.emptyInstalled.button"));
        LookUi.primary(go);
        go.addActionListener(e -> showAll.run());
        if (importOther == null) {
            card.add(go, "growx 0, h 36!, gaptop 10");
        } else {
            JButton imp = LookUi.secondary(new JButton(SharedLocale.tr("import.emptyButton")));
            imp.addActionListener(e -> importOther.run());
            JPanel row = new JPanel(new MigLayout("insets 0, gap 8", "[][]push", "[36!]"));
            row.setOpaque(false);
            row.add(go, "h 36!");
            row.add(imp, "h 36!");
            card.add(row, "gaptop 10");
        }
        add(card, "growx");
    }
}
