/*
 * SK's Minecraft Launcher
 * Copyright (C) 2010-2014 Albert Pham <http://www.sk89q.com> and contributors
 * Copyright (C) 2021-2025 GummerCraft <http://gummercraft.fun>
 * Please see LICENSE.txt for license information.
 */

package com.skcraft.launcher;

import com.fasterxml.jackson.core.type.TypeReference;
import com.gummer.launcher.swing.SwingExtras;
import com.skcraft.concurrency.DefaultProgress;
import com.skcraft.concurrency.ProgressObservable;
import com.gummer.launcher.model.java.JavaManifest;
import com.skcraft.launcher.model.modpack.ManifestInfo;
import com.skcraft.launcher.model.modpack.PackageList;
import com.skcraft.launcher.persistence.Persistence;
import com.skcraft.launcher.util.Environment;
import com.skcraft.launcher.util.HttpRequest;
import com.skcraft.launcher.util.SharedLocale;
import lombok.Getter;
import lombok.NonNull;
import lombok.extern.java.Log;
import org.apache.commons.io.filefilter.DirectoryFileFilter;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.net.URL;
import java.util.*;
import java.util.concurrent.Callable;

import static com.skcraft.launcher.LauncherUtils.concat;

/**
 * Stores the list of instances.
 */
@Log
public class InstanceList {

    private final Launcher launcher;
    @Getter
    private final List<Instance> instances = new ArrayList<Instance>();

    @Getter
    private HashMap<String, JavaManifest[]> javaVersions = new HashMap<>();

    /** Назви груп паків зі списку v2 (id групи -> назва для картки), порожньо для списку v1. */
    @Getter
    private Map<String, String> groupTitles = new LinkedHashMap<String, String>();

    /** Описи груп зі списку v2 (id групи -> опис для другого рядка картки). */
    @Getter
    private Map<String, String> groupSubtitles = new LinkedHashMap<String, String>();

    /** 1.32: список модпаків не завантажився, показано збережену копію (немає інтернету чи CDN недоступний). */
    @Getter
    private volatile boolean offline;

    private static final String PACKAGE_CACHE = "packages_cache.json";

    /** Файл packages_cache.json. */
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
    public static class PackageCache {
        public String url;
        public String savedAt;
        public PackageList list;
    }

    /**
     * Create a new instance list.
     *
     * @param launcher the launcher
     */
    public InstanceList(@NonNull Launcher launcher) {
        this.launcher = launcher;
    }

    /**
     * Get the instance at a particular index.
     *
     * @param index the index
     * @return the instance
     */
    public synchronized Instance get(int index) {
        return instances.get(index);
    }

    /**
     * Get the number of instances.
     *
     * @return the number of instances
     */
    public synchronized int size() {
        return instances.size();
    }

    /**
     * Create a worker that loads the list of instances from disk and from
     * the remote list of packages.
     *
     * @return the worker
     */
    public Enumerator createEnumerator() {
        return new Enumerator();
    }

    /**
     * Get a list of selected instances.
     *
     * @return a list of instances
     */
    public synchronized List<Instance> getSelected() {
        List<Instance> selected = new ArrayList<Instance>();
        for (Instance instance : instances) {
            if (instance.isSelected()) {
                selected.add(instance);
            }
        }

        return selected;
    }

    /**
     * Sort the list of instances.
     */
    public synchronized void sort() {
        Collections.sort(instances);
    }

    public final class Enumerator implements Callable<InstanceList>, ProgressObservable {
        private ProgressObservable progress = new DefaultProgress(-1, null);

        private Enumerator() {
        }

