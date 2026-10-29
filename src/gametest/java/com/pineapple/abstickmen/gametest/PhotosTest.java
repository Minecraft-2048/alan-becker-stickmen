package com.pineapple.abstickmen.gametest;

import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

import com.pineapple.abstickmen.entity.Geste;
import com.pineapple.abstickmen.entity.Personnage;
import com.pineapple.abstickmen.entity.Reglages;
import com.pineapple.abstickmen.entity.Stickman;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Les photos du README : un vrai monde (pas le monde plat des tests), une scène aplanie au point
 * d'apparition, le jeu en français, et des captures en 1600×900. Lancé seulement avec
 * {@code -Dabstickmen.photos=true} (voir outils/tester-dans-instance.sh … photos), de préférence
 * dans une instance avec des shaders.
 */
public class PhotosTest implements FabricClientGameTest {
    private static final Logger LOG = LoggerFactory.getLogger("Alan Becker Stickmen photos");
    private static final int LARGEUR = 1600, HAUTEUR = 900;

    private ClientGameTestContext context;
    private TestServerContext server;
    private int x0, y0, z0;

    @Override
    public void runTest(ClientGameTestContext context) {
        if (!Boolean.getBoolean("abstickmen.photos")) {
            return;
        }
        this.context = context;
        // Le jeu en français, avant d'entrer dans le monde (l'orange écrit dans la langue du joueur)
        context.runOnClient(mc -> {
            mc.options.languageCode = "fr_fr";
            mc.getLanguageManager().setSelected("fr_fr");
        });
        var rechargement = context.computeOnClient(mc -> mc.reloadResourcePacks());
        context.waitFor(mc -> rechargement.isDone(), 2400);

        try (TestSingleplayerContext solo = context.worldBuilder()
                .setUseConsistentSettings(false)
                .adjustSettings(ui -> {
                    ui.setSeed(System.getProperty("abstickmen.photos.graine", "alanbecker"));
                    ui.setAllowCommands(true);
                    ui.setGameMode(WorldCreationUiState.SelectedGameMode.SURVIVAL);
                })
                .create()) {
            server = solo.getServer();
            run("gamerule advance_time false");
            run("gamerule advance_weather false");
            run("gamerule spawn_mobs false");
            run("gamerule send_command_feedback false");
            run("gamerule show_advancement_messages false");
            run("weather clear");
            run("time set 2000");
            run("effect give @p resistance infinite 255 true");
            run("effect give @p saturation infinite 255 true");
            server.runOnServer(s -> s.getPlayerList().op(joueur(s).nameAndId()));
            solo.getConnection().waitForChunksRender();
            BlockPos pied = server.computeOnServer(s -> joueur(s).blockPosition());
            x0 = pied.getX();
            y0 = pied.getY();
            z0 = pied.getZ();
            LOG.info("[photos] scène en {} ({})", pied, (Object) server.computeOnServer(s ->
                    s.overworld().getBiome(pied).unwrapKey().map(k -> k.identifier().getPath()).orElse("?")));
            scene();
            solo.getConnection().waitForChunksRender();
            context.waitTicks(40);

            dessin();
            if (voulu("bande")) {
                bande();
            }
            if (voulu("acrobaties")) {
                acrobaties();
            }
            if (voulu("commande")) {
                commande();
            }
            if (voulu("tete")) {
                teteCreuse();
            }
            if (voulu("combat")) {
                combat();
            }
            if (voulu("arc")) {
                arc();
            }
            if (voulu("minage")) {
                minage();
            }
        }
    }

    /** -Dabstickmen.photos.scenes=arc,combat : seulement ces photos-là (toutes sinon). */
    private static boolean voulu(String scene) {
        String seulement = System.getProperty("abstickmen.photos.scenes", "");
        return seulement.isEmpty() || seulement.contains(scene);
    }

