package com.aryangpt007.journeymode;

import net.minecraft.util.text.TranslationTextComponent;

import com.aryangpt007.journeymode.client.ClientSetup;
import com.aryangpt007.journeymode.commands.JourneyModeCommand;
import com.aryangpt007.journeymode.config.ConfigHandler;
import com.aryangpt007.journeymode.data.JourneyDataAttachment;
import com.aryangpt007.journeymode.data.JourneyDataCapabilityProvider;
import com.aryangpt007.journeymode.events.JourneyModeEvents;
import com.aryangpt007.journeymode.menu.JourneyModeMenu;
import com.aryangpt007.journeymode.network.NetworkHandler;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.inventory.container.ContainerType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.fml.RegistryObject;

@Mod(JourneyMode.MODID)
public class JourneyMode {
    public static final String MODID = "journeymode";
    public static final Logger LOGGER = LogManager.getLogger("journeymode");

    // Registration for menus
    public static final DeferredRegister<ContainerType<?>> MENUS = DeferredRegister.create(ForgeRegistries.CONTAINERS, MODID);
    public static final RegistryObject<ContainerType<JourneyModeMenu>> JOURNEY_MODE_MENU = 
        MENUS.register("journey_mode_menu", () -> net.minecraftforge.common.extensions.IForgeContainerType.create((windowId, inv, data) -> new JourneyModeMenu(windowId, inv)));

    public JourneyMode() {
        LOGGER.info("Journey Mode is loading...");

        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        // Register configuration
        ConfigHandler.register();
        modEventBus.addListener(ConfigHandler::onConfigLoad);

        // Register deferred registries
        MENUS.register(modEventBus);

        // Register setup event
        modEventBus.addListener(this::setup);

        // Register network packets
        NetworkHandler.register();

        // Register events
        MinecraftForge.EVENT_BUS.register(new JourneyModeEvents());
        
        // Register commands
        MinecraftForge.EVENT_BUS.addListener(this::registerCommands);

        // Client-side setup
        if (FMLEnvironment.dist == Dist.CLIENT) {
            modEventBus.addListener(ClientSetup::onClientSetup);
        }

        LOGGER.info("Journey Mode loaded successfully!");
    }

    private void setup(final net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent event) {
        net.minecraftforge.common.capabilities.CapabilityManager.INSTANCE.register(
            JourneyDataAttachment.class,
            new com.aryangpt007.journeymode.data.JourneyDataCapabilityProvider.Storage(),
            JourneyDataAttachment::new
        );
    }

    private void registerCommands(RegisterCommandsEvent event) {
        JourneyModeCommand.register(event.getDispatcher());
    }

    public static ITextComponent translatable(String key, Object... args) {
        return new TranslationTextComponent("journeymode." + key, args);
    }
}
