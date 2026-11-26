package com.gummer.launcher.dialog;

import com.gummer.launcher.account.ServerAccount;
import com.gummer.launcher.account.ServerAccount.AccountException;
import com.gummer.launcher.account.ServerAccount.Login;
import com.gummer.launcher.account.ServerAccount.Target;
import com.gummer.launcher.support.SupportService;
import com.skcraft.launcher.Instance;
import com.skcraft.launcher.Launcher;
import com.skcraft.launcher.auth.AccountList;
import com.skcraft.launcher.auth.OfflineSession;
import com.skcraft.launcher.auth.SavedSession;
import com.skcraft.launcher.auth.UserType;
import com.skcraft.launcher.persistence.Persistence;
import com.skcraft.launcher.swing.SwingHelper;
import com.fasterxml.jackson.databind.JsonNode;
import com.formdev.flatlaf.util.UIScale;
import lombok.extern.java.Log;
import net.miginfocom.swing.MigLayout;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.skcraft.launcher.util.SharedLocale.tr;

/**
 * 1.33: вікно «Акаунт сервера» (кнопка «Мої акаунти» внизу головного вікна). Повністю необов'язкове:
 * грати можна з будь-яким локальним акаунтом, як і раніше.
 *
 * <p>Не увійшов: вкладки «Вхід» і «Реєстрація» (нік і пароль як на сервері, правила AuthMe), «Забув пароль?»
 * відкриває форму підтримки з готовим текстом (пароль скидають адміни вручну). Увійшов: нік, підказка, як
 * зайти на сервер, і два скіни окремо: StalDayZ і GummerCraft (PNG 64x64, класичні або тонкі руки).</p>
 *
 * <p>Мережа: {@link ServerAccount}. Пароль не зберігається, лише токен.</p>
 */
@Log
public class ServerAccountDialog extends JDialog {

    private static final String HELP_BOT = "https://t.me/gummerCraft_help_bot";
    /** Як allowedNicknameCharacters і довжина ніка в AuthMe StalDayZ. */
    private static final Pattern NICK = Pattern.compile("[a-zA-Z0-9_а-яА-Я]{3,16}");
    private static final Pattern PASSWORD = Pattern.compile("[!-~]{5,30}");

    private static ServerAccountDialog open;

    private final Launcher launcher;
    private final ServerAccount api;
    private final SupportService support;
    private final Supplier<Instance> selectedInstance;
    private final Runnable onChange;
    private final CardLayout cards = new CardLayout();
    private final JPanel root = new JPanel(cards);
    /** Увійшов: дві сторінки, «cabinet» (особистий кабінет) і «skins» (два скіни). */
    private final CardLayout inner = new CardLayout();
    private final JPanel accountCard = new JPanel(inner);
    /** Панелі скінів поточного входу: перед закриттям питаємо про вибраний, але не завантажений скін. */
    private final List<SkinPanel> skinPanels = new ArrayList<SkinPanel>();
    private boolean openSkins;
    /** Закрито: відповіді, що прийшли пізніше, не чіпають вікно. Не isDisplayable(): швидка відповідь могла прийти
     *  ще до першого показу вікна і губилась (голова в кабінеті на пробі 13.11). */
    private volatile boolean closed;

    // вхід
    private final JTextField loginNick = new JTextField(18);
    private final JPasswordField loginPass = new JPasswordField(18);
    private final JButton loginButton = new JButton(tr("serverAccount.login"));
    private final JLabel loginStatus = new JLabel(" ");
    // реєстрація
    private final JTextField regNick = new JTextField(18);
    private final JPasswordField regPass = new JPasswordField(18);
    private final JPasswordField regPass2 = new JPasswordField(18);
    private final JButton regButton = new JButton(tr("serverAccount.register"));
    private final JLabel regStatus = new JLabel(" ");

    /**
     * @param onChange викликається після входу, виходу, нового скіна і закриття вікна (плашка акаунта в головному вікні)
     */
    public static void showFor(Window owner, Launcher launcher, SupportService support, Supplier<Instance> selectedInstance, Runnable onChange) {
        showFor(owner, launcher, support, selectedInstance, onChange, false);
    }

