/*
 * SK's Minecraft Launcher
 * Copyright (C) 2010-2014 Albert Pham <http://www.sk89q.com> and contributors
 * Copyright (C) 2021-2025 GummerCraft <http://gummercraft.fun>
 * Please see LICENSE.txt for license information.
 */

package com.skcraft.launcher.dialog;

import com.gummer.launcher.util.PasteUtils;
import com.skcraft.launcher.Launcher;
import com.skcraft.launcher.swing.LinedBoxPanel;
import com.skcraft.launcher.swing.MessageLog;
import com.skcraft.launcher.swing.SwingHelper;
import lombok.Getter;
import lombok.NonNull;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.datatransfer.ClipboardOwner;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

import static com.skcraft.launcher.util.SharedLocale.tr;

/**
 * A frame capable of showing messages.
 */
public class ConsoleFrame extends JFrame {

    private static ConsoleFrame globalFrame;

    @Getter private final Image appIcon;
    @Getter private final Image trayRunningIcon;
    @Getter private final Image trayClosedIcon;
    @Getter private final Image trayErrorIcon;

    @Getter private final MessageLog messageLog;
    @Getter private LinedBoxPanel buttonsPanel;

    private final JFrame splash = new JFrame();
    private final JProgressBar splashProgress = new JProgressBar();

    private boolean registeredGlobalLog = false;

    /**
     * Construct the frame.
     *
     * @param numLines number of lines to show at a time
     * @param colorEnabled true to enable a colored console
     */
    public ConsoleFrame(int numLines, boolean colorEnabled) {
        this(tr("console.title"), numLines, colorEnabled);
    }

    /**
     * Construct the frame.
     *
     * @param title the title of the window
     * @param numLines number of lines to show at a time
     * @param colorEnabled true to enable a colored console
     */
    public ConsoleFrame(@NonNull String title, int numLines, boolean colorEnabled) {
        messageLog = new MessageLog(numLines, colorEnabled, this);
        appIcon  = SwingHelper.createImage(Launcher.class, "icon.png");
        trayRunningIcon = SwingHelper.createImage(Launcher.class, "ui/icon/tray/running.png");
        trayClosedIcon = SwingHelper.createImage(Launcher.class, "ui/icon/tray/stopped.png");
        trayErrorIcon = SwingHelper.createImage(Launcher.class, "ui/notification/error.png");

        setTitle(title);
        setIconImage(trayRunningIcon);

        setSize(new Dimension(650, 400));
        initComponents();

        setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent event) {
                performClose();
            }
        });
    }

    /**
     * Add components to the frame.
     */
    private void initComponents() {
        // setup splashcreen
        splash.setTitle(tr("splash.client.title"));
        splash.setIconImage(appIcon);
        splash.setUndecorated(true);
        splash.setAlwaysOnTop(true);
        splash.setAutoRequestFocus(true);
        splash.setPreferredSize(new Dimension(300, 80));
        JLabel label = new JLabel(tr("splash.client.message"));
        label.setHorizontalAlignment(JLabel.CENTER);
        splash.add(label, BorderLayout.NORTH);
        splashProgress.setIndeterminate(true);
        splash.add(splashProgress, BorderLayout.CENTER);
        splash.pack();

        JButton pastebinButton = new JButton(tr("console.uploadLog"));
        JButton clearLogButton = new JButton(tr("console.clearLog"));

        pastebinButton.putClientProperty("FlatLaf.styleClass", "large");
        clearLogButton.putClientProperty("FlatLaf.styleClass", "large");

        buttonsPanel = new LinedBoxPanel(true);

        buttonsPanel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        buttonsPanel.addElement(pastebinButton);
        buttonsPanel.addElement(clearLogButton);

        TitledBorder consoleMessageLogTitle = BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(Color.black), ""
                //SharedLocale.tr("console.launcherConsoleTitle")
        );
        messageLog.setBorder(consoleMessageLogTitle);

        add(buttonsPanel, BorderLayout.NORTH);
        add(messageLog, BorderLayout.CENTER);

        clearLogButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                messageLog.clear();
            }
        });

        pastebinButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                pastebinLog();
            }
        });
    }

    /**
     * Register the global logger if it hasn't been registered.
     */
    private void registerLoggerHandler() {
        if (!registeredGlobalLog) {
            getMessageLog().registerLoggerHandler();
            registeredGlobalLog = true;
        }
    }

    /**
     * Attempt to perform window close.
     */
    protected void performClose() {
        messageLog.detachGlobalHandler();
        messageLog.clear();
        registeredGlobalLog = false;
        dispose();
    }

    /**
     * Send the contents of the message log to a pastebin.
     */
    private void pastebinLog() {
        String text = messageLog.getPastableText();
        // Not really bytes!
        messageLog.log(tr("console.pasteUploading", text.length()), messageLog.asHighlighted());

        String out = PasteUtils.paste(text);
        if(out==null){
            messageLog.log(tr("console.pasteFailed"), messageLog.asError());
        }else{
            out = out.replaceFirst("(?i)^http://", "https://"); // always show the https link
            messageLog.log(tr("console.pasteUploaded", out), messageLog.asHighlighted());
            //SwingHelper.openURL(out, messageLog); // don't open browser by default
            try {
                Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(out), (ClipboardOwner)null); // copy link to clipboad
            } catch (Exception exception) {
            }
        }
    }

    public static void showMessages() {
        ConsoleFrame frame = globalFrame;
        if (frame == null) {
            frame = new ConsoleFrame(10000, false);
            globalFrame = frame;
            frame.setTitle(tr("console.launcherConsoleTitle"));
            frame.registerLoggerHandler();
            frame.setVisible(true);
        } else {
            frame.setVisible(true);
            frame.registerLoggerHandler();
            frame.requestFocus();
        }
    }

    public static void showMessagesColor(boolean colorEnabled) {
        ConsoleFrame frame = globalFrame;
        if (frame == null) {
            frame = new ConsoleFrame(10000, colorEnabled);
            globalFrame = frame;
            frame.setTitle(tr("console.launcherConsoleTitle"));
            frame.registerLoggerHandler();
            frame.setVisible(true);
        } else {
            frame.setVisible(true);
            frame.registerLoggerHandler();
            frame.requestFocus();
        }
    }

    public static void hideMessages() {
        ConsoleFrame frame = globalFrame;
        if (frame != null) {
            frame.setVisible(false);
        }
    }

    public synchronized void showSplash() {
        splash.setLocationRelativeTo(null);
        splash.setAlwaysOnTop(false);
        splash.setVisible(true);
        splash.requestFocus();
    }

    public boolean isSplashVisible() {
        return splash.isVisible();
    }

    public synchronized void hideSplash() {
        splash.setAlwaysOnTop(false);
        splash.setVisible(false);
    }

}
