/*
 * Copyright (C) 2021-2026 GummerCraft <http://gummercraft.fun>
 */

package com.gummer.launcher.manage;

import com.skcraft.launcher.Instance;
import com.skcraft.launcher.util.Environment;

import java.io.File;
import java.util.Locale;

/**
 * 17.11.2026 (власник: «Пам'ять під модпак ... радити об'єм за кількістю модів - ок»; разом з пунктом плану
 * «попередження, коли виділено забагато пам'яті»). Скільки пам'яті гри порадити збірці за кількістю увімкнених модів і
 * чи не забагато виділено: більше половини пам'яті ПК (аналіз 15.11: на 14 ГБ гравці мали вдвічі менше FPS і сотні
 * фризів, ніж на 6 ГБ).
 *
 * <p>Пороги (оцінка, не замір): без модів 2 ГБ, до 40 модів 3 ГБ, до 100 4 ГБ, до 200 6 ГБ, більше 8 ГБ. Порада не
 * більша за половину пам'яті ПК і не менша за 2 ГБ.</p>
 */
public final class MemoryAdvice {

    private MemoryAdvice() {
    }

    /** Пам'ять ПК у МБ, або 0, якщо невідомо. */
    public static long totalMb() {
        long t = Environment.getTotalSystemMemory();
        return t > 0 ? t / (1024L * 1024L) : 0;
    }

    /** Увімкнені моди в теці mods збірки. */
    public static int modCount(Instance instance) {
        if (instance == null) return 0;
        if (instance.peekDir() == null) return 0;
        File[] files = InstanceMods.peekModsDir(instance).listFiles(); // без створення тек (17.11 ~23:15)
        if (files == null) return 0;
        int n = 0;
        for (File f : files) {
            String name = f.getName().toLowerCase(Locale.ROOT);
            if (f.isFile() && (name.endsWith(".jar") || name.endsWith(".zip"))) n++;
        }
        return n;
    }

    /** Порада за кількістю модів, без урахування пам'яті ПК. */
    public static int forMods(int mods) {
        if (mods <= 0) return 2048;
        if (mods <= 40) return 3072;
        if (mods <= 100) return 4096;
        if (mods <= 200) return 6144;
        return 8192;
    }

    /** Половина пам'яті ПК (МБ), або 0, якщо невідомо. */
    public static int halfOfPc() {
        long t = totalMb();
        return t > 0 ? (int) (t / 2) : 0;
    }

    /** Порада для збірки: за модами, не більше половини пам'яті ПК і не менше 2 ГБ. */
    public static int recommended(Instance instance) {
        int mb = forMods(modCount(instance));
        int half = halfOfPc();
        if (half > 0) mb = Math.min(mb, Math.max(2048, half));
        return mb;
    }

    /** Чи забагато: більше половини пам'яті ПК. */
    public static boolean tooMuch(int maxMb) {
        int half = halfOfPc();
        return half > 0 && maxMb > half;
    }

    /** ГБ для тексту: 4096 -> «4», 3584 -> «3,5». */
    public static String gb(long mb) {
        double g = mb / 1024.0;
        return Math.abs(g - Math.round(g)) < 0.05 ? String.valueOf(Math.round(g))
                : String.format(Locale.ROOT, "%.1f", g).replace('.', ',');
    }
}
