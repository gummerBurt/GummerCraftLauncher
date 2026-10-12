/*
 * Copyright (C) 2021-2025 GummerCraft <http://gummercraft.fun>
 * Please see LICENSE.txt for license information.
 */

package com.gummer.launcher.dialog;

import com.skcraft.concurrency.ObservableFuture;
import com.skcraft.launcher.Instance;
import com.skcraft.launcher.InstanceList;
import com.skcraft.launcher.Launcher;
import com.skcraft.launcher.dialog.ConfigurationDialog;
import com.skcraft.launcher.dialog.InstanceSettingsDialog;
import com.skcraft.launcher.dialog.ProgressDialog;
import com.skcraft.launcher.launch.LaunchListener;
import com.skcraft.launcher.launch.LaunchOptions;
import com.skcraft.launcher.launch.LaunchOptions.UpdatePolicy;
import com.skcraft.launcher.swing.*;
import com.skcraft.launcher.util.SharedLocale;
import com.skcraft.launcher.util.SwingExecutor;
import lombok.Getter;
import lombok.NonNull;
import lombok.extern.java.Log;
import net.miginfocom.swing.MigLayout;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.Objects;

import com.gummer.launcher.swing.SwingExtras;
import com.gummer.launcher.swing.CustomImageButton;

import static com.skcraft.launcher.util.SharedLocale.tr;

/**
 * The main launcher frame.
 */
@Log
public class LauncherFrameThemed extends JFrame {

    private final Launcher launcher;

    @Getter
    private final InstanceTable instancesTable = new InstanceTable();
    private final InstanceTableModel instancesModel;
    @Getter
    private final JScrollPane instanceScroll = new JScrollPane(instancesTable);
    private WebpagePanel webView;
    private JSplitPane splitPane;
    private final JButton launchButton = new CustomImageButton(ImageIO.read(Objects.requireNonNull(Launcher.class.getResourceAsStream("theme/btn/buttonSquarePlay.png"))),104, 104, SharedLocale.tr("launcher.launch"));
    private final JButton refreshButton = new CustomImageButton(ImageIO.read(Objects.requireNonNull(Launcher.class.getResourceAsStream("theme/btn/buttonSquareReload.png"))), 50, 50, SharedLocale.tr("launcher.checkForUpdates"));
    private final JButton discord = new CustomImageButton(ImageIO.read(Objects.requireNonNull(Launcher.class.getResourceAsStream("theme/btn/buttonSquareDiscord.png"))), 50, 50, SharedLocale.tr("launcher.discord"));
    private final JButton telegram = new CustomImageButton(ImageIO.read(Objects.requireNonNull(Launcher.class.getResourceAsStream("theme/btn/buttonSquareTelegram.png"))), 50, 50, SharedLocale.tr("launcher.telegram"));
    private final JButton website = new CustomImageButton(ImageIO.read(Objects.requireNonNull(Launcher.class.getResourceAsStream("theme/btn/buttonSquareWeb.png"))), 50, 50, SharedLocale.tr("launcher.site"));
    private final JButton wiki = new CustomImageButton(ImageIO.read(Objects.requireNonNull(Launcher.class.getResourceAsStream("theme/btn/buttonSquareInfo.png"))), 50, 50, SharedLocale.tr("launcher.wiki"));
    private final JButton optionsButton = new CustomImageButton(ImageIO.read(Objects.requireNonNull(Launcher.class.getResourceAsStream("theme/btn/buttonSquareSettings2.png"))), 50, 50, SharedLocale.tr("launcher.options"));
    private final JButton selfUpdateButton = new CustomImageButton(ImageIO.read(Objects.requireNonNull(Launcher.class.getResourceAsStream("theme/btn/buttonUpdate.png"))), 313, 50, SharedLocale.tr("launcher.updateLauncher"));
    private final JCheckBox updateCheck = new JCheckBox(SharedLocale.tr("launcher.downloadUpdates"));

    /**
     * Create a new frame.
     *
     * @param launcher the launcher
     */
    public LauncherFrameThemed(@NonNull Launcher launcher) throws IOException {
        super(tr("launcher.title", launcher.getVersion()));

        this.launcher = launcher;
        instancesModel = new InstanceTableModel(launcher.getInstances());

        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(1194, 718));
        setResizable(false);
        initComponents();
        pack();
        setLocationRelativeTo(null);

