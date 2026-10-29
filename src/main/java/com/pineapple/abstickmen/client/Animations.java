package com.pineapple.abstickmen.client;

import com.pineapple.abstickmen.entity.Geste;
import com.pineapple.abstickmen.entity.Stickman;

/**
 * Toutes les animations, calculées à l'image près à partir du geste en cours et du temps écoulé.
 * D'abord la marche (ou la course, la nage, la position assise), puis le geste par-dessus : un
 * geste « corps entier » remplace tout, les autres ne prennent que les bras et le torse.
 */
public final class Animations {
    private static final float PI = (float) Math.PI;
    private static final float DEUX_PI = 2 * PI;

    private Animations() {
    }

    public static void poser(Pose p, Pose geste, StickmanRenderState s) {
        p.repos();
        float age = s.ageInTicks;
        if (s.assis) {
            assis(p, age);
        } else if (s.nage) {
            nage(p, age);
        } else {
            marche(p, s.walkAnimationPos, s.walkAnimationSpeed, age + s.decalage);
            if (!s.auSol) {
                enLAir(p, s.vitesseVerticale);
            }
        }

        if (s.personnage.estOrange() && !s.assis) {
            // Le bras qui tient le bâton reste écarté et balance peu
            p.epaule[1] *= 0.35f;
            p.ecartBras[1] = Math.max(p.ecartBras[1], 0.45f);
            p.coude[1] = Math.max(p.coude[1], 0.35f);
        }

        Geste g = s.geste;
        int d = g.duree > 0 ? g.duree : s.dureeGeste;
        float t = s.tGeste;
        if (g != Geste.AUCUN && g != Geste.ASSIS && (d <= 0 || t < d)) {
            geste.copier(p);
            jouer(geste, g, t, Math.max(1, d), s);
            normaliser(geste);
            boolean vif = g.famille == Geste.Famille.COMBAT;
            float poids = Math.min(lisse(t / (vif ? 1.5f : 3f)), lisse((d - t) / 3f));
            if (g.corpsEntier) {
                p.melanger(geste, poids);
            } else {
                p.hautDuCorps(geste, poids);
            }
        }
        if (s.deathTime > 0) {
            mort(p, s.deathTime);
        }
    }

    // ------------------------------------------------------------------ déplacements

    private static void marche(Pose p, float pos, float vitesse, float age) {
        float k = clamp(vitesse * 2.2f);
        float course = clamp((vitesse - 0.55f) * 2.5f);
        float phi = pos * 0.6662f;
        float jambes = (0.62f + 0.3f * course) * k;
        float bras = (0.55f + 0.45f * course) * k;
        for (int i = 0; i < 2; i++) {
            float ph = phi + (i == 0 ? 0 : PI);
            float sin = (float) Math.sin(ph), cos = (float) Math.cos(ph);
            p.hanche[i] = jambes * sin;
            p.genou[i] = 0.04f + k * (0.2f + 0.9f * course) * Math.max(0, cos) + course * 0.7f * Math.max(0, -sin);
            p.epaule[i] = 0.05f - bras * sin;
            p.coude[i] = 0.15f + 0.15f * k + 1.15f * course;
            p.ecartBras[i] = 0.14f - 0.04f * k;
        }
        p.torse = 0.05f * k + 0.24f * course;
        // Respiration quand il ne bouge pas
        float souffle = (float) Math.sin(age * 0.09f) * (1 - k);
        p.torse += 0.02f * souffle;
        p.epaule[0] += 0.03f * souffle;
        p.epaule[1] += 0.03f * souffle;
        p.ecartBras[0] += 0.02f * (1 - k);
        p.ecartBras[1] += 0.02f * (1 - k);
    }

    private static void enLAir(Pose p, float vy) {
        float w = clamp(Math.abs(vy) * 4f);
        float monte = vy > 0 ? 1 : 0;
        for (int i = 0; i < 2; i++) {
            p.hanche[i] += ((i == 0 ? 0.7f : 0.25f) * monte - p.hanche[i] * 0.5f) * w;
            p.genou[i] += ((i == 0 ? 1.1f : 0.5f) - p.genou[i]) * w * 0.8f;
            p.epaule[i] += (0.9f - p.epaule[i]) * w;
            p.ecartBras[i] += (0.75f - p.ecartBras[i]) * w;
            p.coude[i] += (0.4f - p.coude[i]) * w;
        }
    }

