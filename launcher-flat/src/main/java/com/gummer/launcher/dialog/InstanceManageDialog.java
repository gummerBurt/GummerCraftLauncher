/*
 * Copyright (C) 2021-2026 GummerCraft <http://gummercraft.fun>
 */

package com.gummer.launcher.dialog;

import com.gummer.launcher.look.LookTheme;
import com.gummer.launcher.look.LookUi;
import com.gummer.launcher.manage.InstanceCopies;
import com.gummer.launcher.manage.InstanceMods;
import com.gummer.launcher.popup.Notification;
import com.skcraft.concurrency.ObservableFuture;
import com.skcraft.launcher.Instance;
import com.skcraft.launcher.Launcher;
import com.skcraft.launcher.dialog.ProgressDialog;
import com.skcraft.launcher.persistence.Persistence;
import com.skcraft.launcher.swing.SwingHelper;
import com.skcraft.launcher.util.SharedLocale;
import com.skcraft.launcher.util.SwingExecutor;
import net.miginfocom.swing.MigLayout;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;

import static com.gummer.launcher.popup.ShowNotification.showNotification;
import static com.skcraft.launcher.util.SharedLocale.tr;

/**
 * 17.11.2026: вікно «Керування збіркою» (кнопка «Збірка» біля «Грати» у всіх трьох виглядах і пункт меню збірки).
 * Одне спільне вікно в стилі поточного вигляду ({@link LookTheme}), щоб не множити код у трьох копіях головного вікна.
 * Розділи: Загальне (назва, копія, теки, Java і пам'ять), Продуктивність (FPS: пресети і відеокарта, 24.11.2026), Імпорт/Експорт (19.11.2026: файл збірки .mrpack чи .zip,
 * імпорт з інших лаунчерів), Моди (вкл/викл, необов'язкові моди модпаку), Ресурспаки, Шейдери, Світи, Скріншоти. Файли можна перетягнути у вікно: вони копіюються в теку відкритого розділу.
 */
public class InstanceManageDialog extends JDialog {

    public static final int GENERAL = 0, MODS = 1, RESOURCEPACKS = 2, SHADERS = 3, WORLDS = 4, SCREENSHOTS = 5;
    /** 19.11.2026 (власник: «Збірка - під загальне зроби вкладку "Імпорт/Експорт" і туди перенеси все з
     *  імпортом/експортом»): номер новий, а в меню вона одразу під «Загальне» ({@link #NAV_ORDER}). */
    public static final int IMPEX = 6;
    /** 24.11.2026 (власник: «виведи "Продуктивність (FPS)" окремо, між "Загальне" та "Імпорт/Експорт"»): пресети FPS і
     *  вибір відеокарти, раніше в «Загальне». */
    public static final int PERF = 7;
    private static final int LAST = PERF;
    /** Порядок розділів у меню зліва. */
    private static final int[] NAV_ORDER = {GENERAL, PERF, IMPEX, MODS, RESOURCEPACKS, SHADERS, WORLDS, SCREENSHOTS};

    /** Що вікно просить у головного вікна (воно в трьох варіантах). */
    public interface Host {
        /** Назва чи моди змінились: перечитати список збірок, лишивши вибір. */
        void instanceChanged();

        /** Зроблено копію з такою назвою теки: вибрати її. */
        void copyCreated(String name);

        /** Видалити збірку (з підтвердженням головного вікна). */
        void deleteInstance(Instance instance);

        /** 17.11.2026: змінилась своя іконка чи фон копії: перечитати список і перемалювати фон. */
        default void artChanged() {
            instanceChanged();
        }

        /** 17.11.2026: запустити збірку одразу в цей світ (сторінка «Світи»). */
        default void playWorld(Instance instance, String world) {
        }

        /** 17.11.2026: модпак оновлено чи повернуто: доставити гру і завантажувач, перечитати список. */
        default void buildUpdated(Instance instance) {
            instanceChanged();
        }
    }

    private final Launcher launcher;
    private final Instance instance;
    private final Host host;
    private final LookTheme.Palette pal = LookTheme.palette();
    private final CardLayout pages = new CardLayout();
    /**
     * 24.11.2026 (власник: «інтерфейс лаунчера має "літати"»): розмір вікна задано явно ({@link LookTheme#fitToScreen}),
     * тож бажаний розмір області розділів ні на що не впливає. А {@link CardLayout} рахував його як найбільший з УСІХ
     * збудованих розділів, тобто кожна зміна в будь-якому розділі (навіть схованому) заново міряла весь список модів.
     * На збірці зі 196 модами позначки «клієнт/сервер», що доходять у фоні, вішали вікно на 0,3-0,7 с, навіть коли
     * гравець уже перейшов на «Ресурспаки». Тепер область розділів просто займає все місце, яке їй дають.
     */
    private final JPanel pageHost = new JPanel(pages) {
        @Override
        public Dimension getPreferredSize() {
            return new Dimension(100, 100);
        }

        @Override
        public Dimension getMinimumSize() {
            return new Dimension(0, 0);
        }
    };
    private final JLabel headTitle = new JLabel();
    private int current;
    private boolean changed;

    private final JPanel modsList = column();
    private final JPanel optionalList = column();
    private JPanel optionalCard;
    private final JPanel packsList = column();
    private final JPanel shadersList = column();
    private final JPanel worldsList = column();
    private final JPanel shotsGrid = new JPanel(new MigLayout("insets 0, wrap 3, gap 10 10", "[grow,fill][grow,fill][grow,fill]", ""));
    private SwingWorker<?, ?> shotsLoader;

    public InstanceManageDialog(Window owner, Launcher launcher, Instance instance, Host host) {
        super(owner);
        this.launcher = launcher;
        this.instance = instance;
        this.host = host;
        setTitle(tr("manage.title"));
        setModalityType(DEFAULT_MODALITY_TYPE);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        build();
        Dimension size = LookTheme.fitToScreen(this, 798, 580, 600, 420); // 24.11.2026: 780 -> 798, меню зліва ширше на 18
        setSize(size);
        setMinimumSize(new Dimension(Math.min(600, size.width), Math.min(420, size.height)));
        setResizable(true);
        LookUi.decorate(this, owner);
        setLocationRelativeTo(owner);
    }

    /** Відкрити на розділі page; після закриття, якщо щось змінилось, головне вікно перечитує список. */
    public static void open(Window owner, Launcher launcher, Instance instance, int page, Host host) {
        if (instance == null) return;
        InstanceManageDialog d = new InstanceManageDialog(owner, launcher, instance, host);
        d.select(page);
        d.setVisible(true);
        if (d.changed && host != null) host.instanceChanged();
    }

    // ------------------------------------------------------------------ каркас

    private LookUi.Nav nav;

