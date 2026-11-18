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

    private static final int HEAD = 22; // 17.11.2026: вузька плашка вгорі справа (було 28 у боковій панелі)

    private final Launcher launcher;
    private final JLabel icon = new JLabel();
    private final JLabel text = new JLabel();
    private final JLabel caption = new JLabel(tr("serverAccount.chipShort"));
    private final Icon userIcon;
    private boolean hover;
    private int refreshId;
    /** Голова останнього завантаженого скіна: refresh() викликається на кожне відкриття вікна акаунта, і без цього
     *  голова на секунду мінялась на значок, поки скін качався знову (власник 13.11). */
    private String headUuid;
    private Icon headIcon;
    /** 17.11.2026: розгорнуте скло (setZoom): голова, значок і шрифти більші. */
    private double zoom = 1.0;
    private BufferedImage headSmall;
    private final Font textFont, captionFont;
    private final java.awt.image.BufferedImage userImage;

    AccountChip(Launcher launcher, Runnable onClick) {
        super(new MigLayout("insets 0 8 0 8, gap 6 0", "[][grow]", "[grow, center]"));
        this.launcher = launcher;
        setOpaque(false);
        java.awt.image.BufferedImage ui = null;
        try {
            ui = ImageIO.read(Launcher.class.getResource("ui/icon/light/user.png"));
        } catch (Exception ignored) {
            // значок намалює createIcon
        }
        userImage = ui;
        userIcon = SwingHelper.createIcon(Launcher.class, "ui/icon/light/user.png", HEAD - 6, HEAD - 6);
        text.setForeground(new Color(235, 235, 235));
        text.setFont(text.getFont().deriveFont(Font.BOLD));
        caption.setForeground(new Color(190, 190, 190));
        caption.setFont(caption.getFont().deriveFont(caption.getFont().getSize2D() - 2f));
        icon.setHorizontalAlignment(SwingConstants.CENTER);
        add(icon, "w 22!, h 22!");
        textFont = text.getFont();
        captionFont = caption.getFont();
        JPanel lines = new JPanel(new MigLayout("insets 0, gap 0, wrap 1", "[grow]", "[]0[]"));
        lines.setOpaque(false);
        lines.add(caption, "wmin 0");
        lines.add(text, "wmin 0");
        add(lines, "growx, wmin 0");
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

    /** 17.11.2026: множник розміру (1 у звичайному вікні). */
    void setZoom(double z) {
        if (z == zoom) return;
        zoom = z;
        int head = (int) Math.round(HEAD * z), pad = (int) Math.round(8 * z);
        MigLayout lay = (MigLayout) getLayout();
        lay.setLayoutConstraints("insets 0 " + pad + " 0 " + pad + ", gap " + (int) Math.round(6 * z) + " 0");
        lay.setComponentConstraints(icon, "w " + head + "!, h " + head + "!");
        text.setFont(textFont.deriveFont((float) (textFont.getSize2D() * z)));
        caption.setFont(captionFont.deriveFont((float) (captionFont.getSize2D() * z)));
        if (headSmall != null) headIcon = new ImageIcon(scaleNearest(headSmall, head));
        Icon user = userImage != null ? new ImageIcon(scaleSmooth(userImage, head - (int) Math.round(6 * z))) : userIcon;
        icon.setIcon(headSmall != null && headIcon != null ? headIcon : user);
        revalidate();
        repaint();
    }

    private Icon userIcon() {
        if (zoom == 1.0 || userImage == null) return userIcon;
        return new ImageIcon(scaleSmooth(userImage, (int) Math.round((HEAD - 6) * zoom)));
    }

    private static BufferedImage scaleNearest(BufferedImage src, int size) {
        BufferedImage big = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D b = big.createGraphics();
        b.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        b.drawImage(src, 0, 0, size, size, null);
        b.dispose();
        return big;
    }

    private static BufferedImage scaleSmooth(BufferedImage src, int size) {
        BufferedImage out = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D b = out.createGraphics();
        b.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        b.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        b.drawImage(src, 0, 0, size, size, null);
        b.dispose();
        return out;
    }

    /** Перечитати збережений вхід: нік, голова скіна (у фоні). */
    void refresh() {
        final int id = ++refreshId;
        ServerAccount.Login login = ServerAccount.saved();
        if (login == null) {
            headSmall = null;
            icon.setIcon(userIcon());
            text.setText(tr("serverAccount.chipLogin"));
        } else {
            icon.setIcon(login.uuid != null && login.uuid.equals(headUuid) && headIcon != null ? headIcon : userIcon());
            text.setText(login.nick);
            final String uuid = login.uuid;
            Thread t = new Thread(() -> {
                // 14.11.2026: скін з того сервера, де він справді є (раніше лише StalDayZ, тож у кого скін лише на GummerCraft, був Стів)
                byte[] png = new ServerAccount(launcher.getProperties()).ownSkin(login);
                BufferedImage head = null;
                try {
                    if (png != null) head = head(ImageIO.read(new ByteArrayInputStream(png)), 8);
                } catch (Exception ignored) {
                    // без голови лишається значок
                }
                final BufferedImage h = head;
                SwingUtilities.invokeLater(() -> {
                    if (id == refreshId && h != null) {
                        headUuid = uuid;
                        headSmall = h;
                        headIcon = new ImageIcon(scaleNearest(h, (int) Math.round(HEAD * zoom)));
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
    private static BufferedImage head(BufferedImage skin, int size) {
        if (skin == null || skin.getWidth() < 64 || skin.getHeight() < 32) return null;
        BufferedImage small = new BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = small.createGraphics();
        g.drawImage(skin.getSubimage(8, 8, 8, 8), 0, 0, null);
        g.drawImage(skin.getSubimage(40, 8, 8, 8), 0, 0, null);
        g.dispose();
        return size == 8 ? small : scaleNearest(small, size);
    }

    @Override
    protected void paintComponent(Graphics g) {
        // та сама темна напівпрозора підкладка, що в блоку «Версія Minecraft»
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        SkinChip.paintGlassChip(g2, this, hover); // дизайн 1.35: як у «Змінити скін»
        g2.dispose();
        super.paintComponent(g);
    }
}
