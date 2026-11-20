/*
 * Copyright (C) 2021-2026 GummerCraft <http://gummercraft.fun>
 */

package com.gummer.launcher.util;

import com.skcraft.launcher.launch.runtime.JavaRuntime;

import java.io.File;

/**
 * 20.11.2026 (тікет #88): чи можна запускати гру Java, яку гравець вибрав галочкою «використовувати вказані параметри».
 * Відкидаємо лише те, з чим гра точно не запуститься: Java немає на диску (видалили, оновили в іншу теку) або вона
 * старіша за версію, яку просить гра (наприклад, Java 8 для 1.18+). Тоді на цей запуск наша Java, галочка лишається.
 * «Занадто нову» Java не відкидаємо: старі версії гри з нею часто працюють, це був би хибний відкид.
 */
public final class EnforcedJava {

    private EnforcedJava() {
    }

    /**
     * @param required версія Java, яку просить гра (javaVersion.majorVersion маніфесту), 0 якщо невідомо
     * @return причина для лога або null, якщо цю Java можна брати
     */
    public static String reject(JavaRuntime rt, int required) {
        if (rt == null) return "no Java found";
        File dir = rt.getDir();
        if (dir == null || !dir.isDirectory()) return "folder missing";
        File bin = new File(dir, "bin");
        if (!new File(bin, "java.exe").isFile() && !new File(bin, "javaw.exe").isFile() && !new File(bin, "java").isFile()) {
            return "no java executable";
        }
        int major;
        try {
            major = rt.getMajorVersion();
        } catch (RuntimeException e) {
            return null; // версію не прочитали: не вигадуємо
        }
        if (required > 0 && major > 0 && major < required) return "Java " + major + " is older than required " + required;
        return null;
    }
}
