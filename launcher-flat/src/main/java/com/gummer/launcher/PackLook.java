/*
 * Copyright (C) 2021-2026 GummerCraft <http://gummercraft.fun>
 */

package com.gummer.launcher;

import com.skcraft.launcher.Instance;

import java.awt.*;
import java.util.Locale;

/**
 * Оформлення збірки (дизайн 1.35): ключ вигляду (той самий, що й для фону bg_&lt;ключ&gt;.jpg), акцентний колір
 * картки у списку і група сплеш-написів біля логотипа.
 */
public final class PackLook {

    private PackLook() {
    }

    /** Ключ оформлення вибраної збірки: vanilla, fabric, quilt, forge, neoforge або назва збірки (staldayz, ...). */
    public static String lookOf(Instance instance) {
        if (instance == null) return null;
        return instance.isVanilla() ? com.gummer.launcher.vanilla.VanillaVersions.GROUP
                : instance.isFabric() ? com.gummer.launcher.fabric.FabricVersions.GROUP
                : instance.isQuilt() ? com.gummer.launcher.quilt.QuiltVersions.GROUP
                : instance.isMrpack() ? com.gummer.launcher.manage.InstanceCopies.sectionOf(instance) // 17.11.2026: оформлення свого завантажувача
                : instance.isForgeLike() ? com.gummer.launcher.forge.ForgeVersions.kindOf(instance).group : instance.getName();
    }

    /** Група сплешів і акценту: staldayz, main (сервер GummerCraft і інші пакети з маніфесту), vanilla, fabric, quilt, forge, neoforge. */
    public static String groupOf(String look) {
        if (look == null) return "main";
        String k = look.toLowerCase(Locale.ROOT);
        if (k.startsWith("staldayz") || k.startsWith("sdayz")) return "staldayz";
        switch (k) {
            case "vanilla":
            case "fabric":
            case "quilt":
            case "forge":
            case "neoforge":
                return k;
            default:
                return "main";
        }
    }

    /** Акцентний колір групи: смужка вибраної картки, підсвітка кнопки запуску. */
    public static Color accentOf(String group) {
        switch (group == null ? "main" : group) {
            case "staldayz":
                return new Color(0xE3, 0xA4, 0x27);
            case "vanilla":
                return new Color(0x6D, 0xBE, 0x45);
            case "fabric":
                return new Color(0xDB, 0xD0, 0xB4);
            case "quilt":
                return new Color(0xA8, 0x6B, 0xF0);
            case "forge":
                return new Color(0x9A, 0xA7, 0xB8);
            case "neoforge":
                return new Color(0xE8, 0x83, 0x3A);
            default:
                return new Color(0x7C, 0xC8, 0x3E);
        }
    }

    public static Color accentOf(Instance instance) {
        return accentOf(groupOf(lookOf(instance)));
    }
}
