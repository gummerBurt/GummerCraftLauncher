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
    /** 1.32.2: жовта кнопка кави (Buy Me a Coffee; з 1.32.5 напис «Підтримати»), null, якщо coffeeUrl порожній. */
    private final JButton coffeeButton;
    private final JButton selfUpdateButton;

    // init bottom buttons
    private final JButton optionsButton;
    private final JButton wikiButton;
    /** 1.32: звернення в підтримку (null, якщо в launcher.properties немає supportInboxId). */
    private final com.gummer.launcher.support.SupportService supportService;
    private JButton supportButton;
    private final JButton launchButton;
    /** 1.32: вибір версії для групи паків (наприклад Main), видимий лише коли у вибраної групи кілька версій. */
    private final JComboBox<Instance> versionBox = new JComboBox<Instance>();
    /** Блок «Версія Minecraft» висотою з кнопку «Грати»: темна напівпрозора підкладка, щоб читалось на будь-якому фоні збірки. */
    private final JPanel versionPanel = new JPanel(new MigLayout("insets 10 16 12 16, wrap 1, gap 0 6", "[260!]", "[][]")) {
        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(new Color(0, 0, 0, 150));
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), 18, 18);
            g2.dispose();
            super.paintComponent(g);
        }
    };
    private boolean updatingVersionBox = false;

    // 1.32: вкладки GummerCraft (наші паки, реклама серверів) і Vanilla (чисті версії Mojang)
    /** Підпис над вибором версії: свій для групи GummerCraft, Vanilla і решти (з 1.32). */
    private final JLabel versionLabel = new JLabel(tr("launcher.versionLabel"));
    /** Картки новин і реклами над кнопками (news.json з CDN). */
    private final JPanel newsRow = new JPanel(new MigLayout("insets 0 12 8 12, gap 12", "", "[]"));
    private java.util.List<com.gummer.launcher.news.NewsFeed.Item> news = java.util.Collections.emptyList();
    /** Онлайн серверів (адреса -> стан), оновлюється раз на хвилину. */
    private final java.util.Map<String, com.gummer.launcher.server.ServerPing.Status> online = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.Set<String> offlineServers = java.util.Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<>());
    private final JLabel onlineLabel = new JLabel(" ");
    private final JCheckBox quickJoinBox = new JCheckBox(tr("launcher.quickJoin"));
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
        instancesModel = new InstanceTableModel(launcher.getInstances(), () -> launcher.getConfig().getLastInstance());
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
        String coffeeUrl = launcher.getProperties().getProperty("coffeeUrl", "").trim();
        java.io.InputStream coffeeImage = coffeeUrl.isEmpty() ? null : Launcher.class.getResourceAsStream("ui/button/coffee_" + langCode + ".png");
        if (coffeeImage == null && !coffeeUrl.isEmpty()) coffeeImage = Launcher.class.getResourceAsStream("ui/button/coffee_en_us.png");
        coffeeButton = coffeeImage == null ? null : new CustomImageButton(ImageIO.read(coffeeImage), 200, 45, SharedLocale.tr("tooltip.coffee"));
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
        supportService = com.gummer.launcher.support.SupportService.create(launcher);
        if (supportService != null) {
            supportButton = new CustomImageButton(
                ImageIO.read(Objects.requireNonNull(
                    Launcher.class.getResourceAsStream("ui/button/support_" + langCode + ".png")
                )),
                101,101,
                SharedLocale.tr("tooltip.support")
            );
        }
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

        com.gummer.launcher.prefs.LauncherPrefs.init(launcher.getBaseDir());
        registerUiHooks();

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
        if (coffeeButton != null) {
            sidebarButtonsPanel.add(Box.createVerticalStrut(5));
            sidebarButtonsPanel.add(coffeeButton);
        }
        JComponent sourceLink = createSourceLink();
        if (sourceLink != null) {
            sidebarButtonsPanel.add(Box.createVerticalStrut(4));
            sidebarButtonsPanel.add(sourceLink);
        }
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
        JPanel rightButtonsPanel = new JPanel(new MigLayout("", "[][][]push[][]", "")); // productiom! (1.32: + «Підтримка», вибір версії перед «Грати»)
        //JPanel rightButtonsPanel = new JPanel(new MigLayout("", "[][][][][]push[][]", ""));

        rightButtonsPanel.setOpaque(false);
        rightButtonsPanel.setBorder(BorderFactory.createEmptyBorder(12, 2, 1, 0)); // set ButtonsPanel padding
        //rightButtonsPanel.add(refreshButton); // hide refresh button on new template
        //rightButtonsPanel.add(updateCheck); // hide update checkbox

        rightButtonsPanel.add(optionsButton);
        //rightButtonsPanel.add(accountButton); // disable for now accountButton
        rightButtonsPanel.add(wikiButton);
        if (supportButton != null) {
            rightButtonsPanel.add(supportButton);
            supportButton.addActionListener(e -> com.gummer.launcher.dialog.SupportDialog.showFor(this, supportService, this::getSelectedInstance));
            checkSupportReplies();
        }
        setupVersionBox();
        rightButtonsPanel.add(versionPanel, "aligny center, hidemode 3, gapright 8");
        rightButtonsPanel.add(launchButton);

        // add elements to rightPanel
        // 1.32: замість прихованої веб-панелі новин (у Java 8 немає браузера) картки з news.json над кнопками
        JPanel newsArea = new JPanel(new BorderLayout());
        newsArea.setOpaque(false);
        newsRow.setOpaque(false);
        newsArea.add(newsRow, BorderLayout.SOUTH);
        rightPanel.add(newsArea, BorderLayout.CENTER);
        JPanel bottom = new JPanel(new BorderLayout());
        bottom.setOpaque(false);
        bottom.add(createStatusRow(), BorderLayout.NORTH);
        bottom.add(rightButtonsPanel, BorderLayout.CENTER);
        rightPanel.add(bottom, BorderLayout.PAGE_END);
        JPanel oldExeBanner = createOldExeBanner();
        if (oldExeBanner != null) {
            rightPanel.add(oldExeBanner, BorderLayout.PAGE_START);
        }

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
        // адреси читаються в момент натискання: у збірки можуть бути власні (FlatLauncherFrame.discordUrl/telegramUrl/wikiUrl)
        discordButton.addActionListener(e -> SwingHelper.openURL(discordUrl(), this));
        telegramButton.addActionListener(e -> SwingHelper.openURL(telegramUrl(), this));
        if (coffeeButton != null) {
            coffeeButton.addActionListener(e -> SwingHelper.openURL(launcher.getProperties().getProperty("coffeeUrl").trim(), this));
        }
        wikiButton.addActionListener(e -> SwingHelper.openURL(wikiUrl(), this));

        // instancesTable events
        instancesTable.setModel(instancesModel);
        instancesModel.addTableModelListener(new TableModelListener() {
            @Override
            public void tableChanged(TableModelEvent e) {
                // 1.32: після EDT-черги. Слухачі моделі викликаються у зворотному порядку, тож цей іде раніше за
                // слухача самої JTable, і той на fireTableDataChanged знімав щойно зроблений вибір: не було видно
                // вибору версії, онлайну і «Одразу на сервер». Оновлення рядків (онлайн) вибір не чіпає.
                if (e.getType() == TableModelEvent.UPDATE && e.getFirstRow() != TableModelEvent.HEADER_ROW
                        && e.getLastRow() != Integer.MAX_VALUE) {
                    return;
                }
                SwingUtilities.invokeLater(() -> {
                    if (instancesTable.getRowCount() > 0) {
                        selectRememberedInstance();
                    }
                });
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
                    selected = instancesModel.getInstanceAt(index);
                }
                popupInstanceMenu(e.getComponent(), e.getX(), e.getY(), selected);
            }
        });

