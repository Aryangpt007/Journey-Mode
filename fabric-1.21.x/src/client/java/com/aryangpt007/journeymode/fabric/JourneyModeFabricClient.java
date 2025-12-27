package com.aryangpt007.journeymode.fabric;

import com.aryangpt007.journeymode.fabric.client.Keybinds;
import com.aryangpt007.journeymode.fabric.client.screen.JourneyModeScreen;
import com.aryangpt007.journeymode.fabric.network.FabricClientNetworkHandler;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.gui.screens.MenuScreens;

public class JourneyModeFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        Keybinds.init();
        FabricClientNetworkHandler.registerClientPackets();
        MenuScreens.register(JourneyModeFabric.JOURNEY_MODE_MENU, JourneyModeScreen::new);
    }
}
