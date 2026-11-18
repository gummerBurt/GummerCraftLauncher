/*
 * Copyright (C) 2021-2026 GummerCraft <http://gummercraft.fun>
 */

package com.gummer.launcher.look;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;

/**
 * Готові картинки у фізичних пікселях екрана (16.11.2026, прискорення всіх трьох виглядів). Фони, логотипи і сяйво
 * раніше масштабувались з великої картинки на кожному кадрі анімації (напис біля логотипа 60 разів на секунду,
 * «дихання» кнопки запуску 20 разів); тепер масштабуються один раз під розмір вікна і масштаб Windows, а кадр це
 * просте копіювання пікселів. Малюється з одиничним масштабом по цілому пікселю, тож при 125-150% не розмивається.
 */
public final class DevicePixels {

    private DevicePixels() {
    }

    /** Перетворення без повороту і скосу, з додатним масштабом (звичайне малювання вікна). */
    public static boolean plain(AffineTransform at) {
        return at.getShearX() == 0 && at.getShearY() == 0 && at.getScaleX() > 0 && at.getScaleY() > 0;
    }

    /** Масштаб екрана для цього Graphics (1.0 при 100%, 1.5 при 150%). */
    public static double scale(Graphics2D g) {
        AffineTransform at = g.getTransform();
        return plain(at) ? at.getScaleX() : 1.0;
    }

    /** Розмір у фізичних пікселях для логічного розміру. */
    public static int px(double logical, double scale) {
        return Math.max(1, (int) Math.round(logical * scale));
    }

    /**
     * Якісне зменшення чи збільшення частини картинки (sx, sy, sw, sh) до dw x dh: велике зменшення йде кроками навпіл
     * (білінійно, як усереднення), останній крок бікубічний. Швидше за getScaledInstance і без «драбинок».
     */
    public static BufferedImage resize(Image src, int sx, int sy, int sw, int sh, int dw, int dh, boolean alpha) {
        int type = alpha ? BufferedImage.TYPE_INT_ARGB_PRE : BufferedImage.TYPE_INT_RGB;
        dw = Math.max(1, dw);
        dh = Math.max(1, dh);
        Image cur = src;
        int cx = sx, cy = sy, cw = Math.max(1, sw), ch = Math.max(1, sh);
        while (cw >= dw * 2 || ch >= dh * 2) {
            int nw = Math.max(dw, cw / 2), nh = Math.max(dh, ch / 2);
            BufferedImage half = new BufferedImage(nw, nh, type);
            Graphics2D g = half.createGraphics();
            g.setComposite(AlphaComposite.Src);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(cur, 0, 0, nw, nh, cx, cy, cx + cw, cy + ch, null);
            g.dispose();
            cur = half;
            cx = 0;
            cy = 0;
            cw = nw;
            ch = nh;
        }
        if (cur instanceof BufferedImage && cw == dw && ch == dh && cx == 0 && cy == 0
                && ((BufferedImage) cur).getWidth() == dw && ((BufferedImage) cur).getHeight() == dh && ((BufferedImage) cur).getType() == type) {
            return (BufferedImage) cur;
        }
        BufferedImage out = new BufferedImage(dw, dh, type);
        Graphics2D g = out.createGraphics();
        g.setComposite(AlphaComposite.Src);
        // останній крок менший за 2x: великі фони білінійно (бікубічний програмно втричі довший, різниці на око немає),
        // логотипи та іконки бікубічно
        boolean small = (long) dw * dh <= 600_000L;
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, small ? RenderingHints.VALUE_INTERPOLATION_BICUBIC : RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(cur, 0, 0, dw, dh, cx, cy, cx + cw, cy + ch, null);
        g.dispose();
        return out;
    }

