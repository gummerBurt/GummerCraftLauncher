/*
 * Copyright (C) 2021-2025 GummerCraft <http://gummercraft.fun>
 */

package com.gummer.launcher.swing;

import com.skcraft.launcher.Launcher;
import com.skcraft.launcher.swing.EmptyIcon;
import lombok.SneakyThrows;
import lombok.extern.java.Log;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.plaf.FontUIResource;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Hashtable;
import java.util.Objects;

import static com.gummer.launcher.swing.CustomImageButton.getResizedImage;
import static com.gummer.launcher.util.FlatClientProperties.TABBED_PANE_TAB_WIDTH_MODE;
import static com.gummer.launcher.util.FlatClientProperties.TABBED_PANE_TAB_WIDTH_MODE_COMPACT;
import static com.skcraft.launcher.swing.SwingHelper.createIcon;
import static com.skcraft.launcher.util.Environment.getTotalSystemMemory;
import static org.apache.commons.io.IOUtils.closeQuietly;

/**
 * Swing utility methods.
 */
@Log
public final class SwingExtras {

    public static void enforceMacDefaults() {
        if(System.getProperty("os.name").toLowerCase().contains("mac")) {
            System.setProperty("apple.laf.useScreenMenuBar", "true");
            System.setProperty("apple.awt.application.appearance", "NSAppearanceNameDarkAqua"); // force dark mode title
            System.setProperty("com.apple.mrj.application.apple.menu.about.name", "GummerCraft Launcher");
            System.setProperty("apple.awt.application.name", "GummerCraft Launcher");
            System.setProperty("apple.awt.fileDialogForDirectories", "true");
            System.setProperty("com.apple.mrj.application.growbox.intrudes", "true");
            System.out.println("* applied osx platform options");
        }
    }

    // dummy font changer helper
    public static void setCustomFont(FontUIResource myFont) {
        UIManager.put("CheckBoxMenuItem.acceleratorFont", myFont);
        UIManager.put("Button.font", myFont);
        UIManager.put("ToggleButton.font", myFont);
        UIManager.put("RadioButton.font", myFont);
        UIManager.put("CheckBox.font", myFont);
        UIManager.put("ColorChooser.font", myFont);
        UIManager.put("ComboBox.font", myFont);
        UIManager.put("Label.font", myFont);
        UIManager.put("List.font", myFont);
        UIManager.put("MenuBar.font", myFont);
        UIManager.put("Menu.acceleratorFont", myFont);
        UIManager.put("RadioButtonMenuItem.acceleratorFont", myFont);
        UIManager.put("MenuItem.acceleratorFont", myFont);
        UIManager.put("MenuItem.font", myFont);
        UIManager.put("RadioButtonMenuItem.font", myFont);
        UIManager.put("CheckBoxMenuItem.font", myFont);
        UIManager.put("OptionPane.buttonFont", myFont);
        UIManager.put("OptionPane.messageFont", myFont);
        UIManager.put("Menu.font", myFont);
        UIManager.put("PopupMenu.font", myFont);
        UIManager.put("OptionPane.font", myFont);
        UIManager.put("Panel.font", myFont);
        UIManager.put("ProgressBar.font", myFont);
        UIManager.put("ScrollPane.font", myFont);
        UIManager.put("Viewport.font", myFont);
        UIManager.put("TabbedPane.font", myFont);
        UIManager.put("Slider.font", myFont);
        UIManager.put("Table.font", myFont);
        UIManager.put("TableHeader.font", myFont);
        UIManager.put("TextField.font", myFont);
        UIManager.put("Spinner.font", myFont);
        UIManager.put("PasswordField.font", myFont);
        UIManager.put("TextArea.font", myFont);
        UIManager.put("TextPane.font", myFont);
        UIManager.put("EditorPane.font", myFont);
        UIManager.put("TabbedPane.smallFont", myFont);
        UIManager.put("TitledBorder.font", myFont);
        UIManager.put("ToolBar.font", myFont);
        UIManager.put("ToolTip.font", myFont);
        UIManager.put("Tree.font", myFont);
        UIManager.put("FormattedTextField.font", myFont);
        UIManager.put("IconButton.font", myFont);
        UIManager.put("InternalFrame.optionDialogTitleFont", myFont);
        UIManager.put("InternalFrame.paletteTitleFont", myFont);
        UIManager.put("InternalFrame.titleFont", myFont);
    }

