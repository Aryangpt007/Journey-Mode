package com.aryangpt007.journeymode;

import com.aryangpt007.journeymode.commands.JourneyModeCommand;
import com.aryangpt007.journeymode.config.ConfigHandler;
import com.aryangpt007.journeymode.data.DatapackThresholdLoader;
import com.aryangpt007.journeymode.data.GlobalDataHandler;
import com.aryangpt007.journeymode.menu.JourneyModeMenu;
import com.aryangpt007.journeymode.network.ConfigSyncHelper;
import com.aryangpt007.journeymode.network.FabricNetworkHandler;
import com.mojang.logging.LogUtils;
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
import org.slf4j.Logger;

public class JourneyMode implements ModInitializer {
    public static final String MODID = "journeymode";
    public static final Logger LOGGER = LogUtils.getLogger();

    // Registry for the Journey Mode MenuType
    public static final MenuType<JourneyModeMenu> JOURNEY_MODE_MENU = new MenuType<>(
        JourneyModeMenu::new, 
        FeatureFlags.VANILLA_SET
    );

    @Override
    public void onInitialize() {
        LOGGER.info("Journey Mode Fabric is loading...");

        // Register configuration
        ConfigHandler.initialize();

        // Register custom menu type
        Registry.register(
            BuiltInRegistries.MENU, 
            new ResourceLocation(MODID, "journey_mode_menu"), 
            JOURNEY_MODE_MENU
        );

        // Register common network receivers
        FabricNetworkHandler.registerCommon();

        // §3 datapack threshold packs: data/<namespace>/journeymode/thresholds/*.json
        ResourceManagerHelper.get(PackType.SERVER_DATA).registerReloadListener(DatapackThresholdLoader.INSTANCE);

        // Register Brigadier commands
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            JourneyModeCommand.register(dispatcher, registryAccess);
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

        // Register server play connection events for loading and clearing player unlocks
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            GlobalDataHandler.loadPlayerUnlocks(handler.player);
            ConfigSyncHelper.pushToPlayer(handler.player);
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            GlobalDataHandler.clearPlayerData(handler.player.getUUID());
        });

        LOGGER.info("Journey Mode Fabric loaded successfully!");
    }

    public static Component translatable(String key, Object... args) {
        return Component.translatable("journeymode." + key, args);
    }
}
