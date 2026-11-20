/*
 * Copyright (C) 2021-2026 GummerCraft <http://gummercraft.fun>
 */

package com.gummer.launcher.support;

import java.util.ArrayList;
import java.util.List;

/**
 * Що лаунчер пам'ятає про звернення гравця: {@code <тека даних>/support.json}, окремо від config.json (нове поле там
 * зламало б відкат на 1.31). Видалення файлу лише «забуває» звернення в лаунчері, у Chatwoot нічого не зникає.
 */
public class SupportState {
    /** source_id контакту в Chatwoot (видає сервер при першому зверненні). */
    public String sourceId;
    public String nick;
    public String contact;
    /** Номер останньої розмови (display_id) і її тема. */
    public int conversationId;
    public String topic;
    /** Найбільший id повідомлення, яке гравець уже бачив у вікні (для «нова відповідь»). */
    public long lastSeenMessageId;
    /**
     * 20.11.2026: чат показує всі звернення гравця разом, тож «прочитано» одне на всі розмови (id повідомлень у Chatwoot
     * наскрізні). false: старий support.json, де лічильник скидався з кожним новим зверненням (див. SupportService).
     */
    public boolean unifiedChat;
    /** Коли створювались розмови (мс): обмеження частоти. */
    public List<Long> createdAt = new ArrayList<Long>();
}
