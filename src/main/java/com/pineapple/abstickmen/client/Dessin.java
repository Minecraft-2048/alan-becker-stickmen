package com.pineapple.abstickmen.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Vector3f;

/**
 * Trace un bonhomme-bâton comme sur une feuille : des traits d'épaisseur constante aux bouts ronds,
 * une tête ronde (un simple anneau pour l'orange). Chaque trait et la tête sont des « cartes » toujours
 * tournées vers la caméra : le dessin reste plat et net, sous tous les angles, et la tête creuse de
 * l'orange est toujours vue de face.
 *
 * Tout est calculé au moment de l'appel ; le dessin lui-même a lieu plus tard (rendu différé), d'où les
 * copies gardées ici.
 *
 * Avec des shaders ({@code relief}), les cartes plates seraient éclairées de dos dès que le soleil est
 * derrière elles : les traits deviennent des tubes, la tête une boule (un anneau en relief pour l'orange,
 * toujours tourné vers la caméra), et chaque face a sa vraie orientation.
 */
final class Dessin {
    private static final int BOIS = 0xFF6E4B2A;
    private static final int SEGMENTS_TETE = 28;
    private static final int SEGMENTS_BOUT = 8;

    private final Squelette sq;
    private final Vector3f camera;
    private final int couleur;
    private final int lumiere;
    private final int overlay;
    private final float trace;
    private final boolean teteCreuse;
    private final boolean baton;
    private final boolean relief;

    private PoseStack.Pose pose;
    private VertexConsumer sortie;
    private int teinte;
    private final Vector3f vue = new Vector3f(), u = new Vector3f(), w = new Vector3f(), cote = new Vector3f(), fin = new Vector3f();
    private final Vector3f axe = new Vector3f(), p1 = new Vector3f(), p2 = new Vector3f(), n = new Vector3f(), q = new Vector3f();

    Dessin(Squelette sq, Vector3f camera, int couleur, int lumiere, int overlay, float trace, boolean teteCreuse, boolean baton,
           boolean relief) {
        this.sq = sq;
        this.camera = camera;
        this.couleur = couleur;
        this.lumiere = lumiere;
        this.overlay = overlay;
        this.trace = trace;
        this.teteCreuse = teteCreuse;
        this.baton = baton;
        this.relief = relief;
    }

    /**
     * Ordre du coup de crayon : la tête, le torse, un bras, l'autre, une jambe, l'autre, puis le bâton.
     * Avec {@code trace} &lt; 1, seuls les premiers traits sont là (le dernier à moitié).
     */
    void tracer(PoseStack.Pose pose, VertexConsumer sortie) {
        this.pose = pose;
        this.sortie = sortie;
        teinte = couleur;
        float e = Squelette.EPAISSEUR;
        float r = Squelette.RAYON_TETE;
        float avance = trace * (baton ? 11 : 10);
        float tete = part(avance, 0);
        if (tete > 0) {
            if (relief) {
                if (teteCreuse) {
                    tore(sq.tete, r - e / 2, e / 2, tete);
                } else {
                    boule(sq.tete, r * tete, 28, 16);
                }
            } else if (teteCreuse) {
                anneau(sq.tete, r - e, r, tete);
            } else {
                disque(sq.tete, r, SEGMENTS_TETE, tete);
            }
        }
        trait(sq.cou, sq.hanche, part(avance, 1), e);
        trait(sq.cou, sq.coudes[0], part(avance, 2), e);
        trait(sq.coudes[0], sq.mains[0], part(avance, 3), e);
        trait(sq.cou, sq.coudes[1], part(avance, 4), e);
        trait(sq.coudes[1], sq.mains[1], part(avance, 5), e);
        trait(sq.hanche, sq.genoux[0], part(avance, 6), e);
        trait(sq.genoux[0], sq.pieds[0], part(avance, 7), e);
        trait(sq.hanche, sq.genoux[1], part(avance, 8), e);
        trait(sq.genoux[1], sq.pieds[1], part(avance, 9), e);
        if (baton) {
            teinte = BOIS;
            trait(sq.batonBas, sq.batonHaut, part(avance, 10), e * 0.7f);
        }
    }

    private static float part(float avance, int i) {
        float f = avance - i;
        return f < 0 ? 0 : Math.min(f, 1);
    }

