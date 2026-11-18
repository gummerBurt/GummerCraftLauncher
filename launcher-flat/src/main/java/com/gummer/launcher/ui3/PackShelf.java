/*
 * Copyright (C) 2021-2026 GummerCraft <http://gummercraft.fun>
 */

package com.gummer.launcher.ui3;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/**
 * Редизайн 3: полиця збірок унизу вікна, як бібліотека ігор. Кожна збірка це плитка 16:9 з картинкою її власного фону,
 * іконкою і назвою; сервери і версії Minecraft окремими групами з підписом. Вибрана плитка в білій рамці з відступом
 * (як фокус на консолі), невибрані притемнені, під мишкою світлішає і піднімається на 2 px. На серверах праворуч угорі
 * живий онлайн, жовта крапка означає оновлення, стрілка вниз означає «ще не встановлено».
 *
 * <p>Клавіатура: Tab на полицю, стрілки вліво і вправо міняють збірку, Enter запускає, клавіша меню чи Shift+F10
 * відкриває меню збірки. Коліщатко мишки гортає полицю, коли збірок більше, ніж влазить.</p>
 */
public final class PackShelf extends JComponent implements Scrollable {

    /** Вміст плитки. */
    public static final class Tile {
        public Icon icon;
        public String title;
        public Image thumb;
        public String online;
        public boolean update;
        public boolean notInstalled;
        /** 17.11.2026: є оновлення модпака Modrinth чи своїх модів (UpdateBadges): зелена стрілка. */
        public boolean updates;
        /** Підпис групи, якщо з цієї плитки починається нова група. */
        public String group;
        public String tooltip;
    }

    public interface Listener {
        void select(int index);

        void launch(int index);

        void menu(int index, Component c, int x, int y);
    }

    public static final int TILE_MIN_W = 136, TILE_MAX_W = 196;
    private static final int PAD_X = Ui.S5, LABEL_H = 24, GAP = Ui.S3, GROUP_GAP = Ui.S6;

    private final List<Tile> tiles = new ArrayList<>();
    private final List<Rectangle> rects = new ArrayList<>();
    private int selected = -1, hover = -1;
    private Listener listener;

    public PackShelf() {
        setOpaque(false);
        setFocusable(true);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        MouseAdapter m = new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                int i = indexAt(e.getPoint());
                if (i != hover) {
                    hover = i;
                    setToolTipText(i >= 0 ? tiles.get(i).tooltip : null);
                    repaint();
                }
            }

            @Override
            public void mouseExited(MouseEvent e) {
                hover = -1;
                repaint();
            }