//        // fix osx padding issues
//        if(SystemInfo.isMacOS) {
//            container.setBorder(new EmptyBorder(new Insets(20, 0, 0, 0)));
//        }
    }

    /**
     * true, поки лаунчер сам відновлює вибір (selectRememberedInstance). Такий вибір не запам'ятовується як вибір
     * гравця (FlatLauncherFrame.updatePackBackground): інакше, коли список ще неповний (ванільні версії Mojang ще не
     * підвантажились, немає інтернету), вимушений перший рядок перезаписував lastInstance, і гравець Vanilla
     * наступного разу опинявся на StalDayZ (знайдено 13.11.2026).
     */
    protected boolean restoringSelection;

    /** Вибрати в списку останню вибрану збірку (Configuration.lastInstance), якщо вона є в списку, інакше першу. */
    private void selectRememberedInstance() {
        int row = Math.max(0, instancesModel.rowOf(launcher.getConfig().getLastInstance()));
        restoringSelection = true;
        try {
            instancesTable.setRowSelectionInterval(row, row);
        } finally {
            restoringSelection = false;
        }
        refreshVersionBox();
    }

    /**
     * 1.32: непримітне посилання на відкритий код лаунчера під кнопками соцмереж (sourceCodeUrl у launcher.properties;
     * порожнє - посилання немає). Дрібний сірий текст, світлішає під мишкою.
     */
    private JComponent createSourceLink() {
        final String url = launcher.getProperties().getProperty("sourceCodeUrl");
        if (url == null || url.trim().isEmpty()) {
            return null;
        }
        final Color normal = new Color(0x8A8F98);
        final Color hover = new Color(0xD8DCE2);
        final JLabel link = new JLabel(tr("launcher.sourceCode"));
        link.setFont(link.getFont().deriveFont(Font.PLAIN, 11f));
        link.setForeground(normal);
        link.setToolTipText(url.trim());
        link.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        link.setAlignmentX(Component.LEFT_ALIGNMENT);
        link.setBorder(BorderFactory.createEmptyBorder(0, 4, 0, 0));
        link.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                SwingHelper.openURL(url.trim(), LauncherFrameFlat.this);
            }

            @Override
            public void mouseEntered(MouseEvent e) {
                link.setForeground(hover);
            }

            @Override
            public void mouseExited(MouseEvent e) {
                link.setForeground(normal);
            }
        });
        return link;
    }

    /**
     * 1.32: банер «новий лаунчер без Java» для тих, хто запустив лаунчер старим exe (Windows, Java не вбудована,
     * Toolbox.javaKind() = system). Посилання з launcher.properties (newLauncherUrl); порожнє - банера немає.
     * Закривається хрестиком до наступного запуску.
     */
    private JPanel createOldExeBanner() {
        final String url = launcher.getProperties().getProperty("newLauncherUrl");
        if (url == null || url.trim().isEmpty() || !SystemInfo.isWindows
                || "bundled".equals(com.gummer.launcher.shared.Toolbox.javaKind())) {
            return null;
        }
        final JPanel banner = new JPanel(new MigLayout("insets 8 14 8 8, gap 10", "[grow][][]", "[]")) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(0, 0, 0, 170));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 16, 16);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        banner.setOpaque(false);
        JLabel text = new JLabel("<html>" + SwingHelper.htmlEscape(tr("launcher.oldExeBanner")) + "</html>");
        text.setForeground(new Color(235, 235, 235));
        text.setFont(text.getFont().deriveFont(Font.BOLD, 13f));
        JButton get = new JButton(tr("launcher.oldExeBannerButton"));
        get.addActionListener(e -> SwingHelper.openURL(url, LauncherFrameFlat.this));
        JButton close = new JButton("✕");
        close.setToolTipText(tr("launcher.oldExeBannerClose"));
        close.addActionListener(e -> {
            banner.setVisible(false);
            banner.getParent().revalidate();
        });
        banner.add(text, "growx, wmin 0");
        banner.add(get);
        banner.add(close);
        JPanel wrap = new JPanel(new MigLayout("insets 0 12 0 12", "[grow,fill]", "[]"));
        wrap.setOpaque(false);
        wrap.add(banner);
        return wrap;
    }

    /** 1.32: раз при старті, у фоні: чи відповіли адміни на звернення, поки лаунчер був закритий. */
    private void checkSupportReplies() {
        if (!supportService.hasConversation()) return;
        new SwingWorker<Integer, Void>() {
            @Override
            protected Integer doInBackground() throws Exception {
                return supportService.unreadReplies();
            }

            @Override
            protected void done() {
                try {
                    if (get() > 0) {
                        supportButton.setToolTipText(SharedLocale.tr("support.newReplyTooltip"));
                        showNotification(LauncherFrameFlat.rootFrame, LauncherFrameFlat.class, Notification.Type.INFO,
                                SharedLocale.tr("support.newReply"));
                    }
                } catch (Exception e) {
                    log.log(java.util.logging.Level.INFO, "Support: could not check replies", e);
                }
            }
        }.execute();
    }

    /** Пак у вибраному рядку (для групи: вибрана версія), або null. */
    protected Instance getSelectedInstance() {
        int row = instancesTable.getSelectedRow();
        return row >= 0 && row < instancesModel.getRowCount() ? instancesModel.getInstanceAt(row) : null;
    }

    private void setupVersionBox() {
        versionPanel.setOpaque(false);
        versionPanel.setVisible(false);
        versionLabel.setForeground(new Color(230, 230, 230));
        versionLabel.setFont(versionLabel.getFont().deriveFont(Font.BOLD, 13f));
        versionPanel.add(versionLabel);
        versionPanel.add(versionBox, "growx, h 44!");
        versionBox.setFocusable(false);
        versionBox.setFont(versionBox.getFont().deriveFont(Font.BOLD, 18f));
        versionBox.setMaximumRowCount(12);
        versionBox.setToolTipText(tr("launcher.versionTooltip"));
        versionBox.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                Object text = value;
                if (value instanceof Instance) {
                    Instance i = (Instance) value;
                    String version = instancesModel.versionLabelOf(i);
                    // 1.32: стан видно не лише текстом. Встановлено - зелена крапка і білий текст, є оновлення - жовта,
                    // не встановлено - порожнє коло і приглушений текст; найновіша - синя позначка
                    boolean installed = i.isLocal() && i.isInstalled();
                    String dotColor = !installed ? "#6b7079" : i.isUpdatePending() ? "#e0b050" : "#7ccf6a";
                    String dot = "<span style=\"color:" + dotColor + "\">" + (installed ? "●" : "○") + "</span>&nbsp;";
                    if (index < 0) {
                        // закритий список: позначка і версія
                        text = "<html>" + dot + "Minecraft " + SwingHelper.htmlEscape(version) + "</html>";
                    } else {
                        String status = !installed ? tr("launcher.versionNotInstalled")
                                : i.isUpdatePending() ? tr("launcher.versionUpdate") : tr("launcher.versionInstalled");
                        String statusColor = !installed ? "#8a8f98" : dotColor;
                        String newest = index == 0 ? "&nbsp;&nbsp;<span style=\"color:#5aa9ff;font-size:11\">"
                                + SwingHelper.htmlEscape(tr("launcher.versionNewest")) + "</span>" : "";
                        text = "<html>" + dot + "<b" + (installed ? "" : " style=\"color:#b4b8bf\"") + ">Minecraft "
                                + SwingHelper.htmlEscape(version) + "</b>&nbsp;&nbsp;<span style=\"color:" + statusColor + ";font-size:11\">"
                                + SwingHelper.htmlEscape(status) + "</span>" + newest + "</html>";
                    }
                }
                Component c = super.getListCellRendererComponent(list, text, index, isSelected, cellHasFocus);
                if (index >= 0) {
                    // у відкритому списку шрифт менший, ніж у самому полі (18), щоб влазив стан версії
                    c.setFont(c.getFont().deriveFont(Font.BOLD, 14f));
                    ((JComponent) c).setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 10));
                }
                return c;
            }
        });
        installVersionPopupUi();
        versionBox.addActionListener(e -> {
            if (updatingVersionBox) return;
            Instance chosen = (Instance) versionBox.getSelectedItem();
            Instance shown = getSelectedInstance();
            if (chosen == null || chosen == shown) return;
            instancesModel.chooseInGroup(chosen);
            launcher.getConfig().setLastInstance(chosen.getName());
            com.skcraft.launcher.persistence.Persistence.commitAndForget(launcher.getConfig());
            instancesTable.clearSelection(); // щоб вибір рядка спрацював заново (фон, кнопки збірки)
            instancesModel.update();         // tableChanged -> selectRememberedInstance -> рядок групи з новою версією
        });
        instancesTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                refreshVersionBox();
                refreshStatusRow();
            }
        });
    }

    /**
     * 1.32.5: ширина відкритого списку версій під найширший рядок, і так одразу при відкритті. FlatLaf міряє ширину
     * списку за закритим полем (лише «Minecraft X»), тож рядки зі станом і «найновіша» обрізались («найнові»).
     * У 1.32.1 список розширювався вже після показу через {@code setPopupSize}, але той фіксував розмір вікна списку
     * назавжди: наступний список (інша група, інша кількість версій) відкривався з розміром попереднього, порожнім
     * місцем знизу і знову обрізаним текстом, бо сам вміст FlatLaf щоразу стискав до ширини поля, і ще й
     * перевідкривався на очах. Тепер ширину задаємо там, де FlatLaf рахує розмір списку перед показом.
     */
    private void installVersionPopupUi() {
        if (!(versionBox.getUI() instanceof com.formdev.flatlaf.ui.FlatComboBoxUI)) return; // інша тема: як є
        versionBox.setUI(new com.formdev.flatlaf.ui.FlatComboBoxUI() {
            @Override
            protected javax.swing.plaf.basic.ComboPopup createPopup() {
                return new FlatComboPopup(comboBox) {
                    @Override
                    protected Rectangle computePopupBounds(int px, int py, int pw, int ph) {
                        // ширина рядків (рендерер з індексом >= 0: версія, стан, «найновіша») + рамки списку;
                        // смугу прокрутки FlatLaf додає сам
                        int need = list.getPreferredSize().width;
                        for (javax.swing.border.Border b : new javax.swing.border.Border[]{scroller.getViewportBorder(), scroller.getBorder()}) {
                            if (b != null) {
                                Insets in = b.getBorderInsets(scroller);
                                need += in.left + in.right;
                            }
                        }
                        // Java 9+ на екрані з масштабуванням (125%, 150%): поки список не показано, Swing міряє текст
                        // без масштабу, і на екрані рядки виходять ширшими (Java 17, 125%: 278 замість 270), тож запас
                        GraphicsConfiguration gc = comboBox.getGraphicsConfiguration();
                        double scale = gc != null ? gc.getDefaultTransform().getScaleX() : 1;
                        if (Math.abs(scale - 1) > 0.01) need = (int) Math.ceil(need * 1.06) + 2;
                        if (comboBox.getItemCount() > comboBox.getMaximumRowCount()) {
                            need += scroller.getVerticalScrollBar().getPreferredSize().width;
                        }
                        return super.computePopupBounds(px, py, Math.max(pw, need), ph);
                    }
                };
            }
        });
    }

    /** Показати вибір версії, якщо вибраний рядок це група з кількох версій. */
    private void refreshVersionBox() {
        Instance shown = getSelectedInstance();
        java.util.List<Instance> members = instancesModel.getGroupMembers(shown);
        updatingVersionBox = true;
        try {
            versionBox.removeAllItems();
            if (members.size() >= 2) {
                for (Instance m : members) {
                    versionBox.addItem(m);
                }
                versionBox.setSelectedItem(shown);
            }
            versionPanel.setVisible(members.size() >= 2);
            if (members.size() >= 2) {
                String key = shown.isVanilla() ? "launcher.versionLabelVanilla"
                        : "main".equals(shown.getGroup()) ? "launcher.versionLabelGummerCraft" : "launcher.versionLabel";
                versionLabel.setText(tr(key));
                versionLabel.setToolTipText(tr(key + "Tooltip"));
                versionPanel.setToolTipText(tr(key + "Tooltip"));
            }
        } finally {
            updatingVersionBox = false;
        }
        if (versionPanel.getParent() != null) {
            versionPanel.getParent().revalidate();
            versionPanel.getParent().repaint();
        }
    }

    /** Вибрати пак у списку (для групи: цю версію в групі). */
    private void selectPack(Instance target) {
        instancesModel.chooseInGroup(target);
        launcher.getConfig().setLastInstance(target.getName());
        com.skcraft.launcher.persistence.Persistence.commitAndForget(launcher.getConfig());
        instancesTable.clearSelection();
        instancesModel.update(); // tableChanged -> selectRememberedInstance
    }

    /** Текст для ванільної версії («версія»), якщо є, інакше звичайний («модпак»). */
    private static String trFor(Instance instance, String key, Object... args) {
        String k = instance != null && instance.isVanilla() ? key + ".vanilla" : key;
        return args.length == 0 ? tr(k) : tr(k, args);
    }

    // ------------------------------------------------------------------ 1.32: онлайн і «одразу на сервер»

    private JPanel createStatusRow() {
        JPanel row = new JPanel(new MigLayout("insets 0 14 0 14, gap 14", "push[][]", "[]"));
        row.setOpaque(false);
        onlineLabel.setFont(onlineLabel.getFont().deriveFont(Font.BOLD, 13f));
        onlineLabel.setForeground(new Color(0x8C, 0xE0, 0x7A));
        quickJoinBox.setOpaque(false);
        quickJoinBox.setFocusable(false);
        quickJoinBox.setForeground(new Color(235, 235, 235));
        quickJoinBox.setFont(quickJoinBox.getFont().deriveFont(Font.BOLD, 13f));
        quickJoinBox.setToolTipText(tr("launcher.quickJoinTooltip"));
        quickJoinBox.setSelected(com.gummer.launcher.prefs.LauncherPrefs.getBoolean(com.gummer.launcher.prefs.LauncherPrefs.QUICK_JOIN, true));
        quickJoinBox.addActionListener(e -> com.gummer.launcher.prefs.LauncherPrefs.set(
                com.gummer.launcher.prefs.LauncherPrefs.QUICK_JOIN, quickJoinBox.isSelected()));
        // темна підкладка, щоб читалось на будь-якому фоні
        JPanel pill = new JPanel(new MigLayout("insets 4 12 4 12, gap 14", "[][]", "[]")) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(0, 0, 0, 150));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 14, 14);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        pill.setOpaque(false);
        pill.add(onlineLabel, "hidemode 3");
        pill.add(quickJoinBox, "hidemode 3");
        row.add(pill, "hidemode 3");
        pillPanel = pill;
        return row;
    }

    private JPanel pillPanel;

    /** Онлайн і «Одразу на сервер» для вибраного паку (лише вкладка GummerCraft і паки з адресою сервера). */
    private void refreshStatusRow() {
        Instance sel = getSelectedInstance();
        String address = sel != null && !sel.isVanilla() ? sel.getServerAddress() : null;
        boolean show = address != null;
        launchButton.setToolTipText(trFor(sel, "tooltip.launch"));
        quickJoinBox.setVisible(show);
        com.gummer.launcher.server.ServerPing.Status st = show ? online.get(address) : null;
        if (st != null) {
            onlineLabel.setText("● " + tr("launcher.onlineStatus", st.online, st.max));
            onlineLabel.setForeground(new Color(0x8C, 0xE0, 0x7A));
            onlineLabel.setVisible(true);
        } else if (show && offlineServers.contains(address)) {
            onlineLabel.setText("● " + tr("launcher.onlineUnavailable"));
            onlineLabel.setForeground(new Color(0xB0, 0xB0, 0xB0));
            onlineLabel.setVisible(true);
        } else {
            onlineLabel.setVisible(false);
        }
        if (pillPanel != null) {
            pillPanel.setVisible(show);
            pillPanel.revalidate();
            pillPanel.repaint();
        }
    }

    /** Пінг серверів паків і карток раз на хвилину, у фоні. */
    private void startOnlinePolling() {
        if (onlineTimer == null) {
            onlineTimer = new javax.swing.Timer(60_000, e -> pollOnline());
            onlineTimer.start();
        }
        pollOnline();
    }

    private javax.swing.Timer onlineTimer;

    private void pollOnline() {
        final java.util.Set<String> addresses = new java.util.LinkedHashSet<>();
        synchronized (launcher.getInstances()) {
            for (Instance i : launcher.getInstances().getInstances()) {
                if (!i.isVanilla() && i.getServerAddress() != null) addresses.add(i.getServerAddress());
            }
        }
        for (com.gummer.launcher.news.NewsFeed.Item n : news) {
            if (n.online != null) addresses.add(n.online);
        }
        if (addresses.isEmpty()) return;
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() {
                for (String a : addresses) {
                    com.gummer.launcher.server.ServerPing.Status s = com.gummer.launcher.server.ServerPing.query(a);
                    if (s != null) {
                        online.put(a, s);
                        offlineServers.remove(a);
                    } else {
                        online.remove(a);
                        offlineServers.add(a);
                    }
                }
                return null;
            }

            @Override
            protected void done() {
                synchronized (launcher.getInstances()) {
                    for (Instance i : launcher.getInstances().getInstances()) {
                        com.gummer.launcher.server.ServerPing.Status s = i.getServerAddress() != null ? online.get(i.getServerAddress()) : null;
                        instancesModel.setOnlineLine(i.getName(), s != null ? String.valueOf(s.online) : null);
                    }
                }
                if (instancesModel.getRowCount() > 0) {
                    instancesModel.fireTableRowsUpdated(0, instancesModel.getRowCount() - 1);
                }
                refreshStatusRow();
                rebuildNews();
            }
        }.execute();
    }

    // ------------------------------------------------------------------ 1.32: картки новин і реклами

    private void loadNews() {
        new SwingWorker<java.util.List<com.gummer.launcher.news.NewsFeed.Item>, Void>() {
            @Override
            protected java.util.List<com.gummer.launcher.news.NewsFeed.Item> doInBackground() {
                return com.gummer.launcher.news.NewsFeed.load(launcher);
            }

            @Override
            protected void done() {
                try {
                    news = get();
                } catch (Exception e) {
                    news = java.util.Collections.emptyList();
                }
                rebuildNews();
                pollOnline(); // онлайн для карток з полем online
            }
        }.execute();
    }

    /** Картки для поточної вкладки: не більше трьох, без реклами паку, який уже встановлено (hideIfInstalled). */
    private void rebuildNews() {
        newsRow.removeAll();
        int shown = 0;
        for (com.gummer.launcher.news.NewsFeed.Item n : news) {
            if (shown >= 3) break;
            if (n.hideIfInstalled != null && isInstalled(n.hideIfInstalled)) continue;
            newsRow.add(createNewsCard(n), "w 296!, h 128!");
            shown++;
        }
        newsRow.revalidate();
        newsRow.repaint();
    }

    private boolean isInstalled(String packName) {
        synchronized (launcher.getInstances()) {
            for (Instance i : launcher.getInstances().getInstances()) {
                if (i.getName().equalsIgnoreCase(packName) && i.isLocal() && i.isInstalled()) return true;
            }
        }
        return false;
    }

    private JComponent createNewsCard(final com.gummer.launcher.news.NewsFeed.Item n) {
        final JPanel card = new JPanel(new MigLayout("insets 0, gap 0", "[110!][grow,fill]", "[grow,fill]")) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(12, 12, 14, 205));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 16, 16);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        card.setOpaque(false);

        final CoverImage picture = new CoverImage();
        card.add(picture, "grow");
        if (n.image != null) {
            new SwingWorker<java.awt.image.BufferedImage, Void>() {
                @Override
                protected java.awt.image.BufferedImage doInBackground() {
                    return com.gummer.launcher.news.NewsFeed.image(n.image);
                }

                @Override
                protected void done() {
                    try {
                        java.awt.image.BufferedImage i = get();
                        if (i != null) picture.setImage(i);
                    } catch (Exception ignored) {
                    }
                }
            }.execute();
        }

        JPanel body = new JPanel(new MigLayout("insets 10 12 10 12, wrap 1, gap 0 4, fillx", "[0:0:,grow,fill]", "[][0:0:,grow,top][]"));
        body.setOpaque(false);
        JLabel title = new JLabel(n.title);
        title.setToolTipText(n.title);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 14f));
        title.setForeground(Color.WHITE);
        body.add(title);
        JTextArea text = new JTextArea(n.text);
        text.setLineWrap(true);
        text.setWrapStyleWord(true);
        text.setEditable(false);
        text.setFocusable(false);
        text.setOpaque(false);
        text.setBorder(null);
        text.setForeground(new Color(215, 215, 215));
        text.setFont(text.getFont().deriveFont(12f));
        body.add(text, "growy, hmin 0"); // довгий текст обрізається, кнопка лишається видимою
        JPanel bottomRow = new JPanel(new MigLayout("insets 0, gap 8", "[]push[]", "[]"));
        bottomRow.setOpaque(false);
        com.gummer.launcher.server.ServerPing.Status st = n.online != null ? online.get(n.online) : null;
        JLabel onlineText = new JLabel(st != null ? "● " + tr("launcher.onlineLine", st.online) : "");
        onlineText.setForeground(new Color(0x8C, 0xE0, 0x7A));
        onlineText.setFont(onlineText.getFont().deriveFont(Font.BOLD, 12f));
        bottomRow.add(onlineText);
        if (n.button != null && n.action != null) {
            JButton go = new JButton(n.button);
            go.setFocusable(false);
            go.putClientProperty("FlatLaf.style", "background: #3f8f2f; foreground: #ffffff; font: bold; arc: 8; borderWidth: 0; focusWidth: 0");
            go.addActionListener(e -> runNewsAction(n.action));
            bottomRow.add(go);
        }
        body.add(bottomRow);
        card.add(body, "grow");
        if (n.action != null) {
            card.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            card.addMouseListener(new java.awt.event.MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    runNewsAction(n.action);
                }
            });
        }
        return card;
    }

    /** Дія картки: pack:&lt;назва&gt; (вибрати пак), tab:vanilla (група Vanilla), url:&lt;адреса&gt;. */
    private void runNewsAction(String action) {
        if (action == null) return;
        if (action.startsWith("url:")) {
            SwingHelper.openURL(action.substring(4), this);
        } else if (action.startsWith("tab:")) {
            // вкладок немає з 1.32 (три розділи в одному списку): tab:vanilla веде до групи Vanilla
            if ("vanilla".equals(action.substring(4))) {
                synchronized (launcher.getInstances()) {
                    for (Instance i : launcher.getInstances().getInstances()) {
                        if (i.isVanilla()) {
                            selectPack(i);
                            break;
                        }
                    }
                }
            }
        } else if (action.startsWith("pack:")) {
            String name = action.substring(5);
            Instance target = null;
            synchronized (launcher.getInstances()) {
                for (Instance i : launcher.getInstances().getInstances()) {
                    if (i.getName().equalsIgnoreCase(name)) target = i;
                }
            }
            if (target != null) {
                selectPack(target);
            }
        }
    }

    // ------------------------------------------------------------------ 1.32: дії для вікон помилок і вильотів

    private void registerUiHooks() {
        com.gummer.launcher.ui.UiHooks.support = supportService == null ? null
                : (owner, topic, text) -> com.gummer.launcher.dialog.SupportDialog.showPrefilled(
                        owner != null ? owner : LauncherFrameFlat.this, supportService, this::getSelectedInstance, topic, text);
        com.gummer.launcher.ui.UiHooks.instanceSettings = (owner, instance) -> InstanceSettingsDialogFlat.open(LauncherFrameFlat.this, instance);
        com.gummer.launcher.ui.UiHooks.reinstall = (owner, instance) -> confirmHardUpdate(instance);
        com.gummer.launcher.crash.CrashDialog.driversHelpUrl = launcher.getProperties().getProperty("driversHelpUrl");
    }

    /** Картинка картки новин: обрізка «cover» по центру, закруглений лівий край. */
    private static final class CoverImage extends JComponent {
        private java.awt.image.BufferedImage img;

        void setImage(java.awt.image.BufferedImage i) {
            img = i;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setClip(new java.awt.geom.RoundRectangle2D.Float(0, 0, getWidth() + 16, getHeight(), 16, 16));
            if (img == null) {
                g2.setColor(new Color(40, 90, 35, 200));
                g2.fillRect(0, 0, getWidth(), getHeight());
            } else {
                g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                double sc = Math.max(getWidth() / (double) img.getWidth(), getHeight() / (double) img.getHeight());
                int w = (int) Math.ceil(img.getWidth() * sc), h = (int) Math.ceil(img.getHeight() * sc);
                g2.drawImage(img, (getWidth() - w) / 2, (getHeight() - h) / 2, w, h, null);
            }
            g2.dispose();
        }
    }

    /** Куди веде кнопка Discord (за замовчуванням - з налаштувань лаунчера; FlatLauncherFrame підставляє адресу вибраної збірки). */
    protected String discordUrl() {
        return launcher.getConfig().getDiscordUrl();
    }

    /** Куди веде кнопка "Новини та чат" (Telegram); як discordUrl. */
    protected String telegramUrl() {
        return launcher.getConfig().getTelegramUrl();
    }

    private final java.util.Map<String, java.awt.image.BufferedImage> buttonImages = new java.util.HashMap<>();

    /**
     * Картинки кнопок Telegram/Discord для вибраної збірки: {@code ui/button/telegram_<мова>_<назва збірки>.png} (і discord_...), якщо є, інакше стандартні
     * (на кнопці намальовано назву каналу, тож для збірки зі своїм каналом потрібна своя картинка).
     */
    protected void showPackButtons(String packKey) {
        applyButtonImage((CustomImageButton) telegramButton, "telegram", packKey);
        applyButtonImage((CustomImageButton) discordButton, "discord", packKey);
    }

    private void applyButtonImage(CustomImageButton button, String kind, String packKey) {
        java.awt.image.BufferedImage image = null;
        if (packKey != null && packKey.matches("[a-z0-9_.-]+")) image = buttonImage("ui/button/" + kind + "_" + langCode + "_" + packKey + ".png");
        if (image == null) image = buttonImage("ui/button/" + kind + "_" + langCode + ".png");
        if (image != null) button.setImage(image, 200, 45);
    }

    private java.awt.image.BufferedImage buttonImage(String path) {
        if (!buttonImages.containsKey(path)) {
            java.awt.image.BufferedImage image = null;
            try (java.io.InputStream in = Launcher.class.getResourceAsStream(path)) {
                if (in != null) image = ImageIO.read(in);
            } catch (IOException e) {
                image = null;
            }
            buttonImages.put(path, image);
        }
        return buttonImages.get(path);
    }

    /** Куди веде кнопка "Вікі та FAQ" (за замовчуванням - з налаштувань лаунчера; FlatLauncherFrame підставляє вікі вибраної збірки). */
    protected String wikiUrl() {
        return launcher.getConfig().getWikiUrl();
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
            menuItem = new JMenuItem(!selected.isLocal() ? trFor(selected, "instance.install") : trFor(selected, "instance.launch"), !selected.isLocal() ? downloadIcon : gamepadIcon);
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

                menuItem = new JMenuItem(trFor(selected, "instance.hardForceUpdate"), reinstallIcon);
                menuItem.addActionListener(new ActionListener() {
                    @Override
                    public void actionPerformed(ActionEvent e) {
                        confirmHardUpdate(selected);
                    }
                });
                popup.add(menuItem);

                menuItem = new JMenuItem(trFor(selected, "instance.deleteFiles"), trashIcon);
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
                trFor(instance, "instance.confirmDelete", instance.getTitle()), SharedLocale.tr("confirmTitle"))) {
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
        if (!SwingHelper.confirmDialog(this, trFor(instance, "instance.confirmHardUpdate"), SharedLocale.tr("confirmTitle"))) {
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
                    selectRememberedInstance();
                }
                requestFocus();
                startOnlinePolling();
                loadNews();
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
        Instance instance = getSelectedInstance();
        if (instance == null) return;

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
            this.instance = frame.getSelectedInstance(); //status callback
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
