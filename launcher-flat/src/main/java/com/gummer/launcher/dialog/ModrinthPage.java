/*
 * GummerCraft Launcher
 * Copyright (C) 2026 GummerCraft <http://gummercraft.fun>
 * Please see LICENSE.txt for license information.
 */

package com.gummer.launcher.dialog;

import com.gummer.launcher.look.LookTheme;
import com.gummer.launcher.look.LookUi;
import com.gummer.launcher.manage.ModrinthBrowse;
import com.gummer.launcher.popup.Notification;
import com.skcraft.launcher.Launcher;
import com.skcraft.launcher.swing.SwingHelper;
import net.miginfocom.swing.MigLayout;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.skcraft.launcher.util.SharedLocale.tr;

/**
 * 17.11.2026 (власник: «окрема іконка модрінт. гравець на неї тисне та прямо там, де бекграунд у інших версіях та
 * модпаках, гравець зможе подивитись усі модпаки з модрінт та обравши версію поставити її у відповідний лоадер, і вона
 * з'явиться у випадаючому меню»). Сторінка модпаків Modrinth на місці головної області вікна (рядок «Modrinth» зліва):
 * пошук, фільтр завантажувача і версії гри, у кожного модпака список версій, які лаунчер уміє ставити, і «Встановити».
 * Модпак стає збіркою {@code mrpack-*} у виборі версії свого завантажувача (InstanceList, InstanceCopies.sectionOf).
 * Мережа лише в пулі Modrinth ({@link ModrinthBrowse#POOL}), потік інтерфейсу не чекає.
 *
 * <p>17.11.2026 (власник: «розділи це на модпаки та моди - щоб гравець і моди міг качати прямо з Modrinth ... і обирати,
 * в який вже скачаний модпак їх встановити (який підходить по лоадеру та версії), або створити новий модпак з цим
 * модом»; «сортування, фільтри за категоріями, на встановленому модпаку "Грати"»): вкладки «Модпаки» і «Моди»,
 * порядок (популярні, нові, оновлені), категорія; у мода «Встановити» зі списком збірок, куди він підходить, і «Нова
 * збірка з цим модом»; у встановленого модпака «Грати» і, якщо вийшла новіша версія, «Оновити до ...».</p>
 *
 * <p>1.40.0 (24.11.2026, власник: «зроби тут вкладку CurseForge і скачку модпаків з курсфордж точно як з модрінта»):
 * та сама сторінка в режимі CurseForge ({@code curse}): лише модпаки, категорії CurseForge, пошук і версії через наш
 * проксі (curseforge.CurseBrowse), «Встановити» ставить zip-збірку CurseForge. Оновлення встановленого модпака
 * CurseForge поки немає (MrpackUpdate вміє лише Modrinth).</p>
 *
 * <p>24.11.2026 (власник: «модпаки одним кліком - курсфордж працювали так само як модрінт - мали вкладку і модпаки, і
 * моди»): у режимі CurseForge теж вкладка «Моди» з тими самими «Встановити в збірку» і «Нова збірка з цим модом»
 * (curseforge.CurseBrowse.modVersions і installFile), категорії свого виду.</p>
 */
public final class ModrinthPage extends JPanel {

    private static final Logger log = Logger.getLogger(ModrinthPage.class.getName());
    private static final int ICON = 48;
    private static final String[] LOADERS = {null, "fabric", "forge", "neoforge", "quilt"};
    /** Скільки версій показати одразу (решта за «Показати всі»). */
    private static final int FIRST_VERSIONS = 6;

    private final Launcher launcher;
    /** 1.40.0: сторінка CurseForge, інакше Modrinth. */
    private final boolean curse;
    /** Категорії CurseForge {id, назва} для поточної вкладки, довантажуються при її першому показі. */
    private List<Object[]> curseCategories = new java.util.ArrayList<Object[]>();
    /** Модпак встановлено: назва нової збірки (вікно перечитує список і вибирає її). */
    private final Consumer<String> installed;
    /** «Грати»: назва збірки, яку вибрати і запустити. */
    private final Consumer<String> play;
    private ModrinthBrowse.Kind kind = ModrinthBrowse.Kind.MODPACK;
    private final JLabel title = LookUi.title(tr("mpage.title"), 22f);
    /**
     * 20.11.2026 (власник: «Де кнопка зміни дизайну?»): місце праворуч у шапці. Бібліотека переносить сюди «Вигляд» з
     * лайком, бо сторінка закриває героя, де вони живуть; у Склі та Класиці порожнє (там «Вигляд» у бічній панелі).
     */
    private final JPanel headerSlot = new JPanel(new BorderLayout());

    public JPanel headerSlot() {
        return headerSlot;
    }
    private final WrapText hint;
    private final JComboBox<String> categoryBox = new JComboBox<String>();
    private final JComboBox<String> sortBox = new JComboBox<String>();
    private String[] categories = ModrinthBrowse.MODPACK_CATEGORIES;
    private boolean adjusting;

    private final JTextField search = new JTextField();
    private final JComboBox<String> loaderBox = new JComboBox<String>();
    private final JComboBox<String> gameBox = new JComboBox<String>();
    // 17.11.2026: список тягнеться рівно по ширині видимої області (Scrollable), тож висота описів рахується від
    // справжньої ширини (див. WrapText)
    private final JPanel results = new WidthTrack(new MigLayout("insets 0 0 8 0, gap 0 8, wrap 1, fillx", "[grow,fill]", ""));
    private final JScrollPane scroll;
    private final JLabel status = new JLabel(" ");
    private final JButton more = LookUi.secondary(new JButton(tr("manage.browse.more")));
    private final Timer debounce;

    private int generation;
    private int offset;
    private boolean loadedOnce;
    private boolean gamesLoaded;
    private boolean busy;

    public ModrinthPage(Launcher launcher, Consumer<String> installed, Consumer<String> play) {
        this(launcher, false, installed, play);
    }