    private static void nage(Pose p, float age) {
        for (int i = 0; i < 2; i++) {
            float ph = age * 0.22f + (i == 0 ? 0 : PI);
            p.epaule[i] = 0.35f + 0.25f * (float) Math.sin(ph);
            p.ecartBras[i] = 0.9f + 0.35f * (float) Math.cos(ph);
            p.coude[i] = 0.35f;
            p.hanche[i] = 0.35f * (float) Math.sin(age * 0.3f + (i == 0 ? 0 : PI));
            p.genou[i] = 0.5f + 0.3f * (float) Math.cos(age * 0.3f + (i == 0 ? 0 : PI));
        }
        p.torse = 0.12f;
    }

    private static void assis(Pose p, float age) {
        for (int i = 0; i < 2; i++) {
            p.hanche[i] = 1.35f;
            p.ecartJambe[i] = 0.78f;
            p.genou[i] = 2.55f;
            p.epaule[i] = 0.6f;
            p.ecartBras[i] = 0.32f;
            p.coude[i] = 0.45f;
        }
        p.torse = 0.06f + 0.02f * (float) Math.sin(age * 0.08f);
        p.teteCote = 0.08f * (float) Math.sin(age * 0.03f);
    }

    private static void mort(Pose p, float temps) {
        float u = lisse(temps / 10f);
        p.tourAvant = -PI / 2 * u;
        for (int i = 0; i < 2; i++) {
            p.epaule[i] += (0.3f - p.epaule[i]) * u;
            p.ecartBras[i] += (0.9f - p.ecartBras[i]) * u;
            p.coude[i] += (0.2f - p.coude[i]) * u;
            p.hanche[i] += (0.1f - p.hanche[i]) * u;
            p.ecartJambe[i] += (0.3f - p.ecartJambe[i]) * u;
            p.genou[i] += (0.15f - p.genou[i]) * u;
        }
        p.air = 0;
    }

    // ------------------------------------------------------------------ gestes

