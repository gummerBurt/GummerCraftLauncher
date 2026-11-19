/*
 * SK's Minecraft Launcher
 * Copyright (C) 2010-2014 Albert Pham <http://www.sk89q.com> and contributors
 * Copyright (C) 2021-2025 GummerCraft <http://gummercraft.fun>
 * Please see LICENSE.txt for license information.
 */

package com.skcraft.launcher.launch;

import com.google.common.base.Function;
import com.skcraft.launcher.Launcher;
import com.skcraft.launcher.dialog.ProcessConsoleFrame;
import com.skcraft.launcher.swing.MessageLog;
import com.skcraft.launcher.swing.SwingHelper;
import lombok.Getter;
import lombok.NonNull;
import lombok.extern.java.Log;

import javax.swing.*;
import java.awt.*;
import java.lang.reflect.InvocationTargetException;
import java.util.Objects;
import java.util.logging.Level;

import static com.skcraft.launcher.util.SharedLocale.tr;

/**
 * Handles post-process creation during launch.
 */
@Log
public class LaunchProcessHandler implements Function<Process, ProcessConsoleFrame> {

    private static final int CONSOLE_NUM_LINES = 10000;

    private final Launcher launcher;
    private ProcessConsoleFrame consoleFrame;
    @Getter
    private final Image trayErrorIcon = SwingHelper.createImage(Launcher.class, "ui/notification/error.png");;

    /** 1.32: пак, що запущено (для розбору вильоту), або null. */
    private final com.skcraft.launcher.Instance instance;

    public LaunchProcessHandler(@NonNull Launcher launcher) {
        this(launcher, null);
    }

    /** 14.11.2026: вікно лаунчера, з якого запущено гру (власник вікна «Гра закрилась з помилкою»), або null. */
    private final Window owner;

    public LaunchProcessHandler(@NonNull Launcher launcher, com.skcraft.launcher.Instance instance) {
        this(launcher, instance, null);
    }

    public LaunchProcessHandler(@NonNull Launcher launcher, com.skcraft.launcher.Instance instance, Window owner) {
        this.launcher = launcher;
        this.instance = instance;
        this.owner = owner;
    }

