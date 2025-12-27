package com.aryangpt007.journeymode.fabric.network.packets;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public record SyncJourneyDataPacket(
        Map<String, Integer> collectedCounts,
        Set<String> unlockedItems,
        Map<String, Long> unlockTimestamps
) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<SyncJourneyDataPacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("journeymode", "sync_journey_data"));

    public static final StreamCodec<FriendlyByteBuf, SyncJourneyDataPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.map(HashMap::new, ByteBufCodecs.STRING_UTF8, ByteBufCodecs.VAR_INT),
            SyncJourneyDataPacket::collectedCounts,
            ByteBufCodecs.collection(HashSet::new, ByteBufCodecs.STRING_UTF8),
            SyncJourneyDataPacket::unlockedItems,
            ByteBufCodecs.map(HashMap::new, ByteBufCodecs.STRING_UTF8, ByteBufCodecs.VAR_LONG),
            SyncJourneyDataPacket::unlockTimestamps,
            SyncJourneyDataPacket::new
    );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
