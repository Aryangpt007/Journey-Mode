package com.aryangpt007.journeymode.events;

import com.aryangpt007.journeymode.JourneyMode;
import com.aryangpt007.journeymode.data.GlobalDataHandler;
import com.aryangpt007.journeymode.data.JourneyDataCapabilityProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Event handlers for Journey Mode on Forge (Purely server/common safe)
 */
public class JourneyModeEvents {

    @SubscribeEvent
    public void onAttachCapabilities(AttachCapabilitiesEvent<Entity> event) {
        if (event.getObject() instanceof Player) {
            if (!event.getObject().getCapability(JourneyDataCapabilityProvider.JOURNEY_DATA_CAPABILITY).isPresent()) {
                event.addCapability(
                    new ResourceLocation(JourneyMode.MODID, "journey_data"), 
                    new JourneyDataCapabilityProvider()
                );
            }
        }
    }

    @SubscribeEvent
    public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            GlobalDataHandler.loadPlayerUnlocks(player);
        }
    }

    @SubscribeEvent
    public void onPlayerClone(PlayerEvent.Clone event) {
        if (event.getEntity() instanceof ServerPlayer newPlayer) {
            event.getOriginal().reviveCaps(); // Safety to retrieve capability from dead player container
            var oldData = event.getOriginal().getCapability(JourneyDataCapabilityProvider.JOURNEY_DATA_CAPABILITY).orElse(null);
            var newData = newPlayer.getCapability(JourneyDataCapabilityProvider.JOURNEY_DATA_CAPABILITY).orElse(null);
            if (oldData != null && newData != null) {
                newData.copyFrom(oldData);
                GlobalDataHandler.syncToClient(newPlayer, newData);
            }
        }
    }
}
