package com.aryangpt007.journeymode.network.packets;

import com.aryangpt007.journeymode.JourneyMode;
import com.aryangpt007.journeymode.config.ConfigHandler;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Server -> client push of blacklist/threshold rules (§2 real-time config sync). Sent on
 * player join and after /journeymode reloadconfig or any command that mutates the config
 * files, so the client's own ConfigHandler.getThresholdOverride/isBlacklisted answer the same
 * way the server does even on a dedicated server the client never wrote config files for.
 */
public record ConfigSyncPacket(ConfigHandler.SyncSnapshot snapshot) implements CustomPacketPayload {
    public static final Type<ConfigSyncPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(JourneyMode.MODID, "config_sync"));

    private static final StreamCodec<ByteBuf, List<String>> STRING_LIST =
        ByteBufCodecs.collection(ArrayList::new, ByteBufCodecs.STRING_UTF8);
    private static final StreamCodec<ByteBuf, Map<String, Integer>> STRING_INT_MAP =
        ByteBufCodecs.map(LinkedHashMap::new, ByteBufCodecs.STRING_UTF8, ByteBufCodecs.VAR_INT);

    public static final StreamCodec<ByteBuf, ConfigSyncPacket> STREAM_CODEC = StreamCodec.of(
        ConfigSyncPacket::encode,
        ConfigSyncPacket::decode
    );

    private static void encode(ByteBuf buf, ConfigSyncPacket packet) {
        ConfigHandler.SyncSnapshot s = packet.snapshot;
        STRING_LIST.encode(buf, s.exactBlacklist());
        STRING_LIST.encode(buf, s.tagBlacklist());
        STRING_LIST.encode(buf, s.patternBlacklist());
        STRING_INT_MAP.encode(buf, s.exactThresholds());
        STRING_INT_MAP.encode(buf, s.tagThresholds());
        STRING_INT_MAP.encode(buf, s.patternThresholds());
        boolean hasDefault = s.defaultOverride() != null;
        ByteBufCodecs.BOOL.encode(buf, hasDefault);
        if (hasDefault) {
            ByteBufCodecs.VAR_INT.encode(buf, s.defaultOverride());
        }
        ByteBufCodecs.VAR_INT.encode(buf, s.maxThresholdCap());
    }

    private static ConfigSyncPacket decode(ByteBuf buf) {
        List<String> exactBlacklist = STRING_LIST.decode(buf);
        List<String> tagBlacklist = STRING_LIST.decode(buf);
        List<String> patternBlacklist = STRING_LIST.decode(buf);
        Map<String, Integer> exactThresholds = STRING_INT_MAP.decode(buf);
        Map<String, Integer> tagThresholds = STRING_INT_MAP.decode(buf);
        Map<String, Integer> patternThresholds = STRING_INT_MAP.decode(buf);
        Integer defaultOverride = ByteBufCodecs.BOOL.decode(buf) ? ByteBufCodecs.VAR_INT.decode(buf) : null;
        int maxThresholdCap = ByteBufCodecs.VAR_INT.decode(buf);

        return new ConfigSyncPacket(new ConfigHandler.SyncSnapshot(
            exactBlacklist, tagBlacklist, patternBlacklist,
            exactThresholds, tagThresholds, patternThresholds,
            defaultOverride, maxThresholdCap));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