    private static void jouer(Pose q, Geste g, float t, int d, StickmanRenderState s) {
        float u = clamp(t / d);
        switch (g) {
            case SALUT -> {
                q.epaule[0] = 2.75f;
                q.ecartBras[0] = 0.45f + 0.32f * (float) Math.sin(t * 0.7f);
                q.coude[0] = 0.35f;
                q.torsionCoude[0] = 0;
                q.teteCote = 0.12f;
            }
            case SALTO_ARRIERE -> salto(q, t, -1);
            case SALTO_AVANT -> salto(q, t, 1);
            case ROUE -> {
                q.tourCote = DEUX_PI * lisse(u);
                for (int i = 0; i < 2; i++) {
                    q.epaule[i] = 0.2f;
                    q.ecartBras[i] = 2.75f;
                    q.coude[i] = 0.05f;
                    q.ecartJambe[i] = 0.7f;
                    q.hanche[i] = 0;
                    q.genou[i] = 0.05f;
                }
                q.air = 0.12f * (float) Math.sin(PI * u);
            }
            case DANSE_DISCO -> disco(q, t);
            case DANSE_ROBOT -> robot(q, t);
            case SAUTS_ECART -> {
                float v = (t % 12) / 12f;
                float o = (float) Math.sin(PI * v);
                for (int i = 0; i < 2; i++) {
                    q.epaule[i] = 0.05f;
                    q.ecartBras[i] = 0.15f + 2.65f * o;
                    q.coude[i] = 0.1f;
                    q.ecartJambe[i] = 0.06f + 0.42f * o;
                    q.hanche[i] = 0;
                    q.genou[i] = 0.08f + 0.35f * (1 - o);
                }
                q.air = 0.3f * o;
            }
            case POIRIER -> poirier(q, t, d);
            case POMPES -> pompes(q, t, d);
            case KATA -> kata(q, t);
            case TOUPIE -> {
                q.tourVertical = DEUX_PI * lisse(u);
                for (int i = 0; i < 2; i++) {
                    q.epaule[i] = 0.1f;
                    q.ecartBras[i] = 1.5f;
                    q.coude[i] = 0.1f;
                }
                q.hanche[0] = 0.5f;
                q.genou[0] = 1.4f;
                q.ecartJambe[0] = 0.3f;
                q.hanche[1] = 0;
                q.genou[1] = 0.05f;
            }
            case ETIREMENT -> {
                for (int i = 0; i < 2; i++) {
                    q.epaule[i] = 2.95f;
                    q.ecartBras[i] = 0.18f;
                    q.coude[i] = 0.08f;
                }
                q.torseCote = 0.32f * (float) Math.sin(DEUX_PI * u);
                q.tete = -0.15f;
            }
            case GRATTE_TETE -> {
                q.epaule[0] = 2.1f;
                q.ecartBras[0] = 0.8f;
                q.coude[0] = 2.25f + 0.12f * (float) Math.sin(t * 1.6f);
                q.torsionCoude[0] = 1.15f;
                mainSurHanche(q, 1);
                q.teteCote = -0.18f;
            }
            case BRAS_CROISES -> {
                for (int i = 0; i < 2; i++) {
                    q.epaule[i] = 0.62f;
                    q.ecartBras[i] = 0.12f;
                    q.coude[i] = 1.8f;
                    q.torsionCoude[i] = 1.3f;
                }
                q.hanche[0] = 0.15f;
                q.genou[0] = 0.22f + 0.2f * Math.max(0, (float) Math.sin(t * 0.7f));
                q.teteCote = 0.1f * (float) Math.sin(t * 0.1f);
            }
            case MAINS_HANCHES -> {
                mainSurHanche(q, 0);
                mainSurHanche(q, 1);
                q.ecartJambe[0] = q.ecartJambe[1] = 0.22f;
                q.torse = -0.06f;
                q.tete = -0.12f;
            }
            case VICTOIRE -> {
                float saut = cloche(t, 4, 18);
                q.air = 0.45f * saut;
                q.epaule[0] = 3.0f;
                q.ecartBras[0] = 0.2f;
                q.coude[0] = 0.15f + 0.5f * Math.max(0, (float) Math.sin(t * 0.6f));
                q.epaule[1] = 0.9f;
                q.coude[1] = 2.0f;
                q.ecartBras[1] = 0.1f;
                q.genou[0] = q.genou[1] = 0.1f + 0.7f * saut;
                q.hanche[0] = q.hanche[1] = 0.4f * saut;
            }
            case DIRECT -> {
                garde(q);
                float coup = cloche(u, 0.1f, 0.8f);
                q.epaule[0] = 1.05f + 0.5f * coup;
                q.coude[0] = 1.9f * (1 - coup);
                q.torse = 0.15f * coup;
                q.torsion = 0.25f * coup;
            }
            case CROCHET -> {
                garde(q);
                float coup = cloche(u, 0.1f, 0.85f);
                q.epaule[1] = 1.35f;
                q.ecartBras[1] = 1.2f - 1.4f * coup;
                q.coude[1] = 1.5f;
                q.torsionCoude[1] = 1.1f;
                q.torsion = -0.5f * coup;
            }
            case UPPERCUT -> {
                garde(q);
                float coup = lisse(clamp((u - 0.15f) / 0.45f));
                float fin = 1 - lisse(clamp((u - 0.75f) / 0.25f));
                q.epaule[0] = 0.3f + 2.2f * coup * fin;
                q.coude[0] = 1.7f - 0.8f * coup;
                q.torse = 0.25f - 0.4f * coup * fin;
                q.air = 0.12f * cloche(u, 0.3f, 0.9f);
            }
            case COUP_DE_PIED -> {
                garde(q);
                float jambe = cloche(u, 0.05f, 0.95f);
                float detente = cloche(u, 0.3f, 0.8f);
                q.hanche[0] = 1.55f * jambe;
                q.genou[0] = jambe * 1.8f * (1 - detente);
                q.genou[1] = 0.15f;
                q.hanche[1] = -0.1f;
                q.torse = -0.35f * jambe;
            }
            case COUP_DE_PIED_RETOURNE -> {
                q.tourVertical = DEUX_PI * lisse(u);
                float jambe = cloche(u, 0.2f, 0.9f);
                q.ecartJambe[0] = 0.08f + 1.3f * jambe;
                q.hanche[0] = 0.2f * jambe;
                q.genou[0] = 0.2f;
                q.genou[1] = 0.2f;
                q.torseCote = 0.4f * jambe;
                for (int i = 0; i < 2; i++) {
                    q.ecartBras[i] = 1.0f;
                    q.epaule[i] = 0.4f;
                    q.coude[i] = 0.6f;
                }
            }
            case COUP_DE_PIED_SAUTE -> {
                float vol = cloche(u, 0f, 0.95f);
                q.air = 0.65f * vol;
                q.hanche[0] = 1.45f * vol;
                q.genou[0] = 0.1f;
                q.hanche[1] = 1.3f * vol;
                q.genou[1] = 2.2f * vol;
                q.torse = -0.45f * vol;
                for (int i = 0; i < 2; i++) {
                    q.epaule[i] = -0.6f * vol;
                    q.ecartBras[i] = 0.6f;
                    q.coude[i] = 0.3f;
                }
            }
            case COUP_D_EPEE -> {
                float elan = lisse(clamp(u / 0.35f));
                float frappe = lisse(clamp((u - 0.35f) / 0.25f));
                q.epaule[0] = 0.5f + 2.4f * elan - 2.2f * frappe;
                q.coude[0] = 0.8f * elan * (1 - frappe) + 0.1f;
                q.ecartBras[0] = 0.15f;
                q.torse = 0.25f * frappe;
                q.torsion = 0.4f * frappe;
                q.epaule[1] = 0.4f;
                q.ecartBras[1] = 0.6f;
            }
            case COUP_DE_BATON -> {
                float elan = lisse(clamp(u / 0.35f));
                float frappe = lisse(clamp((u - 0.35f) / 0.25f));
                q.batonLeve = 1;
                q.epaule[1] = 0.6f + 2.1f * elan - 2.0f * frappe;
                q.coude[1] = 0.3f - 0.2f * frappe;
                q.ecartBras[1] = 0.15f;
                q.epaule[0] = q.epaule[1] - 0.3f;
                q.coude[0] = 0.8f;
                q.ecartBras[0] = 0.05f;
                q.torsion = -0.4f + 0.8f * frappe;
                q.torse = 0.2f * frappe;
            }
            case TIR_A_L_ARC -> tirALArc(q, t, d);
            case PIOCHE -> {
                float elan = lisse(clamp(u / 0.5f));
                float frappe = lisse(clamp((u - 0.5f) / 0.2f));
                q.epaule[0] = 0.8f + 1.9f * elan - 1.95f * frappe;
                q.coude[0] = 0.4f + 0.5f * elan - 0.7f * frappe;
                q.ecartBras[0] = 0.12f;
                q.torse = 0.3f * frappe;
                q.epaule[1] = 0.5f;
                q.coude[1] = 0.8f;
            }
            case LANCER -> {
                float l = lisse(clamp((u - 0.15f) / 0.35f));
                q.epaule[0] = -0.6f + 2.2f * l;
                q.coude[0] = 0.2f;
                q.torse = 0.1f * l;
            }
            case PERLE -> {
                float l = lisse(clamp(u / 0.4f));
                q.epaule[0] = 2.8f - 1.6f * l;
                q.coude[0] = 1.2f * (1 - l);
            }
            case COMMANDE -> commande(q, t, d, s);
            case ECRIRE -> {
                float w = Math.min(lisse(t / 5f), lisse((d - 16 - t) / 5f));
                q.epaule[0] = 0.1f + (1.8f + 0.14f * (float) Math.sin(t * 1.25f)) * w;
                q.ecartBras[0] = 0.15f + 0.14f * (float) Math.cos(t * 1.25f) * w;
                q.coude[0] = 0.25f;
                q.tete = -0.15f * w;
                q.teteCote = 0.08f;
                if (t > d - 16) {
                    // Il admire son œuvre, main sur la hanche
                    mainSurHanche(q, 0);
                    q.tete = -0.1f;
                }
            }
            default -> {
            }
        }
    }

