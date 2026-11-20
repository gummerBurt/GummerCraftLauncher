/*
 * SK's Minecraft Launcher
 * Copyright (C) 2010-2014 Albert Pham <http://www.sk89q.com> and contributors
 * Copyright (C) 2021-2025 GummerCraft <http://gummercraft.fun>
 * Please see LICENSE.txt for license information.
 */

package com.skcraft.launcher.swing;

import com.skcraft.launcher.Instance;
import com.skcraft.launcher.InstanceList;
import com.skcraft.launcher.Launcher;
import com.skcraft.launcher.util.SharedLocale;
import com.gummer.launcher.swing.SwingExtras;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import java.awt.*;
import java.io.File;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

@SuppressWarnings("ConstantConditions")
public class InstanceTableModel extends AbstractTableModel {

    private final InstanceList instances;
    private final Icon instanceIcon;
    private final java.util.Map<java.awt.image.BufferedImage, Icon> artIcons = new java.util.WeakHashMap<>();
    /** Іконки модпаків Modrinth за файлом: шлях -> {час зміни, Icon}. */
    private final java.util.Map<String, Object[]> packIcons = new java.util.HashMap<>();

    /**
     * 17.11.2026 (власник: «в неї ставала іконка не modrinth, а самої збірки»): іконка проєкту з Modrinth
     * ({@code pack-icon.png}), інакше {@code icon.png} з файлів модпака, інакше null (іконка Modrinth). Кеш за часом
     * зміни файлу: раніше картинка читалась і зменшувалась на кожне малювання рядка.
     */
    private Icon packIcon(Instance instance) {
        File f = new File(instance.getDir(), com.gummer.launcher.modrinth.MrpackPacks.PACK_ICON);
        if (!f.isFile()) f = new File(instance.getContentDir(), "icon.png");
        if (!f.isFile()) return null;
        String key = f.getAbsolutePath();
        long mod = f.lastModified();
        synchronized (packIcons) {
            Object[] c = packIcons.get(key);
            if (c != null && (Long) c[0] == mod) return (Icon) c[1];
        }
        Icon icon = null;
        try {
            java.awt.image.BufferedImage img = javax.imageio.ImageIO.read(f);
            if (img != null) icon = new CrispIcon(img, 32, 32);
        } catch (Exception ignored) {
            // зіпсована картинка: іконка Modrinth
        }
        synchronized (packIcons) {
            packIcons.put(key, new Object[]{mod, icon});
        }
        return icon;
    }
    private final Icon customInstanceIcon;
    private final Icon downloadIcon;
    /** 1.33: свої іконки трьох розділів, однакові незалежно від того, встановлено пак чи ні (власник 13.11). */
    private final Icon staldayzIcon;
    private final Icon gummercraftIcon;
    private final Icon vanillaIcon;
    private final Icon fabricIcon;
    private final Icon forgeIcon;
    private final Icon quiltIcon;
    private final Icon modrinthIcon;
    private final Icon neoforgeIcon;

    /**
     * 1.32: групи паків зі списку v2 (поле group, наприклад "main") показуються одним рядком, версія вибирається окремо
     * (у FlatLaf-вікні випадний список біля «Грати»). Лише для вікна, яке це вміє (конструктор з lastInstance);
     * старі вікна працюють як раніше: рядок = індекс у InstanceList.
     */
    private final boolean grouped;
    private final Supplier<String> lastInstance;
    private final List<Instance> rows = new ArrayList<Instance>();
    /** Вибір версії в групі за цей запуск лаунчера (група -> назва паку). Між запусками: lastInstance і дата запуску. */
    private final Map<String, String> groupChoice = new HashMap<String, String>();
    /** 1.32: які паки показувати (вкладки GummerCraft і Vanilla), null - усі. */
    private java.util.function.Predicate<Instance> filter;
    /** 1.32: онлайн сервера паку для другого рядка (назва паку -> «5 онлайн»). */
    private final Map<String, String> onlineLines = new java.util.concurrent.ConcurrentHashMap<String, String>();

    public void setFilter(java.util.function.Predicate<Instance> filter) {
        this.filter = filter;
    }

    /**
     * 17.11.2026 (власник: перемикач лівого меню «АБО всі доступні, АБО всі встановлені»): true, список «Встановлені»:
     * кожна збірка на диску окремим рядком зі своєю іконкою (своя іконка копії, іконка модпака), без вибору версії.
     * false, список «Усі»: StalDayZ, GummerCraft, Vanilla, завантажувачі (копії і модпаки у виборі версії свого
     * розділу) і в кінці рядок Modrinth (сторінка модпаків).
     */
    private boolean installedOnly;

    public boolean isInstalledOnly() {
        return installedOnly;
    }

    public void setInstalledOnly(boolean installedOnly) {
        this.installedOnly = installedOnly;
    }

    /**
     * 17.11.2026: рядок «Modrinth» лівого меню. Не збірка, а сторінка модпаків Modrinth на місці фону: гравець вибирає
     * модпак і версію, і модпак стає у вибір версії свого завантажувача. Вікна перевіряють {@link #isBrowser(Instance)}
     * і не запускають, не встановлюють і не запам'ятовують його.
     */
    public static class BrowserRow extends Instance {
        public BrowserRow() {
            this("gc-browse-modrinth", "Modrinth");
        }

        protected BrowserRow(String name, String title) {
            setName(name);
            setTitle(title);
            setLocal(false);
        }

        /** Не сервер (без пінгу і підпису «Сервери»), стоїть серед версій Minecraft. */
        @Override
        public boolean isBuiltIn() {
            return true;
        }

        @Override
        public boolean equals(Object o) {
            return o == this;
        }

        @Override
        public int hashCode() {
            return System.identityHashCode(this);
        }
    }

    public static final BrowserRow MODRINTH = new BrowserRow();

    /**
     * 17.11.2026 (власник: «у режимі "Встановлені" неможливо додати збірку ... якщо треба, ок»): рядок «+ Додати збірку»
     * у кінці списку «Встановлені». Як і рядок Modrinth, це не збірка ({@link #isBrowser} теж true); вікна, вибравши
     * його, перемикають список на «Усі», де є всі версії і модпаки.
     */
    public static final class AddRow extends BrowserRow {
        AddRow() {
            super("gc-add-build", "+");
        }

