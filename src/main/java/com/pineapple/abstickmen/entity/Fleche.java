package com.pineapple.abstickmen.entity;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.level.Level;

/**
 * La flèche d'un stickman : une flèche ordinaire qui ne touche que les monstres (et la cible de
 * l'archer). Elle traverse le joueur, les stickmen, les animaux et les villageois. On ne peut pas la
 * ramasser, et elle disparaît quelques secondes après s'être plantée.
 */
public class Fleche extends Arrow {
    public Fleche(EntityType<? extends Fleche> type, Level level) {
        super(type, level);
    }

    @Override
    protected boolean canHitEntity(Entity cible) {
        boolean visee = cible instanceof Enemy || getOwner() instanceof Mob archer && archer.getTarget() == cible;
        return visee && !(cible instanceof Stickman) && super.canHitEntity(cible);
    }

    @Override
    protected void tickDespawn() {
        super.tickDespawn();
        if (inGroundTime > 100) {
            discard();
        }
    }
}
