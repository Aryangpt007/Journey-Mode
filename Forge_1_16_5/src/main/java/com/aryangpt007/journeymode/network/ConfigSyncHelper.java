package com.aryangpt007.journeymode.network;

import com.aryangpt007.journeymode.config.ConfigHandler;
import com.aryangpt007.journeymode.network.packets.ConfigSyncPacket;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.fml.network.PacketDistributor;

/** Server-side helper for pushing config sync (§2) to one or all players. */
public final class ConfigSyncHelper {
    private ConfigSyncHelper() {}

    public static void pushToPlayer(ServerPlayerEntity player) {
        NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new ConfigSyncPacket(ConfigHandler.buildSyncSnapshot()));
    }

    public static void pushToAllPlayers(MinecraftServer server) {
        if (server == null) return;
        ConfigHandler.SyncSnapshot snapshot = ConfigHandler.buildSyncSnapshot();
        for (ServerPlayerEntity player : server.getPlayerList().getPlayers()) {
            NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new ConfigSyncPacket(snapshot));
        }
    }
}