    /** Un trait de {@code a} vers {@code b} (seulement la fraction {@code part}), bouts arrondis. */
    private void trait(Vector3f a, Vector3f b, float part, float largeur) {
        if (part <= 0) {
            return;
        }
        fin.set(b).sub(a).mul(part).add(a);
        if (relief) {
            tube(a, fin, largeur / 2);
            boule(a, largeur / 2, 8, 4);
            boule(fin, largeur / 2, 8, 4);
            return;
        }
        float mx = (a.x + fin.x) / 2, my = (a.y + fin.y) / 2, mz = (a.z + fin.z) / 2;
        vue.set(camera).sub(mx, my, mz);
        if (vue.lengthSquared() > 1e-8f) {
            vue.normalize();
            cote.set(fin).sub(a).cross(vue);
            if (cote.lengthSquared() > 1e-10f) {
                cote.normalize().mul(largeur / 2);
                // Sens anti-horaire vu de la caméra : c'est la face avant, bien éclairée
                sommet(a.x + cote.x, a.y + cote.y, a.z + cote.z, 0, 0);
                sommet(fin.x + cote.x, fin.y + cote.y, fin.z + cote.z, 0, 1);
                sommet(fin.x - cote.x, fin.y - cote.y, fin.z - cote.z, 1, 1);
                sommet(a.x - cote.x, a.y - cote.y, a.z - cote.z, 1, 0);
            }
        }
        disque(a, largeur / 2, SEGMENTS_BOUT, 1);
        disque(fin, largeur / 2, SEGMENTS_BOUT, 1);
    }

    /** Un disque plein face à la caméra (seulement la part {@code part} du tour, pour le coup de crayon). */
    private void disque(Vector3f centre, float rayon, int n, float part) {
        base(centre);
        int fait = Math.max(2, (int) Math.ceil(n * part));
        for (int k = 0; k < fait; k += 2) {
            sommet(centre.x, centre.y, centre.z, 0.5f, 0.5f);
            for (int j = 0; j < 3; j++) {
                double angle = 2 * Math.PI * Math.min(k + j, fait) / n;
                float cos = (float) Math.cos(angle), sin = (float) Math.sin(angle);
                point(centre, cos * rayon, sin * rayon, 0.5f + 0.5f * cos, 0.5f + 0.5f * sin);
            }
        }
    }

    /** Un anneau face à la caméra : la tête creuse de l'orange. */
    private void anneau(Vector3f centre, float dedans, float dehors, float part) {
        base(centre);
        int fait = Math.max(1, (int) Math.ceil(SEGMENTS_TETE * part));
        for (int k = 0; k < fait; k++) {
            double a0 = 2 * Math.PI * k / SEGMENTS_TETE, a1 = 2 * Math.PI * (k + 1) / SEGMENTS_TETE;
            float c0 = (float) Math.cos(a0), s0 = (float) Math.sin(a0), c1 = (float) Math.cos(a1), s1 = (float) Math.sin(a1);
            float r = 0.5f * dedans / dehors;
            point(centre, c0 * dedans, s0 * dedans, 0.5f + r * c0, 0.5f + r * s0);
            point(centre, c0 * dehors, s0 * dehors, 0.5f + 0.5f * c0, 0.5f + 0.5f * s0);
            point(centre, c1 * dehors, s1 * dehors, 0.5f + 0.5f * c1, 0.5f + 0.5f * s1);
            point(centre, c1 * dedans, s1 * dedans, 0.5f + r * c1, 0.5f + r * s1);
        }
    }

    private void point(Vector3f centre, float x, float y, float tu, float tv) {
        sommet(centre.x + u.x * x + w.x * y, centre.y + u.y * x + w.y * y, centre.z + u.z * x + w.z * y, tu, tv);
    }

    /** Deux axes (u, w) du plan qui fait face à la caméra, en {@code centre}. */
    private void base(Vector3f centre) {
        vue.set(camera).sub(centre);
        if (vue.lengthSquared() < 1e-8f) {
            vue.set(0, 0, 1);
        }
        vue.normalize();
        u.set(0, 1, 0).cross(vue);
        if (u.lengthSquared() < 1e-6f) {
            u.set(1, 0, 0).cross(vue);
        }
        u.normalize();
        w.set(vue).cross(u);
    }

