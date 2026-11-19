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
    private static final Color GLASS_FILL = new Color(255, 255, 255, 14), GLASS_LINE = new Color(255, 255, 255, 30);

    public static JPanel card(String layout, String cols, String rows) {
        JPanel c = new JPanel(new MigLayout(layout, cols, rows)) {
            @Override
            protected void paintComponent(Graphics g) {
                LookTheme.Palette p = LookTheme.palette();
                Graphics2D g2 = (Graphics2D) g.create();
                int r = p.radius * 2 > getHeight() ? getHeight() / 2 : p.radius;
                // 17.11.2026: готова картинка замість згладженої фігури на кожен кадр (RoundBg)
                if (p.glass()) {
                    RoundBg.paint(g2, getWidth(), getHeight(), r * 2, GLASS_FILL, GLASS_LINE, 1);
                } else {
                    RoundBg.paint(g2, getWidth(), getHeight(), r * 2, p.surface, p.library() ? null : p.line, 1);
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

    /**
     * 18.11.2026 (власник: «Нова збірка з цим модом» не спрацьовує; лог: NullPointerException «this.xTotal is null» у
     * BoxLayout вікна JOptionPane). Панель MigLayout з текстом, що переноситься, рахує розмір у два проходи, і між ними
     * MigLayout.adjustWindowSize переміряв усе вікно JOptionPane посеред його ж розрахунку. Перед показом у JOptionPane
     * розмір такої панелі рахується тут, поки вона ще без вікна, і фіксується.
     */
    public static <T extends JComponent> T freeze(T c) {
        for (int i = 0; i < 3; i++) {
            Dimension d = c.getPreferredSize();
            c.setSize(d);
            c.doLayout();
            for (Component k : c.getComponents()) if (k instanceof Container) ((Container) k).doLayout();
        }
        c.setPreferredSize(c.getPreferredSize());
        return c;
    }

    /** Текст, що переноситься по ширині (JLabel з html ламався при масштабі Windows). */
    public static JTextArea body(String text, boolean dim) {
        // 17.11.2026: текст лише для читання не прокручує сторінку до своєї каретки (інакше сторінка «Моди» і пошук
        // Modrinth, що наповнюються у фоні, відкривались прокрученими на середину)
        JTextArea a = new JTextArea(plainText(stripHtml(text))) {
            @Override
            public void scrollRectToVisible(Rectangle r) {
            }

            // 19.11.2026 (власник: «якщо швидко перемикатись в "збірка" між кнопками ресурспаки шейдери моди -
            // залагує на пів секунди», Pixelmon): MigLayout на кожній розкладці питає базову лінію кожного тексту, а
            // JTextArea з переносом рядків щоразу наново розкладає весь свій текст (BoxView.setSize). 20 описів з
            // Modrinth давали ~1,7 с у потоці інтерфейсу. Текст тут завжди зверху, тож базова лінія це базова лінія
            // першого рядка: відступ плюс висота шрифту над лінією, без розкладки (вирівнювання те саме, що і було)
            @Override
            public int getBaseline(int width, int height) {
                if (width < 0 || height < 0) return super.getBaseline(width, height);
                return getInsets().top + getFontMetrics(getFont()).getAscent();
            }

            @Override
            public Component.BaselineResizeBehavior getBaselineResizeBehavior() {
                return Component.BaselineResizeBehavior.CONSTANT_ASCENT;
            }
        };
        a.setLineWrap(true);
        a.setWrapStyleWord(true);
        a.setEditable(false);
        a.setFocusable(false);
        a.setOpaque(false);
        a.setBorder(null);
        // 17.11.2026: каретка тексту, доданого у фоні (список модів, Modrinth), не прокручує сторінку до себе
        if (a.getCaret() instanceof javax.swing.text.DefaultCaret) {
            ((javax.swing.text.DefaultCaret) a.getCaret()).setUpdatePolicy(javax.swing.text.DefaultCaret.NEVER_UPDATE);
        }
        a.setFont(UIManager.getFont("Label.font").deriveFont(dim ? 12.5f : 13.5f));
        a.setForeground(dim ? LookTheme.palette().text3 : LookTheme.palette().text2);
        return a;
    }

    /**
     * 17.11.2026: без емодзі та інших символів поза основною площиною Unicode. Один такий символ (часто в описах з
     * Modrinth) перемикає JTextArea у повільну «складну» розкладку тексту, і кожен перерахунок сторінки займав сотні
     * мілісекунд процесора (вікно підвисало). Шрифт лаунчера їх однаково не малює.
     */
    public static String plainText(String s) {
        if (s == null) return "";
        StringBuilder b = null;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            boolean drop = Character.isSurrogate(c) || c == 0x200D || (c >= 0xFE00 && c <= 0xFE0F); // ZWJ, варіанти вигляду
            if (drop && b == null) b = new StringBuilder(s.length()).append(s, 0, i);
            else if (!drop && b != null) b.append(c);
        }
        return b == null ? s : b.toString().replaceAll("  +", " ").trim();
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

    /** Небезпечна дія (видалення): червоний контур, червона заливка лише при наведенні. */
    public static JButton danger(JButton b) {
        LookTheme.Palette p = LookTheme.palette();
        b.putClientProperty("FlatLaf.style", "foreground: " + hex(p.danger) + "; borderColor: " + hex(p.danger)
                + "; hoverBorderColor: " + hex(p.danger) + "; focusedBorderColor: " + hex(p.danger)
                + "; hoverBackground: " + hex(p.danger) + "; pressedBackground: " + hex(p.danger.darker())
                + "; font: bold; arc: " + p.buttonArc);
        b.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseEntered(java.awt.event.MouseEvent e) {
                if (b.isEnabled()) b.setForeground(Color.WHITE);
            }

            @Override
            public void mouseExited(java.awt.event.MouseEvent e) {
                b.setForeground(p.danger);
            }
        });
        return b;
    }

    /** Підтвердження небезпечної дії: суцільна червона кнопка. */
    public static JButton dangerSolid(JButton b) {
        LookTheme.Palette p = LookTheme.palette();
        b.putClientProperty("FlatLaf.style", "background: " + hex(p.danger) + "; foreground: #FFFFFF; borderColor: "
                + hex(p.danger) + "; hoverBackground: " + hex(p.danger.darker()) + "; pressedBackground: "
                + hex(p.danger.darker().darker()) + "; disabledText: #FFFFFF99; font: bold; arc: " + p.buttonArc);
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

            // 17.11.2026: без власного розміру JComponent бере за мінімум свій поточний розмір, і пункт міг лишитись
            // ширшим за колонку меню (права рамка вибраного пункту обрізалась). Ширину задає лише колонка.
            @Override
            public Dimension getMinimumSize() {
                return new Dimension(0, 38);
            }

            @Override
            public Dimension getPreferredSize() {
                return new Dimension(0, 38);
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
                        // 17.11.2026: рамка на півпікселя всередині, щоб на 125% і 150% жоден її край не падав за межу пункту
                        java.awt.geom.RoundRectangle2D.Float rr = new java.awt.geom.RoundRectangle2D.Float(0.5f, 0.5f, w - 1.5f, h - 1.5f, arc, arc);
                        g2.setColor(new Color(255, 255, 255, 30));
                        g2.fill(rr);
                        g2.setColor(new Color(255, 255, 255, 46));
                        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
                        g2.draw(rr);
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