        @Override
        public InstanceList call() throws Exception {
            // display local system info on debug
            SwingExtras.logDebugMessage(launcher,"system information",
                "\n   os.name: " + System.getProperty("os.name") +
                "\n   os.version: " + System.getProperty("os.version") +
                "\n   os.arch: " + System.getProperty("os.arch") +
                "\n   java.specification.version: " + System.getProperty("java.specification.version") +
                "\n   java.runtime.version: " + System.getProperty("java.runtime.version") +
                "\n   java.version.date: " + System.getProperty("java.version.date") +
                "\n   java.vm.vendor: " + System.getProperty("java.vm.vendor") +
                "\n   java.vm.name: " + System.getProperty("java.vm.name")
            );

            log.info("Enumerating instance list...");
            progress = new DefaultProgress(0, SharedLocale.tr("instanceLoader.loadingLocal"));

            List<Instance> local = new ArrayList<Instance>();
            List<Instance> remote = new ArrayList<Instance>();

            File[] dirs = launcher.getInstancesDir().listFiles((FileFilter) DirectoryFileFilter.INSTANCE);
            if (dirs != null) {
                for (File dir : dirs) {
                    File file = new File(dir, "instance.json");
                    Instance instance = Persistence.load(file, Instance.class);
                    instance.setDir(dir);
                    instance.setName(dir.getName());
                    instance.setSelected(true);
                    instance.setLocal(true);
                    if (instance.isVanilla()) {
                        // вкладка Vanilla: група і назва відомі з назви теки, навіть без інтернету
                        instance.setGroup(com.gummer.launcher.vanilla.VanillaVersions.GROUP);
                        instance.setTitle(com.gummer.launcher.vanilla.VanillaVersions.titleOf(
                                com.gummer.launcher.vanilla.VanillaVersions.versionIdOf(instance)));
                    }
                    local.add(instance);

                    log.info(instance.getName() + " local instance found at " + dir.getAbsolutePath());
                }
            }

            progress = new DefaultProgress(0.3, SharedLocale.tr("instanceLoader.checkingRemote"));
            InstanceList.this.offline = false;

            // 1.32: вкладка Vanilla, релізи з маніфесту Mojang. Окремо від списку модпаків: його помилка не ховає ванілу
            // і навпаки. Без інтернету: збережений список (vanilla_versions.json) або лише встановлені версії.
            Set<String> vanillaNames = new HashSet<String>();
            try {
                for (com.gummer.launcher.vanilla.VanillaVersions.Release r : com.gummer.launcher.vanilla.VanillaVersions.releases(launcher)) {
                    String vname = com.gummer.launcher.vanilla.VanillaVersions.nameOf(r.id);
                    vanillaNames.add(vname.toLowerCase(Locale.ROOT));
                    Instance found = null;
                    for (Instance instance : local) {
                        if (instance.getName().equalsIgnoreCase(vname)) found = instance;
                    }
                    URL vurl = new URL(r.url);
                    if (found != null) {
                        found.setManifestURL(vurl);
                    } else {
                        File dir = new File(launcher.getInstancesDir(), vname);
                        // через Persistence.load, як і паки з CDN: інакше Updater не може записати instance.json
                        // («Cannot persist unbound object») і установка ванільної версії падає одразу
                        Instance instance = Persistence.load(new File(dir, "instance.json"), Instance.class);
                        instance.setDir(dir);
                        instance.setName(vname);
                        instance.setTitle(com.gummer.launcher.vanilla.VanillaVersions.titleOf(r.id));
                        instance.setVersion(r.id);
                        instance.setGroup(com.gummer.launcher.vanilla.VanillaVersions.GROUP);
                        instance.setSelected(false);
                        instance.setManifestURL(vurl);
                        instance.setUpdatePending(true);
                        instance.setLocal(false);
                        remote.add(instance);
                    }
                }
                log.info(vanillaNames.size() + " vanilla release(s) listed");
            } catch (Exception e) {
                log.log(java.util.logging.Level.WARNING, "The vanilla version list is unavailable", e);
            }

            try {
                URL javaManifestUrl = launcher.getJavaManifestURL();
                HashMap<String, HashMap<String, JavaManifest[]>> javaManifests = HttpRequest
                    .get(javaManifestUrl)
                    .execute()
                    .expectResponseCode(200)
                    .returnContent()
                    .asJson(new TypeReference<HashMap<String, HashMap<String, JavaManifest[]>>>() {
                    });
                SwingExtras.logDebugMessage(launcher, "javaManifests", String.valueOf(javaManifests));

                String currentOs = Environment.getInstance().getMojangOs();
                //SwingExtras.logDebugMessage(launcher, "currentOs", String.valueOf(currentOs));

                if (!javaManifests.containsKey(currentOs)) {
                    throw new Exception("Unable to find a correct Java version for your os/arch: Not available in the manifest");
                }

                InstanceList.this.javaVersions = javaManifests.get(currentOs);
                SwingExtras.logDebugMessage(launcher, "javaManifests.get(currentOs)", String.valueOf(InstanceList.this.javaVersions));
                //#endregion
            } catch (Exception e) {
                // Без маніфесту Java список модпаків однаково показуємо: грати можна з уже встановленою Java,
                // а помилку про Java гравець побачить лише при встановленні модпаку (Updater). До 1.32 тут кидалась
                // помилка, і недоступний маніфест Java ховав увесь список модпаків.
                log.log(java.util.logging.Level.WARNING, "The Java versions could not be downloaded, continuing with the modpack list", e);
            }

            try {
                // 1.32+: спершу список v2 (ті самі паки + group, server, recommendedMemory). Не вдалося з будь-якої
                // причини: старий список v1, як у 1.31. Так лаунчер працює, навіть поки v2 не викладено на CDN.
                URL packagesURL = null;
                PackageList packages = null;
                URL packagesV2URL = launcher.getPackagesURLV2();
                if (packagesV2URL != null) {
                    try {
                        packages = fetchPackageList(packagesV2URL);
                        if (packages.getPackages() == null) {
                            throw new IOException("The modpack list v2 has no packages");
                        }
                        packagesURL = packagesV2URL;
                        log.info("Using the modpack list v2");
                    } catch (InterruptedException e) {
                        throw e;
                    } catch (Exception e) {
                        log.log(java.util.logging.Level.WARNING, "The modpack list v2 could not be used, falling back to v1", e);
                        packages = null;
                    }
                }
                if (packages == null) {
                    try {
                        packagesURL = launcher.getPackagesURL();
                        packages = fetchPackageList(packagesURL);
                    } catch (InterruptedException e) {
                        throw e;
                    } catch (Exception e) {
                        // 1.32: без інтернету - останній успішний список (packages_cache.json), щоб встановлені збірки
                        // лишились у своїх розділах і запускались. Кешу немає: помилка, як раніше
                        PackageCache cached = readPackageCache();
                        if (cached == null) throw e;
                        log.log(java.util.logging.Level.WARNING, "The modpack list is unavailable, using the saved copy from " + cached.savedAt, e);
                        packagesURL = new URL(cached.url);
                        packages = cached.list;
                        InstanceList.this.offline = true;
                    }
                }
                if (!InstanceList.this.offline) {
                    writePackageCache(packagesURL, packages);
                }

                Map<String, String> titles = new LinkedHashMap<String, String>();
                titles.put(com.gummer.launcher.vanilla.VanillaVersions.GROUP, "Vanilla");
                if (packages.getGroups() != null) {
                    for (Map.Entry<String, PackageList.PackageGroup> g : packages.getGroups().entrySet()) {
                        if (g.getValue() != null && g.getValue().getTitle() != null) {
                            titles.put(g.getKey(), g.getValue().getTitle());
                        }
                    }
                }
                InstanceList.this.groupTitles = titles;
                Map<String, String> subtitles = new LinkedHashMap<String, String>();
                if (packages.getGroups() != null) {
                    for (Map.Entry<String, PackageList.PackageGroup> g : packages.getGroups().entrySet()) {
                        if (g.getValue() != null && g.getValue().getSubtitle() != null) {
                            subtitles.put(g.getKey(), g.getValue().getSubtitle());
                        }
                    }
                }
                InstanceList.this.groupSubtitles = subtitles;

                if (packages.getMinimumVersion() > Launcher.PROTOCOL_VERSION) {
                    throw new LauncherException("Update required", SharedLocale.tr("errors.updateRequiredError"));
                }

                Set<String> listedNames = new HashSet<String>();
                for (ManifestInfo manifest : packages.getPackages()) {
                    if (manifest.getName() != null) {
                        listedNames.add(manifest.getName().toLowerCase(Locale.ROOT));
                    }
                }
                // Пак на диску, якого вже немає в списку CDN (наприклад main-1.19.3): позначка «більше не підтримується»
                // і місце в кінці списку. Лише коли список завантажився успішно: без інтернету нічого не позначаємо.
                for (Instance instance : local) {
                    if (instance.isVanilla()) continue; // ванільні версії не з нашого списку, вони не «покинуті»
                    instance.setOrphaned(!listedNames.contains(instance.getName().toLowerCase(Locale.ROOT)));
                    if (instance.isOrphaned()) {
                        log.info(instance.getName() + " is not in the modpack list anymore (orphaned)");
                    }
                }

                for (ManifestInfo manifest : packages.getPackages()) {
                    boolean foundLocal = false;

                    for (Instance instance : local) {
                        if (instance.getName().equalsIgnoreCase(manifest.getName())) {
                            foundLocal = true;

                            instance.setTitle(manifest.getTitle());
                            instance.setPriority(manifest.getPriority());
                            instance.setJavaRuntime(manifest.getJavaRuntime());
                            applyListFields(instance, manifest);
                            URL url = concat(packagesURL, manifest.getLocation());
                            instance.setManifestURL(url);

                            log.info("(" + instance.getName() + ").setManifestURL(" + url + ")");

                            // Check if an update is required
                            if (instance.getVersion() == null || !instance.getVersion().equals(manifest.getVersion())) {
                                instance.setUpdatePending(true);
                                instance.setVersion(manifest.getVersion());
                                Persistence.commitAndForget(instance);
                                log.info(instance.getName() + " requires an update to " + manifest.getVersion());
                            }
                        }
                    }

                    SwingExtras.logDebugMessage(launcher, "manifest.getJavaRuntime()", String.valueOf(manifest.getJavaRuntime()));

                    if (!foundLocal) {
                        File dir = new File(launcher.getInstancesDir(), manifest.getName());
                        File file = new File(dir, "instance.json");
                        Instance instance = Persistence.load(file, Instance.class);
                        instance.setDir(dir);
                        instance.setTitle(manifest.getTitle());
                        instance.setName(manifest.getName());
                        instance.setVersion(manifest.getVersion());
                        instance.setPriority(manifest.getPriority());
                        instance.setJavaRuntime(manifest.getJavaRuntime());
                        applyListFields(instance, manifest);
                        instance.setSelected(false);
                        instance.setManifestURL(concat(packagesURL, manifest.getLocation()));
                        instance.setUpdatePending(true);
                        instance.setLocal(false);
                        remote.add(instance);

                        log.info("Available remote instance: '" + instance.getName() +
                                "' at version " + instance.getVersion());
                    }
                }
            } catch (IOException e) {
                throw new IOException("The list of modpacks could not be downloaded.", e);
            } finally {
                synchronized (InstanceList.this) {
                    instances.clear();
                    instances.addAll(local);
                    instances.addAll(remote);

                    log.info(instances.size() + " instance(s) enumerated.");
                }
            }

            return InstanceList.this;
        }

