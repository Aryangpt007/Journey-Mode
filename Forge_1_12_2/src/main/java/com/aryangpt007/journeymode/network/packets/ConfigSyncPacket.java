package com.aryangpt007.journeymode.network.packets;

import com.aryangpt007.journeymode.config.ConfigHandler;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Server -&gt; client push of blacklist/threshold rules (real-time config sync). Sent on player
 * join and after /journeymode reloadconfig or any command that mutates the config files, so the
 * client's own ConfigHandler.getThresholdOverride/isBlacklisted answer the same way the server
 * does even on a dedicated server the client never wrote config files for.
 *
 * 1.12.2 has no FriendlyByteBuf writeMap/writeCollection convenience methods - everything below
 * is a manual count-prefixed loop over PacketBuffer's readString/writeString/readVarInt/writeVarInt.
 */
public class ConfigSyncPacket implements IMessage {
    private ConfigHandler.SyncSnapshot snapshot;

    public ConfigSyncPacket() {}

    public ConfigSyncPacket(ConfigHandler.SyncSnapshot snapshot) {
        this.snapshot = snapshot;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        PacketBuffer packetBuffer = new PacketBuffer(buf);

        writeStringList(packetBuffer, snapshot.getExactBlacklist());
        writeStringList(packetBuffer, snapshot.getTagBlacklist());
        writeStringList(packetBuffer, snapshot.getPatternBlacklist());

        Map<String, Integer> exactThresholds = snapshot.getExactThresholds();
        packetBuffer.writeVarInt(exactThresholds.size());
        for (Map.Entry<String, Integer> e : exactThresholds.entrySet()) {
            packetBuffer.writeString(e.getKey());
            packetBuffer.writeVarInt(e.getValue());
        }

        writeEntryList(packetBuffer, snapshot.getTagThresholds());
        writeEntryList(packetBuffer, snapshot.getPatternThresholds());

        Integer defaultOverride = snapshot.getDefaultOverride();
        packetBuffer.writeBoolean(defaultOverride != null);
        if (defaultOverride != null) {
            packetBuffer.writeVarInt(defaultOverride);
        }
        packetBuffer.writeVarInt(snapshot.getMaxThresholdCap());
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        PacketBuffer packetBuffer = new PacketBuffer(buf);
        try {
            List<String> exactBlacklist = readStringList(packetBuffer);
            List<String> tagBlacklist = readStringList(packetBuffer);
            List<String> patternBlacklist = readStringList(packetBuffer);

            int exactSize = packetBuffer.readVarInt();
            Map<String, Integer> exactThresholds = new HashMap<>();
            for (int i = 0; i < exactSize; i++) {
                exactThresholds.put(packetBuffer.readString(32767), packetBuffer.readVarInt());
            }

            List<Map.Entry<String, Integer>> tagThresholds = readEntryList(packetBuffer);
            List<Map.Entry<String, Integer>> patternThresholds = readEntryList(packetBuffer);

            Integer defaultOverride = packetBuffer.readBoolean() ? packetBuffer.readVarInt() : null;
            int maxThresholdCap = packetBuffer.readVarInt();

            this.snapshot = new ConfigHandler.SyncSnapshot(
                exactBlacklist, tagBlacklist, patternBlacklist,
                exactThresholds, tagThresholds, patternThresholds,
                defaultOverride, maxThresholdCap);
        } catch (Exception ignored) {}
    }

    private static void writeStringList(PacketBuffer buf, List<String> list) {
        buf.writeVarInt(list.size());
        for (String s : list) buf.writeString(s);
    }

    private static List<String> readStringList(PacketBuffer buf) {
        int size = buf.readVarInt();
        List<String> result = new ArrayList<>(size);
        for (int i = 0; i < size; i++) result.add(buf.readString(32767));
        return result;
    }

    private static void writeEntryList(PacketBuffer buf, List<Map.Entry<String, Integer>> entries) {
        buf.writeVarInt(entries.size());
        for (Map.Entry<String, Integer> e : entries) {
            buf.writeString(e.getKey());
            buf.writeVarInt(e.getValue());
        }
    }

    private static List<Map.Entry<String, Integer>> readEntryList(PacketBuffer buf) {
        int size = buf.readVarInt();
        List<Map.Entry<String, Integer>> result = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            result.add(new AbstractMap.SimpleEntry<>(buf.readString(32767), buf.readVarInt()));
        }
        return result;
    }

    public static class Handler implements IMessageHandler<ConfigSyncPacket, IMessage> {
        @Override
        public IMessage onMessage(ConfigSyncPacket message, MessageContext ctx) {
            runClient(message);
            return null;
        }

        @SideOnly(Side.CLIENT)
        private void runClient(ConfigSyncPacket message) {
            Minecraft.getMinecraft().addScheduledTask(() -> {
                if (message.snapshot != null) {
                    ConfigHandler.applySyncedRules(message.snapshot);
                }
            });
        }
    }
}
