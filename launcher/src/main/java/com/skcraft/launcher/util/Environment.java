/*
 * SK's Minecraft Launcher
 * Copyright (C) 2010-2014 Albert Pham <http://www.sk89q.com> and contributors
 * Copyright (C) 2021-2025 GummerCraft <http://gummercraft.fun>
 * Please see LICENSE.txt for license information.
 */

package com.skcraft.launcher.util;

import com.gummer.launcher.util.Architecture;
import com.sun.management.OperatingSystemMXBean;
import lombok.Getter;

import java.lang.management.ManagementFactory;

/**
 * Represents information about the current environment.
 */
public class Environment {

    @Getter
    private final Platform platform;
    @Getter
    private final String platformVersion;
    @Getter
    private final String arch;

    private String mojangOs = null;

    private Environment(Platform platform, String platformVersion, String arch) {
        this.platform = platform;
        this.platformVersion = platformVersion;
        this.arch = arch;
    }

    /**
     * Get an instance of the current environment.
     *
     * @return the current environment
     */
    public static Environment getInstance() {
        return new Environment(detectPlatform(), System.getProperty("os.version"), System.getProperty("os.arch"));
    }

    public String getArchBits() {
        return arch.contains("64") ? "64" : "32";
    }

    /**
     * Detect the current platform.
     *
     * @return the current platform
     */
    public static Platform detectPlatform() {
        String osName = System.getProperty("os.name").toLowerCase();
        if (osName.contains("win"))
            return Platform.WINDOWS;
        if (osName.contains("mac"))
            return Platform.MAC_OS_X;
        if (osName.contains("solaris") || osName.contains("sunos"))
            return Platform.SOLARIS;
        if (osName.contains("linux"))
            return Platform.LINUX;
        if (osName.contains("unix"))
            return Platform.LINUX;
        if (osName.contains("bsd"))
            return Platform.LINUX;

        return Platform.UNKNOWN;
    }

    // Those value come either from StackOverflow, random searches, or my ...
    public Architecture getArchitecture() {
        String arch = this.getArch().toLowerCase();
        if (arch.startsWith("arm64")) {
            return Architecture.ARM64;
        } else if (arch.startsWith("arm")) {
            return Architecture.ARM32;
        }

        switch (arch) {
            case "x64":
            case "amd64":
            case "x86_64":
                return Architecture.AMD64;
            case "x86":
            case "i386":
            case "i686":
            case "x86_32":
                return Architecture.I386;
            case "aarch64":
                return Architecture.ARM64;
            case "nacl":
                return Architecture.ARM32;
            default:
                return Architecture.UNKNOWN;
        }
    }

    public String getMojangOs() throws Exception {
        if (this.mojangOs != null) {
            return this.mojangOs;
        }

        //#region Radioactive area
        String currentOs = "";

        switch (this.getPlatform()) {
            case WINDOWS:
                currentOs = "windows";
                switch (this.getArchitecture()) {
                    case AMD64:
                        currentOs += "-x64";
                        break;
                    case I386:
                        currentOs += "-x86";
                        break;
                    case ARM64:
                        currentOs += "-arm64";
                        break;
                    default:
                        throw new Exception("Unable to find a correct Java version for your os/arch");
                }
                break;
            case MAC_OS_X:
                currentOs = "mac-os";
                switch (this.getArchitecture()) {
                    case AMD64:
                        // OSX x64 is simply "mac-os"
                        break;
                    case ARM64:
                        currentOs += "-arm64";
                        break;
                    default:
                        throw new Exception("Unable to find a correct Java version for your os/arch");
                }
                break;
            case LINUX:
                currentOs = "linux";
                switch (this.getArchitecture()) {
                    case AMD64:
                        // Linux 64 bits is simply "linux"
                        break;
                    case I386:
                        currentOs += "-i386";
                        break;
                    case ARM64:
                        currentOs += "-arm64";
                        break;
                    // @TODO find a source for JVM linux ARM64
                    default:
                        throw new Exception("Unable to find a correct Java version for your os/arch");
                }
                break;
            default:
                throw new Exception("Unable to find a correct Java version for your os/arch");
        }

        this.mojangOs = currentOs;

        return currentOs;
    }

    /**
     * get total system memory
     */
    public static long getTotalSystemMemory() {
        OperatingSystemMXBean osBean = (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
        return osBean.getTotalPhysicalMemorySize();
    }

}
