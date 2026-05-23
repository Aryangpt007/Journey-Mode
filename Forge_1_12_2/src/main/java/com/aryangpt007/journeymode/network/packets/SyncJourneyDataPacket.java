package com.aryangpt007.journeymode.network.packets;

import com.aryangpt007.journeymode.data.IJourneyData;
import com.aryangpt007.journeymode.data.JourneyDataCapabilityProvider;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.*;

public class SyncJourneyDataPacket implements IMessage {
    private Map<String, Integer> collectedCounts;
    private Set<String> unlockedItems;
    private Map<String, Long> unlockTimestamps;

    public SyncJourneyDataPacket() {
        this.collectedCounts = new HashMap<>();
        this.unlockedItems = new HashSet<>();
        this.unlockTimestamps = new HashMap<>();
    }

    public SyncJourneyDataPacket(Map<String, Integer> collectedCounts, Set<String> unlockedItems, Map<String, Long> unlockTimestamps) {
        this.collectedCounts = collectedCounts;
        this.unlockedItems = unlockedItems;
        this.unlockTimestamps = unlockTimestamps;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        PacketBuffer packetBuffer = new PacketBuffer(buf);
        try {
            int collectedSize = packetBuffer.readInt();
            collectedCounts = new HashMap<>();
            for (int i = 0; i < collectedSize; i++) {
                collectedCounts.put(packetBuffer.readString(32767), packetBuffer.readInt());
            }
            
            int unlockedSize = packetBuffer.readInt();
            unlockedItems = new HashSet<>();
            for (int i = 0; i < unlockedSize; i++) {
                unlockedItems.add(packetBuffer.readString(32767));
            }
            
            int timestampsSize = packetBuffer.readInt();
            unlockTimestamps = new HashMap<>();
            for (int i = 0; i < timestampsSize; i++) {
                unlockTimestamps.put(packetBuffer.readString(32767), packetBuffer.readLong());
            }
        } catch (Exception ignored) {}
    }

    @Override
    public void toBytes(ByteBuf buf) {
        PacketBuffer packetBuffer = new PacketBuffer(buf);
        packetBuffer.writeInt(collectedCounts.size());
        for (Map.Entry<String, Integer> entry : collectedCounts.entrySet()) {
            packetBuffer.writeString(entry.getKey());
            packetBuffer.writeInt(entry.getValue());
        }
        packetBuffer.writeInt(unlockedItems.size());
        for (String item : unlockedItems) {
            packetBuffer.writeString(item);
        }
        packetBuffer.writeInt(unlockTimestamps.size());
        for (Map.Entry<String, Long> entry : unlockTimestamps.entrySet()) {
            packetBuffer.writeString(entry.getKey());
            packetBuffer.writeLong(entry.getValue());
        }
    }

    public static class Handler implements IMessageHandler<SyncJourneyDataPacket, IMessage> {
        @Override
        public IMessage onMessage(SyncJourneyDataPacket message, MessageContext ctx) {
            runClient(message);
            return null;
        }

        @SideOnly(Side.CLIENT)
        private void runClient(SyncJourneyDataPacket message) {
            net.minecraft.client.Minecraft.getMinecraft().addScheduledTask(() -> {
                net.minecraft.client.entity.EntityPlayerSP player = net.minecraft.client.Minecraft.getMinecraft().player;
                if (player != null) {
                    IJourneyData data = player.getCapability(JourneyDataCapabilityProvider.JOURNEY_DATA_CAPABILITY, null);
                    if (data != null) {
                        data.updateFromSync(message.collectedCounts, message.unlockedItems, message.unlockTimestamps);
                    }
                }
            });
        }
    }
}
