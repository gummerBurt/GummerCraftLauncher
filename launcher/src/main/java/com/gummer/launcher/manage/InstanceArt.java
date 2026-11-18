/*
 * Copyright (C) 2021-2026 GummerCraft <http://gummercraft.fun>
 */

package com.gummer.launcher.manage;

import com.skcraft.launcher.Instance;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * 17.11.2026: своя іконка і свій фон для копії збірки (вирішено: «нехай на свої копії гравці зможуть ставити бекграунд
 * та іконку»). Картинки лежать у теці копії: {@value #ICON} (квадрат 128x128) і {@value #BACKGROUND} (до 2560x1440).
 * Гравець вибирає будь-який PNG, JPG, GIF чи BMP: лаунчер сам обрізає іконку до квадрата і зменшує фон, тож у теці
 * не лежать картинки на десятки мегабайт. З 17.11.2026 і для встановлених версій, завантажувачів і модпаків Modrinth
 * ({@link #supported}); у збірок GummerCraft і StalDayZ оформлення від GummerCraft. Назви файлів лишились «copy-».
 *
 * <p>Прочитане кешується за шляхом, розміром і часом зміни файлу: список збірок питає іконку на кожному малюванні.</p>
 */
public final class InstanceArt {

    public static final String ICON = "copy-icon.png";
    public static final String BACKGROUND = "copy-background.jpg";
    private static final int ICON_SIZE = 128;
    private static final int BG_MAX_W = 2560, BG_MAX_H = 1440;
    private static final long MAX_SOURCE = 40L << 20;

    private static final Map<String, Object[]> CACHE = new HashMap<String, Object[]>();

    private InstanceArt() {
    }

    /**
     * Чи можна цій збірці ставити свої картинки і свою назву. 17.11.2026 (власник: «нехай гравець може змінити назву,
     * іконку та бекграунд усіх скачаних збірок, крім збірок гаммеркрафту та сталдейз»): копії і все встановлене, що
     * лаунчер збирає сам (версії Vanilla, завантажувачі, модпаки Modrinth); модпаки з нашого CDN ні.
     */
    public static boolean supported(Instance instance) {
        return instance != null && (instance.isCopy() || instance.isLocal() && instance.isBuiltIn());
    }

    public static File iconFile(Instance instance) {
        return new File(instance.getDir(), ICON);
    }

    public static File backgroundFile(Instance instance) {
        return new File(instance.getDir(), BACKGROUND);
    }

    /** Своя іконка копії (128x128) або null. */
    public static BufferedImage icon(Instance instance) {
        return supported(instance) ? load(iconFile(instance)) : null;
    }

    /** Свій фон копії або null. */
    public static BufferedImage background(Instance instance) {
        return supported(instance) ? load(backgroundFile(instance)) : null;
    }

    private static BufferedImage load(File f) {
        if (!f.isFile()) return null;
        String key = f.getAbsolutePath();
        long len = f.length(), mod = f.lastModified();
        synchronized (CACHE) {
            Object[] c = CACHE.get(key);
            if (c != null && (Long) c[0] == len && (Long) c[1] == mod) return (BufferedImage) c[2];
        }
        BufferedImage img;
        try {
            img = ImageIO.read(f);
        } catch (IOException | RuntimeException e) {
            img = null;
        }
        synchronized (CACHE) {
            CACHE.put(key, new Object[]{len, mod, img});
        }
        return img;
    }

    /** Поставити іконку з будь-якої картинки: середина обрізається до квадрата і зменшується до 128x128. */
    public static void setIcon(Instance instance, File source) throws IOException {
        BufferedImage src = read(source);
        int s = Math.min(src.getWidth(), src.getHeight());
        BufferedImage square = src.getSubimage((src.getWidth() - s) / 2, (src.getHeight() - s) / 2, s, s);
        BufferedImage out = scale(square, Math.min(ICON_SIZE, s), Math.min(ICON_SIZE, s), true);
        File target = iconFile(instance);
        File tmp = new File(target.getPath() + ".tmp");
        if (!ImageIO.write(out, "png", tmp)) throw new IOException("PNG");
        Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
    }

    /** Поставити фон: більший за 2560x1440 зменшується зі збереженням пропорцій, зберігається як JPG. */
    public static void setBackground(Instance instance, File source) throws IOException {
        writeBackground(read(source), backgroundFile(instance));
    }

    /** Записати фон у файл (зменшення до 2560x1440 зі збереженням пропорцій, JPG). */
    public static void writeBackground(BufferedImage src, File target) throws IOException {
        double k = Math.min(1.0, Math.min(BG_MAX_W / (double) src.getWidth(), BG_MAX_H / (double) src.getHeight()));
        int w = Math.max(1, (int) Math.round(src.getWidth() * k)), h = Math.max(1, (int) Math.round(src.getHeight() * k));
        BufferedImage out = scale(src, w, h, false);
        File tmp = new File(target.getPath() + ".tmp");
        writeJpeg(out, tmp, 0.9f);
        Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
    }

    /** Прибрати свою іконку чи фон (повернеться оформлення типу збірки). */
    public static void clear(File f) throws IOException {
        Files.deleteIfExists(f.toPath());
    }

    private static BufferedImage read(File source) throws IOException {
        if (source == null || !source.isFile()) throw new IOException("Немає файлу");
        if (source.length() > MAX_SOURCE) throw new IOException("Файл більший за 40 МБ");
        BufferedImage img = ImageIO.read(source);
        if (img == null || img.getWidth() < 1 || img.getHeight() < 1) throw new IOException("Це не картинка PNG, JPG, GIF чи BMP");
        return img;
    }

    /** Зменшення кроками навпіл (білінійно) і останній крок бікубічно: без «драбинок» і без розмиття. */
    private static BufferedImage scale(BufferedImage src, int dw, int dh, boolean alpha) {
        int type = alpha ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB;
        BufferedImage cur = src;
        while (cur.getWidth() >= dw * 2 && cur.getHeight() >= dh * 2) {
            BufferedImage half = new BufferedImage(Math.max(dw, cur.getWidth() / 2), Math.max(dh, cur.getHeight() / 2), type);
            draw(cur, half, RenderingHints.VALUE_INTERPOLATION_BILINEAR, alpha);
            cur = half;
        }
        BufferedImage out = new BufferedImage(dw, dh, type);
        draw(cur, out, RenderingHints.VALUE_INTERPOLATION_BICUBIC, alpha);
        return out;
    }

    private static void draw(BufferedImage from, BufferedImage to, Object interpolation, boolean alpha) {
        Graphics2D g = to.createGraphics();
        try {
            if (!alpha) { // прозорі місця фону: темні, а не чорні плями після JPG
                g.setColor(new Color(0x0F, 0x10, 0x13));
                g.fillRect(0, 0, to.getWidth(), to.getHeight());
            } else {
                g.setComposite(AlphaComposite.Src);
            }
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, interpolation);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.drawImage(from, 0, 0, to.getWidth(), to.getHeight(), null);
        } finally {
            g.dispose();
        }
    }

    private static void writeJpeg(BufferedImage img, File f, float quality) throws IOException {
        Iterator<ImageWriter> it = ImageIO.getImageWritersByFormatName("jpg");
        if (!it.hasNext()) throw new IOException("JPG");
        ImageWriter w = it.next();
        try (ImageOutputStream out = ImageIO.createImageOutputStream(f)) {
            w.setOutput(out);
            ImageWriteParam p = w.getDefaultWriteParam();
            p.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            p.setCompressionQuality(quality);
            w.write(null, new IIOImage(img, null, null), p);
        } finally {
            w.dispose();
        }
    }
}
