/*
 * Copyright (C) 2021-2026 GummerCraft <http://gummercraft.fun>
 */

package com.gummer.launcher.manage;

import com.skcraft.launcher.Instance;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.util.zip.ZipFile;

/**
 * 25.11.2026 (власник про виліт Main 26.2 «Fancy World Animations → EntityCulling»: «а чому б не запропонувати скачати
 * правильний ентіті кулінг? ... Якщо є якась залежність, щоб лаунчер її детектив і докачував»). Виліт
 * {@code NoClassDefFoundError}: один мод звертається до класу іншого, якого в цій збірці немає. Тут лагодимо саму
 * залежність, а не вимикаємо мод:
 * <ul>
 * <li>залежність є, але стара: оновити її з Modrinth чи CurseForge до найновішої версії під цю гру ({@link ModUpdates}) і перевірити,
 * що потрібний клас у новій версії справді є; якщо немає, стара версія повертається ({@link ModRollback}), щоб не
 * лишити гравця з іншою, теж непридатною версією;</li>
 * <li>залежності немає зовсім: докачати її ({@link DepFetch}: спершу за залежностями мода на Modrinth чи CurseForge, потім за назвою
 * пакета класу, наприклад {@code dev/tr7zw/entityculling/} -> {@code entityculling}).</li>
 * </ul>
 * Не з потоку інтерфейсу.
 */
public final class ClassFix {

    private static final Logger log = Logger.getLogger(ClassFix.class.getName());

    /** Чим закінчилось. */
    public enum Outcome {
        /** Клас тепер є (оновлено чи докачано). */
        FIXED,
        /** Новішої версії під цю гру немає ні на Modrinth, ні на CurseForge: нічого не змінено. */
        NO_UPDATE,
        /** Нова версія теж без потрібного класу: стару повернуто (докачане лишається, воно нічого не ламає). */
        NO_CLASS,
        /** Не знайшлося ні на Modrinth, ні на CurseForge, що докачати. */
        NOT_FOUND
    }

    public static final class Result {
        public final Outcome outcome;
        /** Нова версія (оновлення) чи назви поставлених файлів (докачування), для тексту. */
        public final String what;
        /** Повернута стара версія після невдалої спроби (для другої спроби), або null. */
        File file;

        Result(Outcome outcome, String what) {
            this.outcome = outcome;
            this.what = what;
        }
    }

    private ClassFix() {
    }

    /**
     * Оновити jar залежності, у якому бракує класу {@code classEntry} (шлях усередині jar, {@code a/b/C.class}).
     * 25.11.2026 (власник: «вони мають доповнювати та страхувати одне одного»): спершу джерело, яке знає цей файл
     * (Modrinth, інакше CurseForge за fingerprint), а якщо там новішої версії немає чи в ній теж немає класу, друге:
     * CurseForge для модів з Modrinth, той самий мод на Modrinth (точний збіг slug чи назви) для модів з CurseForge.
     * Кожна невдала спроба повертає стару версію.
     */
    public static Result updateDep(Instance instance, File depFile, String classEntry) throws IOException, InterruptedException {
        String game = ModrinthBrowse.gameVersionOf(instance);
        String[] loaders = ModrinthBrowse.loadersOf(ModrinthBrowse.Kind.MOD, instance);
        File cur = depFile;
        ModUpdates.Update first = ModUpdates.check(Collections.singletonList(cur), game, loaders).get(cur);
        String tried = null;
        if (first != null) {
            Result r = tryUpdate(first, game, loaders, classEntry);
            if (r.outcome == Outcome.FIXED) return r;
            tried = first.versionNumber;
            cur = r.file != null ? r.file : cur;
        }
        ModUpdates.Update second = null;
        if (first == null || first.cf == null) {
            try {
                second = ModUpdates.checkCurse(Collections.singletonList(cur), game, loaders).get(cur);
            } catch (IOException e) {
                log.info("Class fix: CurseForge unavailable: " + e);
            }
        }
        if (second == null && (first == null || first.cf != null)) {
            // мод з CurseForge: той самий мод на Modrinth, лише версія, новіша за поставлений файл
            com.gummer.launcher.curseforge.CurseForge.FileInfo id = ModUpdates.curseIdentity(cur);
            if (id != null) {
                String date = null;
                try {
                    date = com.gummer.launcher.curseforge.CurseBrowse.fileDate(com.gummer.launcher.curseforge.CurseBrowse.shared(),
                            id.modId, id.id, game, loaders);
                } catch (IOException e) {
                    log.info("Class fix: CurseForge file date unavailable: " + e);
                }
                second = ModUpdates.modrinthSame(cur, id.modName, com.gummer.launcher.curseforge.CurseForge.slugOf(id.modUrl),
                        id.displayName, date, game, loaders);
            }
        }
        // той самий файл (байт у байт), що вже не допоміг з першого джерела, вдруге не качаємо
        if (second != null && first != null && sha1Of(second) != null && sha1Of(second).equalsIgnoreCase(sha1Of(first))) {
            log.info("Class fix: " + second.source() + " has the same file as " + first.source() + ", skipped");
            second = null;
        }
        if (second != null) {
            Result r = tryUpdate(second, game, loaders, classEntry);
            if (r.outcome == Outcome.FIXED) return r;
            tried = tried == null ? second.versionNumber : tried + ", " + second.versionNumber;
        }
        return tried == null ? new Result(Outcome.NO_UPDATE, null) : new Result(Outcome.NO_CLASS, tried);
    }

