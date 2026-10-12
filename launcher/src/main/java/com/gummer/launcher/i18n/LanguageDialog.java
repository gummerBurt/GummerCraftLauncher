/*
 * Copyright (C) 2021-2025 GummerCraft <http://gummercraft.fun>
 */

package com.gummer.launcher.i18n;

import java.awt.BorderLayout;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.WindowConstants;

import com.skcraft.launcher.Launcher;
import com.skcraft.launcher.swing.ActionListeners;
import com.skcraft.launcher.util.SharedLocale;

import net.miginfocom.swing.MigLayout;

public class LanguageDialog extends JDialog {
    public LanguageDialog(Window parent, Launcher launcher) {
        super(parent, SharedLocale.tr("options.langTitle"), ModalityType.DOCUMENT_MODAL);

        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        initComponents(launcher);
        setResizable(false);
        pack();
        setLocationRelativeTo(parent);
    }

    private void initComponents(Launcher launcher) {
        JPanel container = new JPanel();
        container.setLayout(new MigLayout("insets dialog"));
        Lang lang = Lang.getFromName(launcher.getConfig().getLang());
        String[] stl = new String[Lang.values().length];

        for (int i = 0; i < Lang.values().length; i++) {
            stl[i] = Lang.values()[i].getName() + "  " + Lang.values()[i].getTranslataor();
        }

        JComboBox<String> langsc = new JComboBox<String>(stl);

        for (int i = 0; i < Lang.values().length; i++) {
            if (lang == Lang.values()[i]) {
                langsc.setSelectedItem(stl[i]);
            }
        }


        langsc.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                I18n.changeLang((Lang) Lang.values()[langsc.getSelectedIndex()], launcher);
            }
        });

        container.add(langsc);

        container.add(new JLabel(SharedLocale.tr("options.lang.needrestart")));

        JButton okButton = new JButton(SharedLocale.tr("button.ok"));

        container.add(okButton, "tag ok, sizegroup bttn");

        add(container, BorderLayout.CENTER);
        getRootPane().setDefaultButton(okButton);
        getRootPane().registerKeyboardAction(ActionListeners.dispose(this),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);

        okButton.addActionListener(ActionListeners.dispose(this));

    }

    public static void showLanguageDialog(Window parent, Launcher launcher) {
        LanguageDialog dialog = new LanguageDialog(parent, launcher);
        dialog.setVisible(true);
    }
}