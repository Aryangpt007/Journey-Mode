package com.aryangpt007.journeymode.events;

import com.aryangpt007.journeymode.JourneyMode;
import com.aryangpt007.journeymode.data.GlobalDataHandler;
import com.aryangpt007.journeymode.data.IJourneyData;
import com.aryangpt007.journeymode.data.JourneyDataCapabilityProvider;
import net.minecraft.util.ResourceLocation;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent.PlayerLoggedInEvent;

/**
 * Event handlers for Journey Mode on Forge 1.12.2
 */
public class JourneyModeEvents {

    @SubscribeEvent
    public void onAttachCapabilities(AttachCapabilitiesEvent<Entity> event) {
        if (event.getObject() instanceof EntityPlayer) {
            if (!event.getObject().hasCapability(JourneyDataCapabilityProvider.JOURNEY_DATA_CAPABILITY, null)) {
                event.addCapability(
                    new ResourceLocation(JourneyMode.MODID, "journey_data"), 
                    new JourneyDataCapabilityProvider()
                );
            }
        }
    }

    @SubscribeEvent
    public void onPlayerLoggedIn(PlayerLoggedInEvent event) {
        if (event.player instanceof EntityPlayerMP) {
            EntityPlayerMP player = (EntityPlayerMP) event.player;
            GlobalDataHandler.loadPlayerUnlocks(player);
        }
    }

    @SubscribeEvent
    public void onPlayerClone(PlayerEvent.Clone event) {
        if (event.getEntityPlayer() instanceof EntityPlayerMP) {
            EntityPlayerMP newPlayer = (EntityPlayerMP) event.getEntityPlayer();
            IJourneyData oldData = event.getOriginal().getCapability(JourneyDataCapabilityProvider.JOURNEY_DATA_CAPABILITY, null);
            IJourneyData newData = newPlayer.getCapability(JourneyDataCapabilityProvider.JOURNEY_DATA_CAPABILITY, null);
            if (oldData != null && newData != null) {
                newData.copyFrom(oldData);
                GlobalDataHandler.syncToClient(newPlayer, newData);
            }
        }
    }
}
