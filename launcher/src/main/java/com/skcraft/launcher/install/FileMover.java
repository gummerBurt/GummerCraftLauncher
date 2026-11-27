/*
 * SK's Minecraft Launcher
 * Copyright (C) 2010-2014 Albert Pham <http://www.sk89q.com> and contributors
 * Please see LICENSE.txt for license information.
 */

package com.skcraft.launcher.install;

import com.skcraft.launcher.Launcher;
import lombok.NonNull;
import lombok.extern.java.Log;

import java.io.File;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.logging.Level;

import static com.skcraft.launcher.util.SharedLocale.tr;

/**
 * Перенести скачаний файл з тимчасової теки на місце.
 * <p>
 * 27.11.2026 (тікети #134, #138): раніше {@code to.delete(); from.renameTo(to)} мовчки ігнорували будь-яку помилку: файл
 * лишався в тимчасовій теці, а встановлення падало далі на «не той sha1» чи «Отказано в доступе» вже без справжньої
 * причини. Тепер: тека створюється з перевіркою (помилка з шляхом, {@code AccessDeniedException}), перенесення
 * повторюється до {@link #ATTEMPTS} разів з паузами (антивірус чи індексатор ненадовго тримають новий файл), запасний
 * шлях копіювання і видалення. Не вийшло, але на місці вже лежить той самий вміст: успіх. Інакше помилка з причиною.
 */
@Log
public class FileMover implements InstallTask {

    /** Скільки разів пробувати і паузи між спробами, мс (разом близько 2 секунд). */
    static final int ATTEMPTS = 5;
    private static final long[] PAUSES = {100, 250, 500, 1000};

    private final File from;
    private final File to;

    public FileMover(@NonNull File from, @NonNull File to) {
        this.from = from;
        this.to = to;
    }

    @Override
    public void execute(Launcher launcher) throws IOException {
        log.log(Level.INFO, "Moving to {0} (from {1})...", new Object[]{to.getAbsoluteFile(), from.getName()});
        move(from, to);
    }

    /** Надійне перенесення (див. опис класу). Для перевірки поза встановленням. */
    public static void move(File from, File to) throws IOException {
        File parent = to.getAbsoluteFile().getParentFile();
        if (parent != null && !parent.isDirectory()) Files.createDirectories(parent.toPath());
        IOException last = null;
        for (int attempt = 0; attempt < ATTEMPTS; attempt++) {
            if (attempt > 0) pause(PAUSES[Math.min(attempt - 1, PAUSES.length - 1)]);
            if (!from.exists()) {
                // хтось уже переніс (повтор задачі) чи антивірус забрав тимчасовий файл
                if (to.isFile()) return;
                throw last != null ? last : new java.nio.file.NoSuchFileException(from.getAbsolutePath());
            }
            try {
                Files.move(from.toPath(), to.toPath(), StandardCopyOption.REPLACE_EXISTING);
                if (attempt > 0) log.info("Moved " + to.getName() + " on attempt " + (attempt + 1));
                return;
            } catch (IOException e) {
                last = e;
            }
            try {
                // запасний шлях: копія поверх і видалення тимчасового (якщо не видалиться, приберуть чистки тимчасової теки)
                Files.copy(from.toPath(), to.toPath(), StandardCopyOption.REPLACE_EXISTING);
                if (!from.delete()) from.deleteOnExit();
                log.info("Copied " + to.getName() + " instead of moving (attempt " + (attempt + 1) + "): " + last);
                return;
            } catch (IOException e) {
                log.info("Cannot move " + to.getAbsolutePath() + " (attempt " + (attempt + 1) + "): " + last + " / " + e);
                last = e;
            }
        }
        if (to.isFile() && sameContent(from, to)) {
            log.info("Target already has the same content, keeping it: " + to.getAbsolutePath());
            if (!from.delete()) from.deleteOnExit();
            return;
        }
        throw last;
    }

    private static boolean sameContent(File a, File b) {
        try {
            if (a.length() != b.length()) return false;
            return com.skcraft.launcher.util.FileUtils.getShaHash(a).equals(com.skcraft.launcher.util.FileUtils.getShaHash(b));
        } catch (IOException e) {
            return false;
        }
    }

    static void pause(long ms) throws InterruptedIOException {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new InterruptedIOException("interrupted");
        }
    }

    @Override
    public double getProgress() {
        return -1;
    }

    @Override
    public String getStatus() {
        return tr("installer.movingFile", from, to);
    }

}
