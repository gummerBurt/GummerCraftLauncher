/*
 * Copyright (C) 2021-2026 GummerCraft <http://gummercraft.fun>
 */

package com.gummer.launcher.dialog;

import com.skcraft.launcher.Launcher;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;

/**
 * Дизайн 1.35: кнопка-слот у стилі хотбару Minecraft (налаштування, вікі, підтримка). Вдавлена клітинка, біла рамка
 * виділення при наведенні (як вибраний слот у грі), іконка і підпис з тінню. {@link #setBadge} показує червону крапку
 * (нова відповідь підтримки).
 */
public class HotbarSlot extends JButton {

    public static final int WIDTH = 100, HEIGHT = 88;

    private final Image icon;
    private final String caption;
    private boolean hover;
    private int badge;
    /**
     * 24.11.2026 (власник: «кнопка "Налаштування збірки" загалом найважливіша, її потрібно якось виділити»): слот із
     * зеленим відтінком і зеленою рамкою, колір як у сяйва «Запуску» ({@link #HIGHLIGHT}).
     */
    private boolean highlight;
    private static final Color HIGHLIGHT = new Color(0x8C, 0xE0, 0x4A);

    /** 24.11.2026: виділений слот (кнопка «Налаштування збірки»). */
    public HotbarSlot highlight() {
        highlight = true;
        repaint();
        return this;
    }

    public HotbarSlot(String iconPath, String caption, String tooltip) {
        this(iconPath, caption, tooltip, WIDTH);
    }

