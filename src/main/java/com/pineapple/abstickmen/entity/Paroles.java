package com.pineapple.abstickmen.entity;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.pineapple.abstickmen.AlanBeckerStickmen;
import net.minecraft.util.RandomSource;

/**
 * Les phrases que l'orange écrit sur l'écran. Chacune est composée au moment d'écrire, à partir de
 * ce qui se passe (heure du jeu, météo, monstres battus, minerais trouvés, dernière acrobatie…), et
 * n'est jamais écrite deux fois : tout ce qu'il a déjà écrit est noté dans
 * config/abstickmen/phrases-ecrites.txt, d'une partie à l'autre aussi.
 */
public final class Paroles {
    /** Ce que l'orange sait au moment d'écrire. */
    public record Contexte(boolean fr, String joueur, String evenement, long heure, boolean pluie, boolean orage,
                           String dimension, boolean sousTerre, int y, float coeurs, int faim, String biome,
                           Map<String, Integer> victimes, Map<String, Integer> minerais, Personnage championVictimes,
                           Personnage championMinerais, Geste dernierGeste, Personnage auteurGeste, int amis,
                           boolean commandesPermises) {
    }

    private static final Set<String> DITES = new HashSet<>();
    private static boolean lues;

    private Paroles() {
    }

    private static Path journal() {
        return Reglages.dossier().resolve("phrases-ecrites.txt");
    }

    public static synchronized String composer(Contexte c, RandomSource r) {
        lire();
        String phrase = null;
        for (int essai = 0; essai < 400 && phrase == null; essai++) {
            String p = c.fr ? francais(c, r) : anglais(c, r);
            if (p.length() <= 120 && !DITES.contains(cle(p))) {
                phrase = p;
            }
        }
        if (phrase == null) {
            // Presque impossible : on ajoute de quoi la rendre unique
            phrase = (c.fr ? "Note n°" : "Note #") + (DITES.size() + 1) + " : " + (c.fr ? francais(c, r) : anglais(c, r));
        }
        retenir(phrase);
        return phrase;
    }

    // ------------------------------------------------------------------ français

