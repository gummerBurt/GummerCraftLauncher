/*
 * SK's Minecraft Launcher
 * Copyright (C) 2010-2014 Albert Pham <http://www.sk89q.com> and contributors
 * Please see LICENSE.txt for license information.
 */

package com.skcraft.launcher.swing;

import javax.swing.table.TableModel;

public class CheckboxTable extends DefaultTable {

    public CheckboxTable() {
        super();
        setTableHeader(null);
        setShowVerticalLines(false);
        setRowHeight(48);
        setFocusable(false);
    }

    @Override
    public void setModel(TableModel dataModel) {
        super.setModel(dataModel);
        try {
            getColumnModel().getColumn(0).setMaxWidth(38);
            getColumnModel().getColumn(0).setMinWidth(38);
            getColumnModel().getColumn(0).setWidth(38);
        } catch (ArrayIndexOutOfBoundsException e) {
        }
    }

}
