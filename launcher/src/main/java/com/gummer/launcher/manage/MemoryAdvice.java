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

    /**
     * 22.11.2026, запобіжник (власник: «дітки, котрі виставлять якусь хуйню, якої прям ТОЧНО буде замало»): нижче цього
     * лаунчер не дає навіть вибір гравця. Без модів 1 ГБ, до 40 модів 2 ГБ, до 100 3 ГБ, більше 4 ГБ; не більше половини
     * пам'яті ПК (але не менше 2 ГБ, як і порада). Оцінка, не замір.
     */
    public static int floorForMods(int mods) {
        int mb;
        if (mods <= 0) mb = 1024;
        else if (mods <= 40) mb = 2048;
        else if (mods <= 100) mb = 3072;
        else mb = 4096;
        int half = halfOfPc();
        if (half > 0) mb = Math.min(mb, Math.max(2048, half));
        return mb;
    }

    /** Звідки береться пам'ять збірки ({@link #effective}). FROM_FLOOR: вибір гравця замалий, піднято запобіжником. */
    public static final int FROM_BUILD = 0, FROM_SETTINGS = 1, FROM_LIST = 2, FROM_MODS = 3, FROM_FLOOR = 4;

    /**
     * 22.11.2026: скільки пам'яті (МБ) отримає гра і звідки: {@code {мегабайти, FROM_*, вибране гравцем}}. Та сама логіка, що була в
     * Runner.addJvmArgs, тепер одна на запуск і на картку «Пам'ять і Java» у вікні «Збірка»:
     * <ol>
     * <li>своя пам'ять збірки (InstanceSettings.memorySettings): рівно вона;</li>
     * <li>інакше загальне налаштування лаунчера; якщо гравець його не змінював ({@code Configuration.memoryChosen}),
     * лаунчер піднімає його до рекомендованого списком модпаків (1.32, лишаючи системі 2 ГБ) і до поради за кількістю
     * модів (17-18.11.2026, для завантажувачів, Modrinth і копій). Менше за загальне налаштування не ставить. Обидві
     * поради не більші за половину пам'яті ПК (власник 22.11: щедра порада, «не більше половини доступної пам'яті»).</li>
     * </ol>
     * Вибір гравця (своя пам'ять збірки або змінене загальне) точний, крім запобіжника {@link #floorForMods}.
     */
    public static int[] effective(com.skcraft.launcher.Configuration config, Instance instance) {
        com.skcraft.launcher.InstanceSettings s = instance == null ? null : instance.getSettings();
        int max;
        int from;
        if (s != null && s.getMemorySettings() != null) {
            int own = s.getMemorySettings().getMaxMemory();
            max = own > 0 ? own : 2048;
            from = FROM_BUILD;
        } else {
            max = config.getMaxMemory() > 0 ? config.getMaxMemory() : 2048;
            from = FROM_SETTINGS;
        }
        int chosen = max;
        if (from == FROM_SETTINGS) {
            int[] r = raiseByAdvice(config, instance, max);
            max = r[0];
            from = r[1];
        }
        if (instance != null) {
            int floor = floorForMods(modCount(instance));
            if (max < floor) {
                max = floor;
                from = FROM_FLOOR;
            }
        }
        return new int[]{max, from, chosen};
    }

    /** Порада списку і порада за модами для загального налаштування, яке гравець не змінював. */
    private static int[] raiseByAdvice(com.skcraft.launcher.Configuration config, Instance instance, int max) {
        int from = FROM_SETTINGS;
        if (instance != null && !config.isMemoryChosen()) {
            Integer recommended = instance.getRecommendedMemory();
            if (recommended != null && recommended > max) {
                int half = halfOfPc();
                int raised = half > 0 ? Math.min(recommended, Math.max(2048, half)) : recommended;
                if (raised > max) {
                    max = raised;
                    from = FROM_LIST;
                }
            }
            if ((instance.isBuiltIn() || instance.isMrpack() || instance.isCopy()) && !instance.isVanilla()) {
                int advice = recommended(instance);
                if (advice > max) {
                    max = advice;
                    from = FROM_MODS;
                }
            }
        }
        return new int[]{max, from};
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