    private static String francais(Contexte c, RandomSource r) {
        List<String> debuts = new ArrayList<>();
        List<String> idees = new ArrayList<>();
        List<String> principales = new ArrayList<>();
        String j = c.joueur;
        switch (c.evenement) {
            case "arrivee" -> {
                debuts.addAll(List.of("Coucou " + j + " !", "Me voilà !", "Merci de m'avoir dessiné, " + j + " !",
                        "Salut " + j + ", toute la bande est là !", "Ça y est, je suis entier !",
                        "Un coup de crayon et hop, me voilà !", "Bonjour " + j + " !", "Tadaaa !"));
                principales.addAll(List.of("on va bien s'amuser", "je garde mon bâton à portée de main", "les autres sont prêts aussi",
                        "tu nous fais visiter ton monde", "on te suit partout", "j'ai encore l'encre toute fraîche",
                        "on va t'aider à miner et à te battre", "personne n'embête notre ami " + j));
            }
            case "victoire" -> {
                debuts.addAll(List.of("Et voilà !", "Trop facile !", "Bien joué l'équipe !", "Victoire !", "On les a eus !",
                        "Ouf !", "Ha ha !", "Pas mal, hein ?"));
            }
            case "danger" -> {
                debuts.addAll(List.of("Attention " + j + " !", "Ouille !", "Fais gaffe !", "Oh non !", j + ", recule !"));
                int coeurs = Math.max(1, Math.round(c.coeurs));
                principales.addAll(List.of("tu n'as plus que " + coeurs + (coeurs > 1 ? " cœurs" : " cœur"), "recule, on s'en occupe",
                        "mange quelque chose, vite", "reste derrière nous", "tu es presque à court de cœurs",
                        "laisse-nous faire le gros du travail"));
            }
            case "diamants" -> debuts.addAll(List.of("Regarde ça !", "Incroyable !", "Des diamants !", "Waouh !", "Jackpot !"));
            case "nuit" -> {
                debuts.addAll(List.of("La nuit tombe.", "Il fait noir…", "Bonsoir " + j + ".", "Hou hou…"));
                principales.addAll(List.of("les monstres vont sortir", "reste près de nous", "je tiens mon bâton prêt",
                        "on monte la garde", "un lit, ce serait pas mal", "allume des torches"));
            }
            case "clic" -> debuts.addAll(List.of("Oui " + j + " ?", "Tu m'as appelé ?", "Hé " + j + " !", "Me voilà !",
                    "Psst, " + j + "…", "Tu sais quoi ?", "Écoute bien :", "Je t'écoute.", "Coucou !", "Hmm…"));
            default -> debuts.addAll(List.of("Hé " + j + " !", "Psst…", "Dis donc,", "Tu sais quoi ?", "Au fait,",
                    "Petit message :", "Je me disais…", "", "", ""));
        }

        // Ce qui s'est passé récemment
        Map.Entry<String, Integer> mob = premier(c.victimes);
        if (mob != null) {
            int n = mob.getValue();
            String nom = mobFr(mob.getKey(), n);
            (c.evenement.equals("victoire") ? principales : idees).addAll(List.of("on a battu " + n + " " + nom, n + " " + nom + " de moins dans le coin",
                    quiA(c.championVictimes) + " battu " + (n > 1 ? "plus de monstres que les autres" : "un " + nom + " tout seul"),
                    "personne ne touche à " + j + " tant qu'on est là", "les " + mobFr(mob.getKey(), 2) + " n'avaient aucune chance"));
        }
        Map.Entry<String, Integer> minerai = premier(c.minerais);
        if (minerai != null) {
            int n = minerai.getValue();
            String nom = mineraiFr(minerai.getKey());
            String qui = nomFr(c.championMinerais);
            (c.evenement.equals("diamants") ? principales : idees).addAll(List.of(quiA(c.championMinerais) + " miné " + n + (n > 1 ? " minerais " : " minerai ") + nom,
                    "on t'a rapporté " + n + (n > 1 ? " minerais " : " minerai ") + nom,
                    "je sens qu'il y a encore des minerais " + nom + " par ici",
                    (c.championMinerais == Personnage.ORANGE ? "j'adore" : qui + " adore") + " miner les minerais " + nom));
        }
        if (c.dernierGeste != Geste.AUCUN && !c.dernierGeste.nomFr.isEmpty()) {
            String geste = c.dernierGeste.nomFr;
            if (c.auteurGeste == Personnage.ORANGE) {
                idees.addAll(List.of("tu as vu " + possessif(geste, "mon ", "ma ") + " ?",
                        "je m'entraîne encore à faire " + geste, "j'ai réussi " + geste));
            } else {
                idees.addAll(List.of(nomFr(c.auteurGeste) + " vient de faire " + geste,
                        nomFr(c.auteurGeste) + " est tout fier de " + possessif(geste, "son ", "sa ")));
            }
        }

        // Le moment et l'endroit
        long h = c.heure;
        if (c.dimension.equals("the_nether")) {
            idees.addAll(List.of("il fait une chaleur infernale ici", "attention à la lave", "je n'aime pas trop le Nether",
                    "mes traits fondent presque dans le Nether", "les ghasts pleurent fort ce soir"));
        } else if (c.dimension.equals("the_end")) {
            idees.addAll(List.of("l'End, c'est vraiment étrange", "le dragon n'a qu'à bien se tenir",
                    "ne regarde pas les endermen dans les yeux", "il n'y a même pas de jour ici"));
        } else if (c.sousTerre) {
            idees.addAll(List.of("on est à la couche " + c.y, "il fait sombre dans cette grotte", "on creuse, on creuse…",
                    "je sens qu'il y a des diamants pas loin", "j'entends de l'eau couler quelque part",
                    "une grotte de plus à explorer"));
        } else {
            if (h < 1500) {
                idees.addAll(List.of("le soleil se lève", "une nouvelle journée commence", "c'est le matin, on est en forme"));
            } else if (h < 11500) {
                idees.addAll(List.of("il fait beau aujourd'hui", "le soleil tape fort", "c'est le moment de construire",
                        "il fait jour, profitons-en"));
            } else if (h < 13500) {
                idees.addAll(List.of("le soleil se couche", "le ciel devient tout orange, comme moi", "la nuit approche"));
            } else {
                idees.addAll(List.of("il fait nuit noire", "la lune est jolie ce soir", "j'entends des zombies au loin",
                        "la nuit, on reste groupés"));
            }
            if (c.orage) {
                idees.addAll(List.of("quel orage !", "j'ai peur de la foudre… sauf quand c'est moi qui la fais tomber"));
            } else if (c.pluie) {
                idees.addAll(List.of("il pleut des cordes", "j'adore le bruit de la pluie", "la pluie efface presque mes traits"));
            }
            String lieu = biomeFr(c.biome);
            if (lieu != null) {
                idees.add(lieu);
            }
        }
        if (c.coeurs < 5 && !c.evenement.equals("danger")) {
            idees.addAll(List.of("tu as l'air fatigué", "pense à te soigner"));
        }
        if (c.faim < 10) {
            idees.addAll(List.of("ton ventre gargouille", "il faudrait manger un morceau"));
        }
        if (c.amis > 0) {
            idees.addAll(List.of("on est " + (c.amis + 1) + " à te protéger", "Rouge et Bleu font encore la course",
                    "Jaune danse dès qu'on a le dos tourné", "Vert aiguise son épée", "Bleu travaille ses coups de pied",
                    "Rouge veut toujours se battre en premier"));
        }
        if (c.commandesPermises) {
            idees.addAll(List.of("mon bâton de commande est prêt", "les commandes sont activées, ça m'arrange",
                    "si tu as besoin, je tape une commande"));
        }
        idees.addAll(List.of("je suis fait de cinq traits et d'un cercle", "ma tête est creuse, mais pas vide",
                "j'écris avec mon doigt, c'est plus rapide", "j'adore ce monde en cubes", "un jour je ferai un salto quadruple",
                "tu construis quoi aujourd'hui ?", "je me demande qui m'a dessiné", "on fait une bonne équipe, toi et nous",
                "je ne sais pas cligner des yeux, je n'en ai pas", "ce monde est bien plus grand que mon bureau"));

        String debut = debuts.isEmpty() ? "" : debuts.get(r.nextInt(debuts.size()));
        String phrase = apres(debut, choisir(principales, idees, r));
        if (r.nextInt(3) == 0) {
            String autre = idees.get(r.nextInt(idees.size()));
            if (!autre.equalsIgnoreCase(phrase) && !autre.endsWith("?") && !phrase.endsWith("?")) {
                phrase = phrase + (r.nextBoolean() ? ", et " : ". Et ") + autre;
            }
        }
        if (!phrase.endsWith("?") && !phrase.endsWith("!") && !phrase.endsWith("…")) {
            phrase += r.nextInt(3) == 0 ? " !" : ".";
        }
        String[] fins = {"", "", "", " Je reste à côté de toi.", " Compte sur nous.", " Allez, on continue !",
                " C'est parti !", " Hi hi.", " On fait une pause ?", " Bon, je retourne à mon poste."};
        return (debut.isEmpty() ? "" : debut + " ") + phrase + fins[r.nextInt(fins.length)];
    }

