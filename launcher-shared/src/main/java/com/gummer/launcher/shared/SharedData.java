/*
 * Copyright (C) 2021-2025 GummerCraft <http://gummercraft.fun>
 */

package com.gummer.launcher.shared;

import java.io.File;
import javax.swing.*;
import javax.swing.filechooser.*;

public class SharedData {
	public static File getFileChooseDefaultDir() {
		// Папка "Документи" через FileSystemView напряму. Раніше тут створювався JFileChooser лише заради getFileSystemView():
		// він будує модель вікна вибору файлів і перебирає вміст робочого стола (читає атрибути ярликів .lnk) - пісочниці показували це як
		// "лаунчер читає ярлики на робочому столі". Результат той самий (FileSystemView.getDefaultDirectory), тож шлях до наявних установок не змінюється.
		return FileSystemView.getFileSystemView().getDefaultDirectory();
	}
}