        SwingHelper.setFrameIcon(this, Launcher.class, "icon.png");

        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                loadInstances();
            }
        });
    }

    private void initComponents() {
        JPanel container = createContainerPanel();
        container.setLayout(new MigLayout("fill, insets dialog", "[][][][][]push[][]", "[grow][]"));

        webView = createNewsPanel();
        splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, instanceScroll, webView);
        selfUpdateButton.setVisible(launcher.getUpdateManager().getPendingUpdate());
        updateCheck.setVisible(false);

        launcher.getUpdateManager().addPropertyChangeListener(new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent evt) {
                if (evt.getPropertyName().equals("pendingUpdate")) {
                    selfUpdateButton.setVisible((Boolean) evt.getNewValue());

                }
            }
        });

        updateCheck.setSelected(true);
        instancesTable.setModel(instancesModel);
        launchButton.setFont(launchButton.getFont().deriveFont(Font.BOLD));
        splitPane.setDividerLocation(200);
        splitPane.setDividerSize(0);
        splitPane.setOpaque(false);
        container.add(splitPane, "grow, wrap, span 5, gapbottom unrel, w null:680, h null:350");
        SwingHelper.flattenJSplitPane(splitPane);
        container.add(refreshButton, "bottom");
        container.add(discord, "bottom");
        container.add(telegram, "bottom");
        container.add(website, "bottom");
        container.add(wiki, "bottom");
        container.add(selfUpdateButton,"bottom");
        container.add(updateCheck,"bottom");
        container.add(optionsButton, "bottom");
        container.add(launchButton);

        add(container, BorderLayout.CENTER);

        // social links
        discord.addActionListener(ActionListeners.openURL(this, launcher.getConfig().getDiscordUrl()));
        telegram.addActionListener(ActionListeners.openURL(this, launcher.getConfig().getTelegramUrl()));
        website.addActionListener(ActionListeners.openURL(this, launcher.getConfig().getSiteUrl()));
        wiki.addActionListener(ActionListeners.openURL(this, launcher.getConfig().getWikiUrl()));

        instancesModel.addTableModelListener(new TableModelListener() {
            @Override
            public void tableChanged(TableModelEvent e) {
                if (instancesTable.getRowCount() > 0) {
                    instancesTable.setRowSelectionInterval(0, 0);
                }
            }
        });

        instancesTable.addMouseListener(new DoubleClickToButtonAdapter(launchButton));

        refreshButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                loadInstances();
                launcher.getUpdateManager().checkForUpdate(LauncherFrameThemed.this);
                webView.browse(launcher.getNewsURL(), false);
            }
        });

        selfUpdateButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                launcher.getUpdateManager().performUpdate(LauncherFrameThemed.this);
            }
        });

        optionsButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                showOptions();
            }
        });

        launchButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                launch();
            }
        });

        instancesTable.addMouseListener(new PopupMouseAdapter() {
            @Override
            protected void showPopup(MouseEvent e) {
                int index = instancesTable.rowAtPoint(e.getPoint());
                Instance selected = null;
                if (index >= 0) {
                    instancesTable.setRowSelectionInterval(index, index);
                    selected = launcher.getInstances().get(index);
                }
                popupInstanceMenu(e.getComponent(), e.getX(), e.getY(), selected);
            }
        });
    }

    protected JPanel createContainerPanel() {
        return new JPanel();
    }

    /**
     * Return the news panel.
     *
     * @return the news panel
     */
    protected WebpagePanel createNewsPanel() {
        return WebpagePanel.forURL(launcher.getNewsURL(), false);
    }

    /**
     * Popup the menu for the instances.
     *
     * @param component the component
     * @param x mouse X
     * @param y mouse Y
     * @param selected the selected instance, possibly null
     */
    private void popupInstanceMenu(Component component, int x, int y, final Instance selected) {
        JPopupMenu popup = new JPopupMenu();
        JMenuItem menuItem;

        final ImageIcon installIcon;
        final ImageIcon launchIcon;
        final ImageIcon openFolderIcon;
        final ImageIcon openResourcePacksIcon;
        final ImageIcon openScreenshotsIcon;
        final ImageIcon deleteFilesIcon;
        final ImageIcon hardForceUpdateIcon;
        final ImageIcon forceUpdateIcon;
        final ImageIcon openSettingsIcon;

        if (selected != null) {
            //menuItem = new JMenuItem(!selected.isLocal() ? tr("instance.install") : tr("instance.launch"));
            installIcon = new ImageIcon(Objects.requireNonNull(SwingExtras.readIconImage(Launcher.class, "theme/menu/installIcon.png")));
            launchIcon = new ImageIcon(Objects.requireNonNull(SwingExtras.readIconImage(Launcher.class, "theme/menu/launchIcon.png")));
            menuItem = new JMenuItem(!selected.isLocal() ? tr("instance.install") : tr("instance.launch"), !selected.isLocal() ? installIcon : launchIcon);
            menuItem.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    launch();
                }
            });
            popup.add(menuItem);

            if (selected.isLocal()) {
                popup.addSeparator();

                //menuItem = new JMenuItem(SharedLocale.tr("instance.openFolder"));
                openFolderIcon = new ImageIcon(Objects.requireNonNull(SwingExtras.readIconImage(Launcher.class, "theme/menu/openFolderIcon.png")));
                menuItem = new JMenuItem(SharedLocale.tr("instance.openFolder"), openFolderIcon);
                menuItem.addActionListener(ActionListeners.browseDir(
                        LauncherFrameThemed.this, selected.getContentDir(), true));
                popup.add(menuItem);

                //menuItem = new JMenuItem(SharedLocale.tr("instance.openResourcePacks"));
                openResourcePacksIcon = new ImageIcon(Objects.requireNonNull(SwingExtras.readIconImage(Launcher.class, "theme/menu/openResourcePacksIcon.png")));
                menuItem = new JMenuItem(SharedLocale.tr("instance.openResourcePacks"), openResourcePacksIcon);
                menuItem.addActionListener(ActionListeners.browseDir(
                        LauncherFrameThemed.this, new File(selected.getContentDir(), "resourcepacks"), true));
                popup.add(menuItem);

                //menuItem = new JMenuItem(SharedLocale.tr("instance.openScreenshots"));
                openScreenshotsIcon = new ImageIcon(Objects.requireNonNull(SwingExtras.readIconImage(Launcher.class, "theme/menu/openScreenshotsIcon.png")));
                menuItem = new JMenuItem(SharedLocale.tr("instance.openScreenshots"), openScreenshotsIcon);
                menuItem.addActionListener(ActionListeners.browseDir(
                        LauncherFrameThemed.this, new File(selected.getContentDir(), "screenshots"), true));
                popup.add(menuItem);

                openSettingsIcon = new ImageIcon(Objects.requireNonNull(SwingExtras.readIconImage(Launcher.class, "theme/menu/adjustments-horizontal.png")));
                menuItem = new JMenuItem(SharedLocale.tr("instance.openSettings"), openSettingsIcon);
                menuItem.addActionListener(e -> {
                    InstanceSettingsDialog.open(this, selected);
                });
                popup.add(menuItem);

                popup.addSeparator();

                forceUpdateIcon = new ImageIcon(Objects.requireNonNull(SwingExtras.readIconImage(Launcher.class, "theme/menu/forceUpdateIcon.png")));
                if (!selected.isUpdatePending()) {
                    //menuItem = new JMenuItem(SharedLocale.tr("instance.forceUpdate"));
                    menuItem = new JMenuItem(SharedLocale.tr("instance.forceUpdate"), forceUpdateIcon);
                    menuItem.addActionListener(new ActionListener() {
                        @Override
                        public void actionPerformed(ActionEvent e) {
                            selected.setUpdatePending(true);
                            launch();
                            instancesModel.update();
                        }
                    });
                    popup.add(menuItem);
                }

                //menuItem = new JMenuItem(SharedLocale.tr("instance.hardForceUpdate"));
                hardForceUpdateIcon = new ImageIcon(Objects.requireNonNull(SwingExtras.readIconImage(Launcher.class, "theme/menu/hardForceUpdateIcon.png")));
                menuItem = new JMenuItem(SharedLocale.tr("instance.hardForceUpdate"), hardForceUpdateIcon);
                menuItem.addActionListener(new ActionListener() {
                    @Override
                    public void actionPerformed(ActionEvent e) {
                        confirmHardUpdate(selected);
                    }
                });
                popup.add(menuItem);

                //menuItem = new JMenuItem(SharedLocale.tr("instance.deleteFiles"));
                deleteFilesIcon = new ImageIcon(Objects.requireNonNull(SwingExtras.readIconImage(Launcher.class, "theme/menu/deleteFilesIcon.png")));
                menuItem = new JMenuItem(SharedLocale.tr("instance.deleteFiles"), deleteFilesIcon);
                menuItem.addActionListener(new ActionListener() {
                    @Override
                    public void actionPerformed(ActionEvent e) {
                        confirmDelete(selected);
                    }
                });
                popup.add(menuItem);
            }

            //popup.addSeparator();
        }

        /*
        menuItem = new JMenuItem(SharedLocale.tr("launcher.refreshList"));
        menuItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                loadInstances();
            }
        });
        popup.add(menuItem);
        */

        popup.show(component, x, y);

    }

    private void confirmDelete(Instance instance) {
        if (!SwingHelper.confirmDialog(this,
                tr("instance.confirmDelete", instance.getTitle()), SharedLocale.tr("confirmTitle"))) {
            return;
        }

        ObservableFuture<Instance> future = launcher.getInstanceTasks().delete(this, instance);


        // Update the list of instances after updating
        future.addListener(new Runnable() {
            @Override
            public void run() {
                loadInstances();
            }
        }, SwingExecutor.INSTANCE);
    }

    private void confirmHardUpdate(Instance instance) {
        if (!SwingHelper.confirmDialog(this, SharedLocale.tr("instance.confirmHardUpdate"), SharedLocale.tr("confirmTitle"))) {
            return;
        }

        ObservableFuture<Instance> future = launcher.getInstanceTasks().hardUpdate(this, instance);


        // Update the list of instances after updating
        future.addListener(new Runnable() {
            @Override
            public void run() {
                launch();
                instancesModel.update();
            }
        }, SwingExecutor.INSTANCE);
    }

    private void loadInstances() {
        ObservableFuture<InstanceList> future = launcher.getInstanceTasks().reloadInstances(this);

        future.addListener(new Runnable() {
            @Override
            public void run() {
                instancesModel.update();
                if (instancesTable.getRowCount() > 0) {
                    instancesTable.setRowSelectionInterval(0, 0);
                }
                requestFocus();
            }
        }, SwingExecutor.INSTANCE);

        ProgressDialog.showProgress(this, future, SharedLocale.tr("launcher.checkingTitle"), SharedLocale.tr("launcher.checkingStatus"));
        SwingHelper.addErrorDialogCallback(this, future);
    }

    private void showOptions() {
        ConfigurationDialog configDialog = new ConfigurationDialog(this, launcher);
        configDialog.setVisible(true);
    }

    private void launch() {
        boolean permitUpdate = updateCheck.isSelected();
        Instance instance = launcher.getInstances().get(instancesTable.getSelectedRow());

        LaunchOptions options = new LaunchOptions.Builder()
                .setInstance(instance)
                .setListener(new LaunchListenerImpl(this))
                .setUpdatePolicy(permitUpdate ? UpdatePolicy.UPDATE_IF_SESSION_ONLINE : UpdatePolicy.NO_UPDATE)
                .setWindow(this)
                .build();
        launcher.getLaunchSupervisor().launch(options);
    }

    private static class LaunchListenerImpl implements LaunchListener {
        private final WeakReference<LauncherFrameThemed> frameRef;
        private final Launcher launcher;

        //status callback
        private final Instance instance;

        private LaunchListenerImpl(LauncherFrameThemed frame) {
            this.frameRef = new WeakReference<LauncherFrameThemed>(frame);
            this.launcher = frame.launcher;

            //status callback
            this.instance = frame.launcher.getInstances().get(frame.instancesTable.getSelectedRow());
        }

        @Override
        public void instancesUpdated() {
            LauncherFrameThemed frame = frameRef.get();
            if (frame != null) {
                frame.instancesModel.update();
            }

            //status callback
        }

        @Override
        public void gameStarted() {
            LauncherFrameThemed frame = frameRef.get();
            if (frame != null) {
                frame.dispose();
            }

            //status callback
        }

        @Override
        public void gameClosed() {
            LauncherFrameThemed frame = frameRef.get();
            if (frame != null) {
                frame.setVisible(true);
                return;
            }
            launcher.showLauncherWindow();
        }
    }

}
