package com.pineapple.abstickmen.entity;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.pineapple.abstickmen.AlanBeckerStickmen;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Réglages du mod, dans config/abstickmen.json (créé au premier lancement). Tout est activé par
 * défaut ; l'orange ne tape de commandes que si le joueur a lui-même le droit d'en taper.
 */
public final class Reglages {
    /** L'orange peut taper des commandes avec son bâton (si les commandes sont activées dans le monde). */
    public boolean commandes = true;
    /** L'orange peut écrire des messages sur l'écran. */
    public boolean ecrire = true;
    /** Il écrit aussi de lui-même de temps en temps (sinon seulement quand on le lui demande). */
    public boolean messagesSpontanes = true;
    /** Les stickmen minent les minerais visibles autour du joueur et lui rapportent ce qu'ils trouvent. */
    public boolean miner = true;
    /** Ils font des acrobaties et des danses quand tout est calme. */
    public boolean acrobaties = true;

    private static Reglages actuels = new Reglages();

    public static Reglages get() {
        return actuels;
    }

    public static Path dossier() {
        return FabricLoader.getInstance().getConfigDir().resolve(AlanBeckerStickmen.MOD_ID);
    }

    public static void charger() {
        Path fichier = FabricLoader.getInstance().getConfigDir().resolve(AlanBeckerStickmen.MOD_ID + ".json");
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        try {
            if (Files.exists(fichier)) {
                Reglages lus = gson.fromJson(Files.readString(fichier, StandardCharsets.UTF_8), Reglages.class);
                if (lus != null) {
                    actuels = lus;
                }
            }
            // Réécrit le fichier : les nouveaux réglages y apparaissent avec leur valeur par défaut
            Files.writeString(fichier, gson.toJson(actuels), StandardCharsets.UTF_8);
        } catch (IOException | JsonParseException e) {
            AlanBeckerStickmen.LOG.warn("Réglages illisibles ({}), valeurs par défaut", e.getMessage());
        }
    }
}
