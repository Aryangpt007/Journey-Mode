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
    public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player) {
            com.aryangpt007.journeymode.data.GlobalDataHandler.loadPlayerUnlocks(player);
        }
    }

    @SubscribeEvent
    public void onPlayerClone(PlayerEvent.Clone event) {
        if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer newPlayer) {
            var oldData = event.getOriginal().getData(JourneyMode.JOURNEY_DATA);
            var newData = newPlayer.getData(JourneyMode.JOURNEY_DATA);
            if (oldData != null && newData != null) {
                newData.copyFrom(oldData);
                com.aryangpt007.journeymode.data.GlobalDataHandler.syncToClient(newPlayer, newData);
            }
        }
    }

    /**
     * Client-side key handler
     */
    @OnlyIn(Dist.CLIENT)
    public static class ClientKeyHandler {
        @SubscribeEvent
        public static void onClientTick(ClientTickEvent.Post event) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return;

            // Use the configurable keybind instead of hardcoded key
            if (com.aryangpt007.journeymode.client.ClientSetup.OPEN_JOURNEY_MODE_KEY.consumeClick()) {
                // Key was just pressed
                PacketDistributor.sendToServer(new OpenJourneyMenuPacket());
            }
        }
    }
}
