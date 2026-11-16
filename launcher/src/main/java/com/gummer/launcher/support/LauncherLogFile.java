/*
 * Copyright (C) 2021-2026 GummerCraft <http://gummercraft.fun>
 */

package com.gummer.launcher.support;

import com.skcraft.launcher.util.SimpleLogFormatter;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/**
 * 16.11.2026: лог лаунчера у файл {@code <тека даних>/logs/launcher.log}. До того лог жив лише в пам'яті
 * ({@link SupportLogBuffer}) і зникав із закриттям вікна, тож «зависло на 66%» уночі потім ніхто не міг розібрати.
 *
 * <p>При кожному старті попередній лог стає {@code launcher-1.log}, ще старіший {@code launcher-2.log} (далі
 * видаляються). Один файл не росте понад {@link #MAX_BYTES}: далі пишеться лише рядок про обрізання. Токени
 * входу ховаються так само, як у буфері для підтримки. Помилки запису мовчки ігноруються: лог не має заважати.</p>
 */
public final class LauncherLogFile extends Handler {

    static final long MAX_BYTES = 8L * 1024 * 1024;
    static final int KEEP = 2;
    private static LauncherLogFile instance;

    private final File file;
    private OutputStream out;
    private long written;
    private boolean truncatedNoticeWritten;

    private LauncherLogFile(File file) throws IOException {
        this.file = file;
        this.out = new FileOutputStream(file, false);
        setFormatter(new SimpleLogFormatter());
    }

    /** Поставити один раз при старті. */
    public static synchronized void install(File baseDir) {
        if (instance != null || baseDir == null) return;
        try {
            File dir = new File(baseDir, "logs");
            dir.mkdirs();
            rotate(dir);
            instance = new LauncherLogFile(new File(dir, "launcher.log"));
            Logger.getLogger("").addHandler(instance);
        } catch (Throwable t) {
            // без файлу лаунчер працює як раніше
        }
    }

    /** Файл попереднього запуску (null, якщо його немає). */
    public static File previousLog(File baseDir) {
        File f = new File(new File(baseDir, "logs"), "launcher-1.log");
        return f.isFile() ? f : null;
    }

    static void rotate(File dir) {
        File oldest = new File(dir, "launcher-" + KEEP + ".log");
        oldest.delete();
        for (int i = KEEP - 1; i >= 1; i--) {
            File from = new File(dir, "launcher-" + i + ".log");
            if (from.isFile()) from.renameTo(new File(dir, "launcher-" + (i + 1) + ".log"));
        }
        File current = new File(dir, "launcher.log");
        if (current.isFile()) current.renameTo(new File(dir, "launcher-1.log"));
    }

    @Override
    public synchronized void publish(LogRecord record) {
        if (out == null || record == null || !isLoggable(record)) return;
        try {
            String s = SupportLogBuffer.redact(getFormatter().format(record));
            byte[] b = s.getBytes(StandardCharsets.UTF_8);
            if (written + b.length > MAX_BYTES) {
                if (!truncatedNoticeWritten) {
                    out.write("... лог обрізано: файл досягнув 8 МБ\n".getBytes(StandardCharsets.UTF_8));
                    out.flush();
                    truncatedNoticeWritten = true;
                }
                return;
            }
            out.write(b);
            written += b.length;
            out.flush(); // світло може зникнути будь-коли: краще кожен рядок одразу на диск
        } catch (Throwable t) {
            // ігноруємо
        }
    }

    @Override
    public synchronized void flush() {
        try {
            if (out != null) out.flush();
        } catch (IOException ignored) {
            // ігноруємо
        }
    }

    @Override
    public synchronized void close() {
        try {
            if (out != null) out.close();
        } catch (IOException ignored) {
            // ігноруємо
        }
        out = null;
    }
}
