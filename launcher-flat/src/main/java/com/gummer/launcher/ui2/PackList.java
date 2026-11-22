/*
 * Copyright (C) 2021-2026 GummerCraft <http://gummercraft.fun>
 */

package com.gummer.launcher.ui2;

import javax.swing.*;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.function.IntFunction;

/**
 * Редизайн 2: вигляд списку збірок у рейці. Таблиця лишається тією ж {@code InstanceTable} (вибір, контекстне меню,
 * подвійний клік працюють як раніше), змінюється лише малювання рядка: іконка 32, назва, під нею стан, праворуч
 * онлайн сервера. Над першим рядком кожного розділу (сервери, Minecraft) підпис розділу: рядок вищий на
 * {@link #HEADER_H}. Вибраний рядок: світліша поверхня і зелена смужка ліворуч; під мишкою: ледь світліша.
 */
public final class PackList {

    public static final int ROW_H = 52;
    public static final int HEADER_H = 30;

    /** Що показати в рядку. */
    public static final class Row {
        public Icon icon;
        public String title;
        public String sub;
        public Color subColor = Ui.TEXT_2;
        /** Онлайн праворуч («12»), або null. */
        public String online;
        /** Підпис розділу над рядком, або null. */
        public String header;
        /** Підказка під мишкою (17.11.2026: що є оновлення), або null. */
        public String tooltip;
        /** 21.11.2026: підпис розділу згортається кліком (стрілка поруч), розділ згорнутий, рядок лише підпис згорнутого розділу. */
        public boolean collapsible, collapsed, stub;
    }

    private PackList() {
    }

    /** Підключити вигляд до таблиці: rows(r) дає вміст рядка r (індекс моделі). */
    public static void install(final JTable table, final IntFunction<Row> rows) {
        table.setShowGrid(false);
        table.setIntercellSpacing(new Dimension(0, 0));
        table.setBackground(Ui.SURFACE);
        table.setOpaque(true);
        table.setFillsViewportHeight(true);
        // лише одна видима колонка (назва): іконку малює той самий рядок
        if (table.getColumnModel().getColumnCount() > 1) {
            table.setAutoCreateColumnsFromModel(false);
            table.removeColumn(table.getColumnModel().getColumn(0));
        }
        final int[] hover = {-1};
        final Cell cell = new Cell();
        table.setDefaultRenderer(Object.class, cell);
        table.getColumnModel().getColumn(0).setCellRenderer(cell);
        cell.rows = rows;
        cell.hover = hover;
        MouseAdapter tracker = new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                int r = table.rowAtPoint(e.getPoint());
                if (r != hover[0]) {
                    hover[0] = r;
                    table.repaint();
                }
            }

