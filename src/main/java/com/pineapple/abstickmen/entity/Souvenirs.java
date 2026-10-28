package com.pineapple.abstickmen.entity;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Ce que la bande d'un joueur a vécu ces dernières minutes : monstres battus, minerais trouvés,
 * dernière acrobatie. L'orange s'en sert pour écrire des phrases de circonstance, et pour savoir
 * quand le joueur mine beaucoup. Rien n'est sauvegardé : c'est une mémoire de courte durée.
 */
public final class Souvenirs {
    private static final Map<UUID, Souvenirs> PAR_JOUEUR = new HashMap<>();
    /** Au-delà, on oublie (en ticks : 5 minutes). */
    private static final long OUBLI = 6000;

    private record Fait(long quand, String quoi, Personnage qui) {
    }

    private final ArrayDeque<Fait> victimes = new ArrayDeque<>();
    private final ArrayDeque<Fait> minerais = new ArrayDeque<>();
    private final ArrayDeque<Long> blocsDuJoueur = new ArrayDeque<>();
    public Geste dernierGeste = Geste.AUCUN;
    public Personnage auteurDernierGeste = Personnage.ORANGE;

    public static synchronized Souvenirs de(UUID joueur) {
        return PAR_JOUEUR.computeIfAbsent(joueur, k -> new Souvenirs());
    }

    public static synchronized void oublierTout() {
        PAR_JOUEUR.clear();
    }

    public synchronized void victime(long quand, String mob, Personnage qui) {
        victimes.addLast(new Fait(quand, mob, qui));
        nettoyer(quand);
    }

    public synchronized void minerai(long quand, String minerai, Personnage qui) {
        minerais.addLast(new Fait(quand, minerai, qui));
        nettoyer(quand);
    }

    public synchronized void blocCasseParLeJoueur(long quand) {
        blocsDuJoueur.addLast(quand);
        while (!blocsDuJoueur.isEmpty() && blocsDuJoueur.peekFirst() < quand - 600) {
            blocsDuJoueur.removeFirst();
        }
    }

    /** Nombre de blocs cassés par le joueur pendant les 30 dernières secondes. */
    public synchronized int blocsRecents(long maintenant) {
        int n = 0;
        for (long t : blocsDuJoueur) {
            if (t >= maintenant - 600) {
                n++;
            }
        }
        return n;
    }

    /** Monstres battus depuis {@code depuis}, du plus nombreux au moins nombreux. */
    public synchronized Map<String, Integer> victimesDepuis(long depuis) {
        return compter(victimes, depuis);
    }

    public synchronized Map<String, Integer> mineraisDepuis(long depuis) {
        return compter(minerais, depuis);
    }

    /** Qui a battu ou miné le plus de choses de ce type depuis {@code depuis}. */
    public synchronized Personnage champion(boolean minerai, String quoi, long depuis) {
        Map<Personnage, Integer> n = new HashMap<>();
        for (Fait f : minerai ? minerais : victimes) {
            if (f.quand >= depuis && f.quoi.equals(quoi)) {
                n.merge(f.qui, 1, Integer::sum);
            }
        }
        return n.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(Personnage.ORANGE);
    }

    private static Map<String, Integer> compter(ArrayDeque<Fait> faits, long depuis) {
        Map<String, Integer> n = new HashMap<>();
        for (Fait f : faits) {
            if (f.quand >= depuis) {
                n.merge(f.quoi, 1, Integer::sum);
            }
        }
        Map<String, Integer> trie = new LinkedHashMap<>();
        n.entrySet().stream().sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .forEach(e -> trie.put(e.getKey(), e.getValue()));
        return trie;
    }

    private void nettoyer(long maintenant) {
        for (ArrayDeque<Fait> faits : java.util.List.of(victimes, minerais)) {
            Iterator<Fait> it = faits.iterator();
            while (it.hasNext() && it.next().quand < maintenant - OUBLI) {
                it.remove();
            }
        }
    }
}
