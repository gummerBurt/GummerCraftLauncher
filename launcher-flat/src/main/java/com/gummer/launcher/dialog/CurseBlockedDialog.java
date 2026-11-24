/*
 * Copyright (C) 2021-2026 GummerCraft <http://gummercraft.fun>
 */

package com.gummer.launcher.dialog;

import com.gummer.launcher.curseforge.CurseForge;
import com.gummer.launcher.look.LookTheme;
import com.gummer.launcher.look.LookUi;
import com.skcraft.launcher.swing.SwingHelper;
import com.skcraft.launcher.util.SharedLocale;
import net.miginfocom.swing.MigLayout;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

/**
 * 1.40.0 (24.11.2026): вікно «Ці моди треба скачати з сайту CurseForge» під час імпорту збірки CurseForge. Автори цих
 * модів заборонили сторонні завантаження, тож лаунчер їх не качає: у кожного рядка кнопка «Скачати» відкриває сторінку
 * файлу на curseforge.com (сайт сам починає завантаження), а лаунчер щосекунди дивиться в «Завантаження» і забирає
 * файл, щойно він там з'явився (звіряє розмір і sha1, назва може бути будь-яка). Усе знайдено: вікно закривається само.
 * «Продовжити без них»: збірка ставиться без цих файлів (список лишається в mrpack.json, missing).
 */
public final class CurseBlockedDialog {

    private CurseBlockedDialog() {
    }

    /** Зареєструвати вікно для імпорту (FancyLauncher при старті). */
    public static void install() {
        CurseForge.setBlockedHandler((title, files) -> {
            CountDownLatch closed = new CountDownLatch(1);
            SwingUtilities.invokeLater(() -> {
                try {
                    show(title, files, closed);
                } catch (RuntimeException e) {
                    closed.countDown();
                    throw e;
                }
            });
            closed.await();
        });
    }

