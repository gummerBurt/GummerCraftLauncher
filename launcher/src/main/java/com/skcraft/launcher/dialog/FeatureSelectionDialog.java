/*
 * SK's Minecraft Launcher
 * Copyright (C) 2010-2014 Albert Pham <http://www.sk89q.com> and contributors
 * Copyright (C) 2021-2025 GummerCraft <http://gummercraft.fun>
 * Please see LICENSE.txt for license information.
 */

package com.skcraft.launcher.dialog;

import com.skcraft.launcher.model.modpack.Feature;
import com.skcraft.launcher.swing.*;
import com.skcraft.launcher.util.SharedLocale;
import lombok.NonNull;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;

import static javax.swing.BorderFactory.createEmptyBorder;

public class FeatureSelectionDialog extends JDialog {

    private final List<Feature> features;
    private final JPanel container = new JPanel(new BorderLayout());
    private final JTextPane descText = new JTextPane();

    private final JScrollPane descScroll = new JScrollPane(descText);
    private final CheckboxTable componentsTable = new CheckboxTable();
    private final JScrollPane componentsScroll = new JScrollPane(componentsTable);
    private final JPanel componentsPanel = new JPanel(new BorderLayout());
    private final JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, componentsPanel, descScroll);
    private final JButton installButton = new JButton(SharedLocale.tr("features.install"));
    private final Object receiver;

    public FeatureSelectionDialog(Window owner, @NonNull List<Feature> features, Object receiver) {
        super(owner, ModalityType.DOCUMENT_MODAL);

        this.features = features;
        this.receiver = receiver;

        setTitle(SharedLocale.tr("features.title"));
        initComponents();
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setSize(new Dimension(1099, 590));
        setResizable(false);
        setLocationRelativeTo(owner);

        installButton.requestFocusInWindow();
    }

    private void initComponents() {
        // configure install button
        installButton.setFont(installButton.getFont().deriveFont(Font.BOLD, 24));
        installButton.setHorizontalTextPosition(SwingConstants.LEFT);
        installButton.setVerticalTextPosition(SwingConstants.CENTER);
        installButton.setMargin(new Insets(3, 12, 4, 12));
        installButton.setForeground(Color.WHITE);

        // configure scrollPanel model
        componentsTable.setModel(new FeatureTableModel(features));
        descScroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED); // show scroll only if needed

        // setup description panel
        descText.setFont(new JLabel().getFont());
        descText.setEditable(false);
        descText.setFocusable(false);
        descText.setContentType("text/html");
        SwingHelper.removeOpaqueness(descText);
        descText.setComponentPopupMenu(TextFieldPopupMenu.INSTANCE);

        // configure split section
        splitPane.setDividerLocation(247);
        splitPane.setDividerSize(10);
        SwingHelper.flattenJSplitPane(splitPane);

        // init root panel
        container.setBorder(createEmptyBorder(12, 12, 12, 12));
        container.add(splitPane, BorderLayout.CENTER);

        // setup description info section
        JLabel descLabel = new JLabel(SharedLocale.tr("features.intro"));
        descText.putClientProperty("FlatLaf.styleClass", "large");
        descLabel.putClientProperty("FlatLaf.styleClass", "large");
        descLabel.setBorder(createEmptyBorder(12, 12, 4, 12));

        // configure button with spacing
        JPanel buttonPanel = new JPanel(new BorderLayout());
        buttonPanel.add(installButton, BorderLayout.CENTER);
        buttonPanel.setBorder(BorderFactory.createEmptyBorder(11, 0, 0, 0));
        SwingHelper.equalWidth(installButton, new JButton(SharedLocale.tr("button.cancelsm")));

        // populate components
        componentsPanel.add(componentsScroll, BorderLayout.CENTER);
        componentsPanel.add(buttonPanel, BorderLayout.SOUTH);

        add(container, BorderLayout.CENTER);

        // set default features help
        descText.setText(SharedLocale.tr("features.homescreen"));

        componentsTable.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
            public void valueChanged(ListSelectionEvent e) {
                updateDescription();
            }
        });

        installButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                synchronized (receiver) {
                    receiver.notifyAll();
                }
                FeatureSelectionDialog.this.dispose();
            }
        });
    }

    private void updateDescription() {
        Feature feature = features.get(componentsTable.getSelectedRow());

        if (feature != null) {
            descText.setText(feature.getDescription());
        } else {
            descText.setText("<html><h1>" + SharedLocale.tr("features.selectForInfo") + "</h1></html>");
        }
    }

}
