#!/usr/bin/env bash
# GummerCraft: установка лаунчера на Linux x86_64 і Steam Deck одним кліком (з 13.11.2026).
#
# Гравець завантажує GummerCraft-Linux.desktop з сайту і двічі клацає по ньому (Steam Deck: режим робочого столу,
# у вікні «Ненадійна програма» кнопка «Продовжити»). Той запускає цей скрипт з launch.gummercraft.fun. Скрипт:
#   1. ставить Java для самого лаунчера (Temurin 17 JRE, офіційний архів Adoptium, sha256 перевіряється) у ~/GummerCraft/app/jre;
#   2. кладе лаунчер (Gummer_Craft_launcher.jar, той самий, що і для ручного запуску), іконку і скрипт запуску в ~/GummerCraft/app;
#   3. додає GummerCraft у меню програм і на робочий стіл;
#   4. додає GummerCraft у бібліотеку Steam як гру не з Steam з обкладинками (запис у shortcuts.vdf при закритому Steam,
#      відкритий Steam закриває лише з дозволу гравця і відкриває знову), щоб грати в ігровому режимі Steam Deck;
#   5. запускає лаунчер.
# Прав root не треба, системні файли не змінюються (SteamOS має системний розділ лише для читання, тому все в домашній теці).
# Повторний запуск оновлює лаунчер і ярлики, Java перекачує лише при зміні версії, у Steam вдруге не додає.
# Ігри, світи і Java для гри лежать у ~/GummerCraft (як і без цього скрипта); установка їх не чіпає.
# Видалення: ~/GummerCraft/app/uninstall.sh (ярлик у Steam прибрати вручну).
#
# Змінні для перевірки (гравцям не потрібні): GC_BASE_URL (звідки брати лаунчер та іконку), GC_NO_LAUNCH=1 (не запускати в кінці),
# GC_NO_STEAM=1 (не додавати в Steam).
#
# Усе тіло у функції main і викликається в останньому рядку: якщо завантаження скрипта обірветься (curl | bash), неповний скрипт нічого не виконає.

