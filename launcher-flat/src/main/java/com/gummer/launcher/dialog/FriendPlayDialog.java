/*
 * Copyright (C) 2021-2026 GummerCraft <http://gummercraft.fun>
 */

package com.gummer.launcher.dialog;

import com.gummer.launcher.look.LookTheme;
import com.gummer.launcher.look.LookUi;
import com.gummer.launcher.manage.FriendPlay;
import com.gummer.launcher.manage.InstanceCopies;
import com.skcraft.launcher.Instance;
import com.skcraft.launcher.Launcher;
import com.skcraft.launcher.util.SharedLocale;
import lombok.extern.java.Log;
import net.miginfocom.swing.MigLayout;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.function.Consumer;

/**
 * 26.11.2026 (власник: «гра з другом ... зробимо з цього кіллерфічу ... максимально юзер френдлі для новачка/дитини»):
 * помічник «Гра з другом». Питає, чи друг в одній мережі, ставить і сам налаштовує мод ({@link FriendPlay}), пояснює
 * кроками, що робити в грі, і дає кнопки замість текстів: брандмауер Windows, «Передати збірку другу», для ванільної
 * збірки «Зробити копію на Fabric».
 */
@Log
public final class FriendPlayDialog {

    private FriendPlayDialog() {
    }

    /**
     * @param share відкрити «Імпорт/Експорт» цієї збірки (передати другу), або null
     * @param switchTo вибрати іншу збірку в головному вікні (після копії на Fabric), або null
     */
    public static void open(Window owner, Launcher launcher, Instance instance, Runnable share, Consumer<String> switchTo) {
        final JDialog d = new JDialog(owner, SharedLocale.tr("friend.title"), Dialog.ModalityType.DOCUMENT_MODAL);
        final LookTheme.Palette pal = LookTheme.palette();
        final CardLayout cards = new CardLayout();
        final JPanel root = new JPanel(cards);

        FriendPlay.Availability av = FriendPlay.availability(instance);
        if (av == FriendPlay.Availability.LOCKED) {
            root.add(page(pal, SharedLocale.tr("friend.title"), SharedLocale.tr("friend.locked"), closeRow(d)), "msg");
        } else if (av == FriendPlay.Availability.VANILLA) {
            root.add(vanillaPage(d, pal, launcher, instance, share, switchTo), "msg");
        } else {
            // 26.11.2026 (власник: «якшо вже моди поставлені, нашо показувати те ж діалогове вікно?»): мод уже стоїть,
            // одразу кроки в грі; «Налаштувати заново» повертає питання (інший спосіб, друг без ліцензії, голос)
            FriendPlay.Result had = FriendPlay.existing(instance);
            if (had != null) {
                Runnable again = () -> {
                    root.removeAll();
                    root.add(askPage(d, pal, cards, root, launcher, instance, share), "ask");
                    cards.show(root, "ask");
                    refit(d);
                };
                root.add(resultPage(d, pal, cards, root, launcher, instance, had.mode, false, had.voice != null, had, share, again), "done");
            } else {
                root.add(askPage(d, pal, cards, root, launcher, instance, share), "ask");
            }
        }
        d.setContentPane(root);
        LookUi.decorate(d, owner);
        d.setMinimumSize(new Dimension(560, 10));
        refit(d);
        d.setVisible(true);
    }

    /**
     * Розмір під поточну сторінку. Тексти з переносом (JTextArea) знають свою висоту лише після першої розкладки на
     * потрібну ширину, тож перший pack дає зайве місце внизу; другий уже точний.
     */
    private static void refit(JDialog d) {
        d.pack();
        d.validate();
        // 26.11.2026 (власник: «тут вікно занадто велике»): коли вікно вже показане, другий pack брав закешовані
        // розміри розкладки з першого (текст ще без ширини, тобто вищий): «Готово» мало 554 px замість 358. Нова
        // висота тексту кеш не скидає, тож скидаємо його самі для всього дерева.
        invalidateAll(d.getContentPane());
        d.pack();
        d.setLocationRelativeTo(d.getOwner());
    }

    private static void invalidateAll(Component c) {
        c.invalidate();
        if (c instanceof Container) for (Component ch : ((Container) c).getComponents()) invalidateAll(ch);
    }

    private static JPanel page(LookTheme.Palette pal, String title, String text, JComponent bottom) {
        JPanel p = new JPanel(new MigLayout("insets 20 22 18 22, wrap 1, gap 0 10, fillx", "[grow,fill]", ""));
        p.setOpaque(false);
        p.add(LookUi.title(title, 18f));
        if (text != null) p.add(LookUi.body(text, false), "w 520!, wmin 0");
        if (bottom != null) p.add(bottom, "gaptop 8");
        return p;
    }

