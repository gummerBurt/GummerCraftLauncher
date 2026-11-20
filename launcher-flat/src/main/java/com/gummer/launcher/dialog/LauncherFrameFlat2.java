/*
 * Copyright (C) 2021-2025 GummerCraft <http://gummercraft.fun>
 */

package com.gummer.launcher.dialog;

import com.formdev.flatlaf.util.SystemInfo;
import com.gummer.launcher.i18n.Lang;
import com.gummer.launcher.popup.Notification;
import com.gummer.launcher.ui2.Btn;
import com.gummer.launcher.ui2.Glyph;
import com.gummer.launcher.ui2.PackList;
import com.gummer.launcher.ui2.PlayButton;
import com.gummer.launcher.ui2.Ui;
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
public class LauncherFrameFlat2 extends JFrame {

    /** 17.11.2026: розгорнуте вікно до показу розкладається з тими ж рамками, що й після (див. Zoom.settledInsets). */
    @Override
    public Insets getInsets() {
        return com.gummer.launcher.look.Zoom.settledInsets(this, super.getInsets());
    }

    /** 17.11.2026: плавний перехід при розгортанні та відновленні вікна (look.MorphRootPane). */
    @Override
    protected JRootPane createRootPane() {
        return new com.gummer.launcher.look.MorphRootPane(null);
    }

    public final Launcher launcher;
    private final Lang lang;
    private final String langCode;
    public static LauncherFrameFlat2 rootFrame;

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
    /** 17.11.2026: «Збірка» біля «Грати»: вікно «Керування збіркою». */
    private final Btn manageButton = new Btn(tr("ui2.manage"), Glyph.BOX, Btn.Style.TONAL).height(44);
    private final JButton wikiButton;
    /** 1.32: звернення в підтримку (null, якщо в launcher.properties немає supportInboxId). */
    private final com.gummer.launcher.support.SupportService supportService;
    private JButton supportButton;
    private final JButton launchButton;
    /** 1.32: вибір версії для групи паків (наприклад Main), видимий лише коли у вибраної групи кілька версій. */
    private final JComboBox<Instance> versionBox = new JComboBox<Instance>();
    /** 18.11.2026: готовий рядок на кожну версію, щоб HTML не розбирався заново (див. HtmlCellCache). */
    private final HtmlCellCache versionCells = new HtmlCellCache();
    /** Редизайн 2: підпис і вибір версії в панелі запуску (панель суцільна, тож без своєї підкладки). */
    private final JPanel versionPanel = new JPanel(new MigLayout("insets 0, wrap 1, gap 0 6", "[224!]", "[][]"));
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
    /** 1.33: плашка необов'язкового акаунта сервера вгорі справа; null, якщо serverAccount=false у launcher.properties. */
    private AccountChip2 accountChip;

    // load popup icons
    private final static Icon downloadIcon = com.gummer.launcher.look.MenuIcon.DOWNLOAD; // 20.11.2026: вектор, чітко на 125-150%
    private final static Icon gamepadIcon = com.gummer.launcher.look.MenuIcon.PLAY; // 20.11.2026: вектор, чітко на 125-150%
    private final static Icon refreshIcon = com.gummer.launcher.look.MenuIcon.REFRESH; // 20.11.2026: вектор, чітко на 125-150%
    private final static Icon folderIcon = com.gummer.launcher.look.MenuIcon.FOLDER; // 20.11.2026: вектор, чітко на 125-150%
    private final static Icon javaIcon = com.gummer.launcher.look.MenuIcon.SETTINGS; // 20.11.2026: вектор, чітко на 125-150%
    private final static Icon widgetIcon = com.gummer.launcher.look.MenuIcon.WIDGET; // 20.11.2026: вектор, чітко на 125-150%
    private final static Icon imageIcon = com.gummer.launcher.look.MenuIcon.IMAGE; // 20.11.2026: вектор, чітко на 125-150%
    private final static Icon crashIcon = com.gummer.launcher.look.MenuIcon.CRASH; // 20.11.2026: вектор, чітко на 125-150%
    private final static Icon reinstallIcon = com.gummer.launcher.look.MenuIcon.REINSTALL; // 20.11.2026: вектор, чітко на 125-150%
    private final static Icon trashIcon = com.gummer.launcher.look.MenuIcon.TRASH; // 20.11.2026: вектор, чітко на 125-150%

    /**
     * Create a new frame.
     *
     * @param launcher the launcher
     */
    public LauncherFrameFlat2(@NonNull Launcher launcher) throws IOException {
        super(tr("launcher.title", launcher.getVersion()));

        this.launcher = launcher;
        instancesModel = new InstanceTableModel(launcher.getInstances(), () -> launcher.getConfig().getLastInstance());
        // 20.11.2026 (власник: відкривати «Встановлені», якщо там щось є): на старті завжди «Встановлені»; жодної
        // встановленої збірки, тоді selectRememberedInstance перемкне на «Додати інсталяцію»
        launcher.getConfig().setInstalledOnly(true);
        instancesModel.setInstalledOnly(true);
        rootFrame = this;
        LauncherFrameFlat.rootFrame = this; // діалоги (налаштування, збірка) шукають вікно тут, хоч який вигляд вибрано

        // Initialize lang from this.launcher config
        this.lang = Lang.getFromName(launcher.getConfig().getLang());
        this.langCode = lang.getId(); // get langCode

        //
        // load multilang UI resources
        //

        // Редизайн 2 (тест, 16.11.2026): кнопки з векторними іконками і живим текстом замість картинок з вшитим
        // написом (текст перекладається, масштабується і читається екранним диктором).
        telegramButton = new Btn("", Glyph.TELEGRAM, Btn.Style.TONAL);
        telegramButton.setToolTipText(SharedLocale.tr("tooltip.telegram"));
        telegramButton.getAccessibleContext().setAccessibleName("Telegram");
        discordButton = new Btn("", Glyph.DISCORD, Btn.Style.TONAL);
        discordButton.setToolTipText(SharedLocale.tr("tooltip.discord"));
        discordButton.getAccessibleContext().setAccessibleName("Discord");
        String coffeeUrl = launcher.getProperties().getProperty("coffeeUrl", "").trim();
        coffeeButton = coffeeUrl.isEmpty() ? null : new Btn(tr("ui2.coffee"), Glyph.COFFEE, Btn.Style.TONAL);
        if (coffeeButton != null) coffeeButton.setToolTipText(SharedLocale.tr("tooltip.coffee"));
        // зелений контур, а не заливка: суцільно зелена на екрані лише «Грати»
        selfUpdateButton = new Btn(tr("ui2.nav.update"), Glyph.UPDATE, Btn.Style.OUTLINE).onScene();
        selfUpdateButton.setToolTipText(SharedLocale.tr("tooltip.update"));

        optionsButton = new Btn(tr("ui2.nav.settings"), Glyph.SETTINGS, Btn.Style.GHOST).onScene();
        optionsButton.setToolTipText(SharedLocale.tr("tooltip.settings"));
        wikiButton = new Btn(tr("ui2.nav.wiki"), Glyph.WIKI, Btn.Style.GHOST).onScene();
        wikiButton.setToolTipText(SharedLocale.tr("tooltip.faq"));
        supportService = com.gummer.launcher.support.SupportService.create(launcher);
        if (supportService != null) {
            supportButton = new Btn(tr("ui2.nav.support"), Glyph.SUPPORT, Btn.Style.GHOST).onScene().reserveBadge();
            supportButton.setToolTipText(SharedLocale.tr("tooltip.support"));
        }
        launchButton = new PlayButton();
        launchButton.setToolTipText(SharedLocale.tr("tooltip.launch"));
        ((PlayButton) launchButton).setLabel(tr("ui2.play"), null);
        launchButton.setEnabled(false);

        com.gummer.launcher.prefs.LauncherPrefs.init(launcher.getBaseDir());
        registerUiHooks();

        // configure main windowFrame
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(1194, 725));
        // 17.11.2026 (власник: «хочу щоб лаунчер можна було відкрити на весь екран - дуже бракує»): вікно розгортається
        // і тягнеться; менше за 1194 x 725 не стискається
        setResizable(true);
        // редизайн 2: заголовок вікна того ж кольору, що рейка, щоб вікно було одним цілим
        getRootPane().putClientProperty("JRootPane.titleBarBackground", Ui.SURFACE);
        getRootPane().putClientProperty("JRootPane.titleBarForeground", Ui.TEXT_2);
        initComponents();
        pack();
        setLocationRelativeTo(null);

        SwingHelper.setFrameIcon(this, Launcher.class, "icon.png");

