/*
 * Copyright (C) 2021-2025 GummerCraft <http://gummercraft.fun>
 */

package com.gummer.launcher;

import com.gummer.launcher.dialog.LauncherFrameFlat;
import com.skcraft.launcher.Launcher;
import com.skcraft.launcher.swing.SwingHelper;
import com.skcraft.launcher.swing.WebpagePanel;
import lombok.NonNull;

import javax.swing.*;
import java.io.IOException;

public class FlatLauncherFrame extends LauncherFrameFlat {

    /** Фонова панель вікна; заповнюється в createContainerPanel (той кличеться з конструктора батька), тому без ініціалізатора. */
    private FlatBackgroundPanel backgroundPanel;

    /**
     * Create a new frame.
     *
     * @param launcher the launcher
     */
    public FlatLauncherFrame(@NonNull Launcher launcher) throws IOException {
        super(launcher);

        setSize(800, 500);
        setLocationRelativeTo(null);

        SwingHelper.removeOpaqueness(getInstancesTable());
        SwingHelper.removeOpaqueness(getInstanceScroll());
        getInstanceScroll().setBorder(BorderFactory.createEmptyBorder());

        // фон міняється разом із вибраною збіркою (FlatBackgroundPanel.showPack)
        getInstancesTable().getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) updatePackBackground();
        });
        updatePackBackground();
    }

    private void updatePackBackground() {
        if (backgroundPanel == null) return;
        int row = getInstancesTable().getSelectedRow();
        String name = null;
        if (row >= 0 && row < launcher.getInstances().size()) {
            name = launcher.getInstances().get(row).getName();
        }
        backgroundPanel.showPack(name);
        showPackButtons(name != null ? name.toLowerCase(java.util.Locale.ROOT) : null);
    }

    /** Вікі вибраної збірки (wiki.url у frame_&lt;назва&gt;.properties), інакше - загальна. */
    @Override
    protected String wikiUrl() {
        String own = backgroundPanel != null ? backgroundPanel.packSetting("wiki.url") : null;
        return own != null ? own : super.wikiUrl();
    }

    /** Telegram вибраної збірки (telegram.url), інакше - загальний. */
    @Override
    protected String telegramUrl() {
        String own = backgroundPanel != null ? backgroundPanel.packSetting("telegram.url") : null;
        return own != null ? own : super.telegramUrl();
    }

    /** Discord вибраної збірки (discord.url), інакше - загальний. */
    @Override
    protected String discordUrl() {
        String own = backgroundPanel != null ? backgroundPanel.packSetting("discord.url") : null;
        return own != null ? own : super.discordUrl();
    }

    @Override
    protected JPanel createContainerPanel() {
        backgroundPanel = new FlatBackgroundPanel();
        return backgroundPanel;
    }

    @Override
    protected WebpagePanel createNewsPanel() {
        WebpagePanel panel = super.createNewsPanel();
        //panel.setBrowserBorder(BorderFactory.createEmptyBorder());
        panel.setBrowserBorder(null); // hide news panel border
        return panel;
    }

}
