/*
 * Copyright (C) 2021-2025 GummerCraft <http://gummercraft.fun>
 */

package com.gummer.launcher.dialog;

import com.formdev.flatlaf.util.SystemInfo;
import com.gummer.launcher.i18n.Lang;
import com.gummer.launcher.popup.Notification;
import com.gummer.launcher.swing.CustomImageButton;
import com.gummer.launcher.swing.SwingExtras;
import com.skcraft.concurrency.ObservableFuture;
import com.skcraft.launcher.Instance;
import com.skcraft.launcher.InstanceList;
import com.skcraft.launcher.Launcher;
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
import javax.swing.border.EmptyBorder;
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

import static com.gummer.launcher.popup.ShowNotification.showNotification;
import static com.skcraft.launcher.util.SharedLocale.tr;

/**
 * The main launcher frame Theme override
 */
@Log
public class LauncherFrameFlat extends JFrame {

    public final Launcher launcher;
    private final Lang lang;
    private final String langCode;
    public static LauncherFrameFlat rootFrame;

    @Getter
    private final InstanceTable instancesTable = new InstanceTable();
    private final InstanceTableModel instancesModel;
    @Getter
    private final JScrollPane instanceScroll = new JScrollPane(instancesTable);

    private WebpagePanel webView;
    private final JCheckBox updateCheck = new JCheckBox(SharedLocale.tr("launcher.downloadUpdates"));

    // init sidebar buttons
    private final JButton telegramButton;
    private final JButton discordButton;
    private final JButton selfUpdateButton;

    // init bottom buttons
    private final JButton optionsButton;
    private final JButton wikiButton;
    private final JButton launchButton;
    //private final JButton refreshButton;  // disable for now refreshBurron
    //private JButton accountButton; // disable for now accountButton

    // load popup icons
    private final static ImageIcon downloadIcon = new ImageIcon(Objects.requireNonNull(SwingExtras.readIconImage(Launcher.class, "ui/icon/light/download.png")));
    private final static ImageIcon gamepadIcon = new ImageIcon(Objects.requireNonNull(SwingExtras.readIconImage(Launcher.class, "ui/icon/light/play.png")));
    private final static ImageIcon refreshIcon = new ImageIcon(Objects.requireNonNull(SwingExtras.readIconImage(Launcher.class, "ui/icon/light/refresh.png")));
    private final static ImageIcon folderIcon = new ImageIcon(Objects.requireNonNull(SwingExtras.readIconImage(Launcher.class, "ui/icon/light/folder.png")));
    private final static ImageIcon javaIcon = new ImageIcon(Objects.requireNonNull(SwingExtras.readIconImage(Launcher.class, "ui/icon/light/settings.png")));
    private final static ImageIcon widgetIcon = new ImageIcon(Objects.requireNonNull(SwingExtras.readIconImage(Launcher.class, "ui/icon/light/widget.png")));
    private final static ImageIcon imageIcon = new ImageIcon(Objects.requireNonNull(SwingExtras.readIconImage(Launcher.class, "ui/icon/light/image.png")));
    private final static ImageIcon crashIcon = new ImageIcon(Objects.requireNonNull(SwingExtras.readIconImage(Launcher.class, "ui/icon/light/crash.png")));
    private final static ImageIcon reinstallIcon = new ImageIcon(Objects.requireNonNull(SwingExtras.readIconImage(Launcher.class, "ui/icon/light/reinstall.png")));
    private final static ImageIcon trashIcon = new ImageIcon(Objects.requireNonNull(SwingExtras.readIconImage(Launcher.class, "ui/icon/light/trash.png")));

