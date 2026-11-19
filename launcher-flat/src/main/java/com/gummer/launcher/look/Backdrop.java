/*
 * Copyright (C) 2021-2026 GummerCraft <http://gummercraft.fun>
 */

package com.gummer.launcher.look;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.image.BufferedImage;
import java.awt.image.ConvolveOp;
import java.awt.image.Kernel;

/**
 * 16.11.2026: «скло» для вікон у вигляді Liquid Glass. Знімок головного вікна в момент відкриття діалогу,
 * зменшений і сильно розмитий, малюється під діалогом рівно там, де під ним лежить головне вікно (як справжнє
 * матове скло), зверху темний шар, щоб текст читався. Де діалог виходить за межі головного вікна, просто темне тло.
 *
 * <p>Знімок один раз на відкриття (дешево: вікно 1/4 розміру, три проходи розмиття), далі лише малюється.</p>
 */
public final class Backdrop extends JPanel {

    private static final int SCALE = 4;

    private final Window dialog;
    private final Window owner;
    private final BufferedImage blur;
    private final Point ownerAt;

    private Backdrop(Window dialog, Window owner, BufferedImage blur, Point ownerAt) {
        super(new BorderLayout());
        this.dialog = dialog;
        this.owner = owner;
        this.blur = blur;
        this.ownerAt = ownerAt;
        setOpaque(true);
        setBackground(LookTheme.GLASS.bg);
        dialog.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentMoved(ComponentEvent e) {
                repaint();
            }
        });
    }

    /**
     * Огорнути вміст діалогу склом. Вміст (панелі, прокрутки) стає прозорим, поля вводу лишаються зі своїм тлом.
     * Без головного вікна чи без знімка: просто темне тло.
     */
    public static void install(RootPaneContainer dialog, Window owner) {
        Window dw = (Window) dialog;
        BufferedImage img = null;
        Point at = null;
        if (owner instanceof RootPaneContainer && owner.isShowing() && owner.getWidth() > 0 && owner.getHeight() > 0) {
            try {
                img = snapshot(((RootPaneContainer) owner).getRootPane());
                at = ((RootPaneContainer) owner).getRootPane().getLocationOnScreen();
            } catch (RuntimeException e) {
                img = null;
            }
        }
        Container old = dialog.getContentPane();
        Backdrop b = new Backdrop(dw, owner, img, at);
        dialog.setContentPane(b);
        b.add(old, BorderLayout.CENTER);
        clearOpaque(old);
    }

    /** Прибрати непрозорість з панелей і прокруток (поля, кнопки, списки лишаються). */
    public static void clearOpaque(Component c) {
        if (c instanceof JPanel || c instanceof JScrollPane || c instanceof JViewport || c instanceof JTabbedPane
                || c instanceof JLayeredPane) {
            ((JComponent) c).setOpaque(false);
        }
        if (c instanceof Container) {
            for (Component k : ((Container) c).getComponents()) clearOpaque(k);
        }
    }

    private static BufferedImage snapshot(JRootPane root) {
        int w = Math.max(1, root.getWidth() / SCALE), h = Math.max(1, root.getHeight() / SCALE);
        // поле на розмиття, щоб краї не темніли
        int pad = 8;
        BufferedImage small = new BufferedImage(w + pad * 2, h + pad * 2, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = small.createGraphics();
        g.setColor(LookTheme.GLASS.bg);
        g.fillRect(0, 0, small.getWidth(), small.getHeight());
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_SPEED);
        g.translate(pad, pad);
        g.scale(1.0 / SCALE, 1.0 / SCALE);
        root.paint(g);
        g.dispose();
        BufferedImage out = small;
        float[] k = new float[25];
        java.util.Arrays.fill(k, 1f / 25f);
        ConvolveOp op = new ConvolveOp(new Kernel(5, 5, k), ConvolveOp.EDGE_NO_OP, null);
        for (int i = 0; i < 3; i++) out = op.filter(out, null);
        return out.getSubimage(pad, pad, w, h);
    }

    /**
     * 17.11.2026: готове тло (розмиття, темний шар, відблиск) у фізичних пікселях. Раніше на кожен кадр прокрутки
     * знімок розтягувався вчетверо і зверху лягали два напівпрозорі шари на все вікно; тепер кадр це одне копіювання.
     * Перебудовується лише коли вікно зсунули чи змінили розмір або масштаб.
     */
    private BufferedImage composed;
    private java.util.List<Object> composedKey;

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            int w = getWidth(), h = getHeight();
            double ds = DevicePixels.scale(g2);
            int pw = DevicePixels.px(w, ds), ph = DevicePixels.px(h, ds);
            int dx = 0, dy = 0;
            boolean withBlur = !LookTheme.LITE && blur != null && ownerAt != null && isShowing();
            if (withBlur) {
                Point me = getLocationOnScreen();
                dx = ownerAt.x - me.x;
                dy = ownerAt.y - me.y;
            }
            java.util.List<Object> key = java.util.Arrays.<Object>asList(pw, ph, ds, dx, dy, withBlur);
            if (composed == null || !key.equals(composedKey)) {
                composed = compose(pw, ph, ds, w, h, withBlur, dx, dy);
                composedKey = key;
            }
            DevicePixels.draw(g2, composed, 0, 0, w, h);
        } finally {
            g2.dispose();
        }
    }

    private BufferedImage compose(int pw, int ph, double ds, int w, int h, boolean withBlur, int dx, int dy) {
        BufferedImage out = new BufferedImage(pw, ph, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2 = out.createGraphics();
        try {
            g2.scale(ds, ds);
            g2.setColor(getBackground());
            g2.fillRect(0, 0, w + 1, h + 1);
            if (withBlur) {
                g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g2.drawImage(blur, dx, dy, blur.getWidth() * SCALE, blur.getHeight() * SCALE, null);
            }
            // темний шар і легкий відблиск згори
            g2.setColor(new Color(10, 12, 15, 168));
            g2.fillRect(0, 0, w + 1, h + 1);
            g2.setPaint(new GradientPaint(0, 0, new Color(255, 255, 255, 16), 0, Math.min(h, 160), new Color(255, 255, 255, 0)));
            g2.fillRect(0, 0, w + 1, Math.min(h, 160));
        } finally {
            g2.dispose();
        }
        return out;
    }
}
