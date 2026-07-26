package com.aryangpt007.journeymode.network;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** Server-side helper for pushing config sync (§2) to one or all players. */
public final class ConfigSyncHelper {
    private ConfigSyncHelper() {}

    public static void pushToPlayer(ServerPlayer player) {
        FabricNetworkHandler.syncConfigToPlayer(player);
    }

    public static void pushToAllPlayers(MinecraftServer server) {
        if (server == null) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            FabricNetworkHandler.syncConfigToPlayer(player);
        }
    }
}