    /** « j'ai » pour l'orange (c'est lui qui écrit), « Rouge a » pour les autres. */
    private static String quiA(Personnage p) {
        return p == Personnage.ORANGE ? "j'ai" : nomFr(p) + " a";
    }

    /** « un salto arrière » → « mon salto arrière », « une roue » → « ma roue ». */
    private static String possessif(String avecArticle, String masculin, String feminin) {
        if (avecArticle.startsWith("un ")) return masculin + avecArticle.substring(3);
        if (avecArticle.startsWith("une ")) return feminin + avecArticle.substring(4);
        if (avecArticle.startsWith("la ")) return feminin + avecArticle.substring(3);
        return avecArticle;
    }

    private static String nomFr(Personnage p) {
        return switch (p) {
            case ORANGE -> "Moi";
            case ROUGE -> "Rouge";
            case BLEU -> "Bleu";
            case VERT -> "Vert";
            case JAUNE -> "Jaune";
        };
    }

    private static String mobFr(String id, int n) {
        boolean p = n > 1;
        return switch (id) {
            case "zombie" -> p ? "zombies" : "zombie";
            case "skeleton" -> p ? "squelettes" : "squelette";
            case "creeper" -> p ? "creepers" : "creeper";
            case "drowned" -> p ? "noyés" : "noyé";
            case "husk" -> p ? "zombies momifiés" : "zombie momifié";
            case "stray" -> p ? "vagabonds" : "vagabond";
            case "bogged" -> p ? "embourbés" : "embourbé";
            case "witch" -> p ? "sorcières" : "sorcière";
            case "slime" -> p ? "slimes" : "slime";
            case "magma_cube" -> p ? "cubes de magma" : "cube de magma";
            case "pillager" -> p ? "pillards" : "pillard";
            case "vindicator" -> p ? "vindicateurs" : "vindicateur";
            case "zombie_villager" -> p ? "zombies villageois" : "zombie villageois";
            case "zombified_piglin" -> p ? "piglins zombifiés" : "piglin zombifié";
            case "piglin", "piglin_brute" -> p ? "piglins" : "piglin";
            case "hoglin" -> p ? "hoglins" : "hoglin";
            case "blaze" -> p ? "blazes" : "blaze";
            case "wither_skeleton" -> p ? "squelettes wither" : "squelette wither";
            case "enderman" -> p ? "endermen" : "enderman";
            case "phantom" -> p ? "phantoms" : "phantom";
            default -> p ? "monstres" : "monstre";       // y compris les bestioles qu'on préfère ne pas nommer
        };
    }

