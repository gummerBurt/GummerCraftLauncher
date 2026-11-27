/*
 * GummerCraft Launcher
 */

package com.gummer.launcher.manage;

import com.skcraft.launcher.Instance;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 27.11.2026 (тікет #139, власник: «Вимикати мод разом з тими, кому він потрібен»): кнопка «Вимкнути Veil» у помічнику
 * вильотів лишала Sable, який без Veil не запускається, і гра падала вже на відсутній залежності. Тут той самий
 * ланцюжок, що в «Мої моди» ({@code InstanceManageDialog.dependentsOf}): увімкнені моди, яким після вимкнення
 * переданих бракуватиме залежності, і далі їхні залежні. Моди модпака StalDayZ (locked) не чіпаються.
 */
public final class ModDependents {

    private ModDependents() {
    }

    /** Увімкнені моди збірки: спершу ті, що дають файли roots, далі ті, хто без них не запуститься. */
    public static List<InstanceMods.Mod> withDependents(Instance instance, Collection<File> roots) {
        List<InstanceMods.Mod> all = new ArrayList<InstanceMods.Mod>();
        for (InstanceMods.Mod m : InstanceMods.list(instance)) if (m.enabled) all.add(m);
        LinkedHashSet<InstanceMods.Mod> off = new LinkedHashSet<InstanceMods.Mod>();
        for (File f : roots) {
            for (InstanceMods.Mod m : all) if (m.file.equals(f)) off.add(m);
        }
        if (off.isEmpty()) return new ArrayList<InstanceMods.Mod>();
        boolean grew = true;
        while (grew) {
            grew = false;
            Set<String> left = new HashSet<String>();
            for (InstanceMods.Mod m : all) if (!off.contains(m)) left.addAll(ModInfo.of(m.file).provides);
            for (InstanceMods.Mod m : all) {
                if (off.contains(m) || m.locked) continue;
                for (String d : ModInfo.of(m.file).depends) {
                    if (!left.contains(d) && providedByAny(off, d)) {
                        off.add(m);
                        grew = true;
                        break;
                    }
                }
            }
        }
        return new ArrayList<InstanceMods.Mod>(off);
    }

    private static boolean providedByAny(Collection<InstanceMods.Mod> mods, String id) {
        for (InstanceMods.Mod m : mods) if (ModInfo.of(m.file).provides.contains(id)) return true;
        return false;
    }

    /** Назви для гравця: «Veil» і «Sable», «A», «B» і «C». */
    public static String names(List<String> names, String and) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < names.size(); i++) {
            if (i > 0) b.append(i == names.size() - 1 ? " " + and + " " : ", ");
            b.append('«').append(names.get(i)).append('»');
        }
        return b.toString();
    }
}
