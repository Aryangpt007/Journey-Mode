package com.aryangpt007.journeymode;

import com.aryangpt007.journeymode.commands.JourneyModeCommand;
import com.aryangpt007.journeymode.config.ConfigHandler;
import com.aryangpt007.journeymode.data.DatapackThresholdLoader;
import com.aryangpt007.journeymode.data.GlobalDataHandler;
import com.aryangpt007.journeymode.menu.JourneyModeMenu;
import com.aryangpt007.journeymode.network.ConfigSyncHelper;
import com.aryangpt007.journeymode.network.FabricNetworkHandler;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class JourneyMode implements ModInitializer {
    public static final String MODID = "journeymode";
    public static final Logger LOGGER = LogManager.getLogger("journeymode");

    // Menu registration
    public static final MenuType<JourneyModeMenu> JOURNEY_MODE_MENU = Registry.register(
        BuiltInRegistries.MENU,
        ResourceLocation.fromNamespaceAndPath(MODID, "journey_mode_menu"),
        new MenuType<>(
            (id, inventory) -> new JourneyModeMenu(id, inventory),
            FeatureFlags.VANILLA_SET
        )
    );

    @Override
    public void onInitialize() {
        LOGGER.info("Journey Mode is loading on Fabric...");

        // Initialize configs
        ConfigHandler.initialize();

        // Register common network and packet receivers
        FabricNetworkHandler.registerCommon();

        // §3 datapack threshold packs: data/<namespace>/journeymode/thresholds/*.json
        ResourceManagerHelper.get(PackType.SERVER_DATA).registerReloadListener(DatapackThresholdLoader.INSTANCE);

        // Register commands
        CommandRegistrationCallback.EVENT.register((dispatcher, buildContext, environment) -> {
            JourneyModeCommand.register(dispatcher, buildContext);
        });

        // §1 Shared Team Catalogs: (re)load the per-world teams file for this session, clearing
        // any previous world's cache - a singleplayer client that switches worlds must never see
        // a different world's teams.
        ServerLifecycleEvents.SERVER_STARTING.register(server ->
            com.aryangpt007.journeymode.data.TeamDataHandler.load(server));
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STARTING.register(server ->
            com.aryangpt007.journeymode.data.GlobalDataHandler.invalidateCachedRoot());
        // Item 3: identify this world before anything reads a catalog, so per-save scope
        // resolves against the right block from the very first query.
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STARTING.register(server ->
            com.aryangpt007.journeymode.data.SaveIdStamp.begin(
                server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).toFile()));
        // Item 5: bake every threshold once at recipe-load, not during play. Recipes are
        // datapack-driven, so they only exist from server start onwards - a mod-load-time
        // bake would find an empty RecipeManager.
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STARTING.register(server ->
            com.aryangpt007.journeymode.data.ThresholdTable.rebuild(server.getRecipeManager(), server.registryAccess()));
        // Debounced writes (see GlobalDataHandler.markDirty) must be forced out when a
        // player leaves or the server stops, or the last deposits before quitting are lost.
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPING.register(server ->
            com.aryangpt007.journeymode.data.GlobalDataHandler.flush());
        net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
            com.aryangpt007.journeymode.data.GlobalDataHandler.flush());

        // Register player join handler to load global unlocks JSON in real-time, then push the
        // resolved config rules (§2) so the client's ConfigHandler matches the server's.
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            GlobalDataHandler.loadPlayerUnlocks(handler.getPlayer());
            ConfigSyncHelper.pushToPlayer(handler.getPlayer());
        });

        LOGGER.info("Journey Mode loaded successfully on Fabric!");
    }

    public static Component translatable(String key, Object... args) {
        return Component.translatable("journeymode." + key, args);
    }
}