    /**
     * Спільний кеш готових картинок (найдавніше використані викидаються понад ~160 МБ, але не більше чверті пам'яті
     * лаунчера). 18.11.2026: було 64 МБ, а це лише ~4 фони на розгорнутому вікні 1440p, тож при перемиканні збірок
     * фони викидались і масштабувались заново (сторож EdtWatch: 60-100 мс на кожен клік).
     */
    private static final long CACHE_BYTES = Math.min(160L << 20, Runtime.getRuntime().maxMemory() / 4);
    private static long cacheBytes;
    private static final java.util.LinkedHashMap<java.util.List<Object>, BufferedImage> CACHE =
            new java.util.LinkedHashMap<java.util.List<Object>, BufferedImage>(32, 0.75F, true);

    /**
     * Те саме, що {@link #resize(Image, int, int, int, int, int, int, boolean)}, але з кешем: сцени, плитки і кнопки
     * створюються заново при перемиканні збірки, а картинки ті самі. Ключ це сама картинка (за тотожністю) і розміри,
     * тож різний масштаб екрана (вікно між моніторами) тримає обидві версії, а не перераховує їх по черзі.
     */
    public static synchronized BufferedImage cached(Image src, int sx, int sy, int sw, int sh, int dw, int dh, boolean alpha) {
        java.util.List<Object> key = java.util.Arrays.<Object>asList(new Identity(src), sx, sy, sw, sh, dw, dh, alpha);
        BufferedImage img = CACHE.get(key);
        if (img != null) return img;
        img = resize(src, sx, sy, sw, sh, dw, dh, alpha);
        CACHE.put(key, img);
        cacheBytes += 4L * img.getWidth() * img.getHeight();
        java.util.Iterator<java.util.Map.Entry<java.util.List<Object>, BufferedImage>> it = CACHE.entrySet().iterator();
        while (cacheBytes > CACHE_BYTES && CACHE.size() > 1 && it.hasNext()) {
            BufferedImage old = it.next().getValue();
            if (old == img) break;
            cacheBytes -= 4L * old.getWidth() * old.getHeight();
            it.remove();
        }
        return img;
    }