    private static String mineraiFr(String id) {
        String nom = mineraiCourt(id);
        return switch (nom) {
            case "coal" -> "de charbon";
            case "iron" -> "de fer";
            case "copper" -> "de cuivre";
            case "gold" -> "d'or";
            case "diamond" -> "de diamant";
            case "emerald" -> "d'émeraude";
            case "lapis" -> "de lapis-lazuli";
            case "redstone" -> "de redstone";
            case "quartz" -> "de quartz";
            default -> "rares";
        };
    }

    private static String biomeFr(String biome) {
        if (biome.contains("desert") || biome.contains("badlands")) return "il fait chaud dans ce désert";
        if (biome.contains("snow") || biome.contains("frozen") || biome.contains("ice")) return "brrr, il fait froid ici";
        if (biome.contains("ocean") || biome.contains("beach")) return "que d'eau par ici";
        if (biome.contains("jungle")) return "cette jungle est immense";
        if (biome.contains("cherry")) return "les cerisiers sont tout roses";
        if (biome.contains("forest") || biome.contains("taiga")) return "j'aime bien cette forêt";
        if (biome.contains("plains") || biome.contains("meadow")) return "ces plaines sont parfaites pour courir";
        if (biome.contains("peaks") || biome.contains("hills") || biome.contains("slopes")) return "quelle vue d'ici";
        if (biome.contains("swamp")) return "ce marais sent bizarre";
        if (biome.contains("mushroom")) return "des champignons géants, c'est rigolo";
        return null;
    }

    // ------------------------------------------------------------------ anglais