    /** sha1 нового файлу оновлення, або null. */
    private static String sha1Of(ModUpdates.Update u) {
        if (u.cf != null) return u.cf.sha1;
        return u.primary != null ? u.primary.path("hashes").path("sha1").asText(null) : null;
    }

    /** Поставити оновлення і перевірити клас; немає класу: повернути стару версію ({@code file} у результаті). */
    private static Result tryUpdate(ModUpdates.Update u, String game, String[] loaders, String classEntry)
            throws IOException, InterruptedException {
        File target = ModUpdates.apply(u, game, loaders, new ArrayList<String>(), new ArrayList<String>());
        if (classEntry == null || has(target, classEntry)) {
            log.info("Class fix: " + u.file.getName() + " -> " + target.getName() + " (" + u.source() + ") has " + classEntry);
            return new Result(Outcome.FIXED, u.versionNumber + " (" + u.source() + ")");
        }
        // нова версія теж не та: повернути стару, з якою хоча б решта модів працювала
        ModRollback.Entry back = ModRollback.recentFor(target);
        File restored = back != null ? ModRollback.rollback(back) : null;
        log.info("Class fix: " + target.getName() + " (" + u.source() + ") has no " + classEntry + ", rolled back: " + (restored != null));
        Result r = new Result(Outcome.NO_CLASS, u.versionNumber);
        r.file = restored;
        return r;
    }

    /** Докачати залежність, якої немає в збірці. {@code depId}: здогад за пакетом класу. */
    public static Result fetchDep(Instance instance, File culprit, String depId, String classEntry) throws InterruptedException {
        List<String> missing = new ArrayList<String>();
        List<String> got = DepFetch.fetch(instance, culprit,
                depId == null ? Collections.<String>emptyList() : Collections.singletonList(depId), missing);
        if (got.isEmpty()) return new Result(Outcome.NOT_FOUND, depId);
        if (classEntry != null && find(instance, classEntry) == null) {
            log.info("Class fix: fetched " + got + " but " + classEntry + " is still missing");
            return new Result(Outcome.NO_CLASS, String.join(", ", got));
        }
        return new Result(Outcome.FIXED, String.join(", ", got));
    }

    /** Увімкнений мод збірки, у якому є цей клас, або null. */
    public static File find(Instance instance, String classEntry) {
        for (InstanceMods.Mod m : InstanceMods.list(instance)) {
            if (m.enabled && has(m.file, classEntry)) return m.file;
        }
        return null;
    }

    static boolean has(File jar, String classEntry) {
        try (ZipFile z = new ZipFile(jar)) {
            return z.getEntry(classEntry) != null;
        } catch (IOException | RuntimeException e) {
            return false;
        }
    }
}
