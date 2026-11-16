/*
 * Copyright (C) 2021-2026 GummerCraft <http://gummercraft.fun>
 */

package com.gummer.launcher.dialog;

import com.gummer.launcher.support.SupportService;
import lombok.extern.java.Log;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.function.IntConsumer;
import java.util.logging.Level;

/**
 * 16.11.2026: число непрочитаних відповідей підтримки на кнопці «Підтримка» у всіх трьох виглядах.
 *
 * <p>Раніше перевірка була лише раз при старті, тож відповідь, що прийшла, поки лаунчер відкритий, гравець не бачив.
 * Тепер: перша перевірка через {@link #FIRST_MS} після показу вікна, далі кожні {@link #EVERY_MS}, лише поки вікно
 * видно (під час гри вікно сховане, запитів немає) і лише якщо в гравця є розмова (інакше мережі немає взагалі). Поки
 * відкрите вікно підтримки, воно саме показує відповіді і позначає їх прочитаними: значок 0. Сповіщення лише тоді,
 * коли непрочитаних стало більше, ніж було.</p>
 */
@Log
public final class SupportUnread {

    static final int FIRST_MS = 1500;
    static final int EVERY_MS = 60_000;

    private final Window frame;
    private final SupportService service;
    private final IntConsumer onCount;
    private final Runnable onNewReply;
    private final Timer timer;
    private int last;
    private boolean busy;

    /**
     * @param onCount    у потоці Swing: скільки непрочитаних (0: значок сховати)
     * @param onNewReply у потоці Swing: непрочитаних побільшало (сповіщення)
     */
    public static SupportUnread watch(Window frame, SupportService service, IntConsumer onCount, Runnable onNewReply) {
        return new SupportUnread(frame, service, onCount, onNewReply);
    }

    private SupportUnread(Window frame, SupportService service, IntConsumer onCount, Runnable onNewReply) {
        this.frame = frame;
        this.service = service;
        this.onCount = onCount;
        this.onNewReply = onNewReply;
        timer = new Timer(EVERY_MS, e -> check());
        timer.setInitialDelay(FIRST_MS);
        timer.start();
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                timer.stop(); // вікно перестворюється після гри і при зміні вигляду
            }
        });
    }

    private void check() {
        if (SupportDialog.isOpen()) {
            last = 0;
            onCount.accept(0);
            return;
        }
        if (busy || !frame.isShowing() || !service.hasConversation()) return;
        busy = true;
        new SwingWorker<Integer, Void>() {
            @Override
            protected Integer doInBackground() throws Exception {
                return service.unreadReplies();
            }

            @Override
            protected void done() {
                busy = false;
                try {
                    int n = get();
                    if (SupportDialog.isOpen()) n = 0;
                    onCount.accept(n);
                    if (n > last) onNewReply.run();
                    last = n;
                } catch (Exception e) {
                    log.log(Level.INFO, "Support: could not check replies: " + e);
                }
            }
        }.execute();
    }

    /**
     * Червоний значок з числом у правому верхньому куті кнопки (1-9, далі «9+»), з темним обідком, щоб читався на
     * будь-якому фоні. Розмір кнопки не міняє: нічого не стрибає.
     *
     * @param right правий край значка, {@code top} верхній
     */
    public static void paint(Graphics2D g, int count, int right, int top, Color ring) {
        if (count <= 0) return;
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            String text = count > 9 ? "9+" : String.valueOf(count);
            Font f = UIManager.getFont("Label.font").deriveFont(Font.BOLD, 10.5f);
            FontMetrics fm = g2.getFontMetrics(f);
            int h = 16;
            int w = Math.max(h, fm.stringWidth(text) + 9);
            int x = right - w, y = top;
            g2.setColor(ring);
            g2.fillRoundRect(x - 2, y - 2, w + 4, h + 4, h + 4, h + 4);
            g2.setColor(new Color(0xE5, 0x39, 0x35));
            g2.fillRoundRect(x, y, w, h, h, h);
            g2.setFont(f);
            g2.setColor(Color.WHITE);
            g2.drawString(text, x + (w - fm.stringWidth(text)) / 2, y + (h - fm.getHeight()) / 2 + fm.getAscent());
        } finally {
            g2.dispose();
        }
    }
}
