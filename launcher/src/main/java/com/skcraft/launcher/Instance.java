/*
 * SK's Minecraft Launcher
 * Copyright (C) 2010-2014 Albert Pham <http://www.sk89q.com> and contributors
 * Copyright (C) 2021-2025 GummerCraft <http://gummercraft.fun>
 * Please see LICENSE.txt for license information.
 */

package com.skcraft.launcher;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.io.Files;
import com.skcraft.launcher.launch.JavaProcessBuilder;
import com.skcraft.launcher.model.modpack.LaunchModifier;
import lombok.Data;
import lombok.Getter;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.Date;

/**
 * An instance is a profile that represents one particular installation
 * of the game, with separate files and so on.
 */
@Data
public class Instance implements Comparable<Instance> {

    private String title;
    private String name;
    private String version;
    private boolean updatePending;
    private boolean installed;
    private Date lastAccessed;
    private String icon;
    @JsonProperty("launch")
    private LaunchModifier launchModifier;
    private String javaRuntime;
    private InstanceSettings settings = new InstanceSettings();

    @JsonIgnore private File dir;
    @JsonIgnore private URL manifestURL;
    @JsonIgnore private int priority;
    @JsonIgnore private boolean selected;
    @JsonIgnore private boolean local;
    /** Пак є на диску, але його вже немає в успішно завантаженому списку модпаків CDN (з 1.32). */
    @JsonIgnore private boolean orphaned;
    /** Зі списку v2 (1.32+): група паків (наприклад "main"), або null. */
    @JsonIgnore private String group;
    /**
     * 17.11.2026: версія Minecraft копії чи збірки Modrinth для порядку у виборі версії (ставить InstanceList). Копія і
     * збірка стоять у розділі свого завантажувача одразу під чистою версією, з якої зроблені.
     */
    @JsonIgnore private String sortVersion;
    /** Зі списку v2: у паку є свій сервер. */
    @JsonIgnore private boolean server;
    /** Зі списку v2: рекомендована пам'ять у МБ, або null. */
    @JsonIgnore private Integer recommendedMemory;
    /** Зі списку v2 (1.32+): адреса сервера паку для «Запуск одразу на сервер» і онлайну на картці, або null. */
    @JsonIgnore private String serverAddress;
    /**
     * 17.11.2026: назва, яку гравець дав збірці у вікні «Керування збіркою» (null: звичайна назва). Зберігається в
     * instance.json; InstanceList ставить її в title після того, як назва прийшла зі списку модпаків чи версій.
     */
    private String customTitle;
    /**
     * 17.11.2026: копія збірки («Мої збірки»). Тут назва збірки, з якої вона зроблена (vanilla-1.21.1, fabric-1.20.1,
     * forge-1.16.5): тип, версія гри і завантажувач беруться з неї, а не з назви теки копії (copy-...). null: не копія.
     */
    private String copyOf;

    /** Назва, за якою визначається тип і версія: для копії назва оригіналу, інакше власна. */
    @JsonIgnore
    public String getKindName() {
        return copyOf != null && !copyOf.isEmpty() ? copyOf : name;
    }

    /** Копія збірки, зроблена гравцем (тека copy-...). */
    @JsonIgnore
    public boolean isCopy() {
        return copyOf != null && !copyOf.isEmpty();
    }

    /** Пак вкладки Vanilla: чиста версія Minecraft з маніфесту Mojang (назва vanilla-&lt;версія&gt;). */
    @JsonIgnore
    public boolean isVanilla() {
        return com.gummer.launcher.vanilla.VanillaVersions.isVanilla(this);
    }

    /** 1.33, розділ Fabric: чистий Minecraft з Fabric Loader (назва fabric-&lt;версія&gt;). */
    @JsonIgnore
    public boolean isFabric() {
        return com.gummer.launcher.fabric.FabricVersions.isFabric(this);
    }

    /** 1.33, розділи Forge і NeoForge: чистий Minecraft з Forge чи NeoForge (назва forge-, neoforge-&lt;версія&gt;). */
    @JsonIgnore
    public boolean isForgeLike() {
        return com.gummer.launcher.forge.ForgeVersions.kindOf(this) != null;
    }

