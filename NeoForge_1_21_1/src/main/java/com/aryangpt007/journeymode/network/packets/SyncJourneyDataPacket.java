package com.aryangpt007.journeymode.network.packets;

import com.aryangpt007.journeymode.JourneyMode;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.*;

/**
 * Packet to sync Journey Mode data from server to client.
 *
 * Also carries `enabled`/`showTooltips` (§3): without these, the client's own data-attachment
 * instance (a separate object from the server's, even in singleplayer) never reflected
 * /journeymode off or a tooltip preference change for client-only checks.
 *
 * `teamDisplayName` (§1) is the 6th field on this composite codec - StreamCodec.composite's
 * ceiling is 6 codec/getter pairs (see ConfigSyncPacket's javadoc, which needed the hand-rolled
 * StreamCodec.of form once it grew past that), so this still fits without a rewrite. Empty
 * string means "not on a team" (StreamCodec has no native null-String support here).
 */
public record SyncJourneyDataPacket(Map<String, Integer> collectedCounts, Set<String> unlockedItems, Map<String, Long> unlockTimestamps, boolean enabled, boolean showTooltips, String teamDisplayName) implements CustomPacketPayload {
    public static final Type<SyncJourneyDataPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(JourneyMode.MODID, "sync_journey_data"));

    public SyncJourneyDataPacket {
        teamDisplayName = teamDisplayName == null ? "" : teamDisplayName;
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, SyncJourneyDataPacket> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.map(HashMap::new, ByteBufCodecs.STRING_UTF8, ByteBufCodecs.VAR_INT),
        SyncJourneyDataPacket::collectedCounts,
        ByteBufCodecs.collection(HashSet::new, ByteBufCodecs.STRING_UTF8),
        packet -> packet.unlockedItems,
        ByteBufCodecs.map(HashMap::new, ByteBufCodecs.STRING_UTF8, ByteBufCodecs.VAR_LONG),
        SyncJourneyDataPacket::unlockTimestamps,
        ByteBufCodecs.BOOL,
        SyncJourneyDataPacket::enabled,
        ByteBufCodecs.BOOL,
        SyncJourneyDataPacket::showTooltips,
        ByteBufCodecs.STRING_UTF8,
        SyncJourneyDataPacket::teamDisplayName,
        SyncJourneyDataPacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SyncJourneyDataPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player().level().isClientSide) {
                var journeyData = context.player().getData(JourneyMode.JOURNEY_DATA);
                // Update the client-side data with server data
                journeyData.updateFromSync(packet.collectedCounts, packet.unlockedItems, packet.unlockTimestamps, packet.enabled, packet.showTooltips, packet.teamDisplayName);
                // §11 Visual Polish: unlock sound + action-bar message on threshold crossing.
                com.aryangpt007.journeymode.client.ClientSetup.celebrateNewUnlocks(journeyData.getAndClearNewlyUnlocked());
            }
        });
    }
}
