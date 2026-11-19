/*
 * Copyright (C) 2021-2026 GummerCraft <http://gummercraft.fun>
 */

package com.gummer.launcher.install;

import java.io.File;
import java.io.IOException;
import java.util.Locale;

/**
 * 19.11.2026 (власник: «Нестача місця на диску перед завантаженням. -- го»): місця не вистачить ще до початку
 * завантаження. Раніше гравець дізнавався про це на середині, коли Windows відмовляла в записі («There is not enough
 * space on the disk», ErrorExplainer «disk»). Тепер {@link #check} перед завантаженням порівнює суму відомих розмірів
 * файлів з вільним місцем, і вікно помилки каже, скільки треба і скільки є ({@code errors.why.diskNeed}).
 *
 * <p>Запас навмисно малий (300 МБ): хибна відмова гірша за пізню помилку, бо не дала б поставити збірку взагалі.
 * Файли без відомого розміру не рахуються, тож справжня потреба може бути більшою; тоді лишається стара пізня помилка.
 */
public class DiskSpaceException extends IOException {

    static final long RESERVE = 300L * 1024 * 1024;

    private final long need;
    private final long free;
    private final File dir;

    public DiskSpaceException(long need, long free, File dir) {
        // текст англійською для логу; «not enough space» впізнає й старий ErrorExplainer
        super("Not enough space on the disk: " + need + " bytes needed, " + free + " free in " + dir);
        this.need = need;
        this.free = free;
        this.dir = dir;
    }

    /**
     * @param dir   куди ляже завантажене (може ще не існувати: перевіряється найближча наявна батьківська тека)
     * @param bytes скільки буде завантажено (сума відомих розмірів)
     */
    public static void check(File dir, long bytes) throws DiskSpaceException {
        if (dir == null || bytes <= 0) return;
        File d = dir.getAbsoluteFile();
        while (d != null && !d.exists()) d = d.getParentFile();
        if (d == null) return;
        long free = d.getUsableSpace();
        if (free <= 0) return; // ОС не сказала (мережевий диск): не заважаємо
        long need = bytes + RESERVE;
        if (free < need) throw new DiskSpaceException(need, free, d);
    }

    public long getNeed() {
        return need;
    }

    public long getFree() {
        return free;
    }

    /** Корінь диска для тексту («C:\»), або сама тека, якщо кореня не знайти. */
    public String getDrive() {
        File r = dir;
        while (r != null && r.getParentFile() != null) r = r.getParentFile();
        return r == null ? String.valueOf(dir) : r.getPath();
    }

    /** «1,3 ГБ» / «850 МБ», одиниці з lang. */
    public static String human(long bytes, String gb, String mb) {
        double g = bytes / (1024.0 * 1024 * 1024);
        if (g >= 1) return String.format(Locale.ROOT, "%.1f %s", g, gb);
        return Math.max(1, Math.round(bytes / (1024.0 * 1024))) + " " + mb;
    }
}
