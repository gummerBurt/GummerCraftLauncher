/*
 * SKCraft Launcher
 * Copyright (C) 2010-2014 Albert Pham <http://www.sk89q.com> and contributors
 * Please see LICENSE.txt for license information.
 */

package com.skcraft.launcher.launch;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.skcraft.concurrency.ObservableFuture;
import com.skcraft.launcher.Instance;
import com.skcraft.launcher.Launcher;
import com.skcraft.launcher.auth.Session;
import com.skcraft.launcher.dialog.AccountSelectDialog;
import com.skcraft.launcher.dialog.ProcessConsoleFrame;
import com.skcraft.launcher.dialog.ProgressDialog;
import com.skcraft.launcher.launch.LaunchOptions.UpdatePolicy;
import com.skcraft.launcher.launch.runtime.JavaRuntime;
import com.skcraft.launcher.model.minecraft.JavaVersion;
import com.skcraft.launcher.persistence.Persistence;
import com.skcraft.launcher.swing.SwingHelper;
import com.skcraft.launcher.update.Updater;
import com.skcraft.launcher.util.SharedLocale;
import com.skcraft.launcher.util.SwingExecutor;
import lombok.RequiredArgsConstructor;
import lombok.extern.java.Log;
import org.apache.commons.io.FileUtils;

import javax.annotation.Nullable;
import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.util.Date;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.function.BiPredicate;
import java.util.logging.Level;

import static com.google.common.util.concurrent.MoreExecutors.sameThreadExecutor;
import static com.skcraft.launcher.util.SharedLocale.tr;

@Log
public class LaunchSupervisor {

    private final Launcher launcher;

    public LaunchSupervisor(Launcher launcher) {
        this.launcher = launcher;
    }

    public void launch(LaunchOptions options) {
        final Window window = options.getWindow();
        final Instance instance = options.getInstance();
        final LaunchListener listener = options.getListener();

        try {
            boolean update = options.getUpdatePolicy().isUpdateEnabled() && instance.isUpdatePending();

            // 22.11.2026: відеокарти читаються заново на кожен запуск (за замовчуванням гра йде на дискретній, Gpu),
            // у фоні, поки триває вхід і перевірка файлів
            com.gummer.launcher.util.Gpu.refreshAsync();

            // Store last access date
            Date now = new Date();
            instance.setLastAccessed(now);
            Persistence.commitAndForget(instance);

            // Perform login
            final Session session;
            if (options.getSession() != null) {
                session = options.getSession();
            } else {
                boolean[] friend = new boolean[1];
                session = AccountSelectDialog.showAccountRequest(window, launcher, instance, friend);
                if (session == null) {
                    return;
                }
                // 26.11.2026: галочка «Грати з другом»: помічник ставить і налаштовує мод, далі звичайний запуск
                com.gummer.launcher.ui.UiHooks.InstanceAction fp = com.gummer.launcher.ui.UiHooks.friendPlay;
                if (friend[0] && fp != null) fp.run(window, instance);
            }

            // If we have to update, we have to update
            if (!instance.isInstalled()) {
                update = true;
            } else if (update && launcher.getInstances().isOffline()) {
                // 1.32: список модпаків не завантажився (немає інтернету) - оновлення точно не вийде, граємо встановлену
                log.info("Offline: skipping the pending update of " + instance.getName());
                update = false;
            }

            // 1.32.2: Mac з Apple Silicon: старій версії гри потрібна Java для Intel (див. MacRosetta), а збірку встановлено
            // старим лаунчером і цієї Java ще немає. Оновлення докачає лише її (решта файлів уже на місці).
            if (!update && instance.isInstalled() && !launcher.getInstances().isOffline()
                    && com.gummer.launcher.util.MacRosetta.intelJavaMissing(launcher, instance)) {
                log.info("Apple Silicon: the Intel Java for " + instance.getName() + " is missing, updating to install it");
                update = true;
                // 1.32.3: без цього Updater бачив «оновлень немає» і нічого не ставив (у 1.32.2 Java не докачувалась,
                // гра стартувала на arm64 Java і падала, допомагало лише перевстановлення). Скидається в Updater після оновлення.
                instance.setUpdatePending(true);
            }

            // 1.36.1 (java4shell): Java гри не та, що в маніфесті CDN (нова збірка Java, наприклад з виправленням
            // вразливості): оновлення докачає лише змінені файли Java, див. JavaSecurity. Скидається в Updater.
            if (!update && options.getUpdatePolicy().isUpdateEnabled() && instance.isInstalled()
                    && !launcher.getInstances().isOffline()
                    && com.gummer.launcher.util.JavaSecurity.runtimeStale(launcher, instance)) {
                update = true;
                instance.setUpdatePending(true);
            }

            if (update) {
                // Execute the updater
                Updater updater = new Updater(launcher, instance);
                updater.setOnline(options.getUpdatePolicy() == UpdatePolicy.ALWAYS_UPDATE || session.isOnline());
                ObservableFuture<Instance> future = new ObservableFuture<Instance>(
                        launcher.getExecutor().submit(updater), updater);

                // Show progress
                ProgressDialog.showProgress(window, future, SharedLocale.tr("launcher.updatingTitle"), tr("launcher.updatingStatus", instance.getTitle()));
                // встановлена збірка + помилка мережі: замість помилки питання «грати встановлену?» (див. onFailure нижче)
                final boolean installed = instance.isInstalled();
                if (!installed) {
                    SwingHelper.addErrorDialogCallback(window, future);
                }

                // Update the list of instances after updating
                future.addListener(new Runnable() {
                    @Override
                    public void run() {
                        SwingUtilities.invokeLater(new Runnable() {
                            @Override
                            public void run() {
                                listener.instancesUpdated();
                            }
                        });
                    }
                }, SwingExecutor.INSTANCE);

                // On success, launch also
                Futures.addCallback(future, new FutureCallback<Instance>() {
                    @Override
                    public void onSuccess(Instance result) {
                        launch(window, instance, session, listener);
                    }

                    @Override
                    public void onFailure(Throwable t) {
                        if (!installed || t instanceof InterruptedException || t instanceof CancellationException) {
                            return;
                        }
                        // 1.32: оновлення впало через мережу, а збірка вже встановлена - можна грати її (без інтернету)
                        if (com.skcraft.launcher.dialog.AccountSelectDialog.isNetworkFailure(t)) {
                            log.log(Level.WARNING, "Update failed, offering the installed version", t);
                            if (SwingHelper.confirmDialog(window,
                                    tr("launcher.updateOfflineFallback", instance.getTitle()),
                                    tr("launcher.updateOfflineFallbackTitle"))) {
                                launch(window, instance, session, listener);
                            }
                            return;
                        }
                        String message = t.getLocalizedMessage() != null ? t.getLocalizedMessage() : tr("errors.genericError");
                        log.log(Level.WARNING, "Task failed", t);
                        SwingHelper.showErrorDialog(window, message, tr("errorTitle"),
                                t instanceof com.skcraft.launcher.LauncherException && t.getCause() != null ? t.getCause() : t);
                    }
                }, SwingExecutor.INSTANCE);
            } else {
                launch(window, instance, session, listener);
            }
        } catch (ArrayIndexOutOfBoundsException e) {
            SwingHelper.showErrorDialog(window, SharedLocale.tr("launcher.noInstanceError"), SharedLocale.tr("launcher.noInstanceTitle"));
        }
    }

