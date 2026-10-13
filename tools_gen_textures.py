# Genera las texturas del mod (corazones y Mao). Ejecutar: python3 tools_gen_textures.py
from PIL import Image
import os
OUT = "src/main/resources/assets/corazondemelon/textures"

HEART = [
"..XXX...XXX..",
".XXXXX.XXXXX.",
"XXXXXXXXXXXXX",
"XXXXXXXXXXXXX",
"XXXXXXXXXXXXX",
".XXXXXXXXXXX.",
"..XXXXXXXXX..",
"...XXXXXXX...",
"....XXXXX....",
".....XXX.....",
"......X......",
]
def heart(name, fill, dark, light, sparkle=False):
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    ox, oy = 1, 2
    mask = set()
    for y, row in enumerate(HEART):
        for x, c in enumerate(row):
            if c == "X": mask.add((x, y))
    for (x, y) in mask:
        edge = any((x+dx, y+dy) not in mask for dx, dy in ((1,0),(-1,0),(0,1),(0,-1)))
        img.putpixel((x+ox, y+oy), dark+(255,) if edge else fill+(255,))
    for (x, y) in [(2,3),(3,3),(2,4),(3,2),(9,3)]:
        if (x, y) in mask: img.putpixel((x+ox, y+oy), light+(255,))
    if sparkle:
        for (x, y) in [(0,1),(14,0),(15,6),(1,12),(13,13)]:
            img.putpixel((x, y), (255,255,255,255))
    img.save(f"{OUT}/item/{name}.png")

heart("corazon_magico",          (255,105,180), (150,30,90),  (255,200,230))
heart("corazon_magico_azul",     (80,170,255),  (20,70,160),  (200,235,255))
heart("corazon_magico_dorado",   (255,205,40),  (170,110,0),  (255,245,170), True)
heart("corazon_magico_violeta",  (190,90,255),  (90,30,150),  (235,200,255), True)

# ---- Mao (64x64) ----
img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
def rect(x, y, w, h, c):
    for i in range(x, x+w):
        for j in range(y, y+h):
            if 0 <= i < 64 and 0 <= j < 64: img.putpixel((i, j), c+(255,))
SKIN=(255,224,200); HAIR=(255,226,140); WHITE=(250,250,250); GOLD=(255,215,0); BLUE=(200,230,255)
# cabeza (0,0) box 8x8x8 -> region 32x16
rect(0,0,32,16,SKIN)
rect(8,0,16,8,HAIR)               # arriba y abajo
rect(0,8,8,8,HAIR); rect(16,8,8,8,HAIR); rect(24,8,8,8,HAIR)  # lados y nuca
rect(8,8,8,2,HAIR)                # flequillo
rect(8,10,1,1,HAIR); rect(15,10,1,1,HAIR)
rect(9,12,2,2,(60,40,30)); rect(13,12,2,2,(60,40,30))   # ojos
rect(9,12,1,1,(255,255,255)); rect(13,12,1,1,(255,255,255))
rect(8,14,2,1,(255,150,160)); rect(14,14,2,1,(255,150,160))  # mejillas
rect(11,14,2,1,(200,90,90))       # boca
# cuerpo (0,16) 6x5x4 -> 20x9
rect(0,16,20,9,WHITE); rect(4,23,6,1,GOLD)
# brazos (24,16) y (32,16): 8x6
rect(24,16,8,6,WHITE); rect(32,16,8,6,WHITE); rect(24,20,8,2,SKIN); rect(32,20,8,2,SKIN)
# piernas (40,16) y (48,16): 8x4
rect(40,16,8,4,SKIN); rect(48,16,8,4,SKIN)
# alas (0,32) y (20,32): 18x11
for ox in (0,20):
    rect(ox,32,18,11,WHITE)
    rect(ox,32,18,1,BLUE); rect(ox,42,18,1,BLUE); rect(ox,32,1,11,BLUE); rect(ox+17,32,1,11,BLUE)
# aureola (0,48): 24x7
rect(0,48,24,7,GOLD); rect(6,48,6,6,(255,245,150))
img.save(f"{OUT}/entity/mao.png")
print("ok")
