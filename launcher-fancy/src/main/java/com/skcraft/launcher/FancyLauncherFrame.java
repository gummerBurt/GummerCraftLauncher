/*
 * SKCraft Launcher
 * Copyright (C) 2010-2014 Albert Pham <http://www.sk89q.com> and contributors
 * Copyright (C) 2021-2025 GummerCraft <http://gummercraft.fun>
 * Please see LICENSE.txt for license information.
 */

package com.skcraft.launcher;

//import com.skcraft.launcher.dialog.LauncherFrame;
import com.gummer.launcher.dialog.LauncherFrameThemed;
import com.skcraft.launcher.swing.SwingHelper;
import com.skcraft.launcher.swing.WebpagePanel;
import lombok.NonNull;

import javax.swing.*;
import java.io.IOException;

//public class FancyLauncherFrame extends LauncherFrame {
public class FancyLauncherFrame extends LauncherFrameThemed {

    /**
     * Create a new frame.
     *
     * @param launcher the launcher
     */
    //public FancyLauncherFrame(@NonNull Launcher launcher) {
    public FancyLauncherFrame(@NonNull Launcher launcher) throws IOException {
        super(launcher);

        setSize(800, 500);
        setLocationRelativeTo(null);

        SwingHelper.removeOpaqueness(getInstancesTable());
        SwingHelper.removeOpaqueness(getInstanceScroll());
        getInstanceScroll().setBorder(BorderFactory.createEmptyBorder());
    }

    @Override
    protected JPanel createContainerPanel() {
        return new FancyBackgroundPanel();
    }

    @Override
    protected WebpagePanel createNewsPanel() {
        WebpagePanel panel = super.createNewsPanel();
        //panel.setBrowserBorder(BorderFactory.createEmptyBorder());
        panel.setBrowserBorder(null); // hide news panel border
        return panel;
    }

}
