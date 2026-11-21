/*
 * SK's Minecraft Launcher
 * Copyright (C) 2010-2014 Albert Pham <http://www.sk89q.com> and contributors
 * Copyright (C) 2021-2025 GummerCraft <http://gummercraft.fun>
 * Please see LICENSE.txt for license information.
 */

package com.skcraft.launcher.launch;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.Files;
import com.gummer.launcher.swing.SwingExtras;
import com.skcraft.concurrency.DefaultProgress;
import com.skcraft.concurrency.ProgressObservable;
import com.skcraft.launcher.*;
import com.skcraft.launcher.auth.Session;
import com.skcraft.launcher.install.ZipExtract;
import com.skcraft.launcher.launch.runtime.JavaRuntime;
import com.skcraft.launcher.launch.runtime.JavaRuntimeFinder;
import com.skcraft.launcher.model.minecraft.*;
import com.skcraft.launcher.persistence.Persistence;
import com.skcraft.launcher.util.Environment;
import com.skcraft.launcher.util.Platform;
import com.skcraft.launcher.util.SharedLocale;
import com.skcraft.launcher.util.FileUtils;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import lombok.extern.java.Log;
import org.apache.commons.lang.text.StrSubstitutor;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.function.BiPredicate;

import static com.skcraft.launcher.LauncherUtils.checkInterrupted;
import static com.skcraft.launcher.util.SharedLocale.tr;

/**
 * Handles the launching of an instance.
 */
@Log
public class Runner implements Callable<Process>, ProgressObservable {

    private ProgressObservable progress = new DefaultProgress(0, SharedLocale.tr("runner.preparing"));

    private final ObjectMapper mapper = com.gummer.launcher.util.Json.newMapper();
    private final Launcher launcher;
    private final Instance instance;
    private final Session session;
    private final File extractDir;
    private final BiPredicate<JavaRuntime, JavaVersion> javaRuntimeMismatch;
    @Getter @Setter private Environment environment = Environment.getInstance();

    private VersionManifest versionManifest;
    private AssetsIndex assetsIndex;
    private File virtualAssetsDir;
    private Configuration config;
    private JavaProcessBuilder builder;
    private AssetsRoot assetsRoot;
    private FeatureList.Mutable featureList;

    /**
     * Create a new instance launcher.
     *  @param launcher the launcher
     * @param instance the instance
     * @param session the session
     * @param extractDir the directory to extract to
     * @param javaRuntimeMismatch
     */
    public Runner(@NonNull Launcher launcher, @NonNull Instance instance,
                  @NonNull Session session, @NonNull File extractDir,
                  BiPredicate<JavaRuntime, JavaVersion> javaRuntimeMismatch) {
        this.launcher = launcher;
        this.instance = instance;
        this.session = session;
        this.extractDir = extractDir;
        this.javaRuntimeMismatch = javaRuntimeMismatch;
        this.featureList = new FeatureList.Mutable();
    }

    /**
     * Get the path to the JAR.
     *
     * @return the JAR path
     */
    private File getJarPath() {
        File jarPath = instance.getCustomJarPath();
        if (!jarPath.exists()) {
            jarPath = launcher.getJarPath(versionManifest);
        }
        return jarPath;
    }

