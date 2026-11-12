/*
 * Copyright (C) 2021-2026 GummerCraft <http://gummercraft.fun>
 */

package com.gummer.launcher.crash;

import com.skcraft.launcher.LauncherException;

import javax.net.ssl.SSLException;
import java.io.FileNotFoundException;
import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.file.AccessDeniedException;
import java.nio.file.FileSystemException;
import java.util.Locale;

/**
 * Людське пояснення помилки лаунчера (з 1.32): за ланцюжком винятків визначає типову причину (немає інтернету,
 * антивірус перехоплює HTTPS, немає місця, файл зайнятий) і повертає ключ тексту в lang ({@code errors.why.<key>}).
 * null: причина невідома, лишається текст самої помилки.
 */
public final class ErrorExplainer {

    private ErrorExplainer() {
    }

    public static String explain(Throwable t) {
        for (Throwable c = t; c != null; c = c.getCause() == c ? null : c.getCause()) {
            String m = String.valueOf(c.getMessage()).toLowerCase(Locale.ROOT);
            if (m.contains("not enough space") || m.contains("no space left") || m.contains("disk full")) {
                return "disk";
            }
            if (c instanceof UnknownHostException || c instanceof NoRouteToHostException) {
                return "offline";
            }
            if (c instanceof ConnectException || c instanceof SocketTimeoutException
                    || m.contains("connection reset") || m.contains("timed out")) {
                return "network";
            }
            if (c instanceof SSLException || m.contains("pkix path") || m.contains("unable to find valid certification path")) {
                return "ssl";
            }
            if (c instanceof AccessDeniedException || m.contains("access is denied") || m.contains("відмовлено в доступі")) {
                return "access";
            }
            if (c instanceof FileSystemException && m.contains("used by another process")
                    || m.contains("being used by another process") || m.contains("використовується іншим процесом")) {
                return "locked";
            }
            if (c instanceof FileNotFoundException && m.contains("(access is denied)")) {
                return "access";
            }
            if (m.contains("checksum") || m.contains("hash mismatch") || m.contains("sha1") && m.contains("mismatch")) {
                return "corrupt";
            }
        }
        return null;
    }

    /** Чи є в повідомленні вже людський текст від лаунчера (LauncherException), тоді пояснення не потрібне. */
    public static boolean isHumanMessage(Throwable t) {
        return t instanceof LauncherException;
    }
}
