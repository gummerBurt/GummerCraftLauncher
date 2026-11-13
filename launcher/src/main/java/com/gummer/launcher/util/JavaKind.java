/*
 * Copyright (C) 2021-2026 GummerCraft <http://gummercraft.fun>
 */

package com.gummer.launcher.util;

import java.io.File;

/**
 * Чим запущено лаунчер: вбудованою Java нового {@code GummerCraft.exe} ({@code "bundled"}) чи системною ({@code "system"}).
 *
 * <p><b>Чому окремий клас, а не {@code com.gummer.launcher.shared.Toolbox.javaKind()}.</b> Старий exe (bootstrap 1.29-1.31)
 * вшиває в себе свою копію {@code launcher-shared}, а лаунчер вантажить через {@code URLClassLoader} з батьком bootstrap. Тому
 * класи {@code com.gummer.launcher.shared.*} у гравців зі старим exe беруться з exe, і методів, доданих туди в 1.32, там немає:
 * {@code NoSuchMethodError}. 13.11.2026 через це у гравця зі старим exe не надсилалась форма підтримки («Перевір інтернет»), а
 * вікна помилок падали ще до показу. Нове в лаунчері - лише в модулях лаунчера, не в {@code launcher-shared}.</p>
 */
public final class JavaKind {

    private JavaKind() {
    }

    public static String get() {
        File home = new File(System.getProperty("java.home", ""));
        for (File jre = home; jre != null && jre.getParentFile() != null; jre = jre.getParentFile()) {
            if (jre.getName().equalsIgnoreCase("jre")) {
                return new File(jre.getParentFile(), "GummerCraft.exe").isFile() ? "bundled" : "system";
            }
        }
        return "system";
    }

    public static boolean isBundled() {
        return "bundled".equals(get());
    }
}