    private void build() {
        pageHost.setOpaque(false);
        // 17.11.2026: сторінка будується, лише коли її вперше відкривають (ensurePage); раніше вікно при кожному
        // відкритті будувало всі шість, хоча показує одну
        for (int i = GENERAL; i <= LAST; i++) {
            JPanel holder = new JPanel(new BorderLayout());
            holder.setOpaque(false);
            pageHolders[i] = holder;
            pageHost.add(holder, String.valueOf(i));
        }

        // 17.11.2026: StalDayZ закритий: набір модів лише наш, сторонні ресурспаки заборонені, одиночної гри немає,
        // тож розділів «Моди», «Ресурспаки», «Світи» в ньому немає (сторінки зібрано, але в меню їх не видно)
        String[] all = {tr("manage.nav.general"), tr("manage.nav.mods"), tr("manage.nav.packs"),
                tr("manage.nav.shaders"), tr("manage.nav.worlds"), tr("manage.nav.screenshots"), tr("manage.nav.impex"), tr("manage.nav.perf")};
        navPages.clear();
        for (int i : NAV_ORDER) {
            if (closedPack() && (i == MODS || i == RESOURCEPACKS || i == WORLDS)) continue;
            // 17.11.2026: Vanilla шейдерів не вміє взагалі, а ресурспаки в грі лише з 1.6 (rd-132211, альфа, бета їх не мають)
            if (instance.isVanilla() && (i == SHADERS || i == RESOURCEPACKS && !vanillaHasResourcePacks())) continue;
            navPages.add(i);
        }
        String[] labels = new String[navPages.size()];
        for (int i = 0; i < labels.length; i++) labels[i] = all[navPages.get(i)];
        nav = new LookUi.Nav(labels, i -> show(navPages.get(i)));

        JPanel head = new JPanel(new MigLayout("insets 16 18 4 18, wrap 1, gap 0 2, fillx", "[grow,fill]", ""));
        head.setOpaque(false);
        headTitle.setFont(UIManager.getFont("Label.font").deriveFont(Font.BOLD, 19f));
        headTitle.setForeground(pal.text);
        headTitle.setText(instance.getTitle());
        head.add(headTitle, "wmin 0");
        String kindText = kindLine();
        if (!kindText.equalsIgnoreCase(instance.getTitle())) { // «Fabric 1.20.1» під «Fabric 1.20.1» не повторюємо
            JLabel kind = new JLabel(kindText);
            kind.setForeground(pal.text3);
            kind.setFont(kind.getFont().deriveFont(12.5f));
            head.add(kind, "wmin 0");
        }

        JPanel body = new JPanel(new MigLayout("insets 10 14 0 14, gap 14 0, fill", "[188!][grow,fill]", "[grow,fill]"));
        body.setOpaque(false);
        JPanel navCol = new JPanel(new MigLayout("insets 2 0 2 2, wrap 1, fillx", "[grow,fill]", "[]push"));
        navCol.setOpaque(false);
        navCol.add(nav, "growx, wmin 0, wmax 186"); // 24.11.2026: 168 -> 186, «Продуктивність (ФПС)» жирним
        body.add(navCol, "grow");
        body.add(pageHost, "grow, wmin 0");

        JButton close = LookUi.primary(new JButton(tr("manage.close")));
        close.putClientProperty("FlatLaf.styleClass", null);
        close.addActionListener(e -> dispose());
        JPanel buttons = new JPanel(new MigLayout("insets 10 16 14 16, gap 8", "[]push[]", "[34!]"));
        buttons.setOpaque(false);
        JLabel drop = footerHint;
        drop.setText(tr(closedPack() ? "manage.dropHintShaders" : "manage.dropHint"));
        footerIdleText = drop.getText();
        drop.setForeground(pal.text3);
        drop.setFont(drop.getFont().deriveFont(12f));
        // 17.11.2026 (власник: «бракує якогось прогрес-бару ... я не розумію, коли можна закривати вікно»): під час
        // «Оновити все» тут смужка і «2 з 5», після кінця «Готово ... вікно можна закрити»
        JPanel foot = new JPanel(new MigLayout("insets 0, gap 0, wrap 1, fillx", "[grow,fill]", "[][]"));
        foot.setOpaque(false);
        foot.add(drop, "wmin 0");
        footerBar.setVisible(false);
        footerBar.putClientProperty("JProgressBar.largeHeight", false);
        footerBar.setPreferredSize(new Dimension(10, 4));
        foot.add(footerBar, "wmin 0, h 4!, gaptop 4, hidemode 3");
        buttons.add(foot, "wmin 0, growx, aligny center");
        buttons.add(close, "w 140!");
        getRootPane().setDefaultButton(close);
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowOpened(java.awt.event.WindowEvent e) {
                close.requestFocusInWindow();
                show(current);
                prefetchMods(); // 24.11.2026: «Моди» потім показуються одразу
            }
        });

        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(pal.bg);
        root.add(head, BorderLayout.NORTH);
        root.add(body, BorderLayout.CENTER);
        root.add(buttons, BorderLayout.SOUTH);
        root.setTransferHandler(new DropFiles());
        setContentPane(root);
    }

    private void select(int page) {
        int i = navPages.indexOf(Math.max(GENERAL, Math.min(LAST, page)));
        nav.select(Math.max(0, i)); // схованого в цій збірці розділу немає: «Загальне»
    }

    /** Розділи в меню зліва (номери сторінок). */
    private final List<Integer> navPages = new ArrayList<Integer>();

    /**
     * Чи є у цій Vanilla ресурспаки: релізи з 1.6, знімки з 13w24a, нові номери (26.x). Старі pre-classic, classic,
     * indev, infdev, альфа і бета мали в кращому разі текстурпаки старого формату.
     */
    private boolean vanillaHasResourcePacks() {
        String v = com.gummer.launcher.manage.InstanceCopies.minecraftVersionOf(instance);
        if (v == null) return true; // невідомо: не ховаємо
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("^1[.]([0-9]+)").matcher(v);
        if (m.find()) return Integer.parseInt(m.group(1)) >= 6;
        m = java.util.regex.Pattern.compile("^([0-9]{2})w([0-9]{2})").matcher(v);
        if (m.find()) {
            int y = Integer.parseInt(m.group(1)), w = Integer.parseInt(m.group(2));
            return y > 13 || y == 13 && w >= 24;
        }
        return v.matches("^[0-9]{2}[.][0-9]+.*"); // 26.1, 26.3 ...
    }

    /** StalDayZ: лише наш набір модів, без сторонніх ресурспаків і без одиночної гри. */
    private boolean closedPack() {
        return InstanceMods.packModsLocked(instance);
    }

    private final JPanel[] pageHolders = new JPanel[LAST + 1];

    /** Побудувати сторінку, якщо її ще не будували. */
    private void ensurePage(int page) {
        JPanel holder = pageHolders[page];
        if (holder.getComponentCount() > 0) return;
        File content = instance.getContentDir();
        JPanel p;
        switch (page) {
            case MODS:
                p = modsPage();
                break;
            case RESOURCEPACKS:
                p = filesPage(RESOURCEPACKS, new File(content, "resourcepacks"), packsList,
                        tr("manage.packs.title"), tr("manage.packs.hint"), "zip");
                break;
            case SHADERS:
                p = filesPage(SHADERS, new File(content, "shaderpacks"), shadersList, tr("manage.shaders.title"),
                        instance.isVanilla() ? tr("manage.shaders.hintVanilla") : tr("manage.shaders.hint"), "zip");
                break;
            case WORLDS:
                p = worldsPage();
                break;
            case SCREENSHOTS:
                p = screenshotsPage();
                break;
            case IMPEX:
                p = impexPage();
                break;
            case PERF:
                p = perfPage();
                break;
            default:
                p = generalPage();
        }
        holder.add(scroll(p), BorderLayout.CENTER);
        holder.revalidate();
        relayoutLater(p); // тексти з переносом розкласти ще раз, уже з остаточною шириною
    }

    private void show(int page) {
        current = page;
        ensurePage(page);
        pages.show(pageHost, String.valueOf(page));
        // сторінка завжди з початку (інакше фокус у полі назви прокручував «Загальне» донизу)
        Component sp = pageHolders[page].getComponent(0);
        if (sp instanceof JScrollPane) SwingUtilities.invokeLater(() -> ((JScrollPane) sp).getVerticalScrollBar().setValue(0));
        // 19.11.2026 (власник: «якщо швидко перемикатись в "збірка" між кнопками ресурспаки шейдери моди - залагує на
        // пів секунди», Pixelmon): списки перебудовуються, лише якщо файли в теці змінились (назви, розмір, дата). Раніше
        // кожне повернення на «Моди» наново будувало сотні рядків і знову питало Modrinth про сторони та оновлення
        if (page == MODS) fillModsIfChanged();
        if (page == RESOURCEPACKS) fillFilesIfChanged(packsList, new File(instance.getContentDir(), "resourcepacks"), "zip");
        if (page == SHADERS) fillFilesIfChanged(shadersList, new File(instance.getContentDir(), "shaderpacks"), "zip");
        // пошук Modrinth вантажиться, лише коли відкрита підвкладка «Знайти»
        if (subTabs[page] != null) subTabs[page].shown();
        if (page == WORLDS) fillWorlds();
        if (page == SCREENSHOTS) fillScreenshots();
    }

    private String kindLine() {
        if (instance.isCopy()) return tr("manage.kind.copy", InstanceCopies.kindTitleOf(instance));
        if (instance.isBuiltIn()) return InstanceCopies.kindTitleOf(instance);
        return tr("manage.kind.modpack");
    }

    @Override
    public void dispose() {
        if (shotsLoader != null) shotsLoader.cancel(true);
        super.dispose();
    }

    // ------------------------------------------------------------------ Загальне

    private JPanel generalPage() {
        JPanel p = page();

        // встановлення без запуску гри
        if (needsInstall(instance)) {
            final JPanel installCard = card();
            boolean update = instance.isInstalled();
            installCard.add(LookUi.title(tr(update ? "manage.install.titleUpdate" : "manage.install.title"), 15f));
            installCard.add(LookUi.body(tr(update ? "manage.install.hintUpdate" : "manage.install.hint"), true), "wmin 0");
            JButton install = LookUi.primary(new JButton(tr(update ? "manage.install.buttonUpdate" : "manage.install.button")));
            install.putClientProperty("FlatLaf.styleClass", null);
            JPanel row = new JPanel(new MigLayout("insets 0, fillx", "[]push", "[34!]"));
            row.setOpaque(false);
            row.add(install, "h 34!");
            installCard.add(row, "gaptop 6");
            install.addActionListener(e -> installOnly(this, launcher, instance, () -> {
                changed = true;
                installCard.setVisible(false);
                if (current == MODS) fillMods();
                if (host != null) host.instanceChanged();
            }));
            p.add(installCard, "hidemode 3");
        }

        // 22.11.2026 (власник: «джава для збірки має бути винесено одразу в налаштування збірки під "загальне" ... а не
        // лізти ... в самому низу маленька кнопочка»): пам'ять і Java одразу, першою карткою
        p.add(memJavaCard());

        // назва
        JPanel nameCard = card();
        nameCard.add(LookUi.title(tr("manage.name.title"), 15f));
        // 17.11.2026: свою назву, іконку і фон можна дати всьому встановленому, крім збірок GummerCraft і StalDayZ
        boolean canRename = com.gummer.launcher.manage.InstanceArt.supported(instance);
        if (canRename) {
            final JTextField name = new JTextField(instance.getCustomTitle() != null ? instance.getCustomTitle() : instance.getTitle());
            final String original = instance.getCustomTitle() == null ? instance.getTitle() : null;
            JButton save = LookUi.secondary(new JButton(tr("manage.name.save")));
            JButton reset = LookUi.secondary(new JButton(tr("manage.name.reset")));
            JPanel row = new JPanel(new MigLayout("insets 0, gap 8, fillx", "[grow,fill][][]", "[32!]"));
            row.setOpaque(false);
            row.add(name, "wmin 0, h 32!");
            row.add(save, "h 32!");
            row.add(reset, "h 32!");
            nameCard.add(LookUi.body(tr("manage.name.hint", InstanceCopies.MAX_TITLE), true), "wmin 0");
            nameCard.add(row, "wmin 0, gaptop 6");
            save.addActionListener(e -> rename(InstanceCopies.cleanTitle(name.getText()), original));
            name.addActionListener(e -> rename(InstanceCopies.cleanTitle(name.getText()), original));
            reset.addActionListener(e -> {
                rename(null, original);
                name.setText(instance.getTitle());
            });
        } else {
            nameCard.add(LookUi.body(tr(instance.isBuiltIn() ? "manage.name.notInstalled" : "manage.name.notForServers"), true), "wmin 0");
        }
        p.add(nameCard);

        // 17.11.2026: своя іконка і фон копії (InstanceArt)
        if (com.gummer.launcher.manage.InstanceArt.supported(instance)) p.add(artCard());

        // копія
        JPanel copyCard = card();
        copyCard.add(LookUi.title(tr("manage.copy.title"), 15f));
        if (InstanceCopies.canCopy(instance)) {
            copyCard.add(LookUi.body(tr("manage.copy.hint"), true), "wmin 0");
            String[] base = instance.isBuiltIn() ? null : InstanceCopies.packBaseOf(instance);
            if (base != null) {
                // копія модпаку GummerCraft стає звичайною збіркою Fabric
                copyCard.add(LookUi.body(tr("manage.copy.hintPack", "Fabric " + base[0].substring(com.gummer.launcher.fabric.FabricVersions.PREFIX.length())), true), "wmin 0");
            }
            final JTextField copyName = new JTextField(tr("manage.copy.defaultTitle", InstanceCopies.kindTitleOf(instance)));
            copyCard.add(copyName, "wmin 0, h 32!, gaptop 6");
            final JCheckBox mods = check(tr("manage.copy.mods"), true);
            final JCheckBox settings = check(tr("manage.copy.settings"), true);
            final JCheckBox worlds = check(tr("manage.copy.worlds"), false);
            final JCheckBox packs = check(tr("manage.copy.packs"), true);
            JPanel checks = new JPanel(new MigLayout("insets 0, wrap 2, gap 16 2, fillx", "[grow,fill][grow,fill]", ""));
            checks.setOpaque(false);
            checks.add(mods, "wmin 0");
            checks.add(settings, "wmin 0");
            checks.add(packs, "wmin 0");
            checks.add(worlds, "wmin 0");
            copyCard.add(checks, "wmin 0, gaptop 4");
            JButton make = LookUi.primary(new JButton(tr("manage.copy.make")));
            make.putClientProperty("FlatLaf.styleClass", null);
            JPanel row = new JPanel(new MigLayout("insets 0, fillx", "[]push", "[34!]"));
            row.setOpaque(false);
            row.add(make, "h 34!");
            copyCard.add(row, "gaptop 6");
            make.addActionListener(e -> {
                EnumSet<InstanceCopies.Part> parts = EnumSet.noneOf(InstanceCopies.Part.class);
                if (mods.isSelected()) parts.add(InstanceCopies.Part.MODS);
                if (settings.isSelected()) parts.add(InstanceCopies.Part.SETTINGS);
                if (worlds.isSelected()) parts.add(InstanceCopies.Part.WORLDS);
                if (packs.isSelected()) parts.add(InstanceCopies.Part.PACKS);
                makeCopy(copyName.getText(), parts);
            });
        } else {
            String why = com.gummer.launcher.manage.InstanceMods.packModsLocked(instance) ? "manage.copy.notForModpacks"
                    : !instance.isBuiltIn() && !instance.isInstalled() ? "manage.copy.notInstalled" : "manage.copy.notSupported";
            copyCard.add(LookUi.body(tr(why), true), "wmin 0");
        }
        p.add(copyCard);

        // 17.11.2026: модпак Modrinth: версія, оновлення на вибір гравця, повернення попередньої
        if (com.gummer.launcher.modrinth.MrpackUpdate.canUpdate(instance)) p.add(modpackCard());
        // 18.11.2026: копія: повернути до стану на момент копіювання
        if (com.gummer.launcher.manage.CopyReset.canReset(instance)) p.add(copyResetCard());

        // 17.11.2026: поділитися збіркою (файл .mrpack) і поставити чужу

        // теки і Java
        JPanel foldersCard = card();
        foldersCard.add(LookUi.title(tr("manage.folders.title"), 15f));
        JPanel grid = new JPanel(new MigLayout("insets 0, wrap 2, gap 8 8, fillx", "[grow,fill][grow,fill]", "[32!]"));
        grid.setOpaque(false);
        File content = instance.getContentDir();
        grid.add(folderButton(tr("manage.folders.instance"), content), "wmin 0");
        grid.add(folderButton(tr("manage.folders.screenshots"), new File(content, "screenshots")), "wmin 0");
        if (closedPack()) grid.add(folderButton(tr("manage.folders.shaders"), new File(content, "shaderpacks")), "wmin 0");
        else if (!instance.isVanilla() || vanillaHasResourcePacks()) grid.add(folderButton(tr("manage.folders.packs"), new File(content, "resourcepacks")), "wmin 0");
        grid.add(folderButton(tr("manage.folders.crashes"), new File(content, "crash-reports")), "wmin 0");
        grid.add(folderButton(tr("manage.folders.logs"), new File(content, "logs")), "wmin 0");
        foldersCard.add(grid, "wmin 0, gaptop 6");
        p.add(foldersCard);

        // видалити копію
        if (instance.isCopy() && host != null) {
            JPanel del = card();
            del.add(LookUi.title(tr("manage.delete.title"), 15f));
            del.add(LookUi.body(tr("manage.delete.hint"), true), "wmin 0");
            JButton b = new JButton(tr("manage.delete.button"));
            b.putClientProperty("FlatLaf.style", "foreground: " + LookUi.hex(pal.danger) + "; arc: " + pal.buttonArc);
            JPanel row = new JPanel(new MigLayout("insets 0, fillx", "[]push", "[32!]"));
            row.setOpaque(false);
            row.add(b, "h 32!");
            del.add(row, "gaptop 6");
            b.addActionListener(e -> {
                dispose();
                host.deleteInstance(instance);
            });
            p.add(del);
        }
        return p;
    }

    /** Текст поради щодо пам'яті для збірки: скільки модів, скільки радимо, скільки на ПК. */
    /**
     * 22.11.2026: картка «Пам'ять і Java» нагорі «Загального». Пам'ять: галочка «своя для цієї збірки» з повзунком (крок
     * 0,5 ГБ) і рядок, скільки гра отримає і чому (MemoryAdvice.effective, та сама логіка, що при запуску). Java: список
     * одразу тут («Автоматично» або встановлена Java), аргументи JVM за кнопкою (старе вікно InstanceSettingsDialogFlat).
     * Усе зберігається одразу, як і відеокарта.
     */
    private JPanel memJavaCard() {
        final JPanel c = card();
        c.add(LookUi.title(tr("manage.memjava.title"), 15f));
        final com.skcraft.launcher.InstanceSettings s = instance.getSettings();
        final com.skcraft.launcher.Configuration config = launcher.getConfig();

        final JCheckBox own = check(tr("manage.memjava.own"), s.getMemorySettings() != null);
        c.add(own, "gaptop 4, wmin 0");
        final JSlider slider = new JSlider();
        final JSpinner minSp = new JSpinner(), maxSp = new JSpinner();
        int start = com.gummer.launcher.manage.MemoryAdvice.effective(config, instance)[0];
        minSp.setValue(start);
        maxSp.setValue(start);
        com.gummer.launcher.swing.SwingExtras.configureMemorySlider(slider, minSp, maxSp);
        slider.setValue(start);
        slider.setOpaque(false);
        c.add(slider, "hidemode 3, wmin 0");
        final JTextArea now = LookUi.body(" ", true);
        c.add(now, "wmin 0");
        final Runnable refresh = () -> {
            int[] e = com.gummer.launcher.manage.MemoryAdvice.effective(config, instance);
            String gb = com.gummer.launcher.manage.MemoryAdvice.gb(e[0]);
            String text;
            if (e[1] == com.gummer.launcher.manage.MemoryAdvice.FROM_BUILD) text = tr("manage.memjava.fromBuild", gb);
            else if (e[1] == com.gummer.launcher.manage.MemoryAdvice.FROM_LIST) text = tr("manage.memjava.fromList", gb,
                    com.gummer.launcher.manage.MemoryAdvice.gb(config.getMaxMemory()));
            else if (e[1] == com.gummer.launcher.manage.MemoryAdvice.FROM_MODS) text = tr("manage.memjava.fromMods", gb,
                    com.gummer.launcher.manage.MemoryAdvice.modCount(instance), com.gummer.launcher.manage.MemoryAdvice.gb(config.getMaxMemory()));
            else if (e[1] == com.gummer.launcher.manage.MemoryAdvice.FROM_FLOOR) text = tr("manage.memjava.floor", gb,
                    com.gummer.launcher.manage.MemoryAdvice.gb(e[2]), com.gummer.launcher.manage.MemoryAdvice.modCount(instance));
            else text = tr("manage.memjava.fromSettings", gb);
            boolean much = com.gummer.launcher.manage.MemoryAdvice.tooMuch(e[0]);
            if (much) text += "\n" + tr("memory.tooMuchSetting", gb,
                    com.gummer.launcher.manage.MemoryAdvice.gb(com.gummer.launcher.manage.MemoryAdvice.totalMb()));
            now.setText(text);
            now.setForeground(much || e[1] == com.gummer.launcher.manage.MemoryAdvice.FROM_FLOOR ? pal.warn : pal.text2);
            slider.setVisible(own.isSelected());
            c.revalidate();
            c.repaint();
        };
        own.addActionListener(e -> {
            if (own.isSelected()) {
                com.skcraft.launcher.launch.MemorySettings m = new com.skcraft.launcher.launch.MemorySettings();
                m.setMinMemory(Math.min(config.getMinMemory(), slider.getValue()));
                m.setMaxMemory(slider.getValue());
                s.setMemorySettings(m);
            } else {
                s.setMemorySettings(null);
            }
            Persistence.commitAndForget(instance);
            changed = true;
            refresh.run();
        });
        slider.addChangeListener(e -> {
            if (!own.isSelected()) return;
            com.skcraft.launcher.launch.MemorySettings m = s.getMemorySettings();
            if (m == null) return;
            m.setMaxMemory(slider.getValue());
            if (m.getMinMemory() > slider.getValue()) m.setMinMemory(slider.getValue());
            refresh.run();
            if (!slider.getValueIsAdjusting()) {
                Persistence.commitAndForget(instance);
                changed = true;
            }
        });

        // Java
        java.util.List<Object> items = new java.util.ArrayList<Object>();
        items.add(tr("manage.memjava.javaAuto"));
        items.addAll(com.skcraft.launcher.launch.runtime.JavaRuntimeFinder.getAvailableRuntimes());
        if (s.getRuntime() != null && !items.contains(s.getRuntime())) items.add(s.getRuntime());
        final JComboBox<Object> javaBox = new JComboBox<Object>(items.toArray());
        javaBox.setSelectedItem(s.getRuntime() != null ? s.getRuntime() : items.get(0));
        JPanel row = new JPanel(new MigLayout("insets 0, gap 8, fillx", "[][grow,fill][]", "[32!]"));
        row.setOpaque(false);
        JLabel jl = new JLabel(tr("manage.memjava.java"));
        jl.setForeground(pal.text2);
        row.add(jl);
        row.add(javaBox, "wmin 0, h 32!");
        JButton more = LookUi.secondary(new JButton(tr("manage.memjava.args")));
        row.add(more, "h 32!");
        c.add(row, "wmin 0, gaptop 8");
        javaBox.addActionListener(e -> {
            Object sel = javaBox.getSelectedItem();
            com.skcraft.launcher.launch.runtime.JavaRuntime rt = sel instanceof com.skcraft.launcher.launch.runtime.JavaRuntime
                    ? (com.skcraft.launcher.launch.runtime.JavaRuntime) sel : null;
            if (rt == null ? s.getRuntime() == null : rt.equals(s.getRuntime())) return;
            s.setRuntime(rt);
            Persistence.commitAndForget(instance);
            changed = true;
            toast(tr("manage.memjava.saved"));
        });
        more.addActionListener(e -> {
            if (InstanceSettingsDialogFlat.open(this, instance)) {
                // старе вікно могло змінити і пам'ять, і Java: показати як є
                own.setSelected(s.getMemorySettings() != null);
                if (s.getMemorySettings() != null) slider.setValue(s.getMemorySettings().getMaxMemory());
                javaBox.setSelectedItem(s.getRuntime() != null ? s.getRuntime() : items.get(0));
                changed = true;
                refresh.run();
            }
        });
        refresh.run();
        return c;
    }

    static String memoryAdvice(Instance instance) {
        int mods = com.gummer.launcher.manage.MemoryAdvice.modCount(instance);
        int advice = com.gummer.launcher.manage.MemoryAdvice.recommended(instance);
        long total = com.gummer.launcher.manage.MemoryAdvice.totalMb();
        String a = com.gummer.launcher.manage.MemoryAdvice.gb(advice);
        return total > 0 ? tr("manage.memory.advice", mods, a, com.gummer.launcher.manage.MemoryAdvice.gb(total))
                : tr("manage.memory.adviceNoTotal", mods, a);
    }

    private static final java.util.Set<String> MEMORY_WARNED = new java.util.HashSet<String>();

    /**
     * 17.11.2026 (пункт плану «попередження, коли виділено забагато пам'яті»): перед запуском, якщо збірці виділено
     * більше половини пам'яті ПК, нагадати (раз на збірку за запуск лаунчера). Запуску не заважає.
     */
    public static void warnMemory(Window owner, Launcher launcher, Instance instance) {
        if (instance == null) return;
        com.skcraft.launcher.InstanceSettings st = instance.getSettings();
        int max = st != null && st.getMemorySettings() != null ? st.getMemorySettings().getMaxMemory() : launcher.getConfig().getMaxMemory();
        if (!com.gummer.launcher.manage.MemoryAdvice.tooMuch(max) || !MEMORY_WARNED.add(instance.getName())) return;
        showNotification(LauncherFrameFlat.rootFrame, LauncherFrameFlat.class, Notification.Type.WARNING,
                tr("memory.tooMuch", com.gummer.launcher.manage.MemoryAdvice.gb(max),
                        com.gummer.launcher.manage.MemoryAdvice.gb(com.gummer.launcher.manage.MemoryAdvice.totalMb())));
    }

    /**
     * 18.11.2026 (власник: «пресети ФПС ... щоб можна було додати на вже існуючі збірки»): налаштування гри
     * (options.txt) і моди для ФПС з Modrinth (manage.FpsPresets). Світів не стосується.
     */
    // ------------------------------------------------------------------ Продуктивність (FPS)

    /** 24.11.2026: окремий розділ між «Загальне» та «Імпорт/Експорт»: пресети FPS і відеокарта. */
    private JPanel perfPage() {
        JPanel p = page();
        // 18.11.2026: пресети ФПС для цієї (вже наявної) збірки
        p.add(fpsCard());
        // 19.11.2026 (власник: «Гра на вбудованій відеокарті ... в ідеалі перемикач в "збірка"»): лише на ПК з двома
        // відеокартами; список відеокарт у фоні (перший раз ~1 с), картка з'являється, коли він готовий
        p.add(gpuCard(), "hidemode 3");
        return p;
    }

    private JPanel fpsCard() {
        final JPanel c = card();
        c.add(LookUi.title(tr("fps.title"), 15f));
        c.add(LookUi.body(tr("fps.text"), true), "wmin 0");
        final JPanel row = new JPanel(new MigLayout("insets 0, gap 8", "[][]push", "[32!]"));
        row.setOpaque(false);
        // 24.11.2026: wmin 0, інакше ряд тримав свою останню ширину як мінімальну, і коли з'являлась смуга прокрутки
        // (картка «Відеокарта» додається вже після першої розкладки), уся сторінка вилазила за правий край
        c.add(row, "gaptop 6, wmin 0");
        final com.gummer.launcher.manage.FpsPresets.Preset[] presets = com.gummer.launcher.manage.FpsPresets.Preset.values();
        final JComboBox<String> pick = new JComboBox<String>();
        for (com.gummer.launcher.manage.FpsPresets.Preset pr : presets) pick.addItem(tr("fps.preset." + pr.name().toLowerCase(java.util.Locale.ROOT)));
        pick.setSelectedIndex(1);
        row.add(pick, "h 32!");
        JButton apply = LookUi.secondary(new JButton(tr("fps.apply")));
        apply.addActionListener(e -> {
            com.gummer.launcher.manage.FpsPresets.Preset pr = presets[pick.getSelectedIndex()];
            try {
                int n = com.gummer.launcher.manage.FpsPresets.applyOptions(instance, pr);
                SwingHelper.showMessageDialog(this, tr("fps.applied", (String) pick.getSelectedItem(), n), tr("fps.title"), null, JOptionPane.INFORMATION_MESSAGE);
            } catch (IOException ex) {
                SwingHelper.showErrorDialog(this, tr("fps.failed", String.valueOf(ex.getMessage())), tr("fps.title"));
            }
        });
        row.add(apply, "h 32!");
        // 18.11.2026: «Повернути налаштування гри» (те, що міняли пресети, як було до першого пресета)
        final JButton restore = LookUi.secondary(new JButton(tr("fps.restore")));
        restore.setVisible(com.gummer.launcher.manage.FpsPresets.canRestoreOptions(instance));
        restore.addActionListener(e -> {
            if (!SwingHelper.confirmDialog(this, tr("fps.restoreConfirm"), tr("fps.title"))) return;
            try {
                int n = com.gummer.launcher.manage.FpsPresets.restoreOptions(instance);
                restore.setVisible(false);
                row.revalidate();
                SwingHelper.showMessageDialog(this, tr("fps.restored", n), tr("fps.title"), null, JOptionPane.INFORMATION_MESSAGE);
            } catch (IOException ex) {
                SwingHelper.showErrorDialog(this, tr("fps.failed", String.valueOf(ex.getMessage())), tr("fps.title"));
            }
        });
        apply.addActionListener(e -> {
            restore.setVisible(com.gummer.launcher.manage.FpsPresets.canRestoreOptions(instance));
            row.revalidate();
        });
        row.add(restore, "h 32!");
        // 18.11.2026: порада пресета за пам'яттю і відеокартою ПК (реєстр читається у фоні), вибирається в списку
        final JTextArea advice = LookUi.body("", true);
        advice.setVisible(false);
        c.add(advice, "wmin 0, gaptop 4");
        final boolean[] touched = {false};
        pick.addActionListener(e -> touched[0] = true);
        final java.util.function.Consumer<com.gummer.launcher.manage.FpsPresets.Advice> showAdvice = a -> {
            String title = tr("fps.preset." + a.preset.name().toLowerCase(java.util.Locale.ROOT));
            java.util.List<String> why = new ArrayList<String>();
            if (a.ramGb > 0) why.add(tr("fps.advice.ram", a.ramGb));
            if (a.gpu != null) why.add(a.gpu);
            advice.setText(why.isEmpty() ? tr("fps.adviceShort", title) : tr("fps.advice", title, String.join(", ", why)));
            advice.setVisible(true);
            if (!touched[0]) {
                pick.setSelectedIndex(a.preset.ordinal());
                touched[0] = false;
            }
            c.revalidate();
            c.repaint();
        };
        // 24.11.2026 (власник: «"красиво" проглюкує на мілісекунду, очевидно лаунчер зчитує конфігурацію компа перш ніж
        // показати "красиво"»): порада рахується у фоні ще на старті лаунчера, тож тут вона майже завжди вже є і
        // ставиться до першого показу. Лише якщо ще ні (розділ відкрили в першу секунду), як раніше, у фоні
        com.gummer.launcher.manage.FpsPresets.Advice ready = com.gummer.launcher.manage.FpsPresets.peek();
        if (ready != null) {
            showAdvice.accept(ready);
        } else {
            new SwingWorker<com.gummer.launcher.manage.FpsPresets.Advice, Void>() {
                @Override
                protected com.gummer.launcher.manage.FpsPresets.Advice doInBackground() {
                    return com.gummer.launcher.manage.FpsPresets.advise();
                }

                @Override
                protected void done() {
                    try {
                        showAdvice.accept(get());
                    } catch (Exception ignored) {
                        // без поради
                    }
                }
            }.execute();
        }
        if (com.gummer.launcher.manage.FpsPresets.canAddMods(instance)) {
            final JPanel mods = new JPanel(new MigLayout("insets 0, gap 8", "[][]push", "[32!]"));
            mods.setOpaque(false);
            c.add(LookUi.body(tr("fps.modsText"), true), "wmin 0, gaptop 8");
            c.add(mods, "gaptop 4");
            final JButton add = LookUi.secondary(new JButton(tr("fps.addMods")));
            add.addActionListener(e -> {
                if (!SwingHelper.confirmDialog(this, tr("fps.addConfirm", instance.getTitle()), tr("fps.title"))) return;
                add.setEnabled(false);
                add.setText(tr("crash.action.working"));
                new SwingWorker<com.gummer.launcher.manage.FpsPresets.Result, Void>() {
                    @Override
                    protected com.gummer.launcher.manage.FpsPresets.Result doInBackground() throws Exception {
                        return com.gummer.launcher.manage.FpsPresets.installMods(instance);
                    }

                    @Override
                    protected void done() {
                        add.setText(tr("fps.addMods"));
                        add.setEnabled(true);
                        com.gummer.launcher.manage.FpsPresets.Result r;
                        try {
                            r = get();
                        } catch (Exception ex) {
                            Throwable t = ex.getCause() != null ? ex.getCause() : ex;
                            SwingHelper.showErrorDialog(InstanceManageDialog.this, tr("fps.failed", String.valueOf(t.getMessage())), tr("fps.title"));
                            return;
                        }
                        changed = true;
                        String msg = r.installed.isEmpty() ? tr("fps.addedNone") : tr("fps.added", String.join(", ", r.installed));
                        if (!r.skipped.isEmpty()) msg += "\n\n" + tr("fps.skipped") + "\n" + String.join("\n", r.skipped);
                        SwingHelper.showMessageDialog(InstanceManageDialog.this, msg, tr("fps.title"), null, JOptionPane.INFORMATION_MESSAGE);
                    }
                }.execute();
            });
            mods.add(add, "h 32!");
            if (!com.gummer.launcher.manage.FpsPresets.addedMods(instance).isEmpty()) {
                JButton remove = LookUi.secondary(new JButton(tr("fps.removeMods")));
                remove.addActionListener(e -> {
                    if (!SwingHelper.confirmDialog(this, tr("fps.removeConfirm", String.join(", ",
                            com.gummer.launcher.manage.FpsPresets.addedMods(instance))), tr("fps.title"))) return;
                    try {
                        int n = com.gummer.launcher.manage.FpsPresets.removeMods(instance);
                        changed = true;
                        SwingHelper.showMessageDialog(this, tr("fps.removed", n), tr("fps.title"), null, JOptionPane.INFORMATION_MESSAGE);
                        remove.setVisible(false);
                    } catch (IOException ex) {
                        SwingHelper.showErrorDialog(this, tr("fps.failed", String.valueOf(ex.getMessage())), tr("fps.title"));
                    }
                });
                mods.add(remove, "h 32!");
            }
        }
        return c;
    }

    /**
     * 18.11.2026 (власник: «повернення до початкового стану для копій збірок»): картка копії на «Загальне»: повернути
     * до стану на момент копіювання (manage.CopyReset) і скасувати останнє повернення.
     */
    private JPanel copyResetCard() {
        final JPanel c = card();
        c.add(LookUi.title(tr("copyReset.title"), 15f));
        c.add(LookUi.body(tr("copyReset.text"), true), "wmin 0");
        final JPanel row = new JPanel(new MigLayout("insets 0, gap 8", "[][]push", "[32!]"));
        row.setOpaque(false);
        c.add(row, "gaptop 6");
        final JButton go = LookUi.secondary(new JButton(tr("copyReset.go")));
        go.addActionListener(e -> {
            go.setEnabled(false);
            new SwingWorker<com.gummer.launcher.manage.CopyReset.Plan, Void>() {
                @Override
                protected com.gummer.launcher.manage.CopyReset.Plan doInBackground() throws Exception {
                    return com.gummer.launcher.manage.CopyReset.plan(instance);
                }

                @Override
                protected void done() {
                    go.setEnabled(true);
                    final com.gummer.launcher.manage.CopyReset.Plan plan;
                    try {
                        plan = get();
                    } catch (Exception ex) {
                        SwingHelper.showErrorDialog(InstanceManageDialog.this, tr("copyReset.failed", String.valueOf(ex.getMessage())), tr("copyReset.title"));
                        return;
                    }
                    if (plan.isEmpty()) {
                        SwingHelper.showMessageDialog(InstanceManageDialog.this, tr("copyReset.nothing"), tr("copyReset.title"), null, JOptionPane.INFORMATION_MESSAGE);
                        return;
                    }
                    if (!SwingHelper.confirmDialog(InstanceManageDialog.this, tr("copyReset.confirm", instance.getTitle(), plan.restore.size(),
                            plan.enable.size() + plan.disable.size(), plan.remove.size()), tr("copyReset.title"))) return;
                    final com.google.common.util.concurrent.ListenableFuture<List<String>> task =
                            launcher.getExecutor().submit(() -> com.gummer.launcher.manage.CopyReset.apply(instance, plan));
                    ProgressDialog.showProgress(InstanceManageDialog.this, new ObservableFuture<List<String>>(task, new com.skcraft.concurrency.ProgressObservable() {
                                @Override
                                public double getProgress() {
                                    return -1;
                                }

                                @Override
                                public String getStatus() {
                                    return tr("copyReset.working", instance.getTitle());
                                }
                            }),
                            tr("copyReset.title"), tr("copyReset.working", instance.getTitle()));
                    task.addListener(() -> {
                        try {
                            List<String> lost = task.get();
                            changed = true;
                            String msg = tr("copyReset.done", instance.getTitle());
                            if (!lost.isEmpty()) msg += "\n\n" + tr("copyReset.lost", String.join(", ", lost.subList(0, Math.min(10, lost.size()))));
                            SwingHelper.showMessageDialog(InstanceManageDialog.this, msg, tr("copyReset.title"), null,
                                    lost.isEmpty() ? JOptionPane.INFORMATION_MESSAGE : JOptionPane.WARNING_MESSAGE);
                            dispose();
                        } catch (Exception ex) {
                            Throwable t = ex.getCause() != null ? ex.getCause() : ex;
                            SwingHelper.showErrorDialog(InstanceManageDialog.this, tr("copyReset.failed", String.valueOf(t.getMessage())), tr("copyReset.title"));
                        }
                    }, SwingExecutor.INSTANCE);
                }
            }.execute();
        });
        row.add(go, "h 32!");
        if (com.gummer.launcher.manage.CopyReset.hasBackup(instance)) {
            JButton undo = LookUi.secondary(new JButton(tr("copyReset.undo")));
            undo.addActionListener(e -> {
                if (!SwingHelper.confirmDialog(this, tr("copyReset.undoConfirm", instance.getTitle()), tr("copyReset.title"))) return;
                try {
                    com.gummer.launcher.manage.CopyReset.restore(instance);
                    changed = true;
                    dispose();
                } catch (IOException ex) {
                    SwingHelper.showErrorDialog(this, tr("copyReset.failed", String.valueOf(ex.getMessage())), tr("copyReset.title"));
                }
            });
            row.add(undo, "h 32!");
        }
        return c;
    }

    /** 17.11.2026: картка модпака Modrinth на «Загальне»: версія, «Оновити до ...» (перевірка у фоні), «Повернути». */
    private JPanel modpackCard() {
        final JPanel c = card();
        c.add(LookUi.title(tr("mrpack.card.title"), 15f));
        com.fasterxml.jackson.databind.JsonNode info = com.gummer.launcher.modrinth.MrpackPacks.info(instance);
        String number = info == null ? "?" : info.path("versionNumber").asText(info.path("versionId").asText("?"));
        final JTextArea state = LookUi.body(tr("mrpack.card.version", number) + " " + tr("mrpack.card.checking"), true);
        c.add(state, "wmin 0");
        final JPanel row = new JPanel(new MigLayout("insets 0, gap 8", "[][]push", "[32!]"));
        row.setOpaque(false);
        c.add(row, "gaptop 6, hidemode 3");
        final String backup = com.gummer.launcher.modrinth.MrpackUpdate.backupVersion(instance);
        if (backup != null) {
            JButton back = LookUi.secondary(new JButton(tr("mrpack.card.rollback", backup)));
            back.addActionListener(e -> MrpackUpdateFlow.rollback(this, launcher, instance, i -> {
                dispose();
                if (host != null) host.buildUpdated(i);
            }));
            row.add(back, "h 32!");
        }
        final String shown = number;
        new SwingWorker<com.gummer.launcher.modrinth.MrpackUpdate.Check, Void>() {
            @Override
            protected com.gummer.launcher.modrinth.MrpackUpdate.Check doInBackground() throws Exception {
                return com.gummer.launcher.modrinth.MrpackUpdate.check(instance);
            }

            @Override
            protected void done() {
                com.gummer.launcher.modrinth.MrpackUpdate.Check ch;
                try {
                    ch = get();
                } catch (Exception e) {
                    state.setText(tr("mrpack.card.version", shown) + " " + tr("mrpack.card.checkFailed"));
                    return;
                }
                if (ch == null) return;
                String text = tr("mrpack.card.version", shown) + " ";
                if (ch.newest != null) {
                    text += tr("mrpack.card.newer", ch.newest.number);
                    final com.gummer.launcher.manage.ModrinthBrowse.PackVersion target = ch.newest;
                    JButton go = LookUi.primary(new JButton(tr("mrpack.card.update", target.number)));
                    go.putClientProperty("FlatLaf.styleClass", null);
                    go.addActionListener(e -> MrpackUpdateFlow.start(InstanceManageDialog.this, launcher, instance, target, i -> {
                        dispose();
                        if (host != null) host.buildUpdated(i);
                    }));
                    row.add(go, "h 32!", 0);
                } else {
                    text += tr("mrpack.card.latest");
                }
                if (ch.otherGame != null) text += " " + tr("mrpack.card.otherGame", ch.otherGame.number, ch.otherGame.game());
                if (ch.current != null) {
                    // 18.11.2026: повернути збірку до стану після встановлення модпака (з резервною копією)
                    final com.gummer.launcher.manage.ModrinthBrowse.PackVersion cur = ch.current;
                    JButton reset = LookUi.secondary(new JButton(tr("mrpack.card.reset")));
                    reset.setToolTipText(tr("mrpack.card.resetTip"));
                    reset.addActionListener(e -> MrpackUpdateFlow.reset(InstanceManageDialog.this, launcher, instance, cur, i -> {
                        dispose();
                        if (host != null) host.buildUpdated(i);
                    }));
                    row.add(reset, "h 32!");
                }
                state.setText(text);
                c.revalidate();
                c.repaint();
            }
        }.execute();
        return c;
    }

    private void rename(String title, String original) {
        instance.setCustomTitle(title);
        if (title != null) {
            instance.setTitle(title);
        } else if (InstanceCopies.builtInTitleOf(instance) != null) {
            instance.setTitle(InstanceCopies.builtInTitleOf(instance)); // копія, версія, завантажувач, модпак Modrinth
        } else if (original != null) {
            instance.setTitle(original);
        } // інакше назву зі списку модпаків поверне перечитування списку
        Persistence.commitAndForget(instance);
        headTitle.setText(instance.getTitle());
        changed = true;
        toast(tr("manage.name.saved"));
    }

    /** Чи є що ставити: збірку не встановлено або чекає оновлення. */
    public static boolean needsInstall(Instance instance) {
        return instance != null && (!instance.isInstalled() || instance.isUpdatePending());
    }

    /**
     * 17.11.2026: встановити чи оновити збірку без запуску гри (вирішено: «дозволь просто встановити збірку/версію,
     * не заходячи одразу на сервер»). Той самий Updater, що перед запуском, але без входу в акаунт і без гри.
     * done викликається на потоці інтерфейсу після успіху.
     */
    public static void installOnly(final Window owner, final Launcher launcher, final Instance instance, final Runnable done) {
        if (instance == null) return;
        final boolean wasInstalled = instance.isInstalled();
        com.skcraft.launcher.update.Updater updater = new com.skcraft.launcher.update.Updater(launcher, instance);
        updater.setOnline(true);
        ObservableFuture<Instance> future = new ObservableFuture<Instance>(launcher.getExecutor().submit(updater), updater);
        ProgressDialog.showProgress(owner, future, SharedLocale.tr("launcher.updatingTitle"),
                SharedLocale.tr("launcher.updatingStatus", instance.getTitle()));
        SwingHelper.addErrorDialogCallback(owner, future);
        future.addListener(() -> {
            try {
                future.get();
                showNotification(LauncherFrameFlat.rootFrame, LauncherFrameFlat.class, Notification.Type.SUCCESS,
                        SharedLocale.tr(wasInstalled ? "manage.install.updated" : "manage.install.done", instance.getTitle()));
                if (done != null) done.run();
            } catch (Exception ignored) {
                // помилку показав addErrorDialogCallback
            }
        }, SwingExecutor.INSTANCE);
    }

    /** 17.11.2026: «Дублювати» в меню збірки: встановлена збірка, яку можна копіювати (не StalDayZ). */
    public static boolean canQuickCopy(Instance instance) {
        return instance != null && instance.isLocal() && instance.isInstalled() && InstanceCopies.canCopy(instance)
                && !com.gummer.launcher.manage.InstanceMods.packModsLocked(instance);
    }

    /**
     * 17.11.2026 (власник: «у встановлених гравець міг натиснути пкм -> дублювати (зробити копію прямо там, швидко).
     * Але не для сталдейзу»): копія в один клац з тим, що «Копія» у вікні «Збірка» бере за замовчуванням (моди,
     * налаштування, ресурспаки; світи ні, вони бувають на гігабайти). created отримує назву нової збірки.
     */
    public static void quickCopy(final Window owner, final Launcher launcher, final Instance instance,
                                 final java.util.function.Consumer<String> created) {
        if (!canQuickCopy(instance)) return;
        String base = com.skcraft.launcher.swing.InstanceTableModel.isNamed(instance) ? instance.getTitle()
                : InstanceCopies.kindTitleOf(instance);
        final String title = SharedLocale.tr("manage.copy.defaultTitle", base);
        final InstanceCopies.Copier copier = new InstanceCopies.Copier(launcher, instance, title,
                EnumSet.of(InstanceCopies.Part.MODS, InstanceCopies.Part.SETTINGS, InstanceCopies.Part.PACKS));
        final com.google.common.util.concurrent.ListenableFuture<String> task = launcher.getExecutor().submit(copier);
        ObservableFuture<String> future = new ObservableFuture<String>(task, copier);
        ProgressDialog.showProgress(owner, future, SharedLocale.tr("manage.copy.progressTitle"), SharedLocale.tr("manage.copy.preparing"));
        SwingHelper.addErrorDialogCallback(owner, future);
        future.addListener(() -> {
            try {
                String name = task.get();
                String shown = InstanceCopies.cleanTitle(title);
                showNotification(LauncherFrameFlat.rootFrame, LauncherFrameFlat.class, Notification.Type.SUCCESS,
                        SharedLocale.tr("manage.copy.quickDone", shown != null ? shown : title));
                if (created != null) created.accept(name);
            } catch (Exception ignored) {
                // помилку показав addErrorDialogCallback, недороблену копію Copier прибрав
            }
        }, SwingExecutor.INSTANCE);
    }

    private void makeCopy(String title, EnumSet<InstanceCopies.Part> parts) {
        final InstanceCopies.Copier copier = new InstanceCopies.Copier(launcher, instance, title, parts);
        com.google.common.util.concurrent.ListenableFuture<String> task = launcher.getExecutor().submit(copier);
        ObservableFuture<String> future = new ObservableFuture<String>(task, copier);
        ProgressDialog.showProgress(this, future, tr("manage.copy.progressTitle"), tr("manage.copy.preparing"));
        SwingHelper.addErrorDialogCallback(this, future);
        future.addListener(() -> {
            try {
                String name = task.get();
                changed = false; // список перечитає copyCreated
                dispose();
                toast(tr("manage.copy.created", InstanceCopies.cleanTitle(title) != null ? InstanceCopies.cleanTitle(title)
                        : tr("manage.copy.defaultTitle", InstanceCopies.kindTitleOf(instance))));
                if (host != null) host.copyCreated(name);
            } catch (Exception ignored) {
                // помилку показав addErrorDialogCallback, недороблену копію Copier прибрав
            }
        }, SwingExecutor.INSTANCE);
    }

    // ------------------------------------------------------------------ Моди

    private JPanel modsPage() {
        JPanel p = sub();
        if (instance.isVanilla()) {
            JPanel c = card();
            c.add(LookUi.title(tr("manage.mods.title"), 15f));
            c.add(LookUi.body(tr("manage.mods.vanilla"), true), "wmin 0");
            p.add(c);
            return tabbed(MODS, p, null, null, null);
        }
        optionalCard = card();
        optionalCard.add(LookUi.title(tr("manage.mods.optionalTitle"), 15f));
        optionalCard.add(LookUi.body(tr("manage.mods.optionalHint"), true), "wmin 0");
        optionalCard.add(optionalList, "wmin 0, gaptop 6");
        optionalCard.setVisible(false);
        p.add(optionalCard, "hidemode 3");

        JPanel c = card();
        JPanel head = new JPanel(new MigLayout("insets 0, gap 8, fillx", "[grow][][][]", "[]"));
        head.setOpaque(false);
        head.add(LookUi.title(tr("manage.mods.title"), 15f));
        // 17.11.2026: «Оновити все (N)», з'являється, коли Modrinth знайшов новіші версії своїх модів гравця
        LookUi.primary(updateAll);
        updateAll.setVisible(false);
        updateAll.addActionListener(e -> updateMods(new ArrayList<ModRow>(pendingUpdates.keySet())));
        head.add(updateAll, "hidemode 3");
        head.add(addButton(MODS, InstanceMods.modsDir(instance), "jar", "zip"));
        head.add(folderButton(tr("manage.openFolder"), InstanceMods.modsDir(instance)));
        c.add(head, "wmin 0");
        String hint = instance.isBuiltIn() ? "manage.mods.hint"
                : InstanceMods.packModsLocked(instance) ? "manage.mods.hintModpack" : "manage.mods.hintPack";
        c.add(LookUi.body(tr(hint), true), "wmin 0");
        c.add(LookUi.body(tr("manage.side.legend"), true), "wmin 0, gaptop 4"); // 17.11.2026: що значать позначки

        // пошук і лічильник «Увімкнено N з M»
        modsSearch.putClientProperty("JTextField.placeholderText", tr("manage.mods.search"));
        modsSearch.putClientProperty("JTextField.showClearButton", true);
        modsSearch.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e) {
                filterMods();
            }

            public void removeUpdate(javax.swing.event.DocumentEvent e) {
                filterMods();
            }

            public void changedUpdate(javax.swing.event.DocumentEvent e) {
                filterMods();
            }
        });
        JPanel tools = new JPanel(new MigLayout("insets 0, gap 10, fillx", "[grow,fill][][][]", "[32!]"));
        tools.setOpaque(false);
        tools.add(modsSearch, "wmin 0, h 32!");
        // 26.11.2026 (власник: «автоматизована гра з другом ... максимально юзер френдлі для новачка/дитини»): помічник
        // ставить і сам налаштовує мод для гри поруч чи через інтернет (FriendPlayDialog); у збірках GummerCraft немає
        if (com.gummer.launcher.manage.FriendPlay.availability(instance) == com.gummer.launcher.manage.FriendPlay.Availability.OK) {
            JButton friend = LookUi.secondary(new JButton(tr("friend.button")));
            friend.setToolTipText(tr("friend.buttonTip"));
            friend.setFocusable(false);
            friend.addActionListener(e -> {
                FriendPlayDialog.open(this, launcher, instance, () -> select(IMPEX), null);
                changed = true;
                modsShown = null;
                fillMods();
            });
            tools.add(friend, "h 32!");
        }
        // 25.11.2026 (власник: «"останні додані", щоб можна було глянути останні додані моди (сортування за назвою/за
        // датою)»): нові спершу за датою появи файлу в теці (InstanceMods.Mod#added); вибір запам'ятовується
        modsSort.addItem(tr("manage.mods.sortName"));
        modsSort.addItem(tr("manage.mods.sortDate"));
        // 25.11.2026 (власник: «третю - "є оновлення", щоб гравець сам побачив, які моди треба оновити, а не скролив все
        // підряд»): лише моди з позначкою «оновлення». Не запам'ятовується: наступного разу список відкривається повністю
        modsSort.addItem(tr("manage.mods.sortUpdates"));
        modsSort.setSelectedIndex(modsByDate() ? 1 : 0);
        modsSort.setFocusable(false);
        modsSort.addActionListener(e -> {
            if (modsUpdatesOnly()) {
                filterMods();
                return;
            }
            boolean date = modsSort.getSelectedIndex() == 1;
            if (date == modsByDate()) {
                filterMods(); // повернення з «Є оновлення»: порядок той самий, лише показати всі
                return;
            }
            com.gummer.launcher.prefs.LauncherPrefs.set(PREF_MODS_SORT, date ? "date" : "name");
            modsShown = null;
            fillMods();
        });
        tools.add(modsSort, "h 32!");
        modsCount.setForeground(pal.text3);
        modsCount.setFont(modsCount.getFont().deriveFont(12f));
        tools.add(modsCount);
        c.add(tools, "wmin 0, gaptop 8, hidemode 3");
        c.add(modsList, "wmin 0, gaptop 6");
        p.add(c);
        // 17.11.2026: моди з Modrinth на підвкладці «Знайти моди»: Fabric, Quilt, Forge, NeoForge, їхні копії і модпаки
        // GummerCraft; не Vanilla і не StalDayZ (його сервер звіряє моди)
        if (com.gummer.launcher.manage.ModrinthBrowse.loadersOf(com.gummer.launcher.manage.ModrinthBrowse.Kind.MOD, instance) != null) {
            modsBrowse = new Browse(com.gummer.launcher.manage.ModrinthBrowse.Kind.MOD, InstanceMods.modsDir(instance), this::fillMods);
        }
        // 17.11.2026: модпаки під цю ж гру і завантажувач (ставляться окремою збіркою); лише розділи Fabric, Quilt,
        // Forge, NeoForge і їхні копії
        Browse packs = modsBrowse != null && com.gummer.launcher.manage.ModrinthBrowse.loadersOf(com.gummer.launcher.manage.ModrinthBrowse.Kind.MODPACK, instance) != null
                ? new Browse(com.gummer.launcher.manage.ModrinthBrowse.Kind.MODPACK, null, () -> { }) : null;
        // 26.11.2026 (власник: «Набори модів» у «Керуванні збіркою», «робимо вже»): готові набори між «Мої моди» і
        // «Знайти моди»; лише там, де моди веде гравець (не GummerCraft і не StalDayZ)
        ModSetsPage sets = modsBrowse != null && com.gummer.launcher.manage.ModSets.available(instance)
                ? new ModSetsPage(this, instance, () -> {
                    FriendPlayDialog.open(this, launcher, instance, () -> select(IMPEX), null);
                    changed = true;
                    modsShown = null;
                    fillMods();
                }, fpsCard(), () -> {
                    changed = true;
                    modsShown = null;
                    fillMods();
                }) : null;
        return tabbed(MODS, p, modsBrowse, "manage.tab.mineMods", "manage.tab.findMods", packs, "manage.tab.modpacks", sets, "manage.tab.sets");
    }

    private final JTextField modsSearch = new JTextField();
    private final JComboBox<String> modsSort = new JComboBox<String>();
    /** 25.11.2026: порядок «Моїх модів» ({@code launcher-ui.json}): name або date (нові спершу). */
    private static final String PREF_MODS_SORT = "modsSort";

    private static boolean modsByDate() {
        return "date".equals(com.gummer.launcher.prefs.LauncherPrefs.getString(PREF_MODS_SORT, "name"));
    }

    /** 25.11.2026: вибрано «Є оновлення» (показати лише моди, для яких знайдено новішу версію). */
    private boolean modsUpdatesOnly() {
        return modsSort.getSelectedIndex() == 2;
    }
    private final JLabel modsCount = new JLabel();
    private final List<ModRow> modRows = new ArrayList<ModRow>();
    /** Підпис вмісту списку модів: файли (назва, розмір, дата) і вибір необов'язкових модів. */
    private String modsSignature(List<InstanceMods.Mod> mods, List<InstanceMods.Optional> optionals) {
        StringBuilder b = new StringBuilder();
        for (InstanceMods.Mod m : mods) {
            b.append(m.file.getName()).append('|').append(m.file.length()).append('|').append(m.file.lastModified())
                    .append('|').append(m.source).append('\n');
        }
        for (InstanceMods.Optional o : optionals) b.append(o.name).append('=').append(o.selected).append('\n');
        return b.toString();
    }

    private String modsShown;

    /** Повернення на «Моди»: перебудувати список, лише якщо щось змінилось (див. {@link #show}). */
    private void fillModsIfChanged() {
        if (instance.isVanilla()) return;
        if (modsShown != null && modsShown.equals(modsSignature(InstanceMods.list(instance), InstanceMods.optionals(instance)))) return;
        fillMods();
    }

    private void fillMods() {
        if (instance.isVanilla()) return;
        optionalList.removeAll();
        List<InstanceMods.Optional> optionals = InstanceMods.optionals(instance);
        for (final InstanceMods.Optional o : optionals) {
            final JCheckBox box = check(o.name, o.selected);
            JPanel row = rowPanel();
            row.add(box, "wmin 0");
            if (!o.description.isEmpty()) {
                JLabel d = small(o.description);
                row.add(d, "wmin 0, gapleft 26, newline");
            }
            box.addActionListener(e -> {
                try {
                    InstanceMods.setOptional(instance, o.name, box.isSelected());
                    changed = true;
                    toast(tr("manage.mods.optionalSaved"));
                } catch (IOException ex) {
                    box.setSelected(!box.isSelected());
                    SwingHelper.showErrorDialog(this, tr("manage.mods.saveFailed"), tr("errorTitle"), ex);
                }
            });
            optionalList.add(row, "wmin 0");
        }
        if (optionalCard != null) optionalCard.setVisible(!optionals.isEmpty());
        optionalList.revalidate();

        // моди: файли одразу, назви, описи й іконки з jar у фоні (200 модів читаються секунду-дві)
        final int gen = ++modsGen; // старі незакінчені читання і порції рядків більше нічого не показують
        final List<InstanceMods.Mod> mods = InstanceMods.list(instance);
        modsShown = modsSignature(mods, optionals);
        modsList.removeAll();
        modRows.clear();
        pendingUpdates.clear();
        updateAll.setVisible(false);
        modsSearch.getParent().setVisible(mods.size() > 6);
        if (mods.isEmpty()) {
            modsList.add(small(tr(instance.isBuiltIn() || instance.isInstalled() ? "manage.mods.empty" : "manage.mods.notInstalled")), "wmin 0");
            modsCount.setText("");
            modsList.revalidate();
            modsList.repaint();
            return;
        }
        // 24.11.2026 (власник: «всі списки мають оновлюватись без підгрузок»): назви, описи й іконки вже прочитані
        // (попереднє відкриття або фонове читання, що стартує з відкриттям вікна, див. prefetchMods): список одразу,
        // без «Завантаження...»
        List<Object[]> ready = new ArrayList<Object[]>(mods.size());
        for (InstanceMods.Mod m : mods) {
            com.gummer.launcher.manage.ModInfo info = com.gummer.launcher.manage.ModInfo.peek(m.file);
            Image icon = info == null ? null : MOD_ICONS.get(modIconKey(m.file));
            if (info == null || icon == null) {
                ready = null;
                break;
            }
            ready.add(new Object[]{m, info, icon == NO_ICON ? null : icon});
        }
        if (ready != null) {
            showModRows(ready, gen);
            return;
        }
        modsList.add(small(tr("manage.mods.loading")), "wmin 0");
        modsList.revalidate();
        modsList.repaint();
        java.util.concurrent.CompletableFuture<List<Object[]>> read = modsPrefetch;
        if (read == null || !modsShown.equals(modsPrefetchSig)) {
            read = java.util.concurrent.CompletableFuture.supplyAsync(() -> readModRows(mods));
        }
        read.whenComplete((rows, err) -> SwingUtilities.invokeLater(() -> {
            if (gen != modsGen) return;
            if (err != null) {
                modsShown = null; // наступне повернення на вкладку спробує ще раз
                modsList.removeAll();
                Throwable cause = err instanceof java.util.concurrent.CompletionException && err.getCause() != null ? err.getCause() : err;
                modsList.add(small(String.valueOf(cause.getMessage())), "wmin 0");
                modsList.revalidate();
                modsList.repaint();
                return;
            }
            showModRows(rows, gen);
        }));
    }

    /** Номер останнього заповнення списку модів: відповіді старих читань і порцій рядків відкидаються. */
    private int modsGen;
    /** Фонове читання модів, що стартує з відкриттям вікна (prefetchMods), і підпис теки, для якої воно. */
    private volatile java.util.concurrent.CompletableFuture<List<Object[]>> modsPrefetch;
    private volatile String modsPrefetchSig;
    /**
     * Скільки рядків модів додається за одну подію потоку інтерфейсу (решта наступними, щоб вікно не завмирало): перша
     * порція трохи більша за видиму частину списку, далі більшими.
     */
    private static final int MOD_ROWS_FIRST = 16, MOD_ROWS_CHUNK = 32;

    /** Назви, описи й іконки з jar (паралельно, порядок списку зберігається). Не з потоку інтерфейсу. */
    private static List<Object[]> readModRows(List<InstanceMods.Mod> mods) {
        // 17.11.2026: jar читаються паралельно (опис і іконка з кожного), порядок списку зберігається
        long t0 = System.nanoTime();
        List<Object[]> out = mods.parallelStream().map(m -> {
            com.gummer.launcher.manage.ModInfo info = com.gummer.launcher.manage.ModInfo.of(m.file);
            return new Object[]{m, info, modIcon(m.file, info)};
        }).collect(java.util.stream.Collectors.toList());
        java.util.logging.Logger.getLogger(InstanceManageDialog.class.getName()).info("Mods list: " + out.size()
                + " jar read in " + (System.nanoTime() - t0) / 1000000 + " ms");
        return out;
    }

    /**
     * 24.11.2026: з відкриттям вікна моди збірки читаються у фоні, поки гравець ще на «Загальне», тож «Моди» потім
     * показуються одразу. Кеш ModInfo і іконок спільний, повторне читання того самого файлу нічого не коштує.
     */
    private void prefetchMods() {
        if (instance.isVanilla() || closedPack() || modsPrefetch != null) return;
        java.util.concurrent.CompletableFuture<List<Object[]>> f = java.util.concurrent.CompletableFuture.supplyAsync(() -> {
            List<InstanceMods.Mod> mods = InstanceMods.list(instance);
            modsPrefetchSig = modsSignature(mods, InstanceMods.optionals(instance));
            List<Object[]> rows = readModRows(mods);
            for (InstanceMods.Mod m : mods) com.gummer.launcher.manage.ModSides.cached(m.file); // позначки з диска
            return rows;
        });
        modsPrefetch = f;
    }

    /**
     * Рядки модів у список. 24.11.2026: перші {@value #MOD_ROWS_FIRST} одразу, решта порціями наступними подіями: на
     * збірці зі 196 модами створення і розкладка всіх рядків разом тримали вікно ~0,1-0,15 с.
     */
    private void showModRows(final List<Object[]> byName, final int gen) {
        final List<Object[]> rows = new ArrayList<Object[]>(byName);
        if (modsByDate()) { // нові спершу; однакова дата: за назвою, як прийшло
            rows.sort((a, b) -> Long.compare(((InstanceMods.Mod) b[0]).added, ((InstanceMods.Mod) a[0]).added));
        }
        modsList.removeAll();
        final String q = modsSearch.getText().trim().toLowerCase(Locale.ROOT);
        final int[] at = {0};
        final Runnable[] step = new Runnable[1];
        step[0] = () -> {
            if (gen != modsGen) return;
            int to = Math.min(rows.size(), at[0] + (at[0] == 0 ? MOD_ROWS_FIRST : MOD_ROWS_CHUNK));
            for (int k = at[0]; k < to; k++) {
                Object[] r = rows.get(k);
                ModRow row = new ModRow((InstanceMods.Mod) r[0], (com.gummer.launcher.manage.ModInfo) r[1], (Image) r[2]);
                if (!q.isEmpty() && !row.matches(q)) row.setVisible(false); // пошук уже введено: без миготіння
                modRows.add(row);
                modsList.add(row, "wmin 0, hidemode 3"); // схований пошуком рядок не займає місця
            }
            at[0] = to;
            if (to < rows.size()) {
                modsList.revalidate();
                modsList.repaint();
                SwingUtilities.invokeLater(step[0]);
                return;
            }
            filterMods();
            loadSides();
            loadUpdates(); // 17.11.2026: паралельно з позначками сторін, а не після них (кнопка «Оновити все» на ~1 с раніше)
            modsList.revalidate();
            modsList.repaint();
        };
        step[0].run();
    }

    private SwingWorker<?, ?> sidesLoader;
    private final JButton updateAll = new JButton();
    /** Підпис унизу вікна (про перетягування файлів) і смужка прогресу оновлення модів. */
    private final JLabel footerHint = new JLabel();
    private final JProgressBar footerBar = new JProgressBar();
    private String footerIdleText = "";
    private Timer footerReset;

    /** Показати хід роботи внизу вікна; done &lt; 0 - без смужки. */
    private void footerProgress(String text, int done, int total) {
        if (footerReset != null) footerReset.stop();
        footerHint.setText(text);
        footerHint.setForeground(pal.text);
        footerHint.setToolTipText(text);
        footerBar.setVisible(done >= 0);
        if (done >= 0) {
            footerBar.setMaximum(Math.max(1, total));
            footerBar.setValue(done);
        }
        footerHint.getParent().revalidate();
    }

    /** Підсумок на кілька секунд, потім знову звичайний підпис. */
    private void footerDone(String text) {
        footerProgress(text, -1, 0);
        footerHint.setForeground(pal.success);
        footerReset = new Timer(8000, e -> {
            footerHint.setText(footerIdleText);
            footerHint.setToolTipText(null);
            footerHint.setForeground(pal.text3);
        });
        footerReset.setRepeats(false);
        footerReset.start();
    }
    /** Рядки, для яких на Modrinth є новіша версія (лише моди, поставлені гравцем). */
    private final java.util.Map<ModRow, com.gummer.launcher.manage.ModUpdates.Update> pendingUpdates =
            new java.util.LinkedHashMap<ModRow, com.gummer.launcher.manage.ModUpdates.Update>();
    private SwingWorker<?, ?> updatesLoader;
    /** 25.11.2026: оновлення модів ще шукаються (для «Є оновлення»: «Шукаю оновлення...», а не «Оновлень немає»). */
    private boolean updatesChecking;

    /**
     * 17.11.2026: новіші версії своїх модів гравця (Modrinth за sha1, два запити на весь список), у фоні після позначок.
     * Моди модпака не перевіряються: їх оновлює модпак.
     */
    private void loadUpdates() {
        if (updatesLoader != null) updatesLoader.cancel(true);
        pendingUpdates.clear();
        updatesChecking = false;
        updateAll.setVisible(false);
        final String game = com.gummer.launcher.manage.ModrinthBrowse.gameVersionOf(instance);
        final String[] loaders = com.gummer.launcher.manage.ModrinthBrowse.loadersOf(com.gummer.launcher.manage.ModrinthBrowse.Kind.MOD, instance);
        if (game == null || loaders == null) return;
        final List<ModRow> rows = new ArrayList<ModRow>();
        final List<File> files = new ArrayList<File>();
        // 17.11.2026: моди модпака Modrinth оновлює сам модпак («Оновити до ...» на «Загальне»)
        final java.util.Set<String> packMods = instance.isMrpack() ? com.gummer.launcher.modrinth.MrpackUpdate.packPaths(instance)
                : java.util.Collections.<String>emptySet();
        for (ModRow r : modRows) {
            if (r.mod.source != InstanceMods.Source.OWN) continue;
            if (packMods.contains(("mods/" + r.mod.name).toLowerCase(Locale.ROOT))) continue;
            rows.add(r);
            files.add(r.mod.file);
        }
        if (files.isEmpty()) return;
        // 17.11.2026: фонова перевірка (бейдж у списку) уже знає, що оновлення є: кнопка одразу, поки уточнюється список
        com.gummer.launcher.manage.UpdateBadges.Info known = com.gummer.launcher.manage.UpdateBadges.of(instance);
        if (known != null && known.mods > 0) {
            updateAll.setText(tr("manage.mods.checking"));
            updateAll.setEnabled(false);
            updateAll.setVisible(true);
            updateAll.getParent().revalidate();
        }
        SwingWorker<java.util.Map<File, com.gummer.launcher.manage.ModUpdates.Update>, Void> w =
                new SwingWorker<java.util.Map<File, com.gummer.launcher.manage.ModUpdates.Update>, Void>() {
            @Override
            protected java.util.Map<File, com.gummer.launcher.manage.ModUpdates.Update> doInBackground() {
                return com.gummer.launcher.manage.ModUpdates.check(files, game, loaders);
            }

            @Override
            protected void done() {
                if (isCancelled()) return;
                updatesChecking = false;
                try {
                    java.util.Map<File, com.gummer.launcher.manage.ModUpdates.Update> m = get();
                    boolean changed = false;
                    for (ModRow r : rows) {
                        com.gummer.launcher.manage.ModUpdates.Update u = m.get(r.mod.file);
                        if (u == null || !modRows.contains(r)) continue;
                        pendingUpdates.put(r, u);
                        r.setUpdate(u);
                        changed = true;
                    }
                    showUpdateAll();
                    if (modsUpdatesOnly()) filterMods(); // «Є оновлення»: показати знайдене
                    // 24.11.2026: без оновлень 196 рядків не перекладаються вдруге (~0,1 с завмирання)
                    if (changed) {
                        modsList.revalidate();
                        modsList.repaint();
                    }
                } catch (Exception ignored) {
                    // без оновлень
                    if (modsUpdatesOnly()) filterMods();
                }
            }
        };
        updatesLoader = w;
        updatesChecking = true;
        w.execute();
    }

    private void showUpdateAll() {
        updateAll.setText(tr("manage.mods.updateAll", pendingUpdates.size()));
        updateAll.setEnabled(true);
        updateAll.setVisible(!pendingUpdates.isEmpty());
        updateAll.getParent().revalidate();
    }

    /**
     * 18.11.2026 (власник: «список змін і для модів» «хороша ідея»): перед оновленням показати, що написали автори
     * модів про нові версії (ModUpdates.changelog, у фоні, по 6 модів паралельно), і оновити лише після «Оновити».
     */
    private void updateMods(final List<ModRow> rows) {
        if (rows.isEmpty()) return;
        final String game = com.gummer.launcher.manage.ModrinthBrowse.gameVersionOf(instance);
        final String[] loaders = com.gummer.launcher.manage.ModrinthBrowse.loadersOf(com.gummer.launcher.manage.ModrinthBrowse.Kind.MOD, instance);
        final List<ModRow> have = new ArrayList<ModRow>();
        for (ModRow r : rows) if (pendingUpdates.get(r) != null) have.add(r);
        if (have.isEmpty()) return;
        updateAll.setEnabled(false);
        updateAll.setText(tr("manage.mods.changelogLoading"));
        footerProgress(tr("manage.mods.changelogLoading"), 0, 1);
        new SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() throws Exception {
                List<java.util.concurrent.Future<String>> parts = new ArrayList<java.util.concurrent.Future<String>>();
                for (ModRow r : have) {
                    final com.gummer.launcher.manage.ModUpdates.Update u = pendingUpdates.get(r);
                    final String name = r.info.name;
                    parts.add(com.gummer.launcher.manage.ModrinthBrowse.POOL.submit(() -> {
                        String log = com.gummer.launcher.manage.ModUpdates.changelog(u, game, loaders);
                        if (log.length() > 3000) log = log.substring(0, 3000) + "\n...";
                        return "▸ " + name + "   " + u.currentNumber + " → " + u.versionNumber + "\n\n" + log;
                    }));
                }
                StringBuilder sb = new StringBuilder();
                for (java.util.concurrent.Future<String> f : parts) {
                    String t;
                    try {
                        t = f.get(30, java.util.concurrent.TimeUnit.SECONDS);
                    } catch (Exception e) {
                        t = null;
                    }
                    if (t != null) sb.append(sb.length() > 0 ? "\n\n\n" : "").append(t);
                }
                return sb.toString();
            }

            @Override
            protected void done() {
                String text;
                try {
                    text = get();
                } catch (Exception e) {
                    text = "";
                }
                footerProgress(footerIdleText, -1, 0); // назад до звичайної підказки внизу
                footerHint.setForeground(pal.text3);
                if (!confirmModUpdates(have.size(), text)) {
                    showUpdateAll();
                    return;
                }
                doUpdateMods(have);
            }
        }.execute();
    }

    /** Вікно «Оновити модів: N?» зі списком змін; true - оновлювати. */
    private boolean confirmModUpdates(int count, String changelog) {
        LookTheme.Palette p = LookTheme.palette();
        JPanel panel = new JPanel(new MigLayout("insets 0, wrap 1, gap 0 6, fillx", "[grow,fill]", ""));
        panel.setOpaque(false);
        panel.add(LookUi.title(tr("manage.mods.changelogConfirm", count), 15f), "wmin 0");
        panel.add(LookUi.title(tr("manage.mods.changelogTitle"), 13f), "wmin 0, gaptop 4");
        JTextArea log = new JTextArea(changelog.isEmpty() ? tr("mrpack.update.noChangelog") : changelog);
        log.setEditable(false);
        log.setLineWrap(true);
        log.setWrapStyleWord(true);
        log.setOpaque(false);
        log.setForeground(p.text2);
        log.setFont(log.getFont().deriveFont(12f));
        log.setCaretPosition(0);
        JScrollPane ls = new JScrollPane(log, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        ls.setBorder(BorderFactory.createEmptyBorder());
        ls.setOpaque(false);
        ls.getViewport().setOpaque(false);
        ls.getVerticalScrollBar().setUnitIncrement(20);
        panel.add(ls, "wmin 0, w 480!, hmax 320");
        Object[] options = {tr("manage.mods.changelogGo"), tr("button.cancel")};
        LookUi.freeze(panel);
        return JOptionPane.showOptionDialog(this, panel, tr("manage.mods.changelogWindow"), JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE, null, options, options[0]) == 0;
    }

    /** Оновити вибрані моди (по одному, у фоні), потім перечитати список і сказати, що не так із залежностями. */
    private void doUpdateMods(final List<ModRow> rows) {
        if (rows.isEmpty()) return;
        final String game = com.gummer.launcher.manage.ModrinthBrowse.gameVersionOf(instance);
        final String[] loaders = com.gummer.launcher.manage.ModrinthBrowse.loadersOf(com.gummer.launcher.manage.ModrinthBrowse.Kind.MOD, instance);
        final List<com.gummer.launcher.manage.ModUpdates.Update> todo = new ArrayList<com.gummer.launcher.manage.ModUpdates.Update>();
        for (ModRow r : rows) {
            com.gummer.launcher.manage.ModUpdates.Update u = pendingUpdates.get(r);
            if (u != null) todo.add(u);
            r.updating();
        }
        updateAll.setEnabled(false);
        updateAll.setText(tr("manage.mods.updating"));
        footerProgress(tr("manage.mods.progress", 0, todo.size(), todo.isEmpty() ? "" : todo.get(0).file.getName()), 0, todo.size());
        final List<String> missing = java.util.Collections.synchronizedList(new ArrayList<String>());
        final List<String> conflicts = java.util.Collections.synchronizedList(new ArrayList<String>());
        final List<String> loaderOld = java.util.Collections.synchronizedList(new ArrayList<String>());
        new SwingWorker<int[], Object[]>() {
            @Override
            protected void process(List<Object[]> chunks) {
                Object[] last = chunks.get(chunks.size() - 1);
                footerProgress(tr("manage.mods.progress", last[0], todo.size(), last[1]), (Integer) last[0], todo.size());
            }

            @Override
            protected int[] doInBackground() throws Exception {
                int ok = 0, failed = 0, n = 0;
                for (com.gummer.launcher.manage.ModUpdates.Update u : todo) {
                    publish(new Object[][]{{n++, u.file.getName()}});
                    try {
                        com.gummer.launcher.manage.ModUpdates.apply(u, game, loaders, missing, conflicts);
                        ok++;
                    } catch (IOException e) {
                        failed++;
                        // 25.11.2026 (тікет #122): жодна новіша версія не йде під завантажувач збірки: сказати гравцю чому
                        if (e instanceof com.gummer.launcher.manage.ModUpdates.LoaderTooOld) loaderOld.add(e.getLocalizedMessage());
                        java.util.logging.Logger.getLogger(InstanceManageDialog.class.getName()).log(java.util.logging.Level.WARNING,
                                "Mod update failed: " + u.file.getName(), e);
                    }
                }
                return new int[]{ok, failed};
            }

            @Override
            protected void done() {
                int[] r = {0, todo.size()};
                try {
                    r = get();
                } catch (Exception ignored) {
                    // нижче покаже, що не вдалося
                }
                changed = true;
                fillMods();
                com.gummer.launcher.manage.UpdateBadges.recheck(instance, null); // 17.11.2026: бейдж оновлень у списку
                String result = r[1] == 0 ? tr("manage.mods.updated", r[0]) : tr("manage.mods.updatedPartly", r[0], r[1]);
                toast(result);
                footerDone(tr("manage.mods.progressDone", result));
                java.util.List<String> warn = new ArrayList<String>();
                if (!missing.isEmpty()) warn.add(tr("manage.mods.updateDepsMissing", String.join(", ", missing), game));
                if (!conflicts.isEmpty()) warn.add(tr("manage.mods.updateDepsConflict", String.join(", ", conflicts)));
                if (!loaderOld.isEmpty()) warn.add(String.join("\n", loaderOld));
                if (!warn.isEmpty()) {
                    JOptionPane.showMessageDialog(InstanceManageDialog.this, String.join("\n\n", warn),
                            tr("manage.browse.depsTitle"), JOptionPane.WARNING_MESSAGE);
                }
            }
        }.execute();
    }

    /** 17.11.2026: де працює кожен мод (Modrinth за хешем файлу, решта з опису мода), у фоні після показу списку. */
    private void loadSides() {
        if (sidesLoader != null) sidesLoader.cancel(true);
        final List<ModRow> rows = new ArrayList<ModRow>(modRows);
        final List<File> files = new ArrayList<File>();
        for (ModRow r : rows) files.add(r.mod.file);
        SwingWorker<java.util.Map<File, com.gummer.launcher.manage.ModSides.Side>, Void> w =
                new SwingWorker<java.util.Map<File, com.gummer.launcher.manage.ModSides.Side>, Void>() {
            @Override
            protected java.util.Map<File, com.gummer.launcher.manage.ModSides.Side> doInBackground() {
                return com.gummer.launcher.manage.ModSides.lookup(files);
            }

            @Override
            protected void done() {
                if (isCancelled()) return;
                try {
                    java.util.Map<File, com.gummer.launcher.manage.ModSides.Side> m = get();
                    boolean changed = false;
                    for (ModRow r : rows) changed |= r.setSide(m.get(r.mod.file));
                    // 24.11.2026: позначки зазвичай уже є з диска (ModRow), тоді список не перекладається вдруге
                    if (changed) {
                        modsList.revalidate();
                        modsList.repaint();
                    }
                } catch (Exception ignored) {
                    // без позначок
                }
            }
        };
        sidesLoader = w;
        w.execute();
    }

    /** Позначка сторони мода з поясненням у підказці. */
    private JLabel sideTag(com.gummer.launcher.manage.ModSides.Side s) {
        String k = s.name().toLowerCase(Locale.ROOT).replace("_", "");
        Color c = s == com.gummer.launcher.manage.ModSides.Side.CLIENT || s == com.gummer.launcher.manage.ModSides.Side.CLIENT_PLUS
                ? new Color(0x5B, 0x9B, 0xE0)
                : s == com.gummer.launcher.manage.ModSides.Side.SERVER || s == com.gummer.launcher.manage.ModSides.Side.SERVER_PLUS
                ? new Color(0xE0, 0x95, 0x4E)
                : s == com.gummer.launcher.manage.ModSides.Side.BOTH ? new Color(0xB0, 0x86, 0xE8) : pal.text3;
        JLabel l = tag(tr("manage.side." + k), c);
        l.setToolTipText(tr("manage.side." + k + ".tip"));
        return l;
    }

    /** Іконки модів, вже зменшені до 2x від розміру в списку (чітко і на 200%), за шляхом без .disabled. */
    private static final java.util.Map<String, Image> MOD_ICONS = new java.util.concurrent.ConcurrentHashMap<String, Image>();
    private static final Image NO_ICON = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
    private static final int ICON = 40;

    private static String modIconKey(File jar) {
        String path = jar.getAbsolutePath();
        if (path.endsWith(InstanceMods.DISABLED)) path = path.substring(0, path.length() - InstanceMods.DISABLED.length());
        return path + "|" + jar.length() + "|" + jar.lastModified();
    }

    private static Image modIcon(File jar, com.gummer.launcher.manage.ModInfo info) {
        String key = modIconKey(jar);
        Image cached = MOD_ICONS.get(key);
        if (cached != null) return cached == NO_ICON ? null : cached;
        BufferedImage img = info.readIcon(jar);
        Image out = null;
        if (img != null && img.getWidth() > 0 && img.getHeight() > 0) {
            int s = ICON * 2;
            if (img.getWidth() <= s && img.getHeight() <= s) {
                // піксельна іконка 16 чи 32: збільшуємо без розмиття, як у самій грі
                BufferedImage b = new BufferedImage(s, s, BufferedImage.TYPE_INT_ARGB);
                Graphics2D g = b.createGraphics();
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
                int k = Math.max(1, s / Math.max(img.getWidth(), img.getHeight()));
                int w = img.getWidth() * k, h = img.getHeight() * k;
                g.drawImage(img, (s - w) / 2, (s - h) / 2, w, h, null);
                g.dispose();
                out = b;
            } else {
                out = com.gummer.launcher.look.DevicePixels.resize(img, s, s, true);
            }
        }
        MOD_ICONS.put(key, out != null ? out : NO_ICON);
        return out;
    }

    /** id, які дають увімкнені моди, крім переданих. */
    private java.util.Set<String> enabledProvides(java.util.Collection<ModRow> except) {
        java.util.Set<String> out = new java.util.HashSet<String>();
        for (ModRow r : modRows) if (r.mod.enabled && !except.contains(r)) out.addAll(r.info.provides);
        return out;
    }

    /** Увімкнені моди, які перестануть запускатися без row (і без них самих, ланцюжком). */
    private java.util.List<ModRow> dependentsOf(ModRow row) {
        java.util.LinkedHashSet<ModRow> off = new java.util.LinkedHashSet<ModRow>();
        off.add(row);
        boolean grew = true;
        while (grew) {
            grew = false;
            java.util.Set<String> left = enabledProvides(off);
            for (ModRow r : modRows) {
                if (!r.mod.enabled || off.contains(r) || r.mod.locked) continue;
                for (String d : r.info.depends) {
                    if (!left.contains(d) && providedByAny(off, d)) {
                        off.add(r);
                        grew = true;
                        break;
                    }
                }
            }
        }
        off.remove(row);
        return new ArrayList<ModRow>(off);
    }

    private static boolean providedByAny(java.util.Collection<ModRow> rows, String id) {
        for (ModRow r : rows) if (r.info.provides.contains(id)) return true;
        return false;
    }

    /** Вимкнені моди, без яких row не запуститься (і їхні вимкнені залежності, ланцюжком). */
    private java.util.List<ModRow> missingFor(ModRow row) {
        java.util.LinkedHashSet<ModRow> on = new java.util.LinkedHashSet<ModRow>();
        java.util.ArrayDeque<ModRow> todo = new java.util.ArrayDeque<ModRow>();
        todo.add(row);
        java.util.Set<String> have = enabledProvides(java.util.Collections.<ModRow>emptySet());
        while (!todo.isEmpty()) {
            ModRow r = todo.poll();
            for (String d : r.info.depends) {
                if (have.contains(d)) continue;
                ModRow c = providerOf(d, row, on);
                if (c != null) {
                    on.add(c);
                    have.addAll(c.info.provides);
                    todo.add(c);
                }
            }
        }
        return new ArrayList<ModRow>(on);
    }

    /**
     * Вимкнений мод, що дає id: спершу той, чий це власний id (Fabric API для fabric-api), лише потім той, хто
     * несе його всередині (Distant Horizons вкладає цілий Fabric API). null: такого серед вимкнених немає.
     */
    private ModRow providerOf(String id, ModRow skip, java.util.Collection<ModRow> taken) {
        ModRow bundled = null;
        for (ModRow c : modRows) {
            if (c.mod.enabled || c == skip || taken.contains(c) || !c.info.provides.contains(id)) continue;
            if (id.equals(c.info.id)) return c;
            if (bundled == null) bundled = c;
        }
        return bundled;
    }

    /** Попередження під увімкненими модами, яким бракує обов'язкового мода (вимкненого чи взагалі відсутнього). */
    private void updateDependencyWarnings() {
        java.util.Set<String> have = enabledProvides(java.util.Collections.<ModRow>emptySet());
        String loader = com.gummer.launcher.manage.PreLaunchCheck.loader(instance);
        boolean connector = false;
        for (ModRow r : modRows) if (r.mod.enabled && com.gummer.launcher.manage.PreLaunchCheck.isConnector(r.mod.file, r.info)) connector = true;
        for (ModRow r : modRows) {
            // без повторів: LambDynamicLights просить п'ять модулів, і всі вони з одного Fabric API
            java.util.Set<String> labels = new java.util.LinkedHashSet<String>();
            if (r.mod.enabled && com.gummer.launcher.manage.PreLaunchCheck.checkDepends(loader, r.info, connector)) {
                for (String d : r.info.depends) {
                    if (have.contains(d)) continue;
                    ModRow c = providerOf(d, r, java.util.Collections.<ModRow>emptySet());
                    labels.add(c != null ? c.info.name + " (" + tr("manage.mods.depOff") + ")" : d);
                }
            }
            StringBuilder missing = new StringBuilder();
            for (String l : labels) missing.append(missing.length() > 0 ? ", " : "").append(l);
            r.warn.setText(missing.length() > 0 ? tr("manage.mods.depMissing", missing.toString()) : "");
            r.warn.setVisible(missing.length() > 0);
        }
    }

    private void filterMods() {
        updateDependencyWarnings();
        String q = modsSearch.getText().trim().toLowerCase(Locale.ROOT);
        boolean updatesOnly = modsUpdatesOnly();
        int on = 0, shown = 0;
        for (ModRow r : modRows) {
            if (r.mod.enabled) on++;
            boolean v = (q.isEmpty() || r.matches(q)) && (!updatesOnly || pendingUpdates.get(r) != null);
            r.setVisible(v);
            if (v) shown++;
        }
        modsCount.setText(tr("manage.mods.count", on, modRows.size()));
        if (updatesOnly) {
            modsCount.setText(shown > 0 ? tr("manage.mods.updatesShown", shown)
                    : updatesChecking ? tr("manage.mods.checking") : !q.isEmpty() ? tr("manage.mods.notFound") : tr("manage.mods.noUpdates"));
        } else if (!q.isEmpty() && shown == 0) {
            modsCount.setText(tr("manage.mods.notFound"));
        }
        modsList.revalidate();
        modsList.repaint();
    }

    /** Рядок мода: іконка, назва і версія, опис, сайт, галочка вкл/викл. */
    private final class ModRow extends JPanel {
        InstanceMods.Mod mod;
        final com.gummer.launcher.manage.ModInfo info;
        final JCheckBox box = check("", false);
        final JLabel name = new JLabel(), version = new JLabel();
        /** «Не запуститься без: Fabric API (вимкнено)»: увімкненому моду бракує обов'язкового. */
        final JLabel warn = new JLabel();
        private JPanel sideLine;
        private JLabel side;

        private JLabel update;

        /** 17.11.2026: позначка «оновлення»: клік ставить нову версію саме цього мода. */
        void setUpdate(com.gummer.launcher.manage.ModUpdates.Update u) {
            if (update != null) return;
            update = tag(tr("manage.mods.updateTag"), pal.accent);
            update.setToolTipText(tr("manage.mods.updateTip", u.versionNumber));
            update.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            update.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    if (update.isEnabled()) updateMods(java.util.Collections.singletonList(ModRow.this));
                }
            });
            sideLine.add(update);
            sideLine.revalidate();
        }

        void updating() {
            if (update == null) return;
            update.setText(tr("manage.mods.updatingTag"));
            update.setEnabled(false);
        }

        /** 17.11.2026: позначка «клієнт», «сервер», «клієнт + сервер» чи «клієнт або сервер». */
        /** true, якщо позначку додано (рядок змінився). */
        boolean setSide(com.gummer.launcher.manage.ModSides.Side s) {
            if (s == null || side != null) return false;
            side = sideTag(s);
            sideLine.add(side);
            sideLine.revalidate();
            return true;
        }

        ModRow(InstanceMods.Mod mod, com.gummer.launcher.manage.ModInfo info, Image icon) {
            super(new MigLayout("insets 8 10 8 8, gap 12 2, fillx", "[" + ICON + "!][grow,fill][]", "[]"));
            this.mod = mod;
            this.info = info;
            setOpaque(false);

            add(new ModIcon(icon, info.name), "spany 3, aligny top, w " + ICON + "!, h " + ICON + "!");

            JPanel line = new JPanel(new MigLayout("insets 0, gap 8", "[]", "[]"));
            line.setOpaque(false);
            name.setText(info.name);
            name.setFont(name.getFont().deriveFont(Font.BOLD, 13.5f));
            line.add(name, "wmin 0");
            if (info.version != null) {
                version.setText(info.version);
                version.setFont(version.getFont().deriveFont(12f));
                line.add(version, "wmin 0");
            }
            if (mod.source == InstanceMods.Source.PACK) line.add(tag(tr("manage.mods.fromPack")));
            sideLine = line;
            setSide(com.gummer.launcher.manage.ModSides.cached(mod.file));
            add(line, "wmin 0");

            box.setSelected(mod.enabled);
            box.setToolTipText(mod.locked ? tr("manage.mods.packLocked") : tr(mod.enabled ? "manage.mods.turnOff" : "manage.mods.turnOn"));
            box.setEnabled(!mod.locked);
            add(box, "spany 3, aligny center, wrap");

            if (info.description != null) {
                // JTextArea переносить рядки за шириною картки (HTML у JLabel розтягнув би рядок на весь опис)
                JTextArea d = LookUi.body(cut(info.description, 220), true);
                d.setFont(d.getFont().deriveFont(12f));
                d.setForeground(pal.text2);
                add(d, "skip 1, wmin 0, wrap");
            }
            if (com.gummer.launcher.manage.ModrinthBrowse.isXray(info.name, info.description, mod.name)) {
                add(xrayWarning(), "skip 1, wmin 0, wrap"); // 24.11.2026: червона позначка X-ray і в «Моїх модах»
            }

            JPanel meta = new JPanel(new MigLayout("insets 0, gap 10", "[]", "[]"));
            meta.setOpaque(false);
            // 25.11.2026: дата появи мода в теці (для «Спершу нові»)
            String added = mod.added > 0 ? " · " + tr("manage.mods.added", java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM).format(new java.util.Date(mod.added))) : "";
            JLabel file = small(mod.name + " · " + InstanceManageDialog.size(mod.size) + added);
            meta.add(file, "wmin 0");
            warn.setFont(warn.getFont().deriveFont(Font.BOLD, 12f));
            warn.setForeground(new Color(0xE5, 0x6B, 0x5D));
            warn.setVisible(false);
            if (info.url != null) {
                JLabel site = new JLabel(tr("manage.mods.site"));
                site.setFont(site.getFont().deriveFont(12f));
                site.setForeground(pal.accent);
                site.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                site.setToolTipText(info.url);
                site.addMouseListener(new MouseAdapter() {
                    @Override
                    public void mouseClicked(MouseEvent e) {
                        SwingHelper.openURL(info.url, InstanceManageDialog.this);
                    }
                });
                meta.add(site);
            }
            // 25.11.2026 (власник: «щоб моди в списку модів можна було не лише вимикати, а й видаляти»): лише свої моди
            // гравця; мод модпаку повернеться з першим оновленням чи перевіркою збірки, його вимикають
            if (mod.source == InstanceMods.Source.OWN && !mod.locked) {
                JLabel del = new JLabel(tr("manage.mods.delete"));
                del.setFont(del.getFont().deriveFont(12f));
                del.setForeground(new Color(0xE5, 0x6B, 0x5D));
                del.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                del.setToolTipText(tr("manage.mods.deleteTip"));
                del.addMouseListener(new MouseAdapter() {
                    @Override
                    public void mouseClicked(MouseEvent e) {
                        deleteMod();
                    }
                });
                meta.add(del);
            }
            add(meta, "skip 1, wmin 0, wrap");
            add(warn, "skip 1, wmin 0, hidemode 3");

            box.addActionListener(e -> toggle());
            paintState();
        }

        /**
         * 25.11.2026: видалити свій мод. Файл іде в кошик Windows (Java 9+), а де кошика немає, у
         * {@code .gummer/deleted-mods} збірки: випадкове видалення можна повернути.
         */
        private void deleteMod() {
            java.util.List<ModRow> deps = dependentsOf(this);
            StringBuilder names = new StringBuilder();
            for (ModRow r : deps) names.append(names.length() > 0 ? ", " : "").append(r.info.name);
            String text = tr("manage.mods.deleteConfirm", info.name) + (deps.isEmpty() ? "" : "\n\n" + tr("manage.mods.deleteDeps", names.toString()));
            if (JOptionPane.showConfirmDialog(InstanceManageDialog.this, text, tr("manage.mods.deleteTitle"),
                    JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE) != JOptionPane.OK_OPTION) return;
            try {
                boolean trash = false;
                try {
                    if (Desktop.isDesktopSupported()) {
                        Object ok = Desktop.class.getMethod("moveToTrash", java.io.File.class).invoke(Desktop.getDesktop(), mod.file);
                        trash = Boolean.TRUE.equals(ok);
                    }
                } catch (ReflectiveOperationException | RuntimeException ignored) {
                    // Java 8 або система без кошика: нижче
                }
                if (!trash) {
                    java.io.File dir = new java.io.File(new java.io.File(instance.getContentDir(), ".gummer"), "deleted-mods");
                    dir.mkdirs();
                    java.nio.file.Files.move(mod.file.toPath(), new java.io.File(dir, System.currentTimeMillis() + "-" + mod.file.getName()).toPath());
                }
                changed = true;
                toast(tr(trash ? "manage.mods.deletedTrash" : "manage.mods.deletedFolder", info.name));
                modsShown = null;
                fillMods();
            } catch (IOException ex) {
                SwingHelper.showErrorDialog(InstanceManageDialog.this, tr("manage.mods.deleteFailed", mod.name), tr("errorTitle"), ex);
            }
        }

        private void toggle() {
            boolean want = box.isSelected();
            box.setSelected(mod.enabled); // стан галочки зміниться лише після вибору у питанні і перейменування
            java.util.List<ModRow> group = new ArrayList<ModRow>();
            group.add(this);
            java.util.List<ModRow> also = want ? missingFor(this) : dependentsOf(this);
            if (!also.isEmpty()) {
                StringBuilder names = new StringBuilder();
                for (ModRow r : also) names.append(names.length() > 0 ? ", " : "").append(r.info.name);
                String[] options = {tr(want ? "manage.mods.depEnableAll" : "manage.mods.depDisableAll"),
                        tr(want ? "manage.mods.depEnableOnly" : "manage.mods.depDisableOnly", info.name), tr("button.cancel")};
                int choice = JOptionPane.showOptionDialog(InstanceManageDialog.this,
                        tr(want ? "manage.mods.depEnableText" : "manage.mods.depDisableText", info.name, names.toString()),
                        tr("manage.mods.depTitle"), JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE, null, options, options[0]);
                if (choice == 2 || choice == JOptionPane.CLOSED_OPTION) return;
                if (choice == 0) group.addAll(also);
            }
            for (ModRow r : group) {
                if (r.mod.enabled == want || r.mod.locked) continue;
                try {
                    InstanceMods.setEnabled(instance, r.mod, want);
                    changed = true;
                } catch (IOException ex) {
                    SwingHelper.showErrorDialog(InstanceManageDialog.this, tr("manage.mods.renameFailed", r.mod.name), tr("errorTitle"), ex);
                }
            }
            // новий стан з диска (файли перейменовано): лише ці рядки, без перебудови списку
            java.util.Map<String, InstanceMods.Mod> now = new java.util.HashMap<String, InstanceMods.Mod>();
            for (InstanceMods.Mod m : InstanceMods.list(instance)) now.put(m.name, m);
            for (ModRow r : group) {
                InstanceMods.Mod m = now.get(r.mod.name);
                if (m != null) r.mod = m;
                r.box.setSelected(r.mod.enabled);
                r.box.setToolTipText(tr(r.mod.enabled ? "manage.mods.turnOff" : "manage.mods.turnOn"));
                r.paintState();
            }
            filterMods();
        }

        /** Вимкнений мод блідіший, щоб список читався з першого погляду. */
        private void paintState() {
            name.setForeground(mod.enabled ? pal.text : pal.text3);
            version.setForeground(pal.text3);
            repaint();
        }

        boolean matches(String q) {
            return has(info.name, q) || has(info.id, q) || has(mod.name, q) || has(info.description, q);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            int arc = pal.library() ? 14 : pal.glass() ? 12 : 6;
            // 17.11.2026: готова картинка (RoundBg): рядків багато, і при прокрутці кожен малювався маскою по шматках
            com.gummer.launcher.look.RoundBg.paint(g2, getWidth(), getHeight(), arc,
                    mod.enabled ? LookTheme.alpha(pal.surface2, pal.glass() ? 150 : 255) : LookTheme.alpha(pal.surface, pal.glass() ? 90 : 160),
                    LookTheme.alpha(pal.line, 120), 0);
            g2.dispose();
            super.paintComponent(g);
        }
    }

    private JLabel tag(String text) {
        return tag(text, pal.accent);
    }

    private JLabel tag(String text, final Color color) {
        JLabel l = new JLabel(text) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(LookTheme.alpha(color, 46));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), getHeight(), getHeight());
                g2.dispose();
                super.paintComponent(g);
            }
        };
        l.setFont(l.getFont().deriveFont(Font.BOLD, 10.5f));
        l.setForeground(color);
        l.setBorder(BorderFactory.createEmptyBorder(1, 7, 1, 7));
        return l;
    }

    /** Іконка мода або перша літера назви на плашці, якщо в jar іконки немає. */
    private final class ModIcon extends JComponent {
        private Image img;
        private final String letter;

        ModIcon(Image img, String name) {
            this.img = img;
            this.letter = name == null || name.isEmpty() ? "?" : name.substring(0, 1).toUpperCase(Locale.ROOT);
        }

        /** Іконка довантажилась: лише перемалювати (розмір фіксований, перерахунок розкладки сторінки не потрібен). */
        void setImage(Image img) {
            this.img = img;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth(), h = getHeight();
            if (img != null) {
                // 17.11.2026: не BICUBIC на кожному кадрі (Direct3D його не вміє, і кожна іконка при прокрутці
                // йшла програмним шляхом зі зчитуванням кадру з відеокарти: просідав FPS). Як у головному вікні:
                // зменшена копія у фізичних пікселях один раз, далі просте копіювання.
                double ds = com.gummer.launcher.look.DevicePixels.scale(g2);
                int pw = com.gummer.launcher.look.DevicePixels.px(w, ds), ph = com.gummer.launcher.look.DevicePixels.px(h, ds);
                com.gummer.launcher.look.DevicePixels.draw(g2, com.gummer.launcher.look.DevicePixels.cached(img, pw, ph, true), 0, 0, w, h);
            } else {
                g2.setColor(pal.surface3);
                g2.fillRoundRect(0, 0, w, h, 10, 10);
                com.gummer.launcher.look.TextAA.apply(g2);
                g2.setFont(getFont().deriveFont(Font.BOLD, 18f));
                FontMetrics fm = g2.getFontMetrics();
                g2.setColor(pal.text2);
                g2.drawString(letter, (w - fm.stringWidth(letter)) / 2, (h - fm.getHeight()) / 2 + fm.getAscent());
            }
            g2.dispose();
        }
    }

    private static boolean has(String s, String q) {
        return s != null && s.toLowerCase(Locale.ROOT).contains(q);
    }

    private static String cut(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max).trim() + "…";
    }


    // ------------------------------------------------------------------ Ресурспаки і шейдери

    private JPanel filesPage(int page, File dir, JPanel list, String title, String hint, String ext) {
        JPanel p = sub();
        JPanel c = card();
        JPanel head = new JPanel(new MigLayout("insets 0, gap 8, fillx", "[grow][][]", "[]"));
        head.setOpaque(false);
        head.add(LookUi.title(title, 15f));
        // 17.11.2026: у StalDayZ сторонні ресурспаки заборонені: ні «Додати», ні перетягування, ні пошуку
        boolean locked = page == RESOURCEPACKS && packsLocked();
        if (!locked) head.add(addButton(page, dir, ext));
        head.add(folderButton(tr("manage.openFolder"), dir));
        c.add(head, "wmin 0");
        c.add(LookUi.body(locked ? tr("manage.packs.hintLocked") : hint, true), "wmin 0");
        c.add(list, "wmin 0, gaptop 6");
        // 17.11.2026: текст порожнього списку з переносом: створений разом зі сторінкою (доданий пізніше JTextArea
        // брав висоту за нульовою шириною і лишав у картці порожнє місце), fillFiles лише показує і ховає
        JTextArea emptyText = LookUi.body("", true);
        emptyText.setFont(emptyText.getFont().deriveFont(12f));
        emptyText.setVisible(false);
        emptyTexts.put(list, emptyText);
        c.add(emptyText, "wmin 0, hidemode 3");
        // 26.11.2026 (тікет #124, власник: «кнопка «Встановити мод для шейдерів»»): без шейдерного мода шейдери не
        // ввімкнуться; помічник сам пропонує, що є саме для цієї збірки
        if (page == SHADERS) p.add(shaderHelperCard(), "wmin 0, hidemode 3");
        p.add(c);
        if (locked) {
            packsBrowse = null;
            return tabbed(page, p, null, null, null);
        }
        // 17.11.2026: пошук і встановлення з Modrinth на окремій підвкладці «Знайти»
        Browse b = new Browse(page == SHADERS ? com.gummer.launcher.manage.ModrinthBrowse.Kind.SHADER
                : com.gummer.launcher.manage.ModrinthBrowse.Kind.RESOURCEPACK, dir, () -> fillFiles(list, dir, ext));
        if (b.loaders == null && page == SHADERS) b = null; // Vanilla: шейдерів немає, лише «Мої»
        if (page == SHADERS) shadersBrowse = b;
        else packsBrowse = b;
        return page == SHADERS ? tabbed(page, p, b, "manage.tab.mineShaders", "manage.tab.findShaders")
                : tabbed(page, p, b, "manage.tab.minePacks", "manage.tab.findPacks");
    }

    /**
     * 26.11.2026: картка «Шейдерам потрібен мод» над «Мої шейдери». Ховається, якщо в збірці вже є Iris, Oculus чи
     * OptiFine. Інакше у фоні шукає шейдерний мод саме для цієї гри і завантажувача (Iris і Sodium, Oculus і Embeddium чи
     * Rubidium; Modrinth, інакше CurseForge, {@link com.gummer.launcher.manage.ModSets}) і дає кнопку «Встановити мод
     * для шейдерів». Немає такого (Forge 1.20.2+, Forge 26.3): чесне пояснення і, де є збірка Fabric цієї версії, кнопка
     * перенести туди світи. Ванільна збірка: одразу ця кнопка.
     */
    private JPanel shaderHelperCard() {
        final JPanel c = card();
        c.setVisible(false);
        c.add(LookUi.title(tr("shaders.helper.title"), 15f));
        final JTextArea text = LookUi.body("", true);
        c.add(text, "wmin 0");
        final JPanel row = new JPanel(new MigLayout("insets 0, gap 8, hidemode 3", "[][]push", "[32!]"));
        row.setOpaque(false);
        final JButton install = LookUi.primary(new JButton(tr("shaders.helper.install")));
        install.setFocusable(false);
        final String mc = com.gummer.launcher.manage.ModrinthBrowse.gameVersionOf(instance);
        final Instance fabric = FriendPlayDialog.fabricFor(launcher, mc);
        final JButton toFabric = LookUi.secondary(new JButton(tr("shaders.helper.toFabric", mc == null ? "" : mc)));
        toFabric.setFocusable(false);
        toFabric.setToolTipText(tr("shaders.helper.toFabricTip"));
        install.setVisible(false);
        toFabric.setVisible(false);
        row.add(install, "h 32!");
        row.add(toFabric, "h 32!");
        c.add(row, "gaptop 6, wmin 0");
        toFabric.addActionListener(e -> moveToFabric(fabric, toFabric));
        if (instance.isVanilla()) {
            text.setText(tr("shaders.helper.vanilla", mc == null ? "" : mc));
            toFabric.setVisible(fabric != null);
            c.setVisible(true);
            return c;
        }
        if (!com.gummer.launcher.manage.ModSets.available(instance)) return c; // GummerCraft, StalDayZ: свій модпак
        final com.gummer.launcher.manage.ModSets.Plan[] plan = new com.gummer.launcher.manage.ModSets.Plan[1];
        new SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() throws Exception {
                String have = com.gummer.launcher.manage.ModSets.shaderModIn(instance);
                if (have != null) return have;
                plan[0] = com.gummer.launcher.manage.ModSets.plan(instance, com.gummer.launcher.manage.ModSets.SHADERS);
                return null;
            }

            @Override
            protected void done() {
                try {
                    if (get() != null || plan[0] == null) return;
                } catch (Exception ex) {
                    return;
                }
                com.gummer.launcher.manage.ModSets.Group g = plan[0].group("shaderMod");
                if (g == null || g.state == com.gummer.launcher.manage.ModSets.State.HAVE) return;
                String[] loaders = com.gummer.launcher.manage.ModrinthBrowse.loadersOf(com.gummer.launcher.manage.ModrinthBrowse.Kind.MOD, instance);
                if (g.state == com.gummer.launcher.manage.ModSets.State.READY) {
                    text.setText(tr("shaders.helper.text", g.titles()));
                    install.setVisible(true);
                } else {
                    text.setText(ModSetsPage.shaderNone(ModSetsPage.loaderTitle(loaders), mc));
                    toFabric.setVisible(fabric != null && fabric != instance && !instance.isFabric());
                }
                c.setVisible(true);
                c.revalidate();
            }
        }.execute();
        install.addActionListener(e -> {
            final com.gummer.launcher.manage.ModSets.Plan pl = plan[0];
            if (pl == null) return;
            if (!SwingHelper.confirmDialog(this, tr("shaders.helper.confirm", pl.group("shaderMod").titles()), tr("shaders.helper.title"))) return;
            install.setEnabled(false);
            install.setText(tr("crash.action.working"));
            new SwingWorker<com.gummer.launcher.manage.ModSets.Result, Void>() {
                @Override
                protected com.gummer.launcher.manage.ModSets.Result doInBackground() throws Exception {
                    return com.gummer.launcher.manage.ModSets.install(instance, pl, java.util.Collections.singleton("shaderMod"), false);
                }

                @Override
                protected void done() {
                    install.setText(tr("shaders.helper.install"));
                    install.setEnabled(true);
                    com.gummer.launcher.manage.ModSets.Result r;
                    try {
                        r = get();
                    } catch (Exception ex) {
                        Throwable t = ex.getCause() != null ? ex.getCause() : ex;
                        SwingHelper.showErrorDialog(InstanceManageDialog.this, tr("fps.failed", String.valueOf(t.getMessage())), tr("shaders.helper.title"));
                        return;
                    }
                    changed = true;
                    modsShown = null;
                    String msg = r.installed.isEmpty() ? tr("fps.addedNone") : tr("shaders.helper.done", String.join(", ", r.installed));
                    if (!r.skipped.isEmpty()) msg += "\n\n" + tr("fps.skipped") + "\n" + String.join("\n", r.skipped);
                    SwingHelper.showMessageDialog(InstanceManageDialog.this, msg, tr("shaders.helper.title"), null, JOptionPane.INFORMATION_MESSAGE);
                    if (!r.installed.isEmpty()) c.setVisible(false);
                }
            }.execute();
        });
        return c;
    }

    /** 26.11.2026: світи і налаштування гри в збірку Fabric тієї ж версії (як у «Грі з другом»), далі її «Шейдери». */
    private void moveToFabric(final Instance fabric, final JButton button) {
        if (fabric == null) return;
        button.setEnabled(false);
        new SwingWorker<Integer, Void>() {
            @Override
            protected Integer doInBackground() throws Exception {
                return FriendPlayDialog.copyWorlds(instance, fabric);
            }

            @Override
            protected void done() {
                int n;
                try {
                    n = get();
                } catch (Exception ex) {
                    java.util.logging.Logger.getLogger(InstanceManageDialog.class.getName()).log(java.util.logging.Level.WARNING, "Copy to Fabric failed", ex);
                    button.setEnabled(true);
                    return;
                }
                java.util.logging.Logger.getLogger(InstanceManageDialog.class.getName()).info("Shaders: " + instance.getName() + " -> " + fabric.getName() + ", worlds " + n);
                Window owner = getOwner();
                host.copyCreated(fabric.getName());
                dispose();
                open(owner, launcher, fabric, SHADERS, host);
            }
        }.execute();
    }

    private Browse packsBrowse, shadersBrowse, modsBrowse;
    private final java.util.Map<JPanel, JTextArea> emptyTexts = new java.util.HashMap<JPanel, JTextArea>();

    /** 17.11.2026: StalDayZ: сторонні ресурспаки через лаунчер не ставляться (так вирішено власником). */
    private boolean packsLocked() {
        return InstanceMods.packModsLocked(instance);
    }

    /** Сторінка без відступів: її кладе в себе {@link #tabbed}. */
    private static JPanel sub() {
        JPanel p = new JPanel(new MigLayout("insets 0, wrap 1, gap 0 12, fillx", "[grow,fill]", ""));
        p.setOpaque(false);
        return p;
    }

    // 17.11.2026: підвкладки «Мої ... / Знайти ...» на сторінках модів, ресурспаків і шейдерів, щоб своє і пошук
    // Modrinth не були змішані на одній сторінці
    private final SubTabs[] subTabs = new SubTabs[LAST + 1];

    /** Сторінка з підвкладками: зверху перемикач, під ним або своє (mine), або пошук (find). find null: лише своє. */
    private JPanel tabbed(int page, JPanel mine, Browse find, String mineKey, String findKey) {
        return tabbed(page, mine, find, mineKey, findKey, null, null);
    }

    /** 17.11.2026: з третьою вкладкою («Модпаки» на сторінці модів), якщо {@code extra} не null. */
    private JPanel tabbed(int page, JPanel mine, Browse find, String mineKey, String findKey, Browse extra, String extraKey) {
        return tabbed(page, mine, find, mineKey, findKey, extra, extraKey, null, null);
    }

    /** 26.11.2026: ще «Набори модів» ({@code sets}) другою вкладкою, між своїм і пошуком, якщо не null. */
    private JPanel tabbed(int page, JPanel mine, Browse find, String mineKey, String findKey, Browse extra, String extraKey,
                          JComponent sets, String setsKey) {
        JPanel p = page();
        if (find == null) {
            p.add(mine, "wmin 0");
            return p;
        }
        List<String> labels = new ArrayList<String>();
        List<JComponent> panes = new ArrayList<JComponent>();
        labels.add(tr(mineKey));
        panes.add(mine);
        if (sets != null) {
            labels.add(tr(setsKey));
            panes.add(sets);
        }
        labels.add(tr(findKey));
        panes.add(find);
        if (extra != null) {
            labels.add(tr(extraKey));
            panes.add(extra);
        }
        SubTabs t = new SubTabs(labels.toArray(new String[0]), panes.toArray(new JComponent[0]));
        subTabs[page] = t;
        JPanel row = new JPanel(new MigLayout("insets 0, fillx", "[grow,fill]", "[36!]"));
        row.setOpaque(false);
        row.add(t, "wmin 0, wmax " + (160 * panes.size() + 140) + ", h 36!");
        p.add(row, "wmin 0");
        for (JComponent c : panes) {
            p.add(c, "wmin 0, hidemode 3");
            c.setVisible(c == mine);
        }
        return p;
    }

    /** Перемикач підвкладок «таблетками» в стилі вигляду (дві чи три: 17.11.2026 «Модпаки»). */
    private final class SubTabs extends JComponent {
        final String[] labels;
        final JComponent[] panes;
        /** Пошук, що вантажиться заздалегідь (друга вкладка); третя вантажиться лише при відкритті. */
        final Browse find;
        /** Місце пошуку серед вкладок. */
        final int findIndex;
        int selected, hover = -1;

        int at(int x) {
            int n = labels.length;
            return Math.max(0, Math.min(n - 1, (x - 4) * n / Math.max(1, getWidth() - 8)));
        }

        SubTabs(String[] labels, JComponent[] panes) {
            this.labels = labels;
            this.panes = panes;
            // 26.11.2026: пошук не обов'язково другий («Набори модів» між «Мої» і «Знайти»)
            Browse f = null;
            int fi = 1;
            for (int k = 0; k < panes.length && f == null; k++) {
                if (panes[k] instanceof Browse) {
                    f = (Browse) panes[k];
                    fi = k;
                }
            }
            this.find = f;
            this.findIndex = fi;
            setFont(UIManager.getFont("Label.font").deriveFont(Font.PLAIN, 13.5f));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new java.awt.event.MouseAdapter() {
                @Override
                public void mousePressed(java.awt.event.MouseEvent e) {
                    select(at(e.getX()));
                }

                @Override
                public void mouseExited(java.awt.event.MouseEvent e) {
                    hover = -1;
                    repaint();
                }
            });
            addMouseMotionListener(new java.awt.event.MouseMotionAdapter() {
                @Override
                public void mouseMoved(java.awt.event.MouseEvent e) {
                    int h = at(e.getX());
                    if (h != hover) {
                        hover = h;
                        repaint();
                    }
                }
            });
        }

        void select(int i) {
            selected = i;
            for (int k = 0; k < panes.length; k++) panes[k].setVisible(k == i);
            if (i != findIndex && panes[i] instanceof Browse) ((Browse) panes[i]).loadIfNeeded();
            if (panes[i] instanceof ModSetsPage) ((ModSetsPage) panes[i]).loadIfNeeded();
            Container sp = SwingUtilities.getAncestorOfClass(JScrollPane.class, this);
            if (sp != null) {
                sp.revalidate();
                SwingUtilities.invokeLater(() -> ((JScrollPane) sp).getVerticalScrollBar().setValue(0));
            }
            repaint();
            shown();
        }

        /**
         * Сторінку відкрито: популярне вантажиться одразу, навіть якщо вибрано «Мої» (17.11.2026: щоб на «Знайти» вже
         * було готове). На «Моди» трохи пізніше, щоб не заважати читанню списку модів.
         */
        void shown() {
            if (selected == findIndex || find.loaded) {
                find.loadIfNeeded();
                return;
            }
            javax.swing.Timer t = new javax.swing.Timer(find.kind == com.gummer.launcher.manage.ModrinthBrowse.Kind.MOD ? 700 : 150,
                    e -> {
                        if (isDisplayable()) find.loadIfNeeded();
                    });
            t.setRepeats(false);
            t.start();
        }

        @Override
        public Dimension getPreferredSize() {
            FontMetrics fm = getFontMetrics(getFont().deriveFont(Font.BOLD));
            int w = 0;
            for (String l : labels) w = Math.max(w, com.gummer.launcher.look.TextFit.widest(fm.getFont(), l) + 40);
            return new Dimension(w * labels.length + 8, 36);
        }

        @Override
        public Dimension getMinimumSize() {
            return new Dimension(0, 36);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            com.gummer.launcher.look.TextAA.apply(g2);
            int w = getWidth(), h = getHeight();
            int arc = pal.library() ? h : pal.glass() ? 14 : 8;
            // доріжка
            // 20.11.2026: по фізичних пікселях (look.Edge)
            com.gummer.launcher.look.Edge.pill(g2, 0, 0, w, h, arc, pal.glass() ? new Color(255, 255, 255, 12) : pal.surface,
                    pal.library() ? null : pal.glass() ? new Color(255, 255, 255, 30) : pal.line);
            int n = labels.length, cell = (w - 8) / n;
            for (int i = 0; i < n; i++) {
                int x = 4 + i * cell, y = 4, sw = i == n - 1 ? w - 8 - cell * (n - 1) : cell, sh = h - 8;
                boolean on = selected == i;
                int a = Math.max(2, arc - 6);
                if (on) {
                    if (pal.glass()) {
                        g2.setColor(new Color(255, 255, 255, 38));
                        g2.fillRoundRect(x, y, sw, sh, a, a);
                        g2.setColor(new Color(255, 255, 255, 52));
                        g2.drawRoundRect(x, y, sw - 1, sh - 1, a, a);
                    } else {
                        g2.setColor(pal.library() ? pal.surface3 : pal.surface2);
                        g2.fillRoundRect(x, y, sw, sh, a, a);
                        if (!pal.library()) {
                            g2.setColor(pal.accentHover);
                            g2.fillRoundRect(x + 10, y + sh - 3, sw - 20, 3, 3, 3);
                        }
                    }
                } else if (hover == i) {
                    g2.setColor(pal.glass() ? new Color(255, 255, 255, 14) : pal.surface2);
                    g2.fillRoundRect(x, y, sw, sh, a, a);
                }
                Font f = on ? getFont().deriveFont(Font.BOLD) : getFont();
                g2.setFont(f);
                FontMetrics fm = g2.getFontMetrics();
                String s = labels[i];
                while (fm.stringWidth(s) > sw - 16 && s.length() > 4) s = s.substring(0, s.length() - 2) + "…";
                g2.setColor(on || hover == i ? pal.text : pal.text2);
                g2.drawString(s, x + (sw - fm.stringWidth(s)) / 2, y + (sh - fm.getHeight()) / 2 + fm.getAscent());
            }
            g2.dispose();
        }
    }

    /**
     * 17.11.2026: картка «Знайти на Modrinth» на сторінках ресурспаків і шейдерів. Популярне під версію гри цієї збірки
     * вантажиться при першому відкритті сторінки, пошук за Enter чи кнопкою, «Показати ще» по 20. «Встановити» качає
     * найновішу версію під цю гру (ModrinthBrowse.install) у теку сторінки і оновлює список вище.
     *
     * <p>24.11.2026 (власник: «в "знайти моди" нехай буде підвкладка "Знайти на Modrinth" та "Знайти на CurseForge". Те
     * саме з шейдерами і ресурспаками, і те саме з модпаками»): замість заголовка перемикач джерела. CurseForge шукає і
     * ставить через curseforge.CurseBrowse (той самий фільтр гри і завантажувача, залежності модів, журнал
     * поставленого); фільтра «Клієнт / Сервер» там немає, бо CurseForge цього не знає.</p>
     */
    private final class Browse extends JPanel {
        final com.gummer.launcher.manage.ModrinthBrowse.Kind kind;
        /** Шукати на CurseForge, а не на Modrinth. */
        boolean curse;
        JPanel filterRow;
        final File dir;
        final Runnable refresh;
        final String game = com.gummer.launcher.manage.ModrinthBrowse.gameVersionOf(instance);
        final String[] loaders;
        final JTextField query = new JTextField();
        final JPanel results = column();
        final JLabel status = new JLabel();
        final JButton more = LookUi.secondary(new JButton(tr("manage.browse.more")));
        /** 17.11.2026: лише для модів: популярне, лише для клієнта, для сервера. */
        final JComboBox<String> filter = new JComboBox<String>(new String[]{tr("manage.browse.sort.popular"),
                tr("manage.browse.sort.client"), tr("manage.browse.sort.server")});
        boolean loaded;
        int offset, total, generation;
        /** 24.11.2026: рядки результатів, що чекають, поки пошук з'явиться на екрані (див. search). */
        Runnable parked;

        Browse(com.gummer.launcher.manage.ModrinthBrowse.Kind kind, File dir, Runnable refresh) {
            super(new MigLayout("insets 0, wrap 1, gap 0 4, fillx", "[grow,fill]", ""));
            setOpaque(false);
            this.kind = kind;
            this.dir = dir;
            this.refresh = refresh;
            this.loaders = com.gummer.launcher.manage.ModrinthBrowse.loadersOf(kind, instance);
            JPanel c = card();
            boolean noShaders = kind == com.gummer.launcher.manage.ModrinthBrowse.Kind.SHADER && loaders == null;
            final JTextArea hintText = LookUi.body(hintFor(false, noShaders), true);
            // позначки «клієнт / сервер» є лише в Modrinth, з CurseForge легенда ховається
            final JTextArea legend = kind == com.gummer.launcher.manage.ModrinthBrowse.Kind.MOD ? LookUi.body(tr("manage.side.legend"), true) : null;
            if (noShaders) {
                c.add(LookUi.title(tr("manage.browse.title"), 15f));
            } else {
                // ListModeSwitch: перший підпис праворуч, другий ліворуч; ліворуч Modrinth
                com.gummer.launcher.look.ListModeSwitch source = new com.gummer.launcher.look.ListModeSwitch(
                        tr("manage.browse.titleCurse"), tr("manage.browse.title"), true, modrinth -> {
                    if (curse == !modrinth) return;
                    curse = !modrinth;
                    hintText.setText(hintFor(curse, false));
                    if (filterRow != null) filter.setVisible(!curse);
                    if (legend != null) legend.setVisible(!curse);
                    loaded = true;
                    search(false);
                });
                c.add(source, "w 360!, h 32!, wmax 100%");
            }
            c.add(hintText, "wmin 0");
            if (legend != null) c.add(legend, "wmin 0, gaptop 4, hidemode 3");
            if (!noShaders) {
                JButton go = LookUi.secondary(new JButton(tr("manage.browse.search")));
                boolean mods = kind == com.gummer.launcher.manage.ModrinthBrowse.Kind.MOD;
                JPanel row = new JPanel(new MigLayout("insets 0, gap 8, fillx", mods ? "[grow,fill][][]" : "[grow,fill][]", "[32!]"));
                row.setOpaque(false);
                query.putClientProperty("JTextField.placeholderText", tr(kind == com.gummer.launcher.manage.ModrinthBrowse.Kind.MODPACK
                        ? "manage.browse.placeholderModpacks" : kind == com.gummer.launcher.manage.ModrinthBrowse.Kind.SHADER
                        ? "manage.browse.placeholderShaders" : kind == com.gummer.launcher.manage.ModrinthBrowse.Kind.MOD
                        ? "manage.browse.placeholderMods" : "manage.browse.placeholderPacks"));
                row.add(query, "wmin 0, h 32!");
                if (mods) {
                    filter.setToolTipText(tr("manage.browse.sort.tip"));
                    filter.addActionListener(e -> search(false));
                    row.add(filter, "h 32!, hidemode 3");
                    filterRow = row;
                }
                row.add(go, "h 32!");
                c.add(row, "wmin 0, gaptop 6");
                status.setForeground(pal.text3);
                status.setFont(status.getFont().deriveFont(12f));
                c.add(status, "wmin 0, gaptop 2");
                c.add(results, "wmin 0, gaptop 4");
                JPanel moreRow = new JPanel(new MigLayout("insets 0, fillx", "push[]push", "[32!]"));
                moreRow.setOpaque(false);
                moreRow.add(more, "h 32!");
                more.setVisible(false);
                c.add(moreRow, "wmin 0, gaptop 4, hidemode 3");
                go.addActionListener(e -> search(false));
                query.addActionListener(e -> search(false));
                more.addActionListener(e -> search(true));
            }
            add(c, "wmin 0");
            addHierarchyListener(e -> {
                if ((e.getChangeFlags() & java.awt.event.HierarchyEvent.SHOWING_CHANGED) != 0 && isShowing() && parked != null) {
                    Runnable r = parked;
                    parked = null;
                    SwingUtilities.invokeLater(r);
                }
            });
        }

        /** Підказка над пошуком: що показано (для модпаків ще звідки). */
        String hintFor(boolean cf, boolean noShaders) {
            return noShaders ? tr("manage.browse.noShaders")
                    : kind == com.gummer.launcher.manage.ModrinthBrowse.Kind.MODPACK
                    ? tr(cf ? "manage.browse.hintModpacksCurse" : "manage.browse.hintModpacks", game, loaderTitle(loaders))
                    : kind == com.gummer.launcher.manage.ModrinthBrowse.Kind.MOD
                    ? (game == null ? tr("manage.browse.hintNoVersion")
                    : !instance.isBuiltIn() && !instance.isCopy() ? tr("manage.browse.hintModsPack", game)
                    : tr("manage.browse.hintMods", game, loaderTitle(loaders)))
                    : game != null ? tr("manage.browse.hint", game) : tr("manage.browse.hintNoVersion");
        }

        /**
         * Завантажувач для пошуку CurseForge (там один): моди за останнім зі списку збірки (у Quilt це Fabric, модів під
         * Quilt на CurseForge мало), модпаки за своїм; ресурспаки і шейдери без нього.
         */
        String curseLoader() {
            if (loaders == null || loaders.length == 0) return null;
            if (kind == com.gummer.launcher.manage.ModrinthBrowse.Kind.MOD) return loaders[loaders.length - 1];
            return kind == com.gummer.launcher.manage.ModrinthBrowse.Kind.MODPACK ? loaders[0] : null;
        }

        void loadIfNeeded() {
            if (loaded) return;
            loaded = true;
            search(false);
        }

        void search(final boolean append) {
            final int gen = append ? generation : ++generation;
            final int from = append ? offset : 0;
            final String q = query.getText();
            final com.gummer.launcher.manage.ModrinthBrowse.Filter f =
                    com.gummer.launcher.manage.ModrinthBrowse.Filter.values()[Math.max(0, filter.getSelectedIndex())];
            final boolean cf = curse;
            status.setText(tr("manage.browse.loading"));
            more.setEnabled(false);
            if (!append) {
                results.removeAll();
                results.revalidate();
                results.repaint();
            }
            // 17.11.2026: свій пул Modrinth замість спільного SwingWorker; «Встановлено» рахується тут же у фоні
            // (для модів це читання всіх jar збірки, у потоці інтерфейсу воно підвішувало вікно)
            com.gummer.launcher.manage.ModrinthBrowse.POOL.execute(() -> {
                com.gummer.launcher.manage.ModrinthBrowse.Page found = null;
                Exception error = null;
                try {
                    found = cf ? com.gummer.launcher.curseforge.CurseBrowse.search(launcher, kind, q, game, curseLoader(), null,
                            com.gummer.launcher.curseforge.CurseBrowse.Sort.BEST, from)
                            : com.gummer.launcher.manage.ModrinthBrowse.search(kind, q, game, loaders, from, f);
                    for (com.gummer.launcher.manage.ModrinthBrowse.Hit h : found.hits) h.installed = looksInstalled(h);
                } catch (Exception ex) {
                    error = ex;
                }
                final com.gummer.launcher.manage.ModrinthBrowse.Page result = found;
                final Exception failed = error;
                SwingUtilities.invokeLater(() -> {
                    if (gen != generation) return; // уже новий пошук
                    more.setEnabled(true);
                    List<com.gummer.launcher.manage.ModrinthBrowse.Hit> hits = java.util.Collections.emptyList();
                    try {
                        if (failed != null) throw failed;
                        com.gummer.launcher.manage.ModrinthBrowse.Page page = result;
                        total = page.total;
                        offset = from + page.hits.size();
                        hits = page.hits;
                        // CurseForge більше 10000 не рахує (curseforge.CurseBrowse.MAX_RESULTS)
                        status.setText(total == 0 ? tr("manage.browse.nothing")
                                : cf && total >= com.gummer.launcher.curseforge.CurseBrowse.MAX_RESULTS ? tr("cpage.foundMany")
                                : tr("manage.browse.found", total));
                        more.setVisible(offset < total && !page.hits.isEmpty());
                    } catch (Exception ex) {
                        status.setText(tr(cf ? "manage.browse.failedCurse" : "manage.browse.failed"));
                        more.setVisible(false);
                    }
                    // 24.11.2026 (власник: «інтерфейс лаунчера має "літати"»): 20 рядків з описами разом тримали вікно
                    // ~75 мс; тепер порціями по 5 наступними подіями, вікно між ними відповідає. І лише коли пошук на
                    // екрані: результати, що приходять до прихованої «Знайти» (вона вантажиться наперед), раніше
                    // розкладались при першому ж кліку на іншу сторінку («Шейдери» на збірці зі 196 модами: 75 мс)
                    final List<com.gummer.launcher.manage.ModrinthBrowse.Hit> todo = hits;
                    final int[] at = {0};
                    final int[] keep = {Integer.MIN_VALUE};
                    final JScrollPane[] sp = new JScrollPane[1];
                    final Runnable[] step = new Runnable[1];
                    step[0] = () -> {
                        if (gen != generation) return; // уже новий пошук
                        if (!isShowing()) {
                            parked = step[0]; // продовжить hierarchyChanged, щойно «Знайти» відкриють
                            return;
                        }
                        if (keep[0] == Integer.MIN_VALUE) {
                            // нові рядки не мають прокручувати сторінку (опис у JTextArea тягне прокрутку до себе)
                            sp[0] = (JScrollPane) SwingUtilities.getAncestorOfClass(JScrollPane.class, Browse.this);
                            keep[0] = sp[0] != null ? sp[0].getVerticalScrollBar().getValue() : -1;
                        }
                        int to = Math.min(todo.size(), at[0] + HIT_ROWS_CHUNK);
                        for (int k = at[0]; k < to; k++) results.add(new HitRow(todo.get(k)), "wmin 0");
                        at[0] = to;
                        results.revalidate();
                        results.repaint();
                        if (to < todo.size()) {
                            SwingUtilities.invokeLater(step[0]);
                            return;
                        }
                        // 17.11.2026: описи нових рядків спершу міряються без ширини (вищими), і під списком лишалась
                        // порожнеча перед «Показати ще»; розкласти ще раз з остаточною шириною
                        relayoutLater(results);
                        if (sp[0] != null) SwingUtilities.invokeLater(() -> sp[0].getVerticalScrollBar().setValue(keep[0]));
                    };
                    step[0].run();
                });
            });
        }

        /** Чи вже лежить файл, схожий на цей проєкт (назва файлу починається з його slug). */
        /**
         * 17.11.2026: модпак стає новою збіркою: прогрес у вікні (моди качаються одразу, по 6), потім головне вікно
         * перечитує список і вибирає нову збірку. Ця збірка і її моди не змінюються.
         */
        void installModpack(final com.gummer.launcher.manage.ModrinthBrowse.Hit h, final JButton install) {
            if (!SwingHelper.confirmDialog(InstanceManageDialog.this, tr("manage.browse.modpackConfirm", h.title, game,
                    loaderTitle(loaders)), tr("manage.browse.modpackConfirmTitle"))) return;
            install.setEnabled(false);
            install.setText(tr("manage.browse.installing"));
            final com.gummer.launcher.modrinth.MrpackPacks importer = new com.gummer.launcher.modrinth.MrpackPacks();
            final boolean cf = com.gummer.launcher.curseforge.CurseBrowse.isCurseId(h.id);
            final com.google.common.util.concurrent.ListenableFuture<String> task = launcher.getExecutor().submit(
                    () -> cf ? com.gummer.launcher.curseforge.CurseBrowse.installModpack(launcher, h, game, loaders, importer)
                            : com.gummer.launcher.manage.ModrinthBrowse.installModpack(launcher, h, game, loaders, importer));
            com.skcraft.concurrency.ObservableFuture<String> future = new com.skcraft.concurrency.ObservableFuture<String>(task, importer);
            com.skcraft.launcher.dialog.ProgressDialog.showProgress(InstanceManageDialog.this, future,
                    tr("manage.browse.modpackProgressTitle"), tr("manage.browse.modpackProgress", h.title));
            future.addListener(() -> {
                try {
                    String name = task.get();
                    install.setText(tr("manage.browse.installed"));
                    com.gummer.launcher.stats.LaunchStats.added(launcher, "modpack");
                    host.copyCreated(name);
                    toast(tr("manage.browse.modpackDone", h.title));
                } catch (Exception ex) {
                    Throwable t = ex.getCause() != null ? ex.getCause() : ex;
                    install.setEnabled(true);
                    install.setText(tr("manage.browse.install"));
                    boolean noVersion = com.gummer.launcher.manage.ModrinthBrowse.NO_VERSION.equals(t.getMessage());
                    toast(noVersion ? tr("manage.browse.noVersion", h.title, game != null ? game : "?")
                            : tr("manage.browse.installFailed", h.title));
                    if (!noVersion) java.util.logging.Logger.getLogger(InstanceManageDialog.class.getName())
                            .log(java.util.logging.Level.WARNING, "Modpack install failed: " + h.slug, t);
                }
            }, com.skcraft.launcher.util.SwingExecutor.INSTANCE);
        }

        boolean looksInstalled(com.gummer.launcher.manage.ModrinthBrowse.Hit h) {
            if (kind == com.gummer.launcher.manage.ModrinthBrowse.Kind.MODPACK) return com.gummer.launcher.modrinth.MrpackPacks.hasProject(launcher, h.id);
            return com.gummer.launcher.manage.ModrinthBrowse.looksInstalled(dir, h.id, h.slug);
        }

        /** Рядок результату: іконка, назва, автор і завантаження, опис, «Сторінка», «Встановити». */
        /** Скільки рядків пошуку додається за одну подію потоку інтерфейсу. */
        private static final int HIT_ROWS_CHUNK = 5;

        final class HitRow extends JPanel {
            HitRow(final com.gummer.launcher.manage.ModrinthBrowse.Hit h) {
                super(new MigLayout("insets 8 10 8 8, gap 12 2, fillx", "[" + ICON + "!][grow,fill][]", "[]"));
                setOpaque(false);
                // 17.11.2026: іконка з пам'яті одразу, інакше у своєму пулі Modrinth (6 паралельно); довантажена лише
                // перемальовується: раніше кожна з 20 іконок перераховувала розкладку всієї сторінки
                Image known = com.gummer.launcher.manage.ModrinthBrowse.cachedIcon(h.iconUrl);
                final ModIcon iconView = new ModIcon(known, h.title);
                add(iconView, "spany 3, aligny top, w " + ICON + "!, h " + ICON + "!");
                if (h.iconUrl != null && known == null) {
                    com.gummer.launcher.manage.ModrinthBrowse.POOL.execute(() -> {
                        final Image img = com.gummer.launcher.manage.ModrinthBrowse.icon(h.iconUrl);
                        if (img == null) return;
                        SwingUtilities.invokeLater(() -> iconView.setImage(img));
                    });
                }
                JLabel name = new JLabel(h.title);
                name.setFont(name.getFont().deriveFont(Font.BOLD, 13.5f));
                name.setForeground(pal.text);
                if (kind == com.gummer.launcher.manage.ModrinthBrowse.Kind.MOD && h.side != null) {
                    // 17.11.2026: де працює мод (клієнт, сервер, обидва)
                    JPanel line = new JPanel(new MigLayout("insets 0, gap 8", "[]", "[]"));
                    line.setOpaque(false);
                    line.add(name, "wmin 0");
                    line.add(sideTag(h.side));
                    add(line, "wmin 0");
                } else {
                    add(name, "wmin 0");
                }
                final JButton install = LookUi.secondary(new JButton(tr("manage.browse.install")));
                // 26.11.2026 (власник: «дозволь обирати версії модів перед установкою»): поруч «Версія...», список версій
                // під цю збірку; «Встановити» як раніше ставить найновішу, що підходить
                final JButton versions = LookUi.secondary(new JButton(tr("manage.version.button")));
                versions.setToolTipText(tr("manage.version.tip"));
                versions.setVisible(kind == com.gummer.launcher.manage.ModrinthBrowse.Kind.MOD && !h.installed);
                if (h.installed) {
                    install.setText(tr("manage.browse.installed"));
                    install.setEnabled(false);
                }
                // 26.11.2026: обидві в одній клітинці (split): схована «Версія...» в окремій колонці лишала проміжок між
                // колонками, і «Встановлено» стояло на 6 px лівіше за «Встановити» в сусідніх рядках
                JPanel actions = new JPanel(new MigLayout("insets 0, gap 6, hidemode 3", "[right]", "[]"));
                actions.setOpaque(false);
                actions.add(versions, "h 32!, split 2");
                actions.add(install, "h 32!");
                add(actions, "spany 3, aligny center, wrap");
                if (h.description != null && !h.description.isEmpty()) {
                    JTextArea d = LookUi.body(cut(h.description, 200), true);
                    if (d.getCaret() instanceof javax.swing.text.DefaultCaret) {
                        ((javax.swing.text.DefaultCaret) d.getCaret()).setUpdatePolicy(javax.swing.text.DefaultCaret.NEVER_UPDATE);
                    }
                    d.setFont(d.getFont().deriveFont(12f));
                    d.setForeground(pal.text2);
                    add(d, "skip 1, wmin 0, wrap");
                }
                if ((kind == com.gummer.launcher.manage.ModrinthBrowse.Kind.MOD || kind == com.gummer.launcher.manage.ModrinthBrowse.Kind.RESOURCEPACK)
                        && com.gummer.launcher.manage.ModrinthBrowse.isXray(h)) {
                    add(xrayWarning(), "skip 1, wmin 0, wrap"); // 24.11.2026: червона позначка X-ray
                }
                JPanel meta = new JPanel(new MigLayout("insets 0, gap 10", "[]", "[]"));
                meta.setOpaque(false);
                meta.add(small(tr("manage.browse.meta", h.author, downloads(h.downloads))), "wmin 0");
                JLabel site = new JLabel(tr("manage.browse.page"));
                site.setFont(site.getFont().deriveFont(12f));
                site.setForeground(pal.accent);
                site.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                final String url = com.gummer.launcher.manage.ModrinthBrowse.projectUrl(kind, h);
                site.setToolTipText(url);
                site.addMouseListener(new MouseAdapter() {
                    @Override
                    public void mouseClicked(MouseEvent e) {
                        SwingHelper.openURL(url, InstanceManageDialog.this);
                    }
                });
                meta.add(site);
                add(meta, "skip 1, wmin 0");

                install.addActionListener(e -> {
                    if (kind == com.gummer.launcher.manage.ModrinthBrowse.Kind.MODPACK) {
                        installModpack(h, install);
                        return;
                    }
                    installHit(h, install, versions, null);
                });
                versions.addActionListener(e -> pickVersion(h, install, versions));
            }

            /** 26.11.2026: версії мода під цю збірку у фоні, потім вікно вибору; вибрана ставиться як звичайно. */
            void pickVersion(final com.gummer.launcher.manage.ModrinthBrowse.Hit h, final JButton install, final JButton versions) {
                versions.setEnabled(false);
                versions.setText(tr("manage.browse.loading"));
                final boolean cf = com.gummer.launcher.curseforge.CurseBrowse.isCurseId(h.id);
                new SwingWorker<List<com.gummer.launcher.manage.ModrinthBrowse.PackVersion>, Void>() {
                    @Override
                    protected List<com.gummer.launcher.manage.ModrinthBrowse.PackVersion> doInBackground() throws Exception {
                        return cf ? com.gummer.launcher.curseforge.CurseBrowse.fileChoices(launcher, kind, h, game, loaders)
                                : ModVersionPicker.forBuild(com.gummer.launcher.manage.ModrinthBrowse.modVersions(h.id), game, loaders);
                    }

                    @Override
                    protected void done() {
                        versions.setEnabled(true);
                        versions.setText(tr("manage.version.button"));
                        List<com.gummer.launcher.manage.ModrinthBrowse.PackVersion> vs;
                        try {
                            vs = get();
                        } catch (Exception ex) {
                            java.util.logging.Logger.getLogger(InstanceManageDialog.class.getName()).log(java.util.logging.Level.INFO,
                                    "Mod versions failed: " + h.slug, ex);
                            toast(tr(cf ? "manage.browse.failedCurse" : "manage.browse.failed"));
                            return;
                        }
                        if (vs.isEmpty()) {
                            toast(tr("manage.browse.noVersion", h.title, game != null ? game : "?"));
                            return;
                        }
                        com.gummer.launcher.manage.ModrinthBrowse.PackVersion v = ModVersionPicker.pick(InstanceManageDialog.this, h.title,
                                loaderTitle(loaders) + " " + (game != null ? game : "?"), vs);
                        if (v != null) installHit(h, install, versions, v.id);
                    }
                }.execute();
            }

            /** Поставити мод (ресурспак, шейдер): {@code versionId} вибрана версія, null найновіша, що підходить. */
            void installHit(final com.gummer.launcher.manage.ModrinthBrowse.Hit h, final JButton install, final JButton versions,
                            final String versionId) {
                    install.setEnabled(false);
                    versions.setEnabled(false);
                    install.setText(tr("manage.browse.installing"));
                    final List<String> got = java.util.Collections.synchronizedList(new ArrayList<String>());
                    final List<String> missing = java.util.Collections.synchronizedList(new ArrayList<String>());
                    final List<String> conflicts = java.util.Collections.synchronizedList(new ArrayList<String>());
                    new SwingWorker<String, Void>() {
                        @Override
                        protected String doInBackground() throws Exception {
                            if (com.gummer.launcher.curseforge.CurseBrowse.isCurseId(h.id)) {
                                return com.gummer.launcher.curseforge.CurseBrowse.installFile(launcher, kind, h, game, loaders, dir, got, missing, conflicts, versionId);
                            }
                            return com.gummer.launcher.manage.ModrinthBrowse.install(kind, h, game, loaders, dir, got, missing, conflicts, versionId);
                        }

                        @Override
                        protected void done() {
                            try {
                                String file = get();
                                install.setText(tr("manage.browse.installed"));
                                versions.setVisible(false);
                                if (kind == com.gummer.launcher.manage.ModrinthBrowse.Kind.MOD) changed = true;
                                refresh.run();
                                java.util.List<String> deps = new ArrayList<String>(got);
                                deps.remove(h.title);
                                toast(deps.isEmpty() ? tr("manage.browse.done", h.title)
                                        : tr("manage.browse.doneWithDeps", h.title, String.join(", ", deps)));
                                com.gummer.launcher.stats.LaunchStats.added(launcher, kind.projectType);
                                // 17.11.2026: чого бракує чи що заважає, гравець має побачити одразу, а не на вильоті гри
                                java.util.List<String> warn = new ArrayList<String>();
                                if (!missing.isEmpty()) warn.add(tr(com.gummer.launcher.curseforge.CurseBrowse.isCurseId(h.id)
                                        ? "manage.browse.depsMissingCurse" : "manage.browse.depsMissing", h.title, String.join(", ", missing), game != null ? game : "?"));
                                if (!conflicts.isEmpty()) warn.add(tr("manage.browse.depsConflict", h.title, String.join(", ", conflicts)));
                                if (!warn.isEmpty()) {
                                    JOptionPane.showMessageDialog(InstanceManageDialog.this, String.join("\n\n", warn),
                                            tr("manage.browse.depsTitle"), JOptionPane.WARNING_MESSAGE);
                                }
                                java.util.logging.Logger.getLogger(InstanceManageDialog.class.getName()).info("Modrinth " + kind.projectType + " installed: " + file);
                            } catch (Exception ex) {
                                Throwable t = ex.getCause() != null ? ex.getCause() : ex;
                                install.setEnabled(true);
                                versions.setEnabled(true);
                                install.setText(tr("manage.browse.install"));
                                boolean noVersion = com.gummer.launcher.manage.ModrinthBrowse.NO_VERSION.equals(t.getMessage());
                                toast(noVersion ? tr("manage.browse.noVersion", h.title, game != null ? game : "?")
                                        // 25.11.2026 (тікет #122): жодна версія не йде під завантажувач збірки
                                        : t instanceof com.gummer.launcher.manage.ModUpdates.LoaderTooOld ? t.getLocalizedMessage()
                                        : com.gummer.launcher.curseforge.CurseBrowse.NOT_DOWNLOADED.equals(t.getMessage())
                                        ? tr("cpage.notDownloaded", h.title) : tr("manage.browse.installFailed", h.title));
                                if (!noVersion) java.util.logging.Logger.getLogger(InstanceManageDialog.class.getName()).log(java.util.logging.Level.WARNING, "Install failed: " + h.id + " " + h.slug, t);
                            }
                        }
                    }.execute();
            }
        }
    }

    private static String loaderTitle(String[] loaders) {
        if (loaders == null || loaders.length == 0) return "?";
        String l = loaders[0];
        return l.equals("neoforge") ? "NeoForge" : Character.toUpperCase(l.charAt(0)) + l.substring(1);
    }

    private static String downloads(long n) {
        if (n >= 1_000_000) return String.format(Locale.ROOT, "%.1f M", n / 1_000_000.0);
        if (n >= 1_000) return String.format(Locale.ROOT, "%.0f K", n / 1_000.0);
        return String.valueOf(n);
    }

    /** Що зараз показано в списку файлів (ресурспаки, шейдери): підпис теки. */
    private final java.util.Map<JPanel, String> filesShown = new java.util.HashMap<JPanel, String>();

    private static String filesSignature(File[] files) {
        if (files == null) return "";
        StringBuilder b = new StringBuilder();
        for (File f : files) b.append(f.getName()).append('|').append(f.length()).append('|').append(f.lastModified()).append('\n');
        return b.toString();
    }

    /** Повернення на вкладку: перебудувати, лише якщо файли змінились. */
    private void fillFilesIfChanged(JPanel list, File dir, String ext) {
        File[] files = dir.listFiles(f -> f.isDirectory() || f.getName().toLowerCase(Locale.ROOT).endsWith("." + ext));
        String sig = filesSignature(files);
        if (sig.equals(filesShown.get(list))) return;
        fillFiles(list, dir, ext);
    }

    private void fillFiles(JPanel list, File dir, String ext) {
        list.removeAll();
        File[] files = dir.listFiles(f -> f.isDirectory() || f.getName().toLowerCase(Locale.ROOT).endsWith("." + ext));
        filesShown.put(list, filesSignature(files));
        JTextArea emptyText = emptyTexts.get(list);
        if (emptyText != null) emptyText.setVisible(false);
        if (files == null || files.length == 0) {
            // 17.11.2026: де є підвкладка «Знайти», порожній список підказує про неї
            boolean canFind = list == packsList ? packsBrowse != null : list == shadersList && shadersBrowse != null;
            String empty = list == packsList && packsLocked() ? "manage.files.emptyLocked"
                    : canFind ? "manage.files.emptyFind" : "manage.files.empty";
            JTextArea e = emptyTexts.get(list);
            if (e != null) {
                e.setText(tr(empty));
                e.setVisible(true);
            } else {
                list.add(small(tr(empty)), "wmin 0");
            }
        } else {
            Arrays.sort(files, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
            for (File f : files) {
                JPanel row = rowPanel();
                JLabel name = new JLabel(f.getName());
                name.setForeground(pal.text);
                row.add(name, "wmin 0, growx");
                row.add(small(f.isDirectory() ? tr("manage.files.folder") : size(f.length())), "gapleft 8");
                if (list == packsList && com.gummer.launcher.manage.ModrinthBrowse.isXray(f.getName())) {
                    row.add(xrayWarning(), "newline, span, wmin 0"); // 24.11.2026: ресурспак X-ray
                }
                list.add(row, "wmin 0");
            }
        }
        list.revalidate();
        list.repaint();
        relayoutLater(list);
    }

    /**
     * 17.11.2026: текст з переносом (JTextArea) знає свою висоту лише після того, як отримав ширину, а MigLayout
     * запам'ятовує розміри з першого проходу: картка лишалась вищою, ніж треба. Після розкладу скинути збережене
     * в усій сторінці і розкласти ще раз.
     */
    static void relayoutLater(final Component c) {
        SwingUtilities.invokeLater(() -> {
            Container sp = SwingUtilities.getAncestorOfClass(JScrollPane.class, c);
            Component root = sp != null ? ((JScrollPane) sp).getViewport().getView() : c;
            invalidateTree(root);
            root.revalidate();
            root.repaint();
        });
    }

    private static void invalidateTree(Component c) {
        c.invalidate();
        if (c instanceof Container) for (Component k : ((Container) c).getComponents()) invalidateTree(k);
    }

    // ------------------------------------------------------------------ Світи

    private JPanel worldsPage() {
        JPanel p = page();
        JPanel c = card();
        File saves = new File(instance.getContentDir(), "saves");
        JPanel head = new JPanel(new MigLayout("insets 0, gap 8, fillx", "[grow][]", "[]"));
        head.setOpaque(false);
        head.add(LookUi.title(tr("manage.worlds.title"), 15f));
        // 17.11.2026 (власник: «Світи: бекап світу, копіювання світу в іншу збірку ... - ок, го»)
        final JButton backups = LookUi.secondary(new JButton(tr("manage.worlds.backups")));
        backups.addActionListener(e -> backupsMenu(backups));
        head.setLayout(new MigLayout("insets 0, gap 8, fillx", "[grow][][]", "[]"));
        head.add(backups);
        head.add(folderButton(tr("manage.openFolder"), saves));
        c.add(head, "wmin 0");
        c.add(LookUi.body(tr("manage.worlds.hint2"), true), "wmin 0");
        c.add(worldsList, "wmin 0, gaptop 6");
        p.add(c);
        return p;
    }

    private void fillWorlds() {
        worldsList.removeAll();
        File[] worlds = new File(instance.getContentDir(), "saves").listFiles(f -> f.isDirectory() && new File(f, "level.dat").isFile());
        if (worlds == null || worlds.length == 0) {
            worldsList.add(small(tr("manage.worlds.empty")), "wmin 0");
        } else {
            Arrays.sort(worlds, (a, b) -> Long.compare(new File(b, "level.dat").lastModified(), new File(a, "level.dat").lastModified()));
            SimpleDateFormat fmt = new SimpleDateFormat("dd.MM.yyyy HH:mm");
            for (final File w : worlds) {
                JPanel row = rowPanel();
                // 17.11.2026: назва і дата двома рядками, щоб біля кнопок назва не обрізалась («Test World (бе...»)
                JPanel info = column();
                JLabel name = new JLabel(w.getName());
                name.setForeground(pal.text);
                name.setToolTipText(w.getName());
                info.add(name, "wmin 0");
                info.add(small(tr("manage.worlds.played", fmt.format(new Date(new File(w, "level.dat").lastModified())))), "wmin 0");
                row.add(info, "wmin 0, growx");
                if (host != null && instance.isInstalled() && com.gummer.launcher.manage.Worlds.canQuickPlay(instance)) {
                    JButton play = LookUi.primary(new JButton(tr("manage.worlds.play")));
                    play.putClientProperty("FlatLaf.styleClass", null);
                    play.setToolTipText(tr("manage.worlds.playTip"));
                    play.addActionListener(e -> {
                        dispose();
                        host.playWorld(instance, w.getName());
                    });
                    row.add(play, "gapleft 8, h 28!");
                }
                final JButton backup = LookUi.secondary(new JButton(tr("manage.worlds.backup")));
                backup.addActionListener(e -> backupWorld(w, backup));
                row.add(backup, "gapleft 6, h 28!");
                final JButton copy = LookUi.secondary(new JButton(tr("manage.worlds.copyTo")));
                copy.addActionListener(e -> copyWorldMenu(w, copy));
                row.add(copy, "gapleft 6, h 28!");
                JButton open = LookUi.secondary(new JButton(tr("manage.worlds.open")));
                open.addActionListener(e -> SwingHelper.browseDir(w, this));
                row.add(open, "gapleft 6, h 28!");
                worldsList.add(row, "wmin 0");
            }
        }
        worldsList.revalidate();
        worldsList.repaint();
    }

    /** Бекап світу у фоні; кнопка показує, що йде запис. */
    private void backupWorld(final File world, final JButton button) {
        button.setEnabled(false);
        button.setText(tr("manage.worlds.backingUp"));
        new SwingWorker<File, Void>() {
            @Override
            protected File doInBackground() throws Exception {
                return com.gummer.launcher.manage.Worlds.backup(launcher, instance, world);
            }

            @Override
            protected void done() {
                button.setEnabled(true);
                button.setText(tr("manage.worlds.backup"));
                try {
                    File zip = get();
                    toast(tr("manage.worlds.backedUp", world.getName(), Math.max(1, zip.length() / (1024 * 1024))));
                } catch (Exception e) {
                    Throwable t = e.getCause() != null ? e.getCause() : e;
                    SwingHelper.showErrorDialog(InstanceManageDialog.this, tr("manage.worlds.failed", String.valueOf(t.getMessage())), tr("manage.worlds.title"));
                }
            }
        }.execute();
    }

    /** Меню бекапів збірки: відновити (у нову теку) або відкрити теку бекапів. */
    private void backupsMenu(JComponent anchor) {
        JPopupMenu menu = new JPopupMenu();
        List<File> zips = com.gummer.launcher.manage.Worlds.backups(launcher, instance);
        if (zips.isEmpty()) {
            JMenuItem none = new JMenuItem(tr("manage.worlds.noBackups"));
            none.setEnabled(false);
            menu.add(none);
        }
        for (int i = 0; i < Math.min(15, zips.size()); i++) {
            final File zip = zips.get(i);
            String name = zip.getName().substring(0, zip.getName().length() - 4);
            JMenuItem item = new JMenuItem(tr("manage.worlds.restore", name, Math.max(1, zip.length() / (1024 * 1024))));
            item.addActionListener(e -> restoreWorld(zip));
            menu.add(item);
        }
        menu.addSeparator();
        JMenuItem open = new JMenuItem(tr("manage.worlds.openBackups"));
        open.addActionListener(e -> {
            File dir = com.gummer.launcher.manage.Worlds.backupsDir(launcher, instance);
            //noinspection ResultOfMethodCallIgnored
            dir.mkdirs();
            SwingHelper.browseDir(dir, this);
        });
        menu.add(open);
        menu.show(anchor, 0, anchor.getHeight());
    }

    private void restoreWorld(final File zip) {
        new SwingWorker<File, Void>() {
            @Override
            protected File doInBackground() throws Exception {
                return com.gummer.launcher.manage.Worlds.restore(instance, zip);
            }

            @Override
            protected void done() {
                try {
                    File w = get();
                    toast(tr("manage.worlds.restored", w.getName()));
                    fillWorlds();
                } catch (Exception e) {
                    Throwable t = e.getCause() != null ? e.getCause() : e;
                    SwingHelper.showErrorDialog(InstanceManageDialog.this, tr("manage.worlds.failed", String.valueOf(t.getMessage())), tr("manage.worlds.title"));
                }
            }
        }.execute();
    }

    /** «Копіювати в...»: інші встановлені збірки; у старішу гру лише після попередження. */
    private void copyWorldMenu(final File world, JComponent anchor) {
        JPopupMenu menu = new JPopupMenu();
        final String from = com.gummer.launcher.manage.ModrinthBrowse.gameVersionOf(instance);
        List<Instance> targets = new ArrayList<Instance>();
        synchronized (launcher.getInstances()) {
            for (Instance i : launcher.getInstances().getInstances()) {
                if (i == instance || com.skcraft.launcher.swing.InstanceTableModel.isBrowser(i)) continue;
                if (!i.isLocal() || !i.isInstalled() || i.getDir().equals(instance.getDir())) continue;
                targets.add(i);
            }
        }
        if (targets.isEmpty()) {
            JMenuItem none = new JMenuItem(tr("manage.worlds.noTargets"));
            none.setEnabled(false);
            menu.add(none);
        }
        for (final Instance t : targets) {
            final String to = com.gummer.launcher.manage.ModrinthBrowse.gameVersionOf(t);
            String label = t.getTitle() + (to != null && !t.getTitle().contains(to) ? "  (" + to + ")" : "");
            JMenuItem item = new JMenuItem(label);
            item.addActionListener(e -> {
                if (com.gummer.launcher.manage.Worlds.compareGame(to, from) < 0
                        && !SwingHelper.confirmDialog(this, tr("manage.worlds.olderGame", world.getName(), from, to), tr("manage.worlds.title"))) {
                    return;
                }
                copyWorld(world, t);
            });
            menu.add(item);
        }
        menu.show(anchor, 0, anchor.getHeight());
    }

    private void copyWorld(final File world, final Instance target) {
        new SwingWorker<File, Void>() {
            @Override
            protected File doInBackground() throws Exception {
                return com.gummer.launcher.manage.Worlds.copyTo(world, target);
            }

            @Override
            protected void done() {
                try {
                    File w = get();
                    toast(tr("manage.worlds.copied", w.getName(), target.getTitle()));
                } catch (Exception e) {
                    Throwable t = e.getCause() != null ? e.getCause() : e;
                    SwingHelper.showErrorDialog(InstanceManageDialog.this, tr("manage.worlds.failed", String.valueOf(t.getMessage())), tr("manage.worlds.title"));
                }
            }
        }.execute();
    }

    // ------------------------------------------------------------------ Скріншоти

    private JPanel screenshotsPage() {
        JPanel p = page();
        JPanel c = card();
        File dir = new File(instance.getContentDir(), "screenshots");
        JPanel head = new JPanel(new MigLayout("insets 0, gap 8, fillx", "[grow][]", "[]"));
        head.setOpaque(false);
        head.add(LookUi.title(tr("manage.shots.title"), 15f));
        head.add(folderButton(tr("manage.openFolder"), dir));
        c.add(head, "wmin 0");
        shotsGrid.setOpaque(false);
        c.add(shotsGrid, "wmin 0, gaptop 6");
        p.add(c);
        return p;
    }

    private static final int SHOTS_MAX = 45;

    private void fillScreenshots() {
        if (shotsLoader != null) return; // уже вантажимо або завантажено
        shotsGrid.removeAll();
        final File[] files = new File(instance.getContentDir(), "screenshots").listFiles(f -> {
            String n = f.getName().toLowerCase(Locale.ROOT);
            return f.isFile() && (n.endsWith(".png") || n.endsWith(".jpg"));
        });
        if (files == null || files.length == 0) {
            shotsGrid.add(small(tr("manage.shots.empty")), "span 3, wmin 0");
            shotsGrid.revalidate();
            return;
        }
        Arrays.sort(files, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
        final int n = Math.min(files.length, SHOTS_MAX);
        final List<JLabel> cells = new ArrayList<JLabel>();
        for (int i = 0; i < n; i++) {
            final File f = files[i];
            JLabel cell = new JLabel("…", SwingConstants.CENTER);
            cell.setForeground(pal.text3);
            cell.setOpaque(true);
            cell.setBackground(pal.surface2);
            cell.setPreferredSize(new Dimension(160, 90));
            cell.setToolTipText(f.getName());
            cell.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            cell.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    try {
                        Desktop.getDesktop().open(f);
                    } catch (IOException | RuntimeException ex) {
                        SwingHelper.browseDir(f.getParentFile(), InstanceManageDialog.this);
                    }
                }
            });
            cells.add(cell);
            shotsGrid.add(cell, "h 90!, wmin 0");
        }
        if (files.length > n) {
            shotsGrid.add(small(tr("manage.shots.more", files.length - n)), "span 3, wmin 0");
        }
        shotsGrid.revalidate();
        // мініатюри у фоні: великі PNG декодуються довго
        shotsLoader = new SwingWorker<Void, Object[]>() {
            @Override
            protected Void doInBackground() {
                for (int i = 0; i < n && !isCancelled(); i++) {
                    try {
                        BufferedImage img = ImageIO.read(files[i]);
                        if (img == null) continue;
                        int w = 320, h = Math.max(1, img.getHeight() * w / Math.max(1, img.getWidth()));
                        BufferedImage thumb = com.gummer.launcher.look.DevicePixels.resize(img, w, h, false);
                        publish(new Object[]{i, thumb});
                    } catch (IOException | RuntimeException ignored) {
                        // зіпсований файл: лишається «…»
                    }
                }
                return null;
            }

            @Override
            protected void process(List<Object[]> chunks) {
                for (Object[] c : chunks) {
                    JLabel cell = cells.get((Integer) c[0]);
                    cell.setText(null);
                    cell.setIcon(new ThumbIcon((BufferedImage) c[1]));
                }
            }
        };
        shotsLoader.execute();
    }

    /** Мініатюра, вписана в клітинку 160x90 (обрізка по центру, як «cover»). */
    private static final class ThumbIcon implements Icon {
        private final BufferedImage img;

        ThumbIcon(BufferedImage img) {
            this.img = img;
        }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            int cw = c.getWidth(), ch = c.getHeight();
            double k = Math.max((double) cw / img.getWidth(), (double) ch / img.getHeight());
            int w = (int) Math.round(img.getWidth() * k), h = (int) Math.round(img.getHeight() * k);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g2.clipRect(0, 0, cw, ch);
            g2.drawImage(img, (cw - w) / 2, (ch - h) / 2, w, h, null);
            g2.dispose();
        }

        @Override
        public int getIconWidth() {
            return 1;
        }

        @Override
        public int getIconHeight() {
            return 1;
        }
    }

    // ------------------------------------------------------------------ оформлення копії

    private final JLabel artIconPreview = new JLabel();
    private final JLabel artBgPreview = new JLabel();

    /** Картка «Оформлення»: своя іконка і свій фон копії. Лише для копій (у модпаків і версій оформлення від нас). */
    private JPanel artCard() {
        JPanel c = card();
        c.add(LookUi.title(tr("manage.art.title"), 15f));
        c.add(LookUi.body(tr("manage.art.hint"), true), "wmin 0");
        // рядок іконки і рядок фону: прев'ю ліворуч (однакова колонка), кнопки праворуч на всю ширину тексту
        JPanel row = new JPanel(new MigLayout("insets 0, gap 14 10, wrap 2, fillx", "[168!, center][grow]", "[]"));
        row.setOpaque(false);
        artIconPreview.setHorizontalAlignment(SwingConstants.CENTER);
        artBgPreview.setHorizontalAlignment(SwingConstants.CENTER);
        row.add(artIconPreview, "w 56!, h 56!");
        row.add(artButtons(true), "aligny center");
        row.add(artBgPreview, "w 168!, h 96!");
        row.add(artButtons(false), "aligny center");
        c.add(row, "wmin 0, gaptop 8");
        updateArtPreviews();
        return c;
    }

    private JPanel artButtons(final boolean icon) {
        JPanel col = new JPanel(new MigLayout("insets 0, gap 8", "[][]", "[32!]"));
        col.setOpaque(false);
        JButton pick = LookUi.secondary(new JButton(tr(icon ? "manage.art.pickIcon" : "manage.art.pickBackground")));
        JButton clear = LookUi.secondary(new JButton(tr("manage.art.reset")));
        pick.addActionListener(e -> {
            // 26.11.2026: системне вікно Windows з ескізами картинок (було JFileChooser без прев'ю)
            final File source = new com.gummer.launcher.swing.NativeFileDialog(this)
                    .title(tr(icon ? "manage.art.pickIcon" : "manage.art.pickBackground"))
                    .filter("PNG, JPG, GIF, BMP", "png", "jpg", "jpeg", "gif", "bmp")
                    .openFile();
            if (source == null) return;
            // зменшення великої картинки може тривати секунду: не в потоці інтерфейсу
            new SwingWorker<Void, Void>() {
                @Override
                protected Void doInBackground() throws Exception {
                    if (icon) com.gummer.launcher.manage.InstanceArt.setIcon(instance, source);
                    else com.gummer.launcher.manage.InstanceArt.setBackground(instance, source);
                    return null;
                }

                @Override
                protected void done() {
                    try {
                        get();
                        artChanged();
                    } catch (Exception ex) {
                        Throwable t = ex.getCause() != null ? ex.getCause() : ex;
                        toast(tr("manage.art.failed", t.getMessage() != null ? t.getMessage() : t.toString()));
                    }
                }
            }.execute();
        });
        clear.addActionListener(e -> {
            try {
                com.gummer.launcher.manage.InstanceArt.clear(icon ? com.gummer.launcher.manage.InstanceArt.iconFile(instance)
                        : com.gummer.launcher.manage.InstanceArt.backgroundFile(instance));
                artChanged();
            } catch (java.io.IOException ex) {
                toast(tr("manage.art.failed", ex.getMessage()));
            }
        });
        col.add(pick, "h 32!");
        col.add(clear, "h 32!");
        return col;
    }

    private void artChanged() {
        updateArtPreviews();
        if (host != null) host.artChanged();
    }

    private void updateArtPreviews() {
        java.awt.image.BufferedImage icon = com.gummer.launcher.manage.InstanceArt.icon(instance);
        artIconPreview.setIcon(icon != null ? new com.skcraft.launcher.swing.CrispIcon(icon, 48, 48) : null);
        artIconPreview.setText(icon != null ? null : tr("manage.art.none"));
        java.awt.image.BufferedImage bg = com.gummer.launcher.manage.InstanceArt.background(instance);
        if (bg != null) {
            double k = Math.min(168.0 / bg.getWidth(), 96.0 / bg.getHeight());
            artBgPreview.setIcon(new com.skcraft.launcher.swing.CrispIcon(bg, Math.max(1, (int) (bg.getWidth() * k)),
                    Math.max(1, (int) (bg.getHeight() * k))));
            artBgPreview.setText(null);
        } else {
            artBgPreview.setIcon(null);
            artBgPreview.setText(tr("manage.art.none"));
        }
        artIconPreview.setForeground(pal.text3);
        artBgPreview.setForeground(pal.text3);
    }

    // ------------------------------------------------------------------ додати файли

    private JButton addButton(final int page, final File dir, final String... ext) {
        JButton b = LookUi.secondary(new JButton(tr("manage.add")));
        b.addActionListener(e -> {
            File[] picked = new com.gummer.launcher.swing.NativeFileDialog(this).title(tr("manage.addTitle"))
                    .filter(String.join(", ", ext), ext).openFiles();
            if (picked != null) importFiles(Arrays.asList(picked), dir, page);
        });
        return b;
    }

    /** Скопіювати файли в теку розділу (наявні з тією ж назвою не перезаписуються). */
    private void importFiles(List<File> files, File dir, int page) {
        if (!dir.isDirectory() && !dir.mkdirs()) return;
        int added = 0, skipped = 0;
        for (File f : files) {
            if (!f.isFile()) {
                skipped++;
                continue;
            }
            File to = new File(dir, f.getName());
            if (to.exists() || new File(dir, f.getName() + InstanceMods.DISABLED).exists()) {
                skipped++;
                continue;
            }
            try {
                Files.copy(f.toPath(), to.toPath());
                added++;
            } catch (IOException e) {
                skipped++;
            }
        }
        if (added > 0) changed = true;
        toast(skipped == 0 ? tr("manage.added", added) : tr("manage.addedSkipped", added, skipped));
        show(page);
    }

    /** Перетягнуті файли: у теку відкритого розділу (Моди, Ресурспаки, Шейдери). */
    private final class DropFiles extends TransferHandler {
        @Override
        public boolean canImport(TransferSupport s) {
            return s.isDataFlavorSupported(DataFlavor.javaFileListFlavor)
                    && (current == MODS && !instance.isVanilla() && !closedPack() || current == RESOURCEPACKS && !packsLocked() || current == SHADERS);
        }

        @Override
        @SuppressWarnings("unchecked")
        public boolean importData(TransferSupport s) {
            if (!canImport(s)) return false;
            try {
                List<File> files = (List<File>) s.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);
                File content = instance.getContentDir();
                File dir = current == MODS ? InstanceMods.modsDir(instance)
                        : new File(content, current == RESOURCEPACKS ? "resourcepacks" : "shaderpacks");
                importFiles(files, dir, current);
                return true;
            } catch (Exception e) {
                return false;
            }
        }
    }

    // ------------------------------------------------------------------ дрібниці

    // ------------------------------------------------------------------ Імпорт/Експорт

    /** 19.11.2026: усе про файли збірок і чужі лаунчери в одному розділі (раніше «Поділитися» було в «Загальне»). */
    private JPanel impexPage() {
        JPanel p = page();
        p.add(shareCard());
        p.add(otherLaunchersCard());
        return p;
    }

    /** Імпорт збірок з Prism, CurseForge, ATLauncher, Modrinth App, офіційного лаунчера і TLauncher (ImportOtherDialog). */
    private JPanel otherLaunchersCard() {
        JPanel c = card();
        c.add(LookUi.title(tr("import.title"), 15f));
        c.add(LookUi.body(tr("import.hint"), true), "wmin 0");
        JButton open = LookUi.secondary(new JButton(tr("import.open")));
        open.putClientProperty("FlatLaf.styleClass", null);
        open.addActionListener(e -> ImportOtherDialog.show(this, launcher, name -> {
            if (host == null) return;
            if (name != null) {
                host.copyCreated(name); // головне вікно вибере нову збірку
                toast(tr("manage.share.imported"));
            } else {
                host.instanceChanged();
            }
        }));
        JPanel row = new JPanel(new MigLayout("insets 0, fillx", "[]push", "[34!]"));
        row.setOpaque(false);
        row.add(open, "h 34!");
        c.add(row, "gaptop 6");
        return c;
    }

    /** 17.11.2026: «Поділитися збіркою»: зберегти .mrpack (моди посиланнями на Modrinth) і відкрити чужий. */
    private JPanel shareCard() {
        JPanel c = card();
        c.add(LookUi.title(tr("manage.share.title"), 15f));
        boolean can = com.gummer.launcher.modrinth.MrpackExport.canExport(instance);
        // 20.11.2026: «не встановлено» лише коли справді не встановлено; встановлена, але без відомої версії завантажувача: noLoader
        c.add(LookUi.body(tr(can ? (instance.isVanilla() ? "manage.share.hintVanilla" : "manage.share.hint")
                : !(instance.isBuiltIn() || instance.isCopy()) ? "manage.share.notForModpacks"
                : !instance.isInstalled() ? "manage.share.notInstalled" : "manage.share.noLoader"), true), "wmin 0");
        if (can && !instance.isVanilla()) c.add(LookUi.body(tr("manage.share.zipHint"), true), "wmin 0, gaptop 4"); // 17.11.2026
        final JCheckBox packs = check(tr("manage.share.packs"), false);
        JPanel row = new JPanel(new MigLayout("insets 0, gap 8, fillx", "[][][]push", "[34!]"));
        row.setOpaque(false);
        if (can) {
            c.add(packs, "wmin 0, gaptop 4");
            JButton export = LookUi.primary(new JButton(tr("manage.share.export")));
            export.putClientProperty("FlatLaf.styleClass", null);
            export.addActionListener(e -> exportPack(packs.isSelected(), false));
            row.add(export, "h 34!");
            // 17.11.2026 (власник: «нехай гравці зможуть експортувати її і в зіпці»): усі моди всередині файлу
            JButton zip = LookUi.secondary(new JButton(tr("manage.share.exportZip")));
            zip.setToolTipText(tr("manage.share.zipTip"));
            zip.addActionListener(e -> exportPack(packs.isSelected(), true));
            if (!instance.isVanilla()) row.add(zip, "h 34!"); // 20.11.2026: у Vanilla модів немає, .zip був би тим самим файлом
        }
        JButton imp = LookUi.secondary(new JButton(tr("manage.share.import")));
        imp.addActionListener(e -> importPack());
        row.add(imp, "h 34!");
        c.add(row, "gaptop 6");
        return c;
    }

    private void exportPack(boolean packs, boolean zip) {
        String ext = zip ? "zip" : "mrpack";
        String def = com.gummer.launcher.modrinth.MrpackExport.fileNameOf(instance);
        if (zip && def.endsWith(".mrpack")) def = def.substring(0, def.length() - ".mrpack".length()) + ".zip";
        File f = new com.gummer.launcher.swing.NativeFileDialog(this).title(tr(zip ? "manage.share.exportZip" : "manage.share.export"))
                .filter(zip ? "ZIP (.zip)" : "Modrinth (.mrpack)", ext).fileName(def).saveFile();
        if (f == null) return;
        if (!f.getName().toLowerCase(Locale.ROOT).endsWith("." + ext)) f = new File(f.getPath() + "." + ext);
        final com.gummer.launcher.modrinth.MrpackExport exporter = new com.gummer.launcher.modrinth.MrpackExport(instance, f, packs, zip);
        exporter.launcher = launcher; // 25.11.2026: моди CurseForge посиланнями (запит до проксі)
        final com.google.common.util.concurrent.ListenableFuture<File> task = launcher.getExecutor().submit(exporter);
        com.skcraft.concurrency.ObservableFuture<File> future = new com.skcraft.concurrency.ObservableFuture<File>(task, exporter);
        com.skcraft.launcher.dialog.ProgressDialog.showProgress(this, future, tr("manage.share.exportTitle"), tr("manage.share.exporting"));
        SwingHelper.addErrorDialogCallback(this, future);
        future.addListener(() -> {
            try {
                File done = task.get();
                toast(zip ? tr("manage.share.exported", done.getName())
                        : tr("manage.share.exportedLinks", done.getName(), exporter.modrinthLinks, exporter.curseLinks));
                if (!exporter.skipped.isEmpty()) {
                    JOptionPane.showMessageDialog(this, tr("manage.share.skipped", String.join(", ", exporter.skipped)),
                            tr("manage.share.title"), JOptionPane.INFORMATION_MESSAGE);
                }
                SwingHelper.browseDir(done.getParentFile(), this);
            } catch (Exception ignored) {
                // помилку показав addErrorDialogCallback
            }
        }, com.skcraft.launcher.util.SwingExecutor.INSTANCE);
    }

    /** Імпорт .mrpack: нова збірка, головне вікно її вибере (той самий код, що «Модпаки»). */
    private void importPack() {
        final File file = new com.gummer.launcher.swing.NativeFileDialog(this).title(tr("mrpack.chooseTitle"))
                .filter("Modrinth, CurseForge (.mrpack, .zip)", "mrpack", "zip").openFile();
        if (file == null) return;
        final com.gummer.launcher.modrinth.MrpackPacks importer = new com.gummer.launcher.modrinth.MrpackPacks();
        final com.google.common.util.concurrent.ListenableFuture<String> task = launcher.getExecutor().submit(() -> importer.importFile(launcher, file));
        com.skcraft.concurrency.ObservableFuture<String> future = new com.skcraft.concurrency.ObservableFuture<String>(task, importer);
        com.skcraft.launcher.dialog.ProgressDialog.showProgress(this, future, tr("mrpack.progressTitle"), tr("mrpack.progressText", file.getName()));
        SwingHelper.addErrorDialogCallback(this, future);
        future.addListener(() -> {
            try {
                String name = task.get();
                if (host != null) host.copyCreated(name);
                toast(tr("manage.share.imported"));
            } catch (Exception ignored) {
                // помилку показав addErrorDialogCallback
            }
        }, com.skcraft.launcher.util.SwingExecutor.INSTANCE);
    }

    private void toast(String text) {
        showNotification(LauncherFrameFlat.rootFrame, LauncherFrameFlat.class, Notification.Type.SUCCESS, text);
    }

    private JButton folderButton(String text, final File dir) {
        JButton b = LookUi.secondary(new JButton(text));
        b.addActionListener(e -> {
            //noinspection ResultOfMethodCallIgnored
            dir.mkdirs();
            SwingHelper.browseDir(dir, this);
        });
        return b;
    }

    /** Картка «Відеокарта»: як самому дати грі потужну відеокарту (Gpu), видно лише на ПК з двома відеокартами. */
    private JPanel gpuCard() {
        final JPanel c = card();
        c.setVisible(false);
        // 24.11.2026: список відеокарт лаунчер читає у фоні на старті; якщо він уже є, картка будується одразу (без
        // появи за мить після відкриття розділу і без зсуву сторінки), інакше, як раніше, у фоні
        if (com.gummer.launcher.util.Gpu.adaptersReady()) {
            String d = com.gummer.launcher.util.Gpu.discreteOfHybrid();
            fillGpuCard(c, d == null ? null : new String[]{d, String.valueOf(com.gummer.launcher.util.Gpu.integratedOfHybrid())}, false);
            return c;
        }
        new SwingWorker<String[], Void>() {
            @Override
            protected String[] doInBackground() {
                String d = com.gummer.launcher.util.Gpu.discreteOfHybrid();
                return d == null ? null : new String[]{d, String.valueOf(com.gummer.launcher.util.Gpu.integratedOfHybrid())};
            }

            @Override
            protected void done() {
                String[] gpus;
                try {
                    gpus = get();
                } catch (Exception e) {
                    gpus = null;
                }
                fillGpuCard(c, gpus, true);
            }
        }.execute();
        return c;
    }

    /** Вміст картки «Відеокарта»; late: картка з'являється вже після першої розкладки сторінки. */
    private void fillGpuCard(final JPanel c, String[] gpus, boolean late) {
                if (gpus == null || !com.gummer.launcher.util.Gpu.choiceSupported()) return;
                boolean win = com.skcraft.launcher.util.Environment.getInstance().getPlatform()
                        == com.skcraft.launcher.util.Platform.WINDOWS;
                c.add(LookUi.title(tr("manage.gpu.title"), 15f));
                c.add(LookUi.body(tr("manage.gpu.pick"), true), "wmin 0");
                // 19.11.2026 (власник: «показувати гравцю які в нього є відеокарти щоб він між ними міг перемикатись?
                // І в дужках "... (інтегрована) / ... (дискретна)"», і раніше: «прозоро і чесно»): три варіанти,
                // що саме робить вибір, написано під ними
                final int current = com.gummer.launcher.util.Gpu.choice(instance);
                final ButtonGroup group = new ButtonGroup();
                String integratedName = "null".equals(gpus[1]) ? tr("manage.gpu.integratedUnknown") : gpus[1];
                String[] labels = {tr("manage.gpu.auto"), tr("manage.gpu.integrated", integratedName),
                        tr("manage.gpu.discrete", gpus[0])};
                int[] values = {com.gummer.launcher.util.Gpu.AUTO, com.gummer.launcher.util.Gpu.INTEGRATED_GPU,
                        com.gummer.launcher.util.Gpu.DISCRETE_GPU};
                for (int k = 0; k < labels.length; k++) {
                    final int value = values[k];
                    final JRadioButton rb = radio(labels[k], value == current);
                    group.add(rb);
                    c.add(rb, (k == 0 ? "gaptop 6, " : "") + "wmin 0");
                    rb.addActionListener(e -> {
                        if (com.gummer.launcher.util.Gpu.choice(instance) == value) return;
                        com.gummer.launcher.util.Gpu.setChoice(instance, value);
                        Persistence.commitAndForget(instance);
                        toast(tr("manage.gpu.saved"));
                        // «Як вирішить система»: записане лаунчером прибрати одразу, не чекаючи запуску
                        if (value == com.gummer.launcher.util.Gpu.AUTO) launcher.getExecutor().submit(
                                () -> com.gummer.launcher.util.Gpu.forget(launcher.getBaseDir(), instance));
                    });
                }
                c.add(LookUi.body(tr(win ? "manage.gpu.howWin" : "manage.gpu.howLinux"), true), "wmin 0, gaptop 4");
                if (!win) {
                    c.setVisible(true);
                    c.revalidate();
                    return;
                }
                c.add(LookUi.body(tr("manage.gpu.manual"), true), "wmin 0, gaptop 6");
                JButton open = LookUi.secondary(new JButton(tr("manage.gpu.open")));
                open.putClientProperty("FlatLaf.styleClass", null);
                JButton copy = LookUi.secondary(new JButton(tr("manage.gpu.copy")));
                copy.putClientProperty("FlatLaf.styleClass", null);
                final JTextArea note = LookUi.body(" ", true);
                note.setVisible(false);
                open.addActionListener(e -> {
                    if (!com.gummer.launcher.util.Gpu.openGraphicsSettings()) {
                        note.setText(tr("manage.gpu.openFailed"));
                        note.setVisible(true);
                        c.revalidate();
                    }
                });
                copy.addActionListener(e -> {
                    File exe = com.gummer.launcher.util.Gpu.lastJava(instance);
                    if (exe == null) {
                        note.setText(tr("manage.gpu.notYet"));
                    } else {
                        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(
                                new java.awt.datatransfer.StringSelection(exe.getAbsolutePath()), null);
                        note.setText(tr("manage.gpu.copied", exe.getAbsolutePath()));
                    }
                    note.setVisible(true);
                    c.revalidate();
                });
                JPanel row = new JPanel(new MigLayout("insets 0, gap 8", "[][]push", "[32!]"));
                row.setOpaque(false);
                row.add(open, "h 32!");
                row.add(copy, "h 32!");
                c.add(row, "gaptop 6, wmin 0"); // 24.11.2026: як ряд картки FPS
                c.add(note, "wmin 0, hidemode 3");
                c.setVisible(true);
                c.revalidate();
                // 24.11.2026: картка з'являється вже після першої розкладки; поява смуги прокрутки звужує сторінку, а
                // тексти з переносом сусідньої картки FPS лишались старої ширини і вилазили за край
                if (late) relayoutLater(c);
    }

    /** 19.11.2026: перемикач з таким самим видом кружечка, як квадратик {@link #check} (ті самі ключі FlatLaf). */
    private JRadioButton radio(String text, boolean selected) {
        JRadioButton b = new JRadioButton(text, selected);
        b.setOpaque(false);
        b.setForeground(pal.text);
        b.setFocusPainted(false);
        b.putClientProperty("FlatLaf.style", "icon.borderWidth: 1; icon.borderColor: " + LookUi.hex(pal.text3)
                + "; icon.background: " + LookUi.hex(pal.surface2)
                + "; icon.selectedBorderColor: " + LookUi.hex(pal.accent)
                + "; icon.selectedBackground: " + LookUi.hex(pal.accent)
                + "; icon.centerDiameter: 7; icon.checkmarkColor: #FFFFFF"
                + "; disabledText: " + LookUi.hex(pal.text3));
        return b;
    }

    private JCheckBox check(String text, boolean selected) {
        JCheckBox b = new JCheckBox(text, selected);
        b.setOpaque(false);
        b.setForeground(pal.text);
        b.setFocusPainted(false);
        // порожній квадратик має бути видно на темному (у склі він зливався з фоном, і вимкнений мод був «без галочки»)
        // 17.11.2026: тема дає рамці товщину 0, і колір рамки нічого не важив: у склі квадратик був ледь темнішою
        // плямою, а на світлішому фоні копії зникав зовсім
        b.putClientProperty("FlatLaf.style", "icon.borderWidth: 1; icon.borderColor: " + LookUi.hex(pal.text3)
                + "; icon.background: " + LookUi.hex(pal.surface2)
                + "; icon.selectedBorderColor: " + LookUi.hex(pal.accent)
                + "; icon.selectedBackground: " + LookUi.hex(pal.accent)
                + "; icon.checkmarkColor: #FFFFFF"
                + "; icon.disabledBorderColor: " + LookUi.hex(pal.line)
                + "; icon.disabledBackground: " + LookUi.hex(pal.surface)
                + "; icon.disabledCheckmarkColor: " + LookUi.hex(pal.text3)
                + "; disabledText: " + LookUi.hex(pal.text3));
        return b;
    }

    /** 24.11.2026: червоне попередження під модом чи ресурспаком X-ray (текст browse.xray), з переносом у вузькому вікні. */
    static JTextArea xrayWarning() {
        JTextArea l = LookUi.body(tr("browse.xray"), false);
        l.setForeground(LookTheme.palette().danger);
        l.setFont(l.getFont().deriveFont(Font.BOLD, 12f));
        return l;
    }

    private JLabel small(String text) {
        JLabel l = new JLabel(text);
        l.setForeground(pal.text3);
        l.setFont(l.getFont().deriveFont(12f));
        return l;
    }

    private static JPanel rowPanel() {
        JPanel r = new JPanel(new MigLayout("insets 2 0 2 0, gap 0, fillx", "[grow,fill][]", "[]"));
        r.setOpaque(false);
        return r;
    }

    private static JPanel column() {
        JPanel c = new JPanel(new MigLayout("insets 0, wrap 1, gap 0 2, fillx", "[grow,fill]", ""));
        c.setOpaque(false);
        return c;
    }

    private static JPanel card() {
        return LookUi.card("insets 16 18 16 18, wrap 1, gap 0 4, fillx", "[grow,fill]", "");
    }

    private static JPanel page() {
        JPanel p = new JPanel(new MigLayout("insets 0 0 14 4, wrap 1, gap 0 12, fillx", "[grow,fill]", ""));
        p.setOpaque(false);
        return p;
    }

    private static String size(long bytes) {
        if (bytes < 1024 * 1024) return String.format(Locale.ROOT, "%d KB", Math.max(1, bytes / 1024));
        return String.format(Locale.ROOT, "%.1f MB", bytes / (1024.0 * 1024.0));
    }

    private static JScrollPane scroll(JPanel page) {
        JPanel track = new Track();
        track.add(page, BorderLayout.NORTH);
        JScrollPane sp = new JScrollPane(track, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        sp.setBorder(null);
        sp.setOpaque(false);
        sp.getViewport().setOpaque(false);
        sp.getVerticalScrollBar().setUnitIncrement(18);
        return sp;
    }

    /** Сторінка тягнеться по ширині за вікном, прокрутка лише вертикальна. */
    private static final class Track extends JPanel implements Scrollable {
        Track() {
            super(new BorderLayout());
            setOpaque(false);
        }

        @Override
        public Dimension getPreferredScrollableViewportSize() {
            // 24.11.2026: не міряти всю сторінку (див. pageHost): розмір видимої частини задає вікно
            return new Dimension(100, 100);
        }

        @Override
        public int getScrollableUnitIncrement(Rectangle r, int o, int d) {
            return 18;
        }

        @Override
        public int getScrollableBlockIncrement(Rectangle r, int o, int d) {
            return r.height - 36;
        }

        @Override
        public boolean getScrollableTracksViewportWidth() {
            return true;
        }

        @Override
        public boolean getScrollableTracksViewportHeight() {
            return false;
        }
    }
}