    public ModrinthPage(Launcher launcher, boolean curse, Consumer<String> installed, Consumer<String> play) {
        // рядки: заголовок, підказка, фільтри, «Знайдено», результати. 17.11.2026: після появи підказки розтягувався рядок
        // «Знайдено» (на весь екран «Знайдено: 1» посередині, картки внизу), тепер росте лише список результатів
        super(new MigLayout("insets 20 22 14 18, gap 0, wrap 1, fill", "[grow,fill]", "[][]12[]8[][grow,fill]"));
        this.launcher = launcher;
        this.curse = curse;
        this.installed = installed;
        this.play = play;
        setOpaque(false);
        LookTheme.Palette p = LookTheme.palette();

        JPanel top = new JPanel(new MigLayout("insets 0, gap 16", "[][]push[]", "[]"));
        top.setOpaque(false);
        if (curse) title.setText(tr("cpage.title"));
        top.add(title);
        com.gummer.launcher.look.ListModeSwitch tabs = new com.gummer.launcher.look.ListModeSwitch(
                tr("mpage.tab.packs"), tr("mpage.tab.mods"), false, mods -> setKind(mods ? ModrinthBrowse.Kind.MOD : ModrinthBrowse.Kind.MODPACK));
        top.add(tabs, "aligny center, w 220!, h 30!");
        headerSlot.setOpaque(false);
        top.add(headerSlot, "aligny center");
        add(top, "wmin 0");
        hint = new WrapText(tr(curse ? "cpage.hint" : "mpage.hint")); // 17.11.2026: у Класиці звичайний текст обрізався збоку
        add(hint, "gaptop 4, wmin 0");

        // 17.11.2026: з категоріями і сортуванням рядок став ширшим за сторінку (сторінка виїжджала за край, обрізались
        // «Популярні» і підказка): пошук тепер стискається, списки заповнюють свої колонки (без дірок між ними), ширина
        // списку за його найдовшим пунктом, але не більше 150-170 (раніше фіксовані 130 обрізали «Усі завантажувачі»)
        JPanel filters = new JPanel(new MigLayout("insets 0, gap 8", "[grow,fill,100::][pref:pref:170,fill][pref:pref:150,fill][pref:pref:170,fill][pref:pref:170,fill]", "[34!]"));
        filters.setOpaque(false);
        search.putClientProperty("JTextField.placeholderText", tr(curse ? "cpage.placeholder" : "mpage.placeholder"));
        search.putClientProperty("JTextField.showClearButton", true);
        filters.add(search, "wmin 100");
        loaderBox.addItem(tr("mpage.allLoaders"));
        for (int i = 1; i < LOADERS.length; i++) loaderBox.addItem(loaderTitle(LOADERS[i]));
        loaderBox.setFocusable(false);
        filters.add(loaderBox, "wmin 60"); // інакше мінімум = найдовший пункт, і список виїжджає за рядок
        gameBox.addItem(tr("mpage.allGames"));
        gameBox.setFocusable(false);
        gameBox.setMaximumRowCount(16);
        filters.add(gameBox, "wmin 60"); // інакше мінімум = найдовший пункт, і список виїжджає за рядок
        fillCategories();
        categoryBox.setFocusable(false);
        categoryBox.setMaximumRowCount(20);
        filters.add(categoryBox, "wmin 60"); // інакше мінімум = найдовший пункт, і список виїжджає за рядок
        sortBox.addItem(tr("mpage.sort.best"));
        sortBox.addItem(tr("mpage.sort.new"));
        sortBox.addItem(tr("mpage.sort.updated"));
        sortBox.setFocusable(false);
        filters.add(sortBox, "wmin 60"); // інакше мінімум = найдовший пункт, і список виїжджає за рядок
        add(filters, "wmin 0");

        status.setForeground(p.text3);
        add(status);

        results.setOpaque(false);
        scroll = new JScrollPane(results, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.setViewportBorder(null);
        scroll.getVerticalScrollBar().setUnitIncrement(24);
        scroll.getVerticalScrollBar().putClientProperty("FlatLaf.style", "width: 8; thumbArc: 999; trackArc: 999");
        add(scroll, "grow, hmin 0");

        more.addActionListener(e -> fetch(generation, offset));

        debounce = new Timer(400, e -> reload());
        debounce.setRepeats(false);
        search.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                debounce.restart();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                debounce.restart();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
            }
        });
        search.addActionListener(e -> {
            debounce.stop();
            reload();
        });
        loaderBox.addActionListener(e -> reload());
        gameBox.addActionListener(e -> {
            if (gamesLoaded) reload();
        });
        categoryBox.addActionListener(e -> {
            if (!adjusting) reload();
        });
        sortBox.addActionListener(e -> reload());
    }

    /** Вкладка «Модпаки» чи «Моди»: свій заголовок, підказка, категорії; пошук і фільтри лишаються. */
    private void setKind(ModrinthBrowse.Kind k) {
        if (k == kind) return;
        kind = k;
        boolean mods = k == ModrinthBrowse.Kind.MOD;
        String p = curse ? "cpage." : "mpage.";
        title.setText(tr(p + (mods ? "titleMods" : "title")));
        hint.setText(tr((mods ? "mpage." : p) + (mods ? "hintMods" : "hint"))); // підказка модів однакова
        search.putClientProperty("JTextField.placeholderText", tr(mods ? "mpage.placeholderMods" : p + "placeholder"));
        search.repaint();
        categories = mods ? ModrinthBrowse.MOD_CATEGORIES : ModrinthBrowse.MODPACK_CATEGORIES;
        if (curse) {
            curseCategories = new java.util.ArrayList<Object[]>();
            loadCurseCategories(k);
        }
        fillCategories();
        reload();
    }

    /** Категорії CurseForge цього виду у фоні; поки вкладка та сама, список оновлюється. */
    private void loadCurseCategories(final ModrinthBrowse.Kind k) {
        ModrinthBrowse.POOL.submit(() -> {
            try {
                final List<Object[]> cats = com.gummer.launcher.curseforge.CurseBrowse.categories(launcher, k);
                SwingUtilities.invokeLater(() -> {
                    if (k != kind) return;
                    curseCategories = cats;
                    fillCategories();
                });
            } catch (Exception e) {
                log.log(Level.INFO, "No CurseForge categories", e);
            }
        });
    }

    private void fillCategories() {
        adjusting = true;
        try {
            categoryBox.removeAllItems();
            categoryBox.addItem(tr("mpage.cat.all"));
            if (curse) {
                for (Object[] c : curseCategories) categoryBox.addItem((String) c[1]);
            } else {
                for (String c : categories) categoryBox.addItem(tr("mpage.cat." + c));
            }
            categoryBox.setSelectedIndex(0);
        } finally {
            adjusting = false;
        }
    }

    /** 1.40.0: категорія CurseForge (id), або null. */
    private Integer curseCategoryFilter() {
        int i = categoryBox.getSelectedIndex();
        return i > 0 && i - 1 < curseCategories.size() ? (Integer) curseCategories.get(i - 1)[0] : null;
    }

    private String categoryFilter() {
        int i = categoryBox.getSelectedIndex();
        return i > 0 && i - 1 < categories.length ? categories[i - 1] : null;
    }

    private ModrinthBrowse.Sort sortFilter() {
        int i = sortBox.getSelectedIndex();
        return i == 1 ? ModrinthBrowse.Sort.NEWEST : i == 2 ? ModrinthBrowse.Sort.UPDATED : ModrinthBrowse.Sort.BEST;
    }

    /** Сторінку показано: перший раз завантажити найпопулярніші модпаки і список версій гри. */
    public void shown() {
        if (loadedOnce) {
            // 18.11.2026 (власник: «після видалення збірки в модрінті вона залишилась як "встановлено"»): позначка
            // рахувалась лише при пошуку. Тепер при кожному поверненні на сторінку вона звіряється з диском у фоні, і якщо
            // модпак видалили чи поставили, сторінка перешукується
            final java.util.List<ModrinthBrowse.Hit> hits = new java.util.ArrayList<ModrinthBrowse.Hit>();
            for (Component c : results.getComponents()) if (c instanceof Card && ((Card) c).cardKind == ModrinthBrowse.Kind.MODPACK) hits.add(((Card) c).hit);
            if (hits.isEmpty()) return;
            ModrinthBrowse.POOL.submit(() -> {
                for (ModrinthBrowse.Hit h : hits) {
                    if (h.installed != !com.gummer.launcher.modrinth.MrpackPacks.dirsOfProject(launcher, h.id).isEmpty()) {
                        SwingUtilities.invokeLater(this::reload);
                        return;
                    }
                }
            });
            return;
        }
        loadedOnce = true;
        reload();
        if (curse) loadCurseCategories(kind);
        ModrinthBrowse.POOL.submit(() -> {
            try {
                List<com.gummer.launcher.vanilla.VanillaVersions.Release> all = com.gummer.launcher.vanilla.VanillaVersions.releases(launcher);
                SwingUtilities.invokeLater(() -> {
                    for (com.gummer.launcher.vanilla.VanillaVersions.Release r : all) {
                        if (!r.museum && !r.snapshot) gameBox.addItem(r.id);
                    }
                    gamesLoaded = true;
                });
            } catch (Exception e) {
                log.log(Level.INFO, "No game version list for the Modrinth page", e);
                SwingUtilities.invokeLater(() -> gamesLoaded = true);
            }
        });
    }

    private String loaderFilter() {
        int i = loaderBox.getSelectedIndex();
        return i > 0 && i < LOADERS.length ? LOADERS[i] : null;
    }

    private String gameFilter() {
        return gameBox.getSelectedIndex() > 0 ? (String) gameBox.getSelectedItem() : null;
    }

    private void reload() {
        generation++;
        offset = 0;
        results.removeAll();
        results.revalidate();
        results.repaint();
        scroll.getVerticalScrollBar().setValue(0);
        fetch(generation, 0);
    }

    private void fetch(final int gen, final int from) {
        final String query = search.getText();
        final String loader = loaderFilter();
        final String game = gameFilter();
        final String category = curse ? null : categoryFilter();
        final Integer curseCat = curse ? curseCategoryFilter() : null;
        final ModrinthBrowse.Sort sort = sortFilter();
        final ModrinthBrowse.Kind k = kind;
        status.setText(tr("manage.browse.loading"));
        more.setEnabled(false);
        ModrinthBrowse.POOL.submit(() -> {
            try {
                final ModrinthBrowse.Page page = curse
                        ? com.gummer.launcher.curseforge.CurseBrowse.search(launcher, k, query, game, loader, curseCat,
                        sort == ModrinthBrowse.Sort.NEWEST ? com.gummer.launcher.curseforge.CurseBrowse.Sort.NEWEST
                                : sort == ModrinthBrowse.Sort.UPDATED ? com.gummer.launcher.curseforge.CurseBrowse.Sort.UPDATED
                                : com.gummer.launcher.curseforge.CurseBrowse.Sort.BEST, from)
                        : ModrinthBrowse.search(k, query, game,
                        loader != null ? new String[]{loader} : null, from, ModrinthBrowse.Filter.POPULAR, sort, category);
                boolean iconAdded = false;
                for (ModrinthBrowse.Hit h : page.hits) {
                    if (k != ModrinthBrowse.Kind.MODPACK) continue;
                    java.util.List<java.io.File> dirs = com.gummer.launcher.modrinth.MrpackPacks.dirsOfProject(launcher, h.id);
                    h.installed = !dirs.isEmpty();
                    // 17.11.2026: збірки, поставлені до цієї версії, теж отримують іконку свого модпака
                    for (java.io.File d : dirs) {
                        // 17.11.2026 ~23:15: і фон зі скріна галереї модпака (раз, якщо фону ще немає)
                        if (h.galleryUrl != null && !new java.io.File(d, com.gummer.launcher.manage.InstanceArt.BACKGROUND).isFile()) {
                            ModrinthBrowse.savePackBackground(d, h);
                            iconAdded = true;
                        }
                        if (new java.io.File(d, com.gummer.launcher.modrinth.MrpackPacks.PACK_ICON).isFile()) continue;
                        ModrinthBrowse.savePackIcon(d, h);
                        iconAdded = true;
                    }
                }
                if (iconAdded) SwingUtilities.invokeLater(() -> {
                    for (java.awt.Frame f : java.awt.Frame.getFrames()) f.repaint();
                });
                SwingUtilities.invokeLater(() -> {
                    if (gen != generation || k != kind) return; // гравець уже шукає інше
                    show(page, from);
                });
            } catch (Exception e) {
                log.log(Level.INFO, (curse ? "CurseForge" : "Modrinth") + " modpack search failed", e);
                SwingUtilities.invokeLater(() -> {
                    if (gen != generation) return;
                    status.setText(tr(curse ? "manage.browse.failedCurse" : "manage.browse.failed"));
                    more.setEnabled(true);
                });
            }
        });
    }

    private void show(ModrinthBrowse.Page page, int from) {
        results.remove(more);
        for (ModrinthBrowse.Hit h : page.hits) results.add(new Card(h, kind));
        offset = from + page.hits.size();
        // 24.11.2026 (власник: «чому знайдено лише 10000 модпаків курсфордж?»): CurseForge більше 10000 не рахує
        status.setText(page.total == 0 ? tr("manage.browse.nothing")
                : curse && page.total >= com.gummer.launcher.curseforge.CurseBrowse.MAX_RESULTS ? tr("cpage.foundMany")
                : tr("manage.browse.found", page.total));
        if (offset < page.total && !page.hits.isEmpty()) {
            more.setEnabled(true);
            results.add(more, "growx 0, alignx center, gaptop 4");
        }
        results.revalidate();
        results.repaint();
    }

    static String loaderTitle(String loader) {
        if (loader == null || loader.isEmpty()) return "?";
        if (loader.equals("neoforge")) return "NeoForge";
        return Character.toUpperCase(loader.charAt(0)) + loader.substring(1);
    }

    private static String downloads(long n) {
        if (n >= 1_000_000) return String.format(Locale.ROOT, "%.1f M", n / 1_000_000.0);
        if (n >= 1_000) return String.format(Locale.ROOT, "%.0f K", n / 1_000.0);
        return String.valueOf(n);
    }

    @Override
    protected void paintComponent(Graphics g) {
        // підкладка, щоб текст читався на будь-якому фоні; у Liquid Glass фон вікна трохи видно
        LookTheme.Palette p = LookTheme.palette();
        Graphics2D g2 = (Graphics2D) g.create();
        Color base = p.bg;
        g2.setColor(p.glass() ? new Color(8, 10, 14, 175) : new Color(base.getRed(), base.getGreen(), base.getBlue(), 236));
        g2.fillRect(0, 0, getWidth(), getHeight());
        g2.dispose();
    }

    /** Список карток: ширина як у видимої області, прокрутка лише вертикальна. */
    private static final class WidthTrack extends JPanel implements Scrollable {
        WidthTrack(LayoutManager layout) {
            super(layout);
        }

        @Override
        public Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }

        @Override
        public int getScrollableUnitIncrement(Rectangle r, int o, int d) {
            return 24;
        }

        @Override
        public int getScrollableBlockIncrement(Rectangle r, int o, int d) {
            return Math.max(24, r.height - 48);
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

    /**
     * 17.11.2026 (власник: «модпакам явно не потрібно таке велике вікно, доки гравець не натиснув "версії". А коли
     * натиснув, вікно кудись втікає»). Опис модпака: JTextArea з переносом рахує висоту від ширини, яку мав під час
     * останньої розкладки, а нова картка спершу має ширину 0 чи випадкову. Від того картка бувала на весь екран, а при
     * наступному перерахунку (кнопка «Версії») всі картки над нею міняли висоту, і список з'їжджав. Тут висота завжди
     * від справжньої ширини (або ширини списку, поки її немає), і зміна ширини одразу перераховує розкладку.
     */
    private final class WrapText extends JTextArea {
        private int laidOutWidth = -1;

        WrapText(String text) {
            // порожні рядки в описах деяких модпаків робили картку вдвічі вищою: усе в один абзац
            super(LookUi.plainText(LookUi.stripHtml(text == null ? "" : text)).replaceAll("\\s*\\n\\s*", " ").trim());
            setLineWrap(true);
            setWrapStyleWord(true);
            setEditable(false);
            setFocusable(false);
            setOpaque(false);
            setBorder(null);
            if (getCaret() instanceof javax.swing.text.DefaultCaret) {
                ((javax.swing.text.DefaultCaret) getCaret()).setUpdatePolicy(javax.swing.text.DefaultCaret.NEVER_UPDATE);
            }
            setFont(UIManager.getFont("Label.font").deriveFont(12.5f));
            setForeground(LookTheme.palette().text3);
        }

        @Override
        public void scrollRectToVisible(Rectangle r) {
        }

        /**
         * Запас праворуч: перенос рахується метриками шрифту компонента, а малюється текст з іншими (згладжування,
         * масштаб), і рядок, що «влазив», виходив за край на 2-3 % (у Класиці обрізалось «...під своєю назв»).
         */
        @Override
        public Insets getInsets() {
            Insets i = super.getInsets();
            return new Insets(i.top, i.left, i.bottom, i.right + Math.max(8, getWidth() / 33));
        }

        private int heightFor(int w) {
            javax.swing.text.View root = getUI().getRootView(this);
            Insets in = getInsets();
            root.setSize(Math.max(1, w - in.left - in.right), Integer.MAX_VALUE / 2);
            return (int) Math.ceil(root.getPreferredSpan(javax.swing.text.View.Y_AXIS)) + in.top + in.bottom;
        }

        @Override
        public Dimension getPreferredSize() {
            int w = getWidth() > 0 ? getWidth() : Math.max(200, results.getWidth() - 90);
            return new Dimension(40, heightFor(w));
        }

        @Override
        public Dimension getMinimumSize() {
            return new Dimension(0, getPreferredSize().height);
        }

        @Override
        public void setBounds(int x, int y, int w, int h) {
            super.setBounds(x, y, w, h);
            if (w != laidOutWidth) {
                laidOutWidth = w;
                if (w > 0 && heightFor(w) != h) SwingUtilities.invokeLater(this::revalidate);
            }
        }
    }

    /** Іконка модпака: з пам'яті одразу, інакше довантажується в пулі Modrinth. */
    private static final class PackIcon extends JComponent {
        private Image image;
        private final String letter;

        PackIcon(Image image, String title) {
            this.image = image;
            this.letter = title == null || title.isEmpty() ? "?" : title.substring(0, 1).toUpperCase(Locale.ROOT);
        }

        void set(Image image) {
            this.image = image;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            int s = Math.min(getWidth(), getHeight());
            // 17.11.2026: clip, а не setClip: setClip скидав межу прокрутки, і іконка картки під низом списку
            // малювалась поверх вікна («запікалась»)
            g2.clip(new java.awt.geom.RoundRectangle2D.Float(0, 0, s, s, 12, 12));
            if (image != null) {
                g2.drawImage(image, 0, 0, s, s, null);
            } else {
                LookTheme.Palette p = LookTheme.palette();
                g2.setColor(p.surface3);
                g2.fillRect(0, 0, s, s);
                g2.setColor(p.text2);
                g2.setFont(getFont().deriveFont(Font.BOLD, s / 2f));
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString(letter, (s - fm.stringWidth(letter)) / 2, (s - fm.getHeight()) / 2 + fm.getAscent());
            }
            g2.dispose();
        }
    }

    /** Картка модпака: іконка, назва, автор і завантаження, опис; «Версії» розгортає список версій з «Встановити». */
    private final class Card extends JPanel {
        private final ModrinthBrowse.Hit hit;
        private final ModrinthBrowse.Kind cardKind;
        private final JPanel versions = new JPanel(new MigLayout("insets 6 0 0 0, gap 0 4, wrap 1, fillx", "[grow,fill]", ""));
        private final JButton versionsButton = LookUi.secondary(new JButton(tr("mpage.versions")));
        private final JPanel actions = new JPanel(new MigLayout("insets 0, gap 6", "[][][]", "[]"));
        private List<ModrinthBrowse.PackVersion> loaded;

        Card(final ModrinthBrowse.Hit h, ModrinthBrowse.Kind cardKind) {
            super(new BorderLayout());
            this.hit = h;
            this.cardKind = cardKind;
            setOpaque(false);
            JPanel card = LookUi.card("insets 10 12 10 12, gap 12 2, fillx", "[" + ICON + "!][grow,fill][]", "[][][]");
            BufferedImage known = ModrinthBrowse.cachedIcon(h.iconUrl);
            final PackIcon icon = new PackIcon(known, h.title);
            card.add(icon, "spany 3, aligny top, w " + ICON + "!, h " + ICON + "!");
            if (h.iconUrl != null && known == null) {
                ModrinthBrowse.POOL.submit(() -> {
                    BufferedImage img = ModrinthBrowse.icon(h.iconUrl);
                    if (img != null) SwingUtilities.invokeLater(() -> icon.set(img));
                });
            }
            JLabel name = LookUi.title(LookUi.plainText(h.title), 15f);
            JPanel head = new JPanel(new MigLayout("insets 0, gap 8", "[]", "[]"));
            head.setOpaque(false);
            head.add(name, "wmin 0");
            if (h.installed) {
                JLabel badge = new JLabel(tr("manage.browse.installed"));
                badge.setForeground(LookTheme.palette().success);
                badge.setFont(badge.getFont().deriveFont(Font.BOLD, 11.5f));
                head.add(badge);
            }
            card.add(head, "wmin 0");
            actions.setOpaque(false);
            if (cardKind == ModrinthBrowse.Kind.MOD) {
                final JButton install = LookUi.primary(new JButton(tr("mpage.mod.install")));
                install.putClientProperty("FlatLaf.styleClass", null);
                install.addActionListener(e -> modMenu(install));
                actions.add(install, "h 30!");
            } else {
                if (h.installed && play != null) {
                    JButton go = LookUi.primary(new JButton(tr("mpage.play")));
                    go.putClientProperty("FlatLaf.styleClass", null);
                    go.addActionListener(e -> {
                        java.io.File dir = newestDir();
                        if (dir != null) play.accept(dir.getName());
                    });
                    actions.add(go, "h 30!");
                    checkUpdate();
                }
                versionsButton.addActionListener(e -> toggleVersions());
                actions.add(versionsButton, "h 30!");
            }
            card.add(actions, "spany 2, aligny top, wrap");
            JLabel meta = new JLabel(tr("manage.browse.meta", LookUi.plainText(h.author), downloads(h.downloads)));
            meta.setForeground(LookTheme.palette().text3);
            meta.setFont(meta.getFont().deriveFont(11.5f));
            // 17.11.2026 (власник: «в самих збірках бракує більше інформації або хоча б посилання на збірку, щоб
            // гравець міг скріншоти глянути»): сторінка модпака на Modrinth у браузері (галерея, повний опис)
            JLabel site = new JLabel(tr(curse ? "cpage.page" : "mpage.page"));
            site.setFont(site.getFont().deriveFont(11.5f));
            site.setForeground(LookTheme.palette().accent);
            site.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            final String url = ModrinthBrowse.projectUrl(cardKind, h);
            site.setToolTipText(url);
            site.addMouseListener(new java.awt.event.MouseAdapter() {
                @Override
                public void mouseClicked(java.awt.event.MouseEvent e) {
                    SwingHelper.openURL(url, ModrinthPage.this);
                }
            });
            JPanel metaRow = new JPanel(new MigLayout("insets 0, gap 10", "[][]", "[]"));
            metaRow.setOpaque(false);
            metaRow.add(meta, "wmin 0");
            metaRow.add(site);
            card.add(metaRow, "cell 1 1, wmin 0");
            WrapText desc = new WrapText(h.description);
            card.add(desc, "cell 1 2, span 2, wmin 0");
            if ((cardKind == ModrinthBrowse.Kind.MOD || cardKind == ModrinthBrowse.Kind.RESOURCEPACK) && ModrinthBrowse.isXray(h)) {
                card.add(InstanceManageDialog.xrayWarning(), "cell 1 3, span 2, wmin 0"); // 24.11.2026: червона позначка X-ray
            }
            versions.setOpaque(false);
            versions.setVisible(false);
            card.add(versions, "cell 1 4, span 2, growx, wmin 0, hidemode 3");
            add(card, BorderLayout.CENTER);
        }

        private void toggleVersions() {
            if (versions.isVisible()) {
                versions.setVisible(false);
                versionsButton.setText(tr("mpage.versions"));
                revalidateAll();
                keepInView();
                return;
            }
            if (loaded != null) {
                versions.setVisible(true);
                versionsButton.setText(tr("mpage.hide"));
                revalidateAll();
                keepInView();
                return;
            }
            versionsButton.setEnabled(false);
            versionsButton.setText(tr("manage.browse.loading"));
            ModrinthBrowse.POOL.submit(() -> {
                try {
                    final List<ModrinthBrowse.PackVersion> list = curse
                            ? com.gummer.launcher.curseforge.CurseBrowse.versions(launcher, hit)
                            : ModrinthBrowse.modpackVersions(hit.id);
                    SwingUtilities.invokeLater(() -> fill(list, false));
                } catch (Exception e) {
                    log.log(Level.INFO, (curse ? "CurseForge" : "Modrinth") + " modpack versions failed: " + hit.slug, e);
                    SwingUtilities.invokeLater(() -> {
                        versionsButton.setEnabled(true);
                        versionsButton.setText(tr("mpage.versions"));
                        status.setText(tr(curse ? "manage.browse.failedCurse" : "manage.browse.failed"));
                    });
                }
            });
        }

        private void fill(final List<ModrinthBrowse.PackVersion> list, boolean all) {
            loaded = list;
            versions.removeAll();
            String loader = loaderFilter(), game = gameFilter();
            int shown = 0;
            for (ModrinthBrowse.PackVersion v : list) {
                if (loader != null && !loader.equals(v.loader())) continue;
                if (game != null && !v.games.contains(game)) continue;
                if (!all && shown >= FIRST_VERSIONS) {
                    int left = 0;
                    for (ModrinthBrowse.PackVersion x : list) {
                        if ((loader == null || loader.equals(x.loader())) && (game == null || x.games.contains(game))) left++;
                    }
                    JButton rest = LookUi.secondary(new JButton(tr("mpage.showAll", left)));
                    rest.addActionListener(e -> fill(list, true));
                    versions.add(rest, "growx 0, gaptop 2");
                    break;
                }
                versions.add(versionRow(v));
                shown++;
            }
            if (shown == 0) {
                JTextArea none = LookUi.body(tr("mpage.versionsNone"), true);
                versions.add(none, "wmin 0");
            }
            versions.setVisible(true);
            versionsButton.setEnabled(true);
            versionsButton.setText(tr("mpage.hide"));
            revalidateAll();
            keepInView();
        }

        /** 17.11.2026: після «Версії» і «Сховати» картка лишається на місці: її верх видно, версії під ним. */
        private void keepInView() {
            SwingUtilities.invokeLater(() -> {
                scroll.validate();
                Rectangle r = getBounds();
                int vh = scroll.getViewport().getExtentSize().height;
                results.scrollRectToVisible(new Rectangle(r.x, r.y, r.width, Math.min(r.height, vh)));
            });
        }

        private JComponent versionRow(final ModrinthBrowse.PackVersion v) {
            JPanel row = new JPanel(new MigLayout("insets 4 8 4 4, gap 10", "[grow,fill][]", "[]")) {
                @Override
                protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setColor(LookTheme.palette().glass() ? new Color(255, 255, 255, 12) : LookTheme.palette().surface2);
                    g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
                    g2.dispose();
                }
            };
            row.setOpaque(false);
            LookTheme.Palette p = LookTheme.palette();
            String type = "beta".equals(v.type) ? tr("mpage.type.beta") : "alpha".equals(v.type) ? tr("mpage.type.alpha") : null;
            String text = "<html><b>" + SwingHelper.htmlEscape(LookUi.plainText(v.number)) + "</b>&nbsp;&nbsp;<span style=\"color:"
                    + LookUi.hex(p.text3) + "\">" + SwingHelper.htmlEscape(loaderTitle(v.loader()) + " " + v.game())
                    + (type != null ? " · " + SwingHelper.htmlEscape(type) : "") + "</span></html>";
            JLabel label = new JLabel(text);
            label.setForeground(p.text);
            label.setToolTipText(LookUi.plainText(v.name));
            row.add(label, "wmin 0");
            final JButton install = LookUi.primary(new JButton(tr("manage.browse.install")));
            install.addActionListener(e -> install(v, install));
            row.add(install, "h 28!");
            return row;
        }

        /** Найновіша (за часом зміни) збірка цього модпака на диску, або null. */
        private java.io.File newestDir() {
            java.io.File best = null;
            for (java.io.File d : com.gummer.launcher.modrinth.MrpackPacks.dirsOfProject(launcher, hit.id)) {
                if (best == null || d.lastModified() > best.lastModified()) best = d;
            }
            return best;
        }

        private com.skcraft.launcher.Instance instanceOf(java.io.File dir) {
            if (dir == null) return null;
            synchronized (launcher.getInstances()) {
                for (com.skcraft.launcher.Instance i : launcher.getInstances().getInstances()) {
                    if (dir.getName().equalsIgnoreCase(i.getName())) return i;
                }
            }
            return null;
        }

        /** 17.11.2026: новіша версія встановленого модпака: кнопка «Оновити до ...» (перевірка у фоні). */
        private void checkUpdate() {
            final com.skcraft.launcher.Instance inst = instanceOf(newestDir());
            if (!com.gummer.launcher.modrinth.MrpackUpdate.canUpdate(inst)) return;
            ModrinthBrowse.POOL.submit(() -> {
                try {
                    final com.gummer.launcher.modrinth.MrpackUpdate.Check c = com.gummer.launcher.modrinth.MrpackUpdate.check(inst);
                    if (c == null || c.newest == null) return;
                    SwingUtilities.invokeLater(() -> {
                        JButton up = LookUi.secondary(new JButton(tr("mrpack.card.update", c.newest.number)));
                        up.setToolTipText(tr("mpage.updateTip"));
                        up.addActionListener(e -> MrpackUpdateFlow.start(SwingUtilities.getWindowAncestor(ModrinthPage.this),
                                launcher, inst, c.newest, i -> {
                                    up.setVisible(false);
                                    if (installed != null) installed.accept(i.getName());
                                }));
                        actions.add(up, "h 30!", 1);
                        actions.revalidate();
                        revalidateAll();
                    });
                } catch (Exception e) {
                    log.log(Level.FINE, "Modpack update check failed: " + hit.slug, e);
                }
            });
        }

        // ------------------------------------------------------------ моди

        /** Збірки, у які лаунчер уміє ставити моди: встановлені, з відомою грою і завантажувачем. */
        private java.util.List<com.skcraft.launcher.Instance> modTargets() {
            java.util.List<com.skcraft.launcher.Instance> out = new java.util.ArrayList<com.skcraft.launcher.Instance>();
            synchronized (launcher.getInstances()) {
                for (com.skcraft.launcher.Instance i : launcher.getInstances().getInstances()) {
                    if (com.skcraft.launcher.swing.InstanceTableModel.isBrowser(i) || !i.isLocal() || !i.isInstalled()) continue;
                    if (ModrinthBrowse.loadersOf(ModrinthBrowse.Kind.MOD, i) == null || ModrinthBrowse.gameVersionOf(i) == null) continue;
                    out.add(i);
                }
            }
            return out;
        }

        /** «Встановити» мода: версії мода і підхожі збірки у фоні, потім меню. */
        private void modMenu(final JButton button) {
            button.setEnabled(false);
            button.setText(tr("manage.browse.loading"));
            final java.util.List<com.skcraft.launcher.Instance> targets = modTargets();
            ModrinthBrowse.POOL.submit(() -> {
                try {
                    final List<ModrinthBrowse.PackVersion> vs = curse
                            ? com.gummer.launcher.curseforge.CurseBrowse.modVersions(launcher, hit) : ModrinthBrowse.modVersions(hit.id);
                    final java.util.List<Object[]> fit = new java.util.ArrayList<Object[]>();
                    for (com.skcraft.launcher.Instance i : targets) {
                        String game = ModrinthBrowse.gameVersionOf(i);
                        String[] loaders = ModrinthBrowse.loadersOf(ModrinthBrowse.Kind.MOD, i);
                        if (!ModrinthBrowse.fits(vs, game, loaders)) continue;
                        boolean has = i.peekDir() != null && ModrinthBrowse.looksInstalled(com.gummer.launcher.manage.InstanceMods.peekModsDir(i), hit.id, hit.slug);
                        fit.add(new Object[]{i, game, loaders, has});
                    }
                    SwingUtilities.invokeLater(() -> {
                        button.setEnabled(true);
                        button.setText(tr("mpage.mod.install"));
                        showModMenu(button, vs, fit);
                    });
                } catch (Exception e) {
                    log.log(Level.INFO, (curse ? "CurseForge" : "Modrinth") + " mod versions failed: " + hit.slug, e);
                    SwingUtilities.invokeLater(() -> {
                        button.setEnabled(true);
                        button.setText(tr("mpage.mod.install"));
                        status.setText(tr(curse ? "manage.browse.failedCurse" : "manage.browse.failed"));
                    });
                }
            });
        }

        private void showModMenu(JButton button, final List<ModrinthBrowse.PackVersion> vs, java.util.List<Object[]> fit) {
            JPopupMenu menu = new JPopupMenu();
            JMenuItem head = new JMenuItem(tr("mpage.mod.into"));
            head.setEnabled(false);
            menu.add(head);
            for (Object[] f : fit) {
                final com.skcraft.launcher.Instance i = (com.skcraft.launcher.Instance) f[0];
                final String game = (String) f[1];
                final String[] loaders = (String[]) f[2];
                boolean has = (Boolean) f[3];
                String where = loaderTitle(loaders[0]) + " " + game;
                String label = i.getTitle() + (i.getTitle().contains(game) ? "" : "  (" + where + ")");
                JMenuItem item = new JMenuItem(has ? label + " · " + tr("mpage.mod.already") : label);
                item.setEnabled(!has);
                item.addActionListener(e -> installInto(i, game, loaders));
                menu.add(item);
            }
            if (fit.isEmpty()) {
                JMenuItem none = new JMenuItem(tr("mpage.mod.noBuilds"));
                none.setEnabled(false);
                menu.add(none);
            }
            menu.addSeparator();
            JMenuItem create = new JMenuItem(tr("mpage.mod.newBuild"));
            create.addActionListener(e -> {
                try {
                    newBuildWith(vs);
                } catch (RuntimeException ex) {
                    log.log(Level.WARNING, "New build with mod failed", ex);
                    toast(Notification.Type.WARNING, tr(curse ? "manage.browse.failedCurse" : "manage.browse.failed"));
                }
            });
            menu.add(create);
            menu.show(button, 0, button.getHeight());
        }

        private void installInto(final com.skcraft.launcher.Instance target, final String game, final String[] loaders) {
            final String modTitle = LookUi.plainText(hit.title);
            toast(Notification.Type.INFO, tr("mpage.mod.installing", modTitle, target.getTitle()));
            ModrinthBrowse.POOL.submit(() -> {
                final List<String> added = new java.util.ArrayList<String>();
                final List<String> missing = new java.util.ArrayList<String>();
                final List<String> conflicts = new java.util.ArrayList<String>();
                try {
                    installMod(game, loaders, com.gummer.launcher.manage.InstanceMods.modsDir(target), added, missing, conflicts);
                    SwingUtilities.invokeLater(() -> {
                        added.remove(hit.title);
                        toast(Notification.Type.SUCCESS, added.isEmpty() ? tr("mpage.mod.installed", modTitle, target.getTitle())
                                : tr("mpage.mod.installedWith", modTitle, target.getTitle(), String.join(", ", added)));
                        if (!missing.isEmpty()) toast(Notification.Type.WARNING, tr(curse ? "cpage.mod.depsMissing" : "mpage.mod.depsMissing",
                                modTitle, String.join(", ", missing), game));
                        if (!conflicts.isEmpty()) toast(Notification.Type.WARNING, tr("mpage.mod.conflicts", modTitle, String.join(", ", conflicts)));
                    });
                } catch (Exception e) {
                    log.log(Level.WARNING, "Mod install failed: " + hit.slug + " -> " + target.getName(), e);
                    SwingUtilities.invokeLater(() -> toast(Notification.Type.WARNING,
                            ModrinthBrowse.NO_VERSION.equals(e.getMessage()) ? tr("mpage.mod.noVersion", modTitle, game)
                                    : com.gummer.launcher.curseforge.CurseBrowse.NOT_DOWNLOADED.equals(e.getMessage())
                                    ? tr("cpage.notDownloaded", modTitle) : tr("manage.browse.installFailed", modTitle)));
                }
            });
        }

        /** Мод (з залежностями) у теку модів: з Modrinth або, на сторінці CurseForge, з CurseForge. */
        private void installMod(String game, String[] loaders, java.io.File mods, List<String> added, List<String> missing,
                                List<String> conflicts) throws Exception {
            if (curse) {
                com.gummer.launcher.curseforge.CurseBrowse.installFile(launcher, ModrinthBrowse.Kind.MOD, hit, game, loaders, mods, added, missing, conflicts);
            } else {
                ModrinthBrowse.install(ModrinthBrowse.Kind.MOD, hit, game, loaders, mods, added, missing, conflicts);
            }
        }

        /**
         * «Нова збірка з цим модом»: вибір завантажувача і версії гри (лише ті, для яких мод є і які лаунчер ставить),
         * чиста копія розділу (як «Копія» без галочок) з цим модом і його залежностями; далі вікно ставить гру.
         */
        private void newBuildWith(List<ModrinthBrowse.PackVersion> vs) {
            final java.util.List<Object[]> options = new java.util.ArrayList<Object[]>();
            java.util.Set<String> seen = new java.util.HashSet<String>();
            String[] order = {"fabric", "neoforge", "forge", "quilt"};
            java.util.List<String> games = new java.util.ArrayList<String>();
            // 18.11.2026 (власник: «Нова збірка з цим модом не спрацьовує»): Worlds.compareGame дає 0 для снапшотів
            // («25w14a»), це не дійсне порівняння, і на довгому списку версій (Sodium, Fabric API) сортування Java кидало
            // «Comparison method violates its general contract» у потоці інтерфейсу: клік тихо нічого не робив. Тепер
            // лише релізи (цифри з крапками), від новішого, без снапшотів (для них у лаунчері немає розділу)
            for (ModrinthBrowse.PackVersion v : vs) for (String g : v.games) if (g != null && g.matches("[0-9]+([.][0-9]+)*") && !games.contains(g)) games.add(g);
            games.sort((a, b) -> {
                String[] x = b.split("[.]"), y = a.split("[.]");
                for (int i = 0; i < Math.max(x.length, y.length); i++) {
                    int p = i < x.length ? Integer.parseInt(x[i]) : 0, q = i < y.length ? Integer.parseInt(y[i]) : 0;
                    if (p != q) return Integer.compare(p, q);
                }
                return 0;
            });
            for (String g : games) {
                for (String l : order) {
                    boolean has = false;
                    for (ModrinthBrowse.PackVersion v : vs) if (v.games.contains(g) && v.loaders.contains(l)) has = true;
                    if (!has || !seen.add(l + g)) continue;
                    com.skcraft.launcher.Instance src = builtIn(l, g);
                    if (src != null) options.add(new Object[]{src, l, g});
                }
            }
            Window owner = SwingUtilities.getWindowAncestor(ModrinthPage.this);
            if (options.isEmpty()) {
                toast(Notification.Type.WARNING, tr("mpage.mod.noNewBuild", LookUi.plainText(hit.title)));
                return;
            }
            JComboBox<String> box = new JComboBox<String>();
            for (Object[] o : options) box.addItem(loaderTitle((String) o[1]) + " " + o[2]);
            box.setMaximumRowCount(16);
            JTextField name = new JTextField(LookUi.plainText(hit.title));
            JPanel form = new JPanel(new MigLayout("insets 0, wrap 1, gap 0 6, fillx", "[grow,fill]", ""));
            form.add(LookUi.body(tr("mpage.mod.newBuildHint", LookUi.plainText(hit.title)), true), "w 380!");
            form.add(new JLabel(tr("mpage.mod.newBuildVersion")), "gaptop 6");
            form.add(box);
            form.add(new JLabel(tr("mpage.mod.newBuildName")), "gaptop 6");
            form.add(name);
            Object[] buttons = {tr("mpage.mod.newBuildGo"), tr("button.cancel")};
            LookUi.freeze(form);
            if (JOptionPane.showOptionDialog(owner, form, tr("mpage.mod.newBuild"), JOptionPane.OK_CANCEL_OPTION,
                    JOptionPane.PLAIN_MESSAGE, null, buttons, buttons[0]) != 0) return;
            final Object[] chosen = options.get(Math.max(0, box.getSelectedIndex()));
            final String title = com.gummer.launcher.manage.InstanceCopies.cleanTitle(name.getText());
            final String loader = (String) chosen[1], game = (String) chosen[2];
            final String[] loaders = "quilt".equals(loader) ? new String[]{"quilt", "fabric"} : new String[]{loader};
            final com.gummer.launcher.manage.InstanceCopies.Copier copier = new com.gummer.launcher.manage.InstanceCopies.Copier(
                    launcher, (com.skcraft.launcher.Instance) chosen[0], title, java.util.EnumSet.noneOf(com.gummer.launcher.manage.InstanceCopies.Part.class));
            final com.google.common.util.concurrent.ListenableFuture<String> task = launcher.getExecutor().submit(() -> {
                String made = copier.call();
                java.io.File mods = new java.io.File(new java.io.File(new java.io.File(launcher.getInstancesDir(), made), "minecraft"), "mods");
                installMod(game, loaders, mods, null, null, null);
                return made;
            });
            com.skcraft.concurrency.ObservableFuture<String> future = new com.skcraft.concurrency.ObservableFuture<String>(task, copier);
            com.skcraft.launcher.dialog.ProgressDialog.showProgress(owner, future, tr("mpage.mod.newBuild"), tr("manage.copy.preparing"));
            SwingHelper.addErrorDialogCallback(owner, future);
            future.addListener(() -> {
                try {
                    String made = task.get();
                    toast(Notification.Type.SUCCESS, tr("mpage.mod.newBuildDone", title != null ? title : LookUi.plainText(hit.title)));
                    if (installed != null) installed.accept(made); // вікно вибирає збірку і ставить гру
                } catch (Exception ignored) {
                    // помилку показав addErrorDialogCallback
                }
            }, com.skcraft.launcher.util.SwingExecutor.INSTANCE);
        }

        /** Розділ лаунчера (fabric-1.21.1, forge-1.20.1...) для завантажувача і гри, або null. */
        private com.skcraft.launcher.Instance builtIn(String loader, String game) {
            String n;
            switch (loader) {
                case "fabric":
                    n = com.gummer.launcher.fabric.FabricVersions.nameOf(game);
                    break;
                case "quilt":
                    n = com.gummer.launcher.quilt.QuiltVersions.nameOf(game);
                    break;
                case "forge":
                    n = com.gummer.launcher.forge.ForgeVersions.nameOf(com.gummer.launcher.forge.ForgeVersions.Kind.FORGE, game);
                    break;
                case "neoforge":
                    n = com.gummer.launcher.forge.ForgeVersions.nameOf(com.gummer.launcher.forge.ForgeVersions.Kind.NEOFORGE, game);
                    break;
                default:
                    return null;
            }
            synchronized (launcher.getInstances()) {
                for (com.skcraft.launcher.Instance i : launcher.getInstances().getInstances()) {
                    if (n.equalsIgnoreCase(i.getName())) return i;
                }
            }
            return null;
        }

        private void install(final ModrinthBrowse.PackVersion v, final JButton button) {
            if (busy) return;
            final String where = loaderTitle(v.loader()) + " " + v.game();
            Window owner = SwingUtilities.getWindowAncestor(ModrinthPage.this);
            if (!SwingHelper.confirmDialog(owner, tr("mpage.confirm", LookUi.plainText(hit.title), LookUi.plainText(v.number), where),
                    tr("manage.browse.modpackConfirmTitle"))) return;
            busy = true;
            button.setEnabled(false);
            button.setText(tr("manage.browse.installing"));
            final com.gummer.launcher.modrinth.MrpackPacks importer = new com.gummer.launcher.modrinth.MrpackPacks();
            final com.google.common.util.concurrent.ListenableFuture<String> task = launcher.getExecutor().submit(
                    () -> curse ? com.gummer.launcher.curseforge.CurseBrowse.install(launcher, hit, v, importer)
                            : ModrinthBrowse.installModpackVersion(launcher, hit, v, importer));
            com.skcraft.concurrency.ObservableFuture<String> future = new com.skcraft.concurrency.ObservableFuture<String>(task, importer);
            com.skcraft.launcher.dialog.ProgressDialog.showProgress(owner, future,
                    tr("manage.browse.modpackProgressTitle"), tr("manage.browse.modpackProgress", LookUi.plainText(hit.title)));
            future.addListener(() -> {
                busy = false;
                try {
                    String name = task.get();
                    button.setText(tr("manage.browse.installed"));
                    hit.installed = true;
                    com.gummer.launcher.stats.LaunchStats.added(launcher, "modpack");
                    // 17.11.2026: далі вікно одразу ставить гру і завантажувач (installed), «Встановлено» скаже воно
                    if (installed != null) installed.accept(name);
                } catch (Exception ex) {
                    Throwable t = ex.getCause() != null ? ex.getCause() : ex;
                    button.setEnabled(true);
                    button.setText(tr("manage.browse.install"));
                    toast(Notification.Type.WARNING, tr("manage.browse.installFailed", LookUi.plainText(hit.title)));
                    log.log(Level.WARNING, "Modpack install failed: " + hit.slug + " " + v.number, t);
                }
            }, com.skcraft.launcher.util.SwingExecutor.INSTANCE);
        }

        private void revalidateAll() {
            results.revalidate();
            results.repaint();
        }
    }

    private void toast(Notification.Type type, String text) {
        Window w = SwingUtilities.getWindowAncestor(this);
        if (w instanceof Frame) com.gummer.launcher.look.Toast.show((Frame) w, type, text, null);
    }
}
