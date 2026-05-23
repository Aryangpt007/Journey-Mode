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

    public SyncJourneyDataPacket(Map<String, Integer> collectedCounts, Set<String> unlockedItems, Map<String, Long> unlockTimestamps) {
        this.collectedCounts = collectedCounts;
        this.unlockedItems = unlockedItems;
        this.unlockTimestamps = unlockTimestamps;
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

    public static void encode(SyncJourneyDataPacket msg, FriendlyByteBuf buf) {
        buf.writeMap(msg.collectedCounts, FriendlyByteBuf::writeUtf, FriendlyByteBuf::writeInt);
        buf.writeCollection(msg.unlockedItems, FriendlyByteBuf::writeUtf);
        buf.writeMap(msg.unlockTimestamps, FriendlyByteBuf::writeUtf, FriendlyByteBuf::writeLong);
    }

    public static SyncJourneyDataPacket decode(FriendlyByteBuf buf) {
        Map<String, Integer> collectedCounts = buf.readMap(HashMap::new, FriendlyByteBuf::readUtf, FriendlyByteBuf::readInt);
        Set<String> unlockedItems = new HashSet<>(buf.readCollection(ArrayList::new, FriendlyByteBuf::readUtf));
        Map<String, Long> unlockTimestamps = buf.readMap(HashMap::new, FriendlyByteBuf::readUtf, FriendlyByteBuf::readLong);
        return new SyncJourneyDataPacket(collectedCounts, unlockedItems, unlockTimestamps);
    }

    public static void handle(SyncJourneyDataPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            // Safety: Only call client setup from client side
            ClientSetup.handleSync(msg);
        });
        ctx.get().setPacketHandled(true);
    }
}