    /** Un tube de {@code a} à {@code b} : huit faces, tournées vers l'extérieur. */
    private void tube(Vector3f a, Vector3f b, float rayon) {
        axe.set(b).sub(a);
        float longueur = axe.length();
        if (longueur < 1e-5f) {
            return;
        }
        axe.div(longueur);
        p1.set(Math.abs(axe.y) < 0.9f ? 0 : 1, Math.abs(axe.y) < 0.9f ? 1 : 0, 0).cross(axe).normalize();
        p2.set(axe).cross(p1);
        int cotes = 8;
        for (int k = 0; k < cotes; k++) {
            // Sens anti-horaire vu de l'extérieur
            bord(a, rayon, k, cotes, 0);
            bord(a, rayon, k + 1, cotes, 0);
            bord(b, rayon, k + 1, cotes, 1);
            bord(b, rayon, k, cotes, 1);
        }
    }

    private void bord(Vector3f bout, float rayon, int j, int cotes, float tv) {
        double angle = 2 * Math.PI * j / cotes;
        float c = (float) Math.cos(angle), s = (float) Math.sin(angle);
        n.set(p1).mul(c).add(p2.x * s, p2.y * s, p2.z * s);
        q.set(n).mul(rayon).add(bout);
        sommet(q.x, q.y, q.z, (float) j / cotes, tv, n);
    }

    /** Une boule (une tête pleine, ou le bout arrondi d'un trait). */
    private void boule(Vector3f centre, float rayon, int longitudes, int latitudes) {
        for (int i = 0; i < latitudes; i++) {
            for (int j = 0; j < longitudes; j++) {
                coin(centre, rayon, i, j, longitudes, latitudes);
                coin(centre, rayon, i + 1, j, longitudes, latitudes);
                coin(centre, rayon, i + 1, j + 1, longitudes, latitudes);
                coin(centre, rayon, i, j + 1, longitudes, latitudes);
            }
        }
    }

    private void coin(Vector3f centre, float rayon, int i, int j, int longitudes, int latitudes) {
        double lat = -Math.PI / 2 + Math.PI * i / latitudes, lon = 2 * Math.PI * j / longitudes;
        float cl = (float) Math.cos(lat);
        n.set(cl * (float) Math.cos(lon), (float) Math.sin(lat), cl * (float) Math.sin(lon));
        q.set(n).mul(rayon).add(centre);
        sommet(q.x, q.y, q.z, (float) j / longitudes, (float) i / latitudes, n);
    }

    /** La tête creuse de l'orange en relief : un anneau dont l'axe vise la caméra. */
    private void tore(Vector3f centre, float rayonAnneau, float rayonTube, float part) {
        base(centre);
        int tours = 36, cotes = 10;
        int fait = Math.max(1, (int) Math.ceil(tours * part));
        for (int k = 0; k < fait; k++) {
            for (int l = 0; l < cotes; l++) {
                pointTore(centre, rayonAnneau, rayonTube, k, l, tours, cotes);
                pointTore(centre, rayonAnneau, rayonTube, k + 1, l, tours, cotes);
                pointTore(centre, rayonAnneau, rayonTube, k + 1, l + 1, tours, cotes);
                pointTore(centre, rayonAnneau, rayonTube, k, l + 1, tours, cotes);
            }
        }
    }

    private void pointTore(Vector3f centre, float rayonAnneau, float rayonTube, int k, int l, int tours, int cotes) {
        double phi = 2 * Math.PI * k / tours, psi = 2 * Math.PI * l / cotes;
        float cf = (float) Math.cos(phi), sf = (float) Math.sin(phi), cp = (float) Math.cos(psi), sp = (float) Math.sin(psi);
        // Direction dans le plan de l'anneau, puis la normale du tube autour de cette direction
        p1.set(u).mul(cf).add(w.x * sf, w.y * sf, w.z * sf);
        n.set(p1).mul(cp).add(vue.x * sp, vue.y * sp, vue.z * sp);
        q.set(p1).mul(rayonAnneau).add(n.x * rayonTube, n.y * rayonTube, n.z * rayonTube).add(centre);
        sommet(q.x, q.y, q.z, (float) k / tours, (float) l / cotes, n);
    }

    private void sommet(float x, float y, float z, float tu, float tv, Vector3f normale) {
        sortie.addVertex(pose, x, y, z).setColor(teinte).setUv(tu, tv).setOverlay(overlay).setLight(lumiere)
                .setNormal(pose, normale.x, normale.y, normale.z);
    }

    private void sommet(float x, float y, float z, float tu, float tv) {
        sortie.addVertex(pose, x, y, z).setColor(teinte).setUv(tu, tv).setOverlay(overlay).setLight(lumiere)
                .setNormal(pose, 0, 1, 0);
    }
}
