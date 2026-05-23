package com.aryangpt007.journeymode;

import com.aryangpt007.journeymode.commands.JourneyModeCommand;
import com.aryangpt007.journeymode.config.ConfigHandler;
import com.aryangpt007.journeymode.data.IJourneyData;
import com.aryangpt007.journeymode.data.JourneyData;
import com.aryangpt007.journeymode.data.JourneyDataCapabilityProvider;
import com.aryangpt007.journeymode.events.JourneyModeEvents;
import com.aryangpt007.journeymode.network.NetworkHandler;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.Mod.Instance;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(modid = JourneyMode.MODID, name = JourneyMode.NAME, version = JourneyMode.VERSION)
public class JourneyMode {
    public static final String MODID = "journeymode";
    public static final String NAME = "Journey Mode";
    public static final String VERSION = "1.6.0N-1.12.2";

    public static final Logger LOGGER = LogManager.getLogger("journeymode");

    @Instance(MODID)
    public static JourneyMode instance;

    @EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        LOGGER.info("Journey Mode pre-initialization...");

        // Register capability
        CapabilityManager.INSTANCE.register(
            IJourneyData.class,
            new JourneyDataCapabilityProvider.Storage(),
            JourneyData::new
        );

        // Register configs
        ConfigHandler.register();

        // Register network
        NetworkHandler.register();
    }

    @EventHandler
    public void init(FMLInitializationEvent event) {
        LOGGER.info("Journey Mode initialization...");

        // Register GUI handler
        NetworkRegistry.INSTANCE.registerGuiHandler(this, new GuiHandler());

        // Register event listeners
        MinecraftForge.EVENT_BUS.register(new JourneyModeEvents());

        // Client-side setup
        if (net.minecraftforge.fml.common.FMLCommonHandler.instance().getSide() == net.minecraftforge.fml.relauncher.Side.CLIENT) {
            com.aryangpt007.journeymode.client.ClientSetup.init();
        }
    }

    @EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        LOGGER.info("Registering Journey Mode commands...");
        event.registerServerCommand(new JourneyModeCommand());
    }

    public static ITextComponent translatable(String key, Object... args) {
        return new TextComponentTranslation("journeymode." + key, args);
    }
}
