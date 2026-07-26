package com.aryangpt007.journeymode.network;

import com.aryangpt007.journeymode.config.ConfigHandler;
import com.aryangpt007.journeymode.network.packets.ConfigSyncPacket;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** Server-side helper for pushing config sync (§2) to one or all players. */
public final class ConfigSyncHelper {
    private ConfigSyncHelper() {}

    public static void pushToPlayer(ServerPlayer player) {
        ServerPlayNetworking.send(player, new ConfigSyncPacket(ConfigHandler.buildSyncSnapshot()));
    }

    public static void pushToAllPlayers(MinecraftServer server) {
        if (server == null) return;
        ConfigHandler.SyncSnapshot snapshot = ConfigHandler.buildSyncSnapshot();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            ServerPlayNetworking.send(player, new ConfigSyncPacket(snapshot));
        }
    }
}