    @Override
    public Process call() throws Exception {
        if (!instance.isInstalled()) {
            throw new LauncherException("Update required", SharedLocale.tr("runner.updateRequired"));
        }

        // 1.32.1: Vanilla одразу українською і з сервером GummerCraft у списку (лише перший раз, див. VanillaDefaults)
        com.gummer.launcher.vanilla.VanillaDefaults.apply(instance);
        // 1.36.2: мова і розмір інтерфейсу до гри, щоб після закриття побачити, що змінив сам гравець (GamePrefs)
        com.gummer.launcher.vanilla.GamePrefs.snapshot(instance);
        // 17.11.2026: моди модпаку, які гравець вимкнув у вікні «Збірка», оновлення паку качає знову: вимкнути ще раз
        com.gummer.launcher.manage.InstanceMods.reapplyDisabled(instance);
        // 17.11.2026: мод вимагає новішу версію іншого мода, ніж стоїть (The Pixelmon Modpack 9.1.13 кладе byg 1.1.6, а
        // Pixelmon вимагає 1.3.4+): поставити потрібну з Modrinth до запуску, інакше гра стає на «only supports ... or above»
        long fixStart = System.nanoTime();
        java.util.List<String> fixedMods = com.gummer.launcher.manage.ModVersionFix.fixBeforeLaunch(instance);
        if (!fixedMods.isEmpty() || System.nanoTime() - fixStart > 300000000L) {
            log.info("Mod version check: " + fixedMods + " in " + (System.nanoTime() - fixStart) / 1000000 + " ms");
        }

        // 17.11.2026 ~23:30: дублікати модів, мод не під завантажувач, бракує обов'язкового мода: спитати до запуску
        com.gummer.launcher.manage.PreLaunchDialog.ask(instance);

        config = launcher.getConfig();
        builder = new JavaProcessBuilder();
        assetsRoot = launcher.getAssets();

        // Load manifiests
        versionManifest = mapper.readValue(instance.getVersionPath(), VersionManifest.class);
        SwingExtras.logDebugMessage(launcher, "versionManifest", String.valueOf(versionManifest));

        // Load assets index
        File assetsFile = assetsRoot.getIndexPath(versionManifest);
        try {
            assetsIndex = mapper.readValue(assetsFile, AssetsIndex.class);
        } catch (FileNotFoundException e) {
            instance.setInstalled(false);
            Persistence.commitAndForget(instance);
            throw new LauncherException("Missing assets index " + assetsFile.getAbsolutePath(),
                    tr("runner.missingAssetsIndex", instance.getTitle(), assetsFile.getAbsolutePath()));
        } catch (IOException e) {
            instance.setInstalled(false);
            Persistence.commitAndForget(instance);
            throw new LauncherException("Corrupt assets index " + assetsFile.getAbsolutePath(),
                    tr("runner.corruptAssetsIndex", instance.getTitle(), assetsFile.getAbsolutePath()));
        }

        // Copy over assets to the tree
        try {
            AssetsRoot.AssetsTreeBuilder assetsBuilder = assetsRoot.createAssetsBuilder(versionManifest);
            progress = assetsBuilder;
            virtualAssetsDir = assetsBuilder.build();
        } catch (LauncherException e) {
            instance.setInstalled(false);
            Persistence.commitAndForget(instance);
            throw e;
        }

        progress = new DefaultProgress(0.9, SharedLocale.tr("runner.collectingArgs"));
        builder.setMainClass(versionManifest.getMainClass());

        addWindowArgs();
        addQuickJoin();
        addLibraries();
        addJvmArgs();
        addJarArgs();
        addProxyArgs();
        addServerArgs();
        addPlatformArgs();
        addLegacyArgs();

        callLaunchModifier();

        verifyJavaRuntime();

        java.util.List<String> command = builder.buildCommand();
        ProcessBuilder processBuilder = new ProcessBuilder(command);
        // 19.11.2026: картка «Відеокарта» у вікні «Збірка»: запам'ятати Java збірки для кнопки «Скопіювати шлях до
        // Java» і, лише якщо гравець поставив галочку «на дискретній відеокарті», попросити систему дати її грі
        // (Windows: те саме значення, що пише сторінка «Параметри графіки»; Linux: змінні PRIME лише для цього
        // процесу). Що саме і чому: опис класу Gpu
        com.gummer.launcher.util.Gpu.beforeLaunch(launcher.getBaseDir(), instance, command.isEmpty() ? null : command.get(0),
                processBuilder.environment());
        processBuilder.directory(instance.getContentDir());
        // 1.32: токен входу не в лог (лог йде у «Завантажити лог» і у звернення в підтримку)
        String launching = builder.toString();
        String token = session.getAccessToken();
        if (token != null && token.length() > 3) launching = launching.replace(token, "<hidden>");
        Runner.log.info("Launching: " + launching);
        checkInterrupted();

        progress = new DefaultProgress(1, SharedLocale.tr("runner.startingJava"));

        try {
            return processBuilder.start();
        } catch (IOException e) {
            // 1.32.2: Java для Intel на Mac з Apple Silicon без Rosetta 2: macOS не запускає її («Bad CPU type», error=86)
            String m = String.valueOf(e.getMessage());
            if (m.contains("Bad CPU type") || m.contains("error=86")) {
                throw new LauncherException("Rosetta 2 is not installed: " + m, SharedLocale.tr("runner.rosettaRequired"));
            }
            throw e;
        }
    }