main() {
    set -u
    local BASE="${GC_BASE_URL:-https://launch.gummercraft.fun/install}"
    # Оновити Java: https://api.adoptium.net/v3/assets/latest/17/hotspot?architecture=x64&image_type=jre&os=linux&vendor=eclipse
    local JRE_NAME="OpenJDK17U-jre_x64_linux_hotspot_17.0.21_9.tar.gz"
    local JRE_URL="https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17.0.21%2B9/OpenJDK17U-jre_x64_linux_hotspot_17.0.21_9.tar.gz"
    local JRE_SHA256="9f41bf88c7900c7097444a1bdeb84146ae692bc08842490ab098063c74bf9a8a"

    local DATA="$HOME/GummerCraft"
    local APP="$DATA/app"
    # LOG і TMP глобальні: їх бачать log() і trap після виходу з main
    LOG="$APP/install.log"
    mkdir -p "$APP" || { LOG=""; ui_error "Не вдалося створити теку $APP."; return 1; }
    TMP="$(mktemp -d "${TMPDIR:-/tmp}/gummercraft-install.XXXXXX")" || { ui_error "Не вдалося створити тимчасову теку."; return 1; }
    trap 'rm -rf "$TMP"; ui_progress_close' EXIT
    : > "$LOG"
    log "Установка GummerCraft $(date '+%Y-%m-%d %H:%M:%S'), $(uname -srm), HOME=$HOME"

    if [ "$(uname -s)" != "Linux" ] || [ "$(uname -m)" != "x86_64" ]; then
        ui_error "Ця установка лише для Linux x86_64 (зокрема Steam Deck). На іншому процесорі завантажте jar-версію лаунчера з сайту gummercraft.fun/launcher та запустіть її своєю Java 8 або новішою."
        return 1
    fi
    if ! command -v curl >/dev/null 2>&1; then
        ui_error "Немає програми curl, без неї установка не може нічого завантажити. Встановіть curl через менеджер пакетів та запустіть установку ще раз."
        return 1
    fi

    ui_progress_open "Установка GummerCraft"

    # 1. Java для лаунчера
    if [ -x "$APP/jre/bin/java" ] && [ "$(cat "$APP/jre/.gummer-jre" 2>/dev/null)" = "$JRE_NAME" ]; then
        log "Java вже встановлено: $JRE_NAME"
    else
        ui_progress_text "Завантажуємо Java (45 МБ)..."
        if ! download "$JRE_URL" "$TMP/jre.tar.gz"; then
            ui_error "Не вдалося завантажити Java. Перевірте інтернет та запустіть установку ще раз."
            return 1
        fi
        local sum
        sum="$(sha256sum "$TMP/jre.tar.gz" | cut -d' ' -f1)"
        if [ "$sum" != "$JRE_SHA256" ]; then
            log "sha256 Java не збігся: $sum"
            ui_error "Завантажена Java пошкоджена (не збіглась контрольна сума). Запустіть установку ще раз."
            return 1
        fi
        ui_progress_text "Розпаковуємо Java..."
        mkdir -p "$TMP/jre-unpack"
        if ! tar -xzf "$TMP/jre.tar.gz" -C "$TMP/jre-unpack"; then
            ui_error "Не вдалося розпакувати Java (можливо, закінчилось місце на диску)."
            return 1
        fi
        local inner
        inner="$(find "$TMP/jre-unpack" -mindepth 1 -maxdepth 1 -type d | head -n 1)"
        if [ -z "$inner" ] || [ ! -x "$inner/bin/java" ]; then
            ui_error "В архіві Java немає bin/java. Запустіть установку ще раз."
            return 1
        fi
        echo "$JRE_NAME" > "$inner/.gummer-jre"
        rm -rf "$APP/jre.old"
        [ -d "$APP/jre" ] && mv "$APP/jre" "$APP/jre.old"
        mv "$inner" "$APP/jre" || { ui_error "Не вдалося покласти Java в $APP/jre."; return 1; }
        rm -rf "$APP/jre.old"
        log "Java встановлено: $JRE_NAME"
    fi
    if ! "$APP/jre/bin/java" -version >>"$LOG" 2>&1; then
        ui_error "Java не запускається на цьому комп'ютері. Подробиці в $LOG"
        return 1
    fi

    # 2. Лаунчер та іконка
    ui_progress_text "Завантажуємо лаунчер..."
    if ! download "$BASE/Gummer_Craft_launcher.jar" "$TMP/launcher.jar" || [ "$(head -c 2 "$TMP/launcher.jar")" != "PK" ]; then
        if [ -f "$APP/Gummer_Craft_launcher.jar" ]; then
            log "Лаунчер не завантажився, лишаю наявний"
        else
            ui_error "Не вдалося завантажити лаунчер. Перевірте інтернет та запустіть установку ще раз."
            return 1
        fi
    else
        mv -f "$TMP/launcher.jar" "$APP/Gummer_Craft_launcher.jar"
    fi
    if download "$BASE/linux/gummercraft.png" "$TMP/icon.png" && [ "$(head -c 4 "$TMP/icon.png" | tail -c 3)" = "PNG" ]; then
        mv -f "$TMP/icon.png" "$APP/gummercraft.png"
    else
        log "Іконка не завантажилась (не страшно)"
    fi
    # Обкладинки для бібліотеки Steam (з 21.11.2026): кавер, широка плитка, шапка, логотип. Ставить їх steam-art.sh нижче.
    mkdir -p "$APP/steam-art"
    local art
    if download "$BASE/linux/steam/gummercraft_neptune.vdf" "$TMP/input.vdf" && [ "$(head -c 21 "$TMP/input.vdf")" = '"controller_mappings"' ]; then
        mv -f "$TMP/input.vdf" "$APP/steam-art/gummercraft_neptune.vdf"
    else
        log "Шаблон керування геймпадом не завантажився (не страшно)"
    fi
    for art in cover wide hero logo; do
        if download "$BASE/linux/steam/$art.png" "$TMP/$art.png" && [ "$(head -c 4 "$TMP/$art.png" | tail -c 3)" = "PNG" ]; then
            mv -f "$TMP/$art.png" "$APP/steam-art/$art.png"
        else
            log "Обкладинка $art не завантажилась (не страшно)"
        fi
    done

    cat > "$APP/gummercraft.sh" <<'RUN'
#!/usr/bin/env bash
# Запуск лаунчера GummerCraft. Створено установкою (install.sh), повторна установка його перезаписує.
APP="$(cd "$(dirname "$(readlink -f "$0")")" && pwd)"
# Java Swing у віконних менеджерах без декорацій (gamescope в ігровому режимі Steam Deck) інакше може показати порожнє вікно.
export _JAVA_AWT_WM_NONREPARENTING=1
cd "$HOME" || true
# Обкладинки Steam: якщо під час установки Steam ще не записав ярлик, поставити їх зараз (у фоні, до успіху один раз).
if [ -x "$APP/steam-art.sh" ] && [ ! -f "$APP/.steam-art-done" ]; then
    "$APP/steam-art.sh" >/dev/null 2>&1 &
fi
exec "$APP/jre/bin/java" -Dawt.useSystemAAFontSettings=on -Dswing.aatext=true -jar "$APP/Gummer_Craft_launcher.jar" "$@"
RUN
    chmod +x "$APP/gummercraft.sh"

    cat > "$APP/uninstall.sh" <<'UNINSTALL'
#!/usr/bin/env bash
# Видалення лаунчера GummerCraft, встановленого через install.sh. Ігри, світи та скріншоти в ~/GummerCraft лишаються.
APP="$(cd "$(dirname "$(readlink -f "$0")")" && pwd)"
rm -f "$HOME/.local/share/applications/gummercraft.desktop"
DESKTOP_DIR="$(xdg-user-dir DESKTOP 2>/dev/null || echo "$HOME/Desktop")"
rm -f "$DESKTOP_DIR/GummerCraft.desktop"
rm -rf "$APP"
echo "Лаунчер видалено. Ігри та світи лишились у $HOME/GummerCraft. Ярлик у Steam приберіть вручну (Керувати -> Видалити ярлик)."
UNINSTALL
    chmod +x "$APP/uninstall.sh"

    # Ярлик і обкладинки Steam (з 21.11.2026 напряму, без steamos-add-to-steam: той лише відкриває посилання
    # steam://addnonsteamgame і завжди повертає 0, навіть коли Steam посилання проігнорував, тож ярлика не було).
    # Ярлики «не з Steam» лежать у бінарному userdata/<акаунт>/config/shortcuts.vdf. Steam читає його на старті і
    # перезаписує зі своєї пам'яті, тому писати лише при закритому Steam. appid ярлика як у Steam і Steam ROM Manager:
    # crc32(Exe + AppName) | 0x80000000. Картинки: config/grid/<appid>p.png (кавер 600x900), <appid>.png (920x430),
    # <appid>_hero.png (1920x620), <appid>_logo.png; ті, що вже є, не перезаписуються (свої обкладинки гравця).
    cat > "$APP/steam-shortcut.py" <<'PY'
# Ярлик GummerCraft у бібліотеці Steam. Створено установкою (install.sh).
# python3 steam-shortcut.py <app> status | add | art
#   status: у скількох акаунтах Steam ярлик уже є і скільки акаунтів загалом ("users=N have=M")
#   add:    дописати ярлик туди, де його немає (лише при закритому Steam!), і поставити обкладинки
#   art:    лише обкладинки для наявних ярликів
import glob, os, re, shutil, struct, sys, time, zlib

app, cmd = sys.argv[1], sys.argv[2]
home = os.path.expanduser("~")
EXE = '"%s/gummercraft.sh"' % app
NAME = "GummerCraft"
ART = {"cover.png": "%dp.png", "wide.png": "%d.png", "hero.png": "%d_hero.png", "logo.png": "%d_logo.png"}
FIXED = {0x02: 4, 0x03: 4, 0x06: 4, 0x07: 8, 0x0A: 8}


def users():
    seen, out = set(), []
    roots = [home + "/.local/share/Steam", home + "/.steam/steam", home + "/.steam/root",
             home + "/.var/app/com.valvesoftware.Steam/.local/share/Steam"]
    for r in roots:
        for d in glob.glob(r + "/userdata/*"):
            if not os.path.basename(d).isdigit() or os.path.basename(d) == "0":
                continue
            if not os.path.isfile(d + "/config/localconfig.vdf"):  # акаунт, яким ще не входили
                continue
            real = os.path.realpath(d)
            if real not in seen:
                seen.add(real)
                out.append(real + "/config")
    return out


def cstr(b, i):
    j = b.index(b"\x00", i)
    return b[i:j], j + 1


def parse(b, i):
    items = []
    while True:
        t = b[i]
        i += 1
        if t == 0x08:
            return items, i
        k, i = cstr(b, i)
        if t == 0x00:
            v, i = parse(b, i)
        elif t == 0x01:
            v, i = cstr(b, i)
        elif t in FIXED:
            v, i = b[i:i + FIXED[t]], i + FIXED[t]
        else:
            raise ValueError("невідомий тип %d" % t)
        items.append([t, k, v])


def dump(items):
    out = b""
    for t, k, v in items:
        out += bytes([t]) + k + b"\x00"
        if t == 0x00:
            out += dump(v)
        elif t == 0x01:
            out += v + b"\x00"
        else:
            out += v
    return out + b"\x08"


def get(entry, key):
    key = key.encode().lower()
    for t, k, v in entry:
        if k.lower() == key:
            return t, v
    return None, None


def load(cfg):
    p = cfg + "/shortcuts.vdf"
    if not os.path.isfile(p) or os.path.getsize(p) == 0:
        return [[0x00, b"shortcuts", []]]
    b = open(p, "rb").read()
    root, end = parse(b, 0)
    if not root or root[0][1].lower() != b"shortcuts" or root[0][0] != 0x00:
        raise ValueError("не той формат")
    return root


def ours(root):
    found = []
    for t, k, e in root[0][2]:
        if t != 0x00:
            continue
        _, exe = get(e, "Exe")
        _, aid = get(e, "appid")
        if exe and (app + "/gummercraft.sh").encode() in exe and aid and len(aid) == 4:
            found.append(struct.unpack("<I", aid)[0])
    return found


def art(cfg, appid):
    grid = cfg + "/grid"
    os.makedirs(grid, exist_ok=True)
    for src, name in ART.items():
        s, d = os.path.join(app, "steam-art", src), os.path.join(grid, name % appid)
        if os.path.isfile(s) and not os.path.exists(d):
            shutil.copyfile(s, d)


def entry(index, appid):
    s = lambda k, v: [0x01, k.encode(), v.encode()]
    n = lambda k, v: [0x02, k.encode(), struct.pack("<I", v)]
    icon = app + "/gummercraft.png"
    return [0x00, str(index).encode(), [
        n("appid", appid), s("AppName", NAME), s("Exe", EXE), s("StartDir", '"%s/"' % app),
        s("icon", icon if os.path.isfile(icon) else ""), s("ShortcutPath", ""), s("LaunchOptions", ""),
        n("IsHidden", 0), n("AllowDesktopConfig", 1), n("AllowOverlay", 1), n("OpenVR", 0), n("Devkit", 0),
        # LastPlayTime зараз, а не 0 (21.11.2026, власник: ярлик «не додався в останні запущені і його треба шукати в
        # бібліотеці»): з нулем ігровий режим не ставить ярлик у «Нещодавні» на головній, доки його не запустять з Steam
        s("DevkitGameID", ""), n("DevkitOverrideAppID", 0), n("LastPlayTime", int(time.time())), s("FlatpakAppID", ""),
        [0x00, b"tags", []]]]


# Керування геймпадом (з 21.11.2026): наш шаблон Steam Input (steam-art/gummercraft_neptune.vdf, генерує
# tools/linux/steam/make_input.py) лежить у <Steam>/controller_base/templates/, а ярлик прив'язаний до нього в
# steamapps/common/Steam Controller Configs/<акаунт>/config/configset_controller_neptune.vdf, ключ: назва ярлика
# малими літерами (так само робить Steam ROM Manager). Вибір гравця (наш ключ уже є) не чіпаємо.
TEMPLATE, SHORT = "gummercraft_neptune.vdf", "gummercraft"


def tparse(s):
    toks = re.findall(r'"((?:[^"\\]|\\.)*)"|([{}])', s)
    stack, key = [[]], None
    for val, br in toks:
        if br == "{":
            if key is None:
                raise ValueError("{ без ключа")
            node = []
            stack[-1].append([key, node])
            stack.append(node)
            key = None
        elif br == "}":
            stack.pop()
            if not stack or key is not None:
                raise ValueError("зайва }")
        elif key is None:
            key = val
        else:
            stack[-1].append([key, val])
            key = None
    if len(stack) != 1 or key is not None:
        raise ValueError("незакритий текстовий VDF")
    return stack[0]


def tdump(items, d=0):
    out = ""
    for k, v in items:
        if isinstance(v, list):
            out += "\t" * d + '"%s"\n' % k + "\t" * d + "{\n" + tdump(v, d + 1) + "\t" * d + "}\n"
        else:
            out += "\t" * d + '"%s"\t\t"%s"\n' % (k, v)
    return out


def input_paths(cfg):
    ud = os.path.dirname(cfg)
    root = os.path.dirname(os.path.dirname(ud))
    return root, os.path.join(root, "steamapps", "common", "Steam Controller Configs", os.path.basename(ud), "config",
                              "configset_controller_neptune.vdf")


def input_top(items):
    for k, v in items:
        if k.lower() == "controller_config" and isinstance(v, list):
            return v
    return None


def input_has(cfg):
    if not os.path.isfile(os.path.join(app, "steam-art", TEMPLATE)):
        return True  # шаблон не завантажився: нема чого ставити
    root, p = input_paths(cfg)
    if not os.path.isfile(p):
        return False
    top = input_top(tparse(open(p, encoding="utf-8").read()))
    return top is not None and any(k.lower() == SHORT for k, v in top)


def input_add(cfg):
    src = os.path.join(app, "steam-art", TEMPLATE)
    if not os.path.isfile(src):
        return False
    tparse(open(src, encoding="utf-8").read())  # битий шаблон не кладемо
    root, p = input_paths(cfg)
    tdir = os.path.join(root, "controller_base", "templates")
    os.makedirs(tdir, exist_ok=True)
    shutil.copyfile(src, os.path.join(tdir, TEMPLATE))
    items = tparse(open(p, encoding="utf-8").read()) if os.path.isfile(p) else []
    top = input_top(items)
    if top is None:
        top = []
        items.append(["controller_config", top])
    if any(k.lower() == SHORT for k, v in top):
        return False
    top.append([SHORT, [["template", TEMPLATE]]])
    data = tdump(items)
    if not any(k.lower() == SHORT for k, v in input_top(tparse(data))):
        raise ValueError("перевірка configset не пройшла")
    os.makedirs(os.path.dirname(p), exist_ok=True)
    if os.path.isfile(p):
        shutil.copyfile(p, p + ".gc-backup")
    open(p + ".gc-tmp", "w", encoding="utf-8", newline="\n").write(data)
    os.replace(p + ".gc-tmp", p)
    return True


us = users()
have, added, failed, inp, need = 0, 0, 0, 0, 0
for cfg in us:
    try:
        ok = input_has(cfg)
        if not ok and cmd == "add":
            ok = input_add(cfg)
            print("Steam %s: керування геймпадом %s" % (cfg, "прив'язано" if ok else "не прив'язано"), file=sys.stderr)
        inp += 1 if ok else 0
        need += 0 if ok else 1
    except Exception as e:
        need += 1
        print("Steam %s: керування геймпадом не вдалося (%s), файл не змінено" % (cfg, e), file=sys.stderr)
for cfg in us:
    try:
        root = load(cfg)
        ids = ours(root)
        if ids:
            have += 1
            if cmd in ("add", "art"):
                for a in ids:
                    art(cfg, a)
            print("Steam %s: ярлик уже є, appid %s" % (cfg, ", ".join(map(str, ids))), file=sys.stderr)
            continue
        if cmd != "add":
            need += 1
            continue
        appid = (zlib.crc32((EXE + NAME).encode()) & 0xFFFFFFFF) | 0x80000000
        idx = 1 + max([int(k) for t, k, v in root[0][2] if k.isdigit()] or [-1])
        root[0][2].append(entry(idx, appid))
        p = cfg + "/shortcuts.vdf"
        if os.path.isfile(p):
            shutil.copyfile(p, p + ".gc-backup")
        data = dump(root)
        if ours(parse(data, 0)[0]) != [appid]:
            raise ValueError("перевірка запису не пройшла")
        tmp = p + ".gc-tmp"
        open(tmp, "wb").write(data)
        os.replace(tmp, p)
        art(cfg, appid)
        added += 1
        print("Steam %s: ярлик додано, appid %d, ярликів у файлі %d" % (cfg, appid, len(root[0][2])), file=sys.stderr)
    except Exception as e:  # чужий чи пошкоджений файл не чіпаємо
        failed += 1
        need += 1
        print("Steam %s: не вдалося (%s), файл не змінено" % (cfg, e), file=sys.stderr)
print("users=%d have=%d added=%d failed=%d input=%d need=%d" % (len(us), have, added, failed, inp, need))
PY

    # Запасний виклик при запуску лаунчера (gummercraft.sh): обкладинки для ярлика, який гравець додав у Steam сам.
    cat > "$APP/steam-art.sh" <<'ART'
#!/usr/bin/env bash
# Ставить обкладинки GummerCraft для наявних ярликів у Steam. Створено установкою (install.sh).
APP="$(cd "$(dirname "$(readlink -f "$0")")" && pwd)"
[ -d "$APP/steam-art" ] && [ -f "$APP/steam-shortcut.py" ] && command -v python3 >/dev/null 2>&1 || exit 0
r="$(python3 "$APP/steam-shortcut.py" "$APP" art 2>/dev/null)"
case "$r" in *" have=0 "*|"") exit 0 ;; esac
touch "$APP/.steam-art-done"
ART
    chmod +x "$APP/steam-art.sh"

    # 3. Ярлики
    ui_progress_text "Створюємо ярлики..."
    local ICON="$APP/gummercraft.png"
    [ -f "$ICON" ] || ICON="applications-games"
    local ENTRY="$HOME/.local/share/applications/gummercraft.desktop"
    mkdir -p "$(dirname "$ENTRY")"
    cat > "$ENTRY" <<ENTRY
