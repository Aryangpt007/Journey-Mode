package com.aryangpt007.journeymode.network.packets;

import com.aryangpt007.journeymode.config.ConfigHandler;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fml.network.NetworkEvent;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Server -> client push of blacklist/threshold rules (§2 real-time config sync). Sent on
 * player join and after /journeymode reloadconfig or any command that mutates the config
 * files, so the client's own ConfigHandler.getThresholdOverride/isBlacklisted answer the same
 * way the server does even on a dedicated server the client never wrote config files for.
 *
 * 1.16.5 uses PacketBuffer (predecessor of FriendlyByteBuf) which lacks the writeCollection/
 * writeMap convenience helpers added in later versions - sizes and entries are written/read
 * manually instead.
 */
public class ConfigSyncPacket {
    private final ConfigHandler.SyncSnapshot snapshot;

    public ConfigSyncPacket(ConfigHandler.SyncSnapshot snapshot) {
        this.snapshot = snapshot;
    }

    public static void encode(ConfigSyncPacket msg, PacketBuffer buf) {
        ConfigHandler.SyncSnapshot s = msg.snapshot;
        writeStringList(buf, s.exactBlacklist());
        writeStringList(buf, s.tagBlacklist());
        writeStringList(buf, s.patternBlacklist());
        writeStringIntMap(buf, s.exactThresholds());
        writeEntryList(buf, s.tagThresholds());
        writeEntryList(buf, s.patternThresholds());
        buf.writeBoolean(s.defaultOverride() != null);
        if (s.defaultOverride() != null) {
            buf.writeVarInt(s.defaultOverride());
        }
        buf.writeVarInt(s.maxThresholdCap());
    }

    public static ConfigSyncPacket decode(PacketBuffer buf) {
        List<String> exactBlacklist = readStringList(buf);
        List<String> tagBlacklist = readStringList(buf);
        List<String> patternBlacklist = readStringList(buf);
        Map<String, Integer> exactThresholds = readStringIntMap(buf);
        List<Map.Entry<String, Integer>> tagThresholds = readEntryList(buf);
        List<Map.Entry<String, Integer>> patternThresholds = readEntryList(buf);
        Integer defaultOverride = buf.readBoolean() ? buf.readVarInt() : null;
        int maxThresholdCap = buf.readVarInt();

        return new ConfigSyncPacket(new ConfigHandler.SyncSnapshot(
            exactBlacklist, tagBlacklist, patternBlacklist,
            exactThresholds, tagThresholds, patternThresholds,
            defaultOverride, maxThresholdCap));
    }

    private static void writeStringList(PacketBuffer buf, List<String> values) {
        buf.writeVarInt(values.size());
        for (String value : values) {
            buf.writeUtf(value);
        }
    }

    private static List<String> readStringList(PacketBuffer buf) {
        int size = buf.readVarInt();
        List<String> result = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            result.add(buf.readUtf(32767));
        }
        return result;
    }

    private static void writeStringIntMap(PacketBuffer buf, Map<String, Integer> map) {
        buf.writeVarInt(map.size());
        for (Map.Entry<String, Integer> entry : map.entrySet()) {
            buf.writeUtf(entry.getKey());
            buf.writeVarInt(entry.getValue());
        }
    }

    private static Map<String, Integer> readStringIntMap(PacketBuffer buf) {
        int size = buf.readVarInt();
        Map<String, Integer> result = new HashMap<>();
        for (int i = 0; i < size; i++) {
            result.put(buf.readUtf(32767), buf.readVarInt());
        }
        return result;
    }

    private static void writeEntryList(PacketBuffer buf, List<Map.Entry<String, Integer>> entries) {
        buf.writeVarInt(entries.size());
        for (Map.Entry<String, Integer> e : entries) {
            buf.writeUtf(e.getKey());
            buf.writeVarInt(e.getValue());
        }
    }

    private static List<Map.Entry<String, Integer>> readEntryList(PacketBuffer buf) {
        int size = buf.readVarInt();
        List<Map.Entry<String, Integer>> result = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            result.add(new AbstractMap.SimpleEntry<>(buf.readUtf(32767), buf.readVarInt()));
        }
        return result;
    }

    public static void handle(ConfigSyncPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> ConfigHandler.applySyncedRules(msg.snapshot));
        ctx.get().setPacketHandled(true);
    }
}
