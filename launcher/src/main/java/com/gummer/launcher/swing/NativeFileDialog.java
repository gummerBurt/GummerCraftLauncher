/*
 * Copyright (C) 2021-2026 GummerCraft <http://gummercraft.fun>
 */

package com.gummer.launcher.swing;

import com.sun.jna.Function;
import com.sun.jna.Native;
import com.sun.jna.NativeLibrary;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;
import com.sun.jna.WString;
import com.sun.jna.platform.win32.Guid;
import com.sun.jna.platform.win32.Ole32;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.PointerByReference;
import lombok.extern.java.Log;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.filechooser.FileFilter;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;

/**
 * 26.11.2026 (власник): вікно вибору файлів і тек. Замість {@link JFileChooser} (вигляд Windows 95, без прев'ю картинок)
 * на Windows відкривається системне вікно Провідника (IFileOpenDialog / IFileSaveDialog через COM і JNA): ескізи
 * картинок, панель прев'ю, швидкий доступ, пошук, остання тека запам'ятовується самою Windows. На macOS системне
 * {@link FileDialog} (Finder, з прев'ю). На Linux і коли нативне вікно не вдалося (старий Windows, Wine, помилка COM)
 * лишається {@link JFileChooser}, але з прев'ю картинки праворуч, якщо фільтр на картинки.
 *
 * <p>Використання: {@code new NativeFileDialog(parent).title(...).filter("PNG, JPG", "png", "jpg").openFile()}.
 * Повертає {@code null}, якщо гравець скасував. Викликати з потоку інтерфейсу (EDT): поки вікно відкрите, Swing далі
 * малює (вторинний цикл подій), а саме вікно модальне до батьківського.
 */
@Log
public final class NativeFileDialog {

    private final Component parent;
    private String title;
    private File folder;
    private String fileName;
    private String filterName;
    private String[] patterns; // для Windows і macOS: «*.png», «java.exe»
    private FileFilter swingFilter; // для JFileChooser
    private String okLabel;
    private String defaultExt;

    public NativeFileDialog(Component parent) {
        this.parent = parent;
    }

    public NativeFileDialog title(String title) {
        this.title = title;
        return this;
    }

    /** Початкова тека (null: та, що Windows запам'ятала минулого разу). */
    public NativeFileDialog folder(File folder) {
        this.folder = folder;
        return this;
    }

    /** Назва файлу за замовчуванням (для збереження). */
    public NativeFileDialog fileName(String fileName) {
        this.fileName = fileName;
        return this;
    }

    /** Фільтр за розширеннями без крапки: {@code filter("PNG, JPG", "png", "jpg")}. */
    public NativeFileDialog filter(String name, String... extensions) {
        this.filterName = name;
        this.patterns = new String[extensions.length];
        for (int i = 0; i < extensions.length; i++) patterns[i] = "*." + extensions[i];
        this.swingFilter = new FileNameExtensionFilter(name, extensions);
        if (extensions.length > 0) this.defaultExt = extensions[0];
        return this;
    }

    /** Фільтр за точними масками (Windows, macOS) і окремий фільтр для JFileChooser. */
    public NativeFileDialog filter(String name, FileFilter swing, String... patterns) {
        this.filterName = name;
        this.patterns = patterns;
        this.swingFilter = swing;
        return this;
    }

    /** Напис на кнопці підтвердження (null: системний «Відкрити» / «Вибрати теку»). */
    public NativeFileDialog okLabel(String okLabel) {
        this.okLabel = okLabel;
        return this;
    }

    public File openFile() {
        File[] r = show(Mode.OPEN);
        return r == null || r.length == 0 ? null : r[0];
    }

    public File[] openFiles() {
        File[] r = show(Mode.OPEN_MULTI);
        return r == null || r.length == 0 ? null : r;
    }

    public File openFolder() {
        File[] r = show(Mode.FOLDER);
        return r == null || r.length == 0 ? null : r[0];
    }

    /** Збереження. Розширення першого фільтра дописується, якщо гравець його не написав. */
    public File saveFile() {
        File[] r = show(Mode.SAVE);
        if (r == null || r.length == 0) return null;
        File f = r[0];
        if (defaultExt != null && !f.getName().toLowerCase(Locale.ROOT).endsWith("." + defaultExt.toLowerCase(Locale.ROOT))) {
            f = new File(f.getPath() + "." + defaultExt);
        }
        return f;
    }

