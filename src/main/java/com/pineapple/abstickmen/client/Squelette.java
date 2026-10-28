package com.pineapple.abstickmen.client;

import org.joml.Vector3f;

/**
 * Les articulations du bonhomme-bâton pour une {@link Pose} donnée, dans le repère du monde centré
 * sur ses pieds. Proportions à la Alan Becker : une grosse tête ronde, bras et jambes de même
 * longueur, les deux bras partent du cou et les deux jambes du même point.
 *
 * Repère du corps pendant le calcul : X vers sa gauche, Y vers le haut, Z devant lui.
 */
public final class Squelette {
    public static final float CUISSE = 0.42f, TIBIA = 0.42f, TORSE = 0.46f, BRAS = 0.33f, AVANT_BRAS = 0.33f, COU = 0.035f;
    /** Rayon extérieur de la tête. */
    public static final float RAYON_TETE = 0.21f;
    /** Épaisseur des traits. */
    public static final float EPAISSEUR = 0.075f;
    /** Longueur du bâton de l'orange. */
    public static final float BATON = 1.2f;

    public final Vector3f hanche = new Vector3f(), cou = new Vector3f(), tete = new Vector3f();
    public final Vector3f[] coudes = {new Vector3f(), new Vector3f()};
    public final Vector3f[] mains = {new Vector3f(), new Vector3f()};
    public final Vector3f[] genoux = {new Vector3f(), new Vector3f()};
    public final Vector3f[] pieds = {new Vector3f(), new Vector3f()};
    /** Axe de chaque avant-bras (du coude vers la main), et son « devant » (là où pointe un outil tenu). */
    public final Vector3f[] axeAvantBras = {new Vector3f(), new Vector3f()};
    public final Vector3f[] devantAvantBras = {new Vector3f(), new Vector3f()};
    public final Vector3f batonBas = new Vector3f(), batonHaut = new Vector3f(), axeBaton = new Vector3f();

    private final Vector3f a = new Vector3f(), f = new Vector3f(), s = new Vector3f(), pli = new Vector3f(), v = new Vector3f();

