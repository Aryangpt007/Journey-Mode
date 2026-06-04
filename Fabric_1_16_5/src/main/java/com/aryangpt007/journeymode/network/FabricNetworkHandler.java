package com.aryangpt007.journeymode.network;

import com.aryangpt007.journeymode.JourneyMode;
import com.aryangpt007.journeymode.data.JourneyDataAttachment;
import com.aryangpt007.journeymode.data.GlobalDataHandler;
import com.aryangpt007.journeymode.menu.JourneyModeMenu;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.core.Registry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class FabricNetworkHandler {
    private static final Logger LOGGER = LogManager.getLogger("journeymode-network");

    public static final ResourceLocation SYNC_JOURNEY_DATA = new ResourceLocation(JourneyMode.MODID, "sync_journey_data");
    public static final ResourceLocation SUBMIT_DEPOSIT = new ResourceLocation(JourneyMode.MODID, "submit_deposit");
    public static final ResourceLocation REQUEST_ITEM = new ResourceLocation(JourneyMode.MODID, "request_item");
    public static final ResourceLocation OPEN_JOURNEY_MENU = new ResourceLocation(JourneyMode.MODID, "open_journey_menu");
    public static final ResourceLocation DELETE_CARRIED = new ResourceLocation(JourneyMode.MODID, "delete_carried");
    public static final ResourceLocation SYNC_TAB = new ResourceLocation(JourneyMode.MODID, "sync_tab");

    /**
     * Called during common initialization to register server receivers
     */
    public static void registerCommon() {
        ServerPlayNetworking.registerGlobalReceiver(SUBMIT_DEPOSIT, (server, player, handler, buf, responseSender) -> {
            server.execute(() -> {
                if (player.containerMenu instanceof JourneyModeMenu) {
                    JourneyModeMenu menu = (JourneyModeMenu) player.containerMenu;
                    menu.processDeposit();
                }
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(REQUEST_ITEM, (server, player, handler, buf, responseSender) -> {
            String itemId = buf.readUtf();
            int count = buf.readVarInt();
            
            server.execute(() -> {
                JourneyDataAttachment journeyData = GlobalDataHandler.getPlayerData(player);
                ItemStack stack = JourneyDataAttachment.itemStackFromKey(itemId);
                
                if (!stack.isEmpty() && journeyData.isUnlocked(itemId)) {
                    stack.setCount(Math.min(count, 64));
                    
                    if (!player.inventory.add(stack)) {
                        ItemEntity entity = new ItemEntity(
                            player.level,
                            player.getX(),
                            player.getY(),
                            player.getZ(),
                            stack
                        );
                        player.level.addFreshEntity(entity);
                    }
                } else {
                    LOGGER.warn("Player {} tried to request locked or invalid item: {}", 
                        player.getName().getString(), itemId);
                }
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(OPEN_JOURNEY_MENU, (server, player, handler, buf, responseSender) -> {
            server.execute(() -> {
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

        ServerPlayNetworking.registerGlobalReceiver(DELETE_CARRIED, (server, player, handler, buf, responseSender) -> {
            server.execute(() -> {
                player.inventory.setCarried(ItemStack.EMPTY);
                player.connection.send(new net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket(-1, -1, ItemStack.EMPTY));
                player.containerMenu.broadcastChanges();
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(SYNC_TAB, (server, player, handler, buf, responseSender) -> {
            boolean inJourneyTab = buf.readBoolean();
            server.execute(() -> {
                if (player.containerMenu instanceof JourneyModeMenu) {
                    JourneyModeMenu menu = (JourneyModeMenu) player.containerMenu;
                    menu.setInJourneyTab(inJourneyTab);
                }
            });
        });
    }

    /**
     * Called during client initialization to register client receivers
     */
    public static void registerClient() {
        ClientPlayNetworking.registerGlobalReceiver(SYNC_JOURNEY_DATA, (client, handler, buf, responseSender) -> {
            // Read buf in network thread to avoid race conditions
            Map<String, Integer> counts = new HashMap<>();
            int countsSize = buf.readVarInt();
            for (int i = 0; i < countsSize; i++) {
                counts.put(buf.readUtf(), buf.readVarInt());
            }

            Set<String> unlocked = new HashSet<>();
            int unlockedSize = buf.readVarInt();
            for (int i = 0; i < unlockedSize; i++) {
                unlocked.add(buf.readUtf());
            }

            Map<String, Long> timestamps = new HashMap<>();
            int timestampsSize = buf.readVarInt();
            for (int i = 0; i < timestampsSize; i++) {
                timestamps.put(buf.readUtf(), buf.readLong());
            }

            client.execute(() -> {
                Player player = client.player;
                if (player != null) {
                    JourneyDataAttachment data = GlobalDataHandler.getPlayerData(player);
                    data.updateFromSync(counts, unlocked, timestamps);
                }
            });
        });
    }

    /**
     * Utility to send sync packet to a specific player
     */
    public static void syncToPlayer(ServerPlayer player, JourneyDataAttachment data) {
        FriendlyByteBuf buf = PacketByteBufs.create();
        
        Map<String, Integer> counts = data.getAllCollectedCounts();
        buf.writeVarInt(counts.size());
        counts.forEach((k, v) -> {
            buf.writeUtf(k);
            buf.writeVarInt(v);
        });

        Set<String> unlocked = data.getUnlockedItems();
        buf.writeVarInt(unlocked.size());
        unlocked.forEach(buf::writeUtf);

        Map<String, Long> timestamps = data.getUnlockTimestamps();
        buf.writeVarInt(timestamps.size());
        timestamps.forEach((k, v) -> {
            buf.writeUtf(k);
            buf.writeLong(v);
        });

        ServerPlayNetworking.send(player, SYNC_JOURNEY_DATA, buf);
    }

    /**
     * Utility to submit deposit from client to server
     */
    public static void submitDeposit() {
        ClientPlayNetworking.send(SUBMIT_DEPOSIT, PacketByteBufs.empty());
    }

    /**
     * Utility to request item from client to server
     */
    public static void requestItem(String itemId, int count) {
        FriendlyByteBuf buf = PacketByteBufs.create();
        buf.writeUtf(itemId);
        buf.writeVarInt(count);
        ClientPlayNetworking.send(REQUEST_ITEM, buf);
    }

    /**
     * Utility to delete carried item on the client
     */
    public static void deleteCarried() {
        ClientPlayNetworking.send(DELETE_CARRIED, PacketByteBufs.empty());
    }

    /**
     * Utility to sync tab state from client to server
     */
    public static void syncTab(boolean inJourneyTab) {
        FriendlyByteBuf buf = PacketByteBufs.create();
        buf.writeBoolean(inJourneyTab);
        ClientPlayNetworking.send(SYNC_TAB, buf);
    }

    /**
     * Utility to open the Journey menu from client to server
     */
    public static void openJourneyMenu() {
        ClientPlayNetworking.send(OPEN_JOURNEY_MENU, PacketByteBufs.empty());
    }
}
