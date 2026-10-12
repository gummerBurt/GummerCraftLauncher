/*
 * Copyright (C) 2021-2025 GummerCraft <http://gummercraft.fun>
 */

package com.gummer.launcher;

import com.skcraft.launcher.Launcher;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.util.Objects;

public class FlatBackgroundPanel extends JPanel {

    private Image background;
    private Image borders;
    private Image logo;

    public FlatBackgroundPanel() {
        try {
            background = ImageIO.read(Objects.requireNonNull(Launcher.class.getResourceAsStream("ui/frame/bg.jpg")));
        } catch (IOException e) {
            background = null;
        }

        try {
            borders = ImageIO.read(Objects.requireNonNull(Launcher.class.getResourceAsStream("ui/frame/borders.png")));
        } catch (IOException e) {
            borders = null;
        }

        try {
            logo = ImageIO.read(Objects.requireNonNull(Launcher.class.getResourceAsStream("ui/frame/logo.png")));
        } catch (IOException e) {
            logo = null;
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (background != null) {
            g.drawImage(background, 100, 0, null);
        }

        if(borders != null){
            g.drawImage(borders, 0, 0, null);
        }

        if(logo != null) {
            g.drawImage(logo, 346, 5, 724, 180, null);
        }
    }

}
