"""Assemble des captures en planches étiquetées. Usage : planche.py <dossier> <sortie.png> <motif> [colonnes] [largeur vignette]"""
import sys
from pathlib import Path
from PIL import Image, ImageDraw

dossier, sortie, motif = Path(sys.argv[1]), sys.argv[2], sys.argv[3]
cols = int(sys.argv[4]) if len(sys.argv) > 4 else 3
larg = int(sys.argv[5]) if len(sys.argv) > 5 else 420
fichiers = sorted(dossier.glob(motif))
if not fichiers:
    print("aucune image"); sys.exit(0)
vignettes = []
for f in fichiers:
    im = Image.open(f).convert("RGB")
    h = int(im.height * larg / im.width)
    im = im.resize((larg, h), Image.LANCZOS)
    d = ImageDraw.Draw(im)
    nom = f.stem.split("_", 1)[-1] if "_" in f.stem else f.stem
    d.rectangle([0, 0, larg, 14], fill=(0, 0, 0))
    d.text((3, 1), nom[:60], fill=(255, 255, 0))
    vignettes.append(im)
h = vignettes[0].height
lignes = (len(vignettes) + cols - 1) // cols
planche = Image.new("RGB", (cols * larg, lignes * h), (40, 40, 40))
for i, v in enumerate(vignettes):
    planche.paste(v, ((i % cols) * larg, (i // cols) * h))
planche.save(sortie)
print(len(vignettes), "images ->", sortie, planche.size)