    private static JPanel closeRow(JDialog d) {
        JPanel row = new JPanel(new MigLayout("insets 0, gap 8", "push[]", ""));
        row.setOpaque(false);
        JButton ok = LookUi.secondary(new JButton(SharedLocale.tr("friend.close")));
        ok.addActionListener(e -> d.dispose());
        row.add(ok, "h 34!");
        return row;
    }

    // ------------------------------------------------------------------ 28.11.2026: дитячий вигляд
    // Власник: «"грати з другом" ... більш інтерактивним для дітей, з виразнішим текстом, іконками-емодзі». Великі
    // картки замість двох однакових кнопок, смужка кроків, кроки в грі з цифрами та іконками, «Друг не бачить гру?» і
    // сторінка для того, кого кличуть. Іконки малює FunIcon: справжні емодзі Java показує лише чорно-білими.

    private static final int W = 540;

    private static JTextArea text(String s, float size, boolean dim) {
        JTextArea a = LookUi.body(s, dim);
        a.setFont(a.getFont().deriveFont(size));
        return a;
    }

    /** Заголовок сторінки: велика іконка, назва і підзаголовок. */
    private static JPanel header(FunIcon.Kind icon, String title, String sub) {
        JPanel h = new JPanel(new MigLayout("insets 0, gap 14 2, fillx", "[][grow,fill]", "[]0[]"));
        h.setOpaque(false);
        h.add(FunIcon.label(icon, 52), "spany 2, aligny top");
        h.add(LookUi.title(title, 21f), "wrap");
        if (sub != null) h.add(text(sub, 13.5f, true), "w " + (W - 70) + "!, wmin 0");
        return h;
    }

    private static JPanel column() {
        JPanel p = new JPanel(new MigLayout("insets 20 22 18 22, wrap 1, gap 0 10, fillx", "[grow,fill]", ""));
        p.setOpaque(false);
        return p;
    }

    /** Смужка кроків «Як граємо, Ставлю мод, Заходимо в гру, Кличемо друга»: зроблені з галочкою, поточний кольором. */
    private static final class Stepper extends JComponent {
        private final int current;
        private final String[] names = {SharedLocale.tr("friend.kid.step1"), SharedLocale.tr("friend.kid.step2"),
                SharedLocale.tr("friend.kid.step3"), SharedLocale.tr("friend.kid.step4")};

        Stepper(int current) {
            this.current = current;
            setPreferredSize(new Dimension(W, 54));
            setMinimumSize(new Dimension(W, 54));
            setFont(UIManager.getFont("Label.font").deriveFont(11.5f));
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            LookTheme.Palette pal = LookTheme.palette();
            int n = names.length, d = 26, w = getWidth(), slot = w / n, cy = 15;
            for (int i = 0; i < n - 1; i++) {
                g.setColor(i < current ? new Color(0x2EB872) : pal.line);
                g.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.drawLine(slot * i + slot / 2 + d / 2 + 4, cy, slot * (i + 1) + slot / 2 - d / 2 - 4, cy);
            }
            for (int i = 0; i < n; i++) {
                int cx = slot * i + slot / 2;
                if (i < current) {
                    new FunIcon(FunIcon.Kind.CHECK, d).paintIcon(this, g, cx - d / 2, cy - d / 2);
                } else if (i == current) {
                    FunIcon.number(i + 1, d).paintIcon(this, g, cx - d / 2, cy - d / 2);
                } else {
                    g.setColor(pal.line);
                    g.setStroke(new BasicStroke(2f));
                    g.drawOval(cx - d / 2 + 1, cy - d / 2 + 1, d - 2, d - 2);
                    g.setColor(pal.text3);
                    FunIcon.centerText(g, getFont().deriveFont(Font.BOLD, 13f), String.valueOf(i + 1), cx, cy);
                }
                g.setFont(i == current ? getFont().deriveFont(Font.BOLD) : getFont());
                g.setColor(i == current ? pal.text : pal.text3);
                FontMetrics fm = g.getFontMetrics();
                g.drawString(names[i], cx - fm.stringWidth(names[i]) / 2, cy + d / 2 + 16);
            }
            g.dispose();
        }
    }

