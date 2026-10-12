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

@SuppressWarnings("ConstantConditions")
public class InstanceTableModel extends AbstractTableModel {

    private final InstanceList instances;
    private final Icon instanceIcon;
    private final Icon customInstanceIcon;
    private final Icon downloadIcon;

    public InstanceTableModel(InstanceList instances) {
        this.instances = instances;
        instanceIcon = SwingHelper.createIcon(Launcher.class, "instance_icon.png", 32, 32);
        customInstanceIcon = instanceIcon;
        downloadIcon = SwingHelper.createIcon(Launcher.class, "download_icon.png", 32, 32);
    }

    public void update() {
        instances.sort();
        fireTableDataChanged();
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
                instances.get(rowIndex).setSelected((boolean) (Boolean) value);
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
        return instances.size();
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
                instance = instances.get(rowIndex);
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
                instance = instances.get(rowIndex);
                //return instance.getTitle();
                return "<html>&nbsp;" + "<span style=\"font-size:13\">" + SwingHelper.htmlEscape(instance.getTitle()) + "</span>" + getAddendum(instance) + "</html>";
            default:
                return null;
        }
    }

    private String getAddendum(Instance instance) {
        if (!instance.isLocal()) {
            return "<br /> &nbsp;&nbsp;&nbsp;<span style=\"color:#969896;font-size:11;\">" + SharedLocale.tr("launcher.notInstalledHint") + "</span>";
        } else if (!instance.isInstalled()) {
            return "<br /> &nbsp;&nbsp;&nbsp;<span style=\"color:#969896;font-size:11;\">" + SharedLocale.tr("launcher.requiresUpdateHint") + "</span>";
        } else if (instance.isUpdatePending()) {
            return "<br /> &nbsp;&nbsp;&nbsp;<span style=\"color:#969896;font-size:11;\">" + SharedLocale.tr("launcher.updatePendingHint") + "</span>";
        } else {
            return "";
        }
    }

}
