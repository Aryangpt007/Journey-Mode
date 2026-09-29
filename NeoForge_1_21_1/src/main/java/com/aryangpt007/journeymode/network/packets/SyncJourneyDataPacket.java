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
public record SyncJourneyDataPacket(Map<String, Integer> collectedCounts, Set<String> unlockedItems, Map<String, Long> unlockTimestamps, boolean enabled, boolean showTooltips, String teamDisplayName, boolean delta) implements CustomPacketPayload {
    public static final Type<SyncJourneyDataPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(JourneyMode.MODID, "sync_journey_data"));

    public SyncJourneyDataPacket {
        teamDisplayName = teamDisplayName == null ? "" : teamDisplayName;
    }

    /**
     * True when this payload carries only the entries that changed, to be merged into the
     * client's existing catalog rather than replacing it. A deposit changes one entry; without
     * this the server re-sent the whole catalog to say so. Full syncs (login, reset, purge,
     * revoke, team join/leave) keep delta=false, because a delta cannot express a removal.
     */
    public SyncJourneyDataPacket(Map<String, Integer> collectedCounts, Set<String> unlockedItems, Map<String, Long> unlockTimestamps, boolean enabled, boolean showTooltips, String teamDisplayName) {
        this(collectedCounts, unlockedItems, unlockTimestamps, enabled, showTooltips, teamDisplayName, false);
    }

    // Converted from StreamCodec.composite to the hand-rolled form: composite tops out at six
    // codec/getter pairs and `delta` is the seventh field. ConfigSyncPacket already had to make
    // the same move for the same reason.
    private static final StreamCodec<RegistryFriendlyByteBuf, Map<String, Integer>> COUNTS_CODEC =
        ByteBufCodecs.map(HashMap::new, ByteBufCodecs.STRING_UTF8, ByteBufCodecs.VAR_INT);
    private static final StreamCodec<RegistryFriendlyByteBuf, Set<String>> UNLOCKED_CODEC =
        ByteBufCodecs.collection(HashSet::new, ByteBufCodecs.STRING_UTF8);
    private static final StreamCodec<RegistryFriendlyByteBuf, Map<String, Long>> TIMESTAMPS_CODEC =
        ByteBufCodecs.map(HashMap::new, ByteBufCodecs.STRING_UTF8, ByteBufCodecs.VAR_LONG);

    public static final StreamCodec<RegistryFriendlyByteBuf, SyncJourneyDataPacket> STREAM_CODEC = StreamCodec.of(
        SyncJourneyDataPacket::encode,
        SyncJourneyDataPacket::decode
    );

    private static void encode(RegistryFriendlyByteBuf buf, SyncJourneyDataPacket packet) {
        COUNTS_CODEC.encode(buf, packet.collectedCounts);
        UNLOCKED_CODEC.encode(buf, packet.unlockedItems);
        TIMESTAMPS_CODEC.encode(buf, packet.unlockTimestamps);
        ByteBufCodecs.BOOL.encode(buf, packet.enabled);
        ByteBufCodecs.BOOL.encode(buf, packet.showTooltips);
        ByteBufCodecs.STRING_UTF8.encode(buf, packet.teamDisplayName);
        ByteBufCodecs.BOOL.encode(buf, packet.delta);
    }

    private static SyncJourneyDataPacket decode(RegistryFriendlyByteBuf buf) {
        Map<String, Integer> collectedCounts = COUNTS_CODEC.decode(buf);
        Set<String> unlockedItems = UNLOCKED_CODEC.decode(buf);
        Map<String, Long> unlockTimestamps = TIMESTAMPS_CODEC.decode(buf);
        boolean enabled = ByteBufCodecs.BOOL.decode(buf);
        boolean showTooltips = ByteBufCodecs.BOOL.decode(buf);
        String teamDisplayName = ByteBufCodecs.STRING_UTF8.decode(buf);
        boolean delta = ByteBufCodecs.BOOL.decode(buf);
        return new SyncJourneyDataPacket(collectedCounts, unlockedItems, unlockTimestamps, enabled, showTooltips, teamDisplayName, delta);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SyncJourneyDataPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player().level().isClientSide) {
                var journeyData = context.player().getData(JourneyMode.JOURNEY_DATA);
                // Update the client-side data with server data
                if (packet.delta) {
                    journeyData.applyDelta(packet.collectedCounts, packet.unlockedItems, packet.unlockTimestamps, packet.enabled, packet.showTooltips, packet.teamDisplayName);
                } else {
                    journeyData.updateFromSync(packet.collectedCounts, packet.unlockedItems, packet.unlockTimestamps, packet.enabled, packet.showTooltips, packet.teamDisplayName);
                }
                // §11 Visual Polish: action-bar message on threshold crossing.
                com.aryangpt007.journeymode.client.ClientSetup.celebrateNewUnlocks(journeyData.getAndClearNewlyUnlocked());
            }
        });
    }
}
