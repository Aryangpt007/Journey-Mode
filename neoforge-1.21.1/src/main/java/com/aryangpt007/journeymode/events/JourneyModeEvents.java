package com.aryangpt007.journeymode.events;

import com.aryangpt007.journeymode.core.JourneyData;
import com.aryangpt007.journeymode.network.packets.OpenJourneyMenuPacket;
import com.aryangpt007.journeymode.platform.neoforge.NeoForgeDataHandler;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Event handlers for Journey Mode
 */
public class JourneyModeEvents {

    @SubscribeEvent
    public void onPlayerClone(PlayerEvent.Clone event) {
        // Copy Journey Mode data when player respawns or returns from End
        if (event.isWasDeath() || !event.getEntity().level().isClientSide) {
            JourneyData oldData = NeoForgeDataHandler.getJourneyData(event.getOriginal());
            JourneyData newData = NeoForgeDataHandler.getJourneyData(event.getEntity());
            
            // Copy all data from old to new
            newData.updateFromSync(
                oldData.getCollectedCounts(),
                oldData.getUnlockedItems(),
                oldData.getUnlockTimestamps()
            );
            newData.setEnabled(oldData.isEnabled());
            
            // Save the updated data
            NeoForgeDataHandler.saveJourneyData(event.getEntity(), newData);
        }
    }

    /**
     * Client-side key handler
     */
    @OnlyIn(Dist.CLIENT)
    public static class ClientKeyHandler {
        @SubscribeEvent
        public static void onClientTick(ClientTickEvent.Post event) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return;

            // Use the configurable keybind instead of hardcoded key
            if (com.aryangpt007.journeymode.client.ClientSetup.OPEN_JOURNEY_MODE_KEY.consumeClick()) {
                // Key was just pressed
                PacketDistributor.sendToServer(new OpenJourneyMenuPacket());
            }
        }
    }
}
