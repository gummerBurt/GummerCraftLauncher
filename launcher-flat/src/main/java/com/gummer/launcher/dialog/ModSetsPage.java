/*
 * Copyright (C) 2021-2026 GummerCraft <http://gummercraft.fun>
 */

package com.gummer.launcher.dialog;

import com.gummer.launcher.look.LookTheme;
import com.gummer.launcher.look.LookUi;
import com.gummer.launcher.manage.ModSets;
import com.gummer.launcher.manage.ModrinthBrowse;
import com.skcraft.launcher.Instance;
import com.skcraft.launcher.swing.SwingHelper;
import net.miginfocom.swing.MigLayout;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.skcraft.launcher.util.SharedLocale.tr;

/**
 * 26.11.2026 (власник: «Набори модів» у «Керуванні збіркою», «робимо вже», ТЗ «Лаунчер - робоча/НАБОРИ-МОДІВ.md»):
 * підвкладка «Набори модів» розділу «Моди», між «Мої моди» і «Знайти моди». Картки: «Гра з другом» (помічник
 * {@link FriendPlayDialog}), «ФПС» (та сама картка, що в «Продуктивності»), «Зручність», «Мапа», «Шейдери»,
 * «Атмосфера і звук» ({@link ModSets}). Що є саме для цієї збірки, рахується у фоні при першому відкритті: кожна група
 * своєю галочкою, наявні аналоги позначено «уже є», чого немає під цю гру, сіре з поясненням.
 */
final class ModSetsPage extends JPanel {

    private static final Logger log = Logger.getLogger(ModSetsPage.class.getName());
    private static final String[] SETS = {ModSets.COMFORT, ModSets.MAP, ModSets.SHADERS, ModSets.AMBIENCE};

    private final Window owner;
    private final Instance instance;
    private final LookTheme.Palette pal = LookTheme.palette();
    private final Runnable changed;
    private final List<SetCard> cards = new ArrayList<SetCard>();
    private boolean loaded;

    /**
     * @param friend  відкрити «Гру з другом», або null (немає для цієї збірки)
     * @param fps     картка «ФПС» з «Продуктивності» (новий екземпляр), або null
     * @param changed після встановлення чи прибирання: перечитати «Мої моди»
     */
    ModSetsPage(Window owner, Instance instance, Runnable friend, JComponent fps, Runnable changed) {
        super(new MigLayout("insets 0, wrap 1, gap 0 12, fillx", "[grow,fill]", ""));
        setOpaque(false);
        this.owner = owner;
        this.instance = instance;
        this.changed = changed;
        JPanel head = card();
        head.add(LookUi.title(tr("sets.title"), 15f));
        head.add(LookUi.body(tr("sets.text"), true), "wmin 0");
        add(head, "wmin 0");
        if (friend != null) {
            JPanel c = card();
            c.add(LookUi.title(tr("friend.title"), 15f));
            c.add(LookUi.body(tr("sets.friend.text"), true), "wmin 0");
            JButton b = LookUi.secondary(new JButton(tr("friend.button")));
            b.setFocusable(false);
            b.addActionListener(e -> friend.run());
            JPanel row = new JPanel(new MigLayout("insets 0, gap 8", "[][]push", "[32!]"));
            row.setOpaque(false);
            row.add(b, "h 32!");
            // 26.11.2026 (власник: «кнопку "видалити моди мультиплеєра", котра видалятиме моди для ігор з другом. Про всяк»)
            JButton rm = LookUi.secondary(new JButton(tr("sets.friend.remove")));
            rm.setFocusable(false);
            rm.setToolTipText(tr("sets.friend.removeTip"));
            rm.addActionListener(e -> removeFriendMods());
            row.add(rm, "h 32!");
            c.add(row, "gaptop 6, wmin 0");
            add(c, "wmin 0");
        }
        if (fps != null) add(fps, "wmin 0");
        for (String id : SETS) {
            SetCard c = new SetCard(id);
            cards.add(c);
            add(c, "wmin 0");
        }
    }

    /** Перше відкриття вкладки: порахувати, що є для збірки (кожен набір окремо, у фоні). */
    void loadIfNeeded() {
        if (loaded) return;
        loaded = true;
        for (SetCard c : cards) c.load();
    }

