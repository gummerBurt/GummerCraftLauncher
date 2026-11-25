/*
 * Copyright (C) 2021-2025 GummerCraft <http://gummercraft.fun>
 */

package com.gummer.launcher.dialog;

import com.formdev.flatlaf.util.SystemInfo;
import com.gummer.launcher.i18n.Lang;
import com.gummer.launcher.popup.Notification;
import com.gummer.launcher.ui3.Btn;
import com.gummer.launcher.ui3.Glyph;
import com.gummer.launcher.ui3.PackShelf;
import com.gummer.launcher.ui3.PlayButton;
import com.gummer.launcher.ui3.Switch;
import com.gummer.launcher.ui3.Ui;
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
public class LauncherFrameFlat3 extends JFrame {

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
    public static LauncherFrameFlat3 rootFrame;

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
    private final Btn manageButton = new Btn(tr("ui3.manage"), Glyph.BOX, Btn.Style.HIGHLIGHT).height(64); // 24.11.2026: HIGHLIGHT, найважливіша кнопка виділена
    private final JButton wikiButton;
    /** 1.32: звернення в підтримку (null, якщо в launcher.properties немає supportInboxId). */
    private final com.gummer.launcher.support.SupportService supportService;
    private JButton supportButton;
    private final JButton launchButton;
    /** 1.32: вибір версії для групи паків (наприклад Main), видимий лише коли у вибраної групи кілька версій. */
    private final JComboBox<Instance> versionBox = new JComboBox<Instance>();
    /** 18.11.2026: готовий рядок на кожну версію, щоб HTML не розбирався заново (див. HtmlCellCache). */
    private final HtmlCellCache versionCells = new HtmlCellCache();
    /** Редизайн 3: обгортка вибору версії в ряду дій героя (ховається, коли версія одна). */
    private final JPanel versionPanel = new JPanel(new MigLayout("insets 0, gap 0", "[236!]", "[64!]"));
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
    private final JCheckBox quickJoinBox = new Switch(tr("launcher.quickJoin"));
    //private final JButton refreshButton;  // disable for now refreshBurron
    /** 1.33: плашка необов'язкового акаунта сервера вгорі справа; null, якщо serverAccount=false у launcher.properties. */
    private AccountChip3 accountChip;

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
    public LauncherFrameFlat3(@NonNull Launcher launcher) throws IOException {
        super(tr("launcher.title", launcher.getVersion()));

        this.launcher = launcher;
        instancesModel = new InstanceTableModel(launcher.getInstances(), () -> launcher.getConfig().getLastInstance());
        // 20.11.2026 (власник: відкривати «Встановлені», якщо там щось є): на старті завжди «Встановлені»; жодної
        // встановленої збірки, тоді selectRememberedInstance перемкне на «Додати інсталяцію»
        launcher.getConfig().setInstalledOnly(true);
        instancesModel.setInstalledOnly(true);
        instancesModel.setDesktopRow(true); // 25.11.2026: рядок «Робочий стіл» угорі «Встановлених»
        rootFrame = this;
        LauncherFrameFlat.rootFrame = this; // діалоги (налаштування, збірка) шукають вікно тут, хоч який вигляд вибрано

        // Initialize lang from this.launcher config
        this.lang = Lang.getFromName(launcher.getConfig().getLang());
        this.langCode = lang.getId(); // get langCode

        //
        // load multilang UI resources
        //

        // Редизайн 3 (тест, 16.11.2026): кнопки з векторними іконками і живим текстом замість картинок з вшитим
        // написом (текст перекладається, масштабується і читається екранним диктором).
        telegramButton = new Btn("", Glyph.TELEGRAM, Btn.Style.GHOST).onScene().height(40);
        telegramButton.setToolTipText(SharedLocale.tr("tooltip.telegram"));
        telegramButton.getAccessibleContext().setAccessibleName("Telegram");
        discordButton = new Btn("", Glyph.DISCORD, Btn.Style.GHOST).onScene().height(40);
        discordButton.setToolTipText(SharedLocale.tr("tooltip.discord"));
        discordButton.getAccessibleContext().setAccessibleName("Discord");
        String coffeeUrl = launcher.getProperties().getProperty("coffeeUrl", "").trim();
        coffeeButton = coffeeUrl.isEmpty() ? null : new Btn(tr("ui3.coffee"), Glyph.COFFEE, Btn.Style.GHOST).onScene().height(40);
        if (coffeeButton != null) coffeeButton.setToolTipText(SharedLocale.tr("tooltip.coffee"));
        selfUpdateButton = new Btn(tr("ui3.update.button"), Glyph.UPDATE, Btn.Style.OUTLINE);
        selfUpdateButton.setToolTipText(SharedLocale.tr("tooltip.update"));

        optionsButton = new Btn(tr("ui3.nav.settings"), Glyph.SETTINGS, Btn.Style.GHOST).onScene().height(40);
        optionsButton.setToolTipText(SharedLocale.tr("tooltip.settings"));
        wikiButton = new Btn(tr("ui3.nav.wiki"), Glyph.WIKI, Btn.Style.GHOST).onScene().height(40);
        wikiButton.setToolTipText(SharedLocale.tr("tooltip.faq"));
        supportService = com.gummer.launcher.support.SupportService.create(launcher);
        if (supportService != null) {
            supportButton = new Btn(tr("ui3.nav.support"), Glyph.SUPPORT, Btn.Style.GHOST).onScene().height(40).reserveBadge();
            supportButton.setToolTipText(SharedLocale.tr("tooltip.support"));
        }
        launchButton = new PlayButton();
        launchButton.setToolTipText(SharedLocale.tr("tooltip.launch"));
        ((PlayButton) launchButton).setLabel(tr("ui3.play"), null);
        launchButton.setEnabled(false);

        com.gummer.launcher.prefs.LauncherPrefs.init(launcher.getBaseDir());
        registerUiHooks();

        // configure main windowFrame
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(1194, 725));
        // 17.11.2026 (власник: «хочу щоб лаунчер можна було відкрити на весь екран - дуже бракує»): вікно розгортається
        // і тягнеться; менше за 1194 x 725 не стискається
        setResizable(true);
        // редизайн 3: заголовок вікна в колір теми
        getRootPane().putClientProperty("JRootPane.titleBarBackground", Ui.BG);
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

