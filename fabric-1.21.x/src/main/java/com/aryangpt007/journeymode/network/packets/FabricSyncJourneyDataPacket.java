package com.aryangpt007.journeymode.network.packets;

import com.aryangpt007.journeymode.JourneyModeFabric;
import com.aryangpt007.journeymode.core.JourneyData;
import com.aryangpt007.journeymode.platform.fabric.FabricDataHandler;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.*;

/**
 * Packet to sync Journey Mode data from server to client
 */
public record FabricSyncJourneyDataPacket(
    Map<String, Integer> collectedCounts,
    Set<String> unlockedItems,
    Map<String, Long> unlockTimestamps
) implements CustomPacketPayload {
    
    public static final CustomPacketPayload.Type<FabricSyncJourneyDataPacket> TYPE = 
        new CustomPacketPayload.Type<>(JourneyModeFabric.id("sync_journey_data"));

    public static final StreamCodec<FriendlyByteBuf, FabricSyncJourneyDataPacket> CODEC = 
        StreamCodec.composite(
            ByteBufCodecs.map(HashMap::new, ByteBufCodecs.STRING_UTF8, ByteBufCodecs.VAR_INT),
            FabricSyncJourneyDataPacket::collectedCounts,
            ByteBufCodecs.collection(HashSet::new, ByteBufCodecs.STRING_UTF8),
            FabricSyncJourneyDataPacket::unlockedItems,
            ByteBufCodecs.map(HashMap::new, ByteBufCodecs.STRING_UTF8, ByteBufCodecs.VAR_LONG),
            FabricSyncJourneyDataPacket::unlockTimestamps,
            FabricSyncJourneyDataPacket::new
        );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void registerHandler() {
        ClientPlayNetworking.registerGlobalReceiver(TYPE, (packet, context) -> {
            context.client().execute(() -> {
                if (context.player() != null) {
                    // Update the client-side data with server data
                    JourneyData journeyData = FabricDataHandler.getJourneyData(context.player());
                    journeyData.updateFromSync(packet.collectedCounts, packet.unlockedItems, packet.unlockTimestamps);
                    // Save the updated data
                    FabricDataHandler.saveJourneyData(context.player(), journeyData);
                }
            });
        });
    }
}
