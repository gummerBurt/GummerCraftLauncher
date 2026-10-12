/*
 * Copyright (C) 2021-2025 GummerCraft <http://gummercraft.fun>
 */

package com.gummer.launcher.shared;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import lombok.SneakyThrows;
import lombok.var;
import org.spongepowered.configurate.objectmapping.ConfigSerializable;
import org.spongepowered.configurate.yaml.NodeStyle;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

@SuppressWarnings("unchecked")
@ConfigSerializable
@Getter
@Setter
public abstract class ConfigFile<T extends ConfigFile<T>> {
	private static final List<ConfigFile<?>> configs = new ArrayList<>();

	transient File file;

	{
		configs.add(this);
	}

	@SuppressWarnings("ResultOfMethodCallIgnored")
    @SneakyThrows
	public T loadYaml() {
		if (!file.exists()) {
			file.getParentFile().mkdirs();
			saveYaml();
			return (T) this;
		}

		var yaml = YamlConfigurationLoader.builder().nodeStyle(NodeStyle.BLOCK).file(file).build().load();

		if (!yaml.node("external-config-file").getString("null").equals("null")) {
			yaml = YamlConfigurationLoader.builder().nodeStyle(NodeStyle.BLOCK).file(new File(yaml.node("external-config-file").getString(file.getCanonicalPath()))).build().load();
		}

		var loaded = yaml.get(getClass());

		for (Field field : getClass().getDeclaredFields()) {
			field.setAccessible(true);
			if (field.get(loaded) != null) {
				field.set(this, field.get(loaded));
			}
		}
		return (T) this;
	}

	@SneakyThrows
	public T load() {
		return loadYaml();
	}

	public T setFile(File file) {
		this.file = file;
		return (T) this;
	}

	@SuppressWarnings("ResultOfMethodCallIgnored")
    @SneakyThrows
	public T saveYaml() {
		if (!file.exists()) file.createNewFile();
		var loader = YamlConfigurationLoader.builder().nodeStyle(NodeStyle.BLOCK).file(file).build();
		loader.save(loader.createNode().set(this));
		return (T) this;
	}

	public T save() {
		return saveYaml();
	}

	@SuppressWarnings("CallToPrintStackTrace")
    public static <Y extends ConfigFile<Y>> Y get(Class<Y> clazz, File file) {
		return configs.stream().filter(cf -> cf.getClass().equals(clazz)).findAny().map(cf -> (Y) cf).orElseGet(() -> {
			try {
				return clazz.getDeclaredConstructor().newInstance().setFile(file).loadYaml();
			} catch (InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
				e.printStackTrace();
			}
			return null;
		});
	}
}