    /** «Видалити моди мультиплеєра»: знайти моди гри з другом, показати їх і видалити після «так». */
    private void removeFriendMods() {
        new SwingWorker<List<com.gummer.launcher.manage.InstanceMods.Mod>, Void>() {
            @Override
            protected List<com.gummer.launcher.manage.InstanceMods.Mod> doInBackground() {
                return com.gummer.launcher.manage.FriendPlay.friendMods(instance);
            }

            @Override
            protected void done() {
                List<com.gummer.launcher.manage.InstanceMods.Mod> mods;
                try {
                    mods = get();
                } catch (Exception ex) {
                    SwingHelper.showErrorDialog(owner, tr("fps.failed", String.valueOf(ex.getMessage())), tr("friend.title"));
                    return;
                }
                if (mods.isEmpty()) {
                    SwingHelper.showMessageDialog(owner, tr("sets.friend.removeNone"), tr("friend.title"), null, JOptionPane.INFORMATION_MESSAGE);
                    return;
                }
                List<String> names = new ArrayList<String>();
                for (com.gummer.launcher.manage.InstanceMods.Mod m : mods) names.add(m.file.getName());
                if (!SwingHelper.confirmDialog(owner, tr("sets.friend.removeConfirm", String.join("\n", names)), tr("friend.title"))) return;
                int n = com.gummer.launcher.manage.FriendPlay.removeFriendMods(instance, mods);
                SwingHelper.showMessageDialog(owner, tr("fps.removed", n), tr("friend.title"), null, JOptionPane.INFORMATION_MESSAGE);
                if (changed != null) changed.run();
            }
        }.execute();
    }

    private static JPanel card() {
        return LookUi.card("insets 16 18 16 18, wrap 1, gap 0 4, fillx", "[grow,fill]", "");
    }

    private JCheckBox check(String text, boolean selected) {
        JCheckBox b = new JCheckBox(text, selected);
        b.setOpaque(false);
        b.setForeground(pal.text);
        b.setFocusPainted(false);
        b.putClientProperty("FlatLaf.style", "icon.borderWidth: 1; icon.borderColor: " + LookUi.hex(pal.text3)
                + "; icon.background: " + LookUi.hex(pal.surface2)
                + "; icon.selectedBorderColor: " + LookUi.hex(pal.accent)
                + "; icon.selectedBackground: " + LookUi.hex(pal.accent)
                + "; icon.checkmarkColor: #FFFFFF"
                + "; icon.disabledBorderColor: " + LookUi.hex(pal.line)
                + "; icon.disabledBackground: " + LookUi.hex(pal.surface)
                + "; disabledText: " + LookUi.hex(pal.text3));
        return b;
    }

    static String loaderTitle(String[] loaders) {
        if (loaders == null || loaders.length == 0) return "?";
        String l = loaders[0];
        return l.equals("neoforge") ? "NeoForge" : Character.toUpperCase(l.charAt(0)) + l.substring(1);
    }

    /** Картка одного набору. */
    private final class SetCard extends JPanel {
        final String id;
        final JTextArea status;
        final JPanel rows = new JPanel(new MigLayout("insets 0, wrap 1, gap 0 2, fillx", "[grow,fill]", ""));
        final JButton install = LookUi.primary(new JButton(tr("sets.install")));
        final JButton remove = LookUi.secondary(new JButton(tr("sets.remove")));
        final Map<String, JCheckBox> boxes = new LinkedHashMap<String, JCheckBox>();
        JCheckBox packBox;
        ModSets.Plan plan;

        SetCard(String id) {
            super(new BorderLayout());
            this.id = id;
            setOpaque(false);
            JPanel c = card();
            add(c, BorderLayout.CENTER);
            c.add(LookUi.title(tr("sets." + id + ".title"), 15f));
            c.add(LookUi.body(tr("sets." + id + ".text"), true), "wmin 0");
            rows.setOpaque(false);
            c.add(rows, "wmin 0, gaptop 6");
            status = LookUi.body(tr("sets.checking"), true);
            status.setFont(status.getFont().deriveFont(12f));
            c.add(status, "wmin 0, gaptop 2, hidemode 3");
            JPanel actions = new JPanel(new MigLayout("insets 0, gap 8, hidemode 3", "[][]push", "[32!]"));
            actions.setOpaque(false);
            install.setEnabled(false);
            install.setFocusable(false);
            remove.setFocusable(false);
            actions.add(install, "h 32!");
            actions.add(remove, "h 32!");
            c.add(actions, "gaptop 8, wmin 0");
            remove.setVisible(!ModSets.added(instance, id).isEmpty());
            install.addActionListener(e -> install());
            remove.addActionListener(e -> remove());
        }

        void load() {
            new SwingWorker<ModSets.Plan, Void>() {
                @Override
                protected ModSets.Plan doInBackground() throws Exception {
                    return ModSets.plan(instance, id);
                }

                @Override
                protected void done() {
                    try {
                        show(get());
                    } catch (Exception ex) {
                        log.log(Level.WARNING, "Mod set plan failed: " + id, ex);
                        status.setText(tr("sets.failed"));
                    }
                }
            }.execute();
        }

