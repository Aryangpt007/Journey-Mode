package com.aryangpt007.journeymode.events;

import com.aryangpt007.journeymode.JourneyMode;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * Event handlers for Journey Mode (Purely server/common safe)
 */
public class JourneyModeEvents {

    @SubscribeEvent
    public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player) {
            com.aryangpt007.journeymode.data.GlobalDataHandler.loadPlayerUnlocks(player);
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