    private static void salto(Pose q, float t, float sens) {
        if (t < 5) {
            float v = lisse(t / 5);
            for (int i = 0; i < 2; i++) {
                q.genou[i] = 1.3f * v;
                q.hanche[i] = 1.0f * v;
                q.epaule[i] = -0.7f * v;
                q.coude[i] = 0.2f;
            }
            q.torse = 0.5f * v;
        } else if (t < 21) {
            float v = (t - 5) / 16f;
            q.tourAvant = sens * DEUX_PI * lisse(v);
            q.air = 1.4f * 4 * v * (1 - v);
            float boule = cloche(v, 0.08f, 0.92f);
            for (int i = 0; i < 2; i++) {
                q.hanche[i] = 0.3f + 1.6f * boule;
                q.genou[i] = 0.2f + 2.0f * boule;
                q.epaule[i] = 0.9f + 0.4f * boule;
                q.coude[i] = 0.8f * boule;
                q.ecartBras[i] = 0.2f;
            }
            q.torse = 0.15f * boule;
        } else {
            float v = 1 - lisse((t - 21) / 5);
            for (int i = 0; i < 2; i++) {
                q.genou[i] = 1.0f * v;
                q.hanche[i] = 0.8f * v;
                q.epaule[i] = 1.2f * v;
                q.ecartBras[i] = 0.3f;
            }
            q.torse = 0.35f * v;
        }
    }

