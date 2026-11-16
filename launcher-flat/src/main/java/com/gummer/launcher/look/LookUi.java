/*
 * Copyright (C) 2021-2026 GummerCraft <http://gummercraft.fun>
 */

package com.gummer.launcher.look;

import net.miginfocom.swing.MigLayout;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;

/**
 * 16.11.2026: спільні шматки вікон лаунчера, що малюються в стилі поточного вигляду ({@link LookTheme}):
 * картка розділу, бічна навігація, заголовки, текст з переносом, головна і звичайна кнопки, оформлення діалогу.
 */
public final class LookUi {

    private LookUi() {
    }

    /** Оформити діалог під поточний вигляд: у Liquid Glass скляне тло з головного вікна. Викликати в кінці конструктора. */
    public static void decorate(JDialog d, Window owner) {
        LookTheme.Palette p = LookTheme.palette();
        if (p.glass()) {
            Backdrop.install(d, owner);
        } else {
            d.getContentPane().setBackground(p.bg);
        }
    }

    /** Картка розділу: у склі напівпрозора з тонкою світлою рамкою, у Класиці поверхня з лінією, у Бібліотеці без рамки. */
    public static JPanel card(String layout, String cols, String rows) {
        JPanel c = new JPanel(new MigLayout(layout, cols, rows)) {
            @Override
            protected void paintComponent(Graphics g) {
                LookTheme.Palette p = LookTheme.palette();
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int r = p.radius * 2 > getHeight() ? getHeight() / 2 : p.radius;
                if (p.glass()) {
                    g2.setColor(new Color(255, 255, 255, 14));
                    g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, r * 2, r * 2);
                    g2.setColor(new Color(255, 255, 255, 30));
                    g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, r * 2, r * 2);
                } else {
                    g2.setColor(p.surface);
                    g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, r * 2, r * 2);
                    if (!p.library()) {
                        g2.setColor(p.line);
                        g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, r * 2, r * 2);
                    }
                }
                g2.dispose();
            }
        };
        c.setOpaque(false);
        return c;
    }

    public static JLabel title(String text, float size) {
        JLabel l = new JLabel(text);
        l.setFont(UIManager.getFont("Label.font").deriveFont(Font.BOLD, size));
        l.setForeground(LookTheme.palette().text);
        return l;
    }

    /** Текст, що переноситься по ширині (JLabel з html ламався при масштабі Windows). */
    public static JTextArea body(String text, boolean dim) {
        JTextArea a = new JTextArea(stripHtml(text));
        a.setLineWrap(true);
        a.setWrapStyleWord(true);
        a.setEditable(false);
        a.setFocusable(false);
        a.setOpaque(false);
        a.setBorder(null);
        a.setFont(UIManager.getFont("Label.font").deriveFont(dim ? 12.5f : 13.5f));
        a.setForeground(dim ? LookTheme.palette().text3 : LookTheme.palette().text2);
        return a;
    }

    public static String stripHtml(String s) {
        if (s == null) return "";
        return s.replaceAll("(?i)<br\\s*/?>", " ").replaceAll("<[^>]+>", "").replace("&nbsp;", " ").replace("&amp;", "&").trim();
    }

    /** Головна дія (зелена). */
    public static JButton primary(JButton b) {
        LookTheme.Palette p = LookTheme.palette();
        b.putClientProperty("FlatLaf.style", "background: " + hex(p.accent) + "; foreground: #FFFFFF; hoverBackground: "
                + hex(p.accentHover) + "; pressedBackground: " + hex(p.accentPressed) + "; borderColor: " + hex(p.accent)
                + "; hoverBorderColor: " + hex(p.accentHover) + "; focusedBorderColor: " + hex(p.accentText)
                + "; font: bold; arc: " + p.buttonArc);
        return b;
    }

    /** Звичайна дія. */
    public static JButton secondary(JButton b) {
        LookTheme.Palette p = LookTheme.palette();
        if (p.glass()) {
            b.putClientProperty("FlatLaf.style", "background: #2A2E35; hoverBackground: #353A42; borderColor: #3E434C; arc: " + p.buttonArc);
        } else {
            b.putClientProperty("FlatLaf.style", "arc: " + p.buttonArc);
        }
        return b;
    }

    public static String hex(Color c) {
        return String.format("#%02X%02X%02X", c.getRed(), c.getGreen(), c.getBlue());
    }

    /**
     * Бічна навігація розділів. У склі і Бібліотеці вибраний пункт «таблеткою», у Класиці смужка акценту зліва.
     */
    public static final class Nav extends JPanel {
        private final List<Item> items = new ArrayList<Item>();
        private final IntConsumer onSelect;
        private int selected;

        public Nav(String[] labels, IntConsumer onSelect) {
            super(new MigLayout("insets 0, wrap 1, gap 0 4, fillx", "[grow,fill]", ""));
            this.onSelect = onSelect;
            setOpaque(false);
            for (int i = 0; i < labels.length; i++) {
                Item it = new Item(labels[i], i);
                items.add(it);
                add(it, "h 38!");
            }
        }

        public void select(int i) {
            selected = i;
            for (Item it : items) it.repaint();
            onSelect.accept(i);
        }

        private final class Item extends JComponent {
            private final String label;
            private final int index;
            private boolean hover;

            Item(String label, int index) {
                this.label = label;
                this.index = index;
                setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                setFont(UIManager.getFont("Label.font").deriveFont(Font.PLAIN, 13.5f));
                setFocusable(true);
                addMouseListener(new MouseAdapter() {
                    @Override
                    public void mouseEntered(MouseEvent e) {
                        hover = true;
                        repaint();
                    }

                    @Override
                    public void mouseExited(MouseEvent e) {
                        hover = false;
                        repaint();
                    }

                    @Override
                    public void mousePressed(MouseEvent e) {
                        select(index);
                    }
                });
                setToolTipText(null);
            }

            @Override
            protected void paintComponent(Graphics g) {
                LookTheme.Palette p = LookTheme.palette();
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                int w = getWidth(), h = getHeight();
                boolean on = selected == index;
                int arc = p.library() ? h : p.glass() ? 12 : 6;
                if (on) {
                    if (p.glass()) {
                        g2.setColor(new Color(255, 255, 255, 30));
                        g2.fillRoundRect(0, 0, w, h, arc, arc);
                        g2.setColor(new Color(255, 255, 255, 46));
                        g2.drawRoundRect(0, 0, w - 1, h - 1, arc, arc);
                    } else if (p.library()) {
                        g2.setColor(p.surface3);
                        g2.fillRoundRect(0, 0, w, h, arc, arc);
                    } else {
                        g2.setColor(p.surface2);
                        g2.fillRoundRect(0, 0, w, h, arc, arc);
                        g2.setColor(p.accentHover);
                        g2.fillRoundRect(0, 6, 3, h - 12, 3, 3);
                    }
                } else if (hover) {
                    g2.setColor(p.glass() ? new Color(255, 255, 255, 14) : p.surface);
                    g2.fillRoundRect(0, 0, w, h, arc, arc);
                }
                Font f = on ? getFont().deriveFont(Font.BOLD) : getFont();
                g2.setFont(f);
                FontMetrics fm = g2.getFontMetrics();
                g2.setColor(on ? p.text : hover ? p.text : p.text2);
                g2.drawString(label, p.library() ? 16 : 14, (h - fm.getHeight()) / 2 + fm.getAscent());
                g2.dispose();
            }
        }
    }
}
