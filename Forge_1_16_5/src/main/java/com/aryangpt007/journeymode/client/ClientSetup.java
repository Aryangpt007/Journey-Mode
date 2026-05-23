package com.aryangpt007.journeymode.client;

import com.aryangpt007.journeymode.JourneyMode;
import com.aryangpt007.journeymode.client.gui.JourneyModeScreen;
import com.aryangpt007.journeymode.network.NetworkHandler;
import com.aryangpt007.journeymode.network.packets.OpenJourneyMenuPacket;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScreenManager;
import net.minecraft.client.util.InputMappings;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import org.lwjgl.glfw.GLFW;

@OnlyIn(Dist.CLIENT)
public class ClientSetup {
    
    // Define the keybind (configurable in Minecraft settings)
    public static final KeyBinding OPEN_JOURNEY_MODE_KEY = new KeyBinding(
        "key.journeymode.open_menu",                    // Translation key
        InputMappings.Type.KEYSYM,                     // Input type
        GLFW.GLFW_KEY_J,                                // Default key (J)
        "key.categories.journeymode"                    // Category
    );
    
    public static void onClientSetup(FMLClientSetupEvent event) {
        JourneyMode.LOGGER.info("Journey Mode client setup");
        net.minecraftforge.fml.client.registry.ClientRegistry.registerKeyBinding(OPEN_JOURNEY_MODE_KEY);
        
        event.enqueueWork(() -> {
            ScreenManager.register(JourneyMode.JOURNEY_MODE_MENU.get(), JourneyModeScreen::new);
        });

        // Register client-side key handler
        MinecraftForge.EVENT_BUS.register(ClientKeyHandler.class);
    }
    
    

    /**
     * Client-side key handler nested inside client setup class to avoid server-side class loading issues
     */
    public static class ClientKeyHandler {
        @SubscribeEvent
        public static void onClientTick(TickEvent.ClientTickEvent event) {
            if (event.phase != TickEvent.Phase.END) return;
            
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return;

            // Use the configurable keybind instead of hardcoded key
            if (OPEN_JOURNEY_MODE_KEY.consumeClick()) {
                // Key was just pressed, send packet to server
                NetworkHandler.CHANNEL.sendToServer(new OpenJourneyMenuPacket());
            }
        }
    }

    public static void handleSync(com.aryangpt007.journeymode.network.packets.SyncJourneyDataPacket msg) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.getCapability(com.aryangpt007.journeymode.data.JourneyDataCapabilityProvider.JOURNEY_DATA_CAPABILITY).ifPresent(data -> {
                data.updateFromSync(msg.getCollectedCounts(), msg.getUnlockedItems(), msg.getUnlockTimestamps());
            });
        }
    }
}
