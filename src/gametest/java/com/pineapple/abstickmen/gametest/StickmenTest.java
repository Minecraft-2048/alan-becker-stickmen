package com.pineapple.abstickmen.gametest;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

import com.pineapple.abstickmen.client.MessageHud;
import com.pineapple.abstickmen.entity.Commandes;
import com.pineapple.abstickmen.entity.Geste;
import com.pineapple.abstickmen.entity.Personnage;
import com.pineapple.abstickmen.entity.Stickman;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Dessine vraiment la bande avec le crayon (clic droit), puis photographie chaque mouvement, un
 * combat, le minage, une commande de l'orange et un message écrit à l'écran.
 *
 *   ./gradlew runClientGameTest                 tout
 *   ./gradlew runClientGameTest -Pscenes=gestes seulement certaines scènes (gestes, angles, combat, minage, commande)
 *
 * Captures : run/gametest/screenshots.
 */
public class StickmenTest implements FabricClientGameTest {
    private static final Logger LOG = LoggerFactory.getLogger("Alan Becker Stickmen test");

    private ClientGameTestContext context;
    private TestServerContext server;

    @Override
    public void runTest(ClientGameTestContext context) {
        if (Boolean.getBoolean("abstickmen.photos")) {
            return;     // seulement les photos (PhotosTest)
        }
        this.context = context;
        try (TestSingleplayerContext solo = context.worldBuilder().create()) {
            server = solo.getServer();
            run("gamerule advance_time false");
            run("gamerule advance_weather false");
            run("gamerule spawn_mobs false");
            run("time set 6000");
            run("weather clear");
            run("gamemode survival @p");
            run("effect give @p resistance infinite 255 true");
            run("effect give @p saturation infinite 255 true");
            run("tp @p 0.5 -60 0.5 0 10");
            run("item replace entity @p weapon.mainhand with abstickmen:crayon");
            solo.getConnection().waitForChunksRender();
            context.waitTicks(20);
            // Comme dans un monde avec les commandes activées : le joueur est opérateur
            server.runOnServer(s -> s.getPlayerList().op(joueur(s).nameAndId()));
            context.waitTicks(5);
            LOG.info("[test] commandes permises au joueur : {}", (Object) server.computeOnServer(s -> Commandes.permises(joueur(s))));

            // Clic droit avec le crayon : la bande se dessine devant le joueur
            context.getInput().holdKeyFor(options -> options.keyUse, 2);
            context.waitTicks(12);
            context.takeScreenshot("01-coup-de-crayon");
            context.waitTicks(26);
            context.takeScreenshot("02-dessines");
            LOG.info("[test] stickmen dessinés : {}", (Object) server.computeOnServer(s -> bande(s).size()));
            context.waitTicks(30);
            context.takeScreenshot("03-message-d-accueil");
            LOG.info("[test] message affiché : {}", (Object) context.computeOnClient(mc -> MessageHud.messageAffiche()));
            context.waitTicks(60);
            context.takeScreenshot("04-message-fin");

            if (voulu("gestes")) {
                galerie();
            }
            if (voulu("angles")) {
                angles();
            }
            if (voulu("commande")) {
                commandeEtMessage();
                phrasesFrancaises();
            }
            if (voulu("combat")) {
                combat();
            }
            if (voulu("minage")) {
                minage();
            }

            // Accroupi + clic droit : la gomme efface toute la bande
            run("tp @p 0.5 -60 0.5 0 10");
            context.waitTicks(40);
            context.getInput().holdKey(options -> options.keyShift);
            context.waitTicks(4);
            context.getInput().holdKeyFor(options -> options.keyUse, 2);
            context.getInput().releaseKey(options -> options.keyShift);
            context.waitTicks(8);
            context.takeScreenshot("90-gomme");
            context.waitTicks(30);
            LOG.info("[test] stickmen après la gomme : {}", (Object) server.computeOnServer(s -> bande(s).size()));
        }
    }