    private enum Mode { OPEN, OPEN_MULTI, FOLDER, SAVE }

    private static final String OS = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
    private static final boolean WINDOWS = OS.contains("win");
    private static final boolean MAC = OS.contains("mac") || OS.contains("darwin");
    /** Після першої невдачі COM більше не пробуємо в цьому запуску (одразу JFileChooser). */
    private static volatile boolean nativeBroken = Boolean.getBoolean("gummer.swingFileChooser");

    private File[] show(Mode mode) {
        if (WINDOWS && !nativeBroken) {
            try {
                WinResult r = showWindows(mode);
                if (r.ok) return r.files;
                nativeBroken = true;
                log.log(Level.WARNING, "Системне вікно вибору файлів не вдалося (HRESULT 0x"
                        + Integer.toHexString(r.hr) + " на кроці " + r.step + "), далі вікно Java");
            } catch (Throwable t) {
                nativeBroken = true;
                log.log(Level.WARNING, "Системне вікно вибору файлів не вдалося, далі вікно Java", t);
            }
        } else if (MAC) {
            try {
                return showMac(mode);
            } catch (Throwable t) {
                log.log(Level.WARNING, "FileDialog macOS не вдався, далі вікно Java", t);
            }
        }
        return showSwing(mode);
    }

    // ---------------------------------------------------------------- Windows

    private static final class WinResult {
        boolean ok;
        File[] files;
        int hr;
        String step;
    }

    private static final int HR_CANCELLED = 0x800704C7;

    private static final int FOS_OVERWRITEPROMPT = 0x2;
    private static final int FOS_NOCHANGEDIR = 0x8;
    private static final int FOS_PICKFOLDERS = 0x20;
    private static final int FOS_FORCEFILESYSTEM = 0x40;
    private static final int FOS_ALLOWMULTISELECT = 0x200;
    private static final int FOS_PATHMUSTEXIST = 0x800;
    private static final int FOS_FILEMUSTEXIST = 0x1000;

    private static final int SIGDN_FILESYSPATH = 0x80058000;

    // IFileDialog (IUnknown 0-2, IModalWindow::Show 3)
    private static final int V_RELEASE = 2, V_SHOW = 3, V_SET_FILE_TYPES = 4, V_SET_FILE_TYPE_INDEX = 5,
            V_SET_OPTIONS = 9, V_GET_OPTIONS = 10, V_SET_FOLDER = 12, V_SET_FILE_NAME = 15, V_SET_TITLE = 17,
            V_SET_OK_LABEL = 18, V_GET_RESULT = 20, V_SET_DEFAULT_EXT = 22, V_GET_RESULTS = 27;
    // IShellItem
    private static final int V_ITEM_GET_DISPLAY_NAME = 5;
    // IShellItemArray
    private static final int V_ARR_GET_COUNT = 7, V_ARR_GET_ITEM_AT = 8;

    private static final String CLSID_OPEN = "{DC1C5A9C-E88A-4DDE-A5A1-60F82A20AEF7}";
    private static final String CLSID_SAVE = "{C0B4E2F3-BA21-4773-8DBA-335EC946EB8B}";
    private static final String IID_OPEN = "{D57C7288-D4AD-4768-BE02-9D969532D960}";
    private static final String IID_SAVE = "{84BCCD23-5FDE-4CDB-AEA4-AF64B83D78AB}";
    private static final String IID_SHELL_ITEM = "{43826D1E-E718-42EE-BC55-A1E261C37BFE}";

    /** COMDLG_FILTERSPEC */
    public static class FilterSpec extends Structure {
        public WString pszName;
        public WString pszSpec;

        @Override
        protected List<String> getFieldOrder() {
            return Arrays.asList("pszName", "pszSpec");
        }
    }

    private static int call(Pointer obj, int index, Object... args) {
        Pointer vtbl = obj.getPointer(0);
        Function f = Function.getFunction(vtbl.getPointer((long) index * Native.POINTER_SIZE), Function.ALT_CONVENTION);
        Object[] all = new Object[args.length + 1];
        all[0] = obj;
        System.arraycopy(args, 0, all, 1, args.length);
        return f.invokeInt(all);
    }

