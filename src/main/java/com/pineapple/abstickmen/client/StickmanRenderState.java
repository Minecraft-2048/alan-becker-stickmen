package com.pineapple.abstickmen.client;

import com.pineapple.abstickmen.entity.Geste;
import com.pineapple.abstickmen.entity.Personnage;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;

/** Ce que le dessin d'un stickman doit savoir, à l'image près. */
public class StickmanRenderState extends LivingEntityRenderState {
    public Personnage personnage = Personnage.ORANGE;
    public Geste geste = Geste.AUCUN;
    /** Ticks écoulés depuis le début du geste, et sa durée quand elle dépend du texte. */
    public float tGeste;
    public int dureeGeste;
    public boolean assis;
    public boolean auSol = true;
    public boolean nage;
    public float vitesseVerticale;
    /** Part du dessin déjà tracée (1 = entier) ; en train d'être gommé si {@code gomme}. */
    public float trace = 1;
    public boolean gomme;
    /** La commande que l'orange est en train de taper (vide sinon). */
    public String commande = "";
    public boolean commandeUrgente;
    /** Décalage propre à chacun, pour qu'ils ne respirent pas tous en même temps. */
    public float decalage;
    public final ItemStackRenderState objetMain = new ItemStackRenderState();
    /** L'objet tenu est un arc : il se tient dans la main gauche, debout, pointé vers la cible. */
    public boolean arc;
    public final ItemStackRenderState blocCommande = new ItemStackRenderState();
}
