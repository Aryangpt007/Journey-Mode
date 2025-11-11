package com.aryangpt007.journeymode.events;

import com.aryangpt007.journeymode.core.JourneyData;
import com.aryangpt007.journeymode.platform.fabric.FabricDataHandler;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.minecraft.server.level.ServerPlayer;

/**
 * Fabric server event handlers for Journey Mode
 */
public class FabricServerEvents {

    public static void register() {
        // Handle player respawn and dimension change
        ServerPlayerEvents.COPY_FROM.register((oldPlayer, newPlayer, alive) -> {
            // Copy Journey Mode data when player respawns or changes dimension
            JourneyData oldData = FabricDataHandler.getJourneyData(oldPlayer);
            JourneyData newData = new JourneyData();
            
            // Copy all data from old to new
            newData.updateFromSync(
                oldData.getCollectedCounts(),
                oldData.getUnlockedItems(),
                oldData.getUnlockTimestamps()
            );
            newData.setEnabled(oldData.isEnabled());
            
            // Save the updated data
            FabricDataHandler.saveJourneyData(newPlayer, newData);
        });
        
        // Clear cache on player logout
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
            if (!alive) {
                FabricDataHandler.clearCache(oldPlayer.getUUID());
            }
        });
    }
}
