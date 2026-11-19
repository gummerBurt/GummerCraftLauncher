/*
 * SK's Minecraft Launcher
 * Copyright (C) 2010-2014 Albert Pham <http://www.sk89q.com> and contributors
 * Copyright (C) 2021-2025 GummerCraft <http://gummercraft.fun>
 * Please see LICENSE.txt for license information.
 */

package com.skcraft.launcher.update;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.skcraft.concurrency.DefaultProgress;
import com.skcraft.concurrency.ProgressFilter;
import com.skcraft.concurrency.ProgressObservable;
import com.skcraft.launcher.Instance;
import com.skcraft.launcher.Launcher;
import com.skcraft.launcher.LauncherException;
import com.skcraft.launcher.install.Installer;
import com.gummer.launcher.model.java.JavaFile;
import com.gummer.launcher.model.java.JavaManifest;
import com.gummer.launcher.model.java.JavaVersionManifest;
import com.skcraft.launcher.model.minecraft.ReleaseList;
import com.skcraft.launcher.model.minecraft.Version;
import com.skcraft.launcher.model.minecraft.VersionManifest;
import com.skcraft.launcher.model.modpack.Manifest;
import com.skcraft.launcher.persistence.Persistence;
import com.gummer.launcher.util.BundledJava;
import com.skcraft.launcher.util.HttpRequest;
import com.skcraft.launcher.util.SharedLocale;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import lombok.extern.java.Log;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import java.util.stream.Collectors;

import static com.skcraft.launcher.util.HttpRequest.url;

@Log
public class Updater extends BaseUpdater implements Callable<Instance>, ProgressObservable {

    private final ObjectMapper mapper = com.gummer.launcher.util.Json.newMapper();
    private final Installer installer;
    private final Launcher launcher;
    private final Instance instance;

    @Getter @Setter
    private boolean online;

    private List<URL> librarySources = new ArrayList<URL>();
    private List<URL> assetsSources = new ArrayList<URL>();

    private ProgressObservable progress = new DefaultProgress(-1, SharedLocale.tr("instanceUpdater.preparingUpdate"));

    public Updater(@NonNull Launcher launcher, @NonNull Instance instance) {
        super(launcher);

        this.installer = new Installer(launcher.getInstallerDir());
        this.launcher = launcher;
        this.instance = instance;

        librarySources.add(launcher.propUrl("librariesSource"));
        if (instance.isQuilt() || instance.isMrpack()) {
            try {
                librarySources.add(new java.net.URL(com.gummer.launcher.quilt.QuiltVersions.MAVEN));
            } catch (java.net.MalformedURLException e) {
                throw new IllegalStateException(e);
            }
        }
        if (instance.isMrpack()) {
            try {
                for (com.gummer.launcher.forge.ForgeVersions.Kind k : com.gummer.launcher.forge.ForgeVersions.Kind.values()) librarySources.add(new java.net.URL(k.maven));
            } catch (java.net.MalformedURLException e) {
                throw new IllegalStateException(e);
            }
        }
        if (instance.isFabric() || instance.isQuilt() || instance.isMrpack()) {
            try {
                librarySources.add(new java.net.URL(com.gummer.launcher.fabric.FabricVersions.MAVEN));
            } catch (java.net.MalformedURLException e) {
                throw new IllegalStateException(e);
            }
        }
        preferOwnLibraryUrl = instance.isBuiltIn();
        if (instance.isForgeLike()) {
            try {
                librarySources.add(new java.net.URL(com.gummer.launcher.forge.ForgeVersions.kindOf(instance).maven));
            } catch (java.net.MalformedURLException e) {
                throw new IllegalStateException(e);
            }
        }
        assetsSources.add(launcher.propUrl("assetsSource"));
    }

    @Override
    public Instance call() throws Exception {
        log.info("Checking for an update for '" + instance.getName() + "'...");

        // Force the directory to be created
        instance.getContentDir();

        boolean updateRequired = !instance.isInstalled();
        boolean updateDesired = (instance.isUpdatePending() || updateRequired);
        boolean updateCapable = (instance.getManifestURL() != null);

        // disabled: local (offline) accounts also count as "offline" here and must still be able to update
        // if (!online && updateRequired) {
        //     log.info("Can't update " + instance.getTitle() + " because offline");
        //     String message = SharedLocale.tr("updater.updateRequiredButOffline");
        //     throw new LauncherException("Update required but currently offline", message);
        // }

        if (updateDesired && !updateCapable) {
            if (updateRequired) {
                log.info("Update required for " + instance.getTitle() + " but there is no manifest");
                String message = SharedLocale.tr("updater.updateRequiredButNoManifest");
                throw new LauncherException("Update required but no manifest", message);
            } else {
                log.info("Can't update " + instance.getTitle() + ", but update is not required");
                return instance; // Can't update
            }
        }

        if (updateDesired) {
            log.info("Updating " + instance.getTitle() + "...");
            // 16.11.2026: анонімна подія про завантаження (звідки качалось, скільки, де висло), див. DownloadStats
            com.gummer.launcher.stats.DownloadStats.begin();
            boolean ok = false;
            try {
                update(instance);
                ok = true;
            } finally {
                com.gummer.launcher.stats.DownloadStats.finish(launcher, instance, ok);
            }
        } else {
            log.info("No update found for " + instance.getTitle());
        }

        return instance;
    }

