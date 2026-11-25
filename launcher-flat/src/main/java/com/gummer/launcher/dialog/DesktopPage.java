/*
 * Copyright (C) 2021-2026 GummerCraft <http://gummercraft.fun>
 */

package com.gummer.launcher.dialog;

import com.gummer.launcher.look.ListModeSwitch;
import com.gummer.launcher.look.LookTheme;
import com.gummer.launcher.look.LookUi;
import com.gummer.launcher.look.WheelScroll;
import com.gummer.launcher.prefs.LauncherPrefs;
import com.skcraft.launcher.Instance;
import com.skcraft.launcher.swing.CrispIcon;
import com.skcraft.launcher.swing.InstanceTableModel;
import net.miginfocom.swing.MigLayout;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Ellipse2D;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.attribute.BasicFileAttributes;
import java.text.Collator;
import java.text.DateFormat;
import java.util.*;
import java.util.List;
import java.util.function.Consumer;

import static com.skcraft.launcher.util.SharedLocale.tr;

/**
 * 25.11.2026 (власник: «додати "Робочий стіл" усіх модпаків: сіткою і списком (на вибір), без прив'язки до категорій та
 * сортуванням за іменем, завантажувачем та датою додавання»). Сторінка на місці головної області, коли зліва вибрано
 * рядок «Робочий стіл» у «Встановлених». Усі збірки гравця одним полем, без розділів:
 * <ul>
 * <li>сітка плиток (іконка 48 px, назва, завантажувач і версія) або список рядків (ще й дата додавання);</li>
 * <li>сортування за іменем (за абеткою, без різниці великих літер), за завантажувачем (ГаммерКрафт, Ваніль, Fabric,
 * Quilt, Forge, NeoForge, інше; усередині за іменем) і за датою додавання (новіші вгорі);</li>
 * <li>клік відкриває збірку (вибирає її в списку зліва, як звичайний клік там), кругла кнопка ▶ на плитці під мишею
 * одразу запускає гру;</li>
 * <li>вибір вигляду і сортування зберігаються в {@code launcher-ui.json} ({@code desktopList}, {@code desktopSort}).</li>
 * </ul>
 * 25.11.2026 (власник: «додай можливість "пінити" модпаки (ставити зірочки), і модпаки з зірочкою завжди ... вище всіх»):
 * зірочка в лівому верхньому куті плитки (у списку перед кнопкою ▶), клік ставить чи знімає. Збірки з зірочкою стоять
 * першими за будь-якого сортування (між собою за тим самим сортуванням). Зберігаються назви збірок ({@code desktopPinned}).
 * Дата додавання це дата створення теки збірки на диску (для старих збірок, перенесених іншою програмою, може бути
 * дата перенесення). Однакова в трьох виглядах, кольори з {@link LookTheme}.
 */
public final class DesktopPage extends JPanel {

    public static final String PREF_LIST = "desktopList";
    public static final String PREF_SORT = "desktopSort";
    /** Назви збірок із зірочкою, по рядку. */
    public static final String PREF_PINS = "desktopPinned";
    private static final String[] SORTS = {"name", "loader", "date"};
    /** Порядок завантажувачів для сортування (розділи «Встановлених»). */
    private static final List<String> LOADER_ORDER = Arrays.asList("gummercraft", "vanilla", "fabric", "quilt", "forge", "neoforge", "other");

    private final InstanceTableModel model;
    private final Consumer<Instance> open;
    private final Consumer<Instance> play;
    private final Menu menu;

    /** Меню збірки по правому кліку, те саме, що в списку зліва (вікно, у якому сторінка). */
    public interface Menu {
        void show(Instance instance, Component invoker, int x, int y);
    }
    private final JLabel count = new JLabel();
    private final JComboBox<String> sortBox = new JComboBox<String>();
    private final Board board = new Board();
    private final JScrollPane scroll;
    private boolean listMode = LauncherPrefs.getBoolean(PREF_LIST, false);
    private String sort = LauncherPrefs.getString(PREF_SORT, "name");

    private static final class Item {
        Instance instance;
        String title, sub, date;
        String sortTitle;
        int loaderRank;
        long added;
        boolean pinned;
        Icon icon;
    }

    private final List<Item> items = new ArrayList<Item>();

