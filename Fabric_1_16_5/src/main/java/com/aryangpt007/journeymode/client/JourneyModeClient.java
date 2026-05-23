package com.aryangpt007.journeymode.client;

import com.aryangpt007.journeymode.JourneyMode;
import com.aryangpt007.journeymode.client.gui.JourneyModeScreen;
import com.aryangpt007.journeymode.network.FabricNetworkHandler;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.fabricmc.fabric.api.client.screenhandler.v1.ScreenRegistry;
import org.lwjgl.glfw.GLFW;

public class JourneyModeClient implements ClientModInitializer {
    public static final KeyMapping OPEN_JOURNEY_MODE_KEY = new KeyMapping(
        "key.journeymode.open_menu",
        InputConstants.Type.KEYSYM,
        GLFW.GLFW_KEY_J,
        "key.categories.journeymode"
    );

    @Override
    public void onInitializeClient() {
        // Register screens
        ScreenRegistry.register(JourneyMode.JOURNEY_MODE_MENU, JourneyModeScreen::new);

        // Register key mapping
        KeyBindingHelper.registerKeyBinding(OPEN_JOURNEY_MODE_KEY);

        // Register client receivers
        FabricNetworkHandler.registerClient();

        // Register client tick events
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player != null) {
                if (OPEN_JOURNEY_MODE_KEY.consumeClick()) {
                    FabricNetworkHandler.openJourneyMenu();
                }
            }
        });
    }
}
