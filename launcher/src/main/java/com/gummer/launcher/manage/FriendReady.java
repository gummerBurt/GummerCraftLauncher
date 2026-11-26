/*
 * Copyright (C) 2021-2026 GummerCraft <http://gummercraft.fun>
 */

package com.gummer.launcher.manage;

import com.skcraft.launcher.Instance;

import javax.swing.SwingUtilities;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.io.File;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 26.11.2026 (власник: «познач якось ті збірки, котрі готові до гри з другом ... як на робочому столі, так і у
 * "встановлених"»): збірка готова до гри з другом, коли помічник для неї працює і мод для гри з другом (LAN чи через
 * інтернет) уже стоїть і ввімкнений. Читання модів іде у фоні (один потік), малювання бере лише готову відповідь; тека
 * модів перевіряється не частіше ніж раз на 3 с (час зміни теки міняється, коли мод додали, видалили чи вимкнули).
 */
public final class FriendReady {

    /** Синій і жовтий, як на наклейці «Грати з другом!». */
    public static final Color BLUE = new Color(0x00, 0x57, 0xB7), YELLOW = new Color(0xFF, 0xD5, 0x00);
    private static final long RECHECK_MS = 3000;

    private static final class Entry {
        volatile boolean ready;
        volatile long modsTime = Long.MIN_VALUE;
        volatile long checkedAt;
        volatile boolean pending;
    }

    private static final Map<String, Entry> cache = new ConcurrentHashMap<String, Entry>();
    private static final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "friend-ready");
        t.setDaemon(true);
        t.setPriority(Thread.MIN_PRIORITY);
        return t;
    });
    private static volatile Runnable changed;

    private FriendReady() {
    }

    /** Що перемалювати, коли відповідь для якоїсь збірки змінилась (вікно лаунчера). */
    public static void setChanged(Runnable r) {
        changed = r;
    }

    /** Чи готова збірка (з кешу; перша відповідь false, справжня приходить через {@link #setChanged}). */
    public static boolean of(Instance instance) {
        if (instance == null || !instance.isLocal() || !instance.isInstalled() || instance.getDir() == null) return false;
        String key = instance.getDir().getAbsolutePath();
        Entry e = cache.get(key);
        if (e == null) {
            Entry n = new Entry();
            e = cache.putIfAbsent(key, n);
            if (e == null) e = n;
        }
        long now = System.currentTimeMillis();
        if (!e.pending && now - e.checkedAt > RECHECK_MS) {
            e.pending = true;
            e.checkedAt = now;
            final Entry entry = e;
            worker.execute(() -> check(instance, entry));
        }
        return e.ready;
    }

    /** Перевірити одразу (після помічника чи «Видалити моди мультиплеєра»). */
    public static void recheck(Instance instance) {
        if (instance == null || instance.getDir() == null) return;
        Entry e = cache.get(instance.getDir().getAbsolutePath());
        if (e != null) {
            e.modsTime = Long.MIN_VALUE;
            e.checkedAt = 0;
        }
        of(instance);
    }

    private static void check(Instance instance, Entry e) {
        try {
            File mods = InstanceMods.modsDir(instance);
            long t = mods.lastModified();
            if (t == e.modsTime) return;
            boolean ready = t != 0 && FriendPlay.availability(instance) == FriendPlay.Availability.OK && FriendPlay.hasFriendMod(instance);
            e.modsTime = t;
            if (ready != e.ready) {
                e.ready = ready;
                Runnable r = changed;
                if (r != null) SwingUtilities.invokeLater(r);
            }
        } catch (RuntimeException ex) {
            // збірка зникла чи тека недоступна: лишається попередня відповідь
        } finally {
            e.checkedAt = System.currentTimeMillis();
            e.pending = false;
        }
    }

    // ------------------------------------------------------------------ малювання

    /** Значок у лівому нижньому куті іконки w x h: темне кільце, синє коло, жовті «дві людини». */
    public static void paintBadge(Graphics g, double x, double y, double w, double h) {
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            double d = Math.max(11, Math.min(w, h) * 0.46), ring = Math.max(1.5, d * 0.12);
            double bx = x, by = y + h - d;
            g2.setColor(new Color(16, 18, 22));
            g2.fill(new Ellipse2D.Double(bx - ring, by - ring, d + ring * 2, d + ring * 2));
            paintDot(g2, bx, by, d);
        } finally {
            g2.dispose();
        }
    }

    /** Синє коло діаметра d з жовтими людьми (без кільця). */
    public static void paintDot(Graphics2D g2, double x, double y, double d) {
        g2.setColor(BLUE);
        g2.fill(new Ellipse2D.Double(x, y, d, d));
        double s = d * 0.64;
        paintPeople(g2, x + (d - s) / 2, y + (d - s) / 2 - d * 0.02, s, YELLOW);
    }

    /** Дві людини (мультиплеєр): задня трохи праворуч і вище, передня більша. Розмір {@code s}. */
    public static void paintPeople(Graphics2D g2, double x, double y, double s, Color fg) {
        Area back = person(x + s * 0.50, y + s * 0.06, s * 0.46);
        Area front = person(x + s * 0.06, y + s * 0.20, s * 0.56);
        back.subtract(grow(front, s * 0.07)); // проміжок між фігурами, щоб читались як дві
        g2.setColor(fg);
        g2.fill(back);
        g2.fill(front);
    }

    private static Area person(double x, double y, double s) {
        Area a = new Area(new Ellipse2D.Double(x + s * 0.30, y, s * 0.40, s * 0.40));
        Area body = new Area(new Ellipse2D.Double(x + s * 0.05, y + s * 0.47, s * 0.90, s * 0.80));
        body.intersect(new Area(new Rectangle.Double(x, y + s * 0.47, s, s * 0.53)));
        a.add(body);
        return a;
    }

    private static Area grow(Area a, double d) {
        Rectangle r = a.getBounds();
        double sx = (r.getWidth() + 2 * d) / r.getWidth(), sy = (r.getHeight() + 2 * d) / r.getHeight();
        AffineTransform t = new AffineTransform();
        t.translate(r.getCenterX(), r.getCenterY());
        t.scale(sx, sy);
        t.translate(-r.getCenterX(), -r.getCenterY());
        return a.createTransformedArea(t);
    }
}
