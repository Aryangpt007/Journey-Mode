package com.aryangpt007.journeymode.network.packets;

import com.aryangpt007.journeymode.JourneyMode;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Packet to open the Journey Mode menu on Fabric
 */
public record OpenJourneyMenuPacket() implements CustomPacketPayload {
    public static final Type<OpenJourneyMenuPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(JourneyMode.MODID, "open_journey_menu"));

    public static final StreamCodec<ByteBuf, OpenJourneyMenuPacket> STREAM_CODEC = StreamCodec.unit(new OpenJourneyMenuPacket());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
