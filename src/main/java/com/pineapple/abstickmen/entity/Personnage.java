package com.pineapple.abstickmen.entity;

/**
 * Les cinq bonshommes-bâtons. Seul l'orange a la tête creuse (un simple anneau), le bâton surmonté
 * d'un bloc de commande, et sait écrire sur l'écran du joueur. Chacun a sa façon de se battre.
 */
public enum Personnage {
    ORANGE("orange", 0xFF8A1A, true, Style.BATON),
    ROUGE("red", 0xE53935, false, Style.POINGS),
    BLEU("blue", 0x1E88E5, false, Style.PIEDS),
    VERT("green", 0x43A047, false, Style.EPEE),
    JAUNE("yellow", 0xFDD835, false, Style.HACHE);

    /** Comment il se bat : ce qu'il tient en main et quels coups il donne. */
    public enum Style { BATON, POINGS, PIEDS, EPEE, HACHE }

    /** Nom dans le registre (abstickmen:orange, abstickmen:red…). */
    public final String id;
    public final int couleur;
    public final boolean teteCreuse;
    public final Style style;

    Personnage(String id, int couleur, boolean teteCreuse, Style style) {
        this.id = id;
        this.couleur = couleur;
        this.teteCreuse = teteCreuse;
        this.style = style;
    }

    public boolean estOrange() {
        return this == ORANGE;
    }

    /** Sa place autour du joueur quand il le suit : angle par rapport au dos du joueur, et distance. */
    public float angleFormation() {
        return switch (this) {
            case ORANGE -> 0f;
            case ROUGE -> -38f;
            case BLEU -> 38f;
            case VERT -> -78f;
            case JAUNE -> 78f;
        };
    }

    public float distanceFormation() {
        return switch (this) {
            case ORANGE -> 2.2f;
            case ROUGE, BLEU -> 2.6f;
            case VERT, JAUNE -> 3.0f;
        };
    }
}
