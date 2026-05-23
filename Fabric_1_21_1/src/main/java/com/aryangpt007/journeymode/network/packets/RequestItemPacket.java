package com.aryangpt007.journeymode.network.packets;

import com.aryangpt007.journeymode.JourneyMode;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Packet sent when player requests an item from Journey Mode
 */
public record RequestItemPacket(String itemId, int count) implements CustomPacketPayload {
    public static final Type<RequestItemPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(JourneyMode.MODID, "request_item"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RequestItemPacket> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.STRING_UTF8,
        RequestItemPacket::itemId,
        ByteBufCodecs.VAR_INT,
        RequestItemPacket::count,
        RequestItemPacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
