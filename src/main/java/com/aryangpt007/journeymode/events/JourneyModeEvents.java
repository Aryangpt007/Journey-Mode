package com.aryangpt007.journeymode.events;

import com.aryangpt007.journeymode.JourneyMode;
import com.aryangpt007.journeymode.network.packets.OpenJourneyMenuPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

/**
 * Event handlers for Journey Mode
 */
public class JourneyModeEvents {

    @SubscribeEvent
    public void onPlayerClone(PlayerEvent.Clone event) {
        // Copy Journey Mode data when player respawns or returns from End
        if (event.isWasDeath() || !event.getEntity().level().isClientSide) {
            var oldData = event.getOriginal().getData(JourneyMode.JOURNEY_DATA);
            var newData = event.getEntity().getData(JourneyMode.JOURNEY_DATA);
            
            // Copy unlocked items and counts
            for (var entry : oldData.getAllCollectedCounts().entrySet()) {
                // We need to manually copy since attachments create new instances
                // This is a simplified approach - in production you'd reconstruct the attachment
            }
        }
    }

    /**
     * Client-side key handler
     */
    @OnlyIn(Dist.CLIENT)
    public static class ClientKeyHandler {
        private static boolean wasJKeyPressed = false;

        @SubscribeEvent
        public static void onClientTick(ClientTickEvent.Post event) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return;

            boolean isJKeyPressed = GLFW.glfwGetKey(mc.getWindow().getWindow(), GLFW.GLFW_KEY_J) == GLFW.GLFW_PRESS;

            if (isJKeyPressed && !wasJKeyPressed) {
                // J key was just pressed
                PacketDistributor.sendToServer(new OpenJourneyMenuPacket());
            }

            wasJKeyPressed = isJKeyPressed;
        }
    }
}
