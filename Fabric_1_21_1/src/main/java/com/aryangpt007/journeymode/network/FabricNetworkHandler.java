package com.aryangpt007.journeymode.network;

import com.aryangpt007.journeymode.JourneyMode;
import com.aryangpt007.journeymode.data.JourneyDataAttachment;
import com.aryangpt007.journeymode.data.GlobalDataHandler;
import com.aryangpt007.journeymode.menu.JourneyModeMenu;
import com.aryangpt007.journeymode.network.packets.*;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class FabricNetworkHandler {
    private static final Logger LOGGER = LogManager.getLogger("journeymode-network");

    /**
     * Called during common initialization to register custom payloads and server receivers
     */
    public static void registerCommon() {
        // 1. Register play payloads
        PayloadTypeRegistry.playS2C().register(SyncJourneyDataPacket.TYPE, SyncJourneyDataPacket.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(SubmitDepositPacket.TYPE, SubmitDepositPacket.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(RequestItemPacket.TYPE, RequestItemPacket.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(OpenJourneyMenuPacket.TYPE, OpenJourneyMenuPacket.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(DeleteCarriedPacket.TYPE, DeleteCarriedPacket.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(SyncTabPacket.TYPE, SyncTabPacket.STREAM_CODEC);

        // 2. Register server receivers
        ServerPlayNetworking.registerGlobalReceiver(SubmitDepositPacket.TYPE, (payload, context) -> {
            context.server().execute(() -> {
                ServerPlayer player = context.player();
                if (player.containerMenu instanceof JourneyModeMenu menu) {
                    menu.processDeposit();
                }
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(RequestItemPacket.TYPE, (payload, context) -> {
            context.server().execute(() -> {
                ServerPlayer player = context.player();
                JourneyDataAttachment journeyData = GlobalDataHandler.getPlayerData(player);
                
                ItemStack stack = JourneyDataAttachment.itemStackFromKey(payload.itemId(), player.level().registryAccess());
                
                if (!stack.isEmpty() && journeyData.isUnlocked(payload.itemId())) {
                    stack.setCount(Math.min(payload.count(), 64));
                    
                    if (!player.getInventory().add(stack)) {
                        ItemEntity entity = new ItemEntity(
                            player.level(),
                            player.getX(),
                            player.getY(),
                            player.getZ(),
                            stack
                        );
                        player.level().addFreshEntity(entity);
                    }
                } else {
                    LOGGER.warn("Player {} tried to request locked or invalid item: {}", 
                        player.getName().getString(), payload.itemId());
                }
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(OpenJourneyMenuPacket.TYPE, (payload, context) -> {
            context.server().execute(() -> {
                ServerPlayer player = context.player();
                JourneyDataAttachment data = GlobalDataHandler.getPlayerData(player);
                
                if (!data.isEnabled()) {
                    player.displayClientMessage(
                        JourneyMode.translatable("disabled_message"),
                        false
                    );
                    return;
                }
                
                player.openMenu(new SimpleMenuProvider(
                    (containerId, playerInventory, p) -> new JourneyModeMenu(containerId, playerInventory),
                    JourneyMode.translatable("menu.title")
                ));
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(DeleteCarriedPacket.TYPE, (payload, context) -> {
            context.server().execute(() -> {
                ServerPlayer player = context.player();
                player.containerMenu.setCarried(ItemStack.EMPTY);
                player.containerMenu.broadcastChanges();
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(SyncTabPacket.TYPE, (payload, context) -> {
            context.server().execute(() -> {
                ServerPlayer player = context.player();
                if (player.containerMenu instanceof JourneyModeMenu menu) {
                    menu.setInJourneyTab(payload.inJourneyTab());
                }
            });
        });
    }

    /**
     * Called during client initialization to register client receivers
     */
    public static void registerClient() {
        ClientPlayNetworking.registerGlobalReceiver(SyncJourneyDataPacket.TYPE, (payload, context) -> {
            context.client().execute(() -> {
                Player player = context.player();
                JourneyDataAttachment data = GlobalDataHandler.getPlayerData(player);
                data.updateFromSync(payload.collectedCounts(), payload.unlockedItems(), payload.unlockTimestamps());
            });
        });
    }

    /**
     * Utility to send sync packet to a specific player
     */
    public static void syncToPlayer(ServerPlayer player, JourneyDataAttachment data) {
        SyncJourneyDataPacket packet = new SyncJourneyDataPacket(
            data.getAllCollectedCounts(),
            data.getUnlockedItems(),
            data.getUnlockTimestamps()
        );
        ServerPlayNetworking.send(player, packet);
    }

    /**
     * Utility to send packet to server from client
     */
    public static void sendToServer(CustomPacketPayload packet) {
        ClientPlayNetworking.send(packet);
    }
}
