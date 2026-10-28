package com.pineapple.abstickmen.entity;

import java.util.List;
import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * Ce que l'orange tape avec son bâton de commande, et quand. Il ne le fait que si le joueur a
 * lui-même le droit de taper des commandes (monde avec les commandes activées, ou joueur opérateur),
 * et seulement quand ça sert : le joueur tombe de haut, brûle, se noie, n'a plus de cœurs, il fait
 * nuit et les monstres approchent… Chaque sorte de commande a son temps de recharge.
 */
public final class Commandes {
    /**
     * @param cle      sorte de commande (pour son temps de recharge)
     * @param texte    ce qu'il tape, tel quel
     * @param urgent   tapé très vite (le joueur est en train de tomber, de brûler…)
     */
    public record Choix(String cle, String texte, boolean urgent) {
    }

    private Commandes() {
    }

    /** Les commandes sont-elles activées pour ce joueur ? */
    public static boolean permises(ServerPlayer joueur) {
        return Reglages.get().commandes && joueur.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);
    }

    public static @Nullable Choix choisir(Stickman orange, ServerPlayer p, ServerLevel niveau, Map<String, Long> dernieres,
                                          long derniereQuelconque, long t) {
        String nom = p.getName().getString();
        boolean survie = !p.isCreative() && !p.isSpectator();
        if (p.level() != niveau || orange.distanceToSqr(p) > 32 * 32) {
            return null;
        }

        // Les urgences d'abord : tapées en un éclair
        if (survie) {
            if (p.fallDistance > 7 && p.getDeltaMovement().y < -0.5 && !p.isFallFlying() && !p.hasEffect(MobEffects.SLOW_FALLING)
                    && pret(dernieres, "chute", 200, t)) {
                return new Choix("chute", "/effect give " + nom + " slow_falling 5 0", true);
            }
            if ((p.isOnFire() || p.isInLava()) && !p.hasEffect(MobEffects.FIRE_RESISTANCE) && pret(dernieres, "feu", 600, t)) {
                return new Choix("feu", "/effect give " + nom + " fire_resistance 30 0", true);
            }
            if (p.getAirSupply() < p.getMaxAirSupply() / 3 && !p.hasEffect(MobEffects.WATER_BREATHING) && pret(dernieres, "eau", 600, t)) {
                return new Choix("eau", "/effect give " + nom + " water_breathing 45 0", true);
            }
            if (p.getHealth() <= 8 && !p.hasEffect(MobEffects.REGENERATION) && pret(dernieres, "soin", 900, t)) {
                return new Choix("soin", "/effect give " + nom + " regeneration 10 1", true);
            }
        }

        // Le reste : au plus une commande toutes les 40 secondes, et pas en plein milieu d'un geste
        if (t - derniereQuelconque < 800 || orange.gesteServeur() != Geste.AUCUN) {
            return null;
        }
        BlockPos pos = p.blockPosition();
        boolean dehors = niveau.canSeeSky(pos);
        List<Mob> monstres = niveau.getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(16),
                m -> m.isAlive() && m instanceof Enemy && (m.getTarget() == p || m.getTarget() instanceof Stickman));
        long morts = monstres.stream().filter(m -> m.getType().builtInRegistryHolder().is(EntityTypeTags.UNDEAD)).count();
        long creepers = niveau.getEntitiesOfClass(Creeper.class, p.getBoundingBox().inflate(10), Creeper::isAlive).size();

        if (survie && creepers > 0 && pret(dernieres, "creeper", 600, t)) {
            // Un creeper trop près : il s'envole loin du joueur et retombe
            return new Choix("creeper", "/effect give @e[type=creeper,distance=..14] levitation 4 1", false);
        }
        if (survie && morts >= 3 && pret(dernieres, "morts", 900, t)) {
            // L'astuce de l'orange : la potion de soin blesse les morts-vivants
            return new Choix("morts", "/effect give @e[type=#undead,distance=..16] instant_health 1 1", false);
        }
        if (niveau.dimension() == Level.OVERWORLD && dehors && niveau.isDarkOutside() && (monstres.size() >= 2 || p.getHealth() < 12)
                && pret(dernieres, "jour", 6000, t)) {
            return new Choix("jour", "/time set day", false);
        }
        if (niveau.isThundering() && pret(dernieres, "meteo", 6000, t)) {
            return new Choix("meteo", "/weather clear", false);
        }
        if (survie && p.getFoodData().getFoodLevel() <= 6 && pret(dernieres, "faim", 2400, t)) {
            return new Choix("faim", "/effect give " + nom + " saturation 1 0", false);
        }
        if (survie && !dehors && niveau.getMaxLocalRawBrightness(pos) <= 4 && pos.getY() < 50) {
            if (!p.getInventory().contains(s -> s.is(Items.TORCH)) && pret(dernieres, "torches", 6000, t)) {
                return new Choix("torches", "/give " + nom + " torch 16", false);
            }
            if (!p.hasEffect(MobEffects.NIGHT_VISION) && pret(dernieres, "vision", 3600, t)) {
                return new Choix("vision", "/effect give " + nom + " night_vision 90 0", false);
            }
        }
        if (survie && Souvenirs.de(p.getUUID()).blocsRecents(t) >= 14 && !p.hasEffect(MobEffects.HASTE)
                && pret(dernieres, "hate", 3600, t)) {
            return new Choix("hate", "/effect give " + nom + " haste 60 1", false);
        }
        if (niveau.isRaining() && dehors && pret(dernieres, "pluie", 12000, t) && orange.getRandom().nextInt(30) == 0) {
            return new Choix("pluie", "/weather clear", false);
        }
        // Pour le plaisir, de temps en temps, quand tout est calme
        if (monstres.isEmpty() && pret(dernieres, "cadeau", 9000, t) && orange.getRandom().nextInt(400) == 0) {
            return switch (orange.getRandom().nextInt(3)) {
                case 0 -> new Choix("cadeau", "/give " + nom + " cookie 3", false);
                case 1 -> new Choix("cadeau", String.format(java.util.Locale.ROOT,
                        "/particle totem_of_undying %d %d %d 0.6 0.8 0.6 0.4 80", p.getBlockX(), p.getBlockY() + 2, p.getBlockZ()), false);
                default -> new Choix("cadeau", "/give " + nom + " cake", false);
            };
        }
        return null;
    }

    private static boolean pret(Map<String, Long> dernieres, String cle, int recharge, long t) {
        Long derniere = dernieres.get(cle);
        return derniere == null || t - derniere >= recharge;
    }
}
