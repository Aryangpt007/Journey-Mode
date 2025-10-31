package com.aryangpt007.journeymode;

import com.aryangpt007.journeymode.client.ClientSetup;
import com.aryangpt007.journeymode.commands.JourneyModeCommand;
import com.aryangpt007.journeymode.events.JourneyModeEvents;
import com.aryangpt007.journeymode.menu.JourneyModeMenu;
import com.aryangpt007.journeymode.network.NetworkHandler;
import com.aryangpt007.journeymode.platform.neoforge.NeoForgeConfigHandler;
import com.aryangpt007.journeymode.platform.neoforge.NeoForgeDataHandler;
import com.mojang.logging.LogUtils;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.slf4j.Logger;

/**
 * Main mod class for Journey Mode - NeoForge 1.21.1
 * Multi-loader architecture v1.5.0
 */
@Mod(JourneyMode.MODID)
public class JourneyMode {
    public static final String MODID = "journeymode";
    public static final Logger LOGGER = LogUtils.getLogger();

    // Registration for menus
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, MODID);
    public static final DeferredHolder<MenuType<?>, MenuType<JourneyModeMenu>> JOURNEY_MODE_MENU = 
        MENUS.register("journey_mode_menu", () -> new MenuType<>((id, inventory) -> new JourneyModeMenu(id, inventory), net.minecraft.world.flag.FeatureFlags.VANILLA_SET));

    public JourneyMode(IEventBus modEventBus, ModContainer modContainer) {
        LOGGER.info("Journey Mode is loading... (Multi-loader architecture v1.5.0)");

        // Register configuration
        NeoForgeConfigHandler.register(modContainer);
        modEventBus.addListener(NeoForgeConfigHandler::onConfigLoad);

        // Register deferred registries
        MENUS.register(modEventBus);
        NeoForgeDataHandler.ATTACHMENTS.register(modEventBus);

        // Register network packets
        NetworkHandler.register(modEventBus);

        // Register events
        NeoForge.EVENT_BUS.register(new JourneyModeEvents());
        
        // Register commands
        NeoForge.EVENT_BUS.addListener((RegisterCommandsEvent event) -> {
            JourneyModeCommand.register(event.getDispatcher());
        });

        // Client-side setup
        if (FMLEnvironment.dist == Dist.CLIENT) {
            modEventBus.addListener(ClientSetup::onClientSetup);
            modEventBus.addListener(ClientSetup::registerScreens);
            modEventBus.addListener(ClientSetup::registerKeyMappings);
        }

        LOGGER.info("Journey Mode loaded successfully!");
    }

    public static Component translatable(String key, Object... args) {
        return Component.translatable("journeymode." + key, args);
    }
}
