package com.aryangpt007.journeymode.events;

import com.aryangpt007.journeymode.JourneyMode;
import com.aryangpt007.journeymode.data.GlobalDataHandler;
import com.aryangpt007.journeymode.data.JourneyDataAttachment;
import com.aryangpt007.journeymode.data.JourneyDataCapabilityProvider;
import net.minecraft.util.ResourceLocation;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Event handlers for Journey Mode on Forge (Purely server/common safe)
 */
public class JourneyModeEvents {

    @SubscribeEvent
    public void onAttachCapabilities(AttachCapabilitiesEvent<Entity> event) {
        if (event.getObject() instanceof PlayerEntity) {
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
        if (event.getEntity() instanceof ServerPlayerEntity) {
            ServerPlayerEntity player = (ServerPlayerEntity) event.getEntity();
            GlobalDataHandler.loadPlayerUnlocks(player);
        }
    }

    @SubscribeEvent
    public void onPlayerClone(PlayerEvent.Clone event) {
        if (event.getEntity() instanceof ServerPlayerEntity) {
            ServerPlayerEntity newPlayer = (ServerPlayerEntity) event.getEntity();
            JourneyDataAttachment oldData = event.getOriginal().getCapability(JourneyDataCapabilityProvider.JOURNEY_DATA_CAPABILITY).orElse(null);
            JourneyDataAttachment newData = newPlayer.getCapability(JourneyDataCapabilityProvider.JOURNEY_DATA_CAPABILITY).orElse(null);
            if (oldData != null && newData != null) {
                newData.copyFrom(oldData);
                GlobalDataHandler.syncToClient(newPlayer, newData);
            }
        }
    }
}