    private void launch(Window window, Instance instance, Session session, final LaunchListener listener) {
        final File extractDir = launcher.createExtractDir();

        // Get the process
        Runner task = new Runner(launcher, instance, session, extractDir, new RuntimeVerifier(instance));
        ObservableFuture<Process> processFuture = new ObservableFuture<Process>(
                launcher.getExecutor().submit(task), task);

        // Show process for the process retrieval
        ProgressDialog.showProgress(
                window, processFuture, SharedLocale.tr("launcher.launchingTItle"), tr("launcher.launchingStatus", instance.getTitle()));

        // If the process is started, get rid of this window
        Futures.addCallback(processFuture, new FutureCallback<Process>() {
            @Override
            public void onSuccess(Process result) {
                // 1.33.1: анонімна подія «гру запущено» (яка збірка), див. com.gummer.launcher.stats.LaunchStats
                com.gummer.launcher.stats.LaunchStats.gameStarted(launcher, instance);
                SwingUtilities.invokeLater(listener::gameStarted);
            }

            @Override
            public void onFailure(Throwable t) {
            }
        });

        // Watch the created process
        ListenableFuture<ProcessConsoleFrame> future = Futures.transform(
                processFuture, new LaunchProcessHandler(launcher, instance, window), launcher.getExecutor());
        SwingHelper.addErrorDialogCallback(null, future);

        // Clean up at the very end
        future.addListener(() -> {
            try {
                log.info("Process ended; cleaning up " + extractDir.getAbsolutePath());
                FileUtils.deleteDirectory(extractDir);
            } catch (IOException e) {
                log.log(Level.WARNING, "Failed to clean up " + extractDir.getAbsolutePath(), e);
            }
        }, sameThreadExecutor());

        // Hook up launch listener
        Futures.addCallback(future, new FutureCallback<ProcessConsoleFrame>() {
            @Override
            public void onSuccess(@Nullable ProcessConsoleFrame result) {
                // gameStarted was only invoked on success above, so only call gameClosed on success
                listener.gameClosed();
            }

            @Override
            public void onFailure(Throwable t) {
                // likely user cancellation
                if (!(t instanceof CancellationException)) {
                    log.info("Process failure: " + t.getLocalizedMessage());
                }
            }
        }, SwingExecutor.INSTANCE);
    }

    @RequiredArgsConstructor
    static class RuntimeVerifier implements BiPredicate<JavaRuntime, JavaVersion> {
        private final Instance instance;

        @Override
        public boolean test(JavaRuntime javaRuntime, JavaVersion javaVersion) {
            ListenableFuture<Boolean> fut = SwingExecutor.INSTANCE.submit(() -> {
                Object[] options = new Object[]{
                        tr("button.cancel"),
                        tr("button.launchAnyway"),
                };

                String message = tr("runner.wrongJavaVersion",
                        instance.getTitle(), javaVersion.getMajorVersion(), javaRuntime.getVersion());
                int picked = JOptionPane.showOptionDialog(null,
                        SwingHelper.htmlWrap(message),
                        tr("launcher.javaMismatchTitle"),
                        JOptionPane.DEFAULT_OPTION,
                        JOptionPane.WARNING_MESSAGE,
                        null,
                        options,
                        null);

                return picked == 1;
            });

            try {
                return fut.get();
            } catch (ExecutionException | InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
