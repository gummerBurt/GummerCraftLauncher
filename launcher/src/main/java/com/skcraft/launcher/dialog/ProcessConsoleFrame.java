/*
 * SK's Minecraft Launcher
 * Copyright (C) 2010-2014 Albert Pham <http://www.sk89q.com> and contributors
 * Copyright (C) 2021-2025 GummerCraft <http://gummercraft.fun>
 * Please see LICENSE.txt for license information.
 */

package com.skcraft.launcher.dialog;

import com.skcraft.launcher.swing.LinedBoxPanel;
import com.skcraft.launcher.swing.SwingHelper;
import com.skcraft.launcher.util.SharedLocale;
import lombok.Getter;
import lombok.Setter;
import lombok.SneakyThrows;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowEvent;
import java.io.PrintWriter;
import java.util.ArrayList;

import static com.skcraft.launcher.util.SharedLocale.tr;

/**
 * A version of the console window that can manage a process.
 */
public class ProcessConsoleFrame extends ConsoleFrame {

    private JButton killButton;
    private JButton minimizeButton;
    private TrayIcon trayIcon;

    private long age;
    private static java.util.List<ProcessConsoleFrame> consoleList = new ArrayList<>();

    @Getter private Process process;
    @Getter @Setter private boolean killOnClose;

    private PrintWriter processOut;

    /**
     * Create a new instance of the frame.
     *
     * @param numLines the number of log lines
     * @param colorEnabled whether color is enabled in the log
     */
    public ProcessConsoleFrame(int numLines, boolean colorEnabled) {
        super(SharedLocale.tr("console.title"), numLines, colorEnabled);
        age = System.currentTimeMillis();
        consoleList.add(this);
        processOut = new PrintWriter(
                getMessageLog().getOutputStream(new Color(0, 0, 255)), true);
        initComponents();
        updateComponents(false, false);
        for (ProcessConsoleFrame processConsoleFrame : consoleList) {
            if (!processConsoleFrame.equals(this))
            {
                processConsoleFrame.dispatchEvent(new WindowEvent(processConsoleFrame, WindowEvent.WINDOW_CLOSING));
            }
        }
    }

    /**
     * Track the given process.
     *
     * @param process the process
     */
    @SneakyThrows
    public synchronized void setProcess(Process process) {
        boolean crash = false;
        try {
            Process lastProcess = this.process;
            if (lastProcess != null) {
                if (lastProcess.exitValue() != 0) {
                    processOut.println("\ncrash detected!!!!");
                    processOut.println(String.format("JVM: v%s (%s/%s/%s/%s)",
                        System.getProperty("java.specification.version"),
                        System.getProperty("java.runtime.version"),
                        System.getProperty("java.version.date"),
                        System.getProperty("java.vm.vendor"),
                        System.getProperty("java.vm.name\n")
                    ));
                    crash = true;
                }
                processOut.println(tr("console.processEndCode", lastProcess.exitValue()));
            }
        } catch (IllegalThreadStateException e) {
        }

        if (process != null) {
            // log system info
            processOut.println(String.format("APP: v@version@ (%s/%s)",
                    System.getProperty("os.name"),
                    System.getProperty("os.arch")
            ));
            processOut.println(String.format("JVM: v%s (%s/%s/%s/%s)",
                    System.getProperty("java.specification.version"),
                    System.getProperty("java.runtime.version"),
                    System.getProperty("java.version.date"),
                    System.getProperty("java.vm.vendor"),
                    System.getProperty("java.vm.name")
            ));
            processOut.println(String.format("%s",
                    getClass().getProtectionDomain().getCodeSource().getLocation().toURI().getPath()
            ));
            processOut.println(SharedLocale.tr("console.attachedToProcess"));
        }

        this.process = process;

        boolean isCrashed = crash;
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                updateComponents(isCrashed, false);
            }
        });
    }

    private synchronized boolean hasProcess() {
        return process != null;
    }

    @Override
    protected void performClose() {
        if (hasProcess()) {
            if (killOnClose) {
                performKill();
            }
        }

        if (trayIcon != null) {
            SystemTray.getSystemTray().remove(trayIcon);
        }

        super.performClose();
    }

    private void performKill() {
        if (!confirmKill()) {
            return;
        }

        synchronized (this) {
            if (hasProcess()) {
                process.destroy();
                setProcess(null);

            }
        }

        updateComponents(false, true);
    }

    protected void initComponents() {
        killButton = new JButton(SharedLocale.tr("console.forceClose"));
        minimizeButton = new JButton(); // Text set later

        killButton.putClientProperty("FlatLaf.styleClass", "large");
        minimizeButton.putClientProperty("FlatLaf.styleClass", "large");

        LinedBoxPanel buttonsPanel = getButtonsPanel();
        buttonsPanel.addGlue();
        buttonsPanel.addElement(killButton);
        buttonsPanel.addElement(minimizeButton);

        killButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                performKill();
            }
        });

        minimizeButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                contextualClose();
            }
        });

        if (!setupTrayIcon()) {
            minimizeButton.setEnabled(true);
        }
    }

    private boolean setupTrayIcon() {
        if (!SystemTray.isSupported()) {
            return false;
        }

        trayIcon = new TrayIcon(getTrayRunningIcon());
        trayIcon.setImageAutoSize(true);
        trayIcon.setToolTip(SharedLocale.tr("console.trayTooltip"));

        trayIcon.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                reshow();
            }
        });

        PopupMenu popup = new PopupMenu();
        MenuItem item;

        popup.add(item = new MenuItem(SharedLocale.tr("console.trayTitle")));
        item.setEnabled(false);

        popup.add(item = new MenuItem(SharedLocale.tr("console.tray.showWindow")));
        item.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                reshow();
            }
        });

        popup.add(item = new MenuItem(SharedLocale.tr("console.tray.forceClose")));
        item.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                performKill();
            }
        });

        trayIcon.setPopupMenu(popup);

        try {
            SystemTray tray = SystemTray.getSystemTray();
            tray.add(trayIcon);
            return true;
        } catch (AWTException e) {
        }

        return false;
    }

    private synchronized void updateComponents(Boolean isCrashed, Boolean isKilled) {
        Image icon = hasProcess() ? getTrayRunningIcon() : getTrayClosedIcon();

        killButton.setEnabled(hasProcess());

        if (!hasProcess() || trayIcon == null) {
            minimizeButton.setText(SharedLocale.tr("console.closeWindow"));
        } else {
            minimizeButton.setText(SharedLocale.tr("console.hideWindow"));
        }

        if (isCrashed) { icon = getTrayErrorIcon(); }
        if (trayIcon != null) {
            trayIcon.setImage(icon);
        }
        setIconImage(icon);
    }

    private synchronized void contextualClose() {
        if (!hasProcess() || trayIcon == null) {
            performClose();
        } else {
            minimize();
        }

        updateComponents(false, false);
    }

    private boolean confirmKill() {
        if (System.getProperty("skcraftLauncher.killWithoutConfirm", "false").equalsIgnoreCase("true")) {
            return true;
        } else {
            return SwingHelper.confirmDialog(this,  SharedLocale.tr("console.confirmKill"), SharedLocale.tr("console.confirmKillTitle"));
        }
    }

    private void minimize() {
        setVisible(false);
    }

    private void reshow() {
        setVisible(true);
        requestFocus();
    }

}
