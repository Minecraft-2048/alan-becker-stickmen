"""Lance le Minecraft d'une instance CurseForge Fabric sans passer par le lanceur, hors ligne,
avec des mods ajoutés seulement pour ce lancement (fabric.addMods) et des propriétés Java en plus.

Usage : lancer_instance.py <dossier de l'instance> <journal> <natives> [-Dpropriete=valeur ...]
"""
import json
import os
import subprocess
import sys
import uuid
from pathlib import Path

CF = Path(os.environ["USERPROFILE"]) / "curseforge" / "minecraft" / "Install"
JAVA = Path("C:/Program Files/Eclipse Adoptium/jdk-25.0.3.9-hotspot/bin/java.exe")

instance, journal, natives = Path(sys.argv[1]), Path(sys.argv[2]), Path(sys.argv[3])
proprietes = sys.argv[4:]

conf = json.load(open(instance / "minecraftinstance.json", encoding="utf-8"))
chargeur = conf["baseModLoader"]["name"]                      # fabric-0.19.5-26.3
nom_version = "fabric-loader-" + chargeur.split("-", 1)[1]    # fabric-loader-0.19.5-26.3
fabric = json.load(open(CF / "versions" / nom_version / f"{nom_version}.json", encoding="utf-8"))
mc = fabric["inheritsFrom"]
vanille = json.load(open(CF / "versions" / mc / f"{mc}.json", encoding="utf-8"))


def autorise(regles):
    if not regles:
        return True
    ok = False
    for r in regles:
        os_ = r.get("os", {})
        if "features" in r:
            continue
        correspond = (not os_.get("name") or os_["name"] == "windows") and (not os_.get("arch") or os_["arch"] == "x64")
        if correspond:
            ok = r["action"] == "allow"
    return ok


def chemin(lib):
    art = lib.get("downloads", {}).get("artifact")
    if art:
        return CF / "libraries" / art["path"]
    groupe, nom, version, *classe = lib["name"].split(":")
    fichier = f"{nom}-{version}" + (f"-{classe[0]}" if classe else "") + ".jar"
    return CF / "libraries" / Path(*groupe.split(".")) / nom / version / fichier


classpath, vus = [], set()
for lib in fabric["libraries"] + vanille["libraries"]:
    if not autorise(lib.get("rules")):
        continue
    morceaux = lib["name"].split(":")
    classe = morceaux[3] if len(morceaux) > 3 else ""
    if classe.startswith("natives") and classe != "natives-windows":
        continue
    cle = (morceaux[0], morceaux[1], classe)
    if cle in vus:
        continue
    vus.add(cle)
    p = chemin(lib)
    if not p.exists():
        print("bibliothèque absente :", p)
        continue
    classpath.append(str(p))
classpath.append(str(CF / "versions" / mc / f"{mc}.jar"))

valeurs = {
    "natives_directory": str(natives), "launcher_name": "essai-abstickmen", "launcher_version": "1.0",
    "classpath": os.pathsep.join(classpath), "auth_player_name": "Joueur", "version_name": nom_version,
    "game_directory": str(instance), "assets_root": str(CF / "assets"), "assets_index_name": vanille["assetIndex"]["id"],
    "auth_uuid": str(uuid.uuid3(uuid.NAMESPACE_OID, "OfflinePlayer:Joueur")), "auth_access_token": "0", "clientid": "0",
    "auth_xuid": "0", "version_type": "release",
}


def remplacer(texte):
    for k, v in valeurs.items():
        texte = texte.replace("${" + k + "}", v)
    return texte


def arguments(liste):
    sortie = []
    for a in liste:
        if isinstance(a, str):
            sortie.append(remplacer(a))
        elif autorise(a.get("rules")) and not any("features" in r for r in a.get("rules", [])):
            v = a["value"]
            sortie.extend(remplacer(x) for x in ([v] if isinstance(v, str) else v))
    return sortie


for d in ("java", "jna", "lwjgl", "netty"):
    (natives / d).mkdir(parents=True, exist_ok=True)
jvm = [f"-Xmx{conf.get('allocatedMemory', 4096)}M"] + arguments(vanille["arguments"]["jvm"])
jvm += [remplacer(a) for a in fabric["arguments"]["jvm"]] + proprietes
commande = [str(JAVA)] + jvm[:jvm.index("-cp")] + jvm[jvm.index("-cp") + 2:] + ["-cp", valeurs["classpath"], fabric["mainClass"]]
commande += arguments(vanille["arguments"]["game"])
with open(journal, "w", encoding="utf-8") as j:
    j.write("COMMANDE : " + " ".join(c if len(c) < 300 else c[:300] + "…" for c in commande) + "\n")
    j.flush()
    code = subprocess.call(commande, cwd=instance, stdout=j, stderr=subprocess.STDOUT)
print("code de sortie", code)
