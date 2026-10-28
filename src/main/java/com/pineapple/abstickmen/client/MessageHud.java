package com.pineapple.abstickmen.client;

import java.util.ArrayList;
import java.util.List;

import com.pineapple.abstickmen.entity.Stickman;
import com.pineapple.abstickmen.reseau.MessageEcran;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Util;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

/**
 * Le message que l'orange écrit sur l'écran avec son doigt : grosses lettres à sa couleur, cernées
 * de sombre pour se lire sur n'importe quel fond, qui apparaissent une à une au-dessus de lui (ou en
 * haut de l'écran s'il n'est pas dans le champ), puis s'effacent.
 */
public final class MessageHud implements HudElement {
    private static @Nullable MessageEcran message;
    private static long debut;
    /** Dernière place à l'écran (lissée), pour que le texte ne saute pas quand l'orange bouge. */
    private static float ancreX = Float.NaN, ancreY;

    public static void afficher(MessageEcran nouveau) {
        message = nouveau;
        debut = Util.getMillis();
        ancreX = Float.NaN;
    }

    public static @Nullable String messageAffiche() {
        return message == null ? null : message.texte();
    }

    private static float duree(String texte) {
        return texte.length() / Stickman.LETTRES_PAR_SECONDE + 4.5f;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, DeltaTracker temps) {
        MessageEcran m = message;
        if (m == null) {
            return;
        }
        float secondes = (Util.getMillis() - debut) / 1000f;
        float duree = duree(m.texte());
        if (secondes > duree) {
            message = null;
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        Font police = mc.font;
        float echelle = g.guiWidth() >= 420 ? 2f : 1.5f;
        int largeurMax = (int) Math.min(230, (g.guiWidth() - 24) / echelle);
        List<String> lignes = couper(police, m.texte(), largeurMax);
        int hauteurLigne = police.lineHeight + 1;
        float hauteur = lignes.size() * hauteurLigne * echelle;
        float largeur = 0;
        for (String ligne : lignes) {
            largeur = Math.max(largeur, police.width(ligne) * echelle);
        }

        // Au-dessus de l'orange s'il est à l'écran, sinon en haut au milieu
        float x = g.guiWidth() / 2f, y = g.guiHeight() * 0.22f;
        Vec3 ecran = projeter(mc, m.entite(), temps.getGameTimeDeltaPartialTick(false));
        if (ecran != null) {
            x = (float) ecran.x * g.guiWidth();
            y = (float) ecran.y * g.guiHeight() - 6;
        }
        if (Float.isNaN(ancreX)) {
            ancreX = x;
            ancreY = y;
        } else {
            ancreX += (x - ancreX) * 0.15f;
            ancreY += (y - ancreY) * 0.15f;
        }
        float gauche = Math.max(6, Math.min(g.guiWidth() - 6 - largeur, ancreX - largeur / 2));
        float haut = Math.max(6, Math.min(g.guiHeight() - 40 - hauteur, ancreY - hauteur));

        int alpha = (int) (255 * Math.max(0, Math.min(1, (duree - secondes) / 1.2f)));
        if (alpha < 8) {
            return;
        }
        int encre = (alpha << 24) | (m.couleur() & 0xFFFFFF);
        int contour = ((alpha * 215 / 255) << 24) | 0x121216;
        int lettres = (int) (secondes * Stickman.LETTRES_PAR_SECONDE);

        g.pose().pushMatrix();
        g.pose().translate(gauche, haut);
        g.pose().scale(echelle, echelle);
        int restant = lettres;
        float finX = 0, finY = 0;
        for (int i = 0; i < lignes.size() && restant > 0; i++) {
            String ligne = lignes.get(i);
            String vu = ligne.substring(0, Math.min(ligne.length(), restant));
            restant -= ligne.length() + 1;
            int lx = (int) ((largeur / echelle - police.width(ligne)) / 2);
            int ly = i * hauteurLigne;
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    if (dx != 0 || dy != 0) {
                        g.text(police, vu, lx + dx, ly + dy, contour, false);
                    }
                }
            }
            g.text(police, vu, lx, ly, encre, false);
            finX = lx + police.width(vu);
            finY = ly;
        }
        // Le bout du doigt qui écrit
        if (lettres < m.texte().length()) {
            g.fill((int) finX + 1, (int) finY + 2, (int) finX + 3, (int) finY + 4, encre);
        }
        g.pose().popMatrix();
    }

    /** Coupe le texte en lignes d'au plus {@code largeurMax} pixels, sans couper les mots. */
    private static List<String> couper(Font police, String texte, int largeurMax) {
        List<String> lignes = new ArrayList<>();
        StringBuilder ligne = new StringBuilder();
        for (String mot : texte.split(" ")) {
            String essai = ligne.isEmpty() ? mot : ligne + " " + mot;
            if (police.width(essai) > largeurMax && !ligne.isEmpty()) {
                lignes.add(ligne.toString());
                ligne = new StringBuilder(mot);
            } else {
                ligne = new StringBuilder(essai);
            }
        }
        if (!ligne.isEmpty()) {
            lignes.add(ligne.toString());
        }
        return lignes;
    }

    /** Position de la tête de l'orange à l'écran (0 à 1 sur chaque axe), ou null s'il n'est pas visible. */
    private static @Nullable Vec3 projeter(Minecraft mc, int id, float partialTick) {
        if (mc.level == null) {
            return null;
        }
        Entity orange = mc.level.getEntity(id);
        if (orange == null) {
            return null;
        }
        Vec3 pos = orange.getPosition(partialTick).add(0, orange.getBbHeight() + 0.35, 0);
        Camera camera = mc.gameRenderer.mainCamera();
        Vec3 vers = pos.subtract(camera.position());
        Vector3fc avant = camera.forwardVector();
        if (vers.x * avant.x() + vers.y * avant.y() + vers.z * avant.z() < 0.5) {
            return null;
        }
        Vec3 p = mc.gameRenderer.projectPointToScreen(pos);
        if (p == null || p.z > 1 || p.z < -1 || Math.abs(p.x) > 1.1 || Math.abs(p.y) > 1.1) {
            return null;
        }
        return new Vec3((p.x + 1) / 2, (1 - p.y) / 2, 0);
    }
}
