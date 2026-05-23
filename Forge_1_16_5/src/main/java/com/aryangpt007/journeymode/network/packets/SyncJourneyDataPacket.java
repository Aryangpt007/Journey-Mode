package com.aryangpt007.journeymode.network.packets;

import com.aryangpt007.journeymode.client.ClientSetup;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fml.network.NetworkEvent;

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

    public static void encode(SyncJourneyDataPacket msg, PacketBuffer buf) {
        buf.writeInt(msg.collectedCounts.size());
        for (Map.Entry<String, Integer> entry : msg.collectedCounts.entrySet()) {
            buf.writeUtf(entry.getKey());
            buf.writeInt(entry.getValue());
        }
        buf.writeInt(msg.unlockedItems.size());
        for (String item : msg.unlockedItems) {
            buf.writeUtf(item);
        }
        buf.writeInt(msg.unlockTimestamps.size());
        for (Map.Entry<String, Long> entry : msg.unlockTimestamps.entrySet()) {
            buf.writeUtf(entry.getKey());
            buf.writeLong(entry.getValue());
        }
    }

    public static SyncJourneyDataPacket decode(PacketBuffer buf) {
        int collectedSize = buf.readInt();
        Map<String, Integer> collectedCounts = new HashMap<>();
        for (int i = 0; i < collectedSize; i++) {
            collectedCounts.put(buf.readUtf(32767), buf.readInt());
        }
        
        int unlockedSize = buf.readInt();
        Set<String> unlockedItems = new HashSet<>();
        for (int i = 0; i < unlockedSize; i++) {
            unlockedItems.add(buf.readUtf(32767));
        }
        
        int timestampsSize = buf.readInt();
        Map<String, Long> unlockTimestamps = new HashMap<>();
        for (int i = 0; i < timestampsSize; i++) {
            unlockTimestamps.put(buf.readUtf(32767), buf.readLong());
        }
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