    private static final java.util.concurrent.ExecutorService SCALER = java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "GummerCraft background scaler");
        t.setDaemon(true);
        t.setPriority(Thread.NORM_PRIORITY - 1);
        return t;
    });
    private static final java.util.Set<java.util.List<Object>> PENDING = new java.util.HashSet<>();

    /**
     * 17.11.2026 (власник: «коли ти перемикаєш з віконного в повноекранний, воно взагалі скаче»): уся картинка до
     * dw x dh з кешем, але якщо такого розміру ще немає, а іншого є, одразу віддає той інший (малюється розтягнутим
     * найближчим пікселем, ~1 мс замість 35-45 мс бікубічного), а якісну рахує у фоновому потоці і перемальовує
     * {@code repaint}. Без цього перший кадр нового розміру вікна малювався ~0.1 с, і Windows тим часом показувала
     * старий кадр, обрізаний до нового розміру.
     */
    public static BufferedImage cachedQuick(Image src, int dw, int dh, boolean alpha, Component repaint) {
        java.util.List<Object> key;
        BufferedImage standIn = null;
        synchronized (DevicePixels.class) {
            key = java.util.Arrays.<Object>asList(new Identity(src), 0, 0, src.getWidth(null), src.getHeight(null), dw, dh, alpha);
            BufferedImage img = CACHE.get(key);
            if (img != null) return img;
            for (java.util.Map.Entry<java.util.List<Object>, BufferedImage> e : CACHE.entrySet()) {
                java.util.List<Object> k = e.getKey();
                if (k.get(0).equals(key.get(0)) && k.get(1).equals(0) && k.get(2).equals(0) && k.get(3).equals(key.get(3))
                        && k.get(4).equals(key.get(4)) && k.get(7).equals(alpha)) {
                    standIn = e.getValue();
                    break;
                }
            }
            if (standIn != null && PENDING.add(key)) {
                final java.util.List<Object> k = key;
                SCALER.execute(() -> {
                    try {
                        cached(src, dw, dh, alpha);
                    } finally {
                        synchronized (DevicePixels.class) {
                            PENDING.remove(k);
                        }
                        if (repaint != null) javax.swing.SwingUtilities.invokeLater(repaint::repaint);
                    }
                });
            }
        }
        return standIn != null ? standIn : cached(src, dw, dh, alpha);
    }

    /**
     * 18.11.2026: порахувати картинку dw x dh у фоновому потоці наперед (фони збірок під поточний розмір вікна),
     * щоб клік по збірці брав готову з кешу. Нічого не робить, якщо вона вже є чи рахується.
     */
    public static void prewarm(Image src, int dw, int dh, boolean alpha) {
        if (src == null || src.getWidth(null) <= 0 || dw <= 0 || dh <= 0) return;
        final java.util.List<Object> key;
        synchronized (DevicePixels.class) {
            key = java.util.Arrays.<Object>asList(new Identity(src), 0, 0, src.getWidth(null), src.getHeight(null), dw, dh, alpha);
            if (CACHE.containsKey(key) || !PENDING.add(key)) return;
        }
        SCALER.execute(() -> {
            try {
                cached(src, dw, dh, alpha);
            } catch (RuntimeException | OutOfMemoryError ignored) {
                // порахується при показі, як раніше
            } finally {
                synchronized (DevicePixels.class) {
                    PENDING.remove(key);
                }
            }
        });
    }

    /** Уся картинка до dw x dh, з кешем. */
    public static BufferedImage cached(Image src, int dw, int dh, boolean alpha) {
        return cached(src, 0, 0, src.getWidth(null), src.getHeight(null), dw, dh, alpha);
    }

    /** Ключ за тотожністю об'єкта (equals картинки і так тотожність, але так явно і без сюрпризів). */
    private static final class Identity {
        final Object o;

        Identity(Object o) {
            this.o = o;
        }

        @Override
        public boolean equals(Object x) {
            return x instanceof Identity && ((Identity) x).o == o;
        }

        @Override
        public int hashCode() {
            return System.identityHashCode(o);
        }
    }

    /** Уся картинка до dw x dh. */
    public static BufferedImage resize(Image src, int dw, int dh, boolean alpha) {
        return resize(src, 0, 0, src.getWidth(null), src.getHeight(null), dw, dh, alpha);
    }

    /**
     * Намалювати готову картинку у фізичних пікселях так, щоб її лівий верхній кут був у логічній точці (x, y).
     * Якщо перетворення незвичайне (поворот), картинка просто розтягується в логічний прямокутник lw x lh.
     */
    public static void draw(Graphics2D g, BufferedImage img, double x, double y, int lw, int lh) {
        AffineTransform at = g.getTransform();
        if (!plain(at)) {
            g.drawImage(img, (int) Math.round(x), (int) Math.round(y), lw, lh, null);
            return;
        }
        Graphics2D d = (Graphics2D) g.create();
        try {
            d.setTransform(AffineTransform.getTranslateInstance(
                    Math.round(at.getTranslateX() + x * at.getScaleX()), Math.round(at.getTranslateY() + y * at.getScaleY())));
            int dw = (int) Math.round(lw * at.getScaleX()), dh = (int) Math.round(lh * at.getScaleY());
            if (Math.abs(img.getWidth() - dw) > 2 || Math.abs(img.getHeight() - dh) > 2) {
                // тимчасова картинка іншого розміру (cachedQuick), поки якісна рахується: швидко, найближчим пікселем
                d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
                d.drawImage(img, 0, 0, dw, dh, null);
                return;
            }
            d.drawImage(img, 0, 0, null);
        } finally {
            d.dispose();
        }
    }

    /** Вікно згорнуте: анімації не потрібні. */
    public static boolean iconified(Component c) {
        Window w = c == null ? null : javax.swing.SwingUtilities.getWindowAncestor(c);
        return w instanceof Frame && (((Frame) w).getExtendedState() & Frame.ICONIFIED) != 0;
    }
}
