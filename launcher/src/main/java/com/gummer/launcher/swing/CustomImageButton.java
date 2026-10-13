/*
 * Copyright (C) 2021-2025 GummerCraft <http://gummercraft.fun>
 */

package com.gummer.launcher.swing;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.image.BufferedImage;

public class CustomImageButton extends JButton {

    CustomImageButton instance = this;
    Icon defaultIcon;
    Icon hoverIcon;

    public CustomImageButton(BufferedImage icon, int width, int height, String tooltip) {
        super(null, new ImageIcon(getResizedImage(icon, width, height)));
        Image resized = getResizedImage(icon, width, height);
        defaultIcon = new ImageIcon(resized);
        hoverIcon = new ImageIcon(getHoveredImage(resized));
        this.setOpaque(false);
        this.setContentAreaFilled(false);
        this.setBorderPainted(false);
        this.setFocusPainted(false);

        ToolTipManager.sharedInstance().setInitialDelay(700);
        this.setToolTipText(tooltip);

        this.addMouseListener(new MouseAdapter() {
            public void mouseEntered(java.awt.event.MouseEvent evt) {
                instance.setIcon(hoverIcon);
                instance.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            }

            public void mouseExited(java.awt.event.MouseEvent evt) {
                instance.setIcon(defaultIcon);
            }
        });
    }

    /** Замінити картинку кнопки (наприклад, кнопка соцмережі вибраної збірки); стан наведення зберігається. */
    public void setImage(BufferedImage icon, int width, int height) {
        boolean hovered = getIcon() == hoverIcon;
        Image resized = getResizedImage(icon, width, height);
        defaultIcon = new ImageIcon(resized);
        hoverIcon = new ImageIcon(getHoveredImage(resized));
        setIcon(hovered ? hoverIcon : defaultIcon);
    }

    public static Image getResizedImage(BufferedImage icon, int width, int height) {
        Image tmp = icon.getScaledInstance(width, height, Image.SCALE_SMOOTH);
        BufferedImage resize = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);

        Graphics2D g2d = resize.createGraphics();
        g2d.drawImage(tmp, 0, 0, null);
        g2d.dispose();

        return resize;
    }

    private static Image getHoveredImage(Image source) {
        BufferedImage bi = new BufferedImage(
            source.getWidth(null),
            source.getHeight(null),
            BufferedImage.TYPE_INT_ARGB);

        int[] pixel = {0, 0, 0, 0};
        float[] hsbvals = {0, 0, 0};

        bi.getGraphics().drawImage(source, 0, 0, null);

        for (int i = 0; i < bi.getHeight(); i++) {
            for (int j = 0; j < bi.getWidth(); j++) {
                bi.getRaster().getPixel(j, i, pixel);
                Color.RGBtoHSB(pixel[0], pixel[1], pixel[2], hsbvals);
                Color c = new Color(Color.HSBtoRGB(hsbvals[0], hsbvals[1], hsbvals[2] * 0.87F));
                bi.getRaster().setPixel(j, i, new int[]{c.getRed(), c.getGreen(), c.getBlue(), pixel[3]});
            }
        }

        return bi;
    }
}