        void show(ModSets.Plan p) {
            plan = p;
            rows.removeAll();
            boxes.clear();
            String game = ModrinthBrowse.gameVersionOf(instance);
            String loader = loaderTitle(ModrinthBrowse.loadersOf(ModrinthBrowse.Kind.MOD, instance));
            for (ModSets.Group g : p.groups) {
                String name = tr("sets." + id + "." + g.id);
                JCheckBox b;
                if (g.state == ModSets.State.READY) {
                    b = check(name + ": " + g.titles(), true);
                    boxes.put(g.id, b);
                } else if (g.state == ModSets.State.HAVE) {
                    b = check(name + ": " + tr("sets.have", g.titles()), true);
                    b.setEnabled(false);
                } else {
                    b = check(name + ": " + tr("sets.none", loader, game != null ? game : "?"), false);
                    b.setEnabled(false);
                }
                b.addActionListener(e -> updateInstall());
                rows.add(b, "wmin 0");
            }
            if (ModSets.SHADERS.equals(id)) {
                if (p.pack != null) {
                    String preset = tr("fps.preset." + p.preset.name().toLowerCase(java.util.Locale.ROOT));
                    packBox = check(tr("sets.shaders.pack", p.pack.title() + (p.pack.curse ? " (CurseForge)" : ""), preset), true);
                    packBox.addActionListener(e -> updateInstall());
                    rows.add(packBox, "wmin 0");
                } else {
                    packBox = null;
                }
            }
            String note = null;
            if (p.count(ModSets.State.READY) == 0 && (packBox == null)) {
                note = p.count(ModSets.State.NONE) == p.groups.size()
                        ? (ModSets.SHADERS.equals(id) ? shaderNone(loader, game) : tr("sets.allNone", loader, game != null ? game : "?"))
                        : tr("sets.allHave");
            } else if (ModSets.SHADERS.equals(id) && p.optifine) {
                note = tr("sets.shaders.optifine");
            }
            status.setText(note == null ? "" : note);
            status.setVisible(note != null);
            remove.setVisible(!ModSets.added(instance, id).isEmpty());
            updateInstall();
            revalidate();
            repaint();
        }

        void updateInstall() {
            boolean any = packBox != null && packBox.isSelected();
            for (JCheckBox b : boxes.values()) any |= b.isSelected();
            install.setEnabled(plan != null && any);
        }

        void install() {
            final Set<String> groups = new LinkedHashSet<String>();
            List<String> what = new ArrayList<String>();
            for (Map.Entry<String, JCheckBox> e : boxes.entrySet()) {
                if (!e.getValue().isSelected()) continue;
                groups.add(e.getKey());
                what.add(plan.group(e.getKey()).titles());
            }
            final boolean pack = packBox != null && packBox.isSelected();
            if (pack) what.add(plan.pack.title());
            if (!SwingHelper.confirmDialog(owner, tr("sets.confirm", tr("sets." + id + ".title"), String.join(", ", what)), tr("sets.title"))) return;
            install.setEnabled(false);
            install.setText(tr("crash.action.working"));
            final ModSets.Plan p = plan;
            new SwingWorker<ModSets.Result, Void>() {
                @Override
                protected ModSets.Result doInBackground() throws Exception {
                    return ModSets.install(instance, p, groups, pack);
                }

                @Override
                protected void done() {
                    install.setText(tr("sets.install"));
                    ModSets.Result r;
                    try {
                        r = get();
                    } catch (Exception ex) {
                        Throwable t = ex.getCause() != null ? ex.getCause() : ex;
                        log.log(Level.WARNING, "Mod set install failed: " + id, t);
                        SwingHelper.showErrorDialog(owner, tr("fps.failed", String.valueOf(t.getMessage())), tr("sets.title"));
                        updateInstall();
                        return;
                    }
                    String msg = r.installed.isEmpty() ? tr("fps.addedNone") : tr("fps.added", String.join(", ", r.installed));
                    if (r.pack != null) msg += "\n\n" + tr("sets.shaders.packOn", r.pack);
                    if (!r.skipped.isEmpty()) msg += "\n\n" + tr("fps.skipped") + "\n" + String.join("\n", r.skipped);
                    SwingHelper.showMessageDialog(owner, msg, tr("sets." + id + ".title"), null, JOptionPane.INFORMATION_MESSAGE);
                    if (changed != null) changed.run();
                    status.setText(tr("sets.checking"));
                    status.setVisible(true);
                    load();
                }
            }.execute();
        }

        void remove() {
            List<String> files = ModSets.added(instance, id);
            if (!SwingHelper.confirmDialog(owner, tr("sets.removeConfirm", String.join(", ", files)), tr("sets." + id + ".title"))) return;
            try {
                int n = ModSets.remove(instance, id);
                SwingHelper.showMessageDialog(owner, tr("fps.removed", n), tr("sets." + id + ".title"), null, JOptionPane.INFORMATION_MESSAGE);
                if (changed != null) changed.run();
                status.setText(tr("sets.checking"));
                status.setVisible(true);
                load();
            } catch (java.io.IOException ex) {
                SwingHelper.showErrorDialog(owner, tr("fps.failed", String.valueOf(ex.getMessage())), tr("sets.title"));
            }
        }
    }

    /** Чому шейдерів немає під цю збірку, і що можна зробити. */
    static String shaderNone(String loader, String game) {
        return tr("shaders.helper.none", loader, game != null ? game : "?");
    }
}
