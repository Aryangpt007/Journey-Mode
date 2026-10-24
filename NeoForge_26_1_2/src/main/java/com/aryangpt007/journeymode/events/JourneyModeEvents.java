package com.aryangpt007.journeymode.events;

import com.aryangpt007.journeymode.JourneyMode;
import com.aryangpt007.journeymode.data.DatapackThresholdLoader;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.AddServerReloadListenersEvent;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

/**
 * Event handlers for Journey Mode (Purely server/common safe)
 */
public class JourneyModeEvents {

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
    public void onAddReloadListeners(AddServerReloadListenersEvent event) {
        event.addListener(
            net.minecraft.resources.Identifier.fromNamespaceAndPath(JourneyMode.MODID, "thresholds"),
            DatapackThresholdLoader.INSTANCE);
    }

    /**
     * Fires for a joining player and again for everyone after a /reload - the two moments a
     * client's copy of the recipe-derived thresholds can be missing or stale. The client is not
     * sent recipes any more, so it relies on this table when connected to a dedicated server.
     */
    @SubscribeEvent
    public void onDatapackSync(OnDatapackSyncEvent event) {
        net.minecraft.server.MinecraftServer server = event.getPlayerList().getServer();
        // A /reload dropped the shared calculator (see DatapackThresholdLoader); rebind before
        // reading from it. A no-op when it is already bound to these recipes.
        com.aryangpt007.journeymode.data.ThresholdTable.bind(server.getRecipeManager(), server.registryAccess());
        com.aryangpt007.journeymode.network.packets.ThresholdSyncPacket packet =
            new com.aryangpt007.journeymode.network.packets.ThresholdSyncPacket(
                com.aryangpt007.journeymode.data.ThresholdTable.snapshotBaseThresholds());
        event.getRelevantPlayers().forEach(player ->
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, packet));
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

    /**
     * Writes are debounced (see GlobalDataHandler.markDirty), so an orderly shutdown or a
     * player leaving must force whatever is still pending out to disk. Without this the
     * last few deposits before quitting would sit unwritten.
     */
    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        com.aryangpt007.journeymode.data.GlobalDataHandler.flush();
        // The calculator is static and, in singleplayer, visible to the client in the same JVM.
        // Drop it with the world it was built from so a later connection to a dedicated server
        // falls through to that server's synced thresholds instead of this world's stale ones.
        com.aryangpt007.journeymode.data.ThresholdTable.invalidate();
    }

    @SubscribeEvent
    public void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        com.aryangpt007.journeymode.data.GlobalDataHandler.flush();
    }
}