    /**
     * Create a new frame.
     *
     * @param launcher the launcher
     */
    public LauncherFrameFlat(@NonNull Launcher launcher) throws IOException {
        super(tr("launcher.title", launcher.getVersion()));

        this.launcher = launcher;
        instancesModel = new InstanceTableModel(launcher.getInstances());
        rootFrame = this;

        // Initialize lang from this.launcher config
        this.lang = Lang.getFromName(launcher.getConfig().getLang());
        this.langCode = lang.getId(); // get langCode

        //
        // load multilang UI resources
        //

        // sidebar buttons
        telegramButton = new CustomImageButton(
            ImageIO.read(Objects.requireNonNull(
                Launcher.class.getResourceAsStream("ui/button/telegram_" + langCode + ".png")
            )),
            200,45,
            SharedLocale.tr("tooltip.telegram")
        );
        discordButton = new CustomImageButton(
            ImageIO.read(Objects.requireNonNull(
                Launcher.class.getResourceAsStream("ui/button/discord_" + langCode + ".png")
            )),
            200,45,
            SharedLocale.tr("tooltip.discord")
        );
        selfUpdateButton = new CustomImageButton(
            ImageIO.read(Objects.requireNonNull(
                Launcher.class.getResourceAsStream("ui/button/update_" + langCode + ".png")
            )),
            200,45,
            SharedLocale.tr("tooltip.update")
        );

        // bottom buttons
        // disable for now accountButton
        //accountButton = new CustomImageButton(
        //    ImageIO.read(Objects.requireNonNull(
        //        Launcher.class.getResourceAsStream("ui/button/account_" + langCode + ".png")
        //    )),
        //    101,101,
        //    SharedLocale.tr("tooltip.settings")
        //);
        optionsButton = new CustomImageButton(
            ImageIO.read(Objects.requireNonNull(
                Launcher.class.getResourceAsStream("ui/button/settings_" + langCode + ".png")
            )),
            101,101,
            SharedLocale.tr("tooltip.settings")
        );
        wikiButton = new CustomImageButton(
            ImageIO.read(Objects.requireNonNull(
                Launcher.class.getResourceAsStream("ui/button/faq_" + langCode + ".png")
            )),
            101,101,
            SharedLocale.tr("tooltip.faq")
        );
        launchButton = new CustomImageButton(
            ImageIO.read(Objects.requireNonNull(
                Launcher.class.getResourceAsStream("ui/button/play_" + langCode + ".png")
            )),
            302,101,
            SharedLocale.tr("tooltip.launch")
        );
//        refreshButton = new CustomImageButton(
//            ImageIO.read(Objects.requireNonNull(
//                Launcher.class.getResourceAsStream("ui/button/reload.png")
//            )),
//            50,50,
//            SharedLocale.tr("launcher.checkForUpdates")
//        );

        // configure main windowFrame
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(1194, 725));
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
        // create main container with two column BorderLayout
        JPanel container = createContainerPanel();
        container.setLayout(new BorderLayout());