    /** Un terrain plat et dégagé devant le joueur, quelques fleurs, deux arbres au fond. */
    private void scene() {
        run(f("fill %d %d %d %d %d %d dirt", x0 - 10, y0 - 4, z0 - 6, x0 + 10, y0 - 2, z0 + 16));
        run(f("fill %d %d %d %d %d %d grass_block", x0 - 10, y0 - 1, z0 - 6, x0 + 10, y0 - 1, z0 + 16));
        run(f("fill %d %d %d %d %d %d air", x0 - 10, y0, z0 - 6, x0 + 10, y0 + 14, z0 + 16));
        String[] fleurs = {"poppy", "dandelion", "cornflower", "oxeye_daisy", "azure_bluet", "allium"};
        int[][] places = {{-7, 3}, {-6, 9}, {6, 2}, {8, 8}, {-8, 12}, {7, 13}, {-4, 14}, {4, 15}, {-9, 6}, {9, 5}, {-3, 11}, {5, 10}};
        for (int i = 0; i < places.length; i++) {
            run(f("setblock %d %d %d %s", x0 + places[i][0], y0, z0 + places[i][1], fleurs[i % fleurs.length]));
        }
        run(f("place feature fancy_oak %d %d %d", x0 - 8, y0, z0 + 15));
        run(f("place feature birch %d %d %d", x0 + 8, y0, z0 + 14));
        run("kill @e[type=!player]");
    }

    /** Le coup de crayon : les traits apparaissent l'un après l'autre. */
    private void dessin() {
        run(f("tp @p %.1f %d %.1f 0 8", x0 + 0.5, y0, z0 + 0.5));
        run("item replace entity @p weapon.mainhand with abstickmen:crayon");
        cacherHud(true);
        context.waitTicks(10);
        context.getInput().holdKeyFor(options -> options.keyUse, 2);
        context.waitTicks(15);
        photo("01-coup-de-crayon");
        context.waitTicks(30);
    }

    /** Toute la bande, face au joueur, en V derrière l'orange. */
    private void bande() {
        ranger(4.5);
        regarder(x0 + 0.5, y0, z0 + 0.4, x0 + 0.5, y0 + 1.1, z0 + 5.5);
        context.waitTicks(20);
        photo("02-la-bande");
        cacherHud(false);
        surOrange(o -> o.ecrire(joueur(o.level().getServer()), "arrivee"));
        context.waitTicks(70);
        photo("03-message");
        cacherHud(true);
        context.waitTicks(60);
    }

    /** Chacun son acrobatie, toutes prises au meilleur moment. */
    private void acrobaties() {
        ranger(5.0);
        regarder(x0 + 0.5, y0, z0 + 0.2, x0 + 0.5, y0 + 1.4, z0 + 6.0);
        context.waitTicks(10);
        Object[][] programme = {
                {Personnage.ORANGE, Geste.SALTO_ARRIERE, 13}, {Personnage.ROUGE, Geste.POIRIER, 34},
                {Personnage.BLEU, Geste.ROUE, 15}, {Personnage.VERT, Geste.POMPES, 17}, {Personnage.JAUNE, Geste.DANSE_DISCO, 5},
        };
        int instant = 40;
        for (int t = 0; t <= instant; t++) {
            for (Object[] ligne : programme) {
                if ((int) ligne[2] == instant - t) {
                    Personnage qui = (Personnage) ligne[0];
                    Geste geste = (Geste) ligne[1];
                    sur(qui, s -> s.jouer(geste));
                }
            }
            context.waitTicks(1);
        }
        photo("04-acrobaties");
        context.waitTicks(80);
    }

    /** Jaune lève son bâton au coucher du soleil et tape /time set day. */
    private void commande() {
        ranger(5.0);
        // Jaune passe devant, l'orange prend sa place au bout du V
        sur(Personnage.JAUNE, s -> placer(s, x0 + 0.5, z0 + 3.0, 180));
        sur(Personnage.ORANGE, s -> placer(s, x0 + 3.3, z0 + 6.8, 180));
        run("time set 11600");
        regarder(x0 + 2.1, y0, z0 - 0.1, x0 + 0.5, y0 + 1.5, z0 + 3.0);
        context.waitTicks(20);
        sur(Personnage.JAUNE, s -> s.taperCommande("/time set day", false));
        context.waitTicks(17);
        photo("05-commande");
        cacherHud(false);
        context.waitTicks(30);
        photo("06-commande-faite");
        cacherHud(true);
        context.waitTicks(20);
    }

