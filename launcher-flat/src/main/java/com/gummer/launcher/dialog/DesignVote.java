/*
 * Copyright (C) 2021-2026 GummerCraft <http://gummercraft.fun>
 */

package com.gummer.launcher.dialog;

import com.gummer.launcher.support.SupportService;
import com.skcraft.launcher.Launcher;
import com.skcraft.launcher.util.HttpRequest;
import lombok.extern.java.Log;
import net.miginfocom.swing.MigLayout;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;

import static com.skcraft.launcher.util.SharedLocale.tr;

/**
 * 16.11.2026: блок дизайну в кожному вигляді лаунчера (Liquid Glass, Класика, Бібліотека): зверху «Вигляд: назва ▾»
 * (перемикач без заходу в налаштування), під ним «♥ N» і «Фідбек по дизайну». Висота стала: {@link #HEIGHT}.
 *
 * <p>Лайк: один з однієї IP-адреси на кожен вигляд, рахує сервер ({@code designLikeUrl} у launcher.properties,
 * {@code tools/cdn/design-like.php}). Повторне натискання забирає лайк. Ряд має сталий розмір від першого кадру: поки число
 * не прийшло, на його місці «·», тож нічого не стрибає. Фідбек: окрема розмова в Chatwoot через
 * {@link SupportService#submitDesignFeedback}.</p>
 */
@Log
public final class DesignVote extends JPanel {