    private static void disco(Pose q, float t) {
        float temps = t / 10f;
        int n = (int) temps;
        float coup = lisse(clamp((temps - n) * 3));
        boolean haut = n % 2 == 0;
        float eHaut = 2.6f, cHaut = 0.7f, eBas = 0.35f, cBas = -0.3f;
        float de = haut ? eBas : eHaut, ve = haut ? eHaut : eBas;
        float dc = haut ? cBas : cHaut, vc = haut ? cHaut : cBas;
        q.epaule[0] = de + (ve - de) * coup;
        q.ecartBras[0] = dc + (vc - dc) * coup;
        q.coude[0] = 0.05f;
        mainSurHanche(q, 1);
        float rebond = Math.abs((float) Math.sin(PI * temps));
        for (int i = 0; i < 2; i++) {
            q.genou[i] = 0.25f + 0.25f * rebond;
            q.hanche[i] = 0.12f + 0.12f * rebond;
            q.ecartJambe[i] = 0.2f;
        }
        q.torseCote = 0.12f * (haut ? 1 : -1) * coup;
        q.tourVertical = 0.35f * (float) Math.sin(PI * temps / 2);
    }

    /** Danse du robot : des poses franches qui s'enchaînent d'un coup sec. */
    private static final float[][] ROBOT = {
            // épaule D, écart D, coude D, épaule G, écart G, coude G, torsion, tête côté, hanche D, genou D
            {1.57f, 0.1f, 1.57f, 0.2f, 0.5f, 1.57f, 0.4f, 0.25f, 0.3f, 0.3f},
            {0.2f, 0.5f, 1.57f, 1.57f, 0.1f, 1.57f, -0.4f, -0.25f, 0f, 0.05f},
            {1.57f, 1.4f, 1.57f, 1.57f, 1.4f, 1.57f, 0f, 0.3f, 0.3f, 0.3f},
            {0.1f, 0.2f, 1.9f, 0.1f, 0.2f, 1.9f, 0.6f, -0.3f, 0f, 0.05f},
    };

