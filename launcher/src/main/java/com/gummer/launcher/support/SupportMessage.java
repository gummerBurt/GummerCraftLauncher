/*
 * Copyright (C) 2021-2026 GummerCraft <http://gummercraft.fun>
 */

package com.gummer.launcher.support;

import java.util.ArrayList;
import java.util.List;

/** Повідомлення розмови підтримки, як його віддає Client API Chatwoot. */
public class SupportMessage {
    public long id;
    /** 20.11.2026: номер розмови (display_id), до якої належить повідомлення (спільний чат усіх звернень). */
    public int conversationId;
    public String content;
    /** 0 - від гравця, 1 - від адміна, 3 - шаблон (наприклад привітання входу). */
    public int messageType;
    public long createdAt;
    public String senderName;
    public final List<String> attachments = new ArrayList<String>();

    public boolean isFromPlayer() {
        return messageType == 0;
    }
}