    /**
     * Check whether the package manifest contains an embedded version manifest,
     * otherwise we'll have to download the one for the given Minecraft version.
     *
     * BACKWARDS COMPATIBILITY:
     * Old manifests have an embedded version manifest without the minecraft JARs list present.
     * If we find a manifest without that jar list, fetch the newer copy from launchermeta and use the list from that.
     * We can't just replace the manifest outright because library versions might differ and that screws up Runner.
     */
    private VersionManifest readVersionManifest(Manifest manifest) throws IOException, InterruptedException {
        VersionManifest version = manifest.getVersionManifest();
        URL url = url(launcher.getProperties().getProperty("versionManifestUrl"));

        if (version == null) {
            version = fetchVersionManifest(url, manifest);
        }

        if (version.getDownloads().isEmpty()) {
            // Backwards compatibility hack
            VersionManifest otherManifest = fetchVersionManifest(url, manifest);

            version.setDownloads(otherManifest.getDownloads());
            version.setAssetIndex(otherManifest.getAssetIndex());
        }

        mapper.writeValue(instance.getVersionPath(), version);
        return version;
    }

    private JavaFile[] readJavaManifest(JavaManifest manifest) throws IOException, InterruptedException {
        ArrayList<JavaFile> javaFiles = new ArrayList<>();

        URL url = url(manifest.getManifest().getUrl());

        JavaVersionManifest results = HttpRequest.get(url)
            .execute()
            .expectResponseCode(200)
            .returnContent()
            .asJson(JavaVersionManifest.class);


        for(Map.Entry<String, JavaFile> entry : results.files.entrySet()) {
            JavaFile jf = entry.getValue();
            jf.setPath(entry.getKey());
            javaFiles.add(jf);
        }


        return javaFiles.toArray(new JavaFile[0]);
    }

    private static VersionManifest fetchVersionManifest(URL url, Manifest manifest) throws IOException, InterruptedException {
        ReleaseList releases = HttpRequest.get(url)
            .execute()
            .expectResponseCode(200)
            .returnContent()
            .asJson(ReleaseList.class);

        Version relVersion = releases.find(manifest.getGameVersion());
        return HttpRequest.get(url(relVersion.getUrl()))
            .execute()
            .expectResponseCode(200)
            .returnContent()
            .asJson(VersionManifest.class);
    }