    /** Редизайн 3: полиця збірок і її заміна, коли список порожній. */
    private final PackShelf shelf = new PackShelf();
    private final CardLayout shelfCards = new CardLayout();
    private final JPanel shelfHolder = new JPanel(shelfCards);
    private boolean instancesLoadedOnce;
    /** Герой: місце під логотип (його малює FlatBackgroundPanel3), мітка розділу, назва, стан, опис, кнопка меню. */
    private final JComponent logoSlot = new JComponent() {
    };
    private final JLabel heroTag = new JLabel(" ") {
        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = Ui.smooth(g);
            g2.setColor(Ui.alpha(Color.WHITE, 36));
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), 6, 6);
            g2.dispose();
            super.paintComponent(g);
        }
    };
    private final JLabel heroTitle = new JLabel(" ");
    private final JTextArea heroDesc = new JTextArea();
    private final Btn moreButton = new Btn("", Glyph.MORE, Btn.Style.TONAL).height(64);
    private JPanel updateNotice;

    /**
     * Редизайн 3 (тест, 16.11.2026): «бібліотека ігор». Фон вибраної збірки на все вікно; поверх нього три шари:
     * <ul>
     *   <li>верхня панель: ліворуч спільнота (Telegram, Discord, Підтримати), праворуч Вікі, Підтримка, Налаштування,
     *       профіль і «Змінити скін»;</li>
     *   <li>герой ліворуч унизу: логотип, розділ і назва, стан, короткий опис, ряд дій (Грати, версія, меню збірки),
     *       перемикач «Одразу на сервер»; праворуч угорі повідомлення (оновлення лаунчера, новини);</li>
     *   <li>полиця збірок унизу: плитки з картинкою кожної збірки, сервери і Minecraft окремими групами.</li>
     * </ul>
     * Таблиця збірок лишається невидимою моделлю вибору: полиця лише показує і міняє її вибір, тож уся логіка
     * (FlatLauncherFrame3, вибір версії, онлайн, меню) працює як раніше.
     */
    /** 20.11.2026: стиснути кнопки верхнього ряду до значків, доки ряд не влізе в ширину вікна (див. initComponents). */
    /** Найбільший правий край плашок верхньої панелі (без рядка прапорців мов, він вирівняний праворуч окремо). */
    private static int rightEdge(JPanel bar) {
        int r = 0;
        for (Component c : bar.getComponents()) {
            if (!c.isVisible() || c instanceof com.gummer.launcher.look.LangFlags) continue;
            r = Math.max(r, c.getX() + c.getWidth());
        }
        return r;
    }

    private void fitTopBar(JPanel bar) {
        if (bar.getWidth() <= 0) return;
        java.util.List<Btn> order = new java.util.ArrayList<>();
        for (JComponent c : new JComponent[]{optionsButton, wikiButton, supportButton, coffeeButton}) {
            if (c instanceof Btn) order.add((Btn) c);
        }
        for (Btn b : order) b.setCompact(false);
        // 22.11.2026: preferredLayoutSize казав «влазить», а «Змінити скін» українською все одно виходила за правий
        // край на мінімальній ширині (проба: знімок вікна 1194 px). Тепер міряємо справжню розкладку: правий край
        // найправішої плашки першого рядка має бути в межах панелі мінус правий відступ (24)
        for (Btn b : order) {
            bar.invalidate();
            bar.doLayout();
            if (rightEdge(bar) <= bar.getWidth() - 24) break;
            b.setCompact(true);
        }
        bar.revalidate();
    }

    private void initComponents() {
        JPanel container = createContainerPanel();
        container.setLayout(new BorderLayout());

        // ---------------------------------------------------------------- верхня панель
        JPanel topBar = new JPanel(new MigLayout("insets 14 24 0 24, gap 8", "[][][]8[]push[][][]12[]12[][]", "[40!, center]6[32!]"));
        getRootPane().putClientProperty(com.gummer.launcher.look.Toast.BELOW, topBar); // сповіщення під кнопками, не на них
        topBar.setOpaque(false);
        topBar.add(telegramButton, "w 40!, h 40!");
        topBar.add(discordButton, "w 40!, h 40!");
        if (coffeeButton != null) topBar.add(coffeeButton); else topBar.add(new JLabel());
        topBar.add(createListSwitch(), "h 34!, w pref!"); // 17.11.2026: полиця «Усі / Встановлені»
        topBar.add(wikiButton);
        if (supportButton != null) {
            topBar.add(supportButton);
            supportButton.addActionListener(e -> {
                ((Btn) supportButton).setBadge(false);
                com.gummer.launcher.dialog.SupportDialog.showFor(this, supportService, this::getSelectedInstance);
            });
            checkSupportReplies();
        } else {
            topBar.add(new JLabel());
        }
        topBar.add(optionsButton);
        JComponent divider = new JComponent() {
            @Override
            protected void paintComponent(Graphics g) {
                g.setColor(Ui.alpha(Color.WHITE, 50));
                g.fillRect(0, 4, 1, getHeight() - 8);
            }
        };
        topBar.add(divider, "w 1!, h 32!");
        if (!"false".equalsIgnoreCase(launcher.getProperties().getProperty("serverAccount", "true").trim())) {
            accountChip = new AccountChip3(launcher, () -> ServerAccountDialog.showFor(this, launcher, supportService,
                    this::getSelectedInstance, () -> accountChip.refresh()));
            SkinChip3 skinChip = new SkinChip3(() -> ServerAccountDialog.showFor(this, launcher, supportService,
                    this::getSelectedInstance, () -> accountChip.refresh(), true));
            topBar.add(accountChip, "w 208!, h 40!");
            topBar.add(skinChip, "w pref+12!, h 40!");
        }
        // 18.11.2026 (власник: «винеси перемикання мов на головну сторінку лаунчера. Просто прапорцями»). Окремим
        // рядком праворуч під акаунтом і скіном, як у склі: у верхньому рядку при мінімальній ширині вікна 1194 вільних
        // лише ~24 px, а прапорці займають 146, і плашка скіна вилазила за край вікна на 122 px (проба 18.11 ~19:40)
        topBar.add(new com.gummer.launcher.look.LangFlags(launcher), "newline, span, al right, h 32!");
        // 20.11.2026 (власник на знімку найменшого вікна: «в бібліотеці вопше піздєц», «Змінити скін» і EN за краєм):
        // перемикач «Встановлені / Додати інсталяцію» ширший за старий, і верхній ряд перестав влазити. Коли не влазить,
        // Налаштування, Вікі, Підтримка і Підтримати по черзі стискаються до значка (назва в підказці)
        final JPanel bar = topBar;
        topBar.addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override
            public void componentResized(java.awt.event.ComponentEvent e) {
                fitTopBar(bar);
            }
        });
        container.add(topBar, BorderLayout.NORTH);

        // ---------------------------------------------------------------- герой
        JPanel hero = new JPanel(new MigLayout("insets 0 24 24 24, gap 0", "[grow,left][300!]", "[grow]"));
        hero.setOpaque(false);

        JPanel notices = new JPanel(new MigLayout("insets 12 0 0 0, wrap 1, gap 0 10", "[300!]", ""));
        notices.setOpaque(false);
        updateNotice = createUpdateNotice();
        notices.add(updateNotice, "hidemode 3, growx");
        JPanel oldExeBanner = createOldExeBanner();
        if (oldExeBanner != null) notices.add(oldExeBanner, "growx");
        newsRow.setOpaque(false);
        newsRow.setLayout(new MigLayout("insets 0, wrap 1, gap 0 10", "[300!]", ""));
        notices.add(newsRow, "growx");
        // 16.11.2026: лайк вигляду (один з IP) і «Фідбек по дизайну» внизу правої колонки
        JPanel heroRight = new JPanel(new BorderLayout());
        heroRight.setOpaque(false);
        heroRight.add(notices, BorderLayout.NORTH);
        heroRightRef = heroRight;
        DesignVote vote = DesignVote.create(this, launcher, supportService, com.gummer.launcher.FancyLauncher.LOOK_LIBRARY);
        voteRef3 = vote;
        if (vote != null) {
            vote.setPreferredSize(new Dimension(300, DesignVote.HEIGHT));
            heroRight.add(vote, BorderLayout.SOUTH);
        }
        hero.add(heroRight, "cell 1 0, growy");

        // 17.11.2026: 700 замість 560, щоб у ряд кнопок влізла «Збірка» (Грати 232 + версія 236 + Збірка ~110 + ⋯ 64)
        JPanel block = new JPanel(new MigLayout("insets 0, wrap 1, gap 0", "[700!]", "[]16[]6[]20[]12[]"));
        block.setOpaque(false);
        block.add(logoSlot, "w 460!, h 120!");
        JPanel meta = new JPanel(new MigLayout("insets 0, gap 10", "[][grow]", "[center]"));
        meta.setOpaque(false);
        heroTag.setFont(Ui.font(Font.BOLD, 11f));
        heroTag.setForeground(Ui.TEXT);
        heroTag.setBorder(BorderFactory.createEmptyBorder(3, 8, 3, 8));
        meta.add(heroTag);
        heroTitle.setFont(Ui.font(Font.BOLD, 15f));
        heroTitle.setForeground(Ui.TEXT);
        meta.add(heroTitle, "split 2, gapright 12");
        onlineLabel.setFont(Ui.font(Font.PLAIN, 14f));
        onlineLabel.setForeground(Ui.TEXT_2);
        meta.add(onlineLabel, "hidemode 3, wmin 0");
        block.add(meta);
        heroDesc.setFont(Ui.font(Font.PLAIN, 14f));
        heroDesc.setForeground(Ui.alpha(Ui.TEXT, 210));
        heroDesc.setLineWrap(true);
        heroDesc.setWrapStyleWord(true);
        heroDesc.setEditable(false);
        heroDesc.setFocusable(false);
        heroDesc.setOpaque(false);
        heroDesc.setBorder(null);
        heroDesc.setHighlighter(null);
        block.add(heroDesc, "w 520!, hidemode 3");
        JPanel actions = new JPanel(new MigLayout("insets 0, gap 12", "[][][][]", "[64!]"));
        actions.setOpaque(false);
        actions.add(launchButton, "w 232!, h 64!");
        setupVersionBox();
        actions.add(versionPanel, "hidemode 3");
        moreButton.setToolTipText(tr("ui3.more"));
        moreButton.getAccessibleContext().setAccessibleName(tr("ui3.more"));
        moreButton.addActionListener(e -> {
            // відкрите меню закривається вже на натисканні, а дія кнопки приходить після відпускання і відкривала
            // його знову: другий клац по «⋯» не закривав меню (16.11.2026, як «Вигляд» у DesignVote)
            if (System.currentTimeMillis() - instanceMenuClosedAt < 400) return;
            Instance sel = getSelectedInstance();
            if (sel != null) popupInstanceMenu(moreButton, 0, moreButton.getHeight() + 4, sel);
        });
        manageButton.setToolTipText(tr("manage.tooltip"));
        manageButton.addActionListener(e -> openManage(getSelectedInstance(), InstanceManageDialog.GENERAL));
        CoachMark.manageTip(this, launcher, manageButton);
        actions.add(manageButton, "h 64!");
        actions.add(moreButton, "w 64!, h 64!");
        block.add(actions);
        quickJoinBox.setToolTipText(tr("launcher.quickJoinTooltip"));
        quickJoinBox.setSelected(com.gummer.launcher.prefs.LauncherPrefs.getBoolean(com.gummer.launcher.prefs.LauncherPrefs.QUICK_JOIN, true));
        quickJoinBox.addActionListener(e -> com.gummer.launcher.prefs.LauncherPrefs.set(
                com.gummer.launcher.prefs.LauncherPrefs.QUICK_JOIN, quickJoinBox.isSelected()));
        block.add(quickJoinBox, "hidemode 1, w pref+16!");
        hero.add(block, "cell 0 0, aligny bottom");
        container.add(wrapMain(hero), BorderLayout.CENTER); // 17.11.2026: або сторінка Modrinth

        // логотип малює фон у межах logoSlot
        logoSlot.addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override
            public void componentMoved(java.awt.event.ComponentEvent e) {
                placeLogo(container);
            }

            @Override
            public void componentResized(java.awt.event.ComponentEvent e) {
                placeLogo(container);
            }
        });
        block.addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override
            public void componentMoved(java.awt.event.ComponentEvent e) {
                placeLogo(container);
            }
        });

        // ---------------------------------------------------------------- полиця збірок
        JScrollPane shelfScroll = new JScrollPane(shelf, ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        shelfScroll.setBorder(BorderFactory.createEmptyBorder());
        shelfScroll.setViewportBorder(null);
        shelfScroll.setOpaque(false);
        shelfScroll.getViewport().setOpaque(false);
        shelfScroll.getHorizontalScrollBar().setUnitIncrement(48);
        // 25.11.2026: полиця гортається коліщатком як сторінка CurseForge, 72 px за поділку, одразу
        com.gummer.launcher.look.WheelScroll.install(shelfScroll, true);
        shelfScroll.getHorizontalScrollBar().putClientProperty("FlatLaf.style",
                "width: 6; thumbArc: 999; trackArc: 999; track: #0F1013; thumb: #3A3D44");
        shelfScroll.getViewport().addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override
            public void componentResized(java.awt.event.ComponentEvent e) {
                shelf.revalidate();
                shelf.repaint();
            }
        });
        shelfHolder.setOpaque(false);
        shelfHolder.add(shelfScroll, "shelf");
        shelfHolder.add(createEmptyState(), "empty");
        shelfHolder.setPreferredSize(new Dimension(0, Ui.SHELF_H));
        container.add(shelfHolder, BorderLayout.SOUTH);
        shelf.getAccessibleContext().setAccessibleName(tr("ui3.shelf"));
        shelf.setListener(new PackShelf.Listener() {
            @Override
            public void select(int index) {
                if (index >= 0 && index < instancesTable.getRowCount() && instancesTable.getSelectedRow() != index) {
                    instancesTable.setRowSelectionInterval(index, index);
                }
            }

            @Override
            public void launch(int index) {
                select(index);
                if (launchButton.isEnabled()) LauncherFrameFlat3.this.launch();
            }

            @Override
            public void menu(int index, Component c, int x, int y) {
                select(index);
                popupInstanceMenu(c, x, y, index < instancesModel.getRowCount() ? instancesModel.getInstanceAt(index) : null);
            }
        });

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
                        LauncherFrameFlat3.rootFrame,
                        LauncherFrameFlat3.class,
                        Notification.Type.WARNING,
                        SharedLocale.tr("notification.popup.update")
                    );
                }
            }
        });

        // sidebar buttons events
        // адреси читаються в момент натискання: у збірки можуть бути власні (FlatLauncherFrame3.discordUrl/telegramUrl/wikiUrl)
        discordButton.addActionListener(e -> SwingHelper.openURL(discordUrl(), this));
        telegramButton.addActionListener(e -> SwingHelper.openURL(telegramUrl(), this));
        if (coffeeButton != null) {
            coffeeButton.addActionListener(e -> SwingHelper.openURL(launcher.getProperties().getProperty("coffeeUrl").trim(), this));
        }
        wikiButton.addActionListener(e -> SwingHelper.openURL(wikiUrl(), this));

        // instancesTable events
        instancesTable.setModel(instancesModel);
        instancesTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) shelf.setSelected(instancesTable.getSelectedRow());
        });
        instancesModel.addTableModelListener(e -> SwingUtilities.invokeLater(this::rebuildShelf));
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
//                launcher.getUpdateManager().checkForUpdate(LauncherFrameFlat3.this);
//                webView.browse(launcher.getNewsURL(), false);
//            }
//        });

        selfUpdateButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                launcher.getUpdateManager().performUpdate(LauncherFrameFlat3.this);
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
     * гравця (FlatLauncherFrame3.updatePackBackground): інакше, коли список ще неповний (ванільні версії Mojang ще не
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
        if (row < 0) row = instancesModel.rowOf(launcher.getConfig().getLastInstance());
        if (row < 0) row = instancesModel.firstSelectableRow(); // 21.11.2026: не заглушка згорнутого розділу
        restoringSelection = true;
        try {
            if (row >= 0) instancesTable.setRowSelectionInterval(row, row);
            else instancesTable.clearSelection(); // 22.11.2026: згорнуто все, вибирати нічого
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
        link.setBorder(BorderFactory.createEmptyBorder(0, 4, 0, 0));
        link.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                SwingHelper.openURL(url.trim(), LauncherFrameFlat3.this);
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
        final JPanel banner = noticeCard();
        banner.setLayout(new MigLayout("insets 12 14 12 12, gap 8", "[grow][][]", "[]8[]"));
        banner.setOpaque(false);
        JLabel text = new JLabel("<html>" + SwingHelper.htmlEscape(tr("launcher.oldExeBanner")) + "</html>");
        text.setForeground(Ui.TEXT);
        text.setFont(Ui.font(Font.BOLD, 13f));
        JButton get = new JButton(tr("launcher.oldExeBannerButton"));
        get.addActionListener(e -> SwingHelper.openURL(url, LauncherFrameFlat3.this));
        JButton close = new JButton("✕");
        close.setToolTipText(tr("launcher.oldExeBannerClose"));
        close.addActionListener(e -> {
            banner.setVisible(false);
            banner.getParent().revalidate();
        });
        banner.add(text, "span 3, growx, wmin 0, wrap");
        banner.add(get, "skip 1");
        banner.add(close);
        JPanel wrap = new JPanel(new MigLayout("insets 0", "[grow,fill]", "[]"));
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
        }, () -> showNotification(LauncherFrameFlat3.rootFrame, LauncherFrameFlat3.class, Notification.Type.INFO,
                SharedLocale.tr("support.newReply")));
    }

    /** Пак у вибраному рядку (для групи: вибрана версія), або null. */
    protected Instance getSelectedInstance() {
        int row = instancesTable.getSelectedRow();
        return row >= 0 && row < instancesModel.getRowCount() ? instancesModel.getInstanceAt(row) : null;
    }

    private void setupVersionBox() {
        versionPanel.setOpaque(false);
        versionPanel.setVisible(false);
        // редизайн 3: підпис групи (versionLabel) не окремим рядком, а дрібно всередині закритого вибору
        versionPanel.add(versionBox, "w 236!, h 64!");
        versionBox.setFocusable(true);
        versionBox.setFont(Ui.font(Font.BOLD, 15f));
        versionBox.putClientProperty("FlatLaf.style", "arc: 8; background: #1F2126; buttonBackground: #1F2126; "
                + "buttonEditableBackground: #1F2126; borderColor: #34373E; focusedBorderColor: #8FD16F; "
                + "buttonArrowColor: #B0B5BD; buttonHoverArrowColor: #F2F3F5; focusWidth: 0; padding: 0,12,0,6");
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
                    boolean derived = instancesModel.isNamedHere(i);
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
                        // закритий список (редизайн 3): дрібно підпис групи, під ним позначка і версія
                        text = "<html><span style=\"font-size:10;color:#9AA0A9;font-weight:normal\">"
                                + SwingHelper.htmlEscape(versionLabel.getText()) + "</span><br>" + dot + mc
                                + SwingHelper.htmlEscape(version) + "</html>";
                    } else {
                        String status = !installed ? tr("launcher.versionNotInstalled")
                                : i.isUpdatePending() ? tr("launcher.versionUpdate") : tr("launcher.versionInstalled");
                        String updates = installed && !i.isUpdatePending() ? instancesModel.updatesShown(i) : null;
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
                    SwingHelper.showErrorDialog(LauncherFrameFlat3.this, tr("loader.unavailable", title), title);
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
                int r = JOptionPane.showConfirmDialog(LauncherFrameFlat3.this, panel, title,
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
                    SwingHelper.showErrorDialog(LauncherFrameFlat3.this, e.toString(), title);
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

    /**
     * 17.11.2026: видимий у межах container, навіть поки вікно ще не показане (раніше isShowing(), і при перемиканні
     * вигляду лого з'являлось уже після показу вікна, окремим кадром).
     */
    private static boolean visibleIn(Component c, Container container) {
        for (Component p = c; p != null; p = p.getParent()) {
            if (p == container) return true;
            if (!p.isVisible()) return false;
        }
        return false;
    }

    /** Логотип збірки малює FlatBackgroundPanel3 у межах logoSlot (координати фону). */
    private void placeLogo(JPanel container) {
        if (!(container instanceof com.gummer.launcher.FlatBackgroundPanel3) || !visibleIn(logoSlot, container)) return;
        Rectangle r = SwingUtilities.convertRectangle(logoSlot.getParent(), logoSlot.getBounds(), container);
        ((com.gummer.launcher.FlatBackgroundPanel3) container).setLogoSlot(r);
    }

    /** Повідомлення «Доступна нова версія лаунчера» з кнопкою; видиме разом з selfUpdateButton. */
    private JPanel createUpdateNotice() {
        JPanel card = noticeCard();
        card.setLayout(new MigLayout("insets 12 14 12 12, gap 10", "[][grow][]", "[center]"));
        JComponent icon = new JComponent() {
            @Override
            protected void paintComponent(Graphics g) {
                Glyph.UPDATE.paint((Graphics2D) g, 0, 0, 18, Ui.ACCENT_TEXT);
            }
        };
        card.add(icon, "w 18!, h 18!");
        JLabel text = new JLabel("<html>" + SwingHelper.htmlEscape(tr("ui3.update.title")) + "</html>");
        text.setFont(Ui.font(Font.BOLD, 13f));
        text.setForeground(Ui.TEXT);
        card.add(text, "wmin 0");
        card.add(selfUpdateButton);
        selfUpdateButton.addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override
            public void componentShown(java.awt.event.ComponentEvent e) {
                card.setVisible(true);
                card.getParent().revalidate();
            }

            @Override
            public void componentHidden(java.awt.event.ComponentEvent e) {
                card.setVisible(false);
                card.getParent().revalidate();
            }
        });
        card.setVisible(false);
        return card;
    }

    /** Тло повідомлень і карток праворуч: поверхня з тонкою рамкою, читається на будь-якому фоні. */
    private static JPanel noticeCard() {
        JPanel p = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = Ui.smooth(g);
                com.gummer.launcher.look.Edge.pill(g2, 0, 0, getWidth(), getHeight(), Ui.RADIUS * 2, Ui.alpha(Ui.SURFACE, 235), Ui.LINE); // 20.11.2026: по фізичних пікселях
                g2.dispose();
            }
        };
        p.setOpaque(false);
        return p;
    }

    /**
     * Герой для вибраної збірки: розділ і назва, стан (онлайн сервера або версія і чи встановлено), опис, «Одразу на
     * сервер» лише в збірок із сервером, і підпис головної кнопки за тим, що вона зробить.
     */
    private void refreshStatusRow() {
        Instance sel = getSelectedInstance();
        PlayButton play = (PlayButton) launchButton;
        launchButton.setEnabled(sel != null);
        moreButton.setEnabled(sel != null);
        if (sel == null) {
            heroTag.setVisible(false);
            heroTitle.setText(" ");
            onlineLabel.setText(" "); // висота рядка лишається, нічого не стрибає
            onlineLabel.setVisible(true);
            heroDesc.setVisible(false);
            quickJoinBox.setVisible(false);
            play.setLabel(tr("ui3.play"), null);
            return;
        }
        String address = !sel.isBuiltIn() ? sel.getServerAddress() : null;
        boolean server = address != null;
        heroTag.setVisible(true);
        heroTag.setText(tr(!sel.isBuiltIn() ? "ui3.kind.server" : "ui3.kind.minecraft").toUpperCase(java.util.Locale.ROOT));
        // 17.11.2026: для копії і збірки Modrinth велика назва самої збірки, під нею розділ і версія (як у Класиці)
        boolean derived = instancesModel.isNamedHere(sel);
        heroTitle.setText(derived ? instancesModel.pickerTitleOf(sel) : instancesModel.titleOf(sel));
        launchButton.setToolTipText(trFor(sel, "tooltip.launch"));
        quickJoinBox.setVisible(server);

        boolean installed = sel.isLocal() && sel.isInstalled();
        boolean versions = instancesModel.getGroupMembers(sel).size() >= 2 || sel.isBuiltIn();
        String version = versions ? instancesModel.pickerTitleOf(sel) : null;
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
            String where = null;
            if (derived) {
                String sec = com.gummer.launcher.manage.InstanceCopies.sectionTitle(sel.getGroup());
                String v = instancesModel.derivedVersionOf(sel);
                where = sec == null ? v : v != null ? sec + " " + v : sec;
            }
            // 20.11.2026: у «Додати інсталяцію» ваніль і завантажувачі без стану «встановлено»
            if (instancesModel.isFreshInstall(sel)) onlineLabel.setText(where != null ? where : "");
            else onlineLabel.setText(where != null ? where + "  ·  " + state : state);
            onlineLabel.setForeground(installed && sel.isUpdatePending() ? Ui.WARN : Ui.TEXT_2);
            onlineLabel.setVisible(true);
        }

        String desc = describe(sel);
        heroDesc.setText(desc == null ? "" : desc);
        heroDesc.setVisible(desc != null);

        String label = !sel.isLocal() || !sel.isInstalled() ? tr("ui3.install")
                : sel.isUpdatePending() ? tr("ui3.update") : tr("ui3.play");
        String caption = version != null ? version
                : !sel.isLocal() || !sel.isInstalled() ? tr("ui3.install.caption")
                : sel.isUpdatePending() ? tr("ui3.update.caption") : null;
        if (instancesModel.isFreshInstall(sel)) {
            // 20.11.2026: у «Додати інсталяцію» ваніль і завантажувачі завжди «Встановити»; під кнопкою, скільки таких уже є
            label = tr("ui3.install");
            String have = CatalogInstall.haveCaption(launcher, sel);
            caption = version != null ? (have != null ? version + " · " + have : version) : have;
        }
        play.setLabel(label, caption);
        heroTitle.getParent().revalidate();
        heroTitle.getParent().repaint();
    }

    /** Короткий опис збірки з наявних підказок вибору версії (лише там, де він є). */
    private static String describe(Instance i) {
        String key = i.isVanilla() ? "launcher.versionLabelVanillaTooltip"
                : i.isFabric() ? "launcher.versionLabelFabricTooltip"
                : i.isQuilt() ? "launcher.versionLabelQuiltTooltip"
                : i.isForgeLike() ? (com.gummer.launcher.forge.ForgeVersions.kindOf(i) == com.gummer.launcher.forge.ForgeVersions.Kind.FORGE
                        ? "launcher.versionLabelForgeTooltip" : "launcher.versionLabelNeoForgeTooltip")
                : "main".equals(i.getGroup()) ? "launcher.versionLabelGummerCraftTooltip" : null;
        if (key == null) return null;
        String text = tr(key);
        return text.startsWith("${") ? null : text;
    }

    /** Іконки збірок 20x20 для плиток (кеш, щоб чітка картинка не готувалась наново при кожному оновленні онлайну). */
    private final java.util.Map<com.skcraft.launcher.swing.CrispIcon, Icon> tileIcons = new java.util.IdentityHashMap<>();

    /** Мініатюри полиці за ключем вигляду. */
    private final java.util.Map<String, Image> thumbs = new java.util.HashMap<>();

    /** Полиця з поточних рядків моделі; онлайн і стани беруться наживо. */
    private void rebuildShelf() {
        java.util.List<PackShelf.Tile> list = new java.util.ArrayList<>();
        for (int r = 0; r < instancesModel.getRowCount(); r++) {
            Instance i = instancesModel.getInstanceAt(r);
            PackShelf.Tile t = new PackShelf.Tile();
            Object icon = instancesModel.getValueAt(r, 0);
            t.icon = icon instanceof com.skcraft.launcher.swing.CrispIcon
                    ? tileIcons.computeIfAbsent((com.skcraft.launcher.swing.CrispIcon) icon, c -> c.resized(20, 20))
                    : icon instanceof Icon ? (Icon) icon : null;
            t.title = instancesModel.titleOf(i);
            // 19.11.2026: плитка «Імпорт збірки» з тим самим фоном, що Modrinth над нею
            String key = InstanceTableModel.isDesktopRow(i) ? com.gummer.launcher.FlatBackgroundPanel.DESKTOP_LOOK // 25.11.2026: плитка «Робочого столу» зі своїм фоном
                    : com.gummer.launcher.FlatBackgroundPanel3.lookKey(InstanceTableModel.isImportRow(i) || InstanceTableModel.isCurseRow(i) ? InstanceTableModel.MODRINTH : i);
            // 17.11.2026 ~23:35: свій фон на плитці лише у «Встановлених», у «Усі» плитка розділу зі своїм оформленням
            final java.awt.image.BufferedImage art = instancesModel.isInstalledOnly() ? com.gummer.launcher.manage.InstanceArt.background(i) : null;
            if (art != null) {
                // 17.11.2026: свій фон копії (InstanceArt); ключ міняється разом з файлом
                java.io.File f = com.gummer.launcher.manage.InstanceArt.backgroundFile(i);
                t.thumb = thumbs.computeIfAbsent("art:" + f.getAbsolutePath() + ":" + f.lastModified(),
                        k -> PackShelf.cover(art, PackShelf.TILE_MAX_W, PackShelf.TILE_MAX_W * 9 / 16));
            } else {
                t.thumb = thumbs.computeIfAbsent(key == null ? "" : key, k -> PackShelf.cover(
                        com.gummer.launcher.FlatBackgroundPanel3.backgroundFor(k), PackShelf.TILE_MAX_W, PackShelf.TILE_MAX_W * 9 / 16));
            }
            boolean server = !i.isBuiltIn();
            boolean prevServer = r > 0 && !instancesModel.getInstanceAt(r - 1).isBuiltIn();
            if (r == 0 || server != prevServer) t.group = tr(server ? "ui3.section.servers" : "ui3.section.minecraft");
            if (instancesModel.isInstalledOnly()) { // 17.11.2026: у «Встановлених» розділи як в «Усі»
                t.group = instancesModel.installedCaption(i, r > 0 ? instancesModel.getInstanceAt(r - 1) : null);
            }
            if (InstanceTableModel.isBrowser(i) && !InstanceTableModel.isAddRow(i) && !InstanceTableModel.isDesktopRow(i)) t.group = tr("launcher.section.modrinth"); // 17.11.2026; 25.11.2026 без «Робочого столу»
            if (InstanceTableModel.isImportRow(i) || InstanceTableModel.isCurseRow(i)) t.group = null; // 19.11.2026: під Modrinth, у тому ж розділі
            boolean browser = InstanceTableModel.isBrowser(i); // 17.11.2026: плитка сторінки Modrinth
            t.notInstalled = !browser && !i.isLocal();
            t.update = i.isLocal() && (!i.isInstalled() || i.isUpdatePending());
            String address = server ? i.getServerAddress() : null;
            com.gummer.launcher.server.ServerPing.Status st = address != null ? online.get(address) : null;
            if (st != null) t.online = String.valueOf(st.online);
            String state = i.isOrphaned() ? tr("ui3.state.orphaned") : t.notInstalled ? tr("launcher.versionNotInstalled")
                    : t.update ? tr("launcher.versionUpdate") : tr("launcher.versionInstalled");
            boolean versions = !browser && (instancesModel.getGroupMembers(i).size() >= 2 || i.isBuiltIn());
            String updates = !browser && !t.update && !t.notInstalled ? instancesModel.updatesShown(i) : null;
            t.updates = updates != null; // 17.11.2026: бейдж оновлень (зелена стрілка на плитці)
            if (updates != null) state = state + " · " + updates;
            t.tooltip = InstanceTableModel.isAddRow(i) ? t.title + " · " + tr("launcher.addBuildSub")
                    : InstanceTableModel.isImportRow(i) ? t.title + " · " + tr("launcher.importRowSub")
                    : InstanceTableModel.isDesktopRow(i) ? t.title + " · " + tr("launcher.desktopRowSub", instancesModel.localBuilds().size())
                    : browser ? t.title + " · " + tr("launcher.modrinthRowSub")
                    : t.title + (versions ? " · " + instancesModel.pickerTitleOf(i) : "") + " · " + state;
            list.add(t);
        }
        shelf.setTiles(list);
        shelf.setSelected(instancesTable.getSelectedRow());
        shelfCards.show(shelfHolder, instancesLoadedOnce && list.isEmpty() ? "empty" : "shelf");
    }

    /** Порожній список: що сталося і що зробити, з кнопкою повтору, на місці полиці. */
    private JPanel createEmptyState() {
        JPanel p = new JPanel(new MigLayout("insets 0 24 0 24, gap 0", "[grow,fill]24[]", "push[]push"));
        p.setOpaque(false);
        JPanel lines = new JPanel(new MigLayout("insets 0, wrap 1, gap 0 4", "[grow,fill]", ""));
        lines.setOpaque(false);
        JLabel title = new JLabel(tr("ui3.empty.title"));
        title.setFont(Ui.font(Font.BOLD, 15f));
        title.setForeground(Ui.TEXT);
        JLabel text = new JLabel(tr("ui3.empty.text"));
        text.setFont(Ui.font(Font.PLAIN, 13f));
        text.setForeground(Ui.TEXT_2);
        lines.add(title);
        lines.add(text);
        Btn retry = new Btn(tr("ui3.empty.retry"), Glyph.REFRESH, Btn.Style.TONAL).height(40);
        retry.addActionListener(e -> loadInstances());
        p.add(lines, "growx");
        p.add(retry);
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
                rebuildShelf();
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
            newsRow.add(createNewsCard(n), "w 300!, h 120!");
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
        final JPanel card = new JPanel(new MigLayout("insets 0, gap 0", "[96!][grow,fill]", "[grow,fill]")) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                com.gummer.launcher.look.Edge.pill(g2, 0, 0, getWidth(), getHeight(), Ui.RADIUS * 2, Ui.alpha(Ui.SURFACE, 235), Ui.LINE); // 20.11.2026: по фізичних пікселях
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
                        owner != null ? owner : LauncherFrameFlat3.this, supportService, this::getSelectedInstance, topic, text);
        com.gummer.launcher.ui.UiHooks.instanceSettings = (owner, instance) -> InstanceSettingsDialogFlat.open(LauncherFrameFlat3.this, instance);
        com.gummer.launcher.ui.UiHooks.reinstall = (owner, instance) -> confirmHardUpdate(instance);
        com.gummer.launcher.ui.UiHooks.openMods = (owner, instance) -> openManage(instance, InstanceManageDialog.MODS);
        com.gummer.launcher.ui.UiHooks.openWorlds = (owner, instance) -> openManage(instance, InstanceManageDialog.WORLDS);
        com.gummer.launcher.ui.UiHooks.rollback = (owner, instance) -> MrpackUpdateFlow.rollback(LauncherFrameFlat3.this, launcher, instance, null);
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

    /** Куди веде кнопка Discord (за замовчуванням - з налаштувань лаунчера; FlatLauncherFrame3 підставляє адресу вибраної збірки). */
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
        // редизайн 3: на кнопках Telegram і Discord лише знак сервісу, без назви каналу, тож своєї картинки для збірки
        // не треба; адреса збірки (telegram.url, discord.url) і далі береться при натисканні
    }

    /** Куди веде кнопка "Вікі та FAQ" (за замовчуванням - з налаштувань лаунчера; FlatLauncherFrame3 підставляє вікі вибраної збірки). */
    protected String wikiUrl() {
        // 22.11.2026 (власник: «кнопка FAQ в лаунчері має вести на https://gummercraft.fun/launcher, а не на faq гк»):
        // сторінка лаунчера мовою інтерфейсу (link.launcherPage); вікі збірки (StalDayZ) як і раніше підставляє FlatLauncherFrame
        return com.skcraft.launcher.util.SharedLocale.tr("link.launcherPage");
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
    /** Коли меню збірки востаннє закрилось (для кнопки «⋯»). */
    private long instanceMenuClosedAt;

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
                LauncherFrameFlat3.this.playWorld(i, world);
            }

            @Override
            public void buildUpdated(Instance i) {
                InstanceManageDialog.installOnly(LauncherFrameFlat3.this, launcher, i, LauncherFrameFlat3.this::refreshInstancesQuietly);
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
        popup.addPopupMenuListener(new javax.swing.event.PopupMenuListener() {
            @Override
            public void popupMenuWillBecomeVisible(javax.swing.event.PopupMenuEvent e) {
            }

            @Override
            public void popupMenuWillBecomeInvisible(javax.swing.event.PopupMenuEvent e) {
                instanceMenuClosedAt = System.currentTimeMillis();
            }

            @Override
            public void popupMenuCanceled(javax.swing.event.PopupMenuEvent e) {
            }
        });
        JMenuItem menuItem;


        if (selected != null) {
            menuItem = new JMenuItem(!selected.isLocal() ? trFor(selected, "instance.install") : trFor(selected, "instance.launch"), !selected.isLocal() ? downloadIcon : gamepadIcon);
            menuItem.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    launchFor(selected); // 25.11.2026: з робочого столу меню відкривається для невибраної збірки
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
                            LauncherFrameFlat3.this, new File(selected.getContentDir(), "mods"), true));
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
                        LauncherFrameFlat3.this, selected.getContentDir(), true));
                popup.add(menuItem);

                if (!com.gummer.launcher.manage.InstanceMods.packModsLocked(selected)) { // StalDayZ: сторонні ресурспаки заборонені
                    menuItem = new JMenuItem(SharedLocale.tr("instance.openResourcePacks"), widgetIcon);
                    menuItem.addActionListener(ActionListeners.browseDir(
                            LauncherFrameFlat3.this, new File(selected.getContentDir(), "resourcepacks"), true));
                    popup.add(menuItem);

                }

                menuItem = new JMenuItem(SharedLocale.tr("instance.openScreenshots"), imageIcon);
                menuItem.addActionListener(ActionListeners.browseDir(
                        LauncherFrameFlat3.this, new File(selected.getContentDir(), "screenshots"), true));
                popup.add(menuItem);

                menuItem = new JMenuItem(SharedLocale.tr("instance.openCrashReports"), crashIcon);
                menuItem.addActionListener(ActionListeners.browseDir(
                    LauncherFrameFlat3.this, new File(selected.getContentDir(), "crash-reports"), true));
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
        chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("Modrinth, CurseForge (.mrpack, .zip)", "mrpack", "zip"));
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
                    LauncherFrameFlat3.rootFrame,
                    LauncherFrameFlat3.class,
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
        rebuildShelf();
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
        rebuildShelf();
        }, SwingExecutor.INSTANCE);
    }


    // ---------------------------------------------------------------- 17.11.2026: «Усі / Встановлені» і сторінка Modrinth
    /** Головна область: «main» (як завжди) або «modrinth» (сторінка модпаків, коли зліва вибрано рядок Modrinth). */
    private final CardLayout mainCards = new CardLayout();
    private final JPanel mainHolder = new JPanel(mainCards);
    private ModrinthPage modrinthPage;
    /** 1.40.0: сторінка модпаків CurseForge (рядок «CurseForge» під Modrinth). */
    private ModrinthPage curseforgePage;

    private com.gummer.launcher.look.ListModeSwitch listSwitchRef;
    /** 20.11.2026: ще не вирішено, чи лишити «Встановлені» на старті (див. selectRememberedInstance). */
    private boolean startupTab = true;

    /** Перемикач над списком збірок: «Усі» або «Встановлені» (запам'ятовується в config.json). */
    private JComponent createListSwitch() {
        return listSwitchRef = new com.gummer.launcher.look.ListModeSwitch(tr("launcher.listAll"), tr("launcher.listInstalled"),
                launcher.getConfig().isInstalledOnly(), this::setInstalledOnly);
    }

    /** 20.11.2026: права колонка порожніх «Встановлених» (див. wrapMain), герой і «Вигляд» з лайком. */
    private final JPanel emptyRight = new JPanel(new BorderLayout());
    private JPanel heroRightRef;
    private DesignVote voteRef3;

    /** 20.11.2026: «Вигляд» з лайком туди, що зараз видно (герой або порожні «Встановлені»). */
    private void placeVote(JPanel target) {
        if (voteRef3 == null || target == null || voteRef3.getParent() == target) return;
        java.awt.Container old = voteRef3.getParent();
        target.add(voteRef3, BorderLayout.SOUTH);
        if (old != null) {
            old.revalidate();
            old.repaint();
        }
        target.revalidate();
        target.repaint();
    }

    private JPanel wrapMain(JComponent main) {
        mainHolder.setOpaque(false);
        mainHolder.add(main, "main");
        // 20.11.2026 (власник на знімку порожніх «Встановлених»: «Де кнопка зміни дизайну?»): «Вигляд» і лайк живуть у
        // правій колонці героя, а картка порожнього стану закривала героя разом з ними. Тепер у неї та сама права
        // колонка (300 px, ті самі відступи), і placeVote переносить туди «Вигляд»; ліва колонка такої ж ширини, щоб
        // картка лишилась по центру
        JPanel emptyWrap = new JPanel(new MigLayout("insets 0 24 24 24, gap 0, fill", "[300!][grow,fill][300!]", "[grow,fill]"));
        emptyWrap.setOpaque(false);
        emptyWrap.add(new JLabel());
        emptyWrap.add(new com.gummer.launcher.look.EmptyInstalled(() -> {
            if (listSwitchRef != null) listSwitchRef.setInstalledOnly(false);
        }, this::importOther), "grow");
        emptyRight.setOpaque(false);
        emptyWrap.add(emptyRight, "growy");
        mainHolder.add(emptyWrap, "empty");
        modrinthPage = new ModrinthPage(launcher, name -> {
            // 17.11.2026 (власник: «чому після скачки я його маю окремо знайти в списку і встановити?»): модпак скачано,
            // збірка вибирається і одразу ставляться гра і завантажувач, без ще одного натискання «Встановити»
            Instance added = showNewBuild(name);
            // 20.11.2026: після встановлення «Встановлені» на цій збірці, гра не запускається
            if (added != null) InstanceManageDialog.installOnly(this, launcher, added, () -> afterCatalogInstall(added.getName()));
        }, this::playBuild);
        mainHolder.add(modrinthPage, "modrinth");
        // 25.11.2026: сторінка «Робочий стіл» (рядок угорі «Встановлених»): усі збірки сіткою чи списком
        desktopPage = new DesktopPage(instancesModel, this::openFromDesktop, this::playFromDesktop,
                (i, c, x, y) -> popupInstanceMenu(c, x, y, i)); // 25.11.2026: правий клік як у списку зліва
        mainHolder.add(desktopPage, "desktop");
        curseforgePage = new ModrinthPage(launcher, true, name -> {
            // 17.11.2026 (власник: «чому після скачки я його маю окремо знайти в списку і встановити?»): модпак скачано,
            // збірка вибирається і одразу ставляться гра і завантажувач, без ще одного натискання «Встановити»
            Instance added = showNewBuild(name);
            // 20.11.2026: після встановлення «Встановлені» на цій збірці, гра не запускається
            if (added != null) InstanceManageDialog.installOnly(this, launcher, added, () -> afterCatalogInstall(added.getName()));
        }, this::playBuild);
        mainHolder.add(curseforgePage, "curseforge");
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
        rebuildShelf();
        refreshModrinthPage();
    }

    /** Показати сторінку Modrinth, якщо зліва вибрано її рядок, інакше звичайну головну область. */
    private void refreshModrinthPage() {
        if (InstanceTableModel.isAddRow(getSelectedInstance())) {
            // 19.11.2026: жодної встановленої збірки (у списку лише «+ Додати збірку»): «Встановлені» лишаються
            // відкритими з поясненням (look.EmptyInstalled), раніше список одразу перескакував назад на «Усі»
            if (instancesModel.isInstalledOnly() && instancesModel.getRowCount() <= 1) {
                placeVote(emptyRight);
                mainCards.show(mainHolder, "empty");
                launchButton.setEnabled(false);
                return;
            }
            // 17.11.2026: «+ Додати збірку» у «Встановлених»: показати «Усі» (усі версії і модпаки)
            if (listSwitchRef != null) SwingUtilities.invokeLater(() -> listSwitchRef.setInstalledOnly(false));
            return;
        }
        if (modrinthPage == null) return;
        boolean browse = InstanceTableModel.isBrowser(getSelectedInstance());
        placeVote(browse && !InstanceTableModel.isDesktopRow(getSelectedInstance()) ? (InstanceTableModel.isCurseRow(getSelectedInstance()) && curseforgePage != null ? curseforgePage : modrinthPage).headerSlot() : heroRightRef);
        boolean curseRow = InstanceTableModel.isCurseRow(getSelectedInstance());
        ModrinthPage page = curseRow && curseforgePage != null ? curseforgePage : modrinthPage;
        boolean desk = InstanceTableModel.isDesktopRow(getSelectedInstance());
        mainCards.show(mainHolder, desk ? "desktop" : browse ? (curseRow ? "curseforge" : "modrinth") : "main");
        if (desk && desktopPage != null) desktopPage.refresh();
        
        if (browse && !desk) page.shown();
    }

    private DesktopPage desktopPage;

    /** 25.11.2026: клік по збірці на «Робочому столі»: вибрати її в списку, як клік там (вибір запам'ятовується). */
    private void openFromDesktop(Instance instance) {
        if (instance == null) return;
        int row = instancesModel.rowOf(instance.getName());
        if (row < 0 && instancesModel.revealSectionOf(instance)) row = instancesModel.rowOf(instance.getName()); // розділ був згорнутий
        if (row < 0) return;
        instancesTable.setRowSelectionInterval(row, row);
        instancesTable.scrollRectToVisible(instancesTable.getCellRect(row, 0, true));
    }

    /**
     * 25.11.2026: «Грати / Встановити» з меню збірки. Меню з робочого столу відкривається для збірки, яку в списку зліва не
     * вибрано (вибір не міняється, щоб сторінка лишалась), тож спершу вибрати її; у списку зліва вона вже вибрана.
     */
    private void launchFor(Instance instance) {
        if (instance != null && getSelectedInstance() != instance) {
            playFromDesktop(instance);
            return;
        }
        launch();
    }

    /** 25.11.2026: кнопка ▶ на «Робочому столі»: вибрати збірку і одразу запустити. */
    private void playFromDesktop(Instance instance) {
        openFromDesktop(instance);
        if (getSelectedInstance() == instance) launch();
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
        ConfigurationDialogFlat configDialog = new ConfigurationDialogFlat(this, launcher, LauncherFrameFlat3.rootFrame);
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
        rebuildShelf();
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
        private final WeakReference<LauncherFrameFlat3> frameRef;
        private final Launcher launcher;
        private final Instance instance; //status callback

        private LaunchListenerImpl(LauncherFrameFlat3 frame) {
            this.frameRef = new WeakReference<LauncherFrameFlat3>(frame);
            this.launcher = frame.launcher;
            this.instance = frame.getSelectedInstance(); //status callback
        }

        @Override
        public void instancesUpdated() {
            LauncherFrameFlat3 frame = frameRef.get();
            if (frame != null) {
                frame.instancesModel.update();
            }
        }

        @Override
        public void gameStarted() {
            LauncherFrameFlat3 frame = frameRef.get();
            if (frame != null && !com.gummer.launcher.look.GameReturn.keepDuringGame()) { // 21.11.2026: gamescope
                frame.dispose();
            }
            com.gummer.launcher.look.MemoryTrim.forGame(); // 20.11.2026: не тримати кеш картинок, поки граєш
        }

        @Override
        public void gameClosed() {
            LauncherFrameFlat3 frame = frameRef.get();
            if (frame != null) {
                com.gummer.launcher.look.GameReturn.reshow(frame); // 25.11.2026: без анімації появи розгорнутого вікна
                com.gummer.launcher.look.GameReturn.shown(frame); // 21.11.2026: сірий екран після гри в ігровому режимі Steam Deck
                return;
            }
            com.gummer.launcher.look.GameReturn.shown(launcher.showLauncherWindow());
        }
    }

}
