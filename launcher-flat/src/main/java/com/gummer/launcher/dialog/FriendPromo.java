/*
 * Copyright (C) 2021-2026 GummerCraft <http://gummercraft.fun>
 */

package com.gummer.launcher.dialog;

import com.gummer.launcher.manage.FriendPlay;
import com.skcraft.launcher.Instance;
import com.skcraft.launcher.swing.InstanceTableModel;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;

import static com.skcraft.launcher.util.SharedLocale.tr;

/**
 * 26.11.2026 (власник: «це кіллер-фіча, і про неї потрібно "кричати"»): реклама помічника «Гра з другом» на головній
 * сторінці лаунчера. Наклейка «Грати з другом!» (як наклейка «Вхід заборонено!» StalDayZ на сайті gummercraft.fun:
 * біла картка з чорною рамкою, нахил, загнутий куточок, кругла іконка, що світиться). Жовту стрічку «Нове: ...» власник
 * 26.11 прибрав («наклейки достатньо»). Наклейку видно лише на збірках, де помічник працює ({@link FriendPlay#availability} OK), не в Main і StalDayZ.
 */
public final class FriendPromo {

    /** Синій і жовтий прапора, як прапорець на сайті. */
    static final Color BLUE = new Color(0x00, 0x57, 0xB7), YELLOW = new Color(0xFF, 0xD5, 0x00);

    private FriendPromo() {
    }

    /** Чи показувати рекламу на цій збірці. */
    public static boolean worth(Instance i) {
        if (i == null || InstanceTableModel.isBrowser(i) || InstanceTableModel.isAddRow(i) || InstanceTableModel.isDesktopRow(i)) {
            return false;
        }
        if (!i.isLocal() || !i.isInstalled()) return false;
        // 27.11.2026: і ваніль: помічник зробить копію з завантажувачем (FriendPlayDialog.vanillaPage)
        FriendPlay.Availability a = FriendPlay.availability(i);
        return a == FriendPlay.Availability.OK || a == FriendPlay.Availability.VANILLA;
    }

    // ------------------------------------------------------------------ іконка «дві людини»

    /** Дві людини (мультиплеєр), той самий малюнок, що на значку «готово до гри з другом». Розмір {@code s}. */
    static void paintPeople(Graphics2D g2, double x, double y, double s, Color fg) {
        com.gummer.launcher.manage.FriendReady.paintPeople(g2, x, y, s, fg);
    }

    // ------------------------------------------------------------------ наклейка

    /** Наклейка «Грати з другом!»: клік відкриває помічник для вибраної збірки. */
    public static final class Sticker extends JComponent {
        static final int W = 112, H = 140;
        private static final double REST = 9, HOVER = -4;
        private final Runnable action;
        private double angle = REST, scale = 1;

        public Sticker(Runnable action) {
            this.action = action;
            setOpaque(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setToolTipText(tr("friend.sticker.tip"));
            Dimension d = new Dimension(W, H);
            setPreferredSize(d);
            setMinimumSize(d);
            setMaximumSize(d);
            // 26.11.2026 (власник: «анімація в 2 фпс йде»): без таймера. Кожен кадр наклейки перемальовував під нею фон
            // Liquid Glass, тож пульсація світіння гальмувала. Під мишею поворот одним кадром, світіння стале.
            MouseAdapter m = new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    setHover(true);
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    setHover(false);
                }

                @Override
                public void mouseClicked(MouseEvent e) {
                    if (SwingUtilities.isLeftMouseButton(e) && action != null) action.run();
                }
            };
            addMouseListener(m);
        }

        private void setHover(boolean on) {
            angle = on ? HOVER : REST;
            scale = on ? 1.08 : 1;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            com.gummer.launcher.look.TextAA.apply(g2);
            double k = Math.min(getWidth() / (double) W, getHeight() / (double) H);
            g2.translate(getWidth() / 2.0, getHeight() / 2.0);
            g2.scale(k * scale, k * scale);
            g2.rotate(Math.toRadians(angle));
            paintBody(g2);
            g2.dispose();
        }

