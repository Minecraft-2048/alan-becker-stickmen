"""Textures du mod : blanc pour les traits, crayon, oeufs d'apparition, icône. Usage : textures.py <dossier assets/abstickmen>"""
import math
import sys
from pathlib import Path

from PIL import Image, ImageDraw

racine = Path(sys.argv[1])
(racine / "textures/entity").mkdir(parents=True, exist_ok=True)
(racine / "textures/item").mkdir(parents=True, exist_ok=True)

Image.new("RGBA", (16, 16), (255, 255, 255, 255)).save(racine / "textures/entity/blanc.png")

COULEURS = {"orange": 0xFF8A1A, "red": 0xE53935, "blue": 0x1E88E5, "green": 0x43A047, "yellow": 0xFDD835}


def rvb(c, f=1.0):
    return tuple(max(0, min(255, int(((c >> s) & 255) * f))) for s in (16, 8, 0)) + (255,)


# Crayon : en diagonale, gomme rose en bas à gauche, mine en haut à droite
crayon = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
px = crayon.load()
for i in range(16):
    for j in range(16):
        d = i - (15 - j)          # écart à la diagonale (bas-gauche -> haut-droite)
        t = (i + (15 - j)) / 2    # position le long de la diagonale
        if abs(d) <= 1 and 1 <= t <= 14:
            if t < 3:
                c = (240, 140, 160, 255)            # gomme
            elif t < 4:
                c = (170, 170, 180, 255)            # bague
            elif t < 11:
                c = (250, 200, 40, 255) if d != 1 else (205, 150, 20, 255)   # corps jaune
            elif t < 13:
                c = (230, 190, 140, 255)            # bois taillé
            else:
                c = (50, 50, 55, 255)               # mine
            if abs(d) == 1 and t >= 11:
                if t >= 12.5:
                    continue
            px[i, j] = c
crayon.save(racine / "textures/item/crayon.png")

# Oeufs : forme d'oeuf à la couleur du personnage, petit bonhomme-bâton blanc dessus
masque = [
    "......####......",
    ".....######.....",
    "....########....",
    "...##########...",
    "...##########...",
    "..############..",
    "..############..",
    ".##############.",
    ".##############.",
    ".##############.",
    ".##############.",
    ".##############.",
    "..############..",
    "..############..",
    "...##########...",
    ".....######.....",
]
for nom, c in COULEURS.items():
    oeuf = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    p = oeuf.load()
    for y, ligne in enumerate(masque):
        for x, ch in enumerate(ligne):
            if ch == "#":
                bord = any(
                    not (0 <= y + dy < 16 and 0 <= x + dx < 16 and masque[y + dy][x + dx] == "#")
                    for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))
                clair = 1.15 if x < 7 and y < 8 else 1.0
                p[x, y] = rvb(c, 0.55) if bord else rvb(c, clair)
    blanc = (255, 255, 255, 255)
    # tête (creuse pour l'orange)
    for x, y in ((7, 3), (8, 3), (6, 4), (9, 4), (6, 5), (9, 5), (7, 6), (8, 6)):
        p[x, y] = blanc
    if nom != "orange":
        for x, y in ((7, 4), (8, 4), (7, 5), (8, 5)):
            p[x, y] = blanc
    for y in range(7, 11):
        p[7, y] = blanc
    for x, y in ((6, 8), (5, 9), (8, 8), (9, 9), (6, 11), (5, 12), (8, 11), (9, 12)):
        p[x, y] = blanc
    oeuf.save(racine / f"textures/item/{nom}_spawn_egg.png")

# Icône : les cinq, dessinés comme sur une feuille
taille = 256
icone = Image.new("RGBA", (taille, taille), (250, 247, 238, 255))
d = ImageDraw.Draw(icone)
for k in range(0, taille, 16):
    d.line([(0, k), (taille, k)], fill=(222, 228, 240, 255), width=1)
e = 7
for n, (nom, c) in enumerate(COULEURS.items()):
    x = 24 + n * 47
    sol = 230
    hanche = sol - 64
    cou = hanche - 56
    tete = cou - 26
    col = rvb(c)[:3]
    pose = n % 2
    d.line([(x, cou), (x, hanche)], fill=col, width=e)
    d.line([(x, hanche), (x - 14, sol)], fill=col, width=e)
    d.line([(x, hanche), (x + 14, sol)], fill=col, width=e)
    if pose:
        d.line([(x, cou), (x - 20, cou - 30)], fill=col, width=e)
        d.line([(x, cou), (x + 20, cou - 30)], fill=col, width=e)
    else:
        d.line([(x, cou), (x - 18, cou + 34)], fill=col, width=e)
        d.line([(x, cou), (x + 18, cou + 34)], fill=col, width=e)
    for px_, py_ in ((x, cou), (x, hanche), (x - 14, sol), (x + 14, sol)):
        d.ellipse([px_ - e / 2, py_ - e / 2, px_ + e / 2, py_ + e / 2], fill=col)
    r = 21
    if nom == "orange":
        d.ellipse([x - r, tete - r, x + r, tete + r], outline=col, width=e)
    else:
        d.ellipse([x - r, tete - r, x + r, tete + r], fill=col)
    if nom == "yellow":
        # Le bâton de commande de Jaune, comme dans la série
        d.line([(x + 28, sol), (x + 28, cou - 40)], fill=(110, 75, 42), width=5)
        d.rectangle([x + 20, cou - 58, x + 36, cou - 42], fill=(200, 125, 60), outline=(90, 60, 40), width=2)
icone.resize((128, 128), Image.LANCZOS).save(racine / "icon.png")
print("ok")