    private static final ExecutorService NET = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "design-vote");
        t.setDaemon(true);
        return t;
    });
    /** Останні відомі числа на вигляд: після перемикання вигляду чи перезапуску вікна вони видні з першого кадру. */
    private static final Map<String, int[]> KNOWN = new HashMap<String, int[]>();
    private static final Color HEART = new Color(0xEF5A70);
    private static final Color TEXT = new Color(0xE6E8EC);
    private static final Color DIM = new Color(0x9AA0A9);
    private static long lastFeedbackAt;
    /** Висота блоку: два ряди по 28 і проміжок 6. */
    public static final int HEIGHT = 62;
    private static final String[] LOOKS = {com.gummer.launcher.FancyLauncher.LOOK_GLASS,
            com.gummer.launcher.FancyLauncher.LOOK_CLASSIC, com.gummer.launcher.FancyLauncher.LOOK_LIBRARY};

    private final Window owner;
    private final Launcher launcher;
    private final SupportService support;
    private final String look;
    private final String lookTitle;
    private final String likeUrl;

    private int count = -1;
    private boolean liked;
    private boolean busy;

    public static DesignVote create(Window owner, Launcher launcher, SupportService support, String look) {
        String url = launcher.getProperties().getProperty("designLikeUrl", "").trim();
        return new DesignVote(owner, launcher, support, look, url);
    }

    private DesignVote(Window owner, Launcher launcher, SupportService support, String look, String likeUrl) {
        super(new MigLayout("insets 0, gap 6 6, novisualpadding", "[][grow,fill]", "[28!][28!]"));
        this.owner = owner;
        this.launcher = launcher;
        this.support = support;
        this.look = look;
        this.lookTitle = tr("options.look." + look);
        this.likeUrl = likeUrl;
        setOpaque(false);
        setAlignmentX(Component.LEFT_ALIGNMENT);

        synchronized (KNOWN) {
            int[] k = KNOWN.get(look);
            if (k != null) {
                count = k[0];
                liked = k[1] == 1;
            }
        }
        add(new LookPill(), "span 2, growx, wrap");
        if (!likeUrl.isEmpty()) {
            add(new LikePill(), "w 58!");
            NET.submit(() -> request(null));
        }
        if (support != null) {
            add(new FeedbackPill(), likeUrl.isEmpty() ? "span 2, growx" : "growx");
        }
    }

    // ------------------------------------------------------------------ мережа

    /** @param set null: лише прочитати; true/false: поставити або забрати лайк */
    private void request(Boolean set) {
        try {
            HttpRequest.Form form = HttpRequest.Form.form().add("look", look);
            if (set != null) form.add("set", set ? "1" : "0");
            LikeReply r = HttpRequest.post(HttpRequest.url(likeUrl))
                    .bodyForm(form)
                    .execute()
                    .expectResponseCode(200)
                    .returnContent()
                    .asJson(LikeReply.class);
            SwingUtilities.invokeLater(() -> {
                count = Math.max(0, r.count);
                liked = r.liked;
                busy = false;
                synchronized (KNOWN) {
                    KNOWN.put(look, new int[]{count, liked ? 1 : 0});
                }
                repaint();
            });
        } catch (Exception e) {
            log.log(Level.INFO, "Design like: " + e);
            SwingUtilities.invokeLater(() -> {
                if (set != null) { // повернути як було
                    liked = !set;
                    if (count >= 0) count = Math.max(0, count + (set ? -1 : 1));
                }
                busy = false;
                repaint();
            });
        }
    }

    public static final class LikeReply {
        public int count;
        public boolean liked;
    }

    // ------------------------------------------------------------------ кнопки

    private abstract class Pill extends JComponent {
        boolean hover;

        Pill() {
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setFont(UIManager.getFont("Label.font").deriveFont(Font.PLAIN, 12f));
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
                public void mouseClicked(MouseEvent e) {
                    if (SwingUtilities.isLeftMouseButton(e)) clicked();
                }
            });
        }

        abstract void clicked();

        Graphics2D background(Graphics g, boolean active) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            RoundRectangle2D r = new RoundRectangle2D.Float(0.5f, 0.5f, getWidth() - 1f, getHeight() - 1f, getHeight(), getHeight());
            g2.setColor(new Color(255, 255, 255, active ? 34 : hover ? 30 : 18));
            g2.fill(r);
            g2.setColor(active ? new Color(HEART.getRed(), HEART.getGreen(), HEART.getBlue(), 120) : new Color(255, 255, 255, hover ? 60 : 36));
            g2.draw(r);
            return g2;
        }
    }

    private final class LikePill extends Pill {
        LikePill() {
            updateTip();
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(58, 28);
        }

        void updateTip() {
            setToolTipText(liked ? tr("design.likedTip", lookTitle) : tr("design.likeTip", lookTitle));
        }

        @Override
        void clicked() {
            if (busy) return;
            busy = true;
            boolean set = !liked;
            liked = set; // одразу, сервер потім підтвердить
            if (count >= 0) count = Math.max(0, count + (set ? 1 : -1));
            updateTip();
            repaint();
            NET.submit(() -> request(set));
        }

        @Override
        protected void paintComponent(Graphics g) {
            updateTip();
            Graphics2D g2 = background(g, liked);
            int h = getHeight();
            float s = 12f, hx = 11f, hy = (h - s) / 2f + 0.5f;
            Path2D heart = new Path2D.Float();
            heart.moveTo(hx + s / 2, hy + s * 0.92f);
            heart.curveTo(hx - s * 0.15f, hy + s * 0.48f, hx - s * 0.02f, hy - s * 0.05f, hx + s * 0.27f, hy + s * 0.02f);
            heart.curveTo(hx + s * 0.40f, hy + s * 0.05f, hx + s * 0.47f, hy + s * 0.15f, hx + s / 2, hy + s * 0.24f);
            heart.curveTo(hx + s * 0.53f, hy + s * 0.15f, hx + s * 0.60f, hy + s * 0.05f, hx + s * 0.73f, hy + s * 0.02f);
            heart.curveTo(hx + s * 1.02f, hy - s * 0.05f, hx + s * 1.15f, hy + s * 0.48f, hx + s / 2, hy + s * 0.92f);
            heart.closePath();
            if (liked) {
                g2.setColor(HEART);
                g2.fill(heart);
            } else {
                g2.setColor(hover ? HEART : DIM);
                g2.setStroke(new BasicStroke(1.4f));
                g2.draw(heart);
            }
            String text = count < 0 ? "·" : count > 9999 ? (count / 1000) + "k" : String.valueOf(count);
            g2.setFont(getFont().deriveFont(Font.BOLD));
            FontMetrics fm = g2.getFontMetrics();
            g2.setColor(liked ? TEXT : DIM);
            g2.drawString(text, 29, (h - fm.getHeight()) / 2 + fm.getAscent());
            g2.dispose();
        }
    }

    /** «Вигляд: назва ▾»: меню з трьох виглядів (відкривається вгору, блок завжди внизу вікна), вибір одразу. */
    private final class LookPill extends Pill {
        LookPill() {
            setToolTipText(tr("design.switchTip"));
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(120, 28);
        }

        /**
         * Коли меню востаннє закрилось. Відкрите меню закривається вже на натисканні миші (поза меню, тобто і на самій
         * плашці), а клік приходить після відпускання і відкривав його знову: другий клац «не закривав» (16.11.2026).
         */
        private long menuClosedAt;

        @Override
        void clicked() {
            if (System.currentTimeMillis() - menuClosedAt < 400) return; // цей клік щойно закрив меню
            JPopupMenu menu = new JPopupMenu();
            menu.addPopupMenuListener(new javax.swing.event.PopupMenuListener() {
                @Override
                public void popupMenuWillBecomeVisible(javax.swing.event.PopupMenuEvent e) {
                }

                @Override
                public void popupMenuWillBecomeInvisible(javax.swing.event.PopupMenuEvent e) {
                    menuClosedAt = System.currentTimeMillis();
                }

                @Override
                public void popupMenuCanceled(javax.swing.event.PopupMenuEvent e) {
                }
            });
            ButtonGroup group = new ButtonGroup();
            for (final String id : LOOKS) {
                String html = "<html><b>" + tr("options.look." + id) + "</b><br><span style='color:#9AA0A9'>"
                        + tr("design.lookDesc." + id) + "</span></html>";
                JRadioButtonMenuItem item = new JRadioButtonMenuItem(html, id.equals(look));
                group.add(item);
                item.addActionListener(e -> switchTo(id));
                menu.add(item);
            }
            Dimension d = menu.getPreferredSize();
            menu.show(this, 0, -d.height - 4);
        }

        private void switchTo(String id) {
            if (id.equals(look)) return;
            launcher.getConfig().setLauncherLook(id);
            com.skcraft.launcher.persistence.Persistence.commitAndForget(launcher.getConfig());
            com.gummer.launcher.FancyLauncher.reopenLauncherWindow(launcher, owner);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = background(g, false);
            int h = getHeight(), w = getWidth();
            // три маленькі віконця: «вигляди»
            g2.setColor(hover ? TEXT : DIM);
            for (int i = 0; i < 3; i++) {
                g2.fillRoundRect(11 + i * 5, (h - 10) / 2 + (i == 1 ? -2 : i == 2 ? 2 : 0), 4, 10, 2, 2);
            }
            String prefix = tr("design.lookPrefix") + " ";
            Font plain = getFont(), bold = getFont().deriveFont(Font.BOLD);
            FontMetrics fp = g2.getFontMetrics(plain), fb = g2.getFontMetrics(bold);
            int ty = (h - fp.getHeight()) / 2 + fp.getAscent();
            int x = 32;
            g2.setFont(plain);
            g2.setColor(DIM);
            g2.drawString(prefix, x, ty);
            x += fp.stringWidth(prefix);
            g2.setFont(bold);
            g2.setColor(TEXT);
            g2.drawString(lookTitle, x, ty);
            // стрілка вниз
            int cx = w - 16, cy = h / 2;
            Path2D arrow = new Path2D.Float();
            arrow.moveTo(cx - 4, cy - 2);
            arrow.lineTo(cx, cy + 2);
            arrow.lineTo(cx + 4, cy - 2);
            g2.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.setColor(hover ? TEXT : DIM);
            g2.draw(arrow);
            g2.dispose();
        }
    }

    private final class FeedbackPill extends Pill {
        FeedbackPill() {
            setToolTipText(tr("design.feedbackTip"));
        }

        @Override
        public Dimension getPreferredSize() {
            FontMetrics fm = getFontMetrics(getFont());
            return new Dimension(fm.stringWidth(tr("design.feedback")) + 24, 28);
        }

        @Override
        void clicked() {
            FeedbackDialog.open(owner, launcher, support, look, lookTitle);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = background(g, false);
            FontMetrics fm = g2.getFontMetrics(getFont());
            g2.setFont(getFont());
            String t = tr("design.feedback");
            g2.setColor(hover ? TEXT : DIM);
            g2.drawString(t, (getWidth() - fm.stringWidth(t)) / 2, (getHeight() - fm.getHeight()) / 2 + fm.getAscent());
            g2.dispose();
        }
    }

    // ------------------------------------------------------------------ вікно фідбеку

    private static final class FeedbackDialog extends JDialog {
        private static FeedbackDialog shown;
        private final JTextArea text = new JTextArea(7, 40);
        private final JTextField nick = new JTextField();
        private final JLabel status = new JLabel(" ");
        private final JButton send = new JButton(tr("design.feedbackSend"));

        static void open(Window owner, Launcher launcher, SupportService support, String look, String lookTitle) {
            if (shown != null && shown.isDisplayable()) {
                shown.toFront();
                return;
            }
            shown = new FeedbackDialog(owner, support, look, lookTitle);
            shown.setVisible(true);
        }

        private FeedbackDialog(Window owner, SupportService support, String look, String lookTitle) {
            super(owner, tr("design.feedbackTitle", lookTitle), ModalityType.MODELESS);
            setDefaultCloseOperation(DISPOSE_ON_CLOSE);
            JPanel p = new JPanel(new MigLayout("insets 18 20 16 20, wrap 1, fillx", "[0:0:,grow,fill]", ""));
            JTextArea intro = new JTextArea(tr("design.feedbackIntro", lookTitle));
            intro.setLineWrap(true);
            intro.setWrapStyleWord(true);
            intro.setEditable(false);
            intro.setFocusable(false);
            intro.setOpaque(false);
            intro.setBorder(null);
            intro.setFont(UIManager.getFont("Label.font"));
            intro.setForeground(UIManager.getColor("Label.foreground"));
            p.add(intro, "gapbottom 8");
            text.setLineWrap(true);
            text.setWrapStyleWord(true);
            p.add(new JScrollPane(text), "h 140:180:, growy, pushy");
            p.add(new JLabel(tr("design.feedbackNick")), "gaptop 8");
            String n = support.defaultNick();
            nick.setText(n == null ? "" : n);
            p.add(nick);
            status.setFont(UIManager.getFont("small.font"));
            JButton cancel = new JButton(tr("button.cancel"));
            JPanel buttons = new JPanel(new MigLayout("insets 0, gap 8", "[grow,fill][][]", ""));
            buttons.setOpaque(false);
            buttons.add(status);
            buttons.add(cancel);
            buttons.add(send);
            p.add(buttons, "gaptop 12");
            setContentPane(p);
            getRootPane().setDefaultButton(send);
            cancel.addActionListener(e -> dispose());
            send.addActionListener(e -> submit(support, look, lookTitle));
            Dimension size = com.gummer.launcher.look.LookTheme.fitToScreen(this, 500, 400, 400, 320);
            setSize(size);
            setMinimumSize(new Dimension(Math.min(400, size.width), Math.min(320, size.height)));
            com.gummer.launcher.look.LookUi.primary(send);
            com.gummer.launcher.look.LookUi.secondary(cancel);
            com.gummer.launcher.look.LookUi.decorate(this, owner);
            setLocationRelativeTo(owner);
        }

        private void submit(SupportService support, String look, String lookTitle) {
            String body = text.getText().trim();
            if (body.length() < 3) {
                status.setForeground(UIManager.getColor("Component.error.focusedBorderColor"));
                status.setText(tr("design.feedbackEmpty"));
                return;
            }
            long now = System.currentTimeMillis();
            if (now - lastFeedbackAt < 120_000L) {
                status.setForeground(UIManager.getColor("Component.error.focusedBorderColor"));
                status.setText(tr("design.feedbackWait"));
                return;
            }
            send.setEnabled(false);
            text.setEnabled(false);
            status.setForeground(UIManager.getColor("Label.disabledForeground"));
            status.setText(tr("design.feedbackSending"));
            String who = nick.getText().trim();
            NET.submit(() -> {
                try {
                    support.submitDesignFeedback(who, look, lookTitle, body);
                    SwingUtilities.invokeLater(() -> {
                        lastFeedbackAt = System.currentTimeMillis();
                        JOptionPane.showMessageDialog(this, tr("design.feedbackThanks"), getTitle(), JOptionPane.INFORMATION_MESSAGE);
                        dispose();
                    });
                } catch (Exception e) {
                    log.log(Level.WARNING, "Design feedback failed", e);
                    SwingUtilities.invokeLater(() -> {
                        send.setEnabled(true);
                        text.setEnabled(true);
                        status.setForeground(UIManager.getColor("Component.error.focusedBorderColor"));
                        status.setText(tr("design.feedbackFailed"));
                    });
                }
            });
        }
    }
}
