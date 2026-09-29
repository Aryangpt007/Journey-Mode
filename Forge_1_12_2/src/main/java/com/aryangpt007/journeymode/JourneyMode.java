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

        // §1 Shared Team Catalogs: (re)load the per-world teams file for this session, clearing
        // any previous world's cache - a singleplayer client that switches worlds must never see
        // a different world's teams.
        com.aryangpt007.journeymode.data.GlobalDataHandler.invalidateCachedRoot();
        com.aryangpt007.journeymode.data.TeamDataHandler.load(event.getServer());
        // Item 3: identify this world before anything reads a catalog, so per-save
        // scope resolves against the right block from the very first query.
        com.aryangpt007.journeymode.data.SaveIdStamp.begin(
            event.getServer().getEntityWorld().getSaveHandler().getWorldDirectory());
        // Item 5: resolve every threshold once, here, inside the loading screen the player
        // is already sitting through. Recipes are datapack-driven and only exist by this
        // point - there is nothing to bake at mod-load time. Elapsed time is logged so it
        // is measurable rather than assumed.
        com.aryangpt007.journeymode.data.ThresholdTable.rebuild();
    }

    public static ITextComponent translatable(String key, Object... args) {
        return new TextComponentTranslation("journeymode." + key, args);
    }

    @Mod.EventHandler
    /**
     * Writes are debounced (see GlobalDataHandler.markDirty), so an orderly shutdown or a
     * player leaving must force whatever is still pending out to disk. Without this the
     * last few deposits before quitting would sit unwritten.
     */
    public void serverStopping(net.minecraftforge.fml.common.event.FMLServerStoppingEvent event) {
        com.aryangpt007.journeymode.data.GlobalDataHandler.flush();
    }
}