        // 16.11.2026: перемикання вигляду в налаштуваннях: список збірок уже завантажений, тож без мережі і діалогу
        // «Перевірка нових модпаків», і запам'ятована збірка вибрана ще до показу вікна (раніше ~2 с була перша в списку)
        // і на звичайному старті (16.11.2026, «щоб не тупило»): спершу список з кешів на диску, вікно одразу повне,
        // без діалогу; справжній список з мережі оновлюється потім тихо у фоні (refreshInstancesQuietly)
        boolean reopened = com.gummer.launcher.FancyLauncher.takeQuickReopen() && launcher.getInstances().size() > 0;
        if (reopened || loadInstancesFromCache()) {
            instancesReady();
            if (!reopened) SwingUtilities.invokeLater(this::refreshInstancesQuietly);
        } else {
            SwingUtilities.invokeLater(new Runnable() {
                @Override
                public void run() {
                    loadInstances();
                }
            });
        }
    }

    /** Редизайн 2: вміст рейки (список або «порожньо»). */
    private final CardLayout listCards = new CardLayout();
    private final JPanel listHolder = new JPanel(listCards);
    private boolean instancesLoadedOnce;

    /**
     * Редизайн 2 (тест, 16.11.2026). Три зони замість «список, купа кнопок, картинка»:
     * <ul>
     *   <li>рейка ліворуч ({@code Ui.RAIL_W}): що запускати (сервери, потім Minecraft) і хто грає (акаунт, скін),
     *       внизу дрібно спільнота (Telegram, Discord, підтримати);</li>
     *   <li>сцена справа: фон і логотип збірки, вгорі навігація (вікі, підтримка, налаштування, оновлення лаунчера),
     *       унизу картки новин;</li>
     *   <li>панель запуску внизу ({@code Ui.DOCK_H}): назва і стан вибраної збірки, онлайн і «Одразу на сервер»,
     *       вибір версії і одна зелена кнопка «Грати».</li>
     * </ul>
     * Погляд іде зліва направо і згори вниз: вибрав збірку, побачив її сцену, натиснув «Грати» в кінці шляху.
     */
    private void initComponents() {
        JPanel container = createContainerPanel();
        container.setLayout(new BorderLayout());

        // ---------------------------------------------------------------- рейка
        JPanel rail = new JPanel(new MigLayout("insets 0, gap 0, wrap 1", "[grow,fill]", "[][grow,fill][]")) {
            @Override
            protected void paintComponent(Graphics g) {
                g.setColor(Ui.SURFACE);
                g.fillRect(0, 0, getWidth(), getHeight());
                g.setColor(Ui.LINE);
                g.fillRect(getWidth() - 1, 0, 1, getHeight());
            }
        };
        rail.setOpaque(true);
        Dimension railSize = new Dimension(Ui.RAIL_W, 0);
        rail.setPreferredSize(railSize);
        rail.setMinimumSize(railSize);

        instanceScroll.setBorder(BorderFactory.createEmptyBorder(Ui.S2, 0, 0, 1));
        instanceScroll.setViewportBorder(null);
        instanceScroll.getViewport().setBackground(Ui.SURFACE);
        instanceScroll.setBackground(Ui.SURFACE);
        instanceScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        instanceScroll.getVerticalScrollBar().setUnitIncrement(16);
        instanceScroll.getVerticalScrollBar().putClientProperty("FlatLaf.style",
                "width: 8; thumbArc: 999; trackArc: 999; track: #16171B; thumb: #3A3D44");
        listHolder.setOpaque(false);
        listHolder.add(instanceScroll, "list");
        listHolder.add(createEmptyState(), "empty");
        rail.add(createListSwitch(), "gap 12 13 12 2, h 32!"); // 17.11.2026: «Усі / Встановлені»
        rail.add(listHolder, "grow, hmin 0");

        JPanel railBottom = new JPanel(new MigLayout("insets 12 16 12 16, gap 0, wrap 1", "[grow,fill]", ""));
        railBottom.setOpaque(false);
        // 17.11.2026 (власник: «модпаки та версії мають дуже мало місця ... "змінити скін" та "аккаунт" перенести
        // вправо зверху, але видозмінити та зробити трохи меншим»): акаунт і «Скіни» у верхньому ряду кнопок перед
        // «Вікі», висотою 36 як вони; список збірок у рейці отримав ~100 px
        SkinChip2 skinChip = null;
        if (!"false".equalsIgnoreCase(launcher.getProperties().getProperty("serverAccount", "true").trim())) {
            accountChip = new AccountChip2(launcher, () -> ServerAccountDialog.showFor(this, launcher, supportService,
                    this::getSelectedInstance, () -> accountChip.refresh()));
            skinChip = new SkinChip2(() -> ServerAccountDialog.showFor(this, launcher, supportService,
                    this::getSelectedInstance, () -> accountChip.refresh(), true));
        }
        JPanel community = new JPanel(new MigLayout("insets 0, gap 8", "[36!][36!][grow,fill]", "[36!]"));
        community.setOpaque(false);
        community.add(telegramButton);
        community.add(discordButton);
        if (coffeeButton != null) community.add(coffeeButton);
        railBottom.add(community);
        // 16.11.2026: лайк вигляду (один з IP) і «Фідбек по дизайну»
        DesignVote vote = DesignVote.create(this, launcher, supportService, com.gummer.launcher.FancyLauncher.LOOK_CLASSIC);
        if (vote != null) railBottom.add(vote, "h " + DesignVote.HEIGHT + "!, gaptop 12");
        JComponent sourceLink = createSourceLink();
        if (sourceLink != null) {
            railBottom.add(sourceLink, "gaptop 12"); // 20.11.2026: було 8, лайк і фідбек ближче до «Вигляд», далі від посилання
        }
        rail.add(railBottom);

        // ---------------------------------------------------------------- сцена: навігація вгорі
        JPanel stage = new JPanel(new BorderLayout());
        stage.setOpaque(false);
        JPanel topBar = new JPanel(new MigLayout("insets 16 32 0 16, gap 8", "push[][][][][][]", "[36!]"));
        getRootPane().putClientProperty(com.gummer.launcher.look.Toast.BELOW, topBar); // сповіщення під кнопками, не на них
        topBar.setOpaque(false);
        topBar.add(selfUpdateButton, "hidemode 3, gapright 8");
        if (accountChip != null) {
            topBar.add(accountChip, "h 36!, wmax 150");
            topBar.add(skinChip, "h 36!, w pref+24!, gapright 8"); // MigLayout не додає бічні insets у pref цієї плашки
        }
        // 18.11.2026 (власник: «винеси перемикання мов на головну сторінку лаунчера. Просто прапорцями»)
        topBar.add(new com.gummer.launcher.look.LangFlags(launcher), "h 36!, gapright 8"); // 20.11.2026: на висоту сусідніх кнопок (було 32)
        topBar.add(wikiButton);
        if (supportButton != null) {
            topBar.add(supportButton);
            supportButton.addActionListener(e -> {
                ((Btn) supportButton).setBadge(false);
                com.gummer.launcher.dialog.SupportDialog.showFor(this, supportService, this::getSelectedInstance);
            });
            checkSupportReplies();
        }
        topBar.add(optionsButton);
        // логотип збірки не заходить під кнопки навігації (з «Оновити лаунчер» їх ширше)
        java.awt.event.ComponentAdapter logoLimit = new java.awt.event.ComponentAdapter() {
            @Override
            public void componentMoved(java.awt.event.ComponentEvent e) {
                updateLogoLimit(container, topBar);
            }

            @Override
            public void componentShown(java.awt.event.ComponentEvent e) {
                updateLogoLimit(container, topBar);
            }

            @Override
            public void componentHidden(java.awt.event.ComponentEvent e) {
                updateLogoLimit(container, topBar);
            }
        };
        for (Component c : topBar.getComponents()) c.addComponentListener(logoLimit);
        JPanel north = new JPanel(new BorderLayout());
        north.setOpaque(false);
        north.add(topBar, BorderLayout.NORTH);
        JPanel oldExeBanner = createOldExeBanner();
        if (oldExeBanner != null) {
            // під логотипом (він до 112 px заввишки від верху сцени)
            oldExeBanner.setBorder(BorderFactory.createEmptyBorder(88, 20, 0, 20));
            north.add(oldExeBanner, BorderLayout.CENTER);
        }
        stage.add(north, BorderLayout.NORTH);

        // новини внизу сцени, над панеллю запуску
        JPanel newsArea = new JPanel(new BorderLayout());
        newsArea.setOpaque(false);
        newsRow.setOpaque(false);
        newsRow.setLayout(new MigLayout("insets 0 32 16 24, gap 12", "", "[]"));
        newsArea.add(newsRow, BorderLayout.SOUTH);
        stage.add(wrapMain(newsArea), BorderLayout.CENTER); // 17.11.2026: або сторінка Modrinth

        // ---------------------------------------------------------------- панель запуску
        JPanel dock = new JPanel(new MigLayout("insets 0 32 0 20, gap 0", "[grow,fill]24[]10[]14[]",
                "[" + Ui.DOCK_H + "!, center]")) {
            @Override
            protected void paintComponent(Graphics g) {
                g.setColor(Ui.SURFACE);
                g.fillRect(0, 0, getWidth(), getHeight());
                g.setColor(Ui.LINE);
                g.fillRect(0, 0, getWidth(), 1);
            }
        };
        dock.setOpaque(true);
        dock.add(createDockIdentity(), "wmin 0");
        setupVersionBox();
        manageButton.setToolTipText(tr("manage.tooltip"));
        manageButton.addActionListener(e -> openManage(getSelectedInstance(), InstanceManageDialog.GENERAL));
        CoachMark.manageTip(this, launcher, manageButton);
        // 17.11.2026: «Збірка» одразу ліворуч від «Грати», як у Liquid Glass; на рівні поля версії (воно внизу блоку)
        dock.add(versionPanel, "hidemode 3");
        dock.add(manageButton, manageConstraints(false));
        dockRef = dock;
        dock.add(launchButton, "w 232!, h 68!");
        stage.add(dock, BorderLayout.SOUTH);

        container.add(rail, BorderLayout.WEST);
        container.add(stage, BorderLayout.CENTER);
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
                        LauncherFrameFlat2.rootFrame,
                        LauncherFrameFlat2.class,
                        Notification.Type.WARNING,
                        SharedLocale.tr("notification.popup.update")
                    );
                }
            }
        });

        // sidebar buttons events
        // адреси читаються в момент натискання: у збірки можуть бути власні (FlatLauncherFrame2.discordUrl/telegramUrl/wikiUrl)
        discordButton.addActionListener(e -> SwingHelper.openURL(discordUrl(), this));
        telegramButton.addActionListener(e -> SwingHelper.openURL(telegramUrl(), this));
        if (coffeeButton != null) {
            coffeeButton.addActionListener(e -> SwingHelper.openURL(launcher.getProperties().getProperty("coffeeUrl").trim(), this));
        }
        wikiButton.addActionListener(e -> SwingHelper.openURL(wikiUrl(), this));

        // instancesTable events
        instancesTable.setModel(instancesModel);
        PackList.install(instancesTable, this::packRow);
        instancesTable.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke("ENTER"), "ui2.launch");
        instancesTable.getActionMap().put("ui2.launch", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (launchButton.isEnabled()) launch();
            }
        });
        instancesModel.addTableModelListener(e -> SwingUtilities.invokeLater(this::refreshListLayout));
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
//                launcher.getUpdateManager().checkForUpdate(LauncherFrameFlat2.this);
//                webView.browse(launcher.getNewsURL(), false);
//            }
//        });

        selfUpdateButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                launcher.getUpdateManager().performUpdate(LauncherFrameFlat2.this);
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
     * гравця (FlatLauncherFrame2.updatePackBackground): інакше, коли список ще неповний (ванільні версії Mojang ще не
     * підвантажились, немає інтернету), вимушений перший рядок перезаписував lastInstance, і гравець Vanilla
     * наступного разу опинявся на StalDayZ (знайдено 13.11.2026).
     */
    protected boolean restoringSelection;

    /** Вибрати в списку останню вибрану збірку (Configuration.lastInstance), якщо вона є в списку, інакше першу. */
    private void selectRememberedInstance() {
        // 20.11.2026: на старті «Встановлені»; якщо встановленого немає зовсім (новий гравець), «Додати інсталяцію».
        // Вирішується один раз, коли список уже прочитано (порожній список ще нічого не значить)
        if (startupTab && launcher.getInstances().size() > 0) {
            startupTab = false;
            boolean any = false;
            synchronized (launcher.getInstances()) {
                for (Instance i : launcher.getInstances().getInstances()) {
                    if (i.isLocal() && i.isInstalled()) {
                        any = true;
                        break;
                    }
                }
            }
            if (!any && listSwitchRef != null && listSwitchRef.isInstalledOnly()) {
                SwingUtilities.invokeLater(() -> listSwitchRef.setInstalledOnly(false));
            }
        }
        // 17.11.2026: копія чи збірка Modrinth (щойно створена) у розділі: рядок розділу має показати саме її, а не
        // версію, вибрану в розділі раніше
        // 17.11.2026: свій останній вибір у кожного режиму списку («Усі» / «Встановлені»)
        String remembered = com.gummer.launcher.manage.ListMemory.recall(launcher.getConfig(), instancesModel.isInstalledOnly());
        if (instancesModel.chooseByName(remembered)) instancesModel.refreshChoice();
        int row = instancesModel.rowOf(remembered);
        if (row < 0) row = Math.max(0, instancesModel.rowOf(launcher.getConfig().getLastInstance()));
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
        final Color normal = Ui.TEXT_3;
        final Color hover = Ui.TEXT;
        final JLabel link = new JLabel(tr("launcher.sourceCode"));
        link.setFont(link.getFont().deriveFont(Font.PLAIN, 11f));
        link.setForeground(normal);
        link.setToolTipText(url.trim());
        link.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        link.setAlignmentX(Component.LEFT_ALIGNMENT);
        link.setBorder(BorderFactory.createEmptyBorder(0, 2, 0, 0));
        link.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                SwingHelper.openURL(url.trim(), LauncherFrameFlat2.this);
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
                || "bundled".equals(com.gummer.launcher.util.JavaKind.get())) {
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
        get.addActionListener(e -> SwingHelper.openURL(url, LauncherFrameFlat2.this));
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

    /**
     * 1.32: чи відповіли адміни на звернення. З 16.11.2026 не раз при старті, а поки вікно видно (раз на хвилину,
     * {@link SupportUnread}), з числом непрочитаних на кнопці.
     */
    private void checkSupportReplies() {
        SupportUnread.watch(this, supportService, this::getSelectedInstance, n -> {
            supportButton.setToolTipText(SharedLocale.tr(n > 0 ? "support.newReplyTooltip" : "tooltip.support"));
            ((Btn) supportButton).setBadge(n);
        }, () -> showNotification(LauncherFrameFlat2.rootFrame, LauncherFrameFlat2.class, Notification.Type.INFO,
                SharedLocale.tr("support.newReply")));
    }

    /** Пак у вибраному рядку (для групи: вибрана версія), або null. */
    protected Instance getSelectedInstance() {
        int row = instancesTable.getSelectedRow();
        return row >= 0 && row < instancesModel.getRowCount() ? instancesModel.getInstanceAt(row) : null;
    }

    private JPanel dockRef;

    /**
     * 20.11.2026 (власник: «все по вертикалі централізовано»): без поля версії (збірки GummerCraft, StalDayZ) «Збірка»
     * стоїть по центру зеленої грані «Грати» (кнопка 68 з нижнім ребром 4, грань 64), а з полем версії, як раніше, на
     * рівні поля (воно внизу свого блоку). Раніше вона завжди стояла по низу і без поля версії виглядала опущеною.
     */
    private static String manageConstraints(boolean withVersion) {
        int playTop = (Ui.DOCK_H - 68) / 2;
        int gapBottom = withVersion ? playTop + 1 : Ui.DOCK_H - (playTop + 32 - 22) - 44;
        return "h 44!, aligny bottom, gapbottom " + gapBottom;
    }

    private void alignManage(boolean withVersion) {
        if (dockRef == null) return;
        ((MigLayout) dockRef.getLayout()).setComponentConstraints(manageButton, manageConstraints(withVersion));
        dockRef.revalidate();
    }

    private void setupVersionBox() {
        versionPanel.setOpaque(false);
        versionPanel.setVisible(false);
        versionLabel.setForeground(Ui.TEXT_2);
        versionLabel.setFont(Ui.font(Font.PLAIN, 12f));
        versionPanel.add(versionLabel, "wmax 248");
        versionPanel.add(versionBox, "growx, wmin 0, h 44!"); // wmin 0: ширина за колонкою, а не за найдовшою версією
        versionBox.setFocusable(true);
        versionBox.setFont(Ui.font(Font.BOLD, 15f));
        versionBox.putClientProperty("FlatLaf.style", "arc: 8; background: #1F2126; buttonBackground: #1F2126; "
                + "buttonEditableBackground: #1F2126; borderColor: #34373E; focusedBorderColor: #8FD16F; "
                + "buttonArrowColor: #B0B5BD; buttonHoverArrowColor: #F2F3F5; focusWidth: 0");
        versionBox.setMaximumRowCount(12);
        versionBox.setToolTipText(tr("launcher.versionTooltip"));
        versionBox.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                Object text = value;
                if (value instanceof Instance) {
                    Instance i = (Instance) value;
                    String version = instancesModel.versionLabelOf(i);
                    // «музей» Vanilla (16.11.2026) без «Minecraft»: «Pre-classic rd-20090515» і так довге, а з префіксом
                    // поле версії розпирало під кнопку запуску
                    String mc = com.gummer.launcher.vanilla.VanillaVersions.museumIndex(i) >= 0 ? "" : "Minecraft ";
                    // 17.11.2026: копія чи збірка Modrinth: своя назва, у відкритому списку сіра версія гри перед станом
                    boolean derived = com.skcraft.launcher.swing.InstanceTableModel.isNamed(i);
                    String derivedVer = instancesModel.derivedVersionOf(i);
                    if (derived) {
                        version = instancesModel.pickerTitleOf(i);
                        mc = "";
                    }
                    // 1.32: стан видно не лише текстом. Встановлено - зелена крапка і білий текст, є оновлення - жовта,
                    // не встановлено - порожнє коло і приглушений текст; найновіша - синя позначка
                    boolean installed = i.isLocal() && i.isInstalled();
                    String dotColor = !installed ? "#6b7079" : i.isUpdatePending() ? "#e0b050" : "#7ccf6a";
                    String dot = "<span style=\"color:" + dotColor + "\">" + (installed ? "●" : "○") + "</span>&nbsp;";
                    // 20.11.2026: у «Додати інсталяцію» ваніль і завантажувачі не кажуть, яка версія вже є
                    boolean fresh = instancesModel.isFreshInstall(i);
                    if (fresh) dot = "";
                    if (index < 0) {
                        // закритий список: позначка і версія
                        text = "<html>" + dot + mc + SwingHelper.htmlEscape(version) + "</html>";
                    } else {
                        String status = !installed ? tr("launcher.versionNotInstalled")
                                : i.isUpdatePending() ? tr("launcher.versionUpdate") : tr("launcher.versionInstalled");
                        String updates = installed && !i.isUpdatePending() ? InstanceTableModel.updatesText(i) : null;
                        if (updates != null) status = status + " · " + updates; // 17.11.2026: бейдж оновлень
                        String statusColor = !installed ? "#8a8f98" : dotColor;
                        String newest = instancesModel.isNewest(i) ? "&nbsp;&nbsp;<span style=\"color:#5aa9ff;font-size:11\">"
                                + SwingHelper.htmlEscape(tr("launcher.versionNewest")) + "</span>" : "";
                        if (derived && derivedVer != null) status = derivedVer + " · " + status;
                        if (fresh) status = "";
                        text = "<html>" + dot + "<b" + (installed || fresh ? "" : " style=\"color:#b4b8bf\"") + ">" + mc
                                + SwingHelper.htmlEscape(version) + "</b>&nbsp;&nbsp;<span style=\"color:" + statusColor + ";font-size:11\">"
                                + SwingHelper.htmlEscape(status) + "</span>" + newest + "</html>";
                    }
                }
                Component c = versionCells.of(value, index).getListCellRendererComponent(list, text, index, isSelected, cellHasFocus);
                if (index >= 0) {
                    // у відкритому списку шрифт менший, ніж у самому полі (18), щоб влазив стан версії
                    c.setFont(c.getFont().deriveFont(Font.BOLD, 14f));
                    // 17.11.2026: встановлені вгорі списку, від решти їх відділяє тонка риска
                    Object above = index > 0 ? list.getModel().getElementAt(index - 1) : null;
                    boolean split = value instanceof Instance && above instanceof Instance
                            && !instancesModel.isFreshInstall((Instance) value)
                            && !(((Instance) value).isLocal() && ((Instance) value).isInstalled())
                            && ((Instance) above).isLocal() && ((Instance) above).isInstalled();
                    ((JComponent) c).setBorder(split
                            ? BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(255, 255, 255, 40)),
                            BorderFactory.createEmptyBorder(5, 10, 6, 10))
                            : BorderFactory.createEmptyBorder(6, 10, 6, 10));
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
            showPack(chosen);
        });
        // 19.11.2026: рядок «Імпорт збірки» не вибирається (InstanceTable), а відкриває вікно імпорту
        instancesTable.onImportRow = this::importOther;
        instancesTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                refreshVersionBox();
                refreshStatusRow();
                refreshModrinthPage();
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
                    {
                        // 16.11.2026: поки список закритий, рядки не міряються. Інакше кожна зміна групи (і вибір
                        // версії) рендерила всі HTML-рядки (Vanilla з музеєм: 164) ще до відкриття. Розміри задає show().
                        list.setFixedCellWidth(1);
                        list.setFixedCellHeight(1);
                    }

                    @Override
                    @SuppressWarnings({"unchecked", "rawtypes"})
                    public void show() {
                        int w = 1, h = 1;
                        ListCellRenderer r = list.getCellRenderer();
                        for (int k = 0; k < list.getModel().getSize(); k++) {
                            Dimension d = r.getListCellRendererComponent(list, list.getModel().getElementAt(k), k, false, false).getPreferredSize();
                            w = Math.max(w, d.width);
                            h = Math.max(h, d.height);
                        }
                        list.setFixedCellWidth(w);
                        list.setFixedCellHeight(h);
                        super.show();
                    }

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
                        // 16.11.2026: ширший за поле список росте ліворуч, а не праворуч: праворуч кнопка запуску
                        // (Liquid Glass, Класика) чи край вікна (Бібліотека), і список її перекривав
                        int w = Math.max(pw, need);
                        return super.computePopupBounds(px - (w - pw), py, w, ph);
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
            // 16.11.2026: список версій міняється лише коли змінилась група, і одним махом (setModel). Раніше
            // removeAllItems + addItem по одній: після кожної Swing перемірював усі рядки (HTML), з музеєм Vanilla
            // (164 версії) це ~500 рендерів на кожну зміну версії.
            java.util.List<Instance> items = members.size() >= 2 ? members : java.util.Collections.<Instance>emptyList();
            boolean same = versionBox.getItemCount() == items.size();
            for (int k = 0; same && k < items.size(); k++) {
                same = versionBox.getItemAt(k) == items.get(k);
            }
            if (!same) {
                // закрите поле міряє лише поточну версію, а не всі (16.11.2026, швидкість переходу на Vanilla)
                versionBox.setPrototypeDisplayValue(items.isEmpty() ? null : shown);
                versionBox.setModel(new DefaultComboBoxModel<Instance>(items.toArray(new Instance[0])));
                HtmlCellCache.prewarm(versionBox);
            }
            if (members.size() >= 2) {
                versionBox.setSelectedItem(shown);
            }
            versionPanel.setVisible(members.size() >= 2);
            alignManage(members.size() >= 2);
            if (members.size() >= 2) {
                // 17.11.2026: за розділом, а не за самою збіркою: у розділі може бути вибрана збірка Modrinth
                String sec = shown.getGroup();
                String key = "vanilla".equals(sec) ? "launcher.versionLabelVanilla"
                        : "fabric".equals(sec) ? "launcher.versionLabelFabric"
                        : "quilt".equals(sec) ? "launcher.versionLabelQuilt"
                        : "forge".equals(sec) ? "launcher.versionLabelForge"
                        : "neoforge".equals(sec) ? "launcher.versionLabelNeoForge"
                        : "main".equals(sec) ? "launcher.versionLabelGummerCraft" : "launcher.versionLabel";
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

    /**
     * 1.33: вибір версії завантажувача (Fabric Loader, Forge, NeoForge) для версії Minecraft; діє з наступного запуску
     * (пак оновиться). Список від новішої, перша позначена «найновіша», рекомендована Forge «рекомендована».
     */
    private void chooseLoader(Instance instance) {
        final boolean fabric = instance.isFabric();
        final boolean quilt = instance.isQuilt();
        final com.gummer.launcher.forge.ForgeVersions.Kind kind = com.gummer.launcher.forge.ForgeVersions.kindOf(instance);
        final String title = fabric ? "Fabric Loader" : quilt ? "Quilt Loader" : kind.title;
        final String mc = fabric ? com.gummer.launcher.fabric.FabricVersions.versionIdOf(instance)
                : quilt ? com.gummer.launcher.quilt.QuiltVersions.versionIdOf(instance) : com.gummer.launcher.forge.ForgeVersions.versionIdOf(instance);
        new SwingWorker<java.util.List<String>, Void>() {
            private String recommended;

            @Override
            protected java.util.List<String> doInBackground() {
                if (fabric) return com.gummer.launcher.fabric.FabricVersions.loaders(launcher);
                if (quilt) return com.gummer.launcher.quilt.QuiltVersions.loaders(launcher);
                java.util.List<String> all = com.gummer.launcher.forge.ForgeVersions.versions(launcher, kind).get(mc);
                if (all == null) return java.util.Collections.emptyList();
                recommended = com.gummer.launcher.forge.ForgeVersions.recommended(launcher, kind, mc, all);
                return all;
            }

            @Override
            protected void done() {
                java.util.List<String> all;
                try {
                    all = get();
                } catch (Exception e) {
                    all = java.util.Collections.emptyList();
                }
                if (all.isEmpty()) {
                    SwingHelper.showErrorDialog(LauncherFrameFlat2.this, tr("loader.unavailable", title), title);
                    return;
                }
                String current = fabric ? com.gummer.launcher.fabric.FabricVersions.pinnedLoader(instance)
                        : quilt ? com.gummer.launcher.quilt.QuiltVersions.pinnedLoader(instance) : com.gummer.launcher.forge.ForgeVersions.pinnedLoader(instance);
                java.util.List<String> items = new java.util.ArrayList<>();
                for (int i = 0; i < all.size(); i++) {
                    String v = all.get(i);
                    items.add(v.equals(recommended) && i > 0 ? tr("loader.recommended", v)
                            : i == 0 ? tr("loader.latest", v) : v);
                }
                JComboBox<String> box = new JComboBox<>(items.toArray(new String[0]));
                box.setMaximumRowCount(15);
                int idx = current != null ? all.indexOf(current) : (recommended != null ? all.indexOf(recommended) : 0);
                box.setSelectedIndex(Math.max(0, idx));
                JPanel panel = new JPanel(new MigLayout("insets 0, wrap 1", "[grow,fill]"));
                panel.add(new JLabel(tr("loader.text", mc, current != null ? current : tr("loader.notInstalled"))));
                panel.add(box);
                com.gummer.launcher.look.LookUi.freeze(panel); // 18.11.2026: розкладка наперед (див. LookUi.freeze)
                int r = JOptionPane.showConfirmDialog(LauncherFrameFlat2.this, panel, title,
                        JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
                if (r != JOptionPane.OK_OPTION) return;
                String chosen = all.get(box.getSelectedIndex());
                if (chosen.equals(current)) return;
                try {
                    if (fabric) com.gummer.launcher.fabric.FabricVersions.pinLoader(instance, chosen);
                    else if (quilt) com.gummer.launcher.quilt.QuiltVersions.pinLoader(instance, chosen);
                    else com.gummer.launcher.forge.ForgeVersions.pinLoader(instance, chosen);
                    if (instance.isLocal()) {
                        instance.setUpdatePending(true);
                        com.skcraft.launcher.persistence.Persistence.commitAndForget(instance);
                    }
                    instancesModel.update();
                } catch (Exception e) {
                    SwingHelper.showErrorDialog(LauncherFrameFlat2.this, e.toString(), title);
                }
            }
        }.execute();
    }

    /** Вибрати пак у списку (для групи: цю версію в групі). */
    private void selectPack(Instance target) {
        showPack(target);
    }

    /**
     * Показати пак (для групи: цю версію в рядку групи) і вибрати його рядок. До 16.11.2026 тут були clearSelection і
     * повне update(), а новий вибір ставився лише в наступному кроці EDT (tableChanged -> invokeLater): між ними
     * вікно встигало намалюватись без вибраної збірки, і при кожній зміні версії миготіли список зліва, вибір версії
     * та фон. Тепер рядки лише оновлюються (вибір не знімається), а вибір переставляється за один крок: слухачі
     * отримують одну подію вже з новою версією.
     */
    private void showPack(Instance target) {
        instancesModel.chooseInGroup(target);
        launcher.getConfig().setLastInstance(target.getName());
        com.skcraft.launcher.persistence.Persistence.commitAndForget(launcher.getConfig());
        instancesModel.refreshChoice();
        int row = instancesModel.rowOf(target.getName());
        if (row < 0) { // пака немає в списку (фільтр): як раніше
            instancesTable.clearSelection();
            instancesModel.update(); // tableChanged -> selectRememberedInstance
            return;
        }
        ListSelectionModel sm = instancesTable.getSelectionModel();
        sm.setValueIsAdjusting(true);
        try {
            instancesTable.clearSelection();
            instancesTable.setRowSelectionInterval(row, row);
        } finally {
            sm.setValueIsAdjusting(false); // одна подія для слухачів: фон, вибір версії, рядок стану
        }
        instancesTable.scrollRectToVisible(instancesTable.getCellRect(row, 0, true));
    }

    /** Текст для Vanilla і Fabric («версія»), якщо є, інакше звичайний («модпак»). */
    private static String trFor(Instance instance, String key, Object... args) {
        // 17.11.2026: для копій свій текст («копію»), якщо він є, інакше як для версії
        String k = instance != null && instance.isCopy() && SharedLocale.trOr(key + ".copy", null) != null ? key + ".copy"
                : instance != null && instance.isBuiltIn() ? key + ".vanilla" : key;
        return args.length == 0 ? tr(k) : tr(k, args);
    }

    // ------------------------------------------------------------------ 1.32: онлайн і «одразу на сервер»

    private void updateLogoLimit(JPanel container, JPanel topBar) {
        if (!(container instanceof com.gummer.launcher.FlatBackgroundPanel2)) return;
        int left = Integer.MAX_VALUE;
        for (Component c : topBar.getComponents()) {
            if (c.isVisible()) left = Math.min(left, SwingUtilities.convertPoint(topBar, c.getX(), 0, container).x);
        }
        ((com.gummer.launcher.FlatBackgroundPanel2) container).setLogoLimit(left == Integer.MAX_VALUE ? left : left - Ui.S5);
    }

    /** Редизайн 2: іконка, назва і стан вибраної збірки в панелі запуску. */
    private final JLabel dockIcon = new JLabel();
    private final JLabel dockTitle = new JLabel(" ");

    private JPanel createDockIdentity() {
        JPanel box = new JPanel(new MigLayout("insets 0, gap 0", "[]16[grow,fill]", "[]"));
        box.setOpaque(false);
        dockIcon.setPreferredSize(new Dimension(48, 48));
        dockIcon.setHorizontalAlignment(SwingConstants.CENTER);
        box.add(dockIcon, "w 48!, h 48!, aligny center");
        JPanel lines = new JPanel(new MigLayout("insets 0, gap 0, wrap 1", "[grow,fill]", "[]2[]2[]"));
        lines.setOpaque(false);
        dockTitle.setFont(Ui.font(Font.BOLD, 18f));
        dockTitle.setForeground(Ui.TEXT);
        lines.add(dockTitle, "wmin 0");
        onlineLabel.setFont(Ui.font(Font.PLAIN, 13f));
        onlineLabel.setForeground(Ui.TEXT_2);
        lines.add(onlineLabel, "wmin 0, hidemode 3");
        quickJoinBox.setOpaque(false);
        quickJoinBox.setFocusable(true);
        quickJoinBox.setForeground(Ui.TEXT_2);
        quickJoinBox.setFont(Ui.font(Font.PLAIN, 13f));
        quickJoinBox.setToolTipText(tr("launcher.quickJoinTooltip"));
        quickJoinBox.setSelected(com.gummer.launcher.prefs.LauncherPrefs.getBoolean(com.gummer.launcher.prefs.LauncherPrefs.QUICK_JOIN, true));
        quickJoinBox.addActionListener(e -> com.gummer.launcher.prefs.LauncherPrefs.set(
                com.gummer.launcher.prefs.LauncherPrefs.QUICK_JOIN, quickJoinBox.isSelected()));
        lines.add(quickJoinBox, "hidemode 3, gapleft 0");
        box.add(lines, "wmin 0, aligny center");
        return box;
    }

    /**
     * Панель запуску для вибраної збірки: назва, стан (онлайн сервера або встановлено чи ні), «Одразу на сервер»
     * лише в збірок з сервером, і підпис головної кнопки за тим, що вона зробить.
     */
    /** «Forge 1.16.5»: розділ і версія гри копії чи збірки Modrinth. */
    private String derivedSubtitle(Instance sel) {
        String sec = com.gummer.launcher.manage.InstanceCopies.sectionTitle(sel.getGroup());
        String v = instancesModel.derivedVersionOf(sel);
        if (sec == null) return v;
        return v != null ? sec + " " + v : sec;
    }

    private void refreshStatusRow() {
        Instance sel = getSelectedInstance();
        PlayButton play = (PlayButton) launchButton;
        launchButton.setEnabled(sel != null);
        if (sel == null) {
            dockTitle.setText(" ");
            dockIcon.setIcon(null);
            onlineLabel.setText(" "); // висота рядка лишається, нічого не стрибає
            onlineLabel.setVisible(true);
            quickJoinBox.setVisible(false);
            play.setLabel(tr("ui2.play"), null);
            return;
        }
        int row = instancesTable.getSelectedRow();
        Object icon = row >= 0 ? instancesModel.getValueAt(row, 0) : null;
        dockIcon.setIcon(icon instanceof Icon ? scaledIcon((Icon) icon, 48) : null);
        // 17.11.2026 (власник: «з'їхав напис»): для копії і збірки Modrinth назва самої збірки, а під нею розділ і версія
        // («Forge 1.16.5 · встановлено»), бо «The Pixelmon Modpack · встановлено» не влазило в рядок
        boolean derived = com.skcraft.launcher.swing.InstanceTableModel.isNamed(sel);
        dockTitle.setText(derived ? instancesModel.pickerTitleOf(sel) : instancesModel.titleOf(sel));
        if (InstanceTableModel.isBrowser(sel)) {
            // 17.11.2026: рядок Modrinth не збірка: підказка замість «не встановлено», кнопки сховані (refreshModrinthPage)
            quickJoinBox.setVisible(false);
            onlineLabel.setText(tr("launcher.modrinthDock"));
            onlineLabel.setForeground(Ui.TEXT_2);
            onlineLabel.setVisible(true);
            dockTitle.getParent().revalidate();
            dockTitle.getParent().repaint();
            return;
        }
        String address = !sel.isBuiltIn() ? sel.getServerAddress() : null;
        boolean server = address != null;
        launchButton.setToolTipText(trFor(sel, "tooltip.launch"));
        quickJoinBox.setVisible(server);

        boolean installed = sel.isLocal() && sel.isInstalled();
        String version = derived ? derivedSubtitle(sel)
                : instancesModel.getGroupMembers(sel).size() >= 2 || sel.isBuiltIn()
                ? instancesModel.pickerTitleOf(sel) : null;
        if (server) {
            com.gummer.launcher.server.ServerPing.Status st = online.get(address);
            if (st != null) {
                onlineLabel.setText("● " + tr("launcher.onlineStatus", st.online, st.max));
                onlineLabel.setForeground(Ui.ONLINE);
                onlineLabel.setVisible(true);
            } else if (offlineServers.contains(address)) {
                onlineLabel.setText("● " + tr("launcher.onlineUnavailable"));
                onlineLabel.setForeground(Ui.TEXT_3);
                onlineLabel.setVisible(true);
            } else {
                // 16.11.2026: місце під рядок онлайну зайняте одразу, без стрибка назви, коли прийде пінг
                onlineLabel.setText("● " + tr("launcher.onlineChecking"));
                onlineLabel.setForeground(Ui.TEXT_3);
                onlineLabel.setVisible(true);
            }
        } else {
            String state = !installed ? tr("launcher.versionNotInstalled")
                    : sel.isUpdatePending() ? tr("launcher.versionUpdate") : tr("launcher.versionInstalled");
            if (instancesModel.isFreshInstall(sel)) { // 20.11.2026: у «Додати інсталяцію» без стану «встановлено»
                onlineLabel.setText(version != null ? version : "");
            } else {
                onlineLabel.setText(version != null ? version + "  ·  " + state : state);
            }
            onlineLabel.setForeground(installed && sel.isUpdatePending() ? Ui.WARN : Ui.TEXT_2);
            onlineLabel.setVisible(true);
        }

        // підпис кнопки: що саме станеться
        String label = !sel.isLocal() || !sel.isInstalled() ? tr("ui2.install")
                : sel.isUpdatePending() ? tr("ui2.update") : tr("ui2.play");
        String caption = version != null ? version
                : !sel.isLocal() || !sel.isInstalled() ? tr("ui2.install.caption")
                : sel.isUpdatePending() ? tr("ui2.update.caption") : null;
        if (instancesModel.isFreshInstall(sel)) {
            // 20.11.2026: у «Додати інсталяцію» ваніль і завантажувачі завжди «Встановити»; під кнопкою, скільки таких уже є
            label = tr("ui2.install");
            String have = CatalogInstall.haveCaption(launcher, sel);
            caption = version != null ? (have != null ? version + " · " + have : version) : have;
        }
        play.setLabel(label, caption);
        dockTitle.getParent().revalidate();
        dockTitle.getParent().repaint();
    }

    private final java.util.Map<Icon, Icon> bigIcons = new java.util.WeakHashMap<>();

    /** Іконка збірки 32 px, збільшена для панелі запуску (з оригіналу, у фізичних пікселях, без розмиття). */
    private Icon scaledIcon(Icon icon, int size) {
        return bigIcons.computeIfAbsent(icon, i -> {
            if (i instanceof com.skcraft.launcher.swing.CrispIcon) return ((com.skcraft.launcher.swing.CrispIcon) i).resized(size, size);
            java.awt.image.BufferedImage src = new java.awt.image.BufferedImage(i.getIconWidth(), i.getIconHeight(), java.awt.image.BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = src.createGraphics();
            i.paintIcon(null, g, 0, 0);
            g.dispose();
            return new com.skcraft.launcher.swing.CrispIcon(src, size, size);
        });
    }

    /** Вміст рядка списку збірок (PackList): назва, стан, онлайн, підпис розділу над першим сервером і першою версією. */
    private PackList.Row packRow(int r) {
        if (r < 0 || r >= instancesModel.getRowCount()) return null;
        Instance i = instancesModel.getInstanceAt(r);
        PackList.Row row = new PackList.Row();
        Object icon = instancesModel.getValueAt(r, 0);
        row.icon = icon instanceof Icon ? (Icon) icon : null;
        row.title = instancesModel.titleOf(i);
        // 17.11.2026: оновлення це зелена стрілка на іконці (InstanceTableModel), текст у підказці
        row.tooltip = InstanceTableModel.updatesText(i);
        boolean server = !i.isBuiltIn();
        boolean prevServer = r > 0 && !instancesModel.getInstanceAt(r - 1).isBuiltIn();
        if (r == 0 || server != prevServer) row.header = tr(server ? "ui2.section.servers" : "ui2.section.minecraft");
        if (instancesModel.isInstalledOnly()) { // 17.11.2026: у «Встановлених» розділи як в «Усі»
            row.header = instancesModel.installedCaption(i, r > 0 ? instancesModel.getInstanceAt(r - 1) : null);
        }
        if (InstanceTableModel.isBrowser(i) && !InstanceTableModel.isAddRow(i)) row.header = tr("launcher.section.modrinth"); // 17.11.2026
        if (InstanceTableModel.isImportRow(i)) row.header = null; // 19.11.2026: під Modrinth, у тому ж розділі
        boolean grouped = instancesModel.getGroupMembers(i).size() >= 2;
        if (InstanceTableModel.isBrowser(i)) {
            row.sub = tr(InstanceTableModel.isAddRow(i) ? "launcher.addBuildSub" : InstanceTableModel.isImportRow(i)
                    ? "launcher.importRowSub" : "launcher.modrinthRowSub"); // 17.11.2026, 19.11.2026 імпорт
            row.subColor = Ui.TEXT_3;
            return row;
        }
        if (instancesModel.isInstalledOnly() && InstanceTableModel.isNamed(i) && i.isInstalled() && !i.isUpdatePending()) {
            row.sub = derivedSubtitle(i); // «Встановлені»: копія чи модпак окремим рядком, під назвою розділ і версія
            return row;
        }
        if (i.isOrphaned()) {
            row.sub = tr("ui2.state.orphaned");
            row.subColor = Ui.TEXT_3;
        } else if (!i.isLocal()) {
            row.sub = grouped || i.isBuiltIn() ? instancesModel.pickerTitleOf(i) : tr("launcher.versionNotInstalled");
            row.subColor = Ui.TEXT_3;
        } else if (grouped || i.isBuiltIn()) {
            // 17.11.2026 ~23:15 (власник: «жовте «є оновлення» ... так, обов'язково, зводить спантелику»): рядок розділу
            // (Vanilla, завантажувачі) показує версію, як у склі. Жовте «є оновлення» стояло тут через недовстановлену
            // теку чи прапорець оновлення списку версій, хоча оновлювати нічого. Оновлення збірок показує зелена стрілка
            row.sub = instancesModel.pickerTitleOf(i);
            if (!i.isInstalled()) row.subColor = Ui.TEXT_3;
        } else if (!i.isInstalled()) {
            // сервер, встановлення якого не закінчене: не «є оновлення», а як є
            row.sub = tr("launcher.versionNotInstalled");
            row.subColor = Ui.TEXT_3;
        } else if (i.isUpdatePending()) {
            // справжнє оновлення модпака сервера (StalDayZ, GummerCraft)
            row.sub = tr("launcher.versionUpdate");
            row.subColor = Ui.WARN;
        } else {
            row.sub = grouped || i.isBuiltIn() ? instancesModel.pickerTitleOf(i) : tr("launcher.versionInstalled");
        }
        if (!instancesModel.isInstalledOnly()) row.sub = null; // 20.11.2026: у «Додати інсталяцію» без другого рядка
        String address = server ? i.getServerAddress() : null;
        com.gummer.launcher.server.ServerPing.Status st = address != null ? online.get(address) : null;
        if (st != null) row.online = String.valueOf(st.online);
        return row;
    }

    /** Висоти рядків (підписи розділів) і «порожньо», коли список уже завантажився, а збірок немає. */
    private void refreshListLayout() {
        PackList.updateHeights(instancesTable, this::packRow);
        listCards.show(listHolder, instancesLoadedOnce && instancesModel.getRowCount() == 0 ? "empty" : "list");
    }

    /** Порожній список: що сталося і що зробити, з кнопкою повтору. */
    private JPanel createEmptyState() {
        JPanel p = new JPanel(new MigLayout("insets 32 24 0 24, wrap 1, gap 0", "[grow,fill]", "[]8[]16[]"));
        p.setOpaque(false);
        JLabel title = new JLabel(tr("ui2.empty.title"));
        title.setFont(Ui.font(Font.BOLD, 14f));
        title.setForeground(Ui.TEXT);
        JLabel text = new JLabel("<html>" + SwingHelper.htmlEscape(tr("ui2.empty.text")) + "</html>");
        text.setFont(Ui.font(Font.PLAIN, 13f));
        text.setForeground(Ui.TEXT_2);
        Btn retry = new Btn(tr("ui2.empty.retry"), Glyph.REFRESH, Btn.Style.TONAL);
        retry.addActionListener(e -> loadInstances());
        p.add(title, "wmin 0");
        p.add(text, "wmin 0, w 200:216:216");
        p.add(retry, "growx 0");
        return p;
    }

    /**
     * Онлайн серверів паків і карток, у фоні (14.11.2026): раз на 30 с, поки вікно лаунчера активне, раз на 90 с,
     * поки воно відкрите, але не в фокусі; згорнуте чи сховане (гра запущена) не пінгує зовсім. Щойно вікно знову
     * активне, онлайн оновлюється одразу (якщо минуло понад 10 с). Справжній рілтайм (сервер сам шле зміни) не
     * потрібен: пінг серверного списку легкий, і 30 с для гравця виглядає як живе число.
     */
    private void startOnlinePolling() {
        if (onlineTimer == null) {
            onlineTimer = new javax.swing.Timer(10_000, e -> maybePollOnline(false));
            onlineTimer.start();
            addWindowListener(new java.awt.event.WindowAdapter() {
                @Override
                public void windowActivated(java.awt.event.WindowEvent e) {
                    maybePollOnline(true);
                }

                @Override
                public void windowDeiconified(java.awt.event.WindowEvent e) {
                    maybePollOnline(true);
                }
            });
        }
        pollOnline();
    }

    private static final long ONLINE_ACTIVE_MS = 30_000L;
    private static final long ONLINE_BACKGROUND_MS = 90_000L;
    private static final long ONLINE_REFOCUS_MS = 10_000L;

    private javax.swing.Timer onlineTimer;
    private long onlinePolledAt;
    private boolean onlinePolling;

    private void maybePollOnline(boolean refocus) {
        if (onlinePolling || !isShowing() || (getExtendedState() & ICONIFIED) != 0) return;
        long every = refocus ? ONLINE_REFOCUS_MS : isActive() ? ONLINE_ACTIVE_MS : ONLINE_BACKGROUND_MS;
        if (System.currentTimeMillis() - onlinePolledAt >= every) pollOnline();
    }

    private void pollOnline() {
        final java.util.Set<String> addresses = new java.util.LinkedHashSet<>();
        synchronized (launcher.getInstances()) {
            for (Instance i : launcher.getInstances().getInstances()) {
                if (!i.isBuiltIn() && i.getServerAddress() != null) addresses.add(i.getServerAddress());
            }
        }
        for (com.gummer.launcher.news.NewsFeed.Item n : news) {
            if (n.online != null) addresses.add(n.online);
        }
        if (addresses.isEmpty() || onlinePolling) return;
        onlinePolling = true;
        onlinePolledAt = System.currentTimeMillis();
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
                onlinePolling = false;
                synchronized (launcher.getInstances()) {
                    for (Instance i : launcher.getInstances().getInstances()) {
                        com.gummer.launcher.server.ServerPing.Status s = i.getServerAddress() != null ? online.get(i.getServerAddress()) : null;
                        instancesModel.setOnlineLine(i.getName(), s != null ? String.valueOf(s.online) : null);
                    }
                }
                if (instancesModel.getRowCount() > 0) {
                    instancesModel.fireTableRowsUpdated(0, instancesModel.getRowCount() - 1);
                }
                instancesTable.repaint();
                refreshStatusRow();
                // набір карток міг змінитись (пак з hideIfInstalled встановили): тоді перебудувати, інакше лише цифри
                if (!visibleNews().equals(shownNews)) rebuildNews(); else refreshNewsOnline();
            }
        }.execute();
    }

    /** Мітки онлайну на картках новин: оновлюються на місці, без перебудови карток (інакше блимала б картинка). */
    private final java.util.List<Object[]> newsOnlineLabels = new java.util.ArrayList<>();

    private void refreshNewsOnline() {
        for (Object[] o : newsOnlineLabels) {
            JLabel l = (JLabel) o[0];
            com.gummer.launcher.server.ServerPing.Status st = online.get((String) o[1]);
            String text = st != null ? "● " + tr("launcher.onlineLine", st.online) : "";
            if (!text.equals(l.getText())) {
                l.setText(text);
                l.revalidate();
                l.repaint();
            }
        }
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
    private java.util.List<com.gummer.launcher.news.NewsFeed.Item> shownNews = java.util.Collections.emptyList();

    private java.util.List<com.gummer.launcher.news.NewsFeed.Item> visibleNews() {
        java.util.List<com.gummer.launcher.news.NewsFeed.Item> out = new java.util.ArrayList<>();
        for (com.gummer.launcher.news.NewsFeed.Item n : news) {
            if (out.size() >= 3) break;
            if (n.hideIfInstalled != null && isInstalled(n.hideIfInstalled)) continue;
            out.add(n);
        }
        return out;
    }

    private void rebuildNews() {
        newsRow.removeAll();
        newsOnlineLabels.clear();
        shownNews = visibleNews();
        for (com.gummer.launcher.news.NewsFeed.Item n : shownNews) {
            newsRow.add(createNewsCard(n), "w 280!, h 120!");
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
                com.gummer.launcher.look.Edge.pill(g2, 0, 0, getWidth(), getHeight(), Ui.RADIUS * 2, Ui.alpha(Ui.SURFACE, 240), Ui.LINE); // 20.11.2026: по фізичних пікселях
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
        onlineText.setForeground(Ui.ONLINE);
        onlineText.setFont(onlineText.getFont().deriveFont(Font.BOLD, 12f));
        bottomRow.add(onlineText);
        if (n.online != null) newsOnlineLabels.add(new Object[]{onlineText, n.online});
        if (n.button != null && n.action != null) {
            JButton go = new JButton(n.button);
            go.setFocusable(false);
            go.putClientProperty("FlatLaf.style", "background: #292C33; foreground: #F2F3F5; hoverBackground: #34373E; font: bold; arc: 8; borderWidth: 0; focusWidth: 0");
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
                        owner != null ? owner : LauncherFrameFlat2.this, supportService, this::getSelectedInstance, topic, text);
        com.gummer.launcher.ui.UiHooks.instanceSettings = (owner, instance) -> InstanceSettingsDialogFlat.open(LauncherFrameFlat2.this, instance);
        com.gummer.launcher.ui.UiHooks.reinstall = (owner, instance) -> confirmHardUpdate(instance);
        com.gummer.launcher.ui.UiHooks.openMods = (owner, instance) -> openManage(instance, InstanceManageDialog.MODS);
        com.gummer.launcher.ui.UiHooks.openWorlds = (owner, instance) -> openManage(instance, InstanceManageDialog.WORLDS);
        com.gummer.launcher.ui.UiHooks.rollback = (owner, instance) -> MrpackUpdateFlow.rollback(LauncherFrameFlat2.this, launcher, instance, null);
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
            g2.clip(new java.awt.geom.RoundRectangle2D.Float(0, 0, getWidth() + 16, getHeight(), 16, 16));
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

    /** Куди веде кнопка Discord (за замовчуванням - з налаштувань лаунчера; FlatLauncherFrame2 підставляє адресу вибраної збірки). */
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
        // редизайн 2: на кнопках Telegram і Discord лише знак сервісу, без назви каналу, тож своєї картинки для збірки
        // не треба; адреса збірки (telegram.url, discord.url) і далі береться при натисканні
    }

    /** Куди веде кнопка "Вікі та FAQ" (за замовчуванням - з налаштувань лаунчера; FlatLauncherFrame2 підставляє вікі вибраної збірки). */
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
    /** 17.11.2026: вікно «Керування збіркою» для збірки (кнопка «Збірка» біля «Грати» і меню збірки). */
    private void openManage(Instance instance, int page) {
        if (InstanceTableModel.isBrowser(instance)) return;
        if (instance == null) return;
        CoachMark.manageSeen(launcher, manageButton);
        InstanceManageDialog.open(this, launcher, instance, page, new InstanceManageDialog.Host() {
            @Override
            public void instanceChanged() {
                refreshInstancesQuietly();
            }

            @Override
            public void copyCreated(String name) {
                launcher.getConfig().setLastInstance(name);
                com.skcraft.launcher.persistence.Persistence.commitAndForget(launcher.getConfig());
                loadInstances();
            }

            @Override
            public void deleteInstance(Instance i) {
                confirmDelete(i);
            }

            @Override
            public void playWorld(Instance i, String world) {
                LauncherFrameFlat2.this.playWorld(i, world);
            }

            @Override
            public void buildUpdated(Instance i) {
                InstanceManageDialog.installOnly(LauncherFrameFlat2.this, launcher, i, LauncherFrameFlat2.this::refreshInstancesQuietly);
            }

            @Override
            public void artChanged() {
                refreshInstancesQuietly();
                packArtChanged();
            }
        });
    }

    /** 17.11.2026: своя іконка чи фон копії змінились (InstanceArt); FlatLauncherFrame* перемальовує фон. */
    protected void packArtChanged() {
    }

    private void popupInstanceMenu(Component component, int x, int y, final Instance selected) {
        if (InstanceTableModel.isBrowser(selected)) return;
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

            // 17.11.2026: вікно «Керування збіркою» (назва, копія, моди, ресурспаки, світи, скріншоти)
            if (InstanceManageDialog.needsInstall(selected)) {
                // 17.11.2026: встановити чи оновити без запуску гри
                menuItem = new JMenuItem(tr(selected.isInstalled() ? "manage.install.menuUpdate" : "manage.install.menu"), widgetIcon);
                menuItem.addActionListener(e -> InstanceManageDialog.installOnly(this, launcher, selected, this::refreshInstancesQuietly));
                popup.add(menuItem);
            }
            menuItem = new JMenuItem(tr("manage.menu"), widgetIcon);
            menuItem.addActionListener(e -> openManage(selected, InstanceManageDialog.GENERAL));
            popup.add(menuItem);
            // 17.11.2026 (власник: «пкм -> дублювати (зробити копію прямо там, швидко). Але не для сталдейзу»)
            if (InstanceManageDialog.canQuickCopy(selected)) {
                menuItem = new JMenuItem(tr("manage.copy.quick"), widgetIcon);
                menuItem.addActionListener(e -> InstanceManageDialog.quickCopy(this, launcher, selected, this::showNewBuild));
                popup.add(menuItem);
            }
            // 17.11.2026 (власник: «"Продовжити останній світ" - ок, го»): одразу в останній зіграний світ (1.20+)
            final java.io.File lastWorld = com.gummer.launcher.manage.Worlds.canQuickPlay(selected)
                    ? com.gummer.launcher.manage.Worlds.last(selected) : null;
            if (lastWorld != null) {
                menuItem = new JMenuItem(tr("launcher.continueWorld", lastWorld.getName()), widgetIcon);
                menuItem.addActionListener(e -> playWorld(selected, lastWorld.getName()));
                popup.add(menuItem);
            }
            // 17.11.2026: у StalDayZ розділу «Моди» немає (набір модів лише наш)
            if (!selected.isVanilla() && !com.gummer.launcher.manage.InstanceMods.packModsLocked(selected)) {
                menuItem = new JMenuItem(tr("manage.menuMods"), widgetIcon);
                menuItem.addActionListener(e -> openManage(selected, InstanceManageDialog.MODS));
                popup.add(menuItem);
            }

            if (selected.isLoaderPack()) {
                // 1.33: версія завантажувача (закріплена в fabric-loader.txt, forge-loader.txt, neoforge-loader.txt)
                // і тека модів гравця
                menuItem = new JMenuItem(tr("loader.menu", selected.isFabric() ? "Fabric Loader"
                        : selected.isQuilt() ? "Quilt Loader" : com.gummer.launcher.forge.ForgeVersions.kindOf(selected).title), javaIcon);
                menuItem.addActionListener(e -> chooseLoader(selected));
                popup.add(menuItem);
            }
            if (selected.isLoaderPack() || selected.isMrpack()) {
                if (selected.isLocal()) {
                    menuItem = new JMenuItem(tr("fabric.openMods"), folderIcon);
                    menuItem.addActionListener(ActionListeners.browseDir(
                            LauncherFrameFlat2.this, new File(selected.getContentDir(), "mods"), true));
                    popup.add(menuItem);
                }
            }

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
                        LauncherFrameFlat2.this, selected.getContentDir(), true));
                popup.add(menuItem);

                if (!com.gummer.launcher.manage.InstanceMods.packModsLocked(selected)) { // StalDayZ: сторонні ресурспаки заборонені
                    menuItem = new JMenuItem(SharedLocale.tr("instance.openResourcePacks"), widgetIcon);
                    menuItem.addActionListener(ActionListeners.browseDir(
                            LauncherFrameFlat2.this, new File(selected.getContentDir(), "resourcepacks"), true));
                    popup.add(menuItem);

                }

                menuItem = new JMenuItem(SharedLocale.tr("instance.openScreenshots"), imageIcon);
                menuItem.addActionListener(ActionListeners.browseDir(
                        LauncherFrameFlat2.this, new File(selected.getContentDir(), "screenshots"), true));
                popup.add(menuItem);

                menuItem = new JMenuItem(SharedLocale.tr("instance.openCrashReports"), crashIcon);
                menuItem.addActionListener(ActionListeners.browseDir(
                    LauncherFrameFlat2.this, new File(selected.getContentDir(), "crash-reports"), true));
                popup.add(menuItem);

                popup.addSeparator();

                if (!selected.isMrpack() && !selected.isCopy()) { // Modrinth і копії: перевстановлення стерло б моди гравця
                    menuItem = new JMenuItem(trFor(selected, "instance.hardForceUpdate"), reinstallIcon);
                    menuItem.addActionListener(new ActionListener() {
                        @Override
                        public void actionPerformed(ActionEvent e) {
                            confirmHardUpdate(selected);
                        }
                    });
                    popup.add(menuItem);
                }

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

        // 1.33: імпорт збірки з Modrinth (.mrpack)
        if (com.gummer.launcher.modrinth.MrpackPacks.ENABLED) {
            // 20.11.2026: роздільник лише під пунктами збірки; клік по порожньому місцю давав порожню смужку зверху
            if (popup.getComponentCount() > 0) popup.addSeparator();
            menuItem = new JMenuItem(tr("mrpack.importMenu"), downloadIcon);
            menuItem.addActionListener(e -> importMrpack());
            popup.add(menuItem);
            // 19.11.2026: збірки з Prism, CurseForge, ATLauncher, Modrinth App, офіційного лаунчера, TLauncher
            menuItem = new JMenuItem(tr("import.menu"), downloadIcon);
            menuItem.addActionListener(e -> importOther());
            popup.add(menuItem);
        }

        popup.show(component, x, y);
    }

    /** 1.33: вибрати .mrpack, імпортувати (моди качаються одразу), оновити список і вибрати нову збірку. */
    /** 19.11.2026: імпорт збірок з інших лаунчерів (ImportOtherDialog), потім показати останню імпортовану. */
    private void importOther() {
        ImportOtherDialog.show(this, launcher, name -> {
            if (name == null) {
                loadInstances();
                return;
            }
            // 19.11.2026: як модпак Modrinth: збірка вибирається, одразу ставляться гра і завантажувач, і список
            // перемикається на «Встановлені», де вона тепер є
            Instance added = showNewBuild(name);
            if (added != null) InstanceManageDialog.installOnly(this, launcher, added, () -> {
                refreshInstancesQuietly();
                if (listSwitchRef != null) listSwitchRef.setInstalledOnly(true);
                SwingUtilities.invokeLater(() -> reselect(name));
            });
        });
    }

    private void importMrpack() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle(tr("mrpack.chooseTitle"));
        chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("Modrinth (.mrpack, .zip)", "mrpack", "zip"));
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        final File file = chooser.getSelectedFile();
        final com.gummer.launcher.modrinth.MrpackPacks importer = new com.gummer.launcher.modrinth.MrpackPacks();
        com.google.common.util.concurrent.ListenableFuture<String> task = launcher.getExecutor().submit(
                () -> importer.importFile(launcher, file));
        ObservableFuture<String> future = new ObservableFuture<>(task, importer);
        ProgressDialog.showProgress(this, future, tr("mrpack.progressTitle"), tr("mrpack.progressText", file.getName()));
        SwingHelper.addErrorDialogCallback(this, future);
        future.addListener(() -> {
            try {
                String name = task.get();
                launcher.getConfig().setLastInstance(name);
                loadInstances();
            } catch (Exception ignored) {
                // помилку вже показав addErrorDialogCallback
            }
        }, SwingExecutor.INSTANCE);
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
                    LauncherFrameFlat2.rootFrame,
                    LauncherFrameFlat2.class,
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
                instancesReady();
            }
        }, SwingExecutor.INSTANCE);

        ProgressDialog.showProgress(this, future, SharedLocale.tr("launcher.checkingTitle"), SharedLocale.tr("launcher.checkingStatus"));
        SwingHelper.addErrorDialogCallback(this, future);
    }

    /** Список збірок готовий (після завантаження або одразу при перемиканні вигляду): показати і вибрати. */
    private void instancesReady() {
        instancesLoadedOnce = true;
        instancesModel.update();
        if (instancesTable.getRowCount() > 0) {
            selectRememberedInstance();
        }
        refreshListLayout();
        requestFocus();
        startOnlinePolling();
        loadNews();
        com.gummer.launcher.manage.UpdateBadges.refresh(launcher, this::badgesChanged);
    }

    /** Швидкий перший показ: список збірок лише з кешів на диску (мілісекунди, без мережі). false - кешу немає. */
    private boolean loadInstancesFromCache() {
        try {
            launcher.getInstances().createEnumerator(true).call();
            return launcher.getInstances().size() > 0;
        } catch (Exception e) {
            log.log(java.util.logging.Level.INFO, "No cached instance list yet, loading from the network", e);
            return false;
        }
    }

    /** Справжній список з мережі у фоні, без діалогу; вибрана збірка лишається вибраною (одна подія EDT, без мигання). */
    private void refreshInstancesQuietly() {
        com.skcraft.launcher.InstanceList.Enumerator loader = launcher.getInstances().createEnumerator();
        ObservableFuture<InstanceList> future = new ObservableFuture<InstanceList>(launcher.getExecutor().submit(loader), loader);
        future.addListener(() -> {
            Instance current = getSelectedInstance();
            String keep = current != null ? current.getName() : null;
        instancesLoadedOnce = true;
        // ті самі рядки: без повного оновлення таблиці (воно скидає вибір, і список «блимав»), лише нові дані версій
        if (instancesModel.updateKeepingRows()) {
            refreshVersionBox();
            refreshStatusRow();
        } else if (instancesTable.getRowCount() > 0) {
            reselect(keep);
        }
        refreshListLayout();
        }, SwingExecutor.INSTANCE);
    }


    // ---------------------------------------------------------------- 17.11.2026: «Усі / Встановлені» і сторінка Modrinth
    /** Головна область: «main» (як завжди) або «modrinth» (сторінка модпаків, коли зліва вибрано рядок Modrinth). */
    private final CardLayout mainCards = new CardLayout();
    private final JPanel mainHolder = new JPanel(mainCards);
    private ModrinthPage modrinthPage;

    private com.gummer.launcher.look.ListModeSwitch listSwitchRef;
    /** 20.11.2026: ще не вирішено, чи лишити «Встановлені» на старті (див. selectRememberedInstance). */
    private boolean startupTab = true;

    /** Перемикач над списком збірок: «Усі» або «Встановлені» (запам'ятовується в config.json). */
    private JComponent createListSwitch() {
        return listSwitchRef = new com.gummer.launcher.look.ListModeSwitch(tr("launcher.listAll"), tr("launcher.listInstalled"),
                launcher.getConfig().isInstalledOnly(), this::setInstalledOnly);
    }

    private JPanel wrapMain(JComponent main) {
        mainHolder.setOpaque(false);
        mainHolder.add(main, "main");
        mainHolder.add(new com.gummer.launcher.look.EmptyInstalled(() -> {
            if (listSwitchRef != null) listSwitchRef.setInstalledOnly(false);
        }, this::importOther), "empty");
        modrinthPage = new ModrinthPage(launcher, name -> {
            // 17.11.2026 (власник: «чому після скачки я його маю окремо знайти в списку і встановити?»): модпак скачано,
            // збірка вибирається і одразу ставляться гра і завантажувач, без ще одного натискання «Встановити»
            Instance added = showNewBuild(name);
            // 20.11.2026: після встановлення «Встановлені» на цій збірці, гра не запускається
            if (added != null) InstanceManageDialog.installOnly(this, launcher, added, () -> afterCatalogInstall(added.getName()));
        }, this::playBuild);
        mainHolder.add(modrinthPage, "modrinth");
        return mainHolder;
    }

    /**
     * 17.11.2026: нова збірка (модпак Modrinth, «Дублювати»): список перечитується лише з диска (мілісекунди, без мережі
     * і без вікна перевірки модпаків) і вибирається вона. null, якщо її не знайдено (тоді звичайне перечитування).
     */
    private Instance showNewBuild(String name) {
        launcher.getConfig().setLastInstance(name);
        com.skcraft.launcher.persistence.Persistence.commitAndForget(launcher.getConfig());
        if (loadInstancesFromCache()) {
            instancesModel.update();
            reselect(name);
            for (Instance i : launcher.getInstances().getInstances()) {
                if (i.getName().equals(name)) return i;
            }
        }
        loadInstances();
        return null;
    }

    /** 20.11.2026: після встановлення з «Додати інсталяцію»: «Встановлені», вибрана саме нова збірка, кнопка «Запуск». */
    private void afterCatalogInstall(String name) {
        launcher.getConfig().setLastInstanceInstalled(name);
        showNewBuild(name); // перечитати список з диска (нова копія) і запам'ятати її
        if (listSwitchRef != null && !listSwitchRef.isInstalledOnly()) {
            listSwitchRef.setInstalledOnly(true);
        } else {
            reselect(name);
        }
    }

    private void setInstalledOnly(boolean on) {
        launcher.getConfig().setInstalledOnly(on);
        com.skcraft.launcher.persistence.Persistence.commitAndForget(launcher.getConfig());
        // 17.11.2026 (власник: «пам'ять має працювати як "що було відкрите останнє у встановлених" та "що було останнє
        // вибране в усіх"»): кожен режим повертає свій вибір. «+ Додати збірку» відкриває «Усі» на сторінці Modrinth
        // (раніше Modrinth мигав і вибір стрибав на останню збірку)
        if (!on && InstanceTableModel.isAddRow(getSelectedInstance())) launcher.getConfig().setLastInstanceAll(InstanceTableModel.MODRINTH.getName());
        String keep = com.gummer.launcher.manage.ListMemory.recall(launcher.getConfig(), on);
        instancesModel.setInstalledOnly(on);
        instancesModel.update();
        if (instancesTable.getRowCount() > 0) reselect(keep);
        refreshListLayout();
        refreshModrinthPage();
    }

    /** Показати сторінку Modrinth, якщо зліва вибрано її рядок, інакше звичайну головну область. */
    private void refreshModrinthPage() {
        if (InstanceTableModel.isAddRow(getSelectedInstance())) {
            // 19.11.2026: жодної встановленої збірки (у списку лише «+ Додати збірку»): «Встановлені» лишаються
            // відкритими з поясненням (look.EmptyInstalled), раніше список одразу перескакував назад на «Усі»
            if (instancesModel.isInstalledOnly() && instancesModel.getRowCount() <= 1) {
                mainCards.show(mainHolder, "empty");
                launchButton.setVisible(false);
                manageButton.setVisible(false);
                return;
            }
            // 17.11.2026: «+ Додати збірку» у «Встановлених»: показати «Усі» (усі версії і модпаки)
            if (listSwitchRef != null) SwingUtilities.invokeLater(() -> listSwitchRef.setInstalledOnly(false));
            return;
        }
        if (modrinthPage == null) return;
        boolean browse = InstanceTableModel.isBrowser(getSelectedInstance());
        mainCards.show(mainHolder, browse ? "modrinth" : "main");
        if (browse) {
            launchButton.setEnabled(false);
            manageButton.setEnabled(false);
        } else {
            manageButton.setEnabled(true);
        }
        // 17.11.2026 (власник: «що таке "Modrinth - не встановлено"... ми ж не встановлюємо модрінт»)
        launchButton.setVisible(!browse);
        manageButton.setVisible(!browse);
        if (browse) modrinthPage.shown();
    }

    /** Знову вибрати збірку на ім'я (або запам'ятовану), не записуючи це як вибір гравця. */
    private void reselect(String name) {
        if (instancesModel.chooseByName(name)) instancesModel.refreshChoice(); // копія чи збірка Modrinth у розділі: рядок показує саме її
        int row = name != null ? instancesModel.rowOf(name) : -1;
        if (row < 0) {
            selectRememberedInstance();
            return;
        }
        restoringSelection = true;
        try {
            instancesTable.setRowSelectionInterval(row, row);
        } finally {
            restoringSelection = false;
        }
        refreshVersionBox();
    }

    private void showOptions() {
        ConfigurationDialogFlat configDialog = new ConfigurationDialogFlat(this, launcher, LauncherFrameFlat2.rootFrame);
        configDialog.setVisible(true);
    }

    /** 17.11.2026: «Грати» на сторінці Modrinth (встановлений модпак): вибрати збірку і запустити. */
    private void playBuild(String name) {
        Instance i = showNewBuild(name);
        if (i != null && !InstanceTableModel.isBrowser(getSelectedInstance())) launch();
    }

    /**
     * 17.11.2026 (власник: «"Продовжити останній світ" - ок, го»): одразу в цей світ (--quickPlaySingleplayer, 1.20+;
     * разовий запит, див. Worlds і Runner.addQuickJoin).
     */
    private void playWorld(Instance instance, String world) {
        com.gummer.launcher.manage.Worlds.requestQuickPlay(instance, world);
        playBuild(instance.getName());
    }

    /** 17.11.2026: бейджі оновлень (UpdateBadges) змінились: перемалювати список, не скидаючи вибір. */
    private void badgesChanged() {
        if (instancesModel.getRowCount() > 0) instancesModel.refreshChoice();
        refreshListLayout();
        repaint();
    }

    private void launch() {
        boolean permitUpdate = updateCheck.isSelected();
        Instance instance = getSelectedInstance();
        if (InstanceTableModel.isBrowser(instance)) return; // рядок Modrinth: сторінка модпаків, не збірка
        if (instance == null) return;
        // 20.11.2026 (власник: «Додати інсталяцію» це «меню суто для встановлення нових збірок»): ваніль і завантажувачі
        // щоразу ставлять нову інсталяцію, не встановлений сервер ставиться; гра після цього не запускається
        if (!instancesModel.isInstalledOnly()
                && (instancesModel.isFreshInstall(instance) || !(instance.isLocal() && instance.isInstalled()))) {
            CatalogInstall.install(this, launcher, instance, this::showNewBuild, this::afterCatalogInstall);
            return;
        }
        InstanceManageDialog.warnMemory(this, launcher, instance); // 17.11.2026: більше половини пам'яті ПК

        LaunchOptions options = new LaunchOptions.Builder()
                .setInstance(instance)
                .setListener(new LaunchListenerImpl(this))
                .setUpdatePolicy(permitUpdate ? UpdatePolicy.UPDATE_IF_SESSION_ONLINE : UpdatePolicy.NO_UPDATE)
                .setWindow(this)
                .build();
        launcher.getLaunchSupervisor().launch(options);
    }

    private static class LaunchListenerImpl implements LaunchListener {
        private final WeakReference<LauncherFrameFlat2> frameRef;
        private final Launcher launcher;
        private final Instance instance; //status callback

        private LaunchListenerImpl(LauncherFrameFlat2 frame) {
            this.frameRef = new WeakReference<LauncherFrameFlat2>(frame);
            this.launcher = frame.launcher;
            this.instance = frame.getSelectedInstance(); //status callback
        }

        @Override
        public void instancesUpdated() {
            LauncherFrameFlat2 frame = frameRef.get();
            if (frame != null) {
                frame.instancesModel.update();
            }
        }

        @Override
        public void gameStarted() {
            LauncherFrameFlat2 frame = frameRef.get();
            if (frame != null) {
                frame.dispose();
            }
            com.gummer.launcher.look.MemoryTrim.forGame(); // 20.11.2026: не тримати кеш картинок, поки граєш
        }

        @Override
        public void gameClosed() {
            LauncherFrameFlat2 frame = frameRef.get();
            if (frame != null) {
                frame.setVisible(true);
                return;
            }
            launcher.showLauncherWindow();
        }
    }

}
