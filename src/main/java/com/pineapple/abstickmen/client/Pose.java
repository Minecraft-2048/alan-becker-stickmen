package com.pineapple.abstickmen.client;

/**
 * Une pose du bonhomme-bâton, en angles (radians). Indice 0 = côté droit, 1 = côté gauche.
 *
 * Bras : {@code epaule} le lève vers l'avant (π/2 = horizontal devant, π = tout droit en l'air),
 * {@code ecartBras} l'écarte sur le côté, {@code coude} plie l'avant-bras vers l'avant du bras et
 * {@code torsionCoude} fait tourner ce pli vers l'intérieur (pour croiser les bras, mains sur les hanches).
 * Jambes : {@code hanche} lance la cuisse vers l'avant, {@code ecartJambe} l'écarte, {@code genou} plie
 * le tibia vers l'arrière. Le corps entier peut tourner autour de sa taille (saltos, roues, toupies).
 */
public final class Pose {
    public final float[] epaule = new float[2];
    public final float[] ecartBras = new float[2];
    public final float[] coude = new float[2];
    public final float[] torsionCoude = new float[2];
    public final float[] hanche = new float[2];
    public final float[] ecartJambe = new float[2];
    public final float[] genou = new float[2];
    /** Penché vers l'avant / sur le côté droit / épaules tournées vers la gauche. */
    public float torse, torseCote, torsion;
    public float tete, teteCote;
    /** Rotation du corps entier : salto (vers l'avant), roue (sur le côté), toupie. */
    public float tourAvant, tourCote, tourVertical;
    /** Hauteur au-dessus du sol (sauts), décalage vers l'avant. */
    public float air, avance;
    /** Le bâton de l'orange : 0 = tenu comme un bâton de marche, 1 = levé dans l'axe du bras. */
    public float batonLeve;

    public void repos() {
        for (int i = 0; i < 2; i++) {
            epaule[i] = 0.05f;
            ecartBras[i] = 0.14f;
            coude[i] = 0.15f;
            torsionCoude[i] = 0;
            hanche[i] = 0;
            ecartJambe[i] = 0.08f;
            genou[i] = 0.04f;
        }
        torse = torseCote = torsion = tete = teteCote = 0;
        tourAvant = tourCote = tourVertical = 0;
        air = avance = 0;
        batonLeve = 0;
    }

    /** Mélange vers {@code autre} : t = 0 garde cette pose, t = 1 donne {@code autre}. */
    public void melanger(Pose autre, float t) {
        for (int i = 0; i < 2; i++) {
            epaule[i] += (autre.epaule[i] - epaule[i]) * t;
            ecartBras[i] += (autre.ecartBras[i] - ecartBras[i]) * t;
            coude[i] += (autre.coude[i] - coude[i]) * t;
            torsionCoude[i] += (autre.torsionCoude[i] - torsionCoude[i]) * t;
            hanche[i] += (autre.hanche[i] - hanche[i]) * t;
            ecartJambe[i] += (autre.ecartJambe[i] - ecartJambe[i]) * t;
            genou[i] += (autre.genou[i] - genou[i]) * t;
        }
        torse += (autre.torse - torse) * t;
        torseCote += (autre.torseCote - torseCote) * t;
        torsion += (autre.torsion - torsion) * t;
        tete += (autre.tete - tete) * t;
        teteCote += (autre.teteCote - teteCote) * t;
        tourAvant += (autre.tourAvant - tourAvant) * t;
        tourCote += (autre.tourCote - tourCote) * t;
        tourVertical += (autre.tourVertical - tourVertical) * t;
        air += (autre.air - air) * t;
        avance += (autre.avance - avance) * t;
        batonLeve += (autre.batonLeve - batonLeve) * t;
    }

    /** Recopie seulement le haut du corps (bras, torse, tête) de {@code autre}. */
    public void hautDuCorps(Pose autre, float t) {
        for (int i = 0; i < 2; i++) {
            epaule[i] += (autre.epaule[i] - epaule[i]) * t;
            ecartBras[i] += (autre.ecartBras[i] - ecartBras[i]) * t;
            coude[i] += (autre.coude[i] - coude[i]) * t;
            torsionCoude[i] += (autre.torsionCoude[i] - torsionCoude[i]) * t;
        }
        torse += (autre.torse - torse) * t;
        torseCote += (autre.torseCote - torseCote) * t;
        torsion += (autre.torsion - torsion) * t;
        tete += (autre.tete - tete) * t;
        teteCote += (autre.teteCote - teteCote) * t;
        batonLeve += (autre.batonLeve - batonLeve) * t;
    }

    public void copier(Pose autre) {
        repos();
        melanger(autre, 1f);
    }
}
