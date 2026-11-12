#!/bin/sh
# Збирає установку лаунчера з вбудованою Java (1.32+): GummerCraft-Setup-<версія>.exe і портативний GummerCraft-<версія>-portable.zip.
#
#   sh tools/installer/build-installer.sh [версія] [шлях до exe Launch4j]
#
# Версія за замовчуванням з build.gradle, exe з launcher-bootstrap/build/launch4j/Gummer_Craft_launcher.exe
# (спершу ./gradlew :launcher-bootstrap:createExe). Дві JRE (17 і 8) качаються за tools/installer/jre.lock, sha256 перевіряється,
# кеш у tools/installer/.cache (не в git). Потрібен Inno Setup 6 (ISCC.exe) і python (розпаковка zip).
# Вихід: launcher-bootstrap/build/installer/.
set -eu

ROOT=$(cd "$(dirname "$0")/../.." && pwd)
HERE="$ROOT/tools/installer"
VERSION=${1:-$(sed -n "s/^ *version = '\([0-9.]*\)'.*/\1/p" "$ROOT/build.gradle" | head -1)}
EXE=${2:-"$ROOT/launcher-bootstrap/build/launch4j/Gummer_Craft_launcher.exe"}
OUT="$ROOT/launcher-bootstrap/build/installer"
STAGE="$OUT/stage"
CACHE="$HERE/.cache"

ISCC=""
for c in "$LOCALAPPDATA/Programs/Inno Setup 6/ISCC.exe" "/c/Program Files (x86)/Inno Setup 6/ISCC.exe" "/c/Program Files/Inno Setup 6/ISCC.exe"; do
  [ -f "$c" ] && ISCC="$c" && break
done
[ -n "$ISCC" ] || { echo "Немає Inno Setup 6 (ISCC.exe)"; exit 1; }
[ -f "$EXE" ] || { echo "Немає exe: $EXE (спершу ./gradlew :launcher-bootstrap:createExe)"; exit 1; }

rm -rf "$STAGE" && mkdir -p "$STAGE" "$CACHE"
cp "$EXE" "$STAGE/GummerCraft.exe"
cp "$ROOT/launcher-bootstrap/src/main/resources/com/gummer/launcher/jaunc4j/app.ico" "$STAGE/app.ico"

# jre17 -> stage/jre (Windows 10+), jre8 -> stage/jre8 (Windows 7/8.1); див. jre.lock
fetch_jre() { # $1 - ключ у jre.lock (jre17|jre8), $2 - тека в stage
  NAME=$(sed -n "s/^$1\.name=//p" "$HERE/jre.lock")
  URL=$(sed -n "s/^$1\.url=//p" "$HERE/jre.lock")
  SHA=$(sed -n "s/^$1\.sha256=//p" "$HERE/jre.lock")
  [ -n "$NAME" ] && [ -n "$URL" ] && [ -n "$SHA" ] || { echo "У jre.lock немає $1"; exit 1; }
  if [ ! -f "$CACHE/$NAME" ]; then
    echo "Качаю $NAME"
    curl -fL -o "$CACHE/$NAME.part" "$URL"
    mv "$CACHE/$NAME.part" "$CACHE/$NAME"
  fi
  echo "$SHA  $CACHE/$NAME" | sha256sum -c - >/dev/null || { echo "sha256 JRE не збігається з jre.lock: $CACHE/$NAME"; exit 1; }
  # архів Adoptium має одну теку верхнього рівня (jdk...-jre): її вміст стає stage/<тека>
  python - "$CACHE/$NAME" "$STAGE" "$2" <<'PY2'
import os, shutil, sys, zipfile
src, stage, name = sys.argv[1], sys.argv[2], sys.argv[3]
tmp = os.path.join(stage, "_unzip")
zipfile.ZipFile(src).extractall(tmp)
tops = os.listdir(tmp)
assert len(tops) == 1, tops
shutil.move(os.path.join(tmp, tops[0]), os.path.join(stage, name))
os.rmdir(tmp)
PY2
  [ -f "$STAGE/$2/bin/javaw.exe" ] || { echo "У $2 немає bin/javaw.exe"; exit 1; }
}
fetch_jre jre17 jre
fetch_jre jre8 jre8

STAGE_WIN=$(cygpath -w "$STAGE" 2>/dev/null || echo "$STAGE")
OUT_WIN=$(cygpath -w "$OUT" 2>/dev/null || echo "$OUT")
ISS_WIN=$(cygpath -w "$HERE/GummerCraft.iss" 2>/dev/null || echo "$HERE/GummerCraft.iss")
"$ISCC" //Q "//DAppVersion=$VERSION" "//DStage=$STAGE_WIN" "//O$OUT_WIN" "$ISS_WIN"

# портативний zip: GummerCraft/ з exe і jre (Java 17, тобто Windows 10+; Windows 7/8.1 - лише Setup), без установки
python - "$STAGE" "$OUT/GummerCraft-$VERSION-portable.zip" <<'PY'
import os, sys, zipfile
stage, out = sys.argv[1], sys.argv[2]
with zipfile.ZipFile(out, "w", zipfile.ZIP_DEFLATED, compresslevel=9) as z:
    z.write(os.path.join(stage, "GummerCraft.exe"), "GummerCraft/GummerCraft.exe")
    for d, _, files in os.walk(os.path.join(stage, "jre")):
        for f in files:
            p = os.path.join(d, f)
            z.write(p, "GummerCraft/" + os.path.relpath(p, stage).replace(os.sep, "/"))
PY

echo "Готово:"
ls -la "$OUT"/GummerCraft-Setup-"$VERSION".exe "$OUT"/GummerCraft-"$VERSION"-portable.zip
sha256sum "$OUT"/GummerCraft-Setup-"$VERSION".exe "$OUT"/GummerCraft-"$VERSION"-portable.zip
