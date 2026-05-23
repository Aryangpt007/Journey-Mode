package com.aryangpt007.journeymode;

import com.aryangpt007.journeymode.commands.JourneyModeCommand;
import com.aryangpt007.journeymode.config.ConfigHandler;
import com.aryangpt007.journeymode.data.GlobalDataHandler;
import com.aryangpt007.journeymode.menu.JourneyModeMenu;
import com.aryangpt007.journeymode.network.FabricNetworkHandler;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
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

        // Register commands
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            JourneyModeCommand.register(dispatcher);
        });

        // Register player join handler to load global unlocks JSON in real-time
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            GlobalDataHandler.loadPlayerUnlocks(handler.getPlayer());
        });

        LOGGER.info("Journey Mode loaded successfully on Fabric!");
    }

    public static Component translatable(String key, Object... args) {
        return Component.translatable("journeymode." + key, args);
    }
}
