package com.skcraft.launcher.launch.runtime;

import com.skcraft.launcher.util.SharedLocale;

import java.io.File;

/**
 * Special sentinel subclass used to represent the "add custom runtime" entry in the dropdown
 */
public class AddJavaRuntime extends JavaRuntime {
	public AddJavaRuntime() {
		super(new File(""), "", false);
	}

	@Override
	public String toString() {
		return SharedLocale.tr("options.jvmRuntime.add");
	}

	public static final AddJavaRuntime ADD_RUNTIME_SENTINEL = new AddJavaRuntime();
}
