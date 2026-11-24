/*
 * Copyright (C) 2021-2026 GummerCraft <http://gummercraft.fun>
 */

package com.gummer.launcher.perf;

import java.awt.AWTEvent;
import java.awt.Component;
import java.awt.EventQueue;
import java.awt.Toolkit;
import java.awt.event.InvocationEvent;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 18.11.2026 (власник: «додай в тестовий лаунчер моніторинг перформансу, котрий працюватиме весь час і по котрому ти
 * потім прочитаєш слабкі місця»). Сторож потоку інтерфейсу: кожна подія (клік, перемальовування, завдання), що тримає
 * його довше {@value #SLOW_MS} мс, пишеться в {@code <dir>/launcher/perf/edt-ДАТА.log}: скільки, що за подія і де стояв
 * потік (знімки стеку кожні {@value #SAMPLE_MS} мс, поки подія триває). Вмикається лише в тестовому лаунчері (тека даних
 * закінчується на «-test») або з {@code -Dgummer.perf=true}. Файл пишеться у фоні.
 */
public final class EdtWatch extends EventQueue {

    /** Поріг у мс; 24.11.2026: змінюється {@code -Dgummer.perf.ms=N} (проби швидкості міряють від 16 мс). */
    static final int SLOW_MS = Math.max(1, Integer.getInteger("gummer.perf.ms", 60));
    /** Найдовша подія (мс) з останнього {@link #takeWorst()}: для проб, що міряють окремі дії. */
    private static final java.util.concurrent.atomic.AtomicLong worst = new java.util.concurrent.atomic.AtomicLong();
    private static final java.util.concurrent.atomic.AtomicLong blocked = new java.util.concurrent.atomic.AtomicLong();
    static final int SAMPLE_MS = 8;
    private static final int MAX_SAMPLES = 400;

    private final File dir;
    private final ExecutorService writer = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "EdtWatch writer");
        t.setDaemon(true);
        return t;
    });
    private volatile Thread edt;
    private volatile long started;
    private volatile int depth;
    private volatile boolean nested;
    private final List<StackTraceElement[]> samples = new ArrayList<StackTraceElement[]>();

    private EdtWatch(File dir) {
        this.dir = dir;
    }

    /** Увімкнути, якщо це тестовий лаунчер. Будь-яка помилка лишає звичайну чергу подій. */
    public static void installIfTest(File baseDir) {
        try {
            boolean on = Boolean.getBoolean("gummer.perf")
                    || baseDir != null && baseDir.getName().toLowerCase(java.util.Locale.ROOT).endsWith("-test");
            if (!on || baseDir == null) return;
            File dir = new File(baseDir, "launcher/perf");
            dir.mkdirs();
            final EdtWatch w = new EdtWatch(dir);
            Toolkit.getDefaultToolkit().getSystemEventQueue().push(w);
            Thread s = new Thread(w::sample, "EdtWatch sampler");
            s.setDaemon(true);
            s.setPriority(Thread.MAX_PRIORITY);
            s.start();
            w.log("--- старт " + new SimpleDateFormat("dd.MM.yyyy HH:mm:ss").format(new Date()) + ", поріг " + SLOW_MS + " мс");
        } catch (Throwable ignored) {
            // без сторожа
        }
    }

    @Override
    protected void dispatchEvent(AWTEvent event) {
        if (edt == null) edt = Thread.currentThread();
        boolean outer = depth == 0; // вкладена черга (модальне вікно): міряємо лише зовнішню подію
        if (outer) {
            synchronized (samples) {
                samples.clear();
            }
            started = System.nanoTime();
            nested = false;
        } else {
            nested = true; // модальне вікно всередині події: її час це час роботи гравця з вікном, не затуп
        }
        depth++;
        try {
            super.dispatchEvent(event);
        } finally {
            depth--;
            if (outer) {
                long ms = (System.nanoTime() - started) / 1000000L;
                started = 0;
                worst.accumulateAndGet(ms, Math::max);
                if (ms >= 16) blocked.addAndGet(ms);
                if (ms >= SLOW_MS && !nested) report(event, ms);
            }
        }
    }

    /**
     * 24.11.2026: найдовша подія потоку інтерфейсу і сума подій від 16 мс з минулого виклику, {@code {найдовша, сума}},
     * і скидання. Лише для проб швидкості (сторож має бути ввімкнений).
     */
    public static long[] takeWorst() {
        return new long[]{worst.getAndSet(0), blocked.getAndSet(0)};
    }

    private void sample() {
        while (true) {
            try {
                Thread.sleep(SAMPLE_MS);
            } catch (InterruptedException e) {
                return;
            }
            long s = started;
            Thread t = edt;
            if (s == 0 || t == null || depth > 1) continue;
            if ((System.nanoTime() - s) / 1000000L < SAMPLE_MS * 2) continue;
            StackTraceElement[] st = t.getStackTrace();
            synchronized (samples) {
                if (started == s && samples.size() < MAX_SAMPLES) samples.add(st);
            }
        }
    }

    private void report(AWTEvent event, final long ms) {
        final List<StackTraceElement[]> got;
        synchronized (samples) {
            got = new ArrayList<StackTraceElement[]>(samples);
            samples.clear();
        }
        final String what = describe(event);
        final String time = new SimpleDateFormat("HH:mm:ss.SSS").format(new Date());
        writer.execute(() -> {
            StringBuilder b = new StringBuilder();
            b.append("\n=== ").append(time).append(' ').append(ms).append(" ms  ").append(what).append("  samples ").append(got.size()).append('\n');
            // найглибший рядок коду лаунчера в кожному знімку: де саме час
            Map<String, Integer> own = new HashMap<String, Integer>();
            Map<String, Integer> top = new HashMap<String, Integer>();
            for (StackTraceElement[] st : got) {
                if (st.length > 0) top.merge(frame(st[0]), 1, Integer::sum);
                for (StackTraceElement e : st) {
                    String c = e.getClassName();
                    if (c.startsWith("com.gummer") || c.startsWith("com.skcraft")) {
                        own.merge(frame(e), 1, Integer::sum);
                        break;
                    }
                }
            }
            top(b, "  launcher: ", own);
            top(b, "  top:      ", top);
            // один знімок із середини повністю (лише код лаунчера, Swing, AWT, FlatLaf, ImageIO)
            if (!got.isEmpty()) {
                StackTraceElement[] mid = got.get(got.size() / 2);
                int n = 0;
                for (StackTraceElement e : mid) {
                    String c = e.getClassName();
                    if (!(c.startsWith("com.gummer") || c.startsWith("com.skcraft") || c.startsWith("javax.swing") || c.startsWith("java.awt")
                            || c.startsWith("com.formdev") || c.startsWith("javax.imageio") || c.startsWith("sun.") || c.startsWith("java.io")
                            || c.startsWith("java.net") || c.startsWith("java.util.zip"))) continue;
                    b.append("    at ").append(frame(e)).append('\n');
                    if (++n >= 30) break;
                }
            }
            log(b.toString());
        });
    }

    private static void top(StringBuilder b, String label, Map<String, Integer> m) {
        List<Map.Entry<String, Integer>> l = new ArrayList<Map.Entry<String, Integer>>(m.entrySet());
        l.sort((x, y) -> y.getValue() - x.getValue());
        for (int i = 0; i < Math.min(6, l.size()); i++) {
            b.append(i == 0 ? label : "            ").append(l.get(i).getValue()).append("x ").append(l.get(i).getKey()).append('\n');
        }
    }

    private static String frame(StackTraceElement e) {
        String c = e.getClassName();
        return c.substring(c.lastIndexOf('.') + 1) + "." + e.getMethodName() + ":" + e.getLineNumber();
    }

    private static String describe(AWTEvent event) {
        String s = event.getClass().getSimpleName() + "#" + event.getID();
        Object src = event.getSource();
        if (src instanceof Component) s += " on " + src.getClass().getName().replaceAll("^.*[.]", "");
        if (event instanceof InvocationEvent) {
            try {
                Field f = InvocationEvent.class.getDeclaredField("runnable");
                f.setAccessible(true);
                Object r = f.get(event);
                if (r != null) s += " " + r.getClass().getName();
            } catch (Throwable ignored) {
                // без назви завдання (Java 9+ може не пустити)
            }
        }
        return s;
    }

    private void log(String text) {
        File f = new File(dir, "edt-" + new SimpleDateFormat("yyyy-MM-dd").format(new Date()) + ".log");
        try (Writer w = new OutputStreamWriter(new FileOutputStream(f, true), StandardCharsets.UTF_8)) {
            w.write(text);
            w.write('\n');
        } catch (Exception ignored) {
            // диск недоступний: пропускаємо
        }
    }
}
