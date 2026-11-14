package com.gummer.launcher.dialog;

import com.gummer.launcher.account.ServerAccount;
import com.skcraft.launcher.Launcher;
import com.skcraft.launcher.swing.SwingHelper;
import net.miginfocom.swing.MigLayout;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;

import static com.skcraft.launcher.util.SharedLocale.tr;

/**
 * 1.33: аккаунт на ГаммерКрафті в боковій панелі над Telegram (необов'язковий акаунт, ServerAccountDialog), 200x45.
 * Два рядки: дрібно «Аккаунт на ГаммерКрафті», під ним жирно «Увійти» або нік; зліва значок або голова скіна StalDayZ.
 *
 * <p>Де вже пробували (13.11): п'ятою кнопкою внизу виштовхувала «Запуск» за край вікна на групах з вибором версії,
 * вгорі справа налазила на лого збірки і довгий напис обрізався.</p>
 */
final class AccountChip extends JPanel {

    private static final int HEAD = 28;

    private final Launcher launcher;
    private final JLabel icon = new JLabel();
    private final JLabel text = new JLabel();
    private final JLabel caption = new JLabel(tr("serverAccount.chip"));
    private final Icon userIcon;
    private boolean hover;
    private int refreshId;
    /** Голова останнього завантаженого скіна: refresh() викликається на кожне відкриття вікна акаунта, і без цього
     *  голова на секунду мінялась на значок, поки скін качався знову (власник 13.11). */
    private String headUuid;
    private Icon headIcon;

    AccountChip(Launcher launcher, Runnable onClick) {
        super(new MigLayout("insets 4 10 4 10, gap 8 0", "[][grow]", "[center]"));
        this.launcher = launcher;
        setOpaque(false);
        userIcon = SwingHelper.createIcon(Launcher.class, "ui/icon/light/user.png", HEAD - 6, HEAD - 6);
        text.setForeground(new Color(235, 235, 235));
        text.setFont(text.getFont().deriveFont(Font.BOLD, text.getFont().getSize2D() + 1f));
        caption.setForeground(new Color(175, 175, 175));
        caption.setFont(caption.getFont().deriveFont(caption.getFont().getSize2D() - 2f));
        icon.setHorizontalAlignment(SwingConstants.CENTER);
        add(icon, "w 28!, h 28!");
        JPanel lines = new JPanel(new MigLayout("insets 0, gap 0, wrap 1", "[grow]", "[]0[]"));
        lines.setOpaque(false);
        lines.add(caption);
        lines.add(text);
        add(lines, "growx, wmin 0");
        setToolTipText(tr("tooltip.serverAccount"));
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (SwingUtilities.isLeftMouseButton(e)) onClick.run();
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
        refresh();
    }

    /** Перечитати збережений вхід: нік, голова скіна (у фоні). */
    void refresh() {
        final int id = ++refreshId;
        ServerAccount.Login login = ServerAccount.saved();
        if (login == null) {
            icon.setIcon(userIcon);
            text.setText(tr("serverAccount.chipLogin"));
        } else {
            icon.setIcon(login.uuid != null && login.uuid.equals(headUuid) && headIcon != null ? headIcon : userIcon);
            text.setText(login.nick);
            final String uuid = login.uuid;
            Thread t = new Thread(() -> {
                // 14.11.2026: скін з того сервера, де він справді є (раніше лише StalDayZ, тож у кого скін лише на GummerCraft, був Стів)
                byte[] png = new ServerAccount(launcher.getProperties()).ownSkin(login);
                BufferedImage head = null;
                try {
                    if (png != null) head = head(ImageIO.read(new ByteArrayInputStream(png)));
                } catch (Exception ignored) {
                    // без голови лишається значок
                }
                final BufferedImage h = head;
                SwingUtilities.invokeLater(() -> {
                    if (id == refreshId && h != null) {
                        headUuid = uuid;
                        headIcon = new ImageIcon(h);
                        icon.setIcon(headIcon);
                    }
                });
            }, "account-chip");
            t.setDaemon(true);
            t.start();
        }
        revalidate();
        repaint();
    }

    /** Обличчя скіна з капюшоном, збільшене без розмиття. */
    private static BufferedImage head(BufferedImage skin) {
        if (skin == null || skin.getWidth() < 64 || skin.getHeight() < 32) return null;
        BufferedImage small = new BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = small.createGraphics();
        g.drawImage(skin.getSubimage(8, 8, 8, 8), 0, 0, null);
        g.drawImage(skin.getSubimage(40, 8, 8, 8), 0, 0, null);
        g.dispose();
        BufferedImage big = new BufferedImage(HEAD, HEAD, BufferedImage.TYPE_INT_ARGB);
        Graphics2D b = big.createGraphics();
        b.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        b.drawImage(small, 0, 0, HEAD, HEAD, null);
        b.dispose();
        return big;
    }

    @Override
    protected void paintComponent(Graphics g) {
        // та сама темна напівпрозора підкладка, що в блоку «Версія Minecraft»
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(new Color(0, 0, 0, hover ? 190 : 150));
        g2.fillRoundRect(0, 0, getWidth(), getHeight(), 18, 18);
        g2.dispose();
        super.paintComponent(g);
    }
}