    @Override
    public ProcessConsoleFrame apply(final Process process) {
        log.info("Watching process " + process);
        final long startedAt = System.currentTimeMillis();
        // 1.36.3: поки гра йде, кнопки видалення в налаштуваннях відмовляють
        com.gummer.launcher.util.Uninstall.RUNNING_GAMES.incrementAndGet();

        try {
            SwingUtilities.invokeAndWait(new Runnable() {
                @Override
                public void run() {
                    consoleFrame = new ProcessConsoleFrame(CONSOLE_NUM_LINES, launcher.getConfig().isConsoleUseColors());
                    consoleFrame.setProcess(process);
                    if (launcher.getConfig().isShowConsole()) {
                        consoleFrame.setVisible(true);
                    } else {
                        consoleFrame.showSplash();
                    }
                    MessageLog messageLog = consoleFrame.getMessageLog();
                    messageLog.consume(process.getInputStream());
                    //messageLog.consume(process.getErrorStream());
                    messageLog.consume(process.getErrorStream(), Color.RED);
                }
            });

            // 1.33: звіт про відключення з кнопки на екрані відключення гри (наші моди пишуть файл у теку гри)
            startDisconnectReportWatcher(process);

            // Wait for the process to end
            process.waitFor();
        } catch (InterruptedException e) {
            // Orphan process
            consoleFrame.setVisible(true); // auto-open console on failure
        } catch (InvocationTargetException e) {
            consoleFrame.setVisible(true); // auto-open console on failure
            log.log(Level.WARNING, "Unexpected failure", e);
        }

        com.gummer.launcher.util.Uninstall.RUNNING_GAMES.decrementAndGet();
        log.info("Process ended, re-showing launcher...");
        // 1.36.2: мова чи розмір інтерфейсу, змінені гравцем у грі, стають уподобанням для нових збірок
        if (instance != null) com.gummer.launcher.vanilla.GamePrefs.afterGame(launcher.getBaseDir(), instance);
        // 1.33: кнопку могли натиснути за мить до закриття гри
        checkDisconnectReport();

        // 1.32: розбір вильоту (поза EDT: читає звіти про збій і логи)
        int exit = 0;
        try {
            exit = process.exitValue();
        } catch (IllegalThreadStateException ignored) {
        }
        final int exitCode = exit;
        com.gummer.launcher.crash.CrashAnalyzer.Diagnosis diagnosis = null;
        if (exitCode != 0 && instance != null) {
            try {
                String console = consoleFrame != null ? consoleFrame.getMessageLog().getPastableText() : "";
                diagnosis = com.gummer.launcher.crash.CrashAnalyzer.analyze(instance, startedAt, console, exitCode);
                log.info("Game exited with code " + exitCode + ", crash diagnosis: " + diagnosis.key);
            } catch (RuntimeException e) {
                log.log(Level.WARNING, "Crash analysis failed", e);
            }
        }
        final com.gummer.launcher.crash.CrashAnalyzer.Diagnosis crash = diagnosis;

        // Restore the launcher
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                if (consoleFrame != null) {
                    consoleFrame.setProcess(null);

                    // show client console on minecraft crash
                    if (crash != null) {
                        // 1.32: замість голої консолі вікно з причиною і що робити; консоль за кнопкою
                        final ProcessConsoleFrame frame = consoleFrame;
                        // 14.11.2026: власник = вікно лаунчера. Лаунчер з'являється після цього вікна (gameClosed іде
                        // слідом), і без власника перекривав його; вікно з власником Windows тримає над ним
                        com.gummer.launcher.crash.CrashDialog.launcher = launcher;
                        com.gummer.launcher.crash.CrashDialog.show(owner, instance, crash, () -> {
                            frame.setVisible(true);
                            frame.setTitle(tr("splash.client.crashed"));
                            frame.setIconImage(getTrayErrorIcon());
                            frame.toFront();
                        });
                    } else if (!launcher.getConfig().isShowConsole() && process != null && exitCode != 0) {
                        consoleFrame.setVisible(true);
                        consoleFrame.setLocationRelativeTo(null);
                        consoleFrame.setLocation(5, 5);
                        consoleFrame.setTitle(tr("splash.client.crashed"));
                        consoleFrame.setIconImage(getTrayErrorIcon());
                        consoleFrame.setAutoRequestFocus(true);
                        consoleFrame.requestFocus();
                    }

                    // hide splash on client process exit
                    if (consoleFrame.isSplashVisible()) {
                        consoleFrame.hideSplash();
                    }

                    consoleFrame.requestFocus();
                }
            }
        });

        return consoleFrame;
    }

    /** Як часто, поки гра запущена, перевіряти файл звіту про відключення. */
    private static final long REPORT_POLL_MS = 2000L;

    /**
     * 1.33 (13.11.2026): поки гра працює, раз на {@link #REPORT_POLL_MS} мс шукає в теці гри файл
     * {@link com.gummer.launcher.support.DisconnectReport#FILE_NAME} (його пише кнопка «Надіслати звіт у техпідтримку» з GummerVibe
     * чи міні-мода Fabric) і відкриває форму підтримки. Потік фоновий і сам завершується разом з грою.
     */
    private void startDisconnectReportWatcher(final Process process) {
        if (instance == null) return;
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                while (process.isAlive()) {
                    checkDisconnectReport();
                    try {
                        Thread.sleep(REPORT_POLL_MS);
                    } catch (InterruptedException e) {
                        return;
                    }
                }
            }
        }, "Disconnect report watcher");
        t.setDaemon(true);
        t.start();
    }

    private void checkDisconnectReport() {
        if (instance == null) return;
        final com.gummer.launcher.support.DisconnectReport report =
                com.gummer.launcher.support.DisconnectReport.take(instance.getContentDir());
        if (report == null) return;
        log.info("Disconnect report from the game: " + report);
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                com.gummer.launcher.ui.UiHooks.Support support = com.gummer.launcher.ui.UiHooks.support;
                if (support != null) {
                    support.open(null, report.topic(), report.text());
                } else {
                    log.warning("Support is not available, disconnect report dropped");
                }
            }
        });
    }

}