    private static String anglais(Contexte c, RandomSource r) {
        List<String> debuts = new ArrayList<>();
        List<String> idees = new ArrayList<>();
        List<String> principales = new ArrayList<>();
        String j = c.joueur;
        switch (c.evenement) {
            case "arrivee" -> {
                debuts.addAll(List.of("Hi " + j + "!", "Here I am!", "Thanks for drawing me, " + j + "!",
                        "Hey " + j + ", the whole gang is here!", "Done, I'm all drawn!", "Tadaaa!"));
                principales.addAll(List.of("this is going to be fun", "my staff is ready", "the others are ready too",
                        "show us your world", "we'll follow you everywhere", "my ink is still wet"));
            }
            case "victoire" -> debuts.addAll(List.of("There you go!", "Too easy!", "Nice teamwork!", "Victory!", "Got them!", "Phew!"));
            case "danger" -> {
                debuts.addAll(List.of("Careful " + j + "!", "Ouch!", "Watch out!", "Oh no!", j + ", step back!"));
                int coeurs = Math.max(1, Math.round(c.coeurs));
                principales.addAll(List.of("you only have " + coeurs + (coeurs > 1 ? " hearts" : " heart") + " left",
                        "step back, we've got this", "eat something, quick", "stay behind us"));
            }
            case "diamants" -> debuts.addAll(List.of("Look at that!", "Amazing!", "Diamonds!", "Wow!", "Jackpot!"));
            case "nuit" -> {
                debuts.addAll(List.of("Night is falling.", "It's getting dark…", "Good evening " + j + "."));
                principales.addAll(List.of("the monsters are coming out", "stay close to us", "my staff is ready", "we're on guard"));
            }
            case "clic" -> debuts.addAll(List.of("Yes " + j + "?", "You called?", "Hey " + j + "!", "Here I am!",
                    "Psst, " + j + "…", "Guess what?", "Listen:", "Hmm…"));
            default -> debuts.addAll(List.of("Hey " + j + "!", "Psst…", "By the way,", "Guess what?", "Quick note:", "", "", ""));
        }
        Map.Entry<String, Integer> mob = premier(c.victimes);
        if (mob != null) {
            int n = mob.getValue();
            String nom = mobEn(mob.getKey(), n);
            (c.evenement.equals("victoire") ? principales : idees).addAll(List.of("we beat " + n + " " + nom, n + " fewer " + nom + " around here",
                    "nobody touches " + j + " while we're here", "those " + mobEn(mob.getKey(), 2) + " never stood a chance"));
        }
        Map.Entry<String, Integer> minerai = premier(c.minerais);
        if (minerai != null) {
            int n = minerai.getValue();
            String nom = mineraiCourt(minerai.getKey());
            (c.evenement.equals("diamants") ? principales : idees).addAll(List.of(nomEn(c.championMinerais) + " mined " + n + " " + nom + " ore" + (n > 1 ? "s" : ""),
                    "we brought you " + n + " " + nom + " ore" + (n > 1 ? "s" : ""), "I bet there's more " + nom + " around here"));
        }
        if (c.dernierGeste != Geste.AUCUN && !c.dernierGeste.nomEn.isEmpty()) {
            idees.add(c.auteurGeste == Personnage.ORANGE ? "did you see my " + c.dernierGeste.nomEn.replaceFirst("^(a|the) ", "") + "?"
                    : nomEn(c.auteurGeste) + " just did " + c.dernierGeste.nomEn);
        }
        if (c.dimension.equals("the_nether")) {
            idees.addAll(List.of("it's so hot in here", "watch out for the lava", "I don't like the Nether much"));
        } else if (c.dimension.equals("the_end")) {
            idees.addAll(List.of("the End is really weird", "the dragon had better watch out", "don't look endermen in the eye"));
        } else if (c.sousTerre) {
            idees.addAll(List.of("we're at layer " + c.y, "it's dark in this cave", "dig, dig, dig…", "I can smell diamonds nearby"));
        } else {
            long h = c.heure;
            if (h < 1500) idees.addAll(List.of("the sun is rising", "a new day begins"));
            else if (h < 11500) idees.addAll(List.of("what a nice day", "perfect time to build"));
            else if (h < 13500) idees.addAll(List.of("the sun is setting", "the sky is turning orange, like me"));
            else idees.addAll(List.of("it's pitch dark", "the moon looks nice tonight", "I hear zombies far away"));
            if (c.orage) idees.add("what a storm");
            else if (c.pluie) idees.addAll(List.of("it's pouring", "I love the sound of rain"));
        }
        if (c.faim < 10) idees.add("your stomach is growling");
        if (c.amis > 0) idees.addAll(List.of("there are " + (c.amis + 1) + " of us protecting you", "Red and Blue are racing again",
                "Yellow dances whenever we look away"));
        if (c.commandesPermises) idees.addAll(List.of("my command staff is ready", "commands are on, good for me"));
        idees.addAll(List.of("I'm made of five lines and a circle", "my head is hollow, not empty", "I write with my finger",
                "I love this blocky world", "one day I'll do a quadruple flip", "what are you building today?",
                "we make a good team", "this world is way bigger than my desktop"));

        String debut = debuts.isEmpty() ? "" : debuts.get(r.nextInt(debuts.size()));
        String phrase = apres(debut, choisir(principales, idees, r));
        if (r.nextInt(3) == 0) {
            String autre = idees.get(r.nextInt(idees.size()));
            if (!autre.equalsIgnoreCase(phrase) && !autre.endsWith("?") && !phrase.endsWith("?")) {
                phrase = phrase + ", and " + autre;
            }
        }
        if (!phrase.endsWith("?") && !phrase.endsWith("!") && !phrase.endsWith("…")) {
            phrase += r.nextInt(3) == 0 ? "!" : ".";
        }
        String[] fins = {"", "", "", " I'm right here.", " Count on us.", " Let's keep going!", " Hehe."};
        return (debut.isEmpty() ? "" : debut + " ") + phrase + fins[r.nextInt(fins.length)];
    }