    /**
     * @param open вибрати збірку в списку зліва (сторінка зміниться на звичайну головну область)
     * @param play вибрати і одразу запустити
     * @param menu меню збірки по правому кліку (25.11.2026, власник: «клацати пкм по іконках в робочому столі та в списку з тим
     *             самим результатом, як пкм по елементу в боковому меню»); вибір у списку зліва не міняється, сторінка лишається
     */
    public DesktopPage(InstanceTableModel model, Consumer<Instance> open, Consumer<Instance> play, Menu menu) {
        super(new BorderLayout());
        this.model = model;
        this.open = open;
        this.play = play;
        this.menu = menu;
        setOpaque(false);
        LookTheme.Palette p = LookTheme.palette();

        JPanel head = new JPanel(new MigLayout("insets 18 24 10 24, gap 0, fillx", "[]12[]push[]10[]", "[36!]"));
        head.setOpaque(false);
        head.add(LookUi.title(tr("desktop.title"), 20f));
        count.setForeground(p.text3);
        head.add(count, "aligny center");
        for (String s : SORTS) sortBox.addItem(tr("desktop.sort." + s));
        int si = Arrays.asList(SORTS).indexOf(sort);
        sortBox.setSelectedIndex(si < 0 ? 0 : si);
        sortBox.setToolTipText(tr("desktop.sort"));
        sortBox.addActionListener(e -> {
            int i = sortBox.getSelectedIndex();
            if (i < 0 || SORTS[i].equals(sort)) return;
            sort = SORTS[i];
            LauncherPrefs.set(PREF_SORT, sort);
            sortItems();
            board.relayout();
        });
        head.add(sortBox, "h 32!, wmin 190");
        // ліва половина перемикача «Сітка», права «Список» (ListModeSwitch: true = ліва)
        ListModeSwitch view = new ListModeSwitch(tr("desktop.list"), tr("desktop.grid"), !listMode, grid -> {
            listMode = !grid;
            LauncherPrefs.set(PREF_LIST, listMode);
            board.relayout();
        });
        // 25.11.2026 (власник: «кнопка "список" занадто велика відносно тексту»): ширина за текстом, як «Встановлені / Додати +»;
        // примусові 170 px перемикач ділив пропорційно довжині підписів, і зайве йшло в «Список»
        head.add(view, "h 32!");
        add(head, BorderLayout.NORTH);

        scroll = new JScrollPane(board, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(BorderFactory.createEmptyBorder(0, 14, 10, 8));
        scroll.setViewportBorder(null);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.getVerticalScrollBar().putClientProperty("FlatLaf.style",
                "width: 6; thumbArc: 999; trackArc: 999; track: #00000000; thumb: #FFFFFF33");
        scroll.getViewport().addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override
            public void componentResized(java.awt.event.ComponentEvent e) {
                board.relayout(); // інша ширина: інша кількість плиток у ряду і висота поля
            }
        });
        WheelScroll.install(scroll, false); // як на сторінці CurseForge: 72 px за поділку, без анімації
        add(scroll, BorderLayout.CENTER);
    }

    /** Перечитати збірки (вікно показує сторінку чи список змінився). */
    public void refresh() {
        items.clear();
        DateFormat df = DateFormat.getDateInstance(DateFormat.MEDIUM);
        Set<String> pins = pins();
        for (Instance i : model.localBuilds()) {
            Item it = new Item();
            it.instance = i;
            it.title = i.isVanilla() ? model.pickerTitleOf(i) : i.getTitle() != null ? i.getTitle() : i.getName();
            String section = InstanceTableModel.installedSectionOf(i);
            it.loaderRank = Math.max(0, LOADER_ORDER.indexOf(section));
            String loader = tr("launcher.installed.section." + section);
            String v = model.versionLabelOf(i);
            boolean num = !v.isEmpty() && Character.isDigit(v.charAt(0));
            it.sub = i.isVanilla() && !num ? loader : num ? loader + " " + v : loader;
            it.sortTitle = it.title;
            it.added = addedAt(i);
            it.date = it.added > 0 ? df.format(new Date(it.added)) : "";
            it.icon = bigIcon(model.iconOf(i));
            it.pinned = pins.contains(i.getName());
            items.add(it);
        }
        sortItems();
        count.setText(tr("desktop.count", items.size()));
        board.relayout();
    }

    private void sortItems() {
        final Collator collator = Collator.getInstance(new Locale("uk", "UA"));
        collator.setStrength(Collator.SECONDARY);
        Comparator<Item> byName = (a, b) -> collator.compare(a.sortTitle, b.sortTitle);
        Comparator<Item> c;
        if ("loader".equals(sort)) {
            c = Comparator.<Item>comparingInt(a -> a.loaderRank).thenComparing(byName);
        } else if ("date".equals(sort)) {
            c = Comparator.<Item>comparingLong(a -> -a.added).thenComparing(byName);
        } else {
            c = byName;
        }
        Collections.sort(items, Comparator.<Item, Boolean>comparing(a -> !a.pinned).thenComparing(c)); // із зірочкою першими
    }

    private static Set<String> pins() {
        Set<String> out = new LinkedHashSet<String>();
        for (String s : LauncherPrefs.getString(PREF_PINS, "").split("\n")) if (!s.trim().isEmpty()) out.add(s.trim());
        return out;
    }

    /** Поставити чи зняти зірочку: зберегти, пересортувати. */
    private void togglePin(Item it) {
        Set<String> pins = pins();
        it.pinned = !it.pinned;
        if (it.pinned) pins.add(it.instance.getName());
        else pins.remove(it.instance.getName());
        LauncherPrefs.set(PREF_PINS, String.join("\n", pins));
        sortItems();
        board.relayout();
    }

    /** Дата створення теки збірки (мс), 0 якщо невідома. */
    private static long addedAt(Instance i) {
        File dir = i.getDir();
        if (dir == null || !dir.isDirectory()) return 0;
        try {
            BasicFileAttributes a = Files.readAttributes(dir.toPath(), BasicFileAttributes.class);
            long t = a.creationTime().toMillis();
            return t > 0 ? t : dir.lastModified();
        } catch (Exception e) {
            return dir.lastModified();
        }
    }

    /** Іконка 48 px: картинки перераховуються чітко (CrispIcon), намальовані іконки рядків масштабуються при малюванні. */
    private static Icon bigIcon(Icon small) {
        if (small instanceof CrispIcon) return ((CrispIcon) small).resized(Board.ICON, Board.ICON);
        if (small instanceof ImageIcon && ((ImageIcon) small).getImage() != null) {
            return new CrispIcon(((ImageIcon) small).getImage(), Board.ICON, Board.ICON);
        }
        return small;
    }

    /** Поле з плитками або рядками: усе малюється тут, без компонента на кожну збірку. */
    private final class Board extends JComponent implements Scrollable {
        static final int TILE_W = 156, TILE_H = 140, ROW_H = 58, GAP = 10, ICON = 48, PLAY = 30, STAR = 22;
        private int hover = -1;
        private boolean hoverPlay, hoverStar;
        private int cols = 1;

        Board() {
            setOpaque(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setFocusable(false);
            MouseAdapter m = new MouseAdapter() {
                @Override
                public void mouseMoved(MouseEvent e) {
                    track(e.getPoint());
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    hover = -1;
                    hoverPlay = false;
                    hoverStar = false;
                    setToolTipText(null);
                    repaint();
                }

                @Override
                public void mousePressed(MouseEvent e) {
                    popup(e);
                }

                @Override
                public void mouseReleased(MouseEvent e) {
                    popup(e); // Windows показує меню на відпусканні, macOS і Linux на натисканні
                }

                private void popup(MouseEvent e) {
                    if (!e.isPopupTrigger() || menu == null) return;
                    int i = indexAt(e.getPoint());
                    if (i < 0) return;
                    hover = i;
                    repaint();
                    menu.show(items.get(i).instance, Board.this, e.getX(), e.getY());
                }

                @Override
                public void mouseClicked(MouseEvent e) {
                    if (!SwingUtilities.isLeftMouseButton(e)) return;
                    int i = indexAt(e.getPoint());
                    if (i < 0) return;
                    Instance inst = items.get(i).instance;
                    if (onStar(i, e.getPoint())) togglePin(items.get(i));
                    else if (onPlay(i, e.getPoint())) play.accept(inst);
                    else open.accept(inst);
                }
            };
            addMouseListener(m);
            addMouseMotionListener(m);
            ToolTipManager.sharedInstance().registerComponent(this);
        }

        void relayout() {
            hover = -1;
            revalidate();
            repaint();
        }

        private int width() {
            Container parent = getParent();
            return parent instanceof JViewport ? parent.getWidth() : getWidth();
        }

        private int columns() {
            return listMode ? 1 : Math.max(1, (width() + GAP) / (TILE_W + GAP));
        }

        /** Плитки розтягуються, щоб ряд займав усю ширину (без порожньої смуги праворуч). */
        private int tileW() {
            int c = columns();
            return Math.max(TILE_W, (width() - GAP * (c - 1)) / c);
        }

        Rectangle bounds(int i) {
            if (listMode) return new Rectangle(0, i * (ROW_H + GAP / 2), width(), ROW_H);
            int c = columns(), w = tileW();
            return new Rectangle((i % c) * (w + GAP), (i / c) * (TILE_H + GAP), w, TILE_H);
        }

        private Rectangle playBounds(int i) {
            Rectangle r = bounds(i);
            if (listMode) return new Rectangle(r.x + r.width - PLAY - 14, r.y + (r.height - PLAY) / 2, PLAY, PLAY);
            return new Rectangle(r.x + r.width - PLAY - 8, r.y + 8, PLAY, PLAY);
        }

        /** Зірочка: у сітці лівий верхній кут, у списку перед кнопкою ▶. */
        private Rectangle starBounds(int i) {
            Rectangle r = bounds(i);
            if (listMode) return new Rectangle(r.x + r.width - PLAY - 14 - STAR - 12, r.y + (r.height - STAR) / 2, STAR, STAR);
            return new Rectangle(r.x + 10, r.y + 10, STAR, STAR);
        }

        boolean onStar(int i, Point p) {
            Rectangle b = starBounds(i);
            b.grow(3, 3); // трохи більша зона кліку, ніж сама зірочка
            return b.contains(p);
        }

        int indexAt(Point p) {
            for (int i = 0; i < items.size(); i++) {
                if (bounds(i).contains(p)) return i;
            }
            return -1;
        }

        boolean onPlay(int i, Point p) {
            return playBounds(i).contains(p);
        }

        private void track(Point p) {
            int i = indexAt(p);
            boolean pl = i >= 0 && onPlay(i, p);
            boolean st = i >= 0 && onStar(i, p);
            if (i != hover || pl != hoverPlay || st != hoverStar) {
                hover = i;
                hoverPlay = pl;
                hoverStar = st;
                setToolTipText(i < 0 ? null : pl ? tr("desktop.play") : st ? tr(items.get(i).pinned ? "desktop.unpin" : "desktop.pin")
                        : items.get(i).title + " · " + items.get(i).sub
                        + (items.get(i).date.isEmpty() ? "" : " · " + tr("desktop.added", items.get(i).date)));
                repaint();
            }
        }

        @Override
        public Dimension getPreferredSize() {
            int n = items.size();
            if (n == 0) return new Dimension(10, 10);
            if (listMode) return new Dimension(10, n * (ROW_H + GAP / 2));
            int c = columns();
            int rows = (n + c - 1) / c;
            return new Dimension(10, rows * (TILE_H + GAP));
        }

        @Override
        protected void paintComponent(Graphics g) {
            LookTheme.Palette p = LookTheme.palette();
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            com.gummer.launcher.look.TextAA.apply(g2);
            Rectangle clip = g2.getClipBounds();
            Font base = UIManager.getFont("Label.font");
            Font titleFont = base.deriveFont(Font.BOLD, 13.5f);
            Font subFont = base.deriveFont(Font.PLAIN, 12f);
            int arc = Math.min(18, p.radius * 2);
            for (int i = 0; i < items.size(); i++) {
                Rectangle r = bounds(i);
                if (clip != null && !clip.intersects(r)) continue;
                Item it = items.get(i);
                boolean hov = i == hover;
                Color fill = p.glass() ? new Color(255, 255, 255, hov ? 30 : 14) : hov ? p.surface2 : p.surface;
                g2.setColor(fill);
                g2.fillRoundRect(r.x, r.y, r.width, r.height, arc, arc);
                g2.setColor(p.glass() ? new Color(255, 255, 255, hov ? 60 : 30) : p.line);
                g2.drawRoundRect(r.x, r.y, r.width - 1, r.height - 1, arc, arc);
                if (listMode) {
                    paintIcon(g2, it.icon, r.x + 12, r.y + (r.height - 36) / 2, 36);
                    int tx = r.x + 60;
                    int dateW = 0;
                    if (!it.date.isEmpty()) {
                        g2.setFont(subFont);
                        dateW = g2.getFontMetrics().stringWidth(it.date);
                        g2.setColor(p.text3);
                        g2.drawString(it.date, r.x + r.width - PLAY - 30 - STAR - 14 - dateW, r.y + r.height / 2 + 4);
                    }
                    int maxW = r.width - (tx - r.x) - PLAY - 50 - STAR - 14 - dateW;
                    g2.setFont(titleFont);
                    g2.setColor(p.text);
                    g2.drawString(fit(g2, it.title, maxW), tx, r.y + 24);
                    g2.setFont(subFont);
                    g2.setColor(p.text3);
                    g2.drawString(fit(g2, it.sub, maxW), tx, r.y + 42);
                } else {
                    paintIcon(g2, it.icon, r.x + (r.width - ICON) / 2, r.y + 18, ICON);
                    int maxW = r.width - 20;
                    g2.setFont(titleFont);
                    g2.setColor(p.text);
                    String t = fit(g2, it.title, maxW);
                    g2.drawString(t, r.x + (r.width - g2.getFontMetrics().stringWidth(t)) / 2, r.y + 92);
                    g2.setFont(subFont);
                    g2.setColor(p.text3);
                    String s = fit(g2, it.sub, maxW);
                    g2.drawString(s, r.x + (r.width - g2.getFontMetrics().stringWidth(s)) / 2, r.y + 112);
                }
                if (hov || listMode) paintPlay(g2, p, playBounds(i), hov && hoverPlay, hov);
                if (it.pinned || hov) paintStar(g2, p, starBounds(i), it.pinned, hov && hoverStar);
            }
            g2.dispose();
        }

        private void paintPlay(Graphics2D g2, LookTheme.Palette p, Rectangle b, boolean hot, boolean rowHover) {
            Color c = hot ? p.accentHover : rowHover ? p.accent : new Color(p.accent.getRed(), p.accent.getGreen(), p.accent.getBlue(), 120);
            g2.setColor(c);
            g2.fill(new Ellipse2D.Double(b.x, b.y, b.width, b.height));
            g2.setColor(p.onAccent);
            int cx = b.x + b.width / 2 + 1, cy = b.y + b.height / 2;
            Polygon tri = new Polygon(new int[]{cx - 5, cx - 5, cx + 6}, new int[]{cy - 7, cy + 7, cy}, 3);
            g2.fill(tri);
        }

        /** Зірочка: із зірочкою жовта заповнена, без неї контур (видно під мишею). */
        private void paintStar(Graphics2D g2, LookTheme.Palette p, Rectangle b, boolean on, boolean hot) {
            double cx = b.getCenterX(), cy = b.getCenterY() + 0.5, ro = b.width / 2.0, ri = ro * 0.45;
            java.awt.geom.Path2D.Double star = new java.awt.geom.Path2D.Double();
            for (int k = 0; k < 10; k++) {
                double a = -Math.PI / 2 + k * Math.PI / 5, rr = k % 2 == 0 ? ro : ri;
                if (k == 0) star.moveTo(cx + rr * Math.cos(a), cy + rr * Math.sin(a));
                else star.lineTo(cx + rr * Math.cos(a), cy + rr * Math.sin(a));
            }
            star.closePath();
            Color gold = new Color(0xF5, 0xC5, 0x42);
            if (on) {
                g2.setColor(hot ? gold.brighter() : gold);
                g2.fill(star);
            } else {
                g2.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.setColor(hot ? gold : new Color(p.text3.getRed(), p.text3.getGreen(), p.text3.getBlue(), 200));
                g2.draw(star);
            }
        }

        private void paintIcon(Graphics2D g2, Icon icon, int x, int y, int size) {
            if (icon == null) return;
            if (icon.getIconWidth() == size) {
                icon.paintIcon(this, g2, x, y);
                return;
            }
            Graphics2D gi = (Graphics2D) g2.create();
            gi.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            double s = size / (double) Math.max(1, icon.getIconWidth());
            gi.translate(x, y);
            gi.scale(s, s);
            icon.paintIcon(this, gi, 0, 0);
            gi.dispose();
        }

        private String fit(Graphics2D g2, String s, int maxW) {
            if (s == null) return "";
            FontMetrics fm = g2.getFontMetrics();
            if (fm.stringWidth(s) <= maxW) return s;
            String dots = "…";
            int end = s.length();
            while (end > 0 && fm.stringWidth(s.substring(0, end) + dots) > maxW) end--;
            return s.substring(0, end).trim() + dots;
        }

        @Override
        public Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }

        @Override
        public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
            return 24;
        }

        @Override
        public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
            return Math.max(24, visibleRect.height - 40);
        }

        @Override
        public boolean getScrollableTracksViewportWidth() {
            return true;
        }

        @Override
        public boolean getScrollableTracksViewportHeight() {
            return false;
        }
    }
}
