/*
 * Copyright (C) 2021-2025 GummerCraft <http://gummercraft.fun>
 */

package com.gummer.launcher.i18n;

public enum Lang {
    UKRAINIAN("\u0423\u043A\u0440\u0430\u0457\u043D\u0441\u044C\u043A\u0430", "uk_ua", ""), // encode ukrainian via unicode
    ENGLISH("English", "en_us", "");

    private final String name;
    private final String id;
    private final String translataor;

    Lang(String name, String id, String translator) {
        this.name = name;
        this.id = id;
        this.translataor = translator;
    }

    public String getName() {
        return this.name;
    }

    public String getId() {
        return this.id;
    }

    public String getTranslataor() {
        return this.translataor;
    }

    public static Lang getFromName(String name) {
        for (Lang la : values()) {
            if (la.getName().equals(name)) {
                return la;
            }
        }
        return UKRAINIAN;
    }
}