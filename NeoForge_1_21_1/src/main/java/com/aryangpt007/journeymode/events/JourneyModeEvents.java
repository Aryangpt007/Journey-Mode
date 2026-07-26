package com.aryangpt007.journeymode.events;

import com.aryangpt007.journeymode.JourneyMode;
import com.aryangpt007.journeymode.data.DatapackThresholdLoader;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;

/**
 * Event handlers for Journey Mode (Purely server/common safe)
 */
public class JourneyModeEvents {

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        // §1 Shared Team Catalogs: (re)load the per-world teams file for this session, clearing
        // any previous world's cache - a singleplayer client that switches worlds must never see
        // a different world's teams.
        com.aryangpt007.journeymode.data.TeamDataHandler.load(event.getServer());
    }

    @SubscribeEvent
    public void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(DatapackThresholdLoader.INSTANCE);
    }

    @SubscribeEvent
    public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player) {
            com.aryangpt007.journeymode.data.GlobalDataHandler.loadPlayerUnlocks(player);
            com.aryangpt007.journeymode.network.ConfigSyncHelper.pushToPlayer(player);
        }
    }

    @SubscribeEvent
    public void onPlayerClone(PlayerEvent.Clone event) {
        if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer newPlayer) {
            var oldData = event.getOriginal().getData(JourneyMode.JOURNEY_DATA);
            var newData = newPlayer.getData(JourneyMode.JOURNEY_DATA);
            if (oldData != null && newData != null) {
                newData.copyFrom(oldData);
                com.aryangpt007.journeymode.data.GlobalDataHandler.syncToClient(newPlayer, newData);
            }
        }
    }
}
