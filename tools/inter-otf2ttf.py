"""Inter OTF (CFF) -> TTF (glyf), як fontTools Snippets/otf2ttf.py. Аргументи: вхід, вихід."""
import sys
from fontTools.ttLib import TTFont, newTable
from fontTools.pens.cu2quPen import Cu2QuPen
from fontTools.pens.ttGlyphPen import TTGlyphPen

MAX_ERR = 1.0  # одиниці em (2048), як у otf2ttf
POST_FORMAT = 2.0
REVERSE_DIRECTION = True


def glyphs_to_quadratic(glyphs, max_err=MAX_ERR, reverse_direction=REVERSE_DIRECTION):
    quad = {}
    for name in glyphs.keys():
        g = glyphs[name]
        pen = TTGlyphPen(glyphs)
        g.draw(Cu2QuPen(pen, max_err, reverse_direction=reverse_direction))
        quad[name] = pen.glyph()
    return quad


def otf_to_ttf(font, post_format=POST_FORMAT):
    assert font.sfntVersion == 'OTTO'
    assert 'CFF ' in font
    glyph_order = font.getGlyphOrder()
    font['loca'] = newTable('loca')
    font['glyf'] = glyf = newTable('glyf')
    glyf.glyphOrder = glyph_order
    glyf.glyphs = glyphs_to_quadratic(font.getGlyphSet())
    del font['CFF ']
    if 'VORG' in font:
        del font['VORG']
    glyf.compile(font)
    font['maxp'] = maxp = newTable('maxp')
    maxp.tableVersion = 0x00010000
    maxp.maxZones = 1
    maxp.maxTwilightPoints = 0
    maxp.maxStorage = 0
    maxp.maxFunctionDefs = 0
    maxp.maxInstructionDefs = 0
    maxp.maxStackElements = 0
    maxp.maxSizeOfInstructions = 0
    maxp.maxComponentElements = max(len(g.components if hasattr(g, 'components') else []) for g in glyf.glyphs.values())
    maxp.compile(font)
    post = font['post']
    post.formatType = post_format
    post.extraNames = []
    post.mapping = {}
    post.glyphOrder = glyph_order
    font.sfntVersion = '\000\001\000\000'


src, dst = sys.argv[1], sys.argv[2]
f = TTFont(src)
otf_to_ttf(f)
f.save(dst)
print(dst)