    /** Gros plan sur la tête creuse de l'orange, qui fait coucou. */
    private void teteCreuse() {
        run("time set 3000");
        ranger(5.0);
        sur(Personnage.ORANGE, s -> placer(s, x0 + 0.5, z0 + 2.4, 200));
        regarder(x0 + 1.5, y0, z0 + 0.9, x0 + 0.5, y0 + 1.5, z0 + 2.4);
        context.waitTicks(10);
        sur(Personnage.ORANGE, s -> s.jouer(Geste.SALUT));
        context.waitTicks(18);
        photo("07-tete-creuse");
        context.waitTicks(30);
    }

    /** Des zombies momifiés arrivent : la bande les arrête au corps à corps. */
    private void combat() {
        run("time set 11000");
        run(f("tp @p %.1f %d %.1f 0 6", x0 + 0.5, y0, z0 + 0.5));
        // Arcs rangés le temps de la photo : sinon ils tirent de derrière la caméra et n'y apparaissent pas
        boolean arc = server.computeOnServer(s -> Reglages.get().arc);
        server.runOnServer(s -> Reglages.get().arc = false);
        server.runOnServer(s -> Stickman.galerie = false);
        context.waitTicks(40);
        for (int[] p : new int[][]{{-2, 13}, {3, 13}, {0, 15}, {5, 11}}) {
            run(f("summon husk %d %d %d", x0 + p[0], y0, z0 + p[1]));
        }
        context.waitTicks(36);
        photo("08-combat-a");
        context.waitTicks(12);
        photo("08-combat-b");
        context.waitTicks(12);
        photo("08-combat-c");
        context.waitTicks(12);
        photo("08-combat-d");
        context.waitTicks(200);
        server.runOnServer(s -> Reglages.get().arc = arc);
    }

    /** Des zombies momifiés arrivent de loin : la bande les accueille à l'arc (vue de trois quarts arrière). */
    private void arc() {
        run("kill @e[type=husk]");
        run("time set 4500");
        run(f("tp @p %.1f %d %.1f 0 6", x0 + 0.5, y0, z0 + 0.5));
        server.runOnServer(s -> Stickman.galerie = false);
        context.waitTicks(60);
        for (int[] p : new int[][]{{-3, 15}, {2, 16}, {5, 14}}) {
            run(f("summon husk %d %d %d", x0 + p[0], y0, z0 + p[1]));
        }
        context.waitTicks(16);
        regarder(x0 + 3.6, y0, z0 - 3.6, x0 + 0.3, y0 + 1.3, z0 + 5.0);
        for (char n = 'a'; n <= 'l'; n++) {
            context.waitTicks(4);
            photo("10-arc-" + n);
        }
        context.waitTicks(200);
    }

