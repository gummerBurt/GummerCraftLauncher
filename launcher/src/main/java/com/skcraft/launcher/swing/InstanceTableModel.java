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
    private final Icon customInstanceIcon;
    private final Icon downloadIcon;

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
        instanceIcon = SwingHelper.createIcon(Launcher.class, "instance_icon.png", 32, 32);
        customInstanceIcon = instanceIcon;
        downloadIcon = SwingHelper.createIcon(Launcher.class, "download_icon.png", 32, 32);
    }

    public void update() {
        instances.sort();
        rebuildRows();
        fireTableDataChanged();
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
        synchronized (instances) {
            for (Instance i : instances.getInstances()) {
                if (instance.getGroup().equals(i.getGroup()) && !i.isOrphaned()) {
                    members.add(i);
                }
            }
        }
        return members;
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

    /** Версія паку для вибору версії і рядка групи: «Main 1.21» у групі «Main» -> «1.21». */
    public String versionLabelOf(Instance instance) {
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

    /** Другий рядок: частини через сіру крапку, шрифт 11. */
    private static String line(String... parts) {
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p == null) continue;
            if (sb.length() > 0) sb.append(span(GREY, " · "));
            sb.append(p);
        }
        return sb.length() == 0 ? "" : "<br /> &nbsp;&nbsp;&nbsp;<span style=\"font-size:11;\">" + sb + "</span>";
    }

    /** Онлайн сервера паку зеленим, або null. */
    private String onlinePart(Instance instance) {
        String online = onlineLines.get(instance.getName());
        return online != null ? span(GREEN, "● " + online) : null;
    }

    /** Другий рядок групи: «Minecraft 1.21 · ● 3 онлайн». Опис групи (subtitle з meta) у вузький список не влазить, його не показуємо. */
    private String groupLine(Instance instance) {
        return line(span(GREY, "Minecraft " + versionLabelOf(instance)), onlinePart(instance));
    }

    /** Підпис версії під назвою групи: «Main 1.21» у групі «Main» -> «1.21». */
    private static String versionLabel(String groupTitle, Instance instance) {
        String title = instance.getTitle() != null ? instance.getTitle() : instance.getName();
        String prefix = groupTitle + " ";
        return title.regionMatches(true, 0, prefix, 0, prefix.length()) && title.length() > prefix.length()
                ? title.substring(prefix.length()) : title;
    }

    /** Назва групи для рядка (зі списку v2), або null, якщо пак показується сам по собі. */
    private String groupTitleFor(Instance instance) {
        if (!grouped || instance.getGroup() == null || getGroupMembers(instance).size() < 2) {
            return null;
        }
        String title = instances.getGroupTitles().get(instance.getGroup());
        if (title == null && com.gummer.launcher.vanilla.VanillaVersions.GROUP.equals(instance.getGroup())) {
            title = "Vanilla";
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

    @Override
    @SuppressWarnings({"ConstantConditions"})
    public Object getValueAt(int rowIndex, int columnIndex) {
        Instance instance;
        switch (columnIndex) {
            case 0:
                instance = getInstanceAt(rowIndex);
                if (!instance.isLocal()) {
                    return downloadIcon;
                } else {
                    File icon = new File(instance.getContentDir(), "instance_icon.png");
                    if (icon.exists()) {
                        return new ImageIcon(SwingExtras.readIconImage(icon)
                                .getScaledInstance(32, 32, Image.SCALE_SMOOTH));
                    }
                    return instanceIcon;
                }
            case 1:
                instance = getInstanceAt(rowIndex);
                //return instance.getTitle();
                String groupTitle = groupTitleFor(instance);
                if (groupTitle != null) {
                    // рядок групи: назва групи, під нею вибрана версія і її стан
                    return "<html>&nbsp;" + "<span style=\"font-size:13\">" + SwingHelper.htmlEscape(groupTitle) + "</span>"
                            + groupLine(instance)
                            + "</html>"; // стан версії (встановлено, оновлення) видно у виборі версії: у вузькому списку не влазить
                }
                return "<html>&nbsp;" + "<span style=\"font-size:13\">" + SwingHelper.htmlEscape(instance.getTitle()) + "</span>" + getAddendum(instance) + "</html>";
            default:
                return null;
        }
    }

    /** Другий рядок паку без групи: стан (сірий, оновлення жовтим) і онлайн зеленим, як у рядках груп. */
    private String getAddendum(Instance instance) {
        String state;
        if (instance.isOrphaned()) {
            state = span(GREY, SharedLocale.tr("launcher.orphanedHint"));
        } else if (!instance.isLocal()) {
            state = span(GREY, SharedLocale.tr("launcher.notInstalledHint"));
        } else if (!instance.isInstalled()) {
            state = span(AMBER, SharedLocale.tr("launcher.requiresUpdateHint"));
        } else if (instance.isUpdatePending()) {
            state = span(AMBER, SharedLocale.tr("launcher.updatePendingHint"));
        } else {
            state = null;
        }
        return line(state, onlinePart(instance));
    }

}