    /**
     * Call the manifest launch modifier.
     */
    private void callLaunchModifier() {
        instance.modify(builder);
    }

    private void verifyJavaRuntime() {
        JavaRuntime pickedRuntime = builder.getRuntime();
        JavaVersion targetVersion = versionManifest.getJavaVersion();

        if (pickedRuntime == null || targetVersion == null) {
            return;
        }

        if (pickedRuntime.getMajorVersion() != targetVersion.getMajorVersion()) {
            //boolean launchAnyway = javaRuntimeMismatch.test(pickedRuntime, targetVersion);
            boolean launchAnyway = true; // lets ignore version mismatch

            if (!launchAnyway) {
                throw new CancellationException("Launch cancelled by user.");
            }
        }
    }

    /**
     * Add platform-specific arguments.
     */
    private void addPlatformArgs() {
        // Mac OS X arguments
        if (getEnvironment().getPlatform() == Platform.MAC_OS_X) {
            File icnsPath = assetsIndex.getObjectPath(assetsRoot, "icons/minecraft.icns");
            if (icnsPath != null) {
                builder.getFlags().add("-Xdock:icon=" + icnsPath.getAbsolutePath());
                builder.getFlags().add("-Xdock:name=Minecraft");
            }

            // 1.32.3 (1.32.4: GLFW 3.3.10), Apple Silicon + Java для Intel + LWJGL 3.2.x: інший GLFW замість старого (див. MacRosetta.needsNewGlfw)
            // і без раннього вікна Forge, яке лишало помилку GLFW непрочитаною («GLFW error before init»).
            if (com.gummer.launcher.util.MacRosetta.needsNewGlfw(versionManifest)) {
                File glfw = com.gummer.launcher.util.MacRosetta.ensureNewGlfw(launcher);
                if (glfw != null) {
                    builder.getFlags().add("-Dorg.lwjgl.glfw.libname=" + glfw.getAbsolutePath());
                    log.info("Apple Silicon: using the newer GLFW " + glfw.getAbsolutePath());
                }
                builder.getFlags().add("-Dfml.earlyprogresswindow=false");
            }
        }

        // 1.32, Linux: без нативного транспорту Netty (epoll). З ним клієнт на Linux вилітав з DecoderException /
        // «larger than I expected»; опція гри useNativeTransport:false допомагала, але модпак перезаписує options.txt.
        // Прапорець JVM вимикає epoll незалежно від options.txt; звичайний NIO працює так само.
        if (getEnvironment().getPlatform() == Platform.LINUX) {
            builder.getFlags().add("-Dio.netty.transport.noNative=true");
        }

        // 1.33: наші моди (GummerVibe, gcreport) бачать, що лаунчер слухає запити в теці гри (gummercraft-report.json, див.
        // DisconnectReport). Без прапорця (гра запущена іншим лаунчером чи старим нашим) мод одразу веде в Telegram-бот підтримки.
        builder.getFlags().add("-Dgummercraft.report=1");
        // 1.36.8: лаунчер читає з теки гри gummercraft-discord.json (рядок статусу Discord від мода, напр. «Досліджує
        // Прип'ять», див. DiscordPresence.STATUS_FILE). Без прапорця мод файл не пише.
        builder.getFlags().add("-Dgummercraft.discord=1");
    }

