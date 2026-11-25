/*
 * Copyright (C) 2021-2026 GummerCraft <http://gummercraft.fun>
 */

package com.gummer.launcher.crash;

import com.gummer.launcher.manage.InstanceMods;
import com.skcraft.launcher.Instance;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 25.11.2026 (власник: Main 26.2 падав двічі, «виною були три нові моди, жоден з них наш лаунчер не задетектив як причину
 * проблем»). Часто стек вильоту не називає мода зовсім (другий виліт: NullPointerException у самій грі при переході між
 * серверами), але гра працювала до того, як гравець додав кілька модів. Тому лаунчер пам'ятає, коли збірка востаннє
 * закрилась без помилки ({@code .gummer/last-ok-run}, час старту тієї гри), і після вильоту показує увімкнені моди,
 * що з'явились у теці після цього.
 *
 * <p>Якщо записів ще немає (перший виліт після оновлення лаунчера), береться {@value #FALLBACK_DAYS} дні. Більше
 * {@value #MAX} нових модів не показуємо: це свіжа збірка чи оновлення всіх модів, а не «кілька нових», і порада була б
 * хибною.
 */
public final class RecentMods {

    static final String FILE = ".gummer/last-ok-run";
    static final int FALLBACK_DAYS = 3;
    static final int MAX = 8;

    private RecentMods() {
    }

    /** Гра цієї збірки закрилась без помилки: запам'ятати час її старту. */
    public static void markOk(Instance instance, long startedAt) {
        try {
            File f = new File(instance.getContentDir(), FILE);
            f.getParentFile().mkdirs();
            Files.write(f.toPath(), Long.toString(startedAt).getBytes(StandardCharsets.UTF_8));
        } catch (IOException | RuntimeException ignored) {
            // без запису: наступний виліт візьме запасні дні
        }
    }

    /** Від якого часу мод вважається новим. */
    static long since(Instance instance) {
        try {
            File f = new File(instance.getContentDir(), FILE);
            if (f.isFile()) return Long.parseLong(new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8).trim());
        } catch (IOException | RuntimeException ignored) {
            // запасний варіант нижче
        }
        return System.currentTimeMillis() - FALLBACK_DAYS * 24L * 3600 * 1000;
    }

    /** Увімкнені моди, які гравець може вимкнути, що з'явились після останньої вдалої гри (новіші першими), або порожньо. */
    public static List<InstanceMods.Mod> of(Instance instance) {
        try {
            long since = since(instance);
            List<InstanceMods.Mod> out = new ArrayList<InstanceMods.Mod>();
            for (InstanceMods.Mod m : InstanceMods.list(instance)) {
                if (m.enabled && !m.locked && m.added > since) out.add(m);
            }
            if (out.size() > MAX) return Collections.emptyList();
            Collections.sort(out, (a, b) -> Long.compare(b.added, a.added));
            return out;
        } catch (RuntimeException e) {
            return Collections.emptyList();
        }
    }
}