    /** 1.33, розділ Quilt: чистий Minecraft з Quilt Loader (назва quilt-&lt;версія&gt;). */
    @JsonIgnore
    public boolean isQuilt() {
        return com.gummer.launcher.quilt.QuiltVersions.isQuilt(this);
    }

    /** 1.33: збірка, імпортована з файлу Modrinth .mrpack (назва mrpack-&lt;назва&gt;). */
    @JsonIgnore
    public boolean isMrpack() {
        return com.gummer.launcher.modrinth.MrpackPacks.isMrpackName(getKindName());
    }

    /** Чистий Minecraft з завантажувачем модів (Fabric, Quilt, Forge, NeoForge): моди гравець кладе сам. */
    @JsonIgnore
    public boolean isLoaderPack() {
        return isFabric() || isQuilt() || isForgeLike();
    }

    /** Версія, яку лаунчер збирає сам з Mojang (Vanilla, завантажувачі, збірки .mrpack), а не модпак з нашого CDN. */
    @JsonIgnore
    public boolean isBuiltIn() {
        return isVanilla() || isLoaderPack() || isMrpack();
    }

    /**
     * Get the tile of the instance, which might be the same as the
     * instance name if no title is set.
     *
     * @return a title
     */
    public String getTitle() {
        // 17.11.2026 (власник: «нехай гравець може змінити назву, іконку та бекграунд усіх скачаних збірок, крім збірок
        // гаммеркрафту та сталдейз»): своя назва гравця сильніша за назву, яку ставлять список версій і маніфест при
        // кожному оновленні (VanillaVersions.buildManifest і т. ін.). Модпаки з нашого CDN своєї назви не мають.
        if (customTitle != null && !customTitle.trim().isEmpty() && (isBuiltIn() || isCopy())) return customTitle.trim();
        return title != null ? title : name;
    }

    /**
     * Update the given process builder with launch settings that are
     * specific to this instance.
     *
     * @param builder the process builder
     */
    public void modify(JavaProcessBuilder builder) {
        if (launchModifier != null) {
            launchModifier.modify(builder);
        }
    }

    /**
     * Get the instance directory, creating it if possible.
     *
     * @return the directory
     * @see #getContentDir() where game files are stored
     */
    /**
     * 17.11.2026: тека збірки без звертання до диска. {@link #getDir()} щоразу створює теку (createParentDirs + mkdir), а
     * малювання списку збірок кликало його десятки разів на кадр (бейдж оновлень, mrpack.json): для читання брати це.
     */
    public File peekDir() {
        return dir;
    }

    public File getDir() {
        try {
            Files.createParentDirs(dir);
            dir.mkdir();
        } catch (IOException ignored) {
        }
        return dir;
    }

    /**
     * Get the file for the directory where Minecraft's game files are
     * stored, including user files (screenshots, etc.).
     *
     * @return the content directory, which may not exist
     */
    @JsonIgnore
    public File getContentDir() {
        File dir = new File(this.dir, "minecraft");
        try {
            Files.createParentDirs(dir);
            dir.mkdir();
        } catch (IOException ignored) {
        }
        return dir;
    }

    /**
     * Get the file for the package manifest.
     *
     * @return the manifest path, which may not exist
     */
    @JsonIgnore
    public File getManifestPath() {
        return new File(getDir(), "manifest.json");
    }

    /**
     * Get the file for the Minecraft version manfiest file.
     *
     * @return the version path, which may not exist
     */
    @JsonIgnore
    public File getVersionPath() {
        return new File(getDir(), "version.json");
    }

    /**
     * Get the file for the custom JAR file.
     *
     * @return the JAR file, which may not exist
     */
    @JsonIgnore
    public File getCustomJarPath() {
        return new File(getContentDir(), "custom_jar.jar");
    }

    @Override
    public String toString() {
        return name;
    }

    @Override
    public int hashCode() {
        return super.hashCode();
    }

    @Override
    public boolean equals(Object other) {
        return super.equals(other);
    }

