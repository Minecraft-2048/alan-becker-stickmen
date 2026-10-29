package com.pineapple.abstickmen;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.Function;

import com.pineapple.abstickmen.entity.Fleche;
import com.pineapple.abstickmen.entity.Personnage;
import com.pineapple.abstickmen.entity.Reglages;
import com.pineapple.abstickmen.entity.Souvenirs;
import com.pineapple.abstickmen.entity.Stickman;
import com.pineapple.abstickmen.item.CrayonItem;
import com.pineapple.abstickmen.reseau.MessageEcran;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.SpawnEggItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Les stickmen d'Alan Becker dans Minecraft (projet de fan, non officiel) : Orange, Rouge, Bleu,
 * Vert et Jaune suivent le joueur, l'aident à se battre et à miner. Orange a la tête creuse et écrit
 * des messages sur l'écran ; Jaune a son bâton de commande, comme dans la série.
 */
public final class AlanBeckerStickmen implements ModInitializer {
    public static final String MOD_ID = "abstickmen";
    public static final Logger LOG = LoggerFactory.getLogger("Alan Becker Stickmen");

    public static Item CRAYON;
    public static EntityType<Fleche> FLECHE;
    private static final Map<Personnage, EntityType<Stickman>> TYPES = new EnumMap<>(Personnage.class);

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }

    public static EntityType<Stickman> type(Personnage personnage) {
        return TYPES.get(personnage);
    }

    @Override
    public void onInitialize() {
        for (Personnage p : Personnage.values()) {
            ResourceKey<EntityType<?>> cle = ResourceKey.create(Registries.ENTITY_TYPE, id(p.id));
            EntityType<Stickman> type = Registry.register(BuiltInRegistries.ENTITY_TYPE, cle,
                    EntityType.Builder.<Stickman>of(Stickman::new, MobCategory.MISC)
                            .sized(0.6f, 1.8f)
                            .eyeHeight(1.55f)
                            .clientTrackingRange(10)
                            .build(cle));
            Stickman.enregistrer(type, p);
            FabricDefaultAttributeRegistry.register(type, Stickman.attributs());
            TYPES.put(p, type);
        }
        // Leurs flèches : réglées comme celles du jeu
        ResourceKey<EntityType<?>> cleFleche = ResourceKey.create(Registries.ENTITY_TYPE, id("fleche"));
        FLECHE = Registry.register(BuiltInRegistries.ENTITY_TYPE, cleFleche,
                EntityType.Builder.<Fleche>of(Fleche::new, MobCategory.MISC)
                        .noLootTable()
                        .sized(0.5f, 0.5f)
                        .eyeHeight(0.13f)
                        .clientTrackingRange(4)
                        .updateInterval(20)
                        .build(cleFleche));

        CRAYON = enregistrerObjet("crayon", CrayonItem::new, new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON));
        for (Personnage p : Personnage.values()) {
            Item oeuf = enregistrerObjet(p.id + "_spawn_egg", SpawnEggItem::new, new Item.Properties().spawnEgg(TYPES.get(p)));
            CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.SPAWN_EGGS).register(sortie -> sortie.accept(oeuf));
        }
        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register(sortie -> sortie.accept(CRAYON));

        PayloadTypeRegistry.clientboundPlay().register(MessageEcran.TYPE, MessageEcran.CODEC);

        ServerLifecycleEvents.SERVER_STARTING.register(serveur -> Reglages.charger());
        ServerLifecycleEvents.SERVER_STOPPED.register(serveur -> Souvenirs.oublierTout());
        // Jaune remarque quand le joueur mine beaucoup (il lui tape alors /effect … haste)
        PlayerBlockBreakEvents.AFTER.register((niveau, joueur, pos, etat, entite) ->
                Souvenirs.de(joueur.getUUID()).blocCasseParLeJoueur(niveau.getGameTime()));
    }

    private static Item enregistrerObjet(String nom, Function<Item.Properties, Item> fabrique, Item.Properties proprietes) {
        ResourceKey<Item> cle = ResourceKey.create(Registries.ITEM, id(nom));
        return Registry.register(BuiltInRegistries.ITEM, cle, fabrique.apply(proprietes.setId(cle)));
    }
}
