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
        // 17.11.2026 (власник: «треба щоб лаунчер пам'ятав про повноекранку»): launcher-ui.json, windowMaximized
        com.gummer.launcher.look.Zoom.rememberMaximized(this);

        SwingHelper.removeOpaqueness(getInstancesTable());
        SwingHelper.removeOpaqueness(getInstanceScroll());
        getInstanceScroll().setBorder(BorderFactory.createEmptyBorder());

        // дизайн 1.35: редактор розкладки (код ↑↑↓↓←→←→BA), правки в <--dir>/launcher/frame-edit
        if (backgroundPanel != null) {
            LayoutEditor.install(this, backgroundPanel, new java.io.File(launcher.getBaseDir(), "launcher/frame-edit"));
        }

        // одразу вигляд останньої вибраної збірки (ще до завантаження списку збірок) - без "стандартний, а потім перемикання"
        // копія збірки (copy-...): вигляд оригіналу з її instance.json (17.11.2026)
        String last = com.gummer.launcher.manage.InstanceCopies.kindNameOf(launcher.getInstancesDir(), launcher.getConfig().getLastInstance());
        if (backgroundPanel != null && last != null) {
            String look = last.startsWith(com.gummer.launcher.vanilla.VanillaVersions.GROUP + "-") ? com.gummer.launcher.vanilla.VanillaVersions.GROUP
                    : last.startsWith(com.gummer.launcher.fabric.FabricVersions.PREFIX) ? com.gummer.launcher.fabric.FabricVersions.GROUP
                    : last.startsWith(com.gummer.launcher.quilt.QuiltVersions.PREFIX) ? com.gummer.launcher.quilt.QuiltVersions.GROUP
                    : last.startsWith(com.gummer.launcher.modrinth.MrpackPacks.PREFIX) ? com.gummer.launcher.vanilla.VanillaVersions.GROUP
                    : com.gummer.launcher.forge.ForgeVersions.kindOfName(last) != null ? com.gummer.launcher.forge.ForgeVersions.kindOfName(last).group : last;
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

    @Override
    protected void sidebarWidthChanged(int width) {
        if (backgroundPanel != null) backgroundPanel.setSidebarWidth(width);
    }

    @Override
    protected void packArtChanged() {
        updatePackBackground();
    }

    private void updatePackBackground() {
        if (backgroundPanel == null) return;
        com.skcraft.launcher.Instance selected = getSelectedInstance();
        if (selected == null) return; // нічого не вибрано (список ще вантажиться) - лишаємо поточний вигляд
        // 17.11.2026: рядок Modrinth (сторінка модпаків) не збірка: свій фон bg_modrinth.jpg без лого збірки
        // (раніше лишався фон і лого попередньої збірки, напр. StalDayZ), вибір не запам'ятовується
        if (com.skcraft.launcher.swing.InstanceTableModel.isBrowser(selected)) {
            if (!restoringSelection) com.gummer.launcher.manage.ListMemory.remember(launcher.getConfig(), selected); // 17.11.2026: Modrinth теж пам'ять «Усі»
            backgroundPanel.showPack(FlatBackgroundPanel.BROWSER_LOOK, !firstSelection, null);
            firstSelection = false;
            return;
        }
        String name = selected.getName();
        // усі ванільні версії (vanilla-1.21.1, vanilla-26.3, ...) мають одне оформлення: bg_vanilla.jpg і logo_vanilla.png
        // 1.33: Fabric має своє оформлення (bg_fabric.jpg, logo_fabric.png, власник 13.11)
        // Forge і NeoForge мають своє оформлення (bg_forge.jpg, logo_forge.png, logo_neoforge.png, власник 13.11)
        String look = selected.isVanilla() ? com.gummer.launcher.vanilla.VanillaVersions.GROUP
                : selected.isFabric() ? com.gummer.launcher.fabric.FabricVersions.GROUP
                : selected.isQuilt() ? com.gummer.launcher.quilt.QuiltVersions.GROUP
                : selected.isMrpack() ? com.gummer.launcher.manage.InstanceCopies.sectionOf(selected) // 17.11.2026: оформлення свого завантажувача
                : selected.isForgeLike() ? com.gummer.launcher.forge.ForgeVersions.kindOf(selected).group : name;
        // 17.11.2026 ~23:35 (власник: «Усі хибно підтягує картинки з модпаків ... цим бекграундам місце у встановлених»):
        // у «Усі» рядок розділу показує оформлення свого завантажувача, навіть якщо у виборі версії модпак; свій фон лише у «Встановлених»
        backgroundPanel.showPack(look, !firstSelection, launcher.getConfig().isInstalledOnly() ? com.gummer.launcher.manage.InstanceArt.background(selected) : null);
        showPackButtons(look != null ? look.toLowerCase(java.util.Locale.ROOT) : null);
        firstSelection = false;

        // запам'ятати вибір гравця (config.json) - наступного разу лаунчер одразу відкриється з цією збіркою;
        // вибір, який лаунчер відновлює сам (restoringSelection), не запам'ятовуємо
        // 17.11.2026: окремо для «Усі» і «Встановлені» (ListMemory), плюс lastInstance для старту
        if (!restoringSelection) com.gummer.launcher.manage.ListMemory.remember(launcher.getConfig(), selected);
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
