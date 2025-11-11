package com.aryangpt007.journeymode.client;

import com.aryangpt007.journeymode.JourneyModeFabric;
import com.aryangpt007.journeymode.client.gui.JourneyModeScreen;
import com.aryangpt007.journeymode.network.packets.FabricOpenJourneyMenuPacket;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.MenuScreens;
import org.lwjgl.glfw.GLFW;

/**
 * Client-side initialization for Fabric
 */
public class JourneyModeClientFabric implements ClientModInitializer {
    
    // Define the keybind (configurable in Minecraft settings)
    public static final KeyMapping OPEN_JOURNEY_MODE_KEY = KeyBindingHelper.registerKeyBinding(
        new KeyMapping(
            "key.journeymode.open_menu",                    // Translation key
            GLFW.GLFW_KEY_J,                                // Default key (J)
            "key.categories.journeymode"                    // Category
        )
    );
    
    @Override
    public void onInitializeClient() {
        JourneyModeFabric.LOGGER.info("Journey Mode client initializing...");
        
        // Register screen
        MenuScreens.register(JourneyModeFabric.JOURNEY_MODE_MENU, JourneyModeScreen::new);
        
        // Register key handler
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (OPEN_JOURNEY_MODE_KEY.consumeClick()) {
                if (client.player != null) {
                    // Send packet to server to open menu
                    ClientPlayNetworking.send(new FabricOpenJourneyMenuPacket());
                }
            }
        });
        
        JourneyModeFabric.LOGGER.info("Journey Mode client initialized successfully!");
    }
}