    private static void robot(Pose q, float t) {
        int n = (int) (t / 7);
        float coup = lisse(clamp((t % 7) / 2f));
        float[] a = ROBOT[Math.floorMod(n - 1, ROBOT.length)], b = ROBOT[n % ROBOT.length];
        float[] v = new float[a.length];
        for (int i = 0; i < v.length; i++) {
            v[i] = a[i] + (b[i] - a[i]) * coup;
        }
        q.epaule[0] = v[0];
        q.ecartBras[0] = v[1];
        q.coude[0] = v[2];
        q.epaule[1] = v[3];
        q.ecartBras[1] = v[4];
        q.coude[1] = v[5];
        q.torsionCoude[0] = q.torsionCoude[1] = 0.4f;
        q.torsion = v[6];
        q.teteCote = v[7];
        q.hanche[0] = v[8];
        q.genou[0] = v[9];
        q.hanche[1] = v[8] > 0.1f ? 0 : 0.3f;
        q.genou[1] = v[8] > 0.1f ? 0.05f : 0.3f;
    }

    private static void poirier(Pose q, float t, int d) {
        float bascule = lisse(clamp(t / 12f));
        float retour = lisse(clamp((t - (d - 12)) / 12f));
        q.tourAvant = PI * bascule + PI * retour;
        float pose = bascule * (1 - retour);
        for (int i = 0; i < 2; i++) {
            q.epaule[i] = 0.1f + 3.0f * pose;
            q.ecartBras[i] = 0.25f;
            q.coude[i] = 0.05f;
            q.ecartJambe[i] = 0.15f + (0.15f + 0.12f * (float) Math.sin(t * 0.2f)) * pose;
            q.hanche[i] = 0;
            q.genou[i] = 0.05f + 0.35f * pose * Math.max(0, (float) Math.sin(t * 0.3f + i * PI));
        }
        q.tourCote = 0.06f * (float) Math.sin(t * 0.25f) * pose;
    }

    private static void pompes(Pose q, float t, int d) {
        float w = Math.min(lisse(t / 10f), lisse((d - t) / 10f));
        float bas = t > 10 && t < d - 10 ? 0.5f - 0.5f * (float) Math.cos(DEUX_PI * (t - 10) / 15f) : 0;
        q.tourAvant = (PI / 2 - 0.12f) * w;
        for (int i = 0; i < 2; i++) {
            q.epaule[i] = (PI / 2 - 0.75f * bas) * w;
            q.coude[i] = 1.45f * bas * w;
            q.ecartBras[i] = 0.14f + 0.25f * w;
            q.hanche[i] = 0;
            q.genou[i] = 0.02f;
            q.ecartJambe[i] = 0.08f;
        }
        q.torse = 0;
        q.tete = -0.3f * w;
    }

    private static void kata(Pose q, float t) {
        garde(q);
        q.hanche[0] = 0.25f;
        q.hanche[1] = -0.15f;
        q.genou[0] = q.genou[1] = 0.25f;
        if (t < 10) {
            float c = cloche(t, 1, 9);
            q.epaule[0] = 1.05f + 0.5f * c;
            q.coude[0] = 1.9f * (1 - c);
        } else if (t < 20) {
            float c = cloche(t, 11, 19);
            q.epaule[1] = 1.05f + 0.5f * c;
            q.coude[1] = 1.9f * (1 - c);
        } else if (t < 32) {
            float jambe = cloche(t, 20, 32), detente = cloche(t, 23, 29);
            q.hanche[0] = 1.55f * jambe;
            q.genou[0] = jambe * 1.8f * (1 - detente);
            q.torse = -0.3f * jambe;
        } else if (t < 44) {
            float jambe = cloche(t, 32, 44), detente = cloche(t, 35, 41);
            q.ecartJambe[1] = 0.08f + 1.25f * jambe;
            q.genou[1] = 1.5f * jambe * (1 - detente);
            q.hanche[1] = 0.2f * jambe;
            q.torseCote = 0.35f * jambe;
        } else {
            float salut = cloche(t, 44, 60);
            q.torse = 0.5f * salut;
            for (int i = 0; i < 2; i++) {
                q.epaule[i] = 0.9f;
                q.ecartBras[i] = -0.15f;
                q.coude[i] = 1.6f;
                q.torsionCoude[i] = 1.2f;
                q.hanche[i] = 0;
                q.genou[i] = 0.05f;
            }
        }
    }