    /** Tous en ligne face au joueur, chacun fait le même mouvement : une capture par mouvement. */
    private void galerie() {
        ranger(4.5);
        run("tp @p 0.5 -59.4 -1.2 0 6");
        context.runOnClient(mc -> {
            if (!mc.gui.hud.isHidden()) {
                mc.gui.hud.toggle();
            }
        });
        context.waitTicks(10);
        context.takeScreenshot("10-repos");
        Object[][] gestes = {
                {Geste.SALUT, 0.5}, {Geste.SALTO_ARRIERE, 0.45}, {Geste.SALTO_AVANT, 0.4}, {Geste.ROUE, 0.45},
                {Geste.DANSE_DISCO, 0.3}, {Geste.DANSE_ROBOT, 0.3}, {Geste.SAUTS_ECART, 0.13}, {Geste.POIRIER, 0.5},
                {Geste.POMPES, 0.4}, {Geste.KATA, 0.45}, {Geste.TOUPIE, 0.5}, {Geste.ETIREMENT, 0.25},
                {Geste.GRATTE_TETE, 0.5}, {Geste.BRAS_CROISES, 0.5}, {Geste.MAINS_HANCHES, 0.5}, {Geste.VICTOIRE, 0.4},
                {Geste.DIRECT, 0.45}, {Geste.CROCHET, 0.45}, {Geste.UPPERCUT, 0.45}, {Geste.COUP_DE_PIED, 0.5},
                {Geste.COUP_DE_PIED_RETOURNE, 0.55}, {Geste.COUP_DE_PIED_SAUTE, 0.45}, {Geste.COUP_D_EPEE, 0.45},
                {Geste.COUP_DE_BATON, 0.3}, {Geste.PIOCHE, 0.35}, {Geste.LANCER, 0.4},
        };
        for (Object[] g : gestes) {
            Geste geste = (Geste) g[0];
            double moment = (Double) g[1];
            surTous(s -> {
                if (geste == Geste.COUP_D_EPEE) {
                    s.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, new net.minecraft.world.item.ItemStack(Items.IRON_SWORD));
                } else if (geste == Geste.PIOCHE) {
                    s.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, new net.minecraft.world.item.ItemStack(Items.IRON_PICKAXE));
                } else {
                    s.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, net.minecraft.world.item.ItemStack.EMPTY);
                }
                s.jouer(geste);
            });
            context.waitTicks(1 + (int) Math.round(geste.duree * moment));
            context.takeScreenshot("11-geste-" + geste.name().toLowerCase(Locale.ROOT));
            context.waitTicks(geste.duree);
        }
        surTous(s -> s.asseoir(true));
        context.waitTicks(10);
        context.takeScreenshot("12-assis");
        surTous(s -> s.asseoir(false));
        context.waitTicks(5);
    }

    /** La tête creuse de l'orange vue de face, de côté et de dessus : elle doit toujours faire face à la caméra. */
    private void angles() {
        ranger(4.5);
        context.runOnClient(mc -> {
            if (!mc.gui.hud.isHidden()) {
                mc.gui.hud.toggle();
            }
        });
        // L'orange est le premier de la ligne : x = -2.7, z = 4.5, tourné vers le nord
        run("tp @p -2.7 -59.4 2.4 0 14");
        context.waitTicks(10);
        context.takeScreenshot("20-orange-de-face");
        run("tp @p -5.0 -59.3 4.5 -90 12");
        context.waitTicks(10);
        context.takeScreenshot("21-orange-de-cote");
        run("tp @p -2.7 -56.8 4.5 0 90");
        context.waitTicks(10);
        context.takeScreenshot("22-orange-de-dessus");
        run("tp @p -2.7 -59.3 6.8 180 10");
        context.waitTicks(10);
        context.takeScreenshot("23-orange-de-dos");
        run("tp @p -4.4 -59.3 6.2 -135 10");
        context.waitTicks(10);
        context.takeScreenshot("24-orange-de-trois-quarts");
        surOrange(o -> o.taperCommande("/weather clear", false));
        run("tp @p -2.7 -59.4 2.0 0 -2");
        context.waitTicks(30);
        context.takeScreenshot("25-orange-commande-de-pres");
        context.waitTicks(40);
        context.runOnClient(mc -> mc.gui.hud.toggle());
    }

    /** L'orange tape une commande avec son bâton, puis écrit un message sur l'écran. */
    private void commandeEtMessage() {
        ranger(4.5);
        run("tp @p 0.5 -59.4 -1.2 0 6");
        run("time set 18000");
        context.waitTicks(10);
        surOrange(o -> o.taperCommande("/time set day", false));
        context.waitTicks(16);
        context.takeScreenshot("30-commande-tapee");
        context.waitTicks(14);
        context.takeScreenshot("31-commande-executee");
        context.waitTicks(10);
        LOG.info("[test] heure après la commande : {}", (Object) server.computeOnServer(s -> s.overworld().getOverworldClockTime() % 24000));

        surOrange(o -> o.ecrire(joueur(o.level().getServer()), "clic"));
        context.waitTicks(36);
        context.takeScreenshot("32-message-en-cours");
        context.waitTicks(50);
        context.takeScreenshot("33-message-ecrit");
        LOG.info("[test] message affiché : {}", (Object) context.computeOnClient(mc -> MessageHud.messageAffiche()));
        context.waitTicks(80);
    }

    /** Quelques phrases en français, pour vérifier comment elles sonnent (dans le journal du test). */
    private void phrasesFrancaises() {
        server.runOnServer(s -> {
            var hasard = net.minecraft.util.RandomSource.create(42);
            java.util.Map<String, Integer> victimes = new java.util.LinkedHashMap<>();
            victimes.put("zombie", 3);
            java.util.Map<String, Integer> minerais = new java.util.LinkedHashMap<>();
            minerais.put("deepslate_diamond_ore", 2);
            for (String evenement : List.of("arrivee", "clic", "victoire", "nuit", "danger", "diamants", "spontane", "spontane", "clic", "victoire")) {
                var c = new com.pineapple.abstickmen.entity.Paroles.Contexte(true, "Joshua", evenement, 14000, false, false, "overworld",
                        false, 64, 3f, 18, "plains", victimes, minerais, Personnage.ROUGE, Personnage.VERT, Geste.SALTO_ARRIERE,
                        Personnage.BLEU, 4, true);
                LOG.info("[test] phrase ({}) : {}", evenement, com.pineapple.abstickmen.entity.Paroles.composer(c, hasard));
            }
        });
    }

    private void combat() {
        server.runOnServer(s -> Stickman.galerie = false);
        run("tp @p 0.5 -60 0.5 0 10");
        context.waitTicks(40);
        run("summon zombie 5.5 -60 9.5");
        run("summon zombie -4.5 -60 9.5");
        run("summon skeleton 0.5 -60 11.5");
        context.waitTicks(30);
        context.takeScreenshot("40-combat");
        context.waitTicks(30);
        context.takeScreenshot("41-combat");
        context.waitTicks(200);
        int restants = server.computeOnServer(s -> compter(s, EntityTypes.ZOMBIE) + compter(s, EntityTypes.SKELETON));
        LOG.info("[test] monstres encore en vie après 13 s : {}", restants);
        context.takeScreenshot("42-apres-combat");
    }

    /** Un mur de pierre avec des minerais visibles : ils doivent les miner et rapporter le butin. */
    private void minage() {
        server.runOnServer(s -> Stickman.galerie = false);
        run("kill @e[type=item]");
        run("tp @p 0.5 -60 0.5 0 10");
        run("fill -3 -60 6 3 -57 7 stone");
        run("setblock -2 -60 6 diamond_ore");
        run("setblock 0 -59 6 iron_ore");
        run("setblock 2 -60 6 coal_ore");
        run("setblock 1 -58 6 deepslate_gold_ore");
        context.waitTicks(80);
        context.takeScreenshot("50-minage");
        context.waitTicks(300);
        context.takeScreenshot("51-minage-fini");
        int restants = server.computeOnServer(s -> {
            int n = 0;
            for (BlockPos pos : List.of(new BlockPos(-2, -60, 6), new BlockPos(0, -59, 6), new BlockPos(2, -60, 6), new BlockPos(1, -58, 6))) {
                n += s.overworld().getBlockState(pos).is(Blocks.STONE) || s.overworld().getBlockState(pos).isAir() ? 0 : 1;
            }
            return n;
        });
        int diamants = server.computeOnServer(s -> joueur(s).getInventory().countItem(Items.DIAMOND));
        int fer = server.computeOnServer(s -> joueur(s).getInventory().countItem(Items.RAW_IRON));
        LOG.info("[test] minerais restants : {} ; dans l'inventaire : {} diamant(s), {} fer brut", restants, diamants, fer);
    }

    // ------------------------------------------------------------------ outils

    /** Les cinq en ligne, à {@code z} blocs devant le joueur, tournés vers lui, sans plus rien décider. */
    private void ranger(double z) {
        server.runOnServer(s -> {
            Stickman.galerie = true;
            List<Stickman> bande = bande(s);
            bande.sort(Comparator.comparingInt(st -> st.personnage.ordinal()));
            for (int i = 0; i < bande.size(); i++) {
                Stickman st = bande.get(i);
                st.getNavigation().stop();
                st.setTarget(null);
                st.snapTo(-2.7 + i * 1.6, -60, z, 180, 0);
                st.setYBodyRot(180);
                st.setYHeadRot(180);
                st.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
                st.jouer(Geste.AUCUN, 0);
            }
        });
        context.waitTicks(10);
    }

    private void surTous(Consumer<Stickman> action) {
        server.runOnServer(s -> bande(s).forEach(action));
    }

    private void surOrange(Consumer<Stickman> action) {
        server.runOnServer(s -> bande(s).stream().filter(st -> st.personnage == Personnage.ORANGE).forEach(action));
    }

    private static List<Stickman> bande(MinecraftServer s) {
        List<Stickman> bande = new ArrayList<>();
        for (var e : s.overworld().getAllEntities()) {
            if (e instanceof Stickman st && st.isAlive()) {
                bande.add(st);
            }
        }
        return bande;
    }

    private static ServerPlayer joueur(MinecraftServer s) {
        return s.getPlayerList().getPlayers().getFirst();
    }

    private static int compter(MinecraftServer s, EntityType<?> type) {
        int n = 0;
        for (var e : s.overworld().getAllEntities()) {
            if (e.getType() == type && e.isAlive()) {
                n++;
            }
        }
        return n;
    }

    private static boolean voulu(String scene) {
        String seulement = System.getProperty("abstickmen.test.scenes", "");
        return seulement.isEmpty() || seulement.contains(scene);
    }

    private void run(String commande) {
        server.runCommand(commande);
    }
}
