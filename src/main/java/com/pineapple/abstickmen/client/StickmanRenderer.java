package com.pineapple.abstickmen.client;

import java.util.List;
import java.util.Map;

import com.mojang.blaze3d.vertex.PoseStack;
import com.pineapple.abstickmen.AlanBeckerStickmen;
import com.pineapple.abstickmen.entity.Geste;
import com.pineapple.abstickmen.entity.Stickman;
import net.minecraft.client.gui.Font;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Dessine un stickman. Pas de modèle en cubes : le squelette est calculé pour la pose du moment
 * (voir {@link Animations}), puis tracé en traits plats tournés vers la caméra (voir {@link Dessin}).
 * Les objets tenus (pioche, épée, bloc de commande au bout du bâton) sont les vrais objets du jeu.
 */
public class StickmanRenderer extends LivingEntityRenderer<Stickman, StickmanRenderState, StickmanRenderer.ModeleVide> {
    static final Identifier BLANC = AlanBeckerStickmen.id("textures/entity/blanc.png");
    private static final int PLEINE_LUMIERE = 0xF000F0;

    private ItemStack blocCommande;

    public StickmanRenderer(EntityRendererProvider.Context context) {
        super(context, new ModeleVide(), 0.35f);
    }

    @Override
    public StickmanRenderState createRenderState() {
        return new StickmanRenderState();
    }

    @Override
    public void extractRenderState(Stickman entite, StickmanRenderState etat, float partialTick) {
        super.extractRenderState(entite, etat, partialTick);
        etat.personnage = entite.personnage;
        etat.geste = entite.gesteVisible();
        etat.dureeGeste = entite.dureeGeste();
        etat.tGeste = entite.tempsGeste(partialTick);
        etat.assis = entite.estAssis();
        etat.auSol = entite.onGround();
        etat.nage = entite.isInWater() && !entite.onGround();
        etat.vitesseVerticale = (float) entite.getDeltaMovement().y;
        etat.decalage = (entite.getId() * 37) % 100;
        int dessin = entite.dessin();
        etat.gomme = dessin < 0;
        if (dessin > 0) {
            etat.trace = Mth.clamp(1 - (dessin - partialTick) / Stickman.DUREE_DESSIN, 0, 1);
        } else if (dessin < 0) {
            etat.trace = Mth.clamp((-dessin - partialTick) / Stickman.DUREE_GOMME, 0, 1);
        } else {
            etat.trace = 1;
        }
        String commande = entite.commandeVisible();
        etat.commandeUrgente = commande.startsWith(Stickman.MARQUE_URGENTE);
        etat.commande = etat.commandeUrgente ? commande.substring(Stickman.MARQUE_URGENTE.length()) : commande;
        itemModelResolver.updateForLiving(etat.objetMain, entite.getMainHandItem(), ItemDisplayContext.NONE, entite);
        if (entite.personnage.estOrange()) {
            if (blocCommande == null) {
                blocCommande = new ItemStack(Items.COMMAND_BLOCK);
            }
            itemModelResolver.updateForLiving(etat.blocCommande, blocCommande, ItemDisplayContext.NONE, entite);
        } else {
            etat.blocCommande.clear();
        }
    }

    /** Pas de nom au-dessus de la tête, sauf un nom donné avec une étiquette, quand on le vise (comme les mobs). */
    @Override
    protected boolean shouldShowName(Stickman entite, double distance) {
        return super.shouldShowName(entite, distance)
                && (entite.shouldShowName() || entite.hasCustomName() && entite == entityRenderDispatcher.crosshairPickEntity);
    }

    @Override
    public Identifier getTextureLocation(StickmanRenderState etat) {
        return BLANC;
    }

    /** Pas de modèle en cubes à dessiner : tout est fait dans {@link #submit}. */
    @Override
    protected RenderType getRenderType(StickmanRenderState etat, boolean visible, boolean translucide, boolean brille) {
        return null;
    }

    @Override
    public void submit(StickmanRenderState etat, PoseStack pile, SubmitNodeCollector collecteur, CameraRenderState camera) {
        super.submit(etat, pile, collecteur, camera);
        if (etat.isInvisible) {
            return;
        }
        Pose pose = new Pose();
        Animations.poser(pose, new Pose(), etat);
        Squelette sq = new Squelette();
        sq.calculer(pose, etat.bodyRot);
        Vector3f cam = new Vector3f((float) (camera.pos.x - etat.x), (float) (camera.pos.y - etat.y), (float) (camera.pos.z - etat.z));
        boolean orange = etat.personnage.estOrange();
        int couleur = 0xFF000000 | etat.personnage.couleur;
        int overlay = getOverlayCoords(etat, 0);
        Dessin dessin = new Dessin(sq, cam, couleur, etat.lightCoords, overlay, etat.trace, etat.personnage.teteCreuse, orange,
                Shaders.actifs());
        collecteur.submitCustomGeometry(pile, RenderTypes.entityCutout(BLANC), dessin::tracer);

        if (etat.trace >= 1 && !etat.objetMain.isEmpty()) {
            objetEnMain(etat, pile, collecteur, sq);
        }
        if (orange && etat.trace >= 1 && !etat.blocCommande.isEmpty()) {
            blocAuBoutDuBaton(etat, pile, collecteur, sq, cam);
        }
        if (orange && etat.geste == Geste.COMMANDE && !etat.commande.isEmpty()) {
            bulleDeCommande(etat, pile, collecteur, camera, sq);
        }
    }

