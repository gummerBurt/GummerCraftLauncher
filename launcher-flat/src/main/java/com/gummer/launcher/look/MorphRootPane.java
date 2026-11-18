/*
 * Copyright (C) 2021-2026 GummerCraft <http://gummercraft.fun>
 */

package com.gummer.launcher.look;

import javax.swing.*;
import java.awt.*;
import java.awt.image.VolatileImage;

/**
 * 17.11.2026 (власник: «коли ти перемикаєш з віконного в повноекранний, воно взагалі скаче. Має бути плавно, як в
 * віндовсі, елементи красиво "перетікають" і масштабуються»).
 *
 * <p>Коренева панель вікна, яка при великій зміні розміру (розгорнути, відновити) перед новою розкладкою знімає
 * старий вигляд вікна і ще {@link #MS} мс показує його розтягнутим на новий розмір, плавно розчиняючи в новому. Так
 * продовжується те, що робить анімація Windows (вона теж розтягує старий кадр), і замість стрибка вигляд перетікає.
 * Малі зміни (перетягування краю) без цього. Миша проходить крізь шар (у нього немає слухачів).</p>
 */
public class MorphRootPane extends JRootPane {

    private static final int MS = 220;
    private static final double BIG = 0.08;

    private final Runnable beforeLayout;
    private Dimension laidOut;
    private final Overlay overlay = new Overlay();

    public MorphRootPane(Runnable beforeLayout) {
        this.beforeLayout = beforeLayout;
        setOpaque(true);
    }

    private static final boolean DEBUG = Boolean.getBoolean("gummer.morphDebug");

    private static void log(String m) {
        if (DEBUG) System.out.println("MORPH " + System.nanoTime() / 1000000 % 100000 + " " + m);
    }

    /**
     * Перший кадр після зміни розміру: лише розтягнутий старий вигляд (~30 мс), без справжнього вмісту, бо його перше
     * малювання нового розміру буває до 0.1 с, і Windows тим часом показувала б старий кадр обрізаним. Справжній вміст
     * малюється наступним кадром, і з нього починається розчинення.
     */
    @Override
    public void paint(Graphics g) {
        Window w = SwingUtilities.getWindowAncestor(this);
        if (w instanceof RootPaneContainer && w.isShowing() && ((RootPaneContainer) w).getRootPane() == this) {
            Insets in = w.getInsets();
            int ew = w.getWidth() - in.left - in.right, eh = w.getHeight() - in.top - in.bottom;
            if (Math.abs(ew - getWidth()) > 2 || Math.abs(eh - getHeight()) > 2) {
                // вікно вже нового розміру, а розкладка ще стара (Java спершу перемальовує, потім розкладає): ~60 мс
                // старого великого вигляду, обрізаного вікном. Розкладаємо одразу, і цей кадр стає першим кадром переходу.
                w.invalidate();
                w.validate();
            }
        }
        if (overlay.holding) {
            overlay.holding = false;
            overlay.paintAt(g, 1.0F);
            repaint();
            return;
        }
        super.paint(g);
    }

    @Override
    public void doLayout() {
        Dimension now = getSize();
        if (DEBUG) log("doLayout " + laidOut + " -> " + now + " showing=" + isShowing());
        if (laidOut != null && now.width > 0 && now.height > 0 && isShowing() && big(laidOut, now)) {
            overlay.start(snapshot(laidOut));
            log("snapshot done");
        }
        if (beforeLayout != null) beforeLayout.run();
        super.doLayout();
        if (overlay.getParent() != getLayeredPane()) getLayeredPane().add(overlay, JLayeredPane.DRAG_LAYER);
        overlay.setBounds(0, 0, getLayeredPane().getWidth(), getLayeredPane().getHeight());
        if (now.width > 0 && now.height > 0) laidOut = now;
    }

    private static boolean big(Dimension a, Dimension b) {
        return Math.abs(a.width - b.width) > a.width * BIG || Math.abs(a.height - b.height) > a.height * BIG;
    }

    /**
     * Старий вигляд (розкладка ще стара) у пікселях екрана. У апаратну картинку (VolatileImage, як буфер самого Swing):
     * у звичайну BufferedImage напівпрозоре скло, градієнти і великі картинки малювались програмно, ~100-160 мс.
     */
    private VolatileImage snapshot(Dimension size) {
        try {
            GraphicsConfiguration gc = getGraphicsConfiguration();
            if (gc == null) return null;
            double scale = gc.getDefaultTransform().getScaleX();
            JLayeredPane lp = getLayeredPane();
            int w = size.width, h = size.height;
            if (w <= 0 || h <= 0) return null;
            VolatileImage img = gc.createCompatibleVolatileImage((int) Math.ceil(w * scale), (int) Math.ceil(h * scale));
            if (img.validate(gc) == VolatileImage.IMAGE_INCOMPATIBLE) return null;
            Graphics2D g = img.createGraphics();
            g.scale(scale, scale);
            g.setClip(0, 0, w, h);
            overlay.setVisible(false);
            lp.paint(g);
            g.dispose();
            return img.contentsLost() ? null : img;
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static final class Overlay extends JComponent {
        private VolatileImage image;
        private long start;
        private final Timer timer = new Timer(15, e -> tick());
        boolean holding;

        Overlay() {
            setOpaque(false);
            setVisible(false);
        }

        void start(VolatileImage img) {
            if (img == null) return;
            image = img;
            start = 0; // відлік з першого кадру зі справжнім вмістом
            holding = true;
            setVisible(true);
            timer.restart();
            repaint();
        }

        private void stop() {
            timer.stop();
            if (image != null) image.flush();
            image = null;
            holding = false;
            setVisible(false);
        }

        private void tick() {
            if (start != 0 && alpha() <= 0) stop();
            repaint();
        }

        private float alpha() {
            if (start == 0) return 1;
            double t = (System.nanoTime() - start) / 1e6 / MS;
            if (t >= 1) return 0;
            return (float) (1 - t * t * (3 - 2 * t)); // плавно на початку і в кінці
        }

        @Override
        protected void paintComponent(Graphics g) {
            if (start == 0 && image != null) start = System.nanoTime();
            paintAt(g, alpha());
        }

        /** Старий вигляд, розтягнутий на весь шар (як розтягує Windows), з прозорістю a. */
        void paintAt(Graphics g, float a) {
            VolatileImage img = image;
            if (img == null || a <= 0) return;
            if (img.contentsLost()) {
                stop();
                return;
            }
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, a));
                g2.drawImage(img, 0, 0, getWidth(), getHeight(), null);
            } finally {
                g2.dispose();
            }
        }
    }
}