            @Override
            public void mouseExited(MouseEvent e) {
                hover[0] = -1;
                table.repaint();
            }
        };
        table.addMouseListener(tracker);
        table.addMouseMotionListener(tracker);
        table.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        // клавіатура: стрілки вгору і вниз міняють збірку, Enter запускає (рамка фокусу малюється в рядку)
        table.setFocusable(true);
        table.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override
            public void focusGained(java.awt.event.FocusEvent e) {
                table.repaint();
            }

            @Override
            public void focusLost(java.awt.event.FocusEvent e) {
                table.repaint();
            }
        });
    }

    /** Висоти рядків: вищі там, де над рядком підпис розділу. Викликати після кожної зміни моделі. */
    public static void updateHeights(JTable table, IntFunction<Row> rows) {
        for (int r = 0; r < table.getRowCount(); r++) {
            Row row = rows.apply(r);
            int h = row != null && row.stub ? HEADER_H : ROW_H + (row != null && row.header != null ? HEADER_H : 0);
            if (table.getRowHeight(r) != h) table.setRowHeight(r, h);
        }
    }

    private static final class Cell extends JComponent implements TableCellRenderer {
        IntFunction<Row> rows;
        int[] hover;
        private Row row;
        private boolean selected, hovered, focused;

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int r, int column) {
            row = rows.apply(r);
            setToolTipText(row != null ? row.tooltip : null);
            selected = isSelected;
            hovered = r == hover[0];
            focused = table.isFocusOwner() && isSelected;
            return this;
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = Ui.smooth(g);
            try {
                int w = getWidth(), h = getHeight();
                g2.setColor(Ui.SURFACE);
                g2.fillRect(0, 0, w, h);
                if (row == null) return;
                int top = 0;
                if (row.header != null) {
                    g2.setFont(Ui.font(Font.BOLD, 11f));
                    g2.setColor(Ui.TEXT_3);
                    FontMetrics fm = g2.getFontMetrics();
                    String head = row.header.toUpperCase(java.util.Locale.ROOT);
                    // розрідження букв для підпису розділу
                    int x = Ui.S5;
                    int y = HEADER_H - Ui.S2 - fm.getDescent();
                    for (char c : head.toCharArray()) {
                        g2.drawString(String.valueOf(c), x, y);
                        x += fm.charWidth(c) + 1;
                    }
                    if (row.collapsible) {
                        com.gummer.launcher.dialog.CardInstanceTable.caret(g2, x + Ui.S3, y, fm.getAscent() - 3, row.collapsed);
                    }
                    top = HEADER_H;
                }
                if (row.stub) return; // 21.11.2026: згорнутий розділ, лише підпис
                int rx = Ui.S3, rw = w - Ui.S3 * 2, rh = ROW_H - Ui.S1, ry = top + Ui.S1 / 2;
                if (selected || hovered) {
                    g2.setColor(selected ? Ui.SURFACE_3 : Ui.SURFACE_2);
                    g2.fillRoundRect(rx, ry, rw, rh, Ui.RADIUS, Ui.RADIUS);
                }
                if (selected) {
                    g2.setColor(Ui.ACCENT_HOVER);
                    g2.fillRoundRect(rx, ry + Ui.S3, 3, rh - Ui.S3 * 2, 3, 3);
                }
                if (focused) {
                    g2.setColor(Ui.FOCUS);
                    g2.setStroke(new BasicStroke(1.5f));
                    g2.drawRoundRect(rx, ry, rw - 1, rh - 1, Ui.RADIUS, Ui.RADIUS);
                }
                int ix = rx + Ui.S3;
                if (row.icon != null) {
                    int iy = ry + (rh - 32) / 2;
                    // іконки збірок це CrispIcon (малюються у фізичних пікселях, без розтягування на 125%)
                    row.icon.paintIcon(this, g2, ix + (32 - row.icon.getIconWidth()) / 2, iy + (32 - row.icon.getIconHeight()) / 2);
                }
                int tx = ix + 32 + Ui.S3;
                int right = rx + rw - Ui.S3;
                // онлайн праворуч
                if (row.online != null) {
                    g2.setFont(Ui.font(Font.BOLD, 12f));
                    FontMetrics fo = g2.getFontMetrics();
                    int ow = fo.stringWidth(row.online);
                    int oy = ry + rh / 2 + fo.getAscent() / 2 - 1;
                    g2.setColor(Ui.ONLINE);
                    g2.drawString(row.online, right - ow, oy);
                    g2.fillOval(right - ow - 10, oy - fo.getAscent() / 2 - 2, 6, 6);
                    right -= ow + 10 + Ui.S2;
                }
                Font titleFont = Ui.font(Font.BOLD, 14f);
                Font subFont = Ui.font(Font.PLAIN, 12f);
                FontMetrics ft = g2.getFontMetrics(titleFont);
                FontMetrics fs = g2.getFontMetrics(subFont);
                boolean sub = row.sub != null && !row.sub.isEmpty();
                int blockH = ft.getAscent() + (sub ? 2 + fs.getAscent() : 0);
                int ty = ry + (rh - blockH) / 2 + ft.getAscent();
                g2.setFont(titleFont);
                g2.setColor(selected ? Ui.TEXT : Ui.alpha(Ui.TEXT, 235));
                g2.drawString(Ui.fit(row.title, ft, right - tx), tx, ty);
                if (sub) {
                    g2.setFont(subFont);
                    g2.setColor(row.subColor);
                    g2.drawString(Ui.fit(row.sub, fs, right - tx), tx, ty + 2 + fs.getAscent());
                }
            } finally {
                g2.dispose();
            }
        }
    }
}
