package com.aryangpt007.journeymode.network.packets;

import com.aryangpt007.journeymode.JourneyMode;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Packet sent from client to server to sync active tab state on Fabric
 */
public record SyncTabPacket(boolean inJourneyTab) implements CustomPacketPayload {
    public static final Type<SyncTabPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(JourneyMode.MODID, "sync_tab"));

    public static final StreamCodec<ByteBuf, SyncTabPacket> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.BOOL,
        SyncTabPacket::inJourneyTab,
        SyncTabPacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
