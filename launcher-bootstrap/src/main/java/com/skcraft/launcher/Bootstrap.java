/*
 * SK's Minecraft Launcher
 * Copyright (C) 2010-2014 Albert Pham <http://www.sk89q.com> and contributors
 * Copyright (C) 2021-2025 GummerCraft <http://gummercraft.fun>
 * Please see LICENSE.txt for license information.
 */

package com.skcraft.launcher;

import com.gummer.launcher.shared.BootstrapConfig;
import com.gummer.launcher.shared.Toolbox;
import com.gummer.launcher.shared.TrustedTls;
import com.skcraft.launcher.bootstrap.*;
import lombok.Getter;
import lombok.extern.java.Log;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;

import javax.swing.*;
import javax.swing.filechooser.FileSystemView;
import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.logging.Level;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static com.skcraft.launcher.bootstrap.SharedLocale.tr;

@Log
public class Bootstrap {

    private static final int BOOTSTRAP_VERSION = 1;

    @Getter private final File baseDir;
    @Getter private final boolean portable;
    @Getter private final File binariesDir;
    @Getter private final Properties properties;
    private final String[] originalArgs;

    public static void main(String[] args) throws Throwable {
        SimpleLogFormatter.configureGlobalLogger();
        SharedLocale.loadBundle("com.skcraft.launcher.lang.Bootstrap", Locale.getDefault());
        TrustedTls.install(); // strict HTTPS with extra roots for outdated JREs

        boolean portable = isPortableMode();

        Bootstrap bootstrap = new Bootstrap(portable, args);
        try {
            bootstrap.cleanup();
            bootstrap.launch();
        } catch (Throwable t) {
            Bootstrap.log.log(Level.WARNING, "Error", t);
            Bootstrap.setSwingLookAndFeel();
            SwingHelper.showErrorDialog(null, tr("errors.bootstrapError"), tr("errorTitle"), t);
        }
    }

    public Bootstrap(boolean portable, String[] args) throws IOException {
        this.properties = BootstrapUtils.loadProperties(Bootstrap.class, "bootstrap.properties");

        File baseDir = portable ? new File(".") : getUserLauncherDir();

        this.baseDir = baseDir;
        this.portable = portable;
        this.binariesDir = new File(baseDir, "launcher");
        this.originalArgs = args;

        binariesDir.mkdirs();
    }

    public void cleanup() {
        File[] files = binariesDir.listFiles(new FileFilter() {
            @Override
            public boolean accept(File pathname) {
                return pathname.getName().endsWith(".tmp");
            }
        });

        if (files != null) {
            for (File file : files) {
                file.delete();
            }
        }
    }

    public void launch() throws Throwable {
        File[] files = binariesDir.listFiles(new LauncherBinary.Filter());
        List<LauncherBinary> binaries = new ArrayList<LauncherBinary>();

        if (files != null) {
            for (File file : files) {
                Bootstrap.log.info("Found " + file.getAbsolutePath() + "...");
                binaries.add(new LauncherBinary(file));
            }
        }

        if (binaries.isEmpty()) {
            launchInitial();
        } else if (isNewerVersionPublished(binaries)) {
            // download the published version right away instead of starting the old one and asking to self-update;
            // if that download fails, the installed version is started as before
            Bootstrap.log.info("Downloading the newer published launcher...");
            new Thread(new Downloader(this, binaries)).start();
        } else {
            launchExisting(binaries, true);
        }
    }

    public void launchInitial() throws Exception {
        Bootstrap.log.info("Downloading the launcher...");
        Thread thread = new Thread(new Downloader(this));
        thread.start();
    }

    /**
     * Compares the newest installed launcher jar with the version in {@code latestUrl}. Any problem (offline, timeout,
     * unreadable jar) counts as "no newer version", so starting without internet works exactly as before.
     */
    private boolean isNewerVersionPublished(List<LauncherBinary> binaries) {
        try {
            List<LauncherBinary> sorted = new ArrayList<LauncherBinary>(binaries);
            Collections.sort(sorted);
            String installed = readLauncherVersion(sorted.get(0).getPath());
            String published = readPublishedVersion();
            if (installed == null || published == null) {
                return false;
            }
            log.info("Installed launcher " + installed + ", published " + published);
            return compareVersions(published, installed) > 0;
        } catch (Exception e) {
            log.log(Level.INFO, "Could not check for a newer launcher, starting the installed one", e);
            return false;
        }
    }

    private static String readLauncherVersion(File jar) throws IOException {
        try (ZipFile zip = new ZipFile(jar)) {
            ZipEntry entry = zip.getEntry("com/skcraft/launcher/launcher.properties");
            if (entry == null) {
                return null;
            }
            Properties props = new Properties();
            try (InputStream in = zip.getInputStream(entry)) {
                props.load(in);
            }
            return props.getProperty("version");
        }
    }