    /** Une grande grotte éclairée aux torches, des piliers de minerais au milieu : ils les minent. */
    private void minage() {
        int y = y0 - 18;
        run(f("fill %d %d %d %d %d %d stone", x0 - 8, y - 2, z0, x0 + 8, y + 6, z0 + 16));
        run(f("fill %d %d %d %d %d %d air", x0 - 6, y, z0 + 2, x0 + 6, y + 4, z0 + 14));
        // Trois piliers de pierre, des minerais dedans, visibles de tous les côtés
        int[][] piliers = {{-3, 7}, {2, 9}, {-1, 11}, {4, 6}, {-5, 10}};
        for (int[] p : piliers) {
            run(f("fill %d %d %d %d %d %d stone", x0 + p[0], y, z0 + p[1], x0 + p[0], y + 4, z0 + p[1]));
        }
        run(f("setblock %d %d %d diamond_ore", x0 - 3, y + 1, z0 + 7));
        run(f("setblock %d %d %d gold_ore", x0 - 3, y + 3, z0 + 7));
        run(f("setblock %d %d %d iron_ore", x0 + 2, y + 1, z0 + 9));
        run(f("setblock %d %d %d redstone_ore", x0 + 2, y + 3, z0 + 9));
        run(f("setblock %d %d %d lapis_ore", x0 - 1, y + 2, z0 + 11));
        run(f("setblock %d %d %d coal_ore", x0 - 1, y, z0 + 11));
        run(f("setblock %d %d %d copper_ore", x0 + 4, y + 1, z0 + 6));
        run(f("setblock %d %d %d emerald_ore", x0 + 4, y + 3, z0 + 6));
        run(f("setblock %d %d %d diamond_ore", x0 - 5, y + 2, z0 + 10));
        run(f("setblock %d %d %d iron_ore", x0 - 5, y, z0 + 10));
        run(f("setblock %d %d %d gold_ore", x0 + 2, y + 2, z0 + 9));
        run(f("setblock %d %d %d iron_ore", x0 - 3, y + 2, z0 + 7));
        for (int[] l : new int[][]{{-4, 4}, {3, 12}, {0, 7}, {-2, 13}}) {
            run(f("setblock %d %d %d lantern", x0 + l[0], y, z0 + l[1]));
        }
        run(f("setblock %d %d %d wall_torch[facing=east]", x0 - 6, y + 2, z0 + 5));
        run(f("setblock %d %d %d wall_torch[facing=west]", x0 + 6, y + 2, z0 + 8));
        run(f("setblock %d %d %d wall_torch[facing=north]", x0 - 2, y + 2, z0 + 14));
        run(f("setblock %d %d %d torch", x0 + 4, y, z0 + 12));
        run(f("setblock %d %d %d torch", x0 - 5, y, z0 + 11));
        run(f("tp @p %.1f %d %.1f 0 10", x0 + 0.5, y, z0 + 4.0));
        server.runOnServer(s -> {
            int i = 0;
            for (Stickman st : bande(s)) {
                st.getNavigation().stop();
                st.setTarget(null);
                st.snapTo(x0 - 3.0 + i * 1.5, y, z0 + 13.0, 180, 0);
                i++;
            }
        });
        context.waitTicks(30);
        photo("09-minage-a");
        context.waitTicks(15);
        photo("09-minage-b");
        context.waitTicks(15);
        photo("09-minage-c");
        context.waitTicks(15);
        photo("09-minage-d");
        context.waitTicks(20);
    }

    // ------------------------------------------------------------------ outils

    private void photo(String nom) {
        context.takeScreenshot(TestScreenshotOptions.of(nom).withSize(LARGEUR, HAUTEUR));
        LOG.info("[photos] {}", nom);
    }

    private void cacherHud(boolean cacher) {
        context.runOnClient(mc -> {
            if (mc.gui.hud.isHidden() != cacher) {
                mc.gui.hud.toggle();
            }
        });
    }

    /** Place le joueur en (x, y, z) et tourne sa tête vers le point (cx, cy, cz). */
    private void regarder(double x, double y, double z, double cx, double cy, double cz) {
        double dx = cx - x, dy = cy - (y + 1.62), dz = cz - z;
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
        run(f("tp @p %.2f %.2f %.2f %.1f %.1f", x, y, z, yaw, pitch));
    }

    /** L'orange devant, les autres en V derrière lui, tous tournés vers le nord (vers le joueur). */
    private void ranger(double z) {
        server.runOnServer(s -> {
            Stickman.galerie = true;
            double[][] v = {{0.5, 0}, {-0.9, 0.9}, {1.9, 0.9}, {-2.3, 1.8}, {3.3, 1.8}};
            for (Stickman st : bande(s)) {
                double[] p = v[st.personnage.ordinal()];
                placer(st, x0 + p[0], z0 + z + p[1], 180);
                st.jouer(Geste.AUCUN, 0);
            }
        });
        context.waitTicks(5);
    }

    private void placer(Stickman st, double x, double z, float yaw) {
        st.getNavigation().stop();
        st.setTarget(null);
        st.snapTo(x, y0, z, yaw, 0);
        st.setYBodyRot(yaw);
        st.setYHeadRot(yaw);
        st.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
    }

    private void sur(Personnage qui, Consumer<Stickman> action) {
        server.runOnServer(s -> bande(s).stream().filter(st -> st.personnage == qui).forEach(action));
    }

    private void surOrange(Consumer<Stickman> action) {
        sur(Personnage.ORANGE, action);
    }

    private static List<Stickman> bande(MinecraftServer s) {
        List<Stickman> bande = new java.util.ArrayList<>();
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

    private static String f(String format, Object... args) {
        return String.format(Locale.ROOT, format, args);
    }

    private void run(String commande) {
        server.runCommand(commande);
    }
}
