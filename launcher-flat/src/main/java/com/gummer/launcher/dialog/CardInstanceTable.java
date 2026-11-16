/*
 * Copyright (C) 2021-2026 GummerCraft <http://gummercraft.fun>
 */

package com.gummer.launcher.dialog;

import com.gummer.launcher.PackLook;
import com.skcraft.launcher.Instance;
import com.skcraft.launcher.swing.InstanceTable;
import com.skcraft.launcher.swing.InstanceTableModel;

import javax.swing.*;
import javax.swing.event.TableModelEvent;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import static com.skcraft.launcher.util.SharedLocale.tr;

/**
 * Дизайн 1.35: список збірок картками. Кожен рядок це заокруглена картка, вибрана підсвічена кольором своєї збірки
 * (смужка ліворуч), під мишею світліша. Над першим сервером і над першою збіркою для одиночної гри підпис розділу.
 */
public class CardInstanceTable extends InstanceTable {

    private static final int ROW_H = 46;
    private static final int CAPTION_H = 22;
    private static final int GAP = 3;

    private int hoverRow = -1;

    private static final Color TEXT = new Color(222, 222, 222);
    /** Рамки клітинок: [є підпис розділу][перша колонка]. */
    private static final javax.swing.border.Border[][] BORDERS = {
            {BorderFactory.createEmptyBorder(0, 0, 0, 0), BorderFactory.createEmptyBorder(0, 6, 0, 0)},
            {BorderFactory.createEmptyBorder(CAPTION_H, 0, 0, 0), BorderFactory.createEmptyBorder(CAPTION_H, 6, 0, 0)}};

    /**
     * 16.11.2026: окрема мітка на кожен текст рядка (назва збірки в HTML). Спільна мітка розбирала HTML заново для
     * кожного рядка на кожній перемальовці (і ще раз при зміні кольору вибраного), тепер лише коли текст змінився.
     */
    private final java.util.Map<Object, javax.swing.table.DefaultTableCellRenderer> htmlLabels =
            new java.util.LinkedHashMap<Object, javax.swing.table.DefaultTableCellRenderer>(32, 0.75F, true) {
                @Override
                protected boolean removeEldestEntry(java.util.Map.Entry<Object, javax.swing.table.DefaultTableCellRenderer> eldest) {
                    return size() > 64;
                }
            };

    public CardInstanceTable() {
        setRowHeight(ROW_H);
        setSelectionBackground(new Color(0, 0, 0, 0));
        MouseAdapter hover = new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                setHover(rowAtPoint(e.getPoint()));
            }

            @Override
            public void mouseExited(MouseEvent e) {
                setHover(-1);
            }
        };
        addMouseListener(hover);
        addMouseMotionListener(hover);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }

    private void setHover(int row) {
        if (row != hoverRow) {
            int old = hoverRow;
            hoverRow = row;
            // лише дві картки (стара і нова під мишею), не весь список
            repaintRow(old);
            repaintRow(row);
        }
    }

    private void repaintRow(int row) {
        if (row >= 0 && row < getRowCount()) {
            Rectangle r = getCellRect(row, 0, true);
            repaint(0, r.y, getWidth(), r.height);
        }
    }

    @Override
    public TableCellRenderer getCellRenderer(int row, int column) {
        Object value = getValueAt(row, column);
        if (value instanceof String && ((String) value).startsWith("<html")) {
            return htmlLabels.computeIfAbsent(value, v -> new javax.swing.table.DefaultTableCellRenderer() {
                @Override
                public Component getTableCellRendererComponent(JTable table, Object v2, boolean sel, boolean focus, int r, int c) {
                    // колір і рамку ставить prepareRenderer; текст той самий, тож HTML не розбирається
                    setFont(table.getFont());
                    setValue(v2);
                    return this;
                }
            });
        }
        return super.getCellRenderer(row, column);
    }

    @Override
    public void tableChanged(TableModelEvent e) {
        super.tableChanged(e);
        // висота рядків: над першим рядком розділу місце під підпис (після JTable, яка скидає висоти)
        SwingUtilities.invokeLater(this::updateRowHeights);
    }

    private void updateRowHeights() {
        for (int row = 0; row < getRowCount(); row++) {
            int h = ROW_H + (captionFor(row) != null ? CAPTION_H : 0);
            if (getRowHeight(row) != h) setRowHeight(row, h);
        }
    }

    private Instance instanceAt(int row) {
        if (!(getModel() instanceof InstanceTableModel) || row < 0 || row >= getRowCount()) return null;
        try {
            return ((InstanceTableModel) getModel()).getInstanceAt(convertRowIndexToModel(row));
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** Підпис розділу над рядком або null. */
    private String captionFor(int row) {
        Instance inst = instanceAt(row);
        if (inst == null) return null;
        Instance prev = row > 0 ? instanceAt(row - 1) : null;
        boolean builtIn = inst.isBuiltIn();
        if (prev != null && prev.isBuiltIn() == builtIn) return null;
        return tr(builtIn ? "look.section.local" : "look.section.servers");
    }

    @Override
    public Component prepareRenderer(TableCellRenderer renderer, int row, int column) {
        Component c = super.prepareRenderer(renderer, row, column);
        if (c instanceof JComponent) {
            JComponent jc = (JComponent) c;
            jc.setOpaque(false);
            // картка ширша за клітинку на GAP згори і знизу: текст по центру картки
            jc.setBorder(BORDERS[captionFor(row) != null ? 1 : 0][column == 0 ? 1 : 0]);
        }
        c.setForeground(isRowSelected(row) ? Color.WHITE : TEXT);
        return c;
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            Rectangle clip = g2.getClipBounds();
            int width = getWidth();
            for (int row = 0; row < getRowCount(); row++) {
                Rectangle r = getCellRect(row, 0, true);
                if (clip != null && (r.y > clip.y + clip.height || r.y + r.height < clip.y)) continue;
                String caption = captionFor(row);
                int top = r.y + (caption != null ? CAPTION_H : 0);
                if (caption != null) {
                    g2.setFont(getFont().deriveFont(Font.BOLD, 11f));
                    g2.setColor(new Color(255, 255, 255, 135));
                    g2.drawString(caption, 4, r.y + CAPTION_H - 7);
                }
                int x = 1, y = top + GAP / 2, w = width - 2, h = ROW_H - GAP;
                boolean selected = isRowSelected(row);
                Color accent = PackLook.accentOf(instanceAt(row));
                if (selected) {
                    g2.setColor(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 52));
                    g2.fillRoundRect(x, y, w, h, 10, 10);
                    g2.setColor(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 150));
                    g2.drawRoundRect(x, y, w - 1, h - 1, 10, 10);
                    g2.setColor(accent);
                    g2.fillRoundRect(x + 1, y + 7, 3, h - 14, 3, 3);
                } else {
                    g2.setColor(row == hoverRow ? new Color(255, 255, 255, 30) : new Color(255, 255, 255, 11));
                    g2.fillRoundRect(x, y, w, h, 10, 10);
                }
            }
        } finally {
            g2.dispose();
        }
        super.paintComponent(g);
    }
}