[Desktop Entry]
Type=Application
Version=1.0
Name=GummerCraft
GenericName=Minecraft launcher
Comment=Лаунчер GummerCraft та StalDayZ
Exec="$APP/gummercraft.sh"
Icon=$ICON
Terminal=false
Categories=Game;
Keywords=Minecraft;GummerCraft;StalDayZ;
ENTRY
    chmod +x "$ENTRY"
    command -v update-desktop-database >/dev/null 2>&1 && update-desktop-database "$HOME/.local/share/applications" >/dev/null 2>&1
    local DESKTOP_DIR
    DESKTOP_DIR="$(xdg-user-dir DESKTOP 2>/dev/null || echo "$HOME/Desktop")"
    if [ -d "$DESKTOP_DIR" ]; then
        cp -f "$ENTRY" "$DESKTOP_DIR/GummerCraft.desktop"
        chmod +x "$DESKTOP_DIR/GummerCraft.desktop"
        command -v gio >/dev/null 2>&1 && gio set "$DESKTOP_DIR/GummerCraft.desktop" metadata::trusted true >/dev/null 2>&1
    fi
    log "Ярлики: $ENTRY, $DESKTOP_DIR/GummerCraft.desktop"

    # 4. Steam: ярлик у бібліотеці (для ігрового режиму Steam Deck) з обкладинками. Будь-який Linux, де Steam уже
    # входив в акаунт. Вдруге не додає (шукає наш gummercraft.sh у shortcuts.vdf), тож повторна установка лише
    # дописує, чого бракує. Steam відкритий: питаємо дозволу закрити його на кілька секунд і відкриваємо знову.
    local STEAM_NOTE="" MANUAL=". У Steam можна додати вручну: «Ігри», «Додати сторонню гру», GummerCraft"
    rm -f "$APP/.added-to-steam" "$APP/.steam-art-done"  # маркери старої установки (до 21.11.2026)
    if [ -z "${GC_NO_STEAM:-}" ] && command -v python3 >/dev/null 2>&1; then
        local st
        st="$(python3 "$APP/steam-shortcut.py" "$APP" status 2>>"$LOG")"
        log "Steam: $st"
        case "$st" in
            users=0*|"")
                log "Steam не знайдено або ще не входив в акаунт"
                ;;
            *" need=0"*)
                python3 "$APP/steam-shortcut.py" "$APP" art >>"$LOG" 2>&1
                STEAM_NOTE=" та в бібліотеці Steam"
                ;;
            *)
                ui_progress_text "Додаємо GummerCraft у бібліотеку Steam..."
                local restart=""
                if steam_running; then
                    if ui_question "Щоб GummerCraft з'явився в бібліотеці Steam (і в ігровому режимі Steam Deck з керуванням геймпадом), Steam треба закрити на кілька секунд. Закрити Steam зараз? Потім він відкриється сам."; then
                        steam_stop && restart=1
                    fi
                fi
                if steam_running; then
                    log "Steam відкритий, ярлик або керування геймпадом не додано"
                    case "$st" in
                        *" have=0 "*) STEAM_NOTE="$MANUAL (або запустіть цю установку ще раз при закритому Steam)" ;;
                        *) STEAM_NOTE=" та в бібліотеці Steam" ;;  # ярлик уже є, бракує лише шаблону геймпада
                    esac
                else
                    st="$(python3 "$APP/steam-shortcut.py" "$APP" add 2>>"$LOG")"
                    log "Steam: $st"
                    case "$st" in
                        *" have=0 added=0 "*) STEAM_NOTE="$MANUAL" ;;
                        *) STEAM_NOTE=" та в бібліотеці Steam" ;;
                    esac
                    [ -n "$restart" ] && steam_start
                fi
                ;;
        esac
    fi

    ui_progress_close
    log "Готово"
    ui_info "GummerCraft встановлено. Лаунчер є в меню програм, на робочому столі$STEAM_NOTE. Зараз він запуститься."
    if [ -z "${GC_NO_LAUNCH:-}" ]; then
        nohup "$APP/gummercraft.sh" >/dev/null 2>&1 &
    fi
    return 0
}