        // create sidebarPanel with full-height and fixed width/padding
        int sidebarWidth = 220; // fixed width for the sidebar (adjust as needed)
        JPanel sidebarPanel = new JPanel(new BorderLayout());
                        sidebarPanel.setPreferredSize(new Dimension(sidebarWidth, 0));
        sidebarPanel.setMinimumSize(new Dimension(sidebarWidth, 0));
        sidebarPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 5, 8)); // set sidebarPanel padding
        sidebarPanel.setOpaque(false); // transparent

        // configure instanceTable width subtracting horizontal padding
        instanceScroll.setPreferredSize(new Dimension(sidebarWidth - 20, 400)); // height is arbitrary
        instanceScroll.setMaximumSize(new Dimension(sidebarWidth - 20, Integer.MAX_VALUE));
        instanceScroll.setOpaque(false); // transparent

        // create sidebarButtonsPanel with strict width/padding
        JPanel sidebarButtonsPanel = new JPanel();
        sidebarButtonsPanel.setBorder(BorderFactory.createEmptyBorder(10, 0, 2, 0)); // set ButtonsPanel padding
        sidebarButtonsPanel.setMaximumSize(new Dimension(sidebarWidth - 18, Integer.MAX_VALUE)); // set ButtonsPanel width minus padding
        sidebarButtonsPanel.setLayout(new BoxLayout(sidebarButtonsPanel, BoxLayout.Y_AXIS));
        sidebarButtonsPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        sidebarButtonsPanel.setOpaque(false); // transparent
        //sidebarButtonsPanel.add(Box.createVerticalStrut(5)); // add spacing before buttons
        sidebarButtonsPanel.add(telegramButton);
        sidebarButtonsPanel.add(Box.createVerticalStrut(5));
        sidebarButtonsPanel.add(discordButton);
        sidebarButtonsPanel.add(Box.createVerticalStrut(1));
        //sidebarButtonsPanel.add(Box.createVerticalStrut(4));
        sidebarButtonsPanel.add(selfUpdateButton);

        // add elements to sidebarPanel
        sidebarPanel.add(instanceScroll, BorderLayout.CENTER);
        sidebarPanel.add(sidebarButtonsPanel, BorderLayout.SOUTH);

        // create rightPanel
        JPanel rightPanel = new JPanel(new BorderLayout());
        rightPanel.setBorder(BorderFactory.createEmptyBorder(150, 0, 0, 0)); // set rightPanel padding
        rightPanel.setOpaque(false); // transparent

        // create bottom buttons panel with padding
        JPanel rightButtonsPanel = new JPanel(new MigLayout("", "[][]push[]", "")); // productiom!
        //JPanel rightButtonsPanel = new JPanel(new MigLayout("", "[][][][][]push[][]", ""));

        rightButtonsPanel.setOpaque(false);
        rightButtonsPanel.setBorder(BorderFactory.createEmptyBorder(12, 2, 1, 0)); // set ButtonsPanel padding
        //rightButtonsPanel.add(refreshButton); // hide refresh button on new template
        //rightButtonsPanel.add(updateCheck); // hide update checkbox

        rightButtonsPanel.add(optionsButton);
        //rightButtonsPanel.add(accountButton); // disable for now accountButton
        rightButtonsPanel.add(wikiButton);
        rightButtonsPanel.add(launchButton);

        // add elements to rightPanel
        webView = createNewsPanel();
        rightPanel.add(webView, BorderLayout.CENTER);
        rightPanel.add(rightButtonsPanel, BorderLayout.PAGE_END);
        webView.setVisible(false); // hide news panel for now

        // add elements to Main container
        container.add(sidebarPanel, BorderLayout.WEST);
        container.add(rightPanel, BorderLayout.CENTER);

        add(container, BorderLayout.CENTER);

        // 'check' and hide updateCheck checkbox
        updateCheck.setVisible(false);
        updateCheck.setSelected(true);

        // display selfUpdateButton if update available
        selfUpdateButton.setVisible(launcher.getUpdateManager().getPendingUpdate());
        launcher.getUpdateManager().addPropertyChangeListener(new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent evt) {
                if (evt.getPropertyName().equals("pendingUpdate")) {
                    selfUpdateButton.setVisible((Boolean) evt.getNewValue());
                    showNotification(
                        LauncherFrameFlat.rootFrame,
                        LauncherFrameFlat.class,
                        Notification.Type.WARNING,
                        SharedLocale.tr("notification.popup.update")
                    );
                }
            }
        });

        // sidebar buttons events
        discordButton.addActionListener(ActionListeners.openURL(this, launcher.getConfig().getDiscordUrl()));
        telegramButton.addActionListener(ActionListeners.openURL(this, launcher.getConfig().getTelegramUrl()));
        wikiButton.addActionListener(ActionListeners.openURL(this, launcher.getConfig().getWikiUrl()));

        // instancesTable events
        instancesTable.setModel(instancesModel);
        instancesModel.addTableModelListener(new TableModelListener() {
            @Override
            public void tableChanged(TableModelEvent e) {
                if (instancesTable.getRowCount() > 0) {
                    instancesTable.setRowSelectionInterval(0, 0);
                }
            }
        });
        instancesTable.addMouseListener(new DoubleClickToButtonAdapter(launchButton));

//        refreshButton.addActionListener(new ActionListener() {
//            @Override
//            public void actionPerformed(ActionEvent e) {
//                loadInstances();
//                launcher.getUpdateManager().checkForUpdate(LauncherFrameFlat.this);
//                webView.browse(launcher.getNewsURL(), false);
//            }
//        });

        selfUpdateButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                launcher.getUpdateManager().performUpdate(LauncherFrameFlat.this);
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

