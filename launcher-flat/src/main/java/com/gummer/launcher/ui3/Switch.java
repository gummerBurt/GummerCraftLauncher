/*
 * Copyright (C) 2021-2026 GummerCraft <http://gummercraft.fun>
 */

package com.gummer.launcher.ui3;

import javax.swing.*;
import java.awt.*;

/**
 * Редизайн 3: перемикач «увімкнено / вимкнено» з підписом праворуч. Справжній JCheckBox (клавіатура, пробіл, слухачі,
 * диктор екрана), лише намальований як перемикач: доріжка 34x18, кружок ліворуч (вимкнено) або праворуч на
 * зеленому (увімкнено). Фокус клавіатури: зелена рамка навколо доріжки.
 */
public class Switch extends JCheckBox {

    private static final int TW = 34, TH = 18, P = 3;
    /** Рамку фокусу показуємо лише для клавіатури (Tab), не після кліку мишею. */
    private boolean keyboardFocus;

    public Switch(String text) {
        super(text);
        setOpaque(false);
        setFocusPainted(false);
        setBorderPainted(false);
        setRolloverEnabled(true);
        setFont(Ui.font(Font.PLAIN, 13f));
        setForeground(Ui.TEXT);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        setIcon(new Icon() {
            @Override
            public void paintIcon(Component c, Graphics g, int x, int y) {
                Graphics2D g2 = Ui.smooth(g);
                boolean on = isSelected();
                boolean hover = getModel().isRollover();
                g2.setColor(on ? (hover ? Ui.ACCENT_HOVER : Ui.ACCENT) : (hover ? new Color(0x4A4E57) : new Color(0x3A3D44)));
                // 16.11.2026: доріжка з відступом P усередині значка, щоб рамка фокусу (після кліку) не вилазила
                // за межі перемикача ліворуч і вгору (раніше малювалась з x - 2 і різалась краєм)
                int tx = x + P, ty = y + P;
                g2.fillRoundRect(tx, ty, TW, TH, TH, TH);
                g2.setColor(Color.WHITE);
                int d = TH - 6;
                g2.fillOval(on ? tx + TW - d - 3 : tx + 3, ty + 3, d, d);
                if (isFocusOwner() && keyboardFocus) {
                    g2.setColor(Ui.FOCUS);
                    g2.setStroke(new BasicStroke(2f));
                    g2.drawRoundRect(tx - 2, ty - 2, TW + 3, TH + 3, TH + 4, TH + 4);
                }
                g2.dispose();
            }

            @Override
            public int getIconWidth() {
                return TW + 2 * P;
            }

            @Override
            public int getIconHeight() {
                return TH + 2 * P;
            }
        });
        setIconTextGap(Ui.S2 + 2 - P);
        addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mousePressed(java.awt.event.MouseEvent e) {
                keyboardFocus = false;
            }
        });
        addKeyListener(new java.awt.event.KeyAdapter() {
            @Override
            public void keyReleased(java.awt.event.KeyEvent e) {
                if (e.getKeyCode() == java.awt.event.KeyEvent.VK_TAB) {
                    keyboardFocus = true;
                    repaint();
                }
            }
        });
    }
}
