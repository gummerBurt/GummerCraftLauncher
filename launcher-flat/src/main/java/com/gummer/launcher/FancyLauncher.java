/*
 * Copyright (C) 2021-2025 GummerCraft <http://gummercraft.fun>
 */

package com.gummer.launcher;

import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.themes.FlatMacDarkLaf;
import com.formdev.flatlaf.util.SystemInfo;
import com.google.common.base.Supplier;
import com.google.common.io.ByteStreams;
import com.gummer.launcher.swing.SwingExtras;
import com.skcraft.launcher.Launcher;
import com.skcraft.launcher.dialog.ConsoleFrame;
import com.skcraft.launcher.swing.SwingHelper;
import lombok.SneakyThrows;
import lombok.extern.java.Log;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.logging.Level;

import static com.skcraft.launcher.util.SharedLocale.tr;

@Log
public class FancyLauncher {

    public static void main(final String[] args) {
        // ПЕРШИМ ділом, до будь-якого класу FlatLaf: на Windows FlatLaf вантажить свою DLL уже при першому зверненні до
        // com.formdev.flatlaf.util.SystemInfo, тож flatlaf.nativeLibraryPath має бути виставлений раніше (інакше DLL іде в %TEMP%).
        useNativesFromLauncherDir(args);
        SwingExtras.enforceMacDefaults(); // tune and enforce macos defaults
        Launcher.setupLogger();

        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                try {
                    Thread.currentThread().setContextClassLoader(FancyLauncher.class.getClassLoader());
                    UIManager.getLookAndFeelDefaults().put("ClassLoader", FancyLauncher.class.getClassLoader());

                    Launcher launcher = Launcher.createFromArguments(args);
                    SwingHelper.setSwingProperties(tr("launcher.appTitle", launcher.getVersion()));

                    try {
                        // init and setup UI LaF
                        UIManager.setLookAndFeel( new FlatMacDarkLaf() );
                    } catch( Exception ex ) {
                        System.err.println( "Failed to initialize UI LaF" );
                        //ex.printStackTrace();
                    }

                    enforcePlatformDefaults();
                    enforceUIManagerDefaults();

                    launcher.setMainWindowSupplier(new CustomWindowSupplier(launcher));
                    launcher.showLauncherWindow();

                    // show launcher console if config option enabled
                    if (launcher.getConfig().isShowLauncherConsole()) {
                        ConsoleFrame.showMessagesColor(launcher.getConfig().isConsoleUseColors());
                    }
                } catch (Throwable t) {
                    log.log(Level.WARNING, "Load failure", t);
                    SwingHelper.showErrorDialog(null, "Uh oh! The updater couldn't be opened because a " +
                            "problem was encountered.", "Launcher error", t);
                }
            }
        });
    }

    private static class CustomWindowSupplier implements Supplier<Window> {

        private final Launcher launcher;

        private CustomWindowSupplier(Launcher launcher) {
            this.launcher = launcher;
        }

        @SneakyThrows
        @Override
        public Window get() {
            return new FlatLauncherFrame(launcher);
        }
    }

    /**
     * FlatLaf розпаковує свою невелику службову DLL (рамка вікна) у %TEMP% при кожному запуску, якщо {@code flatlaf.nativeLibraryPath} не вказує на папку,
     * де вона вже лежить. Тримаємо її в папці лаунчера (&lt;--dir&gt;/launcher/natives), як звичайна програма, - нічого виконуваного в тимчасову папку.
     * Викликається першим рядком main і НЕ чіпає класів FlatLaf (SystemInfo сам вантажить DLL): ОС і архітектура - з os.name/os.arch, сама DLL -
     * ресурсом із jar. Будь-яка помилка - FlatLaf просто працює як раніше.
     */
    private static void useNativesFromLauncherDir(String[] args) {
        if (!System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).startsWith("windows")) {
            return;
        }
        File baseDir = null;
        for (int i = 0; i + 1 < args.length; i++) {
            if ("--dir".equals(args[i])) baseDir = new File(args[i + 1]);
        }
        if (baseDir == null) return;

        String arch = System.getProperty("os.arch", "").toLowerCase(java.util.Locale.ROOT);
        String classifier = arch.contains("aarch64") || arch.contains("arm64") ? "windows-arm64"
                : (arch.contains("64") ? "windows-x86_64" : "windows-x86");
        String name = "flatlaf-" + classifier + ".dll";
        File dir = new File(baseDir, "launcher/natives");
        File target = new File(dir, name);
        try (InputStream in = FancyLauncher.class.getClassLoader().getResourceAsStream("com/formdev/flatlaf/natives/" + name)) {
            if (in == null) {
                return;
            }
            byte[] bundled = ByteStreams.toByteArray(in);
            if (!target.isFile() || !Arrays.equals(bundled, Files.readAllBytes(target.toPath()))) {
                dir.mkdirs();
                File tmp = new File(dir, name + ".tmp");
                Files.write(tmp.toPath(), bundled);
                Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            System.setProperty("flatlaf.nativeLibraryPath", dir.getAbsolutePath());
        } catch (IOException | RuntimeException e) {
            System.err.println("Could not place FlatLaf native library in " + dir + ": " + e);
        }
    }

    public static void enforcePlatformDefaults() {
        if( SystemInfo.isWindows ) {
            System.setProperty( "flatlaf.useWindowDecorations", "true" );
            System.setProperty( "JRootPane.useWindowDecorations", "true" );
            System.setProperty( "flatlaf.menuBarEmbedded", "true" );
            System.setProperty( "JRootPane.menuBarEmbedded", "true" );
        }
        if( SystemInfo.isLinux ) {
            JFrame.setDefaultLookAndFeelDecorated( true );
            JDialog.setDefaultLookAndFeelDecorated( true );
        }
        System.setProperty("awt.useSystemAAFontSettings", "on");
        System.setProperty("Dawt.useSystemAAFontSettings","on");
        System.setProperty("swing.aatext", "true");
    }

    public static void enforceUIManagerDefaults() {
        UIManager.getDefaults().put("SplitPane.border", BorderFactory.createEmptyBorder());

        UIManager.put( "ProgressBar.arc", 0 );
        UIManager.put( "TextComponent.arc", 5 );
        UIManager.put( "Component.arc", 5 );
        UIManager.put( "Button.arc", 5 );

        UIManager.put( "Button.innerFocusWidth", 0 );

        UIManager.put( "ComboBox.buttonStyle", "button" );
        //UIManager.put( "ComboBox.foreground", SwingExtras.hexToColor("#282828"));
        UIManager.put( "ComboBox.background", SwingExtras.hexToColor("#282828"));
        //UIManager.put( "ComboBox.selectionInsets", "0,0,0,0" );
        //UIManager.put( "ComboBox.popupInsets", "0,0,0,0" );
        //UIManager.put( "ComboBox.selectionInsets", new Insets( 0, 0, 0, 0 ));
        //UIManager.put( "ComboBox.popupInsets", "0,0,0,0" );

        //UIManager.put( "PopupMenu.borderInsets", new Insets( 0, 0, 0, 0 ));

        UIManager.put( "Component.arrowType", "chevron" );
        UIManager.put( "Component.focusWidth", 0 );
        UIManager.put( "Component.innerFocusWidth", 0 );

        UIManager.put( "ScrollBar.showButtons", false );
        UIManager.put( "ScrollBar.thumbInsets", new Insets( 2, 2, 2, 2 ));
        UIManager.put( "ScrollBar.width", 6 );

        UIManager.put("ToolBar.background", SwingExtras.hexToColor("#262626"));
        UIManager.put("ToolBar.hoverButtonGroupBackground", SwingExtras.hexToColor("#262626"));
        UIManager.put( "Button.toolbar.selectedForeground", SwingExtras.hexToColor("#FFFFFF"));
        UIManager.put( "Button.toolbar.pressedForeground", SwingExtras.hexToColor("#FFFFFF"));
        //UIManager.put( "Button.toolbar.pressedBackground", "" );
        //UIManager.put( "Button.toolbar.hoverBackground", "" );

        //UIManager.put( "Button.toolbar.margin", "5,5,5,5" );
        //UIManager.put( "ToolBar.borderMargins", "10,10,10,10" );
        //UIManager.put( "ToolBar.focusableButtons", false );
    }

}
