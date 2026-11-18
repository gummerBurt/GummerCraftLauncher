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

    /**
     * 16.11.2026: сповіщення в стилі вигляду лаунчера, всередині вікна (launcher-flat, {@code look.Toast}).
     * Повертає false, якщо показати не вийшло (вікно сховане): тоді старе {@code popup.Notification}.
     */
    public interface Toast {
        boolean show(java.awt.Frame frame, com.gummer.launcher.popup.Notification.Type type, String message, String title);
    }

    public static volatile Toast toast;
    public static volatile Support support;
    public static volatile InstanceAction instanceSettings;
    public static volatile InstanceAction reinstall;
    /** 18.11.2026: вікно «Збірка» на вкладці модів (помічник при вильоті: вимкнути, оновити). */
    public static volatile InstanceAction openMods;
    /** 18.11.2026: «Повернути версію» модпака Modrinth з резервної копії останнього оновлення. */
    public static volatile InstanceAction rollback;

    private UiHooks() {
    }
}
