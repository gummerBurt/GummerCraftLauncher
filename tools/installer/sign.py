"""Підпис exe лаунчера сертифікатом Certum (з 1.36.15, перший підписаний реліз 24.11.2026).

    python -I tools/installer/sign.py <відбиток SHA-1> <файл.exe> [--launch4j]

Сертифікат лише за відбитком (/sha1), без автовибору: у сховищі Windows можуть бути інші сертифікати (не для коду
або чужі). Мітка часу Certum, тож підпис лишається дійсним і після закінчення сертифіката. Перед підписом має бути
під'єднаний SimplySign Desktop (значок у треї, Connect to cloud, код з телефону).

--launch4j: exe Launch4j має вшитий jar у кінці файлу (ZIP). Підпис дописується після нього, і тоді в кінці ZIP
«зайві» байти. Як sign4j з дистрибутива Launch4j: спершу підписуємо копію, щоб дізнатись довжину підпису, у
справжньому файлі ставимо таку довжину коментаря ZIP (запис EOCD), і лише тоді підписуємо. Після підпису ZIP
знову цілий: коментар якраз покриває підпис.

Після підпису: signtool verify /pa і звірка відбитка вбудованого підписанта. Будь-яка невідповідність: код виходу 1.
"""
import glob
import os
import shutil
import struct
import subprocess
import sys
import tempfile

TS = "http://time.certum.pl"


def signtool():
    c = sorted(glob.glob(r"C:\Program Files (x86)\Windows Kits\10\bin\10.*\x64\signtool.exe"), reverse=True)
    if not c:
        sys.exit("Немає signtool.exe (Windows SDK, Windows Kits 10)")
    return c[0]


def sign(st, thumb, path):
    r = subprocess.run([st, "sign", "/sha1", thumb, "/fd", "sha256", "/tr", TS, "/td", "sha256", path],
                       capture_output=True, text=True)
    if r.returncode != 0:
        sys.exit("signtool sign не вдався (SimplySign Desktop під'єднаний?):\n" + r.stdout + r.stderr)


def eocd(data):
    """Зміщення запису EOCD (кінець ZIP) або -1."""
    i = data.rfind(b"PK\x05\x06", max(0, len(data) - 70000))
    return i


def patch_comment(path, n):
    with open(path, "rb") as f:
        data = bytearray(f.read())
    i = eocd(data)
    if i < 0:
        sys.exit("У " + path + " немає ZIP (це не exe Launch4j?)")
    if i + 22 + struct.unpack("<H", data[i + 20:i + 22])[0] != len(data):
        sys.exit("ZIP у " + path + " не в самому кінці файлу (вже підписаний?)")
    data[i + 20:i + 22] = struct.pack("<H", n)
    with open(path, "wb") as f:
        f.write(data)


def zip_ok(path):
    with open(path, "rb") as f:
        data = f.read()
    i = eocd(data)
    return i >= 0 and i + 22 + struct.unpack("<H", data[i + 20:i + 22])[0] == len(data)


def signer_thumb(path):
    ps = ("$c=[System.Security.Cryptography.X509Certificates.X509Certificate2]::new("
          "[System.Security.Cryptography.X509Certificates.X509Certificate]::CreateFromSignedFile($env:GC_SIGN_FILE));"
          "$c.Thumbprint")
    env = dict(os.environ, GC_SIGN_FILE=os.path.abspath(path))
    r = subprocess.run(["powershell", "-NoProfile", "-Command", ps], capture_output=True, text=True, env=env)
    return r.stdout.strip().upper()


def main():
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    if len(args) != 2:
        sys.exit(__doc__)
    thumb, path = args[0].upper(), args[1]
    st = signtool()
    if "--launch4j" in sys.argv:
        size = os.path.getsize(path)
        tmp = os.path.join(tempfile.mkdtemp(), "probe.exe")
        shutil.copyfile(path, tmp)
        sign(st, thumb, tmp)
        siglen = os.path.getsize(tmp) - size
        shutil.rmtree(os.path.dirname(tmp), ignore_errors=True)
        patch_comment(path, siglen)
        sign(st, thumb, path)
        if not zip_ok(path):
            sys.exit("Після підпису довжина коментаря ZIP не збіглась з підписом: " + path)
    else:
        sign(st, thumb, path)
    r = subprocess.run([st, "verify", "/pa", path], capture_output=True, text=True)
    if r.returncode != 0:
        sys.exit("signtool verify не пройшов:\n" + r.stdout + r.stderr)
    got = signer_thumb(path)
    if got != thumb:
        sys.exit("ЧУЖИЙ ПІДПИС: у " + path + " підписант " + (got or "?") + ", а має бути " + thumb)
    print("підписано: " + os.path.basename(path) + " (" + got + ")")


if __name__ == "__main__":
    main()
