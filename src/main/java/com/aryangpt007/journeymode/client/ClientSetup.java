package com.aryangpt007.journeymode.client;

import com.aryangpt007.journeymode.JourneyMode;
import com.aryangpt007.journeymode.client.gui.JourneyModeScreen;
import com.aryangpt007.journeymode.events.JourneyModeEvents;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.common.NeoForge;

public class ClientSetup {
    
    public static void onClientSetup(FMLClientSetupEvent event) {
        JourneyMode.LOGGER.info("Journey Mode client setup");
        
        // Register client-side key handler
        NeoForge.EVENT_BUS.register(JourneyModeEvents.ClientKeyHandler.class);
    }

    public static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(JourneyMode.JOURNEY_MODE_MENU.get(), JourneyModeScreen::new);
    }
}
