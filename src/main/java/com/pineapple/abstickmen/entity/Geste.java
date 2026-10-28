package com.pineapple.abstickmen.entity;

/**
 * Tout ce qu'un stickman sait faire de spécial. Le serveur choisit le geste, les clients le dessinent
 * (voir client.Animations) : il n'y a que le numéro du geste et sa durée qui voyagent sur le réseau.
 */
public enum Geste {
    AUCUN(0, false, Famille.AUCUNE, "", ""),

    // Pour s'amuser quand tout est calme
    SALUT(40, false, Famille.AMUSEMENT, "un coucou", "a wave"),
    SALTO_ARRIERE(26, true, Famille.AMUSEMENT, "un salto arrière", "a backflip"),
    SALTO_AVANT(26, true, Famille.AMUSEMENT, "un salto avant", "a front flip"),
    ROUE(30, true, Famille.AMUSEMENT, "une roue", "a cartwheel"),
    DANSE_DISCO(80, true, Famille.AMUSEMENT, "une danse disco", "a disco dance"),
    DANSE_ROBOT(84, true, Famille.AMUSEMENT, "la danse du robot", "the robot dance"),
    SAUTS_ECART(48, true, Famille.AMUSEMENT, "une série de sauts écartés", "a set of jumping jacks"),
    POIRIER(70, true, Famille.AMUSEMENT, "un poirier", "a handstand"),
    POMPES(72, true, Famille.AMUSEMENT, "une série de pompes", "a set of push-ups"),
    KATA(60, true, Famille.AMUSEMENT, "un kata de karaté", "a karate kata"),
    TOUPIE(30, true, Famille.AMUSEMENT, "un tour sur moi-même", "a spin"),
    ETIREMENT(50, false, Famille.AMUSEMENT, "un grand étirement", "a big stretch"),
    GRATTE_TETE(40, false, Famille.AMUSEMENT, "un grattage de tête", "a head scratch"),
    BRAS_CROISES(60, false, Famille.AMUSEMENT, "la pose bras croisés", "the arms-crossed pose"),
    MAINS_HANCHES(50, false, Famille.AMUSEMENT, "une pose de héros", "a hero pose"),
    VICTOIRE(30, true, Famille.AMUSEMENT, "un saut de victoire", "a victory jump"),
    /** Il reste assis tant qu'on ne lui dit pas de suivre (durée sans fin). */
    ASSIS(0, true, Famille.AUCUNE, "", ""),

    // Combat
    DIRECT(8, false, Famille.COMBAT, "direct", "jab"),
    CROCHET(10, false, Famille.COMBAT, "crochet", "hook"),
    UPPERCUT(10, false, Famille.COMBAT, "uppercut", "uppercut"),
    COUP_DE_PIED(12, true, Famille.COMBAT, "coup de pied", "kick"),
    COUP_DE_PIED_RETOURNE(14, true, Famille.COMBAT, "coup de pied retourné", "spinning kick"),
    COUP_DE_PIED_SAUTE(16, true, Famille.COMBAT, "coup de pied sauté", "flying kick"),
    COUP_D_EPEE(10, false, Famille.COMBAT, "coup d'épée", "sword slash"),
    COUP_DE_BATON(12, false, Famille.COMBAT, "coup de bâton", "staff strike"),

    // Travail
    PIOCHE(10, false, Famille.TRAVAIL, "coup de pioche", "pickaxe swing"),
    LANCER(12, false, Famille.TRAVAIL, "lancer", "throw"),
    PERLE(12, false, Famille.TRAVAIL, "lancer de perle", "pearl throw"),

    // L'orange seulement (durées calculées d'après le texte)
    COMMANDE(0, false, Famille.SPECIAL, "commande", "command"),
    ECRIRE(0, false, Famille.SPECIAL, "message", "message");

    public enum Famille { AUCUNE, AMUSEMENT, COMBAT, TRAVAIL, SPECIAL }

    /** Durée en ticks (0 : donnée au moment de le jouer, ou sans fin pour ASSIS). */
    public final int duree;
    /** Vrai si le geste prend tout le corps ; sinon les jambes continuent de marcher. */
    public final boolean corpsEntier;
    public final Famille famille;
    /** Nom avec son article, pour les phrases de l'orange (« un salto arrière »). */
    public final String nomFr;
    public final String nomEn;

    Geste(int duree, boolean corpsEntier, Famille famille, String nomFr, String nomEn) {
        this.duree = duree;
        this.corpsEntier = corpsEntier;
        this.famille = famille;
        this.nomFr = nomFr;
        this.nomEn = nomEn;
    }

    private static final Geste[] TOUS = values();

    public static Geste parNumero(int numero) {
        return numero >= 0 && numero < TOUS.length ? TOUS[numero] : AUCUN;
    }
}
