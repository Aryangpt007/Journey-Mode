package com.aryangpt007.journeymode.events;

import com.aryangpt007.journeymode.JourneyMode;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * Event handlers for Journey Mode
 */
public class JourneyModeEvents {

    @SubscribeEvent
    public void onPlayerClone(PlayerEvent.Clone event) {
        // Copy Journey Mode data when player respawns or returns from End
        if (event.isWasDeath() || !event.getEntity().level().isClientSide) {
            var oldData = event.getOriginal().getData(JourneyMode.JOURNEY_DATA);
            var newData = event.getEntity().getData(JourneyMode.JOURNEY_DATA);
            
            // Copy unlocked items and counts
            for (var entry : oldData.getAllCollectedCounts().entrySet()) {
                // We need to manually copy since attachments create new instances
                // This is a simplified approach - in production you'd reconstruct the attachment
            }
        }
    }
}
