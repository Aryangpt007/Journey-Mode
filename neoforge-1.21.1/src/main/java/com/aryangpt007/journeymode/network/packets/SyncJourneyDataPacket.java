package com.aryangpt007.journeymode.network.packets;

import com.aryangpt007.journeymode.core.JourneyData;
import com.aryangpt007.journeymode.platform.neoforge.NeoForgeDataHandler;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.*;

/**
 * Packet to sync Journey Mode data from server to client
 */
public record SyncJourneyDataPacket(Map<String, Integer> collectedCounts, Set<String> unlockedItems, Map<String, Long> unlockTimestamps) implements CustomPacketPayload {
    public static final Type<SyncJourneyDataPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("journeymode", "sync_journey_data"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SyncJourneyDataPacket> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.map(HashMap::new, ByteBufCodecs.STRING_UTF8, ByteBufCodecs.VAR_INT),
        SyncJourneyDataPacket::collectedCounts,
        ByteBufCodecs.collection(HashSet::new, ByteBufCodecs.STRING_UTF8),
        packet -> packet.unlockedItems,
        ByteBufCodecs.map(HashMap::new, ByteBufCodecs.STRING_UTF8, ByteBufCodecs.VAR_LONG),
        SyncJourneyDataPacket::unlockTimestamps,
        SyncJourneyDataPacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SyncJourneyDataPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player().level().isClientSide) {
                // Update the client-side data with server data
                JourneyData journeyData = NeoForgeDataHandler.getJourneyData(context.player());
                journeyData.updateFromSync(packet.collectedCounts, packet.unlockedItems, packet.unlockTimestamps);
                // Save the updated data
                NeoForgeDataHandler.saveJourneyData(context.player(), journeyData);
            }
        });
    }
}
