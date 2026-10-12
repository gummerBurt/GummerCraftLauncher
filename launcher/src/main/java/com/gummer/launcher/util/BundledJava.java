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
        return new File(l.getBaseDir(), "runtime/" + component + "/" + Environment.getInstance().getMojangOs());
    }

}
