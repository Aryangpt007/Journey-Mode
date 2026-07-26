package com.aryangpt007.journeymode.network;

import com.aryangpt007.journeymode.JourneyMode;
import com.aryangpt007.journeymode.config.ConfigHandler;
import com.aryangpt007.journeymode.data.JourneyDataAttachment;
import com.aryangpt007.journeymode.data.GlobalDataHandler;
import com.aryangpt007.journeymode.menu.JourneyModeMenu;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Registry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
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
    /** §2 real-time config sync: server -> client push of resolved blacklist/threshold rules. */
    public static final ResourceLocation CONFIG_SYNC = new ResourceLocation(JourneyMode.MODID, "config_sync");
    /** §8 Deposit All: client -> server, payload is just a boolean includeHotbar. */
    public static final ResourceLocation DEPOSIT_ALL = new ResourceLocation(JourneyMode.MODID, "deposit_all");

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

                // §1 Shared Team Catalogs: unlock check must follow the same team-or-personal
                // resolution as everywhere else - a team member can fetch anything the TEAM
                // unlocked, not just their own personal unlocks. This is the anti-cheat
                // boundary (server is authoritative), so getting this branch right matters.
                boolean unlocked = journeyData.getTeamId() != null
                    ? com.aryangpt007.journeymode.data.TeamDataHandler.getTeamForPlayer(player.getUUID())
                        .map(team -> team.isUnlocked(itemId)).orElse(false)
                    : journeyData.isUnlocked(itemId);

                if (!stack.isEmpty() && unlocked) {
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

        ServerPlayNetworking.registerGlobalReceiver(DEPOSIT_ALL, (server, player, handler, buf, responseSender) -> {
            boolean includeHotbar = buf.readBoolean();
            server.execute(() -> {
                if (player.containerMenu instanceof JourneyModeMenu) {
                    JourneyModeMenu menu = (JourneyModeMenu) player.containerMenu;
                    menu.processDepositAll(includeHotbar);
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

            boolean enabled = buf.readBoolean();
            boolean showTooltips = buf.readBoolean();
            String teamDisplayName = buf.readUtf();

            client.execute(() -> {
                Player player = client.player;
                if (player != null) {
                    JourneyDataAttachment data = GlobalDataHandler.getPlayerData(player);
                    data.updateFromSync(counts, unlocked, timestamps, enabled, showTooltips, teamDisplayName);
                    celebrateNewUnlocks(client, player, data.getAndClearNewlyUnlocked());
                }
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(CONFIG_SYNC, (client, handler, buf, responseSender) -> {
            // Read buf in network thread to avoid race conditions
            ConfigHandler.SyncSnapshot snapshot = decodeConfigSync(buf);
            client.execute(() -> ConfigHandler.applySyncedRules(snapshot));
        });
    }

    /**
     * §11 Visual Polish: unlock sound + action-bar message on threshold crossing, driven purely
     * by client-side diffing (see JourneyDataAttachment.updateFromSync) - no dedicated
     * "newly_unlocked" packet field needed. A full graphical toast (with custom textures) is
     * deliberately out of scope for this pass - there's no art-asset pipeline in play here, so
     * this uses the same action-bar message style the rest of the mod already uses.
     */
    private static void celebrateNewUnlocks(Minecraft client, Player player, Set<String> newlyUnlocked) {
        if (newlyUnlocked.isEmpty()) return;

        if (newlyUnlocked.size() == 1) {
            String key = newlyUnlocked.iterator().next();
            ItemStack stack = JourneyDataAttachment.itemStackFromKey(key);
            String name = stack.isEmpty() ? key : stack.getHoverName().getString();
            player.displayClientMessage(new TextComponent("§6Unlocked: " + name + "!"), true);
        } else {
            player.displayClientMessage(new TextComponent("§6Unlocked " + newlyUnlocked.size() + " items!"), true);
        }
    }

    // ---------------------------------------------------------------- §2 config sync (server -> client)

    /** Server-side: encode and send a config snapshot to a single player. */
    public static void sendConfigSync(ServerPlayer player, ConfigHandler.SyncSnapshot snapshot) {
        FriendlyByteBuf buf = PacketByteBufs.create();
        encodeConfigSync(buf, snapshot);
        ServerPlayNetworking.send(player, CONFIG_SYNC, buf);
    }

    private static void encodeConfigSync(FriendlyByteBuf buf, ConfigHandler.SyncSnapshot snapshot) {
        writeStringList(buf, snapshot.getExactBlacklist());
        writeStringList(buf, snapshot.getTagBlacklist());
        writeStringList(buf, snapshot.getPatternBlacklist());

        Map<String, Integer> exactThresholds = snapshot.getExactThresholds();
        buf.writeVarInt(exactThresholds.size());
        for (Map.Entry<String, Integer> e : exactThresholds.entrySet()) {
            buf.writeUtf(e.getKey());
            buf.writeVarInt(e.getValue());
        }

        writeEntryList(buf, snapshot.getTagThresholds());
        writeEntryList(buf, snapshot.getPatternThresholds());

        Integer defaultOverride = snapshot.getDefaultOverride();
        buf.writeBoolean(defaultOverride != null);
        if (defaultOverride != null) {
            buf.writeVarInt(defaultOverride);
        }
        buf.writeVarInt(snapshot.getMaxThresholdCap());
    }

    private static ConfigHandler.SyncSnapshot decodeConfigSync(FriendlyByteBuf buf) {
        List<String> exactBlacklist = readStringList(buf);
        List<String> tagBlacklist = readStringList(buf);
        List<String> patternBlacklist = readStringList(buf);

        int exactSize = buf.readVarInt();
        Map<String, Integer> exactThresholds = new HashMap<>();
        for (int i = 0; i < exactSize; i++) {
            exactThresholds.put(buf.readUtf(), buf.readVarInt());
        }

        List<Map.Entry<String, Integer>> tagThresholds = readEntryList(buf);
        List<Map.Entry<String, Integer>> patternThresholds = readEntryList(buf);

        Integer defaultOverride = buf.readBoolean() ? Integer.valueOf(buf.readVarInt()) : null;
        int maxThresholdCap = buf.readVarInt();

        return new ConfigHandler.SyncSnapshot(
            exactBlacklist, tagBlacklist, patternBlacklist,
            exactThresholds, tagThresholds, patternThresholds,
            defaultOverride, maxThresholdCap);
    }

    private static void writeStringList(FriendlyByteBuf buf, List<String> values) {
        buf.writeVarInt(values.size());
        for (String v : values) {
            buf.writeUtf(v);
        }
    }

    private static List<String> readStringList(FriendlyByteBuf buf) {
        int size = buf.readVarInt();
        List<String> result = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            result.add(buf.readUtf());
        }
        return result;
    }

    private static void writeEntryList(FriendlyByteBuf buf, List<Map.Entry<String, Integer>> entries) {
        buf.writeVarInt(entries.size());
        for (Map.Entry<String, Integer> e : entries) {
            buf.writeUtf(e.getKey());
            buf.writeVarInt(e.getValue());
        }
    }

    private static List<Map.Entry<String, Integer>> readEntryList(FriendlyByteBuf buf) {
        int size = buf.readVarInt();
        List<Map.Entry<String, Integer>> result = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            result.add(new AbstractMap.SimpleEntry<>(buf.readUtf(), buf.readVarInt()));
        }
        return result;
    }

    /**
     * Utility to send the §8 Deposit All request from client to server.
     */
    public static void sendDepositAll(boolean includeHotbar) {
        FriendlyByteBuf buf = PacketByteBufs.create();
        buf.writeBoolean(includeHotbar);
        ClientPlayNetworking.send(DEPOSIT_ALL, buf);
    }

    /**
     * Utility to send sync packet to a specific player. §1 Shared Team Catalogs: the caller
     * (GlobalDataHandler.syncToClient) has already resolved team-vs-personal data before this is
     * called - this method just encodes whatever it's handed, it never re-resolves anything.
     */
    public static void syncToPlayer(ServerPlayer player, Map<String, Integer> counts, Set<String> unlocked,
                                     Map<String, Long> timestamps, boolean enabled, boolean showTooltips,
                                     String teamDisplayName) {
        FriendlyByteBuf buf = PacketByteBufs.create();

        buf.writeVarInt(counts.size());
        counts.forEach((k, v) -> {
            buf.writeUtf(k);
            buf.writeVarInt(v);
        });

        buf.writeVarInt(unlocked.size());
        unlocked.forEach(buf::writeUtf);

        buf.writeVarInt(timestamps.size());
        timestamps.forEach((k, v) -> {
            buf.writeUtf(k);
            buf.writeLong(v);
        });

        buf.writeBoolean(enabled);
        buf.writeBoolean(showTooltips);
        buf.writeUtf(teamDisplayName == null ? "" : teamDisplayName);

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
