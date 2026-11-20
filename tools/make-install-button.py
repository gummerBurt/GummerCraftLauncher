# 20.11.2026: install_uk_ua.png / install_en_us.png (кнопка «ВСТАНОВИТИ» в Liquid Glass) з play_*.png: той самий фон 302x101,
# напис шрифтом ui/font/minecraftfontukr.ttf (8 px, жирний як у Minecraft), розтягнутий без згладжування.
# Запуск: python -X utf8 tools/make-install-button.py (потрібен Pillow)
from PIL import Image, ImageDraw, ImageFont
B='C:/Main/GummerGit/launcher/public/launcher/src/main/resources/com/skcraft/launcher/ui/'
F=B+'font/minecraftfontukr.ttf'
f=ImageFont.truetype(F,8)
for lang,t in [('uk_ua','ВСТАНОВИТИ'),('en_us','INSTALL')]:
  im=Image.open(B+'button/play_'+lang+'.png').convert('RGBA'); px=im.load(); W,H=im.size
  for y in range(H):
    last=(118,170,22,255)
    for x in range(W):
      p=px[x,y]
      if p[0]>200 and p[1]>200: px[x,y]=last
      elif p[3]==255: last=p
  m=Image.new('L',(300,20),0); d=ImageDraw.Draw(m); x=1
  for ch in t:
    d.text((x,2),ch,font=f,fill=255); d.text((x+1,2),ch,font=f,fill=255)
    x+=int(d.textlength(ch,font=f))+1
  m=m.point(lambda v:255 if v>100 else 0); m=m.crop(m.getbbox())
  tw=min(W-40, m.width*6); th=min(H-36, round(tw/m.width*1.6)*m.height//1)
  th=min(H-36, round(tw/m.width*1.6*m.height))
  m=m.resize((tw,th),Image.NEAREST)
  im.paste(Image.new('RGBA',m.size,(255,255,255,255)),((W-tw)//2,(H-th)//2),m)
  im.save(B+'button/install_'+lang+'.png'); im.save('install_'+lang+'.png'); print(lang,tw,th)
