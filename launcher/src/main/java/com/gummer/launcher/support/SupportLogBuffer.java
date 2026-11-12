/*
 * Copyright (C) 2021-2026 GummerCraft <http://gummercraft.fun>
 */

package com.gummer.launcher.support;

import com.skcraft.launcher.util.SimpleLogFormatter;

import java.util.ArrayDeque;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/**
 * Останні рядки лога лаунчера в пам'яті: лаунчер не пише лог у файл, а для звернення в підтримку він потрібен.
 * Ставиться один раз при старті, тримає до {@link #MAX_LINES} записів.
 */
public final class SupportLogBuffer extends Handler {

    private static final int MAX_LINES = 3000;
    private static final java.util.regex.Pattern TOKEN = java.util.regex.Pattern.compile(
            "(?i)((?:--accessToken|accessToken|access_token|refreshToken|refresh_token|--session)[\"'=:,\\s]+)[A-Za-z0-9._\\-]{16,}");
    private static SupportLogBuffer instance;
    private final ArrayDeque<String> lines = new ArrayDeque<String>();

    private SupportLogBuffer() {
        setFormatter(new SimpleLogFormatter());
    }

    public static synchronized void install() {
        if (instance == null) {
            instance = new SupportLogBuffer();
            Logger.getLogger("").addHandler(instance);
        }
    }

    /** Увесь збережений лог (порожньо, якщо буфер не ставили). */
    public static synchronized String text() {
        if (instance == null) return "";
        synchronized (instance.lines) {
            StringBuilder sb = new StringBuilder();
            for (String l : instance.lines) sb.append(l);
            return sb.toString();
        }
    }

    /** Сховати токени входу (accessToken тощо) у тексті лога. */
    public static String redact(String s) {
        return TOKEN.matcher(s).replaceAll("$1<hidden>");
    }

    @Override
    public void publish(LogRecord record) {
        if (record == null || !isLoggable(record)) return;
        String s;
        try {
            s = getFormatter().format(record);
        } catch (RuntimeException e) {
            return;
        }
        s = redact(s); // запобіжник: токени входу в лог не потрапляють
        synchronized (lines) {
            lines.addLast(s);
            while (lines.size() > MAX_LINES) lines.removeFirst();
        }
    }

    @Override
    public void flush() {
    }

    @Override
    public void close() {
    }
}
