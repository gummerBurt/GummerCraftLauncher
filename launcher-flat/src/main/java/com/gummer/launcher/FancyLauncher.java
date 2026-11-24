/*
 * Copyright (C) 2021-2025 GummerCraft <http://gummercraft.fun>
 */

package com.gummer.launcher;

import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.themes.FlatMacDarkLaf;
import com.formdev.flatlaf.util.SystemInfo;
import com.google.common.base.Supplier;
import com.google.common.io.ByteStreams;
import com.gummer.launcher.swing.SwingExtras;
import com.skcraft.launcher.Launcher;
import com.skcraft.launcher.dialog.ConsoleFrame;
import com.skcraft.launcher.swing.SwingHelper;
import lombok.SneakyThrows;
import lombok.extern.java.Log;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.logging.Level;

import static com.skcraft.launcher.util.SharedLocale.tr;

@Log
public class FancyLauncher {

    public static void main(final String[] args) {
        // ПЕРШИМ ділом, до будь-якого класу FlatLaf: на Windows FlatLaf вантажить свою DLL уже при першому зверненні до
        // com.formdev.flatlaf.util.SystemInfo, тож flatlaf.nativeLibraryPath має бути виставлений раніше (інакше DLL іде в %TEMP%).
        useNativesFromLauncherDir(args);
        UiScaleFit.applySaved(baseDirFromArgs(args)); // теж до AWT: збережений масштаб для малого екрана (1.32)
        SwingExtras.enforceMacDefaults(); // tune and enforce macos defaults
        Launcher.setupLogger();
        // 17.11.2026: картинки з потоків (іконки Modrinth і модів, скіни, новини) читати з пам'яті, а не через тимчасовий
        // файл на диску: розпаковувач WebP читав його дрібними шматками, 20 іконок їли 1.6 с процесора, у 6 потоках
        // на мить усі ядра (вікно підвисало, шумів вентилятор). З пам'яті 46 мс
        javax.imageio.ImageIO.setUseCache(false);
        UiScaleFit.fitOrRelaunch(baseDirFromArgs(args)); // вікно не влазить у екран: інший масштаб і перезапуск
        // 18.11.2026: сторож затупів потоку інтерфейсу, лише в тестовому лаунчері (тека даних «...-test»)
        com.gummer.launcher.perf.EdtWatch.installIfTest(baseDirFromArgs(args));
        // 18.11.2026: фони і логотипи збірок розпаковуються у фоні паралельно зі створенням вікна (раніше перше
        // звернення вигляду, а Бібліотека читала обкладинки сама в потоці інтерфейсу)
        com.gummer.launcher.look.FrameImages.warmUp();

        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                try {
                    Thread.currentThread().setContextClassLoader(FancyLauncher.class.getClassLoader());
                    UIManager.getLookAndFeelDefaults().put("ClassLoader", FancyLauncher.class.getClassLoader());

                    Launcher launcher = Launcher.createFromArguments(args);
                    SwingHelper.setSwingProperties(tr("launcher.appTitle", launcher.getVersion()));

                    try {
                        // 20.11.2026 (власник: «шрифти наче зжаті ... в очах мерехтить»): шрифт інтерфейсу Inter замість
                        // системного Segoe UI. Java малює Segoe UI власним растеризатором, і на масштабі 125% він виходить
                        // щільним і важким; Inter зроблений для екранів і в Java рівний. Повернути Segoe: -Dgummer.font=segoe
                        // 1.36.6: наш Inter у TrueType (look.FontCheck.installInter), лише якщо в системі немає свого «Inter» і розміри збігаються,
                        // інакше Segoe UI, як до 1.36.5 (після 1.36.5 у власника на двох машинах текст був удвічі більший)
                        String fontChoice;
                        if( "segoe".equalsIgnoreCase( System.getProperty( "gummer.font" ) ) ) { fontChoice = "Segoe UI (gummer.font=segoe)"; System.setProperty( "gummer.font.used", "segoe" ); }
                        else if( com.gummer.launcher.look.FontCheck.systemHasInter() ) { fontChoice = "Segoe UI (system has its own Inter)"; System.setProperty( "gummer.font.used", "sysinter" ); }
                        else if( !com.gummer.launcher.look.FontCheck.installInter() ) { fontChoice = "Segoe UI (Inter not registered)"; System.setProperty( "gummer.font.used", "noreg" ); }
                        else {
                            setInter( true );
                            fontChoice = "Inter";
                            System.setProperty( "gummer.font.used", "inter" );
                        }
                        // init and setup UI LaF
                        UIManager.setLookAndFeel( new FlatMacDarkLaf() );
                        if( "Inter".equals( fontChoice ) ) {
                            String bad = com.gummer.launcher.look.FontCheck.interMismatch();
                            if( bad != null ) {
                                setInter( false );
                                UIManager.setLookAndFeel( new FlatMacDarkLaf() );
                                fontChoice = "Segoe UI (Inter mismatch: " + bad + ")";
                                System.setProperty( "gummer.font.used", "mismatch" );
                                com.gummer.launcher.stats.LaunchStats.fontFallback( launcher, "mismatch" );
                            }
                        }
                        com.gummer.launcher.look.FontCheck.logChoice( fontChoice );
                    } catch( Exception ex ) {
                        System.err.println( "Failed to initialize UI LaF" );
                        //ex.printStackTrace();
                    }

                    enforcePlatformDefaults();
                    enforceUIManagerDefaults();
                    com.gummer.launcher.look.EscCloses.install(); // 20.11.2026: Esc закриває вікна-діалоги, як хрестик
                    com.gummer.launcher.look.FocusVisible.install(); // 20.11.2026: рамка фокусу лише після Tab і стрілок
                    com.gummer.launcher.look.PadNav.install(); // 21.11.2026: стрілки між кнопками, Enter натискає (геймпад Steam Deck)
                    // 20.11.2026 (власник: «всюди "скасувати"»): стандартні кнопки вікон підтвердження Java теж з мови
                    // лаунчера (українського перекладу в самій Java немає, і там були англійські Cancel, Yes, No)
                    UIManager.put("OptionPane.cancelButtonText", tr("button.cancel"));
                    UIManager.put("OptionPane.okButtonText", tr("button.ok"));
                    UIManager.put("OptionPane.yesButtonText", tr("dialogYes"));
                    UIManager.put("OptionPane.noButtonText", tr("dialogNo"));

                    launcher.setMainWindowSupplier(new CustomWindowSupplier(launcher));
                    // 17.11.2026 (власник: «відкриваєш лаунчер в повноекранному - і він не відкривається повністю ...
                    // контент наче трохи "зсувається"»): вікно добудовується невидимим (розкладка, події розміру, лого
                    // Бібліотеки) і показується вже готовим, через два проходи черги подій (без помітної затримки)
                    Window first = launcher.getMainWindowSupplier().get();
                    first.validate();
                    SwingUtilities.invokeLater(() -> SwingUtilities.invokeLater(() -> first.setVisible(true)));
                    prewarmOptions(launcher, first);
                    // 24.11.2026 (1.36.16): оновлення Java лаунчера і заміна непідписаного exe (installer.json). Виклик
                    // стояв лише в Launcher.main, а лаунчер стартує тут, тож з 1.36.2 автоматично це не запускалось ніколи
                    // (працювала лише кнопка «Оновити» в плитці «Оновлення Java»)
                    com.gummer.launcher.util.LauncherJreUpdate.start(launcher, first);
                    // 21.11.2026: статус у Discord (тихо нічого не робить, якщо Discord не запущено)
                    com.gummer.launcher.discord.DiscordPresence.start(launcher);

                    // show launcher console if config option enabled
                    if (launcher.getConfig().isShowLauncherConsole()) {
                        ConsoleFrame.showMessagesColor(launcher.getConfig().isConsoleUseColors());
                    }
                } catch (Throwable t) {
                    log.log(Level.WARNING, "Load failure", t);
                    SwingHelper.showStartupError(t);
                }
            }
        });
    }

    private static class CustomWindowSupplier implements Supplier<Window> {

        private final Launcher launcher;

        private CustomWindowSupplier(Launcher launcher) {
            this.launcher = launcher;
        }

        @SneakyThrows
        @Override
        public Window get() {
            // 16.11.2026: вигляд головного вікна з налаштувань (Liquid Glass, Класика, Бібліотека). Вікно і так
            // створюється наново після кожної гри, тож перемикач у налаштуваннях просто перевідкриває його.
            String look = launcher.getConfig().getLauncherLook();
            // 16.11.2026: свій стиль вікон і сповіщень для кожного вигляду (look.LookTheme, look.Toast)
            com.gummer.launcher.look.LookTheme.apply(look);
            com.gummer.launcher.ui.UiHooks.toast = com.gummer.launcher.look.Toast::show;
            if (LOOK_CLASSIC.equals(look)) return new FlatLauncherFrame2(launcher);
            if (LOOK_LIBRARY.equals(look)) return new FlatLauncherFrame3(launcher);
            return new FlatLauncherFrame(launcher);
        }
    }

    /** Значення {@code Configuration.launcherLook}. */
    public static final String LOOK_GLASS = "glass", LOOK_CLASSIC = "classic", LOOK_LIBRARY = "library";

    /** Закрити поточне головне вікно і відкрити нове (після зміни вигляду в налаштуваннях). */
    public static void reopenLauncherWindow(Launcher launcher, Window current) {
        SwingUtilities.invokeLater(() -> {
            quickReopen = true; // нове вікно візьме вже завантажений список збірок (див. takeQuickReopen)
            reopenFrom = current;
            Window next;
            try {
                next = launcher.getMainWindowSupplier().get();
            } finally {
                quickReopen = false;
                reopenFrom = null;
            }
            // 17.11.2026 (власник: «воно наче "підгружає" ще щось через що все з'їзджає коли перемикаєш дизайни»):
            // кадри вікна показали, що нове вікно з'являлось недоробленим (без виділеної збірки, панелі версії, лого) і
            // за 70-130 мс добудовувалось. Тепер воно добудовується невидимим, а старе тим часом лишається на екрані.
            next.validate();
            Timer show = new Timer(REOPEN_SETTLE_MS, e -> {
                next.setVisible(true);
                if (current != null) current.dispose(); // старе закриваємо після показу нового: без миготіння
                next.toFront();
                next.requestFocus();
            });
            show.setRepeats(false);
            show.start();
        });
    }

    /**
     * 16.11.2026: перше відкриття налаштувань чекало ~0.3 с (завантаження класів Swing/FlatLaf, пошук Java), далі
     * ~0.03 с. Через 2.5 с після старту один раз будуємо діалог невидимим і викидаємо, тож і перше відкриття швидке.
     */
    private static void prewarmOptions(Launcher launcher, Window owner) {
        if (!(owner instanceof JFrame)) return;
        // 18.11.2026: сторож EdtWatch показав, що це ~0,28 с у потоці інтерфейсу через 2,5 с після старту, якраз коли
        // гравець уже клацає. Тепер чекає тиші: 1,5 с без руху миші і клавіш (перевірка раз на 0,5 с, до 60 с).
        final long started = System.currentTimeMillis();
        // 19.11.2026: список Java для налаштувань рахується у фоні (раніше ~0,24 с з цих ~0,28 с у потоці інтерфейсу)
        com.skcraft.launcher.launch.runtime.JavaRuntimeFinder.prefetch();
        // 19.11.2026: список відеокарт (картка «Відеокарта» у вікні «Збірка» і запуск гри), PowerShell ~1 с
        Thread gpus = new Thread(com.gummer.launcher.util.Gpu::adapters, "GPU list");
        gpus.setDaemon(true);
        gpus.start();
        Timer t = new Timer(500, null);
        t.setInitialDelay(2500);
        t.addActionListener(e -> {
            if (com.gummer.launcher.look.PackPrewarm.idleMs() < 1500 && System.currentTimeMillis() - started < 60_000) return;
            t.stop();
            try {
                if (!owner.isShowing()) return;
                com.gummer.launcher.dialog.ConfigurationDialogFlat d =
                        new com.gummer.launcher.dialog.ConfigurationDialogFlat(owner, launcher, (JFrame) owner);
                d.pack();
                d.dispose();
            } catch (Throwable ex) {
                log.log(Level.FINE, "Options prewarm failed", ex);
            }
        });
        t.start();
    }

    private static volatile boolean quickReopen;
    /** Скільки нове вікно добудовується невидимим під час перемикання вигляду (картинки, вибір збірки). */
    private static final int REOPEN_SETTLE_MS = 300;
    private static volatile Window reopenFrom;

    /** Вікно, яке зараз замінюється новим (перемикання вигляду), інакше null. */
    public static Window reopeningFrom() {
        return reopenFrom;
    }

    /** true один раз, якщо вікно створюється перемиканням вигляду (список збірок уже є, перезавантажувати не треба). */
    public static boolean takeQuickReopen() {
        boolean q = quickReopen;
        quickReopen = false;
        return q;
    }

    /**
     * FlatLaf розпаковує свою невелику службову DLL (рамка вікна) у %TEMP% при кожному запуску, якщо {@code flatlaf.nativeLibraryPath} не вказує на папку,
     * де вона вже лежить. Тримаємо її в папці лаунчера (&lt;--dir&gt;/launcher/natives), як звичайна програма, - нічого виконуваного в тимчасову папку.
     * Викликається першим рядком main і НЕ чіпає класів FlatLaf (SystemInfo сам вантажить DLL): ОС і архітектура - з os.name/os.arch, сама DLL -
     * ресурсом із jar. Будь-яка помилка - FlatLaf просто працює як раніше.
     */
    private static void useNativesFromLauncherDir(String[] args) {
        if (!System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).startsWith("windows")) {
            return;
        }
        File baseDir = baseDirFromArgs(args);
        if (baseDir == null) return;

        String arch = System.getProperty("os.arch", "").toLowerCase(java.util.Locale.ROOT);
        String classifier = arch.contains("aarch64") || arch.contains("arm64") ? "windows-arm64"
                : (arch.contains("64") ? "windows-x86_64" : "windows-x86");
        String name = "flatlaf-" + classifier + ".dll";
        File dir = new File(baseDir, "launcher/natives");
        File target = new File(dir, name);
        try (InputStream in = FancyLauncher.class.getClassLoader().getResourceAsStream("com/formdev/flatlaf/natives/" + name)) {
            if (in == null) {
                return;
            }
            byte[] bundled = ByteStreams.toByteArray(in);
            if (!target.isFile() || !Arrays.equals(bundled, Files.readAllBytes(target.toPath()))) {
                dir.mkdirs();
                File tmp = new File(dir, name + ".tmp");
                Files.write(tmp.toPath(), bundled);
                Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            System.setProperty("flatlaf.nativeLibraryPath", dir.getAbsolutePath());
        } catch (IOException | RuntimeException e) {
            System.err.println("Could not place FlatLaf native library in " + dir + ": " + e);
        }
    }

    /** Тека даних з аргументу --dir (його передає бутстрапер), або null. */
    private static File baseDirFromArgs(String[] args) {
        File baseDir = null;
        for (int i = 0; i + 1 < args.length; i++) {
            if ("--dir".equals(args[i])) baseDir = new File(args[i + 1]);
        }
        return baseDir;
    }

    /** Шрифт Inter для FlatLaf увімкнути чи вимкнути (до setLookAndFeel). 1.36.6 */
    private static void setInter( boolean on ) {
        if( on ) System.setProperty( "gummer.font.inter", "true" ); // для розмірів у HTML (InstanceTableModel.fs)
        else System.clearProperty( "gummer.font.inter" );
        com.formdev.flatlaf.FlatLaf.setPreferredFontFamily( on ? "Inter" : null );
        com.formdev.flatlaf.FlatLaf.setPreferredLightFontFamily( on ? "Inter Light" : null );
        com.formdev.flatlaf.FlatLaf.setPreferredSemiboldFontFamily( on ? "Inter SemiBold" : null );
    }

    public static void enforcePlatformDefaults() {
        if( SystemInfo.isWindows ) {
            System.setProperty( "flatlaf.useWindowDecorations", "true" );
            System.setProperty( "JRootPane.useWindowDecorations", "true" );
            System.setProperty( "flatlaf.menuBarEmbedded", "true" );
            System.setProperty( "JRootPane.menuBarEmbedded", "true" );
        }
        // 19.11.2026: у спрощеному режимі (look.LookTheme.LITE) рамка вікна на Linux системна: намальована лаунчером
        // перемальовує все вікно при перетягуванні і на частині Wayland/XWayland та Steam Deck поводиться дивно
        if( SystemInfo.isLinux && !com.gummer.launcher.look.LookTheme.LITE ) {
            JFrame.setDefaultLookAndFeelDecorated( true );
            JDialog.setDefaultLookAndFeelDecorated( true );
        }
        // 20.11.2026: на Windows згладжування як у системі (ClearType). Примусове сіре ("on") разом з масштабом 125%
        // стискало Segoe UI 12.5-13 px і робило нерівні відступи між літерами. На Linux без цього тексту згладжування
        // може не бути зовсім, тож там лишаємо; гравець, що задав своє значення, теж не чіпаємо.
        if( !SystemInfo.isWindows && System.getProperty("awt.useSystemAAFontSettings") == null )
            System.setProperty("awt.useSystemAAFontSettings", "on");
        System.setProperty("swing.aatext", "true");
    }

    public static void enforceUIManagerDefaults() {
        UIManager.getDefaults().put("SplitPane.border", BorderFactory.createEmptyBorder());
        com.gummer.launcher.look.TextFit.install(); // 22.11.2026: написи не обрізаються з масштабом Windows 125-150%

        UIManager.put( "ProgressBar.arc", 0 );
        UIManager.put( "TextComponent.arc", 5 );
        UIManager.put( "Component.arc", 5 );
        UIManager.put( "Button.arc", 5 );

        UIManager.put( "Button.innerFocusWidth", 0 );

        UIManager.put( "ComboBox.buttonStyle", "button" );
        //UIManager.put( "ComboBox.foreground", SwingExtras.hexToColor("#282828"));
        UIManager.put( "ComboBox.background", SwingExtras.hexToColor("#282828"));
        //UIManager.put( "ComboBox.selectionInsets", "0,0,0,0" );
        //UIManager.put( "ComboBox.popupInsets", "0,0,0,0" );
        //UIManager.put( "ComboBox.selectionInsets", new Insets( 0, 0, 0, 0 ));
        //UIManager.put( "ComboBox.popupInsets", "0,0,0,0" );

        //UIManager.put( "PopupMenu.borderInsets", new Insets( 0, 0, 0, 0 ));

        UIManager.put( "Component.arrowType", "chevron" );
        UIManager.put( "Component.focusWidth", 0 );
        UIManager.put( "Component.innerFocusWidth", 0 );

        UIManager.put( "ScrollBar.showButtons", false );
        UIManager.put( "ScrollBar.thumbInsets", new Insets( 2, 2, 2, 2 ));
        UIManager.put( "ScrollBar.width", 6 );

        UIManager.put("ToolBar.background", SwingExtras.hexToColor("#262626"));
        UIManager.put("ToolBar.hoverButtonGroupBackground", SwingExtras.hexToColor("#262626"));
        UIManager.put( "Button.toolbar.selectedForeground", SwingExtras.hexToColor("#FFFFFF"));
        UIManager.put( "Button.toolbar.pressedForeground", SwingExtras.hexToColor("#FFFFFF"));
        //UIManager.put( "Button.toolbar.pressedBackground", "" );
        //UIManager.put( "Button.toolbar.hoverBackground", "" );

        //UIManager.put( "Button.toolbar.margin", "5,5,5,5" );
        //UIManager.put( "ToolBar.borderMargins", "10,10,10,10" );
        //UIManager.put( "ToolBar.focusableButtons", false );
    }

}