//        // fix osx padding issues
//        if(SystemInfo.isMacOS) {
//            container.setBorder(new EmptyBorder(new Insets(20, 0, 0, 0)));
//        }
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


        if (selected != null) {
            menuItem = new JMenuItem(!selected.isLocal() ? tr("instance.install") : tr("instance.launch"), !selected.isLocal() ? downloadIcon : gamepadIcon);
            menuItem.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    launch();
                }
            });
            popup.add(menuItem);

            if (selected.isLocal()) {
                if (!selected.isUpdatePending()) {
                    // hide 'soft-force-update' option
                    // menuItem = new JMenuItem(SharedLocale.tr("instance.forceUpdate"), forceUpdateIcon);
                    // menuItem.addActionListener(new ActionListener() {
                    //     @Override
                    //     public void actionPerformed(ActionEvent e) {
                    //         selected.setUpdatePending(true);
                    //         launch();
                    //         instancesModel.update();
                    //     }
                    // });
                    menuItem = new JMenuItem(SharedLocale.tr("instance.forceUpdate"), refreshIcon);
                    menuItem.addActionListener(new ActionListener() {
                        @Override
                        public void actionPerformed(ActionEvent e) {
                            loadInstances();
                        }
                    });
                    popup.add(menuItem);
                }

                menuItem = new JMenuItem(SharedLocale.tr("instance.openSettings"), javaIcon);
                menuItem.addActionListener(e -> {
                    InstanceSettingsDialogFlat.open(this, selected);
                });
                popup.add(menuItem);

                popup.addSeparator();

                menuItem = new JMenuItem(SharedLocale.tr("instance.openFolder"), folderIcon);
                menuItem.addActionListener(ActionListeners.browseDir(
                        LauncherFrameFlat.this, selected.getContentDir(), true));
                popup.add(menuItem);

                menuItem = new JMenuItem(SharedLocale.tr("instance.openResourcePacks"), widgetIcon);
                menuItem.addActionListener(ActionListeners.browseDir(
                        LauncherFrameFlat.this, new File(selected.getContentDir(), "resourcepacks"), true));
                popup.add(menuItem);

                menuItem = new JMenuItem(SharedLocale.tr("instance.openScreenshots"), imageIcon);
                menuItem.addActionListener(ActionListeners.browseDir(
                        LauncherFrameFlat.this, new File(selected.getContentDir(), "screenshots"), true));
                popup.add(menuItem);

                menuItem = new JMenuItem(SharedLocale.tr("instance.openCrashReports"), crashIcon);
                menuItem.addActionListener(ActionListeners.browseDir(
                    LauncherFrameFlat.this, new File(selected.getContentDir(), "crash-reports"), true));
                popup.add(menuItem);

                popup.addSeparator();

                menuItem = new JMenuItem(SharedLocale.tr("instance.hardForceUpdate"), reinstallIcon);
                menuItem.addActionListener(new ActionListener() {
                    @Override
                    public void actionPerformed(ActionEvent e) {
                        confirmHardUpdate(selected);
                    }
                });
                popup.add(menuItem);

                menuItem = new JMenuItem(SharedLocale.tr("instance.deleteFiles"), trashIcon);
                menuItem.addActionListener(new ActionListener() {
                    @Override
                    public void actionPerformed(ActionEvent e) {
                        confirmDelete(selected);
                    }
                });
                popup.add(menuItem);
            }

        }

        popup.show(component, x, y);
    }

    private void confirmDelete(Instance instance) {
        if (!SwingHelper.confirmDialog(this,
                tr("instance.confirmDelete", instance.getTitle()), SharedLocale.tr("confirmTitle"))) {
            return;
        }

        ObservableFuture<Instance> future = launcher.getInstanceTasks().delete(this, instance);

        // update the list of instances after updating
        future.addListener(new Runnable() {
            @Override
            public void run() {
                loadInstances();
                showNotification(
                    LauncherFrameFlat.rootFrame,
                    LauncherFrameFlat.class,
                    Notification.Type.SUCCESS,
                    SharedLocale.tr("notification.popup.modpack.deleted")
                );
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
        ConfigurationDialogFlat configDialog = new ConfigurationDialogFlat(this, launcher, LauncherFrameFlat.rootFrame);
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
        private final WeakReference<LauncherFrameFlat> frameRef;
        private final Launcher launcher;
        private final Instance instance; //status callback

        private LaunchListenerImpl(LauncherFrameFlat frame) {
            this.frameRef = new WeakReference<LauncherFrameFlat>(frame);
            this.launcher = frame.launcher;
            this.instance = frame.launcher.getInstances().get(frame.instancesTable.getSelectedRow()); //status callback
        }

        @Override
        public void instancesUpdated() {
            LauncherFrameFlat frame = frameRef.get();
            if (frame != null) {
                frame.instancesModel.update();
            }
        }

        @Override
        public void gameStarted() {
            LauncherFrameFlat frame = frameRef.get();
            if (frame != null) {
                frame.dispose();
            }
        }

        @Override
        public void gameClosed() {
            LauncherFrameFlat frame = frameRef.get();
            if (frame != null) {
                frame.setVisible(true);
                return;
            }
            launcher.showLauncherWindow();
        }
    }

}
