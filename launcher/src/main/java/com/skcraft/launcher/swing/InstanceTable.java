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
        // GummerCraft 19.11.2026 (власник: «Коли я тисну "імпорт збірки" ... на мілісекунду показується бекграунд
        // модрінта»): рядок «Імпорт збірки» не вибирається взагалі, тож ні фон, ні сторінка Modrinth, ні кнопки не
        // встигають змінитись; замість вибору викликається onImportRow
        setSelectionModel(new javax.swing.DefaultListSelectionModel() {
            @Override
            public void setSelectionInterval(int index0, int index1) {
                if (importRowAt(index1) || stubRowAt(index1)) return;
                super.setSelectionInterval(index0, index1);
            }

            @Override
            public void addSelectionInterval(int index0, int index1) {
                if (importRowAt(index1) || stubRowAt(index1)) return;
                super.addSelectionInterval(index0, index1);
            }
        });
        setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION); // як у DefaultTable, для нової моделі
    }

    /**
     * 21.11.2026 (власник: «згортати та розгортати категорії кліканням на назву категорії»): висота підпису розділу над
     * рядком у пікселях (0, якщо підпису немає). Задає вигляд, що вміє згортати розділи «Встановлених»; null: згортання немає.
     * Клік лівою по підпису згортає чи розгортає розділ і рядок не вибирає; згорнутий розділ це рядок-заглушка лише з підписом.
     */
    public java.util.function.IntUnaryOperator captionHeight;

    @Override
    protected void processMouseEvent(java.awt.event.MouseEvent e) {
        if (captionHeight != null && getModel() instanceof InstanceTableModel) {
            int viewRow = rowAtPoint(e.getPoint());
            if (viewRow >= 0) {
                InstanceTableModel m = (InstanceTableModel) getModel();
                final int row = convertRowIndexToModel(viewRow);
                if (m.isToggleCaptionAt(row)) {
                    java.awt.Rectangle r = getCellRect(viewRow, 0, true);
                    if (m.isCollapsedStubAt(row) || e.getY() - r.y < captionHeight.applyAsInt(viewRow)) {
                        if (e.getID() == java.awt.event.MouseEvent.MOUSE_PRESSED && javax.swing.SwingUtilities.isLeftMouseButton(e)) {
                            javax.swing.SwingUtilities.invokeLater(() -> m.toggleSectionAt(row));
                        }
                        e.consume();
                        return;
                    }
                }
            }
        }
        super.processMouseEvent(e);
    }

    /** 21.11.2026: рядок-заглушка згорнутого розділу не вибирається; стрілки його перестрибують. */
    private boolean stubRowAt(int viewRow) {
        if (viewRow < 0 || !(getModel() instanceof InstanceTableModel)) return false;
        InstanceTableModel m = (InstanceTableModel) getModel();
        int row;
        try {
            row = convertRowIndexToModel(viewRow);
        } catch (IndexOutOfBoundsException e) {
            return false;
        }
        if (!m.isCollapsedStubAt(row)) return false;
        if (java.awt.EventQueue.getCurrentEvent() instanceof java.awt.event.KeyEvent) {
            int sel = getSelectedRow();
            int dir = sel >= 0 && sel > viewRow ? -1 : 1;
            int next = viewRow + dir;
            while (next >= 0 && next < getRowCount() && m.isCollapsedStubAt(convertRowIndexToModel(next))) next += dir;
            if (next >= 0 && next < getRowCount() && next != sel) {
                final int n = next;
                javax.swing.SwingUtilities.invokeLater(() -> {
                    setRowSelectionInterval(n, n);
                    scrollRectToVisible(getCellRect(n, 0, true));
                });
            }
        }
        return true;
    }

    /** 19.11.2026: що робити, коли гравець натиснув рядок «Імпорт збірки» (вікно імпорту). */
    public Runnable onImportRow;

    private boolean importRowAt(int viewRow) {
        if (viewRow < 0 || !(getModel() instanceof InstanceTableModel)) return false;
        int row;
        try {
            row = convertRowIndexToModel(viewRow);
        } catch (IndexOutOfBoundsException e) {
            return false;
        }
        InstanceTableModel m = (InstanceTableModel) getModel();
        if (row >= m.getRowCount() || !InstanceTableModel.isImportRow(m.getInstanceAt(row))) return false;
        // 21.11.2026: стрілки (клавіатура, геймпад Steam Deck) рядок імпорту перестрибують, вікно не відкривають:
        // інакше прохід списком униз відкривав «Імпорт з інших лаунчерів» сам. Вікно лише на клік мишею
        java.awt.AWTEvent ev = java.awt.EventQueue.getCurrentEvent();
        if (ev instanceof java.awt.event.KeyEvent) {
            int sel = getSelectedRow();
            int next = viewRow + (sel >= 0 && sel > viewRow ? -1 : 1);
            if (next >= 0 && next < getRowCount() && next != sel) {
                final int n = next;
                javax.swing.SwingUtilities.invokeLater(() -> {
                    setRowSelectionInterval(n, n);
                    scrollRectToVisible(getCellRect(n, 0, true));
                });
            }
            return true;
        }
        Runnable r = onImportRow;
        if (r != null) javax.swing.SwingUtilities.invokeLater(r);
        return true;
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
