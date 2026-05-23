package com.aryangpt007.journeymode;

import com.aryangpt007.journeymode.commands.JourneyModeCommand;
import com.aryangpt007.journeymode.config.ConfigHandler;
import com.aryangpt007.journeymode.data.GlobalDataHandler;
import com.aryangpt007.journeymode.menu.JourneyModeMenu;
import com.aryangpt007.journeymode.network.FabricNetworkHandler;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v1.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.screenhandler.v1.ScreenHandlerRegistry;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.MenuType;

public class JourneyMode implements ModInitializer {
    public static final String MODID = "journeymode";
    public static final Logger LOGGER = LogManager.getLogger("journeymode");

    // Registry for the Journey Mode MenuType
    public static final MenuType<JourneyModeMenu> JOURNEY_MODE_MENU = ScreenHandlerRegistry.registerSimple(
        new ResourceLocation(MODID, "journey_mode_menu"),
        JourneyModeMenu::new
    );

    @Override
    public void onInitialize() {
        LOGGER.info("Journey Mode Fabric is loading...");

        // Register configuration
        ConfigHandler.initialize();



        // Register common network receivers
        FabricNetworkHandler.registerCommon();

        // Register Brigadier commands
        CommandRegistrationCallback.EVENT.register((dispatcher, dedicated) -> {
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
        return new TranslatableComponent("journeymode." + key, args);
    }
}
