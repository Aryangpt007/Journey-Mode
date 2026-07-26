package com.aryangpt007.journeymode.network;

import com.aryangpt007.journeymode.config.ConfigHandler;
import com.aryangpt007.journeymode.network.packets.ConfigSyncPacket;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;

/** Server-side helper for pushing config sync (real-time config sync) to one or all players. */
public final class ConfigSyncHelper {
    private ConfigSyncHelper() {}

    public static void pushToPlayer(EntityPlayerMP player) {
        NetworkHandler.sendTo(new ConfigSyncPacket(ConfigHandler.buildSyncSnapshot()), player);
    }

    public static void pushToAllPlayers(MinecraftServer server) {
        if (server == null) return;
        ConfigHandler.SyncSnapshot snapshot = ConfigHandler.buildSyncSnapshot();
        for (EntityPlayerMP player : server.getPlayerList().getPlayers()) {
            NetworkHandler.sendTo(new ConfigSyncPacket(snapshot), player);
        }
    }
}
