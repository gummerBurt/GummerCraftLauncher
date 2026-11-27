/*
 * Copyright (C) 2021-2025 GummerCraft <http://gummercraft.fun>
 */

package com.gummer.launcher.util;

import com.skcraft.launcher.Launcher;
import com.skcraft.launcher.util.Environment;

import java.io.File;

public class BundledJava {

    // JVM Path:
    // $rootPath/runtime/{component}/{osarch}/
    public static File getJavaDir(Launcher l, String component) throws Exception {
        return getJavaDir(l, component, Environment.getInstance().getMojangOs());
    }

    /** 1.32.2: тека Java для заданої ОС (на Apple Silicon старі версії гри беруть Java для Intel, див. MacRosetta). */
    public static File getJavaDir(Launcher l, String component, String os) {
        // 27.11.2026: корінь може бути поза текою даних (JavaPlace, тікети #134, #138)
        return new File(JavaPlace.root(l), component + "/" + os);
    }

    /** Чи є в теці runtime виконуваний файл Java (Windows: bin/javaw.exe або bin/java.exe, інакше bin/java). */
    public static boolean hasJavaBinary(File jvmDir) {
        if (jvmDir == null) {
            return false;
        }
        File bin = new File(jvmDir, "bin");
        return new File(bin, "javaw.exe").isFile() || new File(bin, "java.exe").isFile() || new File(bin, "java").isFile();
    }

}
