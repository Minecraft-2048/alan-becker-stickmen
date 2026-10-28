#!/bin/sh
# Joue le test en jeu (avec captures d'écran) dans le vrai Minecraft d'une instance CurseForge :
# ses mods, sa configuration, son Fabric. Le test se lance hors du lanceur, hors ligne, et
# n'ajoute rien à l'instance : à la fin, ses réglages sont remis et le monde de test est sorti.
#
#   sh outils/tester-dans-instance.sh ["Test mods Fabric 26.3"] [scènes : gestes,angles,commande,combat,minage]
#   sh outils/tester-dans-instance.sh "Test mods Fabric 26.3" photos      les photos du README (vrai monde, 1600×900)
#
# Captures dans run/instance/captures, journal du jeu dans run/instance/jeu.log.
set -e
cd "$(dirname "$0")/.."
INSTANCE="$HOME/curseforge/minecraft/Instances/${1:-Test mods Fabric 26.3}"
SCENES="$2"
SORTIE="run/instance"
export JAVA_HOME="C:/Program Files/Eclipse Adoptium/jdk-25.0.3.9-hotspot"

./gradlew build gametestClasses --console=plain -q
rm -rf "$SORTIE"
mkdir -p "$SORTIE/captures" "$SORTIE/mondes"
"$JAVA_HOME/bin/jar" cf "$SORTIE/abstickmen-gametest.jar" -C build/classes/java/gametest . -C build/resources/gametest .
API=$(find "$HOME/scoop/apps/gradle/current/.gradle/caches/modules-2/files-2.1/net.fabricmc.fabric-api/fabric-client-gametest-api-v1" \
    -name "*.jar" ! -name "*sources*" | sort | tail -1)
cp "$API" "$SORTIE/"

# Ce qu'il y a avant, pour tout remettre comme c'était
cp "$INSTANCE/options.txt" "$SORTIE/options.txt.sauvegarde"
ls "$INSTANCE/saves" > "$SORTIE/saves-avant.txt"
ls "$INSTANCE/screenshots" > "$SORTIE/captures-avant.txt" 2>/dev/null || true

W=$(cygpath -w "$PWD/$SORTIE")
python -I outils/lancer_instance.py "$INSTANCE" "$SORTIE/jeu.log" "$PWD/$SORTIE/natives" \
    "-Dfabric.addMods=$W\\$(basename "$API");$W\\abstickmen-gametest.jar" "-Dfabric.client.gametest" \
    ${SCENES:+"-Dabstickmen.test.scenes=$SCENES"} $([ "$SCENES" = photos ] && echo "-Dabstickmen.photos=true") || true

cp "$SORTIE/options.txt.sauvegarde" "$INSTANCE/options.txt"
if [ -d "$INSTANCE/screenshots" ]; then
    ls "$INSTANCE/screenshots" | while read -r f; do
        grep -qxF "$f" "$SORTIE/captures-avant.txt" || mv "$INSTANCE/screenshots/$f" "$SORTIE/captures/"
    done
    rmdir "$INSTANCE/screenshots" 2>/dev/null || true
fi
ls "$INSTANCE/saves" | while read -r m; do
    grep -qxF "$m" "$SORTIE/saves-avant.txt" || mv "$INSTANCE/saves/$m" "$SORTIE/mondes/"
done
grep "\[test\]" "$SORTIE/jeu.log" || echo "Le test n'a rien écrit : voir $SORTIE/jeu.log"
