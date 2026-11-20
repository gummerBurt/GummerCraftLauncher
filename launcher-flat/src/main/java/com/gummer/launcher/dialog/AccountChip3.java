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
final class AccountChip3 extends JPanel {

    private static final int HEAD = 32;

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

    AccountChip3(Launcher launcher, Runnable onClick) {
        // редизайн 2: картка профілю в рейці, 232x56: голова 32, підпис і нік (стрілка «>» не влазила разом з підписом)
        super(new MigLayout("insets 0 12 0 12, gap 12 0", "[][grow]", "[grow,center]"));
        this.launcher = launcher;
        setOpaque(false);
        userIcon = new com.gummer.launcher.look.UserIcon(HEAD - 6); // 20.11.2026: векторний замість user.png 16x16
        text.setForeground(com.gummer.launcher.ui3.Ui.TEXT);
        text.setFont(com.gummer.launcher.ui3.Ui.font(Font.BOLD, 14f));
        caption.setForeground(com.gummer.launcher.ui3.Ui.TEXT_3);
        caption.setFont(com.gummer.launcher.ui3.Ui.font(Font.PLAIN, Boolean.getBoolean("gummer.font.inter") ? 11f : 12f)); // на 11 px у Segoe UI губиться крапка «і»; Inter 11 як Segoe 12
        icon.setHorizontalAlignment(SwingConstants.CENTER);
        add(icon, "w 32!, h 32!");
        JPanel lines = new JPanel(new MigLayout("insets 0, gap 0, wrap 1", "[grow,fill]", "[]0[]"));
        lines.setOpaque(false);
        lines.add(caption);
        lines.add(text);
        add(lines, "growx, wmin 0");
        // клавіатура: Tab доходить сюди, пробіл чи Enter відкриває акаунт
        setFocusable(true);
        getAccessibleContext().setAccessibleName(tr("serverAccount.chip"));
        addFocusListener(new java.awt.event.FocusAdapter() {
            @Override
            public void focusGained(java.awt.event.FocusEvent e) {
                repaint();
            }

            @Override
            public void focusLost(java.awt.event.FocusEvent e) {
                repaint();
            }
        });
        for (String key : new String[]{"SPACE", "ENTER"}) {
            getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(key), "open");
        }
        getActionMap().put("open", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                onClick.run();
            }
        });
        setToolTipText(tr("tooltip.serverAccount"));
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseReleased(MouseEvent e) {
                // 18.11.2026 (власник: «інколи в повноекранному режимі, коли клацаєш по кнопці зміни дизайну, вона не
                // спрацьовує»): mouseClicked Swing не шле, якщо миша між натисканням і відпусканням зсунулась хоч на піксель.
                // Як у кнопки: відпускання над самою плашкою (воно приходить сюди, лише якщо натискали теж на ній)
                if (!e.getComponent().contains(e.getPoint())) return;
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
                        headIcon = new com.skcraft.launcher.swing.CrispIcon(h, HEAD, HEAD); // 8x8 збільшується у фізичних пікселях
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
        return small;
    }

    @Override
    protected void paintComponent(Graphics g) {
        // редизайн 2: поверхня рейки на тон світліша, при наведенні ще світліша, фокус зеленою рамкою
        Graphics2D g2 = com.gummer.launcher.ui3.Ui.smooth(g);
        int arc = com.gummer.launcher.ui3.Ui.RADIUS;
        g2.setColor(hover ? com.gummer.launcher.ui3.Ui.SURFACE_3 : com.gummer.launcher.ui3.Ui.SURFACE_2);
        g2.fillRoundRect(0, 0, getWidth(), getHeight(), arc, arc);
        if (isFocusOwner()) com.gummer.launcher.ui3.Ui.focusRing(g2, getWidth(), getHeight(), arc);
        g2.dispose();
        super.paintComponent(g);
    }
}
