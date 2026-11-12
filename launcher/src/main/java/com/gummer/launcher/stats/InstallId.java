/*
 * Copyright (C) 2021-2026 GummerCraft <http://gummercraft.fun>
 * Please see LICENSE.txt for license information.
 */

package com.gummer.launcher.stats;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.SecureRandom;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Анонімний ідентифікатор установки лаунчера (з 1.32), щоб рахувати справжню кількість людей, а не IP-адрес
 * (IP міняються, кілька людей сидять за одним роутером).
 *
 * <p>Що це і чого тут немає:</p>
 * <ul>
 *   <li>16 випадкових шістнадцяткових символів з {@link SecureRandom}. Він ні з чого не виводиться: ні з ніка, ні з
 *       акаунта Microsoft, ні з заліза, ні з імені комп'ютера. Двох однакових установок він не пов'яже, якщо файл
 *       не скопіювали.</li>
 *   <li>Лежить у теці лаунчера у файлі {@code stats-id.txt}. Видалити файл - буде новий ID.</li>
 *   <li>Вписати в цей файл {@code off} - лаунчер нічого не надсилає.</li>
 *   <li>Надсилається лише разом із запитом списку модпаків v2 на наш CDN (параметр {@code i}), раз на старт лаунчера.
 *       Іншим сайтам (Mojang, Microsoft, GitHub) не надсилається.</li>
 *   <li>Статистика Jarvis зберігає лише солоний хеш ID, як і для IP.</li>
 * </ul>
 */
public final class InstallId {

    private static final Logger log = Logger.getLogger(InstallId.class.getName());
    private static final String FILE = "stats-id.txt";
    private static final String OFF = "off";
    private static String cached;

    private InstallId() {
    }

    /** ID цієї установки, або null, якщо гравець вимкнув ({@code off}) чи файл не вдалося ні прочитати, ні створити. */
    public static synchronized String get(File baseDir) {
        if (cached != null) {
            return cached.isEmpty() ? null : cached;
        }
        File file = new File(baseDir, FILE);
        try {
            if (file.isFile()) {
                String text = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8).trim();
                if (text.equalsIgnoreCase(OFF)) {
                    cached = "";
                    return null;
                }
                if (text.matches("[0-9a-f]{16}")) {
                    cached = text;
                    return cached;
                }
            }
            byte[] bytes = new byte[8];
            new SecureRandom().nextBytes(bytes);
            StringBuilder sb = new StringBuilder(16);
            for (byte b : bytes) {
                sb.append(String.format("%02x", b & 0xff));
            }
            String id = sb.toString();
            File dir = file.getParentFile();
            if (dir != null) {
                dir.mkdirs();
            }
            Files.write(file.toPath(), (id + System.lineSeparator()).getBytes(StandardCharsets.UTF_8));
            cached = id;
            return id;
        } catch (Exception e) {
            log.log(Level.INFO, "Install id is unavailable, statistics will count this start by IP only", e);
            cached = "";
            return null;
        }
    }
}
