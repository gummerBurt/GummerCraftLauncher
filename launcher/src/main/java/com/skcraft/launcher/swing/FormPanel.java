/*
 * SK's Minecraft Launcher
 * Copyright (C) 2010-2014 Albert Pham <http://www.sk89q.com> and contributors
 * Please see LICENSE.txt for license information.
 */

package com.skcraft.launcher.swing;

import javax.swing.*;
import java.awt.*;

public class FormPanel extends JPanel {

    private static final GridBagConstraints labelConstraints;
    private static final GridBagConstraints fieldConstraints;
    private static final GridBagConstraints wideFieldConstraints;

    private final GridBagLayout layout;

    static {
        // default
        fieldConstraints = new GridBagConstraints();
        fieldConstraints.fill = GridBagConstraints.HORIZONTAL;
        fieldConstraints.weightx = 1.0;
        fieldConstraints.gridwidth = GridBagConstraints.REMAINDER;
        fieldConstraints.insets = new Insets(5, 5, 2, 5);

        labelConstraints = (GridBagConstraints) fieldConstraints.clone();
        labelConstraints.weightx = 0.0;
        labelConstraints.gridwidth = 1;
        labelConstraints.insets = new Insets(4, 5, 1, 10);

        wideFieldConstraints = (GridBagConstraints) fieldConstraints.clone();
        wideFieldConstraints.insets = new Insets(7, 2, 1, 2);
    }

    public FormPanel() {
        setLayout(layout = new GridBagLayout());
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
    }

    public void addRow(Component label, Component component) {
        add(label);
        add(component);

        //fieldConstraints = new GridBagConstraints();
        fieldConstraints.fill = GridBagConstraints.HORIZONTAL;
        fieldConstraints.weightx = 1.0;
        fieldConstraints.gridwidth = GridBagConstraints.REMAINDER;
        fieldConstraints.insets = new Insets(5, 5, 2, 5);

        //labelConstraints = (GridBagConstraints) fieldConstraints.clone();
        labelConstraints.weightx = 0.0;
        labelConstraints.gridwidth = 1;
        labelConstraints.insets = new Insets(4, 5, 1, 10);

        //wideFieldConstraints = (GridBagConstraints) fieldConstraints.clone();
        wideFieldConstraints.insets = new Insets(7, 2, 1, 2);

        layout.setConstraints(label, labelConstraints);
        layout.setConstraints(component, fieldConstraints);
    }

    public void addRow(Component component) {
        add(component);

        //fieldConstraints = new GridBagConstraints();
        fieldConstraints.fill = GridBagConstraints.HORIZONTAL;
        fieldConstraints.weightx = 1.0;
        fieldConstraints.gridwidth = GridBagConstraints.REMAINDER;
        fieldConstraints.insets = new Insets(5, 5, 2, 5);

        //labelConstraints = (GridBagConstraints) fieldConstraints.clone();
        labelConstraints.weightx = 0.0;
        labelConstraints.gridwidth = 1;
        labelConstraints.insets = new Insets(4, 5, 1, 10);

        //wideFieldConstraints = (GridBagConstraints) fieldConstraints.clone();
        wideFieldConstraints.insets = new Insets(7, 2, 1, 2);

        layout.setConstraints(component, wideFieldConstraints);
    }

    public void addRow(
        Component label, Component component,
        int top, int left, int bottom, int right
    ) {
        add(label);
        add(component);
        fieldConstraints.insets = new Insets(top, left, bottom, right);
        labelConstraints.insets = new Insets(top, left,bottom, right);
        layout.setConstraints(label, labelConstraints);
        layout.setConstraints(component, fieldConstraints);
    }

    public void addRow(
        Component label, Component component,
        int field_top, int field_left, int field_bottom, int field_right,
        int label_top, int label_left, int label_bottom, int label_right
    ) {
        add(label);
        add(component);
        fieldConstraints.insets = new Insets(field_top, field_left, field_bottom, field_right);
        labelConstraints.insets = new Insets(label_top, label_left, label_bottom, label_right);
        layout.setConstraints(label, labelConstraints);
        layout.setConstraints(component, fieldConstraints);
    }

    public void addRow(
        Component component,
        int top, int left, int bottom, int right
    ) {
        add(component);
        wideFieldConstraints.insets = new Insets(top, left, bottom, right);
        layout.setConstraints(component, wideFieldConstraints);
    }

}
