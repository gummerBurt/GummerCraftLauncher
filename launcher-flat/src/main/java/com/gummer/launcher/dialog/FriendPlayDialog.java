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
                root.add(resultPage(d, pal, launcher, instance, had.mode, false, had.voice != null, had, share, again), "done");
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

    // ------------------------------------------------------------------ крок 1: питання

    private static JPanel askPage(JDialog d, LookTheme.Palette pal, CardLayout cards, JPanel root, Launcher launcher,
                                  Instance instance, Runnable share) {
        JPanel p = new JPanel(new MigLayout("insets 20 22 18 22, wrap 1, gap 0 8, fillx", "[grow,fill]", ""));
        p.setOpaque(false);
        p.add(LookUi.title(SharedLocale.tr("friend.title"), 18f));
        p.add(LookUi.body(SharedLocale.tr("friend.intro"), true), "w 520!, wmin 0");
        JLabel q = LookUi.title(SharedLocale.tr("friend.question"), 15f);
        p.add(q, "gaptop 10");
        JLabel qs = new JLabel(SharedLocale.tr("friend.questionHint"));
        qs.setForeground(pal.text3);
        qs.setFont(qs.getFont().deriveFont(12f));
        p.add(qs);

        final JCheckBox offline = new JCheckBox(SharedLocale.tr("friend.offline"), true);
        offline.setOpaque(false);
        offline.setToolTipText(SharedLocale.tr("friend.offlineTip"));
        final JCheckBox voice = new JCheckBox(SharedLocale.tr("friend.voice"), false);
        voice.setOpaque(false);
        voice.setToolTipText(SharedLocale.tr("friend.voiceTip"));

        JPanel btns = new JPanel(new MigLayout("insets 0, gap 10, fillx", "[grow,fill][grow,fill]", ""));
        btns.setOpaque(false);
        JButton yes = LookUi.primary(new JButton(SharedLocale.tr("friend.yes")));
        JButton no = LookUi.primary(new JButton(SharedLocale.tr("friend.no")));
        btns.add(yes, "h 42!");
        btns.add(no, "h 42!");
        p.add(btns, "gaptop 6");
        p.add(offline, "gaptop 8");
        p.add(voice);

        java.util.function.Consumer<FriendPlay.Mode> go = mode -> {
            JPanel work = page(pal, SharedLocale.tr("friend.title"), SharedLocale.tr("friend.working"), null);
            JProgressBar bar = new JProgressBar();
            bar.setIndeterminate(true);
            work.add(bar, "h 6!");
            root.removeAll(); // інакше CardLayout бере висоту найбільшої сторінки і лишає порожнє місце
            root.add(work, "work");
            cards.show(root, "work");
            refit(d);
            final boolean off = offline.isSelected(), v = voice.isSelected();
            new SwingWorker<FriendPlay.Result, Void>() {
                @Override
                protected FriendPlay.Result doInBackground() throws Exception {
                    return FriendPlay.setup(instance, mode, off, v);
                }

                @Override
                protected void done() {
                    FriendPlay.Result r;
                    try {
                        r = get();
                    } catch (Exception ex) {
                        log.log(java.util.logging.Level.WARNING, "Friend play setup failed", ex);
                        r = null;
                    }
                    root.removeAll();
                    root.add(resultPage(d, pal, launcher, instance, mode, off, v, r, share, null), "done");
                    cards.show(root, "done");
                    refit(d);
                }
            }.execute();
        };
        yes.addActionListener(e -> go.accept(FriendPlay.Mode.LAN));
        no.addActionListener(e -> go.accept(FriendPlay.Mode.INTERNET));
        return p;
    }

    // ------------------------------------------------------------------ крок 2: що далі

    private static JPanel resultPage(JDialog d, LookTheme.Palette pal, Launcher launcher, Instance instance, FriendPlay.Mode mode,
                                     boolean offline, boolean voice, FriendPlay.Result r, Runnable share, Runnable again) {
        boolean lan = mode == FriendPlay.Mode.LAN;
        JPanel p = new JPanel(new MigLayout("insets 20 22 18 22, wrap 1, gap 0 8, fillx", "[grow,fill]", ""));
        p.setOpaque(false);
        if (r == null || r.mod == null) {
            p.add(LookUi.title(SharedLocale.tr("friend.noneTitle"), 18f));
            String why = r != null && r.manualPage != null ? SharedLocale.tr("friend.noneRetro") : SharedLocale.tr(lan ? "friend.noneLan" : "friend.noneNet");
            p.add(LookUi.body(why, false), "w 520!, wmin 0");
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
        p.add(LookUi.title(SharedLocale.tr("friend.readyTitle"), 18f));
        StringBuilder what = new StringBuilder(SharedLocale.tr(r.preexisting ? "friend.already" : r.alreadyHad ? "friend.hadMod" : "friend.gotMod", r.mod));
        if (r.preexisting) {
            if (r.voice != null) what.append(' ').append(SharedLocale.tr("friend.alreadyVoice"));
        } else if (voice) what.append(' ').append(r.voice != null ? SharedLocale.tr("friend.gotVoice") : SharedLocale.tr("friend.noVoice"));
        if (offline && "mcwifipnp".equals(r.slug)) what.append(' ').append(SharedLocale.tr("friend.offlineWorlds", r.worldsConfigured));
        else if (offline && "e4all".equals(r.slug)) what.append(' ').append(SharedLocale.tr("friend.offlineE4all"));
        p.add(LookUi.body(what.toString(), true), "w 520!, wmin 0");
        if (again != null) {
            JButton redo = LookUi.secondary(new JButton(SharedLocale.tr("friend.again")));
            redo.setToolTipText(SharedLocale.tr("friend.againTip"));
            redo.setFocusable(false);
            redo.addActionListener(e -> again.run());
            p.add(redo, "h 30!, growx 0");
        }

        p.add(LookUi.title(SharedLocale.tr("friend.stepsTitle"), 15f), "gaptop 6");
        String steps = SharedLocale.tr(lan ? "friend.stepsLan" : "friend.stepsNet");
        if (offline && "mcwifipnp".equals(r.slug)) steps += "\n" + SharedLocale.tr("friend.stepsLanOffline");
        // 26.11.2026: OfflineLAN не має файлу налаштувань (перемикач лише у вікні гри), тож щоразу руками
        if (offline && "offlinelan".equals(r.slug)) steps += "\n" + SharedLocale.tr("friend.stepsLanOfflineEvery");
        if (voice && r.voice != null) steps += "\n" + SharedLocale.tr("friend.stepsVoice");
        p.add(LookUi.body(steps, false), "w 520!, wmin 0");

        // брандмауер: лише Windows і лише для гри поруч (через інтернет з'єднання вихідне)
        if (lan && FriendPlay.isWindows()) {
            JPanel fw = LookUi.card("insets 12 14 12 14, wrap 1, gap 0 6, fillx", "[grow,fill]", "");
            fw.add(LookUi.title(SharedLocale.tr("friend.fwTitle"), 14f));
            fw.add(LookUi.body(SharedLocale.tr("friend.fwText"), true), "w 490!, wmin 0");
            final JLabel state = new JLabel(SharedLocale.tr("friend.fwChecking"));
            state.setForeground(pal.text3);
            final JButton allow = LookUi.primary(new JButton(SharedLocale.tr("friend.fwAllow")));
            allow.setToolTipText(SharedLocale.tr("friend.fwAllowTip"));
            JPanel row = new JPanel(new MigLayout("insets 0, gap 10, fillx", "[]push[]", ""));
            row.setOpaque(false);
            row.add(state);
            row.add(allow, "h 34!");
            fw.add(row, "gaptop 4");
            p.add(fw, "gaptop 8");
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

        JPanel row = new JPanel(new MigLayout("insets 0, gap 8", "[]push[]", ""));
        row.setOpaque(false);
        if (share != null) {
            JButton sh = LookUi.secondary(new JButton(SharedLocale.tr("friend.share")));
            sh.setToolTipText(SharedLocale.tr("friend.shareTip"));
            sh.addActionListener(e -> {
                d.dispose();
                share.run();
            });
            row.add(sh, "h 34!");
        }
        JButton done = LookUi.primary(new JButton(SharedLocale.tr("friend.done")));
        done.addActionListener(e -> d.dispose());
        row.add(done, "h 34!" + (share == null ? ", skip 1" : ""));
        p.add(row, "gaptop 10");
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

    private static JPanel vanillaPage(JDialog d, LookTheme.Palette pal, Launcher launcher, Instance instance,
                                      Runnable share, Consumer<String> switchTo) {
        final String mc = InstanceCopies.minecraftVersionOf(instance);
        JPanel row = new JPanel(new MigLayout("insets 0, gap 8", "[]push[]", ""));
        row.setOpaque(false);
        final JButton copy = LookUi.primary(new JButton(SharedLocale.tr("friend.vanillaCopy")));
        JButton close = LookUi.secondary(new JButton(SharedLocale.tr("friend.close")));
        close.addActionListener(e -> d.dispose());
        row.add(copy, "h 38!");
        row.add(close, "h 38!");
        final Instance fabric = fabricFor(launcher, mc);
        if (fabric == null) copy.setEnabled(false);
        copy.addActionListener(e -> {
            copy.setEnabled(false);
            new SwingWorker<Integer, Void>() {
                @Override
                protected Integer doInBackground() throws Exception {
                    return copyWorlds(instance, fabric);
                }

                @Override
                protected void done() {
                    int n;
                    try {
                        n = get();
                    } catch (Exception ex) {
                        log.log(java.util.logging.Level.WARNING, "Friend play: copy to Fabric failed", ex);
                        n = -1;
                    }
                    log.info("Friend play: vanilla " + instance.getName() + " -> " + fabric.getName() + ", worlds " + n);
                    d.dispose();
                    if (switchTo != null) switchTo.accept(fabric.getName());
                    open(d.getOwner(), launcher, fabric, null, switchTo);
                }
            }.execute();
        });
        String text = SharedLocale.tr(fabric == null ? "friend.vanillaNoFabric" : "friend.vanilla", mc == null ? "" : mc);
        return page(pal, SharedLocale.tr("friend.title"), text, row);
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
        File src = from.getContentDir(), dst = to.getContentDir();
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