    /** Велика картка вибору: іконка, назва, підпис; підсвічується під мишею, натискається мишею, Enter і пробілом. */
    private static JPanel choiceCard(FunIcon.Kind icon, String title, String sub, Runnable action) {
        // [0] миша над карткою, [1] фокус прийшов з клавіатури (перший автоматичний фокус вікна рамкою не малюється)
        final boolean[] hover = {false, false};
        final int[] focusCount = {0};
        final JPanel c = new JPanel(new MigLayout("insets 16 14 16 14, wrap 1, gap 0 6, align center top", "[center]", "")) {
            @Override
            protected void paintComponent(Graphics g0) {
                Graphics2D g = (Graphics2D) g0.create();
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                LookTheme.Palette pal = LookTheme.palette();
                boolean on = hover[0] || (hover[1] && isFocusOwner());
                // фокус клавіатури лише рамкою: заливка на першій картці виглядала як уже зроблений вибір
                g.setColor(hover[0] ? blend(pal.surface, pal.accent, .16f) : pal.surface);
                g.fillRoundRect(1, 1, getWidth() - 3, getHeight() - 3, 22, 22);
                g.setColor(on ? pal.accent : pal.line);
                g.setStroke(new BasicStroke(on ? 2.5f : 1.5f));
                g.drawRoundRect(1, 1, getWidth() - 3, getHeight() - 3, 22, 22);
                g.dispose();
            }
        };
        c.setOpaque(false);
        c.setFocusable(true);
        c.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        c.add(FunIcon.label(icon, 64));
        c.add(LookUi.title(title, 16f), "gaptop 4");
        JLabel s = new JLabel("<html><div style='text-align:center'>" + sub + "</div></html>");
        s.setForeground(LookTheme.palette().text3);
        s.setFont(s.getFont().deriveFont(12.5f));
        s.setHorizontalAlignment(SwingConstants.CENTER);
        c.add(s, "w 220!");
        c.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseEntered(java.awt.event.MouseEvent e) {
                hover[0] = true;
                c.repaint();
            }

            @Override
            public void mouseExited(java.awt.event.MouseEvent e) {
                hover[0] = false;
                c.repaint();
            }

            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                action.run();
            }
        });
        c.addKeyListener(new java.awt.event.KeyAdapter() {
            @Override
            public void keyPressed(java.awt.event.KeyEvent e) {
                if (e.getKeyCode() == java.awt.event.KeyEvent.VK_ENTER || e.getKeyCode() == java.awt.event.KeyEvent.VK_SPACE) action.run();
            }
        });
        c.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override
            public void focusGained(java.awt.event.FocusEvent e) {
                hover[1] = ++focusCount[0] > 1;
                c.repaint();
            }

            @Override
            public void focusLost(java.awt.event.FocusEvent e) {
                c.repaint();
            }
        });
        return c;
    }

    private static Color blend(Color a, Color b, float t) {
        return new Color(Math.round(a.getRed() + (b.getRed() - a.getRed()) * t), Math.round(a.getGreen() + (b.getGreen() - a.getGreen()) * t),
                Math.round(a.getBlue() + (b.getBlue() - a.getBlue()) * t));
    }

    /** Рядок-перемикач: іконка, назва, підпис і галочка праворуч; клік будь-де по рядку перемикає. */
    private static JPanel toggleRow(FunIcon.Kind icon, String title, String sub, String tip, final JCheckBox box) {
        JPanel r = LookUi.card("insets 8 12 8 10, gap 12 0, fillx", "[][grow,fill][]", "[]0[]");
        r.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        r.add(FunIcon.label(icon, 32), "spany 2");
        r.add(LookUi.title(title, 13.5f));
        box.setOpaque(false);
        r.add(box, "spany 2, wrap");
        JLabel s = new JLabel(sub);
        s.setForeground(LookTheme.palette().text3);
        s.setFont(s.getFont().deriveFont(12f));
        r.add(s, "wmin 0");
        if (tip != null) {
            r.setToolTipText(tip);
            box.setToolTipText(tip);
        }
        r.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                box.doClick();
            }
        });
        return r;
    }

    /** Крок у грі: кольорова цифра, іконка і одне-два речення. */
    private static JPanel stepRow(int n, FunIcon.Kind icon, String s) {
        JPanel r = new JPanel(new MigLayout("insets 0, gap 10 0, fillx", "[][][grow,fill]", "[top]"));
        r.setOpaque(false);
        r.add(new JLabel(FunIcon.number(n, 28)));
        r.add(FunIcon.label(icon, 28));
        r.add(text(s, 14f, false), "w " + (W - 90) + "!, wmin 0, gaptop 4");
        return r;
    }

    /** Порада з іконкою (сторінка «Друг не бачить гру?» і нотатки під кроками). */
    private static JPanel tipRow(FunIcon.Kind icon, String s) {
        JPanel r = new JPanel(new MigLayout("insets 0, gap 10 0, fillx", "[][grow,fill]", "[top]"));
        r.setOpaque(false);
        r.add(FunIcon.label(icon, 22));
        r.add(text(s, 13.5f, false), "w " + (W - 50) + "!, wmin 0, gaptop 2");
        return r;
    }

    private static JButton iconButton(JButton b, FunIcon.Kind icon) {
        b.setIcon(new FunIcon(icon, 18));
        b.setIconTextGap(8);
        return b;
    }

    private static void show(JDialog d, CardLayout cards, JPanel root, JPanel page, String name) {
        root.removeAll(); // інакше CardLayout бере висоту найбільшої сторінки і лишає порожнє місце
        root.add(page, name);
        cards.show(root, name);
        refit(d);
    }

    // ------------------------------------------------------------------ крок 1: питання

    private static JPanel askPage(JDialog d, LookTheme.Palette pal, CardLayout cards, JPanel root, Launcher launcher,
                                  Instance instance, Runnable share) {
        JPanel p = column();
        p.add(header(FunIcon.Kind.FRIENDS, SharedLocale.tr("friend.kid.title"), SharedLocale.tr("friend.kid.intro")));
        p.add(new Stepper(0), "gaptop 4");
        p.add(LookUi.title(SharedLocale.tr("friend.kid.where"), 16f), "gaptop 4");

        final JCheckBox offline = new JCheckBox((String) null, true);
        final JCheckBox voice = new JCheckBox((String) null, false);
        final JPanel[] self = new JPanel[1];

        java.util.function.Consumer<FriendPlay.Mode> go = mode -> {
            JPanel work = column();
            work.add(header(FunIcon.Kind.BOX, SharedLocale.tr("friend.kid.workingTitle"), SharedLocale.tr("friend.working")));
            work.add(new Stepper(1), "gaptop 4");
            JProgressBar bar = new JProgressBar();
            bar.setIndeterminate(true);
            work.add(bar, "h 8!, gaptop 6");
            final String[] hints = {SharedLocale.tr("friend.kid.work1"), SharedLocale.tr("friend.kid.work2"),
                    SharedLocale.tr("friend.kid.work3"), SharedLocale.tr("friend.kid.work4")};
            final JLabel hint = new JLabel(hints[0]);
            hint.setForeground(pal.text2);
            hint.setFont(hint.getFont().deriveFont(13f));
            work.add(hint);
            final int[] at = {0};
            final Timer t = new Timer(2500, e -> hint.setText(hints[Math.min(++at[0], hints.length - 1)]));
            t.start();
            show(d, cards, root, work, "work");
            final boolean off = offline.isSelected(), v = voice.isSelected();
            new SwingWorker<FriendPlay.Result, Void>() {
                @Override
                protected FriendPlay.Result doInBackground() throws Exception {
                    return FriendPlay.setup(instance, mode, off, v);
                }

                @Override
                protected void done() {
                    t.stop();
                    FriendPlay.Result r;
                    try {
                        r = get();
                    } catch (Exception ex) {
                        log.log(java.util.logging.Level.WARNING, "Friend play setup failed", ex);
                        r = null;
                    }
                    show(d, cards, root, resultPage(d, pal, cards, root, launcher, instance, mode, off, v, r, share, null), "done");
                }
            }.execute();
        };

        JPanel two = new JPanel(new MigLayout("insets 0, gap 12, fillx", "[264!,fill][264!,fill]", "[fill]"));
        two.setOpaque(false);
        two.add(choiceCard(FunIcon.Kind.HOUSE, SharedLocale.tr("friend.kid.near"), SharedLocale.tr("friend.kid.nearSub"),
                () -> go.accept(FriendPlay.Mode.LAN)));
        two.add(choiceCard(FunIcon.Kind.GLOBE, SharedLocale.tr("friend.kid.far"), SharedLocale.tr("friend.kid.farSub"),
                () -> go.accept(FriendPlay.Mode.INTERNET)));
        p.add(two);
        p.add(toggleRow(FunIcon.Kind.UNLOCK, SharedLocale.tr("friend.kid.offline"), SharedLocale.tr("friend.kid.offlineSub"), SharedLocale.tr("friend.kid.offlineTip"), offline), "gaptop 4");
        p.add(toggleRow(FunIcon.Kind.MIC, SharedLocale.tr("friend.kid.voice"), SharedLocale.tr("friend.kid.voiceSub"), SharedLocale.tr("friend.voiceTip"), voice));

        JButton invited = iconButton(LookUi.secondary(new JButton(SharedLocale.tr("friend.kid.invited"))), FunIcon.Kind.HAND);
        invited.addActionListener(e -> show(d, cards, root, friendSidePage(d, cards, root, self[0]), "friend"));
        p.add(invited, "h 34!, growx 0, gaptop 6, gapbottom 8");
        self[0] = p;
        return p;
    }

    /** Сторінка для того, кого кличуть: як отримати ту саму збірку і зайти. */
    private static JPanel friendSidePage(JDialog d, CardLayout cards, JPanel root, JPanel back) {
        JPanel p = column();
        p.add(header(FunIcon.Kind.HAND, SharedLocale.tr("friend.kid.invitedTitle"), SharedLocale.tr("friend.kid.invitedSub")));
        p.add(stepRow(1, FunIcon.Kind.BOX, SharedLocale.tr("friend.kid.inv1")), "gaptop 6");
        p.add(stepRow(2, FunIcon.Kind.COPY, SharedLocale.tr("friend.kid.inv2")));
        p.add(stepRow(3, FunIcon.Kind.PLAY, SharedLocale.tr("friend.kid.inv3")));
        p.add(stepRow(4, FunIcon.Kind.HOUSE, SharedLocale.tr("friend.kid.inv4")));
        p.add(stepRow(5, FunIcon.Kind.GLOBE, SharedLocale.tr("friend.kid.inv5")));
        p.add(backRow(d, cards, root, back), "gaptop 8");
        return p;
    }

    private static JPanel backRow(JDialog d, CardLayout cards, JPanel root, JPanel back) {
        JPanel row = new JPanel(new MigLayout("insets 0, gap 8", "[]push[]", ""));
        row.setOpaque(false);
        JButton b = iconButton(LookUi.secondary(new JButton(SharedLocale.tr("friend.kid.back"))), FunIcon.Kind.BACK);
        b.addActionListener(e -> show(d, cards, root, back, "back"));
        row.add(b, "h 34!");
        JButton close = LookUi.secondary(new JButton(SharedLocale.tr("friend.close")));
        close.addActionListener(e -> d.dispose());
        row.add(close, "h 34!");
        return row;
    }

    // ------------------------------------------------------------------ крок 2: що далі

    private static JPanel resultPage(JDialog d, LookTheme.Palette pal, CardLayout cards, JPanel root, Launcher launcher,
                                     Instance instance, FriendPlay.Mode mode, boolean offline, boolean voice, FriendPlay.Result r,
                                     Runnable share, Runnable again) {
        boolean lan = mode == FriendPlay.Mode.LAN;
        JPanel p = column();
        if (r == null || r.mod == null) {
            String why = r != null && r.manualPage != null ? SharedLocale.tr("friend.noneRetro") : SharedLocale.tr(lan ? "friend.noneLan" : "friend.noneNet");
            p.add(header(FunIcon.Kind.CONFUSED, SharedLocale.tr("friend.noneTitle"), null));
            p.add(text(why, 14f, false), "w " + W + "!, wmin 0");
            JPanel row = new JPanel(new MigLayout("insets 0, gap 8", "[]push[]", ""));
            row.setOpaque(false);
            if (r != null && r.manualPage != null) {
                JButton page = LookUi.primary(new JButton(SharedLocale.tr("friend.openModPage")));
                final String url = r.manualPage;
                page.addActionListener(e -> com.skcraft.launcher.swing.SwingHelper.openURL(url, d));
                row.add(page, "h 34!");
            }
            JButton close = LookUi.secondary(new JButton(SharedLocale.tr("friend.close")));
            close.addActionListener(e -> d.dispose());
            row.add(close, "h 34!, skip " + (r != null && r.manualPage != null ? 0 : 1));
            p.add(row, "gaptop 8");
            return p;
        }
        StringBuilder what = new StringBuilder(SharedLocale.tr(r.preexisting ? "friend.already" : r.alreadyHad ? "friend.hadMod" : "friend.gotMod", r.mod));
        if (r.preexisting) {
            if (r.voice != null) what.append(' ').append(SharedLocale.tr("friend.alreadyVoice"));
        } else if (voice) what.append(' ').append(r.voice != null ? SharedLocale.tr("friend.gotVoice") : SharedLocale.tr("friend.noVoice"));
        if (offline && "mcwifipnp".equals(r.slug)) what.append(' ').append(SharedLocale.tr("friend.offlineWorlds", r.worldsConfigured));
        else if (offline && "e4all".equals(r.slug)) what.append(' ').append(SharedLocale.tr("friend.offlineE4all"));
        p.add(header(FunIcon.Kind.PARTY, SharedLocale.tr("friend.kid.readyTitle"), what.toString()));
        p.add(new Stepper(2), "gaptop 4");
        if (again != null) {
            JButton redo = LookUi.secondary(new JButton(SharedLocale.tr("friend.again")));
            redo.setToolTipText(SharedLocale.tr("friend.againTip"));
            redo.setFocusable(false);
            redo.addActionListener(e -> again.run());
            p.add(redo, "h 30!, growx 0");
        }

        p.add(LookUi.title(SharedLocale.tr("friend.kid.stepsTitle"), 16f), "gaptop 4");
        int n = 1;
        p.add(stepRow(n++, FunIcon.Kind.PLAY, SharedLocale.tr("friend.kid.s.start")));
        p.add(stepRow(n++, FunIcon.Kind.ESC, SharedLocale.tr("friend.kid.s.open")));
        if (lan) {
            p.add(stepRow(n++, FunIcon.Kind.FRIENDS, SharedLocale.tr("friend.kid.s.lanFriend")));
        } else {
            p.add(stepRow(n++, FunIcon.Kind.CHAT, SharedLocale.tr("friend.kid.s.netCopy")));
            p.add(stepRow(n++, FunIcon.Kind.SEND, SharedLocale.tr("friend.kid.s.netSend")));
        }
        if (offline && "mcwifipnp".equals(r.slug)) p.add(tipRow(FunIcon.Kind.UNLOCK, SharedLocale.tr("friend.kid.s.offlineNew")));
        // 26.11.2026: OfflineLAN не має файлу налаштувань (перемикач лише у вікні гри), тож щоразу руками
        if (offline && "offlinelan".equals(r.slug)) p.add(tipRow(FunIcon.Kind.UNLOCK, SharedLocale.tr("friend.kid.s.offlineEvery")));
        if (voice && r.voice != null) p.add(tipRow(FunIcon.Kind.MIC, SharedLocale.tr("friend.kid.s.voice")));
        if (!lan) p.add(tipRow(FunIcon.Kind.SHIELD, SharedLocale.tr("friend.kid.s.netSecret")));

        // брандмауер: лише Windows і лише для гри поруч (через інтернет з'єднання вихідне)
        if (lan && FriendPlay.isWindows()) {
            JPanel fw = LookUi.card("insets 12 14 12 14, gap 12 6, fillx", "[][grow,fill]", "");
            fw.add(FunIcon.label(FunIcon.Kind.SHIELD, 40), "spany 3, aligny top");
            fw.add(LookUi.title(SharedLocale.tr("friend.kid.fwTitle"), 14.5f), "wrap");
            fw.add(text(SharedLocale.tr("friend.kid.fwText"), 13f, true), "w " + (W - 100) + "!, wmin 0, wrap");
            final JLabel state = new JLabel(SharedLocale.tr("friend.fwChecking"));
            state.setForeground(pal.text3);
            final JButton allow = LookUi.primary(new JButton(SharedLocale.tr("friend.kid.fwAllow")));
            allow.setToolTipText(SharedLocale.tr("friend.fwAllowTip"));
            JPanel row = new JPanel(new MigLayout("insets 0, gap 10, fillx", "[]push[]", ""));
            row.setOpaque(false);
            row.add(state);
            row.add(allow, "h 34!");
            fw.add(row, "gaptop 2");
            p.add(fw, "gaptop 6");
            final List<File> javaws = FriendPlay.javaws(launcher.getBaseDir());
            Runnable check = () -> new SwingWorker<FriendPlay.Firewall, Void>() {
                @Override
                protected FriendPlay.Firewall doInBackground() {
                    return FriendPlay.firewallState(javaws);
                }

                @Override
                protected void done() {
                    try {
                        showFirewall(state, allow, get(), pal);
                    } catch (Exception ex) {
                        showFirewall(state, allow, FriendPlay.Firewall.UNKNOWN, pal);
                    }
                }
            }.execute();
            allow.addActionListener(e -> {
                allow.setEnabled(false);
                state.setText(SharedLocale.tr("friend.fwAsking"));
                new SwingWorker<Boolean, Void>() {
                    @Override
                    protected Boolean doInBackground() {
                        return FriendPlay.firewallAllow(javaws);
                    }

                    @Override
                    protected void done() {
                        allow.setEnabled(true);
                        check.run();
                    }
                }.execute();
            });
            check.run();
        }

        final JPanel[] self = new JPanel[1];
        JPanel row = new JPanel(new MigLayout("insets 0, gap 8", "[][]push[]", ""));
        row.setOpaque(false);
        JButton help = iconButton(LookUi.secondary(new JButton(SharedLocale.tr("friend.kid.cantSee"))), FunIcon.Kind.CONFUSED);
        help.addActionListener(e -> show(d, cards, root, helpPage(d, cards, root, lan, self[0]), "help"));
        row.add(help, "h 36!");
        if (share != null) {
            JButton sh = iconButton(LookUi.secondary(new JButton(SharedLocale.tr("friend.share"))), FunIcon.Kind.SEND);
            sh.setToolTipText(SharedLocale.tr("friend.shareTip"));
            sh.addActionListener(e -> {
                d.dispose();
                share.run();
            });
            row.add(sh, "h 36!");
        } else {
            row.add(new JLabel());
        }
        JButton done = iconButton(LookUi.primary(new JButton(SharedLocale.tr("friend.kid.go"))), FunIcon.Kind.PLAY);
        done.addActionListener(e -> d.dispose());
        row.add(done, "h 36!");
        p.add(row, "gaptop 10");
        self[0] = p;
        return p;
    }

    /** «Друг не бачить гру?»: короткі перевірки по черзі. */
    private static JPanel helpPage(JDialog d, CardLayout cards, JPanel root, boolean lan, JPanel back) {
        JPanel p = column();
        p.add(header(FunIcon.Kind.CONFUSED, SharedLocale.tr("friend.kid.helpTitle"), SharedLocale.tr("friend.kid.helpSub")));
        String[] keys = lan ? new String[]{"friend.kid.h.same", "friend.kid.h.open", "friend.kid.h.wifi", "friend.kid.h.fw", "friend.kid.h.direct", "friend.kid.h.license"}
                : new String[]{"friend.kid.h.same", "friend.kid.h.open", "friend.kid.h.addr", "friend.kid.h.again", "friend.kid.h.license"};
        boolean first = true;
        for (String k : keys) {
            p.add(tipRow(FunIcon.Kind.CHECK, SharedLocale.tr(k)), first ? "gaptop 6" : "");
            first = false;
        }
        p.add(tipRow(FunIcon.Kind.HAND, SharedLocale.tr("friend.kid.h.support")), "gaptop 4");
        p.add(backRow(d, cards, root, back), "gaptop 8");
        return p;
    }

    private static void showFirewall(JLabel state, JButton allow, FriendPlay.Firewall f, LookTheme.Palette pal) {
        switch (f) {
            case ALLOWED:
                state.setText(SharedLocale.tr("friend.fwAllowed"));
                state.setForeground(new Color(0x3FB950));
                allow.setVisible(false);
                break;
            case BLOCKED:
                state.setText(SharedLocale.tr("friend.fwBlocked"));
                state.setForeground(new Color(0xF85149));
                allow.setVisible(true);
                break;
            case NOT_SET:
                state.setText(SharedLocale.tr("friend.fwNotSet"));
                state.setForeground(pal.text3);
                allow.setVisible(true);
                break;
            default:
                state.setText(SharedLocale.tr("friend.fwUnknown"));
                state.setForeground(pal.text3);
                allow.setVisible(true);
        }
    }

    // ------------------------------------------------------------------ ванільна збірка

    /**
     * 27.11.2026 (власник: «додати наклейку "грати з другом" на ваніль, але нехай воно створить
     * фордж-фабрік-неофордж тієї ж версії, фактично копію ванілі, але з лоадером для гри онлайн»). Кнопка робить
     * нову збірку-копію з завантажувачем тієї ж версії гри ({@link #loaderFor}: Fabric, а де його немає, NeoForge чи
     * Forge), переносить туди світи, налаштування і ресурспаки ({@link #copyFromVanilla}), ставить гру і відкриває
     * помічника вже в новій збірці. Ванільна збірка не змінюється.
     */
    private static JPanel vanillaPage(JDialog d, LookTheme.Palette pal, Launcher launcher, Instance instance,
                                      Runnable share, Consumer<String> switchTo) {
        final String mc = InstanceCopies.minecraftVersionOf(instance);
        JPanel row = new JPanel(new MigLayout("insets 0, gap 8", "[]push[]", ""));
        row.setOpaque(false);
        final Instance base = loaderFor(launcher, mc);
        final String loaderTitle = base == null ? "" : InstanceCopies.kindTitleOf(base);
        final JButton copy = LookUi.primary(new JButton(SharedLocale.tr("friend.vanillaMake", loaderTitle)));
        JButton close = LookUi.secondary(new JButton(SharedLocale.tr("friend.close")));
        close.addActionListener(e -> d.dispose());
        row.add(copy, "h 38!");
        row.add(close, "h 38!");
        if (base == null) copy.setVisible(false);
        copy.addActionListener(e -> {
            copy.setEnabled(false);
            copy.setText(SharedLocale.tr("friend.vanillaMaking"));
            final String title = SharedLocale.tr("friend.vanillaCopyTitle", mc);
            final InstanceCopies.Copier copier = new InstanceCopies.Copier(launcher, base, title,
                    java.util.EnumSet.noneOf(InstanceCopies.Part.class));
            new SwingWorker<Instance, Void>() {
                @Override
                protected Instance doInBackground() throws Exception {
                    String name = copier.call();
                    File dir = new File(launcher.getInstancesDir(), name);
                    Instance made = com.skcraft.launcher.persistence.Persistence.load(new File(dir, "instance.json"), Instance.class);
                    made.setDir(dir);
                    made.setName(name);
                    int n = copyFromVanilla(instance, made.getContentDir());
                    log.info("Friend play: vanilla " + instance.getName() + " -> copy " + name + " on " + base.getName() + ", worlds " + n);
                    return made;
                }

                @Override
                protected void done() {
                    final Instance made;
                    try {
                        made = get();
                    } catch (Exception ex) {
                        log.log(java.util.logging.Level.WARNING, "Friend play: loader copy of vanilla failed", ex);
                        copy.setText(SharedLocale.tr("friend.vanillaMake", loaderTitle));
                        copy.setEnabled(true);
                        com.skcraft.launcher.swing.SwingHelper.showErrorDialog(d, SharedLocale.tr("friend.vanillaFailed"),
                                SharedLocale.tr("errorTitle"), ex.getCause() != null ? ex.getCause() : ex);
                        return;
                    }
                    final Window owner = d.getOwner();
                    d.dispose();
                    // гру ставимо одразу (бібліотеки та Java вже є від ванілі), далі помічник у новій збірці
                    InstanceManageDialog.installOnly(owner, launcher, made, () -> {
                        if (switchTo != null) switchTo.accept(made.getName());
                        open(owner, launcher, made, null, switchTo);
                    });
                }
            }.execute();
        });
        String text = base == null ? SharedLocale.tr("friend.vanillaNoLoader", mc == null ? "" : mc)
                : SharedLocale.tr("friend.vanillaLoader", mc == null ? "" : mc, loaderTitle);
        return page(pal, SharedLocale.tr("friend.title"), text, row);
    }

    /** Розділ лаунчера з завантажувачем для версії гри mc: Fabric, інакше NeoForge, інакше Forge, або null. */
    static Instance loaderFor(Launcher launcher, String mc) {
        if (mc == null) return null;
        Instance f = fabricFor(launcher, mc);
        if (f != null) return f;
        for (com.gummer.launcher.forge.ForgeVersions.Kind k : new com.gummer.launcher.forge.ForgeVersions.Kind[]{
                com.gummer.launcher.forge.ForgeVersions.Kind.NEOFORGE, com.gummer.launcher.forge.ForgeVersions.Kind.FORGE}) {
            String name = com.gummer.launcher.forge.ForgeVersions.nameOf(k, mc);
            for (Instance i : launcher.getInstances().getInstances()) {
                if (name.equalsIgnoreCase(i.getName())) return i;
            }
        }
        return null;
    }

    /**
     * 27.11.2026: з ванільної збірки в теку гри нової: світи (крім тих, що там уже є), налаштування гри (options*.txt,
     * servers.dat, hotbar.nbt) і ресурспаки. Моди не чіпаються, у ванілі їх немає. Повертає кількість світів.
     */
    static int copyFromVanilla(Instance from, File dst) throws IOException {
        int n = copyWorlds(from, dst);
        File src = from.getContentDir();
        File[] top = src.listFiles(File::isFile);
        if (top != null) {
            for (File f : top) {
                String low = f.getName().toLowerCase(java.util.Locale.ROOT);
                boolean setting = low.startsWith("options") && low.endsWith(".txt") || low.equals("servers.dat") || low.equals("hotbar.nbt");
                File t = new File(dst, f.getName());
                if (setting && !t.exists()) {
                    dst.mkdirs();
                    Files.copy(f.toPath(), t.toPath());
                }
            }
        }
        File packs = new File(src, "resourcepacks");
        File[] list = packs.listFiles();
        if (list != null) {
            for (File p : list) {
                File t = new File(new File(dst, "resourcepacks"), p.getName());
                if (t.exists()) continue;
                if (p.isDirectory()) copyTree(p.toPath(), t.toPath());
                else {
                    t.getParentFile().mkdirs();
                    Files.copy(p.toPath(), t.toPath());
                }
            }
        }
        return n;
    }

    static Instance fabricFor(Launcher launcher, String mc) {
        if (mc == null) return null;
        String name = com.gummer.launcher.fabric.FabricVersions.nameOf(mc);
        for (Instance i : launcher.getInstances().getInstances()) {
            if (name.equalsIgnoreCase(i.getName())) return i;
        }
        return null;
    }

    /** Світи і options.txt з ванільної збірки у Fabric тієї ж версії; наявне там не перезаписується. */
    static int copyWorlds(Instance from, Instance to) throws IOException {
        return copyWorlds(from, to.getContentDir());
    }

    static int copyWorlds(Instance from, File dst) throws IOException {
        File src = from.getContentDir();
        int n = 0;
        File[] worlds = new File(src, "saves").listFiles(File::isDirectory);
        if (worlds != null) {
            for (File w : worlds) {
                File t = new File(new File(dst, "saves"), w.getName());
                if (t.exists()) continue;
                copyTree(w.toPath(), t.toPath());
                n++;
            }
        }
        File opt = new File(src, "options.txt"), optTo = new File(dst, "options.txt");
        if (opt.isFile() && !optTo.exists()) {
            dst.mkdirs();
            Files.copy(opt.toPath(), optTo.toPath());
        }
        return n;
    }

    private static void copyTree(final java.nio.file.Path from, final java.nio.file.Path to) throws IOException {
        Files.walkFileTree(from, new java.nio.file.SimpleFileVisitor<java.nio.file.Path>() {
            @Override
            public java.nio.file.FileVisitResult preVisitDirectory(java.nio.file.Path dir, java.nio.file.attribute.BasicFileAttributes a) throws IOException {
                Files.createDirectories(to.resolve(from.relativize(dir).toString()));
                return java.nio.file.FileVisitResult.CONTINUE;
            }

            @Override
            public java.nio.file.FileVisitResult visitFile(java.nio.file.Path file, java.nio.file.attribute.BasicFileAttributes a) throws IOException {
                if (!file.getFileName().toString().equals("session.lock")) {
                    Files.copy(file, to.resolve(from.relativize(file).toString()), StandardCopyOption.COPY_ATTRIBUTES);
                }
                return java.nio.file.FileVisitResult.CONTINUE;
            }
        });
    }
}
