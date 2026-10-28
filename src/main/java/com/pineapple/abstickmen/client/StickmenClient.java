package com.pineapple.abstickmen.client;

import com.pineapple.abstickmen.AlanBeckerStickmen;
import com.pineapple.abstickmen.entity.Personnage;
import com.pineapple.abstickmen.reseau.MessageEcran;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;

public final class StickmenClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        for (Personnage p : Personnage.values()) {
            EntityRendererRegistry.register(AlanBeckerStickmen.type(p), StickmanRenderer::new);
        }
        HudElementRegistry.addLast(AlanBeckerStickmen.id("message_ecran"), new MessageHud());
        ClientPlayNetworking.registerGlobalReceiver(MessageEcran.TYPE, (message, contexte) -> MessageHud.afficher(message));
    }
}
