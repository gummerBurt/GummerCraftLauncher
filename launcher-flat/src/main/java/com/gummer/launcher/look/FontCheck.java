package com.gummer.launcher.look;

import javax.swing.UIManager;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.awt.Toolkit;
import java.awt.font.FontRenderContext;
import java.io.InputStream;
import java.util.Locale;
import java.util.logging.Logger;

/**
 * 20.11.2026, 1.36.6 (власник після 1.36.5: на ноутбуці 3.2K і на FullHD весь текст лаунчера приблизно вдвічі більший
 * за верстку, «Встан…», «Скас…», «Акау…»; «Це сталось після сьогоднішнього апдейту»). У 1.36.5 з'явився шрифт Inter
 * (FlatLaf шукає його за назвою родини). На ПК розробки так не відтворилось ні масштабом Java, ні «розміром тексту»
 * Windows, ні масштабом FlatLaf, а збільшений лише текст (і HTML з явними розмірами, і власні написи), тобто сам
 * шрифт. Тому Inter вмикається лише коли:
 * <ul>
 *   <li>у системі немає свого шрифту з назвою «Inter» (інакше Java бере системний, а не наш з лаунчера);</li>
 *   <li>після встановлення оформлення розміри шрифту інтерфейсу збігаються з нашим файлом Inter-Regular.otf
 *   того ж кегля (ширина зразка і висота великих літер у межах 10%).</li>
 * </ul>
 * Інакше лишається системний Segoe UI, як до 1.36.5. Рядок «UI font: ...» у лозі показує, що вибрано і чому.
 */
public final class FontCheck {

    private static final Logger log = Logger.getLogger(FontCheck.class.getName());
    private static final String SAMPLE = "Встановлені Скасувати HxgЖ";
    private static final FontRenderContext FRC = new FontRenderContext(null, true, true);

    private FontCheck() {
    }

    /** Накреслення Inter у ресурсах лаунчера (TrueType, перегнані з Inter 4.0 OTF скриптом tools/inter-otf2ttf.py). */
    private static final String[] FILES = {"Inter-Regular.ttf", "Inter-Bold.ttf", "Inter-SemiBold.ttf", "Inter-Light.ttf"};
    private static final String DIR = "/com/gummer/launcher/fonts/";

    /**
     * 1.36.6: зареєструвати наш Inter у Java (родини «Inter», «Inter SemiBold», «Inter Light»). Раніше брали Inter з
     * flatlaf-fonts-inter, а там OpenType з CFF (2048 одиниць на em); Oracle Java 8 на Windows малює CFF своїм старим
     * растеризатором, і текст виходив приблизно у 2 рази більшим (так було на обох машинах власника після 1.36.5,
     * старий Gummer_Craft_launcher.exe на Oracle Java 1.8.0_511). Ті самі шрифти у TrueType Java 8 малює як звичайні
     * системні (Segoe UI теж TrueType). true, якщо зареєстровано основне накреслення.
     */
    public static boolean installInter() {
        boolean regular = false;
        GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
        for (String name : FILES) {
            try (InputStream in = FontCheck.class.getResourceAsStream(DIR + name)) {
                if (in == null) continue;
                boolean ok = ge.registerFont(Font.createFont(Font.TRUETYPE_FONT, in));
                if (ok && name.equals(FILES[0])) regular = true;
            } catch (Exception e) {
                log.info("UI font: cannot load " + name + ": " + e);
            }
        }
        return regular;
    }

    /** Чи є в системі свій шрифт з родиною «Inter» (викликати до installInter). */
    public static boolean systemHasInter() {
        try {
            Font f = new Font("Inter", Font.PLAIN, 12);
            return "Inter".equalsIgnoreCase(f.getFamily(Locale.ROOT));
        } catch (RuntimeException e) {
            return false;
        }
    }

    /**
     * Порівняння шрифту інтерфейсу (defaultFont) з нашим Inter-Regular.otf того ж кегля. null: збігається (або це
     * не Inter, тоді нічого перевіряти); інакше опис розбіжності для логу.
     */
    public static String interMismatch() {
        Font ui = uiFont();
        if (ui == null || !ui.getFamily(Locale.ROOT).toLowerCase(Locale.ROOT).startsWith("inter")) return null;
        Font ref = bundledInter(ui.getSize2D());
        if (ref == null) return null; // без еталона не судимо
        double w = width(ui) / width(ref);
        double h = capHeight(ui) / capHeight(ref);
        if (w < 0.9 || w > 1.1 || h < 0.9 || h > 1.1) {
            return String.format(Locale.ROOT, "width x%.2f, caps x%.2f vs bundled Inter %.0f", w, h, ui.getSize2D());
        }
        // 1.36.6: і абсолютно: великі літери Inter 0.727 кегля. Порівняння з нашим файлом нічого не дасть, якщо сама
        // Java рахує цей файл не так (обидва однаково великі), тому ще й межі 0.6-0.85
        double caps = capHeight(ui) / ui.getSize2D();
        if (caps < 0.6 || caps > 0.85) return String.format(Locale.ROOT, "caps %.2f of size", caps);
        return null;
    }

    /** Один рядок у лог: шрифт інтерфейсу і масштаби, щоб з логу гравця було видно, що вибрано. */
    public static void logChoice(String choice) {
        try {
            Font ui = uiFont();
            Object msg = Toolkit.getDefaultToolkit().getDesktopProperty("win.messagebox.font");
            double scale = GraphicsEnvironment.isHeadless() ? 1.0 : GraphicsEnvironment.getLocalGraphicsEnvironment()
                    .getDefaultScreenDevice().getDefaultConfiguration().getDefaultTransform().getScaleX();
            log.info(String.format(Locale.ROOT, "UI font: %s (%s %.1f, sample %.1f px, caps %.1f px); Java %s, scale %.2f, dpi %d, system font %s",
                    choice, ui != null ? ui.getFontName(Locale.ROOT) : "?", ui != null ? ui.getSize2D() : 0f,
                    ui != null ? width(ui) : 0, ui != null ? capHeight(ui) : 0,
                    System.getProperty("java.version"), scale, GraphicsEnvironment.isHeadless() ? 0 : Toolkit.getDefaultToolkit().getScreenResolution(),
                    msg instanceof Font ? ((Font) msg).getFamily(Locale.ROOT) + " " + ((Font) msg).getSize() : "-"));
        } catch (RuntimeException e) {
            log.info("UI font: " + choice);
        }
    }

    private static Font uiFont() {
        Font f = UIManager.getFont("defaultFont");
        return f != null ? f : UIManager.getFont("Label.font");
    }

    private static Font bundledInter(float size) {
        try (InputStream in = FontCheck.class.getResourceAsStream(DIR + FILES[0])) {
            if (in == null) return null;
            return Font.createFont(Font.TRUETYPE_FONT, in).deriveFont(Font.PLAIN, size);
        } catch (Exception e) {
            return null;
        }
    }

    private static double width(Font f) {
        return f.deriveFont(Font.PLAIN).getStringBounds(SAMPLE, FRC).getWidth();
    }

    private static double capHeight(Font f) {
        return new java.awt.font.TextLayout("HЖ", f.deriveFont(Font.PLAIN), FRC).getBounds().getHeight();
    }
}
