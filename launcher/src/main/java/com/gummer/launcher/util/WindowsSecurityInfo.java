/*
 * Copyright (C) 2021-2026 GummerCraft <http://gummercraft.fun>
 */

package com.gummer.launcher.util;

import lombok.extern.java.Log;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/**
 * 27.11.2026 (тікети #134, #138, власник: «Діагностика в логах для підтримки»): які антивіруси стоять і чи увімкнено
 * «Контрольований доступ до папок» (захист Windows від програм-вимагачів). З цим одразу видно, хто не дав лаунчеру
 * доступу до «Документів», без розпитувань гравця. Читається через PowerShell без прав адміністратора
 * ({@code root/SecurityCenter2 AntiVirusProduct}, {@code Get-MpPreference}). Лише на Windows, лише коли треба (звернення
 * в підтримку, помилка встановлення Java), не частіше разу за запуск лаунчера; до 8 секунд, інакше «невідомо».
 */
@Log
public final class WindowsSecurityInfo {

    private static volatile String cached;

    private WindowsSecurityInfo() {
    }

    /** Рядки для system.txt і логу: «Антивіруси: ...», «Контрольований доступ до папок: ...». Не Windows: порожньо. */
    public static String lines() {
        if (!System.getProperty("os.name", "").startsWith("Windows")) return "";
        String c = cached;
        if (c != null) return c;
        String av = "?";
        String cfa = "?";
        try {
            String script = "$ErrorActionPreference='SilentlyContinue';[Console]::OutputEncoding=[Text.Encoding]::UTF8;"
                    + "$a=(Get-CimInstance -Namespace root/SecurityCenter2 -ClassName AntiVirusProduct | ForEach-Object { $_.displayName }) -join ', ';"
                    + "$c=(Get-MpPreference).EnableControlledFolderAccess;"
                    + "Write-Output ('AV=' + $a); Write-Output ('CFA=' + $c)";
            Process p = new ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-Command", script)
                    .redirectErrorStream(true).start();
            p.getOutputStream().close();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            final InputStream in = p.getInputStream();
            Thread reader = new Thread(() -> {
                byte[] buf = new byte[4096];
                int n;
                try {
                    while ((n = in.read(buf)) > 0) {
                        synchronized (out) {
                            out.write(buf, 0, n);
                        }
                    }
                } catch (Exception ignored) {
                    // процес закрито
                }
            }, "windows-security-info");
            reader.setDaemon(true);
            reader.start();
            if (!p.waitFor(8, TimeUnit.SECONDS)) p.destroyForcibly();
            reader.join(1000);
            String text;
            synchronized (out) {
                text = new String(out.toByteArray(), StandardCharsets.UTF_8);
            }
            for (String line : text.split("\\r?\\n")) {
                line = line.trim();
                if (line.startsWith("AV=")) av = line.substring(3).isEmpty() ? "-" : line.substring(3);
                if (line.startsWith("CFA=")) cfa = cfaText(line.substring(4));
            }
        } catch (Exception e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            log.info("Windows security info unavailable: " + e);
        }
        String res = "Антивіруси: " + av + "\nКонтрольований доступ до папок: " + cfa + "\n";
        if (!"?".equals(av) || !"?".equals(cfa)) cached = res;
        return res;
    }

    /** EnableControlledFolderAccess: 0 вимкнено, 1 увімкнено, 2 лише аудит, 3 і 4 захист дисків, порожньо: невідомо. */
    static String cfaText(String v) {
        switch (v.trim()) {
            case "0": return "вимкнено";
            case "1": return "УВІМКНЕНО";
            case "2": return "лише аудит";
            case "3": return "захист дисків";
            case "4": return "аудит дисків";
            default: return v.trim().isEmpty() ? "невідомо (не Захисник Windows?)" : v.trim();
        }
    }
}
