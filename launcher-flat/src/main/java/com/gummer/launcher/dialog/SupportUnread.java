/*
 * Copyright (C) 2021-2026 GummerCraft <http://gummercraft.fun>
 */

package com.gummer.launcher.dialog;

import com.gummer.launcher.support.SupportMessage;
import com.gummer.launcher.support.SupportService;
import com.skcraft.launcher.Instance;
import lombok.extern.java.Log;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.List;
import java.util.function.IntConsumer;
import java.util.function.Supplier;
import java.util.logging.Level;

/**
 * 16.11.2026: число непрочитаних відповідей підтримки на кнопці «Підтримка» у всіх трьох виглядах.
 *
 * <p>Раніше перевірка була лише раз при старті, тож відповідь, що прийшла, поки лаунчер відкритий, гравець не бачив.
 * Тепер: перша перевірка через {@link #FIRST_MS} після показу вікна, далі кожні {@link #EVERY_MS}, лише поки вікно
 * видно (під час гри вікно сховане, запитів немає) і лише якщо в гравця є розмова (інакше мережі немає взагалі). Поки
 * відкрите вікно підтримки, воно саме показує відповіді і позначає їх прочитаними: значок 0. Сповіщення лише тоді,
 * коли непрочитаних стало більше, ніж було.</p>
 *
 * <p>20.11.2026 (тікет #88, власник: «варто зробити трішки більше "сповіщення" про повідомлення від тех підтримки, бо
 * їх толком не читають»): крім значка і тосту, нова відповідь відкриває вікно з її текстом і кнопкою «Відкрити чат»
 * ({@link #popup}). Раз на кожну нову відповідь за запуск лаунчера: «Пізніше» не повторюється щохвилини, але при
 * наступному запуску, поки відповідь не прочитано, вікно з'явиться знову.</p>
 */
@Log
public final class SupportUnread {

    static final int FIRST_MS = 1500;
    static final int EVERY_MS = 60_000;

    private final Window frame;
    private final SupportService service;
    private final IntConsumer onCount;
    private final Runnable onNewReply;
    private final Supplier<Instance> selected;
    private final Timer timer;
    /** Найновіша відповідь, яку вже показано у вікні (на весь процес: вікно лаунчера перестворюється після гри). */
    private static long popupShownId;
    private static boolean popupOpen;
    private int last;
    private boolean busy;

    /**
     * @param onCount    у потоці Swing: скільки непрочитаних (0: значок сховати)
     * @param onNewReply у потоці Swing: непрочитаних побільшало (сповіщення)
     */
    public static SupportUnread watch(Window frame, SupportService service, Supplier<Instance> selected,
                                      IntConsumer onCount, Runnable onNewReply) {
        return new SupportUnread(frame, service, selected, onCount, onNewReply);
    }

    private SupportUnread(Window frame, SupportService service, Supplier<Instance> selected, IntConsumer onCount, Runnable onNewReply) {
        this.frame = frame;
        this.service = service;
        this.selected = selected;
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
        new SwingWorker<List<SupportMessage>, Void>() {
            @Override
            protected List<SupportMessage> doInBackground() throws Exception {
                return service.unreadReplyMessages();
            }

            @Override
            protected void done() {
                busy = false;
                try {
                    List<SupportMessage> unread = get();
                    int n = SupportDialog.isOpen() ? 0 : unread.size();
                    onCount.accept(n);
                    if (n > last) onNewReply.run();
                    last = n;
                    if (n > 0) popup(unread.get(unread.size() - 1), n);
                } catch (Exception e) {
                    log.log(Level.INFO, "Support: could not check replies: " + e);
                }
            }
        }.execute();
    }

    /** Вікно з текстом найновішої відповіді: «Відкрити чат» або «Пізніше». */
    private void popup(SupportMessage newest, int count) {
        if (newest.id <= popupShownId || popupOpen || !frame.isShowing() || SupportDialog.isOpen()) return;
        popupShownId = newest.id;
        String body = newest.content != null ? newest.content.trim() : "";
        if (body.isEmpty() && !newest.attachments.isEmpty()) body = com.skcraft.launcher.util.SharedLocale.tr("support.popupAttachment");
        if (body.length() > 700) body = body.substring(0, 700) + "...";
        String who = newest.senderName != null && !newest.senderName.trim().isEmpty()
                ? newest.senderName.trim() : com.skcraft.launcher.util.SharedLocale.tr("support.popupAdmin");

        JPanel p = new JPanel(new BorderLayout(0, 8));
        p.setOpaque(false);
        JLabel head = new JLabel(com.skcraft.launcher.util.SharedLocale.tr(count > 1 ? "support.popupHeadMany" : "support.popupHead", who, count));
        head.setFont(head.getFont().deriveFont(Font.BOLD));
        p.add(head, BorderLayout.NORTH);
        JTextArea text = new JTextArea(body);
        text.setLineWrap(true);
        text.setWrapStyleWord(true);
        text.setEditable(false);
        text.setRows(Math.min(10, Math.max(3, body.length() / 50 + 1)));
        text.setColumns(44);
        text.setCaretPosition(0);
        JScrollPane sp = new JScrollPane(text);
        p.add(sp, BorderLayout.CENTER);
        p.add(new JLabel(com.skcraft.launcher.util.SharedLocale.tr("support.popupHint")), BorderLayout.SOUTH);

        String open = com.skcraft.launcher.util.SharedLocale.tr("support.popupOpen");
        String later = com.skcraft.launcher.util.SharedLocale.tr("support.popupLater");
        popupOpen = true;
        int a;
        try {
            a = JOptionPane.showOptionDialog(frame, p, com.skcraft.launcher.util.SharedLocale.tr("support.popupTitle"), JOptionPane.DEFAULT_OPTION,
                    JOptionPane.INFORMATION_MESSAGE, null, new Object[]{open, later}, open);
        } finally {
            popupOpen = false;
        }
        if (a == 0) SupportDialog.showFor(frame, service, selected);
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
            com.gummer.launcher.look.TextAA.apply(g2);
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