    /**
     * @param forSkin відкрито кнопкою «Змінити скін»: без входу над формою підказка, навіщо входити
     */
    public static void showFor(Window owner, Launcher launcher, SupportService support, Supplier<Instance> selectedInstance,
                               Runnable onChange, boolean forSkin) {
        if (open == null || !open.isDisplayable()) {
            open = new ServerAccountDialog(owner, launcher, support, selectedInstance, onChange);
        }
        if (forSkin && ServerAccount.saved() == null) {
            open.loginStatus.setForeground(UIManager.getColor("Label.foreground"));
            open.loginStatus.setText("<html>" + SwingHelper.htmlEscape(tr("serverAccount.skinLoginHint")) + "</html>");
        }
        // 1.33 (власник 13.11): «Змінити скін» одразу на скіни, «Аккаунт на ГаммерКрафті» на особистий кабінет
        open.inner.show(open.accountCard, forSkin ? "skins" : "cabinet");
        open.openSkins = forSkin;
        open.setVisible(true);
        open.toFront();
    }

    private ServerAccountDialog(Window owner, Launcher launcher, SupportService support, Supplier<Instance> selectedInstance, Runnable onChange) {
        super(owner, tr("serverAccount.title"), ModalityType.MODELESS);
        this.launcher = launcher;
        this.onChange = onChange;
        this.api = new ServerAccount(launcher.getProperties());
        this.support = support;
        this.selectedInstance = selectedInstance;
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent e) {
                closeAsked();
            }
        });
        root.add(buildSignedOut(), "out");
        root.add(accountCard, "in");
        setContentPane(root);
        // UIScale: на Java 8 FlatLaf сам збільшує шрифти під масштаб Windows, а розмір у пікселях лишався б малим
        // 16.11.2026: не більше за екран, стиль вигляду лаунчера
        Dimension size = com.gummer.launcher.look.LookTheme.fitToScreen(this, UIScale.scale(640), UIScale.scale(600), 460, 420);
        setSize(size);
        setMinimumSize(new Dimension(Math.min(UIScale.scale(560), size.width), Math.min(UIScale.scale(480), size.height)));
        com.gummer.launcher.look.LookUi.decorate(this, owner);
        setLocationRelativeTo(owner);

        Login saved = ServerAccount.saved();
        if (saved != null) {
            showAccount(saved, false);
        } else {
            cards.show(root, "out");
        }
    }

    // ------------------------------------------------------------------ не увійшов

    private JPanel buildSignedOut() {
        JPanel p = new JPanel(new MigLayout("insets 18 20 16 20, wrap 1, fillx", "[0:0:,grow,fill]", ""));
        p.add(title(tr("serverAccount.title")));
        p.add(wrapText(tr("serverAccount.intro"), false), "gapbottom 10");

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab(tr("serverAccount.loginTab"), buildLogin());
        tabs.addTab(tr("serverAccount.registerTab"), buildRegister());
        p.add(tabs, "grow, pushy");
        return p;
    }

    private JPanel buildLogin() {
        JPanel p = new JPanel(new MigLayout("insets 14 12 10 12, wrap 2, fillx", "[right][0:0:,grow,fill]", ""));
        p.add(new JLabel(tr("serverAccount.nick")));
        p.add(loginNick);
        p.add(new JLabel(tr("serverAccount.password")));
        p.add(loginPass);
        p.add(loginButton, "skip, split 2, growx, gaptop 6");
        JButton forgot = linkButton(tr("serverAccount.forgot"));
        forgot.addActionListener(e -> forgotPassword(loginNick.getText().trim()));
        p.add(forgot, "growx 0, gaptop 6, gapleft 12");
        p.add(loginStatus, "skip, gaptop 6");
        p.add(wrapText(tr("serverAccount.loginHint"), true), "newline, span 2, growx, wmin 0, alignx left, gaptop 10");

        loginNick.setText(com.gummer.launcher.prefs.LauncherPrefs.getString("serverAccountNick", ""));
        loginButton.addActionListener(e -> doLogin());
        loginPass.addActionListener(e -> doLogin());
        return p;
    }

    private JPanel buildRegister() {
        JPanel p = new JPanel(new MigLayout("insets 14 12 10 12, wrap 2, fillx", "[right][0:0:,grow,fill]", ""));
        p.add(new JLabel(tr("serverAccount.nick")));
        p.add(regNick);
        p.add(new JLabel(tr("serverAccount.password")));
        p.add(regPass);
        p.add(new JLabel(tr("serverAccount.password2")));
        p.add(regPass2);
        p.add(regButton, "skip, gaptop 6");
        p.add(regStatus, "skip, gaptop 6");
        p.add(wrapText(tr("serverAccount.registerRules"), true), "newline, span 2, growx, wmin 0, alignx left, gaptop 10");

        regButton.addActionListener(e -> doRegister());
        regPass2.addActionListener(e -> doRegister());
        return p;
    }

    private void doLogin() {
        String nick = loginNick.getText().trim();
        String pass = new String(loginPass.getPassword());
        if (nick.isEmpty() || pass.isEmpty()) {
            fail(loginStatus, tr("serverAccount.fillBoth"));
            return;
        }
        busy(loginButton, loginStatus, tr("serverAccount.loggingIn"));
        background(() -> api.login(nick, pass), l -> {
            addLocalAccount(l.nick);
            loginPass.setText("");
            loginButton.setEnabled(true);
            loginStatus.setText(" ");
            showAccount(l, openSkins);
        }, ex -> {
            loginButton.setEnabled(true);
            fail(loginStatus, errorText(ex));
        });
    }

    private void doRegister() {
        String nick = regNick.getText().trim();
        String pass = new String(regPass.getPassword());
        String pass2 = new String(regPass2.getPassword());
        if (!NICK.matcher(nick).matches()) {
            fail(regStatus, tr("serverAccount.error.nick_invalid"));
            return;
        }
        if (!PASSWORD.matcher(pass).matches()) {
            fail(regStatus, tr("serverAccount.error.password_invalid"));
            return;
        }
        if (!pass.equals(pass2)) {
            fail(regStatus, tr("serverAccount.passwordsDiffer"));
            return;
        }
        if (pass.equalsIgnoreCase(nick)) {
            fail(regStatus, tr("serverAccount.error.password_is_nick"));
            return;
        }
        busy(regButton, regStatus, tr("serverAccount.registering"));
        background(() -> api.register(nick, pass), l -> {
            addLocalAccount(l.nick);
            regPass.setText("");
            regPass2.setText("");
            regButton.setEnabled(true);
            regStatus.setText(" ");
            showAccount(l, openSkins);
            JOptionPane.showMessageDialog(this, tr("serverAccount.registered", l.nick),
                    tr("serverAccount.title"), JOptionPane.INFORMATION_MESSAGE);
        }, ex -> {
            regButton.setEnabled(true);
            fail(regStatus, errorText(ex));
        });
    }

    // ------------------------------------------------------------------ увійшов

    private void showAccount(Login login, boolean skinsFirst) {
        accountCard.removeAll();
        skinPanels.clear();
        accountCard.add(buildCabinet(login), "cabinet");
        accountCard.add(buildSkins(login), "skins");
        accountCard.revalidate();
        accountCard.repaint();
        inner.show(accountCard, skinsFirst ? "skins" : "cabinet");
        cards.show(root, "in");
        fitContent();
        changed();
    }

    /** Сторінка скінів: як було до кабінету, плюс «Назад до кабінету». */
    private JPanel buildSkins(Login login) {
        JPanel p = new JPanel(new MigLayout("insets 18 20 16 20, wrap 1, fillx", "[0:0:,grow,fill]", ""));
        p.add(title(tr("serverAccount.signedInAs", login.nick)));
        p.add(wrapText(tr("serverAccount.signedInHint", login.nick), false), "gapbottom 6");

        JPanel skins = new JPanel(new GridLayout(1, 2, 14, 0));
        for (Target t : new Target[]{Target.STALDAYZ, Target.GUMMERCRAFT}) {
            SkinPanel sp = new SkinPanel(t, login);
            skinPanels.add(sp);
            skins.add(sp);
        }
        p.add(skins, "grow, pushy, gaptop 8");
        p.add(wrapText(tr("serverAccount.skinHint"), true), "gaptop 8");

        JButton back = new JButton(tr("serverAccount.cabinetBack"));
        back.addActionListener(e -> {
            if (skinsSettled()) inner.show(accountCard, "cabinet");
        });
        JButton close = new JButton(tr("serverAccount.close"));
        close.addActionListener(e -> closeAsked());
        p.add(back, "split 2, growx 0, gaptop 10");
        p.add(close, "growx 0, gapleft push, gaptop 10");
        return p;
    }

    // ------------------------------------------------------------------ особистий кабінет (1.33)

    /**
     * Особистий кабінет (власник 13.11): дані гравця з серверів (дія profile): коли створено акаунт, перший і
     * останній вхід, скільки награно і на яких серверах, VIP і політ з датою кінця, PvP, регіони (якщо сервер їх знає).
     */
    private JPanel buildCabinet(Login login) {
        JPanel p = new JPanel(new MigLayout("insets 18 20 16 20, wrap 1, fillx", "[0:0:,grow,fill]", ""));
        JLabel head = new JLabel();
        JLabel nick = title(login.nick);
        JLabel sub = new JLabel(tr("serverAccount.cabinet"));
        sub.setForeground(UIManager.getColor("Label.disabledForeground"));
        JPanel top = new JPanel(new MigLayout("insets 0, gap 12 0", "[][grow]", "[]0[]"));
        top.add(head, "spany 2, w 56!, h 56!");
        top.add(sub, "wrap, aligny bottom");
        top.add(nick, "aligny top");
        p.add(top, "gapbottom 8");

        JPanel body = new JPanel(new MigLayout("insets 0, wrap 1, fillx", "[0:0:,grow,fill]", ""));
        body.add(muted(tr("serverAccount.cabinetLoading")));
        JScrollPane scroll = new JScrollPane(body, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        p.add(scroll, "grow, pushy");

        JButton skin = new JButton(tr("serverAccount.cabinetChangeSkin"));
        skin.setBackground(new Color(0x3C, 0x9A, 0x3C));
        skin.setForeground(Color.WHITE);
        skin.addActionListener(e -> inner.show(accountCard, "skins"));
        JButton forgot = linkButton(tr("serverAccount.forgot"));
        forgot.addActionListener(e -> forgotPassword(login.nick));
        JButton logout = new JButton(tr("serverAccount.logout"));
        logout.addActionListener(e -> {
            ServerAccount.forget();
            loginNick.setText(login.nick);
            cards.show(root, "out");
            changed();
        });
        JButton close = new JButton(tr("serverAccount.close"));
        close.addActionListener(e -> closeAsked());
        p.add(skin, "split 4, growx 0, gaptop 10");
        p.add(forgot, "growx 0, gaptop 10");
        p.add(logout, "growx 0, gapleft push, gaptop 10");
        p.add(close, "growx 0, gaptop 10");

        background(() -> {
            byte[] png = api.ownSkin(login);
            BufferedImage img = png == null ? null : ImageIO.read(new ByteArrayInputStream(png));
            return img == null ? null : SkinRender.face(img, 56);
        }, img -> {
            if (img != null) head.setIcon(new ImageIcon(img));
        }, ex -> { /* без голови */ });
        background(() -> api.profile(login), n -> fillCabinet(body, n), ex -> {
            if (ex instanceof AccountException && ((AccountException) ex).isTokenInvalid()) {
                signedOut();
                return;
            }
            body.removeAll();
            body.add(wrapText(tr("serverAccount.cabinetError", errorText(ex)), false));
            body.revalidate();
            body.repaint();
        });
        return p;
    }

    private void fillCabinet(JPanel body, JsonNode n) {
        body.removeAll();
        boolean stats = n.path("stats").asBoolean(false);
        JPanel facts = new JPanel(new MigLayout("insets 0, wrap 2, gap 16 4", "[][grow]", ""));
        // 14.11.2026: «Акаунт створено» прибрано. Сервер з 14.11 сам кладе в first_join найранішу з дат Plan і реєстрації
        // AuthMe, тож окремий рядок реєстрації лише плутав (у gummer реєстрація 2025, а перший вхід 2020). Без Plan
        // (stats=false) «Вперше в грі» береться з реєстрації AuthMe.
        long firstJoin = stats ? n.path("first_join").asLong() : 0;
        if (firstJoin <= 0) firstJoin = n.path("registered").asLong();
        fact(facts, "serverAccount.cabinetFirstJoin", date(firstJoin));
        fact(facts, "serverAccount.cabinetLastLogin", date(n.path("last_login").asLong()));
        if (stats) fact(facts, "serverAccount.cabinetTotal", hours(n.path("total_ms").asLong()));
        body.add(facts, "gapbottom 6");

        if (!stats) {
            body.add(wrapText(tr("serverAccount.cabinetNoStats"), true), "gaptop 6");
        } else {
            body.add(section(tr("serverAccount.cabinetServers")), "gaptop 8");
            JsonNode servers = n.path("servers");
            if (servers.size() == 0) {
                body.add(muted(tr("serverAccount.cabinetNoServers")));
            } else {
                long max = 1;
                for (JsonNode s : servers) max = Math.max(max, s.path("ms").asLong());
                JPanel t = new JPanel(new MigLayout("insets 0, wrap 3, gap 12 3", "[][100:140:,fill][grow]", ""));
                for (JsonNode s : servers) {
                    long ms = s.path("ms").asLong();
                    if (ms < 60000L) continue; // зайшов і вийшов: не цікаво
                    t.add(new JLabel(serverName(s.path("name").asText())));
                    t.add(new Bar(ms / (double) max), "h 10!");
                    t.add(muted(hours(ms) + "   " + tr("serverAccount.cabinetServerLast", date(s.path("last").asLong()))));
                }
                body.add(t);
            }

            body.add(section(tr("serverAccount.cabinetPerks")), "gaptop 10");
            List<String> perks = new ArrayList<String>();
            for (JsonNode g : n.path("groups")) {
                String line = perk(g.path("name").asText(), g.path("expires").asLong());
                if (line != null) perks.add(line + "   (" + g.path("net").asText() + ")");
            }
            if (perks.isEmpty()) {
                body.add(muted(tr("serverAccount.cabinetNoPerks")));
            } else {
                for (String line : perks) body.add(new JLabel(line));
            }

            long kills = n.path("kills").asLong(), deaths = n.path("deaths").asLong();
            if (kills > 0 || deaths > 0) {
                body.add(section(tr("serverAccount.cabinetPvp")), "gaptop 10");
                body.add(new JLabel(tr("serverAccount.cabinetPvpLine", kills, deaths)));
            }
        }

        JsonNode regions = n.path("regions");
        if (regions.isArray() && regions.size() > 0) {
            body.add(section(tr("serverAccount.cabinetRegions")), "gaptop 10");
            for (JsonNode r : regions) {
                body.add(new JLabel(r.path("name").asText() + "   (" + serverName(r.path("server").asText()) + ")"));
            }
        }
        body.revalidate();
        body.repaint();
    }

    private static final Pattern VIP = Pattern.compile("(?i)vip(\\d)\\w*");

    /** Лише привілеї, цікаві гравцю: VIP, політ і все тимчасове; постійні службові групи (клани, пакети приватів) ні. */
    private static String perk(String name, long expires) {
        String title;
        Matcher m = VIP.matcher(name);
        if (m.matches()) {
            title = "VIP" + m.group(1);
        } else if ("fly".equals(name)) {
            title = tr("serverAccount.cabinetFly");
        } else if (expires > 0) {
            title = name;
        } else {
            return null;
        }
        if (expires <= 0) return title + ": " + tr("serverAccount.cabinetForever");
        long days = Math.max(0, (expires * 1000L - System.currentTimeMillis()) / 86400000L);
        return title + ": " + tr("serverAccount.cabinetUntil", date(expires * 1000L), days);
    }

    private static String serverName(String raw) {
        if (raw == null || raw.isEmpty()) return "?";
        // назви серверів у Plan (13.11: Dayz, Main, Main--2, Anarchy, Creative2, Lobby, RolePlay). З 14.11.2026 сервер
        // (launcherApi.php) сам віддає Main-RP для РП: і для RolePlay, і для старого запису Main-2 (той самий сервер до РП)
        String low = raw.toLowerCase(java.util.Locale.ROOT);
        if (low.equals("dayz") || low.startsWith("staldayz")) return "StalDayZ";
        if (low.equals("roleplay") || low.equals("rp")) return "Main-RP";
        raw = raw.replaceAll("-+", "-");
        return Character.toUpperCase(raw.charAt(0)) + raw.substring(1);
    }

    private static void fact(JPanel p, String key, String value) {
        p.add(muted(tr(key)));
        JLabel v = new JLabel(value);
        v.setFont(v.getFont().deriveFont(Font.BOLD));
        p.add(v);
    }

    private static JLabel section(String text) {
        JLabel l = new JLabel(text);
        l.setFont(l.getFont().deriveFont(Font.BOLD, l.getFont().getSize2D() + 1f));
        return l;
    }

    private static JLabel muted(String text) {
        JLabel l = new JLabel(text);
        l.setForeground(UIManager.getColor("Label.disabledForeground"));
        return l;
    }

    private static String date(long ms) {
        if (ms <= 0) return "-";
        return new SimpleDateFormat("dd.MM.yyyy").format(new Date(ms));
    }

    private static String hours(long ms) {
        if (ms < 3600000L) return tr("serverAccount.cabinetLessHour");
        double h = ms / 3600000.0;
        return tr("serverAccount.cabinetHours", h >= 10 ? String.valueOf(Math.round(h)) : String.format("%.1f", h).replace('.', ','));
    }

    /** Смужка частки часу на сервері. */
    private static final class Bar extends JComponent {
        private final double part;

        Bar(double part) {
            this.part = Math.max(0.03, Math.min(1, part));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int y = (getHeight() - 8) / 2;
            g2.setColor(new Color(128, 128, 128, 60));
            g2.fillRoundRect(0, y, getWidth(), 8, 8, 8);
            g2.setColor(new Color(0x4C, 0xAF, 0x50));
            g2.fillRoundRect(0, y, (int) Math.round(getWidth() * part), 8, 8, 8);
            g2.dispose();
        }
    }

    /** Один сервер скінів: поточний скін, вибір PNG, руки, завантаження. */
    private final class SkinPanel extends JPanel {
        private final Target target;
        private final Login login;
        private final JLabel preview = new JLabel("", SwingConstants.CENTER);
        private final JLabel state = new JLabel(" ", SwingConstants.CENTER);
        private final JRadioButton classic = new JRadioButton(tr("serverAccount.armsClassic"), true);
        private final JRadioButton slim = new JRadioButton(tr("serverAccount.armsSlim"));
        private final JButton choose = new JButton(tr("serverAccount.choosePng"));
        private final JButton upload = new JButton(tr("serverAccount.upload"));
        private BufferedImage current;
        /** Стандартний скін сервера, коли свого немає (лише для показу). */
        private BufferedImage fallback;
        private BufferedImage chosen;
        private byte[] chosenBytes;

        SkinPanel(Target target, Login login) {
            super(new MigLayout("insets 10, wrap 1, fillx", "[0:0:,grow,fill]", ""));
            this.target = target;
            this.login = login;
            setBorder(BorderFactory.createTitledBorder(target.title));
            preview.setPreferredSize(new Dimension(110, 200));
            add(preview, "h 200!");
            add(state);
            ButtonGroup g = new ButtonGroup();
            g.add(classic);
            g.add(slim);
            add(classic, "split 2, growx 0");
            add(slim, "growx 0");
            add(choose);
            add(upload);
            upload.setEnabled(false);
            classic.addActionListener(e -> redraw());
            slim.addActionListener(e -> redraw());
            choose.addActionListener(e -> choosePng());
            upload.addActionListener(e -> doUpload());
            loadCurrent();
        }

        private void loadCurrent() {
            state.setText(tr("serverAccount.skinLoading"));
            background(() -> {
                ServerAccount.Status st = api.status(target, login);
                // без скіна сервер віддає стандартного Стіва: показуємо його замість «?» (14.11.2026)
                byte[] png = api.currentSkin(target, st.uuid);
                BufferedImage img = png == null ? null : ImageIO.read(new ByteArrayInputStream(png));
                return new Object[]{st.hasSkin, img};
            }, r -> {
                boolean has = (Boolean) r[0];
                current = has ? (BufferedImage) r[1] : null;
                fallback = has ? null : (BufferedImage) r[1];
                if (chosen == null) {
                    state.setText(has && current != null ? tr("serverAccount.skinCurrent") : tr("serverAccount.skinNone"));
                    redraw();
                }
            }, ex -> {
                if (ex instanceof AccountException && ((AccountException) ex).isTokenInvalid()) {
                    signedOut();
                    return;
                }
                state.setText(errorText(ex));
            });
        }

        private void choosePng() {
            File f = new com.gummer.launcher.swing.NativeFileDialog(ServerAccountDialog.this).filter(tr("serverAccount.pngFilter"), "png").openFile();
            if (f == null) return;
            try {
                byte[] data = Files.readAllBytes(f.toPath());
                BufferedImage img = data.length > 65536 ? null : ImageIO.read(new ByteArrayInputStream(data));
                if (img == null || img.getWidth() != 64 || (img.getHeight() != 64 && img.getHeight() != 32)) {
                    state.setText(tr("serverAccount.error.skin_invalid"));
                    return;
                }
                chosen = img;
                chosenBytes = data;
                slim.setSelected(SkinRender.looksSlim(img));
                classic.setSelected(!slim.isSelected());
                String name = f.getName();
                if (name.length() > 22) name = name.substring(0, 19) + "...";
                state.setText(tr("serverAccount.skinChosen", name));
                upload.setEnabled(true);
                redraw();
            } catch (IOException e) {
                state.setText(tr("serverAccount.error.skin_invalid"));
            }
        }

        boolean hasPending() {
            return chosenBytes != null && upload.isEnabled();
        }

        void discard() {
            chosen = null;
            chosenBytes = null;
            upload.setEnabled(false);
            state.setText(current != null ? tr("serverAccount.skinCurrent") : tr("serverAccount.skinNone"));
            redraw();
        }

        private void doUpload() {
            if (chosenBytes == null) return;
            upload.setEnabled(false);
            choose.setEnabled(false);
            state.setText(tr("serverAccount.uploading"));
            final byte[] data = chosenBytes;
            final boolean isSlim = slim.isSelected();
            background(() -> {
                api.uploadSkin(target, login, data, isSlim);
                return Boolean.TRUE;
            }, ok -> {
                choose.setEnabled(true);
                current = chosen;
                chosen = null;
                chosenBytes = null;
                state.setText(tr("serverAccount.uploaded"));
                redraw();
                changed();
            }, ex -> {
                choose.setEnabled(true);
                upload.setEnabled(true);
                if (ex instanceof AccountException && ((AccountException) ex).isTokenInvalid()) {
                    signedOut();
                    return;
                }
                state.setText(errorText(ex));
            });
        }

        private void redraw() {
            BufferedImage img = chosen != null ? chosen : current != null ? current : fallback;
            preview.setIcon(img == null ? null : new ImageIcon(SkinRender.front(img, slim.isSelected(), 6)));
            preview.setText(img == null ? "?" : "");
        }
    }

    /** Збільшити вікно, якщо вміст не влазить (великий масштаб, довгі тексти), але не більше за екран. */
    private void fitContent() {
        Dimension pref = getPreferredSize();
        Rectangle screen = getGraphicsConfiguration() != null ? getGraphicsConfiguration().getBounds() : new Rectangle(Toolkit.getDefaultToolkit().getScreenSize());
        int w = Math.min(Math.max(getWidth(), pref.width), screen.width - 40);
        int h = Math.min(Math.max(getHeight(), pref.height), screen.height - 60);
        if (w != getWidth() || h != getHeight()) {
            setSize(w, h);
            setLocationRelativeTo(getOwner());
        }
    }

    private void signedOut() {
        ServerAccount.forget();
        cards.show(root, "out");
        changed();
        fail(loginStatus, tr("serverAccount.error.token_invalid"));
    }

    // ------------------------------------------------------------------ локальний акаунт, забутий пароль

    private boolean hasLocalAccount(String nick) {
        AccountList list = launcher.getAccounts();
        for (int i = 0; i < list.getSize(); i++) {
            SavedSession s = list.getElementAt(i);
            if (s.getType() == UserType.LEGACY && nick.equals(s.getUsername())) return true;
        }
        return false;
    }

    /** 1.33 (власник): після входу чи реєстрації локальний акаунт з тим самим ніком додається сам, без окремої кнопки. */
    private void addLocalAccount(String nick) {
        if (nick == null || nick.isEmpty() || hasLocalAccount(nick)) return;
        launcher.getAccounts().add(new OfflineSession(nick).toSavedSession());
        Persistence.commitAndForget(launcher.getAccounts());
    }

    /** Пароль скидають адміни вручну: форма підтримки з готовим текстом або бот, якщо форми немає. */
    private void forgotPassword(String nick) {
        String text = tr("support.passwordText", nick.isEmpty() ? "?" : nick, tr("serverAccount.forgotServer"));
        if (support != null) {
            SupportDialog.showPrefilled(this, support, selectedInstance, "StalDayZ", text);
        } else {
            SwingHelper.openURL(HELP_BOT, this);
        }
    }

    // ------------------------------------------------------------------ дрібниці

    private static String errorText(Throwable ex) {
        if (ex instanceof AccountException) {
            AccountException a = (AccountException) ex;
            if ("rate_limited".equals(a.code) && a.retryAfter > 0) {
                return tr("serverAccount.error.rate_limited_for", Math.max(1, (a.retryAfter + 59) / 60));
            }
            return a.text();
        }
        log.log(Level.WARNING, "Server account request failed", ex);
        return tr("serverAccount.error.unknown", ex.getClass().getSimpleName());
    }

    private static void busy(JButton b, JLabel status, String text) {
        b.setEnabled(false);
        status.setForeground(UIManager.getColor("Label.foreground"));
        status.setText(text);
    }

    private static void fail(JLabel status, String text) {
        status.setForeground(new Color(0xE0, 0x6C, 0x5B));
        status.setText("<html>" + SwingHelper.htmlEscape(text) + "</html>");
    }

    private <T> void background(Callable<T> work, Consumer<T> ok, Consumer<Throwable> err) {
        Thread t = new Thread(() -> {
            try {
                T r = work.call();
                SwingUtilities.invokeLater(() -> {
                    if (!closed) ok.accept(r);
                });
            } catch (Throwable e) {
                SwingUtilities.invokeLater(() -> {
                    if (!closed) err.accept(e);
                });
            }
        }, "server-account");
        t.setDaemon(true);
        t.start();
    }

    private static JLabel title(String text) {
        JLabel l = new JLabel(text);
        l.setFont(l.getFont().deriveFont(Font.BOLD, l.getFont().getSize2D() + 3f));
        return l;
    }

    private static JButton linkButton(String text) {
        JButton b = new JButton(text);
        b.setBorderPainted(false);
        b.setContentAreaFilled(false);
        b.setFocusPainted(false);
        b.setForeground(UIManager.getColor("Component.linkColor") != null ? UIManager.getColor("Component.linkColor") : new Color(0x58, 0x9D, 0xF6));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return b;
    }

    private static JTextArea wrapText(String text, boolean small) {
        JTextArea a = new JTextArea(text);
        a.setLineWrap(true);
        a.setWrapStyleWord(true);
        a.setEditable(false);
        a.setFocusable(false);
        a.setOpaque(false);
        a.setBorder(null);
        a.setFont(UIManager.getFont(small ? "small.font" : "Label.font"));
        a.setForeground(UIManager.getColor("Label.foreground"));
        return a;
    }

    private void changed() {
        if (onChange == null) return;
        try {
            onChange.run();
        } catch (RuntimeException e) {
            log.log(Level.INFO, "Account chip refresh failed", e);
        }
    }

    /** Закрити вікно, якщо немає вибраного, але не завантаженого скіна (або гравець відмовився від нього). */
    private void closeAsked() {
        if (skinsSettled()) dispose();
    }

    /**
     * 14.11.2026 (shumer): скін вибрали, бачили його в прев'ю і закрили вікно, не натиснувши «Завантажити на сервер»;
     * напис «ще не завантажено» обрізала довга назва файлу. Тепер перед виходом зі сторінки скінів питаємо.
     * true: можна йти далі.
     */
    private boolean skinsSettled() {
        for (SkinPanel sp : skinPanels) {
            if (!sp.hasPending()) continue;
            inner.show(accountCard, "skins");
            Object[] options = {tr("serverAccount.pendingUpload"), tr("serverAccount.pendingDiscard"), tr("serverAccount.pendingStay")};
            int r = JOptionPane.showOptionDialog(this, tr("serverAccount.pendingText", sp.target.title), getTitle(),
                    JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE, null, options, options[0]);
            if (r == JOptionPane.YES_OPTION) {
                sp.doUpload();
                return false;
            }
            if (r != JOptionPane.NO_OPTION) return false;
            sp.discard();
        }
        return true;
    }

    @Override
    public void dispose() {
        closed = true;
        super.dispose();
        if (open == this) open = null;
        changed();
    }
}