    /**
     * L'objet tenu dans la main droite : le manche dans le poing, perpendiculaire à l'avant-bras,
     * la lame (ou la tête de la pioche) vers l'avant du bras.
     */
    private static void objetEnMain(StickmanRenderState etat, PoseStack pile, SubmitNodeCollector collecteur, Squelette sq) {
        Vector3f axe = sq.axeAvantBras[0], devant = sq.devantAvantBras[0];
        Vector3f normale = new Vector3f(axe).cross(devant).normalize();
        Vector3f travers = new Vector3f(normale).cross(devant).normalize();
        // Dans l'objet (sprite de 1 bloc centré), le manche est en bas à gauche et la pointe en haut à droite
        float r = (float) (1 / Math.sqrt(2));
        Matrix3f vers = new Matrix3f().set(devant, travers, normale);
        Matrix3f depuis = new Matrix3f().set(new Vector3f(r, r, 0), new Vector3f(-r, r, 0), new Vector3f(0, 0, 1));
        Matrix3f rotation = vers.mul(depuis.transpose());
        Vector3f main = sq.mains[0];
        pile.pushPose();
        pile.translate(main.x, main.y, main.z);
        pile.mulPose(new Matrix4f().set(rotation));
        pile.scale(0.75f, 0.75f, 0.75f);
        pile.translate(0.27f, 0.27f, 0);
        etat.objetMain.submit(pile, collecteur, etat.lightCoords, OverlayTexture.NO_OVERLAY, etat.outlineColor);
        pile.popPose();
    }

    /** Un petit bloc de commande planté au bout du bâton de l'orange. */
    private static void blocAuBoutDuBaton(StickmanRenderState etat, PoseStack pile, SubmitNodeCollector collecteur, Squelette sq, Vector3f cam) {
        Vector3f haut = new Vector3f(sq.axeBaton);
        Vector3f face = new Vector3f(cam).sub(sq.batonHaut);
        face.sub(new Vector3f(haut).mul(face.dot(haut)));
        if (face.lengthSquared() < 1e-6f) {
            face.set(0, 0, 1);
        }
        face.normalize().negate();
        Vector3f cote = new Vector3f(haut).cross(face).normalize();
        Matrix3f rotation = new Matrix3f().set(cote, haut, face);
        float taille = 0.24f;
        Vector3f centre = new Vector3f(haut).mul(taille / 2 - 0.02f).add(sq.batonHaut);
        pile.pushPose();
        pile.translate(centre.x, centre.y, centre.z);
        pile.mulPose(new Matrix4f().set(rotation));
        pile.scale(taille, taille, taille);
        etat.blocCommande.submit(pile, collecteur, etat.lightCoords, OverlayTexture.NO_OVERLAY, etat.outlineColor);
        pile.popPose();
    }

    /** La commande s'écrit en l'air au-dessus de sa tête, lettre après lettre, comme dans la console du jeu. */
    private void bulleDeCommande(StickmanRenderState etat, PoseStack pile, SubmitNodeCollector collecteur, CameraRenderState camera, Squelette sq) {
        int lever = etat.commandeUrgente ? Stickman.LEVER_URGENT : Stickman.LEVER;
        float vitesse = etat.commandeUrgente ? Stickman.FRAPPE_URGENTE : Stickman.FRAPPE;
        int lettres = Mth.clamp((int) ((etat.tGeste - lever) * vitesse), 0, etat.commande.length());
        boolean curseur = lettres < etat.commande.length() || ((int) (etat.ageInTicks / 6)) % 2 == 0;
        String vu = etat.commande.substring(0, lettres) + (curseur ? "_" : " ");
        FormattedCharSequence texte = FormattedCharSequence.forward(vu, Style.EMPTY);
        Font police = getFont();
        float largeur = police.width(texte);
        float y = Math.max(sq.tete.y + Squelette.RAYON_TETE, sq.batonHaut.y + 0.24f) + 0.35f;
        pile.pushPose();
        pile.translate(sq.tete.x, y, sq.tete.z);
        pile.rotate(camera.orientation);
        pile.scale(0.022f, -0.022f, 0.022f);
        collecteur.submitText(pile, -largeur / 2, 0, texte, false, Font.DisplayMode.NORMAL, PLEINE_LUMIERE, 0xFFFFFFFF, 0xD0101014, 0);
        pile.popPose();
    }

    /** Un modèle sans aucun cube : le renderer vivant s'occupe de l'ombre, du nom et de la mort. */
    public static final class ModeleVide extends EntityModel<StickmanRenderState> {
        ModeleVide() {
            super(new ModelPart(List.of(), Map.of()));
        }
    }
}