    private static void release(Pointer obj) {
        if (obj != null) {
            try {
                call(obj, V_RELEASE);
            } catch (Throwable ignored) {
            }
        }
    }

    private WinResult showWindows(final Mode mode) throws Exception {
        Window owner = parent == null ? null : (parent instanceof Window ? (Window) parent : SwingUtilities.getWindowAncestor(parent));
        Pointer hwnd = null;
        try {
            if (owner != null && owner.isDisplayable()) hwnd = Native.getWindowPointer(owner);
        } catch (Throwable ignored) {
        }
        final Pointer ownerHwnd = hwnd;
        final WinResult result = new WinResult();
        final Throwable[] error = new Throwable[1];
        final Runnable work = () -> {
            try {
                runWindows(mode, ownerHwnd, result);
            } catch (Throwable t) {
                error[0] = t;
            }
        };
        if (EventQueue.isDispatchThread()) {
            // окремий потік STA для COM, а EDT далі обробляє події (перемальовка), поки вікно відкрите
            final SecondaryLoop loop = Toolkit.getDefaultToolkit().getSystemEventQueue().createSecondaryLoop();
            final Thread t = new Thread(() -> {
                try {
                    work.run();
                } finally {
                    loop.exit();
                }
            }, "Gummer file dialog");
            t.setDaemon(true);
            // старт з самого циклу: exit() гарантовано після enter()
            EventQueue.invokeLater(t::start);
            loop.enter();
            t.join(2000);
        } else {
            Thread t = new Thread(work, "Gummer file dialog");
            t.setDaemon(true);
            t.start();
            t.join();
        }
        if (error[0] instanceof Exception) throw (Exception) error[0];
        if (error[0] != null) throw new RuntimeException(error[0]);
        return result;
    }