        @Override
        public String getTitle() {
            return SharedLocale.tr("launcher.addBuild");
        }
    }

    public static final AddRow ADD = new AddRow();

    /** Рядок «+ Додати збірку». */
    public static boolean isAddRow(Instance instance) {
        return instance instanceof AddRow;
    }

    /**
     * 19.11.2026 (власник: «під Modrinth додай "Імпорт збірки", малим текстом ... "З іншого лаунчера" --> відкриває
     * вікно імпорту»): рядок під Modrinth у «Усі». Як і Modrinth, не збірка ({@link #isBrowser} true): вибрати його
     * не можна, вікна одразу повертають попередній вибір і відкривають ImportOtherDialog.
     */
    public static final class ImportRow extends BrowserRow {
        ImportRow() {
            super("gc-import-other", "Import");
        }

        @Override
        public String getTitle() {
            return SharedLocale.tr("launcher.importRow");
        }
    }

    public static final ImportRow IMPORT = new ImportRow();

    /** Рядок «Імпорт збірки». */
    public static boolean isImportRow(Instance instance) {
        return instance instanceof ImportRow;
    }

    /** Іконка рядка «Імпорт збірки»: стрілка вниз у лоток, у колі, без файлу картинки. */
    private static final Icon IMPORT_ICON = new Icon() {
        @Override
        public void paintIcon(java.awt.Component c, java.awt.Graphics g, int x, int y) {
            java.awt.Graphics2D g2 = (java.awt.Graphics2D) g.create();
            g2.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(new java.awt.Color(124, 207, 106, 60));
            g2.fillOval(x + 1, y + 1, 30, 30);
            g2.setColor(new java.awt.Color(124, 207, 106));
            g2.setStroke(new java.awt.BasicStroke(2.6f, java.awt.BasicStroke.CAP_ROUND, java.awt.BasicStroke.JOIN_ROUND));
            g2.drawLine(x + 16, y + 8, x + 16, y + 18);
            g2.drawLine(x + 12, y + 14, x + 16, y + 18);
            g2.drawLine(x + 20, y + 14, x + 16, y + 18);
            g2.drawLine(x + 9, y + 18, x + 9, y + 23);
            g2.drawLine(x + 9, y + 23, x + 23, y + 23);
            g2.drawLine(x + 23, y + 23, x + 23, y + 18);
            g2.dispose();
        }

        @Override
        public int getIconWidth() {
            return 32;
        }

        @Override
        public int getIconHeight() {
            return 32;
        }
    };

    /** Іконка рядка «+ Додати збірку»: плюс у колі, без файлу картинки. */
    private static final Icon ADD_ICON = new Icon() {
        @Override
        public void paintIcon(java.awt.Component c, java.awt.Graphics g, int x, int y) {
            java.awt.Graphics2D g2 = (java.awt.Graphics2D) g.create();
            g2.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(new java.awt.Color(124, 207, 106, 60));
            g2.fillOval(x + 1, y + 1, 30, 30);
            g2.setColor(new java.awt.Color(124, 207, 106));
            g2.setStroke(new java.awt.BasicStroke(3f, java.awt.BasicStroke.CAP_ROUND, java.awt.BasicStroke.JOIN_ROUND));
            g2.drawLine(x + 16, y + 9, x + 16, y + 23);
            g2.drawLine(x + 9, y + 16, x + 23, y + 16);
            g2.dispose();
        }

        @Override
        public int getIconWidth() {
            return 32;
        }

        @Override
        public int getIconHeight() {
            return 32;
        }
    };

    /** Рядок сторінки Modrinth, а не збірка. */
    public static boolean isBrowser(Instance instance) {
        return instance instanceof BrowserRow;
    }

    public void setOnlineLine(String instanceName, String line) {
        if (instanceName == null) return;
        if (line == null) onlineLines.remove(instanceName); else onlineLines.put(instanceName, line);
    }

    public InstanceTableModel(InstanceList instances) {
        this(instances, null);
    }

    public InstanceTableModel(InstanceList instances, Supplier<String> lastInstance) {
        this.instances = instances;
        this.grouped = lastInstance != null;
        this.lastInstance = lastInstance;
        instanceIcon = CrispIcon.of(Launcher.class, "instance_icon.png", 32, 32);
        customInstanceIcon = instanceIcon;
        downloadIcon = CrispIcon.of(Launcher.class, "download_icon.png", 32, 32);
        staldayzIcon = CrispIcon.of(Launcher.class, "icon_staldayz.png", 32, 32);
        gummercraftIcon = CrispIcon.of(Launcher.class, "icon_gummercraft.png", 32, 32);
        vanillaIcon = CrispIcon.of(Launcher.class, "icon_vanilla.png", 32, 32);
        // icon_fabric.png: офіційна іконка FabricMC/fabric-loader (src/main/resources/assets/fabricloader/icon.png, Apache 2.0)
        fabricIcon = CrispIcon.of(Launcher.class, "icon_fabric.png", 32, 32);
        // icon_forge.png: ковадло з логотипа Forge (установник Forge, big_logo_dark_mode.png), 64x64
        forgeIcon = CrispIcon.of(Launcher.class, "icon_forge.png", 32, 32);
        // icon_quilt.png: офіційна іконка Quilt (quilt-loader, ui/icon/quilt_x128.png, Apache 2.0), 64x64
        quiltIcon = CrispIcon.of(Launcher.class, "icon_quilt.png", 32, 32);
        // icon_modrinth.png: іконка застосунку Modrinth (github.com/modrinth/code, apps/app/icons/128x128.png), 64x64
        modrinthIcon = CrispIcon.of(Launcher.class, "icon_modrinth.png", 32, 32);
        // icon_neoforge.png: логотип NeoForged (установник NeoForge, icons/neoforged_background_128x128.png), 64x64
        neoforgeIcon = CrispIcon.of(Launcher.class, "icon_neoforge.png", 32, 32);
    }

    private static boolean isStalDayZ(Instance instance) {
        String n = instance.getName();
        return n != null && (n.equals("staldayz") || n.startsWith("staldayz-") || n.startsWith("sdayz"));
    }

    public void update() {
        instances.sort();
        rebuildRows();
        shownKeys = rowKeys();
        fireTableDataChanged();
    }

