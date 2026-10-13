#!/usr/bin/env python3
"""
Перевірка: чи офіційний лаунчер на CDN зібраний із цього коду.

Що робить:
  1. читає release.json з CDN (адреса й SHA-256 поточної версії);
  2. завантажує офіційний jar і звіряє його хеш з опублікованим;
  3. порівнює вміст офіційного jar з вашою збіркою (launcher-flat/build/libs/...) файл за файлом.

Очікувана різниця - лише com/skcraft/launcher/launcher.properties: там ID застосунку Microsoft (у репозиторій не входить)
і дані збірки (commit, branch, pipelineId). Хеш jar цілком не збігається ніколи - у zip записані дати файлів, тому порівнюється вміст.

Використання (потрібен лише Python 3, без сторонніх пакетів):
  ./gradlew clean build                 # спершу зберіть лаунчер (дивись README)
  python tools/verify-cdn.py            # або: python tools/verify-cdn.py шлях/до/вашого.jar

Офіційний jar зібрано з коміту, записаного в його launcher.properties (commit=...). Якщо ваша збірка з іншого коміту,
скрипт підкаже, який саме зробити git checkout.
"""
import glob
import hashlib
import io
import json
import os
import sys
import urllib.request
import zipfile

RELEASE_URL = "https://cdn.gummer.cc/launcher/install/release.json"
PROPS = "com/skcraft/launcher/launcher.properties"
# ключі launcher.properties, які законно відрізняються між офіційною і вашою збіркою
EXPECTED_PROPS = {"microsoftClientId", "commit", "branch", "pipelineId"}


def fetch(url):
    request = urllib.request.Request(url, headers={"User-Agent": "GummerCraft-verify-cdn"})
    with urllib.request.urlopen(request, timeout=60) as response:
        return response.read()


def entries(data):
    with zipfile.ZipFile(io.BytesIO(data)) as z:
        return {n: z.read(n) for n in z.namelist() if not n.endswith("/")}


def props(raw):
    result = {}
    for line in raw.decode("utf-8", "replace").splitlines():
        line = line.strip()
        if line and not line.startswith("#") and "=" in line:
            key, value = line.split("=", 1)
            result[key.strip()] = value.strip()
    return result


def find_local_jar():
    root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    jars = sorted(glob.glob(os.path.join(root, "launcher-flat", "build", "libs", "launcher-flat-*.jar")), key=os.path.getmtime)
    return jars[-1] if jars else None


def main():
    try:
        sys.stdout.reconfigure(encoding="utf-8")  # українські повідомлення й при виводі у файл/пайп на Windows
    except (AttributeError, ValueError):
        pass
    local_path = sys.argv[1] if len(sys.argv) > 1 else find_local_jar()
    if not local_path or not os.path.isfile(local_path):
        print("Не знайдено вашої збірки. Спершу виконайте ./gradlew clean build (або вкажіть шлях до jar аргументом).")
        return 2

    release = json.loads(fetch(RELEASE_URL))
    print("Опублікована версія:", release.get("version"), "->", release.get("url"))
    official = fetch(release["url"])
    actual = hashlib.sha256(official).hexdigest()
    expected = (release.get("sha256") or "").lower()
    if expected:
        status = "збігається" if actual == expected else "НЕ ЗБІГАЄТЬСЯ"
        print("SHA-256 завантаженого jar:", actual, "-", status, "з release.json")
        if actual != expected:
            return 1

    with open(local_path, "rb") as f:
        local = f.read()
    a, b = entries(official), entries(local)
    differing = sorted(n for n in set(a) | set(b) if a.get(n) != b.get(n))
    print("Файлів в офіційному jar:", len(a), " у вашій збірці:", len(b), " (", os.path.basename(local_path), ")")

    official_props = props(a.get(PROPS, b""))
    local_props = props(b.get(PROPS, b""))
    if official_props.get("version") != local_props.get("version") or official_props.get("commit") != local_props.get("commit"):
        print()
        print("Офіційна збірка - версія", official_props.get("version"), "з коміту", official_props.get("commit"),
              "; ваша - версія", local_props.get("version"), "з коміту", str(local_props.get("commit")) + ".")
        print("Щоб порівнювати однакове: git checkout", official_props.get("commit"), "і зберіть ще раз.")

    unexpected = [n for n in differing if n != PROPS]
    prop_keys = sorted(k for k in set(official_props) | set(local_props) if official_props.get(k) != local_props.get(k))
    unexpected_props = [k for k in prop_keys if k not in EXPECTED_PROPS]

    print()
    if not unexpected and not unexpected_props:
        print("РЕЗУЛЬТАТ: офіційний jar відповідає коду. Відрізняється лише", PROPS, "->", ", ".join(prop_keys) or "нічого")
        return 0
    print("РЕЗУЛЬТАТ: є відмінності поза очікуваними:")
    for name in unexpected[:50]:
        print("  файл:", name)
    if len(unexpected) > 50:
        print("  ... і ще", len(unexpected) - 50)
    for key in unexpected_props:
        print("  launcher.properties:", key, ":", official_props.get(key), "(офіційний) /", local_props.get(key), "(ваш)")
    return 1


if __name__ == "__main__":
    sys.exit(main())
