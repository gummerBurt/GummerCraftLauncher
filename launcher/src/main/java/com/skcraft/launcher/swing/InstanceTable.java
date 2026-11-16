/*
 * SK's Minecraft Launcher
 * Copyright (C) 2010-2014 Albert Pham <http://www.sk89q.com> and contributors
 * Copyright (C) 2021-2025 GummerCraft <http://gummercraft.fun>
 * Please see LICENSE.txt for license information.
 */

package com.skcraft.launcher.swing;

import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableModel;
import java.util.LinkedHashMap;
import java.util.Map;

public class InstanceTable extends DefaultTable {

    public InstanceTable() {
        super();
        setTableHeader(null);
        setShowVerticalLines(false);
        setRowHeight(48);
        setFocusable(false); // don't highlight single cell or row        
    }

    /**
     * GummerCraft 16.11.2026: окрема мітка на кожен текст назви збірки (він в HTML). Спільна мітка розбирала HTML заново
     * для кожного рядка на кожній перемальовці; тепер лише коли текст рядка змінився. Лише для звичайного рендерера.
     */
    private final Map<Object, DefaultTableCellRenderer> htmlLabels = new LinkedHashMap<Object, DefaultTableCellRenderer>(32, 0.75F, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Object, DefaultTableCellRenderer> eldest) {
            return size() > 64;
        }
    };

    @Override
    public TableCellRenderer getCellRenderer(int row, int column) {
        TableCellRenderer base = super.getCellRenderer(row, column);
        if (base != null && base.getClass() == DefaultTableCellRenderer.UIResource.class) {
            Object value = getValueAt(row, column);
            if (value instanceof String && ((String) value).startsWith("<html")) {
                DefaultTableCellRenderer r = htmlLabels.get(value);
                if (r == null) {
                    r = new DefaultTableCellRenderer.UIResource();
                    htmlLabels.put(value, r);
                }
                return r;
            }
        }
        return base;
    }

    @Override
    public void setModel(TableModel dataModel) {
        super.setModel(dataModel);
        try {
            getColumnModel().getColumn(0).setMaxWidth(48);
        } catch (ArrayIndexOutOfBoundsException e) {
        }
    }
}
