/*
 * Copyright (C) 2021-2025 GummerCraft <http://gummercraft.fun>
 */

package com.gummer.launcher.dialog;

import com.skcraft.launcher.swing.ActionListeners;
import com.skcraft.launcher.util.SharedLocale;
import java.awt.*;
import java.io.IOException;
import java.net.URISyntaxException;
import javax.swing.*;

import static com.gummer.launcher.util.SelfRestart.restartProcess;

public class RestartRequiredDialog extends JDialog {
    public RestartRequiredDialog(Window parent) {
        super(parent, SharedLocale.tr("dialog.restartRequired.title"), ModalityType.DOCUMENT_MODAL);

        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        initComponents();
        setResizable(false);
        pack();
        setLocationRelativeTo(parent);
    }

    private void initComponents() {
        JPanel container = new JPanel();
        JPanel buttons = new JPanel();

        container.add(new JLabel(SharedLocale.tr("dialog.restartRequired.text")));

        JButton okButton = new JButton(SharedLocale.tr("button.ok"));
        JButton laterButton = new JButton(SharedLocale.tr("dialog.restartRequired.button.later"));

        okButton.putClientProperty("FlatLaf.styleClass", "large");
        laterButton.putClientProperty("FlatLaf.styleClass", "large");

        buttons.add(okButton);
        buttons.add(laterButton);

        add(container, BorderLayout.CENTER);
        add(buttons, BorderLayout.PAGE_END);

        getRootPane().setDefaultButton(okButton);

        okButton.addActionListener(e -> {
            //System.exit(0);
            try {
                restartProcess();
            } catch (IOException | URISyntaxException ex) {
                throw new RuntimeException(ex);
            }
        });
        laterButton.addActionListener(ActionListeners.dispose(this));
    }

    public static void show(Window parent) {
        RestartRequiredDialog dialog = new RestartRequiredDialog(parent);
        dialog.setVisible(true);
    }
}