    private void runWindows(Mode mode, Pointer ownerHwnd, WinResult out) {
        final int COINIT_APARTMENTTHREADED = 0x2, COINIT_DISABLE_OLE1DDE = 0x4;
        int init = Ole32.INSTANCE.CoInitializeEx(null, COINIT_APARTMENTTHREADED | COINIT_DISABLE_OLE1DDE).intValue();
        boolean uninit = init >= 0; // S_OK або S_FALSE
        Pointer dlg = null;
        try {
            boolean save = mode == Mode.SAVE;
            PointerByReference pp = new PointerByReference();
            int hr = Ole32.INSTANCE.CoCreateInstance(new Guid.GUID(save ? CLSID_SAVE : CLSID_OPEN), null, 1 /* CLSCTX_INPROC_SERVER */,
                    new Guid.GUID(save ? IID_SAVE : IID_OPEN), pp).intValue();
            if (hr < 0) {
                out.hr = hr;
                out.step = "CoCreateInstance";
                return;
            }
            dlg = pp.getValue();

            IntByReference opts = new IntByReference();
            call(dlg, V_GET_OPTIONS, opts);
            int o = opts.getValue() | FOS_FORCEFILESYSTEM | FOS_NOCHANGEDIR | FOS_PATHMUSTEXIST;
            if (mode == Mode.FOLDER) o |= FOS_PICKFOLDERS;
            if (mode == Mode.OPEN_MULTI) o |= FOS_ALLOWMULTISELECT;
            if (mode == Mode.OPEN || mode == Mode.OPEN_MULTI) o |= FOS_FILEMUSTEXIST;
            if (save) o |= FOS_OVERWRITEPROMPT;
            hr = call(dlg, V_SET_OPTIONS, o);
            if (hr < 0) {
                out.hr = hr;
                out.step = "SetOptions";
                return;
            }

            if (title != null) call(dlg, V_SET_TITLE, new WString(title));
            if (okLabel != null) call(dlg, V_SET_OK_LABEL, new WString(okLabel));
            if (mode != Mode.FOLDER && patterns != null && patterns.length > 0) {
                FilterSpec[] specs = (FilterSpec[]) new FilterSpec().toArray(1);
                specs[0].pszName = new WString(filterName != null ? filterName : String.join(", ", patterns));
                specs[0].pszSpec = new WString(String.join(";", patterns));
                specs[0].write();
                call(dlg, V_SET_FILE_TYPES, 1, specs[0].getPointer());
                call(dlg, V_SET_FILE_TYPE_INDEX, 1);
            }
            if (save && defaultExt != null) call(dlg, V_SET_DEFAULT_EXT, new WString(defaultExt));
            if (fileName != null) call(dlg, V_SET_FILE_NAME, new WString(fileName));
            if (folder != null) {
                File dir = folder.isFile() ? folder.getParentFile() : folder;
                if (dir != null && dir.isDirectory()) {
                    Pointer item = shellItem(dir);
                    if (item != null) {
                        call(dlg, V_SET_FOLDER, item);
                        release(item);
                    }
                }
            }

            hr = call(dlg, V_SHOW, ownerHwnd);
            if (hr == HR_CANCELLED) {
                out.ok = true;
                out.files = null;
                return;
            }
            if (hr < 0) {
                out.hr = hr;
                out.step = "Show";
                return;
            }

            List<File> files = new ArrayList<>();
            if (mode == Mode.OPEN_MULTI) {
                PointerByReference arrRef = new PointerByReference();
                hr = call(dlg, V_GET_RESULTS, arrRef);
                if (hr < 0) {
                    out.hr = hr;
                    out.step = "GetResults";
                    return;
                }
                Pointer arr = arrRef.getValue();
                try {
                    IntByReference count = new IntByReference();
                    call(arr, V_ARR_GET_COUNT, count);
                    for (int i = 0; i < count.getValue(); i++) {
                        PointerByReference itemRef = new PointerByReference();
                        if (call(arr, V_ARR_GET_ITEM_AT, i, itemRef) < 0) continue;
                        Pointer item = itemRef.getValue();
                        String path = displayPath(item);
                        release(item);
                        if (path != null) files.add(new File(path));
                    }
                } finally {
                    release(arr);
                }
            } else {
                PointerByReference itemRef = new PointerByReference();
                hr = call(dlg, V_GET_RESULT, itemRef);
                if (hr < 0) {
                    out.hr = hr;
                    out.step = "GetResult";
                    return;
                }
                Pointer item = itemRef.getValue();
                String path = displayPath(item);
                release(item);
                if (path != null) files.add(new File(path));
            }
            out.ok = true;
            out.files = files.toArray(new File[0]);
        } finally {
            release(dlg);
            if (uninit) Ole32.INSTANCE.CoUninitialize();
        }
    }

    private static Pointer shellItem(File dir) {
        try {
            Function f = NativeLibrary.getInstance("shell32").getFunction("SHCreateItemFromParsingName", Function.ALT_CONVENTION);
            PointerByReference ref = new PointerByReference();
            int hr = f.invokeInt(new Object[]{new WString(dir.getAbsolutePath()), null, new Guid.GUID(IID_SHELL_ITEM), ref});
            return hr < 0 ? null : ref.getValue();
        } catch (Throwable t) {
            return null;
        }
    }

    private static String displayPath(Pointer item) {
        PointerByReference name = new PointerByReference();
        if (call(item, V_ITEM_GET_DISPLAY_NAME, SIGDN_FILESYSPATH, name) < 0) return null;
        Pointer p = name.getValue();
        if (p == null) return null;
        try {
            return p.getWideString(0);
        } finally {
            Ole32.INSTANCE.CoTaskMemFree(p);
        }
    }

    // ---------------------------------------------------------------- macOS

