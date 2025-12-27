package com.aryangpt007.journeymode.fabric;

import com.aryangpt007.journeymode.fabric.menu.JourneyModeMenu;
import com.aryangpt007.journeymode.fabric.network.FabricNetworkHandler;
import net.fabricmc.api.ModInitializer;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JourneyModeFabric implements ModInitializer {

    public static final String MOD_ID = "journeymode";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    // 1.21.x MenuType requires feature flags
    public static final MenuType<JourneyModeMenu> JOURNEY_MODE_MENU =
            Registry.register(
                    BuiltInRegistries.MENU,
                    ResourceLocation.fromNamespaceAndPath(MOD_ID, "journey_mode_menu"),
                    new MenuType<>(JourneyModeMenu::new, FeatureFlags.VANILLA_SET)
            );

    public static Component translatable(String key, Object... args) {
        return Component.translatable(key, args);
    }

    @Override
    public void onInitialize() {
        FabricNetworkHandler.registerServerPackets();
        // other server-safe registrations can go here later
    }
}
