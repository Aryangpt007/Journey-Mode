package com.aryangpt007.journeymode;

import com.aryangpt007.journeymode.commands.JourneyModeCommand;
import com.aryangpt007.journeymode.config.ConfigHandler;
import com.aryangpt007.journeymode.data.GlobalDataHandler;
import com.aryangpt007.journeymode.menu.JourneyModeMenu;
import com.aryangpt007.journeymode.network.FabricNetworkHandler;
import com.mojang.logging.LogUtils;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.Registry;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.MenuType;
import org.slf4j.Logger;

public class JourneyMode implements ModInitializer {
    public static final String MODID = "journeymode";
    public static final Logger LOGGER = LogUtils.getLogger();

    // Registry for the Journey Mode MenuType
    public static final MenuType<JourneyModeMenu> JOURNEY_MODE_MENU = new MenuType<>(
        JourneyModeMenu::new
    );

    @Override
    public void onInitialize() {
        LOGGER.info("Journey Mode Fabric is loading...");

        // Register configuration
        ConfigHandler.initialize();

        // Register custom menu type
        Registry.register(
            Registry.MENU, 
            new ResourceLocation(MODID, "journey_mode_menu"), 
            JOURNEY_MODE_MENU
        );

        // Register common network receivers
        FabricNetworkHandler.registerCommon();

        // Register Brigadier commands
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            JourneyModeCommand.register(dispatcher);
        });

        // Register server play connection events for loading and clearing player unlocks
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            GlobalDataHandler.loadPlayerUnlocks(handler.player);
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
