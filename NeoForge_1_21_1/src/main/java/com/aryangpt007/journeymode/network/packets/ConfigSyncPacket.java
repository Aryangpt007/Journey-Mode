package com.aryangpt007.journeymode.network.packets;

import com.aryangpt007.journeymode.JourneyMode;
import com.aryangpt007.journeymode.config.ConfigHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Server -> client push of blacklist/threshold rules (§2 real-time config sync). Sent on
 * player join and after /journeymode reloadconfig or any command that mutates the config
 * files, so the client's own ConfigHandler.getThresholdOverride/isBlacklisted answer the same
 * way the server does even on a dedicated server the client never wrote config files for.
 *
 * Eight fields don't fit StreamCodec.composite's 6-field ceiling, so this codec is hand-rolled
 * with StreamCodec.of over raw FriendlyByteBuf read/write calls (RegistryFriendlyByteBuf
 * extends FriendlyByteBuf), mirroring the equivalent Forge 1.20.1 FriendlyByteBuf encode/decode.
 */
public record ConfigSyncPacket(ConfigHandler.SyncSnapshot snapshot) implements CustomPacketPayload {
    public static final Type<ConfigSyncPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(JourneyMode.MODID, "config_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ConfigSyncPacket> STREAM_CODEC = StreamCodec.of(
        ConfigSyncPacket::encode,
        ConfigSyncPacket::decode
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void encode(RegistryFriendlyByteBuf buf, ConfigSyncPacket msg) {
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

    private static ConfigSyncPacket decode(RegistryFriendlyByteBuf buf) {
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

    public static void handle(ConfigSyncPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> ConfigHandler.applySyncedRules(packet.snapshot()));
    }
}
