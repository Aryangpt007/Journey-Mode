package com.aryangpt007.journeymode.client;

import com.aryangpt007.journeymode.JourneyMode;
import com.aryangpt007.journeymode.client.gui.JourneyModeScreen;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

public class ClientSetup {
    
    public static void onClientSetup(FMLClientSetupEvent event) {
        JourneyMode.LOGGER.info("Journey Mode client setup complete");
    }

    public static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(JourneyMode.JOURNEY_MODE_MENU.get(), JourneyModeScreen::new);
        JourneyMode.LOGGER.info("Journey Mode screen registered");
    }
}