    /** L'orange lève son bâton (main gauche) et tape la commande de l'autre main, dans le vide. */
    private static void commande(Pose q, float t, int d, StickmanRenderState s) {
        int lever = s.commandeUrgente ? Stickman.LEVER_URGENT : Stickman.LEVER;
        float frappe = s.commande.length() / (s.commandeUrgente ? Stickman.FRAPPE_URGENTE : Stickman.FRAPPE);
        float w = Math.min(lisse(t / lever), lisse((d - t) / 6f));
        float tape = t > lever && t < lever + frappe ? 1 : 0;
        q.batonLeve = w;
        q.epaule[1] = 0.1f + 2.85f * w + 0.15f * cloche(t, lever + frappe - 1, lever + frappe + 5);
        q.ecartBras[1] = 0.12f + 0.15f * w;
        q.coude[1] = 0.15f;
        q.epaule[0] = 0.1f + 1.15f * w;
        q.coude[0] = 0.9f * w + 0.18f * (float) Math.sin(t * 2.2f) * tape;
        q.ecartBras[0] = 0.1f + 0.1f * (float) Math.sin(t * 1.7f) * tape;
        q.tete = -0.25f * w;
    }

    /**
     * Tir à l'arc : l'arc dans la main gauche, bras tendu vers la cible ; la main droite tire la corde
     * jusqu'à la joue, coude en arrière, et part en arrière quand la flèche est lâchée. Les deux bras
     * restent à l'horizontale : leur écart les fait alors tourner à plat, de l'avant vers le côté puis
     * vers l'arrière. L'orange plante son bâton à côté de lui le temps du tir.
     */
    private static void tirALArc(Pose q, float t, int d) {
        float tension = lisse((t - 3) / 15f);
        float lache = lisse((t - Stickman.LACHER) / 2f);
        q.batonPlante = Math.min(lisse(t / 4f), lisse((d - t) / 4f));
        q.batonLeve = 0;
        q.epaule[1] = PI / 2;
        q.ecartBras[1] = -0.25f;
        q.coude[1] = 0.05f;
        q.torsionCoude[1] = 0;
        q.epaule[0] = PI / 2 - 0.15f * tension;
        q.ecartBras[0] = 0.1f + 1.9f * tension + 0.35f * lache;
        q.coude[0] = 2.4f * tension - 0.5f * lache;
        q.torsionCoude[0] = PI / 2;
        q.torse = -0.06f * tension;
        q.tete = 0;
    }

    private static void garde(Pose q) {
        for (int i = 0; i < 2; i++) {
            q.epaule[i] = 1.0f;
            q.coude[i] = 1.95f;
            q.ecartBras[i] = 0.08f;
            q.torsionCoude[i] = 0.2f;
        }
    }

    private static void mainSurHanche(Pose q, int i) {
        q.epaule[i] = -0.12f;
        q.ecartBras[i] = 0.82f;
        q.coude[i] = 1.75f;
        q.torsionCoude[i] = 1.4f;
    }

    // ------------------------------------------------------------------ outils

    /** Ramène les rotations du corps entier entre -π et π (un salto fini vaut zéro tour). */
    private static void normaliser(Pose q) {
        q.tourAvant = angle(q.tourAvant);
        q.tourCote = angle(q.tourCote);
        q.tourVertical = angle(q.tourVertical);
    }

    private static float angle(float a) {
        a = (float) Math.IEEEremainder(a, DEUX_PI);
        return a;
    }

    static float clamp(float x) {
        return x < 0 ? 0 : x > 1 ? 1 : x;
    }

    static float lisse(float x) {
        x = clamp(x);
        return x * x * (3 - 2 * x);
    }

    /** Une bosse douce : 0 hors de [a, b], 1 au milieu. */
    static float cloche(float t, float a, float b) {
        if (t <= a || t >= b) {
            return 0;
        }
        return (float) Math.sin(PI * (t - a) / (b - a));
    }
}
