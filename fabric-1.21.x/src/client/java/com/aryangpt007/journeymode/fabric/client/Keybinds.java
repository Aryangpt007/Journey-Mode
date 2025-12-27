package com.aryangpt007.journeymode.fabric.client;

import com.aryangpt007.journeymode.fabric.network.packets.OpenJourneyMenuPacket;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

public final class Keybinds {

    private static KeyMapping OPEN_MENU;

    public static void init() {
        OPEN_MENU = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.journeymode.open_menu",          // translation key
                GLFW.GLFW_KEY_J,                      // default key
                "category.journeymode"                // category translation key
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (OPEN_MENU.consumeClick()) {
                // Only makes sense in-world; ignore on title screen
                if (client.player == null) return;

                //JourneyModeFabric.LOGGER.info("Journey Mode key pressed, requesting menu open...");
                ClientPlayNetworking.send(new OpenJourneyMenuPacket());
            }
        });
    }

    private Keybinds() {}
}