    /** Що зараз показано в таблиці (група або ім'я в кожному рядку), щоб знати, чи змінився склад рядків. */
    private List<String> shownKeys = new ArrayList<String>();

    private List<String> rowKeys() {
        List<String> keys = new ArrayList<String>();
        for (int r = 0; r < getRowCount(); r++) {
            Instance i = getInstanceAt(r);
            keys.add(i.getGroup() != null && !i.isOrphaned() && !installedOnly ? "g:" + i.getGroup() : "n:" + i.getName());
        }
        return keys;
    }

    /**
     * GummerCraft 17.11.2026: оновити після тихого перечитування списку з мережі. Якщо рядки ті самі (ті самі збірки
     * в тому ж порядку), лише перемалювати їх: повне оновлення таблиці скидає вибір і висоти рядків, і в Liquid Glass
     * (рядки з підписами розділів вищі) список на мить «блимав» через секунду після старту. Повертає true, якщо рядки
     * лишились ті самі (вибір не скидався).
     */
    public boolean updateKeepingRows() {
        instances.sort();
        rebuildRows();
        List<String> keys = rowKeys();
        if (!keys.isEmpty() && keys.equals(shownKeys)) {
            fireTableRowsUpdated(0, getRowCount() - 1);
            return true;
        }
        shownKeys = keys;
        fireTableDataChanged();
        return false;
    }

    /** Пак у рядку таблиці (для групи: вибрана версія). */
    public Instance getInstanceAt(int row) {
        return grouped ? rows.get(row) : instances.get(row);
    }

    /** Рядок, у якому показано цей пак (або його групу), або -1. */
    public int rowOf(String instanceName) {
        if (instanceName == null) {
            return -1;
        }
        for (int i = 0; i < getRowCount(); i++) {
            Instance shown = getInstanceAt(i);
            if (instanceName.equalsIgnoreCase(shown.getName())) {
                return i;
            }
            for (Instance member : getGroupMembers(shown)) {
                if (instanceName.equalsIgnoreCase(member.getName())) {
                    return i;
                }
            }
        }
        return -1;
    }

    /** Версії групи цього паку (без покинутих), від новішої; порожньо, якщо пак не в групі або групування вимкнено. */
    public List<Instance> getGroupMembers(Instance instance) {
        List<Instance> members = new ArrayList<Instance>();
        if (!grouped || instance == null || instance.getGroup() == null || instance.isOrphaned()) {
            return members;
        }
        if (installedOnly) {
            return members; // «Встановлені»: кожна збірка окремим рядком, вибору версії немає
        }
        if (instance.isBuiltIn()) {
            // 20.11.2026 (власник: в «Додати інсталяцію» ваніль і завантажувачі «можна буде лише встановити і не буде
            // писати яка версія вже є чи ні»): лише чисті версії в загальному порядку, без копій, збірок Modrinth і без
            // підняття встановлених нагору. Свої копії і збірки гравець бачить у «Встановлених»
            synchronized (instances) {
                for (Instance i : instances.getInstances()) {
                    if (instance.getGroup().equals(i.getGroup()) && !i.isOrphaned() && !isDerived(i)) members.add(i);
                }
            }
            return members;
        }
        // 17.11.2026 (власник: «нехай встановлені будуть вверху списку, а не під версією збірки»): спершу все
        // встановлене (чисті версії, копії, модпаки; як зелена крапка у списку), потім решта; усередині кожної частини порядок той самий
        // (від новішої версії, чиста перед своїми копіями і модпаками)
        List<Instance> rest = new ArrayList<Instance>();
        synchronized (instances) {
            for (Instance i : instances.getInstances()) {
                if (instance.getGroup().equals(i.getGroup()) && !i.isOrphaned()) {
                    (i.isLocal() && i.isInstalled() ? members : rest).add(i);
                }
            }
        }
        members.addAll(rest);
        return members;
    }

    /**
     * Найновіша чиста версія розділу (позначка «найновіша» у виборі версії). Раніше це був просто перший рядок, але
     * з 17.11.2026 вгорі встановлені, тож шукаємо за загальним порядком (від новішої, без копій і модпаків).
     */
    public boolean isNewest(Instance instance) {
        if (instance == null || instance.getGroup() == null || isDerived(instance)) return false;
        synchronized (instances) {
            for (Instance i : instances.getInstances()) {
                if (instance.getGroup().equals(i.getGroup()) && !i.isOrphaned() && !isDerived(i)) {
                    return i == instance;
                }
            }
        }
        return false;
    }

    /**
     * Лише перебудувати рядки після вибору версії в групі (16.11.2026): кількість і порядок рядків ті самі, тож
     * подія «рядки оновлено», а не «усе змінилось», і JTable не знімає вибір (див. LauncherFrameFlat.showPack).
     */
    public void refreshChoice() {
        rebuildRows();
        shownKeys = rowKeys();
        if (getRowCount() > 0) {
            fireTableRowsUpdated(0, getRowCount() - 1);
        }
    }

    /** Гравець вибрав версію в групі: рядок групи тепер показує її. */
    public void chooseInGroup(Instance chosen) {
        if (chosen != null && chosen.getGroup() != null) {
            groupChoice.put(chosen.getGroup(), chosen.getName());
        }
    }

    private void rebuildRows() {
        rows.clear();
        if (!grouped) {
            return;
        }
        if (installedOnly) {
            // 17.11.2026 (власник: «щоб "Встановлені" мали групування та сортування, як "Усі"»): розділи в тому ж
            // порядку, що і в «Усі» (StalDayZ, модпаки GummerCraft, Vanilla, Fabric, Quilt, Forge, NeoForge), розділ
            // стоїть там, де його перший рядок у загальному порядку; усередині розділу встановлені збірки підряд
            Map<String, List<Instance>> byKey = new java.util.LinkedHashMap<String, List<Instance>>();
            synchronized (instances) {
                for (Instance instance : instances.getInstances()) {
                    if (filter != null && !filter.test(instance)) continue;
                    String group = instance.getGroup();
                    String key = group == null || instance.isOrphaned() ? "n:" + instance.getName() : "g:" + group;
                    List<Instance> list = byKey.get(key);
                    if (list == null) byKey.put(key, list = new ArrayList<Instance>());
                    if (instance.isLocal()) list.add(instance);
                }
            }
            for (List<Instance> list : byKey.values()) rows.addAll(list);
            if (filter == null) rows.add(ADD);
            return;
        }
        Map<String, Boolean> emitted = new HashMap<String, Boolean>();
        synchronized (instances) {
            for (Instance instance : instances.getInstances()) {
                if (filter != null && !filter.test(instance)) {
                    continue;
                }
                String group = instance.getGroup();
                if (group == null || instance.isOrphaned()) {
                    rows.add(instance);
                } else if (!emitted.containsKey(group)) {
                    emitted.put(group, true);
                    rows.add(chosenOf(getGroupMembers(instance)));
                }
            }
        }
        if (filter == null) {
            rows.add(MODRINTH);
            rows.add(IMPORT); // 19.11.2026: під Modrinth
        }
    }

