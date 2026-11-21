"""Обкладинки Steam для ярлика GummerCraft: кавер 600x900, широка 920x430, шапка 1920x620, логотип.
python tools/linux/steam/make.py <out>  (набір village скопійовано сюди: cover, wide, hero, logo)
  -> <out>/<набір>/{cover,wide,hero,logo}.png і <out>/preview-<набір>.png"""
import sys, os
from PIL import Image, ImageFilter, ImageEnhance, ImageDraw

F = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..', '..', 'launcher', 'src', 'main', 'resources', 'com', 'skcraft', 'launcher', 'ui', 'frame') + os.sep
OUT = sys.argv[1]
LOGO = Image.open(F + 'logo.png').convert('RGBA')
LOGO = LOGO.crop(LOGO.getbbox())

SETS = {
    # назва: (фон, центр кадру по x для вертикального кавера 0..1, зсув по y для шапки 0..1)
    'village': (F + 'bg.jpg', 0.52, 0.55),
    'autumn': (F + 'bg_fabric.jpg', 0.50, 0.45),
    'zone': (F + 'bg_staldayz.jpg', 0.55, 0.60),
}


def cover_fit(im, w, h, cx=0.5, cy=0.5):
    s = max(w / im.width, h / im.height)
    r = im.resize((round(im.width * s), round(im.height * s)), Image.LANCZOS)
    x = min(max(0, round(r.width * cx - w / 2)), r.width - w)
    y = min(max(0, round(r.height * cy - h / 2)), r.height - h)
    return r.crop((x, y, x + w, y + h))


def shade(img, top, bottom, start=0.0):
    """Затемнення градієнтом зверху/знизу (0..255), щоб лого читалось."""
    w, h = img.size
    g = Image.new('L', (1, h))
    for y in range(h):
        t = y / (h - 1)
        a = top * max(0, 1 - t / 0.45) + bottom * max(0, (t - start) / (1 - start)) if start < 1 else top * max(0, 1 - t / 0.45)
        g.putpixel((0, y), int(min(255, a)))
    mask = g.resize((w, h))
    dark = Image.new('RGB', (w, h), (8, 10, 14))
    return Image.composite(dark, img.convert('RGB'), mask)


def put_logo(img, width, cx, cy, glow=True):
    lg = LOGO.resize((width, round(LOGO.height * width / LOGO.width)), Image.LANCZOS)
    x, y = round(cx - lg.width / 2), round(cy - lg.height / 2)
    base = img.convert('RGBA')
    if glow:
        sh = Image.new('RGBA', base.size, (0, 0, 0, 0))
        a = lg.split()[3].point(lambda v: int(v * 0.85))
        sh.paste(Image.new('RGBA', lg.size, (0, 0, 0, 255)), (x, y + max(3, width // 120)), a)
        sh = sh.filter(ImageFilter.GaussianBlur(max(4, width // 60)))
        base = Image.alpha_composite(base, sh)
    base.alpha_composite(lg, (x, y))
    return base.convert('RGB')


os.makedirs(OUT, exist_ok=True)
for name, (bgp, ccx, hcy) in SETS.items():
    d = os.path.join(OUT, name)
    os.makedirs(d, exist_ok=True)
    bg = Image.open(bgp).convert('RGB')
    bg = ImageEnhance.Color(bg).enhance(1.08)
    # кавер: лого вгорі, низ трохи темніший
    cov = shade(cover_fit(bg, 600, 900, ccx, 0.5), 170, 120, 0.6)
    cov = put_logo(cov, 540, 300, 150)
    cov.save(os.path.join(d, 'cover.png'), optimize=True)
    # широка плитка: лого по центру трохи вище середини
    wide = shade(cover_fit(bg, 920, 430, 0.5, 0.5), 60, 140, 0.4)
    wide = put_logo(wide, 640, 460, 200)
    wide.save(os.path.join(d, 'wide.png'), optimize=True)
    # шапка без лого (Steam кладе логотип зверху сам)
    hero = cover_fit(bg, 1920, 620, 0.5, hcy)
    hero.save(os.path.join(d, 'hero.png'), optimize=True)
    # логотип: прозорий PNG, удвічі більший для чіткості на телевізорі
    LOGO.resize((LOGO.width * 2, LOGO.height * 2), Image.LANCZOS).save(os.path.join(d, 'logo.png'), optimize=True)

    # макет сторінки бібліотеки Steam: шапка з лого внизу ліворуч, під нею кавер і широка плитка
    pv = Image.new('RGB', (1920, 1600), (23, 26, 33))
    pv.paste(hero, (0, 0))
    pv = put_logo(pv, 760, 60 + 380, 620 - 40 - round(LOGO.height * 760 / LOGO.width / 2), glow=False)
    pv.paste(cov, (60, 660 - 20 + 20))
    pv.paste(wide.resize((920, 430)), (720, 660))
    ImageDraw.Draw(pv).text((720, 1110), name, fill=(200, 200, 200))
    pv.resize((960, 800), Image.LANCZOS).save(os.path.join(OUT, 'preview-%s.png' % name))
print('ok')