    /**
     * Add libraries.
     */
    @SuppressWarnings("UnstableApiUsage")
    private void addLibraries() throws LauncherException {
        // Add libraries to classpath or extract the libraries as necessary
        for (Library library : versionManifest.getLibraries()) {
            if (!library.matches(environment)) {
                continue;
            }

            File path = new File(launcher.getLibrariesDir(), library.getPath(environment));

            if (path.exists()) {
                Library.Extract extract = library.getExtract();
                if (extract != null) {
                    ZipExtract zipExtract = new ZipExtract(Files.asByteSource(path), extractDir);
                    zipExtract.setExclude(extract.getExclude());
                    zipExtract.run();
                } else {
                    builder.classPath(path);
                }
            } else {
                instance.setInstalled(false);
                Persistence.commitAndForget(instance);
                throw new LauncherException("Missing library " + library.getName(),
                        tr("runner.missingLibrary", instance.getTitle(), library.getName()));
            }
        }

        // The official launcher puts the vanilla jar at the end of the classpath, we'll do the same
        builder.classPath(getJarPath());
    }

    /**
     * Add JVM arguments.
     *
     * @throws IOException on I/O error
     */
    private void addJvmArgs() throws IOException, LauncherException {
        Optional<MemorySettings> memorySettings = Optional.ofNullable(instance.getSettings().getMemorySettings());

        int minMemory = memorySettings
                .map(MemorySettings::getMinMemory)
                .orElse(config.getMinMemory());

        int maxMemory = memorySettings
                .map(MemorySettings::getMaxMemory)
                .orElse(config.getMaxMemory());

        // 1.32: у паку немає своїх налаштувань пам'яті, а список v2 рекомендує більше, ніж загальне налаштування:
        // піднімаємо до рекомендованого, але лишаємо системі щонайменше 2 ГБ. Менше за загальне налаштування не ставимо.
        Integer recommended = instance.getRecommendedMemory();
        if (!memorySettings.isPresent() && recommended != null && recommended > maxMemory) {
            long totalMb = com.skcraft.launcher.util.Environment.getTotalSystemMemory() / (1024L * 1024L);
            int cap = totalMb > 0 ? (int) Math.max(0, totalMb - 2048) : recommended;
            int raised = Math.min(recommended, cap);
            if (raised > maxMemory) {
                log.info("Max memory raised from " + maxMemory + " to " + raised + " MB (recommended by the modpack list)");
                maxMemory = raised;
            }
        }

        // 17.11.2026 (власник: «пам'ять під модпак ... радити об'єм за кількістю модів»): збірка без своїх налаштувань
        // пам'яті з багатьма модами отримує стільки, скільки радить MemoryAdvice (не більше половини пам'яті ПК), якщо
        // загальне налаштування менше. Менше за загальне налаштування не ставимо.
        // 18.11.2026 (власник: «чому для модпаків з модрінта дається всього 2 гб ... вже другий модпак мені впав»): раніше
        // лише розділи завантажувачів; тепер і збірки Modrinth та копії (у них найбільше модів)
        if (!memorySettings.isPresent() && (instance.isBuiltIn() || instance.isMrpack() || instance.isCopy()) && !instance.isVanilla()) {
            int advice = com.gummer.launcher.manage.MemoryAdvice.recommended(instance);
            if (advice > maxMemory) {
                log.info("Max memory raised from " + maxMemory + " to " + advice + " MB ("
                        + com.gummer.launcher.manage.MemoryAdvice.modCount(instance) + " mods)");
                maxMemory = advice;
            }
        }

        int permGen = config.getPermGen();

        if (minMemory <= 0) {
            minMemory = 1024;
        }

        if (maxMemory <= 0) {
            //maxMemory = 1024;
            maxMemory = 2048;
        }

        if (permGen <= 0) {
            permGen = 128;
        }

        if (permGen <= 64) {
            permGen = 64;
        }

        if (minMemory > maxMemory) {
            maxMemory = minMemory;
        }

        builder.setMinMemory(minMemory);
        builder.setMaxMemory(maxMemory);
        builder.setPermGen(permGen);

        File RuntimePackJvmPath = setupZipRuntime(); // check and process legacy ZIP runtime

        // process JVM runtime setup
        try {
            // 20.11.2026 (тікет #88, власник: «може в таких випадках краще щоб лаунчер автоматично примусово знімав
            // галочку? Але щоб не було фолс позитив»): Java, вибрана галочкою «використовувати вказані параметри»,
            // береться, лише якщо вона є на диску і не старіша, ніж просить гра. Інакше на цей запуск наша Java (галочка
            // і вибір гравця лишаються: для інших збірок його Java може підходити). Тільки ці два випадки, бо з ними гра
            // однаково не запуститься; «занадто нова» Java не відкидається (часто працює, був би хибний відкид).
            JavaRuntime enforced = null;
            String enforcedReject = null;
            if (config.isEnforceRuntime()) {
                enforced = Optional.ofNullable(instance.getSettings().getRuntime())
                    .orElseGet(() -> {
                        JavaRuntime configRuntime = config.getJavaRuntime();
                        if (configRuntime != null) {
                            return configRuntime;
                        }
                        return Optional.ofNullable(versionManifest.getJavaVersion())
                            .flatMap(JavaRuntimeFinder::findBestJavaRuntime)
                            .orElse(null);
                    });
                enforcedReject = com.gummer.launcher.util.EnforcedJava.reject(enforced,
                        versionManifest.getJavaVersion() != null ? versionManifest.getJavaVersion().getMajorVersion() : 0);
                if (enforcedReject != null) {
                    log.warning("Chosen Java ignored for this launch (" + enforcedReject + "): " + enforced);
                }
            }
            if (config.isEnforceRuntime() && enforcedReject == null) {
                // check if config enforceRuntime option enabled -- then use specified runtime
                JavaRuntime runtime = enforced;
                builder.setRuntime(runtime);
                SwingExtras.logDebugMessage(launcher, "builder.setRuntime", "using system runtime as config.isEnforceRuntime is true\n" + String.valueOf(runtime));
            } else if (RuntimePackJvmPath != null && !FileUtils.isDirEmpty(RuntimePackJvmPath)) {
                // check if legacy JVM zip file exist in pack -- then use it
                progress = new DefaultProgress(0.99, "Launching JVM ...");
                JavaRuntime runtime = JavaRuntimeFinder.getRuntimeFromPath(new File(RuntimePackJvmPath.getPath()));
                builder.setRuntime(runtime);
                SwingExtras.logDebugMessage(launcher, "builder.setRuntime", "using legacy ZIP runtime\n" + String.valueOf(runtime));
            } else {
                // by default use shared distributed runtime
                JavaRuntime runtime = JavaRuntime.fromDir(
                    (new File(launcher.getBaseDir(), "runtime/" + instance.getJavaRuntime() + "/"
                            + com.gummer.launcher.util.MacRosetta.runtimeOs(versionManifest))).toString()
                );
                builder.setRuntime(runtime);
                SwingExtras.logDebugMessage(launcher, "builder.setRuntime", "using default shared runtime\n" + String.valueOf(runtime));
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        // end final JVM runtime setup

        List<String> flags = builder.getFlags();
        String[] rawJvmArgsList = new String[] {
                config.getJvmArgs(),
                instance.getSettings().getCustomJvmArgs()
        };

        for (String rawJvmArgs : rawJvmArgsList) {
            if (!Strings.isNullOrEmpty(rawJvmArgs)) {
                flags.addAll(JavaProcessBuilder.splitArgs(rawJvmArgs));
            }
        }
        // 1.36.1 (java4shell): -XX:-UseBASE64Intrinsics для Java 17+, якщо гравець сам не задав
        com.gummer.launcher.util.JavaSecurity.addGameFlags(builder.getRuntime(), flags);

        List<GameArgument> javaArguments = versionManifest.getArguments().getJvmArguments();
        StrSubstitutor substitutor = new StrSubstitutor(getCommandSubstitutions());
        for (GameArgument arg : javaArguments) {
            if (arg.shouldApply(environment, featureList)) {
                for (String subArg : arg.getValues()) {
                    flags.add(substitutor.replace(subArg));
                }
            }
        }

        if (versionManifest.getLogging() != null && versionManifest.getLogging().getClient() != null) {
            log.info("Logging config present, log4j2 bug likely mitigated");

            VersionManifest.LoggingConfig config = versionManifest.getLogging().getClient();
            File configFile = new File(launcher.getLibrariesDir(), config.getFile().getId());
            StrSubstitutor loggingSub = new StrSubstitutor(ImmutableMap.of("path", configFile.getAbsolutePath()));

            flags.add(loggingSub.replace(config.getArgument()));
        }
    }

    /**
     * Add arguments for the application.
     *
     * @throws JsonProcessingException on error
     */
    private void addJarArgs() throws JsonProcessingException {
        List<String> args = builder.getArgs();

        List<GameArgument> rawArgs = versionManifest.getArguments().getGameArguments();
        StrSubstitutor substitutor = new StrSubstitutor(getCommandSubstitutions());
        for (GameArgument arg : rawArgs) {
            if (arg.shouldApply(environment, featureList)) {
                for (String subArg : arg.getValues()) {
                    args.add(substitutor.replace(subArg));
                }
            }
        }
    }

    @SuppressWarnings({"UnstableApiUsage", "CallToPrintStackTrace"})
    private File setupZipRuntime() {
        // get custom pack jvm start -- LEGACY ZIP RUNTIME FEATURE
        File PackJvmZip = new File(instance.getContentDir(),"jvm.zip");
        File PackJvmRootPath = new File(instance.getContentDir(),"jvm");
        String PackJvmPathOS = null;
        File PackJvmPath = null;
        String PackJvmPathArch = "";
        String PackJarZipHash = null;

        if (PackJvmZip.isFile()) {
            log.info("JVM archive " + PackJvmZip.getPath() + " exist");
            progress = new DefaultProgress(0.95, "Processing pre-packed JVM");

            try {
                progress = new DefaultProgress(0.96, "Checking pre-packed JVM");
                PackJarZipHash = FileUtils.getMD5Hash(PackJvmZip.getPath());
                log.info("JVM checksum: " + PackJarZipHash);
            } catch (Exception e) {
                e.printStackTrace();
            }

            if (!PackJvmRootPath.exists()) {
                log.info("JVM directory " + PackJvmRootPath.getPath() + " not exist, creating...");
                PackJvmRootPath.mkdirs();
            } else {
                log.info("JVM directory " + PackJvmRootPath.getPath() + " exist, checking...");
                File chkSum = new File(PackJvmRootPath.getPath(),PackJarZipHash + ".txt");
                if (chkSum.isFile()) {
                    log.info("JVM checksum is valid");
                } else {
                    log.info("JVM checksum is invalid, recreating jvm dir");
                    progress = new DefaultProgress(0.97, "Updating pre-packed JVM");
                    FileUtils.delDir(PackJvmRootPath);
                    PackJvmRootPath.mkdirs();
                }
            }

            if (!FileUtils.isDirEmpty(PackJvmRootPath)) {
                log.info("JVM directory " + PackJvmRootPath.getPath() + " is not empty"); // then check hashes
            } else {
                progress = new DefaultProgress(0.98, "Setting-up pre-packed JVM");
                log.info("JVM directory " + PackJvmRootPath.getPath() + " is empty");
                log.info("Unpacking JVM archive " + PackJvmZip.getPath() + " to " + PackJvmRootPath.getPath());
                ZipExtract zipExtract = new ZipExtract(Files.asByteSource(PackJvmZip), PackJvmRootPath);
                zipExtract.run();
                SwingExtras.logDebugMessage(launcher, "Saving checksum file from remote" + PackJarZipHash + ".txt");

                log.info("Saving checksum file from remote");
                try {
                    File chksum = new File(PackJvmRootPath.getPath(),PackJarZipHash + ".txt");
                    if (chksum.createNewFile()) {
                        SwingExtras.logDebugMessage(launcher, "checksum file created " + String.valueOf(chksum.getName()));
                        log.info("checksum file created");
                    } else {
                        log.info("checksum file already exists");
                    }
                } catch (IOException e) {
                    log.info("An error occurred.");
                    e.printStackTrace();
                }
            }

            // get OS and ARCH subdirectory path
            if (getEnvironment().getPlatform() == Platform.WINDOWS) {
                PackJvmPathOS = "windows";
                PackJvmPathArch = System.getProperty("os.arch").contains("64") ? "64" : "32";
            }
            if (getEnvironment().getPlatform() == Platform.MAC_OS_X) {
                PackJvmPathOS = "osx";
                PackJvmPathArch = "Home"; // no x32 jvm builds for osx :(
                FileUtils.fixChmod(new File(new File(new File(PackJvmRootPath,PackJvmPathOS),PackJvmPathArch),"bin").getAbsolutePath(), 777);
            }
            if (getEnvironment().getPlatform() == Platform.SOLARIS) {
                PackJvmPathOS = "solaris";
            }
            if (getEnvironment().getPlatform() == Platform.LINUX) {
                PackJvmPathOS = "linux";
                PackJvmPathArch = System.getProperty("os.arch").contains("64") ? "64" : "32";
                FileUtils.fixChmod(new File(new File(new File(PackJvmRootPath,PackJvmPathOS),PackJvmPathArch),"bin").getAbsolutePath(),777);
            }

            // build PackJvmPath from  jvm_root + os + arch dirs
            PackJvmPath = new File(new File(PackJvmRootPath,PackJvmPathOS),PackJvmPathArch);
            log.info("JVM path: " + PackJvmPath.getPath());
        }

        return PackJvmPath;
        // get custom pack jvm finish setup -- LEGACY ZIP RUNTIME FEATURE
    }

    /**
     * Add proxy arguments.
     */
    private void addProxyArgs() {
        List<String> args = builder.getArgs();

        if (config.isProxyEnabled()) {
            String host = config.getProxyHost();
            int port = config.getProxyPort();
            String username = config.getProxyUsername();
            String password = config.getProxyPassword();

            if (!Strings.isNullOrEmpty(host) && port > 0 && port < 65535) {
                args.add("--proxyHost");
                args.add(config.getProxyHost());
                args.add("--proxyPort");
                args.add(String.valueOf(port));

                if (!Strings.isNullOrEmpty(username)) {
                    builder.getArgs().add("--proxyUser");
                    builder.getArgs().add(username);
                    builder.getArgs().add("--proxyPass");
                    builder.getArgs().add(password);
                }
            }
        }
    }

    /** Адреса для «Запуск одразу на сервер» (1.32): null, якщо цього разу не треба. */
    private String quickJoinAddress;
    private String quickWorld;

    /** Чи є в аргументах гри з JSON версії такий (наприклад quickPlaySingleplayer). */
    private boolean versionHasArg(String name) {
        if (versionManifest.getArguments() == null || versionManifest.getArguments().getGameArguments() == null) return false;
        for (GameArgument arg : versionManifest.getArguments().getGameArguments()) {
            for (String v : arg.getValues()) {
                if (v.contains(name)) return true;
            }
        }
        return false;
    }

    /**
     * 1.32: «Запуск одразу на сервер» для паків вкладки GummerCraft з адресою сервера (список v2, serverAddress).
     * Нові версії (1.20+) мають у JSON версії аргумент --quickPlayMultiplayer (вмикається ознакою
     * is_quick_play_multiplayer); старіші розуміють --server і --port. Ручне налаштування сервера в config
     * (addServerArgs) має перевагу.
     */
    private void addQuickJoin() {
        // 17.11.2026 (власник: «Продовжити останній світ» через --quickPlaySingleplayer): гравець вибрав світ, гра
        // одразу відкриває його (1.20+, ознака is_quick_play_singleplayer у JSON версії). Разовий запит: наступний
        // звичайний «Запуск» відкриває меню
        String world = com.gummer.launcher.manage.Worlds.takeQuickPlay(instance);
        if (world != null) {
            if (versionHasArg("quickPlaySingleplayer")) {
                quickWorld = world;
                featureList.addFeature("is_quick_play_singleplayer", true);
                log.info("Quick play into the world " + world);
                return;
            }
            log.info("This version has no --quickPlaySingleplayer, opening the menu instead of " + world);
        }
        String address = instance.getServerAddress();
        if (address == null || address.trim().isEmpty() || instance.isBuiltIn() || config.isServerEnabled()
                || !com.gummer.launcher.prefs.LauncherPrefs.getBoolean(com.gummer.launcher.prefs.LauncherPrefs.QUICK_JOIN, true)) {
            return;
        }
        address = address.trim();
        boolean quickPlay = versionHasArg("quickPlayMultiplayer");
        if (quickPlay) {
            quickJoinAddress = address;
            featureList.addFeature("is_quick_play_multiplayer", true);
            log.info("Quick join via --quickPlayMultiplayer " + address);
        } else {
            int colon = address.lastIndexOf(':');
            String host = colon > 0 ? address.substring(0, colon) : address;
            String port = colon > 0 ? address.substring(colon + 1) : "25565";
            builder.getArgs().add("--server");
            builder.getArgs().add(host);
            builder.getArgs().add("--port");
            builder.getArgs().add(port);
            log.info("Quick join via --server " + host + " --port " + port);
        }
    }

    /**
     * Add server arguments.
     */
    private void addServerArgs() {
        List<String> args = builder.getArgs();

        if (config.isServerEnabled()) {
            String host = config.getServerHost();
            int port = config.getServerPort();

            if (!Strings.isNullOrEmpty(host) && port > 0 && port < 65535) {
                args.add("--server");
                args.add(host);
                args.add("--port");
                args.add(String.valueOf(port));
            }
        }
    }

    /**
     * Add window arguments.
     */
    private void addWindowArgs() {
        int width = config.getWindowWidth();

        if (width >= 10) {
            featureList.addFeature("has_custom_resolution", true);
        }
    }

    /**
     * Add arguments to make legacy Minecraft work.
     */
    private void addLegacyArgs() {
        List<String> flags = builder.getFlags();

        if (versionManifest.getMinimumLauncherVersion() < 21) {
            // Add bits that the legacy manifests don't
            flags.add("-Djava.library.path=" + extractDir.getAbsoluteFile());
            flags.add("-cp");
            flags.add(builder.buildClassPath());

            if (featureList.hasFeature("has_custom_resolution")) {
                List<String> args = builder.getArgs();
                args.add("--width");
                args.add(String.valueOf(config.getWindowWidth()));
                args.add("--height");
                args.add(String.valueOf(config.getWindowHeight()));
            }

            // Add old platform hacks that the new manifests already specify
            if (getEnvironment().getPlatform() == Platform.WINDOWS) {
                flags.add("-XX:HeapDumpPath=MojangTricksIntelDriversForPerformance_javaw.exe_minecraft.exe.heapdump");
            }
        }

        if (versionManifest.getMinimumLauncherVersion() < 18) {
            // TODO find out exactly what versions need this hack.
            flags.add("-Dminecraft.applet.TargetDirectory=" + instance.getContentDir());
        }
    }

    /**
     * Build the list of command substitutions.
     *
     * @return the map of substitutions
     * @throws JsonProcessingException on error
     */
    private Map<String, String> getCommandSubstitutions() throws JsonProcessingException {
        Map<String, String> map = new HashMap<String, String>();

        map.put("version_name", versionManifest.getId());
        map.put("version_type", launcher.getProperties().getProperty("launcherShortname"));

        map.put("auth_access_token", session.getAccessToken());
        map.put("auth_session", session.getSessionToken());
        map.put("auth_player_name", session.getName());
        map.put("auth_uuid", session.getUuid());

        map.put("profile_name", session.getName());
        map.put("user_type", session.getUserType().getId());
        map.put("user_properties", mapper.writeValueAsString(session.getUserProperties()));

        map.put("game_directory", instance.getContentDir().getAbsolutePath());
        map.put("game_assets", virtualAssetsDir.getAbsolutePath());
        map.put("assets_root", launcher.getAssets().getDir().getAbsolutePath());
        map.put("assets_index_name", versionManifest.getAssetId());

        if (quickJoinAddress != null) {
            map.put("quickPlayMultiplayer", quickJoinAddress);
        }
        if (quickWorld != null) {
            map.put("quickPlaySingleplayer", quickWorld);
        }

        map.put("resolution_width", String.valueOf(config.getWindowWidth()));
        map.put("resolution_height", String.valueOf(config.getWindowHeight()));

        map.put("launcher_name", launcher.getTitle());
        map.put("launcher_version", launcher.getVersion());
        map.put("classpath", builder.buildClassPath());
        map.put("natives_directory", extractDir.getAbsolutePath());

        // Forge additions
        map.put("library_directory", launcher.getLibrariesDir().getAbsolutePath());
        map.put("classpath_separator", System.getProperty("path.separator"));

        return map;
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
