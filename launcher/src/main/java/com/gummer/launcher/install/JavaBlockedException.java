/*
 * Copyright (C) 2021-2026 GummerCraft <http://gummercraft.fun>
 */

package com.gummer.launcher.install;

import java.io.File;
import java.io.IOException;

/**
 * 27.11.2026 (тікети #134, #138): Windows (захист від програм-вимагачів, антивірус) не дав записати чи прочитати файли
 * Java у теці лаунчера. {@link #moved}: лаунчер уже перемкнув теку Java на запасну ({@link #to}), наступна спроба
 * поставить Java туди. Для вікна помилки з кнопками ({@link com.gummer.launcher.crash.ErrorExplainer}).
 */
public class JavaBlockedException extends IOException {
    public final File dir;
    public final boolean moved;
    public final File to;

    public JavaBlockedException(File dir, boolean moved, File to, Throwable cause) {
        super("Windows blocked Java files in " + dir + (moved ? ", next try uses " + to : ""), cause);
        this.dir = dir;
        this.moved = moved;
        this.to = to;
    }
}
