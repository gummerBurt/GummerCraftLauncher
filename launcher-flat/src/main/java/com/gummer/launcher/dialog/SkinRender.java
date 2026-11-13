package com.gummer.launcher.dialog;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;

/**
 * 1.33: плоский вигляд скіна спереду (16x32 пікселі, збільшено без розмиття) для вікна «Акаунт сервера».
 * Розкладка текстури як у Minecraft: 64x64 (з 1.8, окремі ліва рука і нога, другий шар) і стара 64x32
 * (ліві кінцівки дзеркальні копії правих, другий шар лише на голові).
 */
final class SkinRender {

    private SkinRender() {
    }

    /** Вигляд спереду; {@code scale} пікселів екрана на піксель скіна. */
    static BufferedImage front(BufferedImage skin, boolean slim, int scale) {
        boolean modern = skin.getHeight() >= 64;
        int arm = slim ? 3 : 4;
        BufferedImage out = new BufferedImage(16, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        // голова і капюшон
        copy(g, skin, 8, 8, 8, 8, 4, 0, false);
        copy(g, skin, 40, 8, 8, 8, 4, 0, false);
        // тулуб
        copy(g, skin, 20, 20, 8, 12, 4, 8, false);
        // права рука гравця (зліва на екрані) і ліва
        copy(g, skin, 44, 20, arm, 12, 4 - arm, 8, false);
        if (modern) {
            copy(g, skin, 36, 52, arm, 12, 12, 8, false);
        } else {
            copy(g, skin, 44, 20, arm, 12, 12, 8, true);
        }
        // ноги
        copy(g, skin, 4, 20, 4, 12, 4, 20, false);
        if (modern) {
            copy(g, skin, 20, 52, 4, 12, 8, 20, false);
            // другий шар: куртка, рукави, штани
            copy(g, skin, 20, 36, 8, 12, 4, 8, false);
            copy(g, skin, 44, 36, arm, 12, 4 - arm, 8, false);
            copy(g, skin, 52, 52, arm, 12, 12, 8, false);
            copy(g, skin, 4, 36, 4, 12, 4, 20, false);
            copy(g, skin, 4, 52, 4, 12, 8, 20, false);
        } else {
            copy(g, skin, 4, 20, 4, 12, 8, 20, true);
        }
        g.dispose();

        BufferedImage big = new BufferedImage(16 * scale, 32 * scale, BufferedImage.TYPE_INT_ARGB);
        Graphics2D b = big.createGraphics();
        b.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        b.drawImage(out, 0, 0, 16 * scale, 32 * scale, null);
        b.dispose();
        return big;
    }

    /** Обличчя з капюшоном (шар 2), збільшене без розмиття до size x size; null, якщо картинка не скін. */
    static BufferedImage face(BufferedImage skin, int size) {
        if (skin == null || skin.getWidth() < 64 || skin.getHeight() < 32) return null;
        BufferedImage small = new BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = small.createGraphics();
        g.drawImage(skin.getSubimage(8, 8, 8, 8), 0, 0, null);
        g.drawImage(skin.getSubimage(40, 8, 8, 8), 0, 0, null);
        g.dispose();
        BufferedImage big = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D b = big.createGraphics();
        b.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        b.drawImage(small, 0, 0, size, size, null);
        b.dispose();
        return big;
    }

    /**
     * Тонкі руки: у скінах 64x64 для тонких рук стовпчик x=54..55 на рядках 20..31 (зовнішній бік правої руки)
     * зазвичай прозорий. Лише підказка для перемикача, гравець може змінити.
     */
    static boolean looksSlim(BufferedImage skin) {
        if (skin.getHeight() < 64) return false;
        for (int y = 20; y < 32; y++) {
            for (int x = 54; x < 56; x++) {
                if ((skin.getRGB(x, y) >>> 24) != 0) return false;
            }
        }
        return true;
    }

    private static void copy(Graphics2D g, BufferedImage src, int sx, int sy, int w, int h, int dx, int dy, boolean mirror) {
        if (sx + w > src.getWidth() || sy + h > src.getHeight()) return;
        BufferedImage part = src.getSubimage(sx, sy, w, h);
        if (mirror) {
            AffineTransform t = new AffineTransform();
            t.translate(dx + w, dy);
            t.scale(-1, 1);
            g.drawImage(part, t, null);
        } else {
            g.drawImage(part, dx, dy, null);
        }
    }
}