    /**
     * Configures a memory slider (in MB) with appropriate minimum, maximum,
     * tick spacing, label table, and change listener to update the provided spinners.
     * <p>
     * The slider is set up to display values in gigabytes (GB) by converting MB values.
     * When the total system memory is greater than 6 GB, 2 GB tick intervals are used;
     * otherwise, 1 GB intervals are used.
     * </p>
     *
     * @param memorySlider     the slider to configure
     * @param minMemorySpinner the spinner that will reflect the slider’s value (in MB)
     * @param maxMemorySpinner the spinner that will reflect the slider’s value (in MB)
     */
    public static void configureMemorySlider(JSlider memorySlider, JSpinner minMemorySpinner, JSpinner maxMemorySpinner) {
        // Get total system memory (in bytes) and convert to MB and GB.
        long totalMemoryBytes = getTotalSystemMemory();
        int totalMemoryMB = (int) (totalMemoryBytes / (1024 * 1024));
        int totalMemoryGB = totalMemoryMB / 1024;

        // Define memory limits (in GB)
        final int minMemoryGB = 2;      // minimum value to display
        final int defaultMemoryGB = 4;  // default value if not set

        int maxMemoryGB;
        // Try to get the current spinner value (in GB); if not set, use the default.
        int currentMemoryGB;
        try {
            currentMemoryGB = ((Number) minMemorySpinner.getValue()).intValue() / 1024;
        } catch (Exception ex) {
            currentMemoryGB = defaultMemoryGB;
        }

        // Configure slider bounds and tick spacing based on available system memory.
        if (totalMemoryGB > 6) {
            // Use wider (2 GB) intervals if system memory is high, up to a 16 GB cap.
            maxMemoryGB = Math.min(16, totalMemoryGB);
            memorySlider.setMinimum(minMemoryGB * 1024);
            memorySlider.setMaximum(maxMemoryGB * 1024);
            memorySlider.setMajorTickSpacing(2 * 1024);
            // If the current value is less than our default, set it to default.
            if (currentMemoryGB < defaultMemoryGB) {
                minMemorySpinner.setValue(defaultMemoryGB * 1024);
                maxMemorySpinner.setValue(defaultMemoryGB * 1024);
            }
        } else {
            // For systems with 6 GB or less, use 1 GB intervals.
            maxMemoryGB = 6;
            memorySlider.setMinimum(minMemoryGB * 1024);
            memorySlider.setMaximum(maxMemoryGB * 1024);
            memorySlider.setMajorTickSpacing(1 * 1024);
        }

        // Set the slider's current value (assumed to be the value already in the spinners).
        memorySlider.setValue(((Number) maxMemorySpinner.getValue()).intValue());

        // Configure slider appearance.
        memorySlider.setPaintTicks(true);
        memorySlider.setPaintLabels(true);
        memorySlider.setSnapToTicks(true);

        // Create a label table that displays the tick marks in GB.
        Hashtable<Integer, JLabel> labelTable = new Hashtable<>();
        int tickStep = totalMemoryGB > 6 ? 2 : 1;
        for (int i = minMemoryGB; i <= maxMemoryGB; i += tickStep) {
            labelTable.put(i * 1024, new JLabel(i + " GB"));
        }
        memorySlider.setLabelTable(labelTable);

        // Add a change listener that updates both spinners when the slider value changes.
        memorySlider.addChangeListener(e -> {
            int valueInMB = memorySlider.getValue();
            minMemorySpinner.setValue(valueInMB);
            maxMemorySpinner.setValue(valueInMB);
        });
    }

    public static BufferedImage readIconImage(Class<?> clazz, String path) {
        InputStream in = null;
        try {
            in = clazz.getResourceAsStream(path);
            if (in != null) {
                return ImageIO.read(in);
            }
        } catch (IOException e) {
        } finally {
            closeQuietly(in);
        }
        return null;
    }

    public static BufferedImage readIconImage(File path) {
        InputStream in = null;
        try {
            in = new FileInputStream(path);
            if (in != null) {
                return ImageIO.read(in);
            }
        } catch (IOException e) {
        } finally {
            closeQuietly(in);
        }
        return null;
    }

    public static void setIconImage(JFrame frame, Class<?> clazz, String path) {
        BufferedImage image = readIconImage(clazz, path);
        if (image != null) {
            frame.setIconImage(image);
        }
    }

//    public static void addTabWidthMode( JTabbedPane tabbedPane, String tabTitle, String tabWidthMode) {
//        tabbedPane.putClientProperty( TABBED_PANE_TAB_WIDTH_MODE, tabWidthMode );
//        tabbedPane.addTab( tabTitle, null );
//    }