    private String readPublishedVersion() throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(getProperties().getProperty("latestUrl")).openConnection();
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);
        conn.setRequestProperty("User-Agent", Toolbox.USER_AGENT);
        try (Reader reader = new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8)) {
            Object json = JSONValue.parse(reader);
            if (json instanceof JSONObject && ((JSONObject) json).get("version") != null) {
                return String.valueOf(((JSONObject) json).get("version")).trim();
            }
            return null;
        } finally {
            conn.disconnect();
        }
    }

    /** Numeric, dot-separated comparison ("1.29" > "1.27", "1.10" > "1.9"); non-digit characters are ignored. */
    static int compareVersions(String a, String b) {
        String[] x = a.split("\\.");
        String[] y = b.split("\\.");
        for (int i = 0; i < Math.max(x.length, y.length); i++) {
            long p = i < x.length ? parsePart(x[i]) : 0;
            long q = i < y.length ? parsePart(y[i]) : 0;
            if (p != q) {
                return p > q ? 1 : -1;
            }
        }
        return 0;
    }

    private static long parsePart(String part) {
        String digits = part.replaceAll("[^0-9]", "");
        return digits.isEmpty() ? 0 : Long.parseLong(digits);
    }

    public void launchExisting(List<LauncherBinary> binaries, boolean redownload) throws Exception {
        Collections.sort(binaries);
        LauncherBinary working = null;
        Class<?> clazz = null;

        for (LauncherBinary binary : binaries) {
            File testFile = binary.getPath();
            try {
                testFile = binary.getExecutableJar();
                Bootstrap.log.info("Trying " + testFile.getAbsolutePath() + "...");
                clazz = load(testFile);
                Bootstrap.log.info("Launcher loaded successfully.");
                working = binary;
                break;
            } catch (Throwable t) {
                Bootstrap.log.log(Level.WARNING, "Failed to load " + testFile.getAbsoluteFile(), t);
            }
        }

        if (working != null) {
            for (LauncherBinary binary : binaries) {
                if (working != binary) {
                    log.info("Removing " + binary.getPath() + "...");
                    binary.remove();
                }
            }

            execute(clazz);
        } else {
            if (redownload) {
                launchInitial();
            } else {
                throw new IOException("Failed to find launchable .jar");
            }
        }
    }

    public void execute(Class<?> clazz) throws InvocationTargetException, IllegalAccessException, NoSuchMethodException {
        Method method = clazz.getDeclaredMethod("main", String[].class);
        String[] launcherArgs;

        if (portable) {
            launcherArgs = new String[] {
                    "--portable",
                    "--dir",
                    baseDir.getAbsolutePath(),
                    "--bootstrap-version",
                    String.valueOf(BOOTSTRAP_VERSION) };
        } else {
            launcherArgs = new String[] {
                    "--dir",
                    baseDir.getAbsolutePath(),
                    "--bootstrap-version",
                    String.valueOf(BOOTSTRAP_VERSION)  };
        }

        String[] args = new String[originalArgs.length + launcherArgs.length];
        System.arraycopy(launcherArgs, 0, args, 0, launcherArgs.length);
        System.arraycopy(originalArgs, 0, args, launcherArgs.length, originalArgs.length);

        log.info("Launching with arguments " + Arrays.toString(args));

        method.invoke(null, new Object[] { args });
    }

    public Class<?> load(File jarFile) throws MalformedURLException, ClassNotFoundException {
        URL[] urls = new URL[] { jarFile.toURI().toURL() };
        URLClassLoader child = new URLClassLoader(urls, this.getClass().getClassLoader());
        Class<?> clazz = Class.forName(getProperties().getProperty("launcherClass"), true, child);
        return clazz;
    }

    public static void setSwingLookAndFeel() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Throwable e) {
        }
    }

    //private static File getFileChooseDefaultDir() {
    public static File getFileChooseDefaultDir() {
        JFileChooser chooser = new JFileChooser();
        FileSystemView fsv = chooser.getFileSystemView();
        return fsv.getDefaultDirectory();
    }

    private File getUserLauncherDirDefault() {
        String osName = System.getProperty("os.name").toLowerCase();
        if (osName.contains("win")) {
            return new File(getFileChooseDefaultDir(), getProperties().getProperty("homeFolderWindows"));
        }

        File dotFolder = new File(System.getProperty("user.home"), getProperties().getProperty("homeFolder"));
        String xdgFolderName = getProperties().getProperty("homeFolderLinux");

        if (osName.contains("linux") && !dotFolder.exists() && xdgFolderName != null && !xdgFolderName.isEmpty()) {
            String xdgDataHome = System.getenv("XDG_DATA_HOME");
            if (xdgDataHome.isEmpty()) {
                xdgDataHome = System.getProperty("user.home") + "/.local/share";
            }

            return new File(xdgDataHome, xdgFolderName);
        }

        return dotFolder;
    }

    private File getUserLauncherDir() {
        return BootstrapConfig.get().getWorkingDirectory();
    }

    private static boolean isPortableMode() {
        return new File("portable.txt").exists();
    }


}