    private static final java.util.Set<String> INSTALLED_SECTIONS = new java.util.HashSet<String>(java.util.Arrays.asList(
            "vanilla", "fabric", "quilt", "forge", "neoforge"));

    /** Розділ збірки в «Встановлених»: gummercraft (модпаки з CDN), vanilla, fabric, quilt, forge, neoforge або other. */
    private static String installedSectionOf(Instance instance) {
        if (!instance.isBuiltIn()) return "gummercraft";
        String g = instance.getGroup();
        return g != null && INSTALLED_SECTIONS.contains(g) ? g : "other";
    }

    /**
     * 17.11.2026: підпис над першим рядком розділу в «Встановлених» («ГаммерКрафт», «Ваніль», «Fabric», ...), null в
     * інших рядках. У режимі «Усі» теж null: там підписи свої (сервери і Minecraft) у кожному вигляді.
     */
    public String installedCaption(Instance instance, Instance previous) {
        if (!installedOnly || instance == null || isBrowser(instance)) return null;
        String s = installedSectionOf(instance);
        if (previous != null && s.equals(installedSectionOf(previous))) return null;
        return SharedLocale.tr("launcher.installed.section." + s);
    }

    /** Яку версію групи показати: вибрана за цей запуск, далі остання вибрана (config lastInstance), далі остання запущена, далі найновіша. */
    private Instance chosenOf(List<Instance> members) {
        String group = members.get(0).getGroup();
        String byChoice = groupChoice.get(group);
        String byLast = lastInstance != null ? lastInstance.get() : null;
        for (String wanted : new String[]{byChoice, byLast}) {
            if (wanted != null) {
                for (Instance m : members) {
                    if (wanted.equalsIgnoreCase(m.getName())) {
                        return m;
                    }
                }
            }
        }
        Instance recent = null;
        for (Instance m : members) {
            Date d = m.getLastAccessed();
            if (d != null && (recent == null || d.after(recent.getLastAccessed()))) {
                recent = m;
            }
        }
        return recent != null ? recent : members.get(0);
    }

    /**
     * 17.11.2026: збірка зі своєю назвою (копія, модпак Modrinth або версія, яку гравець перейменував): у виборі
     * версії і під назвою розділу показується ця назва, поруч сіра версія гри.
     */
    public static boolean isNamed(Instance instance) {
        return isDerived(instance) || instance != null && instance.getCustomTitle() != null
                && !instance.getCustomTitle().trim().isEmpty() && instance.isBuiltIn();
    }

    /** 17.11.2026: копія чи збірка Modrinth, що стоїть у виборі версії розділу під чистою версією. */
    /**
     * 20.11.2026: рядок каталогу («Додати інсталяцію»), який лише ставить нову інсталяцію: ваніль і завантажувачі.
     * Кнопка завжди «Встановити», стан «встановлено» не показується, повторне встановлення робить ще одну поряд.
     * Сервери GummerCraft і StalDayZ сюди не входять: у каталозі їх і ставлять, і запускають.
     */
    public boolean isFreshInstall(Instance instance) {
        return !installedOnly && instance != null && instance.isBuiltIn() && !isBrowser(instance);
    }

    public static boolean isDerived(Instance instance) {
        return instance != null && (instance.isCopy() || instance.isMrpack());
    }

    /**
     * 17.11.2026: підпис у виборі версії і під назвою розділу: «Minecraft 1.21.1», для «музею» Vanilla сама назва
     * («Pre-classic rd-132211»), для копії і збірки Modrinth її назва («Pixelmon», «Мій Forge»).
     */
    public String pickerTitleOf(Instance instance) {
        if (isNamed(instance)) return instance.getTitle() != null ? instance.getTitle() : instance.getName();
        String v = versionLabelOf(instance);
        return !v.isEmpty() && Character.isDigit(v.charAt(0)) ? "Minecraft " + v : v;
    }

    /** Версія Minecraft копії чи збірки Modrinth («1.16.5») для сірого підпису поруч з назвою, або null. */
    public String derivedVersionOf(Instance instance) {
        if (!isNamed(instance)) return null;
        String v = versionLabelOf(instance);
        return v.isEmpty() || !Character.isDigit(v.charAt(0)) ? null : v;
    }

    /** Гравець чи лаунчер знову вибирає збірку на ім'я: якщо вона в групі, рядок групи показуватиме саме її. true, якщо вибір у групі змінився. */
    public boolean chooseByName(String name) {
        if (name == null) return false;
        synchronized (instances) {
            for (Instance i : instances.getInstances()) {
                if (name.equalsIgnoreCase(i.getName()) && i.getGroup() != null) {
                    return !name.equalsIgnoreCase(groupChoice.put(i.getGroup(), i.getName()));
                }
            }
        }
        return false;
    }

