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
 * 1.33: помітна кнопка «Змінити скін» вгорі справа, поруч з плашкою акаунта (AccountChip3). Власник 13.11: скін-система
 * має «вистрибувати» на гравця, щоб було видно, що скін міняється прямо в лаунчері. Зелена, як «Запуск»; поки гравець
 * її жодного разу не натиснув, м'яко пульсує рамкою (позначка {@code skinChipSeen} у launcher-ui.json).
 */
final class SkinChip3 extends JPanel {

    private static final String SEEN = "skinChipSeen";
    private static final Color GREEN = new Color(0x76, 0xB8, 0x2A);
    private static final Color GREEN_HOVER = new Color(0x86, 0xCC, 0x34);

    private boolean hover;
    private final Timer pulse;
    private float phase;

    SkinChip3(Runnable onClick) {
        // редизайн 2: друга за важливістю дія, тому зелений контур, а не суцільна заливка (суцільна лише в «Грати»)
        super(new MigLayout("insets 0 12 0 12, gap 8", "push[][]push", "[grow,center]"));
        setOpaque(false);
        JLabel icon = new JLabel(com.skcraft.launcher.swing.CrispIcon.of(Launcher.class, "default_skin.png", 20, 20));
        JLabel text = new JLabel(tr("serverAccount.skinChip"));
        text.setForeground(com.gummer.launcher.ui3.Ui.TEXT);
        text.setFont(com.gummer.launcher.ui3.Ui.font(Font.BOLD, 13f));
        add(icon, "w 20!, h 20!");
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
            public void mouseReleased(MouseEvent e) {
                // 18.11.2026 (власник: «інколи в повноекранному режимі, коли клацаєш по кнопці зміни дизайну, вона не
                // спрацьовує»): mouseClicked Swing не шле, якщо миша між натисканням і відпусканням зсунулась хоч на піксель.
                // Як у кнопки: відпускання над самою плашкою (воно приходить сюди, лише якщо натискали теж на ній)
                if (!e.getComponent().contains(e.getPoint())) return;
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
        int arc = com.gummer.launcher.ui3.Ui.RADIUS;
        if (hover) {
            g2.setColor(com.gummer.launcher.ui3.Ui.alpha(com.gummer.launcher.ui3.Ui.ACCENT_HOVER, 45));
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), arc, arc);
        }
        // поки гравець жодного разу не натиснув: контур м'яко «дихає» яскравістю, щоб кнопку помітили
        int a = pulse.isRunning() ? (int) (120 + 135 * (0.5 + 0.5 * Math.sin(phase))) : 255;
        g2.setStroke(new BasicStroke(1.5f));
        g2.setColor(com.gummer.launcher.ui3.Ui.alpha(com.gummer.launcher.ui3.Ui.ACCENT_HOVER, a));
        g2.drawRoundRect(1, 1, getWidth() - 2, getHeight() - 2, arc, arc);
        g2.dispose();
        super.paintComponent(g);
    }

    @Override
    public void removeNotify() {
        pulse.stop();
        super.removeNotify();
    }
}
