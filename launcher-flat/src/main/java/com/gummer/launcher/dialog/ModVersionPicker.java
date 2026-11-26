/*
 * Copyright (C) 2021-2026 GummerCraft <http://gummercraft.fun>
 */

package com.gummer.launcher.dialog;

import com.gummer.launcher.look.LookTheme;
import com.gummer.launcher.look.LookUi;
import com.gummer.launcher.manage.ModrinthBrowse;
import net.miginfocom.swing.MigLayout;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static com.skcraft.launcher.util.SharedLocale.tr;

/**
 * 26.11.2026 (власник: «у встановленні модів дозволь обирати версії модів перед установкою, бо зараз коли гравець пише
 * встановити автоматично встановлюється остання версія і виходять такі проблеми»; гравцю найновіший Sodium 0.9.3 не
 * працював з Iris 1.11.7). Вікно вибору версії мода під гру і завантажувач збірки: зверху найновіша (вибрана одразу),
 * бети позначені, під списком що змінилось у вибраній версії (якщо автор написав). Повертає вибрану версію або null.
 */
public final class ModVersionPicker {

    private ModVersionPicker() {
    }

    /** Лише версії під цю гру і один з завантажувачів збірки (порядок як прийшов, від найновішої). */
    public static List<ModrinthBrowse.PackVersion> forBuild(List<ModrinthBrowse.PackVersion> all, String game, String[] loaders) {
        List<ModrinthBrowse.PackVersion> out = new ArrayList<ModrinthBrowse.PackVersion>();
        if (all == null) return out;
        for (ModrinthBrowse.PackVersion v : all) {
            if (game != null && !v.games.contains(game)) continue;
            boolean ok = loaders == null || loaders.length == 0;
            if (!ok) for (String l : loaders) if (v.loaders.contains(l.toLowerCase(Locale.ROOT))) ok = true;
            if (ok) out.add(v);
        }
        return out;
    }

    /**
     * Показати вибір (з потоку інтерфейсу). {@code versions} від найновішої; порожньо: нічого не показується, null.
     *
     * @param where підпис збірки для тексту, наприклад «Fabric 26.3»
     */
    public static ModrinthBrowse.PackVersion pick(Component parent, String modTitle, String where, List<ModrinthBrowse.PackVersion> versions) {
        if (versions == null || versions.isEmpty()) return null;
        Window owner = parent == null ? null : parent instanceof Window ? (Window) parent : SwingUtilities.getWindowAncestor(parent);
        final JDialog d = new JDialog(owner, tr("manage.version.title", LookUi.plainText(modTitle)), Dialog.ModalityType.APPLICATION_MODAL);
        d.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        LookTheme.Palette pal = LookTheme.palette();
        JPanel p = new JPanel(new MigLayout("insets 18 20 16 20, wrap 1, fillx", "[0:0:,grow,fill]", "[][grow,fill][][]"));
        p.add(LookUi.body(tr("manage.version.text", where), false), "gapbottom 8");

        // зверху часто бети (Sodium 0.9.4-beta.2 над 0.9.3): позначка і вибір одразу на найновішій стабільній, як ставить
        // звичайне «Встановити»; стабільних немає: на найновішій
        int first = 0;
        for (int i = 0; i < versions.size(); i++) {
            if ("release".equals(versions.get(i).type)) {
                first = i;
                break;
            }
        }
        final String newest = versions.get(first).id;
        final JList<ModrinthBrowse.PackVersion> list = new JList<ModrinthBrowse.PackVersion>(versions.toArray(new ModrinthBrowse.PackVersion[0]));
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setSelectedIndex(first);
        list.ensureIndexIsVisible(first);
        list.setVisibleRowCount(Math.min(10, versions.size()));
        list.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> l, Object value, int index, boolean selected, boolean focus) {
                ModrinthBrowse.PackVersion v = (ModrinthBrowse.PackVersion) value;
                StringBuilder s = new StringBuilder(LookUi.plainText(v.number));
                String date = date(v.date);
                if (!date.isEmpty()) s.append("   ·   ").append(date);
                if ("beta".equals(v.type)) s.append("   ·   ").append(tr("manage.version.beta"));
                else if ("alpha".equals(v.type)) s.append("   ·   ").append(tr("manage.version.alpha"));
                if (v.id != null && v.id.equals(newest)) s.append("   ·   ").append(tr("manage.version.newest"));
                JLabel c = (JLabel) super.getListCellRendererComponent(l, s.toString(), index, selected, focus);
                c.setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 10));
                return c;
            }
        });
        p.add(new JScrollPane(list), "h 120:260:, growy, pushy");

        final JTextArea changes = new JTextArea(5, 40);
        changes.setLineWrap(true);
        changes.setWrapStyleWord(true);
        changes.setEditable(false);
        changes.setFont(UIManager.getFont("Label.font").deriveFont(12f));
        changes.setForeground(pal.text2);
        final JScrollPane changesScroll = new JScrollPane(changes);
        p.add(LookUi.title(tr("manage.version.changes"), 12.5f), "gaptop 10");
        p.add(changesScroll, "h 70:110:");
        Runnable showChanges = () -> {
            ModrinthBrowse.PackVersion v = list.getSelectedValue();
            String text = v == null || v.changelog == null ? "" : com.gummer.launcher.modrinth.MrpackUpdate.plain(v.changelog);
            changes.setText(text.isEmpty() ? tr("manage.version.noChanges") : LookUi.plainText(text));
            changes.setCaretPosition(0);
        };
        list.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) showChanges.run();
        });
        showChanges.run();

        final ModrinthBrowse.PackVersion[] result = {null};
        final JButton install = LookUi.primary(new JButton(tr("manage.browse.install")));
        JButton cancel = LookUi.secondary(new JButton(tr("button.cancel")));
        install.addActionListener(e -> {
            result[0] = list.getSelectedValue();
            d.dispose();
        });
        cancel.addActionListener(e -> d.dispose());
        list.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && list.getSelectedValue() != null) install.doClick();
            }
        });
        JPanel buttons = new JPanel(new MigLayout("insets 0, gap 8", "[grow][][]", ""));
        buttons.setOpaque(false);
        buttons.add(new JLabel(), "growx");
        buttons.add(cancel, "h 32!");
        buttons.add(install, "h 32!");
        p.add(buttons, "gaptop 12");
        d.setContentPane(p);
        d.getRootPane().setDefaultButton(install);
        LookUi.decorate(d, owner);
        d.pack();
        d.setSize(Math.max(d.getWidth(), 520), d.getHeight());
        d.setLocationRelativeTo(owner);
        d.setVisible(true);
        return result[0];
    }

    /** «2026-11-21T10:00:00Z» -> «21.11.2026», інакше порожньо. */
    static String date(String iso) {
        if (iso == null || iso.length() < 10 || iso.charAt(4) != '-' || iso.charAt(7) != '-') return "";
        return iso.substring(8, 10) + "." + iso.substring(5, 7) + "." + iso.substring(0, 4);
    }
}
