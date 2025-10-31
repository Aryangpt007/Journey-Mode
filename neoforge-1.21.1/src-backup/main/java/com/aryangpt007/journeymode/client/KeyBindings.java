package com.aryangpt007.journeymode.client;

import com.aryangpt007.journeymode.JourneyMode;
import com.aryangpt007.journeymode.network.packets.OpenJourneyMenuPacket;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.common.util.Lazy;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

/**
 * Client-side keybind handler for Journey Mode
 */
public class KeyBindings {
    
    public static final Lazy<KeyMapping> OPEN_JOURNEY_MODE = Lazy.of(() -> new KeyMapping(
        "key.journeymode.open_menu",
        InputConstants.Type.KEYSYM,
        GLFW.GLFW_KEY_J,
        "key.categories.journeymode"
    ));

    public static void register(RegisterKeyMappingsEvent event) {
        event.register(OPEN_JOURNEY_MODE.get());
        JourneyMode.LOGGER.info("Journey Mode keybindings registered");
    }

    public static class ClientEvents {
        @SubscribeEvent
        public static void onClientTick(PlayerTickEvent.Post event) {
            if (event.getEntity().level().isClientSide) {
                while (OPEN_JOURNEY_MODE.get().consumeClick()) {
                    PacketDistributor.sendToServer(new OpenJourneyMenuPacket());
                    JourneyMode.LOGGER.debug("Journey Mode menu open packet sent");
                }
            }
        }
    }
}