log() {
    [ -n "${LOG:-}" ] && echo "$*" >> "$LOG"
    echo "$*" >&2
}

download() {
    log "Завантаження $1"
    curl -fL --retry 3 --retry-delay 2 --connect-timeout 20 -sS -o "$2" "$1" 2>>"${LOG:-/dev/null}"
}

has_gui() {
    [ -n "${DISPLAY:-}${WAYLAND_DISPLAY:-}" ]
}

ui_error() {
    log "ПОМИЛКА: $1"
    ui_progress_close
    if has_gui && command -v zenity >/dev/null 2>&1; then
        zenity --error --title="GummerCraft" --width=420 --text="$1" 2>/dev/null
    elif has_gui && command -v kdialog >/dev/null 2>&1; then
        kdialog --title "GummerCraft" --error "$1" 2>/dev/null
    fi
}

ui_info() {
    if has_gui && command -v zenity >/dev/null 2>&1; then
        zenity --info --title="GummerCraft" --width=420 --text="$1" 2>/dev/null
    elif has_gui && command -v kdialog >/dev/null 2>&1; then
        kdialog --title "GummerCraft" --msgbox "$1" 2>/dev/null
    else
        echo "$1"
    fi
}

# Так / ні. Без вікон (немає GUI) відповідь «ні»: Steam без дозволу гравця не закриваємо.
ui_question() {
    if has_gui && command -v zenity >/dev/null 2>&1; then
        zenity --question --title="GummerCraft" --width=420 --ok-label="Закрити Steam" --cancel-label="Не зараз" --text="$1" 2>/dev/null
    elif has_gui && command -v kdialog >/dev/null 2>&1; then
        kdialog --title "GummerCraft" --yesno "$1" 2>/dev/null
    else
        return 1
    fi
}

