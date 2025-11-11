package com.aryangpt007.journeymode;

import com.aryangpt007.journeymode.commands.JourneyModeCommand;
import com.aryangpt007.journeymode.events.FabricServerEvents;
import com.aryangpt007.journeymode.menu.JourneyModeMenu;
import com.aryangpt007.journeymode.network.FabricNetworkHandler;
import com.aryangpt007.journeymode.platform.fabric.FabricConfigHandler;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.MenuType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Main mod class for Journey Mode - Fabric 1.21.x
 * Multi-loader architecture v1.0.0
 */
public class JourneyModeFabric implements ModInitializer {
    public static final String MODID = "journeymode";
    public static final Logger LOGGER = LoggerFactory.getLogger(MODID);

    public static final MenuType<JourneyModeMenu> JOURNEY_MODE_MENU = Registry.register(
        BuiltInRegistries.MENU,
        ResourceLocation.fromNamespaceAndPath(MODID, "journey_mode_menu"),
        new MenuType<>((id, inventory) -> new JourneyModeMenu(id, inventory), net.minecraft.world.flag.FeatureFlags.VANILLA_SET)
    );

    @Override
    public void onInitialize() {
        LOGGER.info("Journey Mode is loading... (Fabric Multi-loader architecture v1.0.0)");

        // Initialize configuration
        FabricConfigHandler.initialize();

        // Register network packets
        FabricNetworkHandler.register();

        // Register events
        FabricServerEvents.register();

        // Register commands
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            JourneyModeCommand.register(dispatcher);
        });

        LOGGER.info("Journey Mode loaded successfully!");
    }

    public static Component translatable(String key, Object... args) {
        return Component.translatable("journeymode." + key, args);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }
}