    /** 17.11.2026: вужчий слот (кнопка «Збірка» біля «Запуску», де місця мало). */
    public HotbarSlot(String iconPath, String caption, String tooltip, int width) {
        this.icon = readIcon(iconPath);
        this.caption = caption;
        setToolTipText(tooltip);
        setContentAreaFilled(false);
        setBorderPainted(false);
        setFocusPainted(false);
        setFocusable(false);
        setOpaque(false);
        setRolloverEnabled(true);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        // 22.11.2026 («Налаштування збірки» замість «Збірка»): довгий підпис у два рядки тим самим шрифтом, що в інших
        // слотах (жирний 11), слот ширшає лише під довший рядок («Налаштування»), а не під весь підпис (так спершу
        // «Запуск» у віконному режимі вилазив за край вікна)
        if (caption != null) {
            Font cf = getFont().deriveFont(Font.BOLD, 11f);
            FontMetrics cfm = getFontMetrics(cf);
            int need = 0;
            // 22.11.2026 ~22:00 (власник: на його екрані знову «Налаштуван…»): рядок міряється найширшим з можливих
            // малювань (масштаб Windows 100-200%, дробові метрики і без), бо Swing міряє вужче, ніж потім малює
            for (String l : captionLines(cfm, Integer.MAX_VALUE / 2, width - 10)) need = Math.max(need, com.gummer.launcher.look.TextFit.widest(cf, l));
            width = Math.max(width, need + 8); // по 4 px з боків, щоб текст не лягав на рамку слота
        }
        Dimension d = new Dimension(width, HEIGHT);
        setPreferredSize(d);
        setMinimumSize(d);
        setMaximumSize(d);
        addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseEntered(java.awt.event.MouseEvent e) {
                hover = true;
                repaint();
            }

            @Override
            public void mouseExited(java.awt.event.MouseEvent e) {
                hover = false;
                repaint();
            }
        });
    }

    private static Image readIcon(String path) {
        try (InputStream in = Launcher.class.getResourceAsStream(path)) {
            return in != null ? ImageIO.read(in) : null;
        } catch (IOException e) {
            return null;
        }
    }

    public void setBadge(boolean badge) {
        setBadge(badge ? 1 : 0);
    }

    /** 16.11.2026: число непрочитаних відповідей підтримки (0: значка немає). */
    public void setBadge(int count) {
        if (badge != count) {
            badge = count;
            repaint();
        }
    }

    /** Підпис в один рядок, якщо влазить в oneLine; інакше два рядки з розривом на пробілі, що дає найвужчий довший рядок. */
    private String[] captionLines(FontMetrics fm, int max, int oneLine) {
        String c = caption == null ? "" : caption;
        if (fm.stringWidth(c) <= oneLine) return new String[]{c};
        String[] best = null;
        int bestW = Integer.MAX_VALUE;
        for (int i = c.indexOf(' '); i > 0; i = c.indexOf(' ', i + 1)) {
            String a = c.substring(0, i).trim(), b = c.substring(i + 1).trim();
            int bw = Math.max(fm.stringWidth(a), fm.stringWidth(b));
            if (bw < bestW) {
                bestW = bw;
                best = new String[]{a, b};
            }
        }
        if (best == null) best = new String[]{c};
        for (int k = 0; k < best.length; k++) {
            String t = best[k];
            while (fm.stringWidth(t) > max && t.length() > 3) t = t.substring(0, t.length() - 2) + "…";
            best[k] = t;
        }
        return best;
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            com.gummer.launcher.look.TextAA.apply(g2);
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            int w = getWidth(), h = getHeight();
            boolean pressed = getModel().isPressed() && getModel().isArmed();

            // клітинка: темна, з тінню зверху-зліва і світлим краєм знизу-справа (вдавлена, як слот інвентарю)
            g2.setColor(pressed ? new Color(0, 0, 0, 120) : hover ? new Color(255, 255, 255, 34) : new Color(255, 255, 255, 12));
            g2.fillRect(3, 3, w - 6, h - 6);
            if (highlight) { // 24.11.2026: зелений відтінок клітинки
                g2.setColor(new Color(HIGHLIGHT.getRed(), HIGHLIGHT.getGreen(), HIGHLIGHT.getBlue(), pressed ? 30 : hover ? 62 : 44));
                g2.fillRect(3, 3, w - 6, h - 6);
            }
            g2.setColor(new Color(0, 0, 0, 110));
            g2.fillRect(3, 3, w - 6, 2);
            g2.fillRect(3, 3, 2, h - 6);
            g2.setColor(new Color(255, 255, 255, 38));
            g2.fillRect(3, h - 5, w - 6, 2);
            g2.fillRect(w - 5, 3, 2, h - 6);

            // іконка (білий силует 48x48, зменшений до 38) і підпис
            int dy = pressed ? 1 : 0;
            Font font = getFont().deriveFont(Font.BOLD, 11f);
            g2.setFont(font);
            FontMetrics fm = g2.getFontMetrics();
            // 22.11.2026: довгий підпис у два рядки (див. конструктор); для двох рядків іконка трохи менша і вище,
            // щоб текст її не торкався
            // «…» лише якщо рядок справді не влазить у слот з відступом 2 px з боків (слот уже міряний з запасом)
            String[] lines = captionLines(fm, w - 4, w - 10);
            boolean two = lines.length > 1;
            if (icon != null) {
                int s = two ? 32 : 38;
                // 16.11.2026: зменшена іконка готується один раз (у фізичних пікселях), а не на кожному кадрі
                double ds = com.gummer.launcher.look.DevicePixels.scale(g2);
                int px = com.gummer.launcher.look.DevicePixels.px(s, ds);
                com.gummer.launcher.look.DevicePixels.draw(g2, com.gummer.launcher.look.DevicePixels.cached(icon, px, px, true), (w - s) / 2, (two ? 8 : 12) + dy, s, s);
            }
            int lineH = fm.getAscent() + 1;
            for (int i = 0; i < lines.length; i++) {
                String text = lines[i];
                int tx = (w - fm.stringWidth(text)) / 2, ty = h - 15 - (lines.length - 1 - i) * lineH + dy;
                g2.setColor(new Color(40, 40, 40));
                g2.drawString(text, tx + 1, ty + 1);
                g2.setColor(hover || highlight ? Color.WHITE : new Color(225, 225, 225));
                g2.drawString(text, tx, ty);
            }

            // 24.11.2026: виділений слот: зелена рамка в 2 пікселі (при наведенні її змінює біла)
            if (highlight && !hover) {
                g2.setColor(new Color(HIGHLIGHT.getRed(), HIGHLIGHT.getGreen(), HIGHLIGHT.getBlue(), 220));
                g2.setStroke(new BasicStroke(2f));
                g2.drawRect(2, 2, w - 5, h - 5);
                g2.setStroke(new BasicStroke(1f));
            }
            // вибраний слот: біла рамка в 3 пікселі, як у хотбарі
            if (hover) {
                g2.setColor(new Color(255, 255, 255, 235));
                g2.setStroke(new BasicStroke(3f));
                g2.drawRect(1, 1, w - 3, h - 3);
                g2.setColor(new Color(0, 0, 0, 90));
                g2.setStroke(new BasicStroke(1f));
                g2.drawRect(3, 3, w - 7, h - 7);
            }

            if (!hover) CoachMark.paintRing(g2, this, 4); // 17.11.2026: увага на кнопку «Збірка», поки її не відкривали
            SupportUnread.paint(g2, badge, w - 7, 7, new Color(0x1A, 0x1A, 0x1A));
        } finally {
            g2.dispose();
        }
    }

    /** Темна смуга хотбару, в яку кладуться слоти. */
    public static JPanel bar() {
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 4)) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                com.gummer.launcher.look.Edge.pill(g2, 0, 0, getWidth(), getHeight(), 10, new Color(0, 0, 0, 150), new Color(255, 255, 255, 30));
                g2.dispose();
                super.paintComponent(g);
            }
        };
        bar.setOpaque(false);
        return bar;
    }

    /** Скільки ступенів яскравості має «дихання» сяйва: перемальовка лише тоді, коли ступінь змінився. */
    private static final int GLOW_LEVELS = 24;

    /**
     * Бліде світіння навколо компонента, що повільно «дихає» (кнопка запуску). 16.11.2026: кільце малюється один раз
     * у готову картинку (у фізичних пікселях) і кожен кадр лише накладається з потрібною прозорістю; перемальовка
     * тільки при зміні ступеня яскравості (~11 разів на секунду замість 20) і не в згорнутому вікні.
     */
    public static JPanel glow(JComponent inner, Color color) {
        final int pad = 8;
        final long start = System.currentTimeMillis();
        JPanel wrap = new JPanel(new BorderLayout()) {
            private int level = -1;
            private final java.util.Map<String, java.awt.image.BufferedImage> rings = new java.util.HashMap<String, java.awt.image.BufferedImage>();
            private final Timer timer = new Timer(50, e -> {
                int l = level();
                if (l != level && isShowing() && !com.gummer.launcher.look.DevicePixels.iconified(this)) {
                    level = l;
                    // одразу і лише кільце: repaint() зливався з ділянкою напису біля логотипа в один прямокутник
                    Rectangle r = glowRect(inner, pad);
                    paintImmediately(r.x, r.y, r.width, pad);
                    paintImmediately(r.x, r.y + r.height - pad, r.width, pad);
                    paintImmediately(r.x, r.y + pad, pad, r.height - 2 * pad);
                    paintImmediately(r.x + r.width - pad, r.y + pad, pad, r.height - 2 * pad);
                }
            });

            private int level() {
                if (!inner.isEnabled()) return 0;
                double t = (System.currentTimeMillis() - start) / 2200.0 * Math.PI * 2;
                double k = 0.55 + 0.45 * (0.5 + 0.5 * Math.sin(t));
                return (int) Math.round(k * GLOW_LEVELS);
            }

            @Override
            public void addNotify() {
                super.addNotify();
                if (!com.gummer.launcher.look.LookTheme.LITE) timer.start(); // спрощений режим: кнопка без «дихання»
            }

            @Override
            public void removeNotify() {
                timer.stop();
                super.removeNotify();
            }

            @Override
            protected void paintComponent(Graphics g) {
                int l = level < 0 ? level() : level;
                if (!inner.isEnabled()) l = 0;
                if (l <= 0) return;
                Graphics2D g2 = (Graphics2D) g.create();
                try {
                    // 16.11.2026: сяйво навколо самої картинки кнопки, а не її меж (поля кнопки залежать від теми і масштабу)
                    Rectangle r = glowRect(inner, pad);
                    if (r.width <= 2 * pad || r.height <= 2 * pad) return;
                    double ds = com.gummer.launcher.look.DevicePixels.scale(g2);
                    String key = r.width + "x" + r.height + "@" + ds;
                    java.awt.image.BufferedImage ring = rings.get(key);
                    if (ring == null) {
                        if (rings.size() > 4) rings.clear();
                        ring = glowRing(r.width, r.height, ds, pad, color);
                        rings.put(key, ring);
                    }
                    g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, Math.min(1.0F, l / (float) GLOW_LEVELS)));
                    com.gummer.launcher.look.DevicePixels.draw(g2, ring, r.x, r.y, r.width, r.height);
                } finally {
                    g2.dispose();
                }
            }
        };
        wrap.setOpaque(false);
        wrap.setBorder(BorderFactory.createEmptyBorder(pad, pad, pad, pad));
        wrap.add(inner, BorderLayout.CENTER);
        return wrap;
    }

    /** Кільце сяйва на повну яскравість (прозорість кожної смуги як і раніше), w x h логічних пікселів. */
    private static java.awt.image.BufferedImage glowRing(int w, int h, double ds, int pad, Color color) {
        java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(com.gummer.launcher.look.DevicePixels.px(w, ds),
                com.gummer.launcher.look.DevicePixels.px(h, ds), java.awt.image.BufferedImage.TYPE_INT_ARGB_PRE);
        Graphics2D g2 = img.createGraphics();
        try {
            g2.scale(ds, ds);
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setStroke(new BasicStroke(2f));
            for (int i = pad; i >= 1; i--) {
                double f = 1.0 - (i - 1) / (double) pad;
                int a = (int) (70 * f * f);
                g2.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), Math.max(0, Math.min(255, a))));
                g2.drawRoundRect(pad - i, pad - i, w - 2 * (pad - i) - 1, h - 2 * (pad - i) - 1, 8 + i * 2, 8 + i * 2);
            }
        } finally {
            g2.dispose();
        }
        return img;
    }

    /** Прямокутник сяйва в координатах обгортки: картинка кнопки (по центру її вмісту) плюс pad; без картинки межі кнопки. */
    static Rectangle glowRect(JComponent inner, int pad) {
        Rectangle b = inner.getBounds();
        Icon ic = inner instanceof AbstractButton ? ((AbstractButton) inner).getIcon() : null;
        if (ic == null || ic.getIconWidth() <= 0) {
            return new Rectangle(b.x - pad, b.y - pad, b.width + 2 * pad, b.height + 2 * pad);
        }
        Insets in = inner.getInsets();
        int iw = Math.min(ic.getIconWidth(), b.width), ih = Math.min(ic.getIconHeight(), b.height);
        int x = b.x + in.left + (b.width - in.left - in.right - iw) / 2;
        int y = b.y + in.top + (b.height - in.top - in.bottom - ih) / 2;
        x = Math.max(b.x, Math.min(x, b.x + b.width - iw));
        y = Math.max(b.y, Math.min(y, b.y + b.height - ih));
        return new Rectangle(x - pad, y - pad, iw + 2 * pad, ih + 2 * pad);
    }
}
