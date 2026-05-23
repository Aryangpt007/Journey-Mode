package com.aryangpt007.journeymode;

import com.aryangpt007.journeymode.client.ClientSetup;
import com.aryangpt007.journeymode.commands.JourneyModeCommand;
import com.aryangpt007.journeymode.config.ConfigHandler;
import com.aryangpt007.journeymode.data.JourneyDataAttachment;
import com.aryangpt007.journeymode.data.JourneyDataCapabilityProvider;
import com.aryangpt007.journeymode.events.JourneyModeEvents;
import com.aryangpt007.journeymode.menu.JourneyModeMenu;
import com.aryangpt007.journeymode.network.NetworkHandler;
import com.mojang.logging.LogUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.slf4j.Logger;

@Mod(JourneyMode.MODID)
public class JourneyMode {
    public static final String MODID = "journeymode";
    public static final Logger LOGGER = LogUtils.getLogger();

    // Registration for menus
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(ForgeRegistries.MENU_TYPES, MODID);
    public static final RegistryObject<MenuType<JourneyModeMenu>> JOURNEY_MODE_MENU = 
        MENUS.register("journey_mode_menu", () -> new MenuType<>(JourneyModeMenu::new));

    public JourneyMode() {
        LOGGER.info("Journey Mode is loading...");

        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        // Register configuration
        ConfigHandler.register();
        modEventBus.addListener(ConfigHandler::onConfigLoad);

        // Register deferred registries
        MENUS.register(modEventBus);

        // Register capabilities
        modEventBus.addListener(this::registerCapabilities);

        // Register network packets
        NetworkHandler.register();

        // Register events
        MinecraftForge.EVENT_BUS.register(new JourneyModeEvents());
        
        // Register commands
        MinecraftForge.EVENT_BUS.addListener(this::registerCommands);

        // Client-side setup
        if (FMLEnvironment.dist == Dist.CLIENT) {
            modEventBus.addListener(ClientSetup::onClientSetup);
            modEventBus.addListener(ClientSetup::registerKeyMappings);
        }

        LOGGER.info("Journey Mode loaded successfully!");
    }

    private void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.register(JourneyDataAttachment.class);
    }

    private void registerCommands(RegisterCommandsEvent event) {
        JourneyModeCommand.register(event.getDispatcher());
    }

    public static Component translatable(String key, Object... args) {
        return Component.translatable("journeymode." + key, args);
    }
}