            @Override
            public void mousePressed(MouseEvent e) {
                requestFocusInWindow();
                int i = indexAt(e.getPoint());
                if (i < 0 || listener == null) return;
                if (SwingUtilities.isLeftMouseButton(e)) {
                    listener.select(i);
                    if (e.getClickCount() == 2) listener.launch(i);
                }
                if (e.isPopupTrigger()) popup(i, e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                int i = indexAt(e.getPoint());
                if (i >= 0 && listener != null && e.isPopupTrigger()) popup(i, e);
            }
        };
        addMouseListener(m);
        addMouseMotionListener(m);
        addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) {
                repaint();
            }

            @Override
            public void focusLost(FocusEvent e) {
                repaint();
            }
        });
        bind("LEFT", () -> step(-1));
        bind("RIGHT", () -> step(1));
        bind("HOME", () -> step(-10000));
        bind("END", () -> step(10000));
        bind("ENTER", () -> {
            if (listener != null && selected >= 0) listener.launch(selected);
        });
        bind("CONTEXT_MENU", this::keyMenu);
        bind("shift F10", this::keyMenu);
    }

    private void popup(int i, MouseEvent e) {
        listener.select(i);
        listener.menu(i, this, e.getX(), e.getY());
    }

    private void keyMenu() {
        if (listener != null && selected >= 0 && selected < rects.size()) {
            Rectangle r = rects.get(selected);
            listener.menu(selected, this, r.x + Ui.S4, r.y + r.height / 2);
        }
    }

    private void bind(String key, Runnable r) {
        getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(key), key);
        getActionMap().put(key, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                r.run();
            }
        });
    }

    private void step(int d) {
        if (tiles.isEmpty() || listener == null) return;
        int i = Math.max(0, Math.min(tiles.size() - 1, (selected < 0 ? 0 : selected) + d));
        listener.select(i);
    }

    public void setListener(Listener l) {
        listener = l;
    }

    public void setTiles(List<Tile> list) {
        tiles.clear();
        tiles.addAll(list);
        hover = -1;
        layoutTiles();
        revalidate();
        repaint();
    }

    public void setSelected(int i) {
        selected = i;
        layoutTiles();
        if (i >= 0 && i < rects.size()) {
            Rectangle r = new Rectangle(rects.get(i));
            r.grow(Ui.S5, 0);
            scrollRectToVisible(r);
        }
        repaint();
    }

    private int indexAt(Point p) {
        for (int i = 0; i < rects.size(); i++) {
            Rectangle r = rects.get(i);
            if (p.x >= r.x - GAP / 2 && p.x < r.x + r.width + GAP / 2 && p.y >= r.y - Ui.S2 && p.y < r.y + r.height + Ui.S2) return i;
        }
        return -1;
    }

    /** Ширина плитки: усі влазять у видиму ширину, але не вужчі за TILE_MIN_W і не ширші за TILE_MAX_W. */
    private int tileW() {
        int n = tiles.size();
        if (n == 0) return TILE_MIN_W;
        int groups = 0;
        for (Tile t : tiles) if (t.group != null) groups++;
        Container parent = getParent();
        int avail = (parent instanceof JViewport ? parent.getWidth() : getWidth()) - PAD_X * 2;
        int gaps = (n - Math.max(1, groups)) * GAP + Math.max(0, groups - 1) * GROUP_GAP;
        int w = (avail - gaps) / n;
        return Math.max(TILE_MIN_W, Math.min(TILE_MAX_W, w));
    }

    private void layoutTiles() {
        rects.clear();
        int w = tileW(), h = Math.round(w * 9f / 16f);
        int x = PAD_X;
        for (int i = 0; i < tiles.size(); i++) {
            if (i > 0) x += tiles.get(i).group != null ? GROUP_GAP : GAP;
            rects.add(new Rectangle(x, LABEL_H + Ui.S2, w, h));
            x += w;
        }
    }

    @Override
    public Dimension getPreferredSize() {
        layoutTiles();
        int w = tileW(), h = Math.round(w * 9f / 16f);
        int right = rects.isEmpty() ? 0 : rects.get(rects.size() - 1).x + w;
        return new Dimension(right + PAD_X, LABEL_H + Ui.S2 + h + Ui.S4);
    }

    @Override
    protected void paintComponent(Graphics g) {
        layoutTiles();
        Graphics2D g2 = Ui.smooth(g);
        try {
            int prevLabelEnd = 0;
            for (int i = 0; i < tiles.size(); i++) {
                Tile t = tiles.get(i);
                Rectangle r = rects.get(i);
                if (t.group != null) {
                    g2.setFont(Ui.font(Font.BOLD, 11f));
                    g2.setColor(Ui.TEXT_3);
                    FontMetrics fm = g2.getFontMetrics();
                    String label = t.group.toUpperCase(java.util.Locale.ROOT);
                    // 17.11.2026 (власник: «перевір що ніде нічого не вилазить»): підпис групи з однієї плитки («МОДПАКИ ОДНИМ
                    // КЛІКОМ» над Modrinth) ширший за неї і обрізався краєм вікна. Межа: наступна група або край полиці;
                    // не влазить - зсув ліворуч (не на попередній підпис), інакше «...»
                    int end = getWidth() - 4;
                    for (int j = i + 1; j < tiles.size(); j++) {
                        if (tiles.get(j).group != null) {
                            end = rects.get(j).x - 12;
                            break;
                        }
                    }
                    int need = spacedWidth(fm, label);
                    int x = r.x;
                    if (x + need > end) x = Math.max(prevLabelEnd + 16, end - need);
                    while (label.length() > 1 && x + spacedWidth(fm, label) > end) label = label.substring(0, label.length() - 2) + "…";
                    for (char c : label.toCharArray()) {
                        g2.drawString(String.valueOf(c), x, LABEL_H - 6);
                        x += fm.charWidth(c) + 1;
                    }
                    prevLabelEnd = x;
                }
                paintTile(g2, t, r, i == selected, i == hover);
            }
        } finally {
            g2.dispose();
        }
    }

    /** Ширина підпису групи з розрядкою 1 px між літерами. */
    private static int spacedWidth(FontMetrics fm, String s) {
        int w = 0;
        for (char c : s.toCharArray()) w += fm.charWidth(c) + 1;
        return w;
    }

        private void paintTile(Graphics2D g, Tile t, Rectangle r0, boolean sel, boolean hov) {
        Rectangle r = new Rectangle(r0);
        if (hov && !sel) r.y -= 2;
        int arc = Ui.RADIUS + 2;
        Shape clip = new RoundRectangle2D.Float(r.x, r.y, r.width, r.height, arc, arc);
        Graphics2D c = (Graphics2D) g.create();
        try {
            c.clip(clip);
            c.setColor(Ui.SURFACE_2);
            c.fill(clip);
            if (t.thumb != null) {
                // 16.11.2026: обкладинка під розмір плитки готується один раз, кадр лише копіює пікселі
                double ds = com.gummer.launcher.look.DevicePixels.scale(c);
                int pw = com.gummer.launcher.look.DevicePixels.px(r.width, ds), ph = com.gummer.launcher.look.DevicePixels.px(r.height, ds);
                com.gummer.launcher.look.DevicePixels.draw(c, com.gummer.launcher.look.DevicePixels.cached(t.thumb, pw, ph, false), r.x, r.y, r.width, r.height);
            }
            // невибрані тьмяніші, щоб вибрана читалась одразу
            if (!sel) {
                c.setColor(new Color(0, 0, 0, hov ? 30 : 110));
                c.fill(clip);
            }
            // низ плитки під назву
            c.setPaint(new GradientPaint(0, r.y + r.height - 46, new Color(0, 0, 0, 0), 0, r.y + r.height, new Color(0, 0, 0, 215)));
            c.fillRect(r.x, r.y + r.height - 46, r.width, 46);
        } finally {
            c.dispose();
        }
        // іконка і назва
        int ix = r.x + Ui.S2 + 2, iy = r.y + r.height - Ui.S2 - 20;
        if (t.icon != null) {
            Graphics2D ic = (Graphics2D) g.create();
            ic.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            ic.translate(ix, iy);
            // CrispIcon розміру 20 (його готує вікно) малюється у фізичних пікселях; інша іконка масштабується як є
            if (t.icon.getIconWidth() != 20 || t.icon.getIconHeight() != 20) {
                ic.scale(20.0 / t.icon.getIconWidth(), 20.0 / t.icon.getIconHeight());
            }
            t.icon.paintIcon(this, ic, 0, 0);
            ic.dispose();
        }
        g.setFont(Ui.font(Font.BOLD, 13f));
        FontMetrics fm = g.getFontMetrics();
        int tx = ix + 20 + Ui.S2;
        g.setColor(sel || hov ? Color.WHITE : Ui.alpha(Color.WHITE, 220));
        g.drawString(Ui.fit(t.title, fm, r.x + r.width - tx - Ui.S2), tx, iy + 10 + fm.getAscent() / 2 - 1);

        // праворуч угорі: онлайн, оновлення, не встановлено
        int bx = r.x + r.width - Ui.S2;
        int by = r.y + Ui.S2;
        if (t.online != null) {
            g.setFont(Ui.font(Font.BOLD, 11f));
            FontMetrics fo = g.getFontMetrics();
            int w = fo.stringWidth(t.online) + 22;
            g.setColor(new Color(0, 0, 0, 175));
            g.fillRoundRect(bx - w, by, w, 18, 18, 18);
            g.setColor(Ui.ONLINE);
            g.fillOval(bx - w + 7, by + 6, 6, 6);
            g.drawString(t.online, bx - w + 16, by + 13);
            bx -= w + 4;
        }
        if (t.updates && !t.update && !t.notInstalled) {
            g.setColor(new Color(0, 0, 0, 175));
            g.fillOval(bx - 18, by, 18, 18);
            Glyph.UPDATE.paint(g, bx - 15, by + 3, 12, Ui.ONLINE);
        }
        if (t.update || t.notInstalled) {
            g.setColor(new Color(0, 0, 0, 175));
            g.fillOval(bx - 18, by, 18, 18);
            if (t.update) {
                g.setColor(Ui.WARN);
                g.fillOval(bx - 13, by + 5, 8, 8);
            } else {
                Glyph.UPDATE.paint(g, bx - 15, by + 3, 12, Ui.TEXT);
            }
        }
        if (sel) {
            g.setStroke(new BasicStroke(2f));
            g.setColor(Color.WHITE);
            g.drawRoundRect(r.x - 4, r.y - 4, r.width + 7, r.height + 7, arc + 6, arc + 6);
            if (isFocusOwner()) {
                g.setColor(Ui.FOCUS);
                g.drawRoundRect(r.x - 7, r.y - 7, r.width + 13, r.height + 13, arc + 10, arc + 10);
            }
        }
    }

    // ---------------------------------------------------------------- прокрутка вбік
    @Override
    public Dimension getPreferredScrollableViewportSize() {
        return getPreferredSize();
    }

    @Override
    public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
        return tileW() / 2;
    }

    @Override
    public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
        return Math.max(tileW(), visibleRect.width - tileW());
    }

    @Override
    public boolean getScrollableTracksViewportWidth() {
        return getParent() instanceof JViewport && getParent().getWidth() >= getPreferredSize().width;
    }

    @Override
    public boolean getScrollableTracksViewportHeight() {
        return true;
    }

    /** Мініатюра під плитку: обрізка «cover», удвічі більша за плитку (чітко на екрані 150-200%). */
    public static Image cover(Image src, int w, int h) {
        if (src == null) return null;
        int iw = src.getWidth(null), ih = src.getHeight(null);
        if (iw <= 0 || ih <= 0) return null;
        int W = w * 2, H = h * 2;
        double k = Math.max(W / (double) iw, H / (double) ih);
        int sw = (int) Math.ceil(iw * k), sh = (int) Math.ceil(ih * k);
        BufferedImage out = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(src, (W - sw) / 2, (H - sh) / 2, sw, sh, null);
        g.dispose();
        return out;
    }

    /** Для диктора екрана: полиця це список (JComponent сам контексту не має). Назву задає вікно. */
    @Override
    public javax.accessibility.AccessibleContext getAccessibleContext() {
        if (accessibleContext == null) {
            accessibleContext = new AccessibleJComponent() {
                @Override
                public javax.accessibility.AccessibleRole getAccessibleRole() {
                    return javax.accessibility.AccessibleRole.LIST;
                }
            };
        }
        return accessibleContext;
    }
}
