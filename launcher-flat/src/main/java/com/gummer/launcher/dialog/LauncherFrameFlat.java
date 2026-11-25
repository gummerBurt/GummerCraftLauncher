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

    /** 17.11.2026: розгорнуте вікно до показу розкладається з тими ж рамками, що й після (див. Zoom.settledInsets). */
    @Override
    public Insets getInsets() {
        return com.gummer.launcher.look.Zoom.settledInsets(this, super.getInsets());
    }

    public final Launcher launcher;
    private final Lang lang;
    private final String langCode;
    /** Поточне головне вікно (будь-якого вигляду: Liquid Glass, Класика, Бібліотека); на нього спливають сповіщення діалогів. */
    public static JFrame rootFrame;

    @Getter
    private final InstanceTable instancesTable = new CardInstanceTable(); // дизайн 1.35: картки з підписами розділів
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
    /** 17.11.2026: «Збірка» біля «Запуску»: вікно «Керування збіркою». */
    private final JButton manageButton;
    private final JButton wikiButton;
    /** 1.32: звернення в підтримку (null, якщо в launcher.properties немає supportInboxId). */
    private final com.gummer.launcher.support.SupportService supportService;
    private JButton supportButton;
    private final JButton launchButton;
    /** 1.32: вибір версії для групи паків (наприклад Main), видимий лише коли у вибраної групи кілька версій. */
    private final JComboBox<Instance> versionBox = new JComboBox<Instance>();
    /** 18.11.2026: готовий рядок на кожну версію, щоб HTML не розбирався заново (див. HtmlCellCache). */
    private final HtmlCellCache versionCells = new HtmlCellCache();
    /** Блок «Версія Minecraft» висотою з кнопку «Грати»: темна напівпрозора підкладка, щоб читалось на будь-якому фоні збірки. */
    // 17.11.2026 ~22:30: 196 замість 204: на найменшому вікні (1194) група «версія + Збірка + Запуск» була на 8 px ширша за
    // місце (з'їдала правий відступ і 2 px сяйва «Запуску»)
    private final JPanel versionPanel = new JPanel(new MigLayout("insets 10 12 12 12, wrap 1, gap 0 6", "[196!]", "[][]")) {
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
    private final JLabel versionLabel = new JLabel(tr("launcher.versionLabel")) {
        // 17.11.2026 (власник: «перевір що ніде нічого не вилазить»): довгий напис («Оптимізована збірка для GummerCraft»)
        // спершу малюється дрібнішим шрифтом (до 10; при 10.5 цей напис на ширині 1194 ще обрізався), і лише якщо й так не влазить, з «...»
        @Override
        protected void paintComponent(Graphics g) {
            String t = getText();
            Insets in = getInsets();
            int avail = getWidth() - in.left - in.right;
            Font f = getFont();
            if (t == null || t.startsWith("<html") || avail <= 0 || getFontMetrics(f).stringWidth(t) <= avail) {
                super.paintComponent(g);
                return;
            }
            float size = f.getSize2D();
            while (size > 10f && getFontMetrics(f.deriveFont(size)).stringWidth(t) > avail) size -= 0.5f;
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                Object hints = Toolkit.getDefaultToolkit().getDesktopProperty("awt.font.desktophints");
                if (hints instanceof java.util.Map) g2.addRenderingHints((java.util.Map<?, ?>) hints);
                g2.setFont(f.deriveFont(size));
                g2.setColor(getForeground());
                FontMetrics fm = g2.getFontMetrics();
                String s = t;
                if (fm.stringWidth(s) > avail) {
                    while (s.length() > 1 && fm.stringWidth(s + "...") > avail) s = s.substring(0, s.length() - 1);
                    s = s.trim() + "...";
                }
                int y = in.top + (getHeight() - in.top - in.bottom - fm.getHeight()) / 2 + fm.getAscent();
                g2.drawString(s, in.left, y);
            } finally {
                g2.dispose();
            }
        }
    };
    /** Картки новин і реклами над кнопками (news.json з CDN). */
    private final JPanel newsRow = new JPanel(new MigLayout("insets 0 12 8 12, gap 12", "", "[]"));
    private java.util.List<com.gummer.launcher.news.NewsFeed.Item> news = java.util.Collections.emptyList();
    /** Онлайн серверів (адреса -> стан), оновлюється раз на хвилину. */
    private final java.util.Map<String, com.gummer.launcher.server.ServerPing.Status> online = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.Set<String> offlineServers = java.util.Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<>());
    private final JLabel onlineLabel = new JLabel(" ") {
        // 1.35: кілька пікселів запасу, інакше на деяких екранах останні літери («онлайн») обрізались
        @Override
        public Dimension getPreferredSize() {
            Dimension d = super.getPreferredSize();
            return new Dimension(d.width + 6, d.height);
        }
    };
    private final JCheckBox quickJoinBox = new JCheckBox(tr("launcher.quickJoin"));
    //private final JButton refreshButton;  // disable for now refreshBurron
    /** 1.33: плашка необов'язкового акаунта сервера вгорі справа; null, якщо serverAccount=false у launcher.properties. */
    private AccountChip accountChip;

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
    public LauncherFrameFlat(@NonNull Launcher launcher) throws IOException {
        super(tr("launcher.title", launcher.getVersion()));

        this.launcher = launcher;
        instancesModel = new InstanceTableModel(launcher.getInstances(), () -> launcher.getConfig().getLastInstance());
        // 20.11.2026 (власник: відкривати «Встановлені», якщо там щось є): на старті завжди «Встановлені»; жодної
        // встановленої збірки, тоді selectRememberedInstance перемкне на «Додати інсталяцію»
        launcher.getConfig().setInstalledOnly(true);
        instancesModel.setInstalledOnly(true);
        instancesModel.setDesktopRow(true); // 25.11.2026: рядок «Робочий стіл» угорі «Встановлених»
        // 21.11.2026: розділи «Встановлених» згортаються кліком по підпису, стан у налаштуваннях лаунчера
        instancesModel.setCollapsibleSections(launcher.getConfig().getCollapsedSections(),
                () -> com.skcraft.launcher.persistence.Persistence.commitAndForget(launcher.getConfig()));
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
        coffeeImageRef = coffeeImage == null ? null : ImageIO.read(coffeeImage);
        coffeeButton = coffeeImageRef == null ? null : new CustomImageButton(coffeeImageRef, 200, 45, SharedLocale.tr("tooltip.coffee"));
        updateImageRef = ImageIO.read(Objects.requireNonNull(
                Launcher.class.getResourceAsStream("ui/button/update_" + langCode + ".png")));
        selfUpdateButton = new CustomImageButton(
            updateImageRef,
            200,45,
            SharedLocale.tr("tooltip.update")
        );

        // bottom buttons
        // disable for now accountButton (1.33: акаунт сервера не тут, а плашкою вгорі справа, AccountChip)
        //accountButton = new CustomImageButton(
        //    ImageIO.read(Objects.requireNonNull(
        //        Launcher.class.getResourceAsStream("ui/button/account_" + langCode + ".png")
        //    )),
        //    101,101,
        //    SharedLocale.tr("tooltip.settings")
        //);
        // дизайн 1.35: слоти хотбару замість картинок 101x101 (іконки ті самі, вирізані з settings/faq/support_*.png)
        optionsButton = new HotbarSlot("ui/icon/slot/settings.png", SharedLocale.tr("look.slot.settings"), SharedLocale.tr("tooltip.settings"));
        manageButton = new HotbarSlot("ui/icon/slot/build.png", SharedLocale.tr("look.slot.manage"), SharedLocale.tr("manage.tooltip"), 72)
                .highlight(); // 24.11.2026: найважливіша кнопка, зелений відтінок і рамка
        wikiButton = new HotbarSlot("ui/icon/slot/faq.png", SharedLocale.tr("look.slot.wiki"), SharedLocale.tr("tooltip.faq"));
        supportService = com.gummer.launcher.support.SupportService.create(launcher);
        if (supportService != null) {
            supportButton = new HotbarSlot("ui/icon/slot/support.png", SharedLocale.tr("look.slot.support"), SharedLocale.tr("tooltip.support"));
        }
        playImage = ImageIO.read(Objects.requireNonNull(
                Launcher.class.getResourceAsStream("ui/button/play_" + langCode + ".png")));
        // 20.11.2026 (власник: «коли збірка не встановлена, має бути не кнопка "запуск", а кнопка "встановити"»): та сама
        // зелена плитка 302x101 з написом піксельним шрифтом (зроблена з play_*.png), тож кнопка міняє лише напис і нічого
        // поряд не зсувається
        java.io.InputStream installStream = Launcher.class.getResourceAsStream("ui/button/install_" + langCode + ".png");
        installImage = installStream != null ? ImageIO.read(installStream) : playImage;
        launchButton = new CustomImageButton(playImage, 302, 101, SharedLocale.tr("tooltip.launch"));
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
        // 17.11.2026 (власник: «хочу щоб лаунчер можна було відкрити на весь екран - дуже бракує»): вікно розгортається
        // і тягнеться; менше за 1194 x 725 не стискається
        setResizable(true);
        initComponents();
        pack();
        setLocationRelativeTo(null);
        addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override
            public void componentResized(java.awt.event.ComponentEvent e) {
                zoomForSize();
            }
        });

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

    private void initComponents() {
        // create main container with two column BorderLayout
        JPanel container = createContainerPanel();
        container.setLayout(new BorderLayout());

        // create sidebarPanel with full-height and fixed width/padding
        int sidebarWidth = 220; // fixed width for the sidebar (adjust as needed)
        getRootPane().putClientProperty(com.gummer.launcher.look.Toast.LEFT, sidebarWidth); // сповіщення по центру сцени
        JPanel sidebarPanel = new JPanel(new BorderLayout());
                        sidebarPanel.setPreferredSize(new Dimension(sidebarWidth, 0));
        sidebarPanel.setMinimumSize(new Dimension(sidebarWidth, 0));
        sidebarPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 5, 8)); // set sidebarPanel padding
        sidebarPanel.setOpaque(false); // transparent

        // configure instanceTable width subtracting horizontal padding
        instanceScroll.setPreferredSize(new Dimension(sidebarWidth - 20, 400)); // height is arbitrary
        instanceScroll.setMaximumSize(new Dimension(sidebarWidth - 20, Integer.MAX_VALUE));
        instanceScroll.setOpaque(false); // transparent
        // 25.11.2026 (власник: «скрол пробігає 2-3 модпаки за раз», потім «хочу такий самий швидкий скрол, як у вкладці CurseForge»): 72 px за поділку, одразу
        com.gummer.launcher.look.WheelScroll.install(instanceScroll, false);

        // create sidebarButtonsPanel with strict width/padding
        JPanel sidebarButtonsPanel = new JPanel();
        sidebarButtonsPanel.setBorder(BorderFactory.createEmptyBorder(10, 0, 2, 0)); // set ButtonsPanel padding
        sidebarButtonsPanel.setMaximumSize(new Dimension(sidebarWidth - 18, Integer.MAX_VALUE)); // set ButtonsPanel width minus padding
        sidebarButtonsPanel.setLayout(new BoxLayout(sidebarButtonsPanel, BoxLayout.Y_AXIS));
        sidebarButtonsPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        sidebarButtonsPanel.setOpaque(false); // transparent
        sidebarRef = sidebarPanel;
        sidebarButtonsRef = sidebarButtonsPanel;
        //sidebarButtonsPanel.add(Box.createVerticalStrut(5)); // add spacing before buttons
        // 17.11.2026 (власник: «модпаки та версії мають дуже мало місця, 4.5 елементів ... "змінити скін" та
        // "аккаунт" перенести вправо зверху, але видозмінити та зробити трохи меншим»): акаунт і «Скіни» двома
        // вузькими плашками 112x36 одна під одною у правому верхньому куті, праворуч від зони лого (346..1070 px), тож
        // на лого не налазять навіть у найменшому вікні 1194 px (13.11 широка плашка вгорі справа налазила). Список
        // збірок зліва отримав ~107 px.
        JPanel accountBox = null;
        // 18.11.2026 (власник: «винеси перемикання мов на головну сторінку лаунчера. Просто прапорцями»): під плашками
        // акаунта і скіна (або сама, якщо акаунт вимкнено)
        langFlagsRef = new com.gummer.launcher.look.LangFlags(launcher);
        langFlagsRef.setFill(true); // рівно під плашками акаунта і скіна
        if (!"false".equalsIgnoreCase(launcher.getProperties().getProperty("serverAccount", "true").trim())) {
            accountChip = new AccountChip(launcher, () -> ServerAccountDialog.showFor(this, launcher, supportService,
                    this::getSelectedInstance, () -> accountChip.refresh()));
            SkinChip skinChip = new SkinChip(() -> ServerAccountDialog.showFor(this, launcher, supportService,
                    this::getSelectedInstance, () -> accountChip.refresh(), true));
            skinChipRef = skinChip;
            accountBox = new JPanel(new MigLayout("insets 8 0 0 10, gap 0, wrap 1", "push[112!]", "[36!]6[36!]"));
            accountBox.setOpaque(false);
            accountBox.add(accountChip, "growx, h 36!");
            accountBox.add(skinChip, "growx, h 36!");
        } else {
            accountBox = new JPanel(new MigLayout("insets 8 0 0 10, gap 0, wrap 1", "push[112!]", ""));
            accountBox.setOpaque(false);
        }
        ((MigLayout) accountBox.getLayout()).setRowConstraints(accountChip != null ? "[36!]6[36!]6[32!]" : "[32!]");
        accountBox.add(langFlagsRef, "growx, h 32!");
        sidebarButtonsPanel.add(telegramButton);
        sidebarButtonsPanel.add(Box.createVerticalStrut(5));
        sidebarButtonsPanel.add(discordButton);
        if (coffeeButton != null) {
            sidebarButtonsPanel.add(Box.createVerticalStrut(5));
            sidebarButtonsPanel.add(coffeeButton);
        }
        // 16.11.2026: лайк вигляду (один з IP) і «Фідбек по дизайну»
        DesignVote vote = DesignVote.create(this, launcher, supportService, com.gummer.launcher.FancyLauncher.LOOK_GLASS);
        if (vote != null) {
            Dimension vd = new Dimension(200, DesignVote.HEIGHT);
            vote.setPreferredSize(vd);
            vote.setMinimumSize(vd);
            vote.setMaximumSize(vd);
            sidebarButtonsPanel.add(Box.createVerticalStrut(8));
            sidebarButtonsPanel.add(vote);
            voteRef = vote;
        }
        JComponent sourceLink = createSourceLink();
        sourceLinkRef = sourceLink;
        if (sourceLink != null) {
            sidebarButtonsPanel.add(Box.createVerticalStrut(9)); // 20.11.2026: було 4, лайк і фідбек впритул до посилання
            sidebarButtonsPanel.add(sourceLink);
        }
        sidebarButtonsPanel.add(Box.createVerticalStrut(1));
        //sidebarButtonsPanel.add(Box.createVerticalStrut(4));
        sidebarButtonsPanel.add(selfUpdateButton);

        // add elements to sidebarPanel
        // 17.11.2026: над списком перемикач «Усі / Встановлені»
        JPanel listColumn = new JPanel(new BorderLayout(0, 8));
        listColumn.setOpaque(false);
        JComponent listSwitch = createListSwitch();
        listColumn.add(listSwitch, BorderLayout.NORTH);
        listColumn.add(instanceScroll, BorderLayout.CENTER);
        sidebarPanel.add(listColumn, BorderLayout.CENTER);
        sidebarPanel.add(sidebarButtonsPanel, BorderLayout.SOUTH);

        // create rightPanel
        JPanel rightPanel = new JPanel(new BorderLayout());
        rightPanelRef = rightPanel;
        rightPanel.setOpaque(false); // transparent
        // верхні 150 px (зона лого) окремою панеллю: у ній справа акаунт і «Скіни»; на сторінці Modrinth 12 px
        topArea = new JPanel(new BorderLayout()) {
            @Override
            public Dimension getPreferredSize() {
                return new Dimension(super.getPreferredSize().width, topAreaHeight);
            }
        };
        topArea.setOpaque(false);
        accountBoxRef = accountBox;
        if (accountBox != null) topArea.add(accountBox, BorderLayout.EAST);

        // create bottom buttons panel with padding
        // дизайн 1.35: хотбар ліворуч, праворуч група «вибір версії + Запуск». Група окремою панеллю, бо в одному рядку
        // схований вибір версії (hidemode 3) лишав свій відступ і «Запуск» у збірках без вибору (StalDayZ) з'їжджав ліворуч
        JPanel rightButtonsPanel = new JPanel(new MigLayout("insets 0 0 0 0", "[]push[]", "[]"));
        JPanel launchGroup = new JPanel(new MigLayout("insets 0, gap 0", "[][][]", "[]"));
        launchGroupRef = launchGroup;
        launchGroup.setOpaque(false);
        //JPanel rightButtonsPanel = new JPanel(new MigLayout("", "[][][][][]push[][]", ""));

        rightButtonsPanel.setOpaque(false);
        rightButtonsPanel.setBorder(BorderFactory.createEmptyBorder(6, 12, 6, 6)); // set ButtonsPanel padding
        //rightButtonsPanel.add(refreshButton); // hide refresh button on new template
        //rightButtonsPanel.add(updateCheck); // hide update checkbox

        JPanel hotbar = HotbarSlot.bar();
        getRootPane().putClientProperty(com.gummer.launcher.look.Toast.ABOVE, hotbar); // сповіщення над кнопками, не на лого
        hotbar.add(optionsButton);
        //rightButtonsPanel.add(accountButton); // disable for now accountButton
        hotbar.add(wikiButton);
        if (supportButton != null) {
            hotbar.add(supportButton);
            supportButton.addActionListener(e -> {
                if (supportButton instanceof HotbarSlot) ((HotbarSlot) supportButton).setBadge(false);
                com.gummer.launcher.dialog.SupportDialog.showFor(this, supportService, this::getSelectedInstance);
            });
            checkSupportReplies();
        }
        rightButtonsPanel.add(hotbar, "aligny center");
        setupVersionBox();
        // 17.11.2026: «Збірка» окремою вузькою смугою хотбару одразу ліворуч від «Запуску» (власник: «зліва від кнопки
        // запуск, акуратно, але зрозуміло»). Щоб «Запуск» не з'їжджав за край на найменшому вікні (1194), поле версії
        // вужче (260 -> 204), а в кнопки-картинки «Запуск» прибрано бічні поля 14 px: сяйво однаково малюється навколо картинки
        JPanel manageBar = HotbarSlot.bar();
        manageBar.add(manageButton);
        // 22.11.2026 («Налаштування збірки» у два рядки): слот ширший за 72 на стільки, скільки займає «Налаштування»
        // (українською +19 px). На найменшому вікні з полем версії ряд заповнений впритул, тож на ту саму ширину вужче
        // поле версії, інакше «Запуск» вилазив за правий край (проба з полем версії 22.11 ~21:00)
        int extra = Math.max(0, manageButton.getPreferredSize().width - 72);
        if (extra > 0 && versionPanel.getLayout() instanceof MigLayout) {
            ((MigLayout) versionPanel.getLayout()).setColumnConstraints("[" + (196 - extra) + "!]");
        }
        launchButton.setMargin(new Insets(2, 0, 2, 0));
        launchGroup.add(versionPanel, "aligny center, hidemode 3, gapright 4");
        launchGroup.add(manageBar, "aligny center, gapright 2");
        // «Запуск» з м'яким світінням, що «дихає»
        launchGroup.add(HotbarSlot.glow(launchButton, new Color(0x8C, 0xE0, 0x4A)), "aligny center");
        rightButtonsPanel.add(launchGroup, "aligny center");

        // add elements to rightPanel
        // 1.32: замість прихованої веб-панелі новин (у Java 8 немає браузера) картки з news.json над кнопками
        JPanel newsArea = new JPanel(new BorderLayout());
        newsArea.setOpaque(false);
        newsRow.setOpaque(false);
        newsArea.add(newsRow, BorderLayout.SOUTH);
        rightPanel.add(wrapMain(newsArea), BorderLayout.CENTER); // 17.11.2026: або сторінка Modrinth
        JPanel bottom = new JPanel(new BorderLayout());
        bottom.setOpaque(false);
        bottom.add(createStatusRow(), BorderLayout.NORTH);
        bottom.add(rightButtonsPanel, BorderLayout.CENTER);
        rightPanel.add(bottom, BorderLayout.PAGE_END);
        JPanel north = new JPanel(new BorderLayout());
        north.setOpaque(false);
        north.add(topArea, BorderLayout.CENTER);
        JPanel oldExeBanner = createOldExeBanner();
        if (oldExeBanner != null) {
            north.add(oldExeBanner, BorderLayout.SOUTH);
        }
        rightPanel.add(north, BorderLayout.PAGE_START);

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

        manageButton.addActionListener(e -> openManage(getSelectedInstance(), InstanceManageDialog.GENERAL));
        CoachMark.manageTip(this, launcher, manageButton);

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
            else { // 22.11.2026: згорнуто все, вибирати нічого, «Запуск» і «Збірка» неактивні
                instancesTable.clearSelection();
                launchButton.setEnabled(false);
                manageButton.setEnabled(false);
            }
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

    /**
     * 1.32: чи відповіли адміни на звернення. З 16.11.2026 не раз при старті, а поки вікно видно (раз на хвилину,
     * {@link SupportUnread}), з числом непрочитаних на кнопці.
     */
    private void checkSupportReplies() {
        SupportUnread.watch(this, supportService, this::getSelectedInstance, n -> {
            supportButton.setToolTipText(SharedLocale.tr(n > 0 ? "support.newReplyTooltip" : "tooltip.support"));
            if (supportButton instanceof HotbarSlot) ((HotbarSlot) supportButton).setBadge(n);
        }, () -> showNotification(LauncherFrameFlat.rootFrame, LauncherFrameFlat.class, Notification.Type.INFO,
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
        versionLabel.setForeground(new Color(230, 230, 230));
        // 17.11.2026: блок вужчий (204), тож 12 замість 13 і «...» замість вилазу за край, повний текст у підказці блоку
        versionLabel.setFont(versionLabel.getFont().deriveFont(Font.BOLD, 12f));
        versionPanel.add(versionLabel, "wmin 0, growx");
        versionPanel.add(versionBox, "growx, wmin 0, h 44!"); // wmin 0: ширина за колонкою, а не за найдовшою версією
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
                        // закритий список: позначка і версія
                        text = "<html><nobr>" + dot + mc + SwingHelper.htmlEscape(version) + "</nobr></html>";
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
                } else if (versionBox.getWidth() > 0) {
                    // закрите поле (16.11.2026): довга назва («Pre-classic rd-20090515») при 18 не влазить і
                    // переносилась на два рядки; зменшуємо шрифт, доки не влізе (не менше 13)
                    int room = versionBox.getWidth() - 44; // мінус стрілка і відступи поля
                    // 20.11.2026: міряємо сам текст (без HTML), бо розмір HTML-підпису з кешу (HtmlCellCache) не завжди
                    // перераховувався, і в полі 164 px «○ Minecraft 26.2» обрізалось до «Minecraft 26»
                    String plain = text instanceof String ? ((String) text).replaceAll("<[^>]*>", "").replace("&nbsp;", " ") : String.valueOf(text);
                    float size = 18f;
                    Font f = c.getFont().deriveFont(Font.BOLD, size);
                    while (c.getFontMetrics(f).stringWidth(plain) > room && size > 12f) {
                        size -= 1f;
                        f = f.deriveFont(size);
                    }
                    c.setFont(f);
                    // закрите поле малює значення шрифтом самого поля (BasicComboBoxUI.paintCurrentValue ставить
                    // comboBox.getFont() поверх нашого), тож розмір задаємо полю; той самий розмір вдруге нічого не міняє
                    if (versionBox.getFont().getSize2D() != size) versionBox.setFont(f);
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
                    SwingHelper.showErrorDialog(LauncherFrameFlat.this, tr("loader.unavailable", title), title);
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
                int r = JOptionPane.showConfirmDialog(LauncherFrameFlat.this, panel, title,
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
                    SwingHelper.showErrorDialog(LauncherFrameFlat.this, e.toString(), title);
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
    private java.awt.image.BufferedImage playImage, installImage;
    private boolean showingInstall;

    /** 20.11.2026: «ВСТАНОВИТИ» на не встановленій збірці і на ванілі чи завантажувачі в «Додати інсталяцію», інакше «ЗАПУСК». */
    private void refreshLaunchImage(Instance sel) {
        boolean install = sel != null && !InstanceTableModel.isBrowser(sel) && !InstanceTableModel.isAddRow(sel)
                && (instancesModel.isFreshInstall(sel) || !(sel.isLocal() && sel.isInstalled()));
        if (install == showingInstall || playImage == null) return;
        showingInstall = install;
        ((CustomImageButton) launchButton).setImage(install ? installImage : playImage, 302, 101); // розмір той самий
    }

    private void refreshStatusRow() {
        Instance sel = getSelectedInstance();
        refreshLaunchImage(sel);
        String address = sel != null && !sel.isBuiltIn() ? sel.getServerAddress() : null;
        boolean show = address != null;
        launchButton.setToolTipText(showingInstall ? tr("ui2.install") : trFor(sel, "tooltip.launch"));
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
        } else if (show) {
            // 16.11.2026: поки пінг не повернувся, місце під рядок уже зайняте (раніше він з'являвся і все зсувалось)
            onlineLabel.setText("● " + tr("launcher.onlineChecking"));
            onlineLabel.setForeground(new Color(0xB0, 0xB0, 0xB0));
            onlineLabel.setVisible(true);
        } else {
            onlineLabel.setVisible(false);
        }
        if (pillPanel != null) {
            pillPanel.setVisible(show);
            // 1.35: перерахувати і рядок, інакше довший текст онлайну обрізався («онлай…»)
            Container row = pillPanel.getParent();
            if (row != null) row.revalidate();
            pillPanel.revalidate();
            pillPanel.repaint();
        }
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
            newsRow.add(createNewsCard(n), "w 296!, h 128!");
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
        if (n.online != null) newsOnlineLabels.add(new Object[]{onlineText, n.online});
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
        com.gummer.launcher.ui.UiHooks.openMods = (owner, instance) -> openManage(instance, InstanceManageDialog.MODS);
        com.gummer.launcher.ui.UiHooks.openWorlds = (owner, instance) -> openManage(instance, InstanceManageDialog.WORLDS);
        com.gummer.launcher.ui.UiHooks.rollback = (owner, instance) -> MrpackUpdateFlow.rollback(LauncherFrameFlat.this, launcher, instance, null);
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
        lastPackKey = packKey;
        applyButtonImage((CustomImageButton) telegramButton, "telegram", packKey);
        applyButtonImage((CustomImageButton) discordButton, "discord", packKey);
    }

    private void applyButtonImage(CustomImageButton button, String kind, String packKey) {
        java.awt.image.BufferedImage image = null;
        if (packKey != null && packKey.matches("[a-z0-9_.-]+")) image = buttonImage("ui/button/" + kind + "_" + langCode + "_" + packKey + ".png");
        if (image == null) image = buttonImage("ui/button/" + kind + "_" + langCode + ".png");
        if (image != null) button.setImage(image, zpx(200), zpx(45));
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
                LauncherFrameFlat.this.playWorld(i, world);
            }

            @Override
            public void buildUpdated(Instance i) {
                InstanceManageDialog.installOnly(LauncherFrameFlat.this, launcher, i, LauncherFrameFlat.this::refreshInstancesQuietly);
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
                            LauncherFrameFlat.this, new File(selected.getContentDir(), "mods"), true));
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
                        LauncherFrameFlat.this, selected.getContentDir(), true));
                popup.add(menuItem);

                if (!com.gummer.launcher.manage.InstanceMods.packModsLocked(selected)) { // StalDayZ: сторонні ресурспаки заборонені
                    menuItem = new JMenuItem(SharedLocale.tr("instance.openResourcePacks"), widgetIcon);
                    menuItem.addActionListener(ActionListeners.browseDir(
                            LauncherFrameFlat.this, new File(selected.getContentDir(), "resourcepacks"), true));
                    popup.add(menuItem);

                }

                menuItem = new JMenuItem(SharedLocale.tr("instance.openScreenshots"), imageIcon);
                menuItem.addActionListener(ActionListeners.browseDir(
                        LauncherFrameFlat.this, new File(selected.getContentDir(), "screenshots"), true));
                popup.add(menuItem);

                menuItem = new JMenuItem(SharedLocale.tr("instance.openCrashReports"), crashIcon);
                menuItem.addActionListener(ActionListeners.browseDir(
                    LauncherFrameFlat.this, new File(selected.getContentDir(), "crash-reports"), true));
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
                instancesReady();
            }
        }, SwingExecutor.INSTANCE);

        ProgressDialog.showProgress(this, future, SharedLocale.tr("launcher.checkingTitle"), SharedLocale.tr("launcher.checkingStatus"));
        SwingHelper.addErrorDialogCallback(this, future);
    }

    /** Список збірок готовий (після завантаження або одразу при перемиканні вигляду): показати і вибрати. */
    private void instancesReady() {
        instancesModel.update();
        if (instancesTable.getRowCount() > 0) {
            selectRememberedInstance();
        }
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
        // ті самі рядки: без повного оновлення таблиці (воно скидає вибір, і список «блимав»), лише нові дані версій
        if (instancesModel.updateKeepingRows()) {
            refreshVersionBox();
            refreshStatusRow();
        } else if (instancesTable.getRowCount() > 0) {
            reselect(keep);
        }
        }, SwingExecutor.INSTANCE);
    }


    /** Права частина вікна (верхній відступ 150 під логотип; на сторінці Modrinth менший). */
    private JPanel rightPanelRef;
    // ---- 17.11.2026 (власник: «в повноекранці масштабувати (збільшити)» бічну панель і плашки «Увійти», «Скіни»,
    // «інше я б залишив як є»): множник Zoom.forWindow, розміри бічної панелі, списку, кнопок-картинок, «Вигляд»,
    // посилання і плашок перераховуються при зміні розміру вікна (кроком 0.05)
    private double sideZoom = 1.0;
    private JPanel sidebarRef, sidebarButtonsRef;
    private SkinChip skinChipRef;
    private com.gummer.launcher.look.LangFlags langFlagsRef;
    private DesignVote voteRef;
    private JComponent sourceLinkRef;
    private java.awt.image.BufferedImage coffeeImageRef, updateImageRef;
    private String lastPackKey;

    /** Ширина бічної панелі змінилась (FlatLauncherFrame розтягує під неї матову підкладку фону). */
    protected void sidebarWidthChanged(int width) {
    }

    /** Ширина бічної панелі при множнику 1 (20.11.2026: 250 на кілька годин, повернуто 220 разом з назвою вкладки «Додати +»). */
    static final int SIDEBAR_W = 220;

    private int zpx(double v) {
        return com.gummer.launcher.look.Zoom.px(sideZoom, v);
    }

    /**
     * 17.11.2026 (власник: «ліва панель надто велика. Зроби її шириною як ліва панель в варіанті "класика"»): не
     * ширша за рейку Класики (264 px), тобто множник не більше 264 / 220 = 1.2.
     */
    private void zoomForSize() {
        applySideZoom(Math.min(com.gummer.launcher.look.Zoom.forWindow(getWidth(), getHeight()),
                com.gummer.launcher.ui2.Ui.RAIL_W / (double) SIDEBAR_W));
    }

    /**
     * 17.11.2026 (власник: «досі є цей перескок ... лише в повноекранному»; «коли перемикаєш між повноекранним та
     * віконним воно так різко перебудовується»): кадри самого вікна показали, що розгорнуте скло спершу малювалось з
     * панеллю 220 px, а за ~0.1 с подія зміни розміру збільшувала її, і все праворуч з'їжджало. Тепер масштаб
     * рахується в тому ж розкладанні, що і новий розмір вікна, тож перший кадр уже правильний.
     */
    @Override
    protected JRootPane createRootPane() {
        // і плавний перехід при розгортанні та відновленні (look.MorphRootPane)
        return new com.gummer.launcher.look.MorphRootPane(() -> {
            if (sidebarRef != null) zoomForSize();
        });
    }

    private void applySideZoom(double z) {
        if (z == sideZoom || sidebarRef == null) return;
        sideZoom = z;
        int sw = zpx(SIDEBAR_W);
        sidebarWidthChanged(sw);
        sidebarRef.putClientProperty(com.gummer.launcher.look.Zoom.KEY, z);
        sidebarRef.setPreferredSize(new Dimension(sw, 0));
        sidebarRef.setMinimumSize(new Dimension(sw, 0));
        sidebarRef.setBorder(BorderFactory.createEmptyBorder(zpx(10), zpx(10), zpx(5), zpx(8)));
        getRootPane().putClientProperty(com.gummer.launcher.look.Toast.LEFT, sw);
        instanceScroll.setPreferredSize(new Dimension(sw - zpx(20), 400));
        instanceScroll.setMaximumSize(new Dimension(sw - zpx(20), Integer.MAX_VALUE));
        sidebarButtonsRef.setMaximumSize(new Dimension(sw - zpx(18), Integer.MAX_VALUE));
        showPackButtons(lastPackKey);
        if (coffeeImageRef != null) ((CustomImageButton) coffeeButton).setImage(coffeeImageRef, zpx(200), zpx(45));
        if (updateImageRef != null) ((CustomImageButton) selfUpdateButton).setImage(updateImageRef, zpx(200), zpx(45));
        if (voteRef != null) {
            Dimension vd = new Dimension(zpx(200), DesignVote.height(z));
            voteRef.setPreferredSize(vd);
            voteRef.setMinimumSize(vd);
            voteRef.setMaximumSize(vd);
            voteRef.setZoom(z);
        }
        if (sourceLinkRef != null) sourceLinkRef.setFont(sourceLinkRef.getFont().deriveFont((float) (11 * z)));
        ((CardInstanceTable) instancesTable).setZoom(z);
        if (accountBoxRef != null) {
            accountBoxRef.putClientProperty(com.gummer.launcher.look.Zoom.KEY, z);
            MigLayout lay = (MigLayout) accountBoxRef.getLayout();
            lay.setLayoutConstraints("insets " + zpx(8) + " 0 0 " + zpx(10) + ", gap 0, wrap 1");
            lay.setColumnConstraints("push[" + zpx(112) + "!]");
            if (accountChip != null) {
                lay.setRowConstraints("[" + zpx(36) + "!]" + zpx(6) + "[" + zpx(36) + "!]" + zpx(6) + "[" + zpx(32) + "!]");
                lay.setComponentConstraints(accountChip, "growx, h " + zpx(36) + "!");
                lay.setComponentConstraints(skinChipRef, "growx, h " + zpx(36) + "!");
                accountChip.setZoom(z);
                skinChipRef.setZoom(z);
            } else {
                lay.setRowConstraints("[" + zpx(32) + "!]");
            }
            if (langFlagsRef != null) {
                lay.setComponentConstraints(langFlagsRef, "growx, h " + zpx(32) + "!");
                langFlagsRef.setZoom(z);
            }
        }
        sidebarRef.revalidate();
        getContentPane().revalidate();
        getContentPane().repaint();
    }
    private JPanel topArea;
    private JPanel accountBoxRef;

    /** Плашки акаунта, скіна і прапорців справа вгорі: жовтий напис біля лого на них не заходить (20.11.2026). */
    protected JComponent splashAvoid() {
        return accountBoxRef;
    }
    private int topAreaHeight = 150;

    // ---------------------------------------------------------------- 17.11.2026: «Усі / Встановлені» і сторінка Modrinth
    /** Головна область: «main» (як завжди) або «modrinth» (сторінка модпаків, коли зліва вибрано рядок Modrinth). */
    private final CardLayout mainCards = new CardLayout();
    private final JPanel mainHolder = new JPanel(mainCards);
    private ModrinthPage modrinthPage;
    /** 1.40.0: сторінка модпаків CurseForge (рядок «CurseForge» під Modrinth). */
    private ModrinthPage curseforgePage;
    /** Версія, «Збірка» і «Запуск» (на сторінці Modrinth сховані). */
    private JPanel launchGroupRef;

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
        refreshStatusRow();
        refreshModrinthPage();
    }

    /** Показати сторінку Modrinth, якщо зліва вибрано її рядок, інакше звичайну головну область. */
    private void refreshModrinthPage() {
        if (InstanceTableModel.isAddRow(getSelectedInstance())) {
            // 19.11.2026: жодної встановленої збірки (у списку лише «+ Додати збірку»): «Встановлені» лишаються
            // відкритими з поясненням (look.EmptyInstalled), раніше список одразу перескакував назад на «Усі»
            if (instancesModel.isInstalledOnly() && instancesModel.getRowCount() <= 1) {
                mainCards.show(mainHolder, "empty");
                launchButton.setEnabled(false);
                manageButton.setEnabled(false);
                if (launchGroupRef != null) launchGroupRef.setVisible(false);
                return;
            }
            // 17.11.2026: «+ Додати збірку» у «Встановлених»: показати «Усі» (усі версії і модпаки)
            if (listSwitchRef != null) SwingUtilities.invokeLater(() -> listSwitchRef.setInstalledOnly(false));
            return;
        }
        if (modrinthPage == null) return;
        boolean browse = InstanceTableModel.isBrowser(getSelectedInstance());
        boolean curseRow = InstanceTableModel.isCurseRow(getSelectedInstance());
        ModrinthPage page = curseRow && curseforgePage != null ? curseforgePage : modrinthPage;
        boolean desk = InstanceTableModel.isDesktopRow(getSelectedInstance());
        mainCards.show(mainHolder, desk ? "desktop" : browse ? (curseRow ? "curseforge" : "modrinth") : "main");
        if (desk && desktopPage != null) desktopPage.refresh();
        topAreaHeight = browse ? 12 : 150;
        if (accountBoxRef != null) accountBoxRef.setVisible(!browse); // сторінка Modrinth на всю висоту
        if (topArea != null) topArea.revalidate();
        if (browse) {
            launchButton.setEnabled(false);
            manageButton.setEnabled(false);
        } else {
            manageButton.setEnabled(true);
            launchButton.setEnabled(getSelectedInstance() != null); // раніше лишалась вимкненою після сторінки Modrinth
        }
        // 17.11.2026 (власник: «що таке "Modrinth - не встановлено"... ми ж не встановлюємо модрінт»): на сторінці
        // модпаків кнопок запуску і «Збірка» немає, модпак ставиться кнопкою «Встановити» біля його версії
        if (launchGroupRef != null) launchGroupRef.setVisible(!browse);
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
        ConfigurationDialogFlat configDialog = new ConfigurationDialogFlat(this, launcher, LauncherFrameFlat.rootFrame);
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
        instancesTable.repaint();
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
            if (frame != null && !com.gummer.launcher.look.GameReturn.keepDuringGame()) { // 21.11.2026: gamescope
                frame.dispose();
            }
            com.gummer.launcher.look.MemoryTrim.forGame(); // 20.11.2026: не тримати кеш картинок, поки граєш
        }

        @Override
        public void gameClosed() {
            LauncherFrameFlat frame = frameRef.get();
            if (frame != null) {
                com.gummer.launcher.look.GameReturn.reshow(frame); // 25.11.2026: без анімації появи розгорнутого вікна
                com.gummer.launcher.look.GameReturn.shown(frame); // 21.11.2026: сірий екран після гри в ігровому режимі Steam Deck
                return;
            }
            com.gummer.launcher.look.GameReturn.shown(launcher.showLauncherWindow());
        }
    }

}
