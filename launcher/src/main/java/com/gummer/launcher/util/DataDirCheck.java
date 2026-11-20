/*
 * Copyright (C) 2021-2026 GummerCraft <http://gummercraft.fun>
 */

package com.gummer.launcher.util;

import com.gummer.launcher.shared.BootstrapConfig;
import com.skcraft.launcher.Launcher;
import com.skcraft.launcher.util.SharedLocale;
import lombok.extern.java.Log;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.logging.Level;

/**
 * 20.11.2026 (тікет #88, власник: «можна якось "помічником" зафіксити цю проблему з вандрайвом?»): тека даних, з якої
 * гра не може запуститись. Лише Windows.
 *
 * <p><b>Що ловимо:</b> шлях з літерами, яких немає в кодовій сторінці Windows для програм ({@code sun.jnu.encoding},
 * наприклад «Документи» при Cp1252). Java отримує такий шлях з «?» замість літер і не знаходить файли, гра закривається
 * з кодом 1 ще до першого рядка логу. Так у гравця з тікета #88: {@code OneDrive\?????????\GummerCraft} у лозі лаунчера.
 * Сам OneDrive (Документи в OneDrive з латинськими літерами) гру не ламає, тож його не чіпаємо.</p>
 *
 * <p><b>Коли:</b> лише після справжнього вильоту ({@code CrashAnalyzer.badLettersPath}, кнопка у вікні вильоту), не перед
 * кожним запуском (власник: «змушувати міняти теку з документів в appdata я б не дуже хотів без нагальної потреби»).
 * Гравці, у яких гра з Документів іде, цього ніколи не побачать.</p>
 *
 * <p><b>Перехід</b> ({@link #offerMove}): нова тека в {@code bootstrap.yml} (як налаштування «Тека даних») на безпечну
 * ({@code %LOCALAPPDATA%\GummerCraft\data} поруч з установкою лаунчера, якщо шлях підходить, інакше
 * {@code C:\GummerCraft}; тека створюється лише після «Перейти»), копія дрібних файлів гравця (акаунти, налаштування,
 * розмова з підтримкою), перезапуск. Збірки з CDN докачаються самі, старі файли лишаються на місці. Портативний
 * лаунчер (тека не з {@code bootstrap.yml}) не чіпається: лише порада.</p>
 */
@Log
public final class DataDirCheck {

    /** Дрібні файли гравця, які переносяться в нову теку (решту лаунчер докачає). */
    static final String[] CARRY = {"accounts.dat", "config.json", "launcher-ui.json", "stats-id.txt", "support.json",
            "game-prefs.json", "news-cache.json", "packages_cache.json"};

    private DataDirCheck() {
    }

    /** Чи Windows передасть цей шлях Java без втрат (кодова сторінка для програм не Unicode). */
    public static boolean encodable(String path) {
        try {
            String enc = System.getProperty("sun.jnu.encoding");
            if (enc == null || enc.isEmpty()) return true;
            return Charset.forName(enc).newEncoder().canEncode(path);
        } catch (RuntimeException e) {
            return true; // невідома кодова сторінка: не вигадуємо
        }
    }

    /** OneDrive: сам по собі не причина, але нову теку туди не ставимо. */
    static boolean inOneDrive(String path) {
        String p = path.toLowerCase(Locale.ROOT).replace('/', '\\');
        if (p.contains("\\onedrive\\") || p.endsWith("\\onedrive") || p.contains("\\onedrive - ")) return true;
        for (String env : new String[]{"OneDrive", "OneDriveConsumer", "OneDriveCommercial"}) {
            String v = System.getenv(env);
            if (v == null || v.trim().isEmpty()) continue;
            String root = new File(v.trim()).getAbsolutePath().toLowerCase(Locale.ROOT);
            if (p.equals(root) || p.startsWith(root + "\\")) return true;
        }
        return false;
    }

    /** Безпечна тека для даних або null, якщо такої не знайшлось. Нічого не створює. */
    static File safeDir() {
        String local = System.getenv("LOCALAPPDATA");
        if (local != null && !local.trim().isEmpty()) {
            File data = new File(local.trim(), "GummerCraft" + File.separator + "data");
            if (usable(data)) return data;
        }
        String drive = System.getenv("SystemDrive");
        if (drive == null || !drive.matches("[A-Za-z]:")) drive = "C:";
        File root = new File(drive + File.separator + "GummerCraft");
        if (usable(root)) return root;
        return null;
    }

