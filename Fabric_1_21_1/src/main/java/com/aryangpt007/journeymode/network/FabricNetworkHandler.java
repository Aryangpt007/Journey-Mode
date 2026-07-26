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
        PayloadTypeRegistry.playS2C().register(ConfigSyncPacket.TYPE, ConfigSyncPacket.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(DepositAllPacket.TYPE, DepositAllPacket.STREAM_CODEC);

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

                // §1 Shared Team Catalogs: unlock check must follow the same team-or-personal
                // resolution as everywhere else - a team member can fetch anything the TEAM
                // unlocked, not just their own personal unlocks. This is the anti-cheat boundary
                // (server is authoritative), so getting this branch right matters.
                boolean unlocked = journeyData.getTeamId() != null
                    ? com.aryangpt007.journeymode.data.TeamDataHandler.getTeamForPlayer(player.getUUID())
                        .map(team -> team.isUnlocked(payload.itemId())).orElse(false)
                    : journeyData.isUnlocked(payload.itemId());

                if (!stack.isEmpty() && unlocked) {
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

        ServerPlayNetworking.registerGlobalReceiver(DepositAllPacket.TYPE, (payload, context) -> {
            context.server().execute(() -> {
                ServerPlayer player = context.player();
                if (player.containerMenu instanceof JourneyModeMenu menu) {
                    menu.processDepositAll(payload.includeHotbar());
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
                data.updateFromSync(payload.collectedCounts(), payload.unlockedItems(), payload.unlockTimestamps(), payload.enabled(), payload.showTooltips(), payload.teamDisplayName());
                celebrateNewUnlocks(player, data.getAndClearNewlyUnlocked());
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(ConfigSyncPacket.TYPE, (payload, context) -> {
            context.client().execute(() -> com.aryangpt007.journeymode.config.ConfigHandler.applySyncedRules(payload.snapshot()));
        });
    }

    /**
     * §11 Visual Polish: unlock sound + action-bar message on threshold crossing, driven purely
     * by client-side diffing (see JourneyDataAttachment.updateFromSync) - no dedicated
     * "newly_unlocked" packet field needed. A full graphical toast (with custom textures) is
     * deliberately out of scope for this pass - there's no art-asset pipeline in play here, so
     * this uses the same action-bar message style the rest of the mod already uses. Runs purely
     * client-side (this is called only from the client's SyncJourneyDataPacket receiver above),
     * using the client Player instance directly rather than net.minecraft.client.Minecraft so
     * this stays free of any client-only class references.
     */
    private static void celebrateNewUnlocks(Player player, java.util.Set<String> newlyUnlocked) {
        if (newlyUnlocked.isEmpty() || player == null) return;

        if (newlyUnlocked.size() == 1) {
            String key = newlyUnlocked.iterator().next();
            ItemStack stack = JourneyDataAttachment.itemStackFromKey(key, player.level().registryAccess());
            String name = stack.isEmpty() ? key : stack.getHoverName().getString();
            player.displayClientMessage(net.minecraft.network.chat.Component.literal("§6Unlocked: " + name + "!"), true);
        } else {
            player.displayClientMessage(net.minecraft.network.chat.Component.literal("§6Unlocked " + newlyUnlocked.size() + " items!"), true);
        }
    }

    /**
     * Utility to send sync packet to a specific player. §1 Shared Team Catalogs: if the player
     * is on a team, the TEAM's shared progress is sent instead of their personal progress - the
     * client never needs to know or care which source it came from, it just renders whatever
     * it's given. This is the only place that distinction has to be made for display purposes
     * (deposit/fetch/delete have their own team checks - see JourneyModeMenu / RequestItemPacket
     * handling below).
     */
    public static void syncToPlayer(ServerPlayer player, JourneyDataAttachment data) {
        java.util.Optional<com.aryangpt007.journeymode.data.TeamData> team = data.getTeamId() != null
            ? com.aryangpt007.journeymode.data.TeamDataHandler.getTeamForPlayer(player.getUUID())
            : java.util.Optional.empty();

        var counts = team.map(com.aryangpt007.journeymode.data.TeamData::getAllCollectedCounts).orElseGet(data::getAllCollectedCounts);
        var unlocked = team.map(com.aryangpt007.journeymode.data.TeamData::getUnlockedItems).orElseGet(data::getUnlockedItems);
        var timestamps = team.map(com.aryangpt007.journeymode.data.TeamData::getUnlockTimestamps).orElseGet(data::getUnlockTimestamps);
        String teamName = team.map(com.aryangpt007.journeymode.data.TeamData::getDisplayName).orElse(null);

        SyncJourneyDataPacket packet = new SyncJourneyDataPacket(
            counts,
            unlocked,
            timestamps,
            data.isEnabled(),
            data.isShowTooltips(),
            teamName
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