        /** Копія останнього успішного списку модпаків для гри без інтернету. Адреса без параметрів (ключ бети, ID) не зберігається. */
        private void writePackageCache(URL url, PackageList list) {
            try {
                PackageCache cache = new PackageCache();
                cache.url = new URL(url.getProtocol(), url.getHost(), url.getPort(), url.getPath()).toExternalForm();
                cache.savedAt = new Date().toString();
                cache.list = list;
                com.gummer.launcher.util.Json.newMapper().writeValue(new File(launcher.getBaseDir(), PACKAGE_CACHE), cache);
            } catch (Exception e) {
                log.log(java.util.logging.Level.INFO, "Could not save the modpack list copy", e);
            }
        }

        private PackageCache readPackageCache() {
            File file = new File(launcher.getBaseDir(), PACKAGE_CACHE);
            if (!file.isFile()) return null;
            try {
                PackageCache cache = com.gummer.launcher.util.Json.newMapper().readValue(file, PackageCache.class);
                return cache != null && cache.url != null && cache.list != null && cache.list.getPackages() != null ? cache : null;
            } catch (Exception e) {
                log.log(java.util.logging.Level.WARNING, "The saved modpack list copy is unreadable", e);
                return null;
            }
        }

        private PackageList fetchPackageList(URL url) throws IOException, InterruptedException {
            return HttpRequest
                    .get(url)
                    .execute()
                    .expectResponseCode(200)
                    .returnContent()
                    .asJson(PackageList.class);
        }

        /** Поля списку v2 (у v1 їх немає: null, false, null). */
        private void applyListFields(Instance instance, ManifestInfo manifest) {
            instance.setGroup(manifest.getGroup());
            instance.setServer(manifest.isServer());
            instance.setRecommendedMemory(manifest.getRecommendedMemory());
            instance.setServerAddress(manifest.getServerAddress());
        }

        @Override
        public double getProgress() {
            return -1;
        }

        @Override
        public String getStatus() {
            return progress.getStatus();
        }
    }
}
