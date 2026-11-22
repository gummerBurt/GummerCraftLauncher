/*
 * Copyright (C) 2021-2026 GummerCraft <http://gummercraft.fun>
 */

package com.gummer.launcher.util;

import com.skcraft.launcher.Instance;
import com.skcraft.launcher.InstanceSettings;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Вибір відеокарти для збірки у вікні «Збірка» (картка «Відеокарта»): «Як вирішить система», «назва (інтегрована)»,
 * «назва (дискретна)».
 *
 * <p>Навіщо. 19.11.2026 (власник: «Гра на вбудованій відеокарті. -- ооо, це прям мастхев, і в ідеалі перемикач в
 * "збірка". На це вже не раз жалілись»). На ноутбуці з двома відеокартами (вбудована Intel чи AMD і окрема NVIDIA,
 * AMD Radeon RX, Intel Arc) система часто запускає Java на слабшій, і гра лагає на хорошому залізі. Того ж дня власник
 * попросив список відеокарт замість галочки («показувати гравцю які в нього є відеокарти щоб він між ними міг
 * перемикатись»).
 *
 * <p>Що саме робиться, прозоро (власник: «прозоро і чесно»):
 * <ul>
 * <li><b>Windows 10 і новіші.</b> Лише коли гравець сам вибрав відеокарту (за замовчуванням «Як вирішить система»,
 * і тоді лаунчер нічого не пише). Перед запуском гри лаунчер записує одне значення в реєстр поточного користувача,
 * без прав адміністратора: {@code HKCU\Software\Microsoft\DirectX\UserGpuPreferences}, назва значення це повний шлях до
 * {@code javaw.exe} або {@code java.exe} цієї збірки, вміст {@code GpuPreference=1;} (інтегрована, «Енергозбереження»)
 * або {@code GpuPreference=2;} (дискретна, «Висока продуктивність»). Рівно те саме записує сторінка Windows
 * «Параметри графіки», коли гравець вибирає там цю програму вручну; запис видно і змінюється там же. Пишеться лише
 * для Java, яку поставив сам лаунчер (у його теці): системну Java не чіпаємо, бо нею можуть запускатись інші
 * програми гравця. Вибір, який гравець зробив для цього файлу в Windows сам, лаунчер не змінює і не видаляє.
 * Повернули «Як вирішить система» або видалили збірки: значення, записане лаунчером, видаляється. Що записано,
 * лаунчер тримає у {@code gpu-prefs.txt} у своїй теці, а кожну дію пише в лог. Windows уміє лише «енергозбереження» і
 * «висока продуктивність», тож на ПК з трьома відеокартами «дискретна» означає ту, яку Windows вважає
 * найпотужнішою.</li>
 * <li><b>Linux.</b> Реєстру немає: для дискретної гра запускається зі змінними середовища PRIME ({@code DRI_PRIME=1},
 * а з фірмовим драйвером NVIDIA {@code __NV_PRIME_RENDER_OFFLOAD=1} і {@code __GLX_VENDOR_LIBRARY_NAME=nvidia}).
 * Вони діють лише на цей запуск гри, у системі нічого не змінюється. Інтегрована на Linux і так стоїть за
 * замовчуванням, змінних для неї не треба. Змінні, які гравець уже задав сам, не перезаписуються.</li>
 * <li><b>macOS.</b> Нічого: на Apple Silicon відеокарта одна, на Intel Mac система сама перемикає OpenGL-гру на
 * дискретну. Картки не видно.</li>
 * </ul>
 * На ПК з однією відеокартою картки не видно взагалі. Ручний спосіб (кнопки «Відкрити параметри графіки» і
 * «Скопіювати шлях до Java») лишився в картці на Windows.
 *
 * <p>Вибір зберігається в {@code InstanceSettings.gpu}: «auto» «Як вирішить система», «integrated», «discrete».
 * Старі збірки мають {@code highPerformanceGpu} (true дискретна, false інтегрована), воно читається, якщо {@code gpu}
 * немає.
 *
 * <p><b>За замовчуванням дискретна</b> (22.11.2026, власник після тікета #97: Project Architect 2 у гравця йшов на Intel UHD
 * поруч з NVIDIA MX130, у SKlauncher на NVIDIA; «якщо є інтегрована та дискретна відеокарта - лаунчер перед запуском ...
 * ЩОРАЗУ перевіряти ... та запускати завжди з дискретної, якщо гравець в налаштуваннях САМ не вкаже інакше»). Тому
 * перед кожним запуском гри список відеокарт читається заново ({@link #refreshAsync()} на початку запуску, поки йде
 * вхід і перевірка файлів), а не раз за запуск лаунчера. «Сам вказав» це вибір у картці або свій вибір для цієї Java на
 * сторінці Windows «Параметри графіки»: його лаунчер, як і раніше, не чіпає.
 */
public final class Gpu {

    private static final Logger log = Logger.getLogger(Gpu.class.getName());

    /** Як вирішить система: лаунчер нічого не пише. */
    public static final int AUTO = 0;
    /** Інтегрована («Енергозбереження» у Windows). */
    public static final int INTEGRATED_GPU = 1;
    /** Дискретна («Висока продуктивність» у Windows). */
    public static final int DISCRETE_GPU = 2;

    /** Розділ реєстру, який пише і сторінка Windows «Параметри графіки». */
    private static final String KEY = "HKCU\\Software\\Microsoft\\DirectX\\UserGpuPreferences";
    /** Що записав лаунчер: рядки «шлях до exe», табуляція, ім'я збірки, табуляція, записане значення. */
    private static final String MARKER = "gpu-prefs.txt";

    /** Окрема відеокарта: NVIDIA (крім віртуальних), AMD Radeon RX і Pro, Intel Arc A і B. */
    private static final Pattern DISCRETE = Pattern.compile(
            "(?i)nvidia (geforce|quadro|rtx|t[0-9]{3,4}|a[0-9]{3,4})|radeon(\\(tm\\))? (rx|pro)|arc(\\(tm\\))? [ab][0-9]{3}");
    /** Вбудована: Intel (UHD, Iris, HD, Arc без номера моделі в Core Ultra), AMD Radeon Graphics і Vega у процесорі. */
    private static final Pattern INTEGRATED = Pattern.compile(
            "(?i)intel.*(uhd|iris|hd graphics|arc(\\(tm\\))? graphics)|amd radeon(\\(tm\\))? (graphics|vega|[0-9]{3,4}m)|radeon(\\(tm\\))? vega");
    /** Рядок lspci з відеокартою: «01:00.0 3D controller: NVIDIA Corporation GA107M [GeForce RTX 3050 Mobile]». */
    private static final Pattern LSPCI = Pattern.compile("(?i)(VGA compatible controller|3D controller|Display controller)[^:]*:\\s*(.+)");

    private static volatile List<String> names;
    /** Свіже читання списку відеокарт для поточного запуску гри ({@link #refreshAsync()}), або null. */
    private static volatile java.util.concurrent.Future<List<String>> fresh;

    private Gpu() {
    }

    /**
     * 22.11.2026: прочитати список відеокарт заново у фоні (на початку кожного запуску гри: гравець міг поставити чи зняти
     * відеокарту, перемкнути режим ноутбука). {@link #adapters()} після цього чекає саме на це читання.
     */
    public static void refreshAsync() {
        if (!isWindows() && !isLinux()) return;
        java.util.concurrent.FutureTask<List<String>> task = new java.util.concurrent.FutureTask<List<String>>(Gpu::detect);
        fresh = task;
        Thread t = new Thread(task, "GPU detect");
        t.setDaemon(true);
        t.start();
    }

    /** Відеокарти ПК: свіже читання цього запуску гри, якщо було, інакше кеш запуску лаунчера. Не з потоку інтерфейсу: ~0,5-1 с. */
    public static List<String> adapters() {
        java.util.concurrent.Future<List<String>> f = fresh;
        if (f != null) {
            try {
                List<String> got = f.get(15, TimeUnit.SECONDS);
                fresh = null;
                return names = got;
            } catch (Exception e) {
                log.info("GPU list refresh failed: " + e);
                fresh = null;
            }
        }
        List<String> n = names;
        if (n != null) return n;
        return names = detect();
    }

    private static List<String> detect() {
        List<String> out = new ArrayList<String>();
        try {
            if (isWindows()) {
                for (String l : run(10, "powershell", "-NoProfile", "-NonInteractive", "-Command",
                        "(Get-CimInstance Win32_VideoController).Name")) {
                    if (!l.trim().isEmpty()) out.add(l.trim());
                }
            } else if (isLinux()) {
                for (String l : run(10, "lspci")) {
                    Matcher m = LSPCI.matcher(l);
                    if (m.find()) out.add(m.group(2).trim());
                }
            }
        } catch (Exception e) {
            log.info("GPU list unavailable: " + e);
        }
        log.info("GPUs: " + out);
        return Collections.unmodifiableList(out);
    }

    /** Окрема відеокарта, якщо поруч є і вбудована (дві відеокарти), інакше null. */
    public static String discreteOfHybrid() {
        List<String> all = adapters();
        if (isLinux()) {
            // lspci пише виробника і чип, а не маркетингову назву: дві і більше відеокарт уже гібрид; окрема це NVIDIA,
            // якщо є, інакше друга (DRI_PRIME=1 і так бере не основну)
            if (all.size() < 2) return null;
            for (String a : all) if (a.toLowerCase(Locale.ROOT).contains("nvidia")) return a;
            return all.get(1);
        }
        String discrete = null;
        boolean integrated = false;
        for (String a : all) {
            if (DISCRETE.matcher(a).find()) {
                if (discrete == null) discrete = a;
            } else if (INTEGRATED.matcher(a).find()) {
                integrated = true;
            }
        }
        return integrated ? discrete : null;
    }

    /** Вбудована відеокарта гібридної системи (для списку в картці), або null. */
    public static String integratedOfHybrid() {
        String d = discreteOfHybrid();
        if (d == null) return null;
        for (String a : adapters()) {
            if (a.equals(d)) continue;
            if (isLinux() || (!DISCRETE.matcher(a).find() && INTEGRATED.matcher(a).find())) return a;
        }
        return null;
    }

    /** Чи є вибір на цій системі (Windows або Linux; на macOS картки немає). */
    public static boolean choiceSupported() {
        return isWindows() || isLinux();
    }

    /** Вибір для збірки: {@link #AUTO}, {@link #INTEGRATED_GPU} або {@link #DISCRETE_GPU} (за замовчуванням, 22.11.2026). */
    public static int choice(Instance instance) {
        InstanceSettings s = instance == null ? null : instance.getSettings();
        String g = s == null ? null : s.getGpu();
        if ("auto".equals(g)) return AUTO;
        if ("integrated".equals(g)) return INTEGRATED_GPU;
        if ("discrete".equals(g)) return DISCRETE_GPU;
        Boolean b = s == null ? null : s.getHighPerformanceGpu(); // збірки до 22.11.2026
        if (b != null) return b ? DISCRETE_GPU : INTEGRATED_GPU;
        return DISCRETE_GPU;
    }

    /** Зберегти вибір у налаштуваннях збірки (сам instance.json зберігає той, хто кличе). */
    public static void setChoice(Instance instance, int choice) {
        if (instance.getSettings() == null) instance.setSettings(new InstanceSettings());
        instance.getSettings().setGpu(choice == DISCRETE_GPU ? "discrete" : choice == INTEGRATED_GPU ? "integrated" : "auto");
        instance.getSettings().setHighPerformanceGpu(null);
    }

    // ------------------------------------------------------------------ перед запуском

    /**
     * Перед запуском гри (Runner). Запам'ятати Java збірки для ручного способу і, якщо гравець вибрав відеокарту,
     * попросити систему дати її грі (див. опис класу). Будь-яка помилка лише в лог: гра запускається в будь-якому
     * разі.
     *
     * @param baseDir    тека лаунчера
     * @param javaBinary перший елемент команди запуску (шлях до java, часто без .exe)
     * @param env        змінні середовища процесу гри
     */
    public static void beforeLaunch(File baseDir, Instance instance, String javaBinary, Map<String, String> env) {
        if (instance == null || javaBinary == null) return;
        File exe = exeOf(javaBinary);
        try {
            if (isWindows() && exe != null) rememberJava(instance, exe);
            int c = choice(instance);
            if (isWindows()) {
                // «Як вирішить система»: прибрати те, що лаунчер міг записати раніше (нічого, якщо записів немає)
                if (c == AUTO) forget(baseDir, instance);
                else if (exe != null && discreteOfHybrid() != null) applyWindows(baseDir, instance, exe, c);
                else forget(baseDir, instance); // 22.11.2026: відеокарта тепер одна, запис лаунчера більше не потрібен
            } else if (isLinux() && c == DISCRETE_GPU && discreteOfHybrid() != null) {
                applyLinux(env);
            }
        } catch (Exception e) {
            log.info("GPU preference not applied: " + e);
        }
    }

    private static void applyWindows(File baseDir, Instance instance, File exe, int choice) throws Exception {
        if (!isUnder(exe, baseDir)) {
            log.info("GPU: " + exe + " is not the launcher's own Java, Windows settings are left as they are");
            return;
        }
        String want = "GpuPreference=" + choice + ";";
        String path = exe.getAbsolutePath();
        String now = query(path);
        List<String[]> marks = readMarks(baseDir);
        String ours = lastWritten(marks, path);
        if (now != null && !now.equals(ours)) {
            // для цього файлу вже є вибір, якого лаунчер не писав: його зробив гравець у «Параметрах графіки»
            log.info("GPU: Windows already has \"" + now + "\" for " + path + " (player's own choice), not changed");
            return;
        }
        if (!want.equals(now)) {
            int code = reg("add", KEY, "/v", path, "/t", "REG_SZ", "/d", want, "/f");
            log.info("GPU: wrote " + KEY + " \"" + path + "\" = " + want + " for the "
                    + (choice == DISCRETE_GPU ? "discrete" : "integrated") + " graphics card for " + instance.getName()
                    + (instance.getSettings() != null && instance.getSettings().getGpu() != null ? "" : " (launcher default)")
                    + " (exit " + code + ")");
            if (code != 0) return;
        }
        boolean has = false;
        for (String[] m : marks) {
            if (!m[0].equalsIgnoreCase(path)) continue;
            m[2] = want; // те саме значення для всіх збірок з цією Java
            if (m[1].equals(instance.getName())) has = true;
        }
        if (!has) marks.add(new String[]{path, instance.getName(), want});
        writeMarks(baseDir, marks);
    }

    private static void applyLinux(Map<String, String> env) {
        String d = discreteOfHybrid();
        boolean nvidiaBlob = d != null && d.toLowerCase(Locale.ROOT).contains("nvidia")
                && new File("/proc/driver/nvidia/version").isFile();
        if (nvidiaBlob) {
            // фірмовий драйвер NVIDIA (PRIME render offload); з nouveau ці змінні зламали б GLX, тому лише з ним
            putIfAbsent(env, "__NV_PRIME_RENDER_OFFLOAD", "1");
            putIfAbsent(env, "__GLX_VENDOR_LIBRARY_NAME", "nvidia");
            putIfAbsent(env, "__VK_LAYER_NV_optimus", "NVIDIA_only");
        } else {
            putIfAbsent(env, "DRI_PRIME", "1");
        }
        log.info("GPU: game started with PRIME variables for " + d + " (nvidia driver: " + nvidiaBlob + ")");
    }

    private static void putIfAbsent(Map<String, String> env, String k, String v) {
        if (!env.containsKey(k)) env.put(k, v);
    }

    // ------------------------------------------------------------------ прибрати

    /**
     * «Як вирішить система»: прибрати значення реєстру, яке лаунчер записав для Java цієї збірки, якщо його не
     * потребує інша збірка з вибраною відеокартою і гравець не змінив його сам. Не з потоку інтерфейсу.
     */
    public static void forget(File baseDir, Instance instance) {
        if (!isWindows() || instance == null) return;
        try {
            List<String[]> marks = readMarks(baseDir);
            List<String[]> mine = new ArrayList<String[]>();
            for (java.util.Iterator<String[]> it = marks.iterator(); it.hasNext(); ) {
                String[] m = it.next();
                if (m[1].equals(instance.getName())) {
                    mine.add(m);
                    it.remove();
                }
            }
            if (mine.isEmpty()) return;
            for (String[] p : mine) {
                if (lastWritten(marks, p[0]) != null) {
                    log.info("GPU: " + p[0] + " is still used by another build with a chosen graphics card, kept");
                } else {
                    remove(p[0], p[2]);
                }
            }
            writeMarks(baseDir, marks);
        } catch (Exception e) {
            log.info("GPU preference not removed: " + e);
        }
    }

    /** Видалення збірок чи всього лаунчера: прибрати всі значення, записані лаунчером. */
    public static void forgetAll(File baseDir) {
        if (!isWindows()) return;
        try {
            List<String> done = new ArrayList<String>();
            for (String[] m : readMarks(baseDir)) {
                if (done.contains(m[0].toLowerCase(Locale.ROOT))) continue;
                done.add(m[0].toLowerCase(Locale.ROOT));
                remove(m[0], m[2]);
            }
            Files.deleteIfExists(new File(baseDir, MARKER).toPath());
        } catch (Exception e) {
            log.info("GPU preferences not removed: " + e);
        }
    }

    private static void remove(String path, String written) throws Exception {
        String now = query(path);
        if (now == null) return;
        if (!now.equals(written)) {
            log.info("GPU: player changed the value for " + path + " in Windows to \"" + now + "\", kept");
            return;
        }
        int code = reg("delete", KEY, "/v", path, "/f");
        log.info("GPU: removed " + KEY + " \"" + path + "\" written by the launcher (exit " + code + ")");
    }

    /** Що лаунчер востаннє записав для цього exe, або null, якщо нічого. */
    private static String lastWritten(List<String[]> marks, String path) {
        for (String[] m : marks) if (m[0].equalsIgnoreCase(path)) return m[2];
        return null;
    }

    // ------------------------------------------------------------------ ручний спосіб

    /** Файл у теці збірки, де записано, якою Java вона запускалась востаннє. */
    private static File lastJavaFile(Instance instance) {
        return new File(new File(instance.getContentDir(), ".gummer"), "last-java.txt");
    }

    private static void rememberJava(Instance instance, File exe) {
        try {
            File f = lastJavaFile(instance);
            f.getParentFile().mkdirs();
            Files.write(f.toPath(), exe.getAbsolutePath().getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            log.info("Last Java not saved: " + e);
        }
    }

    /** Яким exe збірка запускалась востаннє, або null (ще не запускалась). */
    public static File lastJava(Instance instance) {
        try {
            File f = lastJavaFile(instance);
            if (!f.isFile()) return null;
            File exe = new File(new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8).trim());
            return exe.isFile() ? exe : null;
        } catch (Exception e) {
            return null;
        }
    }

    /** Відкрити сторінку Windows «Параметри графіки». @return false, якщо не вдалося */
    public static boolean openGraphicsSettings() {
        if (!isWindows()) return false;
        try {
            new ProcessBuilder("explorer.exe", "ms-settings:display-advancedgraphics").start();
            return true;
        } catch (Exception e) {
            log.info("Graphics settings not opened: " + e);
            return false;
        }
    }

    // ------------------------------------------------------------------ дрібне

    private static File exeOf(String javaBinary) {
        File exe = new File(javaBinary);
        if (!exe.isFile()) exe = new File(javaBinary + ".exe");
        return exe.isFile() ? exe.getAbsoluteFile() : null;
    }

    private static boolean isUnder(File f, File dir) {
        try {
            String a = f.getCanonicalPath().toLowerCase(Locale.ROOT);
            String b = dir.getCanonicalPath().toLowerCase(Locale.ROOT) + File.separator;
            return a.startsWith(b);
        } catch (Exception e) {
            return false;
        }
    }

    /** Поточне значення для exe, або null, якщо його немає. */
    private static String query(String path) throws Exception {
        List<String> out = new ArrayList<String>();
        int code = reg(out, "query", KEY, "/v", path);
        if (code != 0) return null;
        for (String l : out) {
            int i = l.indexOf("REG_SZ");
            if (i >= 0) return l.substring(i + 6).trim();
        }
        return null;
    }

    private static int reg(String... args) throws Exception {
        return reg(null, args);
    }

    /** reg.exe з System32 за повним шляхом (не з PATH). */
    private static int reg(List<String> out, String... args) throws Exception {
        String root = System.getenv("SystemRoot");
        File exe = new File(root == null ? "C:\\Windows" : root, "System32\\reg.exe");
        List<String> cmd = new ArrayList<String>();
        cmd.add(exe.isFile() ? exe.getAbsolutePath() : "reg");
        Collections.addAll(cmd, args);
        Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) if (out != null) out.add(line);
        }
        if (!p.waitFor(10, TimeUnit.SECONDS)) {
            p.destroyForcibly();
            return -1;
        }
        return p.exitValue();
    }

    private static List<String> run(int seconds, String... cmd) throws Exception {
        List<String> out = new ArrayList<String>();
        Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) out.add(line);
        }
        if (!p.waitFor(seconds, TimeUnit.SECONDS)) p.destroyForcibly();
        return out;
    }

    private static synchronized List<String[]> readMarks(File baseDir) {
        List<String[]> out = new ArrayList<String[]>();
        File f = new File(baseDir, MARKER);
        if (!f.isFile()) return out;
        try {
            for (String l : Files.readAllLines(f.toPath(), StandardCharsets.UTF_8)) {
                String[] p = l.split("\t", -1);
                if (p.length >= 2 && !p[0].isEmpty()) {
                    // тестові збірки 19.11 писали лише шлях і збірку (тоді лише «Висока продуктивність»)
                    out.add(new String[]{p[0], p[1], p.length >= 3 && !p[2].isEmpty() ? p[2] : "GpuPreference=2;"});
                }
            }
        } catch (Exception e) {
            log.info("GPU marks unreadable: " + e);
        }
        return out;
    }

    private static synchronized void writeMarks(File baseDir, List<String[]> marks) throws Exception {
        File f = new File(baseDir, MARKER);
        if (marks.isEmpty()) {
            Files.deleteIfExists(f.toPath());
            return;
        }
        StringBuilder sb = new StringBuilder();
        for (String[] m : marks) sb.append(m[0]).append('\t').append(m[1]).append('\t').append(m[2]).append('\n');
        Files.write(f.toPath(), sb.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static boolean isWindows() {
        return com.skcraft.launcher.util.Environment.getInstance().getPlatform() == com.skcraft.launcher.util.Platform.WINDOWS;
    }

    private static boolean isLinux() {
        return com.skcraft.launcher.util.Environment.getInstance().getPlatform() == com.skcraft.launcher.util.Platform.LINUX;
    }
}
