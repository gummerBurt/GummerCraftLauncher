/*
 * Copyright (C) 2021-2026 GummerCraft <http://gummercraft.fun>
 */

package com.gummer.launcher.util;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Один спосіб створювати ObjectMapper для всього лаунчера.
 *
 * <p>Невідомі поля в JSON ігноруються. До 1.32 стандартний ObjectMapper падав на будь-якому новому полі
 * (так маніфест Java з полем {@code local_path} уже ламав лаунчер, а нове поле в списку модпаків поклало б
 * список у всіх гравців). Тепер сервер може додавати поля, і старі версії лаунчера їх просто не бачать.</p>
 */
public final class Json {

    private Json() {
    }

    public static ObjectMapper newMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        return mapper;
    }
}
