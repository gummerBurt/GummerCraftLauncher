/*
 * Copyright (C) 2021-2026 GummerCraft <http://gummercraft.fun>
 */

package com.gummer.launcher.manage;

import com.skcraft.concurrency.ProgressObservable;
import com.skcraft.launcher.Instance;
import com.skcraft.launcher.Launcher;
import com.skcraft.launcher.persistence.Persistence;
import com.skcraft.launcher.util.SharedLocale;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.Callable;

/**
 * 17.11.2026, «Мої збірки»: копії збірок Vanilla, Fabric, Quilt, Forge і NeoForge. Збірка в лаунчері це тека в
 * {@code instances}, а її тип і версія беруться з назви теки (fabric-1.21.1), тож друга збірка тієї самої версії
 * може бути лише окремою текою: {@code copy-<id>} з {@link Instance#getCopyOf()} (назва оригіналу) і
 * {@link Instance#getCustomTitle()} у instance.json. Бібліотеки, ресурси гри і Java спільні для всіх збірок, тож
 * копіюється лише тека гри ({@code minecraft}), і то лише вибрані частини.
 * <p>
 * Модпаки GummerCraft (Main) на Fabric теж копіюються (вирішено 17.11: «копію зі збірок гаммеркрафту робити дозволь,
 * просто вони підуть в фабрік»): копія стає звичайною збіркою Fabric тієї ж версії Minecraft і того ж Fabric Loader
 * з модами паку, але вже без оновлень паку. Сервер Main клієнтські моди не звіряє, тож такою копією можна на нього
 * заходити. StalDayZ не копіюється: його сервер пускає лише з модами самого паку.
 */
public final class InstanceCopies {

    public static final String PREFIX = "copy-";

    /** Що перенести в копію. Нічого не вибрано: чиста нова збірка тієї самої версії. */
    public enum Part {
        /** mods, config і решта тек модів (мапи, схеми), крім світів, паків і скріншотів. */
        MODS,
        /** Налаштування гри: options*.txt, servers.dat і інші файли в корені теки гри. */
        SETTINGS,
        /** Світи (saves). */
        WORLDS,
        /** Ресурспаки і шейдери. */
        PACKS
    }

    /** Ніколи не копіюються: це історія саме тієї збірки. */
    private static final String[] NEVER = {"screenshots", "logs", "crash-reports", "debug", ".mixin.out", "natives", ".fabric"};

    private InstanceCopies() {
    }

    /** Чи можна зробити копію: збірки, які лаунчер складає сам, і встановлені модпаки GummerCraft на Fabric. */
    public static boolean canCopy(Instance instance) {
        if (instance == null) return false;
        if (!instance.isBuiltIn()) return instance.isInstalled() && packBaseOf(instance) != null;
        return !instance.isMrpack() || com.gummer.launcher.modrinth.MrpackPacks.ENABLED;
    }

    private static final com.fasterxml.jackson.databind.ObjectMapper MAPPER = com.gummer.launcher.util.Json.newMapper();
    private static final String FABRIC_LOADER = "net.fabricmc:fabric-loader:";

