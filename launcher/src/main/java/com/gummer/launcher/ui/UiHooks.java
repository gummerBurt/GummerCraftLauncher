/*
 * Copyright (C) 2021-2026 GummerCraft <http://gummercraft.fun>
 */

package com.gummer.launcher.ui;

import com.skcraft.launcher.Instance;

import java.awt.Window;

/**
 * Дії головного вікна, потрібні вікнам помилок і вильотів з модуля launcher (1.32). Вікно звернення,
 * налаштування паку і перевстановлення живуть у launcher-flat, тож головне вікно реєструє їх тут при старті.
 * Не зареєстровано (старі вікна, тести): відповідних кнопок просто немає.
 */
public final class UiHooks {

    /** Відкрити звернення в підтримку з готовою темою і текстом. */
    public interface Support {
        void open(Window owner, String topic, String text);
    }

    /** Дія над паком. */
    public interface InstanceAction {
        void run(Window owner, Instance instance);
    }

    public static volatile Support support;
    public static volatile InstanceAction instanceSettings;
    public static volatile InstanceAction reinstall;

    private UiHooks() {
    }
}
