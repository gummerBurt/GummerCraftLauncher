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
        // 17.11.2026: темна підкладка, як у хотбара (на світлому фоні Vanilla сірий підпис «Акаунт ГК» губився)
        // 20.11.2026: рамка по фізичних пікселях (look.Edge), раніше верхній край на 125% зрізало
        com.gummer.launcher.look.Edge.pill(g2, 0, 0, c.getWidth(), c.getHeight(), 12, new Color(0, 0, 0, 120), null);
        com.gummer.launcher.look.Edge.pill(g2, 0, 0, c.getWidth(), c.getHeight(), 12, hover ? new Color(255, 255, 255, 28) : null,
                new Color(255, 255, 255, hover ? 70 : 40));
    }

    private boolean hover;
    private final Timer pulse;
    private final JLabel icon, text;
    private final Font textFont;
    private double zoom = 1.0;

    /** 17.11.2026: розгорнуте скло: значок і текст більші. */
    void setZoom(double z) {
        if (z == zoom) return;
        zoom = z;
        int s = (int) Math.round(20 * z), box = (int) Math.round(22 * z), pad = (int) Math.round(8 * z);
        MigLayout lay = (MigLayout) getLayout();
        lay.setLayoutConstraints("insets 0 " + pad + " 0 " + pad + ", gap " + (int) Math.round(6 * z));
        lay.setComponentConstraints(icon, "w " + box + "!, h " + box + "!");
        icon.setIcon(SwingHelper.createIcon(Launcher.class, "default_skin.png", s, s));
        text.setFont(textFont.deriveFont((float) (textFont.getSize2D() * z)));
        revalidate();
        repaint();
    }
    private float phase;

    SkinChip(Runnable onClick) {
        // 17.11.2026: вузька плашка «Скіни» вгорі справа під акаунтом (було «Змінити скін» 200x45 у боковій панелі)
        super(new MigLayout("insets 0 8 0 8, gap 6", "[][]", "[grow, center]"));
        setOpaque(false);
        icon = new JLabel(SwingHelper.createIcon(Launcher.class, "default_skin.png", 20, 20));
        text = new JLabel(tr("serverAccount.skinChipShort"));
        text.setForeground(Color.WHITE);
        text.setFont(text.getFont().deriveFont(Font.BOLD));
        textFont = text.getFont();
        add(icon, "w 22!, h 22!");
        add(text);
        setToolTipText(tr("tooltip.skinChip"));
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        pulse = new Timer(50, e -> {
            phase += 0.08f;
            repaint();
        });
        if (!LauncherPrefs.getBoolean(SEEN, false) && !com.gummer.launcher.look.LookTheme.LITE) pulse.start();

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
        // дизайн 1.35: сіра скляна підкладка, як у карток збірок (яскраво-зелена відволікала, відгук гравців 16.11)
        paintGlassChip(g2, this, hover);
        if (pulse.isRunning()) {
            int a = (int) (90 + 120 * (0.5 + 0.5 * Math.sin(phase)));
            com.gummer.launcher.look.Edge.pill(g2, 0, 0, getWidth(), getHeight(), 12, null, new Color(255, 255, 255, Math.min(255, a)), 2.5);
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
