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

        // одразу вигляд останньої вибраної збірки (ще до завантаження списку збірок) - без "стандартний, а потім перемикання"
        String last = launcher.getConfig().getLastInstance();
        if (backgroundPanel != null && last != null) {
            String look = last.startsWith(com.gummer.launcher.vanilla.VanillaVersions.GROUP + "-") ? com.gummer.launcher.vanilla.VanillaVersions.GROUP : last;
            backgroundPanel.showPack(look, false);
            showPackButtons(look.toLowerCase(java.util.Locale.ROOT));
        }

        // фон міняється разом із вибраною збіркою (FlatBackgroundPanel.showPack); перший вибір після старту - без переходу
        getInstancesTable().getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) updatePackBackground();
        });
        updatePackBackground();
    }

    private boolean firstSelection = true;

    private void updatePackBackground() {
        if (backgroundPanel == null) return;
        com.skcraft.launcher.Instance selected = getSelectedInstance();
        if (selected == null) return; // нічого не вибрано (список ще вантажиться) - лишаємо поточний вигляд
        String name = selected.getName();
        // усі ванільні версії (vanilla-1.21.1, vanilla-26.3, ...) мають одне оформлення: bg_vanilla.jpg і logo_vanilla.png
        String look = com.gummer.launcher.vanilla.VanillaVersions.GROUP.equals(selected.getGroup())
                ? com.gummer.launcher.vanilla.VanillaVersions.GROUP : name;
        backgroundPanel.showPack(look, !firstSelection);
        showPackButtons(look != null ? look.toLowerCase(java.util.Locale.ROOT) : null);
        firstSelection = false;

        // запам'ятати вибір гравця (config.json) - наступного разу лаунчер одразу відкриється з цією збіркою;
        // вибір, який лаунчер відновлює сам (restoringSelection), не запам'ятовуємо
        if (!restoringSelection && name != null && !name.equals(launcher.getConfig().getLastInstance())) {
            launcher.getConfig().setLastInstance(name);
            com.skcraft.launcher.persistence.Persistence.commitAndForget(launcher.getConfig());
        }
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
