/*
 * Copyright (C) 2021-2025 GummerCraft <http://gummercraft.fun>
 */

package com.gummer.launcher.shared;

import java.io.File;
import javax.swing.*;
import javax.swing.filechooser.*;

public class SharedData {
	public static File getFileChooseDefaultDir() {
		JFileChooser chooser = new JFileChooser();
		FileSystemView fsv = chooser.getFileSystemView();
		return fsv.getDefaultDirectory();
	}
}
