package com.pineapple.abstickmen.item;

import java.util.ArrayList;
import java.util.List;

import com.pineapple.abstickmen.AlanBeckerStickmen;
import com.pineapple.abstickmen.entity.Personnage;
import com.pineapple.abstickmen.entity.Stickman;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Le crayon d'animateur. Clic droit : il dessine devant toi les stickmen qui manquent à ta bande
 * (ou rappelle ceux qui sont loin). Accroupi + clic droit : le bout gomme, toute la bande s'efface.
 */
public class CrayonItem extends Item {
    public CrayonItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player joueur, InteractionHand main) {
        if (!(level instanceof ServerLevel niveau) || !(joueur instanceof ServerPlayer sp)) {
            return InteractionResult.SUCCESS;
        }
        List<Stickman> bande = new ArrayList<>();
        for (ServerLevel monde : niveau.getServer().getAllLevels()) {
            bande.addAll(Stickman.bande(monde, sp.getUUID()));
        }
        if (joueur.isSecondaryUseActive()) {
            if (bande.isEmpty()) {
                sp.sendOverlayMessage(Component.translatable("message.abstickmen.personne"));
            } else {
                bande.forEach(Stickman::effacer);
                sp.sendOverlayMessage(Component.translatable("message.abstickmen.gomme"));
                niveau.playSound(null, joueur.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1f, 0.7f);
            }
        } else {
            int dessines = 0;
            for (Personnage p : Personnage.values()) {
                if (bande.stream().noneMatch(s -> s.personnage == p) && dessinerDevant(niveau, sp, p)) {
                    dessines++;
                }
            }
            if (dessines > 0) {
                sp.sendOverlayMessage(Component.translatable("message.abstickmen.dessin"));
                niveau.playSound(null, joueur.blockPosition(), SoundEvents.UI_CARTOGRAPHY_TABLE_TAKE_RESULT, SoundSource.PLAYERS, 1f, 1f);
            } else {
                // Toute la bande existe déjà : ceux qui sont loin reviennent
                for (Stickman s : bande) {
                    if (s.level() == niveau && s.distanceToSqr(joueur) > 8 * 8) {
                        s.rejoindre(sp);
                    }
                    if (s.estAssis()) {
                        s.asseoir(false);
                    }
                }
                sp.sendOverlayMessage(Component.translatable("message.abstickmen.deja"));
            }
        }
        joueur.getCooldowns().addCooldown(joueur.getItemInHand(main), 30);
        return InteractionResult.SUCCESS;
    }

    /** Dessine un stickman devant le joueur, face à lui, les cinq en arc de cercle. */
    private static boolean dessinerDevant(ServerLevel niveau, ServerPlayer joueur, Personnage personnage) {
        float yaw = joueur.getYRot();
        double a = Math.toRadians(yaw + (personnage.ordinal() - 2) * 22);
        Vec3 voulu = joueur.position().add(-Math.sin(a) * 3.2, 0, Math.cos(a) * 3.2);
        BlockPos pos = BlockPos.containing(voulu);
        BlockPos place = null;
        for (int dy = 2; dy >= -3 && place == null; dy--) {
            BlockPos essai = pos.above(dy);
            if (niveau.getBlockState(essai).getCollisionShape(niveau, essai).isEmpty()
                    && niveau.getBlockState(essai.above()).getCollisionShape(niveau, essai.above()).isEmpty()
                    && !niveau.getBlockState(essai.below()).getCollisionShape(niveau, essai.below()).isEmpty()) {
                place = essai;
            }
        }
        if (place == null) {
            place = Stickman.placeSure(niveau, joueur.blockPosition());
            if (place == null) {
                return false;
            }
            voulu = Vec3.atBottomCenterOf(place);
        }
        Stickman s = AlanBeckerStickmen.type(personnage).create(niveau, EntitySpawnReason.MOB_SUMMONED);
        if (s == null) {
            return false;
        }
        float face = yaw + 180;
        s.snapTo(voulu.x, place.getY(), voulu.z, face, 0);
        s.setYHeadRot(face);
        s.setYBodyRot(face);
        s.adopterPar(joueur);
        s.dessiner();
        niveau.addFreshEntity(s);
        return true;
    }
}
