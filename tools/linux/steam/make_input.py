"""Шаблон Steam Input для GummerCraft на Steam Deck (controller_neptune), з 21.11.2026.

python tools/linux/steam/make_input.py  ->  tools/linux/steam/gummercraft_neptune.vdf

Steam ставить розкладку на ярлик, а гра (Minecraft, StalDayZ) йде під тим самим ярликом лаунчера, тож розкладка одна
на лаунчер і гру. Вона ігрова (клавіатура і миша, клавіші з options.txt модпака StalDayZ, у ванілі зайві не заважають),
а лаунчер (look/PadNav.java, лише під Steam на Linux) читає ці ж кнопки: пробіл = Enter, одиночний Shift = Esc, WASD = стрілки.
  лівий стік = WASD (натиск = Ctrl, біг), хрестовина = стрілки (лаунчер)
  правий стік і правий тачпад = миша / камера (натиск стіка = F5, натиск тачпада = лівий клік)
  R2 = лівий клік (удар, постріл), L2 = правий клік (використати, приціл)
  A = пробіл (стрибок; у лаунчері натиснути), B = Shift (присісти; у лаунчері назад)
  X = E (інвентар), Y = R (перезарядка), L1 / R1 = коліщатко (слот), ☰ = Esc (меню), ⧉ = Tab (гравці)
  лівий тачпад натиском: вгору T (чат), вниз Z (обвіси), ліворуч V (приціл), праворуч H (огляд зброї)
  задні L4 = Q (викинути), R4 = F (друга рука), L5 = G (режим вогню), R5 = L (ліхтар)
install.sh кладе файл у <Steam>/controller_base/templates/ і прив'язує до ярлика GummerCraft у
steamapps/common/Steam Controller Configs/<акаунт>/config/configset_controller_neptune.vdf (якщо гравець не вибрав своє).
"""
import os


def q(s):
    return '"%s"' % s


class W:
    def __init__(self):
        self.lines, self.d = [], 0

    def kv(self, k, v):
        self.lines.append("\t" * self.d + q(k) + "\t\t" + q(v))

    def open(self, k):
        self.lines.append("\t" * self.d + q(k))
        self.lines.append("\t" * self.d + "{")
        self.d += 1

    def close(self):
        self.d -= 1
        self.lines.append("\t" * self.d + "}")


w = W()


def press(name, bindings, repeat=False, activator="Full_Press"):
    w.open(name)
    w.open("activators")
    w.open(activator)
    w.open("bindings")
    for b in bindings:
        w.kv("binding", b)
    w.close()
    if repeat:
        w.open("settings")
        w.kv("hold_repeats", "1")
        w.kv("repeat_rate", "90")
        w.close()
    w.close()
    w.close()
    w.open("disabled_activators")
    w.close()
    w.close()


def key(k):
    return "key_press %s, , " % k


def group(gid, mode, inputs, settings=None):
    w.open("group")
    w.kv("id", str(gid))
    w.kv("mode", mode)
    w.kv("name", "")
    w.kv("description", "")
    w.open("inputs")
    for args in inputs:
        press(*args)
    w.close()
    if settings:
        w.open("settings")
        for k, v in settings.items():
            w.kv(k, v)
        w.close()
    w.close()


ARROWS = [("dpad_north", [key("UP_ARROW")], True), ("dpad_south", [key("DOWN_ARROW")], True),
          ("dpad_east", [key("RIGHT_ARROW")], True), ("dpad_west", [key("LEFT_ARROW")], True)]
WASD = [("dpad_north", [key("W")]), ("dpad_south", [key("S")]), ("dpad_east", [key("D")]), ("dpad_west", [key("A")]),
        ("click", [key("LEFT_CONTROL")])]
PAD = [("dpad_north", [key("T")]), ("dpad_south", [key("Z")]), ("dpad_east", [key("H")]), ("dpad_west", [key("V")])]

TITLE_EN = "GummerCraft (launcher and game)"
DESC_EN = ("GummerCraft launcher, Minecraft and StalDayZ: left stick = WASD, right trackpad and right stick = mouse, "
           "R2 / L2 = left / right click, A = jump, B = sneak, X = inventory, Y = reload, D-pad = launcher arrows.")
TITLE_UK = "GummerCraft (лаунчер та ігри)"
DESC_UK = ("Лаунчер, Minecraft та StalDayZ: лівий стік ходьба, правий тачпад і правий стік миша, R2 / L2 лівий / правий клік, "
           "A стрибок, B присісти, X інвентар, Y перезарядка, хрестовина стрілки в лаунчері.")

w.open("controller_mappings")
for k, v in [("version", "3"), ("revision", "2"), ("title", TITLE_EN), ("description", DESC_EN),
             ("creator", "0"), ("progenitor", ""), ("url", "template://gummercraft_neptune.vdf"),
             ("export_type", "template"), ("controller_type", "controller_neptune"), ("controller_caps", "23117823"),
             ("major_revision", "0"), ("minor_revision", "0"), ("Timestamp", "0")]:
    w.kv(k, v)
w.open("localization")
w.open("english")
w.kv("title", TITLE_EN)
w.kv("description", DESC_EN)
w.close()
w.open("ukrainian")
w.kv("title", TITLE_UK)
w.kv("description", DESC_UK)
w.close()
w.close()
group(0, "four_buttons", [("button_a", [key("SPACE")]), ("button_b", [key("LEFT_SHIFT")]),
                          ("button_x", [key("E")]), ("button_y", [key("R")])])
group(1, "dpad", ARROWS, {"requires_click": "0"})                      # хрестовина: стрілки (лаунчер)
group(2, "dpad", WASD, {"requires_click": "0"})                        # лівий стік: WASD, натиск Ctrl
group(3, "dpad", PAD, {"requires_click": "1"})                         # лівий тачпад натиском
group(4, "absolute_mouse", [("click", ["mouse_button LEFT, , "])])     # правий тачпад
group(5, "trigger", [("edge", ["mouse_button RIGHT, , "])])            # L2
group(6, "trigger", [("edge", ["mouse_button LEFT, , "])])             # R2
group(7, "joystick_mouse", [("click", [key("F5")])])                   # правий стік
group(8, "switches", [("button_escape", [key("ESCAPE")]), ("button_menu", [key("TAB")]),
                      ("left_bumper", ["mouse_wheel SCROLL_UP, , "], True),
                      ("right_bumper", ["mouse_wheel SCROLL_DOWN, , "], True),
                      ("button_back_left", [key("Q")]), ("button_back_right", [key("F")]),
                      ("button_back_left_upper", [key("G")]), ("button_back_right_upper", [key("L")])])
w.open("preset")
w.kv("id", "0")
w.kv("name", "Default")
w.open("group_source_bindings")
for gid, src in [(8, "switch active"), (0, "button_diamond active"), (1, "dpad active"), (2, "joystick active"),
                 (3, "left_trackpad active"), (4, "right_trackpad active"), (5, "left_trigger active"),
                 (6, "right_trigger active"), (7, "right_joystick active")]:
    w.kv(str(gid), src)
w.close()
w.close()
w.open("settings")
w.kv("left_trackpad_mode", "0")
w.kv("right_trackpad_mode", "0")
w.close()
w.close()

out = os.path.join(os.path.dirname(os.path.abspath(__file__)), "gummercraft_neptune.vdf")
open(out, "w", encoding="utf-8", newline="\n").write("\n".join(w.lines) + "\n")
print(out, len(w.lines))
