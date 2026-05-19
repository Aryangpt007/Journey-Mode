package com.aryangpt007.journeymode.network.packets;

import com.aryangpt007.journeymode.JourneyMode;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Packet sent from client to server to delete the currently carried (cursor) item stack on Fabric
 */
public record DeleteCarriedPacket() implements CustomPacketPayload {
    public static final Type<DeleteCarriedPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(JourneyMode.MODID, "delete_carried"));

    public static final StreamCodec<ByteBuf, DeleteCarriedPacket> STREAM_CODEC = StreamCodec.unit(new DeleteCarriedPacket());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