    private static void cleanJvmFiles(JavaFile[] files, File jvmPath) throws IOException {
        Path basePath = Paths.get(jvmPath.getAbsolutePath());

        List<Path> shouldExist = (new ArrayList<>(Arrays.asList(files))
            .stream()
            .map(jf -> basePath.resolve(jf.getPath()))
            .collect(Collectors.toList())
        );

        Files.walkFileTree(
            basePath,
            new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    if (!shouldExist.contains(file)) {
                        System.out.println("Removing unwanted JVM file: " + file.toAbsolutePath().toString());
                        Files.delete(file);
                    }

                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    if (!shouldExist.contains(dir) && !Files.list(dir).findAny().isPresent()) {
                        System.out.println("Removing unwanted JVM folder: " + dir.toAbsolutePath().toString());
                        Files.delete(dir);
                    }

                    return FileVisitResult.CONTINUE;
                }
            }
        );
    }

    /**
     * Update the given instance.
     *
     * @param instance the instance
     * @throws IOException thrown on I/O error
     * @throws InterruptedException thrown on interruption
     * @throws ExecutionException thrown on execution error
     */
    protected void update(Instance instance) throws Exception {
        // Mark this instance as local
        instance.setLocal(true);
        Persistence.commitAndForget(instance);

        // Read manifest
        log.info("Reading package manifest...");
        progress = new DefaultProgress(-1, SharedLocale.tr("instanceUpdater.readingManifest"));
        Manifest manifest = installPackage(installer, instance);

        // Update instance from manifest
        manifest.update(instance);

        // Read version manifest
        log.info("Reading version manifest...");
        progress = new DefaultProgress(-1, SharedLocale.tr("instanceUpdater.readingVersion"));
        VersionManifest version = readVersionManifest(manifest);

        progress = new DefaultProgress(-1, SharedLocale.tr("instanceUpdater.buildingDownloadList"));

        // Read Java version manifest
        log.info("Reading runtime manifest...");
        progress = new DefaultProgress(-1, SharedLocale.tr("instanceUpdater.readingJavaVersion"));


        JavaManifest javaManifest = null;
        File runtimeMarkerDir = null; // 1.36.1: куди після встановлення записати JavaSecurity.MARKER
        // 1.32.2: на Mac з Apple Silicon старі версії гри (LWJGL без arm64) беруть Java для Intel, див. MacRosetta
        String runtimeOs = com.gummer.launcher.util.MacRosetta.runtimeOs(version);
        if (com.gummer.launcher.util.MacRosetta.needsIntelJava(version)) {
            log.info("Apple Silicon: this Minecraft has no arm64 natives (old LWJGL), using the Intel Java (" + runtimeOs + ") under Rosetta 2");
        }
        Map<String, JavaManifest[]> javaVersions = launcher.getInstances().getJavaVersionsFor(runtimeOs);

        for (Map.Entry<String, JavaManifest[]> entry : javaVersions.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(instance.getJavaRuntime())) {
                if (entry.getValue() != null && entry.getValue().length > 0) {
                    javaManifest = entry.getValue()[0];
                }
                break;
            }
        }

        File jvmDir = BundledJava.getJavaDir(launcher, instance.getJavaRuntime(), runtimeOs);

        if (javaManifest == null) {
            // Немає запису про потрібну Java: або маніфест Java не завантажився (javaVersions порожній), або для цієї
            // платформи такої версії немає. До 1.32 тут був NullPointerException. Якщо ця Java вже встановлена,
            // оновлюємо модпак на ній; якщо ні, зрозуміла помилка замість стектрейсу.
            if (BundledJava.hasJavaBinary(jvmDir)) {
                log.warning("No Java manifest entry for runtime \"" + instance.getJavaRuntime()
                        + "\", keeping the installed one at " + jvmDir.getAbsolutePath());
            } else if (javaVersions.isEmpty()) {
                throw new LauncherException("Java manifest unavailable for runtime \"" + instance.getJavaRuntime() + "\"",
                        SharedLocale.tr("errors.javaManifestUnavailable"));
            } else {
                throw new LauncherException("No Java runtime \"" + instance.getJavaRuntime() + "\" for this platform",
                        SharedLocale.tr("errors.javaRuntimeUnavailable", instance.getJavaRuntime()));
            }
        } else if (BundledJava.hasJavaBinary(jvmDir)
                && com.gummer.launcher.util.JavaUpdates.keepInstalled(launcher, instance.getJavaRuntime(), runtimeOs)) {
            // 1.36.3: автооновлення Java вимкнено або гравець відкотив цю Java: наявну не чіпаємо
            log.info("Java auto-update off or runtime pinned, keeping " + jvmDir.getAbsolutePath());
        } else {
            JavaFile[] files = readJavaManifest(javaManifest);

            if (!jvmDir.exists()) {
                jvmDir.mkdirs();
            }

            log.info("Cleanup unwanted runtime files...");
            progress = new DefaultProgress(-1, SharedLocale.tr("instanceUpdater.cleaningJavaFiles"));
            cleanJvmFiles(files, jvmDir);

            // Validating / downloading the JVM
            log.info("Installing correct runtime");
            installJvm(installer, jvmDir, files);
            runtimeMarkerDir = jvmDir;
        }

        // Install the .jar
        File jarPath = launcher.getJarPath(version);
        VersionManifest.Artifact clientJar = version.getDownloads().get("client");
        URL jarSource = url(clientJar.getUrl());
        log.info("JAR at " + jarPath.getAbsolutePath() + ", fetched from " + jarSource);
        installJar(installer, clientJar, jarPath, jarSource);

        // Download libraries
        log.info("Enumerating libraries to download...");

        URL url = manifest.getLibrariesUrl();
        if (url != null) {
            log.info("Added library source: " + url);
            librarySources.add(0, url);
        }

        // 16.11.2026: перша установка цієї версії гри: ассети і бібліотеки Minecraft одним архівом з нашого CDN
        // (докачка після обриву, звірка кожного файлу); не вдалось: далі звичайне пофайлове завантаження
        com.gummer.launcher.install.BaseArchive.prefetch(launcher, version, p -> progress = p);

        progress = new DefaultProgress(-1, SharedLocale.tr("instanceUpdater.collectingLibraries"));
        installLibraries(installer, manifest, launcher.getLibrariesDir(), librarySources);

        // Download assets
        log.info("Enumerating assets to download...");
        progress = new DefaultProgress(-1, SharedLocale.tr("instanceUpdater.collectingAssets"));
        installAssets(installer, version, url(version.getAssetIndex().getUrl()), assetsSources);

        log.info("Executing download phase...");
        progress = ProgressFilter.between(installer.getDownloader(), 0, 0.98);
        installer.download();

        log.info("Executing install phase...");
        progress = ProgressFilter.between(installer, 0.98, 1);
        installer.execute(launcher);

        installer.executeLate(launcher);
        if (runtimeMarkerDir != null) {
            com.gummer.launcher.util.JavaSecurity.writeMarker(runtimeMarkerDir, javaManifest);
            com.gummer.launcher.util.JavaUpdates.writeVersion(runtimeMarkerDir, javaManifest);
        }

        log.info("Completing...");
        complete();

        // Update the instance's information
        log.info("Writing instance information...");
        instance.setVersion(manifest.getVersion());
        instance.setUpdatePending(false);
        instance.setInstalled(true);
        instance.setLocal(true);
        Persistence.commitAndForget(instance);

        log.log(Level.INFO, instance.getName() +
            " has been updated to version " + manifest.getVersion() + ".");
    }

    @Override
    public double getProgress() {
        return progress.getProgress();
    }

    @Override
    public String getStatus() {
        return progress.getStatus();
    }
}
