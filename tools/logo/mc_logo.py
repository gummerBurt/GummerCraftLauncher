"""Напис (FABRIC, QUILT LOADER) у стилі логотипа Minecraft: блокові літери, кам'яна грань, темний низ, чорний контур.
Кольори з офіційного SVG Mojang (minecraft.svg). Результат: прозоре полотно 724x180 (як logo_vanilla.png)."""
import random
import sys
from PIL import Image, ImageDraw, ImageFilter, ImageChops

GLYPHS = {
    "F": ["11111", "11000", "11110", "11000", "11000"],
    "A": ["11111", "11011", "11111", "11011", "11011"],
    "B": ["11110", "11011", "11110", "11011", "11110"],
    "R": ["11110", "11011", "11110", "11101", "11011"],
    "I": ["11", "11", "11", "11", "11"],
    "C": ["11111", "11000", "11000", "11000", "11111"],
    "Q": ["11111", "11011", "11011", "11011", "11111", "00011"],
    "U": ["11011", "11011", "11011", "11011", "11111"],
    "L": ["1100", "1100", "1100", "1100", "1111"],
    "T": ["111111", "001100", "001100", "001100", "001100"],
    "O": ["11111", "11011", "11011", "11011", "11111"],
    "D": ["11110", "11011", "11011", "11011", "11110"],
    "E": ["11111", "11000", "11110", "11000", "11111"],
    " ": ["0", "0", "0", "0", "0"],
}
TEXT = sys.argv[3] if len(sys.argv) > 3 else "FABRIC"
SS = 4                 # надвибірка для згладжування
U = 19 * SS            # одиниця сітки
EXT = int(1.7 * U)     # висота темного низу (3D), лише під нижнім рядом, як у логотипі
OUT = int(0.42 * U)    # контур
GAP = 0.42             # чорний шов між літерами, в одиницях

cols = sum(len(GLYPHS[c][0]) for c in TEXT) + GAP * (len(TEXT) - 1)
W = int(cols * U) + 2 * OUT + 8 * SS
H = 5 * U + EXT + 2 * OUT + 8 * SS
ox, oy = OUT + 4 * SS, OUT + 4 * SS

face = Image.new("L", (W, H), 0)
bottom = []
d = ImageDraw.Draw(face)
x0 = 0
cells = []
for ch in TEXT:
    g = GLYPHS[ch]
    for r, row in enumerate(g):
        for c, v in enumerate(row):
            if v == "1":
                x, y = int(ox + (x0 + c) * U), oy + r * U
                d.rectangle([x, y, x + U - 1, y + U - 1], fill=255)
                cells.append((x, y))
                below = g[r + 1][c] if r + 1 < len(g) and c < len(g[r + 1]) else "0"
                if below != "1" and (r == len(g) - 1 or r == 4):
                    bottom.append((x, y))
    x0 += len(g[0]) + GAP

# темний низ: грань, зсунута вниз
ext = Image.new("L", (W, H), 0)
ed = ImageDraw.Draw(ext)
for (x, y) in bottom:
    ed.rectangle([x, y + U - 1, x + U - 1, y + U + EXT], fill=255)
body = ImageChops.lighter(ext, face)
outline = body.filter(ImageFilter.MaxFilter(2 * (OUT // 2) + 1))

img = Image.new("RGBA", (W, H), (0, 0, 0, 0))
img.paste((23, 22, 21, 255), (0, 0), outline)          # #171615

def vgrad(top, bottom, y0, y1):
    g = Image.new("RGBA", (W, H))
    gd = ImageDraw.Draw(g)
    for y in range(H):
        t = min(1, max(0, (y - y0) / max(1, y1 - y0)))
        col = tuple(int(top[i] + (bottom[i] - top[i]) * t) for i in range(3)) + (255,)
        gd.line([(0, y), (W, y)], fill=col)
    return g

side = vgrad((84, 79, 77), (52, 49, 48), oy + 2 * U, oy + 5 * U + EXT)   # #6B6562 .. #3D3938
img.paste(side, (0, 0), ext)
top = vgrad((214, 204, 199), (160, 150, 146), oy, oy + 5 * U)            # світлий камінь згори, темніший донизу
img.paste(top, (0, 0), face)

# тріщини каменю: короткі ламані з тінню і світлим краєм, лише всередині грані
rnd = random.Random(7)
inner = face.filter(ImageFilter.MinFilter(2 * (U // 5) + 1))
cr = Image.new("RGBA", (W, H), (0, 0, 0, 0))
cd = ImageDraw.Draw(cr)
lw = max(SS, U // 9)
for (x, y) in rnd.sample(cells, len(cells) // 4):
    px, py = x + rnd.randint(U // 5, U // 2), y + rnd.randint(U // 5, U // 2)
    pts = [(px, py)]
    for _ in range(rnd.randint(1, 3)):
        if rnd.random() < 0.5:
            px += rnd.choice([-1, 1]) * rnd.randint(U // 3, 2 * U // 3)
        else:
            py += rnd.choice([-1, 1]) * rnd.randint(U // 4, U // 2)
        pts.append((px, py))
    cd.line([(a + lw, b + lw) for a, b in pts], fill=(232, 226, 222, 200), width=lw)
    cd.line(pts, fill=(112, 104, 100, 230), width=lw)
mask = ImageChops.multiply(cr.getchannel("A"), inner)
cr.putalpha(mask)
img = Image.alpha_composite(img, cr)

# світла кромка згори кожної горизонтальної грані
hl = Image.new("L", (W, H), 0)
hl = ImageChops.subtract(face, ImageChops.offset(face, 0, lw))
img.paste((236, 231, 228, 255), (0, 0), hl)

img = img.resize((W // SS, H // SS), Image.LANCZOS)
bbox = img.getchannel("A").getbbox()
img = img.crop(bbox)
target_w = int(sys.argv[2]) if len(sys.argv) > 2 else 470
img = img.resize((target_w, round(img.height * target_w / img.width)), Image.LANCZOS)
canvas = Image.new("RGBA", (724, 180), (0, 0, 0, 0))
canvas.paste(img, ((724 - img.width) // 2, (180 - img.height) // 2), img)
canvas.save(sys.argv[1])
print("ok", img.size)
