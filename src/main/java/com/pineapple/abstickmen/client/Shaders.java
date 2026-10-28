package com.pineapple.abstickmen.client;

import java.lang.reflect.Method;

import com.pineapple.abstickmen.AlanBeckerStickmen;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Le seul lien avec Iris, s'il est installé : savoir si un pack de shaders est actif. Iris recalcule
 * l'orientation de chaque face pour l'éclairer ; une carte plate tournée vers la caméra se retrouve
 * alors éclairée de dos dès que le soleil est derrière elle. Avec des shaders, les stickmen sont donc
 * dessinés en tubes (voir {@link Dessin}).
 */
final class Shaders {
    private static final Object API;
    private static final Method ACTIFS;

    static {
        Object api = null;
        Method methode = null;
        if (FabricLoader.getInstance().isModLoaded("iris")) {
            try {
                Class<?> classe = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
                api = classe.getMethod("getInstance").invoke(null);
                methode = classe.getMethod("isShaderPackInUse");
            } catch (ReflectiveOperationException | LinkageError e) {
                AlanBeckerStickmen.LOG.warn("API d'Iris introuvable : {}", e.toString());
            }
        }
        API = api;
        ACTIFS = methode;
    }

    private Shaders() {
    }

    static boolean actifs() {
        if (ACTIFS == null) {
            return false;
        }
        try {
            return (Boolean) ACTIFS.invoke(API);
        } catch (ReflectiveOperationException e) {
            return false;
        }
    }
}
