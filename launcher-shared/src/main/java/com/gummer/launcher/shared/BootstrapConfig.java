/*
 * Copyright (C) 2021-2025 GummerCraft <http://gummercraft.fun>
 */

package com.gummer.launcher.shared;

import java.io.File;
import lombok.Getter;
import lombok.Setter;
import org.spongepowered.configurate.objectmapping.ConfigSerializable;

@ConfigSerializable
@Getter
@Setter
public class BootstrapConfig extends ConfigFile<BootstrapConfig> {

	File workingDirectory = new File(SharedData.getFileChooseDefaultDir(), "GummerCraft");

	public static BootstrapConfig get() {
		return get(BootstrapConfig.class, new File(SharedData.getFileChooseDefaultDir(), "GummerCraft/bootstrap.yml"));
	}
}
