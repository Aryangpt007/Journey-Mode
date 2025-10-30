package com.aryangpt007.journeymode;

import com.aryangpt007.journeymode.client.ClientSetup;
import com.aryangpt007.journeymode.data.JourneyDataAttachment;
import com.aryangpt007.journeymode.events.JourneyModeEvents;
import com.aryangpt007.journeymode.menu.JourneyModeMenu;
import com.aryangpt007.journeymode.network.NetworkHandler;
import com.mojang.logging.LogUtils;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import org.slf4j.Logger;

@Mod(JourneyMode.MODID)
public class JourneyMode {
    public static final String MODID = "journeymode";
    public static final Logger LOGGER = LogUtils.getLogger();

    // Registration for menus
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, MODID);
    public static final DeferredHolder<MenuType<?>, MenuType<JourneyModeMenu>> JOURNEY_MODE_MENU = 
        MENUS.register("journey_mode_menu", () -> new MenuType<>((id, inventory) -> new JourneyModeMenu(id, inventory), net.minecraft.world.flag.FeatureFlags.VANILLA_SET));

    // Registration for attachments (player data)
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, MODID);
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<JourneyDataAttachment>> JOURNEY_DATA = 
        ATTACHMENTS.register("journey_data", () -> AttachmentType.builder(() -> new JourneyDataAttachment()).serialize(JourneyDataAttachment.CODEC).build());

    public JourneyMode(IEventBus modEventBus) {
        LOGGER.info("Journey Mode is loading...");

        // Register deferred registries
        MENUS.register(modEventBus);
        ATTACHMENTS.register(modEventBus);

        // Register network packets
        NetworkHandler.register(modEventBus);

        // Register events
        NeoForge.EVENT_BUS.register(new JourneyModeEvents());

        // Client-side setup
        if (FMLEnvironment.dist == Dist.CLIENT) {
            modEventBus.addListener(ClientSetup::onClientSetup);
            modEventBus.addListener(ClientSetup::registerScreens);
            modEventBus.addListener(com.aryangpt007.journeymode.client.KeyBindings::register);
            NeoForge.EVENT_BUS.register(com.aryangpt007.journeymode.client.KeyBindings.ClientEvents.class);
        }

        LOGGER.info("Journey Mode loaded successfully!");
    }

    public static Component translatable(String key, Object... args) {
        return Component.translatable("journeymode." + key, args);
    }
}
