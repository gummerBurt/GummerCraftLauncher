/*
 * Copyright (C) 2021-2026 GummerCraft <http://gummercraft.fun>
 */

package com.gummer.launcher.look;

import javax.swing.*;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.List;

/**
 * Керування лаунчером без миші: геймпадом Steam Deck (шаблон Steam Input з установки шле стрілки, Enter і Esc) і
 * просто клавіатурою (21.11.2026, власник: «Бігання по меню лаунчера стрілками, кнопки підтвердити/повернутись»).
 *
 * <ul>
 *   <li>Стрілки переводять вибір на найближчий елемент у той бік (за розташуванням на екрані, а не за порядком Tab):
 *   кнопки, плашки, список збірок, поля. Елементи, які раніше не брали фокус (список збірок, плашки), беруть його,
 *   щойно до них дійшли стрілкою. У списку вгору / вниз ходять рядками, ліворуч / праворуч виводять зі списку.</li>
 *   <li>Enter натискає кнопку у фокусі (у Swing це робив лише пробіл). Елемент, що реагує лише на мишу (плашки,
 *   картки), отримує клік лівою кнопкою в центр.</li>
 *   <li>Вибраний елемент обводиться рамкою поверх вікна, поки гравець ходить стрілками ({@link FocusVisible}): у
 *   власних плашок своєї рамки фокусу немає.</li>
 *   <li>Esc закриває вікна ({@link EscCloses}).</li>
 * </ul>
 *
 * <p>Стрілку, яку елемент обробив сам (рядки таблиці, перемикач «Встановлені / Додати +», текстове поле, випадний
 * список, повзунок), не перехоплює: перехід робиться лише тоді, коли клавіша нікому не знадобилась.</p>
 */
public final class PadNav {

    private static boolean installed;

    private PadNav() {
    }

