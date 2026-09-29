package com.aryangpt007.journeymode.network.packets;

import com.aryangpt007.journeymode.client.ClientSetup;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.*;
import java.util.function.Supplier;

/**
 * Packet to sync Journey Mode data from server to client.
 * Ported to Forge 1.20.1.
 */
public class SyncJourneyDataPacket {
    private final Map<String, Integer> collectedCounts;
    private final Set<String> unlockedItems;
    private final Map<String, Long> unlockTimestamps;
    private final boolean enabled;
    private final boolean showTooltips;
    private final String teamDisplayName; // empty string = no team

    /**
     * True when this payload carries only the entries that changed, to be merged into the
     * client's existing catalog rather than replacing it. A deposit changes one entry; without
     * this the server re-sent the whole catalog to say so. Full syncs (login, reset, purge,
     * revoke, team join/leave) keep delta=false, because a delta cannot express a removal.
     */
    private final boolean delta;

    public SyncJourneyDataPacket(Map<String, Integer> collectedCounts, Set<String> unlockedItems, Map<String, Long> unlockTimestamps, boolean enabled, boolean showTooltips, String teamDisplayName) {
        this(collectedCounts, unlockedItems, unlockTimestamps, enabled, showTooltips, teamDisplayName, false);
    }

    public SyncJourneyDataPacket(Map<String, Integer> collectedCounts, Set<String> unlockedItems, Map<String, Long> unlockTimestamps, boolean enabled, boolean showTooltips, String teamDisplayName, boolean delta) {
        this.delta = delta;
        this.collectedCounts = collectedCounts;
        this.unlockedItems = unlockedItems;
        this.unlockTimestamps = unlockTimestamps;
        this.enabled = enabled;
        this.showTooltips = showTooltips;
        this.teamDisplayName = teamDisplayName == null ? "" : teamDisplayName;
    }

    public Map<String, Integer> getCollectedCounts() {
        return collectedCounts;
    }

    public Set<String> getUnlockedItems() {
        return unlockedItems;
    }

    public Map<String, Long> getUnlockTimestamps() {
        return unlockTimestamps;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isShowTooltips() {
        return showTooltips;
    }

    public boolean isDelta() {
        return delta;
    }

    public String getTeamDisplayName() {
        return teamDisplayName;
    }

    public static void encode(SyncJourneyDataPacket msg, FriendlyByteBuf buf) {
        buf.writeMap(msg.collectedCounts, FriendlyByteBuf::writeUtf, FriendlyByteBuf::writeInt);
        buf.writeCollection(msg.unlockedItems, FriendlyByteBuf::writeUtf);
        buf.writeMap(msg.unlockTimestamps, FriendlyByteBuf::writeUtf, FriendlyByteBuf::writeLong);
        buf.writeBoolean(msg.enabled);
        buf.writeBoolean(msg.showTooltips);
        buf.writeUtf(msg.teamDisplayName);
        buf.writeBoolean(msg.delta);
    }

    public static SyncJourneyDataPacket decode(FriendlyByteBuf buf) {
        Map<String, Integer> collectedCounts = buf.readMap(HashMap::new, FriendlyByteBuf::readUtf, FriendlyByteBuf::readInt);
        Set<String> unlockedItems = new HashSet<>(buf.readCollection(ArrayList::new, FriendlyByteBuf::readUtf));
        Map<String, Long> unlockTimestamps = buf.readMap(HashMap::new, FriendlyByteBuf::readUtf, FriendlyByteBuf::readLong);
        boolean enabled = buf.readBoolean();
        boolean showTooltips = buf.readBoolean();
        String teamDisplayName = buf.readUtf();
        boolean delta = buf.readBoolean();
        return new SyncJourneyDataPacket(collectedCounts, unlockedItems, unlockTimestamps, enabled, showTooltips, teamDisplayName, delta);
    }

    public static void handle(SyncJourneyDataPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            // Safety: Only call client setup from client side
            ClientSetup.handleSync(msg);
        });
        ctx.get().setPacketHandled(true);
    }
}
