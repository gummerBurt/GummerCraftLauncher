package com.gummer.launcher.dialog;

import com.gummer.launcher.prefs.LauncherPrefs;
import com.skcraft.launcher.Launcher;
import com.skcraft.launcher.swing.SwingHelper;
import net.miginfocom.swing.MigLayout;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import static com.skcraft.launcher.util.SharedLocale.tr;

/**
 * 1.33: помітна кнопка «Змінити скін» вгорі справа, поруч з плашкою акаунта (AccountChip). Власник 13.11: скін-система
 * має «вистрибувати» на гравця, щоб було видно, що скін міняється прямо в лаунчері. Зелена, як «Запуск»; поки гравець
 * її жодного разу не натиснув, м'яко пульсує рамкою (позначка {@code skinChipSeen} у launcher-ui.json).
 */
final class SkinChip extends JPanel {

    private static final String SEEN = "skinChipSeen";
    /** Сіра напівпрозора підкладка з тонкою рамкою (SkinChip і AccountChip, дизайн 1.35). */
    static void paintGlassChip(Graphics2D g2, JComponent c, boolean hover) {
        g2.setColor(new Color(255, 255, 255, hover ? 34 : 16));
        g2.fillRoundRect(0, 0, c.getWidth(), c.getHeight(), 12, 12);
        g2.setColor(new Color(255, 255, 255, hover ? 60 : 32));
        g2.drawRoundRect(0, 0, c.getWidth() - 1, c.getHeight() - 1, 12, 12);
    }

    private boolean hover;
    private final Timer pulse;
    private float phase;

    SkinChip(Runnable onClick) {
        super(new MigLayout("insets 0 10 0 14, gap 8", "[][]", "[grow, center]")); // 1.35: вміст рівно по вертикалі
        setOpaque(false);
        JLabel icon = new JLabel(SwingHelper.createIcon(Launcher.class, "default_skin.png", 24, 24));
        JLabel text = new JLabel(tr("serverAccount.skinChip"));
        text.setForeground(Color.WHITE);
        text.setFont(text.getFont().deriveFont(Font.BOLD, text.getFont().getSize2D() + 1f));
        add(icon, "w 28!, h 28!");
        add(text);
        setToolTipText(tr("tooltip.skinChip"));
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        pulse = new Timer(50, e -> {
            phase += 0.08f;
            repaint();
        });
        if (!LauncherPrefs.getBoolean(SEEN, false)) pulse.start();

        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (!SwingUtilities.isLeftMouseButton(e)) return;
                if (pulse.isRunning()) {
                    pulse.stop();
                    LauncherPrefs.set(SEEN, true);
                    repaint();
                }
                onClick.run();
            }

            @Override
            public void mouseEntered(MouseEvent e) {
                hover = true;
                repaint();
            }

            @Override
            public void mouseExited(MouseEvent e) {
                hover = false;
                repaint();
            }
        });
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        // дизайн 1.35: сіра скляна підкладка, як у карток збірок (яскраво-зелена відволікала, відгук гравців 16.11)
        paintGlassChip(g2, this, hover);
        if (pulse.isRunning()) {
            int a = (int) (90 + 120 * (0.5 + 0.5 * Math.sin(phase)));
            g2.setStroke(new BasicStroke(2.5f));
            g2.setColor(new Color(255, 255, 255, Math.min(255, a)));
            g2.drawRoundRect(1, 1, getWidth() - 3, getHeight() - 3, 11, 11);
        }
        g2.dispose();
        super.paintComponent(g);
    }

    @Override
    public void removeNotify() {
        pulse.stop();
        super.removeNotify();
    }
}
