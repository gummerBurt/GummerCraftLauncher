/*
 * Copyright (C) 2021-2026 GummerCraft <http://gummercraft.fun>
 */

package com.gummer.launcher.crash;

import com.gummer.launcher.ui.UiHooks;
import com.skcraft.launcher.Instance;
import com.skcraft.launcher.swing.SwingHelper;

import javax.swing.*;
import java.awt.*;

import static com.skcraft.launcher.util.SharedLocale.tr;

/**
 * Вікно «Гра закрилась з помилкою» (з 1.32): причина з {@link CrashAnalyzer} людською мовою, що зробити,
 * кнопка дії (пам'ять, перевстановити пак, драйвер), «Написати в підтримку» (лог і звіт про збій лаунчер
 * додає сам) і «Відкрити консоль» для тих, хто хоче бачити лог. До 1.32 у цьому випадку просто відкривалась консоль.
 */
public final class CrashDialog {

    /** Куди веде «Як оновити драйвер» (launcher.properties driversHelpUrl); порожньо: кнопки немає. */
    public static volatile String driversHelpUrl;

    private CrashDialog() {
    }

    /** Показати з EDT. openConsole: що робить «Відкрити консоль» (може бути null). */
    public static void show(final Window owner, final Instance instance, final CrashAnalyzer.Diagnosis d, final Runnable openConsole) {
        final JDialog dialog = new JDialog(owner, tr("crash.windowTitle"), Dialog.ModalityType.MODELESS);
        dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

        JPanel p = new JPanel(new BorderLayout(0, 14));
        p.setBorder(BorderFactory.createEmptyBorder(18, 20, 16, 20));

        // назва мода в заголовку в дужках; без назви нічого, щоб не було «null» і зайвого пробілу
        final String detail = d.detail != null && !d.detail.isEmpty() ? " (" + d.detail + ")" : "";
        String html = "<html><div style=\"width: 440px\">"
                + "<span style=\"font-size: 15px\"><b>" + SwingHelper.htmlEscape(tr("crash.headline", instance.getTitle())) + "</b></span><br><br>"
                + "<b>" + SwingHelper.htmlEscape(tr("crash." + d.key + ".title", detail)) + "</b><br>"
                + SwingHelper.htmlEscape(tr("crash." + d.key + ".advice", detail)).replace("\n", "<br>")
                + "</div></html>";
        p.add(new JLabel(html), BorderLayout.CENTER);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        JButton primary = null;
        switch (d.action) {
            case MEMORY:
                if (UiHooks.instanceSettings != null) {
                    primary = new JButton(tr("crash.action.memory"));
                    primary.addActionListener(e -> {
                        dialog.dispose();
                        UiHooks.instanceSettings.run(owner, instance);
                    });
                }
                break;
            case REINSTALL:
                if (UiHooks.reinstall != null) {
                    primary = new JButton(tr("crash.action.reinstall"));
                    primary.addActionListener(e -> {
                        dialog.dispose();
                        UiHooks.reinstall.run(owner, instance);
                    });
                }
                break;
            case DRIVERS:
                final String url = driversHelpUrl;
                if (url != null && !url.isEmpty()) {
                    primary = new JButton(tr("crash.action.drivers"));
                    primary.addActionListener(e -> SwingHelper.openURL(url, dialog));
                }
                break;
            default:
                break;
        }
        if (openConsole != null) {
            JButton console = new JButton(tr("crash.action.console"));
            console.addActionListener(e -> openConsole.run());
            buttons.add(console);
        }
        final UiHooks.Support support = UiHooks.support;
        if (support != null) {
            JButton write = new JButton(tr("crash.action.support"));
            write.addActionListener(e -> {
                dialog.dispose();
                String text = tr("crash.supportText", instance.getTitle(), tr("crash." + d.key + ".title", detail));
                support.open(owner, com.gummer.launcher.support.SupportService.defaultTopic(instance), text);
            });
            buttons.add(write);
        }
        if (primary != null) {
            primary.putClientProperty("JButton.buttonType", "default");
            buttons.add(primary);
            dialog.getRootPane().setDefaultButton(primary);
        }
        JButton close = new JButton(tr("crash.action.close"));
        close.addActionListener(e -> dialog.dispose());
        buttons.add(close);
        p.add(buttons, BorderLayout.SOUTH);

        dialog.setContentPane(p);
        dialog.pack();
        dialog.setResizable(false);
        dialog.setLocationRelativeTo(owner);
        dialog.setVisible(true);
        dialog.toFront();
        // лаунчер повертається на екран слідом за цим вікном (після гри він схований); якщо власника немає
        // або лаунчер створив нове вікно, ще раз підняти це вікно, коли лаунчер уже показано
        Timer raise = new Timer(700, e -> {
            if (dialog.isShowing()) {
                if (owner != null && owner.isShowing()) dialog.setLocationRelativeTo(owner);
                dialog.toFront();
            }
        });
        raise.setRepeats(false);
        raise.start();
    }
}
