package com.aryangpt007.journeymode.client;

import com.aryangpt007.journeymode.JourneyMode;
import com.aryangpt007.journeymode.client.gui.JourneyModeScreen;
import com.aryangpt007.journeymode.events.JourneyModeEvents;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.lwjgl.glfw.GLFW;

public class ClientSetup {
    
    // Define the keybind (configurable in Minecraft settings)
    public static final KeyMapping OPEN_JOURNEY_MODE_KEY = new KeyMapping(
        "key.journeymode.open_menu",                    // Translation key
        InputConstants.Type.KEYSYM,                     // Input type
        GLFW.GLFW_KEY_J,                                // Default key (J)
        "key.categories.journeymode"                    // Category
    );
    
    public static void onClientSetup(FMLClientSetupEvent event) {
        JourneyMode.LOGGER.info("Journey Mode client setup");
        
        // Register client-side key handler
        NeoForge.EVENT_BUS.register(JourneyModeEvents.ClientKeyHandler.class);
    }
    
    public static void registerKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(OPEN_JOURNEY_MODE_KEY);
    }

    public static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(JourneyMode.JOURNEY_MODE_MENU.get(), JourneyModeScreen::new);
    }
}
