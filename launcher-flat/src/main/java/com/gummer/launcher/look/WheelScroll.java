/*
 * Copyright (C) 2021-2026 GummerCraft <http://gummercraft.fun>
 */

package com.gummer.launcher.look;

import javax.swing.*;
import java.awt.event.MouseWheelEvent;

/**
 * Скрол коліщатком як на сторінці Modrinth / CurseForge: одна «лінія» коліщатка це {@link #UNIT_PX} px (там
 * {@code setUnitIncrement(24)}), Windows за поділку дає 3 лінії, тобто 72 px, одразу, без анімації.
 *
 * <p>Навіщо окремий клас. Список збірок це JTable, а JScrollPane для такого вмісту бере за лінію висоту цілої картки
 * (46-68 px), тож поділка пробігала 2-3 збірки (власник 25.11.2026: «скрол пробігає 2-3 модпаки за раз»).
 * {@code setUnitIncrement} тут не допомагає: BasicScrollPaneUI для Scrollable вмісту питає сам JTable.
 *
 * <p>Історія 25.11.2026. Спершу був плавний скрол з анімацією (SmoothScroll): поділка на одну картку і доїзд за
 * 0,17-0,3 с. Власник двічі сказав, що він «лоу-фпс» (замір показував ~60 кадрів на секунду і малювання 7-10 мс, але
 * дрібні кроки анімації око бачило як ривки), і попросив «такий самий швидкий скрол, як у модпаках у вкладці
 * CurseForge». Тому анімації немає: кожна подія коліщатка одразу ставить смугу на місце.
 *
 * <p>Тачпад дає дробові поділки: залишок менше пікселя накопичується, тож повільний рух пальцями теж гортає.
 */
public final class WheelScroll {

    /** Як {@code scroll.getVerticalScrollBar().setUnitIncrement(24)} на сторінці Modrinth / CurseForge. */
    public static final int UNIT_PX = 24;

    private WheelScroll() {
    }

    /** @param horizontal гортати горизонтальну смугу (полиця Бібліотеки), інакше вертикальну */
    public static void install(final JScrollPane scroll, final boolean horizontal) {
        final JScrollBar bar = horizontal ? scroll.getHorizontalScrollBar() : scroll.getVerticalScrollBar();
        scroll.setWheelScrollingEnabled(false); // інакше JScrollPane ще й сам гортає на 3 картки
        final double[] rest = {0}; // дробовий залишок від тачпада
        scroll.addMouseWheelListener(e -> {
            if (e.getScrollType() != MouseWheelEvent.WHEEL_UNIT_SCROLL && e.getScrollType() != MouseWheelEvent.WHEEL_BLOCK_SCROLL) return;
            double rot = e.getPreciseWheelRotation();
            if (rot == 0) return;
            int max = Math.max(bar.getMinimum(), bar.getMaximum() - bar.getVisibleAmount());
            if (max <= bar.getMinimum()) return; // усе влазить, гортати нічого
            double lines = e.getScrollType() == MouseWheelEvent.WHEEL_BLOCK_SCROLL ? 0 : Math.max(1, e.getScrollAmount());
            double px = e.getScrollType() == MouseWheelEvent.WHEEL_BLOCK_SCROLL
                    ? rot * bar.getVisibleAmount() : rot * lines * UNIT_PX;
            px += rest[0];
            int step = (int) px; // до нуля
            rest[0] = px - step;
            if (step != 0) bar.setValue(Math.max(bar.getMinimum(), Math.min(max, bar.getValue() + step)));
            e.consume();
        });
    }
}
