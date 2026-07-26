package com.aryangpt007.journeymode.network.packets;

import com.aryangpt007.journeymode.config.ConfigHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

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
 */
public class ConfigSyncPacket {
    private final ConfigHandler.SyncSnapshot snapshot;

    public ConfigSyncPacket(ConfigHandler.SyncSnapshot snapshot) {
        this.snapshot = snapshot;
    }

    public static void encode(ConfigSyncPacket msg, FriendlyByteBuf buf) {
        ConfigHandler.SyncSnapshot s = msg.snapshot;
        buf.writeCollection(s.exactBlacklist(), FriendlyByteBuf::writeUtf);
        buf.writeCollection(s.tagBlacklist(), FriendlyByteBuf::writeUtf);
        buf.writeCollection(s.patternBlacklist(), FriendlyByteBuf::writeUtf);
        buf.writeMap(s.exactThresholds(), FriendlyByteBuf::writeUtf, FriendlyByteBuf::writeVarInt);
        writeEntryList(buf, s.tagThresholds());
        writeEntryList(buf, s.patternThresholds());
        buf.writeBoolean(s.defaultOverride() != null);
        if (s.defaultOverride() != null) {
            buf.writeVarInt(s.defaultOverride());
        }
        buf.writeVarInt(s.maxThresholdCap());
    }

    public static ConfigSyncPacket decode(FriendlyByteBuf buf) {
        List<String> exactBlacklist = new ArrayList<>(buf.readCollection(ArrayList::new, FriendlyByteBuf::readUtf));
        List<String> tagBlacklist = new ArrayList<>(buf.readCollection(ArrayList::new, FriendlyByteBuf::readUtf));
        List<String> patternBlacklist = new ArrayList<>(buf.readCollection(ArrayList::new, FriendlyByteBuf::readUtf));
        Map<String, Integer> exactThresholds = buf.readMap(HashMap::new, FriendlyByteBuf::readUtf, FriendlyByteBuf::readVarInt);
        List<Map.Entry<String, Integer>> tagThresholds = readEntryList(buf);
        List<Map.Entry<String, Integer>> patternThresholds = readEntryList(buf);
        Integer defaultOverride = buf.readBoolean() ? buf.readVarInt() : null;
        int maxThresholdCap = buf.readVarInt();

        return new ConfigSyncPacket(new ConfigHandler.SyncSnapshot(
            exactBlacklist, tagBlacklist, patternBlacklist,
            exactThresholds, tagThresholds, patternThresholds,
            defaultOverride, maxThresholdCap));
    }

    private static void writeEntryList(FriendlyByteBuf buf, List<Map.Entry<String, Integer>> entries) {
        buf.writeVarInt(entries.size());
        for (Map.Entry<String, Integer> e : entries) {
            buf.writeUtf(e.getKey());
            buf.writeVarInt(e.getValue());
        }
    }

    private static List<Map.Entry<String, Integer>> readEntryList(FriendlyByteBuf buf) {
        int size = buf.readVarInt();
        List<Map.Entry<String, Integer>> result = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            result.add(new AbstractMap.SimpleEntry<>(buf.readUtf(), buf.readVarInt()));
        }
        return result;
    }

    public static void handle(ConfigSyncPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> ConfigHandler.applySyncedRules(msg.snapshot));
        ctx.get().setPacketHandled(true);
    }
}