    private static String nomEn(Personnage p) {
        return switch (p) {
            case ORANGE -> "I";
            case ROUGE -> "Red";
            case BLEU -> "Blue";
            case VERT -> "Green";
            case JAUNE -> "Yellow";
        };
    }

    private static String mobEn(String id, int n) {
        boolean p = n > 1;
        return switch (id) {
            case "zombie", "skeleton", "creeper", "witch", "slime", "pillager", "vindicator", "piglin", "hoglin", "blaze", "phantom", "stray" ->
                    id + (p ? (id.equals("witch") ? "es" : "s") : "");
            case "drowned" -> "drowned";
            case "husk" -> p ? "husks" : "husk";
            case "enderman" -> p ? "endermen" : "enderman";
            default -> p ? "monsters" : "monster";
        };
    }

    // ------------------------------------------------------------------ outils

    /** « deepslate_diamond_ore » → « diamond ». */
    public static String mineraiCourt(String id) {
        for (String m : List.of("diamond", "emerald", "gold", "iron", "copper", "coal", "lapis", "redstone", "quartz")) {
            if (id.contains(m)) {
                return m;
            }
        }
        return "rare";
    }

    /** L'idée principale : presque toujours celle de l'événement (la victoire, la nuit…), sinon n'importe laquelle. */
    private static String choisir(List<String> principales, List<String> idees, RandomSource r) {
        List<String> liste = !principales.isEmpty() && r.nextInt(8) != 0 ? principales : idees;
        return liste.get(r.nextInt(liste.size()));
    }

    private static Map.Entry<String, Integer> premier(Map<String, Integer> m) {
        return m == null || m.isEmpty() ? null : m.entrySet().iterator().next();
    }

    /** Majuscule en début de phrase, mais pas après « Écoute bien : » ou « Au fait, ». */
    private static String apres(String debut, String phrase) {
        return debut.isEmpty() || debut.endsWith(".") || debut.endsWith("!") || debut.endsWith("?") || debut.endsWith("…")
                ? majuscule(phrase) : phrase;
    }

    private static String majuscule(String s) {
        return s.isEmpty() ? s : s.substring(0, 1).toUpperCase(Locale.ROOT) + s.substring(1);
    }

    private static String cle(String phrase) {
        return phrase.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]", "");
    }

    private static void lire() {
        if (lues) {
            return;
        }
        lues = true;
        try {
            if (Files.exists(journal())) {
                for (String ligne : Files.readAllLines(journal(), StandardCharsets.UTF_8)) {
                    if (!ligne.isBlank()) {
                        DITES.add(cle(ligne));
                    }
                }
            }
        } catch (IOException e) {
            AlanBeckerStickmen.LOG.warn("Journal des phrases illisible : {}", e.getMessage());
        }
    }

    private static void retenir(String phrase) {
        DITES.add(cle(phrase));
        try {
            Files.createDirectories(journal().getParent());
            Files.writeString(journal(), phrase + System.lineSeparator(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            AlanBeckerStickmen.LOG.warn("Impossible de noter la phrase : {}", e.getMessage());
        }
    }
}
