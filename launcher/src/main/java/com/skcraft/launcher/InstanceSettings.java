package com.skcraft.launcher;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.skcraft.launcher.launch.MemorySettings;
import com.skcraft.launcher.launch.runtime.JavaRuntime;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class InstanceSettings {
	private JavaRuntime runtime;
	private MemorySettings memorySettings;
	private String customJvmArgs;
	/** 19.11.2026: галочка «Запускати цю збірку на дискретній відеокарті» (картка «Відеокарта»), див. Gpu.
	 *  null і false: вимкнено (так за замовчуванням). */
	private Boolean highPerformanceGpu;
	/** 22.11.2026: вибір відеокарти «auto», «integrated» або «discrete»; null за замовчуванням (дискретна, див. Gpu.choice). */
	private String gpu;
}
