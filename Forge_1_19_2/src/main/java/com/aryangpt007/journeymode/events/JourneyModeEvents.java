package com.aryangpt007.journeymode.events;

import com.aryangpt007.journeymode.JourneyMode;
import com.aryangpt007.journeymode.data.DatapackThresholdLoader;
import com.aryangpt007.journeymode.data.GlobalDataHandler;
import com.aryangpt007.journeymode.data.JourneyDataCapabilityProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
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
    public void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(DatapackThresholdLoader.INSTANCE);
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        // §1 Shared Team Catalogs: (re)load the per-world teams file for this session, clearing
        // any previous world's cache - a singleplayer client that switches worlds must never see
        // a different world's teams.
        com.aryangpt007.journeymode.data.GlobalDataHandler.invalidateCachedRoot();
        com.aryangpt007.journeymode.data.TeamDataHandler.load(event.getServer());
        // Item 3: identify this world before anything reads a catalog, so per-save
        // scope resolves against the right block from the very first query.
        com.aryangpt007.journeymode.data.SaveIdStamp.begin(
            event.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).toFile());
        // Item 5: resolve every threshold once, here, inside the loading screen the player
        // is already sitting through. Recipes are datapack-driven and only exist by this
        // point - there is nothing to bake at mod-load time. Elapsed time is logged so it
        // is measurable rather than assumed.
        com.aryangpt007.journeymode.data.ThresholdTable.rebuild(
            event.getServer().getRecipeManager(), event.getServer().registryAccess());
    }

    @SubscribeEvent
    public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            GlobalDataHandler.loadPlayerUnlocks(player);
            com.aryangpt007.journeymode.network.ConfigSyncHelper.pushToPlayer(player);
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

    /**
     * Writes are debounced (see GlobalDataHandler.markDirty), so an orderly shutdown or a
     * player leaving must force whatever is still pending out to disk. Without this the
     * last few deposits before quitting would sit unwritten.
     */
    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        com.aryangpt007.journeymode.data.GlobalDataHandler.flush();
    }

    @SubscribeEvent
    public void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        com.aryangpt007.journeymode.data.GlobalDataHandler.flush();
    }
}