    public static void addTabWidthMode(JTabbedPane tabbedPane, String tabTitle, String tabWidthMode, Component tabComponent) {
        tabbedPane.putClientProperty( TABBED_PANE_TAB_WIDTH_MODE, tabWidthMode );
        tabbedPane.addTab( tabTitle, tabComponent );
    }

//    public static void addTabWidthMode( JTabbedPane tabbedPane, String tabTitle, String tabWidthMode, Class<?> clazz, String iconPath) {
//        tabbedPane.putClientProperty( TABBED_PANE_TAB_WIDTH_MODE, tabWidthMode );
//        tabbedPane.addTab(
//            tabTitle,
//            new ImageIcon(Objects.requireNonNull(SwingExtras.readIconImage(clazz, iconPath))),
//            null
//        );
//    }
//
//    public static void addTabWidthMode( JTabbedPane tabbedPane, String tabTitle, String tabWidthMode, Class<?> clazz, String iconPath, Component tabComponent) {
//        tabbedPane.putClientProperty( TABBED_PANE_TAB_WIDTH_MODE, tabWidthMode );
//        tabbedPane.addTab(
//            tabTitle,
//            new ImageIcon(Objects.requireNonNull(SwingExtras.readIconImage(clazz, iconPath))),
//            tabComponent
//        );
//    }
//
//    @SneakyThrows
//    public static void addTabWidthMode(JTabbedPane tabbedPane, String tabTitle, String tabWidthMode, String iconPath) {
//        tabbedPane.putClientProperty( TABBED_PANE_TAB_WIDTH_MODE, tabWidthMode );
//        tabbedPane.addTab(
//            tabTitle,
//            new ImageIcon(Objects.requireNonNull(SwingExtras.readIconImage(new File(iconPath)))),
//            null
//        );
//    }
//
//    @SneakyThrows
//    public static void addTabWidthMode(JTabbedPane tabbedPane, String tabTitle, String tabWidthMode, String iconPath, Component tabComponent) {
//        tabbedPane.putClientProperty( TABBED_PANE_TAB_WIDTH_MODE, tabWidthMode );
//        tabbedPane.addTab(
//            tabTitle,
//            new ImageIcon(Objects.requireNonNull(SwingExtras.readIconImage(new File(iconPath)))),
//            tabComponent
//        );
//    }
//
//    @SneakyThrows
//    public static void addTabWidthMode(JTabbedPane tabbedPane, String tabTitle, String tabWidthMode, String iconPath, int x, int y) {
//        tabbedPane.putClientProperty( TABBED_PANE_TAB_WIDTH_MODE, tabWidthMode );
//        tabbedPane.addTab(
//            tabTitle,
//            new ImageIcon(
//                getResizedImage(
//                    Objects.requireNonNull(SwingExtras.readIconImage(new File(iconPath))),
//                    x, y)),
//            null
//        );
//    }
//
//    @SneakyThrows
//    public static void addTabWidthMode(JTabbedPane tabbedPane, String tabTitle, String tabWidthMode, String iconPath, int x, int y, Component tabComponent) {
//        tabbedPane.putClientProperty( TABBED_PANE_TAB_WIDTH_MODE, tabWidthMode );
//        tabbedPane.addTab(
//            tabTitle,
//            new ImageIcon(
//                getResizedImage(
//                    Objects.requireNonNull(SwingExtras.readIconImage(new File(iconPath))),
//                    x, y)),
//            tabComponent
//        );
//    }

    @SneakyThrows
    public static void addTabWidthMode(JTabbedPane tabbedPane, String tabTitle, String tabWidthMode, ImageIcon icon, Component tabComponent) {
        tabbedPane.putClientProperty( TABBED_PANE_TAB_WIDTH_MODE, tabWidthMode );
        tabbedPane.addTab(
            tabTitle,
            icon,
            tabComponent
        );
    }

    public static Color hexToColor(String hex) {
        // Remove the '#' if it's present.
        if (hex.startsWith("#")) {
            hex = hex.substring(1);
        }

        if (hex.length() == 6) { // RGB (opaque)
            int r = Integer.parseInt(hex.substring(0, 2), 16);
            int g = Integer.parseInt(hex.substring(2, 4), 16);
            int b = Integer.parseInt(hex.substring(4, 6), 16);
            return new Color(r, g, b);
        } else if (hex.length() == 8) { // RGBA (with alpha)
            int r = Integer.parseInt(hex.substring(0, 2), 16);
            int g = Integer.parseInt(hex.substring(2, 4), 16);
            int b = Integer.parseInt(hex.substring(4, 6), 16);
            int a = Integer.parseInt(hex.substring(6, 8), 16);
            return new Color(r, g, b, a);
        } else {
            throw new IllegalArgumentException("Invalid hex color string: " + hex);
        }
    }

    public static JLabel createIconWithTooltip(Class<?> clazz, String imageName, String tooltip) {
        // Create the icon (this will either be an ImageIcon or an EmptyIcon)
        Icon icon = createIcon(clazz, imageName);

        // Wrap the icon in a JLabel
        JLabel label = new JLabel(icon);

        // Optionally, set a custom initial delay for the tooltip globally
        ToolTipManager.sharedInstance().setInitialDelay(700);

        // Set the tooltip text for this label
        label.setToolTipText(tooltip);

        return label;
    }

    @SneakyThrows
    public static void logDebugMessage(Launcher launcher, String message) {
        if (launcher.getConfig().isEnableDebug()) {
            log.info("DEBUG: " + message + " ");
        }
    }

    @SneakyThrows
    public static void logDebugMessage(Launcher launcher, String title, String message) {
        if (launcher.getConfig().isEnableDebug()) {
            log.info("DEBUG '" + title + "': " + message + " ");
        }
    }
}