    /** Версія паку для вибору версії і рядка групи: «Main 1.21» у групі «Main» -> «1.21». */
    public String versionLabelOf(Instance instance) {
        if (instance.isVanilla()) {
            // «музей»: Beta 1.7.3, Alpha 1.2.6, Classic 0.30_01c (числа з назви дали б «1.7.3», як у релізу)
            String id = com.gummer.launcher.vanilla.VanillaVersions.versionIdOf(instance);
            String museum = com.gummer.launcher.vanilla.VanillaVersions.museumName(id);
            if (!museum.equals(id)) return museum;
        }
        // число версії з назви паку (main-1.19.3 -> 1.19.3): назви паків на CDN («Main 26.2») не збігаються з назвою групи
        int[] v = Instance.versionOf(instance);
        if (v.length > 0) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < v.length; i++) {
                sb.append(i > 0 ? "." : "").append(v[i]);
            }
            return sb.toString();
        }
        String groupTitle = instance.getGroup() != null ? instances.getGroupTitles().get(instance.getGroup()) : null;
        return versionLabel(groupTitle != null ? groupTitle : "", instance);
    }

    /** Кольори другого рядка (1.32): сірий - текст і стан, жовтий - є оновлення, зелений - онлайн (однаково в усіх рядках). */
    private static final String GREY = "#969896";
    private static final String AMBER = "#e0b050";
    private static final String GREEN = "#7ccf6a";

    private static String span(String color, String text) {
        return "<span style=\"color:" + color + ";\">" + SwingHelper.htmlEscape(text) + "</span>";
    }

    /** Частина другого рядка: колір і текст (текст може бути скорочений, щоб рядок влазив). */
    private static String[] part(String color, String text) {
        return text == null ? null : new String[]{color, text};
    }

    // ---- 17.11.2026 (власник: «тут щось з текстом»): у Liquid Glass рядок списку фіксованої висоти, а HTML у Swing
    // не вміє «не переносити» (тег nobr він не знає). Довге («Minecraft Pre-classic rd-132211», «The Pixelmon
    // Modpack · 1.16.5») переносилось на зайвий рядок і обрізалось знизу. Тепер таблиця каже ширину колонки
    // (setTextArea), текст міряється тим самим шрифтом і скорочується трикрапкою; повний текст у підказці (tooltipAt).
    private Font fitFont;
    private int fitWidth;
    private boolean fitOff;
    private javax.swing.JLabel measure;
    private final Map<String, String> fitCache = new java.util.LinkedHashMap<String, String>(64, 0.75F, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
            return size() > 256;
        }
    };

    /** Шрифт і ширина колонки тексту (px). true, якщо змінились (тоді таблицю треба перемалювати). */
    public boolean setTextArea(Font font, int width) {
        if (width == fitWidth && (font == null ? fitFont == null : font.equals(fitFont))) return false;
        fitFont = font;
        fitWidth = width;
        fitCache.clear();
        return true;
    }

    private int widthOf(String html) {
        if (measure == null) measure = new javax.swing.JLabel();
        measure.setFont(fitFont);
        measure.setText("<html>" + html + "</html>");
        return measure.getPreferredSize().width;
    }

    private boolean fitting() {
        return !fitOff && fitWidth > 0 && fitFont != null && javax.swing.SwingUtilities.isEventDispatchThread();
    }

    /**
     * Перший крок, що влазить у fitWidth (ширина з кроками лише зменшується), або останній, якщо не влазить жоден.
     * Те саме, що перевіряти кроки по черзі, але ~log2(n) вимірів замість n.
     */
    private int firstFit(java.util.List<String> steps) {
        int last = steps.size() - 1;
        if (widthOf(steps.get(0)) <= fitWidth || last == 0) return 0;
        if (widthOf(steps.get(last)) > fitWidth) return last;
        int lo = 0, hi = last; // lo не влазить, hi влазить
        while (hi - lo > 1) {
            int mid = (lo + hi) >>> 1;
            if (widthOf(steps.get(mid)) <= fitWidth) hi = mid;
            else lo = mid;
        }
        return hi;
    }

    private static String shorter(String s) {
        if (s.endsWith("…")) s = s.substring(0, s.length() - 1);
        s = s.substring(0, Math.max(1, s.length() - 1)).trim();
        return s + "…";
    }

    /** Перший рядок: назва (шрифт 13) і онлайн; назва скорочується, якщо не влазить. */
    private String titleLine(String title, Instance instance) {
        String badge = onlineBadge(instance);
        String html = "<span style=\"font-size:" + fs(13) + "\">&nbsp;" + SwingHelper.htmlEscape(title) + "</span>" + badge;
        if (!fitting()) return html;
        String key = "t|" + fitWidth + "|" + html;
        String cached = fitCache.get(key);
        if (cached != null) return cached;
        // 18.11.2026: ті самі кроки скорочення, що й раніше (по символу), але вимірюється не кожен, а двійковим
        // пошуком: вимір HTML ~1-3 мс, а довга назва давала десятки вимірів (сторож EdtWatch: 0,15 с на першому кадрі)
        java.util.List<String> steps = new ArrayList<String>();
        String t = title;
        steps.add(html);
        while (t.length() > 2 && steps.size() < 400) {
            t = shorter(t);
            steps.add("<span style=\"font-size:" + fs(13) + "\">&nbsp;" + SwingHelper.htmlEscape(t) + "</span>" + badge);
        }
        String out = steps.get(firstFit(steps));
        fitCache.put(key, out);
        return out;
    }

    /** Другий рядок: частини через сіру крапку, шрифт 11; якщо не влазить, скорочується найдовша частина. */
    private String line(String[]... parts) {
        java.util.List<String[]> list = new ArrayList<String[]>();
        for (String[] p : parts) if (p != null && p[1] != null && !p[1].isEmpty()) list.add(new String[]{p[0], p[1]});
        if (list.isEmpty()) return "";
        String body = lineBody(list);
        if (fitting()) {
            String key = "l|" + fitWidth + "|" + body;
            String cached = fitCache.get(key);
            if (cached != null) {
                body = cached;
            } else {
                // 18.11.2026: кроки ті самі (найдовша частина на символ), вимір двійковим пошуком (див. titleLine)
                java.util.List<String> steps = new ArrayList<String>();
                steps.add(body);
                while (steps.size() < 400) {
                    String[] longest = null;
                    for (String[] p : list) if (longest == null || p[1].length() > longest[1].length()) longest = p;
                    if (longest == null || longest[1].length() <= 2) break;
                    longest[1] = shorter(longest[1]);
                    steps.add(lineBody(list));
                }
                body = steps.get(firstFit(steps));
                fitCache.put(key, body);
            }
        }
        return "<br />" + body;
    }

    private String lineBody(java.util.List<String[]> parts) {
        StringBuilder sb = new StringBuilder();
        for (String[] p : parts) {
            if (sb.length() > 0) sb.append(span(GREY, " · "));
            sb.append(span(p[0], p[1]));
        }
        // 20.11.2026 (власник: «просканити лаунчер на ... відсутнє вертикальне центрування, кнопки які хуй зна куди
        // залізли»): відступ той самий, що в назви (один &nbsp; базовим шрифтом), раніше три і другий рядок стояв правіше
        // 20.11.2026, 1.36.6: &nbsp; усередині спану (і в назві, і в значку онлайну): висоту рядків задають лише явні розміри,
        // а не базовий шрифт таблиці (власник: «"доступне оновлення" тепер дуже низько»)
        return "<span style=\"font-size:" + fs(11) + ";\">&nbsp;" + sb + "</span>";
    }

    /** Повний текст рядка для підказки, якщо в списку він скорочений; інакше null. */
    public String tooltipAt(int row) {
        if (row < 0 || row >= getRowCount()) return null;
        Object shown = getValueAt(row, 1);
        fitOff = true;
        Object full;
        try {
            full = getValueAt(row, 1);
        } finally {
            fitOff = false;
        }
        String updates = updatesText(getInstanceAt(row)); // 17.11.2026: що означає зелена стрілка на іконці
        if (full == null || full.equals(shown)) return updates;
        return (updates != null ? full + " · " + updates : full.toString()).replaceAll("<br\\s*/?>", " · ").replaceAll("<[^>]+>", "").replace("&nbsp;", " ")
                .replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"")
                .replaceAll("\\s+", " ").replace(" ·  · ", " · ").trim();
    }

    /**
     * Онлайн сервера паку: зелене «● 3» у першому рядку поруч з назвою, однаково для всіх рядків. У другому рядку
     * (версія, стан) на Java 8 зі старих exe «· ● 3 онлайн» не влазило і переносилось. Повне «3 з 20 гравців онлайн»
     * видно біля кнопки «Запуск».
     */
    private String onlineBadge(Instance instance) {
        String online = onlineLines.get(instance.getName());
        return online != null ? "<span style=\"color:" + GREEN + ";font-size:" + fs(11) + ";\">&nbsp;&nbsp;● " + SwingHelper.htmlEscape(online) + "</span>" : "";
    }

    /** Другий рядок групи: «Minecraft 1.21». Опис групи (subtitle з meta) у вузький список не влазить, його не показуємо. */
    private String groupLine(Instance instance) {
        // 17.11.2026: старі версії «музею» («Pre-classic rd-132211») з префіксом «Minecraft» переносились у третій рядок
        if (isNamed(instance)) {
            // копія, збірка Modrinth чи перейменована версія: її назва, поруч сіра версія гри
            String v = derivedVersionOf(instance);
            return line(part("#d6d8dc", pickerTitleOf(instance)), part(GREY, v));
        }
        return line(part(GREY, pickerTitleOf(instance)));
    }

    /** Підпис версії під назвою групи: «Main 1.21» у групі «Main» -> «1.21». */
    private static String versionLabel(String groupTitle, Instance instance) {
        String title = instance.getTitle() != null ? instance.getTitle() : instance.getName();
        String prefix = groupTitle + " ";
        return title.regionMatches(true, 0, prefix, 0, prefix.length()) && title.length() > prefix.length()
                ? title.substring(prefix.length()) : title;
    }

    /** Назва рядка без розмітки: назва групи (Vanilla, GummerCraft) або назва паку. Для вікна редизайну 2 (16.11.2026). */
    public String titleOf(Instance instance) {
        if (instance == null) return "";
        String group = groupTitleFor(instance);
        return group != null ? group : instance.getTitle() != null ? instance.getTitle() : instance.getName();
    }

    /** Назва групи для рядка (зі списку v2), або null, якщо пак показується сам по собі. */
    private String groupTitleFor(Instance instance) {
        if (!grouped || installedOnly || instance.getGroup() == null || getGroupMembers(instance).size() < 2) {
            return null;
        }
        String title = instances.getGroupTitles().get(instance.getGroup());
        if (title == null) {
            // 17.11.2026: у розділі може бути вибрана копія чи збірка Modrinth: назва за групою, а не за самою збіркою
            title = com.gummer.launcher.manage.InstanceCopies.sectionTitle(instance.getGroup());
        }
        if (title == null && com.gummer.launcher.vanilla.VanillaVersions.GROUP.equals(instance.getGroup())) {
            title = "Vanilla";
        }
        if (title == null && com.gummer.launcher.fabric.FabricVersions.GROUP.equals(instance.getGroup())) {
            title = "Fabric";
        }
        if (title == null && instance.isQuilt()) {
            title = "Quilt";
        }
        if (title == null && instance.isForgeLike()) {
            title = com.gummer.launcher.forge.ForgeVersions.kindOf(instance).title;
        }
        return title != null ? title : instance.getGroup();
    }

    @Override
    public String getColumnName(int columnIndex) {
        switch (columnIndex) {
            case 0:
                return "";
            case 1:
                return SharedLocale.tr("launcher.modpackColumn");
            default:
                return null;
        }
    }

    @Override
    public Class<?> getColumnClass(int columnIndex) {
        switch (columnIndex) {
            case 0:
                return ImageIcon.class;
            case 1:
                return String.class;
            default:
                return null;
        }
    }

    @Override
    public void setValueAt(Object value, int rowIndex, int columnIndex) {
        switch (columnIndex) {
            case 0:
                getInstanceAt(rowIndex).setSelected((boolean) (Boolean) value);
                break;
            case 1:
            default:
                break;
        }
    }

    @Override
    public boolean isCellEditable(int rowIndex, int columnIndex) {
        switch (columnIndex) {
            case 0:
                return false;
            case 1:
                return false;
            default:
                return false;
        }
    }

    @Override
    public int getRowCount() {
        return grouped ? rows.size() : instances.size();
    }

    @Override
    public int getColumnCount() {
        return 2;
    }

    // ---- 17.11.2026 (власник: «в повноекранці масштабувати (збільшити)» список у Liquid Glass): множник розміру
    // тексту і іконок; рядки і підписи збільшує сама таблиця (CardInstanceTable.setZoom)
    private double zoom = 1.0;
    private final Map<Icon, Icon> zoomIcons = new java.util.WeakHashMap<Icon, Icon>();

    public void setZoom(double zoom) {
        if (zoom == this.zoom) return;
        this.zoom = zoom;
        fitCache.clear();
        zoomIcons.clear();
        // 17.11.2026: не fireTableDataChanged, бо той скидав виділену збірку, і при розгортанні скла на мить гасла
        // «ЗАПУСК», ховався вибір версії і перемальовувався фон (власник: «так різко перебудовується»)
        if (getRowCount() > 0) fireTableRowsUpdated(0, getRowCount() - 1);
    }

    public double getZoom() {
        return zoom;
    }

    private String fs(int size) {
        // 20.11.2026: зі шрифтом Inter (FancyLauncher, властивість gummer.font.inter) на 1 менше: Inter 12 за висотою
        // малих літер як Segoe UI 13, а на тому самому кеглі на ~13% ширший, і назви в списку обрізались би
        if (Boolean.getBoolean("gummer.font.inter")) size -= 1;
        return zoom == 1.0 ? String.valueOf(size) : String.valueOf((int) Math.round(size * zoom));
    }

    /** Іконка, збільшена малюванням (CrispIcon і далі бере пікселі екрана, тож не розмивається). */
    private static final class ZoomIcon implements Icon {
        private final Icon inner;
        private final double zoom;

        ZoomIcon(Icon inner, double zoom) {
            this.inner = inner;
            this.zoom = zoom;
        }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.translate(x, y);
            g2.scale(zoom, zoom);
            inner.paintIcon(c, g2, 0, 0);
            g2.dispose();
        }

        @Override
        public int getIconWidth() {
            return (int) Math.round(inner.getIconWidth() * zoom);
        }

        @Override
        public int getIconHeight() {
            return (int) Math.round(inner.getIconHeight() * zoom);
        }
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        Object v = valueAt(rowIndex, columnIndex);
        if (columnIndex == 0 && zoom != 1.0 && v instanceof Icon) {
            Icon icon = (Icon) v;
            Icon z = zoomIcons.get(icon);
            if (z == null) zoomIcons.put(icon, z = new ZoomIcon(icon, zoom));
            v = z;
        }
        // 17.11.2026 (власник: «а бейдж не влазить»): оновлення збірки це зелена стрілка на куті іконки, а не текст у
        // підписі (там він скорочувався до «є оновленн...»); повний текст у підказці (tooltipAt) і біля «Запуску»
        if (columnIndex == 0 && v instanceof Icon && updatesText(getInstanceAt(rowIndex)) != null) {
            Icon icon = (Icon) v;
            synchronized (badgeIcons) {
                Icon b = badgeIcons.get(icon);
                if (b == null) badgeIcons.put(icon, b = new UpdateBadgeIcon(icon));
                return b;
            }
        }
        return v;
    }

    private final java.util.Map<Icon, Icon> badgeIcons = new java.util.WeakHashMap<Icon, Icon>();

    /** Іконка збірки з зеленим кружечком і стрілкою вгору в правому нижньому куті (є оновлення). */
    public static final class UpdateBadgeIcon implements Icon {
        private final Icon base;

        public UpdateBadgeIcon(Icon base) {
            this.base = base;
        }

        @Override
        public void paintIcon(java.awt.Component c, java.awt.Graphics g, int x, int y) {
            base.paintIcon(c, g, x, y);
            paintBadge(g, x, y, getIconWidth(), getIconHeight());
        }

        /** Сам кружечок у межах іконки w x h (його малює і полиця Бібліотеки). */
        public static void paintBadge(java.awt.Graphics g, int x, int y, int w, int h) {
            java.awt.Graphics2D g2 = (java.awt.Graphics2D) g.create();
            try {
                g2.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(java.awt.RenderingHints.KEY_STROKE_CONTROL, java.awt.RenderingHints.VALUE_STROKE_PURE);
                double d = Math.max(11, Math.min(w, h) * 0.46);
                double bx = x + w - d, by = y + h - d, ring = Math.max(1.5, d * 0.12);
                g2.setColor(new java.awt.Color(16, 18, 22));
                g2.fill(new java.awt.geom.Ellipse2D.Double(bx - ring, by - ring, d + ring * 2, d + ring * 2));
                g2.setColor(new java.awt.Color(92, 196, 84));
                g2.fill(new java.awt.geom.Ellipse2D.Double(bx, by, d, d));
                g2.setColor(java.awt.Color.WHITE);
                g2.setStroke(new java.awt.BasicStroke((float) Math.max(1.4, d * 0.13), java.awt.BasicStroke.CAP_ROUND, java.awt.BasicStroke.JOIN_ROUND));
                double cx = bx + d / 2, top = by + d * 0.26, bottom = by + d * 0.74, arm = d * 0.2;
                java.awt.geom.Path2D.Double arrow = new java.awt.geom.Path2D.Double();
                arrow.moveTo(cx, bottom);
                arrow.lineTo(cx, top);
                arrow.moveTo(cx - arm, top + arm);
                arrow.lineTo(cx, top);
                arrow.lineTo(cx + arm, top + arm);
                g2.draw(arrow);
            } finally {
                g2.dispose();
            }
        }

        @Override
        public int getIconWidth() {
            return base.getIconWidth();
        }

        @Override
        public int getIconHeight() {
            return base.getIconHeight();
        }
    }

    @SuppressWarnings({"ConstantConditions"})
    private Object valueAt(int rowIndex, int columnIndex) {
        Instance instance;
        switch (columnIndex) {
            case 0:
                instance = getInstanceAt(rowIndex);
                if (isAddRow(instance)) return ADD_ICON;
                if (isImportRow(instance)) return IMPORT_ICON;
                if (isBrowser(instance)) {
                    return modrinthIcon;
                }
                // 17.11.2026 (власник: «в форджі змінилась іконка. Так не треба, іконка нехай міняється лише в
                // встановлені збірки»): рядок розділу завжди з іконкою розділу, навіть коли в ньому вибрано копію чи
                // модпак; своя іконка копії і іконка модпака лише в списку «Встановлені», де кожна збірка окремо
                boolean sectionRow = groupTitleFor(instance) != null;
                if (sectionRow && isDerived(instance)) {
                    Icon section = sectionIconOf(instance.getGroup());
                    if (section != null) return section;
                }
                // своя іконка копії збірки (InstanceArt), кеш за картинкою: список малюється часто
                java.awt.image.BufferedImage art = sectionRow ? null : com.gummer.launcher.manage.InstanceArt.icon(instance);
                if (art != null) {
                    synchronized (artIcons) {
                        Icon cached = artIcons.get(art);
                        if (cached == null) artIcons.put(art, cached = new CrispIcon(art, 32, 32));
                        return cached;
                    }
                }
                // 1.33 (власник 13.11): у трьох розділів завжди своя іконка, без іконки «не встановлено»:
                // Vanilla - логотип Minecraft (MinecraftIcon.webp власника), GummerCraft і StalDayZ - іконки їхніх паків
                if (instance.isVanilla()) {
                    return vanillaIcon;
                }
                if (instance.isFabric()) {
                    return fabricIcon;
                }
                if (instance.isForgeLike()) {
                    return com.gummer.launcher.forge.ForgeVersions.kindOf(instance) == com.gummer.launcher.forge.ForgeVersions.Kind.FORGE ? forgeIcon : neoforgeIcon;
                }
                if (instance.isQuilt()) {
                    return quiltIcon;
                }
                if (instance.isMrpack()) {
                    Icon own = packIcon(instance);
                    return own != null ? own : modrinthIcon;
                }
                if ("main".equals(instance.getGroup())) {
                    return gummercraftIcon;
                }
                if (isStalDayZ(instance)) {
                    return staldayzIcon;
                }
                if (instance.isLocal()) {
                    File icon = new File(instance.getContentDir(), "instance_icon.png");
                    if (icon.exists()) {
                        return new ImageIcon(SwingExtras.readIconImage(icon)
                                .getScaledInstance(32, 32, Image.SCALE_SMOOTH));
                    }
                }
                return instanceIcon;
            case 1:
                instance = getInstanceAt(rowIndex);
                //return instance.getTitle();
                String groupTitle = groupTitleFor(instance);
                if (groupTitle != null) {
                    // рядок групи: назва групи, під нею вибрана версія і її стан
                    // 20.11.2026 (власник: «прибрати написи з описом останньої запущеної збірки під лоадером»): у каталозі
                    // без другого рядка, вибрану версію видно у виборі версії
                    return "<html>" + titleLine(groupTitle, instance)
                            + (installedOnly ? groupLine(instance) : "")
                            + "</html>"; // стан версії (встановлено, оновлення) видно у виборі версії: у вузькому списку не влазить
                }
                // «Встановлені»: Vanilla як у виборі версії («Pre-classic rd-132211» без «Minecraft», 17.11.2026)
                String title = installedOnly && instance.isVanilla() ? pickerTitleOf(instance) : instance.getTitle();
                // 20.11.2026: у каталозі другий рядок лишається лише в рядків-сторінок (Modrinth, імпорт)
                String addendum = installedOnly || isBrowser(instance) ? getAddendum(instance) : "";
                return "<html>" + titleLine(title, instance) + addendum + "</html>";
            default:
                return null;
        }
    }

    /** Іконка розділу завантажувача за групою (vanilla, fabric, quilt, forge, neoforge), або null. */
    private Icon sectionIconOf(String group) {
        if (group == null) return null;
        switch (group) {
            case "vanilla":
                return vanillaIcon;
            case "fabric":
                return fabricIcon;
            case "quilt":
                return quiltIcon;
            case "forge":
                return forgeIcon;
            case "neoforge":
                return neoforgeIcon;
            default:
                return null;
        }
    }

    /**
     * 17.11.2026 (власник: «бейдж "є оновлення модів" на картці збірки - го»): є новіша версія модпака Modrinth чи
     * своїх модів гравця (UpdateBadges, перевірка у фоні). Текст бейджа оновлень для вікон («є оновлення модпака 1.2», «оновлення модів: 3»), або null. */
    public static String updatesText(Instance instance) {
        if (instance == null || isBrowser(instance)) return null;
        com.gummer.launcher.manage.UpdateBadges.Info u = com.gummer.launcher.manage.UpdateBadges.of(instance);
        if (u == null || !u.any()) return null;
        if (u.pack != null) return SharedLocale.tr("launcher.packUpdateHint", u.pack);
        return SharedLocale.tr("launcher.modUpdatesHint", u.mods);
    }

    /** Другий рядок паку без групи: стан (сірий, оновлення жовтим); онлайн у першому рядку, як у групах. */
    private String getAddendum(Instance instance) {
        if (isAddRow(instance)) {
            return line(part(GREY, SharedLocale.tr("launcher.addBuildSub")));
        }
        if (isImportRow(instance)) {
            return line(part(GREY, SharedLocale.tr("launcher.importRowSub")));
        }
        if (isBrowser(instance)) {
            return line(part(GREY, SharedLocale.tr("launcher.modrinthRowSub")));
        }
        // «Встановлені»: у копії і модпака під назвою розділ і версія гри («Forge 1.16.5»)
        String where = null;
        if (installedOnly && isNamed(instance)) {
            String sec = com.gummer.launcher.manage.InstanceCopies.sectionTitle(instance.getGroup());
            String v = derivedVersionOf(instance);
            where = sec == null ? v : v != null ? sec + " " + v : sec;
        }
        String[] state;
        if (instance.isOrphaned()) {
            state = part(GREY, SharedLocale.tr("launcher.orphanedHint"));
        } else if (!instance.isLocal()) {
            state = part(GREY, SharedLocale.tr("launcher.notInstalledHint"));
        } else if (!instance.isInstalled()) {
            state = part(AMBER, SharedLocale.tr("launcher.requiresUpdateHint"));
        } else if (instance.isUpdatePending()) {
            state = part(AMBER, SharedLocale.tr("launcher.updatePendingHint"));
        } else {
            state = null; // оновлення показує стрілка на іконці (17.11.2026)
        }
        // 17.11.2026 (власник: «перевір що ніде нічого не вилазить»): у вузькому списку «Встановлені» «Fabric 26.2 ·
        // (потрібне оновлення)» скорочувалось до «(потрібне...»; розділ і так у заголовку групи, тож лишається стан
        if (where != null && state != null) return line(state);
        return line(part(GREY, where), state);
    }

}
