package com.aryangpt007.journeymode.client;

import com.aryangpt007.journeymode.JourneyMode;
import com.aryangpt007.journeymode.client.gui.JourneyModeScreen;
import com.aryangpt007.journeymode.network.NetworkHandler;
import com.aryangpt007.journeymode.network.packets.OpenJourneyMenuPacket;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import org.lwjgl.glfw.GLFW;

@OnlyIn(Dist.CLIENT)
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
        
        event.enqueueWork(() -> {
            MenuScreens.register(JourneyMode.JOURNEY_MODE_MENU.get(), JourneyModeScreen::new);
        });

        // Register client-side key handler
        MinecraftForge.EVENT_BUS.register(ClientKeyHandler.class);

        // Register progress tooltip handler (§9)
        MinecraftForge.EVENT_BUS.register(TooltipHandler.class);
    }
    
    public static void registerKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(OPEN_JOURNEY_MODE_KEY);
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
                data.updateFromSync(msg.getCollectedCounts(), msg.getUnlockedItems(), msg.getUnlockTimestamps(), msg.isEnabled(), msg.isShowTooltips(), msg.getTeamDisplayName());
                celebrateNewUnlocks(mc, data.getAndClearNewlyUnlocked());
            });
        }
    }

    /**
     * §11 Visual Polish: action-bar message on threshold crossing, driven purely
     * by client-side diffing (see JourneyDataAttachment.updateFromSync) - no dedicated
     * "newly_unlocked" packet field needed. A full graphical toast (with custom textures) is
     * deliberately out of scope for this pass - there's no art-asset pipeline in play here, so
     * this uses the same action-bar message style the rest of the mod already uses.
     */
    private static void celebrateNewUnlocks(Minecraft mc, java.util.Set<String> newlyUnlocked) {
        if (newlyUnlocked.isEmpty() || mc.player == null) return;

        if (newlyUnlocked.size() == 1) {
            String key = newlyUnlocked.iterator().next();
            net.minecraft.world.item.ItemStack stack = com.aryangpt007.journeymode.data.JourneyDataAttachment.itemStackFromKey(key);
            String name = stack.isEmpty() ? key : stack.getHoverName().getString();
            mc.player.displayClientMessage(net.minecraft.network.chat.Component.literal("§6Unlocked: " + name + "!"), true);
        } else {
            mc.player.displayClientMessage(net.minecraft.network.chat.Component.literal("§6Unlocked " + newlyUnlocked.size() + " items!"), true);
        }
    }
}
