#!/bin/sh
# Joue le test en jeu (avec captures d'écran) dans le vrai Minecraft d'une instance CurseForge :
# ses mods, sa configuration, son Fabric. Le test se lance hors du lanceur, hors ligne, et
# n'ajoute rien à l'instance : à la fin, ses réglages sont remis et le monde de test est sorti.
#
#   sh outils/tester-dans-instance.sh ["Test mods Fabric 26.3"] [scènes : gestes,angles,commande,combat,minage]
#   sh outils/tester-dans-instance.sh "Test mods Fabric 26.3" photos      les photos du README (vrai monde, 1600×900)
#   sh outils/tester-dans-instance.sh "Test mods Fabric 26.3" photos:arc  seulement certaines photos (bande, acrobaties,
#                                                                         commande, tete, combat, arc, minage)
#   SHADERS=ComplementaryReimagined_r5.9.3.zip sh outils/tester-dans-instance.sh …
#                                       ce pack de shaders (dans shaderpacks/) pour la durée du test : le réglage d'Iris
#                                       de l'instance est remis tel quel à la fin
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
IRIS="$INSTANCE/config/iris.properties"
if [ -n "$SHADERS" ] && [ -f "$IRIS" ]; then
    cp "$IRIS" "$SORTIE/iris.properties.sauvegarde"
    sed -i -e "s/^enableShaders=.*/enableShaders=true/" -e "s/^shaderPack=.*/shaderPack=$SHADERS/" "$IRIS"
fi
ls "$INSTANCE/saves" > "$SORTIE/saves-avant.txt"
ls "$INSTANCE/screenshots" > "$SORTIE/captures-avant.txt" 2>/dev/null || true

PHOTOS=""
case "$SCENES" in
    photos:*) PHOTOS="-Dabstickmen.photos=true -Dabstickmen.photos.scenes=${SCENES#photos:}" ;;
    photos) PHOTOS="-Dabstickmen.photos=true" ;;
esac

W=$(cygpath -w "$PWD/$SORTIE")
python -I outils/lancer_instance.py "$INSTANCE" "$SORTIE/jeu.log" "$PWD/$SORTIE/natives" \
    "-Dfabric.addMods=$W\\$(basename "$API");$W\\abstickmen-gametest.jar" "-Dfabric.client.gametest" \
    ${SCENES:+"-Dabstickmen.test.scenes=$SCENES"} $PHOTOS || true

cp "$SORTIE/options.txt.sauvegarde" "$INSTANCE/options.txt"
if [ -f "$SORTIE/iris.properties.sauvegarde" ]; then
    cp "$SORTIE/iris.properties.sauvegarde" "$IRIS"
fi
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
