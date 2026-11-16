#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Збирає «базу Minecraft» для першої установки (лаунчер 1.35+, com.gummer.launcher.install.BaseArchive):
ассети (objects) і бібліотеки Mojang для вказаних версій гри, архівами по ~450 МБ, плюс index.json.

Запуск (краще прямо на CDN-хості, там швидкий канал до Mojang):
    python3 build-base.py --out /mnt/cdn-volume/gummercraft/launcher/base 1.20.1 1.12.2

Що робить:
  * бере version_manifest_v2 Mojang, json версії, індекс ассетів;
  * качає кожен об'єкт і бібліотеку (усі ОС: artifact і natives-класифікатори) у кеш --cache, звіряючи sha1;
  * пише <версія>-<n>.zip: assets/objects/xx/<sha1> і libraries/<шлях maven>, у кожній частині sha1.txt
    («<sha1> <шлях від libraries/>»);
  * index.json оновлює лише для зібраних версій (інші лишаються), записує його атомарно (через .tmp);
  * старі частини, яких більше немає в index.json, видаляє після запису нового індексу, якщо їм понад 2 доби.

Лаунчер бере архів лише при першій установці версії (немає assets/indexes/<assetId>.json), перевіряє sha256
частини і sha1 кожного файлу; що не збіглось або чого немає, докачує пофайлово, як раніше. Тож архів можна
перезібрати будь-коли: старий кеш у гравців просто не збіжеться за описом і почнеться з нуля.
"""

import argparse
import hashlib
import json
import os
import sys
import time
import urllib.request
import zipfile

MANIFEST = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"
UA = "GummerCraft-base-builder/1.0"
PART_LIMIT = 450 * 1024 * 1024


def fetch(url, tries=5):
    last = None
    for t in range(tries):
        try:
            req = urllib.request.Request(url, headers={"User-Agent": UA})
            with urllib.request.urlopen(req, timeout=60) as r:
                return r.read()
        except Exception as e:  # noqa: BLE001
            last = e
            time.sleep(2 * (t + 1))
    raise RuntimeError("не вдалося скачати %s: %s" % (url, last))


def sha1_of(data):
    return hashlib.sha1(data).hexdigest()


def cached(cache, sha1, url):
    """Файл з кешу за sha1 (скачати, якщо немає). Повертає шлях."""
    path = os.path.join(cache, sha1[:2], sha1)
    if os.path.isfile(path):
        with open(path, "rb") as f:
            if sha1_of(f.read()) == sha1:
                return path
    data = fetch(url)
    if sha1_of(data) != sha1:
        raise RuntimeError("sha1 не збігся: %s" % url)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    tmp = path + ".tmp"
    with open(tmp, "wb") as f:
        f.write(data)
    os.replace(tmp, path)
    return path


def collect(version_id, cache):
    """Список (шлях в архіві, sha1, файл у кеші) для версії і id індексу ассетів."""
    manifest = json.loads(fetch(MANIFEST))
    entry = next((v for v in manifest["versions"] if v["id"] == version_id), None)
    if entry is None:
        raise SystemExit("версії %s немає в маніфесті Mojang" % version_id)
    version = json.loads(fetch(entry["url"]))
    asset_index = version["assetIndex"]
    index = json.loads(fetch(asset_index["url"]))

    items = {}
    objects = index.get("objects", {})
    for n, (_, obj) in enumerate(sorted(objects.items())):
        h = obj["hash"]
        name = "assets/objects/%s/%s" % (h[:2], h)
        if name in items:
            continue
        url = "https://resources.download.minecraft.net/%s/%s" % (h[:2], h)
        items[name] = (h, cached(cache, h, url))
        if n % 500 == 0:
            print("  ассети %d/%d" % (n, len(objects)), flush=True)

    libs = 0
    for lib in version.get("libraries", []):
        downloads = lib.get("downloads", {})
        arts = []
        if downloads.get("artifact"):
            arts.append(downloads["artifact"])
        arts.extend((downloads.get("classifiers") or {}).values())
        for a in arts:
            path, h, url = a.get("path"), a.get("sha1"), a.get("url")
            if not path or not h or not url or ".." in path:
                continue
            name = "libraries/" + path
            if name not in items:
                items[name] = (h, cached(cache, h, url))
                libs += 1
    print("  %s: %d ассетів, %d бібліотек" % (version_id, len(objects), libs), flush=True)
    return asset_index["id"], items


def write_parts(version_id, items, out):
    safe = "".join(c if c.isalnum() or c in "._-" else "_" for c in version_id)
    names = sorted(items, key=lambda n: (not n.startswith("libraries/"), n))
    parts, current, size = [], [], 0
    for n in names:
        s = os.path.getsize(items[n][1])
        if current and size + s > PART_LIMIT:
            parts.append(current)
            current, size = [], 0
        current.append(n)
        size += s
    if current:
        parts.append(current)

    result = []
    stamp = time.strftime("%Y%m%d%H%M")
    for i, group in enumerate(parts, 1):
        fname = "%s-%s-%d.zip" % (safe, stamp, i)
        tmp = os.path.join(out, fname + ".tmp")
        with zipfile.ZipFile(tmp, "w", zipfile.ZIP_DEFLATED, compresslevel=6) as z:
            sha = ["%s %s" % (items[n][0], n[len("libraries/"):]) for n in group if n.startswith("libraries/")]
            z.writestr("sha1.txt", "\n".join(sha) + "\n")
            for n in group:
                z.write(items[n][1], n)
        h = hashlib.sha256()
        with open(tmp, "rb") as f:
            for chunk in iter(lambda: f.read(1 << 20), b""):
                h.update(chunk)
        os.replace(tmp, os.path.join(out, fname))
        size = os.path.getsize(os.path.join(out, fname))
        result.append({"url": fname, "size": size, "sha256": h.hexdigest()})
        print("  %s: %d файлів, %.1f МБ" % (fname, len(group), size / 1048576), flush=True)
    return result


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--out", required=True, help="тека на CDN (буде https://cdn.gummercraft.fun/launcher/base/)")
    ap.add_argument("--cache", default=None, help="кеш скачаного (за замовчуванням <out>/../base-cache)")
    ap.add_argument("versions", nargs="+", help="версії гри, напр. 1.20.1")
    args = ap.parse_args()

    out = os.path.abspath(args.out)
    cache = os.path.abspath(args.cache or os.path.join(out, "..", "base-cache"))
    os.makedirs(out, exist_ok=True)
    os.makedirs(cache, exist_ok=True)

    index_path = os.path.join(out, "index.json")
    index = {"v": 1, "bases": {}}
    if os.path.isfile(index_path):
        with open(index_path, encoding="utf-8") as f:
            index = json.load(f)

    for v in args.versions:
        print("Версія %s" % v, flush=True)
        asset_id, items = collect(v, cache)
        index["bases"][v] = {"assetIndex": asset_id, "parts": write_parts(v, items, out)}

    tmp = index_path + ".tmp"
    with open(tmp, "w", encoding="utf-8") as f:
        json.dump(index, f, ensure_ascii=False, indent=1)
    os.replace(tmp, index_path)

    # частини, на які index.json більше не посилається (попередні збірки), видалити
    # (лише старші за 2 доби: хто зараз докачує стару частину, встигне докачати)
    keep = {p["url"] for b in index["bases"].values() for p in b["parts"]}
    for fname in os.listdir(out):
        old = time.time() - os.path.getmtime(os.path.join(out, fname)) > 2 * 86400
        if fname.endswith(".zip") and fname not in keep and old:
            os.remove(os.path.join(out, fname))
            print("  видалено стару частину %s" % fname)
    print("Готово: %s" % index_path)


if __name__ == "__main__":
    sys.exit(main())
