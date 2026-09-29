package com.aryangpt007.journeymode.events;

import com.aryangpt007.journeymode.JourneyMode;
import com.aryangpt007.journeymode.data.DatapackThresholdLoader;
import com.aryangpt007.journeymode.data.GlobalDataHandler;
import com.aryangpt007.journeymode.data.JourneyDataAttachment;
import com.aryangpt007.journeymode.data.JourneyDataCapabilityProvider;
import com.aryangpt007.journeymode.network.ConfigSyncHelper;
import net.minecraft.util.ResourceLocation;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.fml.event.server.FMLServerStartingEvent;
import net.minecraftforge.fml.event.server.FMLServerStoppingEvent;
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
    public void onServerStarting(FMLServerStartingEvent event) {
        // §1 Shared Team Catalogs: (re)load the per-world teams file for this session, clearing
        // any previous world's cache - a singleplayer client that switches worlds must never see
        // a different world's teams.
        com.aryangpt007.journeymode.data.GlobalDataHandler.invalidateCachedRoot();
        com.aryangpt007.journeymode.data.TeamDataHandler.load(event.getServer());
        // Item 3: identify this world before anything reads a catalog, so per-save
        // scope resolves against the right block from the very first query.
        com.aryangpt007.journeymode.data.SaveIdStamp.begin(
            event.getServer().getWorldPath(net.minecraft.world.storage.FolderName.ROOT).toFile());
        // Item 5: resolve every threshold once, here, inside the loading screen the player
        // is already sitting through. Recipes are datapack-driven and only exist by this
        // point - there is nothing to bake at mod-load time. Elapsed time is logged so it
        // is measurable rather than assumed.
        com.aryangpt007.journeymode.data.ThresholdTable.rebuild(
            event.getServer().getRecipeManager());
    }

    @SubscribeEvent
    public void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(DatapackThresholdLoader.INSTANCE);
    }

    @SubscribeEvent
    public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayerEntity) {
            ServerPlayerEntity player = (ServerPlayerEntity) event.getEntity();
            GlobalDataHandler.loadPlayerUnlocks(player);
            ConfigSyncHelper.pushToPlayer(player);
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

    /**
     * Writes are debounced (see GlobalDataHandler.markDirty), so an orderly shutdown or a
     * player leaving must force whatever is still pending out to disk. Without this the
     * last few deposits before quitting would sit unwritten.
     */
    @SubscribeEvent
    public void onServerStopping(FMLServerStoppingEvent event) {
        com.aryangpt007.journeymode.data.GlobalDataHandler.flush();
    }

    @SubscribeEvent
    public void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        com.aryangpt007.journeymode.data.GlobalDataHandler.flush();
    }
}