    /**
     * 21.11.2026: лише там, де є геймпад Steam: Linux (Steam Deck), лаунчер запущено зі Steam (Steam додає змінні
     * SteamGameId / SteamAppId / SteamDeck) або ігровий режим (gamescope). На Windows і Mac, і на Linux без Steam
     * лаунчер поводиться як раніше. GC_PADNAV=1 вмикає примусово (перевірка), GC_PADNAV=0 вимикає.
     */
    public static boolean wanted() {
        String force = System.getenv("GC_PADNAV");
        if ("1".equals(force)) return true;
        if ("0".equals(force)) return false;
        String os = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT);
        if (!os.contains("linux")) return false;
        return env("SteamGameId") || env("SteamAppId") || "1".equals(System.getenv("SteamDeck"))
                || env("GAMESCOPE_WAYLAND_DISPLAY") || String.valueOf(System.getenv("XDG_CURRENT_DESKTOP")).toLowerCase(java.util.Locale.ROOT).contains("gamescope");
    }

    private static boolean env(String name) {
        String v = System.getenv(name);
        return v != null && !v.isEmpty() && !"0".equals(v);
    }

    public static boolean installed() {
        return installed;
    }

    public static void install() {
        if (installed || !wanted()) return;
        installed = true;
        KeyboardFocusManager km = KeyboardFocusManager.getCurrentKeyboardFocusManager();
        km.addKeyEventDispatcher(PadNav::before);
        km.addKeyEventPostProcessor(PadNav::after);
        km.addPropertyChangeListener("focusOwner", e -> Ring.update());
    }

    private static boolean arrow(int k) {
        return k == KeyEvent.VK_UP || k == KeyEvent.VK_DOWN || k == KeyEvent.VK_LEFT || k == KeyEvent.VK_RIGHT;
    }

    private static boolean list(Component c) {
        return c instanceof JTable || c instanceof JList || c instanceof JTree;
    }

    /** Елементи, де стрілки свої: багаторядковий текст, повзунки, лічильники, відкритий випадний список. */
    private static boolean skip(Component c) {
        if (c instanceof JTextComponent && !(c instanceof JTextField)) return true;
        if (c instanceof JComboBox && ((JComboBox<?>) c).isPopupVisible()) return true;
        JComboBox<?> box = (JComboBox<?>) SwingUtilities.getAncestorOfClass(JComboBox.class, c);
        if (box != null && box.isPopupVisible()) return true;
        return c instanceof JSlider || c instanceof JScrollBar || c instanceof JSpinner
                || SwingUtilities.getAncestorOfClass(JSpinner.class, c) != null;
    }

    /** Поле в рядку: ліворуч / праворуч виходять з нього, лише коли курсор уже на краю тексту. */
    private static boolean fieldEdge(JTextField t, int k) {
        if (t.getSelectionStart() != t.getSelectionEnd()) return false;
        int p = t.getCaretPosition();
        return k == KeyEvent.VK_LEFT ? p == 0 : p == t.getDocument().getLength();
    }

    /** До компонента: Enter на кнопці, стрілки без фокусу на елементі, ліворуч / праворуч зі списку. */
    private static boolean before(KeyEvent e) {
        if (gameKeys(e)) return true;
        if (e.getID() != KeyEvent.KEY_PRESSED || e.getModifiersEx() != 0) return false;
        int k = e.getKeyCode();
        if (!arrow(k) && k != KeyEvent.VK_ENTER) return false;
        if (MenuSelectionManager.defaultManager().getSelectedPath().length > 0) return false;
        Component f = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
        if (arrow(k) && (f == null || f instanceof Window || f instanceof JRootPane)) {
            Window w = f instanceof Window ? (Window) f
                    : f == null ? KeyboardFocusManager.getCurrentKeyboardFocusManager().getActiveWindow()
                    : SwingUtilities.getWindowAncestor(f);
            Component first = w == null ? null : first(w);
            if (first == null) return false;
            go(first);
            return true;
        }
        if (!(f instanceof JComponent) || !f.isShowing() || skip(f)) return false;
        if (k == KeyEvent.VK_ENTER) {
            if (f instanceof JTextField || f instanceof JComboBox || SwingUtilities.getAncestorOfClass(JComboBox.class, f) != null) return false;
            if (list(f)) return true; // у списку Enter нічого не робить (раніше переводив на наступний рядок)
            if (f instanceof AbstractButton) {
                AbstractButton b = (AbstractButton) f;
                if (b.isEnabled()) b.doClick();
                return true;
            }
            if (f instanceof ListModeSwitch) return false; // сам обробляє Enter
            InputMap own = ((JComponent) f).getInputMap(JComponent.WHEN_FOCUSED);
            if (own != null && own.get(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0)) != null) return false; // полиця Бібліотеки
            click((JComponent) f);
            return true;
        }
        boolean side = k == KeyEvent.VK_LEFT || k == KeyEvent.VK_RIGHT;
        if (side && (list(f) || (f instanceof JTextField && fieldEdge((JTextField) f, k)))) {
            Component next = nearest(f, k);
            if (next != null) go(next);
            return next != null;
        }
        // кнопки і плашки: переходимо одразу, до того як стрілку забере прокрутка навколо (JScrollPane гортає нею).
        // Свою прив'язку стрілки (WHEN_FOCUSED) і перемикач вкладок не чіпаємо; якщо в той бік нічого, гортає прокрутка
        if (list(f) || f instanceof JTextField || f instanceof JComboBox || SwingUtilities.getAncestorOfClass(JComboBox.class, f) != null) return false;
        if (f instanceof ListModeSwitch && side) return false;
        boolean tabsDown = f instanceof JTabbedPane && !side; // вкладки: ліворуч / праворуч свої, вгору / вниз виходимо
        InputMap im = ((JComponent) f).getInputMap(JComponent.WHEN_FOCUSED);
        if (!tabsDown && im != null && im.get(KeyStroke.getKeyStroke(k, 0)) != null) return false;
        Component next = nearest(f, k);
        if (next == null) return false;
        go(next);
        return true;
    }

    /** Shift натиснули і ще нічого іншого не натискали (кнопка B геймпада). */
    private static boolean shiftAlone;

    /**
     * Ігрова розкладка Steam Input (одна на лаунчер і гру, бо гра йде під тим самим ярликом Steam) шле в лаунчер
     * клавіші гри: A = пробіл (стрибок), B = Shift (присісти), лівий стік = WASD. Поза текстовим полем лаунчер читає
     * їх як Enter, Esc і стрілки. Пробіл на кнопці і випадному списку Swing і так натискає, його не чіпаємо.
     */
    private static boolean gameKeys(KeyEvent e) {
        KeyboardFocusManager km = KeyboardFocusManager.getCurrentKeyboardFocusManager();
        Component f = km.getFocusOwner();
        boolean text = f instanceof JTextComponent;
        boolean menu = MenuSelectionManager.defaultManager().getSelectedPath().length > 0;
        int id = e.getID(), k = e.getKeyCode();
        if (id == KeyEvent.KEY_PRESSED) {
            shiftAlone = k == KeyEvent.VK_SHIFT && (e.getModifiersEx() & ~KeyEvent.SHIFT_DOWN_MASK) == 0;
        } else if (id == KeyEvent.KEY_RELEASED && k == KeyEvent.VK_SHIFT) {
            boolean esc = shiftAlone && !text && !menu;
            shiftAlone = false;
            if (esc) {
                Component t = f != null ? f : km.getActiveWindow();
                if (t != null) {
                    long w = e.getWhen();
                    km.dispatchEvent(new KeyEvent(t, KeyEvent.KEY_PRESSED, w, 0, KeyEvent.VK_ESCAPE, KeyEvent.CHAR_UNDEFINED));
                    km.dispatchEvent(new KeyEvent(t, KeyEvent.KEY_RELEASED, w, 0, KeyEvent.VK_ESCAPE, KeyEvent.CHAR_UNDEFINED));
                }
            }
            return false;
        }
        if (text || menu || (f != null && skip(f))) return false;
        boolean nativeSpace = f instanceof AbstractButton || f instanceof JComboBox || SwingUtilities.getAncestorOfClass(JComboBox.class, f) != null;
        if (id == KeyEvent.KEY_TYPED) {
            char ch = Character.toLowerCase(e.getKeyChar());
            return ch == 'w' || ch == 'a' || ch == 's' || ch == 'd' || (ch == ' ' && !nativeSpace);
        }
        if (e.getModifiersEx() != 0) return false;
        int to = k == KeyEvent.VK_W ? KeyEvent.VK_UP : k == KeyEvent.VK_S ? KeyEvent.VK_DOWN
                : k == KeyEvent.VK_A ? KeyEvent.VK_LEFT : k == KeyEvent.VK_D ? KeyEvent.VK_RIGHT
                : k == KeyEvent.VK_SPACE && !nativeSpace ? KeyEvent.VK_ENTER : 0;
        if (to == 0) return false;
        Component t = f != null ? f : km.getActiveWindow();
        if (t != null) km.dispatchEvent(new KeyEvent(t, id, e.getWhen(), 0, to, KeyEvent.CHAR_UNDEFINED));
        return true;
    }

    /** Після компонента: стрілку ніхто не використав, переходимо. */
    private static boolean after(KeyEvent e) {
        if (e.getID() != KeyEvent.KEY_PRESSED || e.getModifiersEx() != 0 || !arrow(e.getKeyCode())) return false;
        Ring.update(); // рядок у списку міг змінитись
        if (e.isConsumed()) return false;
        if (MenuSelectionManager.defaultManager().getSelectedPath().length > 0) return false;
        Component f = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
        if (!(f instanceof JComponent) || !f.isShowing() || skip(f) || list(f)) return false;
        int k = e.getKeyCode();
        // власні елементи з KeyListener ліворуч / праворуч обробляють самі (перемикач «Встановлені / Додати +»)
        if (f instanceof ListModeSwitch && (k == KeyEvent.VK_LEFT || k == KeyEvent.VK_RIGHT)) return false;
        Component next = nearest(f, k);
        if (next == null) return false;
        go(next);
        e.consume();
        return true;
    }

    private static void go(Component c) {
        FocusVisible.keyboard();
        if (!c.isFocusable()) c.setFocusable(true);
        c.requestFocusInWindow();
        if (c instanceof JComponent) ((JComponent) c).scrollRectToVisible(new Rectangle(0, 0, c.getWidth(), c.getHeight()));
        Ring.update();
    }

    /** Клік лівою кнопкою миші в центр елемента: для плашок і карток, які реагують лише на мишу. */
    private static void click(JComponent c) {
        int x = c.getWidth() / 2, y = c.getHeight() / 2;
        long t = System.currentTimeMillis();
        c.dispatchEvent(new MouseEvent(c, MouseEvent.MOUSE_PRESSED, t, MouseEvent.BUTTON1_DOWN_MASK, x, y, 1, false, MouseEvent.BUTTON1));
        c.dispatchEvent(new MouseEvent(c, MouseEvent.MOUSE_RELEASED, t, 0, x, y, 1, false, MouseEvent.BUTTON1));
        c.dispatchEvent(new MouseEvent(c, MouseEvent.MOUSE_CLICKED, t, 0, x, y, 1, false, MouseEvent.BUTTON1));
    }

    /** Найближчий елемент у бік стрілки, у тому самому вікні. */
    static Component nearest(Component from, int key) {
        Window w = SwingUtilities.getWindowAncestor(from);
        if (w == null) return null;
        Rectangle a = bounds(from, w);
        if (from instanceof JTabbedPane) { // від смужки вибраної вкладки, а не від усього вмісту
            JTabbedPane tp = (JTabbedPane) from;
            int i = tp.getSelectedIndex();
            Rectangle tab = i >= 0 ? tp.getBoundsAt(i) : null;
            if (tab != null) a = SwingUtilities.convertRectangle(tp, tab, w);
        }
        List<Component> all = new ArrayList<Component>();
        collect(w, all);
        Component best = null;
        double bestScore = Double.MAX_VALUE;
        double ax = a.getCenterX(), ay = a.getCenterY();
        for (Component c : all) {
            if (c == from || SwingUtilities.isDescendingFrom(from, c)) continue;
            if (!(from instanceof JTabbedPane) && SwingUtilities.isDescendingFrom(c, from)) continue;
            Rectangle b = bounds(c, w);
            if (b.isEmpty()) continue;
            double main, cross;
            if (key == KeyEvent.VK_RIGHT) {
                if (b.getMinX() < ax) continue;
                main = b.getMinX() - a.getMaxX();
                cross = gap(a.getMinY(), a.getMaxY(), b.getMinY(), b.getMaxY());
            } else if (key == KeyEvent.VK_LEFT) {
                if (b.getMaxX() > ax) continue;
                main = a.getMinX() - b.getMaxX();
                cross = gap(a.getMinY(), a.getMaxY(), b.getMinY(), b.getMaxY());
            } else if (key == KeyEvent.VK_DOWN) {
                if (b.getMinY() < ay) continue;
                main = b.getMinY() - a.getMaxY();
                cross = gap(a.getMinX(), a.getMaxX(), b.getMinX(), b.getMaxX());
            } else {
                if (b.getMaxY() > ay) continue;
                main = a.getMinY() - b.getMaxY();
                cross = gap(a.getMinX(), a.getMaxX(), b.getMinX(), b.getMaxX());
            }
            // елементи одного ряду трохи заходять один на одного: від'ємну відстань рахуємо як нуль
            double score = Math.max(0, main) + cross * 3;
            if (score < bestScore) {
                bestScore = score;
                best = c;
            }
        }
        return best;
    }

    /** З чого почати: найбільший список (збірки) або перший елемент зверху ліворуч. */
    static Component first(Window w) {
        List<Component> all = new ArrayList<Component>();
        collect(w, all);
        Component best = null;
        long bestArea = 0;
        for (Component c : all) {
            if (!list(c)) continue;
            Rectangle b = bounds(c, w);
            long area = (long) b.width * b.height;
            if (area > bestArea) {
                bestArea = area;
                best = c;
            }
        }
        if (best != null) return best;
        double bestScore = Double.MAX_VALUE;
        for (Component c : all) {
            Rectangle b = bounds(c, w);
            if (b.isEmpty()) continue;
            double s = b.getMinY() * 2 + b.getMinX();
            if (s < bestScore) {
                bestScore = s;
                best = c;
            }
        }
        return best;
    }

    /** Відстань між двома відрізками на осі (0, якщо перетинаються). */
    private static double gap(double a0, double a1, double b0, double b1) {
        if (b1 < a0) return a0 - b1;
        if (b0 > a1) return b0 - a1;
        return 0;
    }

    /** Видима частина елемента в координатах вікна (частина, захована прокруткою, не рахується). */
    static Rectangle bounds(Component c, Window w) {
        Rectangle vis = c instanceof JComponent ? ((JComponent) c).getVisibleRect() : new Rectangle(0, 0, c.getWidth(), c.getHeight());
        if (vis.isEmpty()) return new Rectangle();
        return SwingUtilities.convertRectangle(c, vis, w);
    }

    /**
     * Куди можна перейти стрілкою: кнопки, списки, поля, випадні списки, повзунки, а з власних елементів лише ті, що
     * реагують на мишу (плашки, картки). Фони, підписи й панелі пропускаються.
     */
    private static boolean target(Component c) {
        if (!(c instanceof JComponent) || !((JComponent) c).isRequestFocusEnabled()) return false;
        if (c instanceof AbstractButton || list(c) || c instanceof JComboBox || c instanceof JSlider || c instanceof JSpinner) return true;
        if (c instanceof JTextComponent) return ((JTextComponent) c).isEditable() && c.isFocusable();
        // клікабельні панелі й підписи (плашка акаунта, скіни): свій курсор-рука і обробник миші
        if (c.isCursorSet() && c.getCursor().getType() == Cursor.HAND_CURSOR && c.getMouseListeners().length > 0
                && !(c instanceof JRootPane) && !(c instanceof JLayeredPane)) return true;
        if (c instanceof JLabel || c instanceof JPanel || c instanceof JRootPane || c instanceof JLayeredPane
                || c instanceof JViewport || c instanceof JScrollPane || c instanceof JScrollBar
                || c instanceof JToolTip || c instanceof JSeparator || c instanceof Ring) return false;
        return c.getMouseListeners().length > 0 && c.getWidth() >= 8 && c.getHeight() >= 8;
    }

    private static void collect(Container p, List<Component> out) {
        for (Component c : p.getComponents()) {
            if (!c.isShowing() || !c.isEnabled()) continue;
            // заголовок вікна FlatLaf (згорнути, розгорнути, закрити): згорнуте вікно в ігровому режимі Steam Deck не
            // повернути, а закриває Esc
            if (c.getClass().getName().contains("TitlePane")) continue;
            if (target(c)) out.add(c);
            if (c instanceof Container && !list(c) && !(c instanceof JComboBox) && !(c instanceof JSpinner)) {
                collect((Container) c, out);
            }
        }
    }

    /** Рамка навколо вибраного елемента поверх вікна; мишу не перехоплює. */
    static final class Ring extends JComponent {
        private static final String KEY = "gc.padRing";

        static void update() {
            if (!installed) return; // без Steam (Windows, Mac) рамки немає, як і раніше
            KeyboardFocusManager km = KeyboardFocusManager.getCurrentKeyboardFocusManager();
            Window aw = km.getActiveWindow();
            for (Window w : Window.getWindows()) {
                if (!(w instanceof RootPaneContainer) || !w.isShowing()) continue;
                JLayeredPane lp = ((RootPaneContainer) w).getLayeredPane();
                Ring r = (Ring) lp.getClientProperty(KEY);
                if (r == null) {
                    if (w != aw || !FocusVisible.on()) continue;
                    r = new Ring();
                    lp.putClientProperty(KEY, r);
                    lp.add(r, JLayeredPane.DRAG_LAYER);
                    final Ring ring = r;
                    lp.addComponentListener(new java.awt.event.ComponentAdapter() {
                        @Override
                        public void componentResized(java.awt.event.ComponentEvent e) {
                            ring.setBounds(0, 0, e.getComponent().getWidth(), e.getComponent().getHeight());
                        }
                    });
                }
                r.setBounds(0, 0, lp.getWidth(), lp.getHeight());
                r.repaint();
            }
        }

        @Override
        public boolean contains(int x, int y) {
            return false;
        }

        @Override
        protected void paintComponent(Graphics g) {
            if (!FocusVisible.on()) return;
            Component f = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
            if (f == null || !f.isShowing() || SwingUtilities.getWindowAncestor(f) != SwingUtilities.getWindowAncestor(this)) return;
            if (f instanceof JRootPane || f instanceof Window) return;
            Rectangle r;
            if (f instanceof JTable && ((JTable) f).getSelectedRow() >= 0) {
                JTable t = (JTable) f;
                r = t.getCellRect(t.getSelectedRow(), 0, true);
                r.width = t.getWidth();
                r = r.intersection(t.getVisibleRect());
                r = SwingUtilities.convertRectangle(t, r, this);
            } else {
                r = SwingUtilities.convertRectangle(f, ((JComponent) f).getVisibleRect(), this);
            }
            if (r.isEmpty()) return;
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Color accent = UIManager.getColor("Component.focusColor");
            if (accent == null) accent = new Color(0x4CAF50);
            g2.setColor(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 230));
            g2.setStroke(new BasicStroke(2.5f));
            g2.draw(new RoundRectangle2D.Double(r.x - 2, r.y - 2, r.width + 4, r.height + 4, 12, 12));
            g2.dispose();
        }
    }
}