        /** Малюється навколо (0, 0): картка 88x112. */
        private void paintBody(Graphics2D g2) {
            double w = 86, h = 110, x = -w / 2, y = -h / 2, arc = 22, fold = 18;
            // тінь
            for (int i = 6; i >= 1; i--) {
                g2.setColor(new Color(0, 0, 0, 14));
                g2.fill(new RoundRectangle2D.Double(x - i * 0.6, y + 5 - i * 0.4, w + i * 1.2, h + i * 1.2, arc + i, arc + i));
            }
            // 26.11.2026 (власник: «видно білі пікселі за чорною лінією, а чорна лінія обривчаста»): рамка не штрихом
            // поверх білої заливки, а суцільною чорною фігурою (картка плюс обведення 2B), на яку кладеться біла картка.
            // Зовнішній край лише чорний, без білої облямівки згладжування, товщина однакова з усіх боків.
            final double B = 3.0;
            Area card = new Area(new RoundRectangle2D.Double(x, y, w, h, arc, arc));
            Path2D.Double corner = new Path2D.Double();
            corner.moveTo(x + w - fold, y - 4);
            corner.lineTo(x + w + 4, y - 4);
            corner.lineTo(x + w + 4, y + fold);
            corner.lineTo(x + w, y + fold);
            corner.lineTo(x + w - fold, y);
            corner.closePath();
            card.subtract(new Area(corner));
            Color ink = new Color(0x11, 0x11, 0x11);
            g2.setColor(ink);
            g2.fill(outline(card, B));
            g2.setColor(Color.WHITE);
            g2.fill(card);
            // загнутий куточок: так само чорна фігура, на ній сіра
            Path2D.Double flap = new Path2D.Double();
            flap.moveTo(x + w - fold, y);
            flap.lineTo(x + w - fold, y + fold);
            flap.lineTo(x + w, y + fold);
            flap.closePath();
            Area flapA = new Area(flap);
            g2.setColor(ink);
            g2.fill(outline(flapA, 1.4));
            g2.setColor(new Color(0xD9, 0xD9, 0xD9));
            g2.fill(flapA);

            // кругла іконка зі світінням
            double d = 42, cx = 0, cy = y + 8 + d / 2;
            for (int i = 4; i >= 1; i--) {
                double r = d / 2 + i * 1.6;
                g2.setColor(new Color(255, 212, 0, 34));
                g2.fill(new Ellipse2D.Double(cx - r, cy - r, r * 2, r * 2));
            }
            g2.setColor(BLUE);
            g2.fill(new Ellipse2D.Double(cx - d / 2, cy - d / 2, d, d));
            paintPeople(g2, cx - 13, cy - 11, 26, YELLOW);

            // текст
            Font base = UIManager.getFont("Label.font");
            Font big = base.deriveFont(Font.BOLD, 13.5f), small = base.deriveFont(Font.BOLD, 11.5f);
            double ty = cy + d / 2 + 16;
            ty = line(g2, tr("friend.sticker.top").toUpperCase(), big, new Color(0x11, 0x11, 0x11), ty, w - 10);
            ty = line(g2, tr("friend.sticker.mid").toUpperCase(), big, new Color(0x11, 0x11, 0x11), ty + 1, w - 10);
            line(g2, tr("friend.sticker.link") + " →", small, BLUE, ty + 3, w - 8);
        }

        /** Фігура разом з обведенням товщиною {@code b} назовні (для заливки, не штриха). */
        private static Area outline(Area a, double b) {
            Area o = new Area(new BasicStroke((float) (2 * b), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND).createStrokedShape(a));
            o.add(a);
            return o;
        }

        /** Рядок по центру, зменшується, якщо не влазить. Повертає y наступного рядка. */
        private static double line(Graphics2D g2, String s, Font f, Color c, double y, double max) {
            g2.setFont(f);
            FontMetrics fm = g2.getFontMetrics();
            while (fm.stringWidth(s) > max && f.getSize2D() > 9f) {
                f = f.deriveFont(f.getSize2D() - 0.5f);
                g2.setFont(f);
                fm = g2.getFontMetrics();
            }
            g2.setColor(c);
            g2.drawString(s, (float) (-fm.stringWidth(s) / 2.0), (float) y);
            return y + fm.getAscent() * 0.95;
        }
    }
}
