#!/usr/bin/env bash
# GummerCraft: установка лаунчера на Linux x86_64 і Steam Deck одним кліком (з 13.11.2026).
#
# Гравець завантажує GummerCraft-Linux.desktop з сайту і двічі клацає по ньому (Steam Deck: режим робочого столу,
# у вікні «Ненадійна програма» кнопка «Продовжити»). Той запускає цей скрипт з launch.gummercraft.fun. Скрипт:
#   1. ставить Java для самого лаунчера (Temurin 17 JRE, офіційний архів Adoptium, sha256 перевіряється) у ~/GummerCraft/app/jre;
#   2. кладе лаунчер (Gummer_Craft_launcher.jar, той самий, що і для ручного запуску), іконку і скрипт запуску в ~/GummerCraft/app;
#   3. додає GummerCraft у меню програм і на робочий стіл;
#   4. на SteamOS додає GummerCraft у бібліотеку Steam як гру не з Steam (steamos-add-to-steam), щоб грати в ігровому режимі;
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

    # Обкладинки Steam. Steam дає ярлику «не з Steam» випадковий appid і пише його в userdata/<id>/config/shortcuts.vdf,
    # а картинки бере з config/grid/<appid>p.png (кавер 600x900), <appid>.png (920x430), <appid>_hero.png (1920x620),
    # <appid>_logo.png. Скрипт шукає в shortcuts.vdf ярлики на наш gummercraft.sh і кладе картинки лише туди, де їх ще
    # немає (свої обкладинки гравця не перезаписуються). Steam підхоплює їх після перезапуску (перехід в ігровий режим).
    cat > "$APP/steam-art.sh" <<'ART'
#!/usr/bin/env bash
# Ставить обкладинки GummerCraft у бібліотеку Steam. Створено установкою (install.sh). Аргумент: скільки секунд чекати ярлик.
APP="$(cd "$(dirname "$(readlink -f "$0")")" && pwd)"
WAIT="${1:-0}"
[ -d "$APP/steam-art" ] && command -v python3 >/dev/null 2>&1 || exit 0
end=$(( $(date +%s) + WAIT ))
while :; do
    found="$(python3 - "$APP" <<'PY'
import glob, os, re, shutil, struct, sys
app = sys.argv[1]
needle = (app + "/gummercraft.sh").encode()
art = {"cover.png": "%dp.png", "wide.png": "%d.png", "hero.png": "%d_hero.png", "logo.png": "%d_logo.png"}
seen, n = set(), 0
home = os.path.expanduser("~")
for vdf in glob.glob(home + "/.local/share/Steam/userdata/*/config/shortcuts.vdf") + glob.glob(home + "/.steam/steam/userdata/*/config/shortcuts.vdf"):
    real = os.path.realpath(vdf)
    if real in seen:
        continue
    seen.add(real)
    try:
        b = open(real, "rb").read()
    except OSError:
        continue
    marks = [m.end() for m in re.finditer(rb"\x02appid\x00", b, re.I)]
    for k, pos in enumerate(marks):
        entry = b[pos:marks[k + 1] if k + 1 < len(marks) else len(b)]
        if needle not in entry or pos + 4 > len(b):
            continue
        appid = struct.unpack("<I", b[pos:pos + 4])[0]
        grid = os.path.join(os.path.dirname(real), "grid")
        os.makedirs(grid, exist_ok=True)
        for src, name in art.items():
            s, d = os.path.join(app, "steam-art", src), os.path.join(grid, name % appid)
            if os.path.isfile(s) and not os.path.exists(d):
                shutil.copyfile(s, d)
        n += 1
print(n)
PY
)"
    if [ "${found:-0}" -gt 0 ] 2>/dev/null; then
        touch "$APP/.steam-art-done"
        echo "Обкладинки Steam: ярликів $found"
        exit 0
    fi
    [ "$(date +%s)" -ge "$end" ] && exit 0
    sleep 3
done
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

    # 4. Steam (лише SteamOS: там є steamos-add-to-steam). Один раз: повторна установка не плодить дублікати в бібліотеці.
    local STEAM_NOTE=""
    if [ -z "${GC_NO_STEAM:-}" ] && command -v steamos-add-to-steam >/dev/null 2>&1; then
        if [ -f "$APP/.added-to-steam" ]; then
            log "У Steam уже додано раніше"
            STEAM_NOTE=" та в бібліотеці Steam"
        elif steamos-add-to-steam "$ENTRY" >>"$LOG" 2>&1; then
            touch "$APP/.added-to-steam"
            log "Додано в Steam"
            STEAM_NOTE=" та в бібліотеці Steam (розділ «Не з Steam»)"
        else
            log "steamos-add-to-steam не спрацював"
            STEAM_NOTE=". У Steam додайте вручну: Steam -> Ігри -> Додати сторонню гру -> GummerCraft"
        fi
        # обкладинки: Steam пише ярлик у shortcuts.vdf не одразу, тож чекаємо до 90 с у фоні, установка не затримується
        if [ -f "$APP/.added-to-steam" ] && [ ! -f "$APP/.steam-art-done" ]; then
            nohup "$APP/steam-art.sh" 90 >>"$LOG" 2>&1 &
        fi
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