    /**
     * Основа копії модпаку: {"fabric-26.2", "0.20.1"} (тип збірки і версія Fabric Loader) з version.json і
     * manifest.json встановленого паку. Не Fabric, StalDayZ чи не прочитати: null.
     */
    public static String[] packBaseOf(Instance instance) {
        if (instance == null || instance.isBuiltIn() || InstanceMods.packModsLocked(instance)) return null;
        try {
            com.fasterxml.jackson.databind.JsonNode v = MAPPER.readTree(new File(instance.getDir(), "version.json"));
            String loader = null;
            for (com.fasterxml.jackson.databind.JsonNode lib : v.path("libraries")) {
                String n = lib.path("name").asText("");
                if (n.startsWith(FABRIC_LOADER)) loader = n.substring(FABRIC_LOADER.length());
            }
            if (loader == null || loader.isEmpty()) return null;
            String mc = null;
            File manifest = new File(instance.getDir(), "manifest.json");
            if (manifest.isFile()) mc = MAPPER.readTree(manifest).path("gameVersion").asText(null);
            if (mc == null || mc.isEmpty()) mc = v.path("inheritsFrom").asText(v.path("id").asText(null));
            if (mc == null || mc.isEmpty()) return null;
            return new String[]{com.gummer.launcher.fabric.FabricVersions.PREFIX + mc, loader};
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    /** Версія Minecraft збірки (1.21.1), або null. */
    public static String minecraftVersionOf(Instance instance) {
        if (instance.isMrpack()) return com.gummer.launcher.modrinth.MrpackPacks.mcOf(instance);
        if (instance.isFabric()) return com.gummer.launcher.fabric.FabricVersions.versionIdOf(instance);
        if (instance.isQuilt()) return com.gummer.launcher.quilt.QuiltVersions.versionIdOf(instance);
        if (instance.isForgeLike()) return com.gummer.launcher.forge.ForgeVersions.versionIdOf(instance);
        if (instance.isVanilla()) return com.gummer.launcher.vanilla.VanillaVersions.versionIdOf(instance);
        return null;
    }

    /**
     * 17.11.2026: розділ списку (група), у якому стоїть збірка: vanilla, fabric, quilt, forge, neoforge. Копії і
     * збірки Modrinth (і копії модпаків GummerCraft, які стають Fabric) показуються не окремим рядком, а у виборі
     * версії свого завантажувача, під чистою версією. null, якщо це не збірка Minecraft чи завантажувача.
     */
    public static String sectionOf(Instance instance) {
        if (instance == null) return null;
        if (instance.isMrpack()) {
            String l = com.gummer.launcher.modrinth.MrpackPacks.loaderOf(instance).toLowerCase(java.util.Locale.ROOT);
            switch (l) {
                case "fabric":
                    return com.gummer.launcher.fabric.FabricVersions.GROUP;
                case "quilt":
                    return com.gummer.launcher.quilt.QuiltVersions.GROUP;
                case "forge":
                    return com.gummer.launcher.forge.ForgeVersions.Kind.FORGE.group;
                case "neoforge":
                    return com.gummer.launcher.forge.ForgeVersions.Kind.NEOFORGE.group;
                default:
                    return com.gummer.launcher.vanilla.VanillaVersions.GROUP;
            }
        }
        if (instance.isVanilla()) return com.gummer.launcher.vanilla.VanillaVersions.GROUP;
        if (instance.isFabric()) return com.gummer.launcher.fabric.FabricVersions.GROUP;
        if (instance.isQuilt()) return com.gummer.launcher.quilt.QuiltVersions.GROUP;
        if (instance.isForgeLike()) return com.gummer.launcher.forge.ForgeVersions.kindOf(instance).group;
        return null;
    }

    /** Порядок розділу в списку (як у чистих версій): Vanilla 0, Fabric -1, Quilt -2, Forge -3, NeoForge -4. */
    public static int sectionPriority(String section) {
        if (section == null) return -9;
        switch (section) {
            case "fabric":
                return -1;
            case "quilt":
                return -2;
            case "forge":
                return -3;
            case "neoforge":
                return -4;
            default:
                return 0;
        }
    }

    /** Назва розділу: Vanilla, Fabric, Quilt, Forge, NeoForge (або null). */
    public static String sectionTitle(String section) {
        if (section == null) return null;
        switch (section) {
            case "vanilla":
                return "Vanilla";
            case "fabric":
                return "Fabric";
            case "quilt":
                return "Quilt";
            case "forge":
                return com.gummer.launcher.forge.ForgeVersions.Kind.FORGE.title;
            case "neoforge":
                return com.gummer.launcher.forge.ForgeVersions.Kind.NEOFORGE.title;
            default:
                return null;
        }
    }

    /** Тип і версія без назви гравця: «Fabric 1.21.1», «Vanilla 1.20.4», «Forge 1.16.5». */
    public static String kindTitleOf(Instance instance) {
        String mc = minecraftVersionOf(instance);
        if (instance.isMrpack()) return com.gummer.launcher.modrinth.MrpackPacks.titleOf(instance);
        if (instance.isFabric()) return com.gummer.launcher.fabric.FabricVersions.titleOf(mc);
        if (instance.isQuilt()) return com.gummer.launcher.quilt.QuiltVersions.titleOf(mc);
        if (instance.isForgeLike()) {
            return com.gummer.launcher.forge.ForgeVersions.titleOf(com.gummer.launcher.forge.ForgeVersions.kindOf(instance), mc);
        }
        if (instance.isVanilla()) return com.gummer.launcher.vanilla.VanillaVersions.titleOf(mc);
        return instance.getTitle();
    }

    /** Назва копії в списку: та, що дав гравець, інакше «Fabric 1.21.1 (копія)». */
    public static String defaultTitleOf(Instance copy) {
        String custom = copy.getCustomTitle();
        if (custom != null && !custom.trim().isEmpty()) return custom.trim();
        return SharedLocale.tr("manage.copy.defaultTitle", kindTitleOf(copy));
    }

    /** Звичайна назва версії чи модпака Modrinth без своєї назви гравця («Minecraft 26.3», «Forge 1.16.5»), або null. */
    public static String builtInTitleOf(Instance instance) {
        if (instance == null) return null;
        if (instance.isCopy()) return SharedLocale.tr("manage.copy.defaultTitle", kindTitleOf(instance));
        if (instance.isMrpack()) return com.gummer.launcher.modrinth.MrpackPacks.titleOf(instance);
        if (instance.isVanilla()) return com.gummer.launcher.vanilla.VanillaVersions.titleOf(com.gummer.launcher.vanilla.VanillaVersions.versionIdOf(instance));
        if (instance.isFabric()) return com.gummer.launcher.fabric.FabricVersions.titleOf(com.gummer.launcher.fabric.FabricVersions.versionIdOf(instance));
        if (instance.isQuilt()) return com.gummer.launcher.quilt.QuiltVersions.titleOf(com.gummer.launcher.quilt.QuiltVersions.versionIdOf(instance));
        if (instance.isForgeLike()) {
            com.gummer.launcher.forge.ForgeVersions.Kind k = com.gummer.launcher.forge.ForgeVersions.kindOf(instance);
            return com.gummer.launcher.forge.ForgeVersions.titleOf(k, com.gummer.launcher.forge.ForgeVersions.versionIdOf(instance));
        }
        return null;
    }

    /** Найдовша назва збірки, яку можна дати (довша не влазить у картку списку). */
    public static final int MAX_TITLE = 40;

    /** Прибрати зайве з назви, яку ввів гравець: пробіли, керувальні символи, довжина. Порожня -> null. */
    public static String cleanTitle(String title) {
        if (title == null) return null;
        String t = title.replaceAll("\\p{Cntrl}", " ").replaceAll("\\s+", " ").trim();
        if (t.length() > MAX_TITLE) t = t.substring(0, MAX_TITLE).trim();
        return t.isEmpty() ? null : t;
    }

    /** Нова тека copy-<час у base36>, якої ще немає. */
    static File newDir(File instancesDir) {
        long t = System.currentTimeMillis();
        for (int i = 0; i < 1000; i++) {
            File dir = new File(instancesDir, PREFIX + Long.toString(t + i, 36).toLowerCase(Locale.ROOT));
            if (!dir.exists()) return dir;
        }
        throw new IllegalStateException("No free name for a copy in " + instancesDir);
    }

    /**
     * Завдання копіювання для {@code launcher.getExecutor()}: повертає назву нової збірки (теки), щоб її вибрати.
     * Помилка посередині: недороблена тека прибирається, оригінал не чіпається ніколи.
     */
    public static final class Copier implements Callable<String>, ProgressObservable {
        private final Launcher launcher;
        private final Instance source;
        private final String title;
        private final Set<Part> parts;
        private volatile String status;
        private volatile double progress = -1;
        private long totalBytes, doneBytes;

        public Copier(Launcher launcher, Instance source, String title, Set<Part> parts) {
            this.launcher = launcher;
            this.source = source;
            this.title = cleanTitle(title);
            this.parts = parts == null ? EnumSet.noneOf(Part.class) : EnumSet.copyOf(parts);
            this.status = SharedLocale.tr("manage.copy.preparing");
        }

        @Override
        public double getProgress() {
            return progress;
        }

        @Override
        public String getStatus() {
            return status;
        }

        @Override
        public String call() throws Exception {
            if (!canCopy(source)) throw new IOException("This build cannot be copied: " + source.getName());
            final String[] base = source.isBuiltIn() ? null : packBaseOf(source);
            File target = newDir(launcher.getInstancesDir());
            try {
                File from = new File(source.getDir(), "minecraft");
                File to = new File(target, "minecraft");
                if (!to.mkdirs() && !to.isDirectory()) throw new IOException("Cannot create " + to);
                List<File[]> jobs = new ArrayList<File[]>();
                File[] items = from.isDirectory() ? from.listFiles() : null;
                if (items != null) {
                    for (File item : items) {
                        if (wanted(item)) jobs.add(new File[]{item, new File(to, item.getName())});
                    }
                }
                for (File[] job : jobs) totalBytes += size(job[0]);
                for (File[] job : jobs) copy(job[0], job[1]);
                // закріплена версія завантажувача (fabric-loader.txt, forge-loader.txt...) і mrpack.json лежать у теці
                // збірки, не в minecraft: без них копія поставила б найновіший Fabric чи Forge, і моди могли б зламатись
                File[] meta = source.getDir().listFiles(f -> f.isFile() && (f.getName().endsWith("-loader.txt")
                        || f.getName().equals(com.gummer.launcher.modrinth.MrpackPacks.INFO)
                        || f.getName().equals(com.gummer.launcher.modrinth.MrpackPacks.FILES)
                        || f.getName().equals(com.gummer.launcher.modrinth.MrpackExport.FROM_PACK)));
                if (meta != null && base == null) {
                    for (File m : meta) Files.copy(m.toPath(), new File(target, m.getName()).toPath(), StandardCopyOption.REPLACE_EXISTING);
                }
                // копія модпаку: той самий Fabric Loader, що в паку (моди паку зібрані під нього)
                if (base != null) {
                    Files.write(new File(target, "fabric-loader.txt").toPath(), base[1].getBytes(java.nio.charset.StandardCharsets.UTF_8));
                    // 17.11.2026: позначка «копія модпаку GummerCraft»: експорт .mrpack не кладе у файл моди поза Modrinth
                    Files.write(new File(target, com.gummer.launcher.modrinth.MrpackExport.FROM_PACK).toPath(),
                            source.getName().getBytes(java.nio.charset.StandardCharsets.UTF_8));
                }

                // 18.11.2026: стан копії на цей момент, для «Повернути до початкового стану» (CopyReset)
                CopyReset.record(target);

                Instance copy = Persistence.load(new File(target, "instance.json"), Instance.class);
                copy.setDir(target);
                copy.setName(target.getName());
                copy.setCopyOf(base != null ? base[0] : source.getKindName());
                copy.setCustomTitle(title);
                copy.setVersion(base != null ? base[0].substring(com.gummer.launcher.fabric.FabricVersions.PREFIX.length()) : source.getVersion());
                copy.setJavaRuntime(source.getJavaRuntime());
                // Java і пам'ять як в оригіналі; гра доставиться при першому запуску (бібліотеки вже на диску)
                com.skcraft.launcher.InstanceSettings s = source.getSettings();
                if (s != null) {
                    com.skcraft.launcher.InstanceSettings d = new com.skcraft.launcher.InstanceSettings();
                    d.setRuntime(s.getRuntime());
                    d.setCustomJvmArgs(s.getCustomJvmArgs());
                    if (s.getMemorySettings() != null) {
                        com.skcraft.launcher.launch.MemorySettings m = new com.skcraft.launcher.launch.MemorySettings();
                        m.setMinMemory(s.getMemorySettings().getMinMemory());
                        m.setMaxMemory(s.getMemorySettings().getMaxMemory());
                        d.setMemorySettings(m);
                    }
                    copy.setSettings(d);
                }
                copy.setInstalled(false);
                copy.setUpdatePending(true);
                copy.setTitle(defaultTitleOf(copy));
                Persistence.commit(copy);
                status = SharedLocale.tr("manage.copy.done");
                progress = 1;
                return copy.getName();
            } catch (Exception e) {
                deleteQuietly(target);
                throw e;
            }
        }

        private boolean wanted(File item) {
            String n = item.getName().toLowerCase(Locale.ROOT);
            for (String never : NEVER) {
                if (n.equals(never)) return false;
            }
            if (item.isDirectory()) {
                if (n.equals("saves")) return parts.contains(Part.WORLDS);
                if (n.equals("resourcepacks") || n.equals("shaderpacks") || n.equals("texturepacks")) return parts.contains(Part.PACKS);
                return parts.contains(Part.MODS); // mods, config і теки модів (мапи, схеми, налаштування)
            }
            if (n.endsWith(".log")) return false;
            return parts.contains(Part.SETTINGS); // options.txt, servers.dat, usercache...
        }

        private long size(File f) {
            if (f.isFile()) return f.length();
            long s = 0;
            File[] kids = f.listFiles();
            if (kids != null) for (File k : kids) s += size(k);
            return s;
        }

        private void copy(File from, File to) throws IOException, InterruptedException {
            if (Thread.interrupted()) throw new InterruptedException();
            if (from.isDirectory()) {
                if (!to.mkdirs() && !to.isDirectory()) throw new IOException("Cannot create " + to);
                File[] kids = from.listFiles();
                if (kids != null) for (File k : kids) copy(k, new File(to, k.getName()));
                return;
            }
            status = SharedLocale.tr("manage.copy.copying", from.getName());
            Files.copy(from.toPath(), to.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
            doneBytes += from.length();
            progress = totalBytes > 0 ? Math.min(0.99, (double) doneBytes / totalBytes) : -1;
        }
    }

    static void deleteQuietly(File f) {
        if (f == null || !f.exists()) return;
        File[] kids = f.isDirectory() ? f.listFiles() : null;
        if (kids != null) for (File k : kids) deleteQuietly(k);
        //noinspection ResultOfMethodCallIgnored
        f.delete();
    }

    /** Для старту вікна до завантаження списку: тип збірки, записаний у instance.json теки copy-..., інакше сама назва. */
    public static String kindNameOf(File instancesDir, String name) {
        if (name == null || !name.toLowerCase(Locale.ROOT).startsWith(PREFIX)) return name;
        try {
            Instance i = Persistence.read(new File(new File(instancesDir, name), "instance.json"), Instance.class);
            return i != null && i.isCopy() ? i.getCopyOf() : name;
        } catch (RuntimeException e) {
            return name;
        }
    }
}