    private static void show(String packTitle, List<CurseForge.Blocked> files, CountDownLatch closed) {
        Window owner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getActiveWindow();
        final JDialog d = new JDialog(owner, SharedLocale.tr("curseforge.blocked.title"), Dialog.ModalityType.APPLICATION_MODAL);
        d.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        LookTheme.Palette pal = LookTheme.palette();
        JPanel root = new JPanel(new MigLayout("insets 18 20 16 20, wrap 1, gap 0 8, fill", "[grow,fill]", "[][][grow,fill][][]"));
        root.setBackground(pal.surface);
        root.add(LookUi.title(SharedLocale.tr("curseforge.blocked.title"), 16f));
        root.add(LookUi.body(SharedLocale.tr("curseforge.blocked.hint", packTitle, files.size()), true), "w 560!, wmin 0");

        JPanel list = new JPanel(new MigLayout("insets 4, wrap 2, gap 8 6, fillx", "[grow,fill][]", ""));
        list.setOpaque(false);
        final List<JLabel> states = new ArrayList<JLabel>();
        final List<JButton> buttons = new ArrayList<JButton>();
        for (CurseForge.Blocked b : files) {
            JPanel text = new JPanel(new MigLayout("insets 0, wrap 1, gap 0 0, fillx", "[grow,fill]", ""));
            text.setOpaque(false);
            JLabel name = new JLabel(b.file.title());
            name.setFont(name.getFont().deriveFont(Font.BOLD));
            JLabel sub = new JLabel(b.file.fileName + (b.file.author.isEmpty() ? "" : "  ·  " + b.file.author));
            sub.setForeground(pal.text3);
            sub.setFont(sub.getFont().deriveFont(12f));
            text.add(name, "wmin 0");
            text.add(sub, "wmin 0");
            list.add(text, "wmin 0");
            JButton open = LookUi.secondary(new JButton(SharedLocale.tr("curseforge.blocked.open")));
            open.addActionListener(e -> SwingHelper.openURL(b.file.downloadPage(), d));
            JLabel state = new JLabel(SharedLocale.tr("curseforge.blocked.done"));
            state.setForeground(new Color(0x22B35E));
            state.setFont(state.getFont().deriveFont(Font.BOLD));
            state.setVisible(false);
            JPanel right = new JPanel(new MigLayout("insets 0, gap 0", "[]", "[]"));
            right.setOpaque(false);
            right.add(open, "h 30!, hidemode 3");
            right.add(state, "hidemode 3");
            list.add(right, "right");
            states.add(state);
            buttons.add(open);
        }
        JScrollPane scroll = new JScrollPane(list);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        root.add(scroll, "h 120:320:420, wmin 0");

        final JLabel counter = new JLabel();
        counter.setForeground(pal.text3);
        root.add(counter);

        JButton openAll = LookUi.secondary(new JButton(SharedLocale.tr("curseforge.blocked.openAll")));
        JButton skip = LookUi.secondary(new JButton(SharedLocale.tr("curseforge.blocked.skip")));
        JButton folder = LookUi.secondary(new JButton(SharedLocale.tr("curseforge.blocked.folder")));
        JPanel bottom = new JPanel(new MigLayout("insets 0, gap 8", "[][]push[]", "[34!]"));
        bottom.setOpaque(false);
        bottom.add(openAll, "h 34!");
        bottom.add(folder, "h 34!");
        bottom.add(skip, "h 34!");
        root.add(bottom, "gaptop 6");

        // «Відкрити всі»: не більше 10 вкладок за раз, щоб браузер не захлинувся; наступне натискання відкриє наступні
        final int[] nextToOpen = {0};
        openAll.addActionListener(e -> {
            int opened = 0;
            while (nextToOpen[0] < files.size() && opened < 10) {
                CurseForge.Blocked b = files.get(nextToOpen[0]++);
                if (b.isDone()) continue;
                SwingHelper.openURL(b.file.downloadPage(), d);
                opened++;
            }
            if (nextToOpen[0] >= files.size()) nextToOpen[0] = 0;
        });
        final List<File> dirs = CurseForge.downloadDirs();
        folder.setEnabled(!dirs.isEmpty());
        folder.addActionListener(e -> {
            if (!dirs.isEmpty()) SwingHelper.browseDir(dirs.get(dirs.size() - 1), d);
        });
        skip.addActionListener(e -> d.dispose());

        final java.util.function.BooleanSupplier refresh = () -> {
            int ready = 0;
            for (int i = 0; i < files.size(); i++) {
                boolean done = files.get(i).isDone();
                if (done) ready++;
                states.get(i).setVisible(done);
                buttons.get(i).setVisible(!done);
            }
            counter.setText(SharedLocale.tr("curseforge.blocked.counter", ready, files.size()));
            list.revalidate();
            list.repaint();
            if (ready < files.size()) return false;
            if (d.isDisplayable()) d.dispose(); // усе знайдено: вікно закривається само (windowClosed відпускає імпорт)
            return true;
        };
        // пошук у «Завантаженнях» раз на секунду у фоні (sha1 рахується лише для файлів потрібного розміру)
        final Timer timer = new Timer(1000, null);
        final boolean[] busy = {false};
        timer.addActionListener(e -> {
            if (busy[0]) return;
            busy[0] = true;
            new Thread(() -> {
                try {
                    CurseForge.pickup(files, dirs);
                } finally {
                    SwingUtilities.invokeLater(() -> {
                        busy[0] = false;
                        if (d.isDisplayable()) refresh.getAsBoolean();
                    });
                }
            }, "curseforge-pickup").start();
        });
        d.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosed(java.awt.event.WindowEvent e) {
                timer.stop();
                closed.countDown();
            }
        });
        // до показу вікно ще не displayable (немає peer), тож «усе вже знайдено» відпускаємо імпорт тут
        if (refresh.getAsBoolean()) {
            closed.countDown();
            return;
        }
        timer.start();
        d.setContentPane(root);
        d.pack();
        d.setMinimumSize(new Dimension(600, 360));
        d.setLocationRelativeTo(owner);
        d.setVisible(true);
    }
}
