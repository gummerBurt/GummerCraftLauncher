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

    public LaunchProcessHandler(@NonNull Launcher launcher, com.skcraft.launcher.Instance instance) {
        this.launcher = launcher;
        this.instance = instance;
    }

    @Override
    public ProcessConsoleFrame apply(final Process process) {
        log.info("Watching process " + process);
        final long startedAt = System.currentTimeMillis();

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

            // Wait for the process to end
            process.waitFor();
        } catch (InterruptedException e) {
            // Orphan process
            consoleFrame.setVisible(true); // auto-open console on failure
        } catch (InvocationTargetException e) {
            consoleFrame.setVisible(true); // auto-open console on failure
            log.log(Level.WARNING, "Unexpected failure", e);
        }

        log.info("Process ended, re-showing launcher...");

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
                        com.gummer.launcher.crash.CrashDialog.show(null, instance, crash, () -> {
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

}
