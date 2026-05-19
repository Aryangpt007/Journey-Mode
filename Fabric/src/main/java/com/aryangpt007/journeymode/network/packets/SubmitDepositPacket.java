package com.aryangpt007.journeymode.network.packets;

import com.aryangpt007.journeymode.JourneyMode;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Packet to submit items in the deposit slot on Fabric
 */
public record SubmitDepositPacket() implements CustomPacketPayload {
    public static final Type<SubmitDepositPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(JourneyMode.MODID, "submit_deposit"));

    public static final StreamCodec<ByteBuf, SubmitDepositPacket> STREAM_CODEC = StreamCodec.unit(new SubmitDepositPacket());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