    /** Без запису на диск: чи підходить шлях і чи тека (або найближча наявна батьківська) наша і доступна. */
    static boolean usable(File dir) {
        String path = dir.getAbsolutePath();
        if (!encodable(path) || inOneDrive(path)) return false;
        if (dir.isDirectory()) {
            String[] names = dir.list();
            // чужа непорожня тека: не наша (немає config.json чи instances) - не беремо
            if (names != null && names.length > 0 && !new File(dir, "config.json").isFile() && !new File(dir, "instances").isDirectory()) {
                return false;
            }
            return Files.isWritable(dir.toPath());
        }
        if (dir.exists()) return false; // файл з такою назвою
        File parent = dir.getAbsoluteFile().getParentFile();
        while (parent != null && !parent.exists()) parent = parent.getParentFile();
        return parent != null && parent.isDirectory();
    }

    /** З вікна вильоту (потік інтерфейсу): пояснення і перехід на безпечну теку з перезапуском. */
    public static void offerMove(Window owner, Launcher launcher) {
        final File base = launcher.getBaseDir();
        File configured = null;
        try {
            configured = BootstrapConfig.get().getWorkingDirectory();
        } catch (RuntimeException e) {
            log.log(Level.INFO, "Data dir: no bootstrap.yml", e);
        }
        boolean viaBootstrap = configured != null && configured.getAbsoluteFile().equals(base.getAbsoluteFile());
        File target = viaBootstrap ? safeDir() : null;

        String why = SharedLocale.tr("datadir.badLetters", base.getAbsolutePath());
        String what = target != null ? SharedLocale.tr("datadir.move", target.getAbsolutePath()) : SharedLocale.tr("datadir.manual");
        JTextArea text = new JTextArea(why + "\n\n" + what);
        text.setLineWrap(true);
        text.setWrapStyleWord(true);
        text.setEditable(false);
        text.setOpaque(false);
        text.setBorder(null);
        text.setFont(UIManager.getFont("Label.font"));
        text.setSize(new Dimension(460, 10));
        text.setPreferredSize(new Dimension(460, text.getPreferredSize().height));

        if (target == null) {
            JOptionPane.showMessageDialog(owner, text, SharedLocale.tr("datadir.title"), JOptionPane.WARNING_MESSAGE);
            return;
        }
        String moveLabel = SharedLocale.tr("datadir.moveButton");
        Object[] options = {moveLabel, SharedLocale.tr("button.cancel")};
        int a = JOptionPane.showOptionDialog(owner, text, SharedLocale.tr("datadir.title"), JOptionPane.DEFAULT_OPTION,
                JOptionPane.WARNING_MESSAGE, null, options, options[0]);
        if (a != 0) return;
        try {
            move(base, target);
        } catch (IOException | RuntimeException e) {
            log.log(Level.WARNING, "Data dir: move failed", e);
            JOptionPane.showMessageDialog(owner, SharedLocale.tr("datadir.moveFailed", String.valueOf(e.getMessage())),
                    SharedLocale.tr("datadir.title"), JOptionPane.ERROR_MESSAGE);
            return;
        }
        if (!SelfRestart.relaunchBootstrap()) {
            JOptionPane.showMessageDialog(owner, SharedLocale.tr("datadir.restartManually"), SharedLocale.tr("datadir.title"),
                    JOptionPane.INFORMATION_MESSAGE);
        }
    }

    /** Копія дрібних файлів гравця і нова тека в bootstrap.yml. Пакетний доступ для перевірки. */
    static void move(File from, File to) throws IOException {
        Files.createDirectories(to.toPath());
        for (String name : CARRY) {
            File src = new File(from, name);
            File dst = new File(to, name);
            if (src.isFile() && !dst.exists()) {
                Files.copy(src.toPath(), dst.toPath(), StandardCopyOption.COPY_ATTRIBUTES);
            }
        }
        BootstrapConfig cfg = BootstrapConfig.get();
        cfg.setWorkingDirectory(to.getAbsoluteFile());
        cfg.save();
        log.info("Data dir moved: " + from.getAbsolutePath() + " -> " + to.getAbsolutePath());
    }
}
