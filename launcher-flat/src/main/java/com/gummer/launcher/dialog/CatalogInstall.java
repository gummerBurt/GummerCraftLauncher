/*
 * GummerCraft Launcher
 * Copyright (C) 2026 GummerCraft <http://gummercraft.fun>
 * Please see LICENSE.txt for license information.
 */

package com.gummer.launcher.dialog;

import com.gummer.launcher.manage.InstanceCopies;
import com.skcraft.concurrency.ObservableFuture;
import com.skcraft.launcher.Instance;
import com.skcraft.launcher.Launcher;
import com.skcraft.launcher.dialog.ProgressDialog;
import com.skcraft.launcher.swing.SwingHelper;
import com.skcraft.launcher.util.SharedLocale;
import com.skcraft.launcher.util.SwingExecutor;

import java.awt.Window;
import java.util.EnumSet;
import java.util.function.Consumer;

/**
 * 20.11.2026 (власник: вкладка «Усі» стає «Додати інсталяцію», «меню суто для встановлення нових збірок»): що робить
 * кнопка в каталозі, однаково в трьох виглядах.
 * <ul>
 *   <li>Ваніль і завантажувачі ({@code InstanceTableModel.isFreshInstall}): версія ще не стоїть, ставиться вона сама; уже
 *   стоїть, то поряд нова чиста інсталяція тієї ж версії (копія без модів, налаштувань і світів) з назвою
 *   «Forge 1.16.5 (2)», яку гравець потім може перейменувати.</li>
 *   <li>Сервери GummerCraft і StalDayZ, не встановлені: ставляться.</li>
 * </ul>
 * Гра після встановлення не запускається (власник: «інсталяція збірки більше її автоматично не запускатиме»); {@code done}
 * отримує назву нової збірки, і вікно перемикається на «Встановлені» саме на неї.
 */
public final class CatalogInstall {

    private CatalogInstall() {
    }

    /** Скільки встановлених інсталяцій цього типу і версії («Forge 1.16.5») уже є: чиста версія і її копії. */
    public static int haveCount(Launcher launcher, Instance selected) {
        return have(launcher, selected).size();
    }

    /** Встановлені інсталяції цього типу і версії: спершу сама чиста версія (якщо стоїть), потім копії. */
    static java.util.List<Instance> have(Launcher launcher, Instance selected) {
        java.util.List<Instance> out = new java.util.ArrayList<Instance>();
        if (selected == null) return out;
        String kind = InstanceCopies.kindTitleOf(selected);
        if (kind == null) return out;
        synchronized (launcher.getInstances()) {
            for (Instance i : launcher.getInstances().getInstances()) {
                if (!i.isLocal() || !i.isInstalled() || i.isMrpack()) continue;
                if (!i.isBuiltIn() && !i.isCopy()) continue; // модпаки серверів не рахуємо
                if (kind.equals(InstanceCopies.kindTitleOf(i))) {
                    if (i == selected) out.add(0, i); else out.add(i);
                }
            }
        }
        return out;
    }

    /** Підпис під кнопкою в каталозі: «вже є: 2», або null, якщо такої ще немає. */
    public static String haveCaption(Launcher launcher, Instance selected) {
        int n = haveCount(launcher, selected);
        return n > 0 ? SharedLocale.tr("launcher.catalog.have", n) : null;
    }

    /** Встановити з каталогу (див. опис класу). done викликається в потоці Swing лише після успіху. */
    public static void install(final Window owner, final Launcher launcher, final Instance selected,
                               final java.util.function.Function<String, Instance> reload, final Consumer<String> done) {
        if (selected == null) return;
        boolean fresh = selected.isBuiltIn() && selected.isLocal() && selected.isInstalled();
        if (!fresh) {
            InstanceManageDialog.installOnly(owner, launcher, selected, () -> {
                if (done != null) done.accept(selected.getName());
            });
            return;
        }
        // 21.11.2026 (власник: «я натиснув встановити і мені поставило копію», «неюзерфрендлі трохи»): така версія вже
        // стоїть, тож спершу питаємо: відкрити її у «Встановлених» чи справді поставити ще одну окрему
        java.util.List<Instance> existing = have(launcher, selected);
        if (!existing.isEmpty()) {
            StringBuilder names = new StringBuilder();
            for (int k = 0; k < existing.size() && k < 3; k++) {
                if (k > 0) names.append(", ");
                names.append('«').append(existing.get(k).getTitle()).append('»');
            }
            if (existing.size() > 3) names.append(", ...");
            Object[] options = {SharedLocale.tr("launcher.catalog.again.open"), SharedLocale.tr("launcher.catalog.again.another"),
                    SharedLocale.tr("button.cancel")};
            int answer = javax.swing.JOptionPane.showOptionDialog(owner,
                    SharedLocale.tr("launcher.catalog.again.text", InstanceCopies.kindTitleOf(selected), names.toString()),
                    SharedLocale.tr("launcher.catalog.again.title"), javax.swing.JOptionPane.YES_NO_CANCEL_OPTION,
                    javax.swing.JOptionPane.QUESTION_MESSAGE, null, options, options[0]);
            if (answer == 0) {
                if (done != null) done.accept(existing.get(0).getName());
                return;
            }
            if (answer != 1) return;
        }
        int next = existing.size() + 1;
        final String title = SharedLocale.tr("launcher.catalog.copyTitle", InstanceCopies.kindTitleOf(selected), next);
        final InstanceCopies.Copier copier = new InstanceCopies.Copier(launcher, selected, title,
                EnumSet.noneOf(InstanceCopies.Part.class));
        final com.google.common.util.concurrent.ListenableFuture<String> task = launcher.getExecutor().submit(copier);
        ObservableFuture<String> future = new ObservableFuture<String>(task, copier);
        ProgressDialog.showProgress(owner, future, SharedLocale.tr("manage.copy.progressTitle"), SharedLocale.tr("manage.copy.preparing"));
        SwingHelper.addErrorDialogCallback(owner, future);
        future.addListener(() -> {
            try {
                final String name = task.get();
                // копія створюється «не встановленою» (гра доставляється при першому запуску): доставляємо відразу, щоб у
                // «Встановлених» на ній була кнопка «Запуск». Бібліотеки вже на диску від оригіналу, тож це швидко
                Instance copy = reload != null ? reload.apply(name) : null;
                if (copy != null) {
                    InstanceManageDialog.installOnly(owner, launcher, copy, () -> {
                        if (done != null) done.accept(name);
                    });
                } else if (done != null) {
                    done.accept(name);
                }
            } catch (Exception ignored) {
                // помилку показав addErrorDialogCallback, недороблену копію Copier прибрав
            }
        }, SwingExecutor.INSTANCE);
    }

}