    public void calculer(Pose p, float yawDegres) {
        hanche.set(0, 0, 0);
        // Torse et tête
        torse(p, v.set(0, 1, 0));
        cou.set(v).mul(TORSE);
        v.set(0, 1, 0);
        rotX(v, p.tete);
        rotZ(v, p.teteCote);
        torse(p, v);
        tete.set(v).mul(COU + RAYON_TETE).add(cou);

        for (int i = 0; i < 2; i++) {
            float cote = i == 0 ? -1 : 1;
            // Bras : écarté sur le côté, levé vers l'avant, puis suit le torse
            a.set(0, -1, 0);
            f.set(0, 0, 1);
            s.set(cote, 0, 0);
            for (Vector3f w : new Vector3f[]{a, f, s}) {
                rotZ(w, cote * p.ecartBras[i]);
                rotX(w, -p.epaule[i]);
                torse(p, w);
            }
            coudes[i].set(a).mul(BRAS).add(cou);
            float tc = p.torsionCoude[i];
            pli.set(f).mul((float) Math.cos(tc)).sub(s.x * (float) Math.sin(tc), s.y * (float) Math.sin(tc), s.z * (float) Math.sin(tc));
            float c = p.coude[i];
            float cc = (float) Math.cos(c), sc = (float) Math.sin(c);
            axeAvantBras[i].set(a).mul(cc).add(pli.x * sc, pli.y * sc, pli.z * sc);
            devantAvantBras[i].set(a).mul(-sc).add(pli.x * cc, pli.y * cc, pli.z * cc);
            mains[i].set(axeAvantBras[i]).mul(AVANT_BRAS).add(coudes[i]);

            // Jambe : pas de torse, elle part du bassin
            a.set(0, -1, 0);
            f.set(0, 0, 1);
            rotZ(a, cote * p.ecartJambe[i]);
            rotZ(f, cote * p.ecartJambe[i]);
            rotX(a, -p.hanche[i]);
            rotX(f, -p.hanche[i]);
            genoux[i].set(a).mul(CUISSE);
            float g = p.genou[i];
            v.set(a).mul((float) Math.cos(g)).sub(f.x * (float) Math.sin(g), f.y * (float) Math.sin(g), f.z * (float) Math.sin(g));
            pieds[i].set(v).mul(TIBIA).add(genoux[i]);
        }

        // Le bâton de l'orange, dans sa main gauche : debout comme un bâton de marche, ou levé dans l'axe du bras
        float leve = p.batonLeve;
        v.set(0.14f, 0.97f, 0.1f);
        axeBaton.set(v).lerp(axeAvantBras[1], leve).normalize();
        float dessous = 0.72f + (0.25f - 0.72f) * leve, dessus = BATON - dessous;
        batonBas.set(axeBaton).mul(-dessous).add(mains[1]);
        batonHaut.set(axeBaton).mul(dessus).add(mains[1]);

        // Le corps entier tourne autour de la taille (saltos, roues, toupies)
        Vector3f pivot = new Vector3f(0, 0.12f, 0);
        for (Vector3f pt : points()) {
            pt.sub(pivot);
            tourner(p, pt);
            pt.add(pivot);
        }
        for (Vector3f dir : directions()) {
            tourner(p, dir);
        }

        // Le point le plus bas touche le sol, puis on ajoute le saut
        float bas = tete.y - RAYON_TETE;
        for (Vector3f pt : new Vector3f[]{hanche, cou, coudes[0], coudes[1], mains[0], mains[1], genoux[0], genoux[1], pieds[0], pieds[1]}) {
            bas = Math.min(bas, pt.y - EPAISSEUR / 2);
        }
        float yaw = (float) Math.toRadians(-yawDegres);
        for (Vector3f pt : points()) {
            pt.add(0, p.air - bas, p.avance);
            rotY(pt, yaw);
        }
        for (Vector3f dir : directions()) {
            rotY(dir, yaw);
            dir.normalize();
        }
    }

    private Vector3f[] points() {
        return new Vector3f[]{hanche, cou, tete, coudes[0], coudes[1], mains[0], mains[1], genoux[0], genoux[1], pieds[0], pieds[1],
                batonBas, batonHaut};
    }

    private Vector3f[] directions() {
        return new Vector3f[]{axeAvantBras[0], axeAvantBras[1], devantAvantBras[0], devantAvantBras[1], axeBaton};
    }

    private static void torse(Pose p, Vector3f w) {
        rotY(w, p.torsion);
        rotX(w, p.torse);
        rotZ(w, p.torseCote);
    }

    private static void tourner(Pose p, Vector3f w) {
        rotX(w, p.tourAvant);
        rotZ(w, p.tourCote);
        rotY(w, p.tourVertical);
    }

    /** Rotation autour de X : un angle positif penche le haut vers l'avant. */
    static void rotX(Vector3f w, float angle) {
        float c = (float) Math.cos(angle), s = (float) Math.sin(angle);
        float y = w.y * c - w.z * s, z = w.y * s + w.z * c;
        w.y = y;
        w.z = z;
    }

    static void rotY(Vector3f w, float angle) {
        float c = (float) Math.cos(angle), s = (float) Math.sin(angle);
        float x = w.x * c + w.z * s, z = -w.x * s + w.z * c;
        w.x = x;
        w.z = z;
    }

    /** Rotation autour de Z : un angle positif penche le haut vers sa droite. */
    static void rotZ(Vector3f w, float angle) {
        float c = (float) Math.cos(angle), s = (float) Math.sin(angle);
        float x = w.x * c - w.y * s, y = w.x * s + w.y * c;
        w.x = x;
        w.y = y;
    }
}
