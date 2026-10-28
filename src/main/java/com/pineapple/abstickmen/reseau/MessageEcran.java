package com.pineapple.abstickmen.reseau;

import com.pineapple.abstickmen.AlanBeckerStickmen;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Le serveur demande au client d'afficher un message que l'orange écrit sur l'écran, lettre après lettre. */
public record MessageEcran(int entite, String texte, int couleur) implements CustomPacketPayload {
    public static final Type<MessageEcran> TYPE = new Type<>(AlanBeckerStickmen.id("message_ecran"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MessageEcran> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, MessageEcran::entite,
            ByteBufCodecs.STRING_UTF8, MessageEcran::texte,
            ByteBufCodecs.INT, MessageEcran::couleur,
            MessageEcran::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