    private File[] showMac(Mode mode) {
        Window w = parent == null ? null : (parent instanceof Window ? (Window) parent : SwingUtilities.getWindowAncestor(parent));
        FileDialog fd = w instanceof Dialog ? new FileDialog((Dialog) w, title, mode == Mode.SAVE ? FileDialog.SAVE : FileDialog.LOAD)
                : new FileDialog(w instanceof Frame ? (Frame) w : null, title, mode == Mode.SAVE ? FileDialog.SAVE : FileDialog.LOAD);
        String prop = "apple.awt.fileDialogForDirectories";
        String old = System.getProperty(prop);
        try {
            if (mode == Mode.FOLDER) System.setProperty(prop, "true");
            if (mode == Mode.OPEN_MULTI) fd.setMultipleMode(true);
            if (folder != null) fd.setDirectory((folder.isFile() ? folder.getParentFile() : folder).getAbsolutePath());
            if (fileName != null) fd.setFile(fileName);
            if (mode != Mode.FOLDER && swingFilter != null) {
                final FileFilter sf = swingFilter;
                fd.setFilenameFilter((dir, name) -> sf.accept(new File(dir, name)));
            }
            fd.setVisible(true);
            File[] files = fd.getFiles();
            return files == null || files.length == 0 ? null : files;
        } finally {
            if (mode == Mode.FOLDER) {
                if (old == null) System.clearProperty(prop);
                else System.setProperty(prop, old);
            }
        }
    }

    // ---------------------------------------------------------------- Swing

    private File[] showSwing(Mode mode) {
        JFileChooser chooser = new JFileChooser(folder != null ? (folder.isFile() ? folder.getParentFile() : folder) : null);
        if (title != null) chooser.setDialogTitle(title);
        if (mode == Mode.FOLDER) chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if (mode == Mode.OPEN_MULTI) chooser.setMultiSelectionEnabled(true);
        if (mode != Mode.FOLDER && swingFilter != null) chooser.setFileFilter(swingFilter);
        if (fileName != null) chooser.setSelectedFile(new File(chooser.getCurrentDirectory(), fileName));
        if (mode != Mode.FOLDER && isImageFilter()) {
            ImagePreview preview = new ImagePreview();
            chooser.setAccessory(preview);
            chooser.addPropertyChangeListener(JFileChooser.SELECTED_FILE_CHANGED_PROPERTY,
                    e -> preview.load((File) e.getNewValue()));
        }
        int r;
        if (mode == Mode.SAVE) r = chooser.showSaveDialog(parent);
        else if (okLabel != null) r = chooser.showDialog(parent, okLabel);
        else r = chooser.showOpenDialog(parent);
        if (r != JFileChooser.APPROVE_OPTION) return null;
        if (mode == Mode.OPEN_MULTI) return chooser.getSelectedFiles();
        File f = chooser.getSelectedFile();
        return f == null ? null : new File[]{f};
    }

    private boolean isImageFilter() {
        if (patterns == null) return false;
        for (String p : patterns) {
            String s = p.toLowerCase(Locale.ROOT);
            if (s.endsWith(".png") || s.endsWith(".jpg") || s.endsWith(".jpeg") || s.endsWith(".gif") || s.endsWith(".bmp")) {
                return true;
            }
        }
        return false;
    }

    /** Прев'ю картинки праворуч у JFileChooser (запасне вікно). */
    private static final class ImagePreview extends JComponent {
        private static final int SIZE = 180;
        private Image image;
        private String info;

        ImagePreview() {
            setPreferredSize(new Dimension(SIZE + 16, SIZE + 30));
        }

        void load(File f) {
            image = null;
            info = null;
            if (f != null && f.isFile() && f.length() < 64L * 1024 * 1024) {
                try {
                    BufferedImage img = ImageIO.read(f);
                    if (img != null) {
                        info = img.getWidth() + " x " + img.getHeight();
                        double k = Math.min(1.0, Math.min((double) SIZE / img.getWidth(), (double) SIZE / img.getHeight()));
                        int w = Math.max(1, (int) Math.round(img.getWidth() * k));
                        int h = Math.max(1, (int) Math.round(img.getHeight() * k));
                        image = img.getScaledInstance(w, h, Image.SCALE_SMOOTH);
                    }
                } catch (Throwable ignored) {
                }
            }
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            if (image == null) return;
            int w = image.getWidth(null), h = image.getHeight(null);
            int x = 8 + (SIZE - w) / 2, y = (SIZE - h) / 2;
            g.drawImage(image, x, y, null);
            if (info != null) {
                g.setColor(getForeground());
                FontMetrics fm = g.getFontMetrics();
                g.drawString(info, 8 + (SIZE - fm.stringWidth(info)) / 2, SIZE + 8 + fm.getAscent());
            }
        }
    }
}
