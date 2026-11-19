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
            // 19.11.2026: перевірка місця до завантаження знає, скільки треба і скільки є
            if (c instanceof com.gummer.launcher.install.DiskSpaceException) {
                return "diskNeed";
            }
            // 19.11.2026 (власник: «Антивірус заблокував файл. -- го»): Windows прямо каже, що антивірус заблокував
            // файл (ERROR_VIRUS_INFECTED 225, ERROR_VIRUS_DELETED 226): при записі, відкритті чи запуску Java
            // («CreateProcess error=225»). Лише за цими текстами, без вгадування за зниклими файлами
            if (m.contains("error=225") || m.contains("error=226") || m.contains("contains a virus")
                    || m.contains("potentially unwanted software") || m.contains("містить вірус")
                    || m.contains("потенційно небажане")) {
                return "virus";
            }
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

    /** Текст пояснення: для «diskNeed» з числами (скільки треба, скільки вільно, диск), решта як є. */
    public static String text(String why, Throwable t) {
        if ("diskNeed".equals(why)) {
            for (Throwable c = t; c != null; c = c.getCause() == c ? null : c.getCause()) {
                if (c instanceof com.gummer.launcher.install.DiskSpaceException) {
                    com.gummer.launcher.install.DiskSpaceException d = (com.gummer.launcher.install.DiskSpaceException) c;
                    String gb = com.skcraft.launcher.util.SharedLocale.tr("settings.danger.gb");
                    String mb = com.skcraft.launcher.util.SharedLocale.tr("settings.danger.mb");
                    return com.skcraft.launcher.util.SharedLocale.tr("errors.why.diskNeed",
                            com.gummer.launcher.install.DiskSpaceException.human(d.getNeed(), gb, mb),
                            com.gummer.launcher.install.DiskSpaceException.human(d.getFree(), gb, mb), d.getDrive());
                }
            }
        }
        return com.skcraft.launcher.util.SharedLocale.tr("errors.why." + why);
    }

    /** Чи є в повідомленні вже людський текст від лаунчера (LauncherException), тоді пояснення не потрібне. */
    public static boolean isHumanMessage(Throwable t) {
        return t instanceof LauncherException;
    }
}
