/*
 * Copyright (C) 2021-2025 GummerCraft <http://gummercraft.fun>
 */

package com.gummer.launcher.dialog;

import com.skcraft.launcher.Launcher;
import com.skcraft.launcher.auth.OfflineSession;
import com.skcraft.launcher.auth.Session;
import com.skcraft.launcher.swing.FormPanel;
import com.skcraft.launcher.swing.SwingHelper;
import com.skcraft.launcher.util.SharedLocale;
import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import lombok.Getter;
import lombok.NonNull;

public class OfflineLoginDialog extends JDialog {

    FormPanel formPanel = new FormPanel();

    @Getter private Session session;

    private final JTextField usernameText = new JTextField();
    private final JButton loginButton = new JButton(SharedLocale.tr("accounts.local.add"));
    private final JButton cancelButton = new JButton(SharedLocale.tr("button.cancelsm"));

    private final FormPanel formInfoPanel = new FormPanel();
    private final FormPanel formInputPanel = new FormPanel();
    private final JPanel buttonsPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));

    private final JLabel loginTitle = new JLabel(SharedLocale.tr("loginOffline.name_title"));
    private final JLabel formInfoLabel = new JLabel(SharedLocale.tr("accounts.local.note"));

    public OfflineLoginDialog(Window owner, @NonNull Launcher launcher) {
        super(owner, ModalityType.DOCUMENT_MODAL);


        setTitle(SharedLocale.tr("loginOffline.title"));
        initComponents();
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setMinimumSize(new Dimension(380, 0));
        setResizable(false);
        pack();
        setLocationRelativeTo(owner);

        setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent event) {
                dispose();
            }
        });
    }

    private void initComponents() {
        // global styling
        formInfoLabel.putClientProperty("FlatLaf.styleClass", "large");
        loginTitle.putClientProperty("FlatLaf.styleClass", "h1");
        loginTitle.setHorizontalAlignment(SwingConstants.CENTER);

        // style button and inputs
        loginButton.setFont(loginButton.getFont().deriveFont(Font.BOLD, 17));
        usernameText.setFont(usernameText.getFont().deriveFont(Font.BOLD, 28));
        usernameText.setHorizontalAlignment(JTextField.CENTER);
        usernameText.setColumns(15);
        usernameText.setEditable(true);

        // setup input form
        formInputPanel.addRow(loginTitle);
        formInputPanel.addRow(Box.createVerticalStrut(2));
        formInputPanel.addRow(usernameText);

        // setup description panel
        formInfoPanel.addRow(formInfoLabel);
        formInfoPanel.addRow(Box.createVerticalStrut(2));

        // setup buttons panel
        buttonsPanel.setBorder(BorderFactory.createEmptyBorder(0, 13, 20, 23));
        buttonsPanel.add(loginButton);
        //buttonsPanel.addElement(cancelButton);

        // build main form panel
        formPanel.addRow(formInputPanel);
        formPanel.addRow(formInfoPanel);

        // build window container
        add(formPanel, BorderLayout.CENTER);
        add(buttonsPanel, BorderLayout.SOUTH);

        // set selection to login button
        getRootPane().setDefaultButton(loginButton);

        // validate input and proceed
        loginButton.addActionListener(event -> {
            if (!usernameText.getText().isEmpty()) {
                // ensure 3 to 16 characters with no whitespace
                String username = usernameText.getText().trim();
                if (!username.matches("^[^\\s]{3,16}$")) {
                    JOptionPane.showMessageDialog(
                        this,
                        "Nickname must be between 3 and 16 characters long and cannot contain spaces.",
                        "Invalid Nickname",
                        JOptionPane.ERROR_MESSAGE
                    );
                    return;
                }
                session = new OfflineSession(usernameText.getText());
                dispose();
            } else {
                SwingHelper.showErrorDialog(this, SharedLocale.tr("login.OfflineNoLoginError"), SharedLocale.tr("login.OfflineNoLoginTitle"));
            }
        });

        // cancel and close
        cancelButton.addActionListener(e -> dispose());

    }

    public static Session show(Window owner, Launcher launcher) {
        OfflineLoginDialog dialog = new OfflineLoginDialog(owner, launcher);
        dialog.setVisible(true);
        return dialog.getSession();
    }
}