    /**
     * Порядок у списку (з 1.32): покинуті паки в кінці; далі пріоритет зі списку модпаків (більший вище);
     * далі версія Minecraft з назви паку, від новішої до старішої, числами (26.2 вище 1.21, 1.21 вище 1.9);
     * далі назва. До 1.32 встановлені паки йшли першими за датою запуску, а решта за пріоритетом, тож список
     * виглядав випадковим. Останній вибраний пак і так запам'ятовується (Configuration.lastInstance).
     */
    @Override
    public int compareTo(Instance o) {
        if (orphaned != o.orphaned) {
            return orphaned ? 1 : -1;
        }
        if (priority != o.priority) {
            return priority > o.priority ? -1 : 1;
        }
        // «музей» Vanilla (з 16.11.2026): після звичайних версій, у порядку Mojang
        int ma = com.gummer.launcher.vanilla.VanillaVersions.museumIndex(this);
        int mb = com.gummer.launcher.vanilla.VanillaVersions.museumIndex(o);
        if (ma != mb && (ma < 0 || mb < 0)) {
            return ma < 0 ? -1 : 1;
        }
        if (ma != mb) {
            return Integer.compare(ma, mb);
        }
        int byVersion = compareVersions(versionOf(o), versionOf(this));
        if (byVersion != 0) {
            return byVersion;
        }
        // 17.11.2026: снапшоти наступної версії одразу під останнім релізом (і його копіями), від новішого
        int sa = com.gummer.launcher.vanilla.VanillaVersions.snapshotIndex(this);
        int sb = com.gummer.launcher.vanilla.VanillaVersions.snapshotIndex(o);
        if (sa != sb) {
            if (sa < 0 || sb < 0) return sa < 0 ? -1 : 1;
            return Integer.compare(sa, sb);
        }
        // 17.11.2026: чиста версія перша, під нею її копії і збірки Modrinth (за назвою)
        boolean da = isCopy() || isMrpack(), db = o.isCopy() || o.isMrpack();
        if (da != db) {
            return da ? 1 : -1;
        }
        String a = title != null ? title : (name != null ? name : "");
        String b = o.title != null ? o.title : (o.name != null ? o.name : "");
        return a.compareToIgnoreCase(b);
    }

    /** Версія Minecraft з назви паку (main-1.19.3 -> [1, 19, 3]), або з заголовка; немає -> порожній масив. */
    public static int[] versionOf(Instance instance) {
        // снапшот 26.4 стоїть за версією останнього релізу (26.3), а не вище за нього (17.11.2026)
        String snapshotBase = com.gummer.launcher.vanilla.VanillaVersions.snapshotBase(instance);
        if (snapshotBase != null) {
            int[] s = parseVersion(snapshotBase);
            if (s.length > 0) return s;
        }
        if (instance.sortVersion != null) {
            int[] s = parseVersion(instance.sortVersion);
            if (s.length > 0) return s;
        }
        int[] v = parseVersion(instance.getKindName());
        return v.length > 0 ? v : parseVersion(instance.getTitle());
    }

    static int[] parseVersion(String text) {
        if (text == null) {
            return new int[0];
        }
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d+(?:\\.\\d+)+)").matcher(text);
        if (!m.find()) {
            return new int[0];
        }
        String[] parts = m.group(1).split("\\.");
        int[] out = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            try {
                out[i] = Integer.parseInt(parts[i]);
            } catch (NumberFormatException e) {
                out[i] = 0;
            }
        }
        return out;
    }

    /** Порівняння версій покомпонентно; пак без версії вважається найстарішим. */
    static int compareVersions(int[] a, int[] b) {
        if (a.length == 0 || b.length == 0) {
            return Integer.compare(a.length == 0 ? 0 : 1, b.length == 0 ? 0 : 1);
        }
        for (int i = 0; i < Math.max(a.length, b.length); i++) {
            int x = i < a.length ? a[i] : 0;
            int y = i < b.length ? b[i] : 0;
            if (x != y) {
                return Integer.compare(x, y);
            }
        }
        return 0;
    }

    /**
     * Get the raw name of the instance.
     *
     * @return a raw instance name as String
     */
    @JsonIgnore public String getRawName() {
        return name;
    }

    /**
     * Get the raw version of the instance.
     *
     * @return a raw instance version as String
     */
    @JsonIgnore public String getRawVersion() {
        return version;
    }

    /**
     * Get the icon of the instance.
     *
     * @return a icon file name
     */
    public String getIcon() {
        return icon;
    }

}