# Steam (звичайний і Flatpak). Процес клієнта зветься steam (ubuntu12_32/steam), steamwebhelper закривається з ним.
steam_running() {
    pgrep -x steam >/dev/null 2>&1 || pgrep -f 'ubuntu12_32/steam' >/dev/null 2>&1
}

steam_stop() {
    log "Закриваємо Steam"
    if command -v steam >/dev/null 2>&1; then
        steam -shutdown >/dev/null 2>&1 &
    elif command -v flatpak >/dev/null 2>&1; then
        flatpak run com.valvesoftware.Steam -shutdown >/dev/null 2>&1 &
    fi
    local i
    for i in $(seq 1 40); do
        steam_running || { sleep 2; log "Steam закрито"; return 0; }
        sleep 1
    done
    log "Steam не закрився за 40 с"
    return 1
}

steam_start() {
    log "Відкриваємо Steam"
    if command -v steam >/dev/null 2>&1; then
        setsid steam >/dev/null 2>&1 < /dev/null &
    elif command -v flatpak >/dev/null 2>&1; then
        setsid flatpak run com.valvesoftware.Steam >/dev/null 2>&1 < /dev/null &
    fi
}

# Вікно прогресу zenity (є на SteamOS та в більшості стільниць). Текст міняється рядками "# текст" у fd 3.
UI_PROGRESS_OPEN=""
ui_progress_open() {
    if has_gui && command -v zenity >/dev/null 2>&1; then
        exec 3> >(zenity --progress --pulsate --no-cancel --auto-close --title="GummerCraft" --width=420 --text="$1" 2>/dev/null)
        UI_PROGRESS_OPEN=1
    fi
}

ui_progress_text() {
    log "$1"
    [ -n "$UI_PROGRESS_OPEN" ] && echo "# $1" >&3
    return 0
}

ui_progress_close() {
    if [ -n "${UI_PROGRESS_OPEN:-}" ]; then
        echo "100" >&3 2>/dev/null
        exec 3>&-
        UI_PROGRESS_OPEN=""
    fi
    return 0
}

main "$@"
