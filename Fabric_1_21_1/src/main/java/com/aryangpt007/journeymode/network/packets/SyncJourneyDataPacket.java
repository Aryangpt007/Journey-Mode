package com.aryangpt007.journeymode.network.packets;

import com.aryangpt007.journeymode.JourneyMode;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.*;

/**
 * Packet to sync Journey Mode data from server to client. Carries `enabled`/`showTooltips`
 * too - without them, the client's own data instance (a separate object from the server's,
 * even in singleplayer) never learned about /journeymode off or a tooltip preference change
 * for client-only checks (e.g. TooltipHandler). §1 Shared Team Catalogs additionally carries
 * the resolved team's display name (empty string = no team) for the client-side badge - a
 * plain hand-rolled StreamCodec (rather than StreamCodec.composite, which has a field-count
 * ceiling) is already required here for the Map/Set fields, so the extra String rides along
 * for free.
 */
public record SyncJourneyDataPacket(Map<String, Integer> collectedCounts, Set<String> unlockedItems, Map<String, Long> unlockTimestamps, boolean enabled, boolean showTooltips, String teamDisplayName) implements CustomPacketPayload {
    public SyncJourneyDataPacket {
        teamDisplayName = teamDisplayName == null ? "" : teamDisplayName;
    }

    public static final Type<SyncJourneyDataPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(JourneyMode.MODID, "sync_journey_data"));

    // Declared with the Map/Set interface type (not HashMap/HashSet) so encode() can hand it
    // the record's interface-typed fields directly - the constructor-ref supplier only affects
    // which concrete type comes out of decode(), not what encode() is allowed to accept.
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
    }

    private static SyncJourneyDataPacket decode(RegistryFriendlyByteBuf buf) {
        Map<String, Integer> collectedCounts = COUNTS_CODEC.decode(buf);
        Set<String> unlockedItems = UNLOCKED_CODEC.decode(buf);
        Map<String, Long> unlockTimestamps = TIMESTAMPS_CODEC.decode(buf);
        boolean enabled = ByteBufCodecs.BOOL.decode(buf);
        boolean showTooltips = ByteBufCodecs.BOOL.decode(buf);
        String teamDisplayName = ByteBufCodecs.STRING_UTF8.decode(buf);
        return new SyncJourneyDataPacket(collectedCounts, unlockedItems, unlockTimestamps, enabled, showTooltips, teamDisplayName);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
