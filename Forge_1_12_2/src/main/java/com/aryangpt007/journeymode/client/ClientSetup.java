package com.aryangpt007.journeymode.client;

import com.aryangpt007.journeymode.network.NetworkHandler;
import com.aryangpt007.journeymode.network.packets.OpenJourneyMenuPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.input.Keyboard;

@SideOnly(Side.CLIENT)
public class ClientSetup {
    
    public static final KeyBinding OPEN_JOURNEY_MODE_KEY = new KeyBinding(
        "key.journeymode.open_menu",
        Keyboard.KEY_J,
        "key.categories.journeymode"
    );

    public static void init() {
        // Register key binding
        ClientRegistry.registerKeyBinding(OPEN_JOURNEY_MODE_KEY);

        // Register client tick event listener
        MinecraftForge.EVENT_BUS.register(new ClientKeyHandler());
    }

    public static class ClientKeyHandler {
        @SubscribeEvent
        public void onClientTick(TickEvent.ClientTickEvent event) {
            if (event.phase != TickEvent.Phase.END) return;

            Minecraft mc = Minecraft.getMinecraft();
            if (mc.player == null) return;

            if (OPEN_JOURNEY_MODE_KEY.isPressed()) {
                NetworkHandler.sendToServer(new OpenJourneyMenuPacket());
            }
        }
    }
}
